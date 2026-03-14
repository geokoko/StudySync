
package com.studysync.presentation.ui.components;

import com.studysync.domain.service.StudyService;
import com.studysync.domain.service.StudySessionEnd;
import com.studysync.domain.service.DateTimeService;
import com.studysync.domain.service.TaskService;
import com.studysync.domain.service.CategoryService;
import com.studysync.domain.service.MissedOccurrence;
import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.StudySession;
import com.studysync.domain.entity.DailyReflection;
import com.studysync.domain.entity.Task;
import com.studysync.domain.valueobject.TaskCategory;
import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.domain.valueobject.TaskStatus;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.Node;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import javafx.util.StringConverter;
import java.util.function.Consumer;

/**
 * Study Planner panel with:
 * - Date navigation (back / forward arrows, "Today" button)
 * - Tasks section (replaces "Today's Goals"): expandable task cards with linked goals
 * - Sessions section: FlowPane of compact session cards (wraps to next row)
 * - Daily reflection section
 */
public class StudyPlannerPanel extends ScrollPane implements RefreshablePanel {

    private final StudyService studyService;
    private final DateTimeService dateTimeService;
    private final TaskService taskService;
    private final CategoryService categoryService;

    private final Consumer<Node> showModal;
    private final Runnable closeModal;

    // Navigation state — the date currently displayed in the planner
    private LocalDate displayDate;

    // Live-session state
    private StudySession currentSession;
    private Timeline sessionTimer;

    // Tracks which task cards are currently expanded so they survive UI rebuilds
    private final Set<String> expandedTaskIds = new HashSet<>();

    // Sort / group state for the tasks section
    private String currentSort = "Status";
    private String currentGroup = "None";

    // UI containers that get rebuilt on navigation
    private VBox tasksContainer;
    private FlowPane sessionsFlowPane;
    private TextArea reflectionArea;
    private ProgressBar dailyProgressBar;
    private Label progressLabel;
    private Label dateNavLabel;
    private TextArea sessionTextArea;

    // ──────────────────────────────────────────────
    // CONSTRUCTION
    // ──────────────────────────────────────────────

    public StudyPlannerPanel(StudyService studyService, DateTimeService dateTimeService,
                             TaskService taskService, CategoryService categoryService,
                             Consumer<Node> showModal, Runnable closeModal) {
        this.studyService = studyService;
        this.dateTimeService = dateTimeService;
        this.taskService = taskService;
        this.categoryService = categoryService;
        this.showModal = showModal;
        this.closeModal = closeModal;
        this.displayDate = dateTimeService.getCurrentDate();

        VBox mainContent = new VBox(20);
        mainContent.setPadding(new Insets(20));
        mainContent.getStyleClass().add("panel-bg");

        this.setContent(mainContent);
        this.setFitToWidth(true);
        this.setFitToHeight(false);
        this.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.getStyleClass().add("tab-content-area");

        initializeComponents(mainContent);

        dateTimeService.addDateChangeListener(this::onDateChanged);
        updateDisplay();
    }

    // ──────────────────────────────────────────────
    // LAYOUT INITIALIZATION
    // ──────────────────────────────────────────────

    private void initializeComponents(VBox mainContent) {
        createHeader(mainContent);
        createTasksSection(mainContent);
        createSessionSection(mainContent);
        createReflectionSection(mainContent);
    }

    /** Header: date navigation + progress bar. */
    private void createHeader(VBox mainContent) {
        VBox header = new VBox(10);
        header.setAlignment(Pos.CENTER);

        // Date navigation row
        HBox navRow = new HBox(12);
        navRow.setAlignment(Pos.CENTER);

        Button prevBtn = new Button("◀");
        prevBtn.getStyleClass().add("btn-primary");
        prevBtn.setOnAction(e -> {
            displayDate = displayDate.minusDays(1);
            updateDisplay();
        });

        dateNavLabel = new Label();
        TaskStyleUtils.fontBold(dateNavLabel, 22);
        dateNavLabel.setMinWidth(300);
        dateNavLabel.setAlignment(Pos.CENTER);

        Button nextBtn = new Button("▶");
        nextBtn.getStyleClass().add("btn-primary");
        nextBtn.setOnAction(e -> {
            displayDate = displayDate.plusDays(1);
            updateDisplay();
        });

        Button todayBtn = new Button("» Today");
        todayBtn.getStyleClass().add("btn-success");
        todayBtn.setOnAction(e -> {
            displayDate = dateTimeService.getCurrentDate();
            updateDisplay();
        });

        navRow.getChildren().addAll(prevBtn, dateNavLabel, nextBtn, todayBtn);

        dailyProgressBar = new ProgressBar(0);
        dailyProgressBar.setPrefWidth(300);
        dailyProgressBar.setStyle("-fx-accent: #27ae60;");

        progressLabel = new Label("Daily Progress: 0%");
        TaskStyleUtils.fontSemiBold(progressLabel, 14);
        progressLabel.setTextFill(Color.web("#34495e"));

        header.getChildren().addAll(navRow, dailyProgressBar, progressLabel);
        mainContent.getChildren().add(header);
    }

    /** Tasks section (replaces "Today's Goals"). */
    private void createTasksSection(VBox mainContent) {
        VBox section = new VBox(15);
        section.getStyleClass().add("section-card");
        section.setPadding(new Insets(20));

        HBox sectionHeader = new HBox(15);
        sectionHeader.setAlignment(Pos.CENTER_LEFT);

        Label sectionTitle = new Label("☑ Today's Tasks");
        TaskStyleUtils.fontBold(sectionTitle, 18);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addGoalBtn = new Button("+ Add Goal");
        addGoalBtn.getStyleClass().add("btn-purple");
        addGoalBtn.setOnAction(e -> showAddGoalDialog(null));

        sectionHeader.getChildren().addAll(sectionTitle, spacer, addGoalBtn);

        // Sort / group toolbar
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label sortLabel = new Label("Sort:");
        TaskStyleUtils.fontBold(sortLabel, 12);
        ComboBox<String> sortCombo = new ComboBox<>();
        sortCombo.getItems().addAll("Status", "Priority", "Deadline", "Title");
        sortCombo.setValue(currentSort);
        sortCombo.setOnAction(e -> { currentSort = sortCombo.getValue(); updateTasksDisplay(); });

        Label groupLabel = new Label("Group:");
        TaskStyleUtils.fontBold(groupLabel, 12);
        ComboBox<String> groupCombo = new ComboBox<>();
        groupCombo.getItems().addAll("None", "Status", "Category");
        groupCombo.setValue(currentGroup);
        groupCombo.setOnAction(e -> { currentGroup = groupCombo.getValue(); updateTasksDisplay(); });

        toolbar.getChildren().addAll(sortLabel, sortCombo, groupLabel, groupCombo);

        tasksContainer = new VBox(10);
        section.getChildren().addAll(sectionHeader, toolbar, tasksContainer);
        mainContent.getChildren().add(section);
    }

