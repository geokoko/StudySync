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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleDriveServiceTest {

    private GoogleDriveService googleDriveService;
    private GoogleDriveGateway gateway;
    private GoogleDriveSettings settings;
    private GoogleCredentialManager credentialManager;
    private DataSource dataSource;
    private Credential activeCredential;
    private Path localDatabasePath;

    @BeforeEach
    void setUp() throws Exception {
        localDatabasePath = Files.createTempDirectory("studysync-drive-test").resolve("studysync.mv.db");
        Files.writeString(localDatabasePath, "initial");

        settings = new GoogleDriveSettings(
                true,
                "client-id",
                "client-secret",
                8888,
                "StudySync",
                "StudySync",
                "studysync.mv.db",
                localDatabasePath,
                localDatabasePath.getParent().resolve("credentials"));
        credentialManager = mock(GoogleCredentialManager.class);
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
        Instant knownRemoteTime = Instant.now().minusSeconds(120);
        DriveSyncStateStore.write(localDatabasePath, new DriveSyncState(knownRemoteTime.toEpochMilli(), false));
        Files.setLastModifiedTime(localDatabasePath, FileTime.from(Instant.now()));
        setPrivateField(googleDriveService, "localDbDirty", true);
        when(gateway.getRemoteModifiedTime(activeCredential))
                .thenReturn(Optional.of(Instant.now().plusSeconds(120)));

        GoogleDriveService.SyncStatus status = googleDriveService.checkSyncStatus();

        assertEquals(GoogleDriveService.SyncStatus.CONFLICT, status);
    }

    @Test
    void checkSyncStatusIgnoresLocalFileTimestampWhenStoredDriveRevisionMatches() throws Exception {
        Instant remoteTime = Instant.now().minusSeconds(120);
        Files.setLastModifiedTime(localDatabasePath, FileTime.from(Instant.now().plusSeconds(120)));
        DriveSyncStateStore.write(localDatabasePath, new DriveSyncState(remoteTime.toEpochMilli(), false));
        when(gateway.getRemoteModifiedTime(activeCredential)).thenReturn(Optional.of(remoteTime));

        GoogleDriveService.SyncStatus status = googleDriveService.checkSyncStatus();

        assertEquals(GoogleDriveService.SyncStatus.UP_TO_DATE, status);
    }

    @Test
    void checkSyncStatusReturnsDriveNewerAgainstStoredRevisionDespiteNewLocalTimestamp() throws Exception {
        Instant knownRemoteTime = Instant.now().minusSeconds(240);
        Instant remoteTime = knownRemoteTime.plusMillis(1);
        Files.setLastModifiedTime(localDatabasePath, FileTime.from(Instant.now().plusSeconds(120)));
        DriveSyncStateStore.write(localDatabasePath, new DriveSyncState(knownRemoteTime.toEpochMilli(), false));
        when(gateway.getRemoteModifiedTime(activeCredential)).thenReturn(Optional.of(remoteTime));

        GoogleDriveService.SyncStatus status = googleDriveService.checkSyncStatus();

        assertEquals(GoogleDriveService.SyncStatus.DRIVE_NEWER, status);
    }

    @Test
    void checkSyncStatusIgnoresStartupMaintenanceMutations() throws Exception {
        Instant remoteTime = Instant.now().minusSeconds(120);
        DriveSyncStateStore.write(localDatabasePath, new DriveSyncState(remoteTime.toEpochMilli(), false));
        googleDriveService.runStartupMaintenanceWithoutDirtyTracking(googleDriveService::markLocalDbDirty);
        when(gateway.getRemoteModifiedTime(activeCredential)).thenReturn(Optional.of(remoteTime));

        GoogleDriveService.SyncStatus status = googleDriveService.checkSyncStatus();

        assertEquals(GoogleDriveService.SyncStatus.UP_TO_DATE, status);
    }

    @Test
    void localDirtyStateIsPersistedBeforeFirstDriveRevision() {
        googleDriveService.markLocalDbDirty();

        DriveSyncState state = DriveSyncStateStore.read(localDatabasePath).orElseThrow();
        assertEquals(null, state.remoteModifiedTimeEpochMillis());
        assertTrue(state.localChangesPending());
    }

    @Test
    void localDirtyStateSurvivesServiceRestart() throws Exception {
        Instant remoteTime = Instant.now().minusSeconds(120);
        DriveSyncStateStore.write(localDatabasePath,
                new DriveSyncState(remoteTime.toEpochMilli(), false));

        googleDriveService.markLocalDbDirty();

        assertTrue(DriveSyncStateStore.read(localDatabasePath).orElseThrow().localChangesPending());
        GoogleDriveService restartedService = new GoogleDriveService(
                settings, credentialManager, gateway, dataSource);
        setPrivateField(restartedService, "activeCredential", activeCredential);
        when(gateway.getRemoteModifiedTime(activeCredential)).thenReturn(Optional.of(remoteTime));
        assertTrue(restartedService.isLocalDbDirty());
        assertEquals(GoogleDriveService.SyncStatus.LOCAL_NEWER, restartedService.checkSyncStatus());
    }

    @Test
    void successfulUploadRecordsDriveRevisionAndClearsPersistedDirtyState() throws Exception {
        Instant previousRemoteTime = Instant.now().minusSeconds(120);
        Instant uploadedRemoteTime = Instant.now();
        DriveSyncStateStore.write(localDatabasePath,
                new DriveSyncState(previousRemoteTime.toEpochMilli(), false));
        googleDriveService.markLocalDbDirty();
        when(gateway.uploadDatabaseToDrive(activeCredential)).thenReturn(true);
        when(gateway.getRemoteModifiedTime(activeCredential)).thenReturn(Optional.of(uploadedRemoteTime));

        boolean uploaded = googleDriveService.uploadDatabaseSnapshot();

        DriveSyncState state = DriveSyncStateStore.read(localDatabasePath).orElseThrow();
        assertTrue(uploaded);
        assertEquals(uploadedRemoteTime.toEpochMilli(), state.remoteModifiedTimeEpochMillis());
        assertFalse(state.localChangesPending());
        assertFalse(googleDriveService.isLocalDbDirty());
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
