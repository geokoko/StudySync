package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.Task;
import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.valueobject.TaskStatus;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.paint.Color;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

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

    public static final String COLOR_PRIMARY = "#3498db";
    public static final String COLOR_SUCCESS = "#27ae60";
    public static final String COLOR_DANGER = "#e74c3c";
    public static final String COLOR_PURPLE = "#9b59b6";
    public static final String COLOR_ORANGE = "#e67e22";
    public static final String COLOR_MUTED = "#7f8c8d";
    public static final String COLOR_TEXT = "#2c3e50";

    public static final String TINT_PRIMARY = "#e3f2fd";
    public static final String TINT_SUCCESS = "#e8f5e9";
    public static final String TINT_DANGER = "#ffebee";
    public static final String TINT_PURPLE = "#f3e5f5";
    public static final String TINT_ORANGE = "#fff3e0";
    public static final String TINT_NEUTRAL = "#f8f9fa";

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
     * Creates a small Label with the Noto Emoji font, suitable for use
     * as a {@link Labeled#setGraphic(javafx.scene.Node) graphic} on
     * buttons or labels that mix icon characters with regular text.
     */
    public static Label iconLabel(String icon, int size) {
        Label lbl = new Label(icon);
        fontEmoji(lbl, size);
        return lbl;
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
            case COMPLETED   -> COLOR_SUCCESS;
            case DELAYED     -> COLOR_DANGER;
            case IN_PROGRESS -> "#f39c12";
            case CANCELLED   -> "#bdc3c7";
            case POSTPONED   -> "#9c27b0";
            default          -> COLOR_PRIMARY;
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
            case COMPLETED   -> Color.web(COLOR_SUCCESS);
            case DELAYED     -> Color.web(COLOR_DANGER);
            case IN_PROGRESS -> Color.web("#8a4b00");
            case CANCELLED   -> Color.web("#37474f");
            case POSTPONED   -> Color.web("#5e35b1");
            default          -> Color.web(COLOR_PRIMARY);
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

    // ── Overdue / due-today helpers ─────────────────────────────

    /** Red used for overdue task borders and accents. */
    public static final String OVERDUE_COLOR = "#c0392b";

    /** Amber/orange used for "due today" accents. */
    public static final String DUE_TODAY_COLOR = "#e67e22";

    /**
     * Returns {@code true} when the task's deadline is strictly before
     * {@code date} and the task is still unresolved.  Recurring tasks are
     * never considered overdue (they repeat and have no single deadline).
     */
    public static boolean isOverdue(Task task, LocalDate date) {
        if (task.isRecurring()) return false;
        LocalDate deadline = task.getDeadline();
        if (deadline == null) return false;
        TaskStatus s = task.getStatus();
        boolean unresolved = s == TaskStatus.OPEN || s == TaskStatus.IN_PROGRESS
                          || s == TaskStatus.POSTPONED || s == TaskStatus.DELAYED;
        return unresolved && deadline.isBefore(date);
    }

    /**
     * Returns {@code true} when the task's deadline equals {@code date}
     * and the task is still unresolved.  Recurring tasks are excluded.
     */
    public static boolean isDueToday(Task task, LocalDate date) {
        if (task.isRecurring()) return false;
        LocalDate deadline = task.getDeadline();
        if (deadline == null) return false;
        TaskStatus s = task.getStatus();
        boolean unresolved = s == TaskStatus.OPEN || s == TaskStatus.IN_PROGRESS
                          || s == TaskStatus.POSTPONED || s == TaskStatus.DELAYED;
        return unresolved && deadline.equals(date);
    }

    /**
     * Returns the appropriate border colour for a task on a given date,
     * taking overdue state into account.  When overdue, the border is red
     * regardless of status.
     */
    public static String taskBorderColor(Task task, LocalDate date) {
        if (isOverdue(task, date)) return OVERDUE_COLOR;
        return taskBorderColor(task);
    }

    /**
     * Creates a small red "Overdue" badge label.
     * Call font helpers <em>after</em> this method since it uses
     * {@code setStyle()}.
     */
    public static Label createOverdueBadge() {
        Label badge = new Label("Overdue");
        badge.setGraphic(iconLabel("\u26A0", 10));
        badge.setPadding(new Insets(2, 6, 2, 6));
        badge.setStyle("-fx-background-color: #ffebee; -fx-background-radius: 10;");
        fontBold(badge, 10);
        badge.setTextFill(Color.web(OVERDUE_COLOR));
        return badge;
    }

    /**
     * Creates a small amber "Due today" badge label.
     * Call font helpers <em>after</em> this method since it uses
     * {@code setStyle()}.
     */
    public static Label createDueTodayBadge() {
        Label badge = new Label("Due today");
        badge.setPadding(new Insets(2, 6, 2, 6));
        badge.setStyle("-fx-background-color: #fff3e0; -fx-background-radius: 10;");
        fontBold(badge, 10);
        badge.setTextFill(Color.web(DUE_TODAY_COLOR));
        return badge;
    }

    // ── Missed recurring-occurrence helpers ─────────────────────

    /** Red used for missed recurring-task occurrence accents. */
    public static final String MISSED_COLOR = "#e74c3c";

    public static String attemptTextColor(StudyGoal goal) {
        if (goal.getStatus() == StudyGoal.GoalStatus.ABANDONED) {
            return COLOR_DANGER;
        }
        return switch (goal.getAttemptOutcome()) {
            case ACHIEVED -> COLOR_SUCCESS;
            case MISSED -> COLOR_DANGER;
            case PENDING -> COLOR_PRIMARY;
        };
    }

    public static String attemptBackgroundColor(StudyGoal goal) {
        if (goal.getStatus() == StudyGoal.GoalStatus.ABANDONED) {
            return TINT_DANGER;
        }
        return switch (goal.getAttemptOutcome()) {
            case ACHIEVED -> TINT_SUCCESS;
            case MISSED -> TINT_DANGER;
            case PENDING -> TINT_PRIMARY;
        };
    }

    public static String retryTextColor() {
        return COLOR_ORANGE;
    }

    public static String retryBackgroundColor() {
        return TINT_ORANGE;
    }

    public static Label createAttemptBadge(String text, String textColor, String backgroundColor) {
        Label badge = new Label(text);
        fontBold(badge, 10);
        badge.setTextFill(Color.web(textColor));
        badge.setPadding(new Insets(2, 7, 2, 7));
        badge.setStyle("-fx-background-color: " + backgroundColor + "; -fx-background-radius: 10;");
        return badge;
    }

    /**
     * Creates a small red "Missed" badge for a past calendar date where
     * a recurring task was scheduled but had no achieved linked goal.
     */
    public static Label createMissedBadge() {
        Label badge = new Label("Missed");
        badge.setGraphic(iconLabel("\u26A0", 10));
        badge.setPadding(new Insets(2, 6, 2, 6));
        badge.setStyle("-fx-background-color: #ffebee; -fx-background-radius: 10;");
        fontBold(badge, 10);
        badge.setTextFill(Color.web(MISSED_COLOR));
        return badge;
    }

    /**
     * Creates a "Missed [Day]" badge for carry-forward display on today's
     * planner (e.g. "Missed Mon").
     *
     * @param missedDate the date the occurrence was missed
     */
    public static Label createMissedDayBadge(LocalDate missedDate) {
        String dayName = missedDate.getDayOfWeek()
                .getDisplayName(TextStyle.SHORT, Locale.getDefault());
        Label badge = new Label("Missed " + dayName);
        badge.setGraphic(iconLabel("\u26A0", 10));
        badge.setPadding(new Insets(2, 6, 2, 6));
        badge.setStyle("-fx-background-color: #ffebee; -fx-background-radius: 10;");
        fontBold(badge, 10);
        badge.setTextFill(Color.web(MISSED_COLOR));
        return badge;
    }
}
