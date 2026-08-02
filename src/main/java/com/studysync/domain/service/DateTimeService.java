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

    /**
     * Last date listeners were successfully told about. Tracked separately from
     * {@link #currentDate} so a rollover that could not be delivered - the UI
     * had not registered yet - is retried on the next tick instead of being
     * silently consumed.
     */
    private volatile LocalDate lastNotifiedDate;

    public DateTimeService() {
        this.currentDate = LocalDate.now();
        this.dateWatcher = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "studysync-date-watcher");
            // Daemon: this must never be the reason the JVM stays alive.
            thread.setDaemon(true);
            return thread;
        });
        this.lastNotifiedDate = this.currentDate;
        this.dateWatcher.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.MINUTES);
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
     * Scheduler entry point. Swallowing nothing here would be worse than it
     * sounds: {@code scheduleAtFixedRate} cancels all further executions after
     * an uncaught throw and buries the exception in a Future nobody reads, so
     * the app would run on a frozen date for the rest of the session with
     * nothing in the log.
     */
    private void tick() {
        try {
            checkAndUpdateDate();
        } catch (RuntimeException e) {
            logger.warn("Date watcher tick failed; continuing", e);
        }
    }

    /**
     * Check if it's a new day and update if necessary. Called by the watcher
     * and safe to call manually.
     */
    public void checkAndUpdateDate() {
        LocalDate now = LocalDate.now();
        currentDate = now;
        if (!now.equals(lastNotifiedDate) && notifyDateChangeListeners(now)) {
            lastNotifiedDate = now;
        }
    }

    /**
     * Notify all listeners that the date has changed, on the JavaFX thread.
     *
     * @param newDate The new current date
     * @return {@code true} when the notification was handed to the toolkit;
     *         {@code false} when there was nobody to tell, so the caller can
     *         try again on the next tick
     */
    private boolean notifyDateChangeListeners(LocalDate newDate) {
        if (dateChangeListeners.isEmpty()) {
            // Listeners are registered by the UI, so an empty list means the
            // toolkit may not be running yet and Platform.runLater would throw.
            return false;
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
            return true;
        } catch (IllegalStateException e) {
            logger.warn("JavaFX toolkit unavailable, will retry date change notification: {}", e.getMessage());
            return false;
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