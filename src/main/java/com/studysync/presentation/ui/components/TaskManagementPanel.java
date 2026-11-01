package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.Task;
import com.studysync.domain.entity.TaskLink;
import com.studysync.domain.entity.TaskReschedule;
import com.studysync.domain.valueobject.TaskCategory;
import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.domain.valueobject.TaskStatus;
import com.studysync.domain.service.TaskService;
import com.studysync.domain.service.CategoryService;
import com.studysync.domain.service.StudyService;
import com.studysync.domain.service.TaskUpdate;
import javafx.application.HostServices;
import javafx.css.PseudoClass;
import javafx.scene.web.WebView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Urgency-grouped task families with a persistent selection and detail sidebar.
 * Create/edit forms open in the app modal overlay.
 */
public class TaskManagementPanel extends BorderPane implements RefreshablePanel {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final TaskService taskService;
    private final CategoryService categoryService;
    private final StudyService studyService;
    private final Consumer<Node> showModal;
    private final Runnable closeModal;
    private final HostServices hostServices;

    private final VBox taskList = new VBox(12);
    private final VBox taskDetails = new VBox(12);
    private final ScrollPane detailScroll;
    private final Set<TaskListAgenda.Section> expandedSections = EnumSet.noneOf(TaskListAgenda.Section.class);
    private final Set<String> checkedIds = new LinkedHashSet<>();
    private final Map<String, VBox> taskRows = new HashMap<>();
    private final Map<String, CheckBox> taskChecks = new HashMap<>();
    private List<Task> tasks = List.of();
    private String selectedTaskId;
    private TextField searchField;
    private ComboBox<String> categoryFilter;
    private CheckBox selectListed;
    private Button deleteSelected;
    private boolean refreshingCategories;

    public TaskManagementPanel(TaskService taskService, CategoryService categoryService,
                               StudyService studyService, HostServices hostServices,
                               Consumer<Node> showModal, Runnable closeModal) {
        this.taskService = taskService;
        this.categoryService = categoryService;
        this.studyService = studyService;
        this.showModal = showModal;
        this.closeModal = closeModal;
        this.hostServices = hostServices;

        getStyleClass().add("panel-bg");
        setTop(new VBox(buildHeader(), buildToolbar()));
        taskList.setPadding(new Insets(12, 16, 20, 20));
        taskDetails.setPadding(new Insets(18));
        taskDetails.getStyleClass().add("task-details");
        ScrollPane listScroll = scrollPane(taskList);
        listScroll.setMinWidth(320);
        detailScroll = scrollPane(taskDetails);
        detailScroll.setMinWidth(300);
        detailScroll.setPrefWidth(440);
        SplitPane split = new SplitPane(listScroll, detailScroll);
        split.getStyleClass().add("transparent-bg");
        split.setDividerPositions(0.58);
        SplitPane.setResizableWithParent(detailScroll, false);
        setCenter(split);
        refreshData();
    }

    // ──────────────────────────────────────────────
    // UI CONSTRUCTION
    // ──────────────────────────────────────────────

    private HBox buildHeader() {
        HBox header = new HBox(15);
        header.setPadding(new Insets(20, 20, 10, 20));
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Task Management");
        TaskStyleUtils.fontBold(title, 24);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button newTask = new Button("+ New Task");
        newTask.getStyleClass().add("btn-primary");
        newTask.setOnAction(e -> showTaskForm(null));
        header.getChildren().addAll(title, spacer, newTask);
        return header;
    }

    private VBox buildToolbar() {
        searchField = new TextField();
        searchField.setPromptText("Search tasks…");
        searchField.setAccessibleText("Search tasks");
        searchField.setPrefWidth(220);
        searchField.textProperty().addListener((obs, before, after) -> rebuildTaskList());
        categoryFilter = new ComboBox<>();
        categoryFilter.setAccessibleText("Category filter");
        categoryFilter.setPrefWidth(160);
        categoryFilter.setOnAction(e -> {
            if (!refreshingCategories) rebuildTaskList();
        });

        selectListed = new CheckBox("Select listed tasks");
        selectListed.setTooltip(new Tooltip("Includes all tasks in matching families, even in collapsed sections."));
        selectListed.setOnAction(e -> {
            checkedIds.clear();
            if (selectListed.isSelected()) checkedIds.addAll(taskChecks.keySet());
            taskChecks.forEach((id, check) -> check.setSelected(checkedIds.contains(id)));
            updateBulkSelection();
        });
        deleteSelected = new Button("Delete selected (0)");
        deleteSelected.getStyleClass().addAll("btn-danger", "btn-small");
        deleteSelected.setOnAction(e -> deleteSelectedTasks());
        FlowPane filters = new FlowPane(10, 8, searchField, categoryFilter);
        FlowPane bulk = new FlowPane(12, 8, selectListed, deleteSelected);
        VBox toolbar = new VBox(8, filters, bulk);
        toolbar.setPadding(new Insets(0, 20, 10, 20));
        return toolbar;
    }

    private ScrollPane scrollPane(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("transparent-bg");
        return scroll;
    }

    // ──────────────────────────────────────────────
    // TASK LIST AND SELECTION
    // ──────────────────────────────────────────────

    private boolean matchesFilters(Task task) {
        String search = searchField.getText().trim().toLowerCase(Locale.ROOT);
        String category = categoryFilter.getValue();
        return (search.isEmpty() || nvl(task.getTitle()).toLowerCase(Locale.ROOT).contains(search)
                || nvl(task.getDescription()).toLowerCase(Locale.ROOT).contains(search))
                && (category == null || "All".equals(category) || category.equalsIgnoreCase(task.getCategory()));
    }

    private void rebuildTaskList() {
        taskList.getChildren().clear();
        taskRows.clear();
        taskChecks.clear();
        Map<TaskListAgenda.Section, List<TaskListAgenda.Family>> sections =
                TaskListAgenda.build(LocalDate.now(), tasks, this::matchesFilters);
        if (sections.values().stream().allMatch(List::isEmpty)) {
            taskList.getChildren().add(detailLabel("No tasks match these filters."));
        }
        sections.forEach((section, families) -> {
            int count = families.stream().mapToInt(family -> 1 + family.children().size()).sum();
            if (count == 0 && !section.collapsed) return;
            String heading = section.title + " · " + count;
            VBox rows = new VBox(6);
            for (TaskListAgenda.Family family : families) {
                rows.getChildren().add(buildTaskRow(family.parent(), false, family.children()));
                for (Task child : family.children()) {
                    rows.getChildren().add(buildTaskRow(child, true, List.of()));
                }
            }
            if (section.collapsed) {
                TitledPane pane = new TitledPane(heading, rows);
                pane.setAnimated(false);
                pane.setExpanded(expandedSections.contains(section));
                pane.expandedProperty().addListener((obs, before, expanded) -> {
                    if (expanded) expandedSections.add(section);
                    else expandedSections.remove(section);
                });
                taskList.getChildren().add(pane);
            } else {
                Label label = new Label(heading);
                TaskStyleUtils.fontBold(label, 15);
                if (section == TaskListAgenda.Section.THIS_WEEK) {
                    label.setTooltip(new Tooltip("Tomorrow through the next 7 days, inclusive."));
                }
                taskList.getChildren().add(new VBox(8, label, rows));
            }
        });
        checkedIds.retainAll(taskChecks.keySet());
        updateBulkSelection();
        updateSelectionHighlight();
    }

