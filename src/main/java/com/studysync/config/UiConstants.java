package com.studysync.config;

import javafx.scene.paint.Color;

/**
 * Central configuration for UI constants to improve maintainability and consistency.
 * 
 * <p>This class provides a single source of truth for commonly used UI values including
 * colors, dimensions, timeouts, and styling constants. Using these constants ensures
 * consistency across the application and makes theme changes easier to implement.</p>
 * 
 * <p><strong>Categories:</strong>
 * <ul>
 *   <li><strong>Colors:</strong> Brand colors, semantic colors, and neutral shades</li>
 *   <li><strong>Dimensions:</strong> Default window sizes and component dimensions</li>
 *   <li><strong>Timeouts:</strong> Authentication and operation timeouts</li>
 *   <li><strong>Styles:</strong> Common CSS style fragments</li>
 * </ul></p>
 * 
 * @author StudySync Development Team
 * @version 2.0.0
 * @since 2.0.0
 */
public final class UiConstants {
    
    // Prevent instantiation
    private UiConstants() {
        throw new AssertionError("UiConstants should not be instantiated");
    }
    
    // ===================================================================================
    // WINDOW DIMENSIONS
    // ===================================================================================
    
    /** Default application window width */
    public static final double DEFAULT_WINDOW_WIDTH = 1200.0;
    
    /** Default application window height */
    public static final double DEFAULT_WINDOW_HEIGHT = 800.0;
    
    /** Fallback window width for smaller screens */
    public static final double FALLBACK_WINDOW_WIDTH = 800.0;
    
    /** Fallback window height for smaller screens */
    public static final double FALLBACK_WINDOW_HEIGHT = 600.0;
    
    /** Authentication dialog width */
    public static final double AUTH_DIALOG_WIDTH = 600.0;
    
    /** Dialog preferred height for forms */
    public static final double DIALOG_FORM_HEIGHT = 400.0;
    
    /** Study session detail panel height */
    public static final double SESSION_DETAIL_HEIGHT = 600.0;
    
    // ===================================================================================
    // COLORS - JAVAFX COLOR OBJECTS
    // ===================================================================================

    /** Primary brand color - Blue */
    public static final Color PRIMARY_BLUE = Color.web("#3498db");

    /** Secondary brand color - Purple */
    public static final Color SECONDARY_PURPLE = Color.web("#9b59b6");

    /** Success/positive color - Green */
    public static final Color SUCCESS_GREEN = Color.web("#27ae60");

    /** Warning color - Orange */
    public static final Color WARNING_ORANGE = Color.web("#f39c12");

    /** Danger/error color - Red */
    public static final Color DANGER_RED = Color.web("#e74c3c");

    /** Darker red for emphasis */
    public static final Color DARK_RED = Color.web("#c0392b");

    /** Alternative green */
    public static final Color ALT_GREEN = Color.web("#2ecc71");

    /** Alternative orange */
    public static final Color ALT_ORANGE = Color.web("#e67e22");

    /** Google Blue (for authentication) */
    public static final Color GOOGLE_BLUE = Color.web("#4285f4");

    /** Google Red (for logout) */
    public static final Color GOOGLE_RED = Color.web("#db4437");

    /** Google Green (for actions) */
    public static final Color GOOGLE_GREEN = Color.web("#0f9d58");

    /** Default category color */
    public static final Color DEFAULT_CATEGORY_COLOR = Color.web("#3498db");
    
    // ===================================================================================
    // COLORS - TEXT COLORS
    // ===================================================================================
    
    /** Primary text color - Dark blue-gray */
    public static final String TEXT_PRIMARY = "#2c3e50";
    
    /** Secondary text color - Medium gray */
    public static final String TEXT_SECONDARY = "#34495e";
    
    /** Muted text color - Light gray */
    public static final String TEXT_MUTED = "#7f8c8d";
    
    /** Light text color - Very light gray */
    public static final String TEXT_LIGHT = "#6c757d";
    
    /** Disabled text color */
    public static final String TEXT_DISABLED = "#bdc3c7";
    
    /** White text for dark backgrounds */
    public static final String TEXT_WHITE = "#f8f9fa";
    
    /** Purple text accent */
    public static final String TEXT_PURPLE = "#8e44ad";
    
    /** Success text color */
    public static final String TEXT_SUCCESS = "#27ae60";
    
    /** Danger text color */
    public static final String TEXT_DANGER = "#e74c3c";
    
    // ===================================================================================
    // COLORS - BACKGROUND COLORS
    // ===================================================================================
    
    /** Light background color */
    public static final String BG_LIGHT = "#f8f9fa";
    
    /** Very light background */
    public static final String BG_VERY_LIGHT = "#ffffff";
    
    /** Light gray background */
    public static final String BG_LIGHT_GRAY = "#e9ecef";
    
