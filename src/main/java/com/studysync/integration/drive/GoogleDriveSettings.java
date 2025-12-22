package com.studysync.integration.drive;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Simple value object holding the Google Drive integration configuration.
 */
public final class GoogleDriveSettings {

    private final boolean enabled;
    private final String clientId;
    private final String clientSecret;
    private final int redirectPort;
    private final String applicationName;
    private final String folderName;
    private final String remoteFileName;
    private final Path localDatabasePath;
    private final Path credentialsDirectory;

    public GoogleDriveSettings(boolean enabled,
                               String clientId,
                               String clientSecret,
                               int redirectPort,
                               String applicationName,
                               String folderName,
                               String remoteFileName,
                               Path localDatabasePath,
                               Path credentialsDirectory) {
        this.enabled = enabled;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectPort = redirectPort;
        this.applicationName = applicationName != null ? applicationName : "StudySync";
        this.folderName = folderName != null ? folderName : "StudySync";
        this.remoteFileName = remoteFileName != null ? remoteFileName : "studysync.mv.db";
        this.localDatabasePath = Objects.requireNonNull(localDatabasePath, "localDatabasePath");
        this.credentialsDirectory = Objects.requireNonNull(credentialsDirectory, "credentialsDirectory");
    }

    public boolean enabled() {
        return enabled;
    }

    public String clientId() {
        return clientId;
    }

    public String clientSecret() {
        return clientSecret;
    }

    public int redirectPort() {
        return redirectPort;
    }

    public String applicationName() {
        return applicationName;
    }

    public String folderName() {
        return folderName;
    }

    public String remoteFileName() {
        return remoteFileName;
    }

    public Path localDatabasePath() {
        return localDatabasePath;
    }

    public Path credentialsDirectory() {
        return credentialsDirectory;
    }

    /**
     * @return {@code true} when OAuth credentials are fully configured and Drive sync may be used.
     */
    public boolean isReady() {
        return enabled
            && clientId != null && !clientId.isBlank()
            && clientSecret != null && !clientSecret.isBlank();
    }

    public static GoogleDriveSettings disabled(Path localDatabasePath) {
        return new GoogleDriveSettings(false, null, null, 8888,
            "StudySync", "StudySync", "studysync.mv.db", localDatabasePath,
            Path.of(System.getProperty("user.home"), ".studysync", "google"));
    }
}
