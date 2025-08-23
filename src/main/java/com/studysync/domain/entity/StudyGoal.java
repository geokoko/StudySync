
package com.studysync.domain.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Domain entity representing a study goal in the StudySync personal development system.
 * 
 * <p>StudyGoal enables users to set daily learning objectives and track their achievement over time.
 * This supports habit formation, progress tracking, and reflection on learning outcomes. Goals can
 * be simple daily objectives or more complex learning targets.</p>
 * 
 * <p><strong>Goal Lifecycle:</strong>
 * <ol>
 *   <li><strong>Creation:</strong> Goal is set with a description and current date</li>
 *   <li><strong>Tracking:</strong> User works toward achieving the goal throughout the day</li>
 *   <li><strong>Evaluation:</strong> At day's end, goal is marked achieved or not achieved</li>
 *   <li><strong>Reflection:</strong> If not achieved, user can record reasons for learning</li>
 * </ol></p>
 * 
 * <p><strong>Usage Examples:</strong>
 * <pre>
 * // Create a simple daily goal
 * StudyGoal goal = new StudyGoal("Complete 2 hours of focused study");
 * 
 * // Mark goal as achieved
 * goal.setAchieved(true);
 * 
 * // Record reason if not achieved
 * goal.setAchieved(false);
 * goal.setReasonIfNotAchieved("Had unexpected meeting that ran long");
 * </pre></p>
 * 
 * <p><strong>Business Rules:</strong>
 * <ul>
 *   <li>Each goal is automatically assigned a unique ID and current date</li>
 *   <li>Goals default to not achieved until explicitly marked</li>
 *   <li>Reasons for non-achievement are optional but encouraged for reflection</li>
 *   <li>Goals cannot be modified once created (description is immutable)</li>
 * </ul></p>
 * 
 * <p><strong>Integration:</strong> StudyGoals integrate with the daily reflection system
 * and analytics to provide insights into learning patterns and goal achievement rates.</p>
 * 
 * @author StudySync Development Team
 * @version 0.1.0-BETA
 * @since 0.1.0
 * @see DailyReflection
 */
public class StudyGoal {
    private static final Logger logger = LoggerFactory.getLogger(StudyGoal.class);
    private static JdbcTemplate jdbcTemplate;
    
    public static void setJdbcTemplate(JdbcTemplate template) {
        jdbcTemplate = template;
    }
    
    /** Unique identifier for this study goal. */
    private String id;
    
    /** The date this goal was created for (target achievement date). */
    private LocalDate date;
    
    /** Description of what the user wants to achieve. */
    private String description;
    
    /** Whether this goal has been achieved. */
    private boolean achieved;
    
    /** Optional explanation of why the goal was not achieved (for reflection). */
    private String reasonIfNotAchieved;
    
    /** Number of days this goal has been delayed (0 for goals on original date). */
    private int daysDelayed;
    
    /** Whether this goal is a transferred goal from a previous day. */
    private boolean isDelayed;
    
    /** Points deducted due to delays (accumulates over time). */
    private int pointsDeducted;
    
    /** Optional ID of the task this goal is linked to. */
    private String taskId;

    /**
     * Default constructor creating a study goal with auto-generated ID and current date.
     * 
     * <p>The goal is initialized as not achieved. The description must be set separately.</p>
     */
    public StudyGoal() {
        this.id = java.util.UUID.randomUUID().toString();
        this.date = LocalDate.now();
        this.achieved = false;
        this.daysDelayed = 0;
        this.isDelayed = false;
        this.pointsDeducted = 0;
    }

    /**
     * Creates a study goal with the specified description.
     * 
     * <p>The goal is automatically assigned a unique ID, set to the current date,
     * and initialized as not achieved.</p>
     * 
     * @param description what the user wants to achieve
     */
    public StudyGoal(String description) {
        this();
        this.description = description;
    }
    
    /**
     * Creates a study goal with the specified description and optional task link.
     * 
     * @param description what the user wants to achieve
     * @param taskId optional ID of the task this goal is linked to
     */
    public StudyGoal(String description, String taskId) {
        this(description);
        this.taskId = taskId;
    }

    /**
     * Full constructor for creating StudyGoal from JSON or with all parameters.
     * 
     * <p>This constructor provides null-safety by generating defaults for ID and date
     * if not provided. Used primarily for JSON deserialization and testing.</p>
     * 
     * @param id unique identifier (auto-generated if null)
     * @param date goal date (current date if null)
     * @param description goal description
     * @param achieved whether the goal has been achieved
     * @param reasonIfNotAchieved explanation if not achieved
     */
    @JsonCreator
    public StudyGoal(@JsonProperty("id") String id,
                    @JsonProperty("date") LocalDate date,
                    @JsonProperty("description") String description,
                    @JsonProperty("achieved") boolean achieved,
                    @JsonProperty("reasonIfNotAchieved") String reasonIfNotAchieved) {
        this.id = id != null ? id : java.util.UUID.randomUUID().toString();
        this.date = date != null ? date : LocalDate.now();
        this.description = description;
        this.achieved = achieved;
        this.reasonIfNotAchieved = reasonIfNotAchieved;
        this.daysDelayed = 0;
        this.isDelayed = false;
        this.pointsDeducted = 0;
    }
    
    /**
     * Full constructor for creating StudyGoal with all delay tracking fields.
     * Used internally for database operations.
     */
    public StudyGoal(String id, LocalDate date, String description, boolean achieved, 
                    String reasonIfNotAchieved, int daysDelayed, 
                    boolean isDelayed, int pointsDeducted, String taskId) {
        this.id = id != null ? id : java.util.UUID.randomUUID().toString();
        this.date = date != null ? date : LocalDate.now();
        this.description = description;
        this.achieved = achieved;
        this.reasonIfNotAchieved = reasonIfNotAchieved;
        this.daysDelayed = daysDelayed;
        this.isDelayed = isDelayed;
        this.pointsDeducted = pointsDeducted;
        this.taskId = taskId;
    }

    /**
     * Gets the unique identifier of this study goal.
     * 
     * @return the goal ID
     */
    public String getId() { return id; }
    
    /**
     * Sets the unique identifier for this study goal.
     * 
     * @param id the goal ID
     */
    public void setId(String id) { this.id = id; }

    /**
     * Gets the date this goal was created for.
     * 
     * @return the goal date
     */
    public LocalDate getDate() { return date; }
    
    /**
     * Sets the date for this goal.
     * 
     * @param date the goal date
     */
    public void setDate(LocalDate date) { this.date = date; }

    /**
     * Gets the description of what should be achieved.
     * 
     * @return the goal description
     */
    public String getDescription() { return description; }
    
    /**
     * Sets the description of what should be achieved.
     * 
     * @param description the goal description
     */
    public void setDescription(String description) { this.description = description; }

    /**
     * Determines if this goal has been achieved.
     * 
     * @return true if the goal has been achieved, false otherwise
     */
    public boolean isAchieved() { return achieved; }
    
    /**
     * Sets whether this goal has been achieved.
     * 
     * @param achieved true if the goal has been achieved
     */
    public void setAchieved(boolean achieved) { this.achieved = achieved; }

    /**
     * Gets the reason why this goal was not achieved.
     * 
     * <p>This is used for reflection and learning from unachieved goals.
     * Only meaningful when {@code achieved} is false.</p>
     * 
     * @return the reason for non-achievement, or null if not provided
     */
    public String getReasonIfNotAchieved() { return reasonIfNotAchieved; }
    
    /**
     * Sets the reason why this goal was not achieved.
     * 
     * <p>This supports reflection and helps identify patterns in goal achievement.
     * Should be used when marking a goal as not achieved.</p>
     * 
     * @param reasonIfNotAchieved explanation of why the goal wasn't achieved
     */
    public void setReasonIfNotAchieved(String reasonIfNotAchieved) { 
        this.reasonIfNotAchieved = reasonIfNotAchieved; 
    }


    /**
     * Gets the number of days this goal has been delayed.
     * 
     * @return days delayed (0 for goals on original date)
     */
    public int getDaysDelayed() { return daysDelayed; }
    
    /**
     * Sets the number of days this goal has been delayed.
     * 
     * @param daysDelayed days delayed
     */
    public void setDaysDelayed(int daysDelayed) { this.daysDelayed = daysDelayed; }

    /**
     * Checks if this goal is delayed (transferred from a previous day).
     * 
     * @return true if the goal is delayed
     */
    public boolean isDelayed() { return isDelayed; }
    
    /**
     * Sets whether this goal is delayed.
     * 
     * @param delayed true if the goal is delayed
     */
    public void setDelayed(boolean delayed) { this.isDelayed = delayed; }

    /**
     * Gets the total points deducted due to delays.
     * 
     * @return points deducted
     */
    public int getPointsDeducted() { return pointsDeducted; }
    
    /**
     * Sets the points deducted due to delays.
     * 
     * @param pointsDeducted points deducted
     */
    public void setPointsDeducted(int pointsDeducted) { this.pointsDeducted = pointsDeducted; }
    
