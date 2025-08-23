package com.studysync.domain.service;

import com.studysync.domain.exception.DatabaseException;
import com.studysync.domain.exception.ValidationException;
import com.studysync.domain.entity.Task;
import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.domain.valueobject.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Simplified service layer for task-related operations.
 * Uses Active Record pattern - models handle their own persistence.
 */
@Service
@Validated
@Transactional
public class TaskService {
    private static final Logger logger = LoggerFactory.getLogger(TaskService.class);
    
    private final CategoryService categoryService;
    
    @Autowired
    public TaskService(CategoryService categoryService) {
        this.categoryService = categoryService;
    }
    
    @Transactional(readOnly = true)
    public List<Task> getTasks() {
        return Task.findAll();
    }
    
    public CompletableFuture<List<Task>> getTasksAsync() {
        return CompletableFuture.supplyAsync(Task::findAll);
    }
    
    @Transactional
    public Task addTask(@Valid @NotNull Task task) throws ValidationException, DatabaseException {
        logger.debug("Adding task: {}", task.getTitle());
        
        if (!categoryService.categoryExists(task.getCategory())) {
            logger.warn("Attempted to add task with non-existent category: {}", task.getCategory());
            throw ValidationException.invalidInput("category", task.getCategory());
        }
        
        Task taskToSave = task;
        if (task.getPriority() == null) {
            taskToSave = new Task(task.getId(), task.getTitle(), task.getDescription(), task.getCategory(), new TaskPriority(1), task.getDeadline(), task.getStatus(), task.getPoints());
            logger.debug("Set default priority for task: {}", taskToSave.getTitle());
        }

        taskToSave = applyBusinessRules(taskToSave);
        Task savedTask = taskToSave.save();
        
        logger.info("Successfully added task '{}' with priority {} and status {}", 
                   savedTask.getTitle(), savedTask.getPriority().stars(), savedTask.getStatus());
        
        return savedTask;
    }
    
    @Transactional
    public void removeTask(@NotNull Task task) throws ValidationException {
        if (task == null) {
            throw ValidationException.requiredFieldMissing("task");
        }
        
        if (task.getId() == null || !Task.existsById(task.getId())) {
            throw ValidationException.invalidInput("taskId", task.getTitle());
        }
        
        if (!task.delete()) {
            throw ValidationException.invalidInput("taskId", task.getTitle());
        }
        
        logger.info("Removed task: {}", task.getTitle());
    }
    
    @Transactional
    public Task updateTask(@NotNull Task task, @NotNull TaskUpdate update) throws ValidationException, DatabaseException {
        validateTaskExists(task);
        Task updated = applyTaskUpdates(task, update);
        Task finalTask = applyBusinessRules(updated);
        
        Task savedTask = finalTask.save();
        logger.info("Updated task: {}", savedTask.getTitle());
        return savedTask;
    }
    
    @Transactional
    public void updateTaskStatus(@NotNull Task task, @NotNull TaskStatus newStatus) throws ValidationException {
        validateTaskExists(task);
        if (newStatus == null) {
            throw new IllegalArgumentException("Task status cannot be null");
        }
        
        if (!Task.updateStatus(task.getId(), newStatus)) {
            throw new IllegalArgumentException("Failed to update task status: " + task.getTitle());
        }
        
        logger.info("Updated task status for '{}' to {}", task.getTitle(), newStatus);
    }
    
    @Transactional(readOnly = true)
    public Optional<Task> findTaskByTitle(String title) {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        
        return Task.findAll().stream()
            .filter(task -> task.getTitle().equalsIgnoreCase(title.trim()))
            .findFirst();
    }
    
