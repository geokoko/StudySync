package com.studysync.integration.drive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Userinfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Low level helper that communicates with Google Drive/OAuth APIs.
 */
public class GoogleDriveGateway {

    private static final Logger logger = LoggerFactory.getLogger(GoogleDriveGateway.class);

    private final GoogleDriveSettings settings;
    private final GoogleCredentialManager credentialManager;

    public GoogleDriveGateway(GoogleDriveSettings settings, GoogleCredentialManager credentialManager) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.credentialManager = Objects.requireNonNull(credentialManager, "credentialManager");
    }

    public Optional<String> fetchAccountEmail(Credential credential) {
        try {
            Oauth2 oauth2 = new Oauth2.Builder(credentialManager.httpTransport(), credentialManager.jsonFactory(), credential)
                .setApplicationName(settings.applicationName())
                .build();
            Userinfo info = oauth2.userinfo().get().execute();
            return Optional.ofNullable(info.getEmail());
        } catch (IOException e) {
            logger.warn("Unable to fetch Google account info: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public boolean downloadDatabaseFromDrive(Credential credential) {
        if (credential == null) {
            return false;
        }
        try {
            Drive drive = buildDriveClient(credential);
            Optional<String> folderId = ensureAppFolder(drive);
            if (folderId.isEmpty()) {
                logger.info("Google Drive folder '{}' not found and could not be created", settings.folderName());
                return false;
            }
            Optional<File> databaseFile = findDatabaseFile(drive, folderId.get());
            if (databaseFile.isEmpty()) {
                logger.info("No existing StudySync database found in Google Drive. A new one will be created on upload.");
                return false;
            }

            Path localPath = settings.localDatabasePath();
            if (localPath.getParent() != null) {
                Files.createDirectories(localPath.getParent());
            }
            Path tempFile = Files.createTempFile("studysync-drive", ".tmp");
            try (OutputStream outputStream = Files.newOutputStream(tempFile)) {
                drive.files().get(databaseFile.get().getId()).executeMediaAndDownloadTo(outputStream);
            }
            Files.move(tempFile, localPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Downloaded latest StudySync database from Google Drive to {}", localPath);
            return true;
        } catch (IOException e) {
            logger.warn("Failed to download StudySync database from Google Drive: {}", e.getMessage());
            return false;
        }
    }

    public boolean uploadDatabaseToDrive(Credential credential) {
        if (credential == null) {
            return false;
        }
        Path localPath = settings.localDatabasePath();
        if (!Files.exists(localPath)) {
            logger.info("Local StudySync database not found at {}. Nothing to upload.", localPath);
            return false;
        }
        try {
            Drive drive = buildDriveClient(credential);
            Optional<String> folderId = ensureAppFolder(drive);
            if (folderId.isEmpty()) {
                return false;
            }

            Optional<File> existingFile = findDatabaseFile(drive, folderId.get());
            FileContent mediaContent = new FileContent("application/octet-stream", localPath.toFile());
            if (existingFile.isPresent()) {
                drive.files().update(existingFile.get().getId(), null, mediaContent)
                    .setFields("id")
                    .execute();
                logger.info("Updated StudySync database on Google Drive (file id={})", existingFile.get().getId());
            } else {
                File metadata = new File();
                metadata.setName(settings.remoteFileName());
                metadata.setParents(Collections.singletonList(folderId.get()));
                drive.files().create(metadata, mediaContent)
                    .setFields("id")
                    .execute();
                logger.info("Uploaded StudySync database to Google Drive folder '{}'", settings.folderName());
            }
            return true;
        } catch (IOException e) {
            logger.warn("Failed to upload StudySync database to Google Drive: {}", e.getMessage());
            return false;
        }
    }

    private Drive buildDriveClient(Credential credential) {
        return new Drive.Builder(credentialManager.httpTransport(), credentialManager.jsonFactory(), credential)
            .setApplicationName(settings.applicationName())
            .build();
    }

    private Optional<String> ensureAppFolder(Drive drive) throws IOException {
        FileList folderList = drive.files().list()
            .setQ(String.format("mimeType='application/vnd.google-apps.folder' and name='%s' and trashed=false", settings.folderName()))
            .setFields("files(id, name)")
            .setPageSize(1)
            .execute();
        if (folderList.getFiles() != null && !folderList.getFiles().isEmpty()) {
            return Optional.of(folderList.getFiles().get(0).getId());
        }

        File fileMetadata = new File();
        fileMetadata.setName(settings.folderName());
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        fileMetadata.setParents(List.of("root"));
        File createdFolder = drive.files().create(fileMetadata)
            .setFields("id")
            .execute();
        return Optional.ofNullable(createdFolder.getId());
    }

    private Optional<File> findDatabaseFile(Drive drive, String folderId) throws IOException {
        String query = String.format("name='%s' and '%s' in parents and trashed=false", settings.remoteFileName(), folderId);
        FileList fileList = drive.files().list()
            .setQ(query)
            .setFields("files(id, name, modifiedTime)")
            .setPageSize(1)
            .execute();
        if (fileList.getFiles() == null || fileList.getFiles().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(fileList.getFiles().get(0));
    }
}
