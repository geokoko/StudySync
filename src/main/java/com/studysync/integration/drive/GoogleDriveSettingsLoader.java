package com.studysync.integration.drive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Utility responsible for loading Google Drive configuration from disk/environment.
 */
public final class GoogleDriveSettingsLoader {

    private static final Logger logger = LoggerFactory.getLogger(GoogleDriveSettingsLoader.class);
    private static final Path CONFIG_PATH = Paths.get("config", "google", "drive.properties");
    private static final Path USER_CONFIG_PATH = Paths.get(System.getProperty("user.home"), ".config", "studysync", "drive.properties");
    private static final Path DEFAULT_DATABASE = Paths.get("data", "studysync.mv.db");

    private GoogleDriveSettingsLoader() {
    }

    public static GoogleDriveSettings load() {
        Properties properties = new Properties();
        Path loadedPath = null;

        // Try user config first (XDG standard)
        if (Files.exists(USER_CONFIG_PATH)) {
            try (InputStream in = Files.newInputStream(USER_CONFIG_PATH)) {
                properties.load(in);
                loadedPath = USER_CONFIG_PATH;
                logger.info("Loaded configuration from user directory: {}", USER_CONFIG_PATH);
            } catch (IOException e) {
                logger.warn("Failed to read user config {}: {}", USER_CONFIG_PATH, e.getMessage());
            }
        } 
        
        // Fallback to install dir config if user config not found or failed
        if (loadedPath == null && Files.exists(CONFIG_PATH)) {
            try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                properties.load(in);
                loadedPath = CONFIG_PATH;
                logger.info("Loaded configuration from installation directory: {}", CONFIG_PATH);
            } catch (IOException e) {
                logger.warn("Failed to read {}: {}", CONFIG_PATH, e.getMessage());
            }
        }
        
        if (loadedPath == null) {
            logger.info("No configuration file found. Using defaults and environment overrides.");
        }

        boolean enabled = getBoolean("GOOGLE_DRIVE_ENABLED", "google.drive.enabled", properties, false);
        String clientId = getString("GOOGLE_DRIVE_CLIENT_ID", "google.drive.client-id", properties);
        String clientSecret = getString("GOOGLE_DRIVE_CLIENT_SECRET", "google.drive.client-secret", properties);
        int redirectPort = getInt("GOOGLE_DRIVE_REDIRECT_PORT", "google.drive.redirect-port", properties, 8888);
        String applicationName = getString("GOOGLE_DRIVE_APPLICATION_NAME", "google.drive.application-name", properties);
        String folderName = getString("GOOGLE_DRIVE_FOLDER_NAME", "google.drive.folder-name", properties);
        String remoteFileName = getString("GOOGLE_DRIVE_REMOTE_FILE_NAME", "google.drive.remote-file-name", properties);
        Path credentialsDir = resolvePath(getString("GOOGLE_DRIVE_CREDENTIALS_DIR", "google.drive.credentials-dir", properties),
            Paths.get(System.getProperty("user.home"), ".studysync", "google"));
        Path localDatabase = resolvePath(getString("GOOGLE_DRIVE_LOCAL_DB_PATH", "google.drive.local-database-path", properties),
            DEFAULT_DATABASE);

        ensureDirectoryExists(credentialsDir);
        ensureDirectoryExists(localDatabase.getParent());

        if (enabled && (clientId == null || clientSecret == null)) {
            logger.warn("Google Drive sync is enabled but client credentials are missing. Sync will be disabled.");
            enabled = false;
        }

        return new GoogleDriveSettings(enabled, clientId, clientSecret, redirectPort,
            applicationName, folderName, remoteFileName, localDatabase, credentialsDir);
    }

    private static String getString(String envKey, String propertyKey, Properties properties) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }
        String propertyValue = properties.getProperty(propertyKey);
        if (propertyValue == null) {
            return null;
        }
        return propertyValue.trim();
    }

    private static boolean getBoolean(String envKey, String propertyKey, Properties properties, boolean defaultValue) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return Boolean.parseBoolean(envValue);
        }
        String propertyValue = properties.getProperty(propertyKey);
        if (propertyValue == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(propertyValue.trim());
    }

    private static int getInt(String envKey, String propertyKey, Properties properties, int defaultValue) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            try {
                return Integer.parseInt(envValue);
            } catch (NumberFormatException ignored) {
                logger.warn("Invalid integer value '{}' for {}", envValue, envKey);
            }
        }
        String propertyValue = properties.getProperty(propertyKey);
        if (propertyValue == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(propertyValue.trim());
        } catch (NumberFormatException ex) {
            logger.warn("Invalid integer value '{}' for property '{}': {}", propertyValue, propertyKey, ex.getMessage());
            return defaultValue;
        }
    }

    private static Path resolvePath(String configuredValue, Path defaultPath) {
        if (configuredValue == null || configuredValue.isBlank()) {
            return defaultPath;
        }
        String normalized = configuredValue
            .replace("${user.home}", System.getProperty("user.home"))
            .replace("~", System.getProperty("user.home"));
        return Paths.get(normalized).toAbsolutePath();
    }

    private static void ensureDirectoryExists(Path directory) {
        if (directory == null) {
            return;
        }
        try {
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }
        } catch (IOException e) {
            logger.warn("Unable to create directory {}: {}", directory, e.getMessage());
        }
    }
}
