package com.studysync.domain.entity;

import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.domain.valueobject.TaskStatus;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
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
    private LocalDateTime updatedAt;

    // Default constructor
    public Task() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = TaskStatus.OPEN;
        this.points = 0;
    }

    // Full constructor
    public Task(String id, String title, String description, String category, 
                TaskPriority priority, LocalDate deadline, TaskStatus status, int points) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.title = title;
        this.description = description;
        this.category = category;
        this.priority = priority;
        this.deadline = deadline;
        this.status = status != null ? status : TaskStatus.OPEN;
        this.points = points;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        
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

    // Business logic methods
    public boolean isOverdue() {
        return deadline != null && deadline.isBefore(LocalDate.now()) && 
               status != TaskStatus.COMPLETED;
    }

    public boolean hasDeadline() {
        return deadline != null;
    }
    
    public void markCompleted() {
        this.status = TaskStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void updateStatus(TaskStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void addPoints(int additionalPoints) {
        this.points += Math.max(0, additionalPoints);
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
        this.updatedAt = LocalDateTime.now();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.updatedAt = LocalDateTime.now();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
        this.updatedAt = LocalDateTime.now();
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
        this.updatedAt = LocalDateTime.now();
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
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
        this.updatedAt = LocalDateTime.now();
        
        String sql = """
            MERGE INTO tasks (id, title, description, category, priority, deadline, status, points, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;
        
        jdbcTemplate.update(sql,
            id,
            this.title,
            this.description,
            this.category,
            this.priority != null ? this.priority.stars() : 1,
            this.deadline,
            this.status != null ? this.status.name() : TaskStatus.OPEN.name(),
            this.points
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
        
        String sql = "UPDATE tasks SET status = ? WHERE id = ?";
        return jdbcTemplate.update(sql, status.name(), taskId) > 0;
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
            return new Task(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("category"),
                new TaskPriority(rs.getInt("priority")),
                rs.getObject("deadline", LocalDate.class),
                TaskStatus.valueOf(rs.getString("status")),
                rs.getInt("points")
            );
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
