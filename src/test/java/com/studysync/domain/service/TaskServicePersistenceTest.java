package com.studysync.domain.service;

import com.studysync.domain.entity.Task;
import com.studysync.domain.entity.TaskReschedule;
import com.studysync.domain.exception.ValidationException;
import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.domain.valueobject.TaskStatus;
import com.studysync.integration.drive.GoogleDriveService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskServicePersistenceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 28);

    private HikariDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:studysync-tasks-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        dataSource = new HikariDataSource(config);
        jdbcTemplate = new JdbcTemplate(dataSource);

        jdbcTemplate.execute("""
                CREATE TABLE tasks (
                    id VARCHAR(50) PRIMARY KEY,
                    title VARCHAR(255) NOT NULL,
                    description TEXT,
                    category VARCHAR(100),
                    priority INTEGER DEFAULT 1,
                    deadline DATE,
                    status VARCHAR(20) DEFAULT 'OPEN',
                    points INTEGER DEFAULT 0,
                    recurring_pattern VARCHAR(100),
                    start_date DATE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE task_reschedules (
                    id VARCHAR(50) PRIMARY KEY,
                    task_id VARCHAR(50) NOT NULL,
                    old_deadline DATE,
                    new_deadline DATE NOT NULL,
                    rescheduled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
                )
                """);

        Task.setJdbcTemplate(jdbcTemplate);
        TaskReschedule.setJdbcTemplate(jdbcTemplate);

        GoogleDriveService googleDriveService = mock(GoogleDriveService.class);
        when(googleDriveService.saveLocally()).thenReturn(true);

        DateTimeService dateTimeService = mock(DateTimeService.class);
        when(dateTimeService.getCurrentDate()).thenReturn(TODAY);

        taskService = new TaskService(mock(CategoryService.class), googleDriveService, dateTimeService);
    }

    @AfterEach
    void tearDown() {
        Task.setJdbcTemplate(null);
        TaskReschedule.setJdbcTemplate(null);
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Test
    void reschedulingDelayedTaskRecordsHistoryAndRevivesIt() {
        Task task = savedTask("task-1", TODAY.minusDays(8), TaskStatus.DELAYED);

        taskService.rescheduleTask(task, TODAY.plusDays(2));

        Task stored = Task.findById("task-1").orElseThrow();
        assertEquals(TODAY.plusDays(2), stored.getDeadline());
        assertEquals(TaskStatus.OPEN, stored.getStatus());

        List<TaskReschedule> history = TaskReschedule.findByTaskId("task-1");
        assertEquals(1, history.size());
        assertEquals(TODAY.minusDays(8), history.get(0).getOldDeadline());
        assertEquals(TODAY.plusDays(2), history.get(0).getNewDeadline());
    }

    @Test
    void rescheduleRejectsPastDates() {
        Task task = savedTask("task-2", TODAY.minusDays(3), TaskStatus.DELAYED);

        assertThrows(ValidationException.class,
                () -> taskService.rescheduleTask(task, TODAY.minusDays(1)));
        assertEquals(TaskStatus.DELAYED, Task.findById("task-2").orElseThrow().getStatus());
        assertTrue(TaskReschedule.findByTaskId("task-2").isEmpty());
    }

    @Test
    void reschedulingTaskWithoutDeadlineRecordsNullOldDate() {
        Task task = savedTask("task-3", null, TaskStatus.OPEN);

        taskService.rescheduleTask(task, TODAY.plusDays(5));

        List<TaskReschedule> history = TaskReschedule.findByTaskId("task-3");
        assertEquals(1, history.size());
        assertNull(history.get(0).getOldDeadline());
        assertEquals(TODAY.plusDays(5), history.get(0).getNewDeadline());
    }

    @Test
    void markDelayedTasksResumesPostponedTasksWhoseDateArrived() {
        savedTask("resume-today", TODAY, TaskStatus.POSTPONED);
        savedTask("resume-missed", TODAY.minusDays(5), TaskStatus.POSTPONED);
        savedTask("resume-later", TODAY.plusDays(5), TaskStatus.POSTPONED);

        taskService.markDelayedTasks();

        assertEquals(TaskStatus.OPEN, Task.findById("resume-today").orElseThrow().getStatus());
        // Missed its resume date entirely, so it falls through to the DELAYED sweep.
        assertEquals(TaskStatus.DELAYED, Task.findById("resume-missed").orElseThrow().getStatus());
        assertEquals(TaskStatus.POSTPONED, Task.findById("resume-later").orElseThrow().getStatus());
    }

    @Test
    void editingPostponedOrCancelledTaskDoesNotReMarkItDelayed() {
        Task postponed = savedTask("postponed-old", TODAY.minusDays(10), TaskStatus.POSTPONED);
        Task cancelled = savedTask("cancelled-old", TODAY.minusDays(10), TaskStatus.CANCELLED);

        taskService.updateTask(postponed, new TaskUpdate("Renamed postponed", null, null, null, null));
        taskService.updateTask(cancelled, new TaskUpdate("Renamed cancelled", null, null, null, null));

        assertEquals(TaskStatus.POSTPONED, Task.findById("postponed-old").orElseThrow().getStatus());
        assertEquals(TaskStatus.CANCELLED, Task.findById("cancelled-old").orElseThrow().getStatus());
    }

    @Test
    void updateWithUnchangedDeadlineRecordsNoHistory() {
        Task task = savedTask("task-4", TODAY.plusDays(3), TaskStatus.OPEN);

        taskService.updateTask(task, new TaskUpdate("Renamed only", null, null, null, null));

        assertTrue(TaskReschedule.findByTaskId("task-4").isEmpty());
        assertEquals(TODAY.plusDays(3), Task.findById("task-4").orElseThrow().getDeadline());
    }

    private Task savedTask(final String id, final LocalDate deadline, final TaskStatus status) {
        Task task = new Task(id, "Task " + id, "", "Study", new TaskPriority(3),
                deadline, status, 0, "", null);
        return task.save();
    }
}
