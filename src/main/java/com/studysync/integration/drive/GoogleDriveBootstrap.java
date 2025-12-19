package com.studysync.integration.drive;

import com.google.api.client.auth.oauth2.Credential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Runs before Spring Boot starts to make sure the local database is in sync with Google Drive if possible.
 */
public final class GoogleDriveBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(GoogleDriveBootstrap.class);

    private GoogleDriveBootstrap() {
    }

    public static GoogleDriveSettings initialize() {
        GoogleDriveSettings settings = GoogleDriveSettingsLoader.load();
        GoogleDriveContextHolder.set(settings);

        if (!settings.isReady()) {
            logger.info("Google Drive sync is disabled or not fully configured.");
            return settings;
        }

        GoogleCredentialManager credentialManager = new GoogleCredentialManager(settings);
        try {
            Credential storedCredential = credentialManager.loadStoredCredential();
            if (storedCredential == null) {
                logger.info("No stored Google credentials. Sign in from the UI to enable Drive sync.");
                return settings;
            }
            // Automatic download on startup is disabled.
            logger.info("Google Drive sync enabled. Database download must be triggered manually.");
        } catch (IOException e) {
            logger.warn("Unable to load stored Google credentials: {}", e.getMessage());
        }
        return settings;
    }
}
