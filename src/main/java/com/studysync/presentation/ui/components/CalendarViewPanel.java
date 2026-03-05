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

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.Node;

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
        mainContainer.getStyleClass().add("panel-bg-alt");
        
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
        headerSection.getStyleClass().add("section-card");
        
        // Title and navigation
        HBox titleBox = new HBox(20);
        titleBox.setAlignment(Pos.CENTER);
        
        Button prevMonthBtn = new Button("◀ Previous");
        prevMonthBtn.getStyleClass().add("btn-primary");
        prevMonthBtn.setOnAction(e -> {
            currentMonth = currentMonth.minusMonths(1);
            updateCalendarDisplay();
        });
        
        monthYearLabel = new Label();
        TaskStyleUtils.fontBold(monthYearLabel, 24);
        
        Button nextMonthBtn = new Button("Next ▶");
        nextMonthBtn.getStyleClass().add("btn-primary");
        nextMonthBtn.setOnAction(e -> {
            currentMonth = currentMonth.plusMonths(1);
            updateCalendarDisplay();
        });
        
        Button todayBtn = new Button("» Today");
        todayBtn.getStyleClass().add("btn-success");
        todayBtn.setOnAction(e -> {
            currentMonth = YearMonth.now();
            selectedDate = LocalDate.now();
            updateCalendarDisplay();
        });
        
        titleBox.getChildren().addAll(prevMonthBtn, monthYearLabel, nextMonthBtn, todayBtn);
        
        // Quick stats for current month
        Label statsLabel = new Label("Click on any day to view detailed information including goals, sessions, and performance metrics.");
        TaskStyleUtils.fontNormal(statsLabel, 14);
        statsLabel.setTextFill(Color.web("#7f8c8d"));
        statsLabel.setWrapText(true);
        
        headerSection.getChildren().addAll(titleBox, statsLabel);
        mainContainer.getChildren().add(headerSection);
    }
    
    private void createCalendarGrid() {
        VBox calendarSection = new VBox(10);
        calendarSection.getStyleClass().add("section-card");
        calendarSection.setPadding(new Insets(15));
        
        // Day headers (Mon, Tue, Wed, etc.)
        HBox dayHeaders = new HBox();
        dayHeaders.setAlignment(Pos.CENTER);
        String[] dayNames = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        
        for (String dayName : dayNames) {
            Label dayLabel = new Label(dayName);
            dayLabel.setPrefWidth(CELL_WIDTH);
            dayLabel.setAlignment(Pos.CENTER);
            dayLabel.setStyle("-fx-background-color: #ecf0f1; -fx-padding: 10;");
            TaskStyleUtils.fontBold(dayLabel, 14);
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
        legendSection.getStyleClass().add("section-card");
        legendSection.setPadding(new Insets(15));
        
        Label legendTitle = new Label("» Calendar Legend");
        TaskStyleUtils.fontBold(legendTitle, 16);
        
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
        TaskStyleUtils.fontEmoji(iconLabel, 16);
        
        Label textLabel = new Label(text);
        TaskStyleUtils.fontNormal(textLabel, 10);
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
        TaskStyleUtils.fontBold(dayLabel, 16);
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
            TaskStyleUtils.fontEmoji(sessionIcon, 12);
            
            Label sessionText = new Label(dayData.totalSessions + "s");
            TaskStyleUtils.fontNormal(sessionText, 9);
            sessionText.setTextFill(Color.web("#3498db"));
            
            // Focus rating stars
            String focusStars = "\u2605".repeat(Math.max(0, Math.min(5, dayData.avgFocusLevel))) + 
                               "\u2606".repeat(Math.max(0, 5 - Math.max(0, dayData.avgFocusLevel)));
            Label focusLabel = new Label(focusStars);
            TaskStyleUtils.fontEmoji(focusLabel, 10);
            focusLabel.setTextFill(Color.web("#f39c12"));
            
            sessionsBox.getChildren().addAll(sessionIcon, sessionText, focusLabel);
            metricsBox.getChildren().add(sessionsBox);
        }
        
        // Goals indicator
        if (dayData.totalGoals > 0) {
            HBox goalsBox = new HBox(3);
            goalsBox.setAlignment(Pos.CENTER_LEFT);
            
            Label goalIcon = new Label("\u25CE");
            TaskStyleUtils.fontEmoji(goalIcon, 12);
            
            Label goalText = new Label(dayData.achievedGoals + "/" + dayData.totalGoals);
            TaskStyleUtils.fontNormal(goalText, 9);
            goalText.setTextFill(dayData.achievedGoals == dayData.totalGoals ? Color.web("#27ae60") : Color.web("#e74c3c"));
            
            goalsBox.getChildren().addAll(goalIcon, goalText);
            metricsBox.getChildren().add(goalsBox);
        }
        
        // Tasks indicator — split overdue / missed / due-today / normal
        if (!dayData.tasks.isEmpty()) {
            LocalDate today = LocalDate.now();
            long overdueCount = dayData.tasks.stream()
                    .filter(t -> TaskStyleUtils.isOverdue(t, date)).count();
            long dueTodayCount = dayData.tasks.stream()
                    .filter(t -> TaskStyleUtils.isDueToday(t, date)).count();

            // Missed recurring-task occurrences (past dates only)
            long missedCount = 0;
            if (date.isBefore(today)) {
                missedCount = dayData.tasks.stream()
                        .filter(Task::isRecurring)
                        .filter(t -> !StudyGoal.hasAchievedGoalForTask(t.getId(), date))
                        .count();
            }
            long handledRecurring = dayData.tasks.stream().filter(Task::isRecurring).count() - missedCount;
            long normalCount = handledRecurring
                    + (dayData.tasks.size() - overdueCount - dueTodayCount
                       - dayData.tasks.stream().filter(Task::isRecurring).count());

            // Overdue line (red)
            if (overdueCount > 0) {
                HBox overdueBox = new HBox(3);
                overdueBox.setAlignment(Pos.CENTER_LEFT);
                Label warnIcon = new Label("\u26A0");
                TaskStyleUtils.fontNormal(warnIcon, 10);
                warnIcon.setTextFill(Color.web(TaskStyleUtils.OVERDUE_COLOR));
                Label overdueLabel = new Label(overdueCount + " overdue");
                TaskStyleUtils.fontNormal(overdueLabel, 9);
                overdueLabel.setTextFill(Color.web(TaskStyleUtils.OVERDUE_COLOR));
                overdueBox.getChildren().addAll(warnIcon, overdueLabel);
                metricsBox.getChildren().add(overdueBox);
            }

            // Missed recurring line (red)
            if (missedCount > 0) {
                HBox missedBox = new HBox(3);
                missedBox.setAlignment(Pos.CENTER_LEFT);
                Label missedIcon = new Label("\u26A0");
                TaskStyleUtils.fontNormal(missedIcon, 10);
                missedIcon.setTextFill(Color.web(TaskStyleUtils.MISSED_COLOR));
                Label missedLabel = new Label(missedCount + " missed");
                TaskStyleUtils.fontNormal(missedLabel, 9);
                missedLabel.setTextFill(Color.web(TaskStyleUtils.MISSED_COLOR));
                missedBox.getChildren().addAll(missedIcon, missedLabel);
                metricsBox.getChildren().add(missedBox);
            }

            // Due-today line (amber)
            if (dueTodayCount > 0) {
                HBox dueTodayBox = new HBox(3);
                dueTodayBox.setAlignment(Pos.CENTER_LEFT);
                Label dueIcon = new Label("☑");
                TaskStyleUtils.fontNormal(dueIcon, 10);
                dueIcon.setTextFill(Color.web(TaskStyleUtils.DUE_TODAY_COLOR));
                Label dueLabel = new Label(dueTodayCount + " due");
                TaskStyleUtils.fontNormal(dueLabel, 9);
                dueLabel.setTextFill(Color.web(TaskStyleUtils.DUE_TODAY_COLOR));
                dueTodayBox.getChildren().addAll(dueIcon, dueLabel);
                metricsBox.getChildren().add(dueTodayBox);
            }

            // Normal tasks line (purple — handled recurring or no special state)
            if (normalCount > 0) {
                HBox tasksBox = new HBox(3);
                tasksBox.setAlignment(Pos.CENTER_LEFT);
                Label taskIcon = new Label("☑");
                TaskStyleUtils.fontNormal(taskIcon, 11);
                taskIcon.setTextFill(Color.web("#9b59b6"));
                Label taskLabel = new Label(normalCount + " task" + (normalCount > 1 ? "s" : ""));
                TaskStyleUtils.fontNormal(taskLabel, 9);
                taskLabel.setTextFill(Color.web("#9b59b6"));
                tasksBox.getChildren().addAll(taskIcon, taskLabel);
                metricsBox.getChildren().add(tasksBox);
            }
        }
        
        // Productivity indicator
        String productivityIcon = getProductivityIcon(dayData.productivityScore);
        Label prodLabel = new Label(productivityIcon);
        TaskStyleUtils.fontEmoji(prodLabel, 12);
        metricsBox.getChildren().add(prodLabel);
        
        // Study time indicator
        if (dayData.totalMinutes > 0) {
            Label timeLabel = new Label(formatStudyTime(dayData.totalMinutes));
            TaskStyleUtils.fontNormal(timeLabel, 8);
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
        
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.setContent(tabPane);
        dialogPane.getButtonTypes().add(ButtonType.CLOSE);
        dialogPane.setMinSize(820, 640);
        dialogPane.setPrefSize(820, 640);
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
        TaskStyleUtils.fontBold(dateLabel, 20);
        
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
        TaskStyleUtils.fontBold(reflectionTitle, 16);
        
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
                TaskStyleUtils.fontItalic(noReflectionLabel, 12);
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
            addGoalBtn.getStyleClass().add("btn-purple");
            addGoalBtn.setStyle("-fx-font-size: 12px; -fx-padding: 8 16;");
            addGoalBtn.setOnAction(e -> {
                showAddGoalDialog(date);
                // Refresh the goals tab content after the dialog closes
                parentTab.setContent(createGoalsTab(date, parentTab));
            });
            
            Label hintLabel = new Label(isFutureDate ? "» Plan ahead for this day" : "» Set goals for today");
            TaskStyleUtils.fontNormal(hintLabel, 11);
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
            TaskStyleUtils.fontNormal(noGoalsLabel, 14);
            noGoalsLabel.setTextFill(Color.GRAY);
            noGoalsLabel.setPadding(new Insets(30));
            noGoalsLabel.setWrapText(true);
            content.getChildren().add(noGoalsLabel);
            return content;
        }
        
        Label goalsTitle = new Label("\u25CE Study Goals (" + studyGoals.size() + ")");
        TaskStyleUtils.fontBold(goalsTitle, 18);
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
            TaskStyleUtils.fontNormal(noSessionsLabel, 16);
            noSessionsLabel.setTextFill(Color.GRAY);
            noSessionsLabel.setPadding(new Insets(50));
            content.getChildren().add(noSessionsLabel);
            return content;
        }
        
        // Study Sessions
        if (!studySessions.isEmpty()) {
            Label studyTitle = new Label("» Study Sessions (" + studySessions.size() + ")");
            TaskStyleUtils.fontBold(studyTitle, 16);
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
            TaskStyleUtils.fontBold(projectTitle, 16);
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
            TaskStyleUtils.fontNormal(noTasksLabel, 14);
            noTasksLabel.setTextFill(Color.web("#7f8c8d"));
            noTasksLabel.setPadding(new Insets(30));
            content.getChildren().add(noTasksLabel);
            return content;
        }

        Label title = new Label("☑ Tasks (" + dayData.tasks.size() + ")");
        TaskStyleUtils.fontBold(title, 18);
        title.setTextFill(Color.web("#9b59b6"));
        content.getChildren().add(title);

        for (Task task : dayData.tasks) {
            VBox taskBox = new VBox(6);
            taskBox.setPadding(new Insets(12));

            String borderColor = TaskStyleUtils.taskBorderColor(task, date);
            taskBox.setStyle("-fx-background-color: white; -fx-background-radius: 8;" +
                    " -fx-border-color: " + borderColor + "; -fx-border-radius: 8;");

            // Title + status row
            HBox headerRow = new HBox(10);
            headerRow.setAlignment(Pos.CENTER_LEFT);

            Label taskTitle = new Label(task.getTitle());
            TaskStyleUtils.fontBold(taskTitle, 14);

            Label statusBadge = new Label(task.getStatus().name());
            statusBadge.setPadding(new Insets(2, 6, 2, 6));
            statusBadge.setStyle("-fx-background-color: " + TaskStyleUtils.statusBadgeBg(task.getStatus()) +
                    "; -fx-background-radius: 10;");
            TaskStyleUtils.fontBold(statusBadge, 10);
            statusBadge.setTextFill(TaskStyleUtils.statusTextColor(task.getStatus()));

            if (task.isRecurring()) {
                Label recurBadge = new Label("\uD83D\uDD01");
                TaskStyleUtils.fontNormal(recurBadge, 12);
                recurBadge.setTooltip(new Tooltip(task.getRecurringSummary()));
                headerRow.getChildren().add(recurBadge);
            }

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            headerRow.getChildren().addAll(taskTitle, spacer);

            // Overdue / due-today / missed badge (between spacer and status badge)
            if (TaskStyleUtils.isOverdue(task, date)) {
                headerRow.getChildren().add(TaskStyleUtils.createOverdueBadge());
            } else if (TaskStyleUtils.isDueToday(task, date)) {
                headerRow.getChildren().add(TaskStyleUtils.createDueTodayBadge());
            } else if (task.isRecurring() && date.isBefore(LocalDate.now())
                       && !StudyGoal.hasAchievedGoalForTask(task.getId(), date)) {
                headerRow.getChildren().add(TaskStyleUtils.createMissedBadge());
                // Red border for missed recurring occurrence
                taskBox.setStyle("-fx-background-color: white; -fx-background-radius: 8;" +
                        " -fx-border-color: " + TaskStyleUtils.MISSED_COLOR + "; -fx-border-radius: 8;");
            }

            headerRow.getChildren().add(statusBadge);
            taskBox.getChildren().add(headerRow);

            // Description (if non-empty)
            if (task.getDescription() != null && !task.getDescription().isBlank()) {
                Label descLabel = new Label(task.getDescription());
                TaskStyleUtils.fontNormal(descLabel, 12);
                descLabel.setTextFill(Color.web("#555"));
                descLabel.setWrapText(true);
                taskBox.getChildren().add(descLabel);
            }

            // Priority + category row
            HBox metaRow = new HBox(15);
            metaRow.setAlignment(Pos.CENTER_LEFT);
            if (task.getPriority() != null) {
                Label prioLabel = new Label("Priority: " + task.getPriority());
                TaskStyleUtils.fontNormal(prioLabel, 11);
                prioLabel.setTextFill(Color.web("#f39c12"));
                metaRow.getChildren().add(prioLabel);
            }
            if (task.getCategory() != null && !task.getCategory().isBlank()) {
                Label catLabel = new Label("Category: " + task.getCategory());
                TaskStyleUtils.fontNormal(catLabel, 11);
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

    private VBox createPerformanceTab(LocalDate date, DayData dayData) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        // Performance summary
        VBox performanceSection = new VBox(15);
        performanceSection.setStyle("-fx-background-color: #f0f8ff; -fx-background-radius: 10; -fx-padding: 20;");
        
        Label performanceTitle = new Label("↑ Performance Analysis");
        TaskStyleUtils.fontBold(performanceTitle, 18);
        
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
            TaskStyleUtils.fontNormal(goalAnalysis, 12);
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
        TaskStyleUtils.fontBold(recommendationsTitle, 16);
        
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
        TaskStyleUtils.fontNormal(label, 12);
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
        TaskStyleUtils.fontBold(statusLabel, 12);
        
        Label descriptionLabel = new Label("\u25CE " + goal.getDescription());
        TaskStyleUtils.fontNormal(descriptionLabel, 13);
        descriptionLabel.setWrapText(true);
        
        goalBox.getChildren().addAll(statusLabel, descriptionLabel);
        
        // Add delay info if applicable
        if (goal.isDelayed()) {
            Label delayLabel = new Label(String.format("» Originally from: %s • ♨ %d days delayed", 
                goal.getDate().toString(), goal.getDaysDelayed()));
            TaskStyleUtils.fontNormal(delayLabel, 11);
            delayLabel.setTextFill(Color.web("#ff5722"));
            goalBox.getChildren().add(delayLabel);
        }
        
        // Action buttons
        HBox actionBox = new HBox(8);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button deleteBtn = new Button("✕ Delete");
        deleteBtn.getStyleClass().addAll("btn-danger", "btn-small");
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
        TaskStyleUtils.fontBold(timeLabel, 12);
        
        // Focus and points
        Label focusLabel = new Label("◎ Focus: " + "★".repeat(session.getFocusLevel()) + "☆".repeat(5 - session.getFocusLevel()) + " (" + session.getFocusLevel() + "/5)");
        TaskStyleUtils.fontNormal(focusLabel, 11);
        focusLabel.setTextFill(Color.web("#f39c12"));
        
        Label pointsLabel = new Label("♦ " + session.getPointsEarned() + " points earned");
        TaskStyleUtils.fontNormal(pointsLabel, 11);
        pointsLabel.setTextFill(Color.web("#27ae60"));
        
        sessionBox.getChildren().addAll(timeLabel, focusLabel, pointsLabel);
        
        // Subject and topic if available
        if (session.getSubject() != null && !session.getSubject().trim().isEmpty()) {
            Label subjectLabel = new Label("📖 Subject: " + session.getSubject());
            TaskStyleUtils.fontNormal(subjectLabel, 11);
            sessionBox.getChildren().add(subjectLabel);
        }
        
        // Action buttons
        HBox actionBox = new HBox(8);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button deleteBtn = new Button("✕ Delete");
        deleteBtn.getStyleClass().addAll("btn-danger", "btn-small");
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
        TaskStyleUtils.fontBold(projectLabel, 12);
        
        // Time and duration
        String startTime = session.getStartTime() != null ? 
            session.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "Unknown";
        
        Label timeLabel = new Label("⏰ " + startTime + " (" + session.getDurationMinutes() + " min)");
        TaskStyleUtils.fontNormal(timeLabel, 11);
        
        Label pointsLabel = new Label("♦ " + session.getPointsEarned() + " points earned");
        TaskStyleUtils.fontNormal(pointsLabel, 11);
        pointsLabel.setTextFill(Color.web("#27ae60"));
        
        sessionBox.getChildren().addAll(projectLabel, timeLabel, pointsLabel);
        
        // Action buttons
        HBox actionBox = new HBox(8);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button deleteBtn = new Button("✕ Delete");
        deleteBtn.getStyleClass().addAll("btn-danger", "btn-small");
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
        TaskStyleUtils.fontNormal(instructionLabel, 12);
        
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