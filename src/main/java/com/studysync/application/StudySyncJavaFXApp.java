package com.studysync.application;

import com.studysync.StudySyncApplication;
import com.studysync.integration.drive.GoogleDriveService;
import com.studysync.presentation.ui.StudySyncUI;
import javafx.application.Application;
import javafx.application.Platform;
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

/**
 * JavaFX Application class that integrates with Spring Boot dependency injection.
 * 
 * <p>This class extends JavaFX's {@link Application} class and serves as the bridge
 * between the JavaFX application lifecycle and Spring-managed components. It handles
 * the initialization, startup, and shutdown phases of the desktop UI.</p>
 * 
 * <p>The application lifecycle follows this sequence:
 * <ol>
 *   <li>{@link #init()} - Retrieves the Spring context</li>
 *   <li>{@link #start(Stage)} - Initializes and displays the main UI</li>
 *   <li>{@link #stop()} - Performs cleanup when application closes</li>
 * </ol></p>
 * 
 * <p><strong>Thread Safety:</strong> This class is designed to be used by JavaFX's
 * Application Thread and should not be instantiated directly.</p>
 * 
 * @author geokoko
 * @version 0.1.0-BETA
 * @since 0.1.0
 * @see StudySyncApplication
 * @see StudySyncUI
 */
public class StudySyncJavaFXApp extends Application {
    
    /** Logger for this class. */
    private static final Logger logger = LoggerFactory.getLogger(StudySyncJavaFXApp.class);
    
    /** The Spring application context for dependency injection. */
    private ConfigurableApplicationContext springContext;
    
    /**
     * Initializes the JavaFX application before the primary stage is created.
     * 
     * <p>This method is called by the JavaFX runtime during application startup.
     * It retrieves the Spring application context that was initialized by
     * {@link StudySyncApplication#main(String[])}.</p>
     * 
     * @throws IllegalStateException if the Spring context is not available
     */
    @Override
    public void init() {
        logger.info("Initializing JavaFX application with Spring integration");
        this.springContext = StudySyncApplication.getSpringContext();
        
        // Load bundled fonts for cross-platform consistency
        loadCustomFonts();
    }
    
    /**
     * Loads custom fonts bundled with the application.
     * This ensures consistent rendering across different platforms.
     */
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
    
    /**
     * Starts the JavaFX application and displays the main user interface.
     * 
     * <p>This method creates the main UI by retrieving the {@link StudySyncUI}
     * component from the Spring context and delegating the UI initialization to it.
     * It also sets up proper shutdown handling to ensure Spring resources are
     * cleaned up when the application closes.</p>
     * 
     * @param primaryStage the primary stage for the JavaFX application
     * @throws RuntimeException if the UI fails to initialize
     */
    @Override
    public void start(final Stage primaryStage) {
        try {
            logger.info("Starting StudySync JavaFX application");
            
            // Show a splash screen with reasonable size immediately
            primaryStage.setTitle("📚 StudySync - Loading...");
            primaryStage.setWidth(800);
            primaryStage.setHeight(600);
            primaryStage.centerOnScreen();
            primaryStage.show();
            
            // Initialize UI asynchronously to improve perceived startup time
            Platform.runLater(() -> {
                try {
                    // Get StudySyncUI bean from Spring context
                    final StudySyncUI studySyncUI = springContext.getBean(StudySyncUI.class);
                    studySyncUI.start(primaryStage);
                    
                    logger.info("StudySync application started successfully");
                } catch (final Exception e) {
                    logger.error("Failed to initialize StudySync UI", e);
                    Platform.exit();
                    StudySyncApplication.shutdown();
                }
            });
            
            // Ensure proper cleanup when window is closed
            primaryStage.setOnCloseRequest(event -> {
                logger.info("Application close requested, initiating shutdown check");
                try {
                    GoogleDriveService driveService = springContext.getBean(GoogleDriveService.class);

                    if (driveService.isSignedIn()) {
                        event.consume(); // Prevent immediate close

                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                        alert.setTitle("Sync to Google Drive");
                        alert.setHeaderText("Save changes to Google Drive?");
                        alert.setContentText("Do you want to upload your latest data to Google Drive before exiting?");

                        ButtonType buttonSave = new ButtonType("Save & Exit");
                        ButtonType buttonExit = new ButtonType("Exit Only");
                        ButtonType buttonCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

                        alert.getButtonTypes().setAll(buttonSave, buttonExit, buttonCancel);

                        Optional<ButtonType> result = alert.showAndWait();

                        if (result.isPresent()) {
                            if (result.get() == buttonSave) {
                                logger.info("User chose to save and exit");
                                driveService.setShutdownSaveEnabled(true);
                                Platform.exit();
                            } else if (result.get() == buttonExit) {
                                logger.info("User chose to exit without saving to Drive");
                                driveService.setShutdownSaveEnabled(false);
                                Platform.exit();
                            } else {
                                logger.info("User cancelled exit");
                            }
                        }
                    } else {
                        Platform.exit();
                    }
                } catch (Exception e) {
                    logger.error("Error during close request handling", e);
                    Platform.exit();
                }
            });
            
        } catch (final Exception e) {
            logger.error("Failed to start JavaFX application", e);
            Platform.exit();
            StudySyncApplication.shutdown();
        }
    }
    
    /**
     * Performs cleanup when the JavaFX application is stopping.
     * 
     * <p>This method is called by the JavaFX runtime when the application is
     * shutting down. It ensures that the Spring context is properly closed
     * to release all resources.</p>
     */
    @Override
    public void stop() {
        logger.info("Stopping JavaFX application and cleaning up resources");
        StudySyncApplication.shutdown();
    }
}
