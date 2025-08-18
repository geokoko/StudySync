package com.studysync.presentation.ui.components;

import com.studysync.domain.valueobject.ProjectStatus;
import com.studysync.domain.service.ProjectService;
import com.studysync.domain.service.CategoryService;
import com.studysync.domain.service.ProjectSessionEnd;
import com.studysync.domain.entity.Project;
import com.studysync.domain.entity.ProjectSession;
import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.domain.valueobject.TaskCategory;
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
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;

public class ProjectManagementPanel extends ScrollPane implements RefreshablePanel {
    private final ProjectService projectService;
    private final CategoryService categoryService;
    private ProjectSession currentSession;
    private Timeline sessionTimer;
    
    // Project management
    private ListView<Project> projectListView;
    private TextField projectTitleField;
    private TextArea projectDescriptionArea;
    private ComboBox<TaskCategory> projectCategoryCombo;
    private TextField newProjectCategoryField;
    private ComboBox<Integer> projectPriorityCombo;
    private DatePicker targetEndDatePicker;
    private ComboBox<ProjectStatus> projectStatusCombo;
    private Project selectedProject;
    
    // Session management
    private Label currentProjectLabel;
    private ListView<ProjectSession> sessionListView;
    private VBox sessionFormContainer;
    private Button startSessionBtn;
    private Button endSessionBtn;
    
    // Session form fields
    private TextField sessionTitleField;
    private TextArea objectivesArea;
    private TextArea progressArea;
    private TextArea nextStepsArea;
    private TextArea challengesArea;
    private TextArea notesArea;
    private Label sessionTimerLabel;

    public ProjectManagementPanel(ProjectService projectService, CategoryService categoryService) {
        this.projectService = projectService;
        this.categoryService = categoryService;
        // Create main content container
        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(20));
        mainContent.setStyle("-fx-background-color: linear-gradient(to bottom, #f1f2f6, #dfe4ea);");
        mainContent.setFillWidth(true);
        mainContent.setPrefWidth(Region.USE_COMPUTED_SIZE);
        mainContent.setMaxWidth(Double.MAX_VALUE);
        
        // Set up ScrollPane properties for full screen and proper scrolling
        this.setContent(mainContent);
        this.setFitToWidth(true);
        this.setFitToHeight(true);
        this.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.getStyleClass().add("tab-content-area");
        this.setPannable(true);
        
        // Ensure scroll pane uses full available space
        this.setPrefViewportWidth(Region.USE_COMPUTED_SIZE);
        this.setPrefViewportHeight(Region.USE_COMPUTED_SIZE);
        this.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        
        initializeComponents(mainContent);
        updateDisplay();
    }

    private void initializeComponents(VBox mainContent) {
        // Header
        Label headerLabel = new Label("🚀 Project Management");
        headerLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        headerLabel.setTextFill(Color.web("#2c3e50"));
        
        // Create main layout with tabs
        TabPane mainTabPane = new TabPane();
        mainTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        mainTabPane.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        mainTabPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(mainTabPane, Priority.ALWAYS);
        
        // Projects tab
        Tab projectsTab = new Tab("📁 Projects", createProjectsTab());
        
        // Current Session tab
        Tab sessionTab = new Tab("⚡ Work Session", createSessionTab());
        
        // History tab
        Tab historyTab = new Tab("📈 Session History", createHistoryTab());
        
        mainTabPane.getTabs().addAll(projectsTab, sessionTab, historyTab);
        
        mainContent.getChildren().addAll(headerLabel, mainTabPane);
    }
    
    private VBox createProjectsTab() {
        VBox container = new VBox(15);
        container.setPadding(new Insets(15));
        container.setFillWidth(true);
        container.setPrefWidth(Region.USE_COMPUTED_SIZE);
        container.setMaxWidth(Double.MAX_VALUE);
        
        // Project list
        VBox listSection = new VBox(10);
        listSection.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 10;");
        
        Label listTitle = new Label("Your Projects");
        listTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        
        projectListView = new ListView<>();
        projectListView.setPrefHeight(200);
        projectListView.setMaxHeight(Double.MAX_VALUE);
        projectListView.setCellFactory(lv -> new ProjectListCell());
        VBox.setVgrow(projectListView, Priority.SOMETIMES);
        projectListView.getSelectionModel().selectedItemProperty().addListener((obs, old, newProject) -> {
            selectedProject = newProject;
            populateProjectForm();
        });
        
        listSection.getChildren().addAll(listTitle, projectListView);
        
        // Project form
        VBox formSection = createProjectForm();
        
        container.getChildren().addAll(listSection, formSection);
        return container;
    }
    
    private VBox createProjectForm() {
        VBox formSection = new VBox(10);
        formSection.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 10;");
        
        Label formTitle = new Label("Project Details");
        formTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        
        // Form fields
        GridPane formGrid = new GridPane();
        formGrid.setHgap(10);
        formGrid.setVgap(10);
        
        projectTitleField = new TextField();
        projectTitleField.setPromptText("Project title");
        
        projectDescriptionArea = new TextArea();
        projectDescriptionArea.setPromptText("Project description");
        projectDescriptionArea.setPrefRowCount(3);
        
        projectCategoryCombo = new ComboBox<>();
        projectCategoryCombo.setPromptText("Select category");
        updateCategoryCombo();
        
        // New category input section for projects
        newProjectCategoryField = new TextField();
        newProjectCategoryField.setPromptText("Or create new category...");
        Button addProjectCategoryBtn = new Button("➕ Add Category");
        addProjectCategoryBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 11px;");
        addProjectCategoryBtn.setOnAction(e -> addNewProjectCategory());
        
        HBox projectCategoryInputBox = new HBox(8);
        projectCategoryInputBox.setAlignment(Pos.CENTER_LEFT);
        projectCategoryInputBox.getChildren().addAll(newProjectCategoryField, addProjectCategoryBtn);
        
        projectPriorityCombo = new ComboBox<>();
        projectPriorityCombo.setPromptText("Select priority (1-5 stars)");
        projectPriorityCombo.getItems().addAll(1, 2, 3, 4, 5);
        projectPriorityCombo.setCellFactory(lv -> new ListCell<Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    TaskPriority temp = new TaskPriority(item);
                    setText(temp.toString() + " (" + item + " star" + (item == 1 ? "" : "s") + ")");
                }
            }
        });
        projectPriorityCombo.setButtonCell(new ListCell<Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("Select priority (1-5 stars)");
                } else {
                    TaskPriority temp = new TaskPriority(item);
                    setText(temp.toString() + " (" + item + " star" + (item == 1 ? "" : "s") + ")");
                }
            }
        });
        
        targetEndDatePicker = new DatePicker();
        targetEndDatePicker.setPromptText("Target end date (optional)");
        
        projectStatusCombo = new ComboBox<>();
        projectStatusCombo.getItems().addAll(ProjectStatus.values());
        projectStatusCombo.setValue(ProjectStatus.ACTIVE);
        
        // Layout form fields
        formGrid.add(new Label("Title:"), 0, 0);
        formGrid.add(projectTitleField, 1, 0);
        formGrid.add(new Label("Category:"), 0, 1);
        formGrid.add(projectCategoryCombo, 1, 1);
        formGrid.add(projectCategoryInputBox, 1, 2);
        formGrid.add(new Label("Priority:"), 0, 3);
        formGrid.add(projectPriorityCombo, 1, 3);
        formGrid.add(new Label("Target End:"), 0, 4);
        formGrid.add(targetEndDatePicker, 1, 4);
        formGrid.add(new Label("Status:"), 0, 5);
        formGrid.add(projectStatusCombo, 1, 5);
        formGrid.add(new Label("Description:"), 0, 6);
        formGrid.add(projectDescriptionArea, 1, 6);
        
        // Action buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        
        Button saveProjectBtn = new Button("Save Project");
        saveProjectBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        saveProjectBtn.setOnAction(e -> saveProject());
        
        Button newProjectBtn = new Button("➕ New Project");
        newProjectBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white;");
        newProjectBtn.setOnAction(e -> clearProjectForm());
        
        Button deleteProjectBtn = new Button("Delete Project");
        deleteProjectBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
        deleteProjectBtn.setOnAction(e -> deleteSelectedProject());
        
        buttonBox.getChildren().addAll(saveProjectBtn, newProjectBtn, deleteProjectBtn);
        
        formSection.getChildren().addAll(formTitle, formGrid, buttonBox);
        return formSection;
    }
    
    private VBox createSessionTab() {
        VBox container = new VBox(15);
        container.setPadding(new Insets(15));
        container.setFillWidth(true);
        container.setPrefWidth(Region.USE_COMPUTED_SIZE);
        container.setMaxWidth(Double.MAX_VALUE);
        
        // Current project selection
        VBox projectSection = new VBox(10);
        projectSection.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 10;");
        
        Label projectLabel = new Label("Select Project to Work On:");
        projectLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        ComboBox<Project> projectSelector = new ComboBox<>();
        projectSelector.setPromptText("Choose a project...");
        projectSelector.setCellFactory(lv -> new ListCell<Project>() {
            @Override
            protected void updateItem(Project item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getTitle() + " (" + item.getStatus() + ")");
                }
            }
        });
        projectSelector.setButtonCell(new ListCell<Project>() {
            @Override
            protected void updateItem(Project item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("Choose a project...");
                } else {
                    setText(item.getTitle() + " (" + item.getStatus() + ")");
                }
            }
        });
        projectSelector.getItems().setAll(projectService.getActiveProjects());
        projectSelector.getSelectionModel().selectedItemProperty().addListener((obs, old, newProject) -> {
            selectedProject = newProject;
            updateSessionControls();
        });
        
        currentProjectLabel = new Label("No project selected");
        currentProjectLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        currentProjectLabel.setTextFill(Color.web("#34495e"));
        
        projectSection.getChildren().addAll(projectLabel, projectSelector, currentProjectLabel);
        
        // Session controls
        VBox sessionControls = createSessionControls();
        
        // Session form (hidden initially)
        sessionFormContainer = createSessionForm();
        sessionFormContainer.setVisible(false);
        
        container.getChildren().addAll(projectSection, sessionControls, sessionFormContainer);
        return container;
    }
    
    private VBox createSessionControls() {
        VBox controls = new VBox(10);
        controls.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 10;");
        controls.setAlignment(Pos.CENTER);
        
        sessionTimerLabel = new Label("Ready to start session");
        sessionTimerLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        sessionTimerLabel.setTextFill(Color.web("#2c3e50"));
        
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);
        
        startSessionBtn = new Button("Start Session");
        startSessionBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 10 20;");
        startSessionBtn.setOnAction(e -> startSession());
        startSessionBtn.setDisable(true);
        
        endSessionBtn = new Button("End Session");
        endSessionBtn.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-padding: 10 20;");
        endSessionBtn.setOnAction(e -> showEndSessionForm());
        endSessionBtn.setDisable(true);
        
        buttonBox.getChildren().addAll(startSessionBtn, endSessionBtn);
        
        controls.getChildren().addAll(sessionTimerLabel, buttonBox);
        return controls;
    }
    
    private VBox createSessionForm() {
        VBox form = new VBox(10);
        form.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 10;");
        
        Label formTitle = new Label("Session Summary");
        formTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        
        GridPane formGrid = new GridPane();
        formGrid.setHgap(10);
        formGrid.setVgap(10);
        
        sessionTitleField = new TextField();
        sessionTitleField.setPromptText("Session title (e.g., 'Implemented user authentication')");
        
        objectivesArea = new TextArea();
        objectivesArea.setPromptText("What did you plan to accomplish?");
        objectivesArea.setPrefRowCount(2);
        
        progressArea = new TextArea();
        progressArea.setPromptText("What did you actually accomplish?");
        progressArea.setPrefRowCount(3);
        
        nextStepsArea = new TextArea();
        nextStepsArea.setPromptText("What should be done next?");
        nextStepsArea.setPrefRowCount(2);
        
        challengesArea = new TextArea();
        challengesArea.setPromptText("Any challenges or blockers encountered?");
        challengesArea.setPrefRowCount(2);
        
        notesArea = new TextArea();
        notesArea.setPromptText("Additional notes or thoughts");
        notesArea.setPrefRowCount(2);
        
        formGrid.add(new Label("Session Title:"), 0, 0);
        formGrid.add(sessionTitleField, 1, 0);
        formGrid.add(new Label("Objectives:"), 0, 1);
        formGrid.add(objectivesArea, 1, 1);
        formGrid.add(new Label("Progress Made:"), 0, 2);
        formGrid.add(progressArea, 1, 2);
        formGrid.add(new Label("Next Steps:"), 0, 3);
        formGrid.add(nextStepsArea, 1, 3);
        formGrid.add(new Label("Challenges:"), 0, 4);
        formGrid.add(challengesArea, 1, 4);
        formGrid.add(new Label("Notes:"), 0, 5);
        formGrid.add(notesArea, 1, 5);
        
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        
        Button saveSessionBtn = new Button("Save Session");
        saveSessionBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        saveSessionBtn.setOnAction(e -> saveSession());
        
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white;");
        cancelBtn.setOnAction(e -> cancelSession());
        
        buttonBox.getChildren().addAll(saveSessionBtn, cancelBtn);
        
        form.getChildren().addAll(formTitle, formGrid, buttonBox);
        return form;
    }
    
    private VBox createHistoryTab() {
        VBox container = new VBox(15);
        container.setPadding(new Insets(15));
        container.setFillWidth(true);
        container.setPrefWidth(Region.USE_COMPUTED_SIZE);
        container.setMaxWidth(Double.MAX_VALUE);
        
        // Project selection for history
        HBox filterBox = new HBox(10);
        filterBox.setAlignment(Pos.CENTER_LEFT);
        
        Label filterLabel = new Label("Show sessions for:");
        ComboBox<Project> projectFilter = new ComboBox<>();
        projectFilter.setPromptText("All projects");
        projectFilter.getItems().add(null); // For "All projects"
        projectFilter.getItems().addAll(projectService.getProjects());
        projectFilter.setCellFactory(lv -> new ListCell<Project>() {
            @Override
            protected void updateItem(Project item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                } else if (item == null) {
                    setText("All projects");
                } else {
                    setText(item.getTitle());
                }
            }
        });
        projectFilter.setButtonCell(new ListCell<Project>() {
            @Override
            protected void updateItem(Project item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText("All projects");
                } else if (item == null) {
                    setText("All projects");
                } else {
                    setText(item.getTitle());
                }
            }
        });
        projectFilter.getSelectionModel().selectedItemProperty().addListener((obs, old, newProject) -> {
            updateSessionHistory(newProject);
        });
        
        filterBox.getChildren().addAll(filterLabel, projectFilter);
        
        // Session history list
        sessionListView = new ListView<>();
        sessionListView.setPrefHeight(400);
        sessionListView.setMaxHeight(Double.MAX_VALUE);
        sessionListView.setCellFactory(lv -> new SessionListCell());
        VBox.setVgrow(sessionListView, Priority.ALWAYS);
        
        container.getChildren().addAll(filterBox, sessionListView);
        return container;
    }
    
    // Event handlers and helper methods
    private void updateCategoryCombo() {
        projectCategoryCombo.getItems().clear();
        projectCategoryCombo.getItems().addAll(categoryService.getCategories());
    }
    
    private void addNewProjectCategory() {
        String categoryName = newProjectCategoryField.getText().trim();
        if (categoryName.isEmpty()) {
            showAlert("Error", "Please enter a category name.");
            return;
        }
        
        // Check if category already exists
        boolean categoryExists = categoryService.getCategories().stream()
                .anyMatch(cat -> cat.getName().equalsIgnoreCase(categoryName));
                
        if (categoryExists) {
            showAlert("Error", "Category '" + categoryName + "' already exists.");
            return;
        }
        
        try {
            categoryService.addCategory(categoryName);
            updateCategoryCombo();
            
            // Select the newly created category
            TaskCategory newCategory = categoryService.getCategories().stream()
                    .filter(cat -> cat.getName().equals(categoryName))
                    .findFirst()
                    .orElse(null);
            if (newCategory != null) {
                projectCategoryCombo.getSelectionModel().select(newCategory);
            }
            
            newProjectCategoryField.clear();
            showAlert("Success", "Category '" + categoryName + "' added successfully!");
        } catch (Exception e) {
            showAlert("Error", "Failed to add category: " + e.getMessage());
        }
    }
    
    private void updateSessionControls() {
        if (selectedProject != null) {
            currentProjectLabel.setText("Working on: " + selectedProject.getTitle());
            startSessionBtn.setDisable(currentSession != null);
            endSessionBtn.setDisable(currentSession == null);
        } else {
            currentProjectLabel.setText("No project selected");
            startSessionBtn.setDisable(true);
            endSessionBtn.setDisable(true);
        }
    }
    
    private void saveProject() {
        try {
            String title = projectTitleField.getText().trim();
            if (title.isEmpty()) {
                showAlert("Error", "Project title cannot be empty!");
                return;
            }
            
            String description = projectDescriptionArea.getText().trim();
            TaskCategory category = projectCategoryCombo.getValue();
            Integer priorityStars = projectPriorityCombo.getValue();
            LocalDate targetEnd = targetEndDatePicker.getValue();
            ProjectStatus status = projectStatusCombo.getValue();
            
            if (category == null || priorityStars == null) {
                showAlert("Error", "Please select category and priority!");
                return;
            }
            
            TaskPriority priority = new TaskPriority(priorityStars);
            
            if (selectedProject == null) {
                // Create new project
                Project newProject = Project.create(title, description, category.getName(), priority, targetEnd);
                projectService.addProject(newProject);
            } else {
                // Update existing project - create new record with updated values
                Project updatedProject = new Project(
                    selectedProject.getId(),
                    title,
                    description,
                    category.getName(),
                    priority,
                    selectedProject.getStartDate(),
                    targetEnd,
                    status != null ? status : selectedProject.getStatus(),
                    selectedProject.getCreatedAt(),
                    selectedProject.getLastWorkedOn(),
                    selectedProject.getTotalSessionsCount(),
                    selectedProject.getTotalMinutesWorked()
                );
                projectService.updateProject(updatedProject);
            }
            
            updateDisplay();
            clearProjectForm();
            showAlert("Success", "Project saved successfully!");
        } catch (Exception e) {
            showAlert("Error", e.getMessage());
        }
    }
    
    private void clearProjectForm() {
        selectedProject = null;
        projectTitleField.clear();
        projectDescriptionArea.clear();
        projectCategoryCombo.getSelectionModel().clearSelection();
        newProjectCategoryField.clear();
        projectPriorityCombo.getSelectionModel().clearSelection();
        targetEndDatePicker.setValue(null);
        projectStatusCombo.setValue(ProjectStatus.ACTIVE);
        projectListView.getSelectionModel().clearSelection();
    }
    
    private void populateProjectForm() {
        if (selectedProject != null) {
            projectTitleField.setText(selectedProject.getTitle());
            projectDescriptionArea.setText(selectedProject.getDescription());
            
            // Find and select category
            projectCategoryCombo.getSelectionModel().select(
                categoryService.getCategories().stream()
                    .filter(c -> c.getName().equals(selectedProject.getCategory()))
                    .findFirst().orElse(null)
            );
            
            // Select priority stars
            if (selectedProject.getPriority() != null) {
                projectPriorityCombo.getSelectionModel().select(selectedProject.getPriority().getStars());
            }
            
            targetEndDatePicker.setValue(selectedProject.getTargetEndDate());
            projectStatusCombo.setValue(selectedProject.getStatus());
        }
    }
    
    private void deleteSelectedProject() {
        if (selectedProject != null) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Delete Project");
            confirmation.setHeaderText("Are you sure you want to delete this project?");
            confirmation.setContentText("This will also delete all associated sessions. This action cannot be undone.");
            
            confirmation.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    projectService.deleteProject(selectedProject.getId());
                    updateDisplay();
                    clearProjectForm();
                    showAlert("Success", "Project deleted successfully!");
                }
            });
        }
    }
    
    private void startSession() {
        if (selectedProject != null) {
            try {
                currentSession = projectService.startProjectSession(selectedProject.getId());
                currentSession.startSession();
                startSessionTimer();
                updateSessionControls();
                showAlert("Success", "Session started for " + selectedProject.getTitle());
            } catch (Exception e) {
                showAlert("Error", e.getMessage());
            }
        }
    }
    
    private void showEndSessionForm() {
        if (currentSession != null) {
            sessionFormContainer.setVisible(true);
            sessionTitleField.clear();
            objectivesArea.clear();
            progressArea.clear();
            nextStepsArea.clear();
            challengesArea.clear();
            notesArea.clear();
            
            // Ensure scrollpane adjusts to new content height
            this.requestLayout();
            
            // Scroll to show the form
            javafx.application.Platform.runLater(() -> {
                double formY = sessionFormContainer.getBoundsInParent().getMinY();
                double viewportHeight = this.getViewportBounds().getHeight();
                double contentHeight = ((VBox) this.getContent()).getHeight();
                if (contentHeight > viewportHeight) {
                    this.setVvalue(Math.min(1.0, formY / (contentHeight - viewportHeight)));
                }
            });
        }
    }
    
    private void saveSession() {
        if (currentSession != null) {
            try {
                String sessionTitle = sessionTitleField.getText().trim();
                String objectives = objectivesArea.getText().trim();
                String progress = progressArea.getText().trim();
                String nextSteps = nextStepsArea.getText().trim();
                String challenges = challengesArea.getText().trim();
                String notes = notesArea.getText().trim();
                
                ProjectSessionEnd sessionEnd = new ProjectSessionEnd(sessionTitle, objectives, progress, nextSteps, challenges, notes);
                projectService.endProjectSession(currentSession, sessionEnd);
                
                stopSessionTimer();
                currentSession = null;
                sessionFormContainer.setVisible(false);
                this.requestLayout();
                sessionTimerLabel.setText("Session completed!");
                updateSessionControls();
                updateDisplay();
                showAlert("Success", "Session saved successfully!");
            } catch (Exception e) {
                showAlert("Error", e.getMessage());
            }
        }
    }
    
    private void cancelSession() {
        sessionFormContainer.setVisible(false);
        this.requestLayout();
        
        if (currentSession != null) {
            // Remove the session that was started but not completed
            projectService.deleteProjectSession(currentSession.getId());
            stopSessionTimer();
            currentSession = null;
            sessionTimerLabel.setText("Session cancelled");
            updateSessionControls();
        }
    }
    
    private void updateSessionHistory(Project filterProject) {
        if (filterProject == null) {
            sessionListView.getItems().setAll(projectService.getProjectSessions());
        } else {
            sessionListView.getItems().setAll(projectService.getSessionsForProject(filterProject.getId()));
        }
    }
    
    public void updateDisplay() {
        if (projectListView != null) {
            projectListView.getItems().setAll(projectService.getProjects());
        }
        updateCategoryCombo();
        if (sessionListView != null) {
            updateSessionHistory(null);
        }
    }
    
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    // Custom list cells
    private static class ProjectListCell extends ListCell<Project> {
        @Override
        protected void updateItem(Project project, boolean empty) {
            super.updateItem(project, empty);
            if (empty || project == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox content = new VBox(2);
                Label title = new Label(project.getTitle());
                title.setFont(Font.font("System", FontWeight.BOLD, 14));
                
                Label details = new Label(String.format("%s | %s | %d sessions | %s", 
                    project.getStatus(), 
                    project.getPriority().toString(),
                    project.getTotalSessionsCount(),
                    project.getFormattedTotalTime()));
                details.setFont(Font.font("System", 11));
                details.setTextFill(Color.GRAY);
                
                content.getChildren().addAll(title, details);
                setGraphic(content);
            }
        }
    }
    
    private class SessionListCell extends ListCell<ProjectSession> {
        @Override
        protected void updateItem(ProjectSession session, boolean empty) {
            super.updateItem(session, empty);
            if (empty || session == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox content = new VBox(8);
                content.setPadding(new Insets(10));
                content.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 3, 0, 0, 1);");
                
                Project project = projectService.getProjectById(session.getProjectId()).orElse(null);
                String projectTitle = project != null ? project.getTitle() : "Unknown Project";
                
                // Header with project and session title
                HBox headerBox = new HBox(10);
                headerBox.setAlignment(Pos.CENTER_LEFT);
                
                Label projectLabel = new Label("📁 " + projectTitle);
                projectLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
                projectLabel.setTextFill(Color.web("#2c3e50"));
                
                if (session.getSessionTitle() != null && !session.getSessionTitle().trim().isEmpty()) {
                    Label sessionLabel = new Label("• " + session.getSessionTitle());
                    sessionLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
                    sessionLabel.setTextFill(Color.web("#34495e"));
                    headerBox.getChildren().addAll(projectLabel, sessionLabel);
                } else {
                    headerBox.getChildren().add(projectLabel);
                }
                
                // Metrics row
                HBox metricsBox = new HBox(20);
                metricsBox.setAlignment(Pos.CENTER_LEFT);
                
                Label dateLabel = new Label("📅 " + session.getDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
                dateLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
                dateLabel.setTextFill(Color.web("#7f8c8d"));
                
                Label durationLabel = new Label("⏱️ " + session.getDurationMinutes() + " min");
                durationLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
                durationLabel.setTextFill(Color.web("#3498db"));
                
                Label pointsLabel = new Label("🏆 " + session.getPointsEarned() + " pts");
                pointsLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 11));
                pointsLabel.setTextFill(Color.web("#27ae60"));
                
                metricsBox.getChildren().addAll(dateLabel, durationLabel, pointsLabel);
                
                content.getChildren().addAll(headerBox, metricsBox);
                
                // Progress preview (if available)
                if (session.getProgress() != null && !session.getProgress().trim().isEmpty()) {
                    String progressPreview = session.getProgress().length() > 100 ? 
                                           session.getProgress().substring(0, 100) + "..." : 
                                           session.getProgress();
                    Label progressLabel = new Label("✅ " + progressPreview);
                    progressLabel.setFont(Font.font("System", FontWeight.NORMAL, 10));
                    progressLabel.setTextFill(Color.web("#6c757d"));
                    progressLabel.setWrapText(true);
                    content.getChildren().add(progressLabel);
                }
                
                // Next steps preview (if available)
                if (session.getNextSteps() != null && !session.getNextSteps().trim().isEmpty()) {
                    String nextStepsPreview = session.getNextSteps().length() > 80 ? 
                                            session.getNextSteps().substring(0, 80) + "..." : 
                                            session.getNextSteps();
                    Label nextStepsLabel = new Label("📋 Next: " + nextStepsPreview);
                    nextStepsLabel.setFont(Font.font("System", FontWeight.NORMAL, 10));
                    nextStepsLabel.setTextFill(Color.web("#8e44ad"));
                    nextStepsLabel.setWrapText(true);
                    content.getChildren().add(nextStepsLabel);
                }
                
                setGraphic(content);
            }
        }
    }
    
    private void startSessionTimer() {
        if (sessionTimer != null) {
            sessionTimer.stop();
        }
        
        sessionTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateSessionTimer()));
        sessionTimer.setCycleCount(Timeline.INDEFINITE);
        sessionTimer.play();
    }
    
    private void stopSessionTimer() {
        if (sessionTimer != null) {
            sessionTimer.stop();
            sessionTimer = null;
        }
    }
    
    private void updateSessionTimer() {
        if (currentSession != null && currentSession.isActive()) {
            currentSession.updateRealTimeProgress();
            int elapsedMinutes = currentSession.getCurrentElapsedMinutes();
            int hours = elapsedMinutes / 60;
            int minutes = elapsedMinutes % 60;
            
            String timeDisplay;
            if (hours > 0) {
                timeDisplay = String.format("⏱️ Session running: %dh %02dm", hours, minutes);
            } else {
                timeDisplay = String.format("⏱️ Session running: %dm", minutes);
            }
            
            sessionTimerLabel.setText(timeDisplay);
            sessionTimerLabel.setTextFill(Color.web("#27ae60"));
        } else {
            sessionTimerLabel.setText("Ready to start session");
            sessionTimerLabel.setTextFill(Color.web("#2c3e50"));
        }
    }
    
    @Override
    public Node getView() {
        return this;
    }
}