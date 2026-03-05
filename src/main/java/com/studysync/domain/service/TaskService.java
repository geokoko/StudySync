package com.studysync.domain.service;

import com.studysync.domain.exception.ValidationException;
import com.studysync.domain.entity.Task;
import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.domain.valueobject.TaskStatus;
import com.studysync.integration.drive.GoogleDriveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    private final GoogleDriveService googleDriveService;
    
    @Autowired
    public TaskService(CategoryService categoryService, GoogleDriveService googleDriveService) {
        this.categoryService = categoryService;
        this.googleDriveService = googleDriveService;
    }

    private void markDirty() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    googleDriveService.markLocalDbDirty();
                }
            });
        } else {
            googleDriveService.markLocalDbDirty();
        }
    }
    
    @Transactional(readOnly = true)
    public List<Task> getTasks() {
        return Task.findAll();
    }
    
    public CompletableFuture<List<Task>> getTasksAsync() {
        return CompletableFuture.supplyAsync(Task::findAll);
    }
    
    @Transactional
    public Task addTask(@Valid @NotNull Task task) {
        logger.debug("Adding task: {}", task.getTitle());
        
        if (!categoryService.categoryExists(task.getCategory())) {
            logger.warn("Attempted to add task with non-existent category: {}", task.getCategory());
            throw ValidationException.invalidInput("category", task.getCategory());
        }
        
        Task taskToSave = task;
        if (task.getPriority() == null) {
            taskToSave = new Task(task.getId(), task.getTitle(), task.getDescription(), task.getCategory(), new TaskPriority(1), task.getDeadline(), task.getStatus(), task.getPoints(), task.getRecurringPattern());
            logger.debug("Set default priority for task: {}", taskToSave.getTitle());
        }

        taskToSave = applyBusinessRules(taskToSave);
        Task savedTask = taskToSave.save();
        
        logger.info("Successfully added task '{}' with priority {} and status {}", 
                   savedTask.getTitle(), savedTask.getPriority().stars(), savedTask.getStatus());
        markDirty();
        return savedTask;
    }
    
    @Transactional
    public void removeTask(@NotNull Task task) {
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
        markDirty();
    }
    
    @Transactional
    public Task updateTask(@NotNull Task task, @NotNull TaskUpdate update) {
        validateTaskExists(task);
        Task updated = applyTaskUpdates(task, update);
        Task finalTask = applyBusinessRules(updated);
        
        Task savedTask = finalTask.save();
        logger.info("Updated task: {}", savedTask.getTitle());
        markDirty();
        return savedTask;
    }
    
    @Transactional
    public void updateTaskStatus(@NotNull Task task, @NotNull TaskStatus newStatus) {
        validateTaskExists(task);
        if (newStatus == null) {
            throw new IllegalArgumentException("Task status cannot be null");
        }
        
        if (!Task.updateStatus(task.getId(), newStatus)) {
            throw new IllegalArgumentException("Failed to update task status: " + task.getTitle());
        }
        
        logger.info("Updated task status for '{}' to {}", task.getTitle(), newStatus);
        markDirty();
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
            markDirty();
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

    /**
     * Get tasks relevant for a specific date.
     * Includes:
     * <ul>
     *   <li>Active (OPEN / IN_PROGRESS) non-recurring tasks</li>
     *   <li>Active recurring tasks whose pattern matches the given date
     *       (using the task's creation Monday as the reference)</li>
     *   <li>POSTPONED / DELAYED non-recurring tasks (still surfaced until resolved)</li>
     * </ul>
     *
     * @param date the date to retrieve relevant tasks for
     * @return list of tasks relevant for the date, ordered by priority DESC then deadline ASC
     */
    @Transactional(readOnly = true)
    public List<Task> getTasksForDate(LocalDate date) {
        if (date == null) return List.of();

        return Task.findAll().stream()
            .filter(task -> {
                TaskStatus s = task.getStatus();
                boolean isActive = s == TaskStatus.OPEN || s == TaskStatus.IN_PROGRESS;
                boolean isPending = s == TaskStatus.POSTPONED || s == TaskStatus.DELAYED;

                if (task.isRecurring()) {
                    // Reference Monday is derived from the task's creation date,
                    // so that multi-week intervals (e.g. every 2 weeks) are
                    // measured from when the task was created — not from the
                    // queried date (which would always yield weeksBetween == 0).
                    LocalDate taskCreationMonday = task.getCreatedAt().toLocalDate()
                            .with(java.time.temporal.TemporalAdjusters
                                    .previousOrSame(java.time.DayOfWeek.MONDAY));
                    return isActive && recurringTaskAppliesTo(task, date, taskCreationMonday);
                } else {
                    // Non-recurring: show if active or still pending resolution
                    return isActive || isPending;
                }
            })
            .collect(Collectors.toList());
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
    
    private void validateTaskExists(Task task) {
        if (task.getId() == null || !Task.existsById(task.getId())) {
            throw ValidationException.invalidInput("taskId", task.getTitle());
        }
    }
    
    private Task applyTaskUpdates(Task task, TaskUpdate update) {
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
        
        String newRecurringPattern = task.getRecurringPattern();
        if (update.recurringPattern() != null) {
            // Convention:
            //   null  -> keep existing pattern (no change)
            //   ""    -> clear existing pattern
            //   other -> set new pattern
            if (update.recurringPattern().isEmpty()) {
                newRecurringPattern = null;
            } else {
                newRecurringPattern = update.recurringPattern();
            }
        }
        return new Task(task.getId(), newTitle, newDescription, newCategory, newPriority, newDeadline, task.getStatus(), task.getPoints(), newRecurringPattern);
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

    // ================================================================
    // RECURRING TASK OPERATIONS
    // ================================================================

    /**
     * Get all recurring tasks.
     */
    @Transactional(readOnly = true)
    public List<Task> getRecurringTasks() {
        return Task.findRecurring();
    }

    /**
     * Get active recurring tasks (not completed/cancelled).
     */
    @Transactional(readOnly = true)
    public List<Task> getActiveRecurringTasks() {
        return Task.findActiveRecurring();
    }

    /**
     * Check if a recurring task applies to a given date based on its pattern.
     *
     * @param task the recurring task
     * @param date the date to check
     * @param referenceMonday the Monday of the week the task was created (for interval calculation)
     * @return true if the task should recur on this date
     */
    public boolean recurringTaskAppliesTo(Task task, LocalDate date, LocalDate referenceMonday) {
        if (!task.isRecurring()) return false;
        try {
            String[] parts = task.getRecurringPattern().split(":");
            int intervalWeeks = Integer.parseInt(parts[0]);
            String[] dayNums = parts[1].split(",");

            // Check day of week
            int todayDow = date.getDayOfWeek().getValue(); // 1=MON..7=SUN
            boolean dayMatches = false;
            for (String d : dayNums) {
                if (Integer.parseInt(d.trim()) == todayDow) {
                    dayMatches = true;
                    break;
                }
            }
            if (!dayMatches) return false;

            // Check week interval
            long weeksBetween = java.time.temporal.ChronoUnit.WEEKS.between(
                    referenceMonday,
                    date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)));
            return weeksBetween >= 0 && weeksBetween % intervalWeeks == 0;
        } catch (Exception e) {
            logger.warn("Invalid recurring pattern '{}' for task '{}'", task.getRecurringPattern(), task.getTitle());
            return false;
        }
    }
}
