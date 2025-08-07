
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

public class DailyReflection {
    private static final Logger logger = LoggerFactory.getLogger(DailyReflection.class);
    private static JdbcTemplate jdbcTemplate;
    
    public static void setJdbcTemplate(JdbcTemplate template) {
        jdbcTemplate = template;
    }
    private String id;
    private LocalDate date;
    private int overallFocusLevel; // 1-5 scale
    private String whatToChangeTomorrow;
    private int completedSessions;
    private int totalGoalsAchieved;
    private String notes;
    private String reflectionText;
    private boolean deserveReward;

    public DailyReflection() {
        this.id = java.util.UUID.randomUUID().toString();
        this.date = LocalDate.now();
        this.overallFocusLevel = 3;
        this.deserveReward = false;
    }

    @JsonCreator
    public DailyReflection(@JsonProperty("id") String id,
                          @JsonProperty("date") LocalDate date,
                          @JsonProperty("overallFocusLevel") int overallFocusLevel,
                          @JsonProperty("whatToChangeTomorrow") String whatToChangeTomorrow,
                          @JsonProperty("completedSessions") int completedSessions,
                          @JsonProperty("totalGoalsAchieved") int totalGoalsAchieved,
                          @JsonProperty("notes") String notes,
                          @JsonProperty("reflectionText") String reflectionText,
                          @JsonProperty("deserveReward") boolean deserveReward) {
        this.id = id != null ? id : java.util.UUID.randomUUID().toString();
        this.date = date != null ? date : LocalDate.now();
        this.overallFocusLevel = overallFocusLevel;
        this.whatToChangeTomorrow = whatToChangeTomorrow;
        this.completedSessions = completedSessions;
        this.totalGoalsAchieved = totalGoalsAchieved;
        this.notes = notes;
        this.reflectionText = reflectionText;
        this.deserveReward = deserveReward;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public int getOverallFocusLevel() { return overallFocusLevel; }
    public void setOverallFocusLevel(int overallFocusLevel) { this.overallFocusLevel = overallFocusLevel; }

    public String getWhatToChangeTomorrow() { return whatToChangeTomorrow; }
    public void setWhatToChangeTomorrow(String whatToChangeTomorrow) { this.whatToChangeTomorrow = whatToChangeTomorrow; }

    public int getCompletedSessions() { return completedSessions; }
    public void setCompletedSessions(int completedSessions) { this.completedSessions = completedSessions; }

    public int getTotalGoalsAchieved() { return totalGoalsAchieved; }
    public void setTotalGoalsAchieved(int totalGoalsAchieved) { this.totalGoalsAchieved = totalGoalsAchieved; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isDeserveReward() { return deserveReward; }
    public void setDeserveReward(boolean deserveReward) { this.deserveReward = deserveReward; }

    public String getReflectionText() { return reflectionText; }
    public void setReflectionText(String reflectionText) { this.reflectionText = reflectionText; }
    
    /**
     * Calculate penalty points for low overall focus level.
     * Used to discourage consistently low focus ratings.
     * 
     * @return penalty points (negative value)
     */
    public int calculateFocusPenalty() {
        if (overallFocusLevel <= 2) {
            // Significant daily penalty for poor focus
            return -30 * (3 - overallFocusLevel); // -30 for level 2, -60 for level 1
        }
        return 0; // No penalty for focus level 3 or above
    }
    
    /**
     * Gets the total daily penalty points for this reflection.
     * Currently only considers focus penalty, but can be extended.
     * 
     * @return total penalty points
     */
    public int getTotalDailyPenalty() {
        return calculateFocusPenalty();
    }

    @Override
    public String toString() {
        return "Reflection for " + date + " - Focus: " + overallFocusLevel + "/5";
    }
    
    // ==============================================================
    // DATABASE OPERATIONS (Active Record Pattern)
    // ==============================================================
    
    /**
     * Save this daily reflection to the database (insert or update).
     */
    public DailyReflection save() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized. Make sure Spring context is loaded.");
        }
        
        String sql = """
            MERGE INTO daily_reflections (id, date, overall_focus_level, what_to_change_tomorrow, 
                                         completed_sessions, total_goals_achieved, notes, reflection_text, deserve_reward, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;
        
        jdbcTemplate.update(sql,
            this.id, this.date, this.overallFocusLevel, this.whatToChangeTomorrow,
            this.completedSessions, this.totalGoalsAchieved, this.notes, this.reflectionText, this.deserveReward
        );
        
        logger.debug("DailyReflection saved: {} for date: {}", this.id, this.date);
        return this;
    }
    
    /**
     * Delete this daily reflection from the database.
     */
    public boolean delete() {
        if (jdbcTemplate == null || this.id == null) {
            return false;
        }
        
        String sql = "DELETE FROM daily_reflections WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, this.id);
        boolean deleted = rowsAffected > 0;
        
        if (deleted) {
            logger.info("DailyReflection deleted: {} for date: {}", this.id, this.date);
        } else {
            logger.warn("DailyReflection not found for deletion: {}", this.id);
        }
        
        return deleted;
    }
    
    // ==============================================================
    // STATIC QUERY METHODS
    // ==============================================================
    
    /**
     * Get all daily reflections ordered by date (most recent first).
     */
    public static List<DailyReflection> findAll() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        String sql = "SELECT * FROM daily_reflections ORDER BY date DESC";
        List<DailyReflection> reflections = jdbcTemplate.query(sql, getRowMapper());
        logger.debug("Retrieved {} daily reflections", reflections.size());
        return reflections;
    }
    
    /**
     * Find a daily reflection by its ID.
     */
    public static Optional<DailyReflection> findById(String reflectionId) {
        if (jdbcTemplate == null || reflectionId == null) {
            return Optional.empty();
        }
        
        String sql = "SELECT * FROM daily_reflections WHERE id = ?";
        try {
            DailyReflection reflection = jdbcTemplate.queryForObject(sql, getRowMapper(), reflectionId);
            logger.debug("DailyReflection found: {}", reflectionId);
            return Optional.ofNullable(reflection);
        } catch (Exception e) {
            logger.debug("DailyReflection not found: {}", reflectionId);
            return Optional.empty();
        }
    }
    
    /**
     * Get daily reflection for a specific date.
     */
    public static Optional<DailyReflection> findByDate(LocalDate date) {
        if (jdbcTemplate == null || date == null) {
            return Optional.empty();
        }
        
        String sql = "SELECT * FROM daily_reflections WHERE date = ?";
        try {
            DailyReflection reflection = jdbcTemplate.queryForObject(sql, getRowMapper(), date);
            logger.debug("DailyReflection found for date: {}", date);
            return Optional.ofNullable(reflection);
        } catch (Exception e) {
            logger.debug("DailyReflection not found for date: {}", date);
            return Optional.empty();
        }
    }
    
    /**
     * Get daily reflections in date range.
     */
    public static List<DailyReflection> findInDateRange(LocalDate startDate, LocalDate endDate) {
        if (jdbcTemplate == null) {
            return List.of();
        }
        
        String sql = "SELECT * FROM daily_reflections WHERE date >= ? AND date <= ? ORDER BY date DESC";
        List<DailyReflection> reflections = jdbcTemplate.query(sql, getRowMapper(), startDate, endDate);
        logger.debug("Retrieved {} daily reflections in date range: {} to {}", reflections.size(), startDate, endDate);
        return reflections;
    }
    
    /**
     * Get recent daily reflections.
     */
    public static List<DailyReflection> findRecent(int days) {
        if (jdbcTemplate == null) {
            return List.of();
        }
        
        LocalDate cutoffDate = LocalDate.now().minusDays(days);
        String sql = "SELECT * FROM daily_reflections WHERE date >= ? ORDER BY date DESC";
        List<DailyReflection> reflections = jdbcTemplate.query(sql, getRowMapper(), cutoffDate);
        logger.debug("Retrieved {} daily reflections in last {} days", reflections.size(), days);
        return reflections;
    }
    
    /**
     * Delete a daily reflection by ID (static method).
     */
    public static boolean deleteById(String reflectionId) {
        if (jdbcTemplate == null || reflectionId == null) {
            return false;
        }
        
        String sql = "DELETE FROM daily_reflections WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, reflectionId);
        boolean deleted = rowsAffected > 0;
        
        if (deleted) {
            logger.info("DailyReflection deleted: {}", reflectionId);
        } else {
            logger.warn("DailyReflection not found for deletion: {}", reflectionId);
        }
        
        return deleted;
    }
    
    /**
     * Delete daily reflection by date.
     */
    public static boolean deleteByDate(LocalDate date) {
        if (jdbcTemplate == null || date == null) {
            return false;
        }
        
        String sql = "DELETE FROM daily_reflections WHERE date = ?";
        int rowsAffected = jdbcTemplate.update(sql, date);
        boolean deleted = rowsAffected > 0;
        
        if (deleted) {
            logger.info("DailyReflection deleted for date: {}", date);
        } else {
            logger.warn("DailyReflection not found for date: {}", date);
        }
        
        return deleted;
    }
    
    /**
     * Get count of reflections where deserve_reward is true.
     */
    public static long countDeserveReward() {
        if (jdbcTemplate == null) {
            return 0L;
        }
        
        String sql = "SELECT COUNT(*) FROM daily_reflections WHERE deserve_reward = TRUE";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0L;
    }
    
    /**
     * Get average focus level across all reflections.
     */
    public static double getAverageFocusLevel() {
        if (jdbcTemplate == null) {
            return 0.0;
        }
        
        String sql = "SELECT AVG(overall_focus_level) FROM daily_reflections";
        Double average = jdbcTemplate.queryForObject(sql, Double.class);
        return average != null ? average : 0.0;
    }
    
    /**
     * RowMapper for converting database rows to DailyReflection objects.
     */
    private static RowMapper<DailyReflection> getRowMapper() {
        return (rs, rowNum) -> {
            String id = rs.getString("id");
            LocalDate date = rs.getObject("date", LocalDate.class);
            int overallFocusLevel = rs.getInt("overall_focus_level");
            String whatToChangeTomorrow = rs.getString("what_to_change_tomorrow");
            int completedSessions = rs.getInt("completed_sessions");
            int totalGoalsAchieved = rs.getInt("total_goals_achieved");
            String notes = rs.getString("notes");
            String reflectionText = rs.getString("reflection_text");
            boolean deserveReward = rs.getBoolean("deserve_reward");
            
            return new DailyReflection(id, date, overallFocusLevel, whatToChangeTomorrow,
                                     completedSessions, totalGoalsAchieved, notes, reflectionText, deserveReward);
        };
    }
}
