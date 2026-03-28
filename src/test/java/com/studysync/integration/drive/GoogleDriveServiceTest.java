package com.studysync.integration.drive;

import com.google.api.client.auth.oauth2.Credential;
import com.studysync.config.DatabaseReloadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleDriveServiceTest {

    private GoogleDriveService googleDriveService;
    private GoogleDriveGateway gateway;
    private DatabaseReloadService databaseReloadService;
    private Credential activeCredential;

    @BeforeEach
    void setUp() throws Exception {
        GoogleDriveSettings settings = new GoogleDriveSettings(
                true,
                "client-id",
                "client-secret",
                8888,
                "StudySync",
                "StudySync",
                "studysync.mv.db",
                Path.of("build", "tmp", "test-drive", "studysync.mv.db"),
                Path.of("build", "tmp", "test-drive", "credentials"));
        GoogleCredentialManager credentialManager = mock(GoogleCredentialManager.class);
        gateway = mock(GoogleDriveGateway.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        databaseReloadService = mock(DatabaseReloadService.class);

        when(credentialManager.loadStoredCredential()).thenReturn(null);

        googleDriveService = new GoogleDriveService(
                settings,
                credentialManager,
                gateway,
                jdbcTemplate,
                databaseReloadService);

        activeCredential = mock(Credential.class);
        setPrivateField(googleDriveService, "activeCredential", activeCredential);
    }

    @Test
    void onShutdownShutsDownDatabaseBeforeUploading() {
        googleDriveService.setShutdownSaveEnabled(true);

        googleDriveService.onShutdown();

        InOrder inOrder = inOrder(databaseReloadService, gateway);
        inOrder.verify(databaseReloadService).shutdown();
        inOrder.verify(gateway).uploadDatabaseToDrive(activeCredential);
    }

    @Test
    void onShutdownSkipsUploadWhenDatabaseShutdownFails() {
        googleDriveService.setShutdownSaveEnabled(true);
        doThrow(new RuntimeException("boom")).when(databaseReloadService).shutdown();

        googleDriveService.onShutdown();

        verify(gateway, never()).uploadDatabaseToDrive(activeCredential);
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