    /**
     * Gets the ID of the task this goal is linked to.
     * 
     * @return task ID, or null if not linked to any task
     */
    public String getTaskId() { return taskId; }
    
    /**
     * Sets the ID of the task this goal is linked to.
     * 
     * @param taskId task ID, or null to unlink from task
     */
    public void setTaskId(String taskId) { this.taskId = taskId; }

    
    /**
     * Calculate the delay penalty based on days delayed.
     * Formula: 5 points for first delay, then 2 points per additional day.
     * 
     * @return penalty points for the current delay
     */
    public int calculateDelayPenalty() {
        if (daysDelayed == 0) return 0;
        if (daysDelayed == 1) return 5; // First delay
        return 5 + (daysDelayed - 1) * 2; // Additional days
    }
    
    /**
     * Get the color intensity for UI display based on delay.
     * Returns a value between 0.0 (no delay/green) and 1.0 (max delay/red).
     * 
     * @return color intensity for delayed goal visualization
     */
    public double getDelayColorIntensity() {
        if (!isDelayed) return 0.0;
        // Orange starts at day 1, gradually moves to red
        return Math.min(1.0, daysDelayed / 7.0); // Max intensity at 7 days
    }

    /**
     * Returns a string representation of this study goal.
     * 
     * <p>The format shows the goal description followed by a checkmark (✓) if achieved
     * or an X (✗) if not achieved. This provides a quick visual status indicator.</p>
     * 
     * @return a string representation showing goal description and achievement status
     */
    @Override
    public String toString() {
        return description + " - " + (achieved ? "✓" : "✗");
    }
    
    // ==============================================================
    // DATABASE OPERATIONS (Active Record Pattern)
    // ==============================================================
    
    /**
     * Save this study goal to the database (insert or update).
     */
    public StudyGoal save() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized. Make sure Spring context is loaded.");
        }
        
        String sql = """
            MERGE INTO study_goals (id, date, description, achieved, reason_if_not_achieved, 
                                   days_delayed, is_delayed, points_deducted, task_id, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;
        
        jdbcTemplate.update(sql,
            this.id, this.date, this.description, this.achieved, this.reasonIfNotAchieved,
            this.daysDelayed, this.isDelayed, this.pointsDeducted, this.taskId
        );
        
        logger.debug("StudyGoal saved: {} - {}", this.id, this.description);
        return this;
    }
    
    /**
     * Delete this study goal from the database.
     */
    public boolean delete() {
        if (jdbcTemplate == null || this.id == null) {
            return false;
        }
        
        String sql = "DELETE FROM study_goals WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, this.id);
        boolean deleted = rowsAffected > 0;
        
        if (deleted) {
            logger.info("StudyGoal deleted: {} - {}", this.id, this.description);
        } else {
            logger.warn("StudyGoal not found for deletion: {}", this.id);
        }
        
        return deleted;
    }
    
    // ==============================================================
    // STATIC QUERY METHODS
    // ==============================================================
    
    /**
     * Get all study goals ordered by date (most recent first).
     */
    public static List<StudyGoal> findAll() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        String sql = "SELECT * FROM study_goals ORDER BY date DESC, created_at DESC";
        List<StudyGoal> goals = jdbcTemplate.query(sql, getRowMapper());
        logger.debug("Retrieved {} study goals", goals.size());
        return goals;
    }
    
    /**
     * Find a study goal by its ID.
     */
    public static Optional<StudyGoal> findById(String goalId) {
        if (jdbcTemplate == null || goalId == null) {
            return Optional.empty();
        }
        
        String sql = "SELECT * FROM study_goals WHERE id = ?";
        try {
            StudyGoal goal = jdbcTemplate.queryForObject(sql, getRowMapper(), goalId);
            logger.debug("StudyGoal found: {}", goalId);
            return Optional.ofNullable(goal);
        } catch (Exception e) {
            logger.debug("StudyGoal not found: {}", goalId);
            return Optional.empty();
        }
    }
    
    /**
     * Get study goals for a specific date.
     */
    public static List<StudyGoal> findByDate(LocalDate date) {
        if (jdbcTemplate == null || date == null) {
            return List.of();
        }
        
        String sql = "SELECT * FROM study_goals WHERE date = ? ORDER BY created_at";
        List<StudyGoal> goals = jdbcTemplate.query(sql, getRowMapper(), date);
        logger.debug("Retrieved {} study goals for date: {}", goals.size(), date);
        return goals;
    }
    
    /**
     * Get study goals for a specific date including delayed goals that should appear on this date.
     * This method returns:
     * <ul>
     *   <li>Goals originally created for the specified date</li>
     *   <li>Unachieved goals from previous dates that are now delayed</li>
     * </ul>
     * 
     * @param date the date to retrieve goals for
     * @return list of study goals including delayed ones, ordered by delay status and creation time
     */
    public static List<StudyGoal> findByDateIncludingDelayed(LocalDate date) {
        if (jdbcTemplate == null || date == null) {
            return List.of();
        }
        
        String sql = """
            SELECT * FROM study_goals 
            WHERE date = ? 
               OR (is_delayed = TRUE AND achieved = FALSE AND date < ?)
            ORDER BY is_delayed ASC, days_delayed DESC, created_at ASC
            """;
        
        List<StudyGoal> goals = jdbcTemplate.query(sql, getRowMapper(), date, date);
        logger.debug("Retrieved {} study goals (including delayed) for date: {}", goals.size(), date);
        return goals;
    }
    
    /**
     * Get achieved study goals.
     */
    public static List<StudyGoal> findAchieved() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        String sql = "SELECT * FROM study_goals WHERE achieved = TRUE ORDER BY date DESC";
        List<StudyGoal> goals = jdbcTemplate.query(sql, getRowMapper());
        logger.debug("Retrieved {} achieved study goals", goals.size());
        return goals;
    }
    
    /**
     * Delete a study goal by ID (static method).
     */
    public static boolean deleteById(String goalId) {
        if (jdbcTemplate == null || goalId == null) {
            return false;
        }
        
        String sql = "DELETE FROM study_goals WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, goalId);
        boolean deleted = rowsAffected > 0;
        
        if (deleted) {
            logger.info("StudyGoal deleted: {}", goalId);
        } else {
            logger.warn("StudyGoal not found for deletion: {}", goalId);
        }
        
        return deleted;
    }
    
    /**
     * Get count of study goals by achievement status.
     */
    public static long countByAchievement(boolean achieved) {
        if (jdbcTemplate == null) {
            return 0L;
        }
        
        String sql = "SELECT COUNT(*) FROM study_goals WHERE achieved = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, achieved);
        return count != null ? count : 0L;
    }
    
    /**
     * Find unachieved goals for a specific date (candidates for transfer).
     */
    public static List<StudyGoal> findUnachievedByDate(LocalDate date) {
        if (jdbcTemplate == null || date == null) {
            return List.of();
        }
        
        String sql = "SELECT * FROM study_goals WHERE date = ? AND achieved = FALSE ORDER BY created_at";
        List<StudyGoal> goals = jdbcTemplate.query(sql, getRowMapper(), date);
        logger.debug("Retrieved {} unachieved study goals for date: {}", goals.size(), date);
        return goals;
    }
    
    /**
     * Find delayed goals (transferred from previous days).
     */
    public static List<StudyGoal> findDelayed() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        String sql = "SELECT * FROM study_goals WHERE is_delayed = TRUE ORDER BY days_delayed DESC, date DESC";
        List<StudyGoal> goals = jdbcTemplate.query(sql, getRowMapper());
        logger.debug("Retrieved {} delayed study goals", goals.size());
        return goals;
    }
    
    /**
     * Find delayed goals for a specific date.
     */
    public static List<StudyGoal> findDelayedByDate(LocalDate date) {
        if (jdbcTemplate == null || date == null) {
            return List.of();
        }
        
        String sql = "SELECT * FROM study_goals WHERE date = ? AND is_delayed = TRUE ORDER BY days_delayed DESC";
        List<StudyGoal> goals = jdbcTemplate.query(sql, getRowMapper(), date);
        logger.debug("Retrieved {} delayed study goals for date: {}", goals.size(), date);
        return goals;
    }
    
    /**
     * RowMapper for converting database rows to StudyGoal objects.
     */
    private static RowMapper<StudyGoal> getRowMapper() {
        return (rs, rowNum) -> {
            String id = rs.getString("id");
            LocalDate date = rs.getObject("date", LocalDate.class);
            String description = rs.getString("description");
            boolean achieved = rs.getBoolean("achieved");
            String reasonIfNotAchieved = rs.getString("reason_if_not_achieved");
            int daysDelayed = rs.getInt("days_delayed");
            boolean isDelayed = rs.getBoolean("is_delayed");
            int pointsDeducted = rs.getInt("points_deducted");
            String taskId = rs.getString("task_id");
            
            return new StudyGoal(id, date, description, achieved, reasonIfNotAchieved, 
                               daysDelayed, isDelayed, pointsDeducted, taskId);
        };
    }
}
