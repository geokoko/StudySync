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
import javafx.scene.text.FontPosture;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calendar view panel that displays a full month calendar with daily performance metrics
 * and detailed day views when clicking on specific dates. Replaces the DailyViewPanel
 * with a more comprehensive calendar-based interface similar to Google Calendar.
 */
public class CalendarViewPanel extends ScrollPane implements RefreshablePanel {
    private final StudyService studyService;
    private final TaskService taskService;
    private final ProjectService projectService;
    
    // UI Components
    private VBox mainContainer;
    private Label monthYearLabel;
    private GridPane calendarGrid;
    private YearMonth currentMonth;
    private LocalDate selectedDate;
    
    // Calendar layout constants
    private static final int DAYS_IN_WEEK = 7;
    private static final int MAX_WEEKS = 6;
    private static final double CELL_WIDTH = 150;
    private static final double CELL_HEIGHT = 120;
    
    public CalendarViewPanel(StudyService studyService, TaskService taskService, ProjectService projectService) {
        this.studyService = studyService;
        this.taskService = taskService;
        this.projectService = projectService;
        this.currentMonth = YearMonth.now();
        this.selectedDate = LocalDate.now();
        
        // Create main content container
        mainContainer = new VBox(15);
        mainContainer.setPadding(new Insets(20));
        mainContainer.setStyle("-fx-background-color: linear-gradient(to bottom, #f1f2f6, #dfe4ea);");
        
        // Set up ScrollPane properties
        this.setContent(mainContainer);
        this.setFitToWidth(true);
        this.setFitToHeight(true);
        this.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.getStyleClass().add("tab-content-area");
        
        initializeComponents();
        updateDisplay();
    }

    private void initializeComponents() {
        // Header with navigation
        createHeaderSection();
        
        // Calendar grid
        createCalendarGrid();
        
        // Legend for understanding the metrics
        createLegendSection();
    }
    
