package com.studysync.presentation.ui.components;

import com.studysync.domain.service.StudyService;
import com.studysync.domain.service.TaskService;
import com.studysync.domain.service.ProjectService;
import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.StudySession;
import com.studysync.domain.entity.Task;
import com.studysync.domain.entity.Project;
import com.studysync.domain.entity.ProjectSession;
import com.studysync.domain.entity.DailyReflection;
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
import java.util.Map;

public class DailyViewPanel extends ScrollPane implements RefreshablePanel {
    private final StudyService studyService;
    private final TaskService taskService;
    private final ProjectService projectService;
    
    // UI Components
    private DatePicker datePicker;
    private VBox dailyContentContainer;
    private Label selectedDateLabel;
    private Label dailyStatsLabel;
    
    public DailyViewPanel(StudyService studyService, TaskService taskService, ProjectService projectService) {
        this.studyService = studyService;
        this.taskService = taskService;
        this.projectService = projectService;
        
        // Create main content container
        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(20));
        mainContent.setStyle("-fx-background-color: linear-gradient(to bottom, #f1f2f6, #dfe4ea);");
        
        // Set up ScrollPane properties
        this.setContent(mainContent);
        this.setFitToWidth(true);
        this.setFitToHeight(true);
        this.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.getStyleClass().add("tab-content-area");
        
