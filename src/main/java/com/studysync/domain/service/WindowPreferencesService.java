package com.studysync.domain.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.prefs.Preferences;

/**
 * Service for managing window preferences like size, position, and state.
 * Uses Java Preferences API to persist settings between application sessions.
 */
@Service
public class WindowPreferencesService {
    
    private static final Logger logger = LoggerFactory.getLogger(WindowPreferencesService.class);
    
    private static final String WINDOW_WIDTH_KEY = "window.width";
    private static final String WINDOW_HEIGHT_KEY = "window.height";
    private static final String WINDOW_X_KEY = "window.x";
    private static final String WINDOW_Y_KEY = "window.y";
    private static final String WINDOW_MAXIMIZED_KEY = "window.maximized";
    private static final String TAB_ORDER_KEY = "tab.order";
    
    // Default window dimensions
    private static final double DEFAULT_WIDTH = 1200;
    private static final double DEFAULT_HEIGHT = 800;
    private static final double DEFAULT_X = 100;
    private static final double DEFAULT_Y = 100;
    
    private final Preferences preferences;
    
    public WindowPreferencesService() {
        // Use system preferences for the StudySync application
        this.preferences = Preferences.userNodeForPackage(WindowPreferencesService.class);
        logger.info("WindowPreferencesService initialized");
    }
    
    /**
     * Gets the saved window width or default if not set.
     */
    public double getWindowWidth() {
        return preferences.getDouble(WINDOW_WIDTH_KEY, DEFAULT_WIDTH);
    }
    
    /**
     * Gets the saved window height or default if not set.
     */
    public double getWindowHeight() {
        return preferences.getDouble(WINDOW_HEIGHT_KEY, DEFAULT_HEIGHT);
    }
    
    /**
     * Gets the saved window X position or default if not set.
     */
    public double getWindowX() {
        return preferences.getDouble(WINDOW_X_KEY, DEFAULT_X);
    }
    
    /**
     * Gets the saved window Y position or default if not set.
     */
    public double getWindowY() {
        return preferences.getDouble(WINDOW_Y_KEY, DEFAULT_Y);
    }
    
    /**
     * Gets whether the window was maximized in the last session.
     */
    public boolean isWindowMaximized() {
        return preferences.getBoolean(WINDOW_MAXIMIZED_KEY, false);
    }
    
    /**
     * Saves the current window dimensions.
     */
    public void saveWindowSize(double width, double height) {
        preferences.putDouble(WINDOW_WIDTH_KEY, width);
        preferences.putDouble(WINDOW_HEIGHT_KEY, height);
        flushPreferences();
        logger.debug("Saved window size: {}x{}", width, height);
    }
    
    /**
     * Saves the current window position.
     */
    public void saveWindowPosition(double x, double y) {
        preferences.putDouble(WINDOW_X_KEY, x);
        preferences.putDouble(WINDOW_Y_KEY, y);
        flushPreferences();
        logger.debug("Saved window position: {}, {}", x, y);
    }
    
    /**
     * Saves the window maximized state.
     */
    public void saveWindowMaximized(boolean maximized) {
        preferences.putBoolean(WINDOW_MAXIMIZED_KEY, maximized);
        flushPreferences();
        logger.debug("Saved window maximized state: {}", maximized);
    }
    
    /**
     * Saves all window properties at once.
     */
    public void saveWindowState(double width, double height, double x, double y, boolean maximized) {
        preferences.putDouble(WINDOW_WIDTH_KEY, width);
        preferences.putDouble(WINDOW_HEIGHT_KEY, height);
        preferences.putDouble(WINDOW_X_KEY, x);
        preferences.putDouble(WINDOW_Y_KEY, y);
        preferences.putBoolean(WINDOW_MAXIMIZED_KEY, maximized);
        flushPreferences();
        logger.debug("Saved complete window state: {}x{} at ({}, {}), maximized: {}", 
                    width, height, x, y, maximized);
    }
    
    /**
     * Validates that the saved window position is still valid for current screen setup.
     * Returns true if position should be used, false if defaults should be applied.
     */
    public boolean isWindowPositionValid(double x, double y, double width, double height) {
        // Basic validation - ensure window is not completely off-screen
        // This is a simple check; more sophisticated validation could be added
        return x >= -width/2 && y >= -50 && x < 3000 && y < 2000;
    }
    
    /**
     * Resets all window preferences to defaults.
     */
    public void resetToDefaults() {
        preferences.remove(WINDOW_WIDTH_KEY);
        preferences.remove(WINDOW_HEIGHT_KEY);
        preferences.remove(WINDOW_X_KEY);
        preferences.remove(WINDOW_Y_KEY);
        preferences.remove(WINDOW_MAXIMIZED_KEY);
        preferences.remove(TAB_ORDER_KEY);
        flushPreferences();
        logger.info("Reset window preferences to defaults");
    }
    
    /**
     * Gets default window width.
     */
    public double getDefaultWidth() {
        return DEFAULT_WIDTH;
    }
    
    /**
     * Gets default window height.
     */
    public double getDefaultHeight() {
        return DEFAULT_HEIGHT;
    }
    
    /**
     * Saves the tab order as a comma-separated string of tab titles.
     */
    public void saveTabOrder(java.util.List<String> tabTitles) {
        String tabOrder = String.join(",", tabTitles);
        preferences.put(TAB_ORDER_KEY, tabOrder);
        flushPreferences();
        logger.debug("Saved tab order: {}", tabOrder);
    }
    
    /**
     * Gets the saved tab order, returns null if not set.
     */
    public java.util.List<String> getTabOrder() {
        String tabOrder = preferences.get(TAB_ORDER_KEY, null);
        if (tabOrder == null || tabOrder.trim().isEmpty()) {
            return null;
        }
        return java.util.Arrays.asList(tabOrder.split(","));
    }
    
    /**
     * Resets tab order preference.
     */
    public void resetTabOrder() {
        preferences.remove(TAB_ORDER_KEY);
        flushPreferences();
        logger.info("Reset tab order preference");
    }
    
    private void flushPreferences() {
        try {
            preferences.flush();
        } catch (Exception e) {
            logger.warn("Failed to flush window preferences: {}", e.getMessage());
        }
    }
}