package com.studysync.presentation.ui;

import com.studysync.domain.service.CategoryService;
import com.studysync.domain.service.DateTimeService;
import com.studysync.domain.service.ProjectService;
import com.studysync.domain.service.ReminderService;
import com.studysync.domain.service.StudyService;
import com.studysync.domain.service.TaskService;
import com.studysync.integration.drive.GoogleDriveService;
import com.studysync.presentation.ui.components.CalendarViewPanel;
import com.studysync.presentation.ui.components.ProjectManagementPanel;
import com.studysync.presentation.ui.components.ProfileViewPanel;
import com.studysync.presentation.ui.components.RefreshablePanel;
import com.studysync.presentation.ui.components.ReflectionDiaryPanel;
import com.studysync.presentation.ui.components.StudyPlannerPanel;
import com.studysync.presentation.ui.components.TaskManagementPanel;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.image.Image;
import javafx.scene.input.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Component
@DependsOn("activeRecordConfig")
public class StudySyncUI {
    
    private static final Logger logger = LoggerFactory.getLogger(StudySyncUI.class);
    private static final double DEFAULT_WINDOW_WIDTH = 1200;
    private static final double DEFAULT_WINDOW_HEIGHT = 800;
    
    private final TaskService taskService;
    private final CategoryService categoryService;
    private final ReminderService reminderService;
    private final StudyService studyService;
    private final ProjectService projectService;
    private final DateTimeService dateTimeService;
    private final GoogleDriveService googleDriveService;
    private final Map<Tab, RefreshablePanel> panelMap;
    private TabPane tabPane;

    @Autowired
    public StudySyncUI(TaskService taskService, CategoryService categoryService, ReminderService reminderService,
                       StudyService studyService, ProjectService projectService,
                       DateTimeService dateTimeService, GoogleDriveService googleDriveService) {
        this.taskService = Objects.requireNonNull(taskService, "taskService");
        this.categoryService = Objects.requireNonNull(categoryService, "categoryService");
        this.reminderService = Objects.requireNonNull(reminderService, "reminderService");
        this.studyService = Objects.requireNonNull(studyService, "studyService");
        this.projectService = Objects.requireNonNull(projectService, "projectService");
        this.dateTimeService = Objects.requireNonNull(dateTimeService, "dateTimeService");
        this.googleDriveService = Objects.requireNonNull(googleDriveService, "googleDriveService");

        Map<Tab, RefreshablePanel> panels = new LinkedHashMap<>();
        panels.put(new Tab("📅 Calendar View"), new CalendarViewPanel(this.studyService, this.taskService, this.projectService));
        panels.put(new Tab("📚 Study Planner"), new StudyPlannerPanel(this.studyService, this.dateTimeService, this.taskService));
        panels.put(new Tab("⭐ Reflection Diary"), new ReflectionDiaryPanel(this.studyService, this.dateTimeService));
        panels.put(new Tab("🖊️ Projects"), new ProjectManagementPanel(this.projectService, this.categoryService));
        panels.put(new Tab("📋 Tasks"), new TaskManagementPanel(this.taskService, this.categoryService, this.reminderService));
        panelMap = Collections.unmodifiableMap(panels);
    }

