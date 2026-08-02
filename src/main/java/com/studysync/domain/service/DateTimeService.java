package com.studysync.domain.service;

import jakarta.annotation.PreDestroy;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Service to handle date and time operations, including automatic date refresh at midnight.
 * This service notifies registered listeners when the date changes.
 *
 * <p>The tick is a plain daemon scheduler rather than a JavaFX {@code Timeline}:
 * this bean is constructed while the Spring context starts, which happens
 * before {@code Application.launch()}, so a Timeline here would depend on the
 * toolkit being up before anything guarantees it. Polling once a minute rather
 * than scheduling a single job at midnight keeps it correct across clock
 * changes, daylight saving and a laptop waking from sleep.</p>
 */
@Service
public class DateTimeService {

    private static final Logger logger = LoggerFactory.getLogger(DateTimeService.class);

    private final List<Consumer<LocalDate>> dateChangeListeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService dateWatcher;
    private volatile LocalDate currentDate;

    public DateTimeService() {
        this.currentDate = LocalDate.now();
        this.dateWatcher = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "studysync-date-watcher");
            // Daemon: this must never be the reason the JVM stays alive.
            thread.setDaemon(true);
            return thread;
        });
        this.dateWatcher.scheduleAtFixedRate(this::checkAndUpdateDate, 1, 1, TimeUnit.MINUTES);
    }
    
    /**
     * Register a listener to be notified when the date changes at midnight.
     * @param listener Consumer that will be called with the new date
     */
    public void addDateChangeListener(Consumer<LocalDate> listener) {
        dateChangeListeners.add(listener);
    }
    
    /**
     * Remove a date change listener.
     * @param listener The listener to remove
     */
    public void removeDateChangeListener(Consumer<LocalDate> listener) {
        dateChangeListeners.remove(listener);
    }
    
    /**
     * Get the current date.
     * @return Current LocalDate
     */
    public LocalDate getCurrentDate() {
        return currentDate;
    }
    
    /**
     * Get formatted current date string.
     * @return Formatted date string
     */
    public String getFormattedCurrentDate() {
        return getCurrentDate().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"));
    }
    
    /**
     * Check if it's a new day and update if necessary.
     * This is called by the timer and can also be called manually.
     */
    public void checkAndUpdateDate() {
        LocalDate now = LocalDate.now();
        if (!now.equals(currentDate)) {
            currentDate = now;
            notifyDateChangeListeners(currentDate);
        }
    }

    /**
     * Notify all listeners that the date has changed, on the JavaFX thread.
     * @param newDate The new current date
     */
    private void notifyDateChangeListeners(LocalDate newDate) {
        if (dateChangeListeners.isEmpty()) {
            // Listeners are registered by the UI, so an empty list means the
            // toolkit may not be running yet and Platform.runLater would throw.
            return;
        }
        try {
            Platform.runLater(() -> {
                for (Consumer<LocalDate> listener : dateChangeListeners) {
                    try {
                        listener.accept(newDate);
                    } catch (Exception e) {
                        logger.warn("Error notifying date change listener", e);
                    }
                }
            });
        } catch (IllegalStateException e) {
            logger.warn("JavaFX toolkit unavailable, skipping date change notification: {}", e.getMessage());
        }
    }

    /**
     * Stop the date watcher. Invoked by Spring when the context closes.
     */
    @PreDestroy
    public void shutdown() {
        dateWatcher.shutdownNow();
    }
}