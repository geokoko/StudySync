package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.Task;
import com.studysync.domain.service.TaskService;
import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.domain.valueobject.TaskStatus;
import com.studysync.presentation.ui.components.CalendarAgenda.Entry;
import com.studysync.presentation.ui.components.CalendarAgenda.Kind;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarAgendaTest {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 12);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
    private static final LocalDate TO = LocalDate.of(2026, 10, 25);

    private static final BiPredicate<Task, LocalDate> MONDAYS_AND_THURSDAYS = (task, date) ->
            date.getDayOfWeek() == DayOfWeek.MONDAY || date.getDayOfWeek() == DayOfWeek.THURSDAY;

    @Test
    void parentAndChildKeepTheirOwnDatesAndChildGoalsShowParentContext() {
        Task parent = task("parent", "Thesis", TODAY.plusDays(3), TaskStatus.OPEN);
        Task child = task("child", "Introduction", TODAY, TaskStatus.OPEN);
        child.setParentId(parent.getId());
        StudyGoal goal = goal("Write a draft", child.getId(), TODAY.plusDays(1), false, false);
        Map<LocalDate, List<Entry>> days = build(List.of(parent, child), List.of(goal));
        assertEquals("Thesis / Introduction", days.get(TODAY).getFirst().title());
        assertEquals("Thesis / Introduction · Write a draft", days.get(TODAY.plusDays(1)).getFirst().text());
        assertEquals("Thesis", days.get(TODAY.plusDays(3)).getFirst().title());
        assertEquals(1, days.get(TODAY).size());
    }

    @Test
    void taskTitlesNeedNoLookupForATaskWithoutAParent() {
        Task parent = task("parent", "Thesis", null, TaskStatus.OPEN);
        Task child = task("child", "Introduction", null, TaskStatus.OPEN);
        child.setParentId(parent.getId());
        // Map.of() rejects a null key, as an empty map before the first load may be.
        assertEquals("Thesis", CalendarAgenda.taskTitle(parent, Map.of()));
        assertEquals("Introduction", CalendarAgenda.taskTitle(child, Map.of()));
        assertEquals("Thesis / Introduction", CalendarAgenda.taskTitle(child, Map.of("parent", parent)));
    }

    @Test
    void aDayCellShowsOneLinePerTaskWithItsGoalsCounted() {
        Task essay = task("essay", "Essay", TODAY, TaskStatus.OPEN);
        Task reading = task("reading", "Reading", null, TaskStatus.OPEN);
        StudyGoal outline = goal("Outline", essay.getId(), TODAY, true, false);
        StudyGoal draft = goal("Draft", essay.getId(), TODAY, false, false);
        StudyGoal chapter = goal("Chapter 3", reading.getId(), TODAY, false, false);
        StudyGoal desk = goal("Tidy desk", null, TODAY, false, false);
        StudyGoal mail = goal("Answer mail", null, TODAY, false, false);
        List<Entry> day = build(List.of(essay, reading), List.of(outline, draft, chapter, desk, mail)).get(TODAY);

        List<CalendarAgenda.TaskLine> lines = CalendarAgenda.byTask(day);

        assertEquals(List.of("Essay · 1 of 2 goals done", "Goals without a task · 2 goals", "Reading · 1 goal"),
                lines.stream().map(CalendarAgenda.TaskLine::text).toList());
        assertEquals(Kind.DUE, lines.getFirst().kind());
        assertEquals(2, lines.getFirst().goals().size());
        assertNull(lines.getFirst().movableGoal(), "a deadline line folded with goals is not dragged");
        assertEquals(chapter, lines.get(2).movableGoal());
    }

    @Test
    void aLoneGoalWithoutATaskKeepsItsOwnTitleAndALateTaskKeepsItsLateness() {
        Task late = task("late", "Report", TODAY.minusDays(2), TaskStatus.OPEN);
        StudyGoal fix = goal("Fix figures", late.getId(), TODAY, false, false);
        StudyGoal desk = goal("Tidy desk", null, TODAY, true, false);
        List<Entry> day = build(List.of(late), List.of(fix, desk)).get(TODAY);

        assertEquals(List.of("Report · 2d late · 1 goal", "Tidy desk"),
                CalendarAgenda.byTask(day).stream().map(CalendarAgenda.TaskLine::text).toList());
    }

    @Test
    void aSubtasksGoalCoversItsParentButNotItsSiblings() {
        Task parent = task("parent", "Thesis", null, TaskStatus.OPEN);
        Task planned = task("planned", "Introduction", null, TaskStatus.OPEN);
        Task sibling = task("sibling", "Proofread", null, TaskStatus.OPEN);
        planned.setParentId(parent.getId());
        sibling.setParentId(parent.getId());
        assertEquals(Set.of("planned", "parent"),
                CalendarAgenda.withParents(Set.of("planned"), List.of(parent, planned, sibling)));
        assertEquals(Set.of("parent"), CalendarAgenda.withParents(Set.of("parent"), List.of(parent, planned, sibling)));
    }

    @Test
    void recurringParentKeepsCalendarOccurrencesWhenItsChildCompletes() {
        Task parent = task("parent", "Weekly work", null, TaskStatus.OPEN);
        Task child = task("child", "Practice", null, TaskStatus.OPEN);
        parent.setRecurringPattern("1:1,4");
        child.setRecurringPattern("1:1,4");
        child.setParentId(parent.getId());
        LocalDate thursday = TODAY.plusDays(2);
        assertEquals(2, build(List.of(parent, child), List.of()).get(thursday).size());
        child.updateStatus(TaskStatus.COMPLETED);
        child.setCompletedAt(TODAY);
        List<Entry> future = build(List.of(parent, child), List.of()).get(thursday);
        assertEquals(1, future.size());
        assertEquals(parent, future.getFirst().task());
        assertEquals(Kind.RECURRING, future.getFirst().kind());
        parent.updateStatus(TaskStatus.COMPLETED);
        parent.setCompletedAt(TODAY);
        assertTrue(build(List.of(parent, child), List.of()).get(thursday).isEmpty());
    }

    @Test
    void mixedRecurrenceFamiliesKeepIndependentCalendarDatesAndHandledOccurrences() {
        TaskService taskService = new TaskService(null, null, null);
        for (boolean recurringParent : List.of(false, true)) {
            Task parent = task("parent", "Parent", recurringParent ? null : TODAY.plusDays(3), TaskStatus.OPEN);
            Task child = task("child", "Child", recurringParent ? TODAY.plusDays(3) : null, TaskStatus.OPEN);
            child.setParentId(parent.getId());
            Task recurring = recurringParent ? parent : child;
            Task oneOff = recurringParent ? child : parent;
            recurring.setRecurringPattern("1:4");
            recurring.setStartDate(FROM);
            recurring.setRecurrenceEndDate(TO);
            Map<LocalDate, List<Entry>> days = CalendarAgenda.build(FROM, TO, TODAY,
                    List.of(parent, child), List.of(), taskService::isRecurringOccurrence);
            assertEquals(recurring, days.get(TODAY.plusDays(2)).getFirst().task());
            assertEquals(List.of(Kind.RECURRING), kinds(days, TODAY.plusDays(2)));
            assertEquals(oneOff, days.get(TODAY.plusDays(3)).getFirst().task());
            assertEquals(List.of(Kind.DUE), kinds(days, TODAY.plusDays(3)));
            assertTrue(days.get(TODAY).isEmpty());
            LocalDate lastThursday = TODAY.minusDays(5);
            assertEquals(List.of(Kind.RECURRING_MISSED), kinds(days, lastThursday));

            StudyGoal handled = goal("This occurrence only", recurring.getId(), lastThursday, true, false);
            days = CalendarAgenda.build(FROM, TO, TODAY, List.of(parent, child),
                    List.of(handled), taskService::isRecurringOccurrence);
            assertEquals(List.of(Kind.GOAL_ACHIEVED), kinds(days, lastThursday));
            assertEquals(List.of(Kind.RECURRING), kinds(days, TODAY.plusDays(2)));

            child.updateStatus(TaskStatus.COMPLETED);
            child.setCompletedAt(TODAY);
            days = CalendarAgenda.build(FROM, TO, TODAY, List.of(parent, child),
                    List.of(handled), taskService::isRecurringOccurrence);
            assertEquals(recurringParent ? List.of(Kind.RECURRING) : List.of(), kinds(days, TODAY.plusDays(2)));
        }
    }

    @Test
    void lateTaskIsListedOnItsDeadlineAndOnTodayOnly() {
        Task late = task("late", "microlab", LocalDate.of(2026, 10, 15), TaskStatus.DELAYED);

        Map<LocalDate, List<Entry>> days = build(List.of(late), List.of());

        assertEquals(List.of(Kind.DUE), kinds(days, LocalDate.of(2026, 10, 15)));
        Entry overdue = days.get(TODAY).getFirst();
        assertEquals(Kind.OVERDUE, overdue.kind());
        assertEquals("microlab · 5d late", overdue.text());
        // Not repeated on the days in between, nor on future days.
        for (LocalDate date = FROM; !date.isAfter(TO); date = date.plusDays(1)) {
            if (!date.equals(TODAY) && !date.equals(LocalDate.of(2026, 10, 15))) {
                assertTrue(days.get(date).isEmpty(), "nothing expected on " + date);
            }
        }
    }

    @Test
    void completedTaskAppearsOnTheDayItWasCompletedInsteadOfItsDeadline() {
        Task done = task("done", "embedded 6", LocalDate.of(2026, 10, 15), TaskStatus.COMPLETED);
        done.setCompletedAt(LocalDate.of(2026, 10, 14));

        Map<LocalDate, List<Entry>> days = build(List.of(done), List.of());

        assertEquals(List.of(Kind.COMPLETED), kinds(days, LocalDate.of(2026, 10, 14)));
        assertTrue(days.get(LocalDate.of(2026, 10, 15)).isEmpty());
        assertTrue(days.get(TODAY).isEmpty());
    }

    @Test
    void postponedAndCancelledTasksAreOutOfThePlan() {
        Task postponed = task("postponed", "later", LocalDate.of(2026, 10, 15), TaskStatus.POSTPONED);
        Task cancelled = task("cancelled", "never", LocalDate.of(2026, 10, 22), TaskStatus.CANCELLED);

        Map<LocalDate, List<Entry>> days = build(List.of(postponed, cancelled), List.of());

        assertTrue(days.values().stream().allMatch(List::isEmpty));
    }

    @Test
    void goalsAreNamedByTheirTaskAndClassifiedByOutcome() {
        Task lab = task("lab", "Lab 6", LocalDate.of(2026, 10, 30), TaskStatus.IN_PROGRESS);
        StudyGoal planned = goal("anafora + code", "lab", LocalDate.of(2026, 10, 22), false, false);
        StudyGoal achieved = goal("read spec", "lab", LocalDate.of(2026, 10, 16), true, false);
        StudyGoal missed = goal("write isr", "lab", LocalDate.of(2026, 10, 15), false, true);
        StudyGoal unlinked = goal("Le-Roi meeting", null, LocalDate.of(2026, 10, 22), false, false);

        Map<LocalDate, List<Entry>> days = build(List.of(lab), List.of(planned, achieved, missed, unlinked));

        List<Entry> thursday = days.get(LocalDate.of(2026, 10, 22));
        assertEquals(2, thursday.size());
        Entry taskGoal = thursday.stream().filter(e -> e.task() != null).findFirst().orElseThrow();
        assertEquals(Kind.GOAL_PENDING, taskGoal.kind());
        assertEquals("Lab 6 · anafora + code", taskGoal.text());
        assertTrue(taskGoal.movable());
        Entry meeting = thursday.stream().filter(e -> e.task() == null).findFirst().orElseThrow();
        assertEquals("Le-Roi meeting", meeting.title());
        assertNull(meeting.detail());

        assertEquals(List.of(Kind.GOAL_ACHIEVED), kinds(days, LocalDate.of(2026, 10, 16)));
        assertEquals(List.of(Kind.GOAL_MISSED), kinds(days, LocalDate.of(2026, 10, 15)));
        assertFalse(days.get(LocalDate.of(2026, 10, 15)).getFirst().movable());
    }

    @Test
    void recurringTaskShowsItsScheduleWithoutRepeatingWhatAGoalOrDeadlineAlreadySays() {
        Task algorithms = task("algo", "algorithms", LocalDate.of(2026, 10, 22), TaskStatus.IN_PROGRESS);
        algorithms.setRecurringPattern("1:1,4");
        StudyGoal handled = goal("ch. 5", "algo", LocalDate.of(2026, 10, 15), true, false);

        Map<LocalDate, List<Entry>> days = build(List.of(algorithms), List.of(handled));

        assertEquals(List.of(Kind.RECURRING_MISSED), kinds(days, LocalDate.of(2026, 10, 12)));
        assertEquals(List.of(Kind.GOAL_ACHIEVED), kinds(days, LocalDate.of(2026, 10, 15)));
        assertEquals(List.of(Kind.RECURRING_MISSED), kinds(days, LocalDate.of(2026, 10, 19)));
        assertTrue(days.get(TODAY).isEmpty());
        assertEquals(List.of(Kind.DUE), kinds(days, LocalDate.of(2026, 10, 22)));
    }

    @Test
    void mostPressingLinesComeFirst() {
        Task late = task("late", "b late", LocalDate.of(2026, 10, 1), TaskStatus.OPEN);
        Task dueToday = task("due", "c due", TODAY, TaskStatus.OPEN);
        Task done = task("done", "a done", null, TaskStatus.COMPLETED);
        done.setCompletedAt(TODAY);
        StudyGoal planned = goal("d goal", null, TODAY, false, false);

        Map<LocalDate, List<Entry>> days = build(List.of(done, dueToday, late), List.of(planned));

        assertEquals(List.of(Kind.OVERDUE, Kind.DUE, Kind.GOAL_PENDING, Kind.COMPLETED), kinds(days, TODAY));
    }

    private static Map<LocalDate, List<Entry>> build(List<Task> tasks, List<StudyGoal> attempts) {
        return CalendarAgenda.build(FROM, TO, TODAY, tasks, attempts, MONDAYS_AND_THURSDAYS);
    }

    private static List<Kind> kinds(Map<LocalDate, List<Entry>> days, LocalDate date) {
        return days.get(date).stream().map(Entry::kind).toList();
    }

    private static Task task(String id, String title, LocalDate deadline, TaskStatus status) {
        return new Task(id, title, "", "Study", new TaskPriority(3), deadline, status, 0);
    }

    private static StudyGoal goal(String description, String taskId, LocalDate date, boolean achieved, boolean missed) {
        return new StudyGoal(null, date, description, achieved, null, 0, false, 0, taskId, null, missed);
    }
}
