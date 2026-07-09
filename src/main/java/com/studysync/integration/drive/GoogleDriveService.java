package com.studysync.integration.drive;

import com.google.api.client.auth.oauth2.Credential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;

/**
 * High-level service exposed to the rest of the application for Google sign-in and Drive synchronization.
 */
@Service
public class GoogleDriveService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleDriveService.class);
    private static final long SYNC_STATUS_TOLERANCE_SECONDS = 30;
    private static final long UPLOAD_FRESHNESS_TOLERANCE_MS = 5_000;

    private final GoogleDriveSettings settings;
    private final GoogleCredentialManager credentialManager;
    private final GoogleDriveGateway gateway;
    private final DataSource dataSource;
    private Credential activeCredential;
    private String cachedAccountEmail;

    /** Tracks whether the local DB has been modified since the last upload to Drive. */
    private volatile boolean localDbDirty = false;
    private volatile long lastLocalMutationAt = 0L;
    /**
     * Guards paired reads/writes of localDbDirty + lastLocalMutationAt, so the
     * upload thread's compare-and-clear cannot interleave with a concurrent
     * markLocalDbDirty(). Deliberately not the service monitor — that is held
     * for the whole network upload and would stall every mutation.
     */
    private final Object dirtyStateLock = new Object();

    public GoogleDriveService(GoogleDriveSettings settings,
                              GoogleCredentialManager credentialManager,
                              GoogleDriveGateway gateway,
                              DataSource dataSource) {
        this.settings = settings;
        this.credentialManager = credentialManager;
        this.gateway = gateway;
        this.dataSource = dataSource;

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

    public boolean isIntegrationEnabled() {
        return settings != null && settings.isReady();
    }

    public boolean isSignedIn() {
        return activeCredential != null;
    }

    public Optional<String> getSignedInAccountEmail() {
        return Optional.ofNullable(cachedAccountEmail);
    }

    public Path getLocalDatabasePath() {
        Path configured = settings != null ? settings.localDatabasePath() : Path.of("data", "studysync.mv.db");
        return configured.toAbsolutePath();
    }

    public boolean isRestartSupported() {
        return "1".equals(System.getenv("STUDYSYNC_RESTARTABLE"));
    }

    public boolean hasPendingDownload() {
        return Files.exists(PendingDownloadSupport.pendingMetadataPath(getLocalDatabasePath()));
    }

    public boolean hasFailedPendingDownload() {
        return Files.exists(PendingDownloadSupport.failedPendingMetadataPath(getLocalDatabasePath()));
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

    /**
     * Flushes all in-memory H2 data to the .mv.db file on disk and verifies
     * that the file looks fresh enough to contain the latest committed writes.
     */
    public boolean saveLocally() {
        Path localPath = getLocalDatabasePath();
        FileState beforeState = readFileState(localPath);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.execute("CHECKPOINT SYNC");
            }
        } catch (Exception e) {
            logger.error("Local save failed (CHECKPOINT SYNC): {}", e.getMessage(), e);
            return false;
        }

        FileState afterState = readFileState(localPath);
        boolean fresh = verifyLocalDbFreshness(afterState);
        if (fresh) {
            logger.info("Local save completed — file before [exists={}, size={}, mtime={}] after [exists={}, size={}, mtime={}]",
                    beforeState.exists(), beforeState.sizeBytes(), beforeState.lastModified(),
                    afterState.exists(), afterState.sizeBytes(), afterState.lastModified());
            return true;
        }

        logger.error("Local save ran CHECKPOINT SYNC but the database file still looks stale "
                        + "(before [exists={}, size={}, mtime={}]; after [exists={}, size={}, mtime={}]; lastMutationAt={})",
                beforeState.exists(), beforeState.sizeBytes(), beforeState.lastModified(),
                afterState.exists(), afterState.sizeBytes(), afterState.lastModified(),
                lastLocalMutationAt > 0 ? Instant.ofEpochMilli(lastLocalMutationAt) : null);
        return false;
    }

    public synchronized boolean uploadDatabaseSnapshot() {
        if (!isIntegrationEnabled() || activeCredential == null) {
            return false;
        }
        if (!saveLocally()) {
            logger.warn("Aborting Drive upload because the local database could not be verified as fresh");
            return false;
        }

        // Fence against writes that land while the upload is in flight (or after
        // the UI has already timed out waiting for it): only clear the dirty flag
        // if no local mutation happened since the checkpoint we uploaded. The
        // read and the compare-and-clear are atomic w.r.t. markLocalDbDirty()
        // via dirtyStateLock; the network call stays outside the lock.
        long mutationAtUploadStart;
        synchronized (dirtyStateLock) {
            mutationAtUploadStart = lastLocalMutationAt;
        }
        boolean uploaded = gateway.uploadDatabaseToDrive(activeCredential);
        if (uploaded) {
            synchronized (dirtyStateLock) {
                if (lastLocalMutationAt == mutationAtUploadStart) {
                    localDbDirty = false;
                    lastLocalMutationAt = 0L;
                }
            }
        }
        return uploaded;
    }

    public synchronized boolean stageDownloadFromDrive() {
        if (!isIntegrationEnabled() || activeCredential == null) {
            return false;
        }

        Path localPath = getLocalDatabasePath();
        Path partialPath = PendingDownloadSupport.pendingDatabasePartialPath(localPath);
        Path pendingPath = PendingDownloadSupport.pendingDatabasePath(localPath);
        Path metadataPath = PendingDownloadSupport.pendingMetadataPath(localPath);
        Path failedMetadataPath = PendingDownloadSupport.failedPendingMetadataPath(localPath);

        try {
            Files.deleteIfExists(partialPath);
            Optional<RemoteDatabaseSnapshot> snapshot = gateway.downloadDatabaseToPath(activeCredential, partialPath);
            if (snapshot.isEmpty()) {
                return false;
            }

            long sizeBytes = Files.size(partialPath);
            String sha256 = PendingDownloadSupport.sha256Hex(partialPath);
            PendingDownloadSupport.moveReplacing(partialPath, pendingPath);
            PendingDownloadMetadata metadata = new PendingDownloadMetadata(
                    snapshot.get().fileId(),
                    sizeBytes,
                    sha256,
                    snapshot.get().modifiedTimeEpochMillis(),
                    System.currentTimeMillis());
            PendingDownloadSupport.writeMetadata(metadataPath, metadata);
            Files.deleteIfExists(failedMetadataPath);
            logger.info("Staged Google Drive database download at {}", pendingPath);
            return true;
        } catch (Exception e) {
            logger.error("Failed to stage Google Drive database download", e);
            try {
                Files.deleteIfExists(partialPath);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            return false;
        }
    }

    public void markLocalDbDirty() {
        synchronized (dirtyStateLock) {
            this.localDbDirty = true;
            this.lastLocalMutationAt = System.currentTimeMillis();
        }
    }

    public boolean isLocalDbDirty() {
        return localDbDirty;
    }

    public enum SyncStatus {
        DISABLED,
        UP_TO_DATE,
        DRIVE_NEWER,
        LOCAL_NEWER,
        CONFLICT,
        UNKNOWN
    }

    public SyncStatus checkSyncStatus() {
        if (!isIntegrationEnabled() || activeCredential == null) {
            return SyncStatus.DISABLED;
        }
        try {
            Optional<Instant> remoteTime = gateway.getRemoteModifiedTime(activeCredential);
            if (remoteTime.isEmpty()) {
                return localDbDirty ? SyncStatus.LOCAL_NEWER : SyncStatus.UP_TO_DATE;
            }

            Path localPath = getLocalDatabasePath();
            if (!Files.exists(localPath)) {
                return SyncStatus.DRIVE_NEWER;
            }

            Instant localTime = Files.getLastModifiedTime(localPath).toInstant();
            if (localDbDirty && remoteTime.get().isAfter(localTime.plusSeconds(SYNC_STATUS_TOLERANCE_SECONDS))) {
                return SyncStatus.CONFLICT;
            }
            if (localDbDirty) {
                return SyncStatus.LOCAL_NEWER;
            }
            if (remoteTime.get().isAfter(localTime.plusSeconds(SYNC_STATUS_TOLERANCE_SECONDS))) {
                return SyncStatus.DRIVE_NEWER;
            }
            if (localTime.isAfter(remoteTime.get().plusSeconds(SYNC_STATUS_TOLERANCE_SECONDS))) {
                return SyncStatus.LOCAL_NEWER;
            }
            return SyncStatus.UP_TO_DATE;
        } catch (Exception e) {
            logger.warn("Failed to check sync status: {}", e.getMessage());
            return SyncStatus.UNKNOWN;
        }
    }

    private boolean verifyLocalDbFreshness(FileState state) {
        if (!state.exists()) {
            return false;
        }
        if (lastLocalMutationAt <= 0L) {
            return true;
        }
        if (state.lastModified() == null) {
            return false;
        }
        return state.lastModified().toInstant().toEpochMilli() + UPLOAD_FRESHNESS_TOLERANCE_MS >= lastLocalMutationAt;
    }

    private FileState readFileState(Path localPath) {
        try {
            if (!Files.exists(localPath)) {
                return new FileState(false, -1L, null);
            }
            return new FileState(true, Files.size(localPath), Files.getLastModifiedTime(localPath));
        } catch (IOException e) {
            logger.warn("Failed to inspect database file {}: {}", localPath, e.getMessage());
            return new FileState(false, -1L, null);
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

    private record FileState(boolean exists, long sizeBytes, FileTime lastModified) {
    }
}
