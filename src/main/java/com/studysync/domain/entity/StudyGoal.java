package com.studysync.domain.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Domain entity representing a study goal.
 *
 * <p>The database model separates a goal's intent from its scheduled attempts:
 * {@code study_goals} is the parent intent, while {@code study_goal_attempts}
 * stores every planned try and outcome. This class intentionally keeps the old
 * view-style API used by the UI: each queried {@code StudyGoal} instance is a
 * parent goal plus one selected attempt context.</p>
 */
public class StudyGoal {
    private static final Logger logger = LoggerFactory.getLogger(StudyGoal.class);
    private static JdbcTemplate jdbcTemplate;

    public enum GoalStatus {
        ACTIVE,
        ACHIEVED,
        ABANDONED
    }

    public enum AttemptOutcome {
        PENDING,
        ACHIEVED,
        MISSED
    }

    private static final String SELECT_ATTEMPT_VIEW = """
        SELECT
            g.id,
            g.description,
            g.task_id,
            COALESCE(g.status, 'ACTIVE') AS status,
            COALESCE(g.abandoned_explicitly, FALSE) AS abandoned_explicitly,
            g.achieved_attempt_id,
            g.created_at AS goal_created_at,
            g.updated_at AS goal_updated_at,
            a.id AS attempt_id,
            a.planned_for_date,
            a.replanned_from_attempt_id,
            COALESCE(a.outcome, 'PENDING') AS attempt_outcome,
            a.reason_if_not_achieved,
            a.outcome_at,
            a.created_at AS attempt_created_at,
            a.updated_at AS attempt_updated_at,
            (
                SELECT COUNT(*)
                FROM study_goal_attempts x
                WHERE x.goal_id = g.id
                  AND (x.created_at < a.created_at OR (x.created_at = a.created_at AND x.id <= a.id))
            ) AS attempt_number,
            (
                SELECT COUNT(*)
                FROM study_goal_attempts m
                WHERE m.goal_id = g.id
                  AND m.outcome = 'MISSED'
                  AND (m.created_at < a.created_at OR (m.created_at = a.created_at AND m.id <= a.id))
            ) AS missed_attempt_count
        FROM study_goals g
        JOIN study_goal_attempts a ON a.goal_id = g.id
        """;

    private String id;
    private LocalDate date;
    private String description;
    private boolean achieved;
    private String reasonIfNotAchieved;
    private int daysDelayed;
    private boolean isDelayed;
    private int pointsDeducted;
    private String taskId;
    private LocalDate replannedForDate;
    private boolean failed;

    private GoalStatus status = GoalStatus.ACTIVE;
    private boolean abandonedExplicitly;
    private String achievedAttemptId;
    private String attemptId;
    private String replannedFromAttemptId;
    private AttemptOutcome attemptOutcome = AttemptOutcome.PENDING;
    private LocalDateTime outcomeAt;
    private int attemptNumber = 1;
    private int missedAttemptCount = 0;

    public static void setJdbcTemplate(JdbcTemplate template) {
        jdbcTemplate = template;
    }

    public StudyGoal() {
        this.id = UUID.randomUUID().toString();
        this.date = LocalDate.now();
        this.status = GoalStatus.ACTIVE;
        this.attemptOutcome = AttemptOutcome.PENDING;
    }

    public StudyGoal(String description) {
        this();
        this.description = description;
    }

    public StudyGoal(String description, String taskId) {
        this(description);
        this.taskId = taskId;
    }

    @JsonCreator
    public StudyGoal(@JsonProperty("id") String id,
                     @JsonProperty("date") LocalDate date,
                     @JsonProperty("description") String description,
                     @JsonProperty("achieved") boolean achieved,
                     @JsonProperty("reasonIfNotAchieved") String reasonIfNotAchieved) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.date = date != null ? date : LocalDate.now();
        this.description = description;
        this.achieved = achieved;
        this.reasonIfNotAchieved = reasonIfNotAchieved;
        this.status = achieved ? GoalStatus.ACHIEVED : GoalStatus.ACTIVE;
        this.attemptOutcome = achieved ? AttemptOutcome.ACHIEVED : AttemptOutcome.PENDING;
    }

    public StudyGoal(String id, LocalDate date, String description, boolean achieved,
                     String reasonIfNotAchieved, int daysDelayed,
                     boolean isDelayed, int pointsDeducted, String taskId) {
        this(id, date, description, achieved, reasonIfNotAchieved,
                daysDelayed, isDelayed, pointsDeducted, taskId, null, false);
    }

    public StudyGoal(String id, LocalDate date, String description, boolean achieved,
                     String reasonIfNotAchieved, int daysDelayed,
                     boolean isDelayed, int pointsDeducted, String taskId,
                     LocalDate replannedForDate) {
        this(id, date, description, achieved, reasonIfNotAchieved,
                daysDelayed, isDelayed, pointsDeducted, taskId, replannedForDate, false);
    }

    public StudyGoal(String id, LocalDate date, String description, boolean achieved,
                     String reasonIfNotAchieved, int daysDelayed,
                     boolean isDelayed, int pointsDeducted, String taskId,
                     LocalDate replannedForDate, boolean failed) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.date = date != null ? date : LocalDate.now();
        this.description = description;
        this.achieved = achieved;
        this.reasonIfNotAchieved = reasonIfNotAchieved;
        this.daysDelayed = daysDelayed;
        this.isDelayed = isDelayed;
        this.pointsDeducted = pointsDeducted;
        this.taskId = taskId;
        this.replannedForDate = replannedForDate;
        this.failed = failed;
        this.status = achieved ? GoalStatus.ACHIEVED : GoalStatus.ACTIVE;
        this.attemptOutcome = failed ? AttemptOutcome.MISSED : achieved ? AttemptOutcome.ACHIEVED : AttemptOutcome.PENDING;
    }

    private StudyGoal(String id, LocalDate date, String description, String taskId,
                      GoalStatus status, boolean abandonedExplicitly, String achievedAttemptId,
                      String attemptId, String replannedFromAttemptId, AttemptOutcome attemptOutcome,
                      String reasonIfNotAchieved, LocalDateTime outcomeAt,
                      int attemptNumber, int missedAttemptCount) {
        this.id = id;
        this.date = date;
        this.description = description;
        this.taskId = taskId;
        this.status = status;
        this.abandonedExplicitly = abandonedExplicitly;
        this.achievedAttemptId = achievedAttemptId;
        this.attemptId = attemptId;
        this.replannedFromAttemptId = replannedFromAttemptId;
        this.attemptOutcome = attemptOutcome;
        this.reasonIfNotAchieved = reasonIfNotAchieved;
        this.outcomeAt = outcomeAt;
        this.attemptNumber = Math.max(1, attemptNumber);
        this.missedAttemptCount = Math.max(0, missedAttemptCount);
        this.achieved = attemptOutcome == AttemptOutcome.ACHIEVED;
        this.failed = attemptOutcome == AttemptOutcome.MISSED || status == GoalStatus.ABANDONED;
        this.isDelayed = this.missedAttemptCount > 0 || this.failed;
        this.daysDelayed = this.missedAttemptCount;
        this.pointsDeducted = this.missedAttemptCount;
        this.replannedForDate = replannedFromAttemptId != null && attemptOutcome == AttemptOutcome.PENDING ? date : null;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isAchieved() { return achieved; }
    public void setAchieved(boolean achieved) {
        this.achieved = achieved;
        if (achieved) {
            this.failed = false;
            this.status = GoalStatus.ACHIEVED;
            this.attemptOutcome = AttemptOutcome.ACHIEVED;
        } else if (this.attemptOutcome == AttemptOutcome.ACHIEVED) {
            this.status = GoalStatus.ACTIVE;
            this.attemptOutcome = AttemptOutcome.PENDING;
        }
    }

    public String getReasonIfNotAchieved() { return reasonIfNotAchieved; }
    public void setReasonIfNotAchieved(String reasonIfNotAchieved) {
        this.reasonIfNotAchieved = reasonIfNotAchieved;
    }

    public int getDaysDelayed() { return daysDelayed; }
    public void setDaysDelayed(int daysDelayed) { this.daysDelayed = daysDelayed; }

    public boolean isDelayed() { return isDelayed; }
    public void setDelayed(boolean delayed) { this.isDelayed = delayed; }

    public int getPointsDeducted() { return pointsDeducted; }
    public void setPointsDeducted(int pointsDeducted) { this.pointsDeducted = pointsDeducted; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public LocalDate getReplannedForDate() { return replannedForDate; }
    public void setReplannedForDate(LocalDate replannedForDate) { this.replannedForDate = replannedForDate; }

    public boolean isFailed() { return failed; }
    public void setFailed(boolean failed) {
        this.failed = failed;
        if (failed) {
            this.achieved = false;
            this.attemptOutcome = AttemptOutcome.MISSED;
        }
    }

    public GoalStatus getStatus() { return status; }
    public boolean isAbandonedExplicitly() { return abandonedExplicitly; }
    public String getAchievedAttemptId() { return achievedAttemptId; }
    public String getAttemptId() { return attemptId; }
    public String getReplannedFromAttemptId() { return replannedFromAttemptId; }
    public AttemptOutcome getAttemptOutcome() { return attemptOutcome; }
    public LocalDateTime getOutcomeAt() { return outcomeAt; }
    public int getAttemptNumber() { return attemptNumber; }
    public int getMissedAttemptCount() { return missedAttemptCount; }

    public int calculateDelayPenalty() {
        return missedAttemptCount;
    }

    public double getDelayColorIntensity() {
        if (!isDelayed) return 0.0;
        return Math.min(1.0, missedAttemptCount / 5.0);
    }

    @Override
    public String toString() {
        return description + " - " + attemptOutcome;
    }

    public StudyGoal save() {
        requireJdbcTemplate();
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (date == null) {
            date = LocalDate.now();
        }
        if (status == null) {
            status = abandonedExplicitly ? GoalStatus.ABANDONED : achieved ? GoalStatus.ACHIEVED : GoalStatus.ACTIVE;
        }
        if (attemptOutcome == null) {
            attemptOutcome = failed ? AttemptOutcome.MISSED : achieved ? AttemptOutcome.ACHIEVED : AttemptOutcome.PENDING;
        }
        if (attemptId == null || attemptId.isBlank()) {
            attemptId = UUID.randomUUID().toString();
        }
        if (attemptOutcome == AttemptOutcome.ACHIEVED) {
            status = GoalStatus.ACHIEVED;
            achievedAttemptId = attemptId;
            achieved = true;
            failed = false;
        } else if (status == GoalStatus.ACHIEVED) {
            status = GoalStatus.ACTIVE;
            achievedAttemptId = null;
        }

        upsertParent();
        upsertAttempt();
        logger.debug("StudyGoal saved: {} - {}", id, description);
        return this;
    }

    public boolean delete() {
        return deleteById(id);
    }

    private void upsertParent() {
        String sql = """
            MERGE INTO study_goals (
                id, date, description, achieved, reason_if_not_achieved,
                days_delayed, is_delayed, points_deducted, task_id,
                replanned_for_date, failed, status, abandoned_explicitly,
                achieved_attempt_id, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;
        jdbcTemplate.update(sql,
                id, date, description, status == GoalStatus.ACHIEVED, reasonIfNotAchieved,
                daysDelayed, isDelayed, pointsDeducted, taskId,
                replannedForDate, attemptOutcome == AttemptOutcome.MISSED, status.name(),
                abandonedExplicitly, achievedAttemptId);
    }

    private void upsertAttempt() {
        String sql = """
            MERGE INTO study_goal_attempts (
                id, goal_id, planned_for_date, replanned_from_attempt_id, outcome,
                reason_if_not_achieved, outcome_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;
        LocalDateTime resolvedOutcomeAt = attemptOutcome == AttemptOutcome.PENDING ? null
                : outcomeAt != null ? outcomeAt : LocalDateTime.now();
        jdbcTemplate.update(sql,
                attemptId, id, date, replannedFromAttemptId, attemptOutcome.name(),
                reasonIfNotAchieved, resolvedOutcomeAt);
    }

    public static List<StudyGoal> findAll() {
        requireJdbcTemplate();
        String sql = SELECT_ATTEMPT_VIEW + """
            ORDER BY a.planned_for_date DESC, a.created_at DESC
            """;
        return jdbcTemplate.query(sql, getAttemptViewMapper());
    }

    public static Optional<StudyGoal> findById(String goalId) {
        if (jdbcTemplate == null || goalId == null) {
            return Optional.empty();
        }
        String sql = SELECT_ATTEMPT_VIEW + """
            WHERE g.id = ?
            ORDER BY
                CASE a.outcome WHEN 'PENDING' THEN 0 WHEN 'ACHIEVED' THEN 1 ELSE 2 END,
                a.planned_for_date DESC,
                a.created_at DESC
            LIMIT 1
            """;
        List<StudyGoal> goals = jdbcTemplate.query(sql, getAttemptViewMapper(), goalId);
        return goals.stream().findFirst();
    }

    public static List<StudyGoal> findByDate(LocalDate date) {
        if (jdbcTemplate == null || date == null) {
            return List.of();
        }
        String sql = SELECT_ATTEMPT_VIEW + """
            WHERE g.status <> 'ABANDONED'
              AND a.planned_for_date = ?
              AND a.outcome IN ('PENDING', 'ACHIEVED')
            ORDER BY a.outcome ASC, g.created_at ASC
            """;
        return jdbcTemplate.query(sql, getAttemptViewMapper(), date);
    }

    public static List<StudyGoal> findAllByDate(LocalDate date) {
        if (jdbcTemplate == null || date == null) {
            return List.of();
        }
        String sql = SELECT_ATTEMPT_VIEW + """
            WHERE a.planned_for_date = ?
            ORDER BY
                CASE a.outcome WHEN 'PENDING' THEN 0 WHEN 'ACHIEVED' THEN 1 ELSE 2 END,
                g.created_at ASC
            """;
        return jdbcTemplate.query(sql, getAttemptViewMapper(), date);
    }

    public static List<StudyGoal> findByDateIncludingDelayed(LocalDate date) {
        return findByDate(date);
    }

    public static List<StudyGoal> findAllByDateIncludingDelayed(LocalDate date) {
        return findAllByDate(date);
    }

    public static List<StudyGoal> findAchieved() {
        requireJdbcTemplate();
        String sql = SELECT_ATTEMPT_VIEW + """
            WHERE a.outcome = 'ACHIEVED'
            ORDER BY a.planned_for_date DESC, a.created_at DESC
            """;
        return jdbcTemplate.query(sql, getAttemptViewMapper());
    }

    public static boolean deleteById(String goalId) {
        if (jdbcTemplate == null || goalId == null) {
            return false;
        }
        jdbcTemplate.update("DELETE FROM study_goal_attempts WHERE goal_id = ?", goalId);
        int rowsAffected = jdbcTemplate.update("DELETE FROM study_goals WHERE id = ?", goalId);
        boolean deleted = rowsAffected > 0;
        if (deleted) {
            logger.info("StudyGoal deleted: {}", goalId);
        }
        return deleted;
    }

    public static long countByAchievement(boolean achieved) {
        if (jdbcTemplate == null) {
            return 0L;
        }
        String outcome = achieved ? "ACHIEVED" : "PENDING";
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM study_goal_attempts WHERE outcome = ?",
                Integer.class, outcome);
        return count != null ? count : 0L;
    }

    public static List<StudyGoal> findUnachievedByDate(LocalDate date) {
        if (jdbcTemplate == null || date == null) {
            return List.of();
        }
        String sql = SELECT_ATTEMPT_VIEW + """
            WHERE a.planned_for_date = ?
              AND a.outcome <> 'ACHIEVED'
            ORDER BY a.created_at
            """;
        return jdbcTemplate.query(sql, getAttemptViewMapper(), date);
    }

    public static List<StudyGoal> findDelayed() {
        requireJdbcTemplate();
        String sql = SELECT_ATTEMPT_VIEW + """
            WHERE a.outcome = 'MISSED'
            ORDER BY a.planned_for_date DESC, a.created_at DESC
            """;
        return jdbcTemplate.query(sql, getAttemptViewMapper());
    }

    public static List<StudyGoal> findDelayedByDate(LocalDate date) {
        if (jdbcTemplate == null || date == null) {
            return List.of();
        }
        String sql = SELECT_ATTEMPT_VIEW + """
            WHERE a.planned_for_date = ?
              AND a.outcome = 'MISSED'
            ORDER BY a.created_at
            """;
        return jdbcTemplate.query(sql, getAttemptViewMapper(), date);
    }

    public static List<StudyGoal> findByTaskIdForDate(String taskId, LocalDate date) {
        if (jdbcTemplate == null || taskId == null || taskId.isBlank() || date == null) {
            return List.of();
        }
        String sql = SELECT_ATTEMPT_VIEW + """
            WHERE g.task_id = ?
              AND g.status <> 'ABANDONED'
              AND a.planned_for_date = ?
              AND a.outcome IN ('PENDING', 'ACHIEVED')
            ORDER BY a.outcome ASC, a.created_at ASC
            """;
        return jdbcTemplate.query(sql, getAttemptViewMapper(), taskId, date);
    }

    public static List<StudyGoal> findByTaskId(String taskId) {
        if (jdbcTemplate == null || taskId == null || taskId.isBlank()) {
            return List.of();
        }
        String sql = SELECT_ATTEMPT_VIEW + """
            WHERE g.task_id = ?
            ORDER BY a.planned_for_date DESC, a.created_at DESC
            """;
        return jdbcTemplate.query(sql, getAttemptViewMapper(), taskId);
    }

    public static boolean updateDetails(String goalId, String description, LocalDate pendingPlannedForDate) {
        if (jdbcTemplate == null || goalId == null || goalId.isBlank()
                || description == null || description.isBlank()) {
            return false;
        }
        int parentRows = jdbcTemplate.update("""
            UPDATE study_goals
            SET description = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """, description.trim(), goalId);
        if (pendingPlannedForDate != null) {
            jdbcTemplate.update("""
                UPDATE study_goal_attempts
                SET planned_for_date = ?, updated_at = CURRENT_TIMESTAMP
                WHERE goal_id = ? AND outcome = 'PENDING'
                """, pendingPlannedForDate, goalId);
        }
        return parentRows > 0;
    }

    public static Set<String> findAchievedTaskDatePairs(LocalDate rangeStart, LocalDate rangeEnd) {
        if (jdbcTemplate == null || rangeStart == null || rangeEnd == null) {
            return Set.of();
        }
        String sql = """
            SELECT DISTINCT g.task_id, a.planned_for_date
            FROM study_goals g
            JOIN study_goal_attempts a ON a.goal_id = g.id
            WHERE a.outcome = 'ACHIEVED'
              AND g.task_id IS NOT NULL
              AND a.planned_for_date >= ? AND a.planned_for_date <= ?
            """;
        Set<String> result = new HashSet<>();
        jdbcTemplate.query(sql, (rs, rowNum) -> {
            result.add(rs.getString("task_id") + "|" + rs.getDate("planned_for_date").toLocalDate());
            return null;
        }, rangeStart, rangeEnd);
        return result;
    }

    public static Set<String> findHandledTaskDatePairs(LocalDate rangeStart, LocalDate rangeEnd) {
        if (jdbcTemplate == null || rangeStart == null || rangeEnd == null) {
            return Set.of();
        }
        String sql = """
            WITH RECURSIVE handled_attempts (
                task_id, attempt_id, planned_for_date, outcome, replanned_from_attempt_id, depth
            ) AS (
                SELECT g.task_id, a.id, a.planned_for_date, a.outcome, a.replanned_from_attempt_id, 0
                FROM study_goals g
                JOIN study_goal_attempts a ON a.goal_id = g.id
                WHERE g.task_id IS NOT NULL
                  AND a.outcome = 'ACHIEVED'
                UNION ALL
                SELECT h.task_id, parent.id, parent.planned_for_date, parent.outcome,
                       parent.replanned_from_attempt_id, h.depth + 1
                FROM study_goal_attempts parent
                JOIN handled_attempts h ON h.replanned_from_attempt_id = parent.id
                WHERE h.depth < 20
            )
            SELECT DISTINCT task_id, planned_for_date
            FROM handled_attempts
            WHERE planned_for_date >= ?
              AND planned_for_date <= ?
              AND outcome IN ('ACHIEVED', 'MISSED')
            """;
        Set<String> result = new HashSet<>();
        jdbcTemplate.query(sql, (rs, rowNum) -> {
            result.add(rs.getString("task_id") + "|" + rs.getDate("planned_for_date").toLocalDate());
            return null;
        }, rangeStart, rangeEnd);
        return result;
    }

    public static boolean hasAchievedGoalForTask(String taskId, LocalDate date) {
        if (jdbcTemplate == null || taskId == null || taskId.isBlank() || date == null) {
            return false;
        }
        String sql = """
            SELECT COUNT(*)
            FROM study_goals g
            JOIN study_goal_attempts a ON a.goal_id = g.id
            WHERE g.task_id = ?
              AND a.planned_for_date = ?
              AND a.outcome = 'ACHIEVED'
            """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, taskId, date);
        return count != null && count > 0;
    }

    public static boolean hasHandledGoalForTaskOccurrence(String taskId, LocalDate occurrenceDate) {
        if (jdbcTemplate == null || taskId == null || taskId.isBlank() || occurrenceDate == null) {
            return false;
        }
        String sql = """
            WITH RECURSIVE handled_attempts (
                attempt_id, planned_for_date, outcome, replanned_from_attempt_id, depth
            ) AS (
                SELECT a.id, a.planned_for_date, a.outcome, a.replanned_from_attempt_id, 0
                FROM study_goals g
                JOIN study_goal_attempts a ON a.goal_id = g.id
                WHERE g.task_id = ?
                  AND a.outcome = 'ACHIEVED'
                UNION ALL
                SELECT parent.id, parent.planned_for_date, parent.outcome,
                       parent.replanned_from_attempt_id, h.depth + 1
                FROM study_goal_attempts parent
                JOIN handled_attempts h ON h.replanned_from_attempt_id = parent.id
                WHERE h.depth < 20
            )
            SELECT COUNT(*)
            FROM handled_attempts
            WHERE planned_for_date = ?
              AND outcome IN ('ACHIEVED', 'MISSED')
            """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, taskId, occurrenceDate);
        return count != null && count > 0;
    }

    public static List<StudyGoal> findUnlinkedForDate(LocalDate date) {
        if (jdbcTemplate == null || date == null) {
            return List.of();
        }
        String sql = SELECT_ATTEMPT_VIEW + """
            WHERE g.task_id IS NULL
              AND g.status <> 'ABANDONED'
              AND a.planned_for_date = ?
              AND a.outcome IN ('PENDING', 'ACHIEVED')
            ORDER BY a.outcome ASC, a.created_at ASC
            """;
        return jdbcTemplate.query(sql, getAttemptViewMapper(), date);
    }

    public static List<StudyGoal> findDelayedAndNotReplanned() {
        if (jdbcTemplate == null) {
            return List.of();
        }
        String sql = SELECT_ATTEMPT_VIEW + """
            WHERE g.status = 'ACTIVE'
              AND a.outcome = 'MISSED'
              AND NOT EXISTS (
                  SELECT 1 FROM study_goal_attempts p
                  WHERE p.goal_id = g.id AND p.outcome = 'PENDING'
              )
              AND a.id = (
                  SELECT latest.id
                  FROM study_goal_attempts latest
                  WHERE latest.goal_id = g.id
                  ORDER BY latest.created_at DESC, latest.planned_for_date DESC
                  LIMIT 1
              )
            ORDER BY a.planned_for_date ASC, a.created_at ASC
            """;
        return jdbcTemplate.query(sql, getAttemptViewMapper());
    }

    public static int markPendingAttemptsBefore(LocalDate today) {
        if (jdbcTemplate == null || today == null) {
            return 0;
        }
        String sql = """
            UPDATE study_goal_attempts
            SET outcome = 'MISSED', outcome_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE outcome = 'PENDING'
              AND planned_for_date < ?
            """;
        return jdbcTemplate.update(sql, today);
    }

    public static boolean createReplanAttempt(String goalId, LocalDate plannedForDate) {
        if (jdbcTemplate == null || goalId == null || goalId.isBlank() || plannedForDate == null) {
            return false;
        }
        Integer pendingCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM study_goal_attempts
            WHERE goal_id = ? AND outcome = 'PENDING'
            """, Integer.class, goalId);
        if (pendingCount != null && pendingCount > 0) {
            return false;
        }

        String latestAttemptId = jdbcTemplate.query("""
            SELECT id FROM study_goal_attempts
            WHERE goal_id = ?
            ORDER BY created_at DESC, planned_for_date DESC
            LIMIT 1
            """, rs -> rs.next() ? rs.getString("id") : null, goalId);
        if (latestAttemptId == null) {
            return false;
        }

        String attemptId = UUID.randomUUID().toString();
        int rows = jdbcTemplate.update("""
            INSERT INTO study_goal_attempts (
                id, goal_id, planned_for_date, replanned_from_attempt_id, outcome,
                created_at, updated_at
            )
            VALUES (?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, attemptId, goalId, plannedForDate, latestAttemptId);
        jdbcTemplate.update("""
            UPDATE study_goals
            SET status = 'ACTIVE', achieved_attempt_id = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'ACTIVE'
            """, goalId);
        return rows > 0;
    }

    public static boolean markCurrentAttemptAchieved(String goalId, String reasonIfNot) {
        Optional<StudyGoal> goalOpt = findById(goalId);
        if (goalOpt.isEmpty()) {
            return false;
        }
        StudyGoal goal = goalOpt.get();
        if (goal.attemptId == null) {
            return false;
        }
        jdbcTemplate.update("""
            UPDATE study_goal_attempts
            SET outcome = 'ACHIEVED', reason_if_not_achieved = ?, outcome_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """, reasonIfNot, goal.attemptId);
        jdbcTemplate.update("""
            UPDATE study_goals
            SET status = 'ACHIEVED', achieved_attempt_id = ?, achieved = TRUE, failed = FALSE,
                reason_if_not_achieved = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """, goal.attemptId, reasonIfNot, goalId);
        return true;
    }

    public static boolean reopenAchievedGoal(String goalId) {
        if (jdbcTemplate == null || goalId == null || goalId.isBlank()) {
            return false;
        }
        String achievedAttempt = jdbcTemplate.query("""
            SELECT achieved_attempt_id FROM study_goals WHERE id = ?
            """, rs -> rs.next() ? rs.getString("achieved_attempt_id") : null, goalId);
        if (achievedAttempt == null) {
            return false;
        }
        jdbcTemplate.update("""
            UPDATE study_goal_attempts
            SET outcome = 'PENDING', reason_if_not_achieved = NULL, outcome_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """, achievedAttempt);
        jdbcTemplate.update("""
            UPDATE study_goals
            SET status = 'ACTIVE', achieved_attempt_id = NULL, achieved = FALSE,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """, goalId);
        return true;
    }

    public static boolean abandonGoal(String goalId) {
        Optional<StudyGoal> goalOpt = findById(goalId);
        if (goalOpt.isEmpty()) {
            return false;
        }
        StudyGoal goal = goalOpt.get();
        if (goal.attemptId != null && goal.attemptOutcome == AttemptOutcome.PENDING) {
            jdbcTemplate.update("""
                UPDATE study_goal_attempts
                SET outcome = 'MISSED', outcome_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, goal.attemptId);
        }
        int rows = jdbcTemplate.update("""
            UPDATE study_goals
            SET status = 'ABANDONED', achieved_attempt_id = NULL, achieved = FALSE, failed = TRUE,
                abandoned_explicitly = TRUE, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """, goalId);
        return rows > 0;
    }

    private static RowMapper<StudyGoal> getAttemptViewMapper() {
        return (rs, rowNum) -> new StudyGoal(
                rs.getString("id"),
                rs.getObject("planned_for_date", LocalDate.class),
                rs.getString("description"),
                rs.getString("task_id"),
                parseGoalStatus(rs.getString("status")),
                rs.getBoolean("abandoned_explicitly"),
                rs.getString("achieved_attempt_id"),
                rs.getString("attempt_id"),
                rs.getString("replanned_from_attempt_id"),
                parseAttemptOutcome(rs.getString("attempt_outcome")),
                rs.getString("reason_if_not_achieved"),
                rs.getObject("outcome_at", LocalDateTime.class),
                rs.getInt("attempt_number"),
                rs.getInt("missed_attempt_count")
        );
    }

    private static GoalStatus parseGoalStatus(String value) {
        try {
            return value == null ? GoalStatus.ACTIVE : GoalStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return GoalStatus.ACTIVE;
        }
    }

    private static AttemptOutcome parseAttemptOutcome(String value) {
        try {
            return value == null ? AttemptOutcome.PENDING : AttemptOutcome.valueOf(value);
        } catch (IllegalArgumentException e) {
            return AttemptOutcome.PENDING;
        }
    }

    private static void requireJdbcTemplate() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
    }
}