    @Transactional(readOnly = true)
    public List<Task> searchTasks(String title, String category, Integer priorityStars) {
        return Task.findAll().stream()
                .filter(task -> matchesTitle(task, title))
                .filter(task -> matchesCategory(task, category))
                .filter(task -> matchesPriority(task, priorityStars))
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<Task> searchTasksAdvanced(
            Optional<Predicate<String>> titleFilter,
            Optional<String> categoryFilter,
            Optional<TaskStatus> statusFilter,
            Optional<Integer> priorityFilter) {
        
        return Task.findAll().stream()
            .filter(task -> titleFilter.map(f -> f.test(task.getTitle())).orElse(true))
            .filter(task -> categoryFilter.map(c -> c.equalsIgnoreCase(task.getCategory())).orElse(true))
            .filter(task -> statusFilter.map(s -> s.equals(task.getStatus())).orElse(true))
            .filter(task -> priorityFilter.map(p -> task.getPriority() != null && task.getPriority().stars() == p).orElse(true))
            .collect(Collectors.toList());
    }
    
    @Transactional
    public int markDelayedTasks() {
        LocalDate today = LocalDate.now();
        
        List<Task> delayedTasks = Task.findAll().stream()
                .filter(task -> task.getStatus() != TaskStatus.COMPLETED &&
                                 task.getStatus() != TaskStatus.CANCELLED &&
                                 task.getDeadline() != null &&
                                 task.getDeadline().isBefore(today) &&
                                 task.getStatus() != TaskStatus.DELAYED)
                .map(this::applyBusinessRules)
                .collect(Collectors.toList());
        
        int updatedCount = 0;
        for (Task task : delayedTasks) {
            task.save();
            updatedCount++;
        }
        
        if (updatedCount > 0) {
            logger.info("Marked {} tasks as DELAYED", updatedCount);
        }
        
        return updatedCount;
    }
    
    @Transactional(readOnly = true)
    public List<Task> getTasksByStatus(TaskStatus status) {
        return Task.findByStatus(status);
    }
    
    @Transactional(readOnly = true)
    public List<Task> getActiveTasks() {
        List<Task> openTasks = Task.findByStatus(TaskStatus.OPEN);
        List<Task> inProgressTasks = Task.findByStatus(TaskStatus.IN_PROGRESS);
        
        openTasks.addAll(inProgressTasks);
        return openTasks;
    }
    
    @Transactional(readOnly = true)
    public List<Task> getTasksDueWithinDays(int days) {
        if (days < 0) {
            throw new IllegalArgumentException("Days cannot be negative");
        }
        
        LocalDate cutoffDate = LocalDate.now().plusDays(days);
        return Task.findDueBy(cutoffDate);
    }
    
    @Transactional(readOnly = true)
    public long countTasksByStatus(TaskStatus status) {
        return Task.countByStatus(status);
    }
    
    public CompletableFuture<List<Task>> getHighPriorityTasksAsync(int minPriority) {
        if (minPriority < 1 || minPriority > 5) {
            throw new IllegalArgumentException("Priority must be between 1 and 5 stars");
        }
        
        return CompletableFuture.supplyAsync(() -> 
            Task.findHighPriority(minPriority));
    }
    
    @Transactional(readOnly = true)
    public TaskStatistics getTaskStatistics() {
        List<Task> allTasks = Task.findAll();
        long total = allTasks.size();
        long completed = allTasks.stream().mapToLong(t -> t.getStatus() == TaskStatus.COMPLETED ? 1 : 0).sum();
        long pending = allTasks.stream().mapToLong(t -> (t.getStatus() == TaskStatus.OPEN || t.getStatus() == TaskStatus.IN_PROGRESS) ? 1 : 0).sum();
        long delayed = allTasks.stream().mapToLong(t -> t.getStatus() == TaskStatus.DELAYED ? 1 : 0).sum();
        double completionRate = total > 0 ? (double) completed / total * 100.0 : 0.0;
        return new TaskStatistics(total, completed, pending, delayed, completionRate);
    }
    
    public record TaskStatistics(
        long totalTasks,
        long completedTasks,
        long pendingTasks,
        long delayedTasks,
        double completionRate
    ) {}
    
    @Transactional
    public int batchDeleteTasks(List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return 0;
        }
        
        int deletedCount = Task.deleteByIds(taskIds);
        logger.info("Batch deleted {} tasks", deletedCount);
        return deletedCount;
    }
    
    @Transactional(readOnly = true)
    public List<Task> getOverdueTasks() {
        return Task.findOverdue();
    }
    
    @Transactional(readOnly = true)
    public List<Task> getTasksByCategory(String category) {
        return Task.findByCategory(category);
    }
    
    public boolean isHealthy() {
        try {
            Task.findAll();
            return true;
        } catch (Exception e) {
            logger.error("Task service health check failed", e);
            return false;
        }
    }
    
    private boolean matchesTitle(Task task, String title) {
        return title == null || title.isBlank() || 
               task.getTitle().toLowerCase().contains(title.toLowerCase());
    }
    
    private boolean matchesCategory(Task task, String category) {
        return category == null || category.isBlank() || 
               task.getCategory().equalsIgnoreCase(category);
    }
    
    private boolean matchesPriority(Task task, Integer priorityStars) {
        return priorityStars == null || 
               (task.getPriority() != null && task.getPriority().stars() == priorityStars);
    }
    
    private void validateTaskExists(Task task) throws ValidationException {
        if (task.getId() == null || !Task.existsById(task.getId())) {
            throw ValidationException.invalidInput("taskId", task.getTitle());
        }
    }
    
    private Task applyTaskUpdates(Task task, TaskUpdate update) throws ValidationException {
        String newTitle = Optional.ofNullable(update.title()).filter(t -> !t.isBlank()).orElse(task.getTitle());
        String newDescription = Optional.ofNullable(update.description()).filter(d -> !d.isBlank()).orElse(task.getDescription());
        TaskPriority newPriority = Optional.ofNullable(update.priority()).orElse(task.getPriority());
        
        String newCategory = task.getCategory();
        if (update.category() != null && !update.category().isBlank()) {
            if (!categoryService.categoryExists(update.category())) {
                throw ValidationException.invalidInput("category", update.category());
            }
            newCategory = update.category();
        }
        
        LocalDate newDeadline = task.getDeadline();
        if (update.deadline() != null) {
            if (update.deadline().isBefore(LocalDate.now())) {
                throw ValidationException.invalidDateRange(update.deadline().toString(), LocalDate.now().toString());
            }
            newDeadline = update.deadline();
        }
        
        return new Task(task.getId(), newTitle, newDescription, newCategory, newPriority, newDeadline, task.getStatus(), task.getPoints());
    }
    
    private Task applyBusinessRules(Task task) {
        if (task.getDeadline() != null &&
                task.getDeadline().isBefore(LocalDate.now()) &&
                task.getStatus() != TaskStatus.COMPLETED) {
            logger.info("Task '{}' marked as DELAYED due to overdue deadline", task.getTitle());
            task.updateStatus(TaskStatus.DELAYED);
        }
        return task;
    }
}