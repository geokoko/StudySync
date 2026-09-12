package com.studysync.domain.entity;

import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.domain.valueobject.TaskStatus;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Domain entity representing a task in the StudySync system.
 * Uses Active Record pattern - handles its own database operations.
 */
public class Task {
    private static final Logger logger = LoggerFactory.getLogger(Task.class);
    private static JdbcTemplate jdbcTemplate;
    
    public static void setJdbcTemplate(JdbcTemplate template) {
        jdbcTemplate = template;
    }
    
    private String id;
    
    @NotBlank(message = "Title is required")
    @Size(min = 1, max = 200, message = "Title must be between 1 and 200 characters")
    private String title;
    
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;
    
    @NotBlank(message = "Category is required")
    private String category;
    
    @NotNull(message = "Priority is required")
    private TaskPriority priority;
    
    private LocalDate deadline;
    
    @NotNull(message = "Status is required")
    private TaskStatus status;
    
    @Min(value = 0, message = "Points cannot be negative")
    private int points;
    
    private LocalDateTime createdAt;

    /**
     * Recurrence pattern for repeating tasks.
     * Format: "intervalWeeks:daysOfWeek" e.g. "1:1,3,5" = every week on Mon,Wed,Fri.
     * "2:1,4" = every 2 weeks on Mon,Thu.
     * NULL means this is a one-off (non-recurring) task.
     */
    private String recurringPattern;

    /**
     * Start date for recurring tasks.  Acts as:
     * <ul>
     *   <li>The first date from which the task can appear</li>
     *   <li>The recurrence interval anchor (the Monday of start date's week
     *       is used instead of createdAt for interval calculation)</li>
     * </ul>
     * NULL for non-recurring tasks.  When null on a recurring task, the
     * system falls back to {@link #createdAt} for backward compatibility.
     */
    private LocalDate startDate;

    /**
     * Last date a recurring task may produce an occurrence. NULL means the
     * recurrence never ends.
     *
     * <p>This used to be conflated with {@link #deadline}, which left recurring
     * tasks unable to express a real due date. They are separate now:
     * {@code recurrenceEndDate} says when repeating stops, {@code deadline}
     * says when the task must be done - and the deadline is what drives
     * overdue badges and the timeliness part of the score, for recurring and
     * one-off tasks alike.</p>
     */
    private LocalDate recurrenceEndDate;

    /**
     * Date the task was completed, or NULL while it is unfinished. Set when the
     * status becomes COMPLETED and cleared if it moves back out. Decides which
     * day the task's points land on, so off days exclude it correctly.
     */
    private LocalDate completedAt;

    /**
     * How many days before the deadline this task starts reminding, or NULL for
     * no reminder. The reminder date itself is derived from the deadline rather
     * than stored, so moving the deadline moves the reminder with it.
     */
    private Integer remindDaysBefore;
    private String doneCriteria;

    // Default constructor
    public Task() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.status = TaskStatus.OPEN;
        this.points = 0;
        this.recurringPattern = null;
        this.startDate = null;
    }

    // Constructor without recurring pattern
    public Task(String id, String title, String description, String category, 
                TaskPriority priority, LocalDate deadline, TaskStatus status, int points) {
        this(id, title, description, category, priority, deadline, status, points, null, null);
    }

    // Constructor with recurring pattern (no start date)
    public Task(String id, String title, String description, String category, 
                TaskPriority priority, LocalDate deadline, TaskStatus status, int points,
                String recurringPattern) {
        this(id, title, description, category, priority, deadline, status, points, recurringPattern, null);
    }

    // Constructor with recurring pattern and start date (no recurrence end)
    public Task(String id, String title, String description, String category,
                TaskPriority priority, LocalDate deadline, TaskStatus status, int points,
                String recurringPattern, LocalDate startDate) {
        this(id, title, description, category, priority, deadline, status, points,
             recurringPattern, startDate, null);
    }

    // Full constructor
    public Task(String id, String title, String description, String category,
                TaskPriority priority, LocalDate deadline, TaskStatus status, int points,
                String recurringPattern, LocalDate startDate, LocalDate recurrenceEndDate) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.title = title;
        this.description = description;
        this.category = category;
        this.priority = priority;
        this.deadline = deadline;
        this.status = status != null ? status : TaskStatus.OPEN;
        this.points = points;
        this.createdAt = LocalDateTime.now();
        this.recurringPattern = recurringPattern;
        this.startDate = startDate;
        this.recurrenceEndDate = recurrenceEndDate;

        // Validation
        Objects.requireNonNull(this.id, "id cannot be null");
        Objects.requireNonNull(this.title, "title cannot be null");
        Objects.requireNonNull(this.category, "category cannot be null");
        Objects.requireNonNull(this.priority, "priority cannot be null");
        Objects.requireNonNull(this.status, "status cannot be null");
    }

    // Factory method
    public static Task create(String title, String description, String category, TaskPriority priority, LocalDate deadline) {
        return new Task(
            UUID.randomUUID().toString(),
            title,
            description,
            category,
            priority != null ? priority : new TaskPriority(1),
            deadline,
            TaskStatus.OPEN,
            0
        );
    }

    public void markCompleted() {
        updateStatus(TaskStatus.COMPLETED);
    }

    public void updateStatus(TaskStatus newStatus) {
        this.status = newStatus;
        // Completion date follows the status, so no caller has to remember to
        // maintain it. Re-completing keeps the original date.
        if (newStatus == TaskStatus.COMPLETED) {
            if (this.completedAt == null) {
                this.completedAt = LocalDate.now();
            }
        } else {
            this.completedAt = null;
        }
    }

    /**
     * Whether this task was finished by its deadline.
     *
     * @return {@code true} when completed on or before the deadline, {@code false}
     *         when completed late, and empty when the question does not apply
     *         (unfinished, or no deadline to be measured against)
     */
    public Optional<Boolean> completedOnTime() {
        if (status != TaskStatus.COMPLETED || deadline == null || completedAt == null) {
            return Optional.empty();
        }
        return Optional.of(!completedAt.isAfter(deadline));
    }
    
    public void addPoints(int additionalPoints) {
        this.points += Math.max(0, additionalPoints);
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }



    /**
     * Gets the recurrence pattern.
     * @return the pattern string (e.g. "1:1,3,5"), or null if non-recurring
     */
    public String getRecurringPattern() {
        return recurringPattern;
    }

    /**
     * Sets the recurrence pattern.
     * Format: "intervalWeeks:dayOfWeekValues" e.g. "1:1,3,5" (weekly Mon/Wed/Fri).
     * Set to null to make the task non-recurring.
     * @param recurringPattern the pattern string
     */
    public void setRecurringPattern(String recurringPattern) {
        this.recurringPattern = recurringPattern;
    }

    /**
     * Gets the start date for this recurring task.
     * @return the start date, or null if not set (falls back to createdAt)
     */
    public LocalDate getStartDate() {
        return startDate;
    }

    /**
     * Sets the start date for this recurring task.
     * @param startDate the date from which the task starts recurring
     */
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    /**
     * Last date this recurring task may produce an occurrence.
     * @return the end-of-recurrence date, or {@code null} if it never ends
     */
    public LocalDate getRecurrenceEndDate() {
        return recurrenceEndDate;
    }

    /**
     * Sets the end-of-recurrence date. This is not a deadline - see
     * {@link #getDeadline()} for when the task is actually due.
     * @param recurrenceEndDate last date an occurrence may fall on
     */
    public void setRecurrenceEndDate(LocalDate recurrenceEndDate) {
        this.recurrenceEndDate = recurrenceEndDate;
    }

    /**
     * Date the task was completed.
     * @return the completion date, or {@code null} while unfinished
     */
    public LocalDate getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDate completedAt) {
        this.completedAt = completedAt;
    }

    /**
     * Days before the deadline at which this task starts reminding.
     * @return the offset in days, or {@code null} when no reminder is set
     */
    public Integer getRemindDaysBefore() {
        return remindDaysBefore;
    }

    /**
     * Sets the reminder offset. Values below zero are treated as no reminder.
     * @param remindDaysBefore days before the deadline, or {@code null} for none
     */
    public void setRemindDaysBefore(Integer remindDaysBefore) {
        this.remindDaysBefore = (remindDaysBefore != null && remindDaysBefore >= 0) ? remindDaysBefore : null;
    }

    /** Free text saying what finishing this task means, or {@code null} when nothing was written down. */
    public String getDoneCriteria() {
        return doneCriteria;
    }

    /** Blank text is stored as {@code null}, so clearing the field in the form clears the column. */
    public void setDoneCriteria(String doneCriteria) {
        this.doneCriteria = (doneCriteria == null || doneCriteria.isBlank()) ? null : doneCriteria.trim();
    }

    /**
     * The day this task starts reminding, derived from the deadline.
     *
     * @return the reminder date, or empty when the task has no deadline or no
     *         reminder offset
     */
    public Optional<LocalDate> reminderDate() {
        if (deadline == null || remindDaysBefore == null) {
            return Optional.empty();
        }
        return Optional.of(deadline.minusDays(remindDaysBefore));
    }

    /**
     * Whether this task's reminder is currently due: the reminder day has
     * arrived, the deadline has not yet passed (after that it is simply
     * overdue, which is surfaced on its own), and the task is unresolved.
     *
     * @param today the date to evaluate against
     * @return {@code true} when the task should show a reminder
     */
    public boolean isReminderDue(LocalDate today) {
        // POSTPONED is excluded along with the resolved states: a postponed
        // task's deadline is its resume date, not a due date, so counting down
        // to it would announce "due in N days" for something that is not due
        // then - and would drag it back into a planner that deliberately
        // leaves postponed work out.
        if (today == null || status == TaskStatus.COMPLETED
                || status == TaskStatus.CANCELLED || status == TaskStatus.POSTPONED) {
            return false;
        }
        return reminderDate()
                .map(remindOn -> !today.isBefore(remindOn) && !today.isAfter(deadline))
                .orElse(false);
    }

    /**
     * Returns the effective recurrence anchor date.
     * If {@code startDate} is set, returns it; otherwise falls back to
     * the date portion of {@code createdAt}.
     * @return the date used as the recurrence interval anchor
     */
    public LocalDate getRecurrenceAnchor() {
        return startDate != null ? startDate : createdAt.toLocalDate();
    }

    /**
     * Checks whether this task is recurring.
     * @return true if the task has a recurrence pattern
     */
    public boolean isRecurring() {
        return recurringPattern != null && !recurringPattern.isBlank();
    }

    /**
     * Returns a human-readable summary of the recurrence schedule.
     * @return e.g. "Every week on Mon, Wed, Fri" or "Not recurring"
     */
    public String getRecurringSummary() {
        if (!isRecurring()) return "Not recurring";
        try {
            String[] parts = recurringPattern.split(":");
            int interval = Integer.parseInt(parts[0]);
            String[] dayNums = parts[1].split(",");
            String[] dayNames = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
            StringBuilder sb = new StringBuilder();
            sb.append(interval == 1 ? "Every week" : "Every " + interval + " weeks");
            sb.append(" on ");
            boolean firstDayAppended = false;
            for (int i = 0; i < dayNums.length; i++) {
                int dayIdx = Integer.parseInt(dayNums[i].trim()) - 1;
                if (dayIdx >= 0 && dayIdx < 7) {
                    if (firstDayAppended) {
                        sb.append(", ");
                    }
                    sb.append(dayNames[dayIdx]);
                    firstDayAppended = true;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return recurringPattern;
        }
    }

    // ==============================================================
    // DATABASE OPERATIONS (Active Record Pattern)
    // ==============================================================
    
    /**
     * Save this task to the database (insert or update).
     */
    public Task save() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized. Make sure Spring context is loaded.");
        }
        
        String id = (this.id == null || this.id.isBlank()) ? UUID.randomUUID().toString() : this.id;
        this.id = id;
        
        // created_at is deliberately absent: H2 keeps unlisted columns on the
        // update path and applies the column DEFAULT on the insert path. Listing
        // it as CURRENT_TIMESTAMP used to reset the creation date on every save,
        // which silently moved the recurrence anchor of any recurring task
        // without an explicit start date.
        String sql = """
            MERGE INTO tasks (id, title, description, category, priority, deadline, status, points,
                              recurring_pattern, start_date, recurrence_end_date, completed_at,
                              remind_days_before, done_criteria)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        jdbcTemplate.update(sql,
            id,
            this.title,
            this.description,
            this.category,
            this.priority != null ? this.priority.stars() : 1,
            this.deadline,
            this.status != null ? this.status.name() : TaskStatus.OPEN.name(),
            this.points,
            this.recurringPattern,
            this.startDate,
            this.recurrenceEndDate,
            this.completedAt,
            this.remindDaysBefore,
            this.doneCriteria
        );
        
        logger.debug("Task saved: {} - {}", id, this.title);
        return this;
    }
    
    /**
     * Delete this task from the database.
     */
    public boolean delete() {
        if (jdbcTemplate == null || this.id == null) {
            return false;
        }
        
        String sql = "DELETE FROM tasks WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, this.id);
        boolean deleted = rowsAffected > 0;
        
        if (deleted) {
            logger.info("Task deleted: {} - {}", this.id, this.title);
        } else {
            logger.warn("Task not found for deletion: {}", this.id);
        }
        
        return deleted;
    }
    
    // ==============================================================
    // STATIC QUERY METHODS
    // ==============================================================
    
    /**
     * Get all tasks ordered by priority and deadline.
     */
    public static List<Task> findAll() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        String sql = "SELECT * FROM tasks ORDER BY priority DESC, deadline ASC NULLS LAST, created_at DESC";
        List<Task> tasks = jdbcTemplate.query(sql, getRowMapper());
        logger.debug("Retrieved {} tasks", tasks.size());
        return tasks;
    }
    
    /**
     * Find a task by its ID.
     */
    public static Optional<Task> findById(String taskId) {
        if (jdbcTemplate == null || taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }
        
        String sql = "SELECT * FROM tasks WHERE id = ?";
        try {
            Task task = jdbcTemplate.queryForObject(sql, getRowMapper(), taskId);
            return Optional.ofNullable(task);
        } catch (Exception e) {
            logger.debug("Task not found: {}", taskId);
            return Optional.empty();
        }
    }
    
    /**
     * Get tasks by category.
     */
    public static List<Task> findByCategory(String category) {
        if (jdbcTemplate == null || category == null || category.isBlank()) {
            return List.of();
        }
        
        String sql = "SELECT * FROM tasks WHERE LOWER(category) = LOWER(?) ORDER BY priority DESC, deadline ASC NULLS LAST";
        return jdbcTemplate.query(sql, getRowMapper(), category.trim());
    }
    
    /**
     * Get tasks by status.
     */
    public static List<Task> findByStatus(TaskStatus status) {
        if (jdbcTemplate == null || status == null) {
            return List.of();
        }
        
        String sql = "SELECT * FROM tasks WHERE status = ? ORDER BY priority DESC, deadline ASC NULLS LAST, created_at DESC";
        return jdbcTemplate.query(sql, getRowMapper(), status.name());
    }
    
    /**
     * Get tasks due by a specific date (excluding completed/cancelled).
     */
    public static List<Task> findDueBy(LocalDate date) {
        if (jdbcTemplate == null || date == null) {
            return List.of();
        }
        
        String sql = "SELECT * FROM tasks WHERE deadline <= ? AND status NOT IN ('COMPLETED', 'CANCELLED') ORDER BY deadline ASC, priority DESC, created_at ASC";
        return jdbcTemplate.query(sql, getRowMapper(), date);
    }
    
    /**
     * Get overdue tasks.
     */
    public static List<Task> findOverdue() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        String sql = "SELECT * FROM tasks WHERE deadline < CURRENT_DATE AND status NOT IN ('COMPLETED', 'CANCELLED') ORDER BY deadline ASC, priority DESC";
        return jdbcTemplate.query(sql, getRowMapper());
    }
    
    /**
     * Get tasks by priority level.
     */
    public static List<Task> findByPriority(int priorityStars) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        if (priorityStars < 1 || priorityStars > 5) {
            throw new IllegalArgumentException("Priority must be between 1 and 5 stars");
        }
        
        String sql = "SELECT * FROM tasks WHERE priority = ? ORDER BY deadline ASC NULLS LAST, created_at DESC";
        return jdbcTemplate.query(sql, getRowMapper(), priorityStars);
    }
    
    /**
     * Search tasks by title or description.
     */
    public static List<Task> search(String searchTerm) {
        if (jdbcTemplate == null || searchTerm == null || searchTerm.isBlank()) {
            return List.of();
        }
        
        String sql = """
            SELECT * FROM tasks 
            WHERE LOWER(title) LIKE LOWER(?) 
               OR LOWER(description) LIKE LOWER(?) 
            ORDER BY 
                CASE WHEN LOWER(title) LIKE LOWER(?) THEN 1 ELSE 2 END,
                priority DESC, 
                deadline ASC NULLS LAST
            """;
        String searchPattern = "%" + searchTerm.trim() + "%";
        String exactTitlePattern = searchTerm.trim() + "%";
        return jdbcTemplate.query(sql, getRowMapper(), searchPattern, searchPattern, exactTitlePattern);
    }
    
    /**
     * Get high priority tasks.
     */
    public static List<Task> findHighPriority(int minPriority) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        String sql = "SELECT * FROM tasks WHERE priority >= ? AND status <> 'COMPLETED' ORDER BY priority DESC, deadline ASC NULLS LAST";
        return jdbcTemplate.query(sql, getRowMapper(), minPriority);
    }
    
    /**
     * Delete a task by ID (static method).
     */
    public static boolean deleteById(String taskId) {
        if (jdbcTemplate == null || taskId == null || taskId.isBlank()) {
            return false;
        }
        
        String sql = "DELETE FROM tasks WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, taskId);
        boolean deleted = rowsAffected > 0;
        
        if (deleted) {
            logger.info("Task deleted: {}", taskId);
        } else {
            logger.warn("Task not found for deletion: {}", taskId);
        }
        
        return deleted;
    }
    
    /**
     * Update task status by ID.
     */
    public static boolean updateStatus(String taskId, TaskStatus status) {
        if (jdbcTemplate == null || taskId == null || taskId.isBlank() || status == null) {
            return false;
        }
        
        // completed_at is maintained here rather than at the call sites, so
        // every path that completes a task records the date exactly once.
        String sql = """
            UPDATE tasks
            SET status = ?,
                completed_at = CASE WHEN ? = 'COMPLETED' THEN COALESCE(completed_at, CURRENT_DATE) ELSE NULL END
            WHERE id = ?
            """;
        return jdbcTemplate.update(sql, status.name(), status.name(), taskId) > 0;
    }

    /**
     * Tasks completed within a date range, for scoring.
     *
     * @param start first day of the range, inclusive
     * @param end last day of the range, inclusive
     * @return completed tasks whose completion date falls in the range
     */
    public static List<Task> findCompletedBetween(LocalDate start, LocalDate end) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }

        String sql = "SELECT * FROM tasks WHERE status = 'COMPLETED' AND completed_at BETWEEN ? AND ?";
        return jdbcTemplate.query(sql, getRowMapper(), start, end);
    }

    /**
     * Get all recurring tasks (tasks with a non-null recurring_pattern).
     */
    public static List<Task> findRecurring() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }

        String sql = "SELECT * FROM tasks WHERE recurring_pattern IS NOT NULL ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, getRowMapper());
    }

    /**
     * Get active recurring tasks (not completed/cancelled).
     */
    public static List<Task> findActiveRecurring() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }

        String sql = "SELECT * FROM tasks WHERE recurring_pattern IS NOT NULL AND status NOT IN ('COMPLETED', 'CANCELLED', 'POSTPONED') ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, getRowMapper());
    }
    
    /**
     * Get count of tasks by status.
     */
    public static long countByStatus(TaskStatus status) {
        if (jdbcTemplate == null || status == null) {
            return 0L;
        }
        
        String sql = "SELECT COUNT(*) FROM tasks WHERE status = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, status.name());
        return count != null ? count : 0L;
    }
    
    /**
     * Check if task exists by ID.
     */
    public static boolean existsById(String taskId) {
        if (jdbcTemplate == null || taskId == null || taskId.isBlank()) {
            return false;
        }
        
        String sql = "SELECT 1 FROM tasks WHERE id = ? LIMIT 1";
        try {
            Integer result = jdbcTemplate.queryForObject(sql, Integer.class, taskId);
            return result != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Batch delete multiple tasks.
     */
    public static int deleteByIds(List<String> taskIds) {
        if (jdbcTemplate == null || taskIds == null || taskIds.isEmpty()) {
            return 0;
        }
        
        List<Object[]> validIds = taskIds.stream()
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .map(id -> new Object[]{id})
            .collect(Collectors.toList());
        
        if (validIds.isEmpty()) {
            return 0;
        }
        
        String sql = "DELETE FROM tasks WHERE id = ?";
        int[] results = jdbcTemplate.batchUpdate(sql, validIds);
        return java.util.Arrays.stream(results).sum();
    }
    
    /**
     * RowMapper for converting database rows to Task objects.
     */
    private static RowMapper<Task> getRowMapper() {
        return (rs, rowNum) -> {
            Task task = new Task(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("category"),
                new TaskPriority(rs.getInt("priority")),
                rs.getObject("deadline", LocalDate.class),
                TaskStatus.valueOf(rs.getString("status")),
                rs.getInt("points"),
                rs.getString("recurring_pattern"),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("recurrence_end_date", LocalDate.class)
            );
            task.completedAt = rs.getObject("completed_at", LocalDate.class);
            task.remindDaysBefore = rs.getObject("remind_days_before", Integer.class);
            task.doneCriteria = rs.getString("done_criteria");
            java.sql.Timestamp createdTs = rs.getTimestamp("created_at");
            if (createdTs != null) {
                task.setCreatedAt(createdTs.toLocalDateTime());
            }
            return task;
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Task task = (Task) o;
        return Objects.equals(id, task.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Task{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", status=" + status +
                ", priority=" + priority +
                ", deadline=" + deadline +
                '}';
    }
}
