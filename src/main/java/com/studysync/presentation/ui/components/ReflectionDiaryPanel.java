package com.studysync.presentation.ui.components;

import com.studysync.domain.service.StudyService;
import com.studysync.domain.service.DateTimeService;
import com.studysync.domain.entity.DailyReflection;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.Node;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * A diary-like interface for viewing and managing daily reflections.
 * Allows easy navigation between different dates and reflection entries.
 */
public class ReflectionDiaryPanel extends ScrollPane implements RefreshablePanel {
    private final StudyService studyService;
    private final DateTimeService dateTimeService;
    
    // UI Components
    private DatePicker datePicker;
    private TextArea reflectionTextArea;
    private VBox reflectionContent;
    private Label dateHeaderLabel;
    private Button saveButton;
    private ListView<LocalDate> recentReflectionsList;
    
    public ReflectionDiaryPanel(StudyService studyService, DateTimeService dateTimeService) {
        this.studyService = studyService;
        this.dateTimeService = dateTimeService;
        
        // Create main content container
        VBox mainContent = new VBox(20);
        mainContent.setPadding(new Insets(20));
        mainContent.getStyleClass().add("panel-bg-warm");
        
        // Set up ScrollPane properties
        this.setContent(mainContent);
        this.setFitToWidth(true);
        this.setFitToHeight(false);
        this.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.getStyleClass().add("tab-content-area");
        
        initializeComponents(mainContent);
        updateDisplay();
        
        // Register for date change notifications
        dateTimeService.addDateChangeListener(this::onDateChanged);
    }

    private void initializeComponents(VBox mainContent) {
        // Header
        Label headerLabel = new Label("📔 Daily Reflection Diary");
        TaskStyleUtils.fontBold(headerLabel, 24);
        
        // Create main layout with sidebar and content
        HBox mainLayout = new HBox(20);
        mainLayout.setPrefHeight(600);
        
        // Left sidebar for navigation
        VBox sidebar = createSidebar();
        sidebar.setPrefWidth(250);
        sidebar.setMaxWidth(250);
        
        // Right content area
        VBox contentArea = createContentArea();
        HBox.setHgrow(contentArea, Priority.ALWAYS);
        
        mainLayout.getChildren().addAll(sidebar, contentArea);
        
        mainContent.getChildren().addAll(headerLabel, mainLayout);
    }
    
    private VBox createSidebar() {
        VBox sidebar = new VBox(15);
        sidebar.getStyleClass().add("section-card");
        sidebar.setPadding(new Insets(15));
        
        Label sidebarTitle = new Label("» Navigation");
        TaskStyleUtils.fontBold(sidebarTitle, 16);
        
        // Date picker for navigation
        Label dateLabel = new Label("Select Date:");
        TaskStyleUtils.fontSemiBold(dateLabel, 12);
        
        datePicker = new DatePicker(dateTimeService.getCurrentDate());
        datePicker.setMaxWidth(Double.MAX_VALUE);
        datePicker.setOnAction(e -> loadReflectionForDate(datePicker.getValue()));
        
        // Quick navigation buttons
        VBox quickNav = new VBox(5);
        Button todayBtn = new Button("Today");
        todayBtn.setMaxWidth(Double.MAX_VALUE);
        todayBtn.getStyleClass().add("btn-primary");
        todayBtn.setOnAction(e -> navigateToDate(dateTimeService.getCurrentDate()));
        
        Button yesterdayBtn = new Button("Yesterday");
        yesterdayBtn.setMaxWidth(Double.MAX_VALUE);
        yesterdayBtn.getStyleClass().add("btn-purple");
        yesterdayBtn.setOnAction(e -> navigateToDate(dateTimeService.getCurrentDate().minusDays(1)));
        
        Button lastWeekBtn = new Button("One Week Ago");
        lastWeekBtn.setMaxWidth(Double.MAX_VALUE);
        lastWeekBtn.getStyleClass().add("btn-orange");
        lastWeekBtn.setOnAction(e -> navigateToDate(dateTimeService.getCurrentDate().minusDays(7)));
        
        quickNav.getChildren().addAll(todayBtn, yesterdayBtn, lastWeekBtn);
        
        // Recent reflections list
        Label recentLabel = new Label("Recent Reflections:");
        TaskStyleUtils.fontSemiBold(recentLabel, 12);
        
        recentReflectionsList = new ListView<>();
        recentReflectionsList.setPrefHeight(200);
        recentReflectionsList.setCellFactory(param -> new ListCell<LocalDate>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String dateText = item.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
                    if (item.equals(dateTimeService.getCurrentDate())) {
                        dateText += " (Today)";
                    } else if (item.equals(dateTimeService.getCurrentDate().minusDays(1))) {
                        dateText += " (Yesterday)";
                    }
                    setText(dateText);
                }
            }
        });
        recentReflectionsList.setOnMouseClicked(e -> {
            LocalDate selectedDate = recentReflectionsList.getSelectionModel().getSelectedItem();
            if (selectedDate != null) {
                navigateToDate(selectedDate);
            }
        });
        
        loadRecentReflections();
        
        sidebar.getChildren().addAll(
            sidebarTitle,
            new Separator(),
            dateLabel, datePicker,
            new Label("Quick Navigation:"), quickNav,
            new Separator(),
            recentLabel, recentReflectionsList
        );
        
        return sidebar;
    }
    
    private VBox createContentArea() {
        VBox contentArea = new VBox(15);
        contentArea.getStyleClass().add("section-card");
        
        // Date header
        dateHeaderLabel = new Label();
        TaskStyleUtils.fontBold(dateHeaderLabel, 18);
        
        // Reflection text area
        Label reflectionLabel = new Label("💭 Daily Reflection:");
        TaskStyleUtils.fontSemiBold(reflectionLabel, 14);
        
        reflectionTextArea = new TextArea();
        reflectionTextArea.setPromptText("Write your daily reflection here...\n\nSome questions to consider:\n• What went well today?\n• What challenges did you face?\n• What did you learn?\n• What would you do differently?\n• How do you feel about your progress?");
        reflectionTextArea.setPrefRowCount(15);
        reflectionTextArea.setWrapText(true);
        reflectionTextArea.setStyle("-fx-font-family: 'Georgia', 'Times New Roman', serif; -fx-font-size: 13px; -fx-line-spacing: 1.2em;");
        
        // Save button
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        
        saveButton = new Button("💾 Save Reflection");
        saveButton.getStyleClass().add("btn-success");
        saveButton.setOnAction(e -> saveCurrentReflection());
        
        Button newReflectionBtn = new Button("▪ New Entry");
        newReflectionBtn.getStyleClass().add("btn-primary");
        newReflectionBtn.setOnAction(e -> createNewReflection());
        
        buttonBox.getChildren().addAll(newReflectionBtn, saveButton);
        
        reflectionContent = new VBox(10);
        reflectionContent.getChildren().addAll(dateHeaderLabel, reflectionLabel, reflectionTextArea, buttonBox);
        
        contentArea.getChildren().add(reflectionContent);
        
        return contentArea;
    }
    
    private void navigateToDate(LocalDate date) {
        datePicker.setValue(date);
        loadReflectionForDate(date);
    }
    
    private void loadReflectionForDate(LocalDate date) {
        if (date == null) {
            date = dateTimeService.getCurrentDate();
        }
        
        // Update header
        String dateStr = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"));
        if (date.equals(dateTimeService.getCurrentDate())) {
            dateStr += " (Today)";
        } else if (date.equals(dateTimeService.getCurrentDate().minusDays(1))) {
            dateStr += " (Yesterday)";
        }
        dateHeaderLabel.setText("» " + dateStr);
        
        // Load reflection for the date
        try {
            Optional<DailyReflection> reflectionOpt = DailyReflection.findByDate(date);
            if (reflectionOpt.isPresent()) {
                DailyReflection reflection = reflectionOpt.get();
                reflectionTextArea.setText(reflection.getReflectionText() != null ? reflection.getReflectionText() : "");
                saveButton.setText("💾 Update Reflection");
            } else {
                reflectionTextArea.setText("");
                saveButton.setText("💾 Save Reflection");
            }
        } catch (Exception e) {
            reflectionTextArea.setText("");
            System.err.println("Error loading reflection for " + date + ": " + e.getMessage());
        }
        
        // Update recent reflections list to highlight current selection
        loadRecentReflections();
    }
    
    private void loadRecentReflections() {
        try {
            List<DailyReflection> recentReflections = DailyReflection.findRecent(30); // Last 30 days
            recentReflectionsList.getItems().clear();
            
            for (DailyReflection reflection : recentReflections) {
                recentReflectionsList.getItems().add(reflection.getDate());
            }
            
            // Highlight current date if it exists
            LocalDate currentDate = datePicker.getValue();
            if (currentDate != null && recentReflectionsList.getItems().contains(currentDate)) {
                recentReflectionsList.getSelectionModel().select(currentDate);
            }
        } catch (Exception e) {
            System.err.println("Error loading recent reflections: " + e.getMessage());
        }
    }
    
    private void saveCurrentReflection() {
        LocalDate selectedDate = datePicker.getValue();
        if (selectedDate == null) {
            selectedDate = dateTimeService.getCurrentDate();
        }
        
        String reflectionText = reflectionTextArea.getText().trim();
        
        try {
            Optional<DailyReflection> existingOpt = DailyReflection.findByDate(selectedDate);
            DailyReflection reflection;
            
            if (existingOpt.isPresent()) {
                reflection = existingOpt.get();
                reflection.setReflectionText(reflectionText);
            } else {
                reflection = new DailyReflection();
                reflection.setDate(selectedDate);
                reflection.setReflectionText(reflectionText);
            }
            
            studyService.addDailyReflection(reflection);
            
            // Show success message
            Alert success = new Alert(Alert.AlertType.INFORMATION);
            success.initOwner(this.getScene() != null ? this.getScene().getWindow() : null);
            success.setTitle("Success");
            success.setHeaderText(null);
            success.setContentText("Reflection saved successfully for " + selectedDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
            success.showAndWait();
            
            // Refresh recent reflections
            loadRecentReflections();
            
        } catch (Exception e) {
            Alert error = new Alert(Alert.AlertType.ERROR);
            error.initOwner(this.getScene() != null ? this.getScene().getWindow() : null);
            error.setTitle("Error");
            error.setHeaderText(null);
            error.setContentText("Failed to save reflection: " + e.getMessage());
            error.showAndWait();
        }
    }
    
    private void createNewReflection() {
        navigateToDate(dateTimeService.getCurrentDate());
        reflectionTextArea.clear();
        reflectionTextArea.requestFocus();
    }
    
    private void onDateChanged(LocalDate newDate) {
        // Auto-navigate to the new date if currently viewing today
        if (datePicker.getValue().equals(dateTimeService.getCurrentDate().minusDays(1))) {
            navigateToDate(newDate);
        }
    }
    
    @Override
    public void updateDisplay() {
        loadReflectionForDate(datePicker.getValue());
    }
    
    @Override
    public Node getView() {
        return this;
    }
}