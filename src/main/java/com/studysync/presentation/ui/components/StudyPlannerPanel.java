
package com.studysync.presentation.ui.components;

import com.studysync.domain.service.StudyService;
import com.studysync.domain.service.StudySessionEnd;
import com.studysync.domain.service.DateTimeService;
import com.studysync.domain.service.TaskService;
import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.StudySession;
import com.studysync.domain.entity.DailyReflection;
import com.studysync.domain.entity.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.Node;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.Optional;
import javafx.util.Callback;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;

public class StudyPlannerPanel extends ScrollPane implements RefreshablePanel {
    private final StudyService studyService;
    private final DateTimeService dateTimeService;
    private final TaskService taskService;
    private StudySession currentSession;
    private Timeline sessionTimer;
    private VBox goalsContainer;
    private VBox sessionsContainer;
    private TextArea reflectionArea;
    private ProgressBar dailyProgressBar;
    private Label progressLabel;
    private Label dateLabel;
    private TextArea sessionTextArea;

    public StudyPlannerPanel(StudyService studyService, DateTimeService dateTimeService, TaskService taskService) {
        this.studyService = studyService;
        this.dateTimeService = dateTimeService;
        this.taskService = taskService;
        
        // Create main content container
        VBox mainContent = new VBox(20);
        mainContent.setPadding(new Insets(20));
        mainContent.setStyle("-fx-background-color: linear-gradient(to bottom, #f8f9fa, #e9ecef);");
        
        // Set up ScrollPane properties
        this.setContent(mainContent);
        this.setFitToWidth(true);
        this.setFitToHeight(true);
        this.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.getStyleClass().add("tab-content-area");
        
        initializeComponents(mainContent);
        
        // Register for date change notifications
        dateTimeService.addDateChangeListener(this::onDateChanged);
        
        updateDisplay();
    }

    private void initializeComponents(VBox mainContent) {
        // Header with date and progress
        createHeader(mainContent);
        
        // Daily goals section
        createGoalsSection(mainContent);
        
        // Study session section
        createSessionSection(mainContent);
        
        // Daily reflection section
        createReflectionSection(mainContent);
    }

