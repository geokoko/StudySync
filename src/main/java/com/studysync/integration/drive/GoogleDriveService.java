package com.studysync.integration.drive;

import com.google.api.client.auth.oauth2.Credential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * High-level service exposed to the rest of the application for Google sign-in and Drive synchronization.
 */
@Service
public class GoogleDriveService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleDriveService.class);

    private final GoogleDriveSettings settings;
    private final GoogleCredentialManager credentialManager;
    private final GoogleDriveGateway gateway;
    private final JdbcTemplate jdbcTemplate;
    private Credential activeCredential;
    private String cachedAccountEmail;
    private volatile boolean shutdownSaveEnabled = false;

    /** True when the user explicitly chose "Exit without Saving" or "Save Locally & Exit".
     *  Distinguishes an explicit opt-out from the default state (never asked). */
    private volatile boolean shutdownSaveExplicitlyDisabled = false;

    /** Tracks whether the local DB has been modified since the last upload to Drive. */
    private volatile boolean localDbDirty = false;

    /** Listeners notified when the database is reloaded from Drive. */
    private final java.util.List<Runnable> reloadListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Listeners notified just before the database is shut down for a reload. */
    private final java.util.List<Runnable> preReloadListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public GoogleDriveService(GoogleDriveSettings settings, GoogleCredentialManager credentialManager, GoogleDriveGateway gateway, JdbcTemplate jdbcTemplate) {
        this.settings = settings;
        this.credentialManager = credentialManager;
        this.gateway = gateway;
        this.jdbcTemplate = jdbcTemplate;

        if (settings != null && settings.isReady()) {
            this.activeCredential = loadStoredCredential();
            if (this.activeCredential != null) {
                this.cachedAccountEmail = gateway.fetchAccountEmail(activeCredential).orElse(null);
                if (this.cachedAccountEmail != null) {
                    logger.info("Loaded stored Google credentials for account: {}", cachedAccountEmail);
                } else {
                    logger.warn("Stored credential exists but failed to fetch account email");
                }
            } else {
                logger.info("No stored Google credentials found during initialization");
            }
        }
    }

    public void setShutdownSaveEnabled(boolean enabled) {
        this.shutdownSaveEnabled = enabled;
        // Track whether the user made an explicit choice via the exit dialog.
        // Reset when re-enabled so the latch doesn't stick after "Push to Drive".
        this.shutdownSaveExplicitlyDisabled = !enabled;
    }

    public boolean isIntegrationEnabled() {
        return settings != null && settings.isReady();
    }

    /**
     * Checks if a user is currently signed in with Google.
     *
     * @return true if a valid Google credential is present, false otherwise
     */
    public boolean isSignedIn() {
        return activeCredential != null;
    }

    /**
     * Returns the email address of the currently signed-in Google account, if available.
     *
     * @return an {@link Optional} containing the signed-in account's email, or empty if not signed in
     */
    public Optional<String> getSignedInAccountEmail() {
        return Optional.ofNullable(cachedAccountEmail);
    }

    /**
     * Returns the local file system path to the StudySync database.
     * <p>
     * If Google Drive integration is enabled, returns the configured path; otherwise,
     * returns the default path {@code data/studysync.mv.db}.
     *
     * @return the {@link Path} to the local StudySync database file
     */
    public Path getLocalDatabasePath() {
        return settings != null ? settings.localDatabasePath() : Path.of("data", "studysync.mv.db");
    }

    public synchronized boolean signInWithGoogle() {
        if (!isIntegrationEnabled()) {
            return false;
        }
        try {
            Credential credential = credentialManager.authorizeInteractively();
            this.activeCredential = credential;
            this.cachedAccountEmail = gateway.fetchAccountEmail(credential).orElse(null);
            logger.info("Authenticated StudySync user with Google account {}", cachedAccountEmail);
            return true;
        } catch (IOException e) {
            logger.error("Google sign-in failed: {}", e.getMessage());
            return false;
        }
    }

    public synchronized void signOut() {
        if (!isIntegrationEnabled()) {
            return;
        }
        credentialManager.clearStoredCredentials();
        this.activeCredential = null;
        this.cachedAccountEmail = null;
        logger.info("Cleared Google Drive credentials for StudySync");
    }

    public synchronized boolean uploadDatabaseSnapshot() {
        if (!isIntegrationEnabled() || activeCredential == null) {
            return false;
        }
        // Flush H2 in-memory cache to the .mv.db file before uploading.
        // If the checkpoint fails, abort the upload to avoid overwriting
        // Drive with a stale database file that is missing recent writes.
        try {
            jdbcTemplate.execute("CHECKPOINT SYNC");
        } catch (Exception e) {
            logger.error("H2 CHECKPOINT SYNC failed before upload — aborting upload to prevent stale data on Drive", e);
            return false;
        }
        boolean uploaded = gateway.uploadDatabaseToDrive(activeCredential);
        if (uploaded) {
            localDbDirty = false;
        }
        return uploaded;
    }

    public synchronized boolean downloadDatabaseSnapshot() {
        if (!isIntegrationEnabled() || activeCredential == null) {
            return false;
        }
        return gateway.downloadDatabaseFromDrive(activeCredential);
    }

    // ================================================================
    // SYNC STATUS & LOCAL-DIRTY TRACKING
    // ================================================================

    /**
     * Mark the local database as having unsaved changes not yet uploaded to Drive.
     * Should be called after any write operation (add/edit/delete goal, task, session, etc.).
     */
    public void markLocalDbDirty() {
        this.localDbDirty = true;
    }

    /**
     * Returns whether the local database has changes not yet uploaded to Drive.
     */
    public boolean isLocalDbDirty() {
        return localDbDirty;
    }

    /**
     * Possible sync states between local database and Google Drive.
     */
    public enum SyncStatus {
        /** Drive integration not enabled or not signed in. */
        DISABLED,
        /** Local DB is up to date with Drive (or no remote DB exists yet). */
        UP_TO_DATE,
        /** Google Drive has a newer version than the local DB. */
        DRIVE_NEWER,
        /** Local DB has been modified since the last upload. */
        LOCAL_NEWER,
        /** Unable to determine status (e.g. network error). */
        UNKNOWN
    }

    /**
     * Compares local DB last-modified time with Google Drive's copy.
     * This is a network call and should be executed off the FX thread.
     */
    public SyncStatus checkSyncStatus() {
        if (!isIntegrationEnabled() || activeCredential == null) {
            return SyncStatus.DISABLED;
        }
        if (localDbDirty) {
            return SyncStatus.LOCAL_NEWER;
        }
        try {
            Optional<Instant> remoteTime = gateway.getRemoteModifiedTime(activeCredential);
            if (remoteTime.isEmpty()) {
                return SyncStatus.UP_TO_DATE; // no remote file yet
            }
            Path localPath = getLocalDatabasePath();
            if (!Files.exists(localPath)) {
                return SyncStatus.DRIVE_NEWER; // remote exists, local doesn't
            }
            Instant localTime = Files.getLastModifiedTime(localPath).toInstant();
            long toleranceSeconds = 30;
            if (remoteTime.get().isAfter(localTime.plusSeconds(toleranceSeconds))) {
                return SyncStatus.DRIVE_NEWER;
            }
            if (localTime.isAfter(remoteTime.get().plusSeconds(toleranceSeconds))) {
                return SyncStatus.LOCAL_NEWER;
            }
            return SyncStatus.UP_TO_DATE;
        } catch (Exception e) {
            logger.warn("Failed to check sync status: {}", e.getMessage());
            return SyncStatus.UNKNOWN;
        }
    }

    /**
     * Register a listener to be called when the database is reloaded from Drive.
     * UI panels should use this to refresh their views.
     */
    public void addReloadListener(Runnable listener) {
        reloadListeners.add(listener);
    }

    /**
     * Register a listener called just before the DB shutdown/reload begins.
     * UI can use this to show a blocking overlay.
     */
    public void addPreReloadListener(Runnable listener) {
        preReloadListeners.add(listener);
    }

    /**
     * Fires all registered pre-reload listeners (on the caller's thread).
     */
    private void notifyPreReloadListeners() {
        for (Runnable listener : preReloadListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                logger.warn("Pre-reload listener failed: {}", e.getMessage());
            }
        }
    }

    /**
     * Fires all registered reload listeners (on the caller's thread).
     */
    private void notifyReloadListeners() {
        for (Runnable listener : reloadListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                logger.warn("Reload listener failed: {}", e.getMessage());
            }
        }
    }

    /**
     * Shuts down H2, downloads the Drive database to replace the local file,
     * then reconnects and notifies listeners.
     *
     * <p>The shutdown-before-download order ensures the {@code .mv.db} file lock
     * is released before the download attempts to replace it (required on Windows).
     *
     * @param dbShutdown  callback that closes H2 and evicts pooled connections
     * @param dbReconnect callback that reopens H2 and re-applies migrations
     * @return true if the download and reload succeeded
     */
    public synchronized boolean downloadAndReload(Runnable dbShutdown, Runnable dbReconnect) {
        Objects.requireNonNull(dbShutdown, "dbShutdown callback must not be null");
        Objects.requireNonNull(dbReconnect, "dbReconnect callback must not be null");

        if (!isIntegrationEnabled() || activeCredential == null) {
            return false;
        }

        // 0. Notify pre-reload listeners (e.g. show overlay)
        notifyPreReloadListeners();

        // 1. Release the H2 file lock so the download can replace the file
        try {
            dbShutdown.run();
        } catch (Exception e) {
            logger.error("DB shutdown failed during Drive reload — aborting", e);
            return false;
        }

        // 2. Download the Drive copy over the (now unlocked) local file
        boolean downloaded = false;
        try {
            downloaded = gateway.downloadDatabaseFromDrive(activeCredential);
        } finally {
            // 3. Always reconnect — even if the download failed the old file is still there
            try {
                dbReconnect.run();
            } catch (Exception e) {
                logger.error("DB reconnect failed after Drive reload — application may need a restart", e);
                return false;
            }
        }

        if (downloaded) {
            // Clear the dirty flag BEFORE notifying reload listeners.
            // Listeners (e.g. delayed-goal/task processing) may mutate the
            // freshly downloaded DB and call markLocalDbDirty(), which will
            // re-set this flag to true.  Clearing first ensures those
            // mutations are correctly tracked as local changes.
            localDbDirty = false;

            // Force H2 to flush the freshly-opened database to disk immediately.
            // Without this, DB_CLOSE_DELAY=-1 keeps changes in memory, and if the
            // JVM exits before H2's shutdown hook completes (race with class
            // unloading), all downloaded data is silently lost — the file on disk
            // reverts to its pre-download state on the next restart.
            try {
                jdbcTemplate.execute("CHECKPOINT SYNC");
                logger.info("Post-download CHECKPOINT SYNC completed — downloaded data persisted to disk");
            } catch (Exception e) {
                logger.warn("Post-download CHECKPOINT SYNC failed: {}", e.getMessage());
            }

            logger.info("Database reloaded from Google Drive successfully");
        }

        // Always notify reload listeners so the UI overlay is dismissed and
        // panels are refreshed — even on failure (the old DB was reconnected).
        notifyReloadListeners();
        return downloaded;
    }

    @PreDestroy
    public void onShutdown() {
        // Upload to Drive if explicitly requested (dialog "Push to Drive & Exit")
        // OR if the user never went through the exit dialog at all (SIGTERM, OS
        // logout, etc.) and there are unsaved local changes.  This prevents
        // silent data loss on non-dialog shutdowns while still respecting the
        // user's explicit choice of "Exit without Saving" (which sets
        // shutdownSaveEnabled=false).
        boolean shouldUpload = shutdownSaveEnabled || (localDbDirty && !shutdownSaveExplicitlyDisabled);
        if (isIntegrationEnabled() && activeCredential != null && shouldUpload) {
            boolean checkpointOk = false;
            try {
                jdbcTemplate.execute("CHECKPOINT SYNC");
                checkpointOk = true;
                logger.info("H2 checkpoint completed — database file is up to date on disk");
            } catch (Exception e) {
                logger.error("H2 CHECKPOINT SYNC failed during shutdown — upload may contain stale data", e);
            }

            if (checkpointOk) {
                logger.info("Uploading StudySync database to Google Drive before shutdown");
                gateway.uploadDatabaseToDrive(activeCredential);
            } else {
                logger.warn("Skipping shutdown upload to Google Drive — checkpoint failed, "
                        + "uploading could overwrite newer data on Drive");
            }
        }
    }

    private Credential loadStoredCredential() {
        if (!isIntegrationEnabled()) {
            return null;
        }
        try {
            return credentialManager.loadStoredCredential();
        } catch (IOException e) {
            logger.warn("Cannot load stored Google credentials: {}", e.getMessage());
            return null;
        }
    }
}
