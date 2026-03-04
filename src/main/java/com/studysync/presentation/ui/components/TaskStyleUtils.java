package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.Task;
import com.studysync.domain.valueobject.TaskStatus;
import javafx.scene.control.Labeled;
import javafx.scene.paint.Color;

/**
 * Shared styling helpers for task status rendering and CSS-safe font
 * application.
 *
 * <p><strong>Why {@code setFont()} is unsafe:</strong> JavaFX re-applies
 * CSS on every scene-graph pulse (tab switch, focus change, window
 * restore, etc.).  If the author stylesheet sets {@code -fx-font-family}
 * on {@code .root}, every {@code .label} inherits that property and the
 * CSS engine constructs a <em>new</em> {@code Font} object — overwriting
 * whatever was set via {@link javafx.scene.text.Font} / {@code setFont()}.
 * Using inline styles ({@code setStyle()}) has higher CSS specificity than
 * any stylesheet rule, so font properties set this way survive
 * re-application.</p>
 */
public final class TaskStyleUtils {

    private TaskStyleUtils() { /* utility class */ }

    // ── CSS-safe font helpers ───────────────────────────────────

    /**
     * Applies bold font of the given size via inline style so it
     * survives CSS re-application.
     */
    public static void fontBold(Labeled node, int size) {
        appendStyle(node, "-fx-font-weight: bold; -fx-font-size: " + size + "px;");
    }

    /** Applies semi-bold font of the given size via inline style. */
    public static void fontSemiBold(Labeled node, int size) {
        appendStyle(node, "-fx-font-weight: 600; -fx-font-size: " + size + "px;");
    }

    /** Applies normal-weight font of the given size via inline style. */
    public static void fontNormal(Labeled node, int size) {
        appendStyle(node, "-fx-font-weight: normal; -fx-font-size: " + size + "px;");
    }

    /** Applies italic font of the given size via inline style. */
    public static void fontItalic(Labeled node, int size) {
        appendStyle(node, "-fx-font-style: italic; -fx-font-size: " + size + "px;");
    }

    /**
     * Applies the Noto Emoji font family at the given size via inline
     * style, so emoji glyphs render correctly and survive CSS
     * re-application.
     */
    public static void fontEmoji(Labeled node, int size) {
        appendStyle(node, "-fx-font-family: 'Noto Emoji'; -fx-font-size: " + size + "px;");
    }

    /**
     * Appends CSS properties to a node's existing inline style.
     * If the node already has an inline style, the new properties are
     * appended (separated by a space).
     */
    private static void appendStyle(Labeled node, String css) {
        String existing = node.getStyle();
        if (existing == null || existing.isEmpty()) {
            node.setStyle(css);
        } else {
            node.setStyle(existing + " " + css);
        }
    }

    // ── Border / accent colour keyed by status ──────────────────

    /** Border colour for a task card (delegates to status). */
    public static String taskBorderColor(Task task) {
        return taskBorderColor(task.getStatus());
    }

    /** Border colour keyed directly by status. */
    public static String taskBorderColor(TaskStatus s) {
        return switch (s) {
            case COMPLETED   -> "#27ae60";
            case DELAYED     -> "#e74c3c";
            case IN_PROGRESS -> "#f39c12";
            case CANCELLED   -> "#bdc3c7";
            case POSTPONED   -> "#9c27b0";
            default          -> "#3498db";
        };
    }

    // ── Card row background (light tint per status) ─────────────

    /** Light background tint for full task-card rows. */
    public static String statusBackground(TaskStatus s) {
        return switch (s) {
            case COMPLETED   -> "#f0fff0";
            case DELAYED     -> "#fff5f5";
            case IN_PROGRESS -> "#fff8e1";
            case CANCELLED   -> "#f5f5f5";
            case POSTPONED   -> "#f3e5f5";
            default          -> "white";
        };
    }

    // ── Status badge background ─────────────────────────────────

    /** Pastel background for compact status badges. */
    public static String statusBadgeBg(TaskStatus s) {
        return switch (s) {
            case COMPLETED   -> "#e8f5e9";
            case DELAYED     -> "#ffebee";
            case IN_PROGRESS -> "#fff3e0";
            case CANCELLED   -> "#eceff1";
            case POSTPONED   -> "#f3e5f5";
            default          -> "#e3f2fd";
        };
    }

    // ── Badge text colour (dark enough for contrast on pastels) ─

    /** Text colour for status badges — dark shades for readability. */
    public static Color statusTextColor(TaskStatus s) {
        return switch (s) {
            case COMPLETED   -> Color.web("#1b5e20");
            case DELAYED     -> Color.web("#b71c1c");
            case IN_PROGRESS -> Color.web("#8a4b00");
            case CANCELLED   -> Color.web("#37474f");
            case POSTPONED   -> Color.web("#5e35b1");
            default          -> Color.web("#0d47a1");
        };
    }

    // ── Status emoji / icon glyph ───────────────────────────────

    /** Single-character status indicator. */
    public static String statusEmoji(TaskStatus s) {
        return switch (s) {
            case COMPLETED   -> "\u2713"; // ✓
            case DELAYED     -> "\u231B"; // ⏳
            case IN_PROGRESS -> "\u25B6"; // ▶
            case CANCELLED   -> "\u2715"; // ✕
            case POSTPONED   -> "\u23F8"; // ⏸
            default          -> "\u25CB"; // ○
        };
    }
}