    /** Success background - light green */
    public static final String BG_SUCCESS = "#e8f5e8";
    
    /** Warning background - light yellow */
    public static final String BG_WARNING = "#fff3cd";
    
    /** Info background - light blue */
    public static final String BG_INFO = "#d1ecf1";
    
    /** Light blue background */
    public static final String BG_LIGHT_BLUE = "#f0f7ff";
    
    /** Light purple background */
    public static final String BG_LIGHT_PURPLE = "#f0f8ff";
    
    /** Light green background */
    public static final String BG_LIGHT_GREEN = "#f0fff0";
    
    /** Light red background */
    public static final String BG_LIGHT_RED = "#fff5f5";
    
    /** Study session background */
    public static final String BG_SESSION_STUDY = "#f8f9ff";
    
    /** Project session background */
    public static final String BG_SESSION_PROJECT = "#fff8f8";
    
    // ===================================================================================
    // COLORS - BORDER COLORS
    // ===================================================================================
    
    /** Light border color */
    public static final String BORDER_LIGHT = "#e0e0e0";
    
    /** Medium border color */
    public static final String BORDER_MEDIUM = "#dee2e6";
    
    /** Primary border color */
    public static final String BORDER_PRIMARY = "#3498db";
    
    /** Success border color */
    public static final String BORDER_SUCCESS = "#27ae60";
    
    /** Danger border color */
    public static final String BORDER_DANGER = "#e74c3c";
    
    // ===================================================================================
    // GRADIENT DEFINITIONS
    // ===================================================================================
    
    /** Main content gradient background */
    public static final String GRADIENT_MAIN = "linear-gradient(to bottom, #f1f2f6, #dfe4ea)";
    
    /** Secondary gradient background */
    public static final String GRADIENT_SECONDARY = "linear-gradient(to bottom, #f8f9fa, #e9ecef)";
    
    /** Header gradient - purple to blue */
    public static final String GRADIENT_HEADER = "linear-gradient(to right, #667eea, #764ba2)";
    
    // ===================================================================================
    // CATEGORY COLORS PALETTE
    // ===================================================================================
    
    /** Default category colors for new categories */
    public static final String[] DEFAULT_CATEGORY_COLORS = {
        "#3498db", // Blue
        "#e74c3c", // Red  
        "#f39c12", // Orange
        "#27ae60", // Green
        "#9b59b6", // Purple
        "#1abc9c"  // Teal
    };
    
    // ===================================================================================
    // TIMEOUTS AND DURATIONS
    // ===================================================================================
    
    /** Authentication timeout in milliseconds (1 minute) */
    public static final long AUTH_TIMEOUT_MS = 60_000L;
    
    /** Network timeout for Gradle wrapper */
    public static final long NETWORK_TIMEOUT_MS = 10_000L;
    
    
    
    // ===================================================================================
    // COMMON CSS STYLE FRAGMENTS
    // ===================================================================================
    
    /** Primary button style */
    public static final String STYLE_BUTTON_PRIMARY = 
        "-fx-background-color: " + toWebColor(PRIMARY_BLUE) + "; -fx-text-fill: white; -fx-font-weight: bold;";
    
    /** Success button style */
    public static final String STYLE_BUTTON_SUCCESS = 
        "-fx-background-color: " + toWebColor(SUCCESS_GREEN) + "; -fx-text-fill: white;";
    
    /** Danger button style */
    public static final String STYLE_BUTTON_DANGER = 
        "-fx-background-color: " + toWebColor(DANGER_RED) + "; -fx-text-fill: white;";
    
    /** Warning button style */
    public static final String STYLE_BUTTON_WARNING = 
        "-fx-background-color: " + toWebColor(WARNING_ORANGE) + "; -fx-text-fill: white;";
    
    /** Secondary button style */
    public static final String STYLE_BUTTON_SECONDARY = 
        "-fx-background-color: #95a5a6; -fx-text-fill: white;";
    
    /** Google authentication button style */
    public static final String STYLE_BUTTON_GOOGLE = 
        "-fx-background-color: " + toWebColor(GOOGLE_BLUE) + "; -fx-text-fill: white; -fx-font-weight: bold;";
    
    /** Rounded container style */
    public static final String STYLE_CONTAINER_ROUNDED = 
        "-fx-background-radius: 10; -fx-border-radius: 10;";
    
    /** Small rounded container style */
    public static final String STYLE_CONTAINER_SMALL_ROUNDED = 
        "-fx-background-radius: 5; -fx-border-radius: 5;";
    
    /** Drop shadow effect */
    public static final String STYLE_DROP_SHADOW = 
        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 3, 0, 0, 1);";

    private static String toWebColor(Color color) {
        return String.format("#%02X%02X%02X",
                             (int) (color.getRed() * 255),
                             (int) (color.getGreen() * 255),
                             (int) (color.getBlue() * 255));
    }
}