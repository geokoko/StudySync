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
        this.setFitToHeight(false);
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
        
        Button todayBtn = new Button("» Today");
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
        
        Label legendTitle = new Label("» Calendar Legend");
        legendTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        legendTitle.setTextFill(Color.web("#2c3e50"));
        
        HBox legendItems = new HBox(30);
        legendItems.setAlignment(Pos.CENTER_LEFT);
        
        // Today indicator
        VBox todayItem = createLegendItem("\uD83D\uDCC5", "Today", "#3498db");
        
        // High productivity indicator
        VBox highProdItem = createLegendItem("\u2605\u2605", "High Productivity", "#27ae60");
        
        // Medium productivity indicator
        VBox medProdItem = createLegendItem("\u2B50", "Medium Productivity", "#f39c12");
        
        // Low/No productivity indicator
        VBox lowProdItem = createLegendItem("\uD83D\uDCC8", "Low/No Activity", "#95a5a6");
        
        // Goals indicator
        VBox goalsItem = createLegendItem("\u25CE", "Goals Achieved", "#9b59b6");
        
        legendItems.getChildren().addAll(todayItem, highProdItem, medProdItem, lowProdItem, goalsItem);
        
        legendSection.getChildren().addAll(legendTitle, legendItems);
        mainContainer.getChildren().add(legendSection);
    }
    
    private VBox createLegendItem(String icon, String text, String color) {
        VBox item = new VBox(2);
        item.setAlignment(Pos.CENTER);
        
        Label iconLabel = new Label(icon);
        iconLabel.setFont(Font.font("Noto Emoji", FontWeight.NORMAL, 16));
        
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
        boolean isFutureDate = date.isAfter(LocalDate.now());
        boolean isSelected = date.equals(selectedDate);
        
        String baseStyle = "-fx-border-color: #ddd; -fx-border-width: 1; -fx-background-radius: 5; -fx-border-radius: 5;";
        
        if (isToday) {
            baseStyle += " -fx-background-color: #e3f2fd; -fx-border-color: #3498db; -fx-border-width: 2;";
        } else if (isSelected) {
            baseStyle += " -fx-background-color: #f0f8ff; -fx-border-color: #2980b9; -fx-border-width: 2;";
        } else if (isPastDate) {
            baseStyle += " -fx-background-color: #fafafa;";
        } else if (isFutureDate) {
            baseStyle += " -fx-background-color: #f0fff4; -fx-border-color: #27ae60; -fx-border-style: dashed;";
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
            
            Label sessionIcon = new Label("\uD83D\uDCDA");
            sessionIcon.setFont(Font.font("Noto Emoji", FontWeight.NORMAL, 12));
            
            Label sessionText = new Label(dayData.totalSessions + "s");
            sessionText.setFont(Font.font("System", FontWeight.NORMAL, 9));
            sessionText.setTextFill(Color.web("#3498db"));
            
            // Focus rating stars
            String focusStars = "\u2605".repeat(Math.max(0, Math.min(5, dayData.avgFocusLevel))) + 
                               "\u2606".repeat(Math.max(0, 5 - Math.max(0, dayData.avgFocusLevel)));
            Label focusLabel = new Label(focusStars);
            focusLabel.setFont(Font.font("Noto Emoji", FontWeight.NORMAL, 10));
            focusLabel.setTextFill(Color.web("#f39c12"));
            
            sessionsBox.getChildren().addAll(sessionIcon, sessionText, focusLabel);
            metricsBox.getChildren().add(sessionsBox);
        }
        
        // Goals indicator
        if (dayData.totalGoals > 0) {
            HBox goalsBox = new HBox(3);
            goalsBox.setAlignment(Pos.CENTER_LEFT);
            
            Label goalIcon = new Label("\u25CE");
            goalIcon.setFont(Font.font("Noto Emoji", FontWeight.NORMAL, 12));
            
            Label goalText = new Label(dayData.achievedGoals + "/" + dayData.totalGoals);
            goalText.setFont(Font.font("System", FontWeight.NORMAL, 9));
            goalText.setTextFill(dayData.achievedGoals == dayData.totalGoals ? Color.web("#27ae60") : Color.web("#e74c3c"));
            
            goalsBox.getChildren().addAll(goalIcon, goalText);
            metricsBox.getChildren().add(goalsBox);
        }
        
        // Tasks indicator
        if (!dayData.tasks.isEmpty()) {
            HBox tasksBox = new HBox(3);
            tasksBox.setAlignment(Pos.CENTER_LEFT);
            
            Label taskIcon = new Label("☑");
            taskIcon.setFont(Font.font("System", FontWeight.NORMAL, 11));
            taskIcon.setTextFill(Color.web("#9b59b6"));
            
            // Show count + first task title (truncated)
            String firstTitle = dayData.tasks.get(0).getTitle();
            if (firstTitle.length() > 14) firstTitle = firstTitle.substring(0, 13) + "…";
            String taskText = dayData.tasks.size() == 1
                    ? firstTitle
                    : firstTitle + " +" + (dayData.tasks.size() - 1);
            Label taskLabel = new Label(taskText);
            taskLabel.setFont(Font.font("System", FontWeight.NORMAL, 9));
            taskLabel.setTextFill(Color.web("#9b59b6"));
            
            tasksBox.getChildren().addAll(taskIcon, taskLabel);
            metricsBox.getChildren().add(tasksBox);
        }
        
        // Productivity indicator
        String productivityIcon = getProductivityIcon(dayData.productivityScore);
        Label prodLabel = new Label(productivityIcon);
        prodLabel.setFont(Font.font("Noto Emoji", FontWeight.NORMAL, 12));
        metricsBox.getChildren().add(prodLabel);
        
        // Study time indicator
        if (dayData.totalMinutes > 0) {
            Label timeLabel = new Label(formatStudyTime(dayData.totalMinutes));
            timeLabel.setFont(Font.font("System", FontWeight.NORMAL, 8));
            timeLabel.setTextFill(Color.web("#7f8c8d"));
            metricsBox.getChildren().add(timeLabel);
        }
        
        dayCell.getChildren().addAll(dayLabel, metricsBox);
        
        // Make clickable for all dates (past, today, and future)
        dayCell.setOnMouseClicked(e -> {
            selectedDate = date;
            updateCalendarDisplay(); // Refresh to show selection
            showDayDetailDialog(date, dayData);
        });
        dayCell.setOnMouseEntered(e -> {
            if (!isToday && !isSelected) {
                String hoverColor = isFutureDate ? "#e8f5e9" : "#f8f9fa";
                dayCell.setStyle(finalBaseStyle + " -fx-background-color: " + hoverColor + ";");
            }
            dayCell.setStyle(dayCell.getStyle() + " -fx-cursor: hand;");
        });
        dayCell.setOnMouseExited(e -> {
            if (!isToday && !isSelected) {
                dayCell.setStyle(finalBaseStyle);
            }
        });
        
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
            data.tasks = taskService.getTasksForDate(date);
            
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
        } else if (date.isAfter(today)) {
            // For future dates, show all goals planned for that date (no delay processing needed)
            return studyService.getStudyGoalsForFutureDate(date);
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
        if (score >= 70) return "\u2605\u2605"; // High productivity
        else if (score >= 40) return "\u2B50"; // Medium productivity  
        else if (score >= 10) return "\uD83D\uDCC8"; // Low productivity
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
        dialog.initOwner(this.getScene() != null ? this.getScene().getWindow() : null);
        dialog.setTitle("Day Details - " + date.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy")));
        dialog.setHeaderText("» Complete Day Overview");
        
        // Create tabbed content similar to original DailyViewPanel
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setPrefSize(800, 600);
        
        // Overview Tab
        Tab overviewTab = new Tab("▪ Overview", createOverviewTab(date, dayData));
        
        // Goals Tab  
        Tab goalsTab = new Tab("◎ Goals");
        goalsTab.setContent(createGoalsTab(date, goalsTab));
        
        // Sessions Tab
        Tab sessionsTab = new Tab("» Sessions", createSessionsTab(date));
        
        // Tasks Tab
        Tab tasksTab = new Tab("☑ Tasks", createTasksTab(date, dayData));
        
        // Performance Tab
        Tab performanceTab = new Tab("↑ Performance", createPerformanceTab(date, dayData));
        
        tabPane.getTabs().addAll(overviewTab, goalsTab, sessionsTab, tasksTab, performanceTab);
        
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
        // Tasks for this day (used in cell preview and detail dialog)
        List<Task> tasks = List.of();
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
        
        // Tasks
        metricsGrid.add(new Label("Tasks Scheduled:"), 0, 4);
        Label tasksCountLabel = new Label(String.valueOf(dayData.tasks.size()));
        tasksCountLabel.setTextFill(dayData.tasks.isEmpty() ? Color.web("#7f8c8d") : Color.web("#9b59b6"));
        metricsGrid.add(tasksCountLabel, 1, 4);
        
        // Average focus
        metricsGrid.add(new Label("Average Focus:"), 0, 5);
        Label focusLabel = new Label("\u2605".repeat(dayData.avgFocusLevel) + "\u2606".repeat(5 - dayData.avgFocusLevel) + " (" + dayData.avgFocusLevel + "/5)");
        focusLabel.setTextFill(Color.web("#f39c12"));
        metricsGrid.add(focusLabel, 1, 5);
        
        // Productivity score
        metricsGrid.add(new Label("Productivity Score:"), 0, 6);
        Label productivityLabel = new Label(String.format("%.0f%%", dayData.productivityScore));
        productivityLabel.setTextFill(getProductivityColor(dayData.productivityScore));
        metricsGrid.add(productivityLabel, 1, 6);
        
        summarySection.getChildren().addAll(dateLabel, metricsGrid);
        
        // Day reflection if exists
        VBox reflectionSection = createReflectionSection(date);
        
        content.getChildren().addAll(summarySection, reflectionSection);
        return content;
    }
    
    private VBox createReflectionSection(LocalDate date) {
        VBox section = new VBox(10);
        section.setStyle("-fx-background-color: #fff3e0; -fx-background-radius: 10; -fx-padding: 20;");
        
        Label reflectionTitle = new Label("\uD83D\uDCAD Daily Reflection");
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
    
    private VBox createGoalsTab(LocalDate date, Tab parentTab) {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        // Determine if this is a plannable date (today or future)
        boolean isPlannableDate = !date.isBefore(LocalDate.now());
        boolean isFutureDate = date.isAfter(LocalDate.now());
        
        // Add "Plan Goal" button at the top for plannable dates
        if (isPlannableDate) {
            HBox headerBox = new HBox(15);
            headerBox.setAlignment(Pos.CENTER_LEFT);
            
            Button addGoalBtn = new Button("+ Add Study Goal");
            addGoalBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 8 16; -fx-background-radius: 5;");
            addGoalBtn.setOnAction(e -> {
                showAddGoalDialog(date);
                // Refresh the goals tab content after the dialog closes
                parentTab.setContent(createGoalsTab(date, parentTab));
            });
            
            Label hintLabel = new Label(isFutureDate ? "» Plan ahead for this day" : "» Set goals for today");
            hintLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
            hintLabel.setTextFill(Color.web("#7f8c8d"));
            
            headerBox.getChildren().addAll(addGoalBtn, hintLabel);
            content.getChildren().add(headerBox);
        }
        
        List<StudyGoal> studyGoals = getFilteredStudyGoalsForDate(date);
        
        if (studyGoals.isEmpty()) {
            String emptyMessage = isFutureDate 
                ? "\uD83D\uDCC5 No goals planned yet. Click the button above to plan ahead!"
                : "\uD83D\uDCED No goals recorded for this date";
            Label noGoalsLabel = new Label(emptyMessage);
            noGoalsLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
            noGoalsLabel.setTextFill(Color.GRAY);
            noGoalsLabel.setPadding(new Insets(30));
            noGoalsLabel.setWrapText(true);
            content.getChildren().add(noGoalsLabel);
            return content;
        }
        
        Label goalsTitle = new Label("\u25CE Study Goals (" + studyGoals.size() + ")");
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
            Label noSessionsLabel = new Label("\uD83D\uDCED No sessions recorded for this date");
            noSessionsLabel.setFont(Font.font("System", FontWeight.NORMAL, 16));
            noSessionsLabel.setTextFill(Color.GRAY);
            noSessionsLabel.setPadding(new Insets(50));
            content.getChildren().add(noSessionsLabel);
            return content;
        }
        
        // Study Sessions
        if (!studySessions.isEmpty()) {
            Label studyTitle = new Label("» Study Sessions (" + studySessions.size() + ")");
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
            Label projectTitle = new Label("\uD83D\uDE80 Project Sessions (" + projectSessions.size() + ")");
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
    
    private VBox createTasksTab(LocalDate date, DayData dayData) {
        VBox content = new VBox(12);
        content.setPadding(new Insets(20));

        if (dayData.tasks.isEmpty()) {
            Label noTasksLabel = new Label("☑ No tasks scheduled for this day.");
            noTasksLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
            noTasksLabel.setTextFill(Color.web("#7f8c8d"));
            noTasksLabel.setPadding(new Insets(30));
            content.getChildren().add(noTasksLabel);
            return content;
        }

        Label title = new Label("☑ Tasks (" + dayData.tasks.size() + ")");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#9b59b6"));
        content.getChildren().add(title);

        for (Task task : dayData.tasks) {
            VBox taskBox = new VBox(6);
            taskBox.setPadding(new Insets(12));

            String borderColor = taskBorderColor(task);
            taskBox.setStyle("-fx-background-color: white; -fx-background-radius: 8;" +
                    " -fx-border-color: " + borderColor + "; -fx-border-radius: 8;");

            // Title + status row
            HBox headerRow = new HBox(10);
            headerRow.setAlignment(Pos.CENTER_LEFT);

            Label taskTitle = new Label(task.getTitle());
            taskTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
            taskTitle.setTextFill(Color.web("#2c3e50"));

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
            headerRow.getChildren().addAll(taskTitle, spacer, statusBadge);
            taskBox.getChildren().add(headerRow);

            // Description (if non-empty)
            if (task.getDescription() != null && !task.getDescription().isBlank()) {
                Label descLabel = new Label(task.getDescription());
                descLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
                descLabel.setTextFill(Color.web("#555"));
                descLabel.setWrapText(true);
                taskBox.getChildren().add(descLabel);
            }

            // Priority + category row
            HBox metaRow = new HBox(15);
            metaRow.setAlignment(Pos.CENTER_LEFT);
            if (task.getPriority() != null) {
                Label prioLabel = new Label("Priority: " + task.getPriority());
                prioLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
                prioLabel.setTextFill(Color.web("#f39c12"));
                metaRow.getChildren().add(prioLabel);
            }
            if (task.getCategory() != null && !task.getCategory().isBlank()) {
                Label catLabel = new Label("Category: " + task.getCategory());
                catLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
                catLabel.setTextFill(Color.web("#3498db"));
                metaRow.getChildren().add(catLabel);
            }
            if (!metaRow.getChildren().isEmpty()) {
                taskBox.getChildren().add(metaRow);
            }

            content.getChildren().add(taskBox);
        }

        return content;
    }

    private static String taskBorderColor(Task task) {
        return switch (task.getStatus()) {
            case COMPLETED   -> "#27ae60";
            case DELAYED     -> "#e74c3c";
            case IN_PROGRESS -> "#f39c12";
            case CANCELLED   -> "#bdc3c7";
            case POSTPONED   -> "#9c27b0";
            default          -> "#3498db";
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

    private VBox createPerformanceTab(LocalDate date, DayData dayData) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        // Performance summary
        VBox performanceSection = new VBox(15);
        performanceSection.setStyle("-fx-background-color: #f0f8ff; -fx-background-radius: 10; -fx-padding: 20;");
        
        Label performanceTitle = new Label("↑ Performance Analysis");
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
        
        Label recommendationsTitle = new Label("•  Insights & Recommendations");
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
        String statusIcon = goal.isAchieved() ? "\u2705" : (goal.isDelayed() ? "[!] " : "\u25CB");
        String statusText = goal.isAchieved() ? "Achieved" : (goal.isDelayed() ? "Delayed" : "Pending");
        
        Label statusLabel = new Label(statusIcon + " " + statusText);
        statusLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        Label descriptionLabel = new Label("\u25CE " + goal.getDescription());
        descriptionLabel.setFont(Font.font("System", FontWeight.NORMAL, 13));
        descriptionLabel.setWrapText(true);
        
        goalBox.getChildren().addAll(statusLabel, descriptionLabel);
        
        // Add delay info if applicable
        if (goal.isDelayed()) {
            Label delayLabel = new Label(String.format("» Originally from: %s • ♨ %d days delayed", 
                goal.getDate().toString(), goal.getDaysDelayed()));
            delayLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
            delayLabel.setTextFill(Color.web("#ff5722"));
            goalBox.getChildren().add(delayLabel);
        }
        
        // Action buttons
        HBox actionBox = new HBox(8);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button deleteBtn = new Button("✕ Delete");
        deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 10px;");
        deleteBtn.setOnAction(e -> {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.initOwner(goalBox.getScene() != null ? goalBox.getScene().getWindow() : null);
            confirmation.setTitle("Delete Study Goal");
            confirmation.setHeaderText("Are you sure you want to delete this study goal?");
            confirmation.setContentText("Goal: " + goal.getDescription());
            
            confirmation.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    studyService.deleteStudyGoal(goal.getId());
                    updateCalendarDisplay();
                    ((VBox) goalBox.getParent()).getChildren().remove(goalBox);
                }
            });
        });
        
        actionBox.getChildren().add(deleteBtn);
        goalBox.getChildren().add(actionBox);
        
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
        Label focusLabel = new Label("◎ Focus: " + "★".repeat(session.getFocusLevel()) + "☆".repeat(5 - session.getFocusLevel()) + " (" + session.getFocusLevel() + "/5)");
        focusLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
        focusLabel.setTextFill(Color.web("#f39c12"));
        
        Label pointsLabel = new Label("♦ " + session.getPointsEarned() + " points earned");
        pointsLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
        pointsLabel.setTextFill(Color.web("#27ae60"));
        
        sessionBox.getChildren().addAll(timeLabel, focusLabel, pointsLabel);
        
        // Subject and topic if available
        if (session.getSubject() != null && !session.getSubject().trim().isEmpty()) {
            Label subjectLabel = new Label("📖 Subject: " + session.getSubject());
            subjectLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
            sessionBox.getChildren().add(subjectLabel);
        }
        
        // Action buttons
        HBox actionBox = new HBox(8);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button deleteBtn = new Button("✕ Delete");
        deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 10px;");
        deleteBtn.setOnAction(e -> {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.initOwner(sessionBox.getScene() != null ? sessionBox.getScene().getWindow() : null);
            confirmation.setTitle("Delete Study Session");
            confirmation.setHeaderText("Are you sure you want to delete this study session?");
            
            confirmation.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    studyService.deleteStudySession(session.getId());
                    updateCalendarDisplay();
                    ((VBox) sessionBox.getParent()).getChildren().remove(sessionBox);
                }
            });
        });
        
        actionBox.getChildren().add(deleteBtn);
        sessionBox.getChildren().add(actionBox);
        
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
        
        Label pointsLabel = new Label("♦ " + session.getPointsEarned() + " points earned");
        pointsLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
        pointsLabel.setTextFill(Color.web("#27ae60"));
        
        sessionBox.getChildren().addAll(projectLabel, timeLabel, pointsLabel);
        
        // Action buttons
        HBox actionBox = new HBox(8);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button deleteBtn = new Button("✕ Delete");
        deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 10px;");
        deleteBtn.setOnAction(e -> {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.initOwner(sessionBox.getScene() != null ? sessionBox.getScene().getWindow() : null);
            confirmation.setTitle("Delete Project Session");
            confirmation.setHeaderText("Are you sure you want to delete this project session?");
            
            confirmation.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    projectService.deleteProjectSession(session.getId());
                    updateCalendarDisplay();
                    ((VBox) sessionBox.getParent()).getChildren().remove(sessionBox);
                }
            });
        });
        
        actionBox.getChildren().add(deleteBtn);
        sessionBox.getChildren().add(actionBox);
        
        return sessionBox;
    }
    
    /**
     * Shows a dialog to add a new study goal for the specified date.
     * Works for today and future dates to enable planning ahead.
     * 
     * @param date the date to add the goal for
     */
    private void showAddGoalDialog(LocalDate date) {
        Dialog<String> dialog = new Dialog<>();
        dialog.initOwner(this.getScene() != null ? this.getScene().getWindow() : null);
        boolean isFutureDate = date.isAfter(LocalDate.now());
        
        dialog.setTitle(isFutureDate ? "Plan Study Goal" : "Add Study Goal");
        dialog.setHeaderText("Add a study goal for " + date.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy")));
        
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        
        Label instructionLabel = new Label(isFutureDate 
            ? "Plan what you want to achieve on this day:"
            : "What do you want to achieve today?");
        instructionLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
        
        TextArea goalTextArea = new TextArea();
        goalTextArea.setPromptText("e.g., Complete Chapter 5, Practice 10 problems, Review notes from class...");
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
                    updateCalendarDisplay();
                    
                    // Show confirmation
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.initOwner(this.getScene() != null ? this.getScene().getWindow() : null);
                    successAlert.setTitle("Goal Added");
                    successAlert.setHeaderText(null);
                    successAlert.setContentText("Study goal added successfully for " + 
                        date.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")));
                    successAlert.showAndWait();
                } catch (Exception e) {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.initOwner(this.getScene() != null ? this.getScene().getWindow() : null);
                    errorAlert.setTitle("Error");
                    errorAlert.setHeaderText("Failed to add study goal");
                    errorAlert.setContentText(e.getMessage());
                    errorAlert.showAndWait();
                }
            }
        });
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