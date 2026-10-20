package com.studysync.domain.service;

import com.studysync.domain.entity.Task;
import com.studysync.domain.valueobject.TaskStatus;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectMigrationTest {
    private HikariDataSource dataSource;
    private JdbcTemplate jdbc;
    private static final LocalDateTime CREATED = LocalDateTime.of(2023, 4, 5, 10, 30);
    private static final LocalDateTime UPDATED = LocalDateTime.of(2024, 6, 7, 14, 15);

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:migration-" + System.nanoTime());
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        jdbc = new JdbcTemplate(dataSource);
        Task.setJdbcTemplate(jdbc);
        migrate();
        jdbc.update("DELETE FROM schema_migrations WHERE id = 'projects-to-tasks'");
        jdbc.execute("DROP TABLE task_links");
        jdbc.execute("ALTER TABLE tasks DROP CONSTRAINT fk_tasks_parent");
        jdbc.execute("ALTER TABLE tasks DROP COLUMN parent_id");
        jdbc.execute("ALTER TABLE tasks DROP COLUMN estimated_hours");
        jdbc.execute("ALTER TABLE tasks DROP COLUMN notes");
        jdbc.execute("ALTER TABLE tasks DROP COLUMN legacy_project_id");
    }

    @AfterEach
    void tearDown() {
        new com.studysync.config.ActiveRecordConfig(null).initializeModelDatabaseAccess();
        dataSource.close();
    }

    @Test
    void realSchemaMigratesEveryStatusAndPreservesLegacyDataOnRepeatedStartup() {
        for (String status : List.of("ACTIVE", "ON_HOLD", "COMPLETED", "CANCELLED")) {
            project(status, status, LocalDate.of(2024, 5, 6));
        }
        project("fallback", "COMPLETED", null);
        project("custom-category", "ACTIVE", null);
        jdbc.update("UPDATE projects SET category = 'Research' WHERE id = 'custom-category'");
        project("null-fields", "ACTIVE", null);
        jdbc.update("UPDATE projects SET category = NULL, priority = NULL, notes = NULL, estimated_hours = NULL WHERE id = 'null-fields'");
        // Long IDs and collision with an existing task must not overwrite either row.
        project("p".repeat(50), "ACTIVE", null);
        jdbc.update("INSERT INTO tasks (id, title, category) VALUES ('ACTIVE', 'Existing task', 'Study')");
        jdbc.update("""
                INSERT INTO project_sessions (id, project_id, date, duration_minutes, points_earned)
                VALUES ('session', 'ACTIVE', DATE '2024-05-06', 123, 999)
                """);
        List<Map<String, Object>> oldProjects = jdbc.queryForList("SELECT * FROM projects ORDER BY id");
        List<Map<String, Object>> oldSessions = jdbc.queryForList("SELECT * FROM project_sessions ORDER BY id");
        migrate();
        List<Map<String, Object>> first = jdbc.queryForList("SELECT * FROM tasks ORDER BY id");
        migrate();
        assertEquals(first, jdbc.queryForList("SELECT * FROM tasks ORDER BY id"));
        assertEquals(9, Task.findAll().size());
        Map<String, TaskStatus> statuses = Map.of("ACTIVE", TaskStatus.OPEN, "ON_HOLD", TaskStatus.POSTPONED,
                "COMPLETED", TaskStatus.COMPLETED, "CANCELLED", TaskStatus.CANCELLED, "fallback", TaskStatus.COMPLETED);
        statuses.forEach((id, status) -> {
            Task task = imported(id);
            assertEquals("Title " + id, task.getTitle());
            assertEquals("Description\nunchanged", task.getDescription());
            assertEquals("Study", task.getCategory());
            assertEquals(4, task.getPriority().stars());
            assertEquals(LocalDate.of(2024, 8, 9), task.getDeadline());
            assertEquals(CREATED, task.getCreatedAt());
            assertEquals(status, task.getStatus());
            assertEquals(17, task.getEstimatedHours());
            assertEquals("# Notes\n\n  Preserve all text Ελληνικά  \n", task.getNotes());
            assertNull(task.getParentId());
            assertNull(task.getRecurringPattern());
            if (status == TaskStatus.COMPLETED) {
                assertEquals(id.equals("fallback") ? UPDATED.toLocalDate() : LocalDate.of(2024, 5, 6),
                        task.getCompletedAt());
            } else {
                assertNull(task.getCompletedAt());
            }
        });
        assertEquals("Research", imported("custom-category").getCategory());
        assertEquals("Uncategorized", imported("null-fields").getCategory());
        assertEquals(1, imported("null-fields").getPriority().stars());
        assertNull(imported("null-fields").getEstimatedHours());
        assertNull(imported("null-fields").getNotes());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM task_categories WHERE name IN ('Research', 'Uncategorized')", Integer.class));
        assertEquals(oldProjects, jdbc.queryForList("SELECT * FROM projects ORDER BY id"));
        assertEquals(oldSessions, jdbc.queryForList("SELECT * FROM project_sessions ORDER BY id"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM study_sessions", Integer.class));
        assertEquals("Existing task", Task.findById("ACTIVE").orElseThrow().getTitle());
    }

    @Test
    void retryBeforeMarkerDoesNotDuplicateAndLaterLaunchDoesNotResurrectDeletedImports() {
        project("source", "ACTIVE", null);
        migrate();
        Task task = imported("source");
        jdbc.update("DELETE FROM schema_migrations WHERE id = 'projects-to-tasks'");
        migrate();
        assertEquals(task.getId(), imported("source").getId());
        assertEquals(1, Task.findAll().size());
        task.setNotes("User edits after migration");
        task.save();
        jdbc.update("UPDATE projects SET notes = 'Changed by an old build'");
        migrate();
        assertEquals("User edits after migration", imported("source").getNotes());
        task.delete();
        migrate();
        assertTrue(Task.findAll().isEmpty());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM projects", Integer.class));
    }

    @Test
    void legacySessionsHaveNoEffectOnScores() {
        project("source", "ACTIVE", null);
        jdbc.update("""
                INSERT INTO project_sessions (id, project_id, date, duration_minutes, points_earned)
                VALUES ('legacy', 'source', CURRENT_DATE, 120, 500)
                """);
        migrate();
        new com.studysync.config.ActiveRecordConfig(jdbc).initializeModelDatabaseAccess();
        StudyService study = org.mockito.Mockito.mock(StudyService.class);
        DateTimeService clock = org.mockito.Mockito.mock(DateTimeService.class);
        org.mockito.Mockito.when(clock.getCurrentDate()).thenReturn(LocalDate.now());
        org.mockito.Mockito.when(study.getGoalsForDate(LocalDate.now())).thenReturn(List.of());
        ScoreBreakdown score = new ScoringService(study, clock).scoreForDate(LocalDate.now());
        assertEquals(0, score.sessionPoints());
        assertEquals(0, score.minutes());
        assertEquals(0, score.sessions());
    }

    private void project(String id, String status, LocalDate completed) {
        jdbc.update("""
                INSERT INTO projects (id, title, description, category, priority, deadline, status, completion_date,
                                      estimated_hours, notes, created_at, updated_at, progress_percentage)
                VALUES (?, ?, ?, 'Study', 4, DATE '2024-08-09', ?, ?, 17, ?, ?, ?, 70)
                """, id, "Title " + id, "Description\nunchanged", status, completed,
                "# Notes\n\n  Preserve all text Ελληνικά  \n", CREATED, UPDATED);
    }

    private Task imported(String projectId) {
        String id = jdbc.queryForObject("SELECT id FROM tasks WHERE legacy_project_id = ?", String.class, projectId);
        return Task.findById(id).orElseThrow();
    }

    private void migrate() {
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
    }
}