    private HBox buildTaskRow(Task task, boolean child, List<Task> children) {
        CheckBox check = new CheckBox();
        check.setAccessibleText("Select " + taskTitle(task) + " for deletion");
        check.setSelected(checkedIds.contains(task.getId()));
        check.setOnAction(e -> {
            if (check.isSelected()) checkedIds.add(task.getId());
            else checkedIds.remove(task.getId());
            updateBulkSelection();
        });
        taskChecks.put(task.getId(), check);

        Label title = new Label(taskTitle(task));
        title.setWrapText(true);
        TaskStyleUtils.fontBold(title, 14);
        Label meta = detailLabel(deadlineText(task)
                + " · " + (task.getPriority() == null ? "No priority" : task.getPriority())
                + " · " + categoryName(task));
        FlowPane badges = new FlowPane(6, 4, statusBadge(task));
        if (task.isReminderDue(LocalDate.now())) {
            badges.getChildren().add(TaskStyleUtils.createReminderBadge(
                    ChronoUnit.DAYS.between(LocalDate.now(), task.getDeadline())));
        }
        // A plain VBox rather than a Button: a Button sizes its graphic without
        // the row's width, so the wrapping labels inflated every row's height.
        VBox row = new VBox(5, title, meta, badges);
        if (!children.isEmpty()) row.getChildren().add(progressLabel(children));
        if (task.isRecurring()) row.getChildren().add(detailLabel(task.getRecurringSummary()));
        row.setFocusTraversable(true);
        row.setAccessibleRole(javafx.scene.AccessibleRole.BUTTON);
        row.setAccessibleText(taskTitle(task) + ", " + task.getStatus().getDisplayName()
                + ", " + deadlineText(task));
        row.setMaxWidth(Double.MAX_VALUE);
        row.setMinWidth(0);
        row.getStyleClass().add("task-list-row");
        row.setStyle("-fx-base: " + TaskStyleUtils.statusBackground(task.getStatus()) + ";");
        row.setOnMouseClicked(e -> {
            row.requestFocus();
            selectTask(task.getId());
        });
        row.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER || e.getCode() == javafx.scene.input.KeyCode.SPACE) {
                selectTask(task.getId());
                e.consume();
            }
        });
        taskRows.put(task.getId(), row);
        HBox.setHgrow(row, Priority.ALWAYS);
        HBox wrapper = new HBox(8, check, row);
        wrapper.setAlignment(Pos.CENTER_LEFT);
        if (child) wrapper.setPadding(new Insets(0, 0, 0, 26));
        return wrapper;
    }

    private void updateBulkSelection() {
        int count = checkedIds.size();
        selectListed.setDisable(taskChecks.isEmpty());
        selectListed.setSelected(count > 0 && count == taskChecks.size());
        selectListed.setIndeterminate(count > 0 && count < taskChecks.size());
        deleteSelected.setText("Delete selected (" + count + ")");
        deleteSelected.setDisable(count == 0);
    }

    private void selectTask(String id) {
        boolean changed = !Objects.equals(selectedTaskId, id);
        selectedTaskId = id;
        updateSelectionHighlight();
        renderSelectedTask();
        if (changed) detailScroll.setVvalue(0);
    }

    private static String deadlineText(Task task) {
        String due = task.getDeadline() == null ? "No deadline" : "Due " + task.getDeadline();
        return task.getStatus() == TaskStatus.POSTPONED ? due + " · " + resumeText(task) : due;
    }

    private static String resumeText(final Task task) {
        return task.getResumeOn() == null ? "Resume manually" : "Resumes " + task.getResumeOn();
    }

    private void updateSelectionHighlight() {
        taskRows.forEach((id, row) -> row.pseudoClassStateChanged(SELECTED, id.equals(selectedTaskId)));
    }

    // ──────────────────────────────────────────────
    // SELECTED TASK DETAILS
    // ──────────────────────────────────────────────

    private void renderSelectedTask() {
        taskDetails.getChildren().clear();
        Task task = tasks.stream().filter(t -> t.getId().equals(selectedTaskId)).findFirst().orElse(null);
        if (task == null) {
            selectedTaskId = null;
            taskDetails.getChildren().add(detailLabel("Select a task to see its details."));
            return;
        }
        try {
            Label title = new Label(taskTitle(task));
            title.setWrapText(true);
            TaskStyleUtils.fontBold(title, 20);
            taskDetails.getChildren().addAll(title, statusBadge(task), buildTaskActions(task));
            if (task.getParentId() != null) {
                tasks.stream().filter(t -> t.getId().equals(task.getParentId())).findFirst().ifPresent(parent -> {
                    Hyperlink link = new Hyperlink("Parent: " + taskTitle(parent));
                    link.setWrapText(true);
                    link.setOnAction(e -> selectTask(parent.getId()));
                    taskDetails.getChildren().add(link);
                });
            }
            taskDetails.getChildren().addAll(
                    detailLabel("Due date: " + (task.getDeadline() == null ? "No deadline" : task.getDeadline())),
                    detailLabel("Priority: " + (task.getPriority() == null ? "None" : task.getPriority())),
                    detailLabel("Category: " + categoryName(task)),
                    detailLabel("Reminder: " + task.reminderDate().map(date -> date + " ("
                            + task.getRemindDaysBefore() + " days before)").orElse("None")));
            if (task.getStatus() == TaskStatus.POSTPONED) {
                taskDetails.getChildren().add(detailLabel(resumeText(task)));
            }
            if (task.isRecurring()) {
                taskDetails.getChildren().add(detailLabel(task.getRecurringSummary()
                        + " · From " + task.getRecurrenceAnchor()
                        + (task.getRecurrenceEndDate() == null ? " · No end date"
                                : " · Until " + task.getRecurrenceEndDate())));
            }
            if (!nvl(task.getDescription()).isBlank()) taskDetails.getChildren().add(detailLabel(task.getDescription()));
            taskDetails.getChildren().add(detailLabel("Done when: "
                    + (nvl(task.getDoneCriteria()).isBlank() ? "Not specified" : task.getDoneCriteria())));
            TaskService.TaskEstimates estimates = taskService.getTaskEstimates(task);
            taskDetails.getChildren().addAll(
                    detailLabel("Own estimate: " + (estimates.plannedHours() == null ? "Not set" : estimates.plannedHours() + " h")),
                    detailLabel("Subtasks' total: " + (estimates.subtaskHours() == null ? "Not set" : estimates.subtaskHours() + " h")));
            if (task.getParentId() == null) taskDetails.getChildren().add(buildSubtasks(task));
            VBox goals = new VBox(8);
            populateGoalHistory(goals, task);
            taskDetails.getChildren().addAll(new Separator(), goals, new Separator(), detailHeading("Notes"));
            if (nvl(task.getNotes()).isBlank()) {
                taskDetails.getChildren().add(detailLabel("No notes yet."));
            } else {
                WebView notes = new WebView();
                notes.setMinWidth(0);
                notes.setMinHeight(160);
                notes.setPrefHeight(260);
                notes.getEngine().loadContent(Markdown.page(Markdown.toHtml(task.getNotes())));
                taskDetails.getChildren().add(notes);
            }
            taskDetails.getChildren().add(detailHeading("Links"));
            List<TaskLink> links = taskService.getTaskLinks(task);
            if (links.isEmpty()) taskDetails.getChildren().add(detailLabel("No links yet."));
            for (TaskLink link : links) {
                Hyperlink open = new Hyperlink(nvl(link.title()).isBlank() ? link.url() : link.title());
                open.setWrapText(true);
                open.setMaxWidth(Double.MAX_VALUE);
                open.setTooltip(new Tooltip(link.url()));
                open.setOnAction(e -> {
                    try {
                        hostServices.showDocument(TaskService.validateLinkUrl(link.url()));
                    } catch (Exception ex) {
                        showTaskError(ex);
                    }
                });
                taskDetails.getChildren().add(open);
            }
        } catch (Exception ex) {
            Label error = detailLabel("Could not load task details: " + ex.getMessage());
            TaskStyleUtils.textColor(error, TaskStyleUtils.COLOR_DANGER);
            Button retry = new Button("Retry");
            retry.setOnAction(e -> refreshData());
            taskDetails.getChildren().addAll(error, retry);
        }
    }

    private FlowPane buildTaskActions(Task task) {
        Button edit = new Button("Edit");
        edit.getStyleClass().addAll("btn-primary", "btn-small");
        edit.setOnAction(e -> showTaskForm(task));
        FlowPane actions = new FlowPane(6, 6, edit);
        if (task.getStatus() != TaskStatus.COMPLETED && task.getStatus() != TaskStatus.CANCELLED) {
            Button done = new Button("Done");
            done.getStyleClass().addAll("btn-success", "btn-small");
            done.setOnAction(e -> {
                try {
                    if (!confirmSubtaskCompletion(task)) return;
                    taskService.updateTaskStatus(task, TaskStatus.COMPLETED);
                    refreshData();
                } catch (Exception ex) {
                    showTaskError(ex);
                }
            });
            actions.getChildren().add(done);
        }
        if (task.getStatus() == TaskStatus.DELAYED || task.getStatus() == TaskStatus.POSTPONED) {
            Button reschedule = new Button(task.getStatus() == TaskStatus.POSTPONED
                    ? "Set resume date" : "Reschedule");
            reschedule.getStyleClass().add("btn-small");
            reschedule.setOnAction(e -> showRescheduleDialog(task));
            actions.getChildren().add(reschedule);
        }
        Button delete = new Button("Delete");
        delete.getStyleClass().addAll("btn-danger", "btn-small");
        delete.setOnAction(e -> deleteTask(task));
        actions.getChildren().add(delete);
        return actions;
    }

    private VBox buildSubtasks(Task task) {
        List<Task> children = tasks.stream().filter(t -> task.getId().equals(t.getParentId())).toList();
        VBox box = new VBox(6, detailHeading("Subtasks"), progressLabel(children));
        ProgressBar progress = new ProgressBar(Task.progress(children).percentage() / 100.0);
        progress.setMaxWidth(Double.MAX_VALUE);
        box.getChildren().add(progress);
        for (Task child : children) {
            Hyperlink link = new Hyperlink(taskTitle(child) + " · " + child.getStatus().getDisplayName());
            link.setWrapText(true);
            link.setOnAction(e -> selectTask(child.getId()));
            box.getChildren().add(link);
        }
        Button add = new Button("Add subtask");
        add.getStyleClass().add("btn-small");
        add.setOnAction(e -> showTaskForm(null, task));
        box.getChildren().add(add);
        return box;
    }

    private Label progressLabel(List<Task> children) {
        Task.Progress progress = Task.progress(children);
        Label label = detailLabel(progress.completed() + "/" + count(progress.total(), "subtask")
                + " · " + progress.percentage() + "%");
        label.setTooltip(new Tooltip("Cancelled subtasks are excluded."));
        return label;
    }

    private Label statusBadge(Task task) {
        Label badge = TaskStyleUtils.createAttemptBadge(task.getStatus().getDisplayName(),
                TaskStyleUtils.statusTextColor(task.getStatus()), TaskStyleUtils.statusBadgeBg(task.getStatus()));
        badge.setGraphic(TaskStyleUtils.iconLabel(TaskStyleUtils.statusEmoji(task.getStatus()), 11));
        return badge;
    }

    private Label detailLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        TaskStyleUtils.fontNormal(label, 12);
        return label;
    }

    private Label detailHeading(String text) {
        Label label = new Label(text);
        TaskStyleUtils.fontBold(label, 14);
        return label;
    }

    private String taskTitle(Task task) {
        return nvl(task.getTitle()).isBlank() ? "(Untitled task)" : task.getTitle().trim();
    }

    private String categoryName(Task task) {
        return nvl(task.getCategory()).isBlank() ? "Uncategorized" : task.getCategory();
    }

    private void populateGoalHistory(VBox pane, Task task) {
        pane.getChildren().clear();

        HBox toolbar = new HBox(8);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label heading = new Label("Goal history");
        TaskStyleUtils.fontBold(heading, 12);
        TaskStyleUtils.textColor(heading, TaskStyleUtils.COLOR_PRIMARY);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addGoalBtn = new Button("+ Plan Goal");
        addGoalBtn.getStyleClass().addAll("btn-purple", "btn-small");
        addGoalBtn.setOnAction(e -> showTaskGoalForm(task, null, pane));
        toolbar.getChildren().addAll(heading, spacer, addGoalBtn);
        pane.getChildren().add(toolbar);

        List<StudyGoal> attempts = StudyGoal.findByTaskId(task.getId());

        if (attempts.isEmpty()) {
            Label empty = new Label("No attempts have been planned for this task yet.");
            empty.setWrapText(true);
            TaskStyleUtils.fontNormal(empty, 12);
            TaskStyleUtils.textColor(empty, "#7f8c8d");
            pane.getChildren().add(empty);
            return;
        }

        Map<String, List<StudyGoal>> attemptsByGoal = attempts.stream()
                .collect(Collectors.groupingBy(StudyGoal::getId, LinkedHashMap::new, Collectors.toList()));
        attemptsByGoal.values().forEach(goalAttempts ->
                goalAttempts.sort(Comparator.comparingInt(StudyGoal::getAttemptNumber)));

        long achieved = attempts.stream().filter(StudyGoal::isAchieved).count();
        long missed = attempts.stream()
                .filter(goal -> goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.MISSED)
                .count();
        long active = attempts.stream()
                .filter(goal -> goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.PENDING
                        && goal.getStatus() == StudyGoal.GoalStatus.ACTIVE)
                .count();
        int score = (int) (achieved - missed);

        FlowPane stats = new FlowPane(6, 4);
        stats.setPadding(new Insets(0, 0, 4, 0));
        stats.getChildren().addAll(
                createStatChip("Goals " + attemptsByGoal.size(), TaskStyleUtils.COLOR_PRIMARY),
                createStatChip("Attempts " + attempts.size(), TaskStyleUtils.COLOR_PRIMARY),
                createStatChip("Done " + achieved, TaskStyleUtils.COLOR_SUCCESS),
                createStatChip("Missed " + missed, TaskStyleUtils.COLOR_DANGER),
                createStatChip("Active " + active, TaskStyleUtils.COLOR_PRIMARY),
                createStatChip("Lifetime net " + String.format("%+d", score), TaskStyleUtils.COLOR_PURPLE)
        );
        pane.getChildren().add(stats);

        for (List<StudyGoal> goalAttempts : attemptsByGoal.values()) {
            pane.getChildren().add(createGoalAttemptTimeline(task, goalAttempts, pane));
        }
    }

    private Label createStatChip(String text, String textColor) {
        return TaskStyleUtils.createAttemptBadge(text, textColor, TaskStyleUtils.TINT_NEUTRAL);
    }

    private VBox createGoalAttemptTimeline(Task task, List<StudyGoal> attempts, VBox ownerPane) {
        StudyGoal latest = attempts.get(attempts.size() - 1);

        VBox card = new VBox(8);
        card.setPadding(new Insets(10));
        card.getStyleClass().add("task-goal-card");

        Label title = new Label(shorten(latest.getDescription(), 90)
                + TaskStyleUtils.criteriaProgress(latest.getCriteria()));
        TaskStyleUtils.fontSemiBold(title, 12);
        title.setWrapText(true);

        Label parentBadge = new Label(parentStatusLabel(latest));
        parentBadge.setStyle("-fx-background-color: " + parentStatusBackground(latest)
                + "; -fx-background-radius: 8; -fx-padding: 2 8;");
        TaskStyleUtils.fontBold(parentBadge, 10);
        TaskStyleUtils.textColor(parentBadge, parentStatusColor(latest));

        FlowPane actions = new FlowPane(6, 6);

        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().addAll("btn-primary", "btn-small");
        editBtn.setOnAction(e -> showTaskGoalForm(task, latest, ownerPane));
        actions.getChildren().add(editBtn);

        if (canPlanAnotherAttempt(latest)) {
            Button planBtn = new Button("Plan");
            planBtn.getStyleClass().addAll("btn-orange", "btn-small");
            planBtn.setOnAction(e -> showPlanGoalAttemptForm(task, latest, ownerPane));
            actions.getChildren().add(planBtn);
        }

        actions.getChildren().add(GoalActions.menu(latest, studyService, LocalDate.now(),
                () -> populateGoalHistory(ownerPane, task)));

        VBox header = new VBox(6, title, parentBadge, actions);
        if (latest.isDropped() && latest.getDropReason() != null) {
            Label reason = new Label("Reason: " + latest.getDropReason());
            reason.setWrapText(true);
            header.getChildren().add(reason);
        }

        Label summary = new Label("Latest attempt " + latest.getAttemptNumber()
                + " | " + attempts.size() + " total attempt" + (attempts.size() == 1 ? "" : "s"));
        TaskStyleUtils.fontNormal(summary, 11);
        TaskStyleUtils.textColor(summary, "#6c757d");
        summary.setWrapText(true);

        VBox timeline = new VBox(4);
        for (StudyGoal attempt : attempts) {
            timeline.getChildren().add(createAttemptTimelineRow(attempt));
        }
        timeline.setVisible(false);
        timeline.setManaged(false);

        Button toggleAttemptsBtn = new Button("Show attempts");
        toggleAttemptsBtn.getStyleClass().addAll("btn-gray", "btn-small");
        toggleAttemptsBtn.setOnAction(e -> {
            boolean show = !timeline.isManaged();
            timeline.setVisible(show);
            timeline.setManaged(show);
            toggleAttemptsBtn.setText(show ? "Hide attempts" : "Show attempts");
        });

        VBox summaryRow = new VBox(6, summary, toggleAttemptsBtn);

        card.getChildren().addAll(header, summaryRow, timeline);
        return card;
    }

    private boolean canPlanAnotherAttempt(StudyGoal goal) {
        return goal.getStatus() == StudyGoal.GoalStatus.ACTIVE
                && goal.getAttemptOutcome() != StudyGoal.AttemptOutcome.PENDING
                && !goal.isAchieved();
    }

    private FlowPane createAttemptTimelineRow(StudyGoal attempt) {
        FlowPane row = new FlowPane(8, 4);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 6, 4, 6));
        row.setStyle("-fx-background-color: " + TaskStyleUtils.TINT_NEUTRAL + "; -fx-background-radius: 4;"
                + " -fx-border-color: transparent transparent transparent " + attemptStatusColor(attempt) + ";"
                + " -fx-border-width: 0 0 0 4; -fx-border-radius: 4;");

        Label marker = TaskStyleUtils.iconLabel(attemptIcon(attempt), 11);
        TaskStyleUtils.textColor(marker, attemptStatusColor(attempt));

        Label attemptLabel = new Label("Attempt " + attempt.getAttemptNumber());
        TaskStyleUtils.fontSemiBold(attemptLabel, 11);
        attemptLabel.setMinWidth(72);
        TaskStyleUtils.textColor(attemptLabel, "#495057");

        Label dateLabel = new Label(attempt.getDate().toString());
        TaskStyleUtils.fontNormal(dateLabel, 11);
        dateLabel.setMinWidth(82);
        TaskStyleUtils.textColor(dateLabel, "#6c757d");

        Label status = new Label(attemptStatusLabel(attempt));
        status.setStyle("-fx-background-color: " + attemptStatusBackground(attempt)
                + "; -fx-background-radius: 8; -fx-padding: 1 7;");
        TaskStyleUtils.fontBold(status, 10);
        TaskStyleUtils.textColor(status, attemptStatusColor(attempt));

        row.getChildren().addAll(marker, attemptLabel, dateLabel, status);
        return row;
    }

    private String parentStatusLabel(StudyGoal goal) {
        if (goal.getStatus() == StudyGoal.GoalStatus.ACHIEVED) {
            return "Achieved";
        }
        if (goal.getStatus() == StudyGoal.GoalStatus.ABANDONED) {
            return "Dropped";
        }
        return goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.PENDING ? "Active" : "Needs retry";
    }

    private String parentStatusColor(StudyGoal goal) {
        if (goal.getStatus() == StudyGoal.GoalStatus.ACHIEVED) {
            return TaskStyleUtils.COLOR_SUCCESS;
        }
        if (goal.isDropped()) {
            return TaskStyleUtils.COLOR_MUTED;
        }
        return goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.PENDING
                ? TaskStyleUtils.COLOR_PRIMARY : TaskStyleUtils.retryTextColor();
    }

    private String parentStatusBackground(StudyGoal goal) {
        if (goal.getStatus() == StudyGoal.GoalStatus.ACHIEVED) {
            return TaskStyleUtils.TINT_SUCCESS;
        }
        if (goal.isDropped()) {
            return TaskStyleUtils.TINT_NEUTRAL;
        }
        return goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.PENDING
                ? TaskStyleUtils.TINT_PRIMARY : TaskStyleUtils.retryBackgroundColor();
    }

    private String attemptIcon(StudyGoal attempt) {
        return switch (attempt.getAttemptOutcome()) {
            case ACHIEVED -> "\u2713";
            case MISSED -> "\u2715";
            case PENDING -> "\u25CB";
            case DROPPED -> "−";
        };
    }

    private String attemptStatusLabel(StudyGoal attempt) {
        if (attempt.getStatus() == StudyGoal.GoalStatus.ABANDONED
                && attempt.getAttemptOutcome() == StudyGoal.AttemptOutcome.PENDING) {
            return "Dropped";
        }
        return switch (attempt.getAttemptOutcome()) {
            case ACHIEVED -> "Achieved";
            case MISSED -> "Missed";
            case PENDING -> "Pending";
            case DROPPED -> "Dropped";
        };
    }

    private String attemptStatusColor(StudyGoal attempt) {
        return TaskStyleUtils.attemptTextColor(attempt);
    }

    private String attemptStatusBackground(StudyGoal attempt) {
        return TaskStyleUtils.attemptBackgroundColor(attempt);
    }

    private String shorten(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLength ? text.substring(0, maxLength - 3) + "..." : text;
    }

    private void showTaskGoalForm(Task task, StudyGoal existingGoal, VBox ownerPane) {
        boolean isNew = existingGoal == null;

        VBox form = new VBox(12);
        form.setPadding(new Insets(24));
        form.getStyleClass().add("modal-content-light");
        form.setMaxWidth(500);
        form.setMaxHeight(Region.USE_PREF_SIZE);

        Label title = new Label(isNew ? "Plan Goal for Task" : "Edit Goal");
        TaskStyleUtils.fontBold(title, 18);

        Label taskLabel = new Label(task.getTitle());
        TaskStyleUtils.fontSemiBold(taskLabel, 12);
        TaskStyleUtils.textColor(taskLabel, "#495057");
        taskLabel.setWrapText(true);

        GoalEditFields fields = new GoalEditFields(existingGoal, LocalDate.now());

        Button saveBtn = new Button(isNew ? "Plan Goal" : "Save Goal");
        saveBtn.getStyleClass().add("btn-primary");
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-cancel");
        cancelBtn.setOnAction(e -> closeModal.run());

        saveBtn.setOnAction(e -> {
            try {
                if (isNew) {
                    fields.create(studyService, task.getId());
                } else {
                    fields.save(studyService, existingGoal);
                }
                closeModal.run();
                if (isNew) {
                    refreshData(); // planning a goal moves an open task to In progress
                } else {
                    populateGoalHistory(ownerPane, task);
                }
            } catch (Exception ex) {
                fields.showError(ex.getMessage());
            }
        });

        HBox buttons = new HBox(10, saveBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        form.getChildren().addAll(title, new Label("Task:"), taskLabel, fields.view(), buttons);

        showModal.accept(wrapGoalModal(form));
    }

    private void showPlanGoalAttemptForm(Task task, StudyGoal goal, VBox ownerPane) {
        VBox form = new VBox(12);
        form.setPadding(new Insets(24));
        form.getStyleClass().add("modal-content-light");
        form.setMaxWidth(460);
        form.setMaxHeight(Region.USE_PREF_SIZE);

        Label title = new Label("Plan Goal");
        TaskStyleUtils.fontBold(title, 18);

        Label description = new Label(goal.getDescription());
        TaskStyleUtils.fontNormal(description, 13);
        description.setWrapText(true);

        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.setMaxWidth(Double.MAX_VALUE);
        GoalActions.ChecklistChoice checklist = GoalActions.ChecklistChoice.forGoal(goal, datePicker, LocalDate.now());

        Button planBtn = new Button("Plan Goal");
        planBtn.getStyleClass().add("btn-orange");
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-cancel");
        cancelBtn.setOnAction(e -> closeModal.run());

        planBtn.setOnAction(e -> {
            LocalDate plannedDate = datePicker.getValue();
            if (plannedDate == null) {
                showInlineError(form, "Planned date is required.");
                return;
            }
            try {
                boolean created = studyService.planGoalAttempt(goal.getId(), plannedDate,
                        checklist == null ? null : checklist.value());
                if (!created) {
                    showInlineError(form, "This goal already has a pending attempt or cannot be replanned.");
                    return;
                }
                closeModal.run();
                populateGoalHistory(ownerPane, task);
            } catch (Exception ex) {
                showInlineError(form, ex.getMessage());
            }
        });

        HBox buttons = new HBox(10, planBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        form.getChildren().addAll(title, new Label("Goal:"), description, new Label("Date:"), datePicker);
        if (checklist != null) {
            form.getChildren().add(checklist.view);
        }
        form.getChildren().add(buttons);
        showModal.accept(wrapGoalModal(form));
    }

    private ScrollPane wrapGoalModal(VBox form) {
        ScrollPane wrapper = new ScrollPane(form);
        wrapper.setFitToWidth(true);
        wrapper.getStyleClass().add("transparent-bg");
        wrapper.setMaxWidth(520);
        wrapper.setMaxHeight(560);
        return wrapper;
    }

    // ──────────────────────────────────────────────
    // TASK FORM (modal overlay)
    // ──────────────────────────────────────────────

    /** "1 subtask", "3 subtasks". */
    private static String count(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    private boolean confirmSubtaskCompletion(Task task) {
        TaskService.CompletionPreview preview = taskService.previewTaskCompletion(task);
        if (preview.unfinishedSubtasks() == 0) return true;
        String message = "This also completes " + count(preview.unfinishedSubtasks(), "unfinished subtask");
        if (preview.recurringSubtasks() > 0) {
            message += "; " + preview.recurringSubtasks() + " of them "
                    + (preview.recurringSubtasks() == 1 ? "is" : "are") + " recurring and will stop repeating";
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                message + ".", ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle("Complete Task");
        confirm.setHeaderText("Complete \"" + task.getTitle() + "\"?");
        confirm.initOwner(getScene() != null ? getScene().getWindow() : null);
        return confirm.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void deleteTask(Task task) {
        try {
            int childCount = Task.findSubtasks(task.getId()).size();
            String message = "Delete \"" + taskTitle(task) + "\"?";
            if (childCount == 1) message += "\nThis also deletes its subtask, even if it is cancelled.";
            if (childCount > 1) message += "\nThis also deletes all " + childCount + " subtasks, including cancelled ones.";
            message += "\nNotes, links and reschedules are deleted. Goals and study sessions are kept without their task links.";
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
            confirm.setTitle("Delete Task");
            confirm.setHeaderText(null);
            confirm.initOwner(getScene() != null ? getScene().getWindow() : null);
            if (confirm.showAndWait().filter(ButtonType.OK::equals).isPresent()) {
                taskService.removeTask(task, childCount);
                refreshData();
            }
        } catch (Exception ex) {
            showTaskError(ex);
        }
    }

    private void deleteSelectedTasks() {
        try {
            List<String> ids = List.copyOf(checkedIds);
            TaskService.DeletionPreview preview = taskService.previewTaskDeletion(ids);
            if (preview.totalTasks() == 0) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete " + count(preview.totalTasks(), "task") + " in total?"
                            + (preview.subtasks() == 0 ? "" : "\nThis deletes " + count(preview.subtasks(), "subtask")
                                    + " with their parents, including children that are not selected.")
                            + "\nNotes, links and reschedules are deleted. Goals and study sessions are kept without their task links.",
                    ButtonType.OK, ButtonType.CANCEL);
            confirm.setTitle("Delete Selected Tasks");
            confirm.setHeaderText(null);
            confirm.initOwner(getScene() != null ? getScene().getWindow() : null);
            if (confirm.showAndWait().filter(ButtonType.OK::equals).isPresent()) {
                taskService.batchDeleteTasks(ids, preview.subtasks());
                refreshData();
            }
        } catch (Exception ex) {
            showTaskError(ex);
        }
    }

    private void showTaskError(Exception ex) {
        Alert error = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
        error.initOwner(getScene() != null ? getScene().getWindow() : null);
        error.showAndWait();
    }

    private void showTaskForm(Task existingTask) {
        showTaskForm(existingTask, null);
    }

    private void showTaskForm(Task existingTask, Task selectedParent) {
        boolean isNew = existingTask == null;

        VBox form = new VBox(12);
        form.setPadding(new Insets(24));
        form.getStyleClass().add("modal-content-light");
        form.setMaxWidth(520);
        form.setMaxHeight(Region.USE_PREF_SIZE);

        Label formTitle = new Label(isNew ? selectedParent == null ? "+ New Task" : "+ Add subtask" : "Edit Task");
        TaskStyleUtils.fontBold(formTitle, 18);

        // Title
        TextField titleField = new TextField(isNew ? "" : existingTask.getTitle());
        titleField.setPromptText("Task title *");

        // Description
        TextArea descArea = new TextArea(isNew ? "" : nvl(existingTask.getDescription()));
        descArea.setPromptText("Description (optional)");
        descArea.setPrefRowCount(3);
        descArea.setWrapText(true);

        TextArea doneArea = new TextArea(isNew ? "" : nvl(existingTask.getDoneCriteria()));
        doneArea.setPromptText("What done means (optional)");
        doneArea.setPrefRowCount(2);
        doneArea.setWrapText(true);

        TextField estimateField = new TextField(isNew || existingTask.getEstimatedHours() == null
                ? "" : existingTask.getEstimatedHours().toString());
        estimateField.setPromptText("Estimated whole hours (optional)");

        ComboBox<Task> parentCombo = new ComboBox<>();
        parentCombo.setMaxWidth(Double.MAX_VALUE);
        parentCombo.getItems().add(null);
        parentCombo.getItems().addAll(taskService.getTasks().stream()
                .filter(t -> t.getParentId() == null)
                .filter(t -> isNew || !t.getId().equals(existingTask.getId())).toList());
        parentCombo.setCellFactory(lv -> parentCell());
        parentCombo.setButtonCell(parentCell());
        parentCombo.setValue(selectedParent != null ? selectedParent : isNew ? null
                : parentCombo.getItems().stream().filter(Objects::nonNull)
                    .filter(t -> t.getId().equals(existingTask.getParentId())).findFirst().orElse(null));
        Label hierarchyHint = new Label("One level only. Completing a parent also completes unfinished subtasks. "
                + "Recurring parents never auto-complete; completing recurring tasks ends all future occurrences. "
                + "Adding or reopening unfinished work reopens a completed parent.");
        hierarchyHint.setWrapText(true);
        TaskStyleUtils.fontNormal(hierarchyHint, 11);
        TaskStyleUtils.textColor(hierarchyHint, TaskStyleUtils.COLOR_MUTED);

        TextArea notesArea = new TextArea(isNew ? "" : nvl(existingTask.getNotes()));
        notesArea.setPromptText("Markdown notes: headings, lists, tables, **bold**…");
        notesArea.setPrefRowCount(6);
        notesArea.setWrapText(true);
        TitledPane preview = new TitledPane("Read notes", null);
        preview.setExpanded(false);
        WebView notesView = new WebView();
        notesView.setPrefHeight(240);
        Runnable renderNotes = () -> notesView.getEngine().loadContent(Markdown.page(Markdown.toHtml(notesArea.getText())));
        preview.setContent(notesView);
        preview.expandedProperty().addListener((obs, before, expanded) -> { if (expanded) renderNotes.run(); });
        notesArea.textProperty().addListener((obs, before, text) -> { if (preview.isExpanded()) renderNotes.run(); });
        TaskLinksEditor linksEditor = new TaskLinksEditor(isNew ? List.of() : taskService.getTaskLinks(existingTask),
                url -> hostServices.showDocument(TaskService.validateLinkUrl(url)), this::showTaskError);

        // Category
        ComboBox<TaskCategory> catCombo = new ComboBox<>();
        catCombo.setPromptText("Category *");
        catCombo.setMaxWidth(Double.MAX_VALUE);
        catCombo.getItems().addAll(categoryService.getCategories());
        if (!isNew) {
            catCombo.getItems().stream()
                .filter(c -> c.name().equals(existingTask.getCategory()))
                .findFirst().ifPresent(catCombo::setValue);
        }

        // New category inline
        TextField newCatField = new TextField();
        newCatField.setPromptText("Or type new category name…");
        Button addCatBtn = new Button("+ Add");
        addCatBtn.getStyleClass().addAll("btn-primary", "btn-small");
        addCatBtn.setOnAction(e -> {
            String name = newCatField.getText().trim();
            if (name.isEmpty()) return;
            if (categoryService.getCategories().stream().noneMatch(c -> c.name().equalsIgnoreCase(name))) {
                categoryService.addCategory(name);
            }
            catCombo.getItems().setAll(categoryService.getCategories());
            catCombo.getItems().stream().filter(c -> c.name().equalsIgnoreCase(name))
                    .findFirst().ifPresent(catCombo::setValue);
            newCatField.clear();
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
        if (!isNew && existingTask.getPriority() != null) {
            priorityCombo.setValue(existingTask.getPriority().stars());
        }

        // Deadline
        DatePicker deadlinePicker = new DatePicker(isNew ? null : existingTask.getDeadline());
        deadlinePicker.setPromptText("Deadline (optional)");
        deadlinePicker.setMaxWidth(Double.MAX_VALUE);

        ComboBox<Integer> reminderCombo = new ComboBox<>();
        reminderCombo.setMaxWidth(Double.MAX_VALUE);
        reminderCombo.getItems().addAll(null, 1, 3, 7, 30);
        reminderCombo.setCellFactory(lv -> reminderCell());
        reminderCombo.setButtonCell(reminderCell());
        reminderCombo.setValue(isNew ? null : existingTask.getRemindDaysBefore());
        // Nothing to count back from without a deadline.
        reminderCombo.disableProperty().bind(deadlinePicker.valueProperty().isNull());

        // Status (only when editing)
        ComboBox<TaskStatus> statusCombo = new ComboBox<>();
        statusCombo.setMaxWidth(Double.MAX_VALUE);
        statusCombo.getItems().addAll(TaskStatus.values());
        if (!isNew) {
            statusCombo.setValue(existingTask.getStatus());
        }

        // Recurring
        CheckBox recurringCheck = new CheckBox("Recurring task");
        TaskStyleUtils.fontBold(recurringCheck, 12);

        VBox recurringOptions = new VBox(8);
        recurringOptions.setPadding(new Insets(8));
        recurringOptions.setStyle("-fx-background-color: #f0f8ff; -fx-background-radius: 5;" +
                                  " -fx-border-color: #b0d4f1; -fx-border-radius: 5;");
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

        DatePicker startDatePicker = new DatePicker(isNew ? LocalDate.now() : null);
        startDatePicker.setPromptText("Start date");
        startDatePicker.setMaxWidth(Double.MAX_VALUE);

        DatePicker recurrenceEndPicker = new DatePicker();
        recurrenceEndPicker.setPromptText("Never ends");
        recurrenceEndPicker.setMaxWidth(Double.MAX_VALUE);

        Label recurrenceEndHint = new Label("The last day this task repeats. Separate from the deadline, "
                + "which is when the task itself is due.");
        TaskStyleUtils.fontNormal(recurrenceEndHint, 10);
        TaskStyleUtils.textColor(recurrenceEndHint, "#7f8c8d");
        recurrenceEndHint.setWrapText(true);

        recurringOptions.getChildren().addAll(new Label("Repeat every:"), intervalRow,
                new Label("On days:"), daysRow,
                new Label("Start date:"), startDatePicker,
                new Label("Repeat until:"), recurrenceEndPicker, recurrenceEndHint);

        Label deadlineHint = new Label("");
        TaskStyleUtils.fontNormal(deadlineHint, 10);
        TaskStyleUtils.textColor(deadlineHint, "#7f8c8d");
        deadlineHint.setWrapText(true);
        deadlineHint.setVisible(false);
        deadlineHint.setManaged(false);

        Runnable updateDeadlineHint = () -> {
            String text = statusCombo.getValue() == TaskStatus.POSTPONED
                    ? "The deadline stays unchanged when you postpone work. It stays paused until you change "
                            + "its status or use Set resume date in the task's actions."
                    : recurringCheck.isSelected()
                        ? "The deadline is when this task is due. Use \"Repeat until\" below to stop the repetition."
                        : "";
            deadlineHint.setText(text);
            deadlineHint.setVisible(!text.isEmpty());
            deadlineHint.setManaged(!text.isEmpty());
        };
        recurringCheck.selectedProperty().addListener((obs, o, n) -> {
            recurringOptions.setVisible(n);
            recurringOptions.setManaged(n);
            updateDeadlineHint.run();
        });
        statusCombo.valueProperty().addListener((obs, o, n) -> updateDeadlineHint.run());
        updateDeadlineHint.run();

        // Pre-fill recurring pattern if editing
        if (!isNew && existingTask.isRecurring()) {
            recurringCheck.setSelected(true);
            try {
                String[] parts = existingTask.getRecurringPattern().split(":");
                intervalSpinner.getValueFactory().setValue(Integer.parseInt(parts[0]));
                Set<Integer> activeDays = Arrays.stream(parts[1].split(","))
                        .map(String::trim).map(Integer::parseInt).collect(Collectors.toSet());
                for (int i = 0; i < 7; i++) {
                    dayBoxes[i].setSelected(activeDays.contains(i + 1));
                }
            } catch (Exception ignored) { }
            if (existingTask.getStartDate() != null) {
                startDatePicker.setValue(existingTask.getStartDate());
            }
            if (existingTask.getRecurrenceEndDate() != null) {
                recurrenceEndPicker.setValue(existingTask.getRecurrenceEndDate());
            }
        }

        // Buttons
        Button saveBtn = new Button(isNew ? "Create Task" : "Save Changes");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setStyle("-fx-padding: 8 18;");
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-cancel");
        cancelBtn.setStyle("-fx-padding: 8 18;");
        cancelBtn.setOnAction(e -> closeModal.run());

        saveBtn.setOnAction(e -> {
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
                Integer estimate = estimateField.getText().isBlank() ? null : Integer.valueOf(estimateField.getText().trim());
                if (estimate != null && estimate < 0) {
                    showInlineError(form, "Estimated hours must be a whole number, zero or greater.");
                    return;
                }
                String parentId = parentCombo.getValue() == null ? "" : parentCombo.getValue().getId();
                LocalDate startDate = recurringCheck.isSelected() ? startDatePicker.getValue() : null;
                LocalDate recurrenceEnd = recurringCheck.isSelected() ? recurrenceEndPicker.getValue() : null;
                if (isNew) {
                    Task newTask = new Task(null, title, descArea.getText().trim(),
                            cat.name(), new TaskPriority(prio),
                            deadlinePicker.getValue(), TaskStatus.OPEN, 0, recurPattern, startDate, recurrenceEnd);
                    newTask.setRemindDaysBefore(deadlinePicker.getValue() != null ? reminderCombo.getValue() : null);
                    newTask.setDoneCriteria(doneArea.getText());
                    newTask.setParentId(parentId);
                    newTask.setEstimatedHours(estimate);
                    newTask.setNotes(notesArea.getText());
                    taskService.addTask(newTask, linksEditor.links());
                } else {
                    TaskUpdate update = new TaskUpdate(title, descArea.getText().trim(),
                            cat.name(), new TaskPriority(prio),
                            deadlinePicker.getValue(), recurPattern, startDate, recurrenceEnd,
                            reminderSelection(deadlinePicker.getValue(), reminderCombo.getValue()),
                            doneArea.getText().trim(), parentId,
                            estimate == null ? TaskUpdate.CLEAR_ESTIMATE : estimate, notesArea.getText(),
                            linksEditor.links(), statusCombo.getValue() == existingTask.getStatus()
                                    ? null : statusCombo.getValue());
                    if (update.status() == TaskStatus.COMPLETED && !confirmSubtaskCompletion(existingTask)) return;
                    taskService.updateTask(existingTask, update);
                }
                closeModal.run();
                refreshData();
            } catch (Exception ex) {
                showInlineError(form, ex instanceof NumberFormatException
                        ? "Estimated hours must be a whole number, zero or greater." : ex.getMessage());
            }
        });

        HBox btnRow = new HBox(10, saveBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(8, 0, 0, 0));

        form.getChildren().addAll(formTitle,
                new Label("Title:"), titleField,
                new Label("Description:"), descArea,
                new Label("Done when:"), doneArea,
                new Label("Parent task:"), parentCombo, hierarchyHint,
                new Label("Estimated hours:"), estimateField,
                new Label("Notes (Markdown):"), notesArea, preview,
                new Label("Links (in display order):"), linksEditor,
                new Label("Category:"), catCombo, newCatRow,
                new Label("Priority:"), priorityCombo,
                new Label("Deadline:"), deadlinePicker, deadlineHint,
                new Label("Remind me:"), reminderCombo);

        if (!isNew) {
            form.getChildren().addAll(new Label("Status:"), statusCombo);

            List<TaskReschedule> history = TaskReschedule.findByTaskId(existingTask.getId());
            if (!history.isEmpty()) {
                VBox historyBox = new VBox(3);
                Label historyTitle = new Label("Reschedule history:");
                TaskStyleUtils.fontBold(historyTitle, 12);
                historyBox.getChildren().add(historyTitle);
                for (TaskReschedule r : history) {
                    String from = r.getOldDeadline() != null ? r.getOldDeadline().toString() : "no deadline";
                    String when = r.getRescheduledAt() != null
                            ? " (changed " + r.getRescheduledAt().toLocalDate() + ")" : "";
                    Label row = new Label(from + " -> " + r.getNewDeadline() + when);
                    TaskStyleUtils.fontNormal(row, 11);
                    TaskStyleUtils.textColor(row, "#7f8c8d");
                    historyBox.getChildren().add(row);
                }
                form.getChildren().add(historyBox);
            }
        }

        form.getChildren().addAll(recurringCheck, recurringOptions, btnRow);

        ScrollPane wrapper = new ScrollPane(form);
        wrapper.setFitToWidth(true);
        wrapper.getStyleClass().add("transparent-bg");
        wrapper.setMaxWidth(540);
        wrapper.setMaxHeight(680);
        showModal.accept(wrapper);
    }

    /** Shows the error just above the form's buttons (always its last row) and scrolls them into view. */
    private void showInlineError(VBox form, String message) {
        // Remove existing error label if any
        form.getChildren().removeIf(n -> "error-label".equals(n.getUserData()));
        Label err = new Label(message);
        err.setGraphic(TaskStyleUtils.iconLabel("\u26A0", 12));
        err.setUserData("error-label");
        TaskStyleUtils.fontNormal(err, 12);
        TaskStyleUtils.textColor(err, "#e74c3c");
        err.setWrapText(true);
        form.getChildren().add(Math.max(0, form.getChildren().size() - 1), err);
        for (Node node = form.getParent(); node != null; node = node.getParent()) {
            if (node instanceof ScrollPane scroll) {
                scroll.setVvalue(scroll.getVmax());
                break;
            }
        }
    }

    /**
     * Quick reschedule for delayed/postponed tasks: one date picker, no full edit form.
     */
    private void showRescheduleDialog(Task task) {
        if (task.getStatus() == TaskStatus.POSTPONED) {
            resumeDateDialog(task).showAndWait();
            return;
        }
        Dialog<LocalDate> dialog = new Dialog<>();
        dialog.setTitle("Reschedule Task");
        dialog.setHeaderText("Pick a new due date.");
        dialog.initOwner(this.getScene() != null ? this.getScene().getWindow() : null);

        DatePicker picker = new DatePicker(LocalDate.now().plusDays(1));
        picker.setMaxWidth(Double.MAX_VALUE);
        dialog.getDialogPane().setContent(picker);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(bt -> bt == ButtonType.OK ? picker.getValue() : null);

        dialog.showAndWait().ifPresent(newDate -> {
            try {
                taskService.rescheduleTask(task, newDate);
                refreshData();
            } catch (Exception ex) {
                Alert err = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
                err.initOwner(this.getScene() != null ? this.getScene().getWindow() : null);
                err.showAndWait();
            }
        });
    }

    /** Package-visible for exercising the actual save and validation flow. */
    Dialog<ButtonType> resumeDateDialog(final Task task) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Set resume date");
        dialog.setHeaderText("When should this task return?");
        if (getScene() != null) {
            dialog.initOwner(getScene().getWindow());
        }
        DatePicker picker = new DatePicker(task.getResumeOn());
        picker.setPromptText("Resume manually");
        picker.setMaxWidth(Double.MAX_VALUE);
        Button clear = new Button("Clear date");
        clear.getStyleClass().addAll("btn-gray", "btn-small");
        clear.setOnAction(event -> {
            picker.setValue(null);
            picker.getEditor().clear();
        });
        Label hint = new Label("Leave the date empty to keep this task postponed until you change its status. "
                + "Choosing a resume date does not change its deadline.");
        hint.setWrapText(true);
        hint.setMinHeight(Region.USE_PREF_SIZE);
        Label error = new Label();
        error.setWrapText(true);
        error.setMinHeight(Region.USE_PREF_SIZE);
        error.setVisible(false);
        error.setManaged(false);
        TaskStyleUtils.textColor(error, TaskStyleUtils.COLOR_DANGER);
        VBox content = new VBox(12, picker, clear, hint, error);
        content.setPrefWidth(360);
        dialog.getDialogPane().setContent(content);
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, save);
        dialog.getDialogPane().lookupButton(save).getStyleClass().add("btn-primary");
        dialog.getDialogPane().lookupButton(ButtonType.CANCEL).getStyleClass().add("btn-cancel");
        dialog.getDialogPane().lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                picker.commitValue();
                taskService.setTaskResumeDate(task, picker.getValue());
            } catch (Exception ex) {
                error.setText(ex.getMessage() == null ? "Could not save the resume date. Try again." : ex.getMessage());
                error.setVisible(true);
                error.setManaged(true);
                event.consume();
            }
        });
        dialog.setOnHidden(event -> {
            if (dialog.getResult() == save) {
                refreshData();
            }
        });
        return dialog;
    }

    // ──────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────

    private static String nvl(String s) {
        return s != null ? s : "";
    }

    /**
     * Translates the reminder combo into a TaskUpdate value. A cleared
     * selection has to travel as {@link TaskUpdate#CLEAR_REMINDER}, because
     * null on that record means "leave the existing reminder alone".
     */
    private static Integer reminderSelection(LocalDate deadline, Integer selected) {
        if (deadline == null || selected == null) {
            return TaskUpdate.CLEAR_REMINDER;
        }
        return selected;
    }

    /** Renders the reminder offset as plain English; null means no reminder. */
    private ListCell<Integer> reminderCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Integer days, boolean empty) {
                super.updateItem(days, empty);
                if (empty || days == null) {
                    setText("No reminder");
                } else if (days == 1) {
                    setText("1 day before the deadline");
                } else if (days == 7) {
                    setText("1 week before the deadline");
                } else if (days == 30) {
                    setText("1 month before the deadline");
                } else {
                    setText(days + " days before the deadline");
                }
            }
        };
    }

    private ListCell<Task> parentCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Task item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item == null ? "None (standalone task)" : item.getTitle());
            }
        };
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

    // ──────────────────────────────────────────────
    // RefreshablePanel
    // ──────────────────────────────────────────────

    public void refreshData() {
        try {
            tasks = taskService.getTasks();
            String previous = categoryFilter.getValue();
            refreshingCategories = true;
            try {
                categoryFilter.getItems().setAll("All");
                categoryFilter.getItems().addAll(categoryService.getCategories().stream().map(TaskCategory::name).toList());
                categoryFilter.setValue(categoryFilter.getItems().contains(previous) ? previous : "All");
            } finally {
                refreshingCategories = false;
            }
            rebuildTaskList();
            renderSelectedTask();
        } catch (Exception ex) {
            tasks = List.of();
            taskList.getChildren().clear();
            taskDetails.getChildren().clear();
            taskRows.clear();
            taskChecks.clear();
            checkedIds.clear();
            updateBulkSelection();
            Label error = detailLabel("Could not load tasks: " + ex.getMessage());
            TaskStyleUtils.textColor(error, TaskStyleUtils.COLOR_DANGER);
            Button retry = new Button("Retry");
            retry.setOnAction(e -> refreshData());
            taskList.getChildren().addAll(error, retry);
        }
    }

    @Override
    public Node getView() {
        return this;
    }

    @Override
    public void updateDisplay() {
        refreshData();
    }
}
