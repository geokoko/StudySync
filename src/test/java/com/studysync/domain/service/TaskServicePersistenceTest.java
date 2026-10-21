package com.studysync.domain.service;

import com.studysync.domain.entity.Task;
import com.studysync.domain.entity.TaskLink;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.interceptor.TransactionProxyFactoryBean;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Properties;
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
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class TaskServicePersistenceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 28);

    private HikariDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private TaskService taskService;
    private GoogleDriveService googleDriveService;

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
                    done_criteria TEXT,
                    parent_id VARCHAR(50) REFERENCES tasks(id),
                    estimated_hours INTEGER,
                    notes TEXT,
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

        jdbcTemplate.execute("""
                CREATE TABLE task_links (
                    id VARCHAR(50) PRIMARY KEY,
                    task_id VARCHAR(50) NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
                    title TEXT, url TEXT NOT NULL, position INTEGER NOT NULL
                )
                """);
        TaskLink.setJdbcTemplate(jdbcTemplate);
        Task.setJdbcTemplate(jdbcTemplate);
        TaskReschedule.setJdbcTemplate(jdbcTemplate);

        googleDriveService = mock(GoogleDriveService.class);
        when(googleDriveService.saveLocally()).thenReturn(true);

        DateTimeService dateTimeService = mock(DateTimeService.class);
        when(dateTimeService.getCurrentDate()).thenReturn(TODAY);

        CategoryService categoryService = mock(CategoryService.class);
        when(categoryService.categoryExists(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        TransactionProxyFactoryBean proxy = new TransactionProxyFactoryBean();
        proxy.setTarget(new TaskService(categoryService, googleDriveService, dateTimeService));
        proxy.setTransactionManager(new DataSourceTransactionManager(dataSource));
        Properties attributes = new Properties();
        attributes.setProperty("*", "PROPAGATION_REQUIRED");
        proxy.setTransactionAttributes(attributes);
        proxy.afterPropertiesSet();
        taskService = (TaskService) proxy.getObject();
    }

    @AfterEach
    void tearDown() {
        Task.setJdbcTemplate(null);
        TaskLink.setJdbcTemplate(null);
        TaskReschedule.setJdbcTemplate(null);
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Test
    void hierarchyRejectsSelfGrandchildrenAndMovingAParent() {
        Task root = savedTask("root", null, TaskStatus.OPEN);
        Task child = subtask("child", root);
        assertThrows(ValidationException.class, () -> subtask("grandchild", child));
        assertThrows(ValidationException.class, () -> taskService.updateTask(root, details(root.getId(), null, null, null, null)));
        Task other = savedTask("other", null, TaskStatus.OPEN);
        assertThrows(ValidationException.class, () -> taskService.updateTask(root, details(other.getId(), null, null, null, null)));
        assertThrows(ValidationException.class, () -> taskService.updateTask(child, details("missing", null, null, null, null)));
        assertEquals(root.getId(), Task.findById(child.getId()).orElseThrow().getParentId());
    }

    @Test
    void recurringTasksCanBeParentsAndChildrenAndBecomeRecurringInAFamily() {
        Task root = savedTask("root", null, TaskStatus.OPEN);
        Task child = subtask("child", root);
        taskService.updateTask(root, new TaskUpdate(null, null, null, null, null, "1:1", TODAY.minusWeeks(2), null));
        assertFalse(Task.findById(child.getId()).orElseThrow().isRecurring());
        taskService.updateTask(child, new TaskUpdate(null, null, null, null, null, "2:3"));
        Task other = savedTask("other", null, TaskStatus.OPEN);
        taskService.updateTask(other, new TaskUpdate(null, null, null, null, null, "1:4", TODAY.minusWeeks(2), null));
        taskService.updateTask(other, details(root.getId(), null, null, null, null));
        assertEquals(root.getId(), Task.findById(other.getId()).orElseThrow().getParentId());
        assertEquals("1:4", Task.findById(other.getId()).orElseThrow().getRecurringPattern());
        assertEquals("2:3", Task.findById(child.getId()).orElseThrow().getRecurringPattern());
        assertEquals("1:1", Task.findById(root.getId()).orElseThrow().getRecurringPattern());
        assertThrows(ValidationException.class, () -> subtask("grandchild", child));
        taskService.updateTaskStatus(child, TaskStatus.COMPLETED);
        taskService.updateTaskStatus(other, TaskStatus.COMPLETED);
        assertEquals(TaskStatus.OPEN, Task.findById(root.getId()).orElseThrow().getStatus());
        assertNull(Task.findById(root.getId()).orElseThrow().getCompletedAt());
        assertEquals(100, Task.progress(Task.findSubtasks(root.getId())).percentage());
        taskService.updateTaskStatus(other, TaskStatus.OPEN);
        assertTrue(taskService.taskSurfacesOn(Task.findById(root.getId()).orElseThrow(), TODAY.plusDays(2)));
        assertTrue(taskService.taskSurfacesOn(Task.findById(other.getId()).orElseThrow(), TODAY.plusDays(5)));
        taskService.updateTaskStatus(root, TaskStatus.COMPLETED);
        assertEquals(TODAY, Task.findById(other.getId()).orElseThrow().getCompletedAt());
        assertFalse(taskService.taskSurfacesOn(Task.findById(root.getId()).orElseThrow(), TODAY.plusDays(2)));
        assertFalse(taskService.taskSurfacesOn(Task.findById(other.getId()).orElseThrow(), TODAY.plusDays(5)));
    }

    @Test
    void creatingAndMovingMixedFamiliesDoesNotInheritRecurrence() {
        Task recurringParent = new Task("recurring-parent", "Weekly", "", "Study", new TaskPriority(1),
                null, TaskStatus.OPEN, 0, "1:1", TODAY.minusWeeks(2), TODAY.plusMonths(1));
        taskService.addTask(recurringParent);
        Task oneOffChild = subtask("one-off-child", recurringParent);
        assertFalse(oneOffChild.isRecurring());
        assertNull(oneOffChild.getStartDate());
        assertNull(oneOffChild.getRecurrenceEndDate());

        Task oneOffParent = savedTask("one-off-parent", null, TaskStatus.OPEN);
        Task recurringChild = new Task("recurring-child", "Fortnightly", "", "Study", new TaskPriority(1),
                null, TaskStatus.OPEN, 0, "2:4", TODAY, TODAY.plusMonths(2));
        recurringChild.setParentId(oneOffParent.getId());
        taskService.addTask(recurringChild);
        assertFalse(Task.findById(oneOffParent.getId()).orElseThrow().isRecurring());
        taskService.updateTask(recurringChild, details(recurringParent.getId(), null, null, null, null));
        Task moved = Task.findById(recurringChild.getId()).orElseThrow();
        assertEquals("2:4", moved.getRecurringPattern());
        assertEquals(TODAY, moved.getStartDate());
        assertEquals(TODAY.plusMonths(2), moved.getRecurrenceEndDate());
        assertEquals("1:1", Task.findById(recurringParent.getId()).orElseThrow().getRecurringPattern());
    }

    @Test
    void bothManualCompletionPathsCompleteEveryUnfinishedStatusAndPreserveResolvedChildren() {
        for (boolean throughForm : List.of(false, true)) {
            String prefix = throughForm ? "form-" : "card-";
            Task root = savedTask(prefix + "parent", null, TaskStatus.OPEN);
            List<TaskStatus> unfinished = List.of(TaskStatus.OPEN, TaskStatus.IN_PROGRESS, TaskStatus.DELAYED, TaskStatus.POSTPONED);
            for (TaskStatus status : unfinished) {
                Task child = subtask(prefix + status.name(), root);
                taskService.updateTaskStatus(child, status);
                if (status == TaskStatus.POSTPONED) {
                    taskService.updateTask(child, new TaskUpdate(null, null, null, null, null, "1:1", TODAY, null));
                }
            }
            Task done = subtask(prefix + "done", root);
            done.setRecurringPattern("1:1");
            done.updateStatus(TaskStatus.COMPLETED);
            done.setCompletedAt(TODAY.minusDays(2));
            done.save();
            Task cancelled = subtask(prefix + "cancelled", root);
            cancelled.setRecurringPattern("1:1");
            cancelled.save();
            taskService.updateTaskStatus(cancelled, TaskStatus.CANCELLED);
            assertEquals(new TaskService.CompletionPreview(4, 1), taskService.previewTaskCompletion(root));
            if (throughForm) {
                taskService.updateTask(root, details(null, null, "Saved with completion", null, TaskStatus.COMPLETED));
            } else {
                taskService.updateTaskStatus(root, TaskStatus.COMPLETED);
            }
            assertEquals(TODAY, Task.findById(root.getId()).orElseThrow().getCompletedAt());
            for (TaskStatus status : unfinished) {
                Task child = Task.findById(prefix + status.name()).orElseThrow();
                assertEquals(TaskStatus.COMPLETED, child.getStatus());
                assertEquals(TODAY, child.getCompletedAt());
                assertFalse(taskService.taskSurfacesOn(child, TODAY.plusDays(2)));
            }
            assertEquals("1:1", Task.findById(prefix + TaskStatus.POSTPONED.name()).orElseThrow().getRecurringPattern());
            assertEquals(TODAY.minusDays(2), Task.findById(done.getId()).orElseThrow().getCompletedAt());
            assertEquals(TaskStatus.CANCELLED, Task.findById(cancelled.getId()).orElseThrow().getStatus());
            assertNull(Task.findById(cancelled.getId()).orElseThrow().getCompletedAt());
            assertEquals(new TaskService.CompletionPreview(0, 0), taskService.previewTaskCompletion(root));
        }
    }

    @Test
    void lastCompletionCompletesParentOnSameDateAndReopeningClearsIt() {
        Task root = savedTask("root", null, TaskStatus.OPEN);
        Task first = subtask("first", root);
        Task last = subtask("last", root);
        Task cancelled = subtask("cancelled", root);
        taskService.updateTaskStatus(cancelled, TaskStatus.CANCELLED);
        taskService.updateTaskStatus(first, TaskStatus.COMPLETED);
        assertEquals(TaskStatus.OPEN, Task.findById(root.getId()).orElseThrow().getStatus());
        assertEquals(new Task.Progress(1, 2), Task.progress(Task.findSubtasks(root.getId())));
        assertEquals(50, Task.progress(Task.findSubtasks(root.getId())).percentage());
        taskService.updateTaskStatus(last, TaskStatus.COMPLETED);
        assertEquals(TODAY, Task.findById(root.getId()).orElseThrow().getCompletedAt());
        assertEquals(TODAY, Task.findById(last.getId()).orElseThrow().getCompletedAt());
        assertEquals(100, Task.progress(Task.findSubtasks(root.getId())).percentage());
        taskService.updateTaskStatus(root, TaskStatus.POSTPONED);
        taskService.updateTask(first, details(null, null, "An annotation is not a completion event", null, null));
        assertEquals(TaskStatus.POSTPONED, Task.findById(root.getId()).orElseThrow().getStatus());
        taskService.updateTaskStatus(root, TaskStatus.COMPLETED);
        taskService.updateTaskStatus(first, TaskStatus.OPEN);
        assertEquals(TaskStatus.OPEN, Task.findById(root.getId()).orElseThrow().getStatus());
        assertNull(Task.findById(root.getId()).orElseThrow().getCompletedAt());
    }

    @Test
    void addingWorkReopensCompletedParentAndCancellationDoesNotCreateEmptyProgress() {
        Task root = savedTask("root", null, TaskStatus.OPEN);
        taskService.updateTaskStatus(root, TaskStatus.COMPLETED);
        Task child = subtask("child", root);
        assertEquals(TaskStatus.OPEN, Task.findById(root.getId()).orElseThrow().getStatus());
        taskService.updateTaskStatus(child, TaskStatus.CANCELLED);
        assertEquals(new Task.Progress(0, 0), Task.progress(Task.findSubtasks(root.getId())));
        assertEquals(0, Task.progress(Task.findSubtasks(root.getId())).percentage());
        taskService.updateTaskStatus(root, TaskStatus.COMPLETED);
        assertEquals(TaskStatus.CANCELLED, Task.findById(child.getId()).orElseThrow().getStatus());
    }

    @Test
    void completionAndDirtyTrackingWaitForTheSameCommit() {
        Task root = savedTask("root", null, TaskStatus.OPEN);
        Task child = subtask("child", root);
        clearInvocations(googleDriveService);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.executeWithoutResult(status -> {
            taskService.updateTaskStatus(root, TaskStatus.COMPLETED);
            assertEquals(TaskStatus.COMPLETED, Task.findById(root.getId()).orElseThrow().getStatus());
            verify(googleDriveService, never()).markLocalDbDirty();
            status.setRollbackOnly();
        });
        assertEquals(TaskStatus.OPEN, Task.findById(root.getId()).orElseThrow().getStatus());
        assertEquals(TaskStatus.OPEN, Task.findById(child.getId()).orElseThrow().getStatus());
        verify(googleDriveService, never()).markLocalDbDirty();
        taskService.updateTaskStatus(root, TaskStatus.COMPLETED);
        verify(googleDriveService).markLocalDbDirty();
    }

    @Test
    void parentDeletionChecksCountThenDeletesChildrenWithNormalDependentRowSemantics() {
        Task root = savedTask("root", null, TaskStatus.OPEN);
        Task child = subtask("child", root);
        Task cancelled = subtask("cancelled", root);
        taskService.updateTaskStatus(cancelled, TaskStatus.CANCELLED);
        createDeletionDependencies(List.of(root, child, cancelled));
        assertEquals(new TaskService.DeletionPreview(3, 2), taskService.previewTaskDeletion(List.of(root.getId())));
        assertThrows(ValidationException.class, () -> taskService.removeTask(root, 1));
        assertEquals(3, Task.findAll().size());
        taskService.removeTask(root, 2);
        assertTrue(Task.findAll().isEmpty());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task_links", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task_reschedules", Integer.class));
        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM study_goals WHERE task_id IS NULL", Integer.class));
        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM study_goal_attempts", Integer.class));
        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM study_sessions WHERE task_id IS NULL AND goal_id IS NOT NULL", Integer.class));
    }

    @Test
    void bulkDeletionDeduplicatesParentsAndChildrenAndChecksConfirmedCount() {
        Task root = savedTask("root", null, TaskStatus.OPEN);
        Task child = subtask("child", root);
        subtask("other-child", root);
        Task separate = savedTask("separate", null, TaskStatus.OPEN);
        List<String> ids = List.of(child.getId(), root.getId(), root.getId(), separate.getId());
        assertEquals(new TaskService.DeletionPreview(4, 2), taskService.previewTaskDeletion(ids));
        assertThrows(ValidationException.class, () -> taskService.batchDeleteTasks(ids, 1));
        assertEquals(4, Task.findAll().size());
        clearInvocations(googleDriveService);
        assertEquals(4, taskService.batchDeleteTasks(ids, 2));
        verify(googleDriveService).markLocalDbDirty();
        assertTrue(Task.findAll().isEmpty());
        Task next = savedTask("next", null, TaskStatus.OPEN);
        subtask("next-child", next);
        assertEquals(2, taskService.batchDeleteTasks(List.of(next.getId())));
    }

    @Test
    void failedParentDeletionRollsBackChildrenLinksAndGoalUnlinking() {
        Task root = savedTask("root", null, TaskStatus.OPEN);
        Task child = subtask("child", root);
        createDeletionDependencies(List.of(root, child));
        jdbcTemplate.execute("CREATE TABLE deletion_guard (task_id VARCHAR(50) REFERENCES tasks(id))");
        jdbcTemplate.update("INSERT INTO deletion_guard VALUES (?)", root.getId());
        clearInvocations(googleDriveService);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> taskService.removeTask(root, 1));
        assertEquals(2, Task.findAll().size());
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task_links", Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task_reschedules", Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM study_goals WHERE task_id IS NOT NULL", Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM study_goal_attempts", Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM study_sessions WHERE task_id IS NOT NULL AND goal_id IS NOT NULL", Integer.class));
        verify(googleDriveService, never()).markLocalDbDirty();
    }

    private void createDeletionDependencies(List<Task> tasks) {
        // Mirror schema.sql: goals/sessions survive, their task references become NULL,
        // while bookmarks and reschedule history belong to the deleted task.
        jdbcTemplate.execute("CREATE TABLE study_goals (id VARCHAR(50) PRIMARY KEY, task_id VARCHAR(50) REFERENCES tasks(id) ON DELETE SET NULL)");
        jdbcTemplate.execute("CREATE TABLE study_goal_attempts (id VARCHAR(50) PRIMARY KEY, goal_id VARCHAR(50) REFERENCES study_goals(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE TABLE study_sessions (id VARCHAR(50) PRIMARY KEY, task_id VARCHAR(50) REFERENCES tasks(id) ON DELETE SET NULL, goal_id VARCHAR(50) REFERENCES study_goals(id) ON DELETE SET NULL)");
        for (Task task : tasks) {
            taskService.updateTask(task, details(null, null, "Notes", List.of(new TaskLink("Doc", "https://example.com")), null));
            new TaskReschedule(task.getId(), TODAY, TODAY.plusDays(1)).save();
            jdbcTemplate.update("INSERT INTO study_goals VALUES (?, ?)", task.getId(), task.getId());
            jdbcTemplate.update("INSERT INTO study_goal_attempts VALUES (?, ?)", task.getId(), task.getId());
            jdbcTemplate.update("INSERT INTO study_sessions VALUES (?, ?, ?)", task.getId(), task.getId(), task.getId());
        }
    }

    @Test
    void estimatesSumNonCancelledChildrenWithoutReplacingTheParentsBudget() {
        Task parent = savedTask("parent", null, TaskStatus.OPEN);
        taskService.updateTask(parent, details(null, 40, null, null, null));
        Task first = subtask("first", parent);
        Task second = subtask("second", parent);
        Task cancelled = subtask("cancelled", parent);
        assertEquals(new TaskService.TaskEstimates(40, null), taskService.getTaskEstimates(parent));
        taskService.updateTask(first, details(null, 5, null, null, null));
        taskService.updateTask(second, details(null, 7, null, null, TaskStatus.COMPLETED));
        taskService.updateTask(cancelled, details(null, 100, null, null, TaskStatus.CANCELLED));
        assertEquals(new TaskService.TaskEstimates(40, 12L), taskService.getTaskEstimates(parent));
        assertEquals(40, Task.findById(parent.getId()).orElseThrow().getEstimatedHours());
        taskService.updateTask(parent, details(null, TaskUpdate.CLEAR_ESTIMATE, null, null, null));
        assertEquals(new TaskService.TaskEstimates(null, 12L), taskService.getTaskEstimates(parent));
        taskService.updateTask(first, details(null, TaskUpdate.CLEAR_ESTIMATE, null, null, null));
        taskService.updateTask(second, details(null, 0, null, null, null));
        assertEquals(new TaskService.TaskEstimates(null, 0L), taskService.getTaskEstimates(parent));
        taskService.updateTask(second, details(null, TaskUpdate.CLEAR_ESTIMATE, null, null, null));
        assertEquals(new TaskService.TaskEstimates(null, null), taskService.getTaskEstimates(parent));
        taskService.updateTask(first, details(null, Integer.MAX_VALUE, null, null, null));
        taskService.updateTask(second, details(null, Integer.MAX_VALUE, null, null, null));
        assertEquals(2L * Integer.MAX_VALUE, taskService.getTaskEstimates(parent).subtaskHours());
        Task other = savedTask("other", null, TaskStatus.OPEN);
        taskService.updateTask(first, details(other.getId(), null, null, null, null));
        assertEquals((long) Integer.MAX_VALUE, taskService.getTaskEstimates(parent).subtaskHours());
        assertEquals((long) Integer.MAX_VALUE, taskService.getTaskEstimates(other).subtaskHours());
    }

    @Test
    void notesEstimatesAndOrderedLinksCreateReadUpdateClearAndCascadeDelete() {
        Task task = new Task("notes", "Task", "", "Study", new TaskPriority(1), null, TaskStatus.OPEN, 0);
        String markdown = "# Notes\n\n  Indented  \n**Greek: ελληνικά**\n";
        task.setNotes(markdown);
        task.setEstimatedHours(12);
        List<TaskLink> links = List.of(new TaskLink(null, "https://example.com/doc"), new TaskLink("Sheet", "http://example.org"));
        taskService.addTask(task, links);
        assertEquals(markdown, Task.findById(task.getId()).orElseThrow().getNotes());
        assertEquals(12, Task.findById(task.getId()).orElseThrow().getEstimatedHours());
        assertEquals(links, taskService.getTaskLinks(task));
        taskService.updateTask(task, details(null, 0, "Changed", List.of(links.get(1), links.get(0)), null));
        assertEquals(List.of(links.get(1), links.get(0)), taskService.getTaskLinks(task));
        assertEquals(0, Task.findById(task.getId()).orElseThrow().getEstimatedHours());
        taskService.updateTask(task, new TaskUpdate("Renamed using stale object", null, null, null, null));
        assertEquals("Changed", Task.findById(task.getId()).orElseThrow().getNotes());
        taskService.updateTask(task, details(null, TaskUpdate.CLEAR_ESTIMATE, "", List.of(), null));
        assertNull(Task.findById(task.getId()).orElseThrow().getEstimatedHours());
        assertEquals("", Task.findById(task.getId()).orElseThrow().getNotes());
        assertTrue(taskService.getTaskLinks(task).isEmpty());
        taskService.updateTask(task, details(null, null, null, links, null));
        taskService.removeTask(task);
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task_links", Integer.class));
    }

    @Test
    void emptyLinkRowsAreIgnoredButATitleStillNeedsAUrl() {
        Task task = savedTask("task", null, TaskStatus.OPEN);
        TaskLink doc = new TaskLink("Doc", "https://example.com/doc");
        taskService.updateTask(task, details(null, null, null,
                List.of(new TaskLink(null, ""), doc, new TaskLink(" ", " ")), null));
        assertEquals(List.of(doc), taskService.getTaskLinks(task));
        assertThrows(ValidationException.class, () -> taskService.updateTask(task,
                details(null, null, null, List.of(new TaskLink("Sheet", "")), null)));
        assertEquals(List.of(doc), taskService.getTaskLinks(task));
    }

    @Test
    void invalidUrlsAndEstimatesLeaveTheWholeFormUnchanged() {
        Task task = savedTask("task", null, TaskStatus.OPEN);
        List<TaskLink> original = List.of(new TaskLink("Valid", "https://example.com"));
        taskService.updateTask(task, details(null, 5, "Original", original, null));
        for (String url : List.of("", "file:///tmp/x", "javascript:alert(1)", "mailto:a@b.com", "ftp://example.com", "//example.com", "https:", "https://", "https://exa mple.com")) {
            assertThrows(ValidationException.class, () -> taskService.updateTask(task,
                    details(null, 20, "Do not save", List.of(new TaskLink("Doc", url)), TaskStatus.COMPLETED)), url);
        }
        assertThrows(ValidationException.class, () -> taskService.updateTask(task, details(null, -2, null, null, null)));
        assertEquals("Original", Task.findById(task.getId()).orElseThrow().getNotes());
        assertEquals(TaskStatus.OPEN, Task.findById(task.getId()).orElseThrow().getStatus());
        assertEquals(original, taskService.getTaskLinks(task));
        assertEquals("HTTPS://example.com", TaskService.validateLinkUrl(" HTTPS://example.com "));
    }

    @Test
    void movingUnfinishedWorkReconcilesBothParentsAndKeepsTheChild() {
        Task oldParent = savedTask("old", null, TaskStatus.OPEN);
        Task done = subtask("done", oldParent);
        Task moving = subtask("moving", oldParent);
        taskService.updateTaskStatus(done, TaskStatus.COMPLETED);
        Task newParent = savedTask("new", TODAY.minusDays(2), TaskStatus.COMPLETED);
        taskService.updateTask(moving, details(newParent.getId(), null, null, null, null));
        assertEquals(TaskStatus.COMPLETED, Task.findById(oldParent.getId()).orElseThrow().getStatus());
        assertEquals(TODAY, Task.findById(oldParent.getId()).orElseThrow().getCompletedAt());
        assertEquals(TaskStatus.DELAYED, Task.findById(newParent.getId()).orElseThrow().getStatus());
        assertNull(Task.findById(newParent.getId()).orElseThrow().getCompletedAt());
        assertEquals(newParent.getId(), Task.findById(moving.getId()).orElseThrow().getParentId());
    }

    @Test
    void linkWriteFailureRollsBackNewChildParentReopeningAndDirtyFlag() {
        Task root = savedTask("root", null, TaskStatus.OPEN);
        taskService.updateTaskStatus(root, TaskStatus.COMPLETED);
        clearInvocations(googleDriveService);
        jdbcTemplate.execute("ALTER TABLE task_links ADD CONSTRAINT reject_probe CHECK (url <> 'https://reject.example')");
        Task child = new Task("child", "Child", "", "Study", new TaskPriority(1), null, TaskStatus.OPEN, 0);
        child.setParentId(root.getId());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> taskService.addTask(child,
                List.of(new TaskLink(null, "https://example.com"), new TaskLink(null, "https://reject.example"))));
        assertFalse(Task.existsById(child.getId()));
        assertEquals(TaskStatus.COMPLETED, Task.findById(root.getId()).orElseThrow().getStatus());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task_links", Integer.class));
        verify(googleDriveService, never()).markLocalDbDirty();
    }

    private Task subtask(String id, Task parent) {
        Task child = new Task(id, id, "", "Study", new TaskPriority(1), null, TaskStatus.OPEN, 0);
        child.setParentId(parent.getId());
        return taskService.addTask(child);
    }

    private TaskUpdate details(String parent, Integer hours, String notes, List<TaskLink> links, TaskStatus status) {
        return new TaskUpdate(null, null, null, null, null, null, null, null, null, null,
                parent, hours, notes, links, status);
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
    void doneCriteriaRoundTripsNullKeepsAndBlankClears() {
        Task task = new Task(null, "Write thesis intro", "desc", "Study", new TaskPriority(3),
                TODAY.plusDays(7), TaskStatus.OPEN, 0, null, null, null);
        task.setDoneCriteria("  Supervisor has signed off  ");
        Task saved = taskService.addTask(task);
        assertEquals("Supervisor has signed off", Task.findById(saved.getId()).orElseThrow().getDoneCriteria());

        taskService.updateTask(saved, new TaskUpdate("Write thesis intro", null, null, null, null));
        assertEquals("Supervisor has signed off", Task.findById(saved.getId()).orElseThrow().getDoneCriteria());

        taskService.updateTask(saved, new TaskUpdate(null, null, null, null, null, null, null, null, null, ""));
        assertNull(Task.findById(saved.getId()).orElseThrow().getDoneCriteria());
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
                        TaskUpdate.CLEAR_REMINDER, null));

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
