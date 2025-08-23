package com.studysync.domain.entity;

import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.domain.valueobject.ProjectStatus;
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

/**
 * Domain entity representing a project in the StudySync system.
 * Uses Active Record pattern - handles its own database operations.
 */
public class Project {
    private static final Logger logger = LoggerFactory.getLogger(Project.class);
    private static JdbcTemplate jdbcTemplate;
    
    public static void setJdbcTemplate(JdbcTemplate template) {
        jdbcTemplate = template;
    }
    
    private String id;
    private String title;
    private String description;
    private String category;
    private TaskPriority priority;
    private LocalDate startDate;
    private LocalDate targetEndDate;
    private ProjectStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime lastWorkedOn;
    private int totalSessionsCount;
    private int totalMinutesWorked;
    private LocalDateTime updatedAt;

    // Default constructor
    public Project() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.startDate = LocalDate.now();
        this.status = ProjectStatus.ACTIVE;
        this.totalSessionsCount = 0;
        this.totalMinutesWorked = 0;
    }

    // Full constructor
    public Project(String id, String title, String description, String category,
                   TaskPriority priority, LocalDate startDate, LocalDate targetEndDate,
                   ProjectStatus status, LocalDateTime createdAt, LocalDateTime lastWorkedOn,
                   int totalSessionsCount, int totalMinutesWorked) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.title = title;
        this.description = description;
        this.category = category;
        this.priority = priority;
        this.startDate = startDate != null ? startDate : LocalDate.now();
        this.targetEndDate = targetEndDate;
        this.status = status != null ? status : ProjectStatus.ACTIVE;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.lastWorkedOn = lastWorkedOn;
        this.totalSessionsCount = totalSessionsCount;
        this.totalMinutesWorked = totalMinutesWorked;
        this.updatedAt = LocalDateTime.now();

        // Validation
        Objects.requireNonNull(this.id, "id cannot be null");
        Objects.requireNonNull(this.title, "title cannot be null");
        if (this.title.isBlank()) {
            throw new IllegalArgumentException("Title cannot be blank");
        }
    }

    // Factory method
    public static Project create(String title, String description, String category, TaskPriority priority, LocalDate targetEndDate) {
        return new Project(
            UUID.randomUUID().toString(),
            title,
            description,
            category,
            priority != null ? priority : new TaskPriority(1),
            LocalDate.now(),
            targetEndDate,
            ProjectStatus.ACTIVE,
            LocalDateTime.now(),
            null,
            0,
            0
        );
    }

    // Business logic methods
    public void incrementSessionCount() {
        this.totalSessionsCount++;
        this.lastWorkedOn = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void addWorkedMinutes(int minutes) {
        this.totalMinutesWorked += Math.max(0, minutes);
        this.lastWorkedOn = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void updateStatus(ProjectStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isOverdue() {
        return targetEndDate != null && 
               LocalDate.now().isAfter(targetEndDate) && 
               status == ProjectStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == ProjectStatus.ACTIVE;
    }

    public String getFormattedTotalTime() {
        int hours = totalMinutesWorked / 60;
        int minutes = totalMinutesWorked % 60;
        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }

    public void markCompleted() {
        this.status = ProjectStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markCancelled() {
        this.status = ProjectStatus.CANCELLED;
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

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDate getTargetEndDate() {
        return targetEndDate;
    }

    public void setTargetEndDate(LocalDate targetEndDate) {
        this.targetEndDate = targetEndDate;
        this.updatedAt = LocalDateTime.now();
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastWorkedOn() {
        return lastWorkedOn;
    }

    public void setLastWorkedOn(LocalDateTime lastWorkedOn) {
        this.lastWorkedOn = lastWorkedOn;
        this.updatedAt = LocalDateTime.now();
    }

    public int getTotalSessionsCount() {
        return totalSessionsCount;
    }

    public void setTotalSessionsCount(int totalSessionsCount) {
        this.totalSessionsCount = totalSessionsCount;
        this.updatedAt = LocalDateTime.now();
    }

    public int getTotalMinutesWorked() {
        return totalMinutesWorked;
    }

    public void setTotalMinutesWorked(int totalMinutesWorked) {
        this.totalMinutesWorked = totalMinutesWorked;
        this.updatedAt = LocalDateTime.now();
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
     * Save this project to the database (insert or update).
     */
    public Project save() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized. Make sure Spring context is loaded.");
        }
        
        this.updatedAt = LocalDateTime.now();
        
        String sql = """
            MERGE INTO projects (id, title, description, category, status, priority, start_date, 
                               deadline, completion_date, progress_percentage, estimated_hours, 
                               actual_hours, notes, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        """;
        
        // Calculate completion date if project is completed
        LocalDate completionDate = this.status == ProjectStatus.COMPLETED ? 
                                   LocalDate.now() : null;
        
        // Calculate progress percentage
        int progressPercentage = calculateProgressPercentage();
        
        // Convert total minutes to hours for storage
        int actualHours = this.totalMinutesWorked / 60;
        
        jdbcTemplate.update(sql,
            this.id, this.title, this.description, this.category,
            this.status.name(), this.priority != null ? this.priority.stars() : 1,
            this.startDate, this.targetEndDate, completionDate,
            progressPercentage, null, // estimated_hours can be null for now
            actualHours, this.description, // using description as notes for now
            this.createdAt
        );
        
        logger.debug("Project saved: {} - {}", this.id, this.title);
        return this;
    }
    
    /**
     * Delete this project from the database.
     */
    public boolean delete() {
        if (jdbcTemplate == null || this.id == null) {
            return false;
        }
        
        String sql = "DELETE FROM projects WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, this.id);
        boolean deleted = rowsAffected > 0;
        
        if (deleted) {
            logger.info("Project deleted: {} - {}", this.id, this.title);
        } else {
            logger.warn("Project not found for deletion: {}", this.id);
        }
        
        return deleted;
    }
    
    /**
     * Calculate progress percentage based on project status and time worked.
     */
    private int calculateProgressPercentage() {
        if (this.status == ProjectStatus.COMPLETED) {
            return 100;
        }
        if (this.status == ProjectStatus.CANCELLED) {
            return 0;
        }
        // For active projects, basic calculation based on time worked
        return Math.min(100, this.totalMinutesWorked / 10);
    }
    
    // ==============================================================
    // STATIC QUERY METHODS
    // ==============================================================
    
    /**
     * Get all projects ordered by priority and creation date.
     */
    public static List<Project> findAll() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        String sql = "SELECT * FROM projects ORDER BY priority DESC, created_at DESC";
        List<Project> projects = jdbcTemplate.query(sql, getRowMapper());
        logger.debug("Retrieved {} projects", projects.size());
        return projects;
    }
    
    /**
     * Find a project by its ID.
     */
    public static Optional<Project> findById(String projectId) {
        if (jdbcTemplate == null || projectId == null) {
            return Optional.empty();
        }
        
        String sql = "SELECT * FROM projects WHERE id = ?";
        try {
            Project project = jdbcTemplate.queryForObject(sql, getRowMapper(), projectId);
            logger.debug("Project found: {}", projectId);
            return Optional.ofNullable(project);
        } catch (Exception e) {
            logger.debug("Project not found: {}", projectId);
            return Optional.empty();
        }
    }
    
    /**
     * Get projects by status.
     */
    public static List<Project> findByStatus(ProjectStatus status) {
        if (jdbcTemplate == null || status == null) {
            return List.of();
        }
        
        String sql = "SELECT * FROM projects WHERE status = ? ORDER BY priority DESC, created_at DESC";
        List<Project> projects = jdbcTemplate.query(sql, getRowMapper(), status.name());
        logger.debug("Retrieved {} projects with status: {}", projects.size(), status);
        return projects;
    }
    
    /**
     * Get active projects (status = ACTIVE).
     */
    public static List<Project> findActive() {
        return findByStatus(ProjectStatus.ACTIVE);
    }
    
    /**
     * Get projects by category.
     */
    public static List<Project> findByCategory(String category) {
        if (jdbcTemplate == null || category == null) {
            return List.of();
        }
        
        String sql = "SELECT * FROM projects WHERE category = ? ORDER BY priority DESC, created_at DESC";
        List<Project> projects = jdbcTemplate.query(sql, getRowMapper(), category);
        logger.debug("Retrieved {} projects for category: {}", projects.size(), category);
        return projects;
    }
    
    /**
     * Get overdue projects (past target end date and still active).
     */
    public static List<Project> findOverdue() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        String sql = "SELECT * FROM projects WHERE deadline < CURRENT_DATE AND status = 'ACTIVE' ORDER BY deadline ASC";
        List<Project> projects = jdbcTemplate.query(sql, getRowMapper());
        logger.debug("Retrieved {} overdue projects", projects.size());
        return projects;
    }
    
    /**
     * Get projects by priority level.
     */
    public static List<Project> findByPriority(int priorityStars) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        String sql = "SELECT * FROM projects WHERE priority = ? ORDER BY created_at DESC";
        List<Project> projects = jdbcTemplate.query(sql, getRowMapper(), priorityStars);
        logger.debug("Retrieved {} projects with priority: {} stars", projects.size(), priorityStars);
        return projects;
    }
    
    /**
     * Search projects by title or description.
     */
    public static List<Project> search(String searchTerm) {
        if (jdbcTemplate == null || searchTerm == null || searchTerm.isBlank()) {
            return List.of();
        }
        
        String sql = "SELECT * FROM projects WHERE LOWER(title) LIKE LOWER(?) OR LOWER(description) LIKE LOWER(?) ORDER BY priority DESC, created_at DESC";
        String likePattern = "%" + searchTerm + "%";
        List<Project> projects = jdbcTemplate.query(sql, getRowMapper(), likePattern, likePattern);
        logger.debug("Found {} projects matching search term: {}", projects.size(), searchTerm);
        return projects;
    }
    
    /**
     * Get recently worked on projects.
     */
    public static List<Project> findRecentlyWorkedOn(int days) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        String sql = """
            SELECT DISTINCT p.* FROM projects p 
            JOIN project_sessions ps ON p.id = ps.project_id 
            WHERE ps.date >= ? 
            ORDER BY p.priority DESC, p.updated_at DESC
        """;
        
        LocalDate cutoffDate = LocalDate.now().minusDays(days);
        List<Project> projects = jdbcTemplate.query(sql, getRowMapper(), cutoffDate);
        logger.debug("Retrieved {} recently worked on projects (last {} days)", projects.size(), days);
        return projects;
    }
    
    /**
     * Delete a project by ID (static method).
     */
    public static boolean deleteById(String projectId) {
        if (jdbcTemplate == null || projectId == null) {
            return false;
        }
        
        String sql = "DELETE FROM projects WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, projectId);
        boolean deleted = rowsAffected > 0;
        
        if (deleted) {
            logger.info("Project deleted: {}", projectId);
        } else {
            logger.warn("Project not found for deletion: {}", projectId);
        }
        
        return deleted;
    }
    
    /**
     * Update project status by ID.
     */
    public static boolean updateStatus(String projectId, ProjectStatus status) {
        if (jdbcTemplate == null || projectId == null || status == null) {
            return false;
        }
        
        String sql = "UPDATE projects SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, status.name(), projectId);
        boolean updated = rowsAffected > 0;
        
        if (updated) {
            logger.debug("Project status updated: {} -> {}", projectId, status);
        } else {
            logger.warn("Project not found for status update: {}", projectId);
        }
        
        return updated;
    }
    
    /**
     * Get project count by status.
     */
    public static long countByStatus(ProjectStatus status) {
        if (jdbcTemplate == null || status == null) {
            return 0L;
        }
        
        String sql = "SELECT COUNT(*) FROM projects WHERE status = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, status.name());
        return count != null ? count : 0L;
    }
    
    /**
     * Get total count of all projects.
     */
    public static long countAll() {
        if (jdbcTemplate == null) {
            return 0L;
        }
        
        String sql = "SELECT COUNT(*) FROM projects";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0L;
    }
    
    /**
     * Get count of active projects.
     */
    public static long countActive() {
        return countByStatus(ProjectStatus.ACTIVE);
    }
    
    /**
     * RowMapper for converting database rows to Project objects.
     */
    private static RowMapper<Project> getRowMapper() {
        return (rs, rowNum) -> {
            String id = rs.getString("id");
            String title = rs.getString("title");
            String description = rs.getString("description");
            String category = rs.getString("category");
            
            String statusStr = rs.getString("status");
            ProjectStatus status = statusStr != null ? ProjectStatus.valueOf(statusStr) : ProjectStatus.ACTIVE;
            
            int priority = rs.getInt("priority");
            TaskPriority taskPriority = new TaskPriority(priority > 0 ? priority : 1);
            
            LocalDate startDate = rs.getObject("start_date", LocalDate.class);
            LocalDate targetEndDate = rs.getObject("deadline", LocalDate.class);
            LocalDateTime createdAt = rs.getObject("created_at", LocalDateTime.class);
            
            // Convert hours back to minutes for the object
            int actualHours = rs.getInt("actual_hours");
            int totalMinutesWorked = actualHours * 60;
            
            return new Project(
                id, title, description, category, taskPriority, startDate, targetEndDate,
                status, createdAt, null, 0, totalMinutesWorked
            );
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Project project = (Project) o;
        return Objects.equals(id, project.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Project{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", status=" + status +
                ", priority=" + priority +
                ", sessions=" + totalSessionsCount +
                ", totalTime=" + getFormattedTotalTime() +
                '}';
    }
}
