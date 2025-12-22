package com.studysync.config;

import com.studysync.integration.drive.GoogleCredentialManager;
import com.studysync.integration.drive.GoogleDriveContextHolder;
import com.studysync.integration.drive.GoogleDriveGateway;
import com.studysync.integration.drive.GoogleDriveSettings;
import com.studysync.integration.drive.GoogleDriveSettingsLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the Google Drive configuration as a Spring bean.
 */
@Configuration
public class GoogleDriveConfiguration {

    @Bean
    public GoogleDriveSettings googleDriveSettings() {
        GoogleDriveSettings settings = GoogleDriveContextHolder.get();
        if (settings != null) {
            return settings;
        }
        // Fallback for tests or when the bootstrap path was skipped
        return GoogleDriveSettingsLoader.load();
    }

    @Bean
    public GoogleCredentialManager googleCredentialManager(GoogleDriveSettings settings) {
        return new GoogleCredentialManager(settings);
    }

    @Bean
    public GoogleDriveGateway googleDriveGateway(GoogleDriveSettings settings, GoogleCredentialManager credentialManager) {
        return new GoogleDriveGateway(settings, credentialManager);
    }
}
