package com.studysync.integration.drive;

import com.google.api.client.auth.oauth2.Credential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * High-level service exposed to the rest of the application for Google sign-in and Drive synchronization.
 */
@Service
public class GoogleDriveService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleDriveService.class);

    private final GoogleDriveSettings settings;
    private final GoogleCredentialManager credentialManager;
    private final GoogleDriveGateway gateway;
    private Credential activeCredential;
    private String cachedAccountEmail;

    public GoogleDriveService(GoogleDriveSettings settings) {
        this.settings = settings;
        if (settings != null && settings.isReady()) {
            this.credentialManager = new GoogleCredentialManager(settings);
            this.gateway = new GoogleDriveGateway(settings, credentialManager);
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
        } else {
            this.credentialManager = null;
            this.gateway = null;
        }
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
        return gateway.uploadDatabaseToDrive(activeCredential);
    }

    public synchronized boolean downloadDatabaseSnapshot() {
        if (!isIntegrationEnabled() || activeCredential == null) {
            return false;
        }
        return gateway.downloadDatabaseFromDrive(activeCredential);
    }

    @PreDestroy
    public void onShutdown() {
        if (isIntegrationEnabled() && activeCredential != null) {
            logger.info("Uploading StudySync database to Google Drive before shutdown");
            gateway.uploadDatabaseToDrive(activeCredential);
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
