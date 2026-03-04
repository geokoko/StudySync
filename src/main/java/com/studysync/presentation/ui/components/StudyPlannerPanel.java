
package com.studysync.presentation.ui.components;

import com.studysync.domain.service.StudyService;
import com.studysync.domain.service.StudySessionEnd;
import com.studysync.domain.service.DateTimeService;
import com.studysync.domain.service.TaskService;
import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.StudySession;
import com.studysync.domain.entity.DailyReflection;
import com.studysync.domain.entity.Task;
import com.studysync.domain.valueobject.TaskStatus;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.Node;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

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

    private final Consumer<Node> showModal;
    private final Runnable closeModal;

    // Navigation state — the date currently displayed in the planner
    private LocalDate displayDate;

    // Live-session state
    private StudySession currentSession;
    private Timeline sessionTimer;

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
                             TaskService taskService,
                             Consumer<Node> showModal, Runnable closeModal) {
        this.studyService = studyService;
        this.dateTimeService = dateTimeService;
        this.taskService = taskService;
        this.showModal = showModal;
        this.closeModal = closeModal;
        this.displayDate = dateTimeService.getCurrentDate();

        VBox mainContent = new VBox(20);
        mainContent.setPadding(new Insets(20));
        mainContent.setStyle("-fx-background-color: linear-gradient(to bottom, #f8f9fa, #e9ecef);");

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
        prevBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 5;");
        prevBtn.setOnAction(e -> {
            displayDate = displayDate.minusDays(1);
            updateDisplay();
        });

        dateNavLabel = new Label();
        dateNavLabel.setFont(Font.font("System", FontWeight.BOLD, 22));
        dateNavLabel.setTextFill(Color.web("#2c3e50"));
        dateNavLabel.setMinWidth(300);
        dateNavLabel.setAlignment(Pos.CENTER);

        Button nextBtn = new Button("▶");
        nextBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 5;");
        nextBtn.setOnAction(e -> {
            displayDate = displayDate.plusDays(1);
            updateDisplay();
        });

        Button todayBtn = new Button("» Today");
        todayBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-background-radius: 5;");
        todayBtn.setOnAction(e -> {
            displayDate = dateTimeService.getCurrentDate();
            updateDisplay();
        });

        navRow.getChildren().addAll(prevBtn, dateNavLabel, nextBtn, todayBtn);

        dailyProgressBar = new ProgressBar(0);
        dailyProgressBar.setPrefWidth(300);
        dailyProgressBar.setStyle("-fx-accent: #27ae60;");

        progressLabel = new Label("Daily Progress: 0%");
        progressLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));
        progressLabel.setTextFill(Color.web("#34495e"));

        header.getChildren().addAll(navRow, dailyProgressBar, progressLabel);
        mainContent.getChildren().add(header);
    }

    /** Tasks section (replaces "Today's Goals"). */
    private void createTasksSection(VBox mainContent) {
        VBox section = new VBox(15);
        section.setStyle("-fx-background-color: white; -fx-background-radius: 10;" +
                         " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        section.setPadding(new Insets(20));

        HBox sectionHeader = new HBox(15);
        sectionHeader.setAlignment(Pos.CENTER_LEFT);

        Label sectionTitle = new Label("☑ Today's Tasks");
        sectionTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        sectionTitle.setTextFill(Color.web("#2c3e50"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addGoalBtn = new Button("+ Add Goal");
        addGoalBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-background-radius: 5;");
        addGoalBtn.setOnAction(e -> showAddGoalDialog(null));

        sectionHeader.getChildren().addAll(sectionTitle, spacer, addGoalBtn);

        tasksContainer = new VBox(10);
        section.getChildren().addAll(sectionHeader, tasksContainer);
        mainContent.getChildren().add(section);
    }

    /** Session section: controls + FlowPane of session cards. */
    private void createSessionSection(VBox mainContent) {
        VBox sessionSection = new VBox(15);
        sessionSection.setStyle("-fx-background-color: white; -fx-background-radius: 10;" +
                                " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        sessionSection.setPadding(new Insets(20));

        Label sessionTitle = new Label("✎ Study Session");
        sessionTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        sessionTitle.setTextFill(Color.web("#2c3e50"));

        HBox sessionControls = new HBox(15);
        sessionControls.setAlignment(Pos.CENTER_LEFT);

        Button startSessionBtn = new Button("Start Session");
        startSessionBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-background-radius: 5;");

        Button endSessionBtn = new Button("End Session");
        endSessionBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 5;");
        endSessionBtn.setDisable(true);

        Label sessionStatus = new Label("No active session");
        sessionStatus.setFont(Font.font("System", FontWeight.NORMAL, 14));

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
        completedLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        completedLabel.setTextFill(Color.web("#2c3e50"));

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
        reflectionSection.setStyle("-fx-background-color: white; -fx-background-radius: 10;" +
                                   " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        reflectionSection.setPadding(new Insets(20));

        Label reflectionTitle = new Label("Daily Reflection");
        reflectionTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        reflectionTitle.setTextFill(Color.web("#2c3e50"));

        reflectionArea = new TextArea();
        reflectionArea.setPromptText("What helped you focus today?\nWhat distracted you?\nOne thing to improve tomorrow?");
        reflectionArea.setPrefRowCount(4);
        reflectionArea.setWrapText(true);

        Button saveReflectionBtn = new Button("Save Reflection");
        saveReflectionBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-background-radius: 5;");
        saveReflectionBtn.setOnAction(e -> saveReflection());

        reflectionSection.getChildren().addAll(reflectionTitle, reflectionArea, saveReflectionBtn);
        mainContent.getChildren().add(reflectionSection);
    }

    // ──────────────────────────────────────────────
    // TASKS DISPLAY
    // ──────────────────────────────────────────────

    private void updateTasksDisplay() {
        tasksContainer.getChildren().clear();

        List<Task> tasks = taskService.getTasksForDate(displayDate);

        if (tasks.isEmpty()) {
            // No tasks for this day — offer "Create Task" shortcut
            VBox emptyBox = new VBox(8);
            emptyBox.setAlignment(Pos.CENTER_LEFT);
            Label emptyLabel = new Label("No tasks scheduled for this day.");
            emptyLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
            emptyLabel.setTextFill(Color.web("#7f8c8d"));

            Button createTaskBtn = new Button("+ Create Task");
            createTaskBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 5;");
            createTaskBtn.setOnAction(e -> showCreateTaskDialog());

            emptyBox.getChildren().addAll(emptyLabel, createTaskBtn);
            tasksContainer.getChildren().add(emptyBox);
            return;
        }

        for (Task task : tasks) {
            tasksContainer.getChildren().add(buildTaskRow(task));
        }

        // Unlinked goals section (goals with no task)
        List<StudyGoal> unlinkedGoals = StudyGoal.findUnlinkedForDate(displayDate).stream()
                .filter(g -> !g.isAchieved())
                .toList();
        if (!unlinkedGoals.isEmpty()) {
            VBox unlinkedSection = new VBox(6);
            unlinkedSection.setPadding(new Insets(10, 0, 0, 0));
            Label unlinkedTitle = new Label("Goals without a task:");
            unlinkedTitle.setFont(Font.font("System", FontWeight.BOLD, 13));
            unlinkedTitle.setTextFill(Color.web("#6c757d"));
            unlinkedSection.getChildren().add(unlinkedTitle);
            for (StudyGoal goal : unlinkedGoals) {
                unlinkedSection.getChildren().add(buildGoalRow(goal, null));
            }
            tasksContainer.getChildren().add(unlinkedSection);
        }
    }

    /**
     * Builds a clickable task card that expands to show linked goals.
     */
    private VBox buildTaskRow(Task task) {
        VBox card = new VBox();
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8;" +
                      " -fx-border-color: " + taskBorderColor(task) + ";" +
                      " -fx-border-radius: 8;" +
                      " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 4, 0, 0, 1);");

        // ── Header row (always visible, clickable) ──
        HBox headerRow = new HBox(10);
        headerRow.setPadding(new Insets(12, 14, 12, 14));
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setStyle("-fx-cursor: hand;");

        Label arrow = new Label("▶");
        arrow.setFont(Font.font("System", FontWeight.BOLD, 11));
        arrow.setTextFill(Color.web("#7f8c8d"));

        Label taskTitle = new Label(task.getTitle());
        taskTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        taskTitle.setTextFill(Color.web("#2c3e50"));

        Label priorityLabel = new Label(task.getPriority() != null ? task.getPriority().toString() : "");
        priorityLabel.setTextFill(Color.web("#f39c12"));

        Label statusBadge = new Label(task.getStatus().name());
        statusBadge.setFont(Font.font("System", FontWeight.BOLD, 10));
        statusBadge.setPadding(new Insets(2, 6, 2, 6));
        statusBadge.setStyle("-fx-background-color: " + statusBadgeBg(task.getStatus()) +
                             "; -fx-background-radius: 10;");
        statusBadge.setTextFill(statusTextColor(task.getStatus()));

        if (task.isRecurring()) {
            Label recurBadge = new Label("\uD83D\uDD01");
            recurBadge.setFont(Font.font("System", FontWeight.NORMAL, 12));
            recurBadge.setTooltip(new Tooltip(task.getRecurringSummary()));
            headerRow.getChildren().add(recurBadge);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        headerRow.getChildren().addAll(0, List.of(arrow)); // arrow first
        headerRow.getChildren().addAll(taskTitle, priorityLabel, statusBadge, spacer);

        // ── Expandable goals panel ──
        VBox goalsPanel = new VBox(6);
        goalsPanel.setPadding(new Insets(0, 14, 12, 28));
        goalsPanel.setVisible(false);
        goalsPanel.setManaged(false);

        // Toggle expand/collapse on header click
        headerRow.setOnMouseClicked(e -> {
            boolean nowVisible = !goalsPanel.isVisible();
            goalsPanel.setVisible(nowVisible);
            goalsPanel.setManaged(nowVisible);
            arrow.setText(nowVisible ? "▼" : "▶");
            if (nowVisible) {
                populateGoalsPanel(goalsPanel, task);
            }
        });

        card.getChildren().addAll(headerRow, goalsPanel);
        return card;
    }

    private void populateGoalsPanel(VBox goalsPanel, Task task) {
        goalsPanel.getChildren().clear();

        List<StudyGoal> goals = StudyGoal.findByTaskIdForDate(task.getId(), displayDate).stream()
                .filter(g -> !g.isAchieved())
                .toList();

        if (goals.isEmpty()) {
            Label noGoals = new Label("No goals linked to this task yet.");
            noGoals.setFont(Font.font("System", FontWeight.NORMAL, 12));
            noGoals.setTextFill(Color.web("#7f8c8d"));

            Button addGoalBtn = new Button("+ Create Goal");
            addGoalBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white;" +
                                " -fx-background-radius: 4; -fx-font-size: 11px;");
            addGoalBtn.setOnAction(e -> {
                showAddGoalDialog(task);
                populateGoalsPanel(goalsPanel, task); // refresh after add
            });

            goalsPanel.getChildren().addAll(noGoals, addGoalBtn);
        } else {
            for (StudyGoal goal : goals) {
                goalsPanel.getChildren().add(buildGoalRow(goal, task));
            }
            Button addMoreBtn = new Button("+ Add Goal");
            addMoreBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white;" +
                                " -fx-background-radius: 4; -fx-font-size: 11px;");
            addMoreBtn.setOnAction(e -> {
                showAddGoalDialog(task);
                populateGoalsPanel(goalsPanel, task);
            });
            goalsPanel.getChildren().add(addMoreBtn);
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
        goalLabel.setFont(Font.font("System", FontWeight.NORMAL, 13));
        if (goal.isAchieved()) {
            goalLabel.setStyle("-fx-strikethrough: true; -fx-text-fill: #7f8c8d;");
        }
        textBox.getChildren().add(goalLabel);

        if (goal.isDelayed()) {
            Label delayLabel = new Label(String.format("Delayed %d day(s) — -%d pts",
                    goal.getDaysDelayed(), goal.getPointsDeducted()));
            delayLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
            delayLabel.setStyle("-fx-text-fill: #dc3545;");
            textBox.getChildren().add(delayLabel);
        }

        row.getChildren().addAll(check, textBox);
        return row;
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
            none.setFont(Font.font("System", FontWeight.NORMAL, 13));
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
        timeLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        timeLabel.setTextFill(Color.web("#2c3e50"));

        Label durationLabel = new Label(session.getDurationMinutes() + " min");
        durationLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
        durationLabel.setTextFill(Color.web("#7f8c8d"));

        // Focus stars (compact)
        StringBuilder stars = new StringBuilder();
        for (int i = 1; i <= 5; i++) stars.append(i <= session.getFocusLevel() ? "★" : "☆");
        Label focusLabel = new Label(stars.toString());
        focusLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
        focusLabel.setTextFill(Color.web("#f39c12"));

        Label pointsLabel = new Label("♦ " + session.getPointsEarned() + " pts");
        pointsLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 11));
        pointsLabel.setTextFill(Color.web("#27ae60"));

        // Action buttons row
        HBox btnRow = new HBox(5);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        Button detailsBtn = new Button("Details");
        detailsBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;" +
                            " -fx-background-radius: 3; -fx-font-size: 10px; -fx-padding: 3 7;");
        detailsBtn.setOnAction(e -> showSessionDetails(session));

        Button deleteBtn = new Button("✕");
        deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;" +
                           " -fx-background-radius: 3; -fx-font-size: 10px; -fx-padding: 3 7;");
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
        // Minimal task-creation modal (title + category selector is enough to bootstrap)
        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: white; -fx-background-radius: 10;" +
                         " -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 10, 0, 0, 0);");
        content.setMaxWidth(400);
        content.setMaxHeight(Region.USE_PREF_SIZE);

        Label header = new Label("Create a new task");
        header.setFont(Font.font("System", FontWeight.BOLD, 16));

        Label hint = new Label("You can add full details in the Tasks tab.");
        hint.setFont(Font.font("System", FontWeight.NORMAL, 11));
        hint.setTextFill(Color.web("#7f8c8d"));

        TextField titleField = new TextField();
        titleField.setPromptText("Task title *");

        Button okBtn = new Button("Create");
        okBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        okBtn.setDisable(true);
        titleField.textProperty().addListener((obs, o, n) -> okBtn.setDisable(n.trim().isEmpty()));

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white;");
        cancelBtn.setOnAction(e -> closeModal.run());

        okBtn.setOnAction(e -> {
            String title = titleField.getText().trim();
            if (title.isEmpty()) return;
            try {
                // Use "General" as default category if nothing else — user can edit later
                String defaultCat = "General";
                Task t = new Task(null, title, "", defaultCat,
                        new com.studysync.domain.valueobject.TaskPriority(1),
                        null, TaskStatus.OPEN, 0, null);
                taskService.addTask(t);
                closeModal.run();
                updateTasksDisplay();
            } catch (Exception ex) {
                Label err = new Label("⚠ " + ex.getMessage());
                err.setTextFill(Color.web("#e74c3c"));
                content.getChildren().add(err);
            }
        });

        HBox btnRow = new HBox(10, okBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        content.getChildren().addAll(header, hint, new Label("Title:"), titleField, btnRow);
        showModal.accept(content);
    }

    private void showAddGoalDialog(Task linkedTask) {
        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: white; -fx-background-radius: 10;" +
                         " -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 10, 0, 0, 0);");
        content.setMaxWidth(450);
        content.setMaxHeight(Region.USE_PREF_SIZE);

        Label headerLabel = new Label(linkedTask != null
                ? "Add goal for: " + linkedTask.getTitle()
                : "Create a new study goal");
        headerLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

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
            taskLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
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
        okBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white;");
        okBtn.setDisable(true);
        descArea.textProperty().addListener((obs, o, n) -> okBtn.setDisable(n.trim().isEmpty()));

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white;");
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
        content.setStyle("-fx-background-color: white; -fx-background-radius: 10;" +
                         " -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 10, 0, 0, 0);");
        content.setMaxWidth(400);
        content.setMaxHeight(Region.USE_PREF_SIZE);

        Label headerLabel = new Label("How was your study session?");
        headerLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

        Label focusLabel = new Label("Focus Level (1–5):");
        Slider focusSlider = new Slider(1, 5, 3);
        focusSlider.setShowTickLabels(true);
        focusSlider.setShowTickMarks(true);
        focusSlider.setMajorTickUnit(1);
        focusSlider.setSnapToTicks(true);

        Label focusWarning = new Label("• Average focus.");
        focusWarning.setFont(Font.font("System", FontWeight.NORMAL, 11));
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
        okBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white;");
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
        closeButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        closeButton.setOnAction(e -> closeModal.run());
        HBox btnBox = new HBox(closeButton);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        mainContent.getChildren().add(btnBox);

        ScrollPane scrollPane = new ScrollPane(mainContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
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
        sessionTitle.setFont(Font.font("System", FontWeight.BOLD, 24));
        sessionTitle.setTextFill(Color.WHITE);

        Label dateTime = new Label(
                session.getDate().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")) +
                " • " + (session.getStartTime() != null
                        ? session.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "N/A"));
        dateTime.setFont(Font.font("System", FontWeight.NORMAL, 16));
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
        iconL.setFont(Font.font("System", FontWeight.NORMAL, 20));
        Label valueL = new Label(value);
        valueL.setFont(Font.font("System", FontWeight.BOLD, 18));
        valueL.setTextFill(Color.WHITE);
        Label descL = new Label(label);
        descL.setFont(Font.font("System", FontWeight.NORMAL, 12));
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
        timeTitle.setFont(Font.font("System", FontWeight.BOLD, 16));

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
        focusTitle.setFont(Font.font("System", FontWeight.BOLD, 16));

        HBox starsBox = new HBox(2);
        for (int i = 1; i <= 5; i++) {
            Label star = new Label(i <= session.getFocusLevel() ? "★" : "☆");
            star.setFont(Font.font("System", FontWeight.NORMAL, 20));
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
        notesTitle.setFont(Font.font("System", FontWeight.BOLD, 18));

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
        compTitle.setFont(Font.font("System", FontWeight.BOLD, 16));

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
        content.setStyle("-fx-background-color: white; -fx-background-radius: 10;" +
                         " -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 10, 0, 0, 0);");
        content.setMaxWidth(300);
        content.setMaxHeight(Region.USE_PREF_SIZE);
        Label titleL = new Label("Reflection Saved");
        titleL.setFont(Font.font("System", FontWeight.BOLD, 16));
        Button okBtn = new Button("OK");
        okBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
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

    // ──────────────────────────────────────────────
    // STYLE HELPERS
    // ──────────────────────────────────────────────

    private static String taskBorderColor(Task task) {
        return switch (task.getStatus()) {
            case COMPLETED -> "#27ae60";
            case DELAYED   -> "#e74c3c";
            case IN_PROGRESS -> "#f39c12";
            case CANCELLED -> "#bdc3c7";
            case POSTPONED -> "#9c27b0";
            default        -> "#3498db";
        };
    }

    private static String statusBadgeBg(TaskStatus s) {
        return switch (s) {
            case COMPLETED   -> "#e8f5e9";
            case DELAYED     -> "#ffebee";
            case IN_PROGRESS -> "#fff3e0";
            case CANCELLED   -> "#eceff1";
            case POSTPONED   -> "#f3e5f5";
            default          -> "#e3f2fd";
        };
    }

    private static Color statusTextColor(TaskStatus s) {
        return switch (s) {
            case COMPLETED   -> Color.web("#27ae60");
            case DELAYED     -> Color.web("#e74c3c");
            case IN_PROGRESS -> Color.web("#f39c12");
            case CANCELLED   -> Color.web("#7f8c8d");
            case POSTPONED   -> Color.web("#9c27b0");
            default          -> Color.web("#3498db");
        };
    }
}
