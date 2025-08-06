package com.studysync.application;

import com.studysync.StudySyncApplication;
import com.studysync.presentation.ui.StudySyncUI;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;

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
                logger.info("Application close requested, initiating shutdown");
                Platform.exit();
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