    private void createHeader(VBox mainContent) {
        VBox header = new VBox(10);
        header.setAlignment(Pos.CENTER);
        
        dateLabel = new Label("📅 " + dateTimeService.getFormattedCurrentDate());
        dateLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        dateLabel.setTextFill(Color.web("#2c3e50"));
        
        dailyProgressBar = new ProgressBar(0);
        dailyProgressBar.setPrefWidth(300);
        dailyProgressBar.setStyle("-fx-accent: #27ae60;");
        
        progressLabel = new Label("Daily Progress: 0%");
        progressLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));
        progressLabel.setTextFill(Color.web("#34495e"));
        
        header.getChildren().addAll(dateLabel, dailyProgressBar, progressLabel);
        mainContent.getChildren().add(header);
    }

    private void createGoalsSection(VBox mainContent) {
        VBox goalsSection = new VBox(15);
        goalsSection.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        goalsSection.setPadding(new Insets(20));
        
        HBox goalsHeader = new HBox(15);
        goalsHeader.setAlignment(Pos.CENTER_LEFT);
        
        Label goalsTitle = new Label("✅ Today's Goals");
        goalsTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        goalsTitle.setTextFill(Color.web("#2c3e50"));
        
        Button addGoalBtn = new Button("➕ Add Goal");
        addGoalBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 5;");
        addGoalBtn.setOnAction(e -> showAddGoalDialog());
        
        goalsHeader.getChildren().addAll(goalsTitle, new Region(), addGoalBtn);
        HBox.setHgrow(goalsHeader.getChildren().get(1), Priority.ALWAYS);
        
        goalsContainer = new VBox(10);
        
        goalsSection.getChildren().addAll(goalsHeader, goalsContainer);
        mainContent.getChildren().add(goalsSection);
    }

    private void createSessionSection(VBox mainContent) {
        VBox sessionSection = new VBox(15);
        sessionSection.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        sessionSection.setPadding(new Insets(20));
        
        Label sessionTitle = new Label("⏱️ Study Session");
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
                // Save session text before ending
                currentSession.setSessionText(sessionTextArea.getText());
                stopSessionTimer();
                showEndSessionDialog();
                sessionStatus.setText("No active session");
                startSessionBtn.setDisable(false);
                endSessionBtn.setDisable(true);
                sessionTextArea.setDisable(true);
                updateSessionsDisplay();
            }
        });
        
        sessionControls.getChildren().addAll(startSessionBtn, endSessionBtn, sessionStatus);
        
        // Session text area for writing during session
        sessionTextArea = new TextArea();
        sessionTextArea.setPromptText("Write your session notes, thoughts, or study content here...");
        sessionTextArea.setPrefRowCount(6);
        sessionTextArea.setWrapText(true);
        sessionTextArea.setDisable(true);
        
        // Simple current session container (no tabs)
        sessionsContainer = new VBox(10);
        ScrollPane sessionsScroll = new ScrollPane(sessionsContainer);
        sessionsScroll.setPrefHeight(200);
        sessionsScroll.setStyle("-fx-background-color: transparent;");
        
        Label todaySessionsLabel = new Label("📚 Today's Completed Sessions");
        todaySessionsLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        todaySessionsLabel.setTextFill(Color.web("#2c3e50"));
        
        sessionSection.getChildren().addAll(sessionTitle, sessionControls, sessionTextArea, todaySessionsLabel, sessionsScroll);
        mainContent.getChildren().add(sessionSection);
    }


    private void createReflectionSection(VBox mainContent) {
        VBox reflectionSection = new VBox(15);
        reflectionSection.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
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

    private void updateGoalsDisplay() {
        goalsContainer.getChildren().clear();
        List<StudyGoal> todayGoals = studyService.getTodayGoals();
        List<StudyGoal> delayedGoals = studyService.getAllDelayedGoals();
        
        // Combine today's goals with delayed goals (remove duplicates)
        List<StudyGoal> allGoals = new ArrayList<>(todayGoals);
        for (StudyGoal delayed : delayedGoals) {
            if (!todayGoals.contains(delayed)) {
                allGoals.add(delayed);
            }
        }
        
        for (StudyGoal goal : allGoals) {
            HBox goalItem = new HBox(10);
            goalItem.setAlignment(Pos.CENTER_LEFT);
            goalItem.setPadding(new Insets(8));
            
            // Style based on delay status
            String backgroundColor = "#f8f9fa"; // Default
            if (goal.isDelayed()) {
                double intensity = goal.getDelayColorIntensity();
                if (intensity < 0.5) {
                    backgroundColor = "#fff3cd"; // Light orange
                } else {
                    backgroundColor = "#f8d7da"; // Light red
                }
            }
            goalItem.setStyle("-fx-background-color: " + backgroundColor + "; -fx-background-radius: 5;");
            
            CheckBox goalCheck = new CheckBox();
            goalCheck.setSelected(goal.isAchieved());
            goalCheck.setOnAction(e -> {
                studyService.updateStudyGoalAchievement(goal.getId(), goalCheck.isSelected(), null);
                updateProgress();
            });
            
            VBox goalTextBox = new VBox(2);
            
            Label goalLabel = new Label(goal.getDescription());
            goalLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
            if (goal.isAchieved()) {
                goalLabel.setStyle("-fx-strikethrough: true; -fx-text-fill: #7f8c8d;");
            }
            
            goalTextBox.getChildren().add(goalLabel);
            
            // Show delay information if applicable
            if (goal.isDelayed()) {
                Label delayLabel = new Label(String.format("Delayed %d day(s) - Penalty: %d points", 
                    goal.getDaysDelayed(), goal.getPointsDeducted()));
                delayLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
                delayLabel.setStyle("-fx-text-fill: #dc3545;"); // Red text
                goalTextBox.getChildren().add(delayLabel);
            }
            
            // Show linked task if any
            if (goal.getTaskId() != null) {
                try {
                    Optional<Task> linkedTask = Task.findById(goal.getTaskId());
                    if (linkedTask.isPresent()) {
                        Label taskLabel = new Label("📋 Linked to: " + linkedTask.get().getTitle());
                        taskLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
                        taskLabel.setTextFill(javafx.scene.paint.Color.web("#6c757d"));
                        goalTextBox.getChildren().add(taskLabel);
                    }
                } catch (Exception e) {
                    // Silently ignore task loading errors
                }
            }
            
            goalItem.getChildren().addAll(goalCheck, goalTextBox);
            goalsContainer.getChildren().add(goalItem);
        }
    }

    private void updateSessionsDisplay() {
        sessionsContainer.getChildren().clear();
        List<StudySession> todaySessions = studyService.getTodaySessions();
        
        for (StudySession session : todaySessions) {
            if (session.isCompleted()) {
                HBox sessionItem = createSessionItem(session);
                sessionsContainer.getChildren().add(sessionItem);
            }
        }
    }
    
    private HBox createSessionItem(StudySession session) {
        HBox sessionItem = new HBox(15);
        sessionItem.setAlignment(Pos.CENTER_LEFT);
        sessionItem.setPadding(new Insets(12));
        sessionItem.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 3, 0, 0, 1);");
        
        // Left side - Session info
        VBox sessionInfo = new VBox(6);
        sessionInfo.setPrefWidth(300);
        
        // Header with time and duration
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        String startTimeStr = session.getStartTime() != null ? 
            session.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "N/A";
        String endTimeStr = session.getEndTime() != null ? 
            session.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "N/A";
        
        Label timeLabel = new Label(String.format("⏰ %s - %s", startTimeStr, endTimeStr));
        timeLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        timeLabel.setTextFill(Color.web("#2c3e50"));
        
        Label durationLabel = new Label("(" + session.getDurationMinutes() + " min)");
        durationLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
        durationLabel.setTextFill(Color.web("#7f8c8d"));
        
        headerBox.getChildren().addAll(timeLabel, durationLabel);
        
        // Progress metrics row
        HBox metricsBox = new HBox(20);
        metricsBox.setAlignment(Pos.CENTER_LEFT);
        
        // Focus level with stars
        HBox focusBox = new HBox(3);
        focusBox.setAlignment(Pos.CENTER_LEFT);
        Label focusIconLabel = new Label("🎯");
        focusIconLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
        
        HBox starsBox = new HBox(1);
        for (int i = 1; i <= 5; i++) {
            Label star = new Label(i <= session.getFocusLevel() ? "★" : "☆");
            star.setFont(Font.font("System", FontWeight.NORMAL, 12));
            star.setTextFill(i <= session.getFocusLevel() ? Color.web("#f39c12") : Color.web("#bdc3c7"));
            starsBox.getChildren().add(star);
        }
        
        Label focusValue = new Label("(" + session.getFocusLevel() + "/5)");
        focusValue.setFont(Font.font("System", FontWeight.NORMAL, 11));
        focusValue.setTextFill(Color.web("#7f8c8d"));
        
        focusBox.getChildren().addAll(focusIconLabel, starsBox, focusValue);
        
        // Points earned with penalty indication
        VBox pointsContainer = new VBox(2);
        Label pointsLabel = new Label("🏆 " + session.getPointsEarned() + " pts");
        pointsLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));
        
        // Show penalty warning for low focus
        if (session.getFocusLevel() <= 2) {
            pointsLabel.setTextFill(Color.web("#e74c3c")); // Red for penalties
            Label penaltyWarning = new Label("⚠️ Focus penalty applied");
            penaltyWarning.setFont(Font.font("System", FontWeight.NORMAL, 10));
            penaltyWarning.setTextFill(Color.web("#e74c3c"));
            pointsContainer.getChildren().addAll(pointsLabel, penaltyWarning);
        } else {
            pointsLabel.setTextFill(Color.web("#27ae60")); // Green for normal
            pointsContainer.getChildren().add(pointsLabel);
        }
        
        // Efficiency indicator
        double efficiency = (double) session.getPointsEarned() / Math.max(1, session.getDurationMinutes()) * 60;
        String efficiencyIcon = efficiency >= 40 ? "🌟" : efficiency >= 20 ? "⭐" : "📈";
        Label efficiencyLabel = new Label(efficiencyIcon + " " + String.format("%.1f pts/hr", efficiency));
        efficiencyLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
        efficiencyLabel.setTextFill(Color.web("#8e44ad"));
        
        metricsBox.getChildren().addAll(focusBox, pointsContainer, efficiencyLabel);
        
        // Notes preview (if available)
        if (session.getSessionText() != null && !session.getSessionText().trim().isEmpty()) {
            String preview = session.getSessionText().length() > 80 ? 
                           session.getSessionText().substring(0, 80) + "..." : 
                           session.getSessionText();
            Label notesLabel = new Label("📝 " + preview);
            notesLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
            notesLabel.setTextFill(Color.web("#6c757d"));
            notesLabel.setWrapText(true);
            sessionInfo.getChildren().add(notesLabel);
        }
        
        sessionInfo.getChildren().addAll(headerBox, metricsBox);
        
        // Buttons for actions
        HBox buttons = new HBox(5);
        
        Button viewDetailsBtn = new Button("📊 View Details");
        viewDetailsBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 3; -fx-font-size: 10px;");
        viewDetailsBtn.setOnAction(e -> showSessionDetails(session));
        
        Button deleteBtn = new Button("Delete");
        deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 3; -fx-font-size: 10px;");
        deleteBtn.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Delete Session");
            alert.setHeaderText("Are you sure you want to delete this session?");
            alert.setContentText("This action cannot be undone.");
            
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    studyService.deleteStudySession(session.getId());
                    updateSessionsDisplay();
                }
            });
        });
        
        buttons.getChildren().addAll(viewDetailsBtn, deleteBtn);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        sessionItem.getChildren().addAll(sessionInfo, spacer, buttons);
        return sessionItem;
    }
    
    private void showSessionDetails(StudySession session) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("📊 Study Session Details");
        dialog.setHeaderText(null);
        
        // Create comprehensive session details layout
        VBox mainContent = new VBox(20);
        mainContent.setPadding(new Insets(20));
        mainContent.setPrefWidth(700);
        mainContent.setPrefHeight(600);
        
        // Session Header with key metrics
        createSessionHeader(session, mainContent);
        
        // Tab pane for different sections
        TabPane detailsTabs = new TabPane();
        detailsTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // Overview Tab
        Tab overviewTab = new Tab("📈 Overview", createOverviewTab(session));
        
        // Progress Tab  
        Tab progressTab = new Tab("✅ Progress", createProgressTab(session));
        
        // Notes Tab
        Tab notesTab = new Tab("📝 Notes", createNotesTab(session));
        
        // Performance Tab
        Tab performanceTab = new Tab("⚡ Performance", createPerformanceTab(session));
        
        detailsTabs.getTabs().addAll(overviewTab, progressTab, notesTab, performanceTab);
        
        mainContent.getChildren().add(detailsTabs);
        
        ScrollPane scrollPane = new ScrollPane(mainContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        
        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        
        dialog.showAndWait();
    }
    
    private void createSessionHeader(StudySession session, VBox mainContent) {
        VBox header = new VBox(10);
        header.setStyle("-fx-background-color: linear-gradient(to right, #667eea, #764ba2); -fx-background-radius: 10; -fx-padding: 20;");
        
        Label sessionTitle = new Label("📚 Study Session");
        sessionTitle.setFont(Font.font("System", FontWeight.BOLD, 24));
        sessionTitle.setTextFill(Color.WHITE);
        
        Label dateTime = new Label(session.getDate().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")) + 
                                  " • " + (session.getStartTime() != null ? 
                                  session.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "N/A"));
        dateTime.setFont(Font.font("System", FontWeight.NORMAL, 16));
        dateTime.setTextFill(Color.web("#f8f9fa"));
        
        // Key metrics in a horizontal layout
        HBox metricsBox = new HBox(30);
        metricsBox.setAlignment(Pos.CENTER_LEFT);
        
        VBox durationBox = createMetricBox("⏱️", session.getDurationMinutes() + " min", "Duration");
        VBox focusBox = createMetricBox("🎯", "★".repeat(session.getFocusLevel()) + "☆".repeat(5 - session.getFocusLevel()), "Focus Level");
        VBox pointsBox = createMetricBox("🏆", session.getPointsEarned() + " pts", "Points Earned");
        
        metricsBox.getChildren().addAll(durationBox, focusBox, pointsBox);
        
        header.getChildren().addAll(sessionTitle, dateTime, metricsBox);
        mainContent.getChildren().add(header);
    }
    
    private VBox createMetricBox(String icon, String value, String label) {
        VBox box = new VBox(5);
        box.setAlignment(Pos.CENTER);
        
        Label iconLabel = new Label(icon);
        iconLabel.setFont(Font.font("System", FontWeight.NORMAL, 20));
        
        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        valueLabel.setTextFill(Color.WHITE);
        
        Label descLabel = new Label(label);
        descLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
        descLabel.setTextFill(Color.web("#f8f9fa"));
        
        box.getChildren().addAll(iconLabel, valueLabel, descLabel);
        return box;
    }
    
    private VBox createOverviewTab(StudySession session) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        // Time breakdown
        VBox timeSection = new VBox(10);
        timeSection.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 10; -fx-padding: 15;");
        
        Label timeTitle = new Label("⏰ Time Breakdown");
        timeTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        timeTitle.setTextFill(Color.web("#2c3e50"));
        
        GridPane timeGrid = new GridPane();
        timeGrid.setHgap(20);
        timeGrid.setVgap(10);
        
        String startTime = session.getStartTime() != null ? session.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : "N/A";
        String endTime = session.getEndTime() != null ? session.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : "N/A";
        
        timeGrid.add(new Label("Start Time:"), 0, 0);
        timeGrid.add(new Label(startTime), 1, 0);
        timeGrid.add(new Label("End Time:"), 0, 1);
        timeGrid.add(new Label(endTime), 1, 1);
        timeGrid.add(new Label("Duration:"), 0, 2);
        timeGrid.add(new Label(session.getDurationMinutes() + " minutes"), 1, 2);
        timeGrid.add(new Label("Session Date:"), 0, 3);
        timeGrid.add(new Label(session.getDate().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))), 1, 3);
        
        timeSection.getChildren().addAll(timeTitle, timeGrid);
        
        // Session status
        VBox statusSection = new VBox(10);
        statusSection.setStyle("-fx-background-color: #e8f5e8; -fx-background-radius: 10; -fx-padding: 15;");
        
        Label statusTitle = new Label("📊 Session Status");
        statusTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        statusTitle.setTextFill(Color.web("#2c3e50"));
        
        Label completedLabel = new Label("Status: " + (session.isCompleted() ? "✅ Completed" : "⏸️ Incomplete"));
        completedLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
        
        Label pointsLabel = new Label("Points Earned: 🏆 " + session.getPointsEarned());
        pointsLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
        
        statusSection.getChildren().addAll(statusTitle, completedLabel, pointsLabel);
        
        content.getChildren().addAll(timeSection, statusSection);
        return content;
    }
    
    private VBox createProgressTab(StudySession session) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        // Focus level visualization
        VBox focusSection = new VBox(15);
        focusSection.setStyle("-fx-background-color: #fff3cd; -fx-background-radius: 10; -fx-padding: 15;");
        
        Label focusTitle = new Label("🎯 Focus & Productivity");
        focusTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        focusTitle.setTextFill(Color.web("#2c3e50"));
        
        HBox focusDisplay = new HBox(10);
        focusDisplay.setAlignment(Pos.CENTER_LEFT);
        
        Label focusLabel = new Label("Focus Level:");
        focusLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
        
        // Star rating display
        HBox starsBox = new HBox(2);
        for (int i = 1; i <= 5; i++) {
            Label star = new Label(i <= session.getFocusLevel() ? "★" : "☆");
            star.setFont(Font.font("System", FontWeight.NORMAL, 20));
            star.setTextFill(i <= session.getFocusLevel() ? Color.web("#f39c12") : Color.web("#bdc3c7"));
            starsBox.getChildren().add(star);
        }
        
        Label focusText = new Label("(" + session.getFocusLevel() + "/5)");
        focusText.setFont(Font.font("System", FontWeight.NORMAL, 14));
        focusText.setTextFill(Color.web("#7f8c8d"));
        
        focusDisplay.getChildren().addAll(focusLabel, starsBox, focusText);
        
        // Progress bar for visual representation
        ProgressBar focusBar = new ProgressBar((double) session.getFocusLevel() / 5.0);
        focusBar.setPrefWidth(300);
        focusBar.setStyle("-fx-accent: #f39c12;");
        
        // Focus level interpretation
        Label focusInterpretation = new Label(getFocusInterpretation(session.getFocusLevel()));
        focusInterpretation.setFont(Font.font("System", FontWeight.NORMAL, 12));
        focusInterpretation.setTextFill(Color.web("#7f8c8d"));
        focusInterpretation.setWrapText(true);
        
        focusSection.getChildren().addAll(focusTitle, focusDisplay, focusBar, focusInterpretation);
        
        // Productivity metrics
        VBox productivitySection = new VBox(10);
        productivitySection.setStyle("-fx-background-color: #d1ecf1; -fx-background-radius: 10; -fx-padding: 15;");
        
        Label productivityTitle = new Label("📈 Productivity Metrics");
        productivityTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        productivityTitle.setTextFill(Color.web("#2c3e50"));
        
        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(20);
        metricsGrid.setVgap(8);
        
        double minutesPerPoint = session.getPointsEarned() > 0 ? (double) session.getDurationMinutes() / session.getPointsEarned() : 0;
        double pointsPerMinute = session.getDurationMinutes() > 0 ? (double) session.getPointsEarned() / session.getDurationMinutes() : 0;
        
        metricsGrid.add(new Label("Points per Minute:"), 0, 0);
        metricsGrid.add(new Label(String.format("%.2f", pointsPerMinute)), 1, 0);
        metricsGrid.add(new Label("Minutes per Point:"), 0, 1);
        metricsGrid.add(new Label(String.format("%.1f", minutesPerPoint)), 1, 1);
        metricsGrid.add(new Label("Efficiency Rating:"), 0, 2);
        metricsGrid.add(new Label(getEfficiencyRating(session)), 1, 2);
        
        productivitySection.getChildren().addAll(productivityTitle, metricsGrid);
        
        content.getChildren().addAll(focusSection, productivitySection);
        return content;
    }
    
    private VBox createNotesTab(StudySession session) {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        Label notesTitle = new Label("📝 Session Notes & Reflections");
        notesTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        notesTitle.setTextFill(Color.web("#2c3e50"));
        
        TextArea notesArea = new TextArea();
        String sessionText = session.getSessionText();
        notesArea.setText(sessionText != null && !sessionText.trim().isEmpty() ? 
                         sessionText : "No notes were recorded during this session.");
        notesArea.setEditable(false);
        notesArea.setPrefRowCount(20);
        notesArea.setWrapText(true);
        notesArea.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-radius: 5;");
        
        // Word count and character count
        if (sessionText != null && !sessionText.trim().isEmpty()) {
            String[] words = sessionText.trim().split("\\s+");
            int wordCount = words.length;
            int charCount = sessionText.length();
            
            Label statsLabel = new Label(String.format("📊 %d words • %d characters", wordCount, charCount));
            statsLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
            statsLabel.setTextFill(Color.web("#6c757d"));
            
            content.getChildren().addAll(notesTitle, notesArea, statsLabel);
        } else {
            content.getChildren().addAll(notesTitle, notesArea);
        }
        
        return content;
    }
    
    private VBox createPerformanceTab(StudySession session) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        // Session comparison with recent sessions
        VBox comparisonSection = new VBox(15);
        comparisonSection.setStyle("-fx-background-color: #f0f8ff; -fx-background-radius: 10; -fx-padding: 15;");
        
        Label comparisonTitle = new Label("📊 Performance Analysis");
        comparisonTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        comparisonTitle.setTextFill(Color.web("#2c3e50"));
        
        // Get recent sessions for comparison
        List<StudySession> recentSessions = studyService.getRecentStudySessions(7);
        if (recentSessions.size() > 1) {
            double avgDuration = recentSessions.stream().mapToInt(StudySession::getDurationMinutes).average().orElse(0);
            double avgFocus = recentSessions.stream().mapToInt(StudySession::getFocusLevel).average().orElse(0);
            double avgPoints = recentSessions.stream().mapToInt(StudySession::getPointsEarned).average().orElse(0);
            
            GridPane comparisonGrid = new GridPane();
            comparisonGrid.setHgap(30);
            comparisonGrid.setVgap(10);
            
            // Duration comparison
            String durationComparison = session.getDurationMinutes() > avgDuration ? "📈 Above average" : 
                                      session.getDurationMinutes() < avgDuration ? "📉 Below average" : "➡️ Average";
            
            // Focus comparison
            String focusComparison = session.getFocusLevel() > avgFocus ? "📈 Above average" : 
                                   session.getFocusLevel() < avgFocus ? "📉 Below average" : "➡️ Average";
            
            // Points comparison
            String pointsComparison = session.getPointsEarned() > avgPoints ? "📈 Above average" : 
                                    session.getPointsEarned() < avgPoints ? "📉 Below average" : "➡️ Average";
            
            comparisonGrid.add(new Label("Duration vs 7-day avg:"), 0, 0);
            comparisonGrid.add(new Label(String.format("%.0f min %s", avgDuration, durationComparison)), 1, 0);
            comparisonGrid.add(new Label("Focus vs 7-day avg:"), 0, 1);
            comparisonGrid.add(new Label(String.format("%.1f %s", avgFocus, focusComparison)), 1, 1);
            comparisonGrid.add(new Label("Points vs 7-day avg:"), 0, 2);
            comparisonGrid.add(new Label(String.format("%.0f pts %s", avgPoints, pointsComparison)), 1, 2);
            
            comparisonSection.getChildren().addAll(comparisonTitle, comparisonGrid);
        } else {
            Label noDataLabel = new Label("Not enough data for comparison (need at least 2 sessions)");
            noDataLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
            noDataLabel.setTextFill(Color.web("#6c757d"));
            comparisonSection.getChildren().addAll(comparisonTitle, noDataLabel);
        }
        
        // Recommendations
        VBox recommendationsSection = new VBox(10);
        recommendationsSection.setStyle("-fx-background-color: #f0fff0; -fx-background-radius: 10; -fx-padding: 15;");
        
        Label recommendationsTitle = new Label("💡 Recommendations");
        recommendationsTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        recommendationsTitle.setTextFill(Color.web("#2c3e50"));
        
        VBox recommendations = new VBox(5);
        getRecommendations(session).forEach(rec -> {
            Label recLabel = new Label("• " + rec);
            recLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
            recLabel.setWrapText(true);
            recommendations.getChildren().add(recLabel);
        });
        
        recommendationsSection.getChildren().addAll(recommendationsTitle, recommendations);
        
        content.getChildren().addAll(comparisonSection, recommendationsSection);
        return content;
    }
    
    private String getFocusInterpretation(int focusLevel) {
        switch (focusLevel) {
            case 1: return "Very distracted - Consider changing environment or taking a break";
            case 2: return "Somewhat distracted - Try removing distractions or using focus techniques";
            case 3: return "Moderate focus - Good baseline, room for improvement";
            case 4: return "High focus - Great concentration, maintain this level";
            case 5: return "Excellent focus - Outstanding concentration and productivity";
            default: return "Focus level not recorded";
        }
    }
    
    private String getEfficiencyRating(StudySession session) {
        double efficiency = (double) session.getPointsEarned() / Math.max(1, session.getDurationMinutes()) * 60; // points per hour
        if (efficiency >= 60) return "⭐⭐⭐Excellent";
        else if (efficiency >= 40) return "⭐ Good";
        else if (efficiency >= 20) return "👍 Fair";
        else return "📈 Needs Improvement";
    }
    
    private List<String> getRecommendations(StudySession session) {
        List<String> recommendations = new java.util.ArrayList<>();
        
        if (session.getFocusLevel() < 3) {
            recommendations.add("Try the Pomodoro technique to improve focus");
            recommendations.add("Consider changing your study environment");
        }
        
        if (session.getDurationMinutes() < 25) {
            recommendations.add("Aim for longer study sessions (25-50 minutes) for better deep work");
        } else if (session.getDurationMinutes() > 90) {
            recommendations.add("Consider breaking very long sessions into smaller chunks with breaks");
        }
        
        if (session.getSessionText() == null || session.getSessionText().trim().isEmpty()) {
            recommendations.add("Take notes during sessions to improve retention and tracking");
        }
        
        double efficiency = (double) session.getPointsEarned() / Math.max(1, session.getDurationMinutes());
        if (efficiency < 0.5) {
            recommendations.add("Focus on active learning techniques to improve efficiency");
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Great session! Keep up the good work!");
        }
        
        return recommendations;
    }

    private void showEndSessionDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("End Study Session");
        dialog.setHeaderText("How was your study session?");
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        Label focusLabel = new Label("Focus Level (1-5):");
        Slider focusSlider = new Slider(1, 5, 3);
        focusSlider.setShowTickLabels(true);
        focusSlider.setShowTickMarks(true);
        focusSlider.setMajorTickUnit(1);
        focusSlider.setSnapToTicks(true);
        
        // Dynamic focus warning label
        Label focusWarningLabel = new Label();
        focusWarningLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
        focusWarningLabel.setWrapText(true);
        
        // Update warning based on focus level
        focusSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int focusLevel = newVal.intValue();
            if (focusLevel <= 2) {
                focusWarningLabel.setText("⚠️ Low focus rating will result in point penalties. Consider what caused the distraction and how to improve next time.");
                focusWarningLabel.setTextFill(Color.web("#e74c3c"));
            } else if (focusLevel == 3) {
                focusWarningLabel.setText("💡 Average focus. Think about what could help you stay more concentrated.");
                focusWarningLabel.setTextFill(Color.web("#f39c12"));
            } else {
                focusWarningLabel.setText("✅ Great focus! You'll earn bonus points for staying concentrated.");
                focusWarningLabel.setTextFill(Color.web("#27ae60"));
            }
        });
        
        // Initialize warning for default value
        focusWarningLabel.setText("💡 Average focus. Think about what could help you stay more concentrated.");
        focusWarningLabel.setTextFill(Color.web("#f39c12"));
        
        Label notesLabel = new Label("Notes:");
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("What did you accomplish?");
        notesArea.setPrefRowCount(3);
        
        content.getChildren().addAll(focusLabel, focusSlider, focusWarningLabel, notesLabel, notesArea);
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        // Set result converter to return the button type
        dialog.setResultConverter(buttonType -> buttonType);
        
        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                StudySessionEnd sessionEnd = new StudySessionEnd((int) focusSlider.getValue(), notesArea.getText());
                studyService.endStudySession(currentSession, sessionEnd);
                currentSession = null;
            }
        });
    }

    private void saveReflection() {
        String reflectionText = reflectionArea.getText().trim();
        if (!reflectionText.isEmpty()) {
            DailyReflection reflection = new DailyReflection();
            reflection.setDate(LocalDate.now());
            reflection.setReflectionText(reflectionText);
            studyService.addDailyReflection(reflection);
            
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Reflection Saved");
            alert.setHeaderText(null);
            alert.setContentText("Your daily reflection has been saved!");
            alert.showAndWait();
        }
    }

    private void updateProgress() {
        int progress = studyService.calculateDailyProgress();
        dailyProgressBar.setProgress(progress / 100.0);
        progressLabel.setText("Daily Progress: " + progress + "%");
    }

    private void startSessionTimer(Label sessionStatus) {
        if (sessionTimer != null) {
            sessionTimer.stop();
        }
        
        sessionTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateSessionTimer(sessionStatus)));
        sessionTimer.setCycleCount(Timeline.INDEFINITE);
        sessionTimer.play();
    }
    
    private void stopSessionTimer() {
        if (sessionTimer != null) {
            sessionTimer.stop();
            sessionTimer = null;
        }
    }
    
    private void updateSessionTimer(Label sessionStatus) {
        if (currentSession != null && currentSession.isActive()) {
            currentSession.updateRealTimeProgress();
            int elapsedMinutes = currentSession.getCurrentElapsedMinutes();
            int hours = elapsedMinutes / 60;
            int minutes = elapsedMinutes % 60;
            
            String timeDisplay;
            if (hours > 0) {
                timeDisplay = String.format("⏱️ Session running: %dh %02dm", hours, minutes);
            } else {
                timeDisplay = String.format("⏱️ Session running: %dm", minutes);
            }
            
            sessionStatus.setText(timeDisplay);
            sessionStatus.setTextFill(Color.web("#27ae60"));
        } else {
            sessionStatus.setText("No active session");
            sessionStatus.setTextFill(Color.web("#2c3e50"));
        }
    }
    
    /**
     * Handle date change events (called when date changes at midnight).
     * @param newDate The new current date
     */
    private void onDateChanged(LocalDate newDate) {
        // Update the date label
        dateLabel.setText("📅 " + dateTimeService.getFormattedCurrentDate());
        
        // Refresh the entire display for the new day
        updateDisplay();
        
        System.out.println("Date changed to: " + newDate);
    }

    public void updateDisplay() {
        updateGoalsDisplay();
        updateSessionsDisplay();
        updateProgress();
        
        // Load today's reflection if it exists
        DailyReflection todayReflection = studyService.getTodayReflection().orElse(null);
        if (todayReflection != null) {
            reflectionArea.setText(todayReflection.getReflectionText());
        }
    }
    
    private void showAddGoalDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Study Goal");
        dialog.setHeaderText("Create a new daily study goal");
        
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        // Goal description
        Label descLabel = new Label("Goal Description:");
        descLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        TextArea goalTextArea = new TextArea();
        goalTextArea.setPromptText("e.g., Complete Chapter 5 of Java Programming, Practice 10 math problems, Review yesterday's notes...");
        goalTextArea.setPrefRowCount(3);
        goalTextArea.setWrapText(true);
        
        // Task selection (optional)
        Label taskLabel = new Label("Link to Task (optional):");
        taskLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        ComboBox<Task> taskComboBox = new ComboBox<>();
        taskComboBox.setPromptText("Select a task to link with this goal (optional)");
        taskComboBox.setPrefWidth(400);
        
        // Load active tasks
        try {
            List<Task> activeTasks = taskService.getActiveTasks();
            taskComboBox.getItems().add(null); // Add "None" option
            taskComboBox.getItems().addAll(activeTasks);
            
            // Custom cell factory to show task title and description
            taskComboBox.setCellFactory(param -> new ListCell<Task>() {
                @Override
                protected void updateItem(Task item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText("None (No task linked)");
                    } else {
                        String text = item.getTitle();
                        if (item.getDescription() != null && !item.getDescription().trim().isEmpty()) {
                            text += " - " + (item.getDescription().length() > 50 ? 
                                    item.getDescription().substring(0, 50) + "..." : 
                                    item.getDescription());
                        }
                        setText(text);
                    }
                }
            });
            
            taskComboBox.setButtonCell(new ListCell<Task>() {
                @Override
                protected void updateItem(Task item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText("None (No task linked)");
                    } else {
                        setText(item.getTitle());
                    }
                }
            });
            
        } catch (Exception ex) {
            Label errorLabel = new Label("Could not load tasks: " + ex.getMessage());
            errorLabel.setTextFill(javafx.scene.paint.Color.RED);
            content.getChildren().add(errorLabel);
        }
        
        content.getChildren().addAll(descLabel, goalTextArea, taskLabel, taskComboBox);
        dialogPane.setContent(content);
        
        // Enable/disable OK button based on input
        Button okButton = (Button) dialogPane.lookupButton(ButtonType.OK);
        okButton.setDisable(true);
        goalTextArea.textProperty().addListener((observable, oldValue, newValue) -> {
            okButton.setDisable(newValue.trim().isEmpty());
        });
        
        // Set result converter
        dialog.setResultConverter(dialogButton -> dialogButton);
        
        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                String goalDescription = goalTextArea.getText().trim();
                Task selectedTask = taskComboBox.getValue();
                
                if (!goalDescription.isEmpty()) {
                    try {
                        String taskId = selectedTask != null ? selectedTask.getId() : null;
                        studyService.addStudyGoal(goalDescription, dateTimeService.getCurrentDate(), taskId);
                        updateGoalsDisplay();
                        updateProgress();
                        
                        Alert success = new Alert(Alert.AlertType.INFORMATION);
                        success.setTitle("Success");
                        success.setHeaderText(null);
                        String msg = "Study goal added successfully!";
                        if (selectedTask != null) {
                            msg += "\nLinked to task: " + selectedTask.getTitle();
                        }
                        success.setContentText(msg);
                        success.showAndWait();
                        
                    } catch (Exception e) {
                        Alert error = new Alert(Alert.AlertType.ERROR);
                        error.setTitle("Error");
                        error.setHeaderText(null);
                        error.setContentText("Failed to add study goal: " + e.getMessage());
                        error.showAndWait();
                    }
                }
            }
        });
    }
    
    @Override
    public Node getView() {
        return this;
    }
}
