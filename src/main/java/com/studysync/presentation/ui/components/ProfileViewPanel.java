package com.studysync.presentation.ui.components;

import com.studysync.domain.service.StudyService;
import com.studysync.domain.service.ProjectService;
import com.studysync.domain.service.TaskService;
import com.studysync.domain.service.DateTimeService;
import com.studysync.domain.entity.StudySession;
import com.studysync.domain.entity.StudyGoal;
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
import javafx.scene.text.FontPosture;
import javafx.scene.chart.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Profile view panel showing comprehensive user analytics including focus data,
 * productivity metrics, and visual graphs for self-assessment and improvement.
 */
public class ProfileViewPanel extends ScrollPane implements RefreshablePanel {
    private final StudyService studyService;
    private final ProjectService projectService;
    private final TaskService taskService;
    private final DateTimeService dateTimeService;
    
    // UI Components for dynamic updates
    private VBox statsContainer;
    private VBox chartsContainer;
    private Label profileSummaryLabel;
    private ProgressBar productivityRating;
    private Label productivityLabel;
    
    public ProfileViewPanel(StudyService studyService, ProjectService projectService, 
                           TaskService taskService, DateTimeService dateTimeService) {
        this.studyService = studyService;
        this.projectService = projectService;
        this.taskService = taskService;
        this.dateTimeService = dateTimeService;
        
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
        updateDisplay();
    }

    private void initializeComponents(VBox mainContent) {
        // Header
        Label headerLabel = new Label("👤 Study Profile & Analytics");
        headerLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        headerLabel.setTextFill(Color.web("#2c3e50"));
        
        // Profile summary
        VBox profileSection = createProfileSummarySection();
        
        // Statistics cards
        HBox statsSection = createStatsSection();
        
        // Achieved goals section
        VBox achievedGoalsSection = createAchievedGoalsSection();
        
        // Charts section
        VBox chartsSection = createChartsSection();
        
        mainContent.getChildren().addAll(headerLabel, profileSection, statsSection, achievedGoalsSection, chartsSection);
    }
    
    private VBox createProfileSummarySection() {
        VBox section = new VBox(15);
        section.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-padding: 20; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        
        Label sectionTitle = new Label("📊 Overall Performance");
        sectionTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        sectionTitle.setTextFill(Color.web("#2c3e50"));
        
        // Profile summary with dynamic content
        profileSummaryLabel = new Label();
        profileSummaryLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
        profileSummaryLabel.setTextFill(Color.web("#34495e"));
        profileSummaryLabel.setWrapText(true);
        
        // Productivity rating bar
        VBox productivityBox = new VBox(8);
        Label productivityTitle = new Label("Overall Productivity Rating:");
        productivityTitle.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));
        
        productivityRating = new ProgressBar(0.75); // Will be updated dynamically
        productivityRating.setPrefWidth(300);
        productivityRating.setPrefHeight(20);
        productivityRating.setStyle("-fx-accent: #3498db;");
        
        productivityLabel = new Label("Good (75%)");
        productivityLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        productivityLabel.setTextFill(Color.web("#3498db"));
        
        productivityBox.getChildren().addAll(productivityTitle, productivityRating, productivityLabel);
        
        section.getChildren().addAll(sectionTitle, profileSummaryLabel, productivityBox);
        return section;
    }
    
    private HBox createStatsSection() {
        HBox section = new HBox(15);
        section.setAlignment(Pos.CENTER);
        
        statsContainer = new VBox(15);
        HBox.setHgrow(statsContainer, Priority.ALWAYS);
        
        section.getChildren().add(statsContainer);
        return section;
    }
    
    private VBox createAchievedGoalsSection() {
        VBox section = new VBox(15);
        section.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-padding: 20; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label sectionTitle = new Label("🏆 Achieved Goals");
        sectionTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        sectionTitle.setTextFill(Color.web("#2c3e50"));
        
        // Get recent achieved goals count
        List<StudyGoal> allAchievedGoals = studyService.getStudyGoals().stream()
            .filter(StudyGoal::isAchieved)
            .collect(Collectors.toList());
        
        Label countLabel = new Label("(" + allAchievedGoals.size() + " total)");
        countLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
        countLabel.setTextFill(Color.web("#7f8c8d"));
        
        Button viewAllBtn = new Button("📋 View All Achieved Goals");
        viewAllBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-background-radius: 5;");
        viewAllBtn.setOnAction(e -> showAllAchievedGoalsDialog());
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        header.getChildren().addAll(sectionTitle, countLabel, spacer, viewAllBtn);
        
        // Show recent achieved goals (last 5)
        VBox recentGoalsContainer = new VBox(8);
        List<StudyGoal> recentAchievedGoals = allAchievedGoals.stream()
            .sorted((g1, g2) -> g2.getDate().compareTo(g1.getDate())) // Sort by date descending
            .limit(5)
            .collect(Collectors.toList());
        
        if (recentAchievedGoals.isEmpty()) {
            Label noGoalsLabel = new Label("No goals achieved yet. Start setting and completing goals to see them here!");
            noGoalsLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
            noGoalsLabel.setTextFill(Color.web("#7f8c8d"));
            noGoalsLabel.setPadding(new Insets(10, 0, 0, 0));
            recentGoalsContainer.getChildren().add(noGoalsLabel);
        } else {
            Label recentLabel = new Label("Recent Achievements:");
            recentLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));
            recentLabel.setTextFill(Color.web("#2c3e50"));
            recentGoalsContainer.getChildren().add(recentLabel);
            
            for (StudyGoal goal : recentAchievedGoals) {
                HBox goalItem = createAchievedGoalItem(goal);
                recentGoalsContainer.getChildren().add(goalItem);
            }
            
            if (allAchievedGoals.size() > 5) {
                Label moreLabel = new Label("... and " + (allAchievedGoals.size() - 5) + " more. Click 'View All' to see them.");
                moreLabel.setFont(Font.font("System", FontPosture.ITALIC, 11));
                moreLabel.setTextFill(Color.web("#95a5a6"));
                recentGoalsContainer.getChildren().add(moreLabel);
            }
        }
        
        section.getChildren().addAll(header, recentGoalsContainer);
        return section;
    }
    
    private HBox createAchievedGoalItem(StudyGoal goal) {
        HBox item = new HBox(10);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(8, 12, 8, 12));
        item.setStyle("-fx-background-color: #e8f5e8; -fx-background-radius: 5; -fx-border-color: #27ae60; -fx-border-radius: 5;");
        
        Label checkLabel = new Label("✅");
        checkLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
        
        VBox textContainer = new VBox(2);
        
        Label descLabel = new Label(goal.getDescription());
        descLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
        descLabel.setTextFill(Color.web("#2c3e50"));
        descLabel.setWrapText(true);
        
        Label dateLabel = new Label("Achieved on " + goal.getDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        dateLabel.setFont(Font.font("System", FontWeight.NORMAL, 10));
        dateLabel.setTextFill(Color.web("#7f8c8d"));
        
        textContainer.getChildren().addAll(descLabel, dateLabel);
        
        item.getChildren().addAll(checkLabel, textContainer);
        return item;
    }
    
    private void showAllAchievedGoalsDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("All Achieved Goals");
        dialog.setHeaderText("🏆 Your Achievement History");
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setPrefWidth(600);
        content.setPrefHeight(500);
        
        // Get all achieved goals sorted by date (most recent first)
        List<StudyGoal> allAchievedGoals = studyService.getStudyGoals().stream()
            .filter(StudyGoal::isAchieved)
            .sorted((g1, g2) -> g2.getDate().compareTo(g1.getDate()))
            .collect(Collectors.toList());
        
        if (allAchievedGoals.isEmpty()) {
            Label noGoalsLabel = new Label("You haven't achieved any goals yet.\n\nStart by setting daily study goals and completing them to build your achievement history!");
            noGoalsLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
            noGoalsLabel.setTextFill(Color.web("#7f8c8d"));
            noGoalsLabel.setWrapText(true);
            noGoalsLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            noGoalsLabel.setPadding(new Insets(50, 20, 50, 20));
            content.getChildren().add(noGoalsLabel);
        } else {
            Label totalLabel = new Label("Total Achieved Goals: " + allAchievedGoals.size());
            totalLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
            totalLabel.setTextFill(Color.web("#27ae60"));
            content.getChildren().add(totalLabel);
            
            ScrollPane scrollPane = new ScrollPane();
            VBox goalsContainer = new VBox(8);
            goalsContainer.setPadding(new Insets(10));
            
            // Group goals by month for better organization
            Map<String, List<StudyGoal>> goalsByMonth = allAchievedGoals.stream()
                .collect(Collectors.groupingBy(goal -> 
                    goal.getDate().format(DateTimeFormatter.ofPattern("MMMM yyyy"))));
            
            for (Map.Entry<String, List<StudyGoal>> monthEntry : goalsByMonth.entrySet()) {
                // Month header
                Label monthLabel = new Label("📅 " + monthEntry.getKey());
                monthLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
                monthLabel.setTextFill(Color.web("#2c3e50"));
                monthLabel.setPadding(new Insets(15, 0, 5, 0));
                goalsContainer.getChildren().add(monthLabel);
                
                // Goals for this month
                for (StudyGoal goal : monthEntry.getValue()) {
                    HBox goalItem = createDetailedAchievedGoalItem(goal);
                    goalsContainer.getChildren().add(goalItem);
                }
            }
            
            scrollPane.setContent(goalsContainer);
            scrollPane.setFitToWidth(true);
            scrollPane.setPrefHeight(400);
            content.getChildren().add(scrollPane);
        }
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);
        
        dialog.showAndWait();
    }
    
    private HBox createDetailedAchievedGoalItem(StudyGoal goal) {
        HBox item = new HBox(12);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(10, 15, 10, 15));
        item.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 8; -fx-border-color: #27ae60; -fx-border-radius: 8; -fx-border-width: 1;");
        
        Label checkLabel = new Label("✅");
        checkLabel.setFont(Font.font("System", FontWeight.NORMAL, 16));
        
        VBox textContainer = new VBox(4);
        HBox.setHgrow(textContainer, Priority.ALWAYS);
        
        Label descLabel = new Label(goal.getDescription());
        descLabel.setFont(Font.font("System", FontWeight.NORMAL, 13));
        descLabel.setTextFill(Color.web("#2c3e50"));
        descLabel.setWrapText(true);
        
        HBox detailsBox = new HBox(15);
        detailsBox.setAlignment(Pos.CENTER_LEFT);
        
        Label dateLabel = new Label("📅 " + goal.getDate().format(DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy")));
        dateLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
        dateLabel.setTextFill(Color.web("#7f8c8d"));
        
        // Show if it was delayed before achievement
        if (goal.isDelayed()) {
            Label delayLabel = new Label("⚠️ Delayed " + goal.getDaysDelayed() + " day(s)");
            delayLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
            delayLabel.setTextFill(Color.web("#e67e22"));
            detailsBox.getChildren().addAll(dateLabel, delayLabel);
        } else {
            detailsBox.getChildren().add(dateLabel);
        }
        
        textContainer.getChildren().addAll(descLabel, detailsBox);
        
        item.getChildren().addAll(checkLabel, textContainer);
        return item;
    }
    
    private VBox createChartsSection() {
        VBox section = new VBox(20);
        
        chartsContainer = new VBox(20);
        section.getChildren().add(chartsContainer);
        
        return section;
    }
    
    private VBox createStatCard(String title, String value, String subtitle, String color) {
        VBox card = new VBox(8);
        card.setPrefWidth(200);
        card.setPrefHeight(120);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-border-color: " + color + "; -fx-border-width: 2; -fx-border-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 3, 0, 0, 1);");
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        titleLabel.setTextFill(Color.web("#7f8c8d"));
        
        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        valueLabel.setTextFill(Color.web(color));
        
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setFont(Font.font("System", FontWeight.NORMAL, 10));
        subtitleLabel.setTextFill(Color.web("#95a5a6"));
        subtitleLabel.setWrapText(true);
        subtitleLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        
        card.getChildren().addAll(titleLabel, valueLabel, subtitleLabel);
        return card;
    }
    
    private void updateStatsCards() {
        try {
            // Get data for last 30 days
            List<StudySession> recentSessions = studyService.getSessionsGroupedByDate(30).values()
                    .stream().flatMap(List::stream).collect(Collectors.toList());
            List<StudyGoal> recentGoals = studyService.getStudyGoals().stream()
                    .filter(g -> g.getDate().isAfter(dateTimeService.getCurrentDate().minusDays(30)))
                    .collect(Collectors.toList());
            List<Task> allTasks = taskService.getTasks();
            
            // Calculate statistics
            int totalSessions = recentSessions.size();
            int totalMinutes = recentSessions.stream().mapToInt(StudySession::getDurationMinutes).sum();
            int totalPoints = recentSessions.stream().mapToInt(StudySession::getPointsEarned).sum();
            
            double avgFocus = recentSessions.isEmpty() ? 0 : 
                recentSessions.stream().mapToInt(StudySession::getFocusLevel).average().orElse(0);
            
            int achievedGoals = (int) recentGoals.stream().filter(StudyGoal::isAchieved).count();
            int totalGoals = recentGoals.size();
            double goalCompletionRate = totalGoals > 0 ? (double) achievedGoals / totalGoals * 100 : 0;
            
            long completedTasks = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
            long totalActiveTasks = allTasks.stream().filter(t -> t.getStatus() != TaskStatus.COMPLETED).count();
            
            // Create stat cards
            HBox row1 = new HBox(15);
            row1.setAlignment(Pos.CENTER);
            
            VBox sessionsCard = createStatCard("Study Sessions", String.valueOf(totalSessions), "Last 30 days", "#3498db");
            VBox hoursCard = createStatCard("Study Hours", String.format("%.1f", totalMinutes / 60.0), "Total time spent", "#e74c3c");
            VBox pointsCard = createStatCard("Points Earned", String.valueOf(totalPoints), "Achievement score", "#f39c12");
            VBox focusCard = createStatCard("Avg Focus", String.format("%.1f/5", avgFocus), "Concentration level", "#9b59b6");
            
            row1.getChildren().addAll(sessionsCard, hoursCard, pointsCard, focusCard);
            
            HBox row2 = new HBox(15);
            row2.setAlignment(Pos.CENTER);
            
            VBox goalsCard = createStatCard("Goal Rate", String.format("%.0f%%", goalCompletionRate), "Goals achieved", "#27ae60");
            VBox tasksCard = createStatCard("Tasks Done", String.valueOf(completedTasks), "Completed tasks", "#16a085");
            VBox efficiencyCard = createStatCard("Efficiency", 
                totalMinutes > 0 ? String.format("%.1f", (double) totalPoints / totalMinutes * 60) : "0", 
                "Points per hour", "#8e44ad");
            VBox streakCard = createStatCard("Study Streak", calculateStudyStreak() + " days", "Consecutive days", "#e67e22");
            
            row2.getChildren().addAll(goalsCard, tasksCard, efficiencyCard, streakCard);
            
            statsContainer.getChildren().clear();
            statsContainer.getChildren().addAll(row1, row2);
            
        } catch (Exception e) {
            System.err.println("Error updating stats cards: " + e.getMessage());
            statsContainer.getChildren().clear();
            Label errorLabel = new Label("Unable to load statistics");
            errorLabel.setTextFill(Color.web("#e74c3c"));
            statsContainer.getChildren().add(errorLabel);
        }
    }
    
    private void updateCharts() {
        try {
            chartsContainer.getChildren().clear();
            
            // Focus level trend chart
            LineChart<String, Number> focusChart = createFocusTrendChart();
            VBox focusChartBox = new VBox(10);
            focusChartBox.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-padding: 15; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
            Label focusChartTitle = new Label("📈 Focus Level Trend (Last 14 Days)");
            focusChartTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
            focusChartBox.getChildren().addAll(focusChartTitle, focusChart);
            
            // Daily productivity chart
            BarChart<String, Number> productivityChart = createDailyProductivityChart();
            VBox productivityChartBox = new VBox(10);
            productivityChartBox.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-padding: 15; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
            Label productivityChartTitle = new Label("📊 Daily Study Time (Last 7 Days)");
            productivityChartTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
            productivityChartBox.getChildren().addAll(productivityChartTitle, productivityChart);
            
            chartsContainer.getChildren().addAll(focusChartBox, productivityChartBox);
            
        } catch (Exception e) {
            System.err.println("Error creating charts: " + e.getMessage());
            Label errorLabel = new Label("Unable to load charts");
            errorLabel.setTextFill(Color.web("#e74c3c"));
            chartsContainer.getChildren().add(errorLabel);
        }
    }
    
    private LineChart<String, Number> createFocusTrendChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis(1, 5, 1);
        yAxis.setLabel("Focus Level");
        xAxis.setLabel("Date");
        
        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setPrefHeight(300);
        chart.setLegendVisible(false);
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Focus Level");
        
        // Get last 14 days of sessions
        Map<LocalDate, List<StudySession>> sessionsMap = studyService.getSessionsGroupedByDate(14);
        
        for (int i = 13; i >= 0; i--) {
            LocalDate date = dateTimeService.getCurrentDate().minusDays(i);
            List<StudySession> sessions = sessionsMap.getOrDefault(date, List.of());
            
            double avgFocus = sessions.isEmpty() ? 0 : 
                sessions.stream().mapToInt(StudySession::getFocusLevel).average().orElse(0);
            
            String dateStr = date.format(DateTimeFormatter.ofPattern("MM/dd"));
            series.getData().add(new XYChart.Data<>(dateStr, avgFocus));
        }
        
        chart.getData().add(series);
        return chart;
    }
    
    private BarChart<String, Number> createDailyProductivityChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Study Time (hours)");
        xAxis.setLabel("Date");
        
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setPrefHeight(300);
        chart.setLegendVisible(false);
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Study Hours");
        
        // Get last 7 days of sessions
        Map<LocalDate, List<StudySession>> sessionsMap = studyService.getSessionsGroupedByDate(7);
        
        for (int i = 6; i >= 0; i--) {
            LocalDate date = dateTimeService.getCurrentDate().minusDays(i);
            List<StudySession> sessions = sessionsMap.getOrDefault(date, List.of());
            
            double totalHours = sessions.stream().mapToInt(StudySession::getDurationMinutes).sum() / 60.0;
            
            String dateStr = date.format(DateTimeFormatter.ofPattern("MM/dd"));
            series.getData().add(new XYChart.Data<>(dateStr, totalHours));
        }
        
        chart.getData().add(series);
        return chart;
    }
    
    private void updateProfileSummary() {
        try {
            List<StudySession> recentSessions = studyService.getSessionsGroupedByDate(30).values()
                    .stream().flatMap(List::stream).collect(Collectors.toList());
            
            if (recentSessions.isEmpty()) {
                profileSummaryLabel.setText("Welcome to StudySync! Start your first study session to see your progress here.");
                productivityRating.setProgress(0);
                productivityLabel.setText("No data yet");
                return;
            }
            
            // Calculate overall metrics
            double avgFocus = recentSessions.stream().mapToInt(StudySession::getFocusLevel).average().orElse(0);
            int totalHours = recentSessions.stream().mapToInt(StudySession::getDurationMinutes).sum() / 60;
            int studyDays = calculateStudyStreak();
            
            // Calculate productivity rating
            double productivityScore = calculateProductivityScore(recentSessions);
            productivityRating.setProgress(productivityScore / 100.0);
            
            String rating;
            String color;
            if (productivityScore >= 80) {
                rating = "Excellent";
                color = "#27ae60";
            } else if (productivityScore >= 60) {
                rating = "Good";
                color = "#3498db";
            } else if (productivityScore >= 40) {
                rating = "Fair";
                color = "#f39c12";
            } else {
                rating = "Needs Improvement";
                color = "#e74c3c";
            }
            
            productivityRating.setStyle("-fx-accent: " + color + ";");
            productivityLabel.setText(rating + " (" + Math.round(productivityScore) + "%)");
            productivityLabel.setTextFill(Color.web(color));
            
            String summary = String.format(
                "Over the last 30 days, you've completed %d study sessions totaling %d hours. " +
                "Your average focus level is %.1f/5. Keep up the great work and continue building your study habits!",
                recentSessions.size(), totalHours, avgFocus
            );
            
            profileSummaryLabel.setText(summary);
            
        } catch (Exception e) {
            System.err.println("Error updating profile summary: " + e.getMessage());
            profileSummaryLabel.setText("Unable to load profile summary.");
        }
    }
    
    private double calculateProductivityScore(List<StudySession> sessions) {
        if (sessions.isEmpty()) return 0;
        
        // Base score from focus levels (40% weight)
        double avgFocus = sessions.stream().mapToInt(StudySession::getFocusLevel).average().orElse(0);
        double focusScore = (avgFocus / 5.0) * 40;
        
        // Consistency score (30% weight) - based on how many days out of last 30 had sessions
        long daysWithSessions = sessions.stream()
            .map(s -> s.getDate())
            .distinct()
            .count();
        double consistencyScore = Math.min(1.0, daysWithSessions / 30.0) * 30;
        
        // Volume score (20% weight) - based on total study time
        int totalMinutes = sessions.stream().mapToInt(StudySession::getDurationMinutes).sum();
        double avgMinutesPerDay = totalMinutes / 30.0;
        double volumeScore = Math.min(1.0, avgMinutesPerDay / 120.0) * 20; // 2 hours per day = max score
        
        // Goal achievement score (10% weight)
        List<StudyGoal> goals = studyService.getStudyGoals().stream()
            .filter(g -> g.getDate().isAfter(dateTimeService.getCurrentDate().minusDays(30)))
            .collect(Collectors.toList());
        double goalScore = 0;
        if (!goals.isEmpty()) {
            long achievedGoals = goals.stream().filter(StudyGoal::isAchieved).count();
            goalScore = ((double) achievedGoals / goals.size()) * 10;
        }
        
        return focusScore + consistencyScore + volumeScore + goalScore;
    }
    
    private int calculateStudyStreak() {
        try {
            Map<LocalDate, List<StudySession>> sessionsMap = studyService.getSessionsGroupedByDate(90);
            int streak = 0;
            LocalDate date = dateTimeService.getCurrentDate();
            
            while (streak < 90) {
                if (!sessionsMap.containsKey(date) || sessionsMap.get(date).isEmpty()) {
                    break;
                }
                streak++;
                date = date.minusDays(1);
            }
            
            return streak;
        } catch (Exception e) {
            return 0;
        }
    }
    
    @Override
    public void updateDisplay() {
        updateProfileSummary();
        updateStatsCards();
        updateCharts();
    }
    
    @Override
    public Node getView() {
        return this;
    }
}