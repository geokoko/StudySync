package com.studysync.domain.entity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Domain entity representing a project session in the StudySync system.
 * Uses Active Record pattern - handles its own database operations.
 */
public class ProjectSession {
    private static final Logger logger = LoggerFactory.getLogger(ProjectSession.class);
    private static JdbcTemplate jdbcTemplate;
    
    public static void setJdbcTemplate(JdbcTemplate template) {
        jdbcTemplate = template;
    }
    private String id;
    private String projectId; // Links to a specific Project
    private LocalDate date;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int durationMinutes;
    private boolean completed;
    private String sessionTitle; // Title for this specific session/iteration
    private String objectives; // What you planned to accomplish in this session
    private String progress; // What you actually accomplished
    private String nextSteps; // What to do in the next session
    private String challenges; // Any obstacles encountered
    private String notes; // General notes/thoughts about this session
    private int pointsEarned;
    
    // Real-time tracking fields
    private boolean isActive;
    private LocalDateTime lastUpdateTime;
    private int currentElapsedMinutes;

    public ProjectSession() {
        this.id = java.util.UUID.randomUUID().toString();
        this.date = LocalDate.now();
        this.completed = false;
        this.pointsEarned = 0;
        this.isActive = false;
        this.currentElapsedMinutes = 0;
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    public ProjectSession(String projectId) {
        this();
        this.projectId = projectId;
    }

    public ProjectSession(String id, String projectId, LocalDate date, LocalDateTime startTime,
                         LocalDateTime endTime, int durationMinutes, boolean completed,
                         String sessionTitle, String objectives, String progress,
                         String nextSteps, String challenges, String notes, int pointsEarned) {
        this.id = id != null ? id : java.util.UUID.randomUUID().toString();
        this.projectId = projectId;
        this.date = date != null ? date : LocalDate.now();
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMinutes = durationMinutes;
        this.completed = completed;
        this.sessionTitle = sessionTitle;
        this.objectives = objectives;
        this.progress = progress;
        this.nextSteps = nextSteps;
        this.challenges = challenges;
        this.notes = notes;
        this.pointsEarned = pointsEarned;
        this.isActive = false;
        this.currentElapsedMinutes = 0;
        this.lastUpdateTime = LocalDateTime.now();
    }

    public void calculatePoints() {
        int basePoints = Math.min(durationMinutes / 10, 60);
        int completionBonus = completed ? 30 : 0;
        int progressBonus = (progress != null && !progress.trim().isEmpty()) ? 20 : 0;
        int notesBonus = (notes != null && !notes.trim().isEmpty()) ? 10 : 0;
        
        this.pointsEarned = basePoints + completionBonus + progressBonus + notesBonus;
    }

    // Real-time tracking methods
    public void startSession() {
        this.startTime = LocalDateTime.now();
        this.isActive = true;
        this.lastUpdateTime = LocalDateTime.now();
        this.currentElapsedMinutes = 0;
    }

    public void endSession() {
        this.endTime = LocalDateTime.now();
        this.isActive = false;
        if (this.startTime != null) {
            this.durationMinutes = (int) Duration.between(this.startTime, this.endTime).toMinutes();
        }
        this.completed = true;
        calculatePoints();
    }

    public void pauseSession() {
        if (this.isActive && this.startTime != null) {
            this.currentElapsedMinutes = (int) Duration.between(this.startTime, LocalDateTime.now()).toMinutes();
            this.durationMinutes = this.currentElapsedMinutes;
            this.isActive = false;
        }
    }

    public void resumeSession() {
        if (!this.isActive) {
            // Adjust start time to account for already elapsed time
            this.startTime = LocalDateTime.now().minusMinutes(this.currentElapsedMinutes);
            this.isActive = true;
            this.lastUpdateTime = LocalDateTime.now();
        }
    }

    public void updateRealTimeProgress() {
        if (this.isActive && this.startTime != null) {
            this.currentElapsedMinutes = (int) Duration.between(this.startTime, LocalDateTime.now()).toMinutes();
            this.durationMinutes = this.currentElapsedMinutes;
            this.lastUpdateTime = LocalDateTime.now();
        }
    }

    public int getCurrentElapsedMinutes() {
        if (this.isActive && this.startTime != null) {
            return (int) Duration.between(this.startTime, LocalDateTime.now()).toMinutes();
        }
        return this.currentElapsedMinutes;
    }
    
    // ==============================================================
    // DATABASE OPERATIONS (Active Record Pattern)
    // ==============================================================
    
    /**
     * Save this project session to the database (insert or update).
     */
    public void save() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized. Make sure Spring context is loaded.");
        }
        
        String sql = """
            MERGE INTO project_sessions (id, project_id, date, start_time, end_time, duration_minutes, 
                                        completed, session_title, objectives, progress, next_steps, 
                                        challenges, notes, points_earned)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        jdbcTemplate.update(sql,
            this.id, this.projectId, this.date, this.startTime, 
            this.endTime, this.durationMinutes, this.completed, 
            this.sessionTitle, this.objectives, this.progress, 
            this.nextSteps, this.challenges, this.notes, 
            this.pointsEarned
        );
        
        logger.debug("Project session saved: {}", this.id);
    }
    
    /**
     * Delete this project session from the database.
     */
    public boolean delete() {
        if (jdbcTemplate == null || this.id == null) {
            return false;
        }
        
        String sql = "DELETE FROM project_sessions WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, this.id);
        boolean deleted = rowsAffected > 0;
        
        if (deleted) {
            logger.info("Project session deleted: {}", this.id);
        } else {
            logger.warn("Project session not found for deletion: {}", this.id);
        }
        
        return deleted;
    }
    
    // ==============================================================
    // STATIC QUERY METHODS
    // ==============================================================
    
    /**
     * Get all project sessions ordered by date and time (most recent first).
     */
    public static List<ProjectSession> findAll() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        String sql = "SELECT * FROM project_sessions ORDER BY date DESC, start_time DESC";
        List<ProjectSession> sessions = jdbcTemplate.query(sql, getRowMapper());
        logger.debug("Retrieved {} project sessions", sessions.size());
        return sessions;
    }
    
    /**
     * Get project sessions for a specific date.
     */
    public static List<ProjectSession> findByDate(LocalDate date) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        String sql = "SELECT * FROM project_sessions WHERE date = ? ORDER BY start_time DESC";
        List<ProjectSession> sessions = jdbcTemplate.query(sql, getRowMapper(), date);
        logger.debug("Retrieved {} project sessions for date: {}", sessions.size(), date);
        return sessions;
    }
    
    /**
     * Find a project session by its ID.
     */
    public static Optional<ProjectSession> findById(String id) {
        if (jdbcTemplate == null || id == null) {
            return Optional.empty();
        }
        
        String sql = "SELECT * FROM project_sessions WHERE id = ?";
        try {
            ProjectSession session = jdbcTemplate.queryForObject(sql, getRowMapper(), id);
            return Optional.ofNullable(session);
        } catch (Exception e) {
            logger.debug("Project session not found: {}", id);
            return Optional.empty();
        }
    }
    
    /**
     * Get project sessions for a specific project.
     */
    public static List<ProjectSession> findByProjectId(String projectId) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        String sql = "SELECT * FROM project_sessions WHERE project_id = ? ORDER BY date DESC, start_time DESC";
        List<ProjectSession> sessions = jdbcTemplate.query(sql, getRowMapper(), projectId);
        logger.debug("Retrieved {} project sessions for project: {}", sessions.size(), projectId);
        return sessions;
    }
    
    /**
     * Get sessions in date range.
     */
    public static List<ProjectSession> findInDateRange(LocalDate startDate, LocalDate endDate) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        String sql = "SELECT * FROM project_sessions WHERE date >= ? AND date <= ? ORDER BY date DESC, start_time DESC";
        return jdbcTemplate.query(sql, getRowMapper(), startDate, endDate);
    }
    
    /**
     * Get recent project sessions.
     */
    public static List<ProjectSession> findRecent(int days) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        LocalDate cutoffDate = LocalDate.now().minusDays(days);
        String sql = "SELECT * FROM project_sessions WHERE date >= ? ORDER BY date DESC, start_time DESC";
        return jdbcTemplate.query(sql, getRowMapper(), cutoffDate);
    }
    
    /**
     * Delete a project session by ID (static method).
     */
    public static boolean deleteById(String sessionId) {
        if (jdbcTemplate == null || sessionId == null) {
            return false;
        }
        
        String sql = "DELETE FROM project_sessions WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, sessionId);
        boolean deleted = rowsAffected > 0;
        
        if (deleted) {
            logger.info("Project session deleted: {}", sessionId);
        } else {
            logger.warn("Project session not found for deletion: {}", sessionId);
        }
        
        return deleted;
    }
    
    /**
     * Delete all project sessions for a specific project.
     */
    public static void deleteByProjectId(String projectId) {
        if (jdbcTemplate == null) {
            return;
        }
        
        String sql = "DELETE FROM project_sessions WHERE project_id = ?";
        int rowsAffected = jdbcTemplate.update(sql, projectId);
        logger.info("Deleted {} project sessions for project: {}", rowsAffected, projectId);
    }
    
    /**
     * Count project sessions for a specific project.
     */
    public static long countByProjectId(String projectId) {
        if (jdbcTemplate == null) {
            return 0L;
        }
        
        String sql = "SELECT COUNT(*) FROM project_sessions WHERE project_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, projectId);
        return count != null ? count : 0L;
    }
    
    /**
     * RowMapper for converting database rows to ProjectSession objects.
     */
    private static RowMapper<ProjectSession> getRowMapper() {
        return (rs, rowNum) -> {
            return new ProjectSession(
                rs.getString("id"),
                rs.getString("project_id"),
                rs.getObject("date", LocalDate.class),
                rs.getObject("start_time", LocalDateTime.class),
                rs.getObject("end_time", LocalDateTime.class),
                rs.getInt("duration_minutes"),
                rs.getBoolean("completed"),
                rs.getString("session_title"),
                rs.getString("objectives"),
                rs.getString("progress"),
                rs.getString("next_steps"),
                rs.getString("challenges"),
                rs.getString("notes"),
                rs.getInt("points_earned")
            );
        };
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public String getSessionTitle() { return sessionTitle; }
    public void setSessionTitle(String sessionTitle) { this.sessionTitle = sessionTitle; }

    public String getObjectives() { return objectives; }
    public void setObjectives(String objectives) { this.objectives = objectives; }

    public String getProgress() { return progress; }
    public void setProgress(String progress) { this.progress = progress; }

    public String getNextSteps() { return nextSteps; }
    public void setNextSteps(String nextSteps) { this.nextSteps = nextSteps; }

    public String getChallenges() { return challenges; }
    public void setChallenges(String challenges) { this.challenges = challenges; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public int getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(int pointsEarned) { this.pointsEarned = pointsEarned; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getLastUpdateTime() { return lastUpdateTime; }
    public void setLastUpdateTime(LocalDateTime lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }

    @Override
    public String toString() {
        String title = sessionTitle != null && !sessionTitle.trim().isEmpty() ? sessionTitle : "Session";
        return title + " - " + date + " (" + durationMinutes + " min, " + pointsEarned + " pts)";
    }
}