package com.studysync.presentation.ui.components;

import com.studysync.StudySyncApplication;
import com.studysync.domain.service.StudyService;
import com.studysync.domain.service.ProjectService;
import com.studysync.domain.service.TaskService;
import com.studysync.domain.service.DateTimeService;
import com.studysync.domain.entity.StudySession;
import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.DailyReflection;
import com.studysync.domain.entity.Task;
import com.studysync.domain.valueobject.TaskStatus;
import com.studysync.integration.drive.GoogleDriveService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.Node;
import javafx.scene.chart.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Profile view panel showing comprehensive user analytics including focus data,
 * productivity metrics, and visual graphs for self-assessment and improvement.
 */
public class ProfileViewPanel extends ScrollPane implements RefreshablePanel {
    private static final Logger logger = LoggerFactory.getLogger(ProfileViewPanel.class);
    private final StudyService studyService;
    private final ProjectService projectService;
    private final TaskService taskService;
    private final DateTimeService dateTimeService;
    private final GoogleDriveService googleDriveService;
    private volatile GoogleDriveService.SyncStatus lastKnownSyncStatus = GoogleDriveService.SyncStatus.UNKNOWN;
    
    // UI Components for dynamic updates
    private VBox statsContainer;
    private VBox chartsContainer;
    private Label profileSummaryLabel;
    private ProgressBar productivityRating;
    private Label productivityLabel;
    private Label driveStatusLabel;
    private Label driveHintLabel;
    private Label driveActionStatusLabel;
    private Button driveSignInButton;
    private Button driveSignOutButton;
    private Button driveSyncButton;
    private Button driveDownloadButton;
    private Button saveLocallyButton;
    
    public ProfileViewPanel(StudyService studyService, ProjectService projectService,
                           TaskService taskService, DateTimeService dateTimeService,
                           GoogleDriveService googleDriveService) {
        this.studyService = studyService;
        this.projectService = projectService;
        this.taskService = taskService;
        this.dateTimeService = dateTimeService;
        this.googleDriveService = googleDriveService;
        
        // Create main content container
        VBox mainContent = new VBox(20);
        mainContent.setPadding(new Insets(20));
        mainContent.getStyleClass().add("panel-bg");
        
        // Set up ScrollPane properties
        this.setContent(mainContent);
        this.setFitToWidth(true);
        this.setFitToHeight(false);
        this.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.getStyleClass().add("tab-content-area");
        
        initializeComponents(mainContent);
        updateDisplay();
    }

    private void initializeComponents(VBox mainContent) {
        // Header
        Label headerLabel = new Label("Study Profile & Analytics");
        headerLabel.setGraphic(TaskStyleUtils.iconLabel("\u2606", 24));
        TaskStyleUtils.fontBold(headerLabel, 24);
        
        VBox driveSyncSection = createDriveSyncSection();
        
        // Profile summary
        VBox profileSection = createProfileSummarySection();
        
        // Statistics cards
        HBox statsSection = createStatsSection();
        
        // Goal history section
        VBox goalHistorySection = createGoalHistorySection();
        
        // Charts section
        VBox chartsSection = createChartsSection();
        
        mainContent.getChildren().addAll(headerLabel, driveSyncSection, profileSection, statsSection,
                goalHistorySection, chartsSection);
    }
    
