package com.studysync.domain.entity;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/** Ordered bookmarks belonging to a task. Validation belongs to TaskService. */
public record TaskLink(String title, String url) {
    private static JdbcTemplate jdbcTemplate;

    public static void setJdbcTemplate(JdbcTemplate template) {
        jdbcTemplate = template;
    }

    public static List<TaskLink> findByTaskId(String taskId) {
        return jdbcTemplate.query("SELECT title, url FROM task_links WHERE task_id = ? ORDER BY position, id",
                (rs, row) -> new TaskLink(rs.getString("title"), rs.getString("url")), taskId);
    }

    public static void replaceForTask(String taskId, List<TaskLink> links) {
        jdbcTemplate.update("DELETE FROM task_links WHERE task_id = ?", taskId);
        for (int position = 0; position < links.size(); position++) {
            TaskLink link = links.get(position);
            jdbcTemplate.update("INSERT INTO task_links (id, task_id, title, url, position) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(), taskId, link.title(), link.url(), position);
        }
    }
}
