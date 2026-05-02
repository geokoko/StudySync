package com.studysync.integration.drive;

import com.google.api.client.auth.oauth2.Credential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleDriveServiceTest {

    private GoogleDriveService googleDriveService;
    private GoogleDriveGateway gateway;
    private DataSource dataSource;
    private Credential activeCredential;
    private Path localDatabasePath;

    @BeforeEach
    void setUp() throws Exception {
        localDatabasePath = Files.createTempDirectory("studysync-drive-test").resolve("studysync.mv.db");
        Files.writeString(localDatabasePath, "initial");

        GoogleDriveSettings settings = new GoogleDriveSettings(
                true,
                "client-id",
                "client-secret",
                8888,
                "StudySync",
                "StudySync",
                "studysync.mv.db",
                localDatabasePath,
                localDatabasePath.getParent().resolve("credentials"));
        GoogleCredentialManager credentialManager = mock(GoogleCredentialManager.class);
        gateway = mock(GoogleDriveGateway.class);
        dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);

        when(credentialManager.loadStoredCredential()).thenReturn(null);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        googleDriveService = new GoogleDriveService(settings, credentialManager, gateway, dataSource);

        activeCredential = mock(Credential.class);
        setPrivateField(googleDriveService, "activeCredential", activeCredential);
    }

    @Test
    void uploadDatabaseSnapshotAbortsWhenLocalFileStillLooksStale() throws Exception {
        Files.setLastModifiedTime(localDatabasePath, FileTime.from(Instant.now().minusSeconds(120)));
        setPrivateField(googleDriveService, "lastLocalMutationAt", System.currentTimeMillis());

        boolean uploaded = googleDriveService.uploadDatabaseSnapshot();

        assertEquals(false, uploaded);
        verify(gateway, never()).uploadDatabaseToDrive(activeCredential);
    }

    @Test
    void stageDownloadFromDriveWritesPendingDatabaseAndMetadata() throws Exception {
        when(gateway.downloadDatabaseToPath(any(), any())).thenAnswer(invocation -> {
            Path destination = invocation.getArgument(1);
            Files.writeString(destination, "downloaded-db");
            return Optional.of(new RemoteDatabaseSnapshot("drive-file", Files.size(destination), 123456789L));
        });

        boolean staged = googleDriveService.stageDownloadFromDrive();

        assertTrue(staged);
        Path pendingDatabase = PendingDownloadSupport.pendingDatabasePath(localDatabasePath);
        Path metadataPath = PendingDownloadSupport.pendingMetadataPath(localDatabasePath);
        assertTrue(Files.exists(pendingDatabase));
        assertTrue(Files.exists(metadataPath));
        PendingDownloadMetadata metadata = PendingDownloadSupport.readMetadata(metadataPath);
        assertEquals("drive-file", metadata.fileId());
        assertEquals(Files.size(pendingDatabase), metadata.sizeBytes());
        assertEquals(PendingDownloadSupport.sha256Hex(pendingDatabase), metadata.sha256());
    }

    @Test
    void checkSyncStatusReturnsConflictWhenLocalIsDirtyAndDriveIsNewer() throws Exception {
        Files.setLastModifiedTime(localDatabasePath, FileTime.from(Instant.now()));
        setPrivateField(googleDriveService, "localDbDirty", true);
        when(gateway.getRemoteModifiedTime(activeCredential))
                .thenReturn(Optional.of(Instant.now().plusSeconds(120)));

        GoogleDriveService.SyncStatus status = googleDriveService.checkSyncStatus();

        assertEquals(GoogleDriveService.SyncStatus.CONFLICT, status);
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