        initializeComponents(mainContent);
        updateDisplay();
    }

    private void initializeComponents(VBox mainContent) {
        // Header
        Label headerLabel = new Label("📅 Daily Session View");
        headerLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        headerLabel.setTextFill(Color.web("#2c3e50"));
        
        // Date selector section
        VBox dateSection = createDateSection();
        
        // Daily content section
        VBox contentSection = createContentSection();
        
        mainContent.getChildren().addAll(headerLabel, dateSection, contentSection);
    }
    
    private VBox createDateSection() {
        VBox dateSection = new VBox(10);
        dateSection.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 10;");
        
        Label dateLabel = new Label("Select Date to View:");
        dateLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        HBox dateControls = new HBox(15);
        dateControls.setAlignment(Pos.CENTER_LEFT);
        
        // Date picker
        datePicker = new DatePicker(LocalDate.now());
        datePicker.setOnAction(e -> updateDailyView());
        
        // Quick date buttons
        Button todayBtn = new Button("Today");
        todayBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        todayBtn.setOnAction(e -> {
            datePicker.setValue(LocalDate.now());
            updateDailyView();
        });
        
        Button yesterdayBtn = new Button("Yesterday");
        yesterdayBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white;");
        yesterdayBtn.setOnAction(e -> {
            datePicker.setValue(LocalDate.now().minusDays(1));
            updateDailyView();
        });
        
        Button lastWeekBtn = new Button("Last 7 Days");
        lastWeekBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
        lastWeekBtn.setOnAction(e -> showLastWeekView());
        
        dateControls.getChildren().addAll(datePicker, todayBtn, yesterdayBtn, lastWeekBtn);
        
        selectedDateLabel = new Label();
        selectedDateLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        selectedDateLabel.setTextFill(Color.web("#34495e"));
        
        dailyStatsLabel = new Label();
        dailyStatsLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
        dailyStatsLabel.setTextFill(Color.web("#7f8c8d"));
        
        dateSection.getChildren().addAll(dateLabel, dateControls, selectedDateLabel, dailyStatsLabel);
        return dateSection;
    }
    
    private VBox createContentSection() {
        VBox contentSection = new VBox(10);
        contentSection.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 10;");
        
        Label contentTitle = new Label("📊 Daily Activity Summary");
        contentTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        contentTitle.setTextFill(Color.web("#2c3e50"));
        
        dailyContentContainer = new VBox(10);
        
        ScrollPane scrollPane = new ScrollPane(dailyContentContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(400);
        scrollPane.setStyle("-fx-background-color: transparent;");
        
        contentSection.getChildren().addAll(contentTitle, scrollPane);
        return contentSection;
    }
    
    private void updateDailyView() {
        LocalDate selectedDate = datePicker.getValue();
        if (selectedDate == null) {
            selectedDate = LocalDate.now();
        }
        
        updateSelectedDateLabel(selectedDate);
        loadDailyContent(selectedDate);
    }
    
    private void updateSelectedDateLabel(LocalDate date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy");
        selectedDateLabel.setText("📅 " + date.format(formatter));
        
        // Update stats - with safety check for database initialization
        try {
            List<StudyGoal> studyGoals = studyService.getStudyGoalsForDate(date);
            List<StudySession> studySessions = studyService.getSessionsForDate(date);
            List<ProjectSession> projectSessions = projectService.getProjectSessionsForDate(date);
        
            int totalStudyTime = studySessions.stream().mapToInt(StudySession::getDurationMinutes).sum();
            int totalProjectTime = projectSessions.stream().mapToInt(ProjectSession::getDurationMinutes).sum();
            int totalPoints = studySessions.stream().mapToInt(StudySession::getPointsEarned).sum() +
                             projectSessions.stream().mapToInt(ProjectSession::getPointsEarned).sum();
            
            int achievedGoals = (int) studyGoals.stream().filter(StudyGoal::isAchieved).count();
            int totalGoals = studyGoals.size();
            
            String goalsText = totalGoals > 0 ? String.format("🎯 %d/%d goals achieved • ", achievedGoals, totalGoals) : "";
            
            dailyStatsLabel.setText(String.format("%s📈 %d sessions ⏰ %d min study + %d min project 🏆 %d points total", 
                    goalsText, studySessions.size() + projectSessions.size(), totalStudyTime, totalProjectTime, totalPoints));
        } catch (IllegalStateException e) {
            // Database not yet initialized - show basic date only
            dailyStatsLabel.setText("Loading statistics...");
        }
    }
    
    private void loadDailyContent(LocalDate date) {
        dailyContentContainer.getChildren().clear();
        
        try {
            // Load study goals for the date
            List<StudyGoal> studyGoals = studyService.getStudyGoalsForDate(date);
            
            // Load study sessions for the date
            List<StudySession> studySessions = studyService.getSessionsForDate(date);
            
            // Load project sessions for the date
            List<ProjectSession> projectSessions = projectService.getProjectSessionsForDate(date);
        
        if (studyGoals.isEmpty() && studySessions.isEmpty() && projectSessions.isEmpty()) {
            Label noDataLabel = new Label("📭 No goals or sessions recorded for this date");
            noDataLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
            noDataLabel.setTextFill(Color.GRAY);
            noDataLabel.setPadding(new Insets(20));
            dailyContentContainer.getChildren().add(noDataLabel);
            return;
        }
        
        // Study Goals Section
        HBox goalsSectionHeader = new HBox(15);
        goalsSectionHeader.setAlignment(Pos.CENTER_LEFT);
        
        Label goalsTitle = new Label("🎯 Daily Study Goals (" + studyGoals.size() + ")");
        goalsTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        goalsTitle.setTextFill(Color.web("#9b59b6"));
        
        Button addGoalBtn = new Button("➕ Add Goal");
        addGoalBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-font-size: 11px;");
        addGoalBtn.setOnAction(e -> showAddGoalDialog(date));
        
        goalsSectionHeader.getChildren().addAll(goalsTitle, addGoalBtn);
        dailyContentContainer.getChildren().add(goalsSectionHeader);
        
        if (!studyGoals.isEmpty()) {
            for (StudyGoal goal : studyGoals) {
                VBox goalBox = createStudyGoalBox(goal);
                dailyContentContainer.getChildren().add(goalBox);
            }
        } else {
            Label noGoalsLabel = new Label("No study goals set for this date. Click 'Add Goal' to create one.");
            noGoalsLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
            noGoalsLabel.setTextFill(Color.GRAY);
            noGoalsLabel.setPadding(new Insets(10, 0, 15, 0));
            dailyContentContainer.getChildren().add(noGoalsLabel);
        }
        
        // Study Sessions Section
        if (!studySessions.isEmpty()) {
            Label studyTitle = new Label("📚 Study Sessions (" + studySessions.size() + ")");
            studyTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
            studyTitle.setTextFill(Color.web("#3498db"));
            dailyContentContainer.getChildren().add(studyTitle);
            
            for (StudySession session : studySessions) {
                VBox sessionBox = createStudySessionBox(session);
                dailyContentContainer.getChildren().add(sessionBox);
            }
        }
        
        // Project Sessions Section
        if (!projectSessions.isEmpty()) {
            Label projectTitle = new Label("🚀 Project Sessions (" + projectSessions.size() + ")");
            projectTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
            projectTitle.setTextFill(Color.web("#e74c3c"));
            projectTitle.setPadding(new Insets(15, 0, 5, 0));
            dailyContentContainer.getChildren().add(projectTitle);
            
            for (ProjectSession session : projectSessions) {
                VBox sessionBox = createProjectSessionBox(session);
                dailyContentContainer.getChildren().add(sessionBox);
            }
        }
        } catch (IllegalStateException e) {
            // Database not yet initialized - show loading message
            Label loadingLabel = new Label("📡 Loading daily content...");
            loadingLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
            loadingLabel.setTextFill(Color.GRAY);
            loadingLabel.setPadding(new Insets(20));
            dailyContentContainer.getChildren().add(loadingLabel);
        }
    }
    
    private VBox createStudyGoalBox(StudyGoal goal) {
        VBox goalBox = new VBox(8);
        goalBox.setPadding(new Insets(12));
        
        // Dynamic styling based on goal state
        String backgroundColor;
        String borderColor;
        
        if (goal.isAchieved()) {
            backgroundColor = "#e8f5e8";
            borderColor = "#27ae60";
        } else if (goal.isDelayed()) {
            // Orange to red gradient based on delay intensity
            double intensity = goal.getDelayColorIntensity();
            if (intensity <= 0.3) {
                backgroundColor = "#fff3e0"; // Light orange
                borderColor = "#ff9800"; // Orange
            } else if (intensity <= 0.6) {
                backgroundColor = "#ffebee"; // Light red-orange
                borderColor = "#ff5722"; // Red-orange
            } else {
                backgroundColor = "#ffebee"; // Light red
                borderColor = "#f44336"; // Red
            }
        } else {
            backgroundColor = "#fff5f5";
            borderColor = "#e74c3c";
        }
        
        goalBox.setStyle("-fx-background-color: " + backgroundColor + "; -fx-background-radius: 8; -fx-border-color: " + borderColor + "; -fx-border-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 3, 0, 0, 1);");
        
        // Header with status
        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        String statusIcon;
        String statusText;
        Color statusColor;
        
        if (goal.isAchieved()) {
            statusIcon = "✅";
            statusText = "Achieved";
            statusColor = Color.web("#27ae60");
        } else if (goal.isDelayed()) {
            statusIcon = "⚠️";
            statusText = "Delayed (" + goal.getDaysDelayed() + " day" + (goal.getDaysDelayed() > 1 ? "s" : "") + ")";
            double intensity = goal.getDelayColorIntensity();
            if (intensity <= 0.3) {
                statusColor = Color.web("#ff9800"); // Orange
            } else if (intensity <= 0.6) {
                statusColor = Color.web("#ff5722"); // Red-orange
            } else {
                statusColor = Color.web("#f44336"); // Red
            }
        } else {
            statusIcon = "⭕";
            statusText = "Pending";
            statusColor = Color.web("#e74c3c");
        }
        
        Label statusLabel = new Label(statusIcon + " " + statusText);
        statusLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        statusLabel.setTextFill(statusColor);
        
        headerBox.getChildren().add(statusLabel);
        
        // Goal description
        Label descriptionLabel = new Label("🎯 " + goal.getDescription());
        descriptionLabel.setFont(Font.font("System", FontWeight.NORMAL, 13));
        descriptionLabel.setTextFill(Color.web("#2c3e50"));
        descriptionLabel.setWrapText(true);
        
        // Reason if not achieved
        VBox detailsBox = new VBox(4);
        detailsBox.getChildren().add(descriptionLabel);
        
        if (!goal.isAchieved() && goal.getReasonIfNotAchieved() != null && !goal.getReasonIfNotAchieved().trim().isEmpty()) {
            Label reasonLabel = new Label("❌ Reason not achieved: " + goal.getReasonIfNotAchieved());
            reasonLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
            reasonLabel.setTextFill(Color.web("#8e44ad"));
            reasonLabel.setWrapText(true);
            detailsBox.getChildren().add(reasonLabel);
        }
        
        // Delay information for delayed goals
        if (goal.isDelayed()) {
            String delayInfo = String.format("📅 Originally from: %s • 🔥 %d days delayed • ⚡ -%d points penalty", 
                goal.getOriginalDate().toString(), goal.getDaysDelayed(), goal.getPointsDeducted());
            Label delayLabel = new Label(delayInfo);
            delayLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
            delayLabel.setTextFill(Color.web("#ff5722"));
            delayLabel.setWrapText(true);
            detailsBox.getChildren().add(delayLabel);
        }
        
        // Action buttons
        HBox actionBox = new HBox(8);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        
        if (!goal.isAchieved()) {
            Button achieveBtn = new Button("✅ Mark Achieved");
            achieveBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 10px;");
            achieveBtn.setOnAction(e -> {
                studyService.updateStudyGoalAchievement(goal.getId(), true, null);
                updateDailyView(); // Refresh the view
            });
            actionBox.getChildren().add(achieveBtn);
        } else {
            Button unachieveBtn = new Button("⭕ Mark Pending");
            unachieveBtn.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-size: 10px;");
            unachieveBtn.setOnAction(e -> {
                studyService.updateStudyGoalAchievement(goal.getId(), false, null);
                updateDailyView(); // Refresh the view
            });
            actionBox.getChildren().add(unachieveBtn);
        }
        
        Button deleteBtn = new Button("🗑️ Delete");
        deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 10px;");
        deleteBtn.setOnAction(e -> deleteStudyGoal(goal));
        actionBox.getChildren().add(deleteBtn);
        
        goalBox.getChildren().addAll(headerBox, detailsBox, actionBox);
        return goalBox;
    }

    private VBox createStudySessionBox(StudySession session) {
        VBox sessionBox = new VBox(8);
        sessionBox.setPadding(new Insets(12));
        sessionBox.setStyle("-fx-background-color: #f8f9ff; -fx-background-radius: 8; -fx-border-color: #3498db; -fx-border-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 3, 0, 0, 1);");
        
        // Header with time and duration
        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        Label timeLabel = new Label("⏰ " + (session.getStartTime() != null ? 
            session.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "Unknown time"));
        timeLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        timeLabel.setTextFill(Color.web("#2c3e50"));
        
        Label durationLabel = new Label("⏱️ " + session.getDurationMinutes() + " min");
        durationLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
        durationLabel.setTextFill(Color.web("#3498db"));
        
        Label pointsLabel = new Label("🏆 " + session.getPointsEarned() + " pts");
        pointsLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 11));
        pointsLabel.setTextFill(Color.web("#27ae60"));
        
        headerBox.getChildren().addAll(timeLabel, durationLabel, pointsLabel);
        
        // Session details
        VBox detailsBox = new VBox(4);
        
        if (session.getSubject() != null && !session.getSubject().trim().isEmpty()) {
            Label subjectLabel = new Label("📖 Subject: " + session.getSubject());
            subjectLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
            subjectLabel.setTextFill(Color.web("#34495e"));
            detailsBox.getChildren().add(subjectLabel);
        }
        
        if (session.getTopic() != null && !session.getTopic().trim().isEmpty()) {
            Label topicLabel = new Label("📝 Topic: " + session.getTopic());
            topicLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
            topicLabel.setTextFill(Color.web("#34495e"));
            detailsBox.getChildren().add(topicLabel);
        }
        
        if (session.getFocusLevel() > 0) {
            String focusStars = "⭐".repeat(session.getFocusLevel());
            Label focusLabel = new Label("🎯 Focus: " + focusStars + " (" + session.getFocusLevel() + "/5)");
            focusLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
            focusLabel.setTextFill(Color.web("#f39c12"));
            detailsBox.getChildren().add(focusLabel);
        }
        
        if (session.getNotes() != null && !session.getNotes().trim().isEmpty()) {
            String notesPreview = session.getNotes().length() > 100 ? 
                                session.getNotes().substring(0, 100) + "..." : 
                                session.getNotes();
            Label notesLabel = new Label("💭 Notes: " + notesPreview);
            notesLabel.setFont(Font.font("System", FontWeight.NORMAL, 10));
            notesLabel.setTextFill(Color.web("#6c757d"));
            notesLabel.setWrapText(true);
            detailsBox.getChildren().add(notesLabel);
        }
        
        // Action buttons
        HBox actionBox = new HBox(8);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button viewBtn = new Button("👁️ View Details");
        viewBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 10px;");
        viewBtn.setOnAction(e -> showStudySessionDetails(session));
        
        Button deleteBtn = new Button("🗑️ Delete");
        deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 10px;");
        deleteBtn.setOnAction(e -> deleteStudySession(session));
        
        actionBox.getChildren().addAll(viewBtn, deleteBtn);
        
        sessionBox.getChildren().addAll(headerBox, detailsBox, actionBox);
        return sessionBox;
    }
    
    private VBox createProjectSessionBox(ProjectSession session) {
        VBox sessionBox = new VBox(8);
        sessionBox.setPadding(new Insets(12));
        sessionBox.setStyle("-fx-background-color: #fff8f8; -fx-background-radius: 8; -fx-border-color: #e74c3c; -fx-border-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 3, 0, 0, 1);");
        
        // Project info
        Project project = projectService.getProjectById(session.getProjectId()).orElse(null);
        String projectTitle = project != null ? project.getTitle() : "Unknown Project";
        
        // Header with project and time
        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        Label projectLabel = new Label("📁 " + projectTitle);
        projectLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        projectLabel.setTextFill(Color.web("#2c3e50"));
        
        Label timeLabel = new Label("⏰ " + (session.getStartTime() != null ? 
            session.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "Unknown time"));
        timeLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
        timeLabel.setTextFill(Color.web("#7f8c8d"));
        
        Label durationLabel = new Label("⏱️ " + session.getDurationMinutes() + " min");
        durationLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
        durationLabel.setTextFill(Color.web("#e74c3c"));
        
        Label pointsLabel = new Label("🏆 " + session.getPointsEarned() + " pts");
        pointsLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 11));
        pointsLabel.setTextFill(Color.web("#27ae60"));
        
        headerBox.getChildren().addAll(projectLabel, timeLabel, durationLabel, pointsLabel);
        
        // Session details
        VBox detailsBox = new VBox(4);
        
        if (session.getSessionTitle() != null && !session.getSessionTitle().trim().isEmpty()) {
            Label titleLabel = new Label("📝 Session: " + session.getSessionTitle());
            titleLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
            titleLabel.setTextFill(Color.web("#34495e"));
            detailsBox.getChildren().add(titleLabel);
        }
        
        if (session.getProgress() != null && !session.getProgress().trim().isEmpty()) {
            String progressPreview = session.getProgress().length() > 80 ? 
                                   session.getProgress().substring(0, 80) + "..." : 
                                   session.getProgress();
            Label progressLabel = new Label("✅ Progress: " + progressPreview);
            progressLabel.setFont(Font.font("System", FontWeight.NORMAL, 10));
            progressLabel.setTextFill(Color.web("#27ae60"));
            progressLabel.setWrapText(true);
            detailsBox.getChildren().add(progressLabel);
        }
        
        if (session.getNextSteps() != null && !session.getNextSteps().trim().isEmpty()) {
            String nextStepsPreview = session.getNextSteps().length() > 60 ? 
                                    session.getNextSteps().substring(0, 60) + "..." : 
                                    session.getNextSteps();
            Label nextStepsLabel = new Label("📋 Next: " + nextStepsPreview);
            nextStepsLabel.setFont(Font.font("System", FontWeight.NORMAL, 10));
            nextStepsLabel.setTextFill(Color.web("#8e44ad"));
            nextStepsLabel.setWrapText(true);
            detailsBox.getChildren().add(nextStepsLabel);
        }
        
        // Action buttons
        HBox actionBox = new HBox(8);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button viewBtn = new Button("👁️ View Details");
        viewBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 10px;");
        viewBtn.setOnAction(e -> showProjectSessionDetails(session));
        
        Button deleteBtn = new Button("🗑️ Delete");
        deleteBtn.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-size: 10px;");
        deleteBtn.setOnAction(e -> deleteProjectSession(session));
        
        actionBox.getChildren().addAll(viewBtn, deleteBtn);
        
        sessionBox.getChildren().addAll(headerBox, detailsBox, actionBox);
        return sessionBox;
    }
    
    private void showLastWeekView() {
        dailyContentContainer.getChildren().clear();
        
        LocalDate today = LocalDate.now();
        Map<LocalDate, List<StudySession>> weeklyStudySessions = studyService.getSessionsGroupedByDate(7);
        
        Label weekTitle = new Label("📊 Last 7 Days Overview");
        weekTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        weekTitle.setTextFill(Color.web("#2c3e50"));
        weekTitle.setPadding(new Insets(0, 0, 10, 0));
        dailyContentContainer.getChildren().add(weekTitle);
        
        for (Map.Entry<LocalDate, List<StudySession>> entry : weeklyStudySessions.entrySet()) {
            LocalDate date = entry.getKey();
            List<StudySession> sessions = entry.getValue();
            List<ProjectSession> projectSessions = projectService.getProjectSessionsForDate(date);
            
            VBox dayBox = new VBox(5);
            dayBox.setPadding(new Insets(10));
            dayBox.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5; -fx-border-color: #dee2e6; -fx-border-radius: 5;");
            
            String dayLabel = date.equals(today) ? "Today" : 
                            date.equals(today.minusDays(1)) ? "Yesterday" : 
                            date.format(DateTimeFormatter.ofPattern("EEEE, MMM dd"));
            
            Label dateLabel = new Label("📅 " + dayLabel);
            dateLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
            dateLabel.setTextFill(Color.web("#495057"));
            
            int totalMinutes = sessions.stream().mapToInt(StudySession::getDurationMinutes).sum() +
                             projectSessions.stream().mapToInt(ProjectSession::getDurationMinutes).sum();
            int totalPoints = sessions.stream().mapToInt(StudySession::getPointsEarned).sum() +
                            projectSessions.stream().mapToInt(ProjectSession::getPointsEarned).sum();
            
            Label statsLabel = new Label(String.format("📈 %d sessions • ⏰ %d min • 🏆 %d pts", 
                    sessions.size() + projectSessions.size(), totalMinutes, totalPoints));
            statsLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
            statsLabel.setTextFill(Color.web("#6c757d"));
            
            dayBox.getChildren().addAll(dateLabel, statsLabel);
            
            // Make clickable to jump to that date
            dayBox.setOnMouseClicked(e -> {
                datePicker.setValue(date);
                updateDailyView();
            });
            dayBox.setStyle(dayBox.getStyle() + "; -fx-cursor: hand;");
            
            dailyContentContainer.getChildren().add(dayBox);
        }
    }
    
    private void showStudySessionDetails(StudySession session) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Study Session Details");
        dialog.setHeaderText("📚 Study Session from " + session.getDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        
        // Session info in a structured way
        if (session.getStartTime() != null) {
            content.getChildren().add(new Label("Time: " + session.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) + 
                (session.getEndTime() != null ? " - " + session.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "")));
        }
        content.getChildren().add(new Label("⏱️ Duration: " + session.getDurationMinutes() + " minutes"));
        content.getChildren().add(new Label("🏆 Points: " + session.getPointsEarned()));
        
        if (session.getSubject() != null && !session.getSubject().trim().isEmpty()) {
            content.getChildren().add(new Label("Subject: " + session.getSubject()));
        }
        if (session.getTopic() != null && !session.getTopic().trim().isEmpty()) {
            content.getChildren().add(new Label("Topic: " + session.getTopic()));
        }
        if (session.getFocusLevel() > 0) {
            content.getChildren().add(new Label("Focus Level: " + "⭐".repeat(session.getFocusLevel()) + " (" + session.getFocusLevel() + "/5)"));
        }
        if (session.getNotes() != null && !session.getNotes().trim().isEmpty()) {
            Label notesLabel = new Label("Notes:");
            notesLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
            TextArea notesArea = new TextArea(session.getNotes());
            notesArea.setEditable(false);
            notesArea.setPrefRowCount(3);
            content.getChildren().addAll(notesLabel, notesArea);
        }
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        
        dialog.showAndWait();
    }
    
    private void showProjectSessionDetails(ProjectSession session) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Project Session Details");
        dialog.setHeaderText("Project Session from " + session.getDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // Overview Tab
        Tab overviewTab = new Tab("📈 Overview");
        VBox overviewContent = new VBox(10);
        overviewContent.setPadding(new Insets(15));
        
        Project project = projectService.getProjectById(session.getProjectId()).orElse(null);
        if (project != null) {
            overviewContent.getChildren().add(new Label("Project: " + project.getTitle()));
        }
        if (session.getSessionTitle() != null && !session.getSessionTitle().trim().isEmpty()) {
            overviewContent.getChildren().add(new Label("Session: " + session.getSessionTitle()));
        }
        if (session.getStartTime() != null) {
            overviewContent.getChildren().add(new Label("Time: " + session.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) + 
                (session.getEndTime() != null ? " - " + session.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "")));
        }
        overviewContent.getChildren().add(new Label("⏱️ Duration: " + session.getDurationMinutes() + " minutes"));
        overviewContent.getChildren().add(new Label("🏆 Points: " + session.getPointsEarned()));
        
        overviewTab.setContent(new ScrollPane(overviewContent));
        
        // Progress Tab
        Tab progressTab = new Tab("✅ Progress");
        VBox progressContent = new VBox(10);
        progressContent.setPadding(new Insets(15));
        
        if (session.getObjectives() != null && !session.getObjectives().trim().isEmpty()) {
            Label objLabel = new Label("Objectives:");
            objLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
            TextArea objArea = new TextArea(session.getObjectives());
            objArea.setEditable(false);
            objArea.setPrefRowCount(2);
            progressContent.getChildren().addAll(objLabel, objArea);
        }
        
        if (session.getProgress() != null && !session.getProgress().trim().isEmpty()) {
            Label progLabel = new Label("✅ Progress Made:");
            progLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
            TextArea progArea = new TextArea(session.getProgress());
            progArea.setEditable(false);
            progArea.setPrefRowCount(3);
            progressContent.getChildren().addAll(progLabel, progArea);
        }
        
        if (session.getNextSteps() != null && !session.getNextSteps().trim().isEmpty()) {
            Label nextLabel = new Label("📋 Next Steps:");
            nextLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
            TextArea nextArea = new TextArea(session.getNextSteps());
            nextArea.setEditable(false);
            nextArea.setPrefRowCount(2);
            progressContent.getChildren().addAll(nextLabel, nextArea);
        }
        
        progressTab.setContent(new ScrollPane(progressContent));
        
        // Challenges Tab
        Tab challengesTab = new Tab("⚠️ Challenges");
        VBox challengesContent = new VBox(10);
        challengesContent.setPadding(new Insets(15));
        
        if (session.getChallenges() != null && !session.getChallenges().trim().isEmpty()) {
            Label chalLabel = new Label("⚠️ Challenges:");
            chalLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
            TextArea chalArea = new TextArea(session.getChallenges());
            chalArea.setEditable(false);
            chalArea.setPrefRowCount(3);
            challengesContent.getChildren().addAll(chalLabel, chalArea);
        } else {
            challengesContent.getChildren().add(new Label("✅ No challenges reported for this session"));
        }
        
        if (session.getNotes() != null && !session.getNotes().trim().isEmpty()) {
            Label notesLabel = new Label("Additional Notes:");
            notesLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
            TextArea notesArea = new TextArea(session.getNotes());
            notesArea.setEditable(false);
            notesArea.setPrefRowCount(2);
            challengesContent.getChildren().addAll(notesLabel, notesArea);
        }
        
        challengesTab.setContent(new ScrollPane(challengesContent));
        
        tabPane.getTabs().addAll(overviewTab, progressTab, challengesTab);
        
        dialog.getDialogPane().setContent(tabPane);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefSize(600, 400);
        
        dialog.showAndWait();
    }
    
    private void deleteStudySession(StudySession session) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete Study Session");
        confirmation.setHeaderText("Are you sure you want to delete this study session?");
        confirmation.setContentText("This action cannot be undone.");
        
        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                studyService.deleteStudySession(session.getId());
                boolean deleted = true;
                if (deleted) {
                    updateDailyView(); // Refresh the view
                    showAlert("Success", "Study session deleted successfully!");
                } else {
                    showAlert("Error", "Failed to delete study session.");
                }
            }
        });
    }
    
    private void deleteProjectSession(ProjectSession session) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete Project Session");
        confirmation.setHeaderText("Are you sure you want to delete this project session?");
        confirmation.setContentText("This action cannot be undone.");
        
        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                projectService.deleteProjectSession(session.getId());
                boolean deleted = true;
                if (deleted) {
                    updateDailyView(); // Refresh the view
                    showAlert("Success", "Project session deleted successfully!");
                } else {
                    showAlert("Error", "Failed to delete project session.");
                }
            }
        });
    }
    
    private void deleteStudyGoal(StudyGoal goal) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete Study Goal");
        confirmation.setHeaderText("Are you sure you want to delete this study goal?");
        confirmation.setContentText("Goal: " + goal.getDescription() + "\n\nThis action cannot be undone.");
        
        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                studyService.deleteStudyGoal(goal.getId());
                boolean deleted = true;
                if (deleted) {
                    updateDailyView(); // Refresh the view
                    showAlert("Success", "Study goal deleted successfully!");
                } else {
                    showAlert("Error", "Failed to delete study goal.");
                }
            }
        });
    }
    
    private void showAddGoalDialog(LocalDate date) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Add Study Goal");
        dialog.setHeaderText("Add a new study goal for " + date.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")));
        
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        
        Label instructionLabel = new Label("Enter your study goal for this date:");
        instructionLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
        
        TextArea goalTextArea = new TextArea();
        goalTextArea.setPromptText("e.g., Complete Chapter 5 of Java Programming, Practice 10 math problems, Review yesterday's notes...");
        goalTextArea.setPrefRowCount(3);
        goalTextArea.setWrapText(true);
        
        content.getChildren().addAll(instructionLabel, goalTextArea);
        dialogPane.setContent(content);
        
        // Enable/disable OK button based on input
        Button okButton = (Button) dialogPane.lookupButton(ButtonType.OK);
        okButton.setDisable(true);
        goalTextArea.textProperty().addListener((observable, oldValue, newValue) -> {
            okButton.setDisable(newValue.trim().isEmpty());
        });
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return goalTextArea.getText().trim();
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(goalDescription -> {
            if (!goalDescription.isEmpty()) {
                try {
                    studyService.addStudyGoal(goalDescription, date);
                    updateDailyView(); // Refresh the view
                    showAlert("Success", "Study goal added successfully!");
                } catch (Exception e) {
                    showAlert("Error", "Failed to add study goal: " + e.getMessage());
                }
            }
        });
    }
    
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    public void updateDisplay() {
        updateDailyView();
    }
    
    @Override
    public Node getView() {
        return this;
    }
}