    private VBox createDriveSyncSection() {
        VBox section = new VBox(12);
        section.getStyleClass().add("section-card-light");
        
        Label title = new Label("Google Drive Sync");
        title.setGraphic(TaskStyleUtils.iconLabel("\u2601", 18));
        TaskStyleUtils.fontBold(title, 18);
        
        driveStatusLabel = new Label();
        driveStatusLabel.setWrapText(true);
        TaskStyleUtils.fontNormal(driveStatusLabel, 13);
        
        driveHintLabel = new Label();
        driveHintLabel.setWrapText(true);
        TaskStyleUtils.fontItalic(driveHintLabel, 12);
        driveHintLabel.setTextFill(Color.web("#7f8c8d"));
        
        driveActionStatusLabel = new Label();
        driveActionStatusLabel.setWrapText(true);
        TaskStyleUtils.fontNormal(driveActionStatusLabel, 12);
        driveActionStatusLabel.setTextFill(Color.web("#16a085"));
        
        driveSignInButton = new Button("Sign in with Google");
        driveSignInButton.setGraphic(TaskStyleUtils.iconLabel("\u2192", 14));
        driveSignInButton.getStyleClass().add("btn-google");
        driveSignInButton.setOnAction(e -> runDriveAction(
            "Opening Google sign-in…",
            () -> googleDriveService.signInWithGoogle(),
            "Signed in successfully. Use the Drive controls below to upload or stage a download.",
            "Unable to sign in with Google. Please try again."
        ));
        
        driveSignOutButton = new Button("Sign out");
        driveSignOutButton.setGraphic(TaskStyleUtils.iconLabel("\u2716", 14));
        driveSignOutButton.getStyleClass().add("btn-gray");
        driveSignOutButton.setOnAction(e -> runDriveAction(
            "Signing out…",
            () -> {
                googleDriveService.signOut();
                return true;
            },
            "Disconnected from Google Drive.",
            "Failed to sign out from Google."
        ));
        
        driveSyncButton = new Button("Sync to Drive now");
        driveSyncButton.setGraphic(TaskStyleUtils.iconLabel("\u2191", 14));
        driveSyncButton.getStyleClass().add("btn-success");
        driveSyncButton.setOnAction(e -> runDriveAction(
            "Uploading database to Google Drive…",
            () -> googleDriveService.uploadDatabaseSnapshot(),
            "Upload complete!",
            "Upload failed. Check your connection and credentials."
        ));

        driveDownloadButton = new Button("Download from Drive");
        driveDownloadButton.setGraphic(TaskStyleUtils.iconLabel("\u2193", 14));
        driveDownloadButton.getStyleClass().add("btn-orange-download");
        driveDownloadButton.setOnAction(e -> beginStagedDownload());

        saveLocallyButton = new Button("Save Locally");
        saveLocallyButton.setGraphic(TaskStyleUtils.iconLabel("\u2611", 14));
        saveLocallyButton.getStyleClass().add("btn-primary");
        saveLocallyButton.setOnAction(e -> runDriveAction(
            "Saving database to disk…",
            () -> googleDriveService.saveLocally(),
            "Saved! Local checkpoint completed and the file looks fresh.",
            "Save failed. Check logs for details."
        ));

        HBox buttonRow = new HBox(12, driveSignInButton, driveSignOutButton, driveSyncButton, driveDownloadButton, saveLocallyButton);
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        
        section.getChildren().addAll(title, driveStatusLabel, driveHintLabel, buttonRow, driveActionStatusLabel);
        refreshDriveSyncState();
        return section;
    }
    
    private void refreshDriveSyncState() {
        if (googleDriveService == null || !googleDriveService.isIntegrationEnabled()) {
            driveStatusLabel.setText("Google Drive sync is currently disabled. Configure config/google/drive.properties to enable it.");
            driveHintLabel.setText("Once enabled, StudySync will store the H2 database inside a private folder in your Google Drive account.");
            driveActionStatusLabel.setText("");
            driveSignInButton.setDisable(true);
            driveSignOutButton.setDisable(true);
            driveSyncButton.setDisable(true);
            driveDownloadButton.setDisable(true);
            return;
        }
        if (googleDriveService.isSignedIn()) {
            driveDownloadButton.setText(googleDriveService.isRestartSupported()
                    ? "Download from Drive & Restart"
                    : "Download from Drive & Apply on Next Launch");
            driveSignInButton.setDisable(true);
            driveSignOutButton.setDisable(false);
            driveSyncButton.setDisable(false);
            driveDownloadButton.setDisable(false);
            driveStatusLabel.setText("Connected as " + googleDriveService.getSignedInAccountEmail().orElse("Google Account") + ".");
            driveStatusLabel.setGraphic(null);
            driveHintLabel.setText("Checking Google Drive sync status…");
            CompletableFuture.supplyAsync(googleDriveService::checkSyncStatus)
                    .whenComplete((status, error) -> Platform.runLater(() -> {
                        if (error != null) {
                            lastKnownSyncStatus = GoogleDriveService.SyncStatus.UNKNOWN;
                            driveHintLabel.setText("Unable to determine sync status right now.");
                            return;
                        }
                        applySyncStatus(status);
                    }));
        } else {
            driveStatusLabel.setText("Not signed in with Google yet.");
            driveHintLabel.setText("Sign in to store your StudySync H2 database in your private Google Drive for safe multi-device access.");
            driveSignInButton.setDisable(false);
            driveSignOutButton.setDisable(true);
            driveSyncButton.setDisable(true);
            driveDownloadButton.setDisable(true);
        }
    }

