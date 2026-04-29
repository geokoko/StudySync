
package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.Task;
import com.studysync.domain.entity.TaskReminder;
import com.studysync.domain.valueobject.TaskCategory;
import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.domain.valueobject.TaskStatus;
import com.studysync.domain.service.TaskService;
import com.studysync.domain.service.CategoryService;
import com.studysync.domain.service.ReminderService;
import com.studysync.domain.service.TaskUpdate;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Full-width Task Management panel.
 * Tasks are grouped by status in a TabPane.
 * Sort/filter toolbar available on every tab.
 * Create/edit form opens in the app modal overlay.
 */
public class TaskManagementPanel extends ScrollPane implements RefreshablePanel {

    private final TaskService taskService;
    private final CategoryService categoryService;
    private final ReminderService reminderService;
    private final Consumer<Node> showModal;
    private final Runnable closeModal;

    // Toolbar controls (shared across all tabs)
    private TextField searchField;
    private ComboBox<String> categoryFilter;
    private ComboBox<String> priorityFilter;
    private ComboBox<String> sortCombo;

    // Status tabs
    private TabPane statusTabPane;

    public TaskManagementPanel(TaskService taskService, CategoryService categoryService,
                               ReminderService reminderService,
                               Consumer<Node> showModal, Runnable closeModal) {
        this.taskService = taskService;
        this.categoryService = categoryService;
        this.reminderService = reminderService;
        this.showModal = showModal;
        this.closeModal = closeModal;

        VBox mainContent = new VBox(0);
        mainContent.getStyleClass().add("panel-bg");

        this.setContent(mainContent);
        this.setFitToWidth(true);
        this.setFitToHeight(false);
        this.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        this.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.getStyleClass().add("tab-content-area");

        buildUI(mainContent);
        refreshData();
    }

    // ──────────────────────────────────────────────
    // UI CONSTRUCTION
    // ──────────────────────────────────────────────

    private void buildUI(VBox root) {
        root.getChildren().addAll(buildHeader(), buildToolbar(), buildStatusTabPane());
    }

    private HBox buildHeader() {
        HBox header = new HBox(15);
        header.setPadding(new Insets(20, 20, 10, 20));
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Task Management");
        TaskStyleUtils.fontBold(title, 24);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button newTaskBtn = new Button("+ New Task");
        newTaskBtn.getStyleClass().add("btn-primary");
        newTaskBtn.setStyle("-fx-font-size: 13px; -fx-padding: 8 16;");
        newTaskBtn.setOnAction(e -> showTaskForm(null));

        header.getChildren().addAll(title, spacer, newTaskBtn);
        return header;
    }

    private VBox buildToolbar() {
        VBox toolbar = new VBox(8);
        toolbar.setPadding(new Insets(0, 20, 10, 20));

        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        // Search
        searchField = new TextField();
        searchField.setPromptText("Search tasks…");
        searchField.setPrefWidth(220);
        searchField.textProperty().addListener((obs, o, n) -> rebuildAllTabs());

        // Category filter
        categoryFilter = new ComboBox<>();
        categoryFilter.setPromptText("All categories");
        categoryFilter.setPrefWidth(150);
        categoryFilter.getItems().add("All");
        categoryFilter.getItems().addAll(
            categoryService.getCategories().stream().map(TaskCategory::name).collect(Collectors.toList()));
        categoryFilter.setValue("All");
        categoryFilter.setOnAction(e -> rebuildAllTabs());

        // Priority filter
        priorityFilter = new ComboBox<>();
        priorityFilter.setPromptText("All priorities");
        priorityFilter.setPrefWidth(150);
        priorityFilter.getItems().addAll("All", "★★★★★ (5)", "★★★★ (4)", "★★★ (3)", "★★ (2)", "★ (1)");
        priorityFilter.setValue("All");
        priorityFilter.setOnAction(e -> rebuildAllTabs());

        // Sort
        sortCombo = new ComboBox<>();
        sortCombo.getItems().addAll("Priority (high→low)", "Priority (low→high)",
                "Deadline (soonest)", "Deadline (latest)", "Title (A→Z)");
        sortCombo.setValue("Priority (high→low)");
        sortCombo.setPrefWidth(180);
        sortCombo.setOnAction(e -> rebuildAllTabs());

        Label sortLabel = new Label("Sort:");
        TaskStyleUtils.fontNormal(sortLabel, 12);

        row.getChildren().addAll(searchField, categoryFilter, priorityFilter, sortLabel, sortCombo);
        toolbar.getChildren().add(row);
        return toolbar;
    }

    private TabPane buildStatusTabPane() {
        statusTabPane = new TabPane();
        statusTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        statusTabPane.getStyleClass().add("transparent-bg");
        VBox.setVgrow(statusTabPane, Priority.ALWAYS);

        // One tab per status group + "All" tab
        String[] tabNames = {"All", "Open", "In Progress", "Delayed", "Postponed", "Completed", "Cancelled"};
        for (String name : tabNames) {
            Tab t = new Tab(name);
            t.setUserData(name);
            t.setContent(buildTaskList(name));
            statusTabPane.getTabs().add(t);
        }

        statusTabPane.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) {
                n.setContent(buildTaskList((String) n.getUserData()));
            }
        });

        return statusTabPane;
    }

    // ──────────────────────────────────────────────
    // TASK LIST RENDERING
    // ──────────────────────────────────────────────

    private ScrollPane buildTaskList(String statusGroup) {
        List<Task> tasks = getFilteredTasks(statusGroup);

        VBox listBox = new VBox(8);
        listBox.setFillWidth(true);
        listBox.setPadding(new Insets(15, 20, 20, 20));

        if (tasks.isEmpty()) {
            Label empty = new Label("No tasks in this category.");
            TaskStyleUtils.fontNormal(empty, 14);
            empty.setTextFill(Color.web("#7f8c8d"));
            empty.setPadding(new Insets(30));
            listBox.getChildren().add(empty);
        } else {
            for (Task task : tasks) {
                listBox.getChildren().add(buildTaskCard(task));
            }
        }

        ScrollPane sp = new ScrollPane(listBox);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("transparent-bg");
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }

    private VBox buildTaskCard(Task task) {
        VBox wrapper = new VBox(0);
        wrapper.setMaxWidth(Double.MAX_VALUE);

        HBox card = new HBox(15);
        card.setPadding(new Insets(14));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(Double.MAX_VALUE);

        String bgColor = TaskStyleUtils.statusBackground(task.getStatus());
        String borderColor = TaskStyleUtils.taskBorderColor(task.getStatus());
        card.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 8 8 0 0;" +
                      " -fx-border-color: " + borderColor + "; -fx-border-radius: 8 8 0 0;" +
                      " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 4, 0, 0, 1);");

        // Left: Status indicator bar
        VBox colorBar = new VBox();
        colorBar.setMinWidth(4);
        colorBar.setPrefWidth(4);
        colorBar.setStyle("-fx-background-color: " + borderColor + "; -fx-background-radius: 2;");

        // Center: Task info
        VBox info = new VBox(5);
        HBox.setHgrow(info, Priority.ALWAYS);

        String safeTitle = task.getTitle() != null && !task.getTitle().trim().isEmpty()
                ? task.getTitle().trim()
                : "(Untitled task)";
        String safeCategory = task.getCategory() != null && !task.getCategory().trim().isEmpty()
                ? task.getCategory().trim()
                : "Uncategorized";

        // Title row
        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(safeTitle);
        titleLabel.setWrapText(true);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        if (task.getStatus() == TaskStatus.COMPLETED) {
            titleLabel.setStyle("-fx-strikethrough: true; -fx-text-fill: #7f8c8d;");
        }
        TaskStyleUtils.fontBold(titleLabel, 15);

        // Priority stars
        Label priorityLabel = new Label(task.getPriority() != null ? task.getPriority().toString() : "");
        TaskStyleUtils.fontNormal(priorityLabel, 13);
        priorityLabel.setTextFill(Color.web("#f39c12"));

        // Status badge
        Label statusBadge = new Label(task.getStatus().name());
        statusBadge.setGraphic(TaskStyleUtils.iconLabel(TaskStyleUtils.statusEmoji(task.getStatus()), 11));
        statusBadge.setTextFill(TaskStyleUtils.statusTextColor(task.getStatus()));
        statusBadge.setPadding(new Insets(2, 7, 2, 7));
        statusBadge.setStyle("-fx-background-color: " + TaskStyleUtils.statusBadgeBg(task.getStatus()) +
                             "; -fx-background-radius: 10;");
        TaskStyleUtils.fontBold(statusBadge, 11);

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);

        // Right: Action buttons
        HBox actions = new HBox(5);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().addAll("btn-primary", "btn-small");
        editBtn.setOnAction(e -> showTaskForm(task));

        if (task.getStatus() != TaskStatus.COMPLETED && task.getStatus() != TaskStatus.CANCELLED) {
            Button completeBtn = new Button("Done");
            completeBtn.getStyleClass().addAll("btn-success", "btn-small");
            completeBtn.setOnAction(e -> {
                taskService.updateTaskStatus(task, TaskStatus.COMPLETED);
                reminderService.removeRemindersForTask(task.getId());
                rebuildAllTabs();
            });
            actions.getChildren().add(completeBtn);
        }

        Button deleteBtn = new Button("Delete");
        deleteBtn.getStyleClass().addAll("btn-danger", "btn-small");
        deleteBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete \"" + safeTitle + "\"?", ButtonType.OK, ButtonType.CANCEL);
            confirm.setTitle("Delete Task");
            confirm.setHeaderText(null);
            confirm.initOwner(this.getScene() != null ? this.getScene().getWindow() : null);
            confirm.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.OK) {
                    taskService.removeTask(task);
                    reminderService.removeRemindersForTask(task.getId());
                    rebuildAllTabs();
                }
            });
        });

        actions.getChildren().addAll(editBtn, deleteBtn);

        titleRow.getChildren().addAll(titleLabel, priorityLabel, statusBadge);
        if (task.isRecurring()) {
            Label recurBadge = new Label("Recurring");
            recurBadge.setTextFill(Color.web("#7c4dff"));
            recurBadge.setPadding(new Insets(2, 7, 2, 7));
            recurBadge.setStyle("-fx-background-color: #ede7f6; -fx-background-radius: 10;");
            TaskStyleUtils.fontBold(recurBadge, 11);
            titleRow.getChildren().add(recurBadge);
        }
        titleRow.getChildren().addAll(titleSpacer, actions);

        // Meta row
        HBox metaRow = new HBox(18);
        metaRow.setAlignment(Pos.CENTER_LEFT);

         Label catLabel = new Label("Category: " + safeCategory);
         TaskStyleUtils.fontNormal(catLabel, 12);
         catLabel.setTextFill(Color.web("#495057"));

        metaRow.getChildren().add(catLabel);

        if (task.getDeadline() != null) {
            boolean overdue = task.getDeadline().isBefore(LocalDate.now())
                    && task.getStatus() != TaskStatus.COMPLETED
                    && task.getStatus() != TaskStatus.CANCELLED;
            Label deadlineLabel = new Label("Due: " + task.getDeadline().toString());
            TaskStyleUtils.fontNormal(deadlineLabel, 12);
            deadlineLabel.setTextFill(overdue ? Color.web("#c0392b") : Color.web("#495057"));
            metaRow.getChildren().add(deadlineLabel);
        }

        if (task.isRecurring()) {
            Label recurringDetail = new Label(task.getRecurringSummary());
            TaskStyleUtils.fontNormal(recurringDetail, 11);
            recurringDetail.setTextFill(Color.web("#6f42c1"));
            metaRow.getChildren().add(recurringDetail);
        }

        // Description
        if (task.getDescription() != null && !task.getDescription().isBlank()) {
            Label desc = new Label(task.getDescription().length() > 120
                    ? task.getDescription().substring(0, 120) + "…"
                    : task.getDescription());
            TaskStyleUtils.fontNormal(desc, 12);
            desc.setTextFill(Color.web("#7f8c8d"));
            desc.setWrapText(true);
            info.getChildren().addAll(titleRow, metaRow, desc);
        } else {
            Label desc = new Label("No description");
            TaskStyleUtils.fontNormal(desc, 12);
            desc.setTextFill(Color.web("#9aa0a6"));
            info.getChildren().addAll(titleRow, metaRow, desc);
        }

        // Goal History toggle button
        VBox goalHistoryPane = new VBox(6);
        goalHistoryPane.setPadding(new Insets(10, 14, 14, 14));
        goalHistoryPane.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: " + borderColor +
                                 "; -fx-border-width: 0 1 1 1; -fx-background-radius: 0 0 8 8;" +
                                 " -fx-border-radius: 0 0 8 8;");
        goalHistoryPane.setVisible(false);
        goalHistoryPane.setManaged(false);

        Button goalHistoryBtn = new Button("Goal History");
        goalHistoryBtn.getStyleClass().addAll("btn-small");
        goalHistoryBtn.setStyle("-fx-font-size: 11px; -fx-padding: 3 10; -fx-background-color: #e8eaf6;" +
                                " -fx-background-radius: 4; -fx-text-fill: #3949ab; -fx-cursor: hand;");
        goalHistoryBtn.setOnAction(e -> {
            boolean showing = goalHistoryPane.isVisible();
            if (!showing) {
                populateGoalHistory(goalHistoryPane, task);
                card.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 8 8 0 0;" +
                              " -fx-border-color: " + borderColor + "; -fx-border-radius: 8 8 0 0;" +
                              " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 4, 0, 0, 1);");
            } else {
                card.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 8;" +
                              " -fx-border-color: " + borderColor + "; -fx-border-radius: 8;" +
                              " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 4, 0, 0, 1);");
            }
            goalHistoryPane.setVisible(!showing);
            goalHistoryPane.setManaged(!showing);
            goalHistoryBtn.setText(showing ? "Goal History" : "Hide Goals");
        });
        info.getChildren().add(goalHistoryBtn);

        card.getChildren().addAll(colorBar, info);
        wrapper.getChildren().addAll(card, goalHistoryPane);
        return wrapper;
    }

    private void populateGoalHistory(VBox pane, Task task) {
        pane.getChildren().clear();

        List<StudyGoal> goals = StudyGoal.findByTaskId(task.getId());

        if (goals.isEmpty()) {
            Label empty = new Label("No goals have been planned for this task yet.");
            TaskStyleUtils.fontNormal(empty, 12);
            empty.setTextFill(Color.web("#7f8c8d"));
            pane.getChildren().add(empty);
            return;
        }

        // Summary stats
        long achieved = goals.stream().filter(StudyGoal::isAchieved).count();
        long missed = goals.stream().filter(StudyGoal::isFailed).count();
        long active = goals.stream().filter(g -> !g.isAchieved() && !g.isFailed()).count();

        Label statsLabel = new Label("Total: " + goals.size()
                + "  |  Achieved: " + achieved
                + "  |  Missed: " + missed
                + "  |  Active: " + active);
        TaskStyleUtils.fontSemiBold(statsLabel, 11);
        statsLabel.setTextFill(Color.web("#495057"));
        statsLabel.setPadding(new Insets(0, 0, 4, 0));
        pane.getChildren().add(statsLabel);

        // Goal list
        for (StudyGoal goal : goals) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4, 8, 4, 8));

            // Status indicator
            String statusIcon;
            String statusColor;
            if (goal.isAchieved()) {
                statusIcon = "\u2713"; // checkmark
                statusColor = "#27ae60";
            } else if (goal.isFailed()) {
                statusIcon = "\u2715"; // X
                statusColor = "#e74c3c";
            } else if (goal.isDelayed()) {
                statusIcon = "\u231B"; // hourglass
                statusColor = "#f39c12";
            } else {
                statusIcon = "\u25CB"; // circle
                statusColor = "#3498db";
            }

            Label icon = TaskStyleUtils.iconLabel(statusIcon, 11);
            icon.setTextFill(Color.web(statusColor));

            // Date
            Label dateLabel = new Label(goal.getDate().toString());
            TaskStyleUtils.fontNormal(dateLabel, 11);
            dateLabel.setTextFill(Color.web("#6c757d"));
            dateLabel.setMinWidth(80);

            // Description
            String descText = goal.getDescription() != null ? goal.getDescription() : "";
            if (descText.length() > 80) descText = descText.substring(0, 80) + "...";
            Label descLabel = new Label(descText);
            TaskStyleUtils.fontNormal(descLabel, 11);
            descLabel.setWrapText(true);
            HBox.setHgrow(descLabel, Priority.ALWAYS);
            if (goal.isFailed()) {
                descLabel.setTextFill(Color.web("#999"));
            } else if (goal.isAchieved()) {
                descLabel.setTextFill(Color.web("#2d6a4f"));
            } else {
                descLabel.setTextFill(Color.web("#333"));
            }

            // Status badge
            Label badge;
            if (goal.isAchieved()) {
                badge = new Label("Achieved");
                badge.setStyle("-fx-background-color: #e8f5e9; -fx-background-radius: 8; -fx-padding: 1 6;");
                badge.setTextFill(Color.web("#1b5e20"));
            } else if (goal.isFailed()) {
                badge = new Label(goal.getStatus() == StudyGoal.GoalStatus.ABANDONED ? "Abandoned" : "Missed");
                badge.setStyle("-fx-background-color: #ffebee; -fx-background-radius: 8; -fx-padding: 1 6;");
                badge.setTextFill(Color.web("#b71c1c"));
            } else if (goal.isDelayed()) {
                badge = new Label("Attempt " + goal.getAttemptNumber()
                        + " (" + goal.getMissedAttemptCount() + " miss"
                        + (goal.getMissedAttemptCount() == 1 ? "" : "es") + ")");
                badge.setStyle("-fx-background-color: #fff3e0; -fx-background-radius: 8; -fx-padding: 1 6;");
                badge.setTextFill(Color.web("#e65100"));
            } else {
                badge = new Label("Active");
                badge.setStyle("-fx-background-color: #e3f2fd; -fx-background-radius: 8; -fx-padding: 1 6;");
                badge.setTextFill(Color.web("#0d47a1"));
            }
            TaskStyleUtils.fontBold(badge, 10);

            row.getChildren().addAll(icon, dateLabel, descLabel, badge);

            // Alternate row background
            row.setStyle("-fx-background-color: " + (pane.getChildren().size() % 2 == 0 ? "#f0f0f0" : "transparent") +
                         "; -fx-background-radius: 4;");

            pane.getChildren().add(row);
        }
    }

    // ──────────────────────────────────────────────
    // TASK FORM (modal overlay)
    // ──────────────────────────────────────────────

    private void showTaskForm(Task existingTask) {
        boolean isNew = existingTask == null;

        VBox form = new VBox(12);
        form.setPadding(new Insets(24));
        form.getStyleClass().add("modal-content-light");
        form.setMaxWidth(520);
        form.setMaxHeight(Region.USE_PREF_SIZE);

        Label formTitle = new Label(isNew ? "+ New Task" : "Edit Task");
        TaskStyleUtils.fontBold(formTitle, 18);

        // Title
        TextField titleField = new TextField(isNew ? "" : existingTask.getTitle());
        titleField.setPromptText("Task title *");

        // Description
        TextArea descArea = new TextArea(isNew ? "" : nvl(existingTask.getDescription()));
        descArea.setPromptText("Description (optional)");
        descArea.setPrefRowCount(3);
        descArea.setWrapText(true);

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
                LocalDate startDate = recurringCheck.isSelected() ? startDatePicker.getValue() : null;
                if (isNew) {
                    Task newTask = new Task(null, title, descArea.getText().trim(),
                            cat.name(), new TaskPriority(prio),
                            deadlinePicker.getValue(), TaskStatus.OPEN, 0, recurPattern, startDate);
                    taskService.addTask(newTask);
                } else {
                    TaskUpdate update = new TaskUpdate(title, descArea.getText().trim(),
                            cat.name(), new TaskPriority(prio),
                            deadlinePicker.getValue(), recurPattern, startDate);
                    taskService.updateTask(existingTask, update);
                    // Apply status change separately if editing
                    if (statusCombo.getValue() != null && statusCombo.getValue() != existingTask.getStatus()) {
                        taskService.updateTaskStatus(existingTask, statusCombo.getValue());
                    }
                }
                closeModal.run();
                rebuildAllTabs();
            } catch (Exception ex) {
                showInlineError(form, ex.getMessage());
            }
        });

        HBox btnRow = new HBox(10, saveBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(8, 0, 0, 0));

        form.getChildren().addAll(formTitle,
                new Label("Title:"), titleField,
                new Label("Description:"), descArea,
                new Label("Category:"), catCombo, newCatRow,
                new Label("Priority:"), priorityCombo,
                new Label("Deadline:"), deadlinePicker, deadlineHint);

        if (!isNew) {
            form.getChildren().addAll(new Label("Status:"), statusCombo);
        }

        form.getChildren().addAll(recurringCheck, recurringOptions, btnRow);

        ScrollPane wrapper = new ScrollPane(form);
        wrapper.setFitToWidth(true);
        wrapper.getStyleClass().add("transparent-bg");
        wrapper.setMaxWidth(540);
        wrapper.setMaxHeight(680);
        showModal.accept(wrapper);
    }

    private void showInlineError(VBox form, String message) {
        // Remove existing error label if any
        form.getChildren().removeIf(n -> "error-label".equals(n.getUserData()));
        Label err = new Label(message);
        err.setGraphic(TaskStyleUtils.iconLabel("\u26A0", 12));
        err.setUserData("error-label");
        TaskStyleUtils.fontNormal(err, 12);
        err.setTextFill(Color.web("#e74c3c"));
        err.setWrapText(true);
        form.getChildren().add(err);
    }

    // ──────────────────────────────────────────────
    // FILTERING / SORTING
    // ──────────────────────────────────────────────

    private List<Task> getFilteredTasks(String statusGroup) {
        List<Task> all = taskService.getTasks();

        // Status filter
        if (!"All".equals(statusGroup)) {
            TaskStatus target = toStatus(statusGroup);
            all = all.stream()
                    .filter(t -> target != null && t.getStatus() == target)
                    .collect(Collectors.toList());
        }

        // Search filter
        String search = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        if (!search.isEmpty()) {
            all = all.stream()
                    .filter(t -> t.getTitle().toLowerCase().contains(search)
                            || (t.getDescription() != null && t.getDescription().toLowerCase().contains(search)))
                    .collect(Collectors.toList());
        }

        // Category filter
        String cat = categoryFilter != null ? categoryFilter.getValue() : "All";
        if (cat != null && !"All".equals(cat)) {
            all = all.stream()
                    .filter(t -> cat.equalsIgnoreCase(t.getCategory()))
                    .collect(Collectors.toList());
        }

        // Priority filter
        String prio = priorityFilter != null ? priorityFilter.getValue() : "All";
        if (prio != null && !"All".equals(prio)) {
            int stars = parsePriorityStars(prio);
            if (stars > 0) {
                all = all.stream()
                        .filter(t -> t.getPriority() != null && t.getPriority().stars() == stars)
                        .collect(Collectors.toList());
            }
        }

        // Sort
        String sort = sortCombo != null ? sortCombo.getValue() : "Priority (high→low)";
        Comparator<Task> comparator = switch (sort != null ? sort : "") {
            case "Priority (low→high)" -> Comparator.comparingInt(t -> (t.getPriority() != null ? t.getPriority().stars() : 0));
            case "Deadline (soonest)" -> Comparator.comparing(t -> t.getDeadline() != null ? t.getDeadline() : LocalDate.MAX);
            case "Deadline (latest)" -> Comparator.comparing((Task t) -> t.getDeadline() != null ? t.getDeadline() : LocalDate.MIN).reversed();
            case "Title (A→Z)" -> Comparator.comparing(Task::getTitle, String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparingInt((Task t) -> t.getPriority() != null ? t.getPriority().stars() : 0).reversed();
        };

        all.sort(comparator);
        return all;
    }

    private void rebuildAllTabs() {
        if (statusTabPane == null) return;
        for (Tab tab : statusTabPane.getTabs()) {
            tab.setContent(buildTaskList((String) tab.getUserData()));
        }
    }

    // ──────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────

    private static TaskStatus toStatus(String name) {
        return switch (name) {
            case "Open" -> TaskStatus.OPEN;
            case "In Progress" -> TaskStatus.IN_PROGRESS;
            case "Completed" -> TaskStatus.COMPLETED;
            case "Delayed" -> TaskStatus.DELAYED;
            case "Postponed" -> TaskStatus.POSTPONED;
            case "Cancelled" -> TaskStatus.CANCELLED;
            default -> null;
        };
    }

    private static int parsePriorityStars(String val) {
        try {
            // e.g. "★★★ (3)" → extract the number in parentheses
            int start = val.lastIndexOf('(');
            int end = val.lastIndexOf(')');
            if (start >= 0 && end > start) {
                return Integer.parseInt(val.substring(start + 1, end).trim());
            }
        } catch (Exception ignored) { }
        return 0;
    }



    private static String nvl(String s) {
        return s != null ? s : "";
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
        // Refresh category dropdown
        if (categoryFilter != null) {
            String prev = categoryFilter.getValue();
            categoryFilter.getItems().setAll("All");
            categoryFilter.getItems().addAll(
                categoryService.getCategories().stream().map(TaskCategory::name).collect(Collectors.toList()));
            categoryFilter.setValue(prev != null ? prev : "All");
        }
        rebuildAllTabs();
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
