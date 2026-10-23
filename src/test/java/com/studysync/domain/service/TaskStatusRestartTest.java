package com.studysync.domain.service;

import com.studysync.domain.entity.Task;
import com.studysync.domain.entity.TaskLink;
import com.studysync.domain.entity.TaskReschedule;
import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.domain.valueobject.TaskStatus;
import com.studysync.integration.drive.GoogleDriveService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskStatusRestartTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    @TempDir
    Path directory;
    private JdbcTemplate jdbc;
    private TaskService service;
    private SingleConnectionDataSource source;

    private void openDatabase() {
        openDatabase(TODAY);
    }

    private void openDatabase(final LocalDate today) {
        source = new SingleConnectionDataSource("jdbc:h2:file:" + directory.resolve("study"), "sa", "", true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(source);
        jdbc = new JdbcTemplate(source);
        Task.setJdbcTemplate(jdbc);
        TaskLink.setJdbcTemplate(jdbc);
        TaskReschedule.setJdbcTemplate(jdbc);
        var clock = mock(DateTimeService.class);
        when(clock.getCurrentDate()).thenReturn(today);
        var drive = mock(GoogleDriveService.class);
        when(drive.saveLocally()).thenReturn(true);
        service = new TaskService(mock(CategoryService.class), drive, clock);
    }

    private void closeDatabase() {
        jdbc.execute("SHUTDOWN");
        source.destroy();
        jdbc = null;
        Task.setJdbcTemplate(null);
        TaskLink.setJdbcTemplate(null);
        TaskReschedule.setJdbcTemplate(null);
    }

    @AfterEach
    void close() {
        if (jdbc != null) {
            closeDatabase();
        }
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void savedStatusesSurviveDatabaseReopenAndStartupMaintenance(final TaskStatus status) {
        openDatabase();
        service.markDelayedTasks();
        var deadlines = Arrays.asList(TODAY.minusDays(5), TODAY, TODAY.plusDays(5), null);
        for (int index = 0; index < deadlines.size(); index++) {
            Task task = new Task("task-" + index, "Task " + index, "", "Study", new TaskPriority(1),
                    deadlines.get(index), TaskStatus.OPEN, 0);
            task.save();
            service.updateTaskStatus(task, status);
        }
        closeDatabase();
        openDatabase();
        for (int index = 0; index < deadlines.size(); index++) {
            assertEquals(status, Task.findById("task-" + index).orElseThrow().getStatus(),
                    "Stored status before startup maintenance");
        }
        service.markDelayedTasks();
        for (int index = 0; index < deadlines.size(); index++) {
            TaskStatus expected = index == 0 && (status == TaskStatus.OPEN || status == TaskStatus.IN_PROGRESS)
                    ? TaskStatus.DELAYED : status;
            assertEquals(expected, Task.findById("task-" + index).orElseThrow().getStatus(),
                    "After restart with deadline " + deadlines.get(index));
        }
    }

    @Test
    void formPostponementAndExplicitScheduledReturnSurviveRepeatedRestartsForRecurringWork() {
        openDatabase();
        Task task = new Task("recurring", "Weekly reading", "", "Study", new TaskPriority(1),
                TODAY.minusDays(5), TaskStatus.DELAYED, 0, "1:5", TODAY.minusWeeks(2), null);
        task.save();
        service.updateTask(task, new TaskUpdate(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, TaskStatus.POSTPONED));
        closeDatabase();
        openDatabase();
        service.markDelayedTasks();
        assertEquals(TaskStatus.POSTPONED, Task.findById(task.getId()).orElseThrow().getStatus());
        service.setTaskResumeDate(task, TODAY.plusDays(3));
        closeDatabase();
        openDatabase(TODAY.plusDays(2));
        service.markDelayedTasks();
        assertEquals(TaskStatus.POSTPONED, Task.findById(task.getId()).orElseThrow().getStatus());
        assertEquals(TODAY.plusDays(3), Task.findById(task.getId()).orElseThrow().getResumeOn());
        closeDatabase();
        openDatabase(TODAY.plusDays(3));
        service.markDelayedTasks();
        Task resumed = Task.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.DELAYED, resumed.getStatus());
        assertEquals(TODAY.minusDays(5), resumed.getDeadline());
        assertEquals("1:5", resumed.getRecurringPattern());
        assertNull(resumed.getResumeOn());
    }

    @Test
    void upgradingLegacyPostponedWorkDoesNotInventAResumeDateFromItsDeadline() {
        openDatabase();
        Task task = new Task("legacy", "Postponed work", "", "Study", new TaskPriority(1),
                TODAY.minusDays(5), TaskStatus.POSTPONED, 0);
        task.save();
        jdbc.execute("ALTER TABLE tasks DROP COLUMN resume_on");
        closeDatabase();
        openDatabase();
        service.markDelayedTasks();
        assertEquals(TaskStatus.POSTPONED, Task.findById(task.getId()).orElseThrow().getStatus());
        assertNull(Task.findById(task.getId()).orElseThrow().getResumeOn());
    }
}
