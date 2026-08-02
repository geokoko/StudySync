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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                    recurrence_end_date DATE,
                    completed_at DATE,
                    remind_days_before INTEGER,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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

        CategoryService categoryService = mock(CategoryService.class);
        when(categoryService.categoryExists(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        taskService = new TaskService(categoryService, googleDriveService, dateTimeService);
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

    @Test
    void findLatestByTaskIdsReturnsMostRecentReschedulePerTask() {
        savedTask("task-5", TODAY.plusDays(1), TaskStatus.OPEN);
        jdbcTemplate.update(
                "INSERT INTO task_reschedules (id, task_id, old_deadline, new_deadline, rescheduled_at) VALUES (?, ?, ?, ?, ?)",
                "r1", "task-5", TODAY.plusDays(1), TODAY.plusDays(2), TODAY.minusDays(1).atTime(9, 0));
        jdbcTemplate.update(
                "INSERT INTO task_reschedules (id, task_id, old_deadline, new_deadline, rescheduled_at) VALUES (?, ?, ?, ?, ?)",
                "r2", "task-5", TODAY.plusDays(2), TODAY.plusDays(4), TODAY.atTime(9, 0));

        Map<String, TaskReschedule> latest =
                TaskReschedule.findLatestByTaskIds(List.of("task-5", "no-such-task"));

        assertEquals(1, latest.size());
        assertEquals(TODAY.plusDays(2), latest.get("task-5").getOldDeadline());
        assertEquals(TODAY.plusDays(4), latest.get("task-5").getNewDeadline());
    }

    @Test
    void overdueRecurringTaskGoesDelayedAndStaysVisible() {
        // Repeats every Monday from 2026-03-02, due 2026-03-25 (a Wednesday),
        // so by TODAY (Saturday 2026-03-28) it is late.
        Task task = savedRecurringTask("weekly-late", "1:1", LocalDate.of(2026, 3, 2),
                TODAY.minusDays(3), null);

        taskService.markDelayedTasks();

        Task stored = Task.findById("weekly-late").orElseThrow();
        assertEquals(TaskStatus.DELAYED, stored.getStatus());

        // The point of the fix: a DELAYED recurring task still surfaces. It
        // shows on its Monday occurrences and, being late, every day until
        // resolved rather than vanishing until next Monday.
        assertTrue(taskService.taskSurfacesOn(stored, LocalDate.of(2026, 3, 30)), "next Monday occurrence");
        assertTrue(taskService.taskSurfacesOn(stored, TODAY), "late, so visible today");
        assertTrue(taskService.taskSurfacesOn(stored, TODAY.minusDays(3)), "its deadline day");
        assertTrue(taskService.getTasksForDate(TODAY).stream().anyMatch(t -> t.getId().equals("weekly-late")));

        // Before the deadline it only appears on scheduled occurrences.
        assertTrue(taskService.taskSurfacesOn(stored, LocalDate.of(2026, 3, 23)), "an earlier Monday");
        assertFalse(taskService.taskSurfacesOn(stored, LocalDate.of(2026, 3, 24)), "a Tuesday before the deadline");

        // "Has an occurrence" stays a separate question from "is visible".
        assertTrue(taskService.isRecurringOccurrence(stored, LocalDate.of(2026, 3, 30)));
        assertFalse(taskService.isRecurringOccurrence(stored, TODAY));
    }

    @Test
    void reschedulingRecurringTaskIntoTheFutureRevivesIt() {
        Task task = savedRecurringTask("weekly-revived", "1:1", LocalDate.of(2026, 3, 2),
                TODAY.minusDays(5), null);
        taskService.markDelayedTasks();
        assertEquals(TaskStatus.DELAYED, Task.findById("weekly-revived").orElseThrow().getStatus());

        taskService.rescheduleTask(Task.findById("weekly-revived").orElseThrow(), TODAY.plusDays(7));

        Task stored = Task.findById("weekly-revived").orElseThrow();
        assertEquals(TaskStatus.OPEN, stored.getStatus());
        // No longer late, so it is back to appearing on its occurrences only.
        assertFalse(taskService.taskSurfacesOn(stored, TODAY));
        assertTrue(taskService.taskSurfacesOn(stored, LocalDate.of(2026, 3, 30)));
    }

    @Test
    void recurrenceEndStopsOccurrencesButNotAnUnresolvedDeadline() {
        Task task = savedRecurringTask("weekly-ended", "1:1", LocalDate.of(2026, 3, 2),
                TODAY.minusDays(3), TODAY.minusDays(1));

        taskService.markDelayedTasks();
        Task stored = Task.findById("weekly-ended").orElseThrow();

        // Recurrence is over, so no more occurrences...
        assertFalse(taskService.isRecurringOccurrence(stored, LocalDate.of(2026, 3, 30)));
        // ...but an unfinished, overdue task must not be hidden by that.
        assertTrue(taskService.taskSurfacesOn(stored, LocalDate.of(2026, 3, 30)));
    }

    @Test
    void editingATaskKeepsItsCreationDateAndRecurrenceAnchor() {
        savedTask("anchored", TODAY.plusDays(3), TaskStatus.OPEN);
        // A recurring task with no start date anchors its interval on createdAt,
        // so resetting the creation date on save would shift its schedule.
        jdbcTemplate.update("UPDATE tasks SET created_at = ?, recurring_pattern = '2:1' WHERE id = ?",
                LocalDate.of(2026, 1, 5).atTime(8, 0), "anchored");

        Task before = Task.findById("anchored").orElseThrow();
        assertEquals(LocalDate.of(2026, 1, 5), before.getRecurrenceAnchor());

        Task updated = taskService.updateTask(before, new TaskUpdate("Renamed", null, null, null, null));

        assertEquals(LocalDate.of(2026, 1, 5), updated.getRecurrenceAnchor(), "returned object");
        assertEquals(LocalDate.of(2026, 1, 5),
                Task.findById("anchored").orElseThrow().getRecurrenceAnchor(), "stored row");
    }

    @Test
    void remindersFireFromTheOffsetUntilTheDeadlineAndNoLater() {
        Task task = savedTask("remind-me", TODAY.plusDays(7), TaskStatus.OPEN);
        task.setRemindDaysBefore(3);
        task.save();

        Task stored = Task.findById("remind-me").orElseThrow();
        assertEquals(3, stored.getRemindDaysBefore());
        assertEquals(TODAY.plusDays(4), stored.reminderDate().orElseThrow());

        // Silent until the reminder day, then every day up to the deadline.
        assertFalse(stored.isReminderDue(TODAY.plusDays(3)));
        assertTrue(stored.isReminderDue(TODAY.plusDays(4)));
        assertTrue(stored.isReminderDue(TODAY.plusDays(7)));

        // Past the deadline it is overdue, which is surfaced separately -
        // reporting both would double-count the same task.
        assertFalse(stored.isReminderDue(TODAY.plusDays(8)));

        // The reminder is derived, so moving the deadline moves it too.
        taskService.rescheduleTask(stored, TODAY.plusDays(14));
        assertEquals(TODAY.plusDays(11),
                Task.findById("remind-me").orElseThrow().reminderDate().orElseThrow());
    }

    @Test
    void reminderNeedsADeadlineAndAnUnresolvedTask() {
        Task noDeadline = savedTask("no-deadline", null, TaskStatus.OPEN);
        noDeadline.setRemindDaysBefore(3);
        noDeadline.save();
        assertTrue(Task.findById("no-deadline").orElseThrow().reminderDate().isEmpty());

        Task noOffset = savedTask("no-offset", TODAY.plusDays(1), TaskStatus.OPEN);
        assertFalse(noOffset.isReminderDue(TODAY));

        Task done = savedTask("done", TODAY.plusDays(1), TaskStatus.OPEN);
        done.setRemindDaysBefore(5);
        done.markCompleted();
        done.save();
        assertFalse(Task.findById("done").orElseThrow().isReminderDue(TODAY));

        // Only the due ones, soonest deadline first.
        Task soon = savedTask("soon", TODAY.plusDays(1), TaskStatus.OPEN);
        soon.setRemindDaysBefore(5);
        soon.save();
        Task later = savedTask("later", TODAY.plusDays(3), TaskStatus.OPEN);
        later.setRemindDaysBefore(5);
        later.save();

        assertEquals(List.of("soon", "later"),
                taskService.getTasksWithDueReminders(TODAY).stream().map(Task::getId).toList());
    }

    @Test
    void editingATaskKeepsItsReminderOffset() {
        Task task = savedTask("keeps-reminder", TODAY.plusDays(10), TaskStatus.OPEN);
        task.setRemindDaysBefore(7);
        task.save();

        taskService.updateTask(Task.findById("keeps-reminder").orElseThrow(),
                new TaskUpdate("Renamed", null, null, null, null));

        assertEquals(7, Task.findById("keeps-reminder").orElseThrow().getRemindDaysBefore());
    }

    @Test
    void lateRecurringTaskStillDoesNotSurfaceBeforeItsStartDate() {
        // Deadline already passed, start date months away: the overdue rule
        // must not drag it back before the task is supposed to begin.
        Task task = savedRecurringTask("future-start", "1:1", TODAY.plusMonths(5),
                TODAY.minusDays(1), null);

        assertFalse(taskService.taskSurfacesOn(task, TODAY));
        assertFalse(taskService.taskSurfacesOn(task, TODAY.plusDays(30)));
        assertTrue(taskService.taskSurfacesOn(task, TODAY.plusMonths(5)));
    }

    @Test
    void postponedTasksDoNotFireReminders() {
        // A postponed task's deadline is its resume date, so counting down to
        // it would announce "due in N days" for something that is not due then
        // — and drag it into a planner that excludes postponed work.
        Task postponed = savedTask("postponed-reminder", TODAY.plusDays(2), TaskStatus.POSTPONED);
        postponed.setRemindDaysBefore(7);
        postponed.save();

        assertFalse(Task.findById("postponed-reminder").orElseThrow().isReminderDue(TODAY));
        assertTrue(taskService.getTasksWithDueReminders(TODAY).isEmpty());
    }

    @Test
    void reminderCanBeRemovedFromAnExistingTask() {
        Task task = savedTask("clearable", TODAY.plusDays(10), TaskStatus.OPEN);
        task.setRemindDaysBefore(7);
        task.save();

        // null means "leave alone", so clearing needs the explicit sentinel.
        taskService.updateTask(Task.findById("clearable").orElseThrow(),
                new TaskUpdate("Still named", null, null, null, null, null, null, null,
                        TaskUpdate.CLEAR_REMINDER));

        assertNull(Task.findById("clearable").orElseThrow().getRemindDaysBefore());
        assertFalse(Task.findById("clearable").orElseThrow().isReminderDue(TODAY.plusDays(5)));
    }

    @Test
    void addingATaskWithoutAPriorityKeepsItsReminder() {
        // The full constructor rejects a null priority, so the only way into
        // addTask's default-priority branch is the no-arg constructor.
        Task task = new Task();
        task.setId("no-priority");
        task.setTitle("Task");
        task.setCategory("Study");
        task.setDeadline(TODAY.plusDays(10));
        task.setRemindDaysBefore(7);
        assertNull(task.getPriority());

        taskService.addTask(task);

        // addTask rebuilds the task to apply a default priority; that rebuild
        // must not drop fields the constructor does not cover.
        assertEquals(7, Task.findById("no-priority").orElseThrow().getRemindDaysBefore());
    }

    private Task savedTask(final String id, final LocalDate deadline, final TaskStatus status) {
        Task task = new Task(id, "Task " + id, "", "Study", new TaskPriority(3),
                deadline, status, 0, "", null);
        return task.save();
    }

    private Task savedRecurringTask(final String id, final String pattern, final LocalDate startDate,
                                    final LocalDate deadline, final LocalDate recurrenceEnd) {
        Task task = new Task(id, "Task " + id, "", "Study", new TaskPriority(3),
                deadline, TaskStatus.OPEN, 0, pattern, startDate, recurrenceEnd);
        return task.save();
    }
}