    private void applySyncStatus(GoogleDriveService.SyncStatus status) {
        lastKnownSyncStatus = status;
        String email = googleDriveService.getSignedInAccountEmail().orElse("Google Account");
        switch (status) {
            case CONFLICT -> {
                driveStatusLabel.setText("Connected as " + email + ". Local unsaved changes conflict with a newer Drive database.");
                driveStatusLabel.setGraphic(TaskStyleUtils.iconLabel("\u26A0", 13));
                driveHintLabel.setText("Download will stage the newer Drive database and preserve your current local DB in a backup on next launch.");
                driveSyncButton.setDisable(true);
            }
            case DRIVE_NEWER -> {
                driveStatusLabel.setText("Connected as " + email + ". Google Drive has a newer database.");
                driveStatusLabel.setGraphic(TaskStyleUtils.iconLabel("\u2193", 13));
                driveHintLabel.setText("Download it to stage the newer database for next launch.");
                driveSyncButton.setDisable(false);
            }
            case LOCAL_NEWER -> {
                driveStatusLabel.setText("Connected as " + email + ". This device has local changes not yet uploaded to Drive.");
                driveStatusLabel.setGraphic(TaskStyleUtils.iconLabel("\u26A0", 13));
                driveHintLabel.setText("Upload your local database, or download from Drive to stage the remote DB and keep your current local copy in a backup.");
                driveSyncButton.setDisable(false);
            }
            case UP_TO_DATE -> {
                driveStatusLabel.setText("Connected as " + email + ". Local and Drive databases are in sync.");
                driveStatusLabel.setGraphic(null);
                driveHintLabel.setText("Uploads are explicit. Use Sync to Drive when you want to push this device's database.");
                driveSyncButton.setDisable(false);
            }
            case UNKNOWN -> {
                driveStatusLabel.setText("Connected as " + email + ". Sync status is currently unknown.");
                driveStatusLabel.setGraphic(null);
                driveHintLabel.setText("You can still save locally or retry the Drive actions.");
                driveSyncButton.setDisable(false);
            }
            case DISABLED -> {
                driveStatusLabel.setText("Google Drive sync is disabled.");
                driveStatusLabel.setGraphic(null);
                driveHintLabel.setText("Configure Google Drive settings to enable sync.");
                driveSyncButton.setDisable(true);
            }
        }
    }
    
    private void runDriveAction(String pendingMessage, java.util.function.Supplier<Boolean> action,
                                String successMessage, String failureMessage) {
        setDriveButtonsDisabled(true);
        driveActionStatusLabel.setText(pendingMessage);
        CompletableFuture.supplyAsync(action)
            .whenComplete((result, error) -> Platform.runLater(() -> {
                setDriveButtonsDisabled(false);
                if (error != null) {
                    logger.warn("Google Drive action failed", error);
                    driveActionStatusLabel.setText(failureMessage);
                } else if (Boolean.TRUE.equals(result)) {
                    driveActionStatusLabel.setText(successMessage);
                } else {
                    driveActionStatusLabel.setText(failureMessage);
                }
                refreshDriveSyncState();
            }));
    }

    private void beginStagedDownload() {
        setDriveButtonsDisabled(true);
        driveActionStatusLabel.setText("Checking Google Drive sync status…");
        CompletableFuture.supplyAsync(googleDriveService::checkSyncStatus)
                .whenComplete((status, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        setDriveButtonsDisabled(false);
                        driveActionStatusLabel.setText("Unable to check Drive status. Please try again.");
                        refreshDriveSyncState();
                        return;
                    }

                    if (!confirmDownload(status)) {
                        setDriveButtonsDisabled(false);
                        driveActionStatusLabel.setText("Drive download cancelled.");
                        refreshDriveSyncState();
                        return;
                    }

                    driveActionStatusLabel.setText("Staging database download from Google Drive…");
                    CompletableFuture.supplyAsync(googleDriveService::stageDownloadFromDrive)
                            .whenComplete((result, stageError) -> Platform.runLater(() -> {
                                setDriveButtonsDisabled(false);
                                if (stageError != null) {
                                    logger.warn("Google Drive staging failed", stageError);
                                    driveActionStatusLabel.setText("Download failed. Check your connection and credentials.");
                                    refreshDriveSyncState();
                                    return;
                                }
                                if (!Boolean.TRUE.equals(result)) {
                                    driveActionStatusLabel.setText("Download failed. Check your connection and credentials.");
                                    refreshDriveSyncState();
                                    return;
                                }

                                if (googleDriveService.isRestartSupported()) {
                                    driveActionStatusLabel.setText("Download staged. Restarting StudySync to apply it…");
                                    StudySyncApplication.requestRestart();
                                    Platform.exit();
                                } else {
                                    driveActionStatusLabel.setText("Download staged. Close and reopen StudySync to apply it.");
                                    refreshDriveSyncState();
                                }
                            }));
                }));
    }

    private boolean confirmDownload(GoogleDriveService.SyncStatus status) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(getScene() != null ? getScene().getWindow() : null);
        alert.setTitle("Stage Drive Download");
        alert.setHeaderText("Replace the local database on next launch?");
        String restartClause = googleDriveService.isRestartSupported()
                ? "StudySync will restart automatically after the download is staged."
                : "You will need to restart StudySync manually after the download is staged.";

        String body = switch (status) {
            case CONFLICT -> "Google Drive has a newer database and this device also has unsaved local changes. "
                    + "If you continue, the Drive copy will be staged now and your current local DB will be backed up on next launch. "
                    + restartClause;
            case LOCAL_NEWER -> "This device has local changes that are not on Drive. If you continue, the Drive copy will be staged "
                    + "and your current local DB will be backed up on next launch. " + restartClause;
            case DRIVE_NEWER -> "A newer database is available on Drive. Continuing will stage it for next launch. " + restartClause;
            default -> "This will stage the current Drive database for next launch. " + restartClause;
        };
        alert.setContentText(body);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }
    
    private void setDriveButtonsDisabled(boolean disabled) {
        driveSignInButton.setDisable(disabled || (googleDriveService != null && googleDriveService.isSignedIn()));
        driveSignOutButton.setDisable(disabled || googleDriveService == null || !googleDriveService.isSignedIn());
        boolean conflict = lastKnownSyncStatus == GoogleDriveService.SyncStatus.CONFLICT;
        driveSyncButton.setDisable(disabled || googleDriveService == null || !googleDriveService.isSignedIn() || conflict);
        driveDownloadButton.setDisable(disabled || googleDriveService == null || !googleDriveService.isSignedIn());
        saveLocallyButton.setDisable(disabled);
    }
    
    private VBox createProfileSummarySection() {
        VBox section = new VBox(15);
        section.getStyleClass().add("section-card");
        
        Label sectionTitle = new Label("Overall Performance");
        sectionTitle.setGraphic(TaskStyleUtils.iconLabel("\u25AA", 18));
        TaskStyleUtils.fontBold(sectionTitle, 18);
        
        // Profile summary with dynamic content
        profileSummaryLabel = new Label();
        TaskStyleUtils.fontNormal(profileSummaryLabel, 14);
        profileSummaryLabel.setTextFill(Color.web("#34495e"));
        profileSummaryLabel.setWrapText(true);
        
        // Productivity rating bar
        VBox productivityBox = new VBox(8);
        Label productivityTitle = new Label("Overall Productivity Rating:");
        TaskStyleUtils.fontSemiBold(productivityTitle, 12);
        
        productivityRating = new ProgressBar(0.75); // Will be updated dynamically
        productivityRating.setPrefWidth(300);
        productivityRating.setPrefHeight(20);
        productivityRating.setStyle("-fx-accent: #3498db;");
        
        productivityLabel = new Label("Good (75%)");
        TaskStyleUtils.fontBold(productivityLabel, 12);
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
    
    private VBox createGoalHistorySection() {
        VBox section = new VBox(15);
        section.getStyleClass().add("section-card");
        
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label sectionTitle = new Label("Goal History");
        sectionTitle.setGraphic(TaskStyleUtils.iconLabel("\u2666", 18));
        TaskStyleUtils.fontBold(sectionTitle, 18);
        
        List<StudyGoal> allGoals = getSortedGoalHistory("Newest first");
        long achievedCount = allGoals.stream().filter(StudyGoal::isAchieved).count();
        long failedCount = allGoals.stream().filter(StudyGoal::isFailed).count();
        long activeCount = allGoals.stream()
            .filter(goal -> !goal.isAchieved() && !goal.isFailed())
            .count();
        
        Label countLabel = new Label("(" + achievedCount + " achieved, "
                + failedCount + " missed/failed, " + activeCount + " active)");
        TaskStyleUtils.fontNormal(countLabel, 14);
        countLabel.setTextFill(Color.web("#7f8c8d"));
        
        Button viewAllBtn = new Button("» View All Goals");
        viewAllBtn.getStyleClass().add("btn-success");
        viewAllBtn.setOnAction(e -> showAllGoalsDialog());
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        header.getChildren().addAll(sectionTitle, countLabel, spacer, viewAllBtn);
        
        // Show recent goal activity (last 5 attempts, newest first)
        VBox recentGoalsContainer = new VBox(8);
        List<StudyGoal> recentGoals = allGoals.stream()
            .limit(5)
            .collect(Collectors.toList());
        
        if (recentGoals.isEmpty()) {
            Label noGoalsLabel = new Label("No goal attempts recorded yet.");
            TaskStyleUtils.fontNormal(noGoalsLabel, 12);
            noGoalsLabel.setTextFill(Color.web("#7f8c8d"));
            noGoalsLabel.setPadding(new Insets(10, 0, 0, 0));
            recentGoalsContainer.getChildren().add(noGoalsLabel);
        } else {
            Label recentLabel = new Label("Recent Goal Activity:");
            TaskStyleUtils.fontSemiBold(recentLabel, 12);
            recentLabel.setTextFill(Color.web("#2c3e50"));
            recentGoalsContainer.getChildren().add(recentLabel);
            
            for (StudyGoal goal : recentGoals) {
                HBox goalItem = createGoalHistoryItem(goal, false);
                recentGoalsContainer.getChildren().add(goalItem);
            }
            
            if (allGoals.size() > 5) {
                Label moreLabel = new Label("... and " + (allGoals.size() - 5)
                        + " more. Click 'View All Goals' to filter and sort them.");
                TaskStyleUtils.fontItalic(moreLabel, 11);
                moreLabel.setTextFill(Color.web("#95a5a6"));
                recentGoalsContainer.getChildren().add(moreLabel);
            }
        }
        
        section.getChildren().addAll(header, recentGoalsContainer);
        return section;
    }
    
    private HBox createGoalHistoryItem(StudyGoal goal, boolean detailed) {
        HBox item = new HBox(10);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(detailed ? new Insets(10, 15, 10, 15) : new Insets(8, 12, 8, 12));
        item.setStyle("-fx-background-color: " + goalHistoryBackground(goal)
                + "; -fx-background-radius: " + (detailed ? "8" : "5")
                + "; -fx-border-color: " + goalHistoryColor(goal)
                + "; -fx-border-radius: " + (detailed ? "8" : "5")
                + "; -fx-border-width: 1;");

        Label statusIcon = new Label(goalHistoryIcon(goal));
        TaskStyleUtils.fontEmoji(statusIcon, detailed ? 16 : 14);
        statusIcon.setTextFill(Color.web(goalHistoryColor(goal)));

        VBox textContainer = new VBox(detailed ? 4 : 2);
        HBox.setHgrow(textContainer, Priority.ALWAYS);
        
        Label descLabel = new Label(goal.getDescription());
        TaskStyleUtils.fontNormal(descLabel, detailed ? 13 : 12);
        descLabel.setWrapText(true);
        
        HBox detailsBox = new HBox(15);
        detailsBox.setAlignment(Pos.CENTER_LEFT);

        Label dateLabel = new Label(goalHistoryDateText(goal, detailed));
        TaskStyleUtils.fontNormal(dateLabel, detailed ? 11 : 10);
        dateLabel.setTextFill(Color.web("#7f8c8d"));

        Label attemptLabel = new Label("Attempt " + goal.getAttemptNumber());
        TaskStyleUtils.fontNormal(attemptLabel, detailed ? 11 : 10);
        attemptLabel.setTextFill(Color.web(goalHistoryColor(goal)));
        
        detailsBox.getChildren().addAll(dateLabel, attemptLabel);
        textContainer.getChildren().addAll(descLabel, detailsBox);
        
        item.getChildren().addAll(statusIcon, textContainer);
        return item;
    }
    
    private void showAllGoalsDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(this.getScene() != null ? this.getScene().getWindow() : null);
        dialog.setTitle("All Goals");
        dialog.setHeaderText("\u2666 Goal History");
        
        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.setPrefWidth(720);
        content.setPrefHeight(560);
        
        List<StudyGoal> allGoals = studyService.getStudyGoals();
        
        if (allGoals.isEmpty()) {
            Label noGoalsLabel = new Label("No goal attempts recorded yet.");
            TaskStyleUtils.fontNormal(noGoalsLabel, 14);
            noGoalsLabel.setTextFill(Color.web("#7f8c8d"));
            noGoalsLabel.setWrapText(true);
            noGoalsLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            noGoalsLabel.setPadding(new Insets(50, 20, 50, 20));
            content.getChildren().add(noGoalsLabel);
        } else {
            HBox controls = new HBox(10);
            controls.setAlignment(Pos.CENTER_LEFT);

            ComboBox<String> filterCombo = new ComboBox<>();
            filterCombo.getItems().addAll("All goals", "Achieved", "Missed / failed", "Active / pending");
            filterCombo.setValue("All goals");

            ComboBox<String> sortCombo = new ComboBox<>();
            sortCombo.getItems().addAll("Newest first", "Oldest first", "Status", "Attempt number", "Description");
            sortCombo.setValue("Newest first");

            Label totalLabel = new Label();
            TaskStyleUtils.fontBold(totalLabel, 14);
            totalLabel.setTextFill(Color.web("#2c3e50"));

            controls.getChildren().addAll(new Label("Show:"), filterCombo, new Label("Sort:"), sortCombo, totalLabel);
            
            ScrollPane scrollPane = new ScrollPane();
            VBox goalsContainer = new VBox(8);
            goalsContainer.setPadding(new Insets(10));

            scrollPane.setContent(goalsContainer);
            scrollPane.setFitToWidth(true);
            scrollPane.setPrefHeight(430);

            Runnable refresh = () -> populateGoalHistoryDialog(
                    goalsContainer, totalLabel, allGoals, filterCombo.getValue(), sortCombo.getValue());
            filterCombo.setOnAction(e -> refresh.run());
            sortCombo.setOnAction(e -> refresh.run());
            refresh.run();

            content.getChildren().addAll(controls, scrollPane);
        }
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setMinSize(740, 600);
        dialog.setResizable(true);

        dialog.setOnShown(e -> {
            dialog.setWidth(760);
            dialog.setHeight(620);
        });

        dialog.showAndWait();
    }

    private void populateGoalHistoryDialog(VBox goalsContainer, Label totalLabel,
                                           List<StudyGoal> allGoals, String filter, String sort) {
        goalsContainer.getChildren().clear();
        List<StudyGoal> visibleGoals = allGoals.stream()
            .filter(goal -> matchesGoalHistoryFilter(goal, filter))
            .sorted(goalHistoryComparator(sort))
            .collect(Collectors.toList());

        totalLabel.setText("(" + visibleGoals.size() + " shown)");

        if (visibleGoals.isEmpty()) {
            Label emptyLabel = new Label("No goals match this filter.");
            TaskStyleUtils.fontNormal(emptyLabel, 13);
            emptyLabel.setTextFill(Color.web("#7f8c8d"));
            emptyLabel.setPadding(new Insets(30));
            goalsContainer.getChildren().add(emptyLabel);
            return;
        }

        for (StudyGoal goal : visibleGoals) {
            goalsContainer.getChildren().add(createGoalHistoryItem(goal, true));
        }
    }

    private List<StudyGoal> getSortedGoalHistory(String sort) {
        return studyService.getStudyGoals().stream()
            .sorted(goalHistoryComparator(sort))
            .collect(Collectors.toList());
    }

    private boolean matchesGoalHistoryFilter(StudyGoal goal, String filter) {
        return switch (filter) {
            case "Achieved" -> goal.isAchieved();
            case "Missed / failed" -> goal.isFailed();
            case "Active / pending" -> !goal.isAchieved() && !goal.isFailed();
            default -> true;
        };
    }

    private Comparator<StudyGoal> goalHistoryComparator(String sort) {
        return switch (sort) {
            case "Oldest first" -> Comparator
                .comparing(StudyGoal::getDate)
                .thenComparingInt(StudyGoal::getAttemptNumber)
                .thenComparing(goal -> safeText(goal.getDescription()), String.CASE_INSENSITIVE_ORDER);
            case "Attempt number" -> Comparator
                .comparingInt(StudyGoal::getAttemptNumber).reversed()
                .thenComparing(StudyGoal::getDate, Comparator.reverseOrder())
                .thenComparing(goal -> safeText(goal.getDescription()), String.CASE_INSENSITIVE_ORDER);
            case "Status" -> Comparator
                .comparingInt(this::goalHistoryStatusRank)
                .thenComparing(StudyGoal::getDate, Comparator.reverseOrder())
                .thenComparing(Comparator.comparingInt(StudyGoal::getAttemptNumber).reversed())
                .thenComparing(goal -> safeText(goal.getDescription()), String.CASE_INSENSITIVE_ORDER);
            case "Description" -> Comparator
                .comparing((StudyGoal goal) -> safeText(goal.getDescription()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(StudyGoal::getDate, Comparator.reverseOrder())
                .thenComparing(Comparator.comparingInt(StudyGoal::getAttemptNumber).reversed());
            default -> Comparator
                .comparing(StudyGoal::getDate, Comparator.reverseOrder())
                .thenComparing(Comparator.comparingInt(StudyGoal::getAttemptNumber).reversed())
                .thenComparing(goal -> safeText(goal.getDescription()), String.CASE_INSENSITIVE_ORDER);
        };
    }

    private int goalHistoryStatusRank(StudyGoal goal) {
        if (goal.isFailed()) {
            return 0;
        }
        if (goal.isAchieved()) {
            return 1;
        }
        return 2;
    }

    private String goalHistoryIcon(StudyGoal goal) {
        if (goal.isAchieved()) {
            return "\u2713";
        }
        if (goal.isFailed()) {
            return "\u2715";
        }
        return "\u25CB";
    }

    private String goalHistoryColor(StudyGoal goal) {
        if (goal.isAchieved()) {
            return "#27ae60";
        }
        if (goal.isFailed()) {
            return "#c0392b";
        }
        return "#3498db";
    }

    private String goalHistoryBackground(StudyGoal goal) {
        if (goal.isAchieved()) {
            return "#e8f5e8";
        }
        if (goal.isFailed()) {
            return "#fdecea";
        }
        return "#e3f2fd";
    }

    private String goalHistoryDateText(StudyGoal goal, boolean detailed) {
        String date = goal.getDate().format(DateTimeFormatter.ofPattern(
                detailed ? "EEE, MMM dd, yyyy" : "MMM dd, yyyy"));
        if (goal.isAchieved()) {
            return "Achieved on " + date;
        }
        if (goal.isFailed()) {
            return (goal.getStatus() == StudyGoal.GoalStatus.ABANDONED ? "Abandoned on " : "Missed on ") + date;
        }
        return "Planned for " + date;
    }

    private String safeText(String text) {
        return text == null ? "" : text;
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
        TaskStyleUtils.fontBold(titleLabel, 12);
        titleLabel.setTextFill(Color.web("#7f8c8d"));
        
        Label valueLabel = new Label(value);
        TaskStyleUtils.fontBold(valueLabel, 24);
        valueLabel.setTextFill(Color.web(color));
        
        Label subtitleLabel = new Label(subtitle);
        TaskStyleUtils.fontNormal(subtitleLabel, 10);
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
            int missedGoals = (int) recentGoals.stream().filter(StudyGoal::isFailed).count();
            int goalAttemptScore = achievedGoals - missedGoals;
            
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
            
            VBox goalsCard = createStatCard("Goal Score", String.format("%+d", goalAttemptScore), "Achieved minus missed attempts", "#27ae60");
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
            focusChartBox.getStyleClass().add("section-card");
            focusChartBox.setPadding(new Insets(15));
            Label focusChartTitle = new Label("Focus Level Trend (Last 14 Days)");
            focusChartTitle.setGraphic(TaskStyleUtils.iconLabel("\u2191", 16));
            TaskStyleUtils.fontBold(focusChartTitle, 16);
            focusChartBox.getChildren().addAll(focusChartTitle, focusChart);
            
            // Daily productivity chart
            BarChart<String, Number> productivityChart = createDailyProductivityChart();
            VBox productivityChartBox = new VBox(10);
            productivityChartBox.getStyleClass().add("section-card");
            productivityChartBox.setPadding(new Insets(15));
            Label productivityChartTitle = new Label("Daily Study Time (Last 7 Days)");
            productivityChartTitle.setGraphic(TaskStyleUtils.iconLabel("\u25AA", 16));
            TaskStyleUtils.fontBold(productivityChartTitle, 16);
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
        
        // Goal attempt score (10% weight): achieved attempts help, missed attempts hurt.
        List<StudyGoal> goals = studyService.getStudyGoals().stream()
            .filter(g -> g.getDate().isAfter(dateTimeService.getCurrentDate().minusDays(30)))
            .collect(Collectors.toList());
        double goalScore = 0;
        if (!goals.isEmpty()) {
            long achievedGoals = goals.stream().filter(StudyGoal::isAchieved).count();
            long missedGoals = goals.stream().filter(StudyGoal::isFailed).count();
            double normalized = ((double) (achievedGoals - missedGoals + goals.size())) / (2.0 * goals.size());
            goalScore = Math.max(0, Math.min(1, normalized)) * 10;
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
        refreshDriveSyncState();
    }
    
    @Override
    public Node getView() {
        return this;
    }
}