    /** Session section: controls + FlowPane of session cards. */
    private void createSessionSection(VBox mainContent) {
        VBox sessionSection = new VBox(15);
        sessionSection.getStyleClass().add("section-card");
        sessionSection.setPadding(new Insets(20));

        Label sessionTitle = new Label("✎ Study Session");
        TaskStyleUtils.fontBold(sessionTitle, 18);

        HBox sessionControls = new HBox(15);
        sessionControls.setAlignment(Pos.CENTER_LEFT);

        Button startSessionBtn = new Button("Start Session");
        startSessionBtn.getStyleClass().add("btn-success");

        Button endSessionBtn = new Button("End Session");
        endSessionBtn.getStyleClass().add("btn-danger");
        endSessionBtn.setDisable(true);

        Label sessionStatus = new Label("No active session");
        TaskStyleUtils.fontNormal(sessionStatus, 14);

        startSessionBtn.setOnAction(e -> {
            currentSession = studyService.startStudySession();
            currentSession.startSession();
            startSessionTimer(sessionStatus);
            startSessionBtn.setDisable(true);
            endSessionBtn.setDisable(false);
            sessionTextArea.setDisable(false);
            sessionTextArea.clear();
        });

        endSessionBtn.setOnAction(e -> {
            if (currentSession != null) {
                currentSession.setSessionText(sessionTextArea.getText());
                stopSessionTimer();
                showEndSessionDialog();
                javafx.application.Platform.runLater(() -> {
                    sessionStatus.setText("No active session");
                    startSessionBtn.setDisable(false);
                    endSessionBtn.setDisable(true);
                    sessionTextArea.setDisable(true);
                    updateSessionsDisplay();
                });
            }
        });

        sessionControls.getChildren().addAll(startSessionBtn, endSessionBtn, sessionStatus);

        sessionTextArea = new TextArea();
        sessionTextArea.setPromptText("Write your session notes, thoughts, or study content here…");
        sessionTextArea.setPrefRowCount(5);
        sessionTextArea.setWrapText(true);
        sessionTextArea.setDisable(true);

        Label completedLabel = new Label("☑ Today's Completed Sessions");
        TaskStyleUtils.fontBold(completedLabel, 14);

        // FlowPane for compact session cards
        sessionsFlowPane = new FlowPane(10, 10);
        sessionsFlowPane.setPrefWrapLength(Double.MAX_VALUE);
        sessionsFlowPane.setPadding(new Insets(5, 0, 0, 0));

        sessionSection.getChildren().addAll(
                sessionTitle, sessionControls, sessionTextArea, completedLabel, sessionsFlowPane);
        mainContent.getChildren().add(sessionSection);
    }

    private void createReflectionSection(VBox mainContent) {
        VBox reflectionSection = new VBox(15);
        reflectionSection.getStyleClass().add("section-card");
        reflectionSection.setPadding(new Insets(20));

        Label reflectionTitle = new Label("Daily Reflection");
        TaskStyleUtils.fontBold(reflectionTitle, 18);

        reflectionArea = new TextArea();
        reflectionArea.setPromptText("What helped you focus today?\nWhat distracted you?\nOne thing to improve tomorrow?");
        reflectionArea.setPrefRowCount(4);
        reflectionArea.setWrapText(true);

        Button saveReflectionBtn = new Button("Save Reflection");
        saveReflectionBtn.getStyleClass().add("btn-purple");
        saveReflectionBtn.setOnAction(e -> saveReflection());

        reflectionSection.getChildren().addAll(reflectionTitle, reflectionArea, saveReflectionBtn);
        mainContent.getChildren().add(reflectionSection);
    }

    // ──────────────────────────────────────────────
    // TASKS DISPLAY
    // ──────────────────────────────────────────────

    private void updateTasksDisplay() {
        tasksContainer.getChildren().clear();

        List<Task> tasks = new ArrayList<>(taskService.getTasksForDate(displayDate));
        LocalDate today = dateTimeService.getCurrentDate();

        if (tasks.isEmpty()) {
            // No tasks for this day — offer "Create Task" shortcut
            VBox emptyBox = new VBox(8);
            emptyBox.setAlignment(Pos.CENTER_LEFT);
            Label emptyLabel = new Label("No tasks scheduled for this day.");
            TaskStyleUtils.fontNormal(emptyLabel, 14);
            emptyLabel.setTextFill(Color.web("#7f8c8d"));

            Button createTaskBtn = new Button("+ Create Task");
            createTaskBtn.getStyleClass().add("btn-primary");
            createTaskBtn.setOnAction(e -> showCreateTaskDialog());

            emptyBox.getChildren().addAll(emptyLabel, createTaskBtn);
            tasksContainer.getChildren().add(emptyBox);
            // Do NOT return — unlinked goals and re-plan section must still render
        } else {
            // Apply user-selected sort order
            tasks.sort(taskSortComparator());

            // Apply grouping (or flat list)
            if ("None".equals(currentGroup)) {
                for (Task task : tasks) {
                    tasksContainer.getChildren().add(buildTaskRow(task));
                }
            } else {
                LinkedHashMap<String, List<Task>> groups = new LinkedHashMap<>();
                for (Task task : tasks) {
                    String key = groupKeyFor(task);
                    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(task);
                }
                for (Map.Entry<String, List<Task>> entry : groups.entrySet()) {
                    Label groupHeader = new Label(entry.getKey());
                    TaskStyleUtils.fontBold(groupHeader, 13);
                    groupHeader.setTextFill(Color.web("#6c757d"));
                    groupHeader.setPadding(new Insets(6, 0, 2, 0));
                    tasksContainer.getChildren().add(groupHeader);
                    for (Task task : entry.getValue()) {
                        tasksContainer.getChildren().add(buildTaskRow(task));
                    }
                }
            }
        }

        // Missed recurring-task occurrences (carry-forward to today)
        if (displayDate.equals(today)) {
            List<MissedOccurrence> missed = taskService.getMissedRecurringOccurrences(today);
            Set<String> shownTaskIds = tasks.stream().map(Task::getId).collect(Collectors.toSet());
            LinkedHashMap<String, List<MissedOccurrence>> byTask = new LinkedHashMap<>();
            for (MissedOccurrence mo : missed) {
                byTask.computeIfAbsent(mo.task().getId(), k -> new ArrayList<>()).add(mo);
            }

            if (!byTask.isEmpty()) {
                VBox missedSection = new VBox(6);
                missedSection.setPadding(new Insets(10, 0, 0, 0));
                Label missedTitle = new Label("\u26A0 Missed recurring tasks:");
                TaskStyleUtils.fontBold(missedTitle, 13);
                missedTitle.setTextFill(Color.web(TaskStyleUtils.MISSED_COLOR));
                missedSection.getChildren().add(missedTitle);

                for (var entry : byTask.entrySet()) {
                    Task task = entry.getValue().get(0).task();
                    List<MissedOccurrence> occurrences = entry.getValue();
                    boolean alsoScheduledToday = shownTaskIds.contains(task.getId());
                    missedSection.getChildren().add(
                            buildMissedTaskRow(task, occurrences, alsoScheduledToday));
                }
                tasksContainer.getChildren().add(missedSection);
            }
        }

        // Unlinked goals section (goals with no task)
        List<StudyGoal> allUnlinked = StudyGoal.findUnlinkedForDate(displayDate);
        List<StudyGoal> unlinkedGoals = allUnlinked.stream()
                .filter(g -> !g.isAchieved()).toList();
        List<StudyGoal> completedUnlinked = allUnlinked.stream()
                .filter(StudyGoal::isAchieved).toList();
        if (!unlinkedGoals.isEmpty() || !completedUnlinked.isEmpty()) {
            VBox unlinkedSection = new VBox(6);
            unlinkedSection.setPadding(new Insets(10, 0, 0, 0));
            Label unlinkedTitle = new Label("Goals without a task:");
            TaskStyleUtils.fontBold(unlinkedTitle, 13);
            unlinkedTitle.setTextFill(Color.web("#6c757d"));
            unlinkedSection.getChildren().add(unlinkedTitle);
            for (StudyGoal goal : unlinkedGoals) {
                unlinkedSection.getChildren().add(buildGoalRow(goal, null));
            }
            if (!completedUnlinked.isEmpty()) {
                unlinkedSection.getChildren().add(
                        buildCompletedGoalsSection(completedUnlinked, null, unlinkedSection));
            }
            tasksContainer.getChildren().add(unlinkedSection);
        }

        // Re-plan section — only available when viewing today
        if (displayDate.equals(today)) {
            tasksContainer.getChildren().add(buildReplanSection());
        }
    }