    public void start(Stage primaryStage) {
        this.tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        panelMap.forEach((tab, panel) -> tab.setContent(panel.getView()));
        
        // Initialize tabs in the default order
        restoreTabOrder();
        
        // Setup drag and drop functionality for tabs (delayed to allow tab rendering)
        Platform.runLater(this::setupTabDragAndDrop);
        
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && panelMap.containsKey(newTab)) {
                panelMap.get(newTab).updateDisplay();
            }
        });
        
        // Create main layout with header
        BorderPane mainLayout = new BorderPane();
        
        // Create header with profile button
        HBox header = createHeader();
        mainLayout.setTop(header);
        
        // Add tabPane to center
        mainLayout.setCenter(tabPane);
        
        Scene scene = new Scene(mainLayout, DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        
        primaryStage.setTitle("StudySync");
        
        try {
            var iconStream = getClass().getResourceAsStream("/icon.png");
            if (iconStream != null) {
                primaryStage.getIcons().add(new Image(iconStream));
            } else {
                logger.warn("Application icon not found in resources");
            }
        } catch (Exception e) {
            logger.warn("Failed to load application icon: {}", e.getMessage());
        }
        
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();

        primaryStage.show();
        
        // Process yesterday's unachieved goals automatically
        processDelayedGoalsOnStartup();
        
        // Initialize the default tab after showing the window (to ensure Spring context is fully loaded)
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null && panelMap.containsKey(selectedTab)) {
            panelMap.get(selectedTab).updateDisplay();
        }
    }
    
    private void restoreTabOrder() {
        tabPane.getTabs().addAll(panelMap.keySet());
    }
    
    private void setupTabDragAndDrop() {
        // For simplicity, we'll implement a basic version using mouse events
        // This approach detects clicks on tab headers and allows reordering via keyboard shortcuts
        tabPane.setOnKeyPressed(event -> {
            Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
            if (selectedTab != null && event.isControlDown()) {
                int currentIndex = tabPane.getTabs().indexOf(selectedTab);
                int newIndex = -1;
                
                if (event.getCode() == KeyCode.LEFT && currentIndex > 0) {
                    newIndex = currentIndex - 1;
                } else if (event.getCode() == KeyCode.RIGHT && currentIndex < tabPane.getTabs().size() - 1) {
                    newIndex = currentIndex + 1;
                }
                
                if (newIndex != -1) {
                    tabPane.getTabs().remove(currentIndex);
                    tabPane.getTabs().add(newIndex, selectedTab);
                    tabPane.getSelectionModel().select(selectedTab);
                    event.consume();
                }
            }
        });
    }
    
    private HBox createHeader() {
        HBox header = new HBox();
        header.setPadding(new Insets(10, 15, 10, 15));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: linear-gradient(to right, #3498db, #2980b9); -fx-border-color: #2980b9; -fx-border-width: 0 0 2 0;");
        
        // App title/logo area
        Label appTitle = new Label("StudySync");
        appTitle.setFont(Font.font("System", FontWeight.BOLD, 20));
        appTitle.setTextFill(Color.WHITE);
        
        // Spacer to push profile button to the right
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Profile button
        Button profileButton = new Button("👤 Profile");
        profileButton.setStyle("-fx-background-color: rgba(255,255,255,0.2); -fx-text-fill: white; -fx-border-color: rgba(255,255,255,0.5); -fx-border-radius: 5; -fx-background-radius: 5; -fx-font-weight: bold;");
        profileButton.setOnMouseEntered(e -> profileButton.setStyle("-fx-background-color: rgba(255,255,255,0.3); -fx-text-fill: white; -fx-border-color: rgba(255,255,255,0.7); -fx-border-radius: 5; -fx-background-radius: 5; -fx-font-weight: bold;"));
        profileButton.setOnMouseExited(e -> profileButton.setStyle("-fx-background-color: rgba(255,255,255,0.2); -fx-text-fill: white; -fx-border-color: rgba(255,255,255,0.5); -fx-border-radius: 5; -fx-background-radius: 5; -fx-font-weight: bold;"));
        
        profileButton.setOnAction(e -> showProfileWindow());
        
        header.getChildren().addAll(appTitle, spacer, profileButton);
        return header;
    }
    
    private void processDelayedGoalsOnStartup() {
        try {
            StudyService.GoalDelayProcessingResult result = studyService.processAllDelayedGoals();
            if (result.updatedGoals() > 0) {
                logger.info("Updated delay status for {} study goals carried over from previous days", result.updatedGoals());
            }
            if (result.removedGoals() > 0) {
                logger.info("Auto-removed {} study goals overdue by at least two weeks", result.removedGoals());
            }
        } catch (Exception e) {
            logger.error("Failed to process delayed goals on startup", e);
        }
    }
    
    private void showProfileWindow() {
        // Create a new stage for the profile window
        Stage profileStage = new Stage();
        profileStage.setTitle("Study Profile & Analytics");
        profileStage.initOwner(tabPane.getScene().getWindow());
        
        // Create the profile panel
        ProfileViewPanel profilePanel = new ProfileViewPanel(studyService, projectService, taskService, dateTimeService, googleDriveService);
        
        Scene profileScene = new Scene(profilePanel, 1000, 700);
        profileScene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        
        profileStage.setScene(profileScene);
        profileStage.show();
        
        // Update the profile display
        profilePanel.updateDisplay();
    }
}
