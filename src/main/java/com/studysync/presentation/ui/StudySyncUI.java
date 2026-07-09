package com.studysync.presentation.ui;

import com.studysync.domain.service.CategoryService;
import com.studysync.domain.service.DateTimeService;
import com.studysync.domain.service.ProjectService;
import com.studysync.domain.service.ReminderService;
import com.studysync.domain.service.StudyService;
import com.studysync.domain.service.TaskService;
import com.studysync.integration.drive.GoogleDriveService;
import com.studysync.presentation.ui.components.CalendarViewPanel;
import com.studysync.presentation.ui.components.ProfileViewPanel;
import com.studysync.presentation.ui.components.ProjectManagementPanel;
import com.studysync.presentation.ui.components.RefreshablePanel;
import com.studysync.presentation.ui.components.ReflectionDiaryPanel;
import com.studysync.presentation.ui.components.StudyPlannerPanel;
import com.studysync.presentation.ui.components.TaskManagementPanel;
import com.studysync.presentation.ui.components.TaskStyleUtils;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
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
import java.util.concurrent.CompletableFuture;

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
    private StackPane overlayLayer;

    @Autowired
    public StudySyncUI(TaskService taskService,
                       CategoryService categoryService,
                       ReminderService reminderService,
                       StudyService studyService,
                       ProjectService projectService,
                       DateTimeService dateTimeService,
                       GoogleDriveService googleDriveService) {
        this.taskService = Objects.requireNonNull(taskService, "taskService");
        this.categoryService = Objects.requireNonNull(categoryService, "categoryService");
        this.reminderService = Objects.requireNonNull(reminderService, "reminderService");
        this.studyService = Objects.requireNonNull(studyService, "studyService");
        this.projectService = Objects.requireNonNull(projectService, "projectService");
        this.dateTimeService = Objects.requireNonNull(dateTimeService, "dateTimeService");
        this.googleDriveService = Objects.requireNonNull(googleDriveService, "googleDriveService");

        Map<Tab, RefreshablePanel> panels = new LinkedHashMap<>();
        Tab calendarTab = new Tab("Calendar View");
        calendarTab.setGraphic(TaskStyleUtils.iconLabel("\u25A6", 14));
        panels.put(calendarTab, new CalendarViewPanel(this.studyService, this.taskService, this.projectService));
        Tab plannerTab = new Tab("Study Planner");
        plannerTab.setGraphic(TaskStyleUtils.iconLabel("\u270E", 14));
        panels.put(plannerTab, new StudyPlannerPanel(this.studyService, this.dateTimeService, this.taskService,
                this.categoryService, this::showModal, this::closeModal));
        Tab reflectionTab = new Tab("Reflection Diary");
        reflectionTab.setGraphic(TaskStyleUtils.iconLabel("\u2605", 14));
        panels.put(reflectionTab, new ReflectionDiaryPanel(this.studyService, this.dateTimeService));
        Tab projectsTab = new Tab("Projects");
        projectsTab.setGraphic(TaskStyleUtils.iconLabel("\u2261", 14));
        panels.put(projectsTab, new ProjectManagementPanel(this.projectService, this.categoryService,
                this::showModal, this::closeModal));
        Tab tasksTab = new Tab("Tasks");
        tasksTab.setGraphic(TaskStyleUtils.iconLabel("\u2611", 14));
        panels.put(tasksTab, new TaskManagementPanel(this.taskService, this.categoryService, this.reminderService,
                this.studyService, this::showModal, this::closeModal));
        panelMap = Collections.unmodifiableMap(panels);
    }

    public void start(Stage primaryStage) {
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);

        this.tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        panelMap.forEach((tab, panel) -> tab.setContent(panel.getView()));
        restoreTabOrder();
        Platform.runLater(this::setupTabDragAndDrop);

        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && panelMap.containsKey(newTab)) {
                panelMap.get(newTab).updateDisplay();
            }
        });

        BorderPane mainLayout = new BorderPane();
        HBox header = createHeader();
        mainLayout.setTop(header);
        mainLayout.setCenter(tabPane);

        StackPane rootDataPane = new StackPane();
        rootDataPane.getChildren().add(mainLayout);

        overlayLayer = new StackPane();
        overlayLayer.setStyle("-fx-background-color: rgba(0, 0, 0, 0.4);");
        overlayLayer.setVisible(false);
        overlayLayer.setAlignment(Pos.CENTER);
        overlayLayer.setOnMouseClicked(event -> event.consume());
        rootDataPane.getChildren().add(overlayLayer);

        Scene scene = new Scene(rootDataPane, DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        primaryStage.setTitle("StudySync");
        try (var iconStream = getClass().getResourceAsStream("/icon.png")) {
            if (iconStream != null) {
                primaryStage.getIcons().add(new Image(iconStream));
            } else {
                logger.warn("Application icon not found in resources");
            }
        } catch (Exception e) {
            logger.warn("Failed to load application icon: {}", e.getMessage());
        }

        primaryStage.setScene(scene);
        primaryStage.setWidth(DEFAULT_WINDOW_WIDTH);
        primaryStage.setHeight(DEFAULT_WINDOW_HEIGHT);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.centerOnScreen();
        primaryStage.show();

        resolveDriveSyncOnStartup();

        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null && panelMap.containsKey(selectedTab)) {
            panelMap.get(selectedTab).updateDisplay();
        }
    }

    private void restoreTabOrder() {
        tabPane.getTabs().addAll(panelMap.keySet());
    }

    private void setupTabDragAndDrop() {
        tabPane.setOnKeyPressed((KeyEvent event) -> {
            Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
            if (selectedTab == null || !event.isControlDown()) {
                return;
            }

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
        });
    }

    private HBox createHeader() {
        HBox header = new HBox();
        header.setPadding(new Insets(10, 15, 10, 15));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: linear-gradient(to right, #3498db, #2980b9); "
                + "-fx-border-color: #2980b9; -fx-border-width: 0 0 2 0;");

        Label appTitle = new Label("StudySync");
        appTitle.setTextFill(Color.WHITE);
        TaskStyleUtils.fontBold(appTitle, 20);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        javafx.scene.control.Button profileButton = new javafx.scene.control.Button("Profile");
        profileButton.setGraphic(TaskStyleUtils.iconLabel("\u263B", 14));
        profileButton.setStyle("-fx-background-color: rgba(255,255,255,0.2); -fx-text-fill: white; "
                + "-fx-border-color: rgba(255,255,255,0.5); -fx-border-radius: 5; -fx-background-radius: 5; "
                + "-fx-font-weight: bold;");
        profileButton.setOnMouseEntered(e -> profileButton.setStyle("-fx-background-color: rgba(255,255,255,0.3); "
                + "-fx-text-fill: white; -fx-border-color: rgba(255,255,255,0.7); -fx-border-radius: 5; "
                + "-fx-background-radius: 5; -fx-font-weight: bold;"));
        profileButton.setOnMouseExited(e -> profileButton.setStyle("-fx-background-color: rgba(255,255,255,0.2); "
                + "-fx-text-fill: white; -fx-border-color: rgba(255,255,255,0.5); -fx-border-radius: 5; "
                + "-fx-background-radius: 5; -fx-font-weight: bold;"));
        profileButton.setOnAction(e -> showProfileWindow());

        header.getChildren().addAll(appTitle, spacer, profileButton);
        return header;
    }

    private void resolveDriveSyncOnStartup() {
        if (googleDriveService.hasPendingDownload() || googleDriveService.hasFailedPendingDownload()) {
            showStartupAlert("Google Drive Sync",
                    "A staged Drive download could not be fully applied. "
                            + "Resolve it from the Profile window before StudySync mutates delayed tasks or goals.");
            return;
        }

        if (!googleDriveService.isIntegrationEnabled() || !googleDriveService.isSignedIn()) {
            runStartupMaintenance();
            return;
        }

        CompletableFuture.supplyAsync(googleDriveService::checkSyncStatus)
                .whenComplete((status, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        logger.warn("Failed to resolve startup Drive sync state", error);
                        handleStartupSyncStatus(GoogleDriveService.SyncStatus.UNKNOWN);
                        return;
                    }
                    handleStartupSyncStatus(status);
                }));
    }

    private void handleStartupSyncStatus(GoogleDriveService.SyncStatus status) {
        switch (status) {
            case DRIVE_NEWER -> showStartupAlert("Google Drive Sync",
                    "A newer database is available on Google Drive. Download it from the Profile window before "
                            + "StudySync updates delayed tasks or goals.");
            case CONFLICT -> showStartupAlert("Google Drive Sync Conflict",
                    "Google Drive has newer data while this device also has unsaved local changes. Resolve the "
                            + "conflict from the Profile window before StudySync updates delayed tasks or goals.");
            case UP_TO_DATE, LOCAL_NEWER, UNKNOWN, DISABLED -> runStartupMaintenance();
        }
    }

    private void runStartupMaintenance() {
        markDelayedTasksOnStartup();
        processDelayedGoalsOnStartup();
        refreshAllPanels();
    }

    private void markDelayedTasksOnStartup() {
        try {
            int count = taskService.markDelayedTasks();
            if (count > 0) {
                logger.info("Marked {} overdue task(s) as DELAYED on startup", count);
            }
        } catch (Exception e) {
            logger.error("Failed to mark delayed tasks on startup", e);
        }
    }

    private void processDelayedGoalsOnStartup() {
        try {
            StudyService.GoalDelayProcessingResult result = studyService.processAllDelayedGoals();
            if (result.missedAttempts() > 0) {
                logger.info("Marked {} overdue study goal attempt(s) as missed", result.missedAttempts());
            }
        } catch (Exception e) {
            logger.error("Failed to process delayed goals on startup", e);
        }
    }

    private void showStartupAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(tabPane.getScene() != null ? tabPane.getScene().getWindow() : null);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.show();
    }

    private void refreshAllPanels() {
        for (RefreshablePanel panel : panelMap.values()) {
            try {
                panel.updateDisplay();
            } catch (Exception e) {
                logger.warn("Failed to refresh panel {}", panel.getClass().getSimpleName(), e);
            }
        }
    }

    private void showProfileWindow() {
        Stage profileStage = new Stage();
        profileStage.setTitle("Study Profile & Analytics");
        profileStage.initOwner(tabPane.getScene().getWindow());

        ProfileViewPanel profilePanel = new ProfileViewPanel(
                studyService, projectService, taskService, dateTimeService, googleDriveService);

        Scene profileScene = new Scene(profilePanel, 1000, 700);
        profileScene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        profileStage.setScene(profileScene);
        profileStage.setMinWidth(900);
        profileStage.setMinHeight(600);
        profileStage.setWidth(1000);
        profileStage.setHeight(700);
        profileStage.show();

        profilePanel.updateDisplay();
    }

    private void showModal(Node content) {
        if (overlayLayer != null) {
            overlayLayer.getChildren().clear();
            overlayLayer.getChildren().add(content);
            overlayLayer.setVisible(true);
        }
    }

    private void closeModal() {
        if (overlayLayer != null) {
            overlayLayer.setVisible(false);
            overlayLayer.getChildren().clear();
        }
    }
}
