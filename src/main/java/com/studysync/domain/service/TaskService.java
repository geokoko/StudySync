package com.studysync.domain.service;

import com.studysync.domain.exception.ValidationException;
import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.Task;
import com.studysync.domain.entity.TaskReschedule;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
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

    /** Guards {@link #markDelayedTasks()} so it runs at most once per calendar day. */
    private volatile LocalDate lastDelayedTasksProcessedDate = null;

    private final CategoryService categoryService;
    private final GoogleDriveService googleDriveService;
    private final DateTimeService dateTimeService;
    
    @Autowired
    public TaskService(CategoryService categoryService, GoogleDriveService googleDriveService,
                       DateTimeService dateTimeService) {
        this.categoryService = categoryService;
        this.googleDriveService = googleDriveService;
        this.dateTimeService = dateTimeService;
    }

    /**
     * Clears cached processing guards so that delay-marking and other
     * once-per-day operations re-run against the newly loaded database.
     * Must be called after a live database reload (e.g. Google Drive download).
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void resetAfterReload() {
        synchronized (this) {
            lastDelayedTasksProcessedDate = null;
        }
        logger.info("TaskService caches reset after DB reload");
    }


    private void markDirtyAndSaveLocally(final String operation) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    googleDriveService.markLocalDbDirty();
                    if (!googleDriveService.saveLocally()) {
                        logger.warn("Local checkpoint failed after {}", operation);
                    }
                }
            });
        } else {
            googleDriveService.markLocalDbDirty();
            if (!googleDriveService.saveLocally()) {
                logger.warn("Local checkpoint failed after {}", operation);
            }
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
            taskToSave = new Task(task.getId(), task.getTitle(), task.getDescription(), task.getCategory(),
                    new TaskPriority(1), task.getDeadline(), task.getStatus(), task.getPoints(),
                    task.getRecurringPattern(), task.getStartDate(), task.getRecurrenceEndDate());
            // The constructor covers neither of these, and rebuilding the task
            // here must not quietly drop fields the caller set.
            taskToSave.setRemindDaysBefore(task.getRemindDaysBefore());
            taskToSave.setCompletedAt(task.getCompletedAt());
            logger.debug("Set default priority for task: {}", taskToSave.getTitle());
        }

        taskToSave = applyBusinessRules(taskToSave);
        Task savedTask = taskToSave.save();
        
        logger.info("Successfully added task '{}' with priority {} and status {}", 
                   savedTask.getTitle(), savedTask.getPriority().stars(), savedTask.getStatus());
        markDirtyAndSaveLocally("task creation");
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
        markDirtyAndSaveLocally("task deletion");
    }
    
    @Transactional
    public Task updateTask(@NotNull Task task, @NotNull TaskUpdate update) {
        validateTaskExists(task);
        Task updated = applyTaskUpdates(task, update);
        Task finalTask = applyBusinessRules(updated);

        Task savedTask = finalTask.save();
        if (!Objects.equals(task.getDeadline(), savedTask.getDeadline()) && savedTask.getDeadline() != null) {
            new TaskReschedule(savedTask.getId(), task.getDeadline(), savedTask.getDeadline()).save();
            logger.info("Recorded reschedule for task '{}': {} -> {}",
                    savedTask.getTitle(), task.getDeadline(), savedTask.getDeadline());
        }
        logger.info("Updated task: {}", savedTask.getTitle());
        markDirtyAndSaveLocally("task update");
        return savedTask;
    }

    /**
     * Moves a task to a new due date, recording the change in the reschedule
     * history. For a DELAYED task this also revives it (status back to OPEN);
     * for a POSTPONED task the new deadline acts as the resume date and the
     * status is left alone until the daily pass resurfaces it.
     */
    @Transactional
    public Task rescheduleTask(@NotNull Task task, @NotNull LocalDate newDeadline) {
        if (newDeadline == null) {
            throw ValidationException.requiredFieldMissing("newDeadline");
        }
        return updateTask(task, new TaskUpdate(null, null, null, null, newDeadline, null, null));
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
        markDirtyAndSaveLocally("task status update");
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
            .filter(task -> categoryFilter.map(c -> task.getCategory() != null && c.equalsIgnoreCase(task.getCategory())).orElse(true))
            .filter(task -> statusFilter.map(s -> s.equals(task.getStatus())).orElse(true))
            .filter(task -> priorityFilter.map(p -> task.getPriority() != null && task.getPriority().stars() == p).orElse(true))
            .collect(Collectors.toList());
    }
    
    @Transactional
    public int markDelayedTasks() {
        LocalDate today = dateTimeService.getCurrentDate();

        // The check-and-set must be atomic so concurrent callers cannot both
        // pass the guard and run the DB scan.
        synchronized (this) {
            if (today.equals(lastDelayedTasksProcessedDate)) {
                return 0;
            }
            lastDelayedTasksProcessedDate = today;
        }

        // Resurface postponed tasks whose resume date (their deadline) has
        // arrived: OPEN when it resumes today, straight to DELAYED when the
        // resume date was already missed.
        int updatedCount = 0;
        for (Task task : Task.findByStatus(TaskStatus.POSTPONED)) {
            if (task.getDeadline() == null || task.getDeadline().isAfter(today)) {
                continue;
            }
            boolean missed = task.getDeadline().isBefore(today);
            task.updateStatus(missed ? TaskStatus.DELAYED : TaskStatus.OPEN);
            task.save();
            updatedCount++;
            logger.info("Postponed task '{}' {}", task.getTitle(),
                    missed ? "missed its resume date, marked DELAYED" : "resumed as OPEN");
        }

        List<Task> delayedTasks = Task.findAll().stream()
                .filter(task -> task.getStatus() != TaskStatus.COMPLETED &&
                                 task.getStatus() != TaskStatus.CANCELLED &&
                                 task.getStatus() != TaskStatus.POSTPONED &&
                                 task.getDeadline() != null &&
                                 task.getDeadline().isBefore(today) &&
                                 task.getStatus() != TaskStatus.DELAYED)
                .map(this::applyBusinessRules)
                .collect(Collectors.toList());

        for (Task task : delayedTasks) {
            task.save();
            updatedCount++;
        }

        if (updatedCount > 0) {
            logger.info("Updated {} tasks in the daily delayed/postponed pass", updatedCount);
            markDirtyAndSaveLocally("delayed task processing");
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
        if (deletedCount > 0) {
            markDirtyAndSaveLocally("batch task deletion");
        }
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
     * Returns the tasks that are relevant for a specific calendar date.
     *
     * <h3>Recurring tasks</h3>
     * Active (OPEN / IN_PROGRESS) recurring tasks whose pattern matches the
     * given date (using the task's creation Monday as the interval reference).
     * If the task has a deadline, occurrences after that deadline are excluded
     * (the deadline acts as an end-of-recurrence boundary).
     *
     * <h3>Non-recurring tasks</h3>
     * <ul>
     *   <li><strong>deadline == date</strong> — shown (this is the task's due date)</li>
     *   <li><strong>deadline &lt; date</strong> (overdue) — shown from the deadline
     *       date onwards so the task remains visible until resolved</li>
     *   <li><strong>deadline &gt; date</strong> (future) — not shown yet</li>
     *   <li><strong>deadline == null</strong> — shown only when {@code date} is
     *       today (undated tasks surface as daily to-dos)</li>
     * </ul>
     * In all cases the task must still be unresolved (OPEN, IN_PROGRESS,
     * POSTPONED or DELAYED).
     *
     * @param date the date to retrieve relevant tasks for
     * @return list of tasks relevant for the date
     */
    @Transactional(readOnly = true)
    /**
     * Returns the tasks that should appear in the daily planner for the given date.
     * <p>
     * The result includes:
     * <ul>
     *   <li>Active tasks ({@link TaskStatus#OPEN} and {@link TaskStatus#IN_PROGRESS})</li>
     *   <li>Delayed tasks ({@link TaskStatus#DELAYED})</li>
     *   <li>Tasks linked to a {@link StudyGoal} on the given date, when their status is
     *       {@link TaskStatus#IN_PROGRESS} or {@link TaskStatus#DELAYED}, even if they would
     *       not otherwise surface based on deadline or date rules</li>
     * </ul>
     * POSTPONED and CANCELLED tasks are intentionally excluded from the daily planner.
     *
     * @param date the calendar date for which tasks should be retrieved; if {@code null},
     *             an empty list is returned
     * @return the list of tasks to display for the specified date
     */
    public List<Task> getTasksForDate(LocalDate date) {
        if (date == null) return List.of();

        // Fetch all tasks once and index by ID so the goal-linking pass below
        // can look up tasks without extra DB round-trips.
        List<Task> allTasks = Task.findAll();
        Map<String, Task> tasksById = allTasks.stream()
                .collect(Collectors.toMap(Task::getId, Function.identity(),
                        (existing, replacement) -> existing));

        List<Task> result = allTasks.stream()
            .filter(task -> taskSurfacesOn(task, date))
            .collect(Collectors.toCollection(ArrayList::new));

        // Also include IN_PROGRESS or DELAYED tasks that have a goal planned for
        // this date but would not otherwise surface (e.g. deadline is in the future
        // or the task has no deadline but this isn't today).
        // POSTPONED and CANCELLED tasks are intentionally excluded.
        Set<String> resultIds = result.stream()
                .map(Task::getId)
                .collect(Collectors.toSet());

        StudyGoal.findByDate(date).stream()
                .map(StudyGoal::getTaskId)
                .filter(tid -> tid != null && !tid.isBlank())
                .filter(tid -> !resultIds.contains(tid))
                .distinct()
                .forEach(tid -> {
                    Task task = tasksById.get(tid);
                    if (task != null) {
                        TaskStatus s = task.getStatus();
                        if (s == TaskStatus.IN_PROGRESS || s == TaskStatus.DELAYED) {
                            result.add(task);
                            resultIds.add(task.getId());
                        }
                    }
                });

        return result;
    }

    /**
     * Decides whether a task surfaces in the daily planner for the given date
     * based on its status, deadline, and recurrence rules alone (goal-linked
     * inclusion is handled separately by {@link #getTasksForDate}).
     *
     * @param task the task to test; {@code null} returns {@code false}
     * @param date the planner date; {@code null} returns {@code false}
     * @return {@code true} if the task should appear on that date
     */
    public boolean taskSurfacesOn(Task task, LocalDate date) {
        if (task == null || date == null) {
            return false;
        }
        TaskStatus s = task.getStatus();
        boolean isActive = s == TaskStatus.OPEN || s == TaskStatus.IN_PROGRESS;
        // POSTPONED and CANCELLED tasks are excluded from the daily
        // planner — only DELAYED tasks surface alongside active ones.
        boolean isPending = s == TaskStatus.DELAYED;

        if (task.isRecurring()) {
            // DELAYED counts as unresolved here too. Leaving it out used to make
            // a late recurring task disappear from the planner entirely, which
            // is why recurring tasks were kept out of DELAYED marking.
            if (!(isActive || isPending)) return false;

            // Nothing appears before the task's own start date - not even an
            // overdue one. This guard has to sit above the deadline check
            // below, not just inside isRecurringOccurrence, or a task whose
            // deadline has passed would surface for every date back to the
            // epoch.
            if (task.getStartDate() != null && date.isBefore(task.getStartDate())) {
                return false;
            }

            // On or after its own deadline and still unresolved: visible every
            // day until resolved, exactly like a one-off overdue task. The
            // deadline day itself is included on purpose - that is the due day.
            // Deliberately not bounded by the end of recurrence - being late
            // outlives the schedule, and the end of repeating must not hide a
            // task the user never finished.
            if (task.getDeadline() != null && !date.isBefore(task.getDeadline())) {
                return true;
            }

            // Remaining bounds belong to the occurrence rule itself.
            return isRecurringOccurrence(task, date);
        }

        // Non-recurring: must be unresolved
        if (!(isActive || isPending)) return false;

        LocalDate deadline = task.getDeadline();
        if (deadline == null) {
            // Undated tasks only surface for today
            return date.equals(dateTimeService.getCurrentDate());
        }
        // Tasks with a deadline: show on the due date and every day after
        // (so overdue tasks remain visible until resolved)
        return !date.isBefore(deadline);
    }

    /**
     * Whether a recurring task's schedule actually lands on a date, ignoring
     * status and deadline. "Has an occurrence here" and "shows up here" are
     * different questions now that a late recurring task also surfaces between
     * its occurrences.
     *
     * @param task the task to test; non-recurring returns {@code false}
     * @param date the date to test
     * @return {@code true} when the recurrence pattern produces an occurrence
     *         on that date, within the task's start and end bounds
     */
    public boolean isRecurringOccurrence(Task task, LocalDate date) {
        if (task == null || date == null || !task.isRecurring()) {
            return false;
        }
        if (task.getStartDate() != null && date.isBefore(task.getStartDate())) {
            return false;
        }
        if (task.getRecurrenceEndDate() != null && date.isAfter(task.getRecurrenceEndDate())) {
            return false;
        }
        // Reference Monday is derived from the task's recurrence anchor
        // (startDate if set, otherwise createdAt), so multi-week intervals are
        // measured consistently.
        LocalDate anchorMonday = task.getRecurrenceAnchor()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return recurringTaskAppliesTo(task, date, anchorMonday);
    }

    /**
     * Tasks whose reminder has come due: the reminder day has arrived and the
     * deadline has not yet passed. Tasks already past their deadline are left
     * out - they are overdue, which the planner surfaces on its own, and
     * showing both would double-report the same task.
     *
     * @param date the day to evaluate against
     * @return unresolved tasks that should show a reminder, soonest deadline first
     */
    @Transactional(readOnly = true)
    public List<Task> getTasksWithDueReminders(LocalDate date) {
        if (date == null) {
            return List.of();
        }
        return Task.findAll().stream()
                .filter(task -> task.isReminderDue(date))
                .sorted(Comparator.comparing(Task::getDeadline))
                .toList();
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
               category.equalsIgnoreCase(task.getCategory());
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
            LocalDate today = dateTimeService.getCurrentDate();
            if (update.deadline().isBefore(today)) {
                throw ValidationException.invalidDateRange(update.deadline().toString(), today.toString());
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

        // Start date: null in the update means "keep existing"
        LocalDate newStartDate = task.getStartDate();
        if (update.startDate() != null) {
            newStartDate = update.startDate();
        }
        // End of recurrence: same convention as start date
        LocalDate newRecurrenceEnd = task.getRecurrenceEndDate();
        if (update.recurrenceEndDate() != null) {
            newRecurrenceEnd = update.recurrenceEndDate();
        }
        // If recurring pattern is being cleared, the recurrence dates go with it
        if (newRecurringPattern == null) {
            newStartDate = null;
            newRecurrenceEnd = null;
        }
        Task updated = new Task(task.getId(), newTitle, newDescription, newCategory, newPriority, newDeadline,
                task.getStatus(), task.getPoints(), newRecurringPattern, newStartDate, newRecurrenceEnd);
        updated.setCompletedAt(task.getCompletedAt());
        // null keeps the existing reminder; CLEAR_REMINDER removes it.
        // setRemindDaysBefore maps any negative value to "no reminder".
        updated.setRemindDaysBefore(update.remindDaysBefore() != null
                ? update.remindDaysBefore() : task.getRemindDaysBefore());
        // The constructor stamps a fresh creation date; keep the real one, or
        // the returned object reports a recurrence anchor the database does not
        // agree with.
        updated.setCreatedAt(task.getCreatedAt());
        return updated;
    }
    
    private Task applyBusinessRules(Task task) {
        LocalDate today = dateTimeService.getCurrentDate();
        // Recurring tasks go DELAYED like any other: their deadline is a real
        // due date, and taskSurfacesOn() keeps a DELAYED recurring task visible.
        // COMPLETED and CANCELLED are terminal; POSTPONED keeps its resume
        // date until markDelayedTasks resurfaces it.
        boolean delayEligible = task.getStatus() != TaskStatus.COMPLETED
                && task.getStatus() != TaskStatus.CANCELLED
                && task.getStatus() != TaskStatus.POSTPONED;
        if (task.getDeadline() != null
                && task.getDeadline().isBefore(today)
                && delayEligible) {
            logger.info("Task '{}' marked as DELAYED due to overdue deadline", task.getTitle());
            task.updateStatus(TaskStatus.DELAYED);
        } else if (task.getStatus() == TaskStatus.DELAYED) {
            // The deadline was removed or moved to today/future — the task
            // is no longer late, so a rescheduled DELAYED task revives.
            logger.info("Task '{}' no longer overdue, reset to OPEN", task.getTitle());
            task.updateStatus(TaskStatus.OPEN);
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
            long weeksBetween = ChronoUnit.WEEKS.between(
                    referenceMonday,
                    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
            return weeksBetween >= 0 && weeksBetween % intervalWeeks == 0;
        } catch (Exception e) {
            logger.warn("Invalid recurring pattern '{}' for task '{}'", task.getRecurringPattern(), task.getTitle());
            return false;
        }
    }

    // ================================================================
    // MISSED RECURRING OCCURRENCE DETECTION
    // ================================================================

    /**
     * Returns missed occurrences of active recurring tasks for yesterday only.
     *
     * <p>An occurrence is "missed" when no achieved {@link StudyGoal} is
     * linked to the task on that date. Only yesterday is checked: a missed
     * occurrence is carried forward by exactly one day, then disappears.
     * The task reappears naturally on its next scheduled recurring date.</p>
     *
     * <p>Today's occurrence is never considered missed — the user still has
     * time to complete it.</p>
     *
     * @param today the current date (usually {@code dateTimeService.getCurrentDate()})
     * @return list of missed occurrences from yesterday, one per task at most
     */
    @Transactional(readOnly = true)
    public List<MissedOccurrence> getMissedRecurringOccurrences(LocalDate today) {
        if (today == null) return List.of();

        // Only carry forward a missed occurrence by exactly one day.
        // If a recurring task was scheduled for yesterday and has no achieved
        // goal for that date, show it today as a carry-forward. It disappears
        // tomorrow and reappears naturally on its next scheduled occurrence.
        LocalDate yesterday = today.minusDays(1);

        List<Task> activeTasks = Task.findActiveRecurring();
        List<MissedOccurrence> result = new ArrayList<>();

        // Deliberately not isRecurringOccurrence(): carry-forward uses a
        // stricter lower bound (createdAt when there is no start date), because
        // a task cannot have missed an occurrence from before it existed.
        for (Task task : activeTasks) {
            LocalDate anchor = task.getRecurrenceAnchor();
            LocalDate anchorMonday = anchor
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

            // Don't consider dates before the task's own start
            LocalDate taskStart = task.getStartDate() != null
                    ? task.getStartDate()
                    : task.getCreatedAt().toLocalDate();
            if (yesterday.isBefore(taskStart)) {
                continue;
            }

            // Occurrences stop at the end of recurrence
            if (task.getRecurrenceEndDate() != null && yesterday.isAfter(task.getRecurrenceEndDate())) {
                continue;
            }

            if (recurringTaskAppliesTo(task, yesterday, anchorMonday)
                    && !StudyGoal.hasHandledGoalForTaskOccurrence(task.getId(), yesterday)) {
                result.add(new MissedOccurrence(task, yesterday));
            }
        }
        return result;
    }

    /**
     * Checks whether a specific past occurrence of a recurring task was
     * "handled" — i.e. at least one achieved study goal is linked to
     * the task on that date.
     *
     * @param task the recurring task
     * @param date the occurrence date to check
     * @return {@code true} if the occurrence was handled
     */
    public boolean isOccurrenceHandled(Task task, LocalDate date) {
        return StudyGoal.hasHandledGoalForTaskOccurrence(task.getId(), date);
    }
}
