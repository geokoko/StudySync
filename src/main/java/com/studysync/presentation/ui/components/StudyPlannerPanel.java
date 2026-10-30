
package com.studysync.presentation.ui.components;

import com.studysync.domain.service.StudyService;
import com.studysync.domain.service.StudySessionEnd;
import com.studysync.domain.service.DateTimeService;
import com.studysync.domain.service.TaskService;
import com.studysync.domain.service.CategoryService;
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
import javafx.scene.Node;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.input.DataFormat;
import javafx.scene.input.TransferMode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import java.util.function.Consumer;

/** Daily goals and available work, with the live session always in reach. */
public class StudyPlannerPanel extends BorderPane implements RefreshablePanel {

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

    private enum PlannerTaskView {
        TODAY,
        ALL_TASKS
    }

    // Sort / group state for the tasks section
    private String currentSort = "Status";
    private String currentGroup = "None";
    private GoalListAgenda.Group goalGroup = GoalListAgenda.Group.TASK;
    private GoalListAgenda.Sort goalSort = GoalListAgenda.Sort.STATUS;
    // Task IDs survive refreshes and renames; null is the "No task" group.
    private final Set<String> collapsedGoalTaskIds = new HashSet<>();
    private PlannerTaskView currentTaskView = PlannerTaskView.TODAY;

    // UI containers that get rebuilt on navigation
    private VBox tasksContainer;
    private FlowPane attemptOverviewContainer;
    private VBox goalsContainer;
    private VBox needsPlanContainer;
    private Label needsPlanHint;
    private VBox unlinkedRetriesContainer;
    private Label goalsTitle;
    private Label sessionsTitle;
    private final Label plannerError = new Label();
    private Map<String, Task> tasksById = Map.of();
    private List<StudyGoal> retryableGoals = List.of();
    private Map<String, PlannerAgenda.Entry> planningEntries = Map.of();
    private final List<Button> goalSessionButtons = new ArrayList<>();
    private static final DataFormat PLAN_DRAG = new DataFormat("application/x-studysync-planner-entry");
    private static final PseudoClass DROP = PseudoClass.getPseudoClass("drop");
    private static final double PLAN_TILE_WIDTH = 132;
    private static final double PLAN_TILE_HEIGHT = 92;
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
    private Label sessionLinkLabel;
    private ComboBox<Object> sessionLinkCombo;

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

        ScrollPane scroll = new ScrollPane(mainContent);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("tab-content-area");
        setCenter(scroll);
        getStyleClass().add("panel-bg");
        createSessionBar();
        initializeComponents(mainContent);

