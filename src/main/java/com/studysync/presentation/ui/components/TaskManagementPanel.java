
package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.Task;
import com.studysync.domain.entity.TaskReminder;
import com.studysync.domain.valueobject.TaskCategory;
import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.domain.valueobject.TaskStatus;
import com.studysync.domain.service.TaskService;
import com.studysync.domain.service.CategoryService;
import com.studysync.domain.service.ReminderService;
import com.studysync.domain.service.TaskUpdate;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.Node;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.time.LocalDate;
import java.util.List;

public class TaskManagementPanel extends ScrollPane implements RefreshablePanel {
    private final TaskService taskService;
    private final CategoryService categoryService;
    private final ReminderService reminderService;
    private ListView<Task> taskListView;
    private TextField titleField;
    private TextArea descriptionArea;
    private ComboBox<TaskCategory> categoryCombo;
    private TextField newCategoryField;
    private ComboBox<Integer> priorityCombo;
    private DatePicker deadlinePicker;
    private CheckBox recurringCheckBox;
    private Spinner<Integer> intervalSpinner;
    private CheckBox[] dayCheckBoxes;
    private VBox recurringOptionsBox;
    private Task selectedTask;

    public TaskManagementPanel(TaskService taskService, CategoryService categoryService, ReminderService reminderService) {
        this.taskService = taskService;
        this.categoryService = categoryService;
        this.reminderService = reminderService;
        
        // Create main content container
        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(20));
        mainContent.setStyle("-fx-background-color: linear-gradient(to bottom, #f8f9fa, #e9ecef);");
        
        // Set up ScrollPane properties
        this.setContent(mainContent);
        this.setFitToWidth(true);
        this.setFitToHeight(false);
        this.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.getStyleClass().add("tab-content-area");
        
        initializeComponents(mainContent);
        updateTaskList();
    }

    private void initializeComponents(VBox container) {
        // Header
        Label headerLabel = new Label("» Task Management");
        headerLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        headerLabel.setTextFill(Color.web("#2c3e50"));
        
        // Create task form and list in horizontal layout
        HBox mainLayout = new HBox(20);
        mainLayout.setAlignment(Pos.TOP_LEFT);
        
        // Task list on the left
        VBox listPanel = createTaskListPanel();
        listPanel.setPrefWidth(400);
        
        // Task form on the right
        VBox formPanel = createTaskFormPanel();
        formPanel.setPrefWidth(400);
        
        mainLayout.getChildren().addAll(listPanel, formPanel);
        
        container.getChildren().addAll(headerLabel, mainLayout);
    }

    private VBox createTaskListPanel() {
        VBox listPanel = new VBox(15);
        listPanel.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        listPanel.setPadding(new Insets(20));
        
        Label listTitle = new Label("Current Tasks");
        listTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        listTitle.setTextFill(Color.web("#34495e"));
        
        taskListView = new ListView<>();
        taskListView.setPrefHeight(400);
        taskListView.setCellFactory(lv -> new TaskListCell());
        taskListView.getSelectionModel().selectedItemProperty().addListener((obs, oldTask, newTask) -> {
            selectedTask = newTask;
            populateFormWithTask(newTask);
        });
        
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        
        Button deleteBtn = new Button("Delete Task");
        deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
        deleteBtn.setOnAction(e -> deleteSelectedTask());
        
        Button completeBtn = new Button("Mark Complete");
        completeBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
        completeBtn.setOnAction(e -> markTaskComplete());
        
        buttonBox.getChildren().addAll(deleteBtn, completeBtn);
        
        listPanel.getChildren().addAll(listTitle, taskListView, buttonBox);
        return listPanel;
    }

    private VBox createTaskFormPanel() {
        VBox formPanel = new VBox(15);
        formPanel.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        formPanel.setPadding(new Insets(20));
        
        Label formTitle = new Label("Task Details");
        formTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        formTitle.setTextFill(Color.web("#34495e"));
        
        // Form fields
        titleField = new TextField();
        titleField.setPromptText("Task title...");
        
        descriptionArea = new TextArea();
        descriptionArea.setPromptText("Task description...");
        descriptionArea.setPrefRowCount(3);
        
        categoryCombo = new ComboBox<>();
        categoryCombo.setPromptText("Select category");
        updateCategoryCombo();
        
        // New category input section
        newCategoryField = new TextField();
        newCategoryField.setPromptText("Or create new category...");
        Button addCategoryBtn = new Button("+ Add Category");
        addCategoryBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 11px;");
        addCategoryBtn.setOnAction(e -> addNewCategory());
        
        HBox categoryInputBox = new HBox(8);
        categoryInputBox.setAlignment(Pos.CENTER_LEFT);
        categoryInputBox.getChildren().addAll(newCategoryField, addCategoryBtn);
        
        priorityCombo = new ComboBox<>();
        priorityCombo.setPromptText("Select stars (1-5)");
        priorityCombo.getItems().addAll(1, 2, 3, 4, 5);
        priorityCombo.setCellFactory(lv -> new ListCell<Integer>() {
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
        priorityCombo.setButtonCell(new ListCell<Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("Select stars (1-5)");
                } else {
                    TaskPriority temp = new TaskPriority(item);
                    setText(temp.toString() + " (" + item + " star" + (item == 1 ? "" : "s") + ")");
                }
            }
        });
        
        deadlinePicker = new DatePicker();
        deadlinePicker.setPromptText("Select deadline");
        
        // Recurring task controls
        recurringCheckBox = new CheckBox("Recurring task");
        recurringCheckBox.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        recurringOptionsBox = new VBox(8);
        recurringOptionsBox.setPadding(new Insets(8));
        recurringOptionsBox.setStyle("-fx-background-color: #f0f8ff; -fx-background-radius: 5; -fx-border-color: #b0d4f1; -fx-border-radius: 5;");
        recurringOptionsBox.setVisible(false);
        recurringOptionsBox.setManaged(false);
        
        Label intervalLabel = new Label("Repeat every:");
        intervalLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
        
        HBox intervalBox = new HBox(8);
        intervalBox.setAlignment(Pos.CENTER_LEFT);
        intervalSpinner = new Spinner<>(1, 4, 1);
        intervalSpinner.setPrefWidth(70);
        intervalBox.getChildren().addAll(intervalSpinner, new Label("week(s)"));
        
        Label daysLabel = new Label("On these days:");
        daysLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
        
        HBox daysRow = new HBox(6);
        daysRow.setAlignment(Pos.CENTER_LEFT);
        String[] dayLabels = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        dayCheckBoxes = new CheckBox[7];
        for (int i = 0; i < 7; i++) {
            dayCheckBoxes[i] = new CheckBox(dayLabels[i]);
            dayCheckBoxes[i].setFont(Font.font("System", FontWeight.NORMAL, 11));
            daysRow.getChildren().add(dayCheckBoxes[i]);
        }
        
        recurringOptionsBox.getChildren().addAll(intervalLabel, intervalBox, daysLabel, daysRow);
        
        recurringCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            recurringOptionsBox.setVisible(newVal);
            recurringOptionsBox.setManaged(newVal);
        });
        
        HBox actionButtons = new HBox(10);
        actionButtons.setAlignment(Pos.CENTER);
        
        Button saveBtn = new Button("Save Task");
        saveBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        saveBtn.setOnAction(e -> saveTask());
        
        Button clearBtn = new Button("Clear Form");
        clearBtn.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white;");
        clearBtn.setOnAction(e -> clearForm());
        
        actionButtons.getChildren().addAll(saveBtn, clearBtn);
        
        formPanel.getChildren().addAll(
            formTitle,
            new Label("Title:"), titleField,
            new Label("Description:"), descriptionArea,
            new Label("Category:"), categoryCombo,
            categoryInputBox,
            new Label("Priority:"), priorityCombo,
            new Label("Deadline:"), deadlinePicker,
            recurringCheckBox, recurringOptionsBox,
            actionButtons
        );
        
        return formPanel;
    }

    private void updateCategoryCombo() {
        categoryCombo.getItems().clear();
        categoryCombo.getItems().addAll(categoryService.getCategories());
    }
    
    private void addNewCategory() {
        String categoryName = newCategoryField.getText().trim();
        if (categoryName.isEmpty()) {
            showAlert("Error", "Please enter a category name.");
            return;
        }
        
        // Check if category already exists
        boolean categoryExists = categoryService.getCategories().stream()
                .anyMatch(cat -> cat.name().equalsIgnoreCase(categoryName));
                
        if (categoryExists) {
            showAlert("Error", "Category '" + categoryName + "' already exists.");
            return;
        }
        
        try {
            categoryService.addCategory(categoryName);
            updateCategoryCombo();
            
            // Select the newly created category
            TaskCategory newCategory = categoryService.getCategories().stream()
                    .filter(cat -> cat.name().equals(categoryName))
                    .findFirst()
                    .orElse(null);
            if (newCategory != null) {
                categoryCombo.getSelectionModel().select(newCategory);
            }
            
            newCategoryField.clear();
            showAlert("Success", "Category '" + categoryName + "' added successfully!");
        } catch (Exception e) {
            showAlert("Error", "Failed to add category: " + e.getMessage());
        }
    }


    private void updateTaskList() {
        taskListView.getItems().clear();
        taskListView.getItems().addAll(taskService.getTasks());
    }

    private void populateFormWithTask(Task task) {
        if (task != null) {
            titleField.setText(task.getTitle());
            descriptionArea.setText(task.getDescription());
            
            // Find and select category
            categoryCombo.getSelectionModel().select(
                categoryService.getCategories().stream()
                    .filter(c -> c.name().equals(task.getCategory()))
                    .findFirst().orElse(null)
            );
            
            // Select priority stars
            if (task.getPriority() != null) {
                priorityCombo.getSelectionModel().select(task.getPriority().stars());
            }
            
            deadlinePicker.setValue(task.getDeadline());
            
            // Populate recurring fields
            if (task.isRecurring()) {
                recurringCheckBox.setSelected(true);
                try {
                    String[] parts = task.getRecurringPattern().split(":");
                    intervalSpinner.getValueFactory().setValue(Integer.parseInt(parts[0]));
                    String[] dayNums = parts[1].split(",");
                    for (CheckBox cb : dayCheckBoxes) cb.setSelected(false);
                    for (String d : dayNums) {
                        int idx = Integer.parseInt(d.trim()) - 1;
                        if (idx >= 0 && idx < 7) dayCheckBoxes[idx].setSelected(true);
                    }
                } catch (Exception e) {
                    // Malformed pattern — leave defaults
                }
            } else {
                recurringCheckBox.setSelected(false);
            }
        }
    }

    private void saveTask() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            showAlert("Error", "Title cannot be empty!");
            return;
        }

        String description = descriptionArea.getText().trim();
        TaskCategory category = categoryCombo.getValue();
        Integer priorityStars = priorityCombo.getValue();
        LocalDate deadline = deadlinePicker.getValue();

        if (category == null || priorityStars == null) {
            showAlert("Error", "Please select category and priority!");
            return;
        }

        // Build recurring pattern if enabled
        // Convention: null = no change, "" = clear, non-empty = set pattern
        String recurringPattern = "";
        if (recurringCheckBox.isSelected()) {
            StringBuilder days = new StringBuilder();
            for (int i = 0; i < 7; i++) {
                if (dayCheckBoxes[i].isSelected()) {
                    if (days.length() > 0) days.append(",");
                    days.append(i + 1); // 1=MON..7=SUN
                }
            }
            if (days.length() == 0) {
                showAlert("Error", "Please select at least one day for the recurring schedule!");
                return;
            }
            recurringPattern = intervalSpinner.getValue() + ":" + days;
        }

        TaskPriority priority = new TaskPriority(priorityStars);

        try {
            if (selectedTask == null) {
                // Create new task
                Task newTask = new Task(null, title, description, category.name(), 
                                       priority, deadline, TaskStatus.OPEN, 0, recurringPattern);
                taskService.addTask(newTask);
            } else {
                // Update existing task
                TaskUpdate update = new TaskUpdate(title, description, category.name(), priority, deadline, recurringPattern);
                taskService.updateTask(selectedTask, update);
            }
            
            updateTaskList();
            clearForm();
            showAlert("Success", "Task saved successfully!");
        } catch (Exception e) {
            showAlert("Error", e.getMessage());
        }
    }

    private void deleteSelectedTask() {
        if (selectedTask != null) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Delete Task");
            confirmation.setHeaderText("Are you sure you want to delete this task?");
            confirmation.setContentText(selectedTask.getTitle());
            
            confirmation.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    try {
                        taskService.removeTask(selectedTask);
                        reminderService.removeRemindersForTask(selectedTask.getId());
                        updateTaskList();
                        clearForm();
                    } catch (Exception e) {
                        showAlert("Error", "Failed to delete task: " + e.getMessage());
                    }
                }
            });
        }
    }

    private void markTaskComplete() {
        if (selectedTask != null) {
            try {
                taskService.updateTaskStatus(selectedTask, TaskStatus.COMPLETED);
                reminderService.removeRemindersForTask(selectedTask.getId());
                updateTaskList();
                clearForm();
            } catch (Exception e) {
                showAlert("Error", "Failed to mark task as complete: " + e.getMessage());
            }
        }
    }

    private void clearForm() {
        titleField.clear();
        descriptionArea.clear();
        categoryCombo.getSelectionModel().clearSelection();
        newCategoryField.clear();
        priorityCombo.getSelectionModel().clearSelection();
        deadlinePicker.setValue(null);
        recurringCheckBox.setSelected(false);
        intervalSpinner.getValueFactory().setValue(1);
        for (CheckBox cb : dayCheckBoxes) cb.setSelected(false);
        selectedTask = null;
        taskListView.getSelectionModel().clearSelection();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(title.equals("Error") ? Alert.AlertType.ERROR : Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void refreshData() {
        updateCategoryCombo();
        updateTaskList();
    }

    // Custom list cell for better task display
    private static class TaskListCell extends ListCell<Task> {
        @Override
        protected void updateItem(Task task, boolean empty) {
            super.updateItem(task, empty);
            
            if (empty || task == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox content = new VBox(3);
                
                Label titleLabel = new Label(task.getTitle());
                titleLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
                
                Label detailsLabel = new Label(String.format("%s | %s | Due: %s", 
                    task.getCategory(), 
                    task.getPriority(),
                    task.getDeadline() != null ? task.getDeadline().toString() : "No deadline"));
                detailsLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
                detailsLabel.setTextFill(Color.GRAY);
                
                Label statusLabel = new Label(task.getStatus().toString());
                statusLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
                
                // Color code status
                switch (task.getStatus()) {
                    case COMPLETED -> statusLabel.setTextFill(Color.GREEN);
                    case DELAYED -> statusLabel.setTextFill(Color.RED);
                    case IN_PROGRESS -> statusLabel.setTextFill(Color.ORANGE);
                    default -> statusLabel.setTextFill(Color.BLUE);
                }
                
                content.getChildren().addAll(titleLabel, detailsLabel, statusLabel);
                
                if (task.isRecurring()) {
                    Label recurringLabel = new Label("\uD83D\uDD01 " + task.getRecurringSummary());
                    recurringLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
                    recurringLabel.setTextFill(Color.DARKVIOLET);
                    content.getChildren().add(recurringLabel);
                }
                
                setGraphic(content);
            }
        }
    }
    
    @Override
    public Node getView() {
        return this;
    }
    
    @Override
    public void updateDisplay() {
        updateTaskList();
    }
}
