
package com.studysync.domain.entity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain entity representing a study session in the StudySync system.
 * Uses Active Record pattern - handles its own database operations.
 */
public class StudySession {
    
    private static final Logger logger = LoggerFactory.getLogger(StudySession.class);
    private static JdbcTemplate jdbcTemplate;
    
    private String id;
    private LocalDate date;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int durationMinutes;
    private boolean completed;
    private int focusLevel;
    private int confidenceLevel;
    private String notes;
    private String subject;
    private String topic;
    private String location;
    private String outcomeExpected;
    private String actualWork;
    private String whatHelped;
    private String whatDistracted;
    private String improvementNote;
    private int pointsEarned;
    private String sessionText;
    
    // Real-time tracking fields
    private boolean isActive;
    private LocalDateTime lastUpdateTime;
    private int currentElapsedMinutes;
    
    public static void setJdbcTemplate(JdbcTemplate template) {
        jdbcTemplate = template;
    }

    // Default constructor
    public StudySession() {
        this.id = UUID.randomUUID().toString();
        this.date = LocalDate.now();
        this.completed = false;
        this.pointsEarned = 0;
        this.focusLevel = 3;
        this.confidenceLevel = 3;
        this.isActive = false;
        this.currentElapsedMinutes = 0;
        this.lastUpdateTime = LocalDateTime.now();
    }

    // Full constructor
    public StudySession(String id, LocalDate date, LocalDateTime startTime, LocalDateTime endTime,
                       int durationMinutes, boolean completed, int focusLevel, int confidenceLevel,
                       String notes, String subject, String topic, String location,
                       String outcomeExpected, String actualWork, String whatHelped,
                       String whatDistracted, String improvementNote, int pointsEarned, String sessionText) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.date = date != null ? date : LocalDate.now();
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMinutes = durationMinutes;
        this.completed = completed;
        this.focusLevel = focusLevel;
        this.confidenceLevel = confidenceLevel;
        this.notes = notes;
        this.subject = subject;
        this.topic = topic;
        this.location = location;
        this.outcomeExpected = outcomeExpected;
        this.actualWork = actualWork;
        this.whatHelped = whatHelped;
        this.whatDistracted = whatDistracted;
        this.improvementNote = improvementNote;
        this.pointsEarned = pointsEarned;
        this.sessionText = sessionText;
        this.isActive = false;
        this.currentElapsedMinutes = 0;
        this.lastUpdateTime = LocalDateTime.now();

        // Validation
        Objects.requireNonNull(this.id, "id cannot be null");
        Objects.requireNonNull(this.date, "date cannot be null");
        if (this.focusLevel < 1 || this.focusLevel > 5) {
            throw new IllegalArgumentException("Focus level must be between 1 and 5");
        }
        if (this.confidenceLevel < 1 || this.confidenceLevel > 5) {
            throw new IllegalArgumentException("Confidence level must be between 1 and 5");
        }
    }

    // ==============================================================
    // DATABASE OPERATIONS (Active Record Pattern)
    // ==============================================================
    
    /**
     * Save this study session to the database (insert or update).
     */
    public void save() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized. Make sure Spring context is loaded.");
        }
        
        String sql = "MERGE INTO study_sessions (id, date, start_time, end_time, duration_minutes, completed, focus_level, confidence_level, notes, subject, topic, location, outcome_expected, actual_work, what_helped, what_distracted, improvement_note, points_earned, session_text) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
            this.id, this.date, this.startTime, this.endTime,
            this.durationMinutes, this.completed, this.focusLevel,
            this.confidenceLevel, this.notes, this.subject,
            this.topic, this.location, this.outcomeExpected,
            this.actualWork, this.whatHelped, this.whatDistracted,
            this.improvementNote, this.pointsEarned, this.sessionText
        );
        
        logger.debug("Study session saved: {}", this.id);
    }
    
    /**
     * Delete this study session from the database.
     */
    public boolean delete() {
        if (jdbcTemplate == null || this.id == null) {
            return false;
        }
        
        String sql = "DELETE FROM study_sessions WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, this.id);
        boolean deleted = rowsAffected > 0;
        
        if (deleted) {
            logger.info("Study session deleted: {}", this.id);
        } else {
            logger.warn("Study session not found for deletion: {}", this.id);
        }
        
        return deleted;
    }
    
    // ==============================================================
    // STATIC QUERY METHODS
    // ==============================================================
    
    /**
     * Get all study sessions ordered by date and time (most recent first).
     */
    public static List<StudySession> findAll() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        String sql = "SELECT * FROM study_sessions ORDER BY date DESC, start_time DESC";
        List<StudySession> sessions = jdbcTemplate.query(sql, getRowMapper());
        logger.debug("Retrieved {} study sessions", sessions.size());
        return sessions;
    }
    
    /**
     * Get study sessions for a specific date.
     */
    public static List<StudySession> findByDate(LocalDate date) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        String sql = "SELECT * FROM study_sessions WHERE date = ? ORDER BY start_time DESC";
        List<StudySession> sessions = jdbcTemplate.query(sql, getRowMapper(), date);
        logger.debug("Retrieved {} study sessions for date: {}", sessions.size(), date);
        return sessions;
    }
    
    /**
     * Find a study session by its ID.
     */
    public static Optional<StudySession> findById(String id) {
        if (jdbcTemplate == null || id == null) {
            return Optional.empty();
        }
        
        String sql = "SELECT * FROM study_sessions WHERE id = ?";
        try {
            StudySession session = jdbcTemplate.queryForObject(sql, getRowMapper(), id);
            return Optional.ofNullable(session);
        } catch (Exception e) {
            logger.debug("Study session not found: {}", id);
            return Optional.empty();
        }
    }
    
    /**
     * Get sessions in date range.
     */
    public static List<StudySession> findInDateRange(LocalDate startDate, LocalDate endDate) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        String sql = "SELECT * FROM study_sessions WHERE date BETWEEN ? AND ? ORDER BY date DESC, start_time DESC";
        return jdbcTemplate.query(sql, getRowMapper(), startDate, endDate);
    }
    
    /**
     * Get recent study sessions.
     */
    public static List<StudySession> findRecent(int days) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        LocalDate cutoffDate = LocalDate.now().minusDays(days);
        String sql = "SELECT * FROM study_sessions WHERE date >= ? ORDER BY date DESC, start_time DESC";
        return jdbcTemplate.query(sql, getRowMapper(), cutoffDate);
    }
    
    /**
     * Delete a study session by ID (static method).
     */
    public static boolean deleteById(String sessionId) {
        if (jdbcTemplate == null || sessionId == null) {
            return false;
        }
        
        String sql = "DELETE FROM study_sessions WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, sessionId);
        boolean deleted = rowsAffected > 0;
        
        if (deleted) {
            logger.info("Study session deleted: {}", sessionId);
        } else {
            logger.warn("Study session not found for deletion: {}", sessionId);
        }
        
        return deleted;
    }
    
    /**
     * RowMapper for converting database rows to StudySession objects.
     */
    private static RowMapper<StudySession> getRowMapper() {
        return (rs, rowNum) -> {
            StudySession session = new StudySession(
                rs.getString("id"),
                rs.getObject("date", LocalDate.class),
                rs.getObject("start_time", LocalDateTime.class),
                rs.getObject("end_time", LocalDateTime.class),
                rs.getInt("duration_minutes"),
                rs.getBoolean("completed"),
                rs.getInt("focus_level"),
                rs.getInt("confidence_level"),
                rs.getString("notes"),
                rs.getString("subject"),
                rs.getString("topic"),
                rs.getString("location"),
                rs.getString("outcome_expected"),
                rs.getString("actual_work"),
                rs.getString("what_helped"),
                rs.getString("what_distracted"),
                rs.getString("improvement_note"),
                rs.getInt("points_earned"),
                rs.getString("session_text")
            );
            return session;
        };
    }

    // Business logic methods
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
        calculateAndSetPoints();
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

    public static int calculatePoints(int durationMinutes, int focusLevel, int confidenceLevel, boolean completed) {
        int basePoints = Math.min(durationMinutes / 10, 60);
        
        // Focus scoring with penalties for low focus (1-2 stars)
        int focusScore;
        if (focusLevel <= 2) {
            // Penalty for low focus: significant point reduction
            focusScore = -20 * (3 - focusLevel); // -20 for level 2, -40 for level 1
        } else {
            // Bonus for good focus levels (3-5)
            focusScore = (focusLevel - 2) * 15; // +15 for level 3, +30 for level 4, +45 for level 5
        }
        
        int confidenceBonus = confidenceLevel * 5;
        int completionBonus = completed ? 20 : 0;
        
        // Ensure minimum score is 0 (can't go negative)
        int totalPoints = basePoints + focusScore + confidenceBonus + completionBonus;
        return Math.max(0, totalPoints);
    }

    public void calculateAndSetPoints() {
        this.pointsEarned = calculatePoints(this.durationMinutes, this.focusLevel, this.confidenceLevel, this.completed);
    }

    public void updateFocusLevel(int newFocusLevel) {
        if (newFocusLevel >= 1 && newFocusLevel <= 5) {
            this.focusLevel = newFocusLevel;
            this.lastUpdateTime = LocalDateTime.now();
            if (this.completed) {
                calculateAndSetPoints();
            }
        }
    }

    public void updateConfidenceLevel(int newConfidenceLevel) {
        if (newConfidenceLevel >= 1 && newConfidenceLevel <= 5) {
            this.confidenceLevel = newConfidenceLevel;
            this.lastUpdateTime = LocalDateTime.now();
            if (this.completed) {
                calculateAndSetPoints();
            }
        }
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { 
        this.startTime = startTime; 
        this.lastUpdateTime = LocalDateTime.now();
    }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { 
        this.endTime = endTime; 
        this.lastUpdateTime = LocalDateTime.now();
    }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { 
        this.durationMinutes = durationMinutes; 
        this.lastUpdateTime = LocalDateTime.now();
    }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { 
        this.completed = completed; 
        this.lastUpdateTime = LocalDateTime.now();
        if (completed) {
            calculateAndSetPoints();
        }
    }

    public int getFocusLevel() { return focusLevel; }
    public void setFocusLevel(int focusLevel) { 
        if (focusLevel >= 1 && focusLevel <= 5) {
            this.focusLevel = focusLevel; 
            this.lastUpdateTime = LocalDateTime.now();
        }
    }

    public int getConfidenceLevel() { return confidenceLevel; }
    public void setConfidenceLevel(int confidenceLevel) { 
        if (confidenceLevel >= 1 && confidenceLevel <= 5) {
            this.confidenceLevel = confidenceLevel; 
            this.lastUpdateTime = LocalDateTime.now();
        }
    }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { 
        this.notes = notes; 
        this.lastUpdateTime = LocalDateTime.now();
    }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { 
        this.subject = subject; 
        this.lastUpdateTime = LocalDateTime.now();
    }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { 
        this.topic = topic; 
        this.lastUpdateTime = LocalDateTime.now();
    }

    public String getLocation() { return location; }
    public void setLocation(String location) { 
        this.location = location; 
        this.lastUpdateTime = LocalDateTime.now();
    }

    public String getOutcomeExpected() { return outcomeExpected; }
    public void setOutcomeExpected(String outcomeExpected) { 
        this.outcomeExpected = outcomeExpected; 
        this.lastUpdateTime = LocalDateTime.now();
    }

    public String getActualWork() { return actualWork; }
    public void setActualWork(String actualWork) { 
        this.actualWork = actualWork; 
        this.lastUpdateTime = LocalDateTime.now();
    }

    public String getWhatHelped() { return whatHelped; }
    public void setWhatHelped(String whatHelped) { 
        this.whatHelped = whatHelped; 
        this.lastUpdateTime = LocalDateTime.now();
    }

    public String getWhatDistracted() { return whatDistracted; }
    public void setWhatDistracted(String whatDistracted) { 
        this.whatDistracted = whatDistracted; 
        this.lastUpdateTime = LocalDateTime.now();
    }

    public String getImprovementNote() { return improvementNote; }
    public void setImprovementNote(String improvementNote) { 
        this.improvementNote = improvementNote; 
        this.lastUpdateTime = LocalDateTime.now();
    }

    public int getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(int pointsEarned) { 
        this.pointsEarned = pointsEarned; 
        this.lastUpdateTime = LocalDateTime.now();
    }

    public String getSessionText() { return sessionText; }
    public void setSessionText(String sessionText) { 
        this.sessionText = sessionText; 
        this.lastUpdateTime = LocalDateTime.now();
    }

    public boolean isActive() { return isActive; }
    public void setActive(boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getLastUpdateTime() { return lastUpdateTime; }
    public void setLastUpdateTime(LocalDateTime lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StudySession that = (StudySession) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "StudySession{" +
                "id='" + id + '\'' +
                ", subject='" + subject + '\'' +
                ", topic='" + topic + '\'' +
                ", duration=" + durationMinutes + "min" +
                ", completed=" + completed +
                ", active=" + isActive +
                ", points=" + pointsEarned +
                '}';
    }
}
