package com.studysync.domain.service;

import com.studysync.domain.entity.Project;
import com.studysync.domain.entity.ProjectSession;
import com.studysync.domain.valueobject.ProjectStatus;
import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.integration.drive.GoogleDriveService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectServicePersistenceTest {

    private HikariDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:studysync-projects-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        dataSource = new HikariDataSource(config);
        jdbcTemplate = new JdbcTemplate(dataSource);
        createTables();

        Project.setJdbcTemplate(jdbcTemplate);
        ProjectSession.setJdbcTemplate(jdbcTemplate);

        GoogleDriveService googleDriveService = mock(GoogleDriveService.class);
        when(googleDriveService.saveLocally()).thenReturn(true);

        projectService = new ProjectService(googleDriveService);
    }

    @AfterEach
    void tearDown() {
        Project.setJdbcTemplate(null);
        ProjectSession.setJdbcTemplate(null);
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Test
    void workedMinutesAndSessionCountSurviveAReload() {
        Project project = savedProject("p1");

        // Minutes used to be stored as whole hours, so anything not divisible
        // by 60 was lost on every save, and the session count was never stored.
        project.addWorkedMinutes(95);
        project.incrementSessionCount();
        project.save();

        Project reloaded = Project.findById("p1").orElseThrow();
        assertEquals(95, reloaded.getTotalMinutesWorked());
        assertEquals(1, reloaded.getTotalSessionsCount());
        assertNotNull(reloaded.getLastWorkedOn());

        // Repeated round-trips must not erode the total either.
        reloaded.save();
        assertEquals(95, Project.findById("p1").orElseThrow().getTotalMinutesWorked());
    }

    @Test
    void deletingASessionGivesItsTimeBack() {
        savedProject("p2");
        ProjectSession session = projectService.startProjectSession("p2");
        jdbcTemplate.update("UPDATE project_sessions SET duration_minutes = 50, completed = TRUE WHERE id = ?",
                session.getId());

        Project project = Project.findById("p2").orElseThrow();
        project.addWorkedMinutes(50);
        project.incrementSessionCount();
        project.save();
        assertEquals(50, Project.findById("p2").orElseThrow().getTotalMinutesWorked());

        projectService.deleteProjectSession(session.getId());

        // addWorkedMinutes used to clamp its argument at zero, so subtracting
        // was silently a no-op and deleted time stayed on the project forever.
        Project after = Project.findById("p2").orElseThrow();
        assertEquals(0, after.getTotalMinutesWorked());
        assertEquals(0, after.getTotalSessionsCount());
    }

    @Test
    void completionDateIsSetOnceAndNotBumpedByLaterSaves() {
        Project project = savedProject("p3");
        project.updateStatus(ProjectStatus.COMPLETED);
        project.save();

        jdbcTemplate.update("UPDATE projects SET completion_date = DATE '2026-01-15' WHERE id = ?", "p3");

        Project.findById("p3").orElseThrow().save();

        assertEquals(LocalDate.of(2026, 1, 15), jdbcTemplate.queryForObject(
                "SELECT completion_date FROM projects WHERE id = ?", LocalDate.class, "p3"));
    }

    @Test
    void savingDoesNotClobberNotesOrTheHourEstimate() {
        savedProject("p4");
        jdbcTemplate.update("UPDATE projects SET notes = 'keep me', estimated_hours = 12 WHERE id = ?", "p4");

        Project.findById("p4").orElseThrow().save();

        assertEquals("keep me", jdbcTemplate.queryForObject(
                "SELECT notes FROM projects WHERE id = ?", String.class, "p4"));
        assertEquals(12, jdbcTemplate.queryForObject(
                "SELECT estimated_hours FROM projects WHERE id = ?", Integer.class, "p4"));
    }

    private Project savedProject(final String id) {
        Project project = new Project(id, "Project " + id, "A description", "Study",
                new TaskPriority(3), LocalDate.of(2026, 1, 1), null,
                ProjectStatus.ACTIVE, null, null, 0, 0);
        return project.save();
    }

    private void createTables() {
        jdbcTemplate.execute("""
                CREATE TABLE projects (
                    id VARCHAR(50) PRIMARY KEY,
                    title VARCHAR(255) NOT NULL,
                    description TEXT,
                    category VARCHAR(100),
                    status VARCHAR(20) DEFAULT 'ACTIVE',
                    priority INTEGER DEFAULT 1,
                    start_date DATE,
                    deadline DATE,
                    completion_date DATE,
                    progress_percentage INTEGER DEFAULT 0,
                    estimated_hours INTEGER,
                    actual_hours INTEGER,
                    total_minutes_worked INTEGER,
                    total_sessions_count INTEGER,
                    last_worked_on TIMESTAMP,
                    notes TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE project_sessions (
                    id VARCHAR(50) PRIMARY KEY,
                    project_id VARCHAR(50) NOT NULL,
                    date DATE NOT NULL,
                    start_time TIMESTAMP,
                    end_time TIMESTAMP,
                    duration_minutes INTEGER DEFAULT 0,
                    completed BOOLEAN DEFAULT FALSE,
                    session_title VARCHAR(255),
                    objectives TEXT,
                    progress TEXT,
                    next_steps TEXT,
                    challenges TEXT,
                    notes TEXT,
                    points_earned INTEGER DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
                )
                """);
    }
}
