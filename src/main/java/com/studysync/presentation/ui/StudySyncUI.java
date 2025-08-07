
package com.studysync.presentation.ui;

import com.studysync.domain.service.*;
import com.studysync.presentation.ui.components.*;
import com.studysync.domain.service.WindowPreferencesService;
import com.studysync.config.ActiveRecordConfig;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.DependsOn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@Component
@DependsOn("activeRecordConfig")
public class StudySyncUI {
    
    private static final Logger logger = LoggerFactory.getLogger(StudySyncUI.class);
    
    private final TaskService taskService;
    private final CategoryService categoryService;
    private final ReminderService reminderService;
    private final StudyService studyService;
    private final ProjectService projectService;
    private final WindowPreferencesService windowPreferencesService;
    private final DataImportService dataImportService;
    private final DateTimeService dateTimeService;
    private final Map<Tab, RefreshablePanel> panelMap;
    private TabPane tabPane;

    @Autowired
    public StudySyncUI(TaskService taskService, CategoryService categoryService, ReminderService reminderService,
                       StudyService studyService, ProjectService projectService, WindowPreferencesService windowPreferencesService,
                       DataImportService dataImportService, DateTimeService dateTimeService) {
        this.taskService = taskService;
        this.categoryService = categoryService;
        this.reminderService = reminderService;
        this.studyService = studyService;
        this.projectService = projectService;
        this.windowPreferencesService = windowPreferencesService;
        this.dataImportService = dataImportService;
        this.dateTimeService = dateTimeService;

        panelMap = Map.of(
            new Tab("📊 Daily View"), new DailyViewPanel(studyService, taskService, projectService),
            new Tab("📚 Study Planner"), new StudyPlannerPanel(studyService, dataImportService, dateTimeService, taskService),
            new Tab("⭐ Reflection Diary"), new ReflectionDiaryPanel(studyService, dateTimeService),
            new Tab("🖊️ Projects"), new ProjectManagementPanel(projectService, categoryService),
            new Tab("📋 Tasks"), new TaskManagementPanel(taskService, categoryService, reminderService)
        );
    }

    public void start(Stage primaryStage) {
        this.tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        panelMap.forEach((tab, panel) -> tab.setContent(panel.getView()));
        
        // Restore tab order from preferences or use default order
        restoreTabOrder();
        
        // Setup drag and drop functionality for tabs (delayed to allow tab rendering)
        Platform.runLater(this::setupTabDragAndDrop);
        
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && panelMap.containsKey(newTab)) {
                panelMap.get(newTab).updateDisplay();
            }
        });
        
        double savedWidth = windowPreferencesService.getWindowWidth();
        double savedHeight = windowPreferencesService.getWindowHeight();
        double savedX = windowPreferencesService.getWindowX();
        double savedY = windowPreferencesService.getWindowY();
        boolean wasMaximized = windowPreferencesService.isWindowMaximized();
        
        if (!windowPreferencesService.isWindowPositionValid(savedX, savedY, savedWidth, savedHeight)) {
            logger.info("Saved window position is invalid, using defaults");
            savedX = 100;
            savedY = 100;
        }
        
        // Create main layout with header
        BorderPane mainLayout = new BorderPane();
        
        // Create header with profile button
        HBox header = createHeader();
        mainLayout.setTop(header);
        
        // Add tabPane to center
        mainLayout.setCenter(tabPane);
        
        Scene scene = new Scene(mainLayout, savedWidth, savedHeight);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        
        primaryStage.setTitle("StudySync");
        primaryStage.setX(savedX);
        primaryStage.setY(savedY);
        primaryStage.setMaximized(wasMaximized);
        
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
        
        setupWindowStateListeners(primaryStage);
        
        primaryStage.setOnCloseRequest(e -> {
            saveCurrentWindowState(primaryStage);
            saveCurrentTabOrder();
        });
        
        primaryStage.show();
        
        // Initialize the default tab after showing the window (to ensure Spring context is fully loaded)
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null && panelMap.containsKey(selectedTab)) {
            panelMap.get(selectedTab).updateDisplay();
        }
    }
    
    private void setupWindowStateListeners(Stage primaryStage) {
        primaryStage.widthProperty().addListener((obs, oldVal, newVal) -> saveCurrentWindowState(primaryStage));
        primaryStage.heightProperty().addListener((obs, oldVal, newVal) -> saveCurrentWindowState(primaryStage));
        primaryStage.xProperty().addListener((obs, oldVal, newVal) -> saveCurrentWindowState(primaryStage));
        primaryStage.yProperty().addListener((obs, oldVal, newVal) -> saveCurrentWindowState(primaryStage));
        primaryStage.maximizedProperty().addListener((obs, oldVal, newVal) -> saveCurrentWindowState(primaryStage));
    }
    
    private void saveCurrentWindowState(Stage primaryStage) {
        if (!primaryStage.isMaximized()) {
            windowPreferencesService.saveWindowState(
                primaryStage.getWidth(),
                primaryStage.getHeight(),
                primaryStage.getX(),
                primaryStage.getY(),
                false
            );
        } else {
            windowPreferencesService.saveWindowMaximized(true);
        }
    }
    
    private void restoreTabOrder() {
        List<String> savedOrder = windowPreferencesService.getTabOrder();
        
        if (savedOrder != null && savedOrder.size() == panelMap.size()) {
            // Restore tabs in saved order
            Map<String, Tab> tabsByTitle = panelMap.keySet().stream()
                .collect(Collectors.toMap(Tab::getText, tab -> tab));
            
            for (String title : savedOrder) {
                Tab tab = tabsByTitle.get(title);
                if (tab != null) {
                    tabPane.getTabs().add(tab);
                }
            }
            
            // Add any missing tabs (in case of version changes)
            for (Tab tab : panelMap.keySet()) {
                if (!tabPane.getTabs().contains(tab)) {
                    tabPane.getTabs().add(tab);
                }
            }
        } else {
            // Use default order
            tabPane.getTabs().addAll(panelMap.keySet());
        }
    }
    
    private void saveCurrentTabOrder() {
        List<String> currentOrder = tabPane.getTabs().stream()
            .map(Tab::getText)
            .collect(Collectors.toList());
        windowPreferencesService.saveTabOrder(currentOrder);
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
                    saveCurrentTabOrder();
                    event.consume();
                }
            }
        });
    }
    
    private Tab findTabByTitle(String title) {
        return tabPane.getTabs().stream()
            .filter(tab -> tab.getText().equals(title))
            .findFirst()
            .orElse(null);
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
    
    private void showProfileWindow() {
        // Create a new stage for the profile window
        Stage profileStage = new Stage();
        profileStage.setTitle("Study Profile & Analytics");
        profileStage.initOwner(tabPane.getScene().getWindow());
        
        // Create the profile panel
        ProfileViewPanel profilePanel = new ProfileViewPanel(studyService, projectService, taskService, dateTimeService);
        
        Scene profileScene = new Scene(profilePanel, 1000, 700);
        profileScene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        
        profileStage.setScene(profileScene);
        profileStage.show();
        
        // Update the profile display
        profilePanel.updateDisplay();
    }
}