    private void createHeaderSection() {
        VBox headerSection = new VBox(15);
        headerSection.setStyle("-fx-background-color: white; -fx-padding: 20; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        
        // Title and navigation
        HBox titleBox = new HBox(20);
        titleBox.setAlignment(Pos.CENTER);
        
        Button prevMonthBtn = new Button("◀ Previous");
        prevMonthBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 5;");
        prevMonthBtn.setOnAction(e -> {
            currentMonth = currentMonth.minusMonths(1);
            updateCalendarDisplay();
        });
        
        monthYearLabel = new Label();
        monthYearLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        monthYearLabel.setTextFill(Color.web("#2c3e50"));
        
        Button nextMonthBtn = new Button("Next ▶");
        nextMonthBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 5;");
        nextMonthBtn.setOnAction(e -> {
            currentMonth = currentMonth.plusMonths(1);
            updateCalendarDisplay();
        });
        
        Button todayBtn = new Button("📅 Today");
        todayBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-background-radius: 5;");
        todayBtn.setOnAction(e -> {
            currentMonth = YearMonth.now();
            selectedDate = LocalDate.now();
            updateCalendarDisplay();
        });
        
        titleBox.getChildren().addAll(prevMonthBtn, monthYearLabel, nextMonthBtn, todayBtn);
        
        // Quick stats for current month
        Label statsLabel = new Label("Click on any day to view detailed information including goals, sessions, and performance metrics.");
        statsLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
        statsLabel.setTextFill(Color.web("#7f8c8d"));
        statsLabel.setWrapText(true);
        
        headerSection.getChildren().addAll(titleBox, statsLabel);
        mainContainer.getChildren().add(headerSection);
    }
    
    private void createCalendarGrid() {
        VBox calendarSection = new VBox(10);
        calendarSection.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        
        // Day headers (Mon, Tue, Wed, etc.)
        HBox dayHeaders = new HBox();
        dayHeaders.setAlignment(Pos.CENTER);
        String[] dayNames = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        
        for (String dayName : dayNames) {
            Label dayLabel = new Label(dayName);
            dayLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
            dayLabel.setTextFill(Color.web("#2c3e50"));
            dayLabel.setPrefWidth(CELL_WIDTH);
            dayLabel.setAlignment(Pos.CENTER);
            dayLabel.setStyle("-fx-background-color: #ecf0f1; -fx-padding: 10;");
            dayHeaders.getChildren().add(dayLabel);
        }
        
        // Calendar grid
        calendarGrid = new GridPane();
        calendarGrid.setAlignment(Pos.CENTER);
        calendarGrid.setHgap(2);
        calendarGrid.setVgap(2);
        
        calendarSection.getChildren().addAll(dayHeaders, calendarGrid);
        mainContainer.getChildren().add(calendarSection);
    }
    
    private void createLegendSection() {
        VBox legendSection = new VBox(10);
        legendSection.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        
        Label legendTitle = new Label("📋 Calendar Legend");
        legendTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        legendTitle.setTextFill(Color.web("#2c3e50"));
        
        HBox legendItems = new HBox(30);
        legendItems.setAlignment(Pos.CENTER_LEFT);
        
        // Today indicator
        VBox todayItem = createLegendItem("📅", "Today", "#3498db");
        
        // High productivity indicator
        VBox highProdItem = createLegendItem("🌟", "High Productivity", "#27ae60");
        
        // Medium productivity indicator
        VBox medProdItem = createLegendItem("⭐", "Medium Productivity", "#f39c12");
        
        // Low/No productivity indicator
        VBox lowProdItem = createLegendItem("📈", "Low/No Activity", "#95a5a6");
        
        // Goals indicator
        VBox goalsItem = createLegendItem("🎯", "Goals Achieved", "#9b59b6");
        
        legendItems.getChildren().addAll(todayItem, highProdItem, medProdItem, lowProdItem, goalsItem);
        
        legendSection.getChildren().addAll(legendTitle, legendItems);
        mainContainer.getChildren().add(legendSection);
    }
    
    private VBox createLegendItem(String icon, String text, String color) {
        VBox item = new VBox(2);
        item.setAlignment(Pos.CENTER);
        
        Label iconLabel = new Label(icon);
        iconLabel.setFont(Font.font("System", FontWeight.NORMAL, 16));
        
        Label textLabel = new Label(text);
        textLabel.setFont(Font.font("System", FontWeight.NORMAL, 10));
        textLabel.setTextFill(Color.web(color));
        
        item.getChildren().addAll(iconLabel, textLabel);
        return item;
    }
    
    private void updateCalendarDisplay() {
        // Update month/year label
        String monthYear = currentMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + currentMonth.getYear();
        monthYearLabel.setText(monthYear);
        
        // Clear existing calendar
        calendarGrid.getChildren().clear();
        
        // Get first day of month and calculate starting position
        LocalDate firstOfMonth = currentMonth.atDay(1);
        int dayOfWeek = firstOfMonth.getDayOfWeek().getValue(); // 1 = Monday, 7 = Sunday
        int startCol = (dayOfWeek - 1) % 7; // Convert to 0-6 where 0 = Monday
        
        // Fill in the calendar days
        int daysInMonth = currentMonth.lengthOfMonth();
        int row = 0;
        int col = startCol;
        
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = currentMonth.atDay(day);
            VBox dayCell = createDayCell(date);
            
            calendarGrid.add(dayCell, col, row);
            
            col++;
            if (col >= DAYS_IN_WEEK) {
                col = 0;
                row++;
            }
        }
    }
    
    private VBox createDayCell(LocalDate date) {
        VBox dayCell = new VBox(5);
        dayCell.setPrefSize(CELL_WIDTH, CELL_HEIGHT);
        dayCell.setPadding(new Insets(8));
        dayCell.setAlignment(Pos.TOP_LEFT);
        
        // Determine cell styling based on date
        boolean isToday = date.equals(LocalDate.now());
        boolean isPastDate = date.isBefore(LocalDate.now());
        boolean isSelected = date.equals(selectedDate);
        
        String baseStyle = "-fx-border-color: #ddd; -fx-border-width: 1; -fx-background-radius: 5; -fx-border-radius: 5;";
        
        if (isToday) {
            baseStyle += " -fx-background-color: #e3f2fd; -fx-border-color: #3498db; -fx-border-width: 2;";
        } else if (isSelected) {
            baseStyle += " -fx-background-color: #f0f8ff; -fx-border-color: #2980b9; -fx-border-width: 2;";
        } else if (isPastDate) {
            baseStyle += " -fx-background-color: #fafafa;";
        } else {
            baseStyle += " -fx-background-color: white;";
        }
        
        final String finalBaseStyle = baseStyle;
        dayCell.setStyle(baseStyle);
        
        // Day number
        Label dayLabel = new Label(String.valueOf(date.getDayOfMonth()));
        dayLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        dayLabel.setTextFill(isToday ? Color.web("#3498db") : Color.web("#2c3e50"));
        
        // Get data for this date
        DayData dayData = getDayData(date);
        
        // Performance indicators
        VBox metricsBox = new VBox(2);
        
        // Study sessions indicator
        if (dayData.totalSessions > 0) {
            HBox sessionsBox = new HBox(3);
            sessionsBox.setAlignment(Pos.CENTER_LEFT);
            
            Label sessionIcon = new Label("📚");
            sessionIcon.setFont(Font.font("System", FontWeight.NORMAL, 10));
            
            Label sessionText = new Label(dayData.totalSessions + "s");
            sessionText.setFont(Font.font("System", FontWeight.NORMAL, 9));
            sessionText.setTextFill(Color.web("#3498db"));
            
            // Focus rating stars
            String focusStars = "★".repeat(Math.max(0, Math.min(5, dayData.avgFocusLevel))) + 
                               "☆".repeat(Math.max(0, 5 - Math.max(0, dayData.avgFocusLevel)));
            Label focusLabel = new Label(focusStars);
            focusLabel.setFont(Font.font("System", FontWeight.NORMAL, 8));
            focusLabel.setTextFill(Color.web("#f39c12"));
            
            sessionsBox.getChildren().addAll(sessionIcon, sessionText, focusLabel);
            metricsBox.getChildren().add(sessionsBox);
        }
        
        // Goals indicator
        if (dayData.totalGoals > 0) {
            HBox goalsBox = new HBox(3);
            goalsBox.setAlignment(Pos.CENTER_LEFT);
            
            Label goalIcon = new Label("🎯");
            goalIcon.setFont(Font.font("System", FontWeight.NORMAL, 10));
            
            Label goalText = new Label(dayData.achievedGoals + "/" + dayData.totalGoals);
            goalText.setFont(Font.font("System", FontWeight.NORMAL, 9));
            goalText.setTextFill(dayData.achievedGoals == dayData.totalGoals ? Color.web("#27ae60") : Color.web("#e74c3c"));
            
            goalsBox.getChildren().addAll(goalIcon, goalText);
            metricsBox.getChildren().add(goalsBox);
        }
        
        // Productivity indicator
        String productivityIcon = getProductivityIcon(dayData.productivityScore);
        Label prodLabel = new Label(productivityIcon);
        prodLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
        metricsBox.getChildren().add(prodLabel);
        
        // Study time indicator
        if (dayData.totalMinutes > 0) {
            Label timeLabel = new Label(formatStudyTime(dayData.totalMinutes));
            timeLabel.setFont(Font.font("System", FontWeight.NORMAL, 8));
            timeLabel.setTextFill(Color.web("#7f8c8d"));
            metricsBox.getChildren().add(timeLabel);
        }
        
        dayCell.getChildren().addAll(dayLabel, metricsBox);
        
        // Make clickable for past dates and today
        if (isPastDate || isToday) {
            dayCell.setOnMouseClicked(e -> {
                selectedDate = date;
                updateCalendarDisplay(); // Refresh to show selection
                showDayDetailDialog(date, dayData);
            });
            dayCell.setOnMouseEntered(e -> {
                if (!isToday && !isSelected) {
                    dayCell.setStyle(finalBaseStyle + " -fx-background-color: #f8f9fa;");
                }
                dayCell.setStyle(dayCell.getStyle() + " -fx-cursor: hand;");
            });
            dayCell.setOnMouseExited(e -> {
                if (!isToday && !isSelected) {
                    dayCell.setStyle(finalBaseStyle);
                }
            });
        }
        
        return dayCell;
    }
    
    private DayData getDayData(LocalDate date) {
        try {
            List<StudyGoal> studyGoals = getFilteredStudyGoalsForDate(date);
            List<StudySession> studySessions = studyService.getSessionsForDate(date);
            List<ProjectSession> projectSessions = projectService.getProjectSessionsForDate(date);
            
            DayData data = new DayData();
            data.date = date;
            data.totalSessions = studySessions.size() + projectSessions.size();
            data.totalMinutes = studySessions.stream().mapToInt(StudySession::getDurationMinutes).sum() +
                               projectSessions.stream().mapToInt(ProjectSession::getDurationMinutes).sum();
            data.totalPoints = studySessions.stream().mapToInt(StudySession::getPointsEarned).sum() +
                              projectSessions.stream().mapToInt(ProjectSession::getPointsEarned).sum();
            data.totalGoals = studyGoals.size();
            data.achievedGoals = (int) studyGoals.stream().filter(StudyGoal::isAchieved).count();
            data.avgFocusLevel = studySessions.isEmpty() ? 0 : 
                                (int) Math.round(studySessions.stream().mapToInt(StudySession::getFocusLevel).average().orElse(0));
            
            // Calculate productivity score
            data.productivityScore = calculateDayProductivityScore(data);
            
            return data;
        } catch (Exception e) {
            return new DayData(); // Return empty data if error
        }
    }
    
    private List<StudyGoal> getFilteredStudyGoalsForDate(LocalDate date) {
        LocalDate today = LocalDate.now();
        
        if (date.equals(today)) {
            // For today, show all goals including delayed ones
            return studyService.getStudyGoalsForDate(date);
        } else {
            // For previous days, show only goals achieved that day OR goals originally set for that day
            List<StudyGoal> allGoalsForDate = studyService.getStudyGoalsForDate(date);
            return allGoalsForDate.stream()
                    .filter(goal -> goal.isAchieved() || goal.getDate().equals(date))
                    .collect(Collectors.toList());
        }
    }
    
    private double calculateDayProductivityScore(DayData data) {
        if (data.totalSessions == 0) return 0.0;
        
        // Base score from sessions and time (50%)
        double timeScore = Math.min(1.0, data.totalMinutes / 120.0) * 25; // 2 hours = max
        double sessionScore = Math.min(1.0, data.totalSessions / 3.0) * 25; // 3 sessions = max
        
        // Focus score (30%)
        double focusScore = (data.avgFocusLevel / 5.0) * 30;
        
        // Goal achievement score (20%)
        double goalScore = data.totalGoals > 0 ? ((double) data.achievedGoals / data.totalGoals) * 20 : 0;
        
        return timeScore + sessionScore + focusScore + goalScore;
    }
    
    private String getProductivityIcon(double score) {
        if (score >= 70) return "🌟"; // High productivity
        else if (score >= 40) return "⭐"; // Medium productivity  
        else if (score >= 10) return "📈"; // Low productivity
        else return ""; // No activity
    }
    
    private String formatStudyTime(int minutes) {
        if (minutes >= 60) {
            int hours = minutes / 60;
            int remainingMinutes = minutes % 60;
            if (remainingMinutes == 0) {
                return hours + "h";
            } else {
                return hours + "h" + remainingMinutes + "m";
            }
        } else {
            return minutes + "m";
        }
    }
    
    private void showDayDetailDialog(LocalDate date, DayData dayData) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Day Details - " + date.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy")));
        dialog.setHeaderText("📅 Complete Day Overview");
        
        // Create tabbed content similar to original DailyViewPanel
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setPrefSize(800, 600);
        
        // Overview Tab
        Tab overviewTab = new Tab("📊 Overview", createOverviewTab(date, dayData));
        
        // Goals Tab  
        Tab goalsTab = new Tab("🎯 Goals", createGoalsTab(date));
        
        // Sessions Tab
        Tab sessionsTab = new Tab("📚 Sessions", createSessionsTab(date));
        
        // Performance Tab
        Tab performanceTab = new Tab("📈 Performance", createPerformanceTab(date, dayData));
        
        tabPane.getTabs().addAll(overviewTab, goalsTab, sessionsTab, performanceTab);
        
        dialog.getDialogPane().setContent(tabPane);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);
        
        dialog.showAndWait();
    }
    
    // Helper class to store day data
    private static class DayData {
        LocalDate date;
        int totalSessions = 0;
        int totalMinutes = 0;
        int totalPoints = 0;
        int totalGoals = 0;
        int achievedGoals = 0;
        int avgFocusLevel = 0;
        double productivityScore = 0.0;
    }
    
    private VBox createOverviewTab(LocalDate date, DayData dayData) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        // Day summary header
        VBox summarySection = new VBox(10);
        summarySection.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 10; -fx-padding: 20;");
        
        Label dateLabel = new Label(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy")));
        dateLabel.setFont(Font.font("System", FontWeight.BOLD, 20));
        dateLabel.setTextFill(Color.web("#2c3e50"));
        
        // Key metrics
        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(30);
        metricsGrid.setVgap(15);
        metricsGrid.setPadding(new Insets(15, 0, 0, 0));
        
        // Sessions
        metricsGrid.add(new Label("Study Sessions:"), 0, 0);
        metricsGrid.add(new Label(String.valueOf(dayData.totalSessions)), 1, 0);
        
        // Study time
        metricsGrid.add(new Label("Total Study Time:"), 0, 1);
        metricsGrid.add(new Label(formatStudyTime(dayData.totalMinutes)), 1, 1);
        
        // Points earned
        metricsGrid.add(new Label("Points Earned:"), 0, 2);
        metricsGrid.add(new Label(String.valueOf(dayData.totalPoints)), 1, 2);
        
        // Goals
        metricsGrid.add(new Label("Goals Achieved:"), 0, 3);
        metricsGrid.add(new Label(dayData.achievedGoals + "/" + dayData.totalGoals), 1, 3);
        
        // Average focus
        metricsGrid.add(new Label("Average Focus:"), 0, 4);
        Label focusLabel = new Label("★".repeat(dayData.avgFocusLevel) + "☆".repeat(5 - dayData.avgFocusLevel) + " (" + dayData.avgFocusLevel + "/5)");
        focusLabel.setTextFill(Color.web("#f39c12"));
        metricsGrid.add(focusLabel, 1, 4);
        
        // Productivity score
        metricsGrid.add(new Label("Productivity Score:"), 0, 5);
        Label productivityLabel = new Label(String.format("%.0f%%", dayData.productivityScore));
        productivityLabel.setTextFill(getProductivityColor(dayData.productivityScore));
        metricsGrid.add(productivityLabel, 1, 5);
        
        summarySection.getChildren().addAll(dateLabel, metricsGrid);
        
        // Day reflection if exists
        VBox reflectionSection = createReflectionSection(date);
        
        content.getChildren().addAll(summarySection, reflectionSection);
        return content;
    }
    
    private VBox createReflectionSection(LocalDate date) {
        VBox section = new VBox(10);
        section.setStyle("-fx-background-color: #fff3e0; -fx-background-radius: 10; -fx-padding: 20;");
        
        Label reflectionTitle = new Label("💭 Daily Reflection");
        reflectionTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        reflectionTitle.setTextFill(Color.web("#2c3e50"));
        
        try {
            DailyReflection reflection = studyService.getDailyReflectionForDate(date).orElse(null);
            if (reflection != null && reflection.getReflectionText() != null && !reflection.getReflectionText().trim().isEmpty()) {
                TextArea reflectionArea = new TextArea(reflection.getReflectionText());
                reflectionArea.setEditable(false);
                reflectionArea.setPrefRowCount(4);
                reflectionArea.setWrapText(true);
                reflectionArea.setStyle("-fx-background-color: white;");
                section.getChildren().addAll(reflectionTitle, reflectionArea);
            } else {
                Label noReflectionLabel = new Label("No reflection recorded for this day.");
                noReflectionLabel.setFont(Font.font("System", FontPosture.ITALIC, 12));
                noReflectionLabel.setTextFill(Color.web("#7f8c8d"));
                section.getChildren().addAll(reflectionTitle, noReflectionLabel);
            }
        } catch (Exception e) {
            Label errorLabel = new Label("Unable to load reflection data.");
            errorLabel.setTextFill(Color.web("#e74c3c"));
            section.getChildren().addAll(reflectionTitle, errorLabel);
        }
        
        return section;
    }
    
    private VBox createGoalsTab(LocalDate date) {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        List<StudyGoal> studyGoals = getFilteredStudyGoalsForDate(date);
        
        if (studyGoals.isEmpty()) {
            Label noGoalsLabel = new Label("📭 No goals recorded for this date");
            noGoalsLabel.setFont(Font.font("System", FontWeight.NORMAL, 16));
            noGoalsLabel.setTextFill(Color.GRAY);
            noGoalsLabel.setPadding(new Insets(50));
            content.getChildren().add(noGoalsLabel);
            return content;
        }
        
        Label goalsTitle = new Label("🎯 Study Goals (" + studyGoals.size() + ")");
        goalsTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        goalsTitle.setTextFill(Color.web("#9b59b6"));
        content.getChildren().add(goalsTitle);
        
        for (StudyGoal goal : studyGoals) {
            VBox goalBox = createStudyGoalBox(goal);
            content.getChildren().add(goalBox);
        }
        
        return content;
    }
    
    private VBox createSessionsTab(LocalDate date) {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        List<StudySession> studySessions = studyService.getSessionsForDate(date);
        List<ProjectSession> projectSessions = projectService.getProjectSessionsForDate(date);
        
        if (studySessions.isEmpty() && projectSessions.isEmpty()) {
            Label noSessionsLabel = new Label("📭 No sessions recorded for this date");
            noSessionsLabel.setFont(Font.font("System", FontWeight.NORMAL, 16));
            noSessionsLabel.setTextFill(Color.GRAY);
            noSessionsLabel.setPadding(new Insets(50));
            content.getChildren().add(noSessionsLabel);
            return content;
        }
        
        // Study Sessions
        if (!studySessions.isEmpty()) {
            Label studyTitle = new Label("📚 Study Sessions (" + studySessions.size() + ")");
            studyTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
            studyTitle.setTextFill(Color.web("#3498db"));
            content.getChildren().add(studyTitle);
            
            for (StudySession session : studySessions) {
                VBox sessionBox = createStudySessionBox(session);
                content.getChildren().add(sessionBox);
            }
        }
        
        // Project Sessions
        if (!projectSessions.isEmpty()) {
            Label projectTitle = new Label("🚀 Project Sessions (" + projectSessions.size() + ")");
            projectTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
            projectTitle.setTextFill(Color.web("#e74c3c"));
            projectTitle.setPadding(new Insets(15, 0, 5, 0));
            content.getChildren().add(projectTitle);
            
            for (ProjectSession session : projectSessions) {
                VBox sessionBox = createProjectSessionBox(session);
                content.getChildren().add(sessionBox);
            }
        }
        
        return content;
    }
    
    private VBox createPerformanceTab(LocalDate date, DayData dayData) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        // Performance summary
        VBox performanceSection = new VBox(15);
        performanceSection.setStyle("-fx-background-color: #f0f8ff; -fx-background-radius: 10; -fx-padding: 20;");
        
        Label performanceTitle = new Label("📈 Performance Analysis");
        performanceTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        performanceTitle.setTextFill(Color.web("#2c3e50"));
        
        // Performance breakdown
        VBox metricsBreakdown = new VBox(10);
        
        // Efficiency metrics
        if (dayData.totalSessions > 0) {
            double pointsPerMinute = dayData.totalMinutes > 0 ? (double) dayData.totalPoints / dayData.totalMinutes : 0;
            double pointsPerSession = (double) dayData.totalPoints / dayData.totalSessions;
            double avgSessionLength = (double) dayData.totalMinutes / dayData.totalSessions;
            
            GridPane efficiencyGrid = new GridPane();
            efficiencyGrid.setHgap(20);
            efficiencyGrid.setVgap(8);
            
            efficiencyGrid.add(new Label("Points per Minute:"), 0, 0);
            efficiencyGrid.add(new Label(String.format("%.2f", pointsPerMinute)), 1, 0);
            
            efficiencyGrid.add(new Label("Points per Session:"), 0, 1);
            efficiencyGrid.add(new Label(String.format("%.1f", pointsPerSession)), 1, 1);
            
            efficiencyGrid.add(new Label("Avg Session Length:"), 0, 2);
            efficiencyGrid.add(new Label(String.format("%.0f min", avgSessionLength)), 1, 2);
            
            metricsBreakdown.getChildren().add(efficiencyGrid);
        }
        
        // Goal achievement analysis
        if (dayData.totalGoals > 0) {
            double goalCompletionRate = ((double) dayData.achievedGoals / dayData.totalGoals) * 100;
            Label goalAnalysis = new Label(String.format("Goal Completion: %.0f%% (%d out of %d goals achieved)", 
                goalCompletionRate, dayData.achievedGoals, dayData.totalGoals));
            goalAnalysis.setFont(Font.font("System", FontWeight.NORMAL, 12));
            goalAnalysis.setTextFill(goalCompletionRate == 100 ? Color.web("#27ae60") : Color.web("#e74c3c"));
            metricsBreakdown.getChildren().add(goalAnalysis);
        }
        
        performanceSection.getChildren().addAll(performanceTitle, metricsBreakdown);
        
        // Recommendations
        VBox recommendationsSection = createRecommendationsSection(dayData);
        
        content.getChildren().addAll(performanceSection, recommendationsSection);
        return content;
    }
    
    private VBox createRecommendationsSection(DayData dayData) {
        VBox section = new VBox(10);
        section.setStyle("-fx-background-color: #f0fff0; -fx-background-radius: 10; -fx-padding: 20;");
        
        Label recommendationsTitle = new Label("💡 Insights & Recommendations");
        recommendationsTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        recommendationsTitle.setTextFill(Color.web("#2c3e50"));
        
        VBox recommendations = new VBox(5);
        
        // Generate recommendations based on performance
        if (dayData.totalSessions == 0) {
            recommendations.getChildren().add(createRecommendationLabel("• No study activity recorded - consider setting daily study goals"));
        } else {
            if (dayData.avgFocusLevel < 3) {
                recommendations.getChildren().add(createRecommendationLabel("• Focus level could be improved - try removing distractions"));
            }
            
            if (dayData.totalMinutes < 60) {
                recommendations.getChildren().add(createRecommendationLabel("• Consider longer study sessions for better deep work"));
            }
            
            if (dayData.totalGoals > 0 && dayData.achievedGoals < dayData.totalGoals) {
                recommendations.getChildren().add(createRecommendationLabel("• Some goals were not achieved - review and adjust goal difficulty"));
            }
            
            if (dayData.productivityScore >= 80) {
                recommendations.getChildren().add(createRecommendationLabel("• Excellent productivity day! Keep up the great work!"));
            }
        }
        
        section.getChildren().addAll(recommendationsTitle, recommendations);
        return section;
    }
    
    private Label createRecommendationLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.NORMAL, 12));
        label.setWrapText(true);
        return label;
    }
    
    private Color getProductivityColor(double score) {
        if (score >= 80) return Color.web("#27ae60");
        else if (score >= 60) return Color.web("#3498db");
        else if (score >= 40) return Color.web("#f39c12");
        else return Color.web("#e74c3c");
    }
    
    // Reuse methods from DailyViewPanel for consistency
    private VBox createStudyGoalBox(StudyGoal goal) {
        VBox goalBox = new VBox(8);
        goalBox.setPadding(new Insets(12));
        
        String backgroundColor;
        String borderColor;
        
        if (goal.isAchieved()) {
            backgroundColor = "#e8f5e8";
            borderColor = "#27ae60";
        } else if (goal.isDelayed()) {
            double intensity = goal.getDelayColorIntensity();
            if (intensity <= 0.3) {
                backgroundColor = "#fff3e0";
                borderColor = "#ff9800";
            } else if (intensity <= 0.6) {
                backgroundColor = "#ffebee";
                borderColor = "#ff5722";
            } else {
                backgroundColor = "#ffebee";
                borderColor = "#f44336";
            }
        } else {
            backgroundColor = "#fff5f5";
            borderColor = "#e74c3c";
        }
        
        goalBox.setStyle("-fx-background-color: " + backgroundColor + "; -fx-background-radius: 8; -fx-border-color: " + borderColor + "; -fx-border-radius: 8;");
        
        // Status and description
        String statusIcon = goal.isAchieved() ? "✅" : (goal.isDelayed() ? "⚠️" : "⭕");
        String statusText = goal.isAchieved() ? "Achieved" : (goal.isDelayed() ? "Delayed" : "Pending");
        
        Label statusLabel = new Label(statusIcon + " " + statusText);
        statusLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        Label descriptionLabel = new Label("🎯 " + goal.getDescription());
        descriptionLabel.setFont(Font.font("System", FontWeight.NORMAL, 13));
        descriptionLabel.setWrapText(true);
        
        goalBox.getChildren().addAll(statusLabel, descriptionLabel);
        
        // Add delay info if applicable
        if (goal.isDelayed()) {
            Label delayLabel = new Label(String.format("📅 Originally from: %s • 🔥 %d days delayed", 
                goal.getDate().toString(), goal.getDaysDelayed()));
            delayLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
            delayLabel.setTextFill(Color.web("#ff5722"));
            goalBox.getChildren().add(delayLabel);
        }
        
        return goalBox;
    }
    
    private VBox createStudySessionBox(StudySession session) {
        VBox sessionBox = new VBox(8);
        sessionBox.setPadding(new Insets(12));
        sessionBox.setStyle("-fx-background-color: #f8f9ff; -fx-background-radius: 8; -fx-border-color: #3498db; -fx-border-radius: 8;");
        
        // Time and duration
        String startTime = session.getStartTime() != null ? 
            session.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "Unknown";
        String endTime = session.getEndTime() != null ? 
            session.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "Unknown";
        
        Label timeLabel = new Label("⏰ " + startTime + " - " + endTime + " (" + session.getDurationMinutes() + " min)");
        timeLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        // Focus and points
        Label focusLabel = new Label("🎯 Focus: " + "★".repeat(session.getFocusLevel()) + "☆".repeat(5 - session.getFocusLevel()) + " (" + session.getFocusLevel() + "/5)");
        focusLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
        focusLabel.setTextFill(Color.web("#f39c12"));
        
        Label pointsLabel = new Label("🏆 " + session.getPointsEarned() + " points earned");
        pointsLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
        pointsLabel.setTextFill(Color.web("#27ae60"));
        
        sessionBox.getChildren().addAll(timeLabel, focusLabel, pointsLabel);
        
        // Subject and topic if available
        if (session.getSubject() != null && !session.getSubject().trim().isEmpty()) {
            Label subjectLabel = new Label("📖 Subject: " + session.getSubject());
            subjectLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
            sessionBox.getChildren().add(subjectLabel);
        }
        
        return sessionBox;
    }
    
    private VBox createProjectSessionBox(ProjectSession session) {
        VBox sessionBox = new VBox(8);
        sessionBox.setPadding(new Insets(12));
        sessionBox.setStyle("-fx-background-color: #fff8f8; -fx-background-radius: 8; -fx-border-color: #e74c3c; -fx-border-radius: 8;");
        
        // Project info
        Project project = projectService.getProjectById(session.getProjectId()).orElse(null);
        String projectTitle = project != null ? project.getTitle() : "Unknown Project";
        
        Label projectLabel = new Label("📁 " + projectTitle);
        projectLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        // Time and duration
        String startTime = session.getStartTime() != null ? 
            session.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "Unknown";
        
        Label timeLabel = new Label("⏰ " + startTime + " (" + session.getDurationMinutes() + " min)");
        timeLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
        
        Label pointsLabel = new Label("🏆 " + session.getPointsEarned() + " points earned");
        pointsLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
        pointsLabel.setTextFill(Color.web("#27ae60"));
        
        sessionBox.getChildren().addAll(projectLabel, timeLabel, pointsLabel);
        
        return sessionBox;
    }
    
    @Override
    public void updateDisplay() {
        updateCalendarDisplay();
    }
    
    @Override
    public Node getView() {
        return this;
    }
}