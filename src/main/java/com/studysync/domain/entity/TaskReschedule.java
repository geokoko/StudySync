package com.studysync.domain.entity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One deadline change of a task: the date it was due before and the date
 * it was moved to. Rows are append-only history; the current due date
 * always lives on the task itself.
 *
 * <p>Uses Active Record pattern - handles its own database operations.</p>
 *
 * @since 0.1.5
 */
public class TaskReschedule {
    private static final Logger logger = LoggerFactory.getLogger(TaskReschedule.class);
    private static JdbcTemplate jdbcTemplate;

    public static void setJdbcTemplate(JdbcTemplate template) {
        jdbcTemplate = template;
    }

    private String id;
    private String taskId;
    private LocalDate oldDeadline;
    private LocalDate newDeadline;
    private LocalDateTime rescheduledAt;

    public TaskReschedule() {
    }

    public TaskReschedule(String taskId, LocalDate oldDeadline, LocalDate newDeadline) {
        this.taskId = taskId;
        this.oldDeadline = oldDeadline;
        this.newDeadline = newDeadline;
        this.rescheduledAt = LocalDateTime.now();
    }

    /**
     * Insert this reschedule event into the database.
     */
    public TaskReschedule save() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized. Make sure Spring context is loaded.");
        }

        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }

        String sql = """
            INSERT INTO task_reschedules (id, task_id, old_deadline, new_deadline, rescheduled_at)
            VALUES (?, ?, ?, ?, ?)
            """;

        jdbcTemplate.update(sql,
            this.id,
            this.taskId,
            this.oldDeadline,
            this.newDeadline,
            this.rescheduledAt
        );

        logger.debug("Task reschedule saved: task {} moved {} -> {}", taskId, oldDeadline, newDeadline);
        return this;
    }

    /**
     * All reschedules of a task, most recent first.
     */
    public static List<TaskReschedule> findByTaskId(String taskId) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        if (taskId == null || taskId.isBlank()) {
            return List.of();
        }

        String sql = "SELECT * FROM task_reschedules WHERE task_id = ? ORDER BY rescheduled_at DESC";
        return jdbcTemplate.query(sql, getRowMapper(), taskId);
    }

    private static RowMapper<TaskReschedule> getRowMapper() {
        return (rs, rowNum) -> {
            TaskReschedule reschedule = new TaskReschedule();
            reschedule.id = rs.getString("id");
            reschedule.taskId = rs.getString("task_id");
            reschedule.oldDeadline = rs.getDate("old_deadline") != null
                ? rs.getDate("old_deadline").toLocalDate() : null;
            reschedule.newDeadline = rs.getDate("new_deadline") != null
                ? rs.getDate("new_deadline").toLocalDate() : null;
            reschedule.rescheduledAt = rs.getTimestamp("rescheduled_at") != null
                ? rs.getTimestamp("rescheduled_at").toLocalDateTime() : null;
            return reschedule;
        };
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public LocalDate getOldDeadline() {
        return oldDeadline;
    }

    public LocalDate getNewDeadline() {
        return newDeadline;
    }

    public LocalDateTime getRescheduledAt() {
        return rescheduledAt;
    }
}
