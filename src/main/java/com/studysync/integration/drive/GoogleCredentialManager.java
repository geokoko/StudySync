package com.studysync.integration.drive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.oauth2.Oauth2Scopes;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Objects;

/**
 * Handles OAuth 2.0 flows and stored credentials for Google Drive access.
 */
public class GoogleCredentialManager {

    private static final Logger logger = LoggerFactory.getLogger(GoogleCredentialManager.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = List.of(
        DriveScopes.DRIVE_FILE,
        Oauth2Scopes.USERINFO_EMAIL,
        Oauth2Scopes.USERINFO_PROFILE
    );

    private final GoogleDriveSettings settings;
    private final NetHttpTransport httpTransport;
    private final FileDataStoreFactory dataStoreFactory;

    public GoogleCredentialManager(GoogleDriveSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        try {
            this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            this.dataStoreFactory = new FileDataStoreFactory(settings.credentialsDirectory().toFile());
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Unable to initialize Google OAuth components", e);
        }
    }

    public boolean hasStoredCredential() {
        try {
            return buildFlow().loadCredential("user") != null;
        } catch (IOException e) {
            logger.warn("Failed to load stored Google credentials: {}", e.getMessage());
            return false;
        }
    }

    public Credential loadStoredCredential() throws IOException {
        return buildFlow().loadCredential("user");
    }

    public Credential authorizeInteractively() throws IOException {
        GoogleAuthorizationCodeFlow flow = buildFlow();
        try (LocalServerReceiver receiver = new LocalServerReceiver.Builder()
            .setPort(settings.redirectPort())
            .build()) {
            return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        }
    }

    public void clearStoredCredentials() {
        try {
            dataStoreFactory.getDataStore("StoredCredential").delete("user");
        } catch (IOException e) {
            logger.warn("Failed to clear stored Google credentials: {}", e.getMessage());
        }
    }

    public NetHttpTransport httpTransport() {
        return httpTransport;
    }

    public JsonFactory jsonFactory() {
        return JSON_FACTORY;
    }

    private GoogleAuthorizationCodeFlow buildFlow() throws IOException {
        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
        details.setClientId(settings.clientId());
        details.setClientSecret(settings.clientSecret());

        GoogleClientSecrets secrets = new GoogleClientSecrets();
        secrets.setInstalled(details);

        return new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, secrets, SCOPES)
            .setDataStoreFactory(dataStoreFactory)
            .setAccessType("offline")
            .build();
    }
}
