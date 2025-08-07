package com.studysync.domain.service;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Service to handle date and time operations, including automatic date refresh at midnight.
 * This service notifies registered listeners when the date changes.
 */
@Service
public class DateTimeService {
    
    private final List<Consumer<LocalDate>> dateChangeListeners = new ArrayList<>();
    private final Timeline midnightTimer;
    private LocalDate currentDate;
    
    public DateTimeService() {
        this.currentDate = LocalDate.now();
        this.midnightTimer = createMidnightTimer();
        this.midnightTimer.play();
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
            LocalDate oldDate = currentDate;
            currentDate = now;
            notifyDateChangeListeners(currentDate);
        }
    }
    
    /**
     * Create a timeline that checks for date changes every minute.
     * @return Timeline for midnight detection
     */
    private Timeline createMidnightTimer() {
        Timeline timeline = new Timeline(new KeyFrame(
            Duration.minutes(1), 
            e -> checkAndUpdateDate()
        ));
        timeline.setCycleCount(Animation.INDEFINITE);
        return timeline;
    }
    
    /**
     * Notify all listeners that the date has changed.
     * @param newDate The new current date
     */
    private void notifyDateChangeListeners(LocalDate newDate) {
        Platform.runLater(() -> {
            for (Consumer<LocalDate> listener : dateChangeListeners) {
                try {
                    listener.accept(newDate);
                } catch (Exception e) {
                    System.err.println("Error notifying date change listener: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Stop the midnight timer (call this when shutting down the application).
     */
    public void shutdown() {
        if (midnightTimer != null) {
            midnightTimer.stop();
        }
    }
}