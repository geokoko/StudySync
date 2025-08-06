package com.studysync.domain.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Userinfo;
// TODO: Replace with proper credential storage
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.StringReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for secure Google authentication with Spring integration.
 */
@Service
public class SecureGoogleAuthService {
    
    private static final Logger logger = LoggerFactory.getLogger(SecureGoogleAuthService.class);
    private static final String APPLICATION_NAME = "Study Planner";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Arrays.asList(
        CalendarScopes.CALENDAR_READONLY,
        "openid",
        "email",
        "profile"
    );
    
    private NetHttpTransport httpTransport;
    private Calendar calendarService;
    private String userEmail;
    private boolean isAuthenticated = false;
    
    public SecureGoogleAuthService() {
        try {
            this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            loadStoredCredentials();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Google Auth Service", e);
        }
    }
    
    private void loadStoredCredentials() {
        // TODO: Implement credential storage
        // For now, just set as not authenticated
        this.isAuthenticated = false;
    }
    
    public boolean isAuthenticated() {
        return isAuthenticated && calendarService != null;
    }
    
    public String getUserEmail() {
        return userEmail;
    }
    
    public CompletableFuture<Boolean> authenticateWithUI(Stage parentStage) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        Platform.runLater(() -> {
            try {
                showAuthDialog(parentStage, future);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }
    
    private void showAuthDialog(Stage parentStage, CompletableFuture<Boolean> future) {
        Stage authStage = new Stage();
        authStage.initModality(Modality.APPLICATION_MODAL);
        authStage.initOwner(parentStage);
        authStage.setTitle("Google Calendar Authentication");
        authStage.setWidth(600);
        authStage.setHeight(500);
        
        VBox root = new VBox(10);
        root.setPadding(new Insets(20));
        
        Label titleLabel = new Label("Google Calendar Setup");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        
        TextArea instructionsArea = new TextArea();
        instructionsArea.setEditable(false);
        instructionsArea.setPrefRowCount(8);
        instructionsArea.setText("""
            To connect your Google Calendar, you need to set up Google OAuth credentials:
            
            1. Go to https://console.developers.google.com/
            2. Create a new project or select an existing one
            3. Enable the Google Calendar API
            4. Go to 'Credentials' and create OAuth 2.0 Client ID
            5. Set Application type to 'Desktop application'
            6. Add http://localhost:8888 to authorized redirect URIs
            7. Download the credentials JSON file
            8. Copy the Client ID and Client Secret from the JSON file
            """);
        
        Label clientIdLabel = new Label("Client ID:");
        TextField clientIdField = new TextField();
        clientIdField.setPromptText("Your Google OAuth Client ID");
        
        Label clientSecretLabel = new Label("Client Secret:");
        PasswordField clientSecretField = new PasswordField();
        clientSecretField.setPromptText("Your Google OAuth Client Secret");
        
        Button authenticateButton = new Button("Authenticate with Google");
        authenticateButton.setStyle("-fx-background-color: #4285f4; -fx-text-fill: white; -fx-font-weight: bold;");
        authenticateButton.setOnAction(e -> {
            String clientId = clientIdField.getText().trim();
            String clientSecret = clientSecretField.getText().trim();
            
            if (clientId.isEmpty() || clientSecret.isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Missing Credentials", 
                    "Please enter both Client ID and Client Secret.");
                return;
            }
            
            authenticateButton.setDisable(true);
            authenticateButton.setText("Authenticating...");
            
            CompletableFuture.supplyAsync(() -> performOAuthFlow(clientId, clientSecret))
                .thenAccept(success -> Platform.runLater(() -> {
                    if (success) {
                        authStage.close();
                        future.complete(true);
                        showAlert(Alert.AlertType.INFORMATION, "Authentication Successful", 
                            "Successfully connected to Google Calendar!");
                    } else {
                        authenticateButton.setDisable(false);
                        authenticateButton.setText("Authenticate with Google");
                        future.complete(false);
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        authenticateButton.setDisable(false);
                        authenticateButton.setText("Authenticate with Google");
                        showAlert(Alert.AlertType.ERROR, "Authentication Failed", 
                            "Failed to authenticate: " + ex.getMessage());
                        future.complete(false);
                    });
                    return null;
                });
        });
        
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> {
            authStage.close();
            future.complete(false);
        });
        
        root.getChildren().addAll(titleLabel, instructionsArea, clientIdLabel, clientIdField, 
                                clientSecretLabel, clientSecretField, authenticateButton, cancelButton);
        
        Scene scene = new Scene(root);
        authStage.setScene(scene);
        authStage.show();
    }
    