        dateTimeService.addDateChangeListener(this::onDateChanged);
        updateDisplay();
    }

    // ──────────────────────────────────────────────
    // LAYOUT INITIALIZATION
    // ──────────────────────────────────────────────

    private void initializeComponents(VBox mainContent) {
        createHeader(mainContent);
        GridPane columns = new GridPane();
        columns.setHgap(20);
        ColumnConstraints main = new ColumnConstraints();
        main.setPercentWidth(56);
        ColumnConstraints side = new ColumnConstraints();
        side.setPercentWidth(44);
        columns.getColumnConstraints().addAll(main, side);

        VBox goals = new VBox(12);
        goals.setMinWidth(0);
        goals.getStyleClass().addAll("section-card", "planner-goals");
        goals.setPadding(new Insets(20));
        goalsTitle = new Label();
        TaskStyleUtils.fontBold(goalsTitle, 18);
        Button addGoal = new Button("+ Add goal");
        addGoal.getStyleClass().addAll("btn-purple", "btn-small");
        addGoal.setOnAction(event -> showAddGoalDialog(null));
        Region goalsHeaderSpacer = new Region();
        HBox.setHgrow(goalsHeaderSpacer, Priority.ALWAYS);
        HBox goalsHeader = new HBox(10, goalsTitle, goalsHeaderSpacer, addGoal);
        goalsHeader.setAlignment(Pos.CENTER_LEFT);
        goalsContainer = new VBox(16);
        unlinkedRetriesContainer = new VBox(8);
        goals.getChildren().addAll(goalsHeader, createGoalListToolbar(), goalsContainer, unlinkedRetriesContainer);
        installPlanningDropTarget(goals);

        VBox sidebar = new VBox(20);
        sidebar.setMinWidth(0);
        VBox needsPlan = new VBox(10);
        needsPlan.setPadding(new Insets(20));
        needsPlan.getStyleClass().add("section-card");
        Label title = new Label("Needs a plan");
        TaskStyleUtils.fontBold(title, 18);
        needsPlanHint = mutedLabel("");
        needsPlanHint.setMinHeight(Region.USE_PREF_SIZE);
        needsPlanContainer = new VBox(8);
        needsPlan.getChildren().addAll(title, needsPlanHint, needsPlanContainer);
        sidebar.getChildren().add(needsPlan);
        createSessionSection(sidebar);
        columns.add(goals, 0, 0);
        columns.add(sidebar, 1, 0);
        GridPane.setValignment(goals, javafx.geometry.VPos.TOP);
        GridPane.setFillHeight(goals, false); // as tall as its goals, not as the other column
        mainContent.getChildren().add(columns);
        createTasksSection(mainContent);
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
        dateNavLabel.setMinWidth(0);
        dateNavLabel.setWrapText(true);
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
        dailyProgressBar.getStyleClass().add("planner-progress");

        progressLabel = new Label("Daily Progress: 0%");
        TaskStyleUtils.fontSemiBold(progressLabel, 14);
        TaskStyleUtils.textColor(progressLabel, "#34495e");

        attemptOverviewContainer = new FlowPane(10, 8);
        attemptOverviewContainer.setAlignment(Pos.CENTER);
        plannerError.setWrapText(true);
        TaskStyleUtils.textColor(plannerError, TaskStyleUtils.COLOR_DANGER);
        plannerError.setVisible(false);
        plannerError.setManaged(false);
        header.getChildren().addAll(navRow, dailyProgressBar, progressLabel, attemptOverviewContainer, plannerError);
        mainContent.getChildren().add(header);
    }

    /** Secondary task browsing retains the original filters and creation shortcut. */
    private void createTasksSection(VBox mainContent) {
        VBox section = new VBox(15);
        section.setPadding(new Insets(16));
        Button createTask = new Button("+ Create Task");
        createTask.getStyleClass().add("btn-primary");
        createTask.setOnAction(event -> showCreateTaskDialog());

        // Sort / group toolbar
        FlowPane toolbar = new FlowPane(10, 8);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        ToggleButton todayViewBtn = new ToggleButton("Selected day");
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
        groupCombo.getItems().addAll("None", "Status", "Category", "Reason");
        groupCombo.setValue(currentGroup);
        groupCombo.setOnAction(e -> {
            currentGroup = groupCombo.getValue();
            updateTasksDisplay();
        });

        toolbar.getChildren().addAll(
                todayViewBtn, allTasksViewBtn, sortLabel, sortCombo, groupLabel, groupCombo);

        tasksContainer = new VBox(10);
        section.getChildren().addAll(toolbar, createTask, tasksContainer);
        TitledPane browse = new TitledPane("Browse tasks", section);
        browse.setExpanded(false);
        browse.setAnimated(false);
        mainContent.getChildren().add(browse);
    }

    /** Group and sort controls for the goal list; the rules are in {@link GoalListAgenda}. */
    private FlowPane createGoalListToolbar() {
        Label groupLabel = new Label("Group:");
        TaskStyleUtils.fontBold(groupLabel, 12);
        ComboBox<GoalListAgenda.Group> groupCombo = new ComboBox<>();
        groupCombo.getItems().addAll(GoalListAgenda.Group.values());
        groupCombo.setValue(goalGroup);
        groupCombo.setAccessibleText("Group goals by");
        groupCombo.valueProperty().addListener((obs, old, value) -> {
            goalGroup = value;
            updateTasksDisplay();
        });

        Label sortLabel = new Label("Sort:");
        TaskStyleUtils.fontBold(sortLabel, 12);
        ComboBox<GoalListAgenda.Sort> sortCombo = new ComboBox<>();
        sortCombo.getItems().addAll(GoalListAgenda.Sort.values());
        sortCombo.setValue(goalSort);
        sortCombo.setAccessibleText("Sort goals by");
        sortCombo.valueProperty().addListener((obs, old, value) -> {
            goalSort = value;
            updateTasksDisplay();
        });

        FlowPane toolbar = new FlowPane(8, 6, groupLabel, groupCombo, sortLabel, sortCombo);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        FlowPane.setMargin(sortLabel, new Insets(0, 0, 0, 8));
        return toolbar;
    }

    private void createSessionBar() {
        // An HBox, not a FlowPane: a FlowPane is at least as wide as its widest child's
        // preferred width, so a long goal name would set the whole planner's minimum width.
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("planner-session-bar");
        startSessionBtn = new Button("Start Session");
        startSessionBtn.getStyleClass().add("btn-success");
        endSessionBtn = new Button("Stop");
        endSessionBtn.getStyleClass().add("btn-danger");
        sessionStatusLabel = new Label("No active session");
        TaskStyleUtils.fontSemiBold(sessionStatusLabel, 14);
        // One line, so a long goal cannot push the planner out of the window;
        // the tooltip has the full text.
        sessionLinkLabel = new Label();
        sessionLinkLabel.setMinWidth(0);
        sessionLinkLabel.setMaxWidth(420);
        sessionLinkCombo = new ComboBox<>();
        sessionLinkCombo.setPromptText("Link to (optional)");
        sessionLinkCombo.setPrefWidth(260);
        sessionLinkCombo.setCellFactory(lv -> sessionLinkCell());
        sessionLinkCombo.setButtonCell(sessionLinkCell());
        sessionLinkCombo.setOnShowing(event -> populateSessionLinkCombo(sessionLinkCombo));
        startSessionBtn.setOnAction(event -> startSession(sessionLinkCombo.getValue()));
        endSessionBtn.setOnAction(event -> {
            if (currentSession != null) {
                currentSession.setSessionText(sessionTextArea.getText());
                showEndSessionDialog();
            }
        });
        bar.getChildren().addAll(sessionStatusLabel, sessionLinkLabel, sessionLinkCombo, startSessionBtn, endSessionBtn);
        setTop(bar);
    }

    private void startSession(Object link) {
        if (hasActiveSession()) {
            return; // one session at a time; Stop ends the running one
        }
        try {
            String goalId = null;
            String taskId = null;
            // Re-read links: a deleted selection must not violate a session FK.
            if (link instanceof StudyGoal goal) {
                StudyGoal saved = StudyGoal.findById(goal.getId()).orElse(null);
                if (saved != null) {
                    goalId = saved.getId();
                    taskId = saved.getTaskId();
                }
            } else if (link instanceof Task task) {
                taskId = task.getId();
            }
            if (taskId != null && Task.findById(taskId).isEmpty()) {
                taskId = null;
            }
            currentSession = studyService.startStudySession(goalId, taskId);
            sessionLinkCombo.setValue(null);
            sessionTextArea.clear();
            startSessionTimer();
            syncSessionControls();
            updateSessionsDisplay();
        } catch (Exception ex) {
            showPlannerError("Could not start session: " + ex.getMessage());
        }
    }

    private void createSessionSection(VBox sidebar) {
        VBox section = new VBox(12);
        section.setPadding(new Insets(20));
        section.getStyleClass().add("section-card");
        sessionsTitle = new Label();
        TaskStyleUtils.fontBold(sessionsTitle, 18);
        sessionTextArea = new TextArea();
        sessionTextArea.setPromptText("Write your session notes, thoughts, or study content here…");
        sessionTextArea.setPrefRowCount(3);
        sessionTextArea.setWrapText(true);
        TitledPane notes = new TitledPane("Live session notes", sessionTextArea);
        notes.setExpanded(false);
        notes.setAnimated(false);
        sessionsFlowPane = new FlowPane(10, 10);
        section.getChildren().addAll(sessionsTitle, notes, sessionsFlowPane);
        sidebar.getChildren().add(section);
    }

    private void createReflectionSection(VBox mainContent) {
        VBox reflectionSection = new VBox(15);
        reflectionSection.getStyleClass().add("section-card");
        reflectionSection.setPadding(new Insets(20));

        Label reflectionTitle = new Label("Daily Reflection");
        TaskStyleUtils.fontBold(reflectionTitle, 18);

        reflectionArea = new TextArea();
        reflectionArea.setPromptText("What helped you focus today? What distracted you? One thing to improve tomorrow?");
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
        tasksById = new LinkedHashMap<>();
        taskService.getTasks().forEach(task -> tasksById.putIfAbsent(task.getId(), task));
        refreshDisplayDateAttemptCache();
        updateAttemptOverview();
        goalsTitle.setText(displayDate.equals(dateTimeService.getCurrentDate()) ? "Today's Goals" : "Goals");
        goalsContainer.getChildren().clear();
        goalSessionButtons.clear();
        List<StudyGoal> active = displayDateAttempts.stream()
                .filter(goal -> !goal.isDropped() && !goal.isDroppedAttempt()).toList();
        boolean byTask = goalGroup == GoalListAgenda.Group.TASK;
        GoalListAgenda.build(active, tasksById, goalGroup, goalSort).forEach(section -> {
            VBox group = new VBox(8);
            if (!byTask && section.title() != null) {
                group.getChildren().add(goalGroupHeader(section));
            }
            section.goals().forEach(goal ->
                    group.getChildren().add(buildGoalRow(goal, tasksById.get(goal.getTaskId()), !byTask)));
            goalsContainer.getChildren().add(byTask ? taskGoalGroup(section, group) : group);
        });
        List<StudyGoal> dropped = displayDateAttempts.stream()
                .filter(goal -> goal.isDropped() || goal.isDroppedAttempt()).toList();
        if (!dropped.isEmpty()) {
            VBox history = new VBox(8);
            dropped.forEach(goal ->
                    history.getChildren().add(buildGoalRow(goal, tasksById.get(goal.getTaskId()), true)));
            TitledPane section = new TitledPane("Dropped (" + dropped.size() + ")", history);
            section.setExpanded(false);
            goalsContainer.getChildren().add(section);
        }
        if (displayDateAttempts.isEmpty()) {
            goalsContainer.getChildren().add(mutedLabel("No goals planned for this day. Add a goal or drag work here."));
        }

        LocalDate today = dateTimeService.getCurrentDate();
        retryableGoals = displayDate.equals(today) ? StudyGoal.findDelayedAndNotReplanned() : List.of();
        List<PlannerAgenda.Entry> entries = PlannerAgenda.build(displayDate, today,
                new ArrayList<>(tasksById.values()), displayDateAttempts,
                retryableGoals,
                displayDate.equals(today) ? taskService.getMissedRecurringOccurrences(today) : List.of(),
                taskService::isRecurringOccurrence);
        planningEntries = new LinkedHashMap<>();
        needsPlanContainer.getChildren().clear();
        unlinkedRetriesContainer.getChildren().clear();
        String taskHint = "Drag a task into " + goalsTitle.getText() + ", or double-click it, to plan it.";
        needsPlanHint.setText(taskHint + " Tasks stay here so you can plan more goals.");
        TilePane retries = planTiles();
        TilePane needed = planTiles();
        TilePane comingUp = planTiles();
        for (PlannerAgenda.Entry entry : entries) {
            planningEntries.put(entry.key(), entry);
            if (entry.task() == null) {
                // Already in the goals column, so there is nowhere to drag it.
                retries.getChildren().add(buildPlanningTile(entry, "Double-click or press Enter to plan a retry.", null));
            } else {
                (entry.kind() == PlannerAgenda.Kind.REMINDER ? comingUp : needed).getChildren()
                        .add(buildPlanningTile(entry, taskHint, PLAN_DRAG));
            }
        }
        if (!retries.getChildren().isEmpty()) {
            unlinkedRetriesContainer.getChildren().addAll(
                    mutedLabel("Missed goals without a task · ready to retry"), retries);
        }
        if (!needed.getChildren().isEmpty()) {
            needsPlanContainer.getChildren().add(needed);
        }
        if (!comingUp.getChildren().isEmpty()) {
            needsPlanContainer.getChildren().addAll(mutedLabel("Coming up"), comingUp);
        }
        if (needsPlanContainer.getChildren().isEmpty()) {
            needsPlanContainer.getChildren().add(mutedLabel("No tasks to plan for this day."));
        }
        updateTaskBrowser();
        syncSessionControls();
    }

    /** A grid of same-size planning tiles; it wraps to the column's width. */
    private static TilePane planTiles() {
        TilePane tiles = new TilePane(8, 8);
        tiles.setPrefTileWidth(PLAN_TILE_WIDTH);
        tiles.setPrefTileHeight(PLAN_TILE_HEIGHT);
        tiles.setTileAlignment(Pos.TOP_LEFT);
        tiles.setPrefColumns(2);
        return tiles;
    }

    private Node buildPlanningTile(PlannerAgenda.Entry entry, String hint, DataFormat dragFormat) {
        String color = switch (entry.kind()) {
            case OVERDUE, MISSED -> TaskStyleUtils.OVERDUE_COLOR;
            case DUE -> TaskStyleUtils.DUE_TODAY_COLOR;
            case RETRY -> TaskStyleUtils.retryTextColor();
            case REMINDER -> TaskStyleUtils.REMINDER_COLOR;
            default -> TaskStyleUtils.COLOR_MUTED;
        };
        return PlanningRow.tile(entry.title(), entry.reason(), color, hint, dragFormat, entry.key(),
                () -> planEntry(entry));
    }

    private void planEntry(PlannerAgenda.Entry entry) {
        if (entry.retries().isEmpty()) {
            showAddGoalDialog(entry.task());
        } else {
            new RetryDialog(entry).refresh(null, false);
        }
    }

    /**
     * The "Plan retry" modal. Every action in it refreshes the planner and brings the dialog back
     * with a notice, so several goals can be handled in one visit; only Done closes it. The frame is
     * built once so a refresh keeps the scroll position; only the notice and rows are rebuilt.
     */
    private final class RetryDialog {
        private final PlannerAgenda.Entry entry;
        private final Label notice = new Label();
        private final VBox rows = new VBox(12);
        private final ScrollPane wrapper;

        RetryDialog(PlannerAgenda.Entry entry) {
            this.entry = entry;
            VBox content = new VBox(12);
            content.setPadding(new Insets(20));
            content.getStyleClass().add("modal-content");
            content.setMaxWidth(560);
            content.setMaxHeight(Region.USE_PREF_SIZE);
            Label title = new Label("Plan retry: " + entry.title());
            title.setWrapText(true);
            TaskStyleUtils.fontBold(title, 16);
            notice.setWrapText(true);
            TaskStyleUtils.fontNormal(notice, 12);

            Button newGoal = new Button("+ New goal");
            newGoal.getStyleClass().add("btn-purple");
            newGoal.setOnAction(event -> showAddGoalDialog(entry.task(),
                    refreshError -> showAfterRefresh("Added a new goal.", false, refreshError), this::reopen));
            Button done = new Button("Done");
            done.getStyleClass().add("btn-primary");
            done.setOnAction(event -> {
                closeModal.run();
                focusPlanningTile(entry.key());
            });
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            content.getChildren().addAll(title, mutedLabel(entry.task() != null
                            ? "Try a missed goal again today, or add a new goal for this task."
                            : "Try this missed goal again today, or add a new goal."),
                    notice, rows, new HBox(10, newGoal, spacer, done));
            wrapper = new ScrollPane(content);
            wrapper.setFitToWidth(true);
            wrapper.getStyleClass().add("transparent-bg");
            wrapper.setMaxWidth(580);
            wrapper.setMaxHeight(600);
        }

        /** Reload the planner after a change made here, then show the dialog's current retries. */
        void refresh(String message, boolean error) {
            showAfterRefresh(message, error, refreshAfterChange());
        }

        /** A failed reload must not offer actions on stale retry rows or claim the list is empty. */
        void showAfterRefresh(String message, boolean error, String refreshError) {
            if (refreshError == null) {
                show(message, error, true);
                return;
            }
            String failure = refreshError;
            if (message != null) {
                failure = (error ? message + "\n" : "Changes saved. ") + refreshError;
            }
            show(failure, true, false);
        }

        void reopen() {
            refresh(null, false);
        }

        /** Offer retry actions only when the planner's list was successfully refreshed. */
        private void show(String message, boolean error, boolean listAvailable) {
            // The planner rebuilds its entries on refresh; handled goals have left this one.
            PlannerAgenda.Entry current = planningEntries.get(entry.key());
            List<StudyGoal> retries = current == null ? List.of() : current.retries();
            notice.setText(message);
            notice.setVisible(message != null);
            notice.setManaged(message != null);
            notice.setGraphic(TaskStyleUtils.iconLabel(error ? "\u26A0" : "\u2713", 12));
            TaskStyleUtils.textColor(notice, error ? TaskStyleUtils.COLOR_DANGER : TaskStyleUtils.COLOR_SUCCESS);
            rows.getChildren().clear();
            if (!listAvailable) {
                rows.getChildren().add(mutedLabel("Retry list unavailable. Close and reopen this dialog to refresh."));
            } else if (retries.isEmpty()) {
                rows.getChildren().add(mutedLabel("No missed goals left to retry."));
            } else {
                rows.getChildren().setAll(retries.stream().map(goal -> (Node) buildTaskRetryRow(goal, this)).toList());
            }
            showModal.accept(wrapper);
        }
    }

    /**
     * Reload the planner after a change that already succeeded. A failure here is reported rather
     * than thrown, so the dialog that made the change still closes or moves on instead of staying
     * open and inviting the same save twice.
     */
    private String refreshAfterChange() {
        try {
            updateTasksDisplay();
            updateProgress();
            plannerError.setVisible(false);
            plannerError.setManaged(false);
            return null;
        } catch (Exception ex) {
            String message = "The planner could not refresh: " + ex.getMessage();
            showPlannerError(message);
            return message;
        }
    }

    /**
     * Opening the retry dialog rebuilds the planning tiles, so the one it was opened from has lost
     * keyboard focus; give it to that entry's new tile, if it is still listed.
     */
    private void focusPlanningTile(String key) {
        Stream.of(needsPlanContainer, unlinkedRetriesContainer)
                .flatMap(container -> container.lookupAll(".plan-tile").stream())
                .filter(tile -> key.equals(tile.getProperties().get(PlanningRow.KEY)))
                .findFirst().ifPresent(Node::requestFocus);
    }

    private void installPlanningDropTarget(VBox goals) {
        goals.setOnDragOver(event -> {
            if (planningEntries.containsKey(event.getDragboard().getContent(PLAN_DRAG))) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });
        goals.setOnDragEntered(event -> goals.pseudoClassStateChanged(DROP,
                planningEntries.containsKey(event.getDragboard().getContent(PLAN_DRAG))));
        goals.setOnDragExited(event -> goals.pseudoClassStateChanged(DROP, false));
        goals.setOnDragDropped(event -> {
            PlannerAgenda.Entry entry = planningEntries.get(event.getDragboard().getContent(PLAN_DRAG));
            event.setDropCompleted(entry != null);
            goals.pseudoClassStateChanged(DROP, false);
            if (entry != null) {
                Platform.runLater(() -> planEntry(entry));
            }
            event.consume();
        });
    }

    private Label mutedLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        TaskStyleUtils.fontNormal(label, 12);
        TaskStyleUtils.textColor(label, TaskStyleUtils.COLOR_MUTED);
        return label;
    }

    private void showPlannerError(String message) {
        plannerError.setText(message);
        plannerError.setVisible(true);
        plannerError.setManaged(true);
    }

    private void updateTaskBrowser() {
        tasksContainer.getChildren().clear();
        List<Task> tasks = new ArrayList<>(tasksForCurrentView());
        tasks.sort(taskSortComparator());
        Map<String, List<Task>> groups = new LinkedHashMap<>();
        tasks.forEach(task -> groups.computeIfAbsent(groupKeyFor(task), key -> new ArrayList<>()).add(task));
        groups.forEach((name, items) -> {
            if (!name.isEmpty()) {
                tasksContainer.getChildren().add(mutedLabel(name));
            }
            items.forEach(task -> tasksContainer.getChildren().add(buildTaskRow(task)));
        });
        if (tasks.isEmpty()) {
            tasksContainer.getChildren().add(mutedLabel("No active tasks in this view."));
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
        long dropped = attempts.stream().filter(StudyGoal::isDroppedAttempt).count();
        long missed = attempts.stream()
                .filter(goal -> goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.MISSED)
                .count();
        long retrying = attempts.stream()
                .filter(goal -> goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.PENDING
                        && goal.getMissedAttemptCount() > 0)
                .count();
        int score = (int) (achieved - missed);

        attemptOverviewContainer.getChildren().addAll(
                createAttemptMetricCard("Goal net", String.format("%+d", score),
                        TaskStyleUtils.COLOR_PURPLE, TaskStyleUtils.TINT_PURPLE, true),
                createAttemptMetricCard("Goals", String.valueOf(attempts.size() - dropped),
                        TaskStyleUtils.COLOR_PRIMARY, TaskStyleUtils.TINT_NEUTRAL, false),
                createAttemptMetricCard("Pending", String.valueOf(pending),
                        TaskStyleUtils.COLOR_PRIMARY, TaskStyleUtils.TINT_NEUTRAL, false),
                createAttemptMetricCard("Retry", String.valueOf(retrying),
                        TaskStyleUtils.COLOR_ORANGE, TaskStyleUtils.TINT_NEUTRAL, false),
                createAttemptMetricCard("Done", String.valueOf(achieved),
                        TaskStyleUtils.COLOR_SUCCESS, TaskStyleUtils.TINT_NEUTRAL, false),
                createAttemptMetricCard("Missed", String.valueOf(missed),
                        TaskStyleUtils.COLOR_DANGER, TaskStyleUtils.TINT_NEUTRAL, false),
                createAttemptMetricCard("Dropped", String.valueOf(dropped),
                        TaskStyleUtils.COLOR_MUTED, TaskStyleUtils.TINT_NEUTRAL, false)
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
        TaskStyleUtils.textColor(valueLabel, textColor);

        Label titleLabel = new Label(title);
        TaskStyleUtils.fontNormal(titleLabel, 10);
        TaskStyleUtils.textColor(titleLabel, textColor);

        card.getChildren().addAll(valueLabel, titleLabel);
        return card;
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
            return tasksById.values().stream()
                    .filter(this::isActivePlannerTask)
                    .toList();
        }
        return taskService.getTasksForDate(displayDate);
    }

    private boolean isActivePlannerTask(Task task) {
        return CalendarAgenda.isActive(task.getStatus());
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
            case "Reason" -> reasonGroupKeyFor(task);
            default -> "";
        };
    }

    private String reasonGroupKeyFor(Task task) {
        boolean surfaces = taskService.taskSurfacesOn(task, displayDate);
        LocalDate deadline = task.getDeadline();
        // Deadline state is checked before recurrence: a recurring task that is
        // late is here because it is late, not because today is its day.
        if (surfaces && deadline != null && deadline.isBefore(displayDate)) {
            return "Overdue";
        }
        if (surfaces && deadline != null && deadline.equals(displayDate)) {
            return "Due";
        }
        if (surfaces && taskService.isRecurringOccurrence(task, displayDate)) {
            return "Recurring";
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

    private Node buildTaskRow(Task task) {
        VBox row = new VBox(6);
        row.getStyleClass().add("planner-task-row");
        Label title = new Label(taskTitle(task));
        title.setWrapText(true);
        TaskStyleUtils.fontSemiBold(title, 13);
        Label status = mutedLabel(task.getStatus() + " · "
                + (task.getPriority() == null ? "" : task.getPriority() + " · ") + reasonGroupKeyFor(task));
        TaskStyleUtils.textColor(status, TaskStyleUtils.statusTextColor(task.getStatus()));
        if (task.isRecurring()) {
            status.setText(status.getText() + " · " + task.getRecurringSummary());
        }
        if (task.getDeadline() != null) {
            status.setText(status.getText() + " · due " + task.getDeadline());
        }
        Button add = new Button("+ Add goal");
        add.getStyleClass().addAll("btn-purple", "btn-small");
        add.setOnAction(event -> showAddGoalDialog(task));
        row.getChildren().addAll(title, status, add);
        FlowPane badges = new FlowPane(6, 4);
        badges.getChildren().addAll(createTaskAttemptBadges(task));
        if (TaskStyleUtils.isOverdue(task, displayDate)) {
            badges.getChildren().add(TaskStyleUtils.createOverdueBadge());
        } else if (TaskStyleUtils.isDueToday(task, displayDate)) {
            badges.getChildren().add(TaskStyleUtils.createDueTodayBadge());
        } else if (task.isReminderDue(displayDate)) {
            badges.getChildren().add(TaskStyleUtils.createReminderBadge(
                    ChronoUnit.DAYS.between(displayDate, task.getDeadline())));
        }
        if (!badges.getChildren().isEmpty()) {
            row.getChildren().add(badges);
        }
        retryableGoals.stream().filter(goal -> task.getId().equals(goal.getTaskId())).forEach(goal ->
                row.getChildren().add(buildTaskRetryRow(goal, null)));
        return row;
    }

    private List<Label> createTaskAttemptBadges(Task task) {
        List<StudyGoal> attempts = displayDateAttemptsByTaskId.getOrDefault(task.getId(), List.of());
        if (attempts.isEmpty()) {
            return List.of();
        }
        long pending = attempts.stream()
                .filter(goal -> goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.PENDING).count();
        long achieved = attempts.stream().filter(StudyGoal::isAchieved).count();
        long missed = attempts.stream()
                .filter(goal -> goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.MISSED).count();
        long retries = attempts.stream().filter(goal -> goal.getMissedAttemptCount() > 0).count();
        List<Label> badges = new ArrayList<>();
        badges.add(TaskStyleUtils.createAttemptBadge(attempts.size() + " goal" + (attempts.size() == 1 ? "" : "s"),
                TaskStyleUtils.COLOR_PRIMARY, TaskStyleUtils.TINT_NEUTRAL));
        if (pending > 0) {
            badges.add(TaskStyleUtils.createAttemptBadge(pending + " pending",
                    TaskStyleUtils.COLOR_PRIMARY, TaskStyleUtils.TINT_PRIMARY));
        }
        if (retries > 0) {
            badges.add(TaskStyleUtils.createAttemptBadge(retries + " retry",
                    TaskStyleUtils.retryTextColor(), TaskStyleUtils.retryBackgroundColor()));
        }
        if (achieved > 0) {
            badges.add(TaskStyleUtils.createAttemptBadge(achieved + " done",
                    TaskStyleUtils.COLOR_SUCCESS, TaskStyleUtils.TINT_SUCCESS));
        }
        if (missed > 0) {
            badges.add(TaskStyleUtils.createAttemptBadge(missed + " missed",
                    TaskStyleUtils.COLOR_DANGER, TaskStyleUtils.TINT_DANGER));
        }
        return badges;
    }

    /**
     * A missed goal as a card: text on top, actions on their own line so their labels never truncate.
     * Inside the retry dialog its actions return to that dialog; in the task browser ({@code dialog}
     * null) they just refresh the planner.
     */
    private VBox buildTaskRetryRow(StudyGoal goal, RetryDialog dialog) {
        VBox row = new VBox(4);
        row.getStyleClass().add("planner-retry-row");

        Label goalLabel = new Label(goal.getDescription());
        goalLabel.setWrapText(true);
        TaskStyleUtils.fontSemiBold(goalLabel, 13);
        Label attemptLabel = new Label(formatRetryAttemptSummary(goal));
        TaskStyleUtils.fontNormal(attemptLabel, 11);
        TaskStyleUtils.textColor(attemptLabel, TaskStyleUtils.retryTextColor());
        attemptLabel.setWrapText(true);

        Button replanBtn = new Button("Plan retry today");
        replanBtn.setGraphic(TaskStyleUtils.iconLabel("\u21BA", 12));
        replanBtn.getStyleClass().addAll("btn-orange", "btn-small");
        Button editBtn = editGoalButton(goal);
        if (dialog != null) {
            editBtn.setOnAction(e -> showEditGoalDialog(goal,
                    refreshError -> dialog.showAfterRefresh("Goal saved.", false, refreshError),
                    dialog::reopen));
        }
        MenuButton more = goalActions(goal, dialog == null ? null
                : () -> dialog.refresh("Updated \u201C" + goal.getDescription() + "\u201D.", false));
        FlowPane actions = new FlowPane(6, 6, replanBtn, editBtn, more);
        actions.setPadding(new Insets(4, 0, 0, 0));
        actions.getChildren().forEach(node -> ((Region) node).setMinWidth(Region.USE_PREF_SIZE));
        replanBtn.setOnAction(e -> {
            if (goal.isChecklistComplete()) {
                askCompletedChecklist(goal, dialog, actions);
            } else {
                retryToday(goal, dialog, null);
            }
        });

        row.getChildren().addAll(goalLabel, attemptLabel, actions);
        return row;
    }

    /**
     * Every checklist line is already ticked, so a retry would start pending with nothing left to do.
     * Ask in the row itself, replacing its actions until the user answers or cancels.
     */
    private void askCompletedChecklist(StudyGoal goal, RetryDialog dialog, FlowPane actions) {
        List<Node> original = List.copyOf(actions.getChildren());
        Label prompt = mutedLabel("Every checklist item is already ticked.");
        Button startOver = new Button("Start over");
        startOver.getStyleClass().addAll("btn-orange", "btn-small");
        startOver.setOnAction(e -> retryToday(goal, dialog, StudyService.CompletedChecklist.START_OVER));
        Button markDone = new Button("Mark done");
        markDone.getStyleClass().addAll("btn-success", "btn-small");
        markDone.setOnAction(e -> retryToday(goal, dialog, StudyService.CompletedChecklist.MARK_DONE));
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().addAll("btn-gray", "btn-small");
        cancel.setOnAction(e -> actions.getChildren().setAll(original));
        List.of(startOver, markDone, cancel).forEach(button -> button.setMinWidth(Region.USE_PREF_SIZE));
        actions.getChildren().setAll(prompt, startOver, markDone, cancel);
    }

    /** Plan the goal's retry for today; {@code choice} answers a complete checklist, else null. */
    private void retryToday(StudyGoal goal, RetryDialog dialog, StudyService.CompletedChecklist choice) {
        String error = null;
        try {
            if (!studyService.replanGoalForToday(goal.getId(), choice)) {
                error = dialog != null
                        ? "This goal is no longer ready to retry. The list has been updated."
                        : "This goal is no longer ready to retry. Refresh the planner and try again.";
            }
        } catch (Exception ex) {
            error = "Could not plan retry: " + ex.getMessage();
        }
        if (dialog != null) {
            String name = "“" + goal.getDescription() + "”";
            String done = choice == StudyService.CompletedChecklist.MARK_DONE ? "Marked " + name + " done."
                    : choice == StudyService.CompletedChecklist.START_OVER
                    ? "Planned " + name + " for today with its checklist cleared."
                    : "Planned " + name + " for today.";
            dialog.refresh(error != null ? error : done, error != null);
        } else {
            refreshAfterChange();
            if (error != null) {
                showPlannerError(error);
            }
        }
    }

    private String formatRetryAttemptSummary(StudyGoal goal) {
        // Retry rows show the goal's latest attempt, the one that was missed.
        String summary = "Attempt " + goal.getAttemptNumber() + " missed";
        if (goal.getDate() != null) {
            summary += " " + goal.getDate().format(DateTimeFormatter.ofPattern("MMM d"));
        }
        return summary + TaskStyleUtils.criteriaProgress(goal.getCriteria());
    }

    /** Independently collapsible task goals, retaining the user's choice across planner rebuilds. */
    private TitledPane taskGoalGroup(GoalListAgenda.Section section, VBox content) {
        Task task = tasksById.get(section.goals().getFirst().getTaskId());
        String taskId = task == null ? null : task.getId();
        HBox header = goalGroupHeader(section);
        TitledPane pane = new TitledPane(section.title(), content);
        pane.setMinWidth(0);
        pane.getStyleClass().add("planner-task-goals");
        pane.setGraphic(header);
        pane.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        pane.setAccessibleText(header.getAccessibleText());
        pane.setAnimated(false);
        pane.setExpanded(!collapsedGoalTaskIds.contains(taskId));
        pane.expandedProperty().addListener((observable, wasExpanded, expanded) -> {
            if (expanded) {
                collapsedGoalTaskIds.remove(taskId);
            } else {
                collapsedGoalTaskIds.add(taskId);
            }
        });
        return pane;
    }

    /** A group heading with the task's planned/done summary, or a status's goal count. */
    private HBox goalGroupHeader(GoalListAgenda.Section section) {
        Label title = new Label(section.title());
        title.setWrapText(true);
        title.setMinWidth(0);
        TaskStyleUtils.fontBold(title, 14);
        int total = section.goals().size();
        String count = goalGroup == GoalListAgenda.Group.TASK
                ? total + " planned \u00B7 " + section.goals().stream().filter(StudyGoal::isAchieved).count() + " done"
                : total + (total == 1 ? " goal" : " goals");
        Label countLabel = mutedLabel(count);
        countLabel.setWrapText(false);
        countLabel.setMinWidth(Region.USE_PREF_SIZE);
        HBox header = new HBox(8, title, countLabel);
        header.setMinWidth(0);
        HBox.setHgrow(title, Priority.ALWAYS);
        header.setAccessibleText(section.title() + ", " + count);
        header.setAlignment(Pos.BASELINE_LEFT);
        header.getStyleClass().add("planner-goal-group-header");
        return header;
    }

    /** {@code showTask} names the goal's task under its title; off when a task heading already does. */
    private HBox buildGoalRow(StudyGoal goal, Task linkedTask, boolean showTask) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("planner-goal-row");

        String bgColor = TaskStyleUtils.TINT_NEUTRAL;
        boolean missed = goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.MISSED
                || goal.isDropped() || goal.isDroppedAttempt();
        if (goal.isAchieved()) {
            bgColor = TaskStyleUtils.TINT_SUCCESS;
        } else if (!missed && goal.isDelayed()) {
            bgColor = goal.getDelayColorIntensity() < 0.5 ? "#fff3cd" : "#f8d7da";
        }
        row.setStyle("-fx-background-color: " + bgColor + ";");

        CheckBox check = new CheckBox();
        check.setSelected(goal.isAchieved());
        check.setDisable(missed);
        check.setAccessibleText("Achieve goal: " + goal.getDescription());
        check.setOnAction(e -> {
            try {
                studyService.updateStudyGoalAchievement(goal.getId(), check.isSelected(), null);
            } catch (Exception ex) {
                showPlannerError("Could not update goal: " + ex.getMessage());
            }
            updateProgress();
            updateTasksDisplay();
        });

        // The goal and its actions share the first line; its task, checklist and attempt sit under it.
        Label title = new Label(goal.getDescription());
        title.setWrapText(true);
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        TaskStyleUtils.fontBold(title, 13);
        if (missed || goal.isAchieved()) {
            TaskStyleUtils.textColor(title, TaskStyleUtils.COLOR_MUTED);
        }
        HBox.setHgrow(title, Priority.ALWAYS);
        HBox actions = new HBox(6, editGoalButton(goal));
        actions.setMinWidth(Region.USE_PREF_SIZE);
        if (!missed && !goal.isAchieved() && displayDate.equals(dateTimeService.getCurrentDate())) {
            Button start = new Button("Start session");
            start.getStyleClass().addAll("btn-success", "btn-small");
            start.setOnAction(event -> startSession(goal));
            start.setDisable(hasActiveSession());
            goalSessionButtons.add(start);
            actions.getChildren().add(start);
        }
        actions.getChildren().add(goalActions(goal));
        HBox titleLine = new HBox(10, title, actions);
        titleLine.setAlignment(Pos.TOP_LEFT);

        VBox textBox = new VBox(4, titleLine);
        textBox.setMinWidth(0);
        if (showTask && linkedTask != null) {
            Label taskLabel = mutedLabel("Task: " + taskTitle(linkedTask));
            taskLabel.setMinWidth(0);
            textBox.getChildren().add(taskLabel);
        }
        if (!goal.getCriteria().isEmpty()) {
            textBox.getChildren().add(TaskStyleUtils.criteriaChecklist(goal.getCriteria(),
                    missed ? null : criterionToggle(goal)));
        }

        Label attemptLabel = new Label(formatAttemptSummary(goal));
        attemptLabel.setWrapText(true);
        TaskStyleUtils.textColor(attemptLabel, missed ? TaskStyleUtils.COLOR_MUTED
                : goal.getMissedAttemptCount() > 0 ? TaskStyleUtils.COLOR_DANGER : TaskStyleUtils.COLOR_MUTED);
        TaskStyleUtils.fontNormal(attemptLabel, 11);
        textBox.getChildren().add(attemptLabel);
        if (goal.isDropped() && goal.getDropReason() != null) {
            textBox.getChildren().add(mutedLabel("Reason: " + goal.getDropReason()));
        }
        HBox.setHgrow(textBox, Priority.ALWAYS);
        row.getChildren().addAll(check, textBox);
        return row;
    }

    private Button editGoalButton(StudyGoal goal) {
        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().addAll("btn-gray", "btn-small");
        editBtn.setOnAction(e -> showEditGoalDialog(goal));
        return editBtn;
    }

    private MenuButton goalActions(StudyGoal goal) {
        return goalActions(goal, null);
    }

    /** {@code afterChange} replaces the default close-and-refresh; it must refresh the planner itself. */
    private MenuButton goalActions(StudyGoal goal, Runnable afterChange) {
        return GoalActions.menu(goal, studyService, dateTimeService.getCurrentDate(), afterChange != null
                ? afterChange : () -> {
                    closeModal.run();
                    updateTasksDisplay();
                    updateProgress();
                });
    }

    private void showEditGoalDialog(StudyGoal goal) {
        showEditGoalDialog(goal, refreshError -> closeModal.run(), closeModal);
    }

    /**
     * Edit an existing goal's description, done-when checklist and (while pending) its date.
     * {@code onSaved} receives the refresh error, or null on success, after the save succeeds.
     */
    private void showEditGoalDialog(StudyGoal goal, Consumer<String> onSaved, Runnable onCancel) {
        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("modal-content");
        content.setMaxWidth(450);
        content.setMaxHeight(Region.USE_PREF_SIZE);

        Label headerLabel = new Label("Edit goal");
        TaskStyleUtils.fontBold(headerLabel, 16);
        StudyGoal currentGoal = StudyGoal.findById(goal.getId()).orElse(goal);
        GoalEditFields fields = new GoalEditFields(currentGoal, displayDate);

        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setOnAction(e -> {
            try {
                fields.save(studyService, currentGoal);
            } catch (Exception ex) {
                fields.showError(ex.getMessage());
                return;
            }
            onSaved.accept(refreshAfterChange());
        });
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-cancel");
        cancelBtn.setOnAction(e -> onCancel.run());
        HBox btnRow = new HBox(10, saveBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        content.getChildren().addAll(headerLabel, fields.view(), btnRow);
        showModal.accept(content);
    }

    private String formatAttemptSummary(StudyGoal goal) {
        String summary = "Attempt " + goal.getAttemptNumber();
        if (goal.isAchieved()) {
            summary += " - achieved";
        } else if (goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.MISSED) {
            summary += " - missed";
        } else if (goal.isDroppedAttempt()) {
            summary += " - dropped";
        } else {
            summary += " - pending";
        }
        if (goal.isDropped() && !goal.isDroppedAttempt()) {
            summary += " · goal dropped";
        }
        return summary + TaskStyleUtils.criteriaProgress(goal.getCriteria());
    }

    private BiConsumer<Integer, Boolean> criterionToggle(StudyGoal goal) {
        return (index, ticked) -> {
            try {
                studyService.setGoalCriterionDone(goal.getId(), index, ticked);
            } catch (Exception ex) {
                showPlannerError("Could not update checklist: " + ex.getMessage());
            }
            updateProgress();
            updateTasksDisplay();
        };
    }

    // ──────────────────────────────────────────────
    // SESSION DISPLAY (FlowPane)
    // ──────────────────────────────────────────────

    private void updateSessionsDisplay() {
        sessionsTitle.setText(displayDate.equals(dateTimeService.getCurrentDate())
                ? "Today's Sessions" : "Sessions · " + displayDate.format(DateTimeFormatter.ofPattern("MMM d")));
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
            TaskStyleUtils.textColor(none, "#7f8c8d");
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
        TaskStyleUtils.textColor(durationLabel, "#7f8c8d");

        // Focus stars (compact)
        StringBuilder stars = new StringBuilder();
        for (int i = 1; i <= 5; i++) stars.append(i <= session.getFocusLevel() ? "\u2605" : "\u2606");
        Label focusLabel = new Label(stars.toString());
        TaskStyleUtils.fontEmoji(focusLabel, 12);
        TaskStyleUtils.textColor(focusLabel, "#f39c12");

        Label pointsLabel = new Label(session.getPointsEarned() + " pts");
        pointsLabel.setGraphic(TaskStyleUtils.iconLabel("\u2666", 11));
        TaskStyleUtils.fontSemiBold(pointsLabel, 11);
        TaskStyleUtils.textColor(pointsLabel, "#27ae60");

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
        Label linkLabel = TaskStyleUtils.sessionLinkLabel(session);
        if (linkLabel != null) {
            card.getChildren().add(card.getChildren().size() - 1, linkLabel);
        }
        return card;
    }

    /** Renders a goal or task entry in the session link ComboBox. */
    private ListCell<Object> sessionLinkCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : sessionLinkText(item));
            }
        };
    }

    private String sessionLinkText(Object item) {
        String text;
        if (item instanceof StudyGoal goal) {
            text = "Goal: " + goal.getDescription();
        } else if (item instanceof Task task) {
            text = "Task: " + taskTitle(task);
        } else {
            return "None";
        }
        return text.length() > 60 ? text.substring(0, 57) + "\u2026" : text;
    }

    /** Fills the link ComboBox with today's goals, then tasks not already covered by one. */
    private void populateSessionLinkCombo(ComboBox<Object> combo) {
        Object selected = combo.getValue();
        combo.getItems().clear();
        combo.getItems().add(null);
        LocalDate today = dateTimeService.getCurrentDate();
        List<StudyGoal> goals = studyService.getStudyGoalsForDate(today).stream()
                .filter(g -> !g.isAchieved())
                .toList();
        combo.getItems().addAll(goals);
        Set<String> linkedTaskIds = goals.stream()
                .map(StudyGoal::getTaskId)
                .filter(id -> id != null && !id.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        taskService.getTasksForDate(today).stream()
                .filter(t -> !linkedTaskIds.contains(t.getId()))
                .forEach(combo.getItems()::add);
        // Re-select by id: StudyGoal has no equals(), and the previously
        // selected instance is never in the freshly loaded list.
        combo.setValue(combo.getItems().stream()
                .filter(item -> sameLinkTarget(item, selected))
                .findFirst().orElse(null));
    }

    private static boolean sameLinkTarget(Object item, Object selected) {
        if (item instanceof StudyGoal goal && selected instanceof StudyGoal prev) {
            return goal.getId().equals(prev.getId());
        }
        if (item instanceof Task task && selected instanceof Task prev) {
            return task.getId().equals(prev.getId());
        }
        return false;
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
        TaskStyleUtils.textColor(statusLabel, "#d35400");

        Label timeLabel = new Label(startStr + "\u2013" + endStr);
        timeLabel.setGraphic(TaskStyleUtils.iconLabel("\u23F0", 12));
        TaskStyleUtils.fontBold(timeLabel, 12);

        String durationText = session.getDurationMinutes() > 0
                ? session.getDurationMinutes() + " min tracked"
                : "Not completed";
        Label durationLabel = new Label(durationText);
        TaskStyleUtils.fontNormal(durationLabel, 11);
        TaskStyleUtils.textColor(durationLabel, "#7f8c8d");

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
        Label linkLabel = TaskStyleUtils.sessionLinkLabel(session);
        if (linkLabel != null) {
            card.getChildren().add(card.getChildren().size() - 1, linkLabel);
        }
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

        DatePicker recurrenceEndPicker = new DatePicker();
        recurrenceEndPicker.setPromptText("Never ends");
        recurrenceEndPicker.setMaxWidth(Double.MAX_VALUE);

        recurringOptions.getChildren().addAll(new Label("Repeat every:"), intervalRow,
                new Label("On days:"), daysRow,
                new Label("Start date:"), startDatePicker,
                new Label("Repeat until:"), recurrenceEndPicker);

        Label deadlineHint = new Label("");
        TaskStyleUtils.fontNormal(deadlineHint, 10);
        TaskStyleUtils.textColor(deadlineHint, "#7f8c8d");
        deadlineHint.setWrapText(true);
        deadlineHint.setVisible(false);
        deadlineHint.setManaged(false);

        recurringCheck.selectedProperty().addListener((obs, o, n) -> {
            recurringOptions.setVisible(n);
            recurringOptions.setManaged(n);
            deadlineHint.setVisible(n);
            deadlineHint.setManaged(n);
            deadlineHint.setText(n
                    ? "The deadline is when this task is due. \"Repeat until\" is what stops the repetition."
                    : "");
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
                LocalDate recurrenceEnd = recurringCheck.isSelected() ? recurrenceEndPicker.getValue() : null;
                Task newTask = new Task(null, title, descArea.getText().trim(),
                        cat.name(), new TaskPriority(prio),
                        deadlinePicker.getValue(), TaskStatus.OPEN, 0, recurPattern, startDate, recurrenceEnd);
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
        TaskStyleUtils.textColor(err, "#e74c3c");
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
        showAddGoalDialog(linkedTask, refreshError -> closeModal.run(), closeModal);
    }

    /** {@code onAdded} receives the refresh error, or null on success, after the save succeeds. */
    private void showAddGoalDialog(Task linkedTask, Consumer<String> onAdded, Runnable onCancel) {
        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("modal-content");
        content.setMaxWidth(450);
        content.setMaxHeight(Region.USE_PREF_SIZE);

        Label headerLabel = new Label(linkedTask != null
                ? "Add goal for: " + taskTitle(linkedTask)
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

        TextArea criteriaArea = TaskStyleUtils.criteriaArea(null);
        Label criteriaLabel = new Label(TaskStyleUtils.CRITERIA_LABEL);

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
                    setText(empty || item == null ? "None" : taskTitle(item));
                }
            });
            taskCombo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(Task item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "None" : taskTitle(item));
                }
            });
            taskSection.getChildren().addAll(taskLabel, taskCombo);
            content.getChildren().addAll(headerLabel, dateRow,
                    new Label("Description:"), descArea, criteriaLabel, criteriaArea, taskSection);
        } else {
            content.getChildren().addAll(headerLabel, dateRow,
                    new Label("Description:"), descArea, criteriaLabel, criteriaArea);
        }

        Button okBtn = new Button("Add Goal");
        okBtn.getStyleClass().add("btn-purple");
        okBtn.setDisable(true);
        descArea.textProperty().addListener((obs, o, n) -> okBtn.setDisable(n.trim().isEmpty()));

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-cancel");
        cancelBtn.setOnAction(e -> onCancel.run());

        okBtn.setOnAction(e -> {
            String desc = descArea.getText().trim();
            if (desc.isEmpty()) return;
            LocalDate date = datePicker.getValue() != null ? datePicker.getValue() : displayDate;
            String taskId = linkedTask != null ? linkedTask.getId()
                    : (taskCombo.getValue() != null ? taskCombo.getValue().getId() : null);
            try {
                studyService.addStudyGoal(desc, date, taskId, criteriaArea.getText());
            } catch (Exception ex) {
                Label err = new Label(ex.getMessage());
                err.setGraphic(TaskStyleUtils.iconLabel("\u26A0", 12));
                TaskStyleUtils.textColor(err, "#e74c3c");
                content.getChildren().add(err);
                return;
            }
            onAdded.accept(refreshAfterChange());
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
        TaskStyleUtils.textColor(focusWarning, "#f39c12");

        focusSlider.valueProperty().addListener((obs, o, nv) -> {
            int lv = nv.intValue();
            if (lv <= 2) {
                focusWarning.setText("Low focus \u2014 this session scores less per minute.");
                focusWarning.setGraphic(TaskStyleUtils.iconLabel("\u26A0", 11));
                TaskStyleUtils.textColor(focusWarning, "#e74c3c");
            } else if (lv == 3) {
                focusWarning.setText("Average focus.");
                focusWarning.setGraphic(null);
                TaskStyleUtils.textColor(focusWarning, "#f39c12");
            } else {
                focusWarning.setText("Great focus \u2014 this session scores more per minute.");
                focusWarning.setGraphic(TaskStyleUtils.iconLabel("\u2713", 11));
                TaskStyleUtils.textColor(focusWarning, "#27ae60");
            }
        });

        TextArea notesArea = new TextArea();
        notesArea.setPromptText("What did you accomplish?");
        notesArea.setPrefRowCount(3);

        Label errorLabel = new Label();
        errorLabel.setGraphic(TaskStyleUtils.iconLabel("\u26A0", 12));
        TaskStyleUtils.textColor(errorLabel, "#e74c3c");
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
        TaskStyleUtils.textColor(sessionIcon, "#ffffff");
        sessionTitle.setGraphic(sessionIcon);
        TaskStyleUtils.fontBold(sessionTitle, 24);
        TaskStyleUtils.textColor(sessionTitle, "#ffffff");

        Label dateTime = new Label(
                session.getDate().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")) +
                " • " + (session.getStartTime() != null
                        ? session.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "N/A"));
        TaskStyleUtils.fontNormal(dateTime, 16);
        TaskStyleUtils.textColor(dateTime, "#f8f9fa");

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
        TaskStyleUtils.textColor(valueL, "#ffffff");
        Label descL = new Label(label);
        TaskStyleUtils.fontNormal(descL, 12);
        TaskStyleUtils.textColor(descL, "#f8f9fa");
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
            TaskStyleUtils.textColor(star, i <= session.getFocusLevel() ? "#f39c12" : "#bdc3c7");
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
            TaskStyleUtils.textColor(sessionStatusLabel, "#27ae60");
        } else {
            sessionStatusLabel.setText("No active session");
            TaskStyleUtils.textColor(sessionStatusLabel, null);
        }
    }

    private boolean hasActiveSession() {
        return currentSession != null && currentSession.isActive();
    }

    private void syncSessionControls() {
        if (startSessionBtn == null || endSessionBtn == null || sessionStatusLabel == null || sessionTextArea == null) {
            return;
        }

        boolean hasActiveSession = hasActiveSession();
        startSessionBtn.setDisable(hasActiveSession);
        startSessionBtn.setVisible(!hasActiveSession);
        startSessionBtn.setManaged(!hasActiveSession);
        endSessionBtn.setDisable(!hasActiveSession);
        endSessionBtn.setVisible(hasActiveSession);
        endSessionBtn.setManaged(hasActiveSession);
        sessionLinkCombo.setVisible(!hasActiveSession);
        sessionLinkCombo.setManaged(!hasActiveSession);
        sessionTextArea.setDisable(!hasActiveSession);
        goalSessionButtons.forEach(button -> button.setDisable(hasActiveSession));
        sessionLinkLabel.setVisible(hasActiveSession);
        sessionLinkLabel.setManaged(hasActiveSession);
        if (hasActiveSession) {
            String goal = currentSession.getGoalId() == null ? null
                    : StudyGoal.findById(currentSession.getGoalId()).map(StudyGoal::getDescription).orElse(null);
            String task = currentSession.getTaskId() == null ? null
                    : Task.findById(currentSession.getTaskId()).map(this::taskTitle).orElse(null);
            String link = task != null && goal != null ? task + " · " + goal
                    : task != null ? task : goal != null ? goal : "Unlinked session";
            sessionLinkLabel.setText(link.replaceAll("\\s+", " "));
            sessionLinkLabel.setTooltip(new Tooltip(link));
        }

        if (!hasActiveSession) {
            updateSessionTimer();
        }
    }

    private void restoreActiveSessionIfNeeded() {
        if (currentSession != null || sessionTextArea == null) {
            return;
        }
        StudySession activeSession = studyService.getActiveSession().orElse(null);
        if (activeSession == null) {
            return;
        }

        currentSession = activeSession;
        sessionTextArea.setText(activeSession.getSessionText() != null ? activeSession.getSessionText() : "");
        startSessionTimer();
    }

    private void saveReflection() {
        String text = reflectionArea.getText();
        boolean deleting = text == null || text.isBlank();

        studyService.saveReflectionText(displayDate, text);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("modal-content");
        content.setMaxWidth(300);
        content.setMaxHeight(Region.USE_PREF_SIZE);
        Label titleL = new Label(deleting ? "Reflection Deleted" : "Reflection Saved");
        TaskStyleUtils.fontBold(titleL, 16);
        Button okBtn = new Button("OK");
        okBtn.getStyleClass().add("btn-primary");
        okBtn.setOnAction(e -> closeModal.run());
        HBox btnRow = new HBox(okBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        String confirmation = deleting
                ? "Your reflection has been deleted."
                : "Your reflection has been saved!";
        content.getChildren().addAll(titleL, new Label(confirmation), btnRow);
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
        plannerError.setVisible(false);
        plannerError.setManaged(false);
        restoreActiveSessionIfNeeded();
        boolean isToday = displayDate.equals(dateTimeService.getCurrentDate());
        String prefix = isToday ? "Today \u2014 " : "";
        dateNavLabel.setGraphic(dateNavIcon);
        dateNavLabel.setText(prefix + displayDate.format(
                DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")));

        updateTasksDisplay();
        // After the tasks are loaded: the session bar names the session's task.
        syncSessionControls();
        updateSessionsDisplay();
        updateProgress();

        DailyReflection todayReflection = studyService.getDailyReflectionForDate(displayDate).orElse(null);
        reflectionArea.setText(todayReflection != null ? todayReflection.getReflectionText() : "");
    }

    @Override
    public Node getView() {
        return this;
    }

    private String taskTitle(Task task) {
        return CalendarAgenda.taskTitle(task, tasksById);
    }

}
