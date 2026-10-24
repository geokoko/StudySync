package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.Task;
import com.studysync.domain.service.MissedOccurrence;
import com.studysync.domain.service.TaskService;
import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.domain.valueobject.TaskStatus;
import com.studysync.presentation.ui.components.PlannerAgenda.Entry;
import com.studysync.presentation.ui.components.PlannerAgenda.Kind;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlannerAgendaTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
    private final TaskService recurrence = new TaskService(null, null, null);

    @Test
    void overdueDueAndUndatedTasksQualifyButLaterDeadlinesDoNot() {
        Task late = task("late", TODAY.minusDays(3));
        Task due = task("due", TODAY);
        Task open = task("open", null);
        Task later = task("later", TODAY.plusDays(1));
        List<Entry> entries = build(TODAY, List.of(later, open, due, late), List.of(), List.of(), List.of());
        assertEquals(List.of(Kind.OVERDUE, Kind.DUE, Kind.OPEN), kinds(entries));
        assertEquals(List.of("3d late", "due today", "no deadline"),
                entries.stream().map(Entry::reason).toList());
    }

    @Test
    void completedCancelledAndPostponedTasksDoNotQualify() {
        for (TaskStatus status : List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED, TaskStatus.POSTPONED)) {
            Task task = task("closed", TODAY.minusDays(1));
            task.setStatus(status);
            assertTrue(build(TODAY, List.of(task), List.of(), List.of(), List.of()).isEmpty());
        }
    }

    @Test
    void pendingAchievedAndMissedAttemptsKeepTheirActiveTaskAvailable() {
        Task task = task("due", TODAY);
        for (StudyGoal.AttemptOutcome outcome : List.of(StudyGoal.AttemptOutcome.PENDING,
                StudyGoal.AttemptOutcome.ACHIEVED, StudyGoal.AttemptOutcome.MISSED)) {
            StudyGoal attempt = goal("plan", task, TODAY);
            if (outcome == StudyGoal.AttemptOutcome.ACHIEVED) {
                attempt.setAchieved(true);
            }
            if (outcome == StudyGoal.AttemptOutcome.MISSED) {
                attempt.setFailed(true);
            }
            List<Entry> entries = build(TODAY, List.of(task), List.of(attempt), List.of(), List.of());
            assertEquals(1, entries.size());
            assertEquals(task, entries.getFirst().task());
            assertEquals("due today · work planned", entries.getFirst().reason());
        }
    }

    @Test
    void aGoalOnAnotherDateOrWithoutATaskDoesNotCoverTheTask() {
        Task task = task("due", TODAY);
        List<StudyGoal> attempts = List.of(goal("earlier", task, TODAY.minusDays(1)),
                goal("later", task, TODAY.plusDays(1)), goal("unlinked", null, TODAY));
        assertEquals(List.of(Kind.DUE), kinds(build(TODAY, List.of(task), attempts, List.of(), List.of())));
    }

    @Test
    void droppedGoalsDoNotMarkTheirTasksAsPlanned() {
        Task task = task("due", TODAY);
        StudyGoal dropped = mock(StudyGoal.class);
        when(dropped.getTaskId()).thenReturn(task.getId());
        when(dropped.getDate()).thenReturn(TODAY);
        when(dropped.isDropped()).thenReturn(true);
        assertEquals("due today", build(TODAY, List.of(task), List.of(dropped), List.of(), List.of())
                .getFirst().reason());
        task.setDeadline(TODAY.plusDays(5));
        assertTrue(build(TODAY, List.of(task), List.of(dropped), List.of(), List.of()).isEmpty());
    }

    @Test
    void overdueWorkUsesTheSelectedDateOnPastAndFutureDays() {
        Task task = task("late", TODAY.minusDays(5));
        Task undated = task("undated", null);
        assertEquals("3d late", build(TODAY.minusDays(2), List.of(task, undated),
                List.of(), List.of(), List.of()).getFirst().reason());
        assertEquals("7d late", build(TODAY.plusDays(2), List.of(task, undated),
                List.of(), List.of(), List.of()).getFirst().reason());
    }

    @Test
    void remindersAppearOnlyDuringTheirWindowAndOnlyToday() {
        Task soon = task("soon", TODAY.plusDays(3));
        soon.setRemindDaysBefore(3);
        Task later = task("later", TODAY.plusDays(4));
        later.setRemindDaysBefore(3);
        assertEquals(List.of(Kind.REMINDER), kinds(build(TODAY, List.of(later, soon),
                List.of(), List.of(), List.of())));
        assertTrue(build(TODAY.minusDays(1), List.of(soon), List.of(), List.of(), List.of()).isEmpty());
        assertTrue(build(TODAY.plusDays(1), List.of(soon), List.of(), List.of(), List.of()).isEmpty());
        assertEquals(List.of(Kind.DUE), kinds(build(soon.getDeadline(), List.of(soon),
                List.of(), List.of(), List.of())));
    }

    @Test
    void remindersDoNotDuplicateDueWorkAndStayAvailableAfterPlanning() {
        Task due = task("due", TODAY);
        due.setRemindDaysBefore(3);
        assertEquals("due today", build(TODAY, List.of(due), List.of(), List.of(), List.of()).getFirst().reason());
        Task soon = task("soon", TODAY.plusDays(2));
        soon.setRemindDaysBefore(3);
        List<Entry> entries = build(TODAY, List.of(soon), List.of(goal("planned", soon, TODAY)),
                List.of(), List.of());
        assertEquals(List.of(Kind.REMINDER), kinds(entries));
        assertEquals("due Thu, Oct 22 · work planned", entries.getFirst().reason());
    }

    @Test
    void carryForwardIncludesOnlyYesterdayAndOnlyOnToday() {
        Task yesterday = recurring("yesterday", "1:1");
        Task older = recurring("older", "1:7");
        List<MissedOccurrence> missed = List.of(new MissedOccurrence(yesterday, TODAY.minusDays(1)),
                new MissedOccurrence(older, TODAY.minusDays(2)));
        List<Entry> entries = build(TODAY, List.of(yesterday, older), List.of(), List.of(), missed);
        assertEquals(List.of(Kind.MISSED), kinds(entries));
        assertEquals("missed Mon", entries.getFirst().reason());
        assertTrue(build(TODAY.plusDays(1), List.of(yesterday, older), List.of(), List.of(), missed).isEmpty());
        assertTrue(build(TODAY.minusDays(2), List.of(yesterday), List.of(), List.of(), missed).isEmpty());
    }

    @Test
    void handledOccurrencesDropOutButPlannedCarryForwardStaysAvailable() {
        Task recurring = recurring("weekly", "1:1");
        // The domain query omits occurrences handled by an achieved attempt, including a later retry.
        assertTrue(build(TODAY, List.of(recurring), List.of(), List.of(), List.of()).isEmpty());
        List<Entry> entries = build(TODAY, List.of(recurring), List.of(goal("catch up", recurring, TODAY)),
                List.of(), List.of(new MissedOccurrence(recurring, TODAY.minusDays(1))));
        assertEquals(List.of(Kind.MISSED), kinds(entries));
        assertEquals("missed Mon · work planned", entries.getFirst().reason());
    }

    @Test
    void allReasonsForATaskMergeAndEveryRetryRemainsAvailable() {
        Task task = recurring("daily", "1:1,2");
        task.setDeadline(TODAY.minusDays(3));
        StudyGoal first = missed("first", task, TODAY.minusDays(2));
        StudyGoal second = missed("second", task, TODAY.minusDays(1));
        List<Entry> entries = build(TODAY, List.of(task, task), List.of(), List.of(second, first),
                List.of(new MissedOccurrence(task, TODAY.minusDays(1))));
        assertEquals(1, entries.size());
        Entry entry = entries.getFirst();
        assertEquals(Kind.OVERDUE, entry.kind());
        assertEquals("3d late · missed Mon · 2 retries · scheduled Tue", entry.reason());
        assertEquals(List.of(first, second), entry.retries());
    }

    @Test
    void missedGoalsAreRetrySuggestionsOnlyWhileTheirTaskIsActive() {
        Task task = task("task", TODAY.plusDays(5));
        StudyGoal retry = missed("retry", task, TODAY.minusDays(1));
        assertEquals(List.of(Kind.RETRY), kinds(build(TODAY, List.of(task),
                List.of(), List.of(retry), List.of())));
        for (TaskStatus closed : List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED, TaskStatus.POSTPONED)) {
            task.setStatus(closed);
            assertTrue(build(TODAY, List.of(task), List.of(), List.of(retry), List.of()).isEmpty(), closed.name());
        }
        task.setStatus(TaskStatus.OPEN);
        retry.setAchieved(true);
        assertTrue(build(TODAY, List.of(task), List.of(), List.of(retry), List.of()).isEmpty());
    }

    @Test
    void unlinkedRetriesRemainDistinctAndAreTodayOnly() {
        StudyGoal first = missed("first", null, TODAY.minusDays(2));
        StudyGoal second = missed("second", null, TODAY.minusDays(1));
        List<Entry> entries = build(TODAY, List.of(), List.of(), List.of(second, first), List.of());
        assertEquals(List.of("first", "second"), entries.stream().map(Entry::title).toList());
        assertNull(entries.getFirst().task());
        assertTrue(build(TODAY.plusDays(1), List.of(), List.of(), List.of(first), List.of()).isEmpty());
        assertTrue(build(TODAY.minusDays(1), List.of(), List.of(), List.of(first), List.of()).isEmpty());
    }

    @Test
    void pendingOrNotYetMissedGoalsAreNotRetrySuggestions() {
        StudyGoal pending = goal("pending", null, TODAY.minusDays(1));
        StudyGoal today = missed("today", null, TODAY);
        StudyGoal future = missed("future", null, TODAY.plusDays(1));
        assertTrue(build(TODAY, List.of(), List.of(), List.of(pending, today, future), List.of()).isEmpty());
        StudyGoal old = missed("old", null, TODAY.minusDays(2));
        StudyGoal replanned = new StudyGoal(old.getId(), TODAY, old.getDescription(), false, null);
        assertTrue(build(TODAY, List.of(), List.of(replanned), List.of(old), List.of()).isEmpty());
    }

    @Test
    void abandonedGoalsAndResolvedRecurringTasksDoNotReturnAsRetriesOrCarryForward() {
        StudyGoal abandoned = mock(StudyGoal.class);
        when(abandoned.getStatus()).thenReturn(StudyGoal.GoalStatus.ABANDONED);
        Task done = recurring("done", "1:1,2");
        done.setStatus(TaskStatus.COMPLETED);
        assertTrue(build(TODAY, List.of(done), List.of(), List.of(abandoned),
                List.of(new MissedOccurrence(done, TODAY.minusDays(1)))).isEmpty());
    }

    @Test
    void aPlannedTaskKeepsItsOtherReasonsAndRetries() {
        Task task = recurring("daily", "1:1,2");
        task.setDeadline(TODAY.minusDays(2));
        StudyGoal retry = missed("retry", task, TODAY.minusDays(1));
        List<Entry> entries = build(TODAY, List.of(task), List.of(goal("plan", task, TODAY)),
                List.of(retry), List.of(new MissedOccurrence(task, TODAY.minusDays(1))));
        assertEquals(1, entries.size());
        assertEquals("2d late · missed Mon · retry · scheduled Tue · work planned", entries.getFirst().reason());
        assertEquals(List.of(retry), entries.getFirst().retries());
    }

    @Test
    void planningOneRetryKeepsTheOtherRetryAndThenTheTaskAvailable() {
        Task task = task("later", TODAY.plusDays(5));
        StudyGoal first = missed("first", task, TODAY.minusDays(2));
        StudyGoal second = missed("second", task, TODAY.minusDays(1));
        StudyGoal firstRetry = goal("first", task, TODAY);
        firstRetry.setId(first.getId());
        StudyGoal secondRetry = goal("second", task, TODAY);
        secondRetry.setId(second.getId());

        List<Entry> entries = build(TODAY, List.of(task), List.of(firstRetry),
                List.of(first, second), List.of());
        assertEquals(1, entries.size());
        assertEquals(List.of(second), entries.getFirst().retries());

        entries = build(TODAY, List.of(task), List.of(firstRetry, secondRetry), List.of(), List.of());
        assertEquals(1, entries.size());
        assertEquals(task, entries.getFirst().task());
        assertTrue(entries.getFirst().retries().isEmpty());
        assertEquals("work planned", entries.getFirst().reason());
    }

    @Test
    void plannedWorkStaysAvailableOnlyOnItsDayAndWhileItsTaskIsActive() {
        Task task = task("later", TODAY.plusDays(5));
        StudyGoal attempt = goal("plan", task, TODAY.plusDays(1));
        List<Entry> entries = build(attempt.getDate(), List.of(task), List.of(attempt), List.of(), List.of());
        assertEquals(1, entries.size());
        assertEquals(task, entries.getFirst().task());
        assertEquals("work planned", entries.getFirst().reason());
        assertTrue(build(TODAY, List.of(task), List.of(attempt), List.of(), List.of()).isEmpty());
        for (TaskStatus status : List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED, TaskStatus.POSTPONED)) {
            task.setStatus(status);
            assertTrue(build(attempt.getDate(), List.of(task), List.of(attempt), List.of(), List.of()).isEmpty());
        }
    }

    @Test
    void recurringSchedulesRespectAnchorsEndDatesAndIndependentDeadlines() {
        Task task = recurring("fortnightly", "2:2");
        task.setStartDate(TODAY.minusWeeks(2));
        task.setRecurrenceEndDate(TODAY.plusWeeks(2));
        assertEquals(List.of(Kind.RECURRING), kinds(build(TODAY, List.of(task), List.of(), List.of(), List.of())));
        assertTrue(build(TODAY.plusWeeks(1), List.of(task), List.of(), List.of(), List.of()).isEmpty());
        assertTrue(build(TODAY.plusWeeks(4), List.of(task), List.of(), List.of(), List.of()).isEmpty());
        task.setDeadline(TODAY.plusDays(1));
        assertEquals(List.of(Kind.DUE), kinds(build(TODAY.plusDays(1), List.of(task),
                List.of(), List.of(), List.of())));
        assertEquals(List.of(Kind.OVERDUE), kinds(build(TODAY.plusWeeks(4), List.of(task),
                List.of(), List.of(), List.of())));
        assertTrue(build(task.getStartDate().minusDays(1), List.of(task), List.of(), List.of(), List.of()).isEmpty());
    }

    @Test
    void recurringTaskWithNoStartUsesItsCreationAnchor() {
        Task task = recurring("fortnightly", "2:2");
        task.setStartDate(null);
        task.setCreatedAt(TODAY.minusWeeks(1).atStartOfDay());
        assertTrue(build(TODAY, List.of(task), List.of(), List.of(), List.of()).isEmpty());
        assertEquals(List.of(Kind.RECURRING), kinds(build(TODAY.plusWeeks(1), List.of(task),
                List.of(), List.of(), List.of())));
    }

    @Test
    void aSubtaskGoalMarksItsParentAsPlannedButKeepsTheWholeFamilyAvailable() {
        Task parent = task("Parent", TODAY);
        Task child = task("Child", TODAY);
        Task sibling = task("Sibling", TODAY);
        child.setParentId(parent.getId());
        sibling.setParentId(parent.getId());
        List<Entry> entries = build(TODAY, List.of(parent, child, sibling),
                List.of(goal("parent goal", parent, TODAY)), List.of(), List.of());
        assertEquals(List.of("Parent", "Parent / Child", "Parent / Sibling"),
                entries.stream().map(Entry::title).toList());
        assertEquals(List.of("due today · work planned", "due today", "due today"),
                entries.stream().map(Entry::reason).toList());
        entries = build(TODAY, List.of(parent, child, sibling),
                List.of(goal("child goal", child, TODAY)), List.of(), List.of());
        assertEquals(List.of("Parent", "Parent / Child", "Parent / Sibling"),
                entries.stream().map(Entry::title).toList());
        assertEquals(List.of("due today · work planned", "due today · work planned", "due today"),
                entries.stream().map(Entry::reason).toList());
        entries = build(TODAY, List.of(parent, child, sibling),
                List.of(goal("child goal", child, TODAY.minusDays(1))), List.of(), List.of());
        assertEquals(List.of("Parent", "Parent / Child", "Parent / Sibling"),
                entries.stream().map(Entry::title).toList());
        assertEquals("Child", build(TODAY, List.of(child), List.of(), List.of(), List.of()).getFirst().title());
    }

    @Test
    void mixedRecurrenceFamiliesKeepTheirOwnSchedules() {
        for (boolean parentRepeats : List.of(true, false)) {
            Task parent = task("Parent", parentRepeats ? null : TODAY.plusDays(1));
            Task child = task("Child", parentRepeats ? TODAY.plusDays(1) : null);
            child.setParentId(parent.getId());
            Task recurring = parentRepeats ? parent : child;
            recurring.setRecurringPattern("1:2");
            recurring.setStartDate(TODAY.minusWeeks(1));
            assertEquals(recurring, build(TODAY, List.of(parent, child),
                    List.of(), List.of(), List.of()).getFirst().task());
            assertEquals(parentRepeats ? child : parent, build(TODAY.plusDays(1), List.of(parent, child),
                    List.of(), List.of(), List.of()).getFirst().task());
        }
    }

    @Test
    void urgencyThenDatePriorityTitleAndIdGiveStableOrdering() {
        Task due = task("due", TODAY);
        Task old = task("old", TODAY.minusDays(5));
        Task a = task("A", TODAY.minusDays(2));
        Task z = task("Z", TODAY.minusDays(2));
        z.setPriority(new TaskPriority(5));
        Task b = task("B", TODAY.minusDays(2));
        Task reminder = task("reminder", TODAY.plusDays(1));
        reminder.setRemindDaysBefore(1);
        List<Task> tasks = List.of(reminder, b, due, a, old, z);
        assertEquals(List.of("old", "Z", "A", "B", "due", "reminder"),
                build(TODAY, tasks, List.of(), List.of(), List.of()).stream().map(Entry::title).toList());
    }

    @Test
    void urgencyGroupsPutRemindersAfterAllWorkThatNeedsAttention() {
        Task overdue = task("overdue", TODAY.minusDays(1));
        Task due = task("due", TODAY);
        Task missed = recurring("missed", "1:1");
        Task retry = task("retry", TODAY.plusDays(10));
        Task scheduled = recurring("scheduled", "1:2");
        Task open = task("open", null);
        Task reminder = task("reminder", TODAY.plusDays(2));
        reminder.setRemindDaysBefore(2);
        List<Entry> entries = build(TODAY, List.of(reminder, open, scheduled, retry, missed, due, overdue),
                List.of(), List.of(missed("retry", retry, TODAY.minusDays(1))),
                List.of(new MissedOccurrence(missed, TODAY.minusDays(1))));
        assertEquals(List.of(Kind.OVERDUE, Kind.DUE, Kind.MISSED, Kind.RETRY,
                Kind.RECURRING, Kind.OPEN, Kind.REMINDER), kinds(entries));
    }

    private List<Entry> build(LocalDate date, List<Task> tasks, List<StudyGoal> attempts,
                              List<StudyGoal> retries, List<MissedOccurrence> missed) {
        return PlannerAgenda.build(date, TODAY, tasks, attempts, retries, missed, recurrence::isRecurringOccurrence);
    }

    private static List<Kind> kinds(List<Entry> entries) {
        return entries.stream().map(Entry::kind).toList();
    }

    private static Task task(String title, LocalDate deadline) {
        return new Task(title, title, "", "Study", new TaskPriority(3), deadline,
                TaskStatus.OPEN, 0, "", null);
    }

    private static Task recurring(String title, String pattern) {
        Task task = task(title, null);
        task.setRecurringPattern(pattern);
        task.setStartDate(TODAY.minusWeeks(2));
        return task;
    }

    private static StudyGoal goal(String description, Task task, LocalDate date) {
        return new StudyGoal(null, date, description, false, null, 0, false, 0,
                task == null ? null : task.getId());
    }

    private static StudyGoal missed(String description, Task task, LocalDate date) {
        StudyGoal goal = goal(description, task, date);
        goal.setFailed(true);
        return goal;
    }
}
