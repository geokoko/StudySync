
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
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

    private enum PlannerTaskView {
        TODAY,
        ALL_TASKS
    }

    // Sort / group state for the tasks section
    private String currentSort = "Status";
    private String currentGroup = "None";
    private PlannerTaskView currentTaskView = PlannerTaskView.TODAY;

    // UI containers that get rebuilt on navigation
    private VBox tasksContainer;
    private FlowPane attemptOverviewContainer;
    private Label taskSectionTitle;
    private List<StudyGoal> displayDateAttempts = List.of();
    private Map<String, List<StudyGoal>> displayDateAttemptsByTaskId = Map.of();
    private Set<String> taskIdsWithAttemptsOnDisplayDate = Set.of();
    private FlowPane sessionsFlowPane;
    private TextArea reflectionArea;
    private ProgressBar dailyProgressBar;
    private Label progressLabel;
    private Label dateNavLabel;
    private final Label dateNavIcon = TaskStyleUtils.iconLabel("\u25A6", 22);
    private TextArea sessionTextArea;
    private Button startSessionBtn;
    private Button endSessionBtn;
    private Label sessionStatusLabel;

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

        Button prevBtn = new Button();
        prevBtn.setGraphic(TaskStyleUtils.iconLabel("\u25C0", 14));
        prevBtn.getStyleClass().add("btn-primary");
        prevBtn.setOnAction(e -> {
            displayDate = displayDate.minusDays(1);
            updateDisplay();
        });

        dateNavLabel = new Label();
        TaskStyleUtils.fontBold(dateNavLabel, 22);
        dateNavLabel.setMinWidth(300);
        dateNavLabel.setAlignment(Pos.CENTER);

        Button nextBtn = new Button();
        nextBtn.setGraphic(TaskStyleUtils.iconLabel("\u25B6", 14));
        nextBtn.getStyleClass().add("btn-primary");
        nextBtn.setOnAction(e -> {
            displayDate = displayDate.plusDays(1);
            updateDisplay();
        });

        Button todayBtn = new Button();
        todayBtn.setGraphic(TaskStyleUtils.iconLabel("\u00BB", 14));
        todayBtn.setText("Today");
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

        taskSectionTitle = new Label("Today's Tasks & Goals");
        taskSectionTitle.setGraphic(TaskStyleUtils.iconLabel("\u2611", 18));
        TaskStyleUtils.fontBold(taskSectionTitle, 18);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addGoalBtn = new Button("+ Add Goal");
        addGoalBtn.getStyleClass().add("btn-purple");
        addGoalBtn.setOnAction(e -> showAddGoalDialog(null));

        sectionHeader.getChildren().addAll(taskSectionTitle, spacer, addGoalBtn);

        attemptOverviewContainer = new FlowPane();
        attemptOverviewContainer.setHgap(10);
        attemptOverviewContainer.setVgap(8);
        attemptOverviewContainer.setAlignment(Pos.CENTER_LEFT);

        // Sort / group toolbar
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        ToggleButton todayViewBtn = new ToggleButton("Today");
        ToggleButton allTasksViewBtn = new ToggleButton("All Tasks");
        ToggleGroup viewGroup = new ToggleGroup();
        todayViewBtn.setToggleGroup(viewGroup);
        allTasksViewBtn.setToggleGroup(viewGroup);
        todayViewBtn.setSelected(currentTaskView == PlannerTaskView.TODAY);
        allTasksViewBtn.setSelected(currentTaskView == PlannerTaskView.ALL_TASKS);
        todayViewBtn.getStyleClass().addAll("planner-view-toggle", "btn-small");
        allTasksViewBtn.getStyleClass().addAll("planner-view-toggle", "btn-small");
        todayViewBtn.setOnAction(e -> {
            if (!todayViewBtn.isSelected()) {
                todayViewBtn.setSelected(true);
            }
            currentTaskView = PlannerTaskView.TODAY;
            updateTasksDisplay();
        });
        allTasksViewBtn.setOnAction(e -> {
            if (!allTasksViewBtn.isSelected()) {
                allTasksViewBtn.setSelected(true);
            }
            currentTaskView = PlannerTaskView.ALL_TASKS;
            updateTasksDisplay();
        });

        Label sortLabel = new Label("Sort:");
        TaskStyleUtils.fontBold(sortLabel, 12);
        ComboBox<String> sortCombo = new ComboBox<>();
        sortCombo.getItems().addAll("Status", "Priority", "Deadline", "Title");
        sortCombo.setValue(currentSort);
        sortCombo.setOnAction(e -> {
            currentSort = sortCombo.getValue();
            updateTasksDisplay();
        });

        Label groupLabel = new Label("Group:");
        TaskStyleUtils.fontBold(groupLabel, 12);
        ComboBox<String> groupCombo = new ComboBox<>();
        groupCombo.getItems().addAll("None", "Status", "Category", "Deadline");
        groupCombo.setValue(currentGroup);
        groupCombo.setOnAction(e -> {
            currentGroup = groupCombo.getValue();
            updateTasksDisplay();
        });

        toolbar.getChildren().addAll(
                todayViewBtn, allTasksViewBtn, sortLabel, sortCombo, groupLabel, groupCombo);

        tasksContainer = new VBox(10);
        section.getChildren().addAll(sectionHeader, attemptOverviewContainer, toolbar, tasksContainer);
        mainContent.getChildren().add(section);
    }

    /** Session section: controls + FlowPane of session cards. */
    private void createSessionSection(VBox mainContent) {
        VBox sessionSection = new VBox(15);
        sessionSection.getStyleClass().add("section-card");
        sessionSection.setPadding(new Insets(20));

        Label sessionTitle = new Label("Study Session");
        sessionTitle.setGraphic(TaskStyleUtils.iconLabel("\u270E", 18));
        TaskStyleUtils.fontBold(sessionTitle, 18);

        HBox sessionControls = new HBox(15);
        sessionControls.setAlignment(Pos.CENTER_LEFT);

        startSessionBtn = new Button("Start Session");
        startSessionBtn.getStyleClass().add("btn-success");

        endSessionBtn = new Button("End Session");
        endSessionBtn.getStyleClass().add("btn-danger");
        endSessionBtn.setDisable(true);

        sessionStatusLabel = new Label("No active session");
        TaskStyleUtils.fontNormal(sessionStatusLabel, 14);

        startSessionBtn.setOnAction(e -> {
            currentSession = studyService.startStudySession();
            sessionTextArea.clear();
            startSessionTimer();
            syncSessionControls();
            updateSessionsDisplay();
        });

        endSessionBtn.setOnAction(e -> {
            if (currentSession != null) {
                currentSession.setSessionText(sessionTextArea.getText());
                showEndSessionDialog();
            }
        });

        sessionControls.getChildren().addAll(startSessionBtn, endSessionBtn, sessionStatusLabel);

        sessionTextArea = new TextArea();
        sessionTextArea.setPromptText("Write your session notes, thoughts, or study content here…");
        sessionTextArea.setPrefRowCount(5);
        sessionTextArea.setWrapText(true);
        sessionTextArea.setDisable(true);

        Label completedLabel = new Label("Today's Completed Sessions");
        completedLabel.setGraphic(TaskStyleUtils.iconLabel("\u2611", 14));
        TaskStyleUtils.fontBold(completedLabel, 14);

        // FlowPane for compact session cards
        sessionsFlowPane = new FlowPane(10, 10);
        sessionsFlowPane.setPrefWrapLength(Double.MAX_VALUE);
        sessionsFlowPane.setPadding(new Insets(5, 0, 0, 0));

        sessionSection.getChildren().addAll(
                sessionTitle, sessionControls, sessionTextArea, completedLabel, sessionsFlowPane);
        mainContent.getChildren().add(sessionSection);
        syncSessionControls();
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

        if (taskSectionTitle != null) {
            taskSectionTitle.setText(taskSectionTitleText());
        }

        refreshDisplayDateAttemptCache();
        List<Task> tasks = new ArrayList<>(tasksForCurrentView());
        LocalDate today = dateTimeService.getCurrentDate();
        boolean dateScopedView = currentTaskView == PlannerTaskView.TODAY;
        if (attemptOverviewContainer != null) {
            attemptOverviewContainer.setVisible(dateScopedView);
            attemptOverviewContainer.setManaged(dateScopedView);
            if (dateScopedView) {
                updateAttemptOverview();
            } else {
                attemptOverviewContainer.getChildren().clear();
            }
        }

        if (tasks.isEmpty()) {
            // No tasks for this day — offer "Create Task" shortcut
            VBox emptyBox = new VBox(8);
            emptyBox.setAlignment(Pos.CENTER_LEFT);
            Label emptyLabel = new Label(dateScopedView
                    ? "No tasks scheduled for this day." : "No active tasks.");
            TaskStyleUtils.fontNormal(emptyLabel, 14);
            emptyLabel.setTextFill(Color.web("#7f8c8d"));

            Button createTaskBtn = new Button("+ Create Task");
            createTaskBtn.getStyleClass().add("btn-primary");
            createTaskBtn.setOnAction(e -> showCreateTaskDialog());

            emptyBox.getChildren().addAll(emptyLabel, createTaskBtn);
            tasksContainer.getChildren().add(emptyBox);
            // Do NOT return — unlinked goals must still render in the date-scoped view
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
        if (dateScopedView && displayDate.equals(today)) {
            List<MissedOccurrence> missed = taskService.getMissedRecurringOccurrences(today);
            Set<String> shownTaskIds = tasks.stream().map(Task::getId).collect(Collectors.toSet());
            LinkedHashMap<String, List<MissedOccurrence>> byTask = new LinkedHashMap<>();
            for (MissedOccurrence mo : missed) {
                byTask.computeIfAbsent(mo.task().getId(), k -> new ArrayList<>()).add(mo);
            }

            if (!byTask.isEmpty()) {
                VBox missedSection = new VBox(6);
                missedSection.setPadding(new Insets(10, 0, 0, 0));
                Label missedTitle = new Label("Missed recurring tasks:");
                missedTitle.setGraphic(TaskStyleUtils.iconLabel("\u26A0", 13));
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

        VBox unlinkedRetrySection = buildUnlinkedRetrySection();
        if (unlinkedRetrySection != null) {
            tasksContainer.getChildren().add(unlinkedRetrySection);
        }

        // Unlinked attempts section (goals with no task)
        if (dateScopedView) {
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
                            buildCompletedGoalsSection(completedUnlinked));
                }
                tasksContainer.getChildren().add(unlinkedSection);
            }
        }
    }

    private void updateAttemptOverview() {
        if (attemptOverviewContainer == null) {
            return;
        }
        attemptOverviewContainer.getChildren().clear();

        List<StudyGoal> attempts = displayDateAttempts;
        long pending = attempts.stream()
                .filter(goal -> goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.PENDING)
                .count();
        long achieved = attempts.stream().filter(StudyGoal::isAchieved).count();
        long missed = attempts.stream()
                .filter(goal -> goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.MISSED)
                .count();
        long retrying = attempts.stream()
                .filter(goal -> goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.PENDING
                        && goal.getMissedAttemptCount() > 0)
                .count();
        int score = (int) (achieved - missed);

        attemptOverviewContainer.getChildren().addAll(
                createAttemptMetricCard("Today's net", String.format("%+d", score),
                        TaskStyleUtils.COLOR_PURPLE, TaskStyleUtils.TINT_PURPLE, true),
                createAttemptMetricCard("Goals", String.valueOf(attempts.size()),
                        TaskStyleUtils.COLOR_PRIMARY, TaskStyleUtils.TINT_NEUTRAL, false),
                createAttemptMetricCard("Pending", String.valueOf(pending),
                        TaskStyleUtils.COLOR_PRIMARY, TaskStyleUtils.TINT_NEUTRAL, false),
                createAttemptMetricCard("Retry", String.valueOf(retrying),
                        TaskStyleUtils.COLOR_ORANGE, TaskStyleUtils.TINT_NEUTRAL, false),
                createAttemptMetricCard("Done", String.valueOf(achieved),
                        TaskStyleUtils.COLOR_SUCCESS, TaskStyleUtils.TINT_NEUTRAL, false),
                createAttemptMetricCard("Missed", String.valueOf(missed),
                        TaskStyleUtils.COLOR_DANGER, TaskStyleUtils.TINT_NEUTRAL, false)
        );
    }

    private VBox createAttemptMetricCard(String title, String value, String textColor,
                                         String backgroundColor, boolean primary) {
        VBox card = new VBox(2);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMinWidth(primary ? 120 : 78);
        card.setPadding(primary ? new Insets(8, 12, 8, 12) : new Insets(6, 9, 6, 9));
        card.setStyle("-fx-background-color: " + backgroundColor + "; -fx-background-radius: 8;"
                + " -fx-border-color: #d6dbe0; -fx-border-radius: 8;");

        Label valueLabel = new Label(value);
        TaskStyleUtils.fontBold(valueLabel, primary ? 18 : 14);
        valueLabel.setTextFill(Color.web(textColor));

        Label titleLabel = new Label(title);
        TaskStyleUtils.fontNormal(titleLabel, 10);
        titleLabel.setTextFill(Color.web(textColor));

        card.getChildren().addAll(valueLabel, titleLabel);
        return card;
    }

    private String taskSectionTitleText() {
        if (currentTaskView == PlannerTaskView.ALL_TASKS) {
            return "All Active Tasks";
        }
        LocalDate today = dateTimeService.getCurrentDate();
        if (displayDate.equals(today)) {
            return "Today's Tasks & Goals";
        }
        return "Tasks & Goals - " + displayDate.format(DateTimeFormatter.ofPattern("MMM d"));
    }

    private List<StudyGoal> getAllAttemptsForDisplayDate() {
        LocalDate today = dateTimeService.getCurrentDate();
        if (displayDate.isAfter(today)) {
            return studyService.getAllGoalsForFutureDate(displayDate);
        }
        return studyService.getAllGoalsForDate(displayDate);
    }

    private void refreshDisplayDateAttemptCache() {
        displayDateAttempts = getAllAttemptsForDisplayDate();
        displayDateAttemptsByTaskId = displayDateAttempts.stream()
                .filter(goal -> goal.getTaskId() != null && !goal.getTaskId().isBlank())
                .collect(Collectors.groupingBy(
                        StudyGoal::getTaskId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        taskIdsWithAttemptsOnDisplayDate = new HashSet<>(displayDateAttemptsByTaskId.keySet());
    }

    private List<Task> tasksForCurrentView() {
        if (currentTaskView == PlannerTaskView.ALL_TASKS) {
            return taskService.getTasks().stream()
                    .filter(this::isActivePlannerTask)
                    .toList();
        }
        return taskService.getTasksForDate(displayDate);
    }

    private boolean isActivePlannerTask(Task task) {
        TaskStatus status = task.getStatus();
        return status == TaskStatus.OPEN
                || status == TaskStatus.IN_PROGRESS
                || status == TaskStatus.DELAYED;
    }

    private boolean surfacesByDateRules(Task task, LocalDate date) {
        if (task == null || date == null) {
            return false;
        }

        TaskStatus status = task.getStatus();
        boolean active = status == TaskStatus.OPEN || status == TaskStatus.IN_PROGRESS;
        boolean delayed = status == TaskStatus.DELAYED;

        if (task.isRecurring()) {
            if (!active) {
                return false;
            }
            if (task.getStartDate() != null && date.isBefore(task.getStartDate())) {
                return false;
            }
            if (task.getDeadline() != null && date.isAfter(task.getDeadline())) {
                return false;
            }
            LocalDate anchorMonday = task.getRecurrenceAnchor()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return taskService.recurringTaskAppliesTo(task, date, anchorMonday);
        }

        if (!(active || delayed)) {
            return false;
        }
        LocalDate deadline = task.getDeadline();
        if (deadline == null) {
            return date.equals(dateTimeService.getCurrentDate());
        }
        return !date.isBefore(deadline);
    }

    private boolean hasGoalOnDisplayDate(Task task) {
        return task != null && taskIdsWithAttemptsOnDisplayDate.contains(task.getId());
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
            case "Deadline", "Reason" -> reasonGroupKeyFor(task);
            default -> "";
        };
    }

    private String reasonGroupKeyFor(Task task) {
        if (task.isRecurring() && surfacesByDateRules(task, displayDate)) {
            return "Recurring";
        }
        LocalDate deadline = task.getDeadline();
        if (deadline != null && deadline.equals(displayDate) && surfacesByDateRules(task, displayDate)) {
            return "Due";
        }
        if (deadline != null && deadline.isBefore(displayDate) && surfacesByDateRules(task, displayDate)) {
            return "Overdue";
        }
        if (hasGoalOnDisplayDate(task)) {
            return "Has goal";
        }
        if (currentTaskView == PlannerTaskView.TODAY
                && !task.isRecurring()
                && deadline == null
                && isActivePlannerTask(task)
                && displayDate.equals(dateTimeService.getCurrentDate())) {
            return "Open (no deadline)";
        }
        return "Other active tasks";
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

        Label arrow = new Label("\u25B6");
        TaskStyleUtils.fontEmoji(arrow, 11);
        arrow.setTextFill(Color.web("#7f8c8d"));

        Label taskTitle = new Label(task.getTitle());
        TaskStyleUtils.fontBold(taskTitle, 14);
        taskTitle.setWrapText(true);
        taskTitle.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(taskTitle, Priority.ALWAYS);

        Label priorityLabel = new Label(task.getPriority() != null ? task.getPriority().toString() : "");
        priorityLabel.setTextFill(Color.web("#f39c12"));

        Label statusBadge = new Label(task.getStatus().name());
        statusBadge.setPadding(new Insets(2, 6, 2, 6));
        statusBadge.setStyle("-fx-background-color: " + TaskStyleUtils.statusBadgeBg(task.getStatus()) +
                             "; -fx-background-radius: 10;");
        TaskStyleUtils.fontBold(statusBadge, 10);
        statusBadge.setTextFill(TaskStyleUtils.statusTextColor(task.getStatus()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        headerRow.getChildren().add(arrow);
        if (task.isRecurring()) {
            Label recurBadge = TaskStyleUtils.iconLabel("\u21BA", 12);
            recurBadge.setTooltip(new Tooltip(task.getRecurringSummary()));
            headerRow.getChildren().add(recurBadge);
        }
        headerRow.getChildren().addAll(taskTitle, priorityLabel);
        headerRow.getChildren().add(spacer);

        // Overdue / due-today badge (between spacer and status badge)
        if (TaskStyleUtils.isOverdue(task, displayDate)) {
            headerRow.getChildren().add(TaskStyleUtils.createOverdueBadge());
        } else if (TaskStyleUtils.isDueToday(task, displayDate)) {
            headerRow.getChildren().add(TaskStyleUtils.createDueTodayBadge());
        }

        headerRow.getChildren().add(statusBadge);

        List<Label> attemptBadges = createTaskAttemptBadges(task);
        FlowPane badgeRow = new FlowPane(6, 4);
        badgeRow.setPadding(new Insets(0, 14, 10, 42));
        badgeRow.getChildren().addAll(attemptBadges);
        badgeRow.setVisible(!attemptBadges.isEmpty());
        badgeRow.setManaged(!attemptBadges.isEmpty());
        badgeRow.setStyle("-fx-cursor: hand;");

        // ── Expandable goals panel ──
        VBox goalsPanel = new VBox(6);
        goalsPanel.setPadding(new Insets(0, 14, 12, 28));

        // Restore previous expand state so panels survive UI rebuilds
        boolean wasExpanded = expandedTaskIds.contains(task.getId());
        goalsPanel.setVisible(wasExpanded);
        goalsPanel.setManaged(wasExpanded);
        if (wasExpanded) {
            arrow.setText("\u25BC");
            populateGoalsPanel(goalsPanel, task);
        }

        // Toggle expand/collapse on header click
        Runnable toggleExpanded = () -> {
            boolean nowVisible = !goalsPanel.isVisible();
            goalsPanel.setVisible(nowVisible);
            goalsPanel.setManaged(nowVisible);
            arrow.setText(nowVisible ? "\u25BC" : "\u25B6");
            if (nowVisible) {
                expandedTaskIds.add(task.getId());
                populateGoalsPanel(goalsPanel, task);
            } else {
                expandedTaskIds.remove(task.getId());
            }
        };
        headerRow.setOnMouseClicked(e -> toggleExpanded.run());
        badgeRow.setOnMouseClicked(e -> toggleExpanded.run());

        card.getChildren().addAll(headerRow, badgeRow, goalsPanel);
        return card;
    }

    private List<Label> createTaskAttemptBadges(Task task) {
        List<StudyGoal> attempts = displayDateAttemptsByTaskId.getOrDefault(task.getId(), List.of());
        if (attempts.isEmpty()) {
            return List.of();
        }

        long pending = attempts.stream()
                .filter(goal -> goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.PENDING)
                .count();
        long achieved = attempts.stream().filter(StudyGoal::isAchieved).count();
        long missed = attempts.stream()
                .filter(goal -> goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.MISSED)
                .count();
        long retries = attempts.stream().filter(goal -> goal.getMissedAttemptCount() > 0).count();

        List<Label> badges = new ArrayList<>();
        badges.add(createTaskAttemptBadge(attempts.size() + " goal" + (attempts.size() == 1 ? "" : "s"),
                TaskStyleUtils.COLOR_PRIMARY, TaskStyleUtils.TINT_NEUTRAL));
        if (pending > 0) {
            badges.add(createTaskAttemptBadge(pending + " pending", TaskStyleUtils.COLOR_PRIMARY, TaskStyleUtils.TINT_PRIMARY));
        }
        if (retries > 0) {
            badges.add(createTaskAttemptBadge(retries + " retry", TaskStyleUtils.retryTextColor(), TaskStyleUtils.retryBackgroundColor()));
        }
        if (achieved > 0) {
            badges.add(createTaskAttemptBadge(achieved + " done", TaskStyleUtils.COLOR_SUCCESS, TaskStyleUtils.TINT_SUCCESS));
        }
        if (missed > 0) {
            badges.add(createTaskAttemptBadge(missed + " missed", TaskStyleUtils.COLOR_DANGER, TaskStyleUtils.TINT_DANGER));
        }
        return badges;
    }

    private Label createTaskAttemptBadge(String text, String textColor, String backgroundColor) {
        return TaskStyleUtils.createAttemptBadge(text, textColor, backgroundColor);
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
        VBox retrySection = buildTaskRetrySection(task);

        if (activeGoals.isEmpty() && completedGoals.isEmpty()) {
            if (retrySection != null) {
                goalsPanel.getChildren().add(retrySection);
            } else {
                Label noGoals = new Label("No goals linked to this task for this date.");
                TaskStyleUtils.fontNormal(noGoals, 12);
                noGoals.setTextFill(Color.web("#7f8c8d"));

                Button addGoalBtn = new Button("+ Create Goal");
                addGoalBtn.getStyleClass().addAll("btn-purple", "btn-small");
                addGoalBtn.setOnAction(e -> {
                    showAddGoalDialog(task);
                    populateGoalsPanel(goalsPanel, task); // refresh after add
                });

                goalsPanel.getChildren().addAll(noGoals, addGoalBtn);
            }
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

        // Completed goals - collapsible section
        if (!completedGoals.isEmpty()) {
            goalsPanel.getChildren().add(
                    buildCompletedGoalsSection(completedGoals));
        }

        if (retrySection != null && (!activeGoals.isEmpty() || !completedGoals.isEmpty())) {
            goalsPanel.getChildren().add(retrySection);
        }
    }

    private VBox buildUnlinkedRetrySection() {
        if (currentTaskView != PlannerTaskView.TODAY
                || !displayDate.equals(dateTimeService.getCurrentDate())) {
            return null;
        }
        List<StudyGoal> retryableGoals = studyService.getUnlinkedDelayedGoalsForReplanning();
        if (retryableGoals.isEmpty()) {
            return null;
        }

        VBox section = new VBox(6);
        section.setPadding(new Insets(10, 0, 0, 0));

        Label header = new Label("Missed goals without a task");
        header.setGraphic(TaskStyleUtils.iconLabel("\u21BA", 12));
        TaskStyleUtils.fontBold(header, 13);
        header.setTextFill(Color.web(TaskStyleUtils.retryTextColor()));
        section.getChildren().add(header);

        for (StudyGoal goal : retryableGoals) {
            section.getChildren().add(buildTaskRetryRow(goal));
        }
        return section;
    }

    private VBox buildTaskRetrySection(Task task) {
        if (!displayDate.equals(dateTimeService.getCurrentDate())) {
            return null;
        }
        List<StudyGoal> retryableGoals = studyService.getDelayedGoalsForReplanning(task.getId());
        if (retryableGoals.isEmpty()) {
            return null;
        }

        VBox section = new VBox(6);
        section.setPadding(new Insets(8, 0, 0, 0));

        Label header = new Label("Missed goals ready to retry");
        header.setGraphic(TaskStyleUtils.iconLabel("\u21BA", 12));
        TaskStyleUtils.fontBold(header, 12);
        header.setTextFill(Color.web(TaskStyleUtils.retryTextColor()));
        section.getChildren().add(header);

        for (StudyGoal goal : retryableGoals) {
            section.getChildren().add(buildTaskRetryRow(goal));
        }
        return section;
    }

    private HBox buildTaskRetryRow(StudyGoal goal) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 8, 6, 8));
        row.setStyle("-fx-background-color: " + TaskStyleUtils.retryBackgroundColor() + "; -fx-background-radius: 5;");

        VBox textBox = new VBox(2);
        Label goalLabel = new Label(goal.getDescription());
        TaskStyleUtils.fontNormal(goalLabel, 13);
        Label attemptLabel = new Label(formatRetryAttemptSummary(goal));
        TaskStyleUtils.fontNormal(attemptLabel, 11);
        attemptLabel.setTextFill(Color.web(TaskStyleUtils.retryTextColor()));
        textBox.getChildren().addAll(goalLabel, attemptLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button replanBtn = new Button("Plan retry today");
        replanBtn.setGraphic(TaskStyleUtils.iconLabel("\u21BA", 12));
        replanBtn.getStyleClass().addAll("btn-orange", "btn-small");
        replanBtn.setOnAction(e -> {
            studyService.replanGoalForToday(goal.getId());
            updateTasksDisplay();
            updateProgress();
        });

        row.getChildren().addAll(textBox, spacer, replanBtn);
        return row;
    }

    private String formatRetryAttemptSummary(StudyGoal goal) {
        String missedOn = goal.getDate() != null
                ? goal.getDate().format(DateTimeFormatter.ofPattern("MMM d"))
                : "previous attempt";
        int nextAttempt = Math.max(goal.getAttemptNumber() + 1, goal.getMissedAttemptCount() + 2);
        return "Missed " + missedOn + " - " + formatAttemptSummary(goal)
                + " - next attempt " + nextAttempt;
    }

    private HBox buildGoalRow(StudyGoal goal, Task linkedTask) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 8, 6, 8));

        String bgColor = TaskStyleUtils.TINT_NEUTRAL;
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

        Label attemptLabel = new Label(formatAttemptSummary(goal));
        attemptLabel.setStyle("-fx-text-fill: "
                + (goal.getMissedAttemptCount() > 0 ? TaskStyleUtils.COLOR_DANGER : "#6c757d") + ";");
        TaskStyleUtils.fontNormal(attemptLabel, 11);
        textBox.getChildren().add(attemptLabel);

        row.getChildren().addAll(check, textBox);
        return row;
    }

    private String formatAttemptSummary(StudyGoal goal) {
        String summary = "Attempt " + goal.getAttemptNumber();
        if (goal.isAchieved()) {
            summary += " - achieved";
        } else if (goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.MISSED) {
            summary += " - missed";
        } else {
            summary += " - pending";
        }
        return summary;
    }

    /**
     * Builds a collapsible completed-goals section for achieved attempts.
     * Clicking the header toggles visibility of the completed goal rows.
     */
    private VBox buildCompletedGoalsSection(List<StudyGoal> completedGoals) {
        VBox section = new VBox(4);
        section.setPadding(new Insets(6, 0, 0, 0));

        VBox itemsBox = new VBox(4);
        itemsBox.setVisible(false);
        itemsBox.setManaged(false);

        Label toggle = new Label("Completed Goals (" + completedGoals.size() + ")");
        toggle.setGraphic(TaskStyleUtils.iconLabel("\u25B6", 11));
        TaskStyleUtils.fontBold(toggle, 11);
        toggle.setTextFill(Color.web("#27ae60"));
        toggle.setStyle("-fx-cursor: hand;");
        toggle.setOnMouseClicked(e -> {
            boolean show = !itemsBox.isVisible();
            itemsBox.setVisible(show);
            itemsBox.setManaged(show);
            toggle.setGraphic(TaskStyleUtils.iconLabel(show ? "\u25BC" : "\u25B6", 11));
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

            VBox textBox = new VBox(2);
            textBox.getChildren().add(label);
            Label attempts = new Label(formatAttemptSummary(goal));
            attempts.setTextFill(Color.web("#7f8c8d"));
            TaskStyleUtils.fontNormal(attempts, 10);
            textBox.getChildren().add(attempts);

            row.getChildren().addAll(check, textBox);
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
        List<StudySession> completedSessions = sessions.stream()
                .filter(StudySession::isCompleted)
                .toList();
        List<StudySession> incompleteSessions = sessions.stream()
                .filter(session -> !session.isCompleted())
                .toList();

        for (StudySession session : completedSessions) {
            sessionsFlowPane.getChildren().add(buildSessionCard(session));
        }

        for (StudySession session : incompleteSessions) {
            sessionsFlowPane.getChildren().add(buildIncompleteSessionCard(session));
        }

        if (sessionsFlowPane.getChildren().isEmpty()) {
            Label none = new Label("No sessions for this date.");
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

        Label timeLabel = new Label(startStr + "\u2013" + endStr);
        timeLabel.setGraphic(TaskStyleUtils.iconLabel("\u23F0", 12));
        TaskStyleUtils.fontBold(timeLabel, 12);

        Label durationLabel = new Label(session.getDurationMinutes() + " min");
        TaskStyleUtils.fontNormal(durationLabel, 11);
        durationLabel.setTextFill(Color.web("#7f8c8d"));

        // Focus stars (compact)
        StringBuilder stars = new StringBuilder();
        for (int i = 1; i <= 5; i++) stars.append(i <= session.getFocusLevel() ? "\u2605" : "\u2606");
        Label focusLabel = new Label(stars.toString());
        TaskStyleUtils.fontEmoji(focusLabel, 12);
        focusLabel.setTextFill(Color.web("#f39c12"));

        Label pointsLabel = new Label(session.getPointsEarned() + " pts");
        pointsLabel.setGraphic(TaskStyleUtils.iconLabel("\u2666", 11));
        TaskStyleUtils.fontSemiBold(pointsLabel, 11);
        pointsLabel.setTextFill(Color.web("#27ae60"));

        // Action buttons row
        HBox btnRow = new HBox(5);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        Button detailsBtn = new Button("Details");
        detailsBtn.getStyleClass().addAll("btn-primary", "btn-small");
        detailsBtn.setStyle("-fx-padding: 3 7;");
        detailsBtn.setOnAction(e -> showSessionDetails(session));

        Button deleteBtn = new Button("\u2715");
        TaskStyleUtils.fontEmoji(deleteBtn, 11);
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

    private VBox buildIncompleteSessionCard(StudySession session) {
        VBox card = new VBox(5);
        card.setPadding(new Insets(10, 12, 10, 12));
        card.setPrefWidth(220);
        card.setStyle("-fx-background-color: #fff8ef; -fx-background-radius: 8;" +
                      " -fx-border-color: #f39c12; -fx-border-radius: 8;" +
                      " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 4, 0, 0, 1);");

        String startStr = session.getStartTime() != null
                ? session.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "?";
        String endStr = session.getEndTime() != null
                ? session.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "?";

        Label statusLabel = new Label(session.isActive() ? "Active, not completed" : "Incomplete session");
        statusLabel.setGraphic(TaskStyleUtils.iconLabel(session.isActive() ? "\u23F3" : "\u26A0", 11));
        TaskStyleUtils.fontBold(statusLabel, 11);
        statusLabel.setTextFill(Color.web("#d35400"));

        Label timeLabel = new Label(startStr + "\u2013" + endStr);
        timeLabel.setGraphic(TaskStyleUtils.iconLabel("\u23F0", 12));
        TaskStyleUtils.fontBold(timeLabel, 12);

        String durationText = session.getDurationMinutes() > 0
                ? session.getDurationMinutes() + " min tracked"
                : "Not completed";
        Label durationLabel = new Label(durationText);
        TaskStyleUtils.fontNormal(durationLabel, 11);
        durationLabel.setTextFill(Color.web("#7f8c8d"));

        Button detailsBtn = new Button("Details");
        detailsBtn.getStyleClass().addAll("btn-primary", "btn-small");
        detailsBtn.setStyle("-fx-padding: 3 7;");
        detailsBtn.setOnAction(e -> showSessionDetails(session));

        Button deleteBtn = new Button("\u2715");
        TaskStyleUtils.fontEmoji(deleteBtn, 11);
        deleteBtn.getStyleClass().addAll("btn-danger", "btn-small");
        deleteBtn.setStyle("-fx-padding: 3 7;");
        deleteBtn.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete this incomplete session?", ButtonType.OK, ButtonType.CANCEL);
            alert.setHeaderText(null);
            alert.initOwner(this.getScene() != null ? this.getScene().getWindow() : null);
            alert.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.OK) {
                    studyService.deleteStudySession(session.getId());
                    if (currentSession != null && currentSession.getId().equals(session.getId())) {
                        currentSession = null;
                        stopSessionTimer();
                        sessionTextArea.clear();
                        syncSessionControls();
                    }
                    updateSessionsDisplay();
                }
            });
        });

        HBox btnRow = new HBox(5, detailsBtn, deleteBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(statusLabel, timeLabel, durationLabel, btnRow);
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
        Label err = new Label(message);
        err.setGraphic(TaskStyleUtils.iconLabel("\u26A0", 12));
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
        descArea.setPromptText("Goal description...");
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
                Label err = new Label(ex.getMessage());
                err.setGraphic(TaskStyleUtils.iconLabel("\u26A0", 12));
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

        Label focusWarning = new Label("Average focus.");
        TaskStyleUtils.fontNormal(focusWarning, 11);
        focusWarning.setWrapText(true);
        focusWarning.setTextFill(Color.web("#f39c12"));

        focusSlider.valueProperty().addListener((obs, o, nv) -> {
            int lv = nv.intValue();
            if (lv <= 2) {
                focusWarning.setText("Low focus \u2014 point penalties will apply.");
                focusWarning.setGraphic(TaskStyleUtils.iconLabel("\u26A0", 11));
                focusWarning.setTextFill(Color.web("#e74c3c"));
            } else if (lv == 3) {
                focusWarning.setText("Average focus.");
                focusWarning.setGraphic(null);
                focusWarning.setTextFill(Color.web("#f39c12"));
            } else {
                focusWarning.setText("Great focus! Bonus points incoming.");
                focusWarning.setGraphic(TaskStyleUtils.iconLabel("\u2713", 11));
                focusWarning.setTextFill(Color.web("#27ae60"));
            }
        });

        TextArea notesArea = new TextArea();
        notesArea.setPromptText("What did you accomplish?");
        notesArea.setPrefRowCount(3);

        Label errorLabel = new Label();
        errorLabel.setGraphic(TaskStyleUtils.iconLabel("\u26A0", 12));
        errorLabel.setTextFill(Color.web("#e74c3c"));
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        Button okBtn = new Button("OK");
        okBtn.getStyleClass().add("btn-primary");
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-cancel");
        cancelBtn.setOnAction(e -> {
            closeModal.run();
            syncSessionControls();
        });

        okBtn.setOnAction(e -> {
            try {
                StudySessionEnd sessionEnd = new StudySessionEnd((int) focusSlider.getValue(), notesArea.getText());
                studyService.endStudySession(currentSession, sessionEnd);
                stopSessionTimer();
                currentSession = null;
                sessionTextArea.clear();
                syncSessionControls();
                closeModal.run();
                updateSessionsDisplay();
                updateProgress();
            } catch (Exception ex) {
                errorLabel.setText("Failed to save session: " + ex.getMessage());
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
            }
        });

        HBox btnRow = new HBox(10, cancelBtn, okBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        content.getChildren().addAll(headerLabel, focusLabel, focusSlider, focusWarning,
                new Label("Notes:"), notesArea, errorLabel, btnRow);
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
        Tab overviewTab = new Tab("Overview", createOverviewTab(session));
        overviewTab.setGraphic(TaskStyleUtils.iconLabel("\u25CE", 12));
        Tab progressTab = new Tab("Progress", createProgressTab(session));
        progressTab.setGraphic(TaskStyleUtils.iconLabel("\u25CE", 12));
        Tab notesTab = new Tab("Notes", createNotesTab(session));
        notesTab.setGraphic(TaskStyleUtils.iconLabel("\u25AA", 12));
        Tab perfTab = new Tab("Performance", createPerformanceTab(session));
        perfTab.setGraphic(TaskStyleUtils.iconLabel("\u21AF", 12));
        detailsTabs.getTabs().addAll(overviewTab, progressTab, notesTab, perfTab
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

        Label sessionTitle = new Label("Study Session");
        Label sessionIcon = TaskStyleUtils.iconLabel("\u270E", 24);
        sessionIcon.setTextFill(Color.WHITE);
        sessionTitle.setGraphic(sessionIcon);
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
                createMetricBox("\u231A", session.getDurationMinutes() + " min", "Duration"),
                createMetricBox("\u25CE",
                        "\u2605".repeat(session.getFocusLevel()) + "\u2606".repeat(5 - session.getFocusLevel()),
                        "Focus"),
                createMetricBox("\u2666", session.getPointsEarned() + " pts", "Points")
        );

        header.getChildren().addAll(sessionTitle, dateTime, metrics);
        mainContent.getChildren().add(header);
    }

    private VBox createMetricBox(String icon, String value, String label) {
        VBox box = new VBox(5);
        box.setAlignment(Pos.CENTER);
        Label iconL = new Label(icon);
        TaskStyleUtils.fontEmoji(iconL, 20);
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
        Label timeTitle = new Label("Time Breakdown");
        timeTitle.setGraphic(TaskStyleUtils.iconLabel("\u23F0", 16));
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
        Label focusTitle = new Label("Focus & Productivity");
        focusTitle.setGraphic(TaskStyleUtils.iconLabel("\u25CE", 16));
        TaskStyleUtils.fontBold(focusTitle, 16);

        HBox starsBox = new HBox(2);
        for (int i = 1; i <= 5; i++) {
            Label star = new Label(i <= session.getFocusLevel() ? "\u2605" : "\u2606");
            TaskStyleUtils.fontEmoji(star, 20);
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
        Label notesTitle = new Label("Session Notes");
        notesTitle.setGraphic(TaskStyleUtils.iconLabel("\u25AA", 18));
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
        Label compTitle = new Label("Performance Analysis");
        compTitle.setGraphic(TaskStyleUtils.iconLabel("\u25AA", 16));
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

    private void startSessionTimer() {
        if (sessionTimer != null) sessionTimer.stop();
        sessionTimer = new Timeline(new KeyFrame(Duration.seconds(1),
                e -> updateSessionTimer()));
        sessionTimer.setCycleCount(Timeline.INDEFINITE);
        sessionTimer.play();
        updateSessionTimer();
    }

    private void stopSessionTimer() {
        if (sessionTimer != null) {
            sessionTimer.stop();
            sessionTimer = null;
        }
    }

    private void updateSessionTimer() {
        if (sessionStatusLabel == null) {
            return;
        }
        if (currentSession != null && currentSession.isActive()) {
            currentSession.updateRealTimeProgress();
            int elapsed = currentSession.getCurrentElapsedMinutes();
            int h = elapsed / 60, m = elapsed % 60;
            sessionStatusLabel.setText(h > 0
                    ? String.format("⌚ Session running: %dh %02dm", h, m)
                    : String.format("⌚ Session running: %dm", m));
            sessionStatusLabel.setTextFill(Color.web("#27ae60"));
        } else {
            sessionStatusLabel.setText("No active session");
            sessionStatusLabel.setTextFill(Color.web("#2c3e50"));
        }
    }

    private void syncSessionControls() {
        if (startSessionBtn == null || endSessionBtn == null || sessionStatusLabel == null || sessionTextArea == null) {
            return;
        }

        boolean hasActiveSession = currentSession != null && currentSession.isActive();
        startSessionBtn.setDisable(hasActiveSession);
        endSessionBtn.setDisable(!hasActiveSession);
        sessionTextArea.setDisable(!hasActiveSession);

        if (!hasActiveSession) {
            updateSessionTimer();
        }
    }

    private void restoreActiveSessionIfNeeded() {
        if (currentSession != null || sessionTextArea == null) {
            return;
        }
        if (!displayDate.equals(dateTimeService.getCurrentDate())) {
            return;
        }

        StudySession activeSession = studyService.getActiveSession().orElse(null);

        if (activeSession == null) {
            if (currentSession != null) {
                currentSession = null;
                stopSessionTimer();
                sessionTextArea.clear();
            }
            return;
        }

        boolean sameActiveSession = currentSession != null
                && currentSession.getId() != null
                && currentSession.getId().equals(activeSession.getId());
        currentSession = activeSession;
        if (!sameActiveSession) {
            String sessionText = activeSession.getSessionText() != null ? activeSession.getSessionText() : "";
            if (!sessionText.equals(sessionTextArea.getText())) {
                sessionTextArea.setText(sessionText);
            }
        }
        if (sessionTimer == null) {
            startSessionTimer();
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
        restoreActiveSessionIfNeeded();
        syncSessionControls();
        boolean isToday = displayDate.equals(dateTimeService.getCurrentDate());
        String prefix = isToday ? "Today \u2014 " : "";
        dateNavLabel.setGraphic(dateNavIcon);
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
