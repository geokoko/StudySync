package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.Task;
import com.studysync.domain.service.MissedOccurrence;
import com.studysync.domain.service.TaskService;
import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.domain.valueobject.TaskStatus;
import com.studysync.presentation.ui.components.PlannerAgenda.Block;
import com.studysync.presentation.ui.components.PlannerAgenda.Entry;
import com.studysync.presentation.ui.components.PlannerAgenda.Kind;
import com.studysync.presentation.ui.components.PlannerAgenda.Occurrence;
import com.studysync.presentation.ui.components.PlannerAgenda.Plan;
import com.studysync.presentation.ui.components.PlannerAgenda.Scheduled;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void aParentAndItsListedSubtasksFormOneBlockWithTheParentFirst() {
        Task parent = task("Parent", TODAY);
        Task later = child("Alpha", parent, TODAY);
        Task sooner = child("Zed", parent, TODAY.minusDays(1));
        Task other = task("Other", TODAY.minusDays(2));
        Plan plan = plan(TODAY, List.of(later, other, parent, sooner));
        assertEquals(2, plan.needed().size());
        Block family = plan.needed().getFirst();
        assertTrue(family.isFamily());
        assertEquals(parent, family.parent());
        assertEquals(List.of(parent, sooner, later), tasks(family));
        assertEquals(List.of("Parent", "Zed", "Alpha"), family.entries().stream().map(Entry::tileTitle).toList());
        assertEquals("Parent / Zed", family.entries().get(1).title());
        assertEquals(parent, family.header().task());
        assertEquals(List.of(sooner, later), family.tiles().stream().map(Entry::task).toList());
        assertNull(plan.needed().get(1).header());
        assertFalse(plan.needed().get(1).isFamily());
        assertEquals(List.of(other), tasks(plan.needed().get(1)));
        assertTrue(plan.comingUp().isEmpty());
        assertEquals(4, plan.entries().size());
    }

    @Test
    void blocksAreOrderedByTheirMostUrgentMemberAndStandaloneTasksComeLast() {
        Task calm = task("Calm", null);
        Task lateChild = child("Late child", calm, TODAY.minusDays(5));
        Task due = task("Due", TODAY);
        Task dueChild = child("Due child", due, TODAY);
        Task veryLate = task("Very late", TODAY.minusDays(10));
        Task open = task("Open", null);
        Plan plan = plan(TODAY, List.of(open, due, dueChild, veryLate, calm, lateChild));
        assertEquals(List.of(calm, due), plan.needed().stream().filter(Block::isFamily).map(Block::parent).toList());
        Block standalone = plan.needed().getLast();
        assertFalse(standalone.isFamily());
        assertEquals(List.of(veryLate, open), tasks(standalone));
    }

    @Test
    void aListedParentBringsItsActiveSubtasksWithoutAReasonOfTheirOwn() {
        Task parent = task("Parent", TODAY);
        Task friday = child("Friday", parent, TODAY.plusDays(3));
        Task weekEdge = child("Week edge", parent, TODAY.plusDays(7));
        Task november = child("November", parent, TODAY.plusDays(22));
        Task repeating = child("Repeating", parent, null);
        repeating.setRecurringPattern("1:3");
        repeating.setStartDate(TODAY.minusWeeks(1));
        Task undated = child("Undated", parent, null);
        Plan plan = plan(TODAY, List.of(parent, friday, weekEdge, november, repeating, undated));
        assertEquals(1, plan.needed().size());
        Block family = plan.needed().getFirst();
        assertEquals(List.of(parent, friday, weekEdge, november, repeating, undated), tasks(family));
        assertEquals(List.of(Kind.DUE, Kind.SUBTASK, Kind.SUBTASK, Kind.SUBTASK, Kind.SUBTASK, Kind.OPEN),
                kinds(family.entries()));
        assertEquals(List.of("due today", "due Fri", "due Tue", "due Nov 11", "no deadline", "no deadline"),
                family.entries().stream().map(Entry::reason).toList());
        // Every tile is plannable: its entry is listed, after all work with a reason of its own.
        assertEquals(List.of(Kind.DUE, Kind.OPEN, Kind.SUBTASK, Kind.SUBTASK, Kind.SUBTASK, Kind.SUBTASK),
                kinds(plan.entries()));
        assertEquals(6, plan.entries().stream().map(Entry::key).distinct().count());
    }

    @Test
    void aListedSubtaskWithoutAListedParentGetsABlockWithoutAParentTile() {
        Task parent = task("Parent", TODAY.plusDays(10));
        Task due = child("Due", parent, TODAY);
        Task later = child("Later", parent, TODAY.plusDays(3));
        Plan plan = plan(TODAY, List.of(parent, due, later));
        assertEquals(1, plan.needed().size());
        Block family = plan.needed().getFirst();
        assertEquals(parent, family.parent());
        assertEquals(List.of(due), tasks(family));
        assertEquals("Due", family.entries().getFirst().tileTitle());
        assertNull(family.header());
        assertEquals(family.entries(), family.tiles());
    }

    @Test
    void aBlockIsComingUpOnlyWhenEveryMemberListedForItsOwnReasonIsAReminder() {
        Task reminding = task("Reminding", TODAY.plusDays(2));
        reminding.setRemindDaysBefore(3);
        Task quiet = child("Quiet", reminding, TODAY.plusDays(4));
        Task urgent = task("Urgent parent", TODAY.plusDays(2));
        urgent.setRemindDaysBefore(3);
        Task dueChild = child("Due child", urgent, TODAY);
        Task hidden = task("Hidden parent", TODAY.plusDays(20));
        Task remindedChild = child("Reminded child", hidden, TODAY.plusDays(1));
        remindedChild.setRemindDaysBefore(1);
        Task lone = task("Lone reminder", TODAY.plusDays(1));
        lone.setRemindDaysBefore(1);
        Plan plan = plan(TODAY, List.of(lone, reminding, quiet, urgent, dueChild, hidden, remindedChild));
        assertEquals(List.of(urgent), plan.needed().stream().map(Block::parent).toList());
        assertEquals(List.of(urgent, dueChild), tasks(plan.needed().getFirst()));
        assertEquals(3, plan.comingUp().size());
        assertEquals(List.of(hidden, reminding), plan.comingUp().subList(0, 2).stream().map(Block::parent).toList());
        assertEquals(List.of(remindedChild), tasks(plan.comingUp().getFirst()));
        assertEquals(List.of(reminding, quiet), tasks(plan.comingUp().get(1)));
        assertEquals(Kind.SUBTASK, plan.comingUp().get(1).entries().get(1).kind());
        assertFalse(plan.comingUp().getLast().isFamily());
        assertEquals(List.of(lone), tasks(plan.comingUp().getLast()));
    }

    @Test
    void inactiveSubtasksAreNotBroughtAlongAndTheParentStandsAlone() {
        Task parent = task("Parent", TODAY);
        List<Task> tasks = new ArrayList<>(List.of(parent));
        for (TaskStatus status : List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED, TaskStatus.POSTPONED)) {
            Task closed = child(status.name(), parent, TODAY.plusDays(3));
            closed.setStatus(status);
            tasks.add(closed);
        }
        Plan plan = plan(TODAY, tasks);
        assertEquals(List.of(parent), plan.entries().stream().map(Entry::task).toList());
        assertEquals(1, plan.needed().size());
        assertFalse(plan.needed().getFirst().isFamily());
    }

    @Test
    void standaloneTasksKeepTheUrgencyOrderAndTasklessRetriesStayApart() {
        Task due = task("due", TODAY);
        Task old = task("old", TODAY.minusDays(5));
        Task a = task("A", TODAY.minusDays(2));
        Task z = task("Z", TODAY.minusDays(2));
        z.setPriority(new TaskPriority(5));
        Task open = task("open", null);
        StudyGoal unlinked = missed("unlinked", null, TODAY.minusDays(1));
        Plan plan = PlannerAgenda.build(TODAY, TODAY, List.of(open, due, a, old, z), List.of(),
                List.of(unlinked), List.of(), recurrence::isRecurringOccurrence);
        assertEquals(1, plan.needed().size());
        assertEquals(List.of(old, z, a, due, open), tasks(plan.needed().getFirst()));
        assertEquals(List.of(unlinked), plan.taskless().getFirst().retries());
        assertEquals(1, plan.taskless().size());
    }

    @Test
    void scheduledListsTheRecurringTasksWhoseScheduleLandsOnTheDay() {
        Task tuesday = recurring("Gym", "1:2");
        Task wednesday = recurring("Spanish", "1:3");
        Task due = task("Essay", TODAY);
        List<Task> tasks = List.of(tuesday, wednesday, due);
        assertEquals(List.of(tuesday), scheduledTasks(TODAY, tasks, List.of()));
        assertEquals(List.of(wednesday), scheduledTasks(TODAY.plusDays(1), tasks, List.of()));
        assertEquals(List.of(tuesday), scheduledTasks(TODAY.plusWeeks(1), tasks, List.of()));
        Scheduled gym = scheduled(TODAY, tasks, List.of()).getFirst();
        assertEquals(Occurrence.NOT_PLANNED, gym.state());
        assertEquals("not planned", gym.detail());
        assertEquals("task:" + tuesday.getId(), gym.key());
    }

    @Test
    void scheduledFollowsTheScheduleRatherThanDeadlinesOrLateness() {
        Task late = recurring("Gym", "1:2");
        late.setDeadline(TODAY.minusDays(3));
        late.setStatus(TaskStatus.DELAYED);
        assertEquals(List.of(late), scheduledTasks(TODAY, List.of(late), List.of()));
        assertTrue(scheduled(TODAY.plusDays(1), List.of(late), List.of()).isEmpty(),
                "a late task is in Needs a plan every day, but scheduled only on its schedule");
        late.setRecurrenceEndDate(TODAY.minusDays(1));
        assertTrue(scheduled(TODAY, List.of(late), List.of()).isEmpty());
    }

    @Test
    void anOccurrenceIsJudgedByItsOwnGoalsDatedThatDay() {
        Task task = recurring("Gym", "1:2");
        StudyGoal pending = goal("Legs", task, TODAY);
        StudyGoal achieved = goal("Arms", task, TODAY);
        achieved.setAchieved(true);
        StudyGoal missed = missed("Run", task, TODAY);
        assertState(task, List.of(pending), Occurrence.PLANNED, "planned");
        assertState(task, List.of(pending, goal("Core", task, TODAY)), Occurrence.PLANNED, "2 goals planned");
        assertState(task, List.of(pending, achieved), Occurrence.PLANNED, "1 of 2 done");
        assertState(task, List.of(achieved), Occurrence.DONE, "done");
        assertState(task, List.of(achieved, missed), Occurrence.DONE, "1 of 2 done");
        assertState(task, List.of(missed), Occurrence.MISSED, "goal missed");
        assertState(task, List.of(goal("Earlier", task, TODAY.minusWeeks(1))), Occurrence.NOT_PLANNED, "not planned");

        Task child = child("Warm-up", task, null);
        assertEquals(Occurrence.NOT_PLANNED, scheduled(TODAY, List.of(task, child),
                List.of(goal("Stretch", child, TODAY))).getFirst().state(),
                "a subtask's goal is not the parent's occurrence");

        StudyGoal dropped = mock(StudyGoal.class);
        when(dropped.getTaskId()).thenReturn(task.getId());
        when(dropped.getDate()).thenReturn(TODAY);
        when(dropped.isDroppedAttempt()).thenReturn(true);
        when(dropped.getAttemptOutcome()).thenReturn(StudyGoal.AttemptOutcome.DROPPED);
        assertState(task, List.of(dropped), Occurrence.DROPPED, "dropped");
        assertState(task, List.of(dropped, pending), Occurrence.PLANNED, "planned");
    }

    @Test
    void aGoalMissedAndRetriedTheSameDayCountsOnceByItsBestAttempt() {
        Task task = recurring("Gym", "1:2");
        StudyGoal missed = new StudyGoal("legs", TODAY, "Legs", false, null, 0, false, 0, task.getId());
        missed.setFailed(true);
        StudyGoal retry = new StudyGoal("legs", TODAY, "Legs", false, null, 0, false, 0, task.getId());
        assertState(task, List.of(retry, missed), Occurrence.PLANNED, "planned");
        retry.setAchieved(true);
        assertState(task, List.of(retry, missed), Occurrence.DONE, "done");
        assertState(task, List.of(missed, retry), Occurrence.DONE, "done");
    }

    @Test
    void aPastOccurrenceWithoutAGoalIsNotDone() {
        Task task = recurring("Gym", "1:2");
        Scheduled past = scheduled(TODAY.minusWeeks(1), List.of(task), List.of()).getFirst();
        assertEquals(Occurrence.MISSED, past.state());
        assertEquals("not done", past.detail());
    }

    @Test
    void anInactiveTaskIsScheduledOnlyWhereItHasAGoalThatDay() {
        Task task = recurring("Gym", "1:2");
        for (TaskStatus status : List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED, TaskStatus.POSTPONED)) {
            task.setStatus(status);
            assertTrue(scheduled(TODAY, List.of(task), List.of()).isEmpty());
        }
        task.setStatus(TaskStatus.COMPLETED);
        StudyGoal achieved = goal("Legs", task, TODAY.minusWeeks(1));
        achieved.setAchieved(true);
        Scheduled past = scheduled(TODAY.minusWeeks(1), List.of(task), List.of(achieved)).getFirst();
        assertEquals(Occurrence.DONE, past.state());
    }

    @Test
    void scheduledPutsOpenWorkFirstThenOrdersByTitleAndShowsTheParent() {
        Task parent = task("Course", null);
        Task done = recurring("Anki", "1:2");
        Task planned = recurring("Reading", "1:2");
        Task open = recurring("Zumba", "1:2");
        Task alsoOpen = recurring("Drills", "1:2");
        alsoOpen.setParentId(parent.getId());
        StudyGoal achieved = goal("Deck", done, TODAY);
        achieved.setAchieved(true);
        List<Scheduled> scheduled = scheduled(TODAY, List.of(parent, done, planned, open, alsoOpen),
                List.of(achieved, goal("Chapter", planned, TODAY)));
        assertEquals(List.of("Course / Drills", "Zumba", "Reading", "Anki"),
                scheduled.stream().map(Scheduled::title).toList());
    }

    private void assertState(Task task, List<StudyGoal> attempts, Occurrence state, String detail) {
        Scheduled scheduled = scheduled(TODAY, List.of(task), attempts).getFirst();
        assertEquals(state, scheduled.state());
        assertEquals(detail, scheduled.detail());
    }

    private List<Scheduled> scheduled(LocalDate date, List<Task> tasks, List<StudyGoal> attempts) {
        return PlannerAgenda.scheduled(date, TODAY, tasks, attempts, recurrence::isRecurringOccurrence);
    }

    private List<Task> scheduledTasks(LocalDate date, List<Task> tasks, List<StudyGoal> attempts) {
        return scheduled(date, tasks, attempts).stream().map(Scheduled::task).toList();
    }

    private List<Entry> build(LocalDate date, List<Task> tasks, List<StudyGoal> attempts,
                              List<StudyGoal> retries, List<MissedOccurrence> missed) {
        return PlannerAgenda.build(date, TODAY, tasks, attempts, retries, missed, recurrence::isRecurringOccurrence)
                .entries();
    }

    private Plan plan(LocalDate date, List<Task> tasks) {
        return PlannerAgenda.build(date, TODAY, tasks, List.of(), List.of(), List.of(),
                recurrence::isRecurringOccurrence);
    }

    private static List<Task> tasks(Block block) {
        return block.entries().stream().map(Entry::task).toList();
    }

    private static Task child(String title, Task parent, LocalDate deadline) {
        Task child = task(title, deadline);
        child.setParentId(parent.getId());
        return child;
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
