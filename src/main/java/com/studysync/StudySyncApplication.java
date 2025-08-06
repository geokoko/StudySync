package com.studysync;

import com.studysync.config.AppProperties;
import com.studysync.application.StudySyncJavaFXApp;
import javafx.application.Application;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Main entry point for the StudySync desktop application.
 * 
 * <p>This class serves as the bridge between Spring Boot's dependency injection
 * framework and JavaFX's application lifecycle. It initializes the Spring context
 * first, then launches the JavaFX application with access to Spring-managed beans.</p>
 * 
 * <p>The application follows a clean architecture pattern with distinct layers:
 * <ul>
 *   <li><strong>Domain:</strong> Business logic and entities</li>
 *   <li><strong>Infrastructure:</strong> Data persistence and external services</li>
 *   <li><strong>Presentation:</strong> JavaFX UI components and controllers</li>
 *   <li><strong>Configuration:</strong> Spring configuration and properties</li>
 * </ul></p>
 * 
 * @author geokoko
 * @version 0.1.0-BETA
 * @since 0.1.0
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class StudySyncApplication {
    
    /** The Spring application context instance shared across the application. */
    private static ConfigurableApplicationContext springContext;
    
    /**
     * Main method that starts the StudySync application.
     * 
     * <p>This method performs the following initialization sequence:
     * <ol>
     *   <li>Configures system properties for JavaFX compatibility</li>
     *   <li>Initializes the Spring Boot application context</li>
     *   <li>Launches the JavaFX application with Spring integration</li>
     * </ol></p>
     * 
     * @param args command line arguments passed to both Spring Boot and JavaFX
     */
    public static void main(final String[] args) {
        // Disable Spring Boot's automatic shutdown when main method ends
        System.setProperty("java.awt.headless", "false");
        
        // Configure Spring Boot for faster startup
        SpringApplication app = new SpringApplication(StudySyncApplication.class);
        
        // Enable lazy initialization for faster startup
        app.setLazyInitialization(true);
        
        // Reduce startup output
        app.setLogStartupInfo(false);
        app.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
        
        // Start Spring Boot application context
        springContext = app.run(args);
        
        // Launch JavaFX application
        Application.launch(StudySyncJavaFXApp.class, args);
    }
    
    /**
     * Provides access to the Spring application context for JavaFX components.
     * 
     * <p>This method allows JavaFX components to access Spring-managed beans
     * and services. It should be called only after the Spring context has been
     * initialized in the main method.</p>
     * 
     * @return the configured Spring application context
     * @throws IllegalStateException if called before Spring context initialization
     */
    public static ConfigurableApplicationContext getSpringContext() {
        if (springContext == null) {
            throw new IllegalStateException("Spring context not initialized");
        }
        return springContext;
    }
    
    /**
     * Gracefully shuts down the Spring application context.
     * 
     * <p>This method should be called when the JavaFX application is closing
     * to ensure proper cleanup of Spring-managed resources, database connections,
     * and other services.</p>
     * 
     * <p>The method is idempotent and safe to call multiple times.</p>
     */
    public static void shutdown() {
        if (springContext != null && springContext.isActive()) {
            springContext.close();
            springContext = null;
        }
    }
}
