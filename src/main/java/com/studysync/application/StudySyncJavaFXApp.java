package com.studysync.application;

import com.studysync.StudySyncApplication;
import com.studysync.integration.drive.GoogleDriveService;
import com.studysync.presentation.ui.StudySyncUI;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * JavaFX Application class that integrates with Spring Boot dependency injection.
 */
public class StudySyncJavaFXApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(StudySyncJavaFXApp.class);

    private ConfigurableApplicationContext springContext;
    private volatile boolean shutdownInProgress;

    @Override
    public void init() {
        logger.info("Initializing JavaFX application with Spring integration");
        this.springContext = StudySyncApplication.getSpringContext();
        loadCustomFonts();
    }

    private void loadCustomFonts() {
        String[] fontPaths = {
                "/fonts/Roboto-Regular.ttf",
                "/fonts/Roboto-Bold.ttf",
                "/fonts/NotoEmoji-Regular.ttf"
        };

        for (String fontPath : fontPaths) {
            try (InputStream fontStream = getClass().getResourceAsStream(fontPath)) {
                if (fontStream != null) {
                    Font loadedFont = Font.loadFont(fontStream, 12);
                    if (loadedFont != null) {
                        logger.info("Loaded font: {} ({})", loadedFont.getName(), fontPath);
                    } else {
                        logger.warn("Failed to load font from: {}", fontPath);
                    }
                } else {
                    logger.warn("Font resource not found: {}", fontPath);
                }
            } catch (Exception e) {
                logger.warn("Error loading font {}: {}", fontPath, e.getMessage());
            }
        }
    }

    @Override
    public void start(final Stage primaryStage) {
        try {
            logger.info("Starting StudySync JavaFX application");

            primaryStage.setTitle("📚 StudySync - Loading...");
            primaryStage.setWidth(800);
            primaryStage.setHeight(600);
            primaryStage.centerOnScreen();
            primaryStage.show();

            Platform.runLater(() -> {
                try {
                    final StudySyncUI studySyncUI = springContext.getBean(StudySyncUI.class);
                    studySyncUI.start(primaryStage);
                    logger.info("StudySync application started successfully");
                } catch (final Exception e) {
                    logger.error("Failed to initialize StudySync UI", e);
                    Platform.exit();
                    StudySyncApplication.shutdown();
                }
            });

            primaryStage.setOnCloseRequest(event -> {
                logger.info("Application close requested, initiating shutdown check");
                event.consume();
                handleCloseRequest(primaryStage);
            });
        } catch (final Exception e) {
            logger.error("Failed to start JavaFX application", e);
            Platform.exit();
            StudySyncApplication.shutdown();
        }
    }

    private void handleCloseRequest(Stage primaryStage) {
        if (shutdownInProgress) {
            return;
        }

        try {
            GoogleDriveService driveService = springContext.getBean(GoogleDriveService.class);

            if (!driveService.isSignedIn()) {
                if (driveService.saveLocally()) {
                    Platform.exit();
                } else {
                    showErrorAlert(primaryStage, "Unable to save the local database before exit. The app will stay open.");
                }
                return;
            }

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.initOwner(primaryStage);
            alert.setTitle("Exit StudySync");
            alert.setHeaderText("How would you like to exit?");
            alert.setContentText("Choose how to save your data before closing.");

            ButtonType buttonDrive = new ButtonType("Push to Drive & Exit");
            ButtonType buttonLocal = new ButtonType("Save Locally & Exit");
            ButtonType buttonNoSave = new ButtonType("Exit without Saving");
            ButtonType buttonCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(buttonDrive, buttonLocal, buttonNoSave, buttonCancel);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isEmpty() || result.get() == buttonCancel) {
                logger.info("User cancelled exit");
                return;
            }

            if (result.get() == buttonDrive) {
                logger.info("User chose to push to Drive and exit");
                uploadToDriveAndExit(primaryStage, driveService);
            } else if (result.get() == buttonLocal) {
                logger.info("User chose to save locally and exit");
                if (driveService.saveLocally()) {
                    Platform.exit();
                } else {
                    showErrorAlert(primaryStage, "Local save failed. StudySync will stay open so you can retry.");
                }
            } else if (result.get() == buttonNoSave) {
                logger.info("User chose to exit without saving");
                Platform.exit();
            }
        } catch (Exception e) {
            logger.error("Error during close request handling", e);
            showErrorAlert(primaryStage, "Unexpected error while closing the app. StudySync will stay open.");
        }
    }

    private void uploadToDriveAndExit(Stage primaryStage, GoogleDriveService driveService) {
        shutdownInProgress = true;
        setRootDisabled(primaryStage, true);

        Alert progressAlert = new Alert(Alert.AlertType.INFORMATION);
        progressAlert.initOwner(primaryStage);
        progressAlert.setTitle("StudySync");
        progressAlert.setHeaderText("Uploading database to Google Drive");
        progressAlert.setContentText("StudySync will close after the upload completes.");
        progressAlert.getButtonTypes().clear();
        progressAlert.show();

        // The timeout backstop guarantees whenComplete fires even if the upload
        // hangs, so the disabled UI and button-less progress alert always recover.
        CompletableFuture.supplyAsync(driveService::uploadDatabaseSnapshot)
                .orTimeout(3, TimeUnit.MINUTES)
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    progressAlert.close();
                    shutdownInProgress = false;
                    setRootDisabled(primaryStage, false);

                    if (error != null) {
                        logger.error("Drive upload failed during shutdown", error);
                        showErrorAlert(primaryStage, "Google Drive upload failed. StudySync will stay open.");
                        return;
                    }
                    if (!Boolean.TRUE.equals(result)) {
                        showErrorAlert(primaryStage, "Google Drive upload failed. StudySync will stay open.");
                        return;
                    }

                    Platform.exit();
                }));
    }

    private void setRootDisabled(Stage primaryStage, boolean disabled) {
        if (primaryStage.getScene() == null) {
            return;
        }
        Parent root = primaryStage.getScene().getRoot();
        if (root == null) {
            return;
        }
        root.setDisable(disabled);
        root.setCursor(disabled ? Cursor.WAIT : Cursor.DEFAULT);
    }

    private void showErrorAlert(Stage owner, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setTitle("StudySync");
        alert.setHeaderText("Action failed");
        alert.setContentText(message);
        alert.show();
    }

    @Override
    public void stop() {
        logger.info("Stopping JavaFX application and cleaning up resources");
        StudySyncApplication.shutdown();
    }
}