    /** Returns a comparator based on the user's current sort choice. */
    private Comparator<Task> taskSortComparator() {
        return switch (currentSort) {
            case "Priority" -> Comparator.comparingInt(
                    (Task t) -> t.getPriority() != null ? t.getPriority().stars() : 0).reversed()
                    .thenComparing(Task::getTitle, String.CASE_INSENSITIVE_ORDER);
            case "Deadline" -> Comparator.comparing(
                    (Task t) -> t.getDeadline() != null ? t.getDeadline() : LocalDate.MAX)
                    .thenComparing(Task::getTitle, String.CASE_INSENSITIVE_ORDER);
            case "Title" -> Comparator.comparing(Task::getTitle, String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparingInt((Task t) -> statusOrder(t.getStatus()))
                    .thenComparing(Task::getTitle, String.CASE_INSENSITIVE_ORDER);
        };
    }

    /** Defines a display-friendly ordering for task statuses. */
    private static int statusOrder(TaskStatus s) {
        return switch (s) {
            case IN_PROGRESS -> 0;
            case OPEN -> 1;
            case DELAYED -> 2;
            case POSTPONED -> 3;
            case COMPLETED -> 4;
            case CANCELLED -> 5;
        };
    }

    /** Returns the grouping key for a task based on the current group choice. */
    private String groupKeyFor(Task task) {
        return switch (currentGroup) {
            case "Status" -> task.getStatus().name();
            case "Category" -> task.getCategory() != null && !task.getCategory().isBlank()
                    ? task.getCategory() : "Uncategorized";
            default -> "";
        };
    }

    /**
     * Builds the "Re-plan a delayed goal for today" section.
     * Shows a ComboBox of delayed goals (from non-cancelled/non-postponed tasks)
     * and a button to reschedule the selected goal to appear today exactly once.
     */
    private VBox buildReplanSection() {
        List<StudyGoal> candidates = studyService.getDelayedGoalsForReplanning();

        VBox section = new VBox(8);
        section.setPadding(new Insets(14, 0, 0, 0));

        // Collapsible header row
        Label header = new Label("\u21BA Re-plan a Delayed Goal");
        TaskStyleUtils.fontBold(header, 13);
        header.setTextFill(Color.web("#6c757d"));

        if (candidates.isEmpty()) {
            Label none = new Label("No delayed goals available to re-plan.");
            TaskStyleUtils.fontNormal(none, 12);
            none.setTextFill(Color.web("#7f8c8d"));
            section.getChildren().addAll(header, none);
            return section;
        }

        // Preload task titles once to avoid DB lookups during ComboBox cell rendering.
        Map<String, String> taskTitles = new LinkedHashMap<>();
        for (StudyGoal goal : candidates) {
            String taskId = goal.getTaskId();
            if (taskId == null || taskId.isBlank() || taskTitles.containsKey(taskId)) {
                continue;
            }
            Task.findById(taskId).ifPresent(t -> taskTitles.put(taskId, t.getTitle()));
        }

        ComboBox<StudyGoal> combo = new ComboBox<>();
        combo.getItems().addAll(candidates);
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(StudyGoal goal, boolean empty) {
                super.updateItem(goal, empty);
                setText(empty || goal == null ? "" : formatDelayedGoal(goal, taskTitles));
            }
        });
        combo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(StudyGoal goal, boolean empty) {
                super.updateItem(goal, empty);
                setText(empty || goal == null ? "" : formatDelayedGoal(goal, taskTitles));
            }
        });
        combo.setPromptText("Select a delayed goal to re-plan...");

        Button replanBtn = new Button("\u21BA Re-plan for Today");
        replanBtn.getStyleClass().addAll("btn-orange", "btn-small");
        replanBtn.setDisable(true);

        combo.setOnAction(e -> replanBtn.setDisable(combo.getValue() == null));

        replanBtn.setOnAction(e -> {
            StudyGoal selected = combo.getValue();
            if (selected == null) return;
            studyService.replanGoalForToday(selected.getId());
            updateTasksDisplay();
            updateProgress();
        });

        HBox controls = new HBox(8, combo, replanBtn);
        controls.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(combo, Priority.ALWAYS);

        section.getChildren().addAll(header, controls);
        return section;
    }

    /** Formats a delayed goal for display in the re-plan ComboBox. */
    private String formatDelayedGoal(StudyGoal goal, Map<String, String> taskTitles) {
        String taskLabel = "";
        String taskId = goal.getTaskId();
        if (taskId != null && !taskId.isBlank()) {
            String title = taskTitles.get(taskId);
            if (title != null && !title.isBlank()) {
                taskLabel = title + ": ";
            }
        }
        String desc = goal.getDescription();
        if (desc != null && desc.length() > 60) {
            desc = desc.substring(0, 57) + "...";
        }
        return taskLabel + desc + " (" + goal.getDaysDelayed() + "d delayed)";
    }

    /**
     * Builds a clickable task card that expands to show linked goals.
     */
    private VBox buildTaskRow(Task task) {
        VBox card = new VBox();
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8;" +
                      " -fx-border-color: " + TaskStyleUtils.taskBorderColor(task, displayDate) + ";" +
                      " -fx-border-radius: 8;" +
                      " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 4, 0, 0, 1);");

        // ── Header row (always visible, clickable) ──
        HBox headerRow = new HBox(10);
        headerRow.setPadding(new Insets(12, 14, 12, 14));
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setStyle("-fx-cursor: hand;");

        Label arrow = new Label("▶");
        TaskStyleUtils.fontBold(arrow, 11);
        arrow.setTextFill(Color.web("#7f8c8d"));

        Label taskTitle = new Label(task.getTitle());
        TaskStyleUtils.fontBold(taskTitle, 14);

        Label priorityLabel = new Label(task.getPriority() != null ? task.getPriority().toString() : "");
        priorityLabel.setTextFill(Color.web("#f39c12"));

        Label statusBadge = new Label(task.getStatus().name());
        statusBadge.setPadding(new Insets(2, 6, 2, 6));
        statusBadge.setStyle("-fx-background-color: " + TaskStyleUtils.statusBadgeBg(task.getStatus()) +
                             "; -fx-background-radius: 10;");
        TaskStyleUtils.fontBold(statusBadge, 10);
        statusBadge.setTextFill(TaskStyleUtils.statusTextColor(task.getStatus()));

        if (task.isRecurring()) {
            Label recurBadge = new Label("\uD83D\uDD01");
            TaskStyleUtils.fontNormal(recurBadge, 12);
            recurBadge.setTooltip(new Tooltip(task.getRecurringSummary()));
            headerRow.getChildren().add(recurBadge);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        headerRow.getChildren().addAll(0, List.of(arrow)); // arrow first
        headerRow.getChildren().addAll(taskTitle, priorityLabel, spacer);

        // Overdue / due-today badge (between spacer and status badge)
        if (TaskStyleUtils.isOverdue(task, displayDate)) {
            headerRow.getChildren().add(TaskStyleUtils.createOverdueBadge());
        } else if (TaskStyleUtils.isDueToday(task, displayDate)) {
            headerRow.getChildren().add(TaskStyleUtils.createDueTodayBadge());
        }

        headerRow.getChildren().add(statusBadge);

        // ── Expandable goals panel ──
        VBox goalsPanel = new VBox(6);
        goalsPanel.setPadding(new Insets(0, 14, 12, 28));

        // Restore previous expand state so panels survive UI rebuilds
        boolean wasExpanded = expandedTaskIds.contains(task.getId());
        goalsPanel.setVisible(wasExpanded);
        goalsPanel.setManaged(wasExpanded);
        if (wasExpanded) {
            arrow.setText("▼");
            populateGoalsPanel(goalsPanel, task);
        }

        // Toggle expand/collapse on header click
        headerRow.setOnMouseClicked(e -> {
            boolean nowVisible = !goalsPanel.isVisible();
            goalsPanel.setVisible(nowVisible);
            goalsPanel.setManaged(nowVisible);
            arrow.setText(nowVisible ? "▼" : "▶");
            if (nowVisible) {
                expandedTaskIds.add(task.getId());
                populateGoalsPanel(goalsPanel, task);
            } else {
                expandedTaskIds.remove(task.getId());
            }
        });

        card.getChildren().addAll(headerRow, goalsPanel);
        return card;
    }

    /**
     * Builds a compact card for a recurring task with missed past occurrences.
     * Shows the task title with one "Missed [Day]" badge per missed date.
     *
     * @param task               the recurring task
     * @param occurrences        the missed occurrence dates (non-empty)
     * @param alsoScheduledToday true if the task is also shown in the regular
     *                           task list (avoids duplicating the full card)
     */
    private VBox buildMissedTaskRow(Task task, List<MissedOccurrence> occurrences,
                                    boolean alsoScheduledToday) {
        VBox card = new VBox(4);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8;" +
                      " -fx-border-color: " + TaskStyleUtils.MISSED_COLOR + ";" +
                      " -fx-border-radius: 8;" +
                      " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 4, 0, 0, 1);");
        card.setPadding(new Insets(10, 14, 10, 14));

        // Title row
        HBox headerRow = new HBox(8);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Label taskTitle = new Label(task.getTitle());
        TaskStyleUtils.fontBold(taskTitle, 13);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        headerRow.getChildren().addAll(taskTitle, spacer);

        // One badge per missed date
        for (MissedOccurrence mo : occurrences) {
            headerRow.getChildren().add(TaskStyleUtils.createMissedDayBadge(mo.missedDate()));
        }

        card.getChildren().add(headerRow);

        if (alsoScheduledToday) {
            Label hint = new Label("(also scheduled today \u2014 see above)");
            TaskStyleUtils.fontItalic(hint, 11);
            hint.setTextFill(Color.web("#7f8c8d"));
            card.getChildren().add(hint);
        }

        return card;
    }

    private void populateGoalsPanel(VBox goalsPanel, Task task) {
        goalsPanel.getChildren().clear();

        List<StudyGoal> allGoals = StudyGoal.findByTaskIdForDate(task.getId(), displayDate);
        List<StudyGoal> activeGoals = allGoals.stream().filter(g -> !g.isAchieved()).toList();
        List<StudyGoal> completedGoals = allGoals.stream().filter(StudyGoal::isAchieved).toList();

        if (activeGoals.isEmpty() && completedGoals.isEmpty()) {
            Label noGoals = new Label("No goals linked to this task yet.");
            TaskStyleUtils.fontNormal(noGoals, 12);
            noGoals.setTextFill(Color.web("#7f8c8d"));

            Button addGoalBtn = new Button("+ Create Goal");
            addGoalBtn.getStyleClass().addAll("btn-purple", "btn-small");
            addGoalBtn.setOnAction(e -> {
                showAddGoalDialog(task);
                populateGoalsPanel(goalsPanel, task); // refresh after add
            });

            goalsPanel.getChildren().addAll(noGoals, addGoalBtn);
        } else {
            for (StudyGoal goal : activeGoals) {
                goalsPanel.getChildren().add(buildGoalRow(goal, task));
            }
            Button addMoreBtn = new Button("+ Add Goal");
            addMoreBtn.getStyleClass().addAll("btn-purple", "btn-small");
            addMoreBtn.setOnAction(e -> {
                showAddGoalDialog(task);
                populateGoalsPanel(goalsPanel, task);
            });
            goalsPanel.getChildren().add(addMoreBtn);
        }

        // Completed goals — collapsible section
        if (!completedGoals.isEmpty()) {
            goalsPanel.getChildren().add(
                    buildCompletedGoalsSection(completedGoals, task, goalsPanel));
        }
    }

    private HBox buildGoalRow(StudyGoal goal, Task linkedTask) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 8, 6, 8));

        String bgColor = "#f8f9fa";
        if (goal.isDelayed()) {
            bgColor = goal.getDelayColorIntensity() < 0.5 ? "#fff3cd" : "#f8d7da";
        }
        row.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 5;");

        CheckBox check = new CheckBox();
        check.setSelected(goal.isAchieved());
        check.setOnAction(e -> {
            studyService.updateStudyGoalAchievement(goal.getId(), check.isSelected(), null);
            updateProgress();
            updateTasksDisplay();
        });

        VBox textBox = new VBox(2);
        Label goalLabel = new Label(goal.getDescription());
        if (goal.isAchieved()) {
            goalLabel.setStyle("-fx-strikethrough: true; -fx-text-fill: #7f8c8d;");
        }
        TaskStyleUtils.fontNormal(goalLabel, 13);
        textBox.getChildren().add(goalLabel);

        if (goal.isDelayed()) {
            Label delayLabel = new Label(String.format("Delayed %d day(s) — -%d pts",
                    goal.getDaysDelayed(), goal.getPointsDeducted()));
            delayLabel.setStyle("-fx-text-fill: #dc3545;");
            TaskStyleUtils.fontNormal(delayLabel, 11);
            textBox.getChildren().add(delayLabel);
        }

        row.getChildren().addAll(check, textBox);
        return row;
    }

    /**
     * Builds a collapsible "Completed" section for achieved goals.
     * Clicking the header toggles visibility of the completed goal rows.
     */
    private VBox buildCompletedGoalsSection(List<StudyGoal> completedGoals,
                                            Task linkedTask, VBox parentPanel) {
        VBox section = new VBox(4);
        section.setPadding(new Insets(6, 0, 0, 0));

        VBox itemsBox = new VBox(4);
        itemsBox.setVisible(false);
        itemsBox.setManaged(false);

        Label toggle = new Label("▶ Completed (" + completedGoals.size() + ")");
        TaskStyleUtils.fontBold(toggle, 11);
        toggle.setTextFill(Color.web("#27ae60"));
        toggle.setStyle("-fx-cursor: hand;");
        toggle.setOnMouseClicked(e -> {
            boolean show = !itemsBox.isVisible();
            itemsBox.setVisible(show);
            itemsBox.setManaged(show);
            toggle.setText((show ? "▼" : "▶") + " Completed (" + completedGoals.size() + ")");
        });

        for (StudyGoal goal : completedGoals) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4, 8, 4, 8));
            row.setStyle("-fx-background-color: #eafaf1; -fx-background-radius: 5;");

            CheckBox check = new CheckBox();
            check.setSelected(true);
            check.setOnAction(e -> {
                studyService.updateStudyGoalAchievement(goal.getId(), false, null);
                updateProgress();
                updateTasksDisplay();
            });

            Label label = new Label(goal.getDescription());
            label.setStyle("-fx-strikethrough: true; -fx-text-fill: #7f8c8d;");
            TaskStyleUtils.fontNormal(label, 12);

            row.getChildren().addAll(check, label);
            itemsBox.getChildren().add(row);
        }

        section.getChildren().addAll(toggle, itemsBox);
        return section;
    }

    // ──────────────────────────────────────────────
    // SESSION DISPLAY (FlowPane)
    // ──────────────────────────────────────────────

    private void updateSessionsDisplay() {
        sessionsFlowPane.getChildren().clear();
        List<StudySession> sessions = studyService.getSessionsForDate(displayDate);

        for (StudySession session : sessions) {
            if (session.isCompleted()) {
                sessionsFlowPane.getChildren().add(buildSessionCard(session));
            }
        }

        if (sessionsFlowPane.getChildren().isEmpty()) {
            Label none = new Label("No completed sessions yet.");
            TaskStyleUtils.fontNormal(none, 13);
            none.setTextFill(Color.web("#7f8c8d"));
            sessionsFlowPane.getChildren().add(none);
        }
    }

    /** Compact session card for the FlowPane. */
    private VBox buildSessionCard(StudySession session) {
        VBox card = new VBox(5);
        card.setPadding(new Insets(10, 12, 10, 12));
        card.setPrefWidth(200);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8;" +
                      " -fx-border-color: #3498db; -fx-border-radius: 8;" +
                      " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 4, 0, 0, 1);");

        String startStr = session.getStartTime() != null
                ? session.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "?";
        String endStr = session.getEndTime() != null
                ? session.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "?";

        Label timeLabel = new Label("⏰ " + startStr + "–" + endStr);
        TaskStyleUtils.fontBold(timeLabel, 12);

        Label durationLabel = new Label(session.getDurationMinutes() + " min");
        TaskStyleUtils.fontNormal(durationLabel, 11);
        durationLabel.setTextFill(Color.web("#7f8c8d"));

        // Focus stars (compact)
        StringBuilder stars = new StringBuilder();
        for (int i = 1; i <= 5; i++) stars.append(i <= session.getFocusLevel() ? "★" : "☆");
        Label focusLabel = new Label(stars.toString());
        TaskStyleUtils.fontNormal(focusLabel, 12);
        focusLabel.setTextFill(Color.web("#f39c12"));

        Label pointsLabel = new Label("♦ " + session.getPointsEarned() + " pts");
        TaskStyleUtils.fontSemiBold(pointsLabel, 11);
        pointsLabel.setTextFill(Color.web("#27ae60"));

        // Action buttons row
        HBox btnRow = new HBox(5);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        Button detailsBtn = new Button("Details");
        detailsBtn.getStyleClass().addAll("btn-primary", "btn-small");
        detailsBtn.setStyle("-fx-padding: 3 7;");
        detailsBtn.setOnAction(e -> showSessionDetails(session));

        Button deleteBtn = new Button("✕");
        deleteBtn.getStyleClass().addAll("btn-danger", "btn-small");
        deleteBtn.setStyle("-fx-padding: 3 7;");
        deleteBtn.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete this session?", ButtonType.OK, ButtonType.CANCEL);
            alert.setHeaderText(null);
            alert.initOwner(this.getScene() != null ? this.getScene().getWindow() : null);
            alert.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.OK) {
                    studyService.deleteStudySession(session.getId());
                    updateSessionsDisplay();
                }
            });
        });

        btnRow.getChildren().addAll(detailsBtn, deleteBtn);
        card.getChildren().addAll(timeLabel, durationLabel, focusLabel, pointsLabel, btnRow);
        return card;
    }

    // ──────────────────────────────────────────────
    // DIALOGS
    // ──────────────────────────────────────────────

    private void showCreateTaskDialog() {
        VBox form = new VBox(10);
        form.setPadding(new Insets(20));
        form.getStyleClass().add("modal-content");
        form.setMaxWidth(480);
        form.setMaxHeight(Region.USE_PREF_SIZE);

        Label formTitle = new Label("Create a new task");
        TaskStyleUtils.fontBold(formTitle, 16);

        // Title
        TextField titleField = new TextField();
        titleField.setPromptText("Task title *");
        titleField.setMaxWidth(Double.MAX_VALUE);

        // Description
        TextArea descArea = new TextArea();
        descArea.setPromptText("Description (optional)");
        descArea.setPrefRowCount(3);
        descArea.setWrapText(true);
        descArea.setMaxWidth(Double.MAX_VALUE);

        // Category
        ComboBox<TaskCategory> catCombo = new ComboBox<>();
        catCombo.setPromptText("Category *");
        catCombo.setMaxWidth(Double.MAX_VALUE);
        catCombo.getItems().addAll(categoryService.getCategories());

        // New category inline
        TextField newCatField = new TextField();
        newCatField.setPromptText("Or type new category name…");
        Button addCatBtn = new Button("+ Add");
        addCatBtn.getStyleClass().addAll("btn-primary", "btn-small");
        addCatBtn.setOnAction(e -> {
            String name = newCatField.getText().trim();
            if (name.isEmpty()) return;
            try {
                if (categoryService.getCategories().stream().noneMatch(c -> c.name().equalsIgnoreCase(name))) {
                    categoryService.addCategory(name);
                }
                catCombo.getItems().setAll(categoryService.getCategories());
                catCombo.getItems().stream().filter(c -> c.name().equalsIgnoreCase(name))
                        .findFirst().ifPresent(catCombo::setValue);
                newCatField.clear();
            } catch (Exception ex) {
                showInlineError(form, "Could not add category: " + ex.getMessage());
            }
        });
        HBox newCatRow = new HBox(8, newCatField, addCatBtn);
        HBox.setHgrow(newCatField, Priority.ALWAYS);
        newCatRow.setAlignment(Pos.CENTER_LEFT);

        // Priority
        ComboBox<Integer> priorityCombo = new ComboBox<>();
        priorityCombo.setPromptText("Priority *");
        priorityCombo.setMaxWidth(Double.MAX_VALUE);
        priorityCombo.getItems().addAll(5, 4, 3, 2, 1);
        priorityCombo.setCellFactory(lv -> starCell());
        priorityCombo.setButtonCell(starCell());

        // Deadline
        DatePicker deadlinePicker = new DatePicker();
        deadlinePicker.setPromptText("Deadline (optional)");
        deadlinePicker.setMaxWidth(Double.MAX_VALUE);

        // Recurring
        CheckBox recurringCheck = new CheckBox("Recurring task");
        TaskStyleUtils.fontBold(recurringCheck, 12);

        VBox recurringOptions = new VBox(8);
        recurringOptions.setPadding(new Insets(8));
        recurringOptions.setStyle("-fx-background-color: #f0f8ff; -fx-background-radius: 5;"
                + " -fx-border-color: #b0d4f1; -fx-border-radius: 5;");
        recurringOptions.setVisible(false);
        recurringOptions.setManaged(false);

        Spinner<Integer> intervalSpinner = new Spinner<>(1, 4, 1);
        intervalSpinner.setPrefWidth(70);
        HBox intervalRow = new HBox(8, intervalSpinner, new Label("week(s)"));
        intervalRow.setAlignment(Pos.CENTER_LEFT);

        String[] dayLabels = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        CheckBox[] dayBoxes = new CheckBox[7];
        HBox daysRow = new HBox(6);
        daysRow.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < 7; i++) {
            dayBoxes[i] = new CheckBox(dayLabels[i]);
            TaskStyleUtils.fontNormal(dayBoxes[i], 11);
            daysRow.getChildren().add(dayBoxes[i]);
        }

        DatePicker startDatePicker = new DatePicker(LocalDate.now());
        startDatePicker.setMaxWidth(Double.MAX_VALUE);

        recurringOptions.getChildren().addAll(new Label("Repeat every:"), intervalRow,
                new Label("On days:"), daysRow,
                new Label("Start date:"), startDatePicker);

        Label deadlineHint = new Label("");
        TaskStyleUtils.fontNormal(deadlineHint, 10);
        deadlineHint.setTextFill(Color.web("#7f8c8d"));
        deadlineHint.setWrapText(true);
        deadlineHint.setVisible(false);
        deadlineHint.setManaged(false);

        recurringCheck.selectedProperty().addListener((obs, o, n) -> {
            recurringOptions.setVisible(n);
            recurringOptions.setManaged(n);
            deadlineHint.setVisible(n);
            deadlineHint.setManaged(n);
            deadlineHint.setText(n ? "For recurring tasks, the deadline acts as the end-of-recurrence date." : "");
        });

        // Buttons
        Button createBtn = new Button("Create Task");
        createBtn.getStyleClass().add("btn-primary");
        createBtn.setStyle("-fx-padding: 8 18;");
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-cancel");
        cancelBtn.setStyle("-fx-padding: 8 18;");
        cancelBtn.setOnAction(e -> closeModal.run());

        createBtn.setOnAction(e -> {
            String title = titleField.getText().trim();
            TaskCategory cat = catCombo.getValue();
            Integer prio = priorityCombo.getValue();
            if (title.isEmpty() || cat == null || prio == null) {
                showInlineError(form, "Title, category and priority are required.");
                return;
            }

            String recurPattern = "";
            if (recurringCheck.isSelected()) {
                StringBuilder days = new StringBuilder();
                for (int i = 0; i < 7; i++) {
                    if (dayBoxes[i].isSelected()) {
                        if (days.length() > 0) days.append(",");
                        days.append(i + 1);
                    }
                }
                if (days.length() == 0) {
                    showInlineError(form, "Select at least one day for the recurring schedule.");
                    return;
                }
                recurPattern = intervalSpinner.getValue() + ":" + days;
            }

            try {
                LocalDate startDate = recurringCheck.isSelected() ? startDatePicker.getValue() : null;
                Task newTask = new Task(null, title, descArea.getText().trim(),
                        cat.name(), new TaskPriority(prio),
                        deadlinePicker.getValue(), TaskStatus.OPEN, 0, recurPattern, startDate);
                taskService.addTask(newTask);
                closeModal.run();
                updateTasksDisplay();
            } catch (Exception ex) {
                showInlineError(form, ex.getMessage());
            }
        });

        HBox btnRow = new HBox(10, createBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(8, 0, 0, 0));

        form.getChildren().addAll(formTitle,
                new Label("Title:"), titleField,
                new Label("Description:"), descArea,
                new Label("Category:"), catCombo, newCatRow,
                new Label("Priority:"), priorityCombo,
                new Label("Deadline:"), deadlinePicker, deadlineHint,
                recurringCheck, recurringOptions, btnRow);

        ScrollPane wrapper = new ScrollPane(form);
        wrapper.setFitToWidth(true);
        wrapper.getStyleClass().add("transparent-bg");
        wrapper.setMaxWidth(520);
        wrapper.setMaxHeight(600);
        showModal.accept(wrapper);
    }

    private void showInlineError(VBox form, String message) {
        form.getChildren().removeIf(n -> "error-label".equals(n.getUserData()));
        Label err = new Label("⚠ " + message);
        err.setUserData("error-label");
        TaskStyleUtils.fontNormal(err, 12);
        err.setTextFill(Color.web("#e74c3c"));
        err.setWrapText(true);
        form.getChildren().add(err);
    }

    private ListCell<Integer> starCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(new TaskPriority(item).toString() + " (" + item + " star" + (item == 1 ? "" : "s") + ")");
                }
            }
        };
    }

    private void showAddGoalDialog(Task linkedTask) {
        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("modal-content");
        content.setMaxWidth(450);
        content.setMaxHeight(Region.USE_PREF_SIZE);

        Label headerLabel = new Label(linkedTask != null
                ? "Add goal for: " + linkedTask.getTitle()
                : "Create a new study goal");
        TaskStyleUtils.fontBold(headerLabel, 16);

        // Date
        DatePicker datePicker = new DatePicker(displayDate);
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (item.isBefore(dateTimeService.getCurrentDate())) {
                    setDisable(true);
                    setStyle("-fx-background-color: #e0e0e0;");
                }
            }
        });

        HBox dateRow = new HBox(8, new Label("Date:"), datePicker);
        dateRow.setAlignment(Pos.CENTER_LEFT);

        TextArea descArea = new TextArea();
        descArea.setPromptText("Goal description…");
        descArea.setPrefRowCount(3);
        descArea.setWrapText(true);

        // Task selector (only when no task pre-linked)
        ComboBox<Task> taskCombo = new ComboBox<>();
        VBox taskSection = new VBox(4);
        if (linkedTask == null) {
            Label taskLabel = new Label("Link to task (optional):");
            TaskStyleUtils.fontBold(taskLabel, 12);
            taskCombo.setPromptText("None");
            taskCombo.setMaxWidth(Double.MAX_VALUE);
            taskCombo.getItems().add(null);
            taskCombo.getItems().addAll(taskService.getActiveTasks());
            taskCombo.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(Task item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "None" : item.getTitle());
                }
            });
            taskCombo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(Task item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "None" : item.getTitle());
                }
            });
            taskSection.getChildren().addAll(taskLabel, taskCombo);
            content.getChildren().addAll(headerLabel, dateRow,
                    new Label("Description:"), descArea, taskSection);
        } else {
            content.getChildren().addAll(headerLabel, dateRow,
                    new Label("Description:"), descArea);
        }

        Button okBtn = new Button("Add Goal");
        okBtn.getStyleClass().add("btn-purple");
        okBtn.setDisable(true);
        descArea.textProperty().addListener((obs, o, n) -> okBtn.setDisable(n.trim().isEmpty()));

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-cancel");
        cancelBtn.setOnAction(e -> closeModal.run());

        okBtn.setOnAction(e -> {
            String desc = descArea.getText().trim();
            if (desc.isEmpty()) return;
            LocalDate date = datePicker.getValue() != null ? datePicker.getValue() : displayDate;
            String taskId = linkedTask != null ? linkedTask.getId()
                    : (taskCombo.getValue() != null ? taskCombo.getValue().getId() : null);
            try {
                studyService.addStudyGoal(desc, date, taskId);
                closeModal.run();
                updateTasksDisplay();
                updateProgress();
            } catch (Exception ex) {
                Label err = new Label("⚠ " + ex.getMessage());
                err.setTextFill(Color.web("#e74c3c"));
                content.getChildren().add(err);
            }
        });

        HBox btnRow = new HBox(10, okBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        content.getChildren().add(btnRow);

        showModal.accept(content);
    }

    private void showEndSessionDialog() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("modal-content");
        content.setMaxWidth(400);
        content.setMaxHeight(Region.USE_PREF_SIZE);

        Label headerLabel = new Label("How was your study session?");
        TaskStyleUtils.fontBold(headerLabel, 16);

        Label focusLabel = new Label("Focus Level (1–5):");
        Slider focusSlider = new Slider(1, 5, 3);
        focusSlider.setShowTickLabels(true);
        focusSlider.setShowTickMarks(true);
        focusSlider.setMajorTickUnit(1);
        focusSlider.setSnapToTicks(true);

        Label focusWarning = new Label("• Average focus.");
        TaskStyleUtils.fontNormal(focusWarning, 11);
        focusWarning.setWrapText(true);
        focusWarning.setTextFill(Color.web("#f39c12"));

        focusSlider.valueProperty().addListener((obs, o, nv) -> {
            int lv = nv.intValue();
            if (lv <= 2) {
                focusWarning.setText("[!] Low focus — point penalties will apply.");
                focusWarning.setTextFill(Color.web("#e74c3c"));
            } else if (lv == 3) {
                focusWarning.setText("• Average focus.");
                focusWarning.setTextFill(Color.web("#f39c12"));
            } else {
                focusWarning.setText("[✓] Great focus! Bonus points incoming.");
                focusWarning.setTextFill(Color.web("#27ae60"));
            }
        });

        TextArea notesArea = new TextArea();
        notesArea.setPromptText("What did you accomplish?");
        notesArea.setPrefRowCount(3);

        Button okBtn = new Button("OK");
        okBtn.getStyleClass().add("btn-primary");
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-cancel");
        cancelBtn.setOnAction(e -> closeModal.run());

        okBtn.setOnAction(e -> {
            StudySessionEnd sessionEnd = new StudySessionEnd((int) focusSlider.getValue(), notesArea.getText());
            studyService.endStudySession(currentSession, sessionEnd);
            currentSession = null;
            closeModal.run();
            updateSessionsDisplay();
            updateProgress();
        });

        HBox btnRow = new HBox(10, cancelBtn, okBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        content.getChildren().addAll(headerLabel, focusLabel, focusSlider, focusWarning,
                new Label("Notes:"), notesArea, btnRow);
        showModal.accept(content);
    }

    /** Full session-details modal — identical to old implementation. */
    private void showSessionDetails(StudySession session) {
        VBox mainContent = new VBox(20);
        mainContent.setPadding(new Insets(20));
        mainContent.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        mainContent.setMaxWidth(700);
        mainContent.setMaxHeight(600);

        createSessionHeader(session, mainContent);

        TabPane detailsTabs = new TabPane();
        detailsTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        detailsTabs.getTabs().addAll(
                new Tab("◎ Overview", createOverviewTab(session)),
                new Tab("◎ Progress", createProgressTab(session)),
                new Tab("▪ Notes", createNotesTab(session)),
                new Tab("↯ Performance", createPerformanceTab(session))
        );
        mainContent.getChildren().add(detailsTabs);

        Button closeButton = new Button("Close");
        closeButton.getStyleClass().add("btn-primary");
        closeButton.setOnAction(e -> closeModal.run());
        HBox btnBox = new HBox(closeButton);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        mainContent.getChildren().add(btnBox);

        ScrollPane scrollPane = new ScrollPane(mainContent);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("transparent-bg");
        scrollPane.setMaxWidth(720);
        scrollPane.setMaxHeight(650);
        showModal.accept(scrollPane);
    }

    // ──────────────────────────────────────────────
    // SESSION DETAIL TABS (carried over)
    // ──────────────────────────────────────────────

    private void createSessionHeader(StudySession session, VBox mainContent) {
        VBox header = new VBox(10);
        header.setStyle("-fx-background-color: linear-gradient(to right, #667eea, #764ba2);" +
                        " -fx-background-radius: 10; -fx-padding: 20;");

        Label sessionTitle = new Label("✎ Study Session");
        TaskStyleUtils.fontBold(sessionTitle, 24);
        sessionTitle.setTextFill(Color.WHITE);

        Label dateTime = new Label(
                session.getDate().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")) +
                " • " + (session.getStartTime() != null
                        ? session.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "N/A"));
        TaskStyleUtils.fontNormal(dateTime, 16);
        dateTime.setTextFill(Color.web("#f8f9fa"));

        HBox metrics = new HBox(30);
        metrics.setAlignment(Pos.CENTER_LEFT);
        metrics.getChildren().addAll(
                createMetricBox("⌚", session.getDurationMinutes() + " min", "Duration"),
                createMetricBox("◎",
                        "★".repeat(session.getFocusLevel()) + "☆".repeat(5 - session.getFocusLevel()),
                        "Focus"),
                createMetricBox("♦", session.getPointsEarned() + " pts", "Points")
        );

        header.getChildren().addAll(sessionTitle, dateTime, metrics);
        mainContent.getChildren().add(header);
    }

    private VBox createMetricBox(String icon, String value, String label) {
        VBox box = new VBox(5);
        box.setAlignment(Pos.CENTER);
        Label iconL = new Label(icon);
        TaskStyleUtils.fontNormal(iconL, 20);
        Label valueL = new Label(value);
        TaskStyleUtils.fontBold(valueL, 18);
        valueL.setTextFill(Color.WHITE);
        Label descL = new Label(label);
        TaskStyleUtils.fontNormal(descL, 12);
        descL.setTextFill(Color.web("#f8f9fa"));
        box.getChildren().addAll(iconL, valueL, descL);
        return box;
    }

    private VBox createOverviewTab(StudySession session) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));

        VBox timeSection = new VBox(10);
        timeSection.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 10; -fx-padding: 15;");
        Label timeTitle = new Label("⏰ Time Breakdown");
        TaskStyleUtils.fontBold(timeTitle, 16);

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(10);
        grid.add(new Label("Start:"), 0, 0);
        grid.add(new Label(session.getStartTime() != null
                ? session.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : "N/A"), 1, 0);
        grid.add(new Label("End:"), 0, 1);
        grid.add(new Label(session.getEndTime() != null
                ? session.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : "N/A"), 1, 1);
        grid.add(new Label("Duration:"), 0, 2);
        grid.add(new Label(session.getDurationMinutes() + " minutes"), 1, 2);

        timeSection.getChildren().addAll(timeTitle, grid);
        content.getChildren().add(timeSection);
        return content;
    }

    private VBox createProgressTab(StudySession session) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));

        VBox focusSection = new VBox(15);
        focusSection.setStyle("-fx-background-color: #fff3cd; -fx-background-radius: 10; -fx-padding: 15;");
        Label focusTitle = new Label("◎ Focus & Productivity");
        TaskStyleUtils.fontBold(focusTitle, 16);

        HBox starsBox = new HBox(2);
        for (int i = 1; i <= 5; i++) {
            Label star = new Label(i <= session.getFocusLevel() ? "★" : "☆");
            TaskStyleUtils.fontNormal(star, 20);
            star.setTextFill(i <= session.getFocusLevel() ? Color.web("#f39c12") : Color.web("#bdc3c7"));
            starsBox.getChildren().add(star);
        }

        ProgressBar focusBar = new ProgressBar((double) session.getFocusLevel() / 5.0);
        focusBar.setPrefWidth(300);
        focusBar.setStyle("-fx-accent: #f39c12;");

        focusSection.getChildren().addAll(focusTitle, starsBox, focusBar);
        content.getChildren().add(focusSection);
        return content;
    }

    private VBox createNotesTab(StudySession session) {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        Label notesTitle = new Label("▪ Session Notes");
        TaskStyleUtils.fontBold(notesTitle, 18);

        String text = session.getSessionText();
        TextArea notesArea = new TextArea(text != null && !text.isBlank()
                ? text : "No notes recorded.");
        notesArea.setEditable(false);
        notesArea.setPrefRowCount(15);
        notesArea.setWrapText(true);

        content.getChildren().addAll(notesTitle, notesArea);
        return content;
    }

    private VBox createPerformanceTab(StudySession session) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));

        VBox compSection = new VBox(15);
        compSection.setStyle("-fx-background-color: #f0f8ff; -fx-background-radius: 10; -fx-padding: 15;");
        Label compTitle = new Label("▪ Performance Analysis");
        TaskStyleUtils.fontBold(compTitle, 16);

        List<StudySession> recent = studyService.getRecentStudySessions(7);
        if (recent.size() > 1) {
            double avgDur = recent.stream().mapToInt(StudySession::getDurationMinutes).average().orElse(0);
            double avgFocus = recent.stream().mapToInt(StudySession::getFocusLevel).average().orElse(0);
            GridPane grid = new GridPane();
            grid.setHgap(30);
            grid.setVgap(10);
            grid.add(new Label("Duration vs 7-day avg:"), 0, 0);
            grid.add(new Label(String.format("%.0f min  %s", avgDur,
                    session.getDurationMinutes() > avgDur ? "↑ Above" : "↓ Below")), 1, 0);
            grid.add(new Label("Focus vs 7-day avg:"), 0, 1);
            grid.add(new Label(String.format("%.1f  %s", avgFocus,
                    session.getFocusLevel() > avgFocus ? "↑ Above" : "↓ Below")), 1, 1);
            compSection.getChildren().addAll(compTitle, grid);
        } else {
            compSection.getChildren().addAll(compTitle,
                    new Label("Not enough data for comparison."));
        }

        content.getChildren().add(compSection);
        return content;
    }

    // ──────────────────────────────────────────────
    // TIMER / PROGRESS
    // ──────────────────────────────────────────────

    private void updateProgress() {
        // Progress is only meaningful for today
        if (!displayDate.equals(dateTimeService.getCurrentDate())) {
            dailyProgressBar.setProgress(0);
            progressLabel.setText("Progress tracking is only available for today.");
            return;
        }
        int progress = studyService.calculateDailyProgress();
        dailyProgressBar.setProgress(progress / 100.0);
        progressLabel.setText("Daily Progress: " + progress + "%");
    }

    private void startSessionTimer(Label statusLabel) {
        if (sessionTimer != null) sessionTimer.stop();
        sessionTimer = new Timeline(new KeyFrame(Duration.seconds(1),
                e -> updateSessionTimer(statusLabel)));
        sessionTimer.setCycleCount(Timeline.INDEFINITE);
        sessionTimer.play();
    }

    private void stopSessionTimer() {
        if (sessionTimer != null) {
            sessionTimer.stop();
            sessionTimer = null;
        }
    }

    private void updateSessionTimer(Label statusLabel) {
        if (currentSession != null && currentSession.isActive()) {
            currentSession.updateRealTimeProgress();
            int elapsed = currentSession.getCurrentElapsedMinutes();
            int h = elapsed / 60, m = elapsed % 60;
            statusLabel.setText(h > 0
                    ? String.format("⌚ Session running: %dh %02dm", h, m)
                    : String.format("⌚ Session running: %dm", m));
            statusLabel.setTextFill(Color.web("#27ae60"));
        } else {
            statusLabel.setText("No active session");
            statusLabel.setTextFill(Color.web("#2c3e50"));
        }
    }

    private void saveReflection() {
        String text = reflectionArea.getText().trim();
        if (text.isEmpty()) return;

        DailyReflection reflection = new DailyReflection();
        reflection.setDate(displayDate);
        reflection.setReflectionText(text);
        studyService.addDailyReflection(reflection);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("modal-content");
        content.setMaxWidth(300);
        content.setMaxHeight(Region.USE_PREF_SIZE);
        Label titleL = new Label("Reflection Saved");
        TaskStyleUtils.fontBold(titleL, 16);
        Button okBtn = new Button("OK");
        okBtn.getStyleClass().add("btn-primary");
        okBtn.setOnAction(e -> closeModal.run());
        HBox btnRow = new HBox(okBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        content.getChildren().addAll(titleL, new Label("Your reflection has been saved!"), btnRow);
        showModal.accept(content);
    }

    // ──────────────────────────────────────────────
    // DATE CHANGE LISTENER
    // ──────────────────────────────────────────────

    private void onDateChanged(LocalDate newDate) {
        // When the real date rolls over at midnight, snap the display to today
        displayDate = newDate;
        updateDisplay();
    }

    // ──────────────────────────────────────────────
    // RefreshablePanel
    // ──────────────────────────────────────────────

    @Override
    public void updateDisplay() {
        boolean isToday = displayDate.equals(dateTimeService.getCurrentDate());
        String prefix = isToday ? "▦ Today — " : "▦ ";
        dateNavLabel.setText(prefix + displayDate.format(
                DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")));

        updateTasksDisplay();
        updateSessionsDisplay();
        updateProgress();

        DailyReflection todayReflection = studyService.getDailyReflectionForDate(displayDate).orElse(null);
        reflectionArea.setText(todayReflection != null ? todayReflection.getReflectionText() : "");
    }

    @Override
    public Node getView() {
        return this;
    }

}
