package com.studysync;

import com.studysync.application.StudySyncJavaFXApp;
import com.studysync.bootstrap.PendingDownloadApplier;
import com.studysync.integration.drive.GoogleDriveBootstrap;
import com.studysync.integration.drive.GoogleDriveSettings;
import com.studysync.integration.drive.GoogleDriveSettingsLoader;
import javafx.application.Application;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;

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
 * @version 0.1.2
 * @since 0.1.0
 */
@SpringBootApplication
public class StudySyncApplication {

    public static final int RESTART_EXIT_CODE = 75;
    
    /** The Spring application context instance shared across the application. */
    private static ConfigurableApplicationContext springContext;
    private static volatile int requestedExitCode = 0;

    /** File lock held for the lifetime of the process to enforce single-instance. */
    @SuppressWarnings("unused") // must stay referenced to prevent GC releasing the lock
    private static FileLock instanceLock;
    @SuppressWarnings("unused")
    private static RandomAccessFile lockRaf;

    /**
     * Main method that starts the StudySync application.
     *
     * @param args command line arguments passed to both Spring Boot and JavaFX
     */
    public static void main(final String[] args) {
        // Enforce single instance — exit immediately if another process holds the lock
        if (!acquireInstanceLock()) {
            System.err.println("StudySync is already running. Exiting.");
            System.exit(1);
        }

        // Disable Spring Boot's automatic shutdown when main method ends
        System.setProperty("java.awt.headless", "false");

        GoogleDriveSettings driveSettings = GoogleDriveSettingsLoader.load();
        PendingDownloadApplier.applyIfPresent(driveSettings.localDatabasePath());

        // Attempt to initialize Google Drive hosted data (if configured) before Spring initializes the database
        GoogleDriveBootstrap.initialize(driveSettings);

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
        shutdown();
        if (requestedExitCode != 0) {
            System.exit(requestedExitCode);
        }
    }

    public static void requestRestart() {
        requestedExitCode = RESTART_EXIT_CODE;
    }

    /**
     * Attempts to acquire an exclusive file lock to prevent multiple instances.
     * The lock file is created in the user's data directory and held for the
     * lifetime of the JVM process (released automatically on exit).
     *
     * @return true if the lock was acquired (no other instance running)
     */
    private static boolean acquireInstanceLock() {
        try {
            Path lockDir = Path.of(System.getProperty("user.home"), ".local", "share", "studysync");
            Files.createDirectories(lockDir);
            Path lockFile = lockDir.resolve(".studysync.lock");

            // RandomAccessFile + FileLock held in static fields so GC cannot release them
            lockRaf = new RandomAccessFile(lockFile.toFile(), "rw");
            FileChannel channel = lockRaf.getChannel();
            instanceLock = channel.tryLock();

            if (instanceLock == null) {
                // Another instance holds the lock — close the file before exiting
                try { lockRaf.close(); } catch (IOException ignored) { }
                lockRaf = null;
                return false;
            }
            return true;
        } catch (Exception e) {
            // Catches IOException AND OverlappingFileLockException (from same-JVM
            // re-entry, e.g. test harnesses calling main() twice).
            System.err.println("Warning: could not check instance lock: " + e.getMessage());
            return true; // allow startup if lock mechanism fails
        }
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