    private boolean performOAuthFlow(String clientId, String clientSecret) {
        try {
            GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
            details.setClientId(clientId);
            details.setClientSecret(clientSecret);
            details.setAuthUri("https://accounts.google.com/o/oauth2/auth");
            details.setTokenUri("https://oauth2.googleapis.com/token");
            details.setRedirectUris(Arrays.asList("http://localhost:8888"));
            
            GoogleClientSecrets clientSecrets = new GoogleClientSecrets();
            clientSecrets.setInstalled(details);
            
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setAccessType("offline")
                .build();
            
            String authUrl = flow.newAuthorizationUrl()
                .setRedirectUri("http://localhost:8888")
                .build();
            
            // Open browser for authentication
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(authUrl));
            
            // Start local server to receive the callback
            SimpleHttpServer server = new SimpleHttpServer(8888);
            String authCode = server.waitForAuthCode();
            
            if (authCode == null) {
                return false;
            }
            
            TokenResponse tokenResponse = flow.newTokenRequest(authCode)
                .setRedirectUri("http://localhost:8888")
                .execute();
            
            GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(httpTransport)
                .setJsonFactory(JSON_FACTORY)
                .setClientSecrets(clientId, clientSecret)
                .build()
                .setFromTokenResponse(tokenResponse);
            
            // Get user info
            Oauth2 oauth2 = new Oauth2.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
            Userinfo userinfo = oauth2.userinfo().get().execute();
            this.userEmail = userinfo.getEmail();
            
            // Create calendar service
            this.calendarService = new Calendar.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
            
            // Store encrypted credentials
            LocalDateTime expiry = tokenResponse.getExpiresInSeconds() != null ?
                LocalDateTime.now().plusSeconds(tokenResponse.getExpiresInSeconds()) : null;
                
            // TODO: Implement credential storage
            // saveGoogleCredentials(...)
            
            this.isAuthenticated = true;
            return true;
            
        } catch (Exception e) {
            logger.error("OAuth flow failed", e);
            return false;
        }
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    public void logout() {
        // TODO: Implement credential deletion
        // deleteGoogleCredentials();
        this.calendarService = null;
        this.isAuthenticated = false;
        this.userEmail = null;
    }
    
    public Calendar getCalendarService() {
        return calendarService;
    }
    
    // Simple HTTP server to receive OAuth callback
    private static class SimpleHttpServer {
        private final int port;
        private com.sun.net.httpserver.HttpServer server;
        private String authCode;
        private final Object lock = new Object();
        
        public SimpleHttpServer(int port) {
            this.port = port;
        }
        
        public String waitForAuthCode() {
            try {
                server = com.sun.net.httpserver.HttpServer.create(
                    new java.net.InetSocketAddress(port), 0);
                server.createContext("/", exchange -> {
                    String query = exchange.getRequestURI().getQuery();
                    if (query != null && query.contains("code=")) {
                        String[] params = query.split("&");
                        for (String param : params) {
                            if (param.startsWith("code=")) {
                                synchronized (lock) {
                                    authCode = URLDecoder.decode(
                                        param.substring(5), StandardCharsets.UTF_8);
                                    lock.notify();
                                }
                                break;
                            }
                        }
                    }
                    
                    String response = "Authentication successful! You can close this window.";
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                });
                
                server.start();
                
                synchronized (lock) {
                    lock.wait(60000); // Wait up to 1 minute
                }
                
                return authCode;
            } catch (Exception e) {
                logger.error("Failed to start callback server", e);
                return null;
            } finally {
                if (server != null) {
                    server.stop(0);
                }
            }
        }
    }
}