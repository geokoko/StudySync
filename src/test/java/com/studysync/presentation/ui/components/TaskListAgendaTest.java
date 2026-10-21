package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.Task;
import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.domain.valueobject.TaskStatus;
import com.studysync.presentation.ui.components.TaskListAgenda.Family;
import com.studysync.presentation.ui.components.TaskListAgenda.Section;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskListAgendaTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    @Test
    void boundariesUseTomorrowThroughSevenDaysRatherThanCalendarWeek() {
        Task overdue = task("overdue", TODAY.minusDays(1), TaskStatus.OPEN);
        Task today = task("today", TODAY, TaskStatus.IN_PROGRESS);
        Task tomorrow = task("tomorrow", TODAY.plusDays(1), TaskStatus.OPEN);
        Task seventh = task("seventh", TODAY.plusDays(7), TaskStatus.OPEN);
        Task eighth = task("eighth", TODAY.plusDays(8), TaskStatus.OPEN);
        Task undated = task("undated", null, TaskStatus.OPEN);

        Map<Section, List<Family>> sections = build(List.of(eighth, seventh, undated, today, overdue, tomorrow));

        assertEquals(List.of(overdue), roots(sections, Section.OVERDUE));
        assertEquals(List.of(today), roots(sections, Section.TODAY));
        assertEquals(List.of(tomorrow, seventh), roots(sections, Section.THIS_WEEK));
        assertEquals(List.of(eighth), roots(sections, Section.LATER));
        assertEquals(List.of(undated), roots(sections, Section.NO_DEADLINE));
    }

    @Test
    void boundariesCrossMonthAndYearUsingTheSuppliedDate() {
        LocalDate today = LocalDate.of(2026, 12, 31);
        Task last = task("last", LocalDate.of(2027, 1, 7), TaskStatus.OPEN);
        Task later = task("later", LocalDate.of(2027, 1, 8), TaskStatus.OPEN);
        Map<Section, List<Family>> sections = TaskListAgenda.build(today, List.of(later, last), task -> true);
        assertEquals(List.of(last), roots(sections, Section.THIS_WEEK));
        assertEquals(List.of(later), roots(sections, Section.LATER));
    }

    @Test
    void overdueChildPullsLaterParentAndAllSiblingsIntoOverdue() {
        Task parent = task("parent", TODAY.plusDays(30), TaskStatus.OPEN);
        Task child = child("child", parent, TODAY.minusDays(2), TaskStatus.OPEN);
        Task done = child("done", parent, TODAY.plusDays(1), TaskStatus.COMPLETED);
        Task cancelled = child("cancelled", parent, null, TaskStatus.CANCELLED);
        Map<Section, List<Family>> sections = build(List.of(done, parent, cancelled, child));

        Family family = sections.get(Section.OVERDUE).getFirst();
        assertEquals(child, family.urgentMember());
        assertEquals(List.of(parent, child, done, cancelled), family.tasks());
        assertEquals(1, sections.values().stream().mapToInt(List::size).sum());
    }

    @Test
    void parentCanBeTheMostUrgentMember() {
        Task parent = task("parent", TODAY, TaskStatus.IN_PROGRESS);
        Task child = child("child", parent, TODAY.plusDays(20), TaskStatus.OPEN);
        Family family = build(List.of(child, parent)).get(Section.TODAY).getFirst();
        assertEquals(parent, family.urgentMember());
        assertEquals(List.of(parent, child), family.tasks());
    }

    @Test
    void terminalChildrenDoNotMakeActiveFamilyOverdue() {
        Task parent = task("parent", TODAY.plusDays(10), TaskStatus.OPEN);
        Task done = child("done", parent, TODAY.minusDays(10), TaskStatus.COMPLETED);
        Task cancelled = child("cancelled", parent, TODAY.minusDays(20), TaskStatus.CANCELLED);
        Map<Section, List<Family>> sections = build(List.of(parent, done, cancelled));
        assertEquals(List.of(parent), roots(sections, Section.LATER));
        assertTrue(sections.get(Section.OVERDUE).isEmpty());
        assertEquals(List.of(parent, cancelled, done), sections.get(Section.LATER).getFirst().tasks());
    }

    @Test
    void postponedMembersDoNotHideActiveWorkEvenWithoutADeadline() {
        Task parent = task("parent", TODAY.minusDays(20), TaskStatus.POSTPONED);
        Task child = child("child", parent, null, TaskStatus.OPEN);
        Map<Section, List<Family>> sections = build(List.of(parent, child));
        assertEquals(List.of(parent), roots(sections, Section.NO_DEADLINE));
        assertTrue(sections.get(Section.POSTPONED).isEmpty());
    }

    @Test
    void activeChildAlsoSurfacesUnderATerminalParent() {
        for (TaskStatus status : List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED)) {
            Task parent = task("parent", TODAY.minusDays(20), status);
            Task child = child("child", parent, TODAY, TaskStatus.OPEN);
            assertEquals(List.of(parent, child), build(List.of(parent, child)).get(Section.TODAY).getFirst().tasks());
        }
    }

    @Test
    void postponedChildPlacesAnOtherwiseFinishedFamilyInPostponed() {
        Task parent = task("parent", TODAY.minusDays(10), TaskStatus.COMPLETED);
        Task child = child("child", parent, TODAY.plusDays(2), TaskStatus.POSTPONED);
        Map<Section, List<Family>> sections = build(List.of(parent, child));
        assertEquals(List.of(parent, child), sections.get(Section.POSTPONED).getFirst().tasks());
        assertTrue(sections.get(Section.COMPLETED).isEmpty());
    }

    @Test
    void collapsedStatusesIgnoreDeadlinesAndFollowActiveSections() {
        Task postponed = task("postponed", TODAY.minusDays(10), TaskStatus.POSTPONED);
        Task done = task("done", TODAY, TaskStatus.COMPLETED);
        Task cancelled = task("cancelled", TODAY.minusDays(20), TaskStatus.CANCELLED);
        Map<Section, List<Family>> sections = build(List.of(cancelled, done, postponed));
        assertEquals(List.of(Section.OVERDUE, Section.TODAY, Section.THIS_WEEK, Section.LATER,
                Section.NO_DEADLINE, Section.POSTPONED, Section.COMPLETED, Section.CANCELLED),
                new ArrayList<>(sections.keySet()));
        assertEquals(List.of(postponed), roots(sections, Section.POSTPONED));
        assertEquals(List.of(done), roots(sections, Section.COMPLETED));
        assertEquals(List.of(cancelled), roots(sections, Section.CANCELLED));
        for (Section section : Section.values()) {
            assertEquals(section.ordinal() >= Section.POSTPONED.ordinal(), section.collapsed);
        }
    }

    @Test
    void terminalFamilyUsesParentStatusAndKeepsMixedTerminalChildren() {
        for (TaskStatus status : List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED)) {
            Task parent = task("parent", null, status);
            Task done = child("done", parent, TODAY, TaskStatus.COMPLETED);
            Task cancelled = child("cancelled", parent, TODAY.plusDays(1), TaskStatus.CANCELLED);
            Section section = status == TaskStatus.COMPLETED ? Section.COMPLETED : Section.CANCELLED;
            Map<Section, List<Family>> sections = build(List.of(parent, cancelled, done));
            assertEquals(List.of(parent, done, cancelled), sections.get(section).getFirst().tasks());
            assertEquals(1, sections.values().stream().mapToInt(List::size).sum());
        }
    }

    @Test
    void orderingUsesDeadlineThenHighestPriorityThenCaseInsensitiveTitle() {
        Task early = task("early", TODAY.plusDays(1), TaskStatus.OPEN);
        Task low = task("low", TODAY.plusDays(2), TaskStatus.OPEN);
        Task highZ = task("Zebra", TODAY.plusDays(2), TaskStatus.OPEN);
        Task highA = task("alpha", TODAY.plusDays(2), TaskStatus.OPEN);
        highZ.setPriority(new TaskPriority(5));
        highA.setPriority(new TaskPriority(5));
        assertEquals(List.of(early, highA, highZ, low),
                roots(build(List.of(highZ, low, highA, early)), Section.THIS_WEEK));
    }

    @Test
    void familyOrderingUsesUrgentMemberAndChildrenRemainAdjacent() {
        Task parent = task("z-parent", TODAY.plusDays(30), TaskStatus.OPEN);
        Task child = child("a-child", parent, TODAY, TaskStatus.OPEN);
        child.setPriority(new TaskPriority(5));
        Task peer = task("peer", TODAY, TaskStatus.OPEN);
        Task laterChild = child("later", parent, TODAY.plusDays(40), TaskStatus.OPEN);
        List<Family> families = build(List.of(peer, laterChild, child, parent)).get(Section.TODAY);
        assertEquals(List.of(parent, child, laterChild, peer),
                families.stream().flatMap(family -> family.tasks().stream()).toList());
    }

    @Test
    void undatedAndCollapsedSectionsStillUsePriorityAndDeadlineOrdering() {
        Task low = task("low", null, TaskStatus.OPEN);
        Task high = task("high", null, TaskStatus.OPEN);
        high.setPriority(new TaskPriority(5));
        assertEquals(List.of(high, low), roots(build(List.of(low, high)), Section.NO_DEADLINE));
        Task undated = task("undated", null, TaskStatus.COMPLETED);
        Task dated = task("dated", TODAY, TaskStatus.COMPLETED);
        assertEquals(List.of(dated, undated), roots(build(List.of(undated, dated)), Section.COMPLETED));
    }

    @Test
    void recurringTasksWithoutDeadlinesStayUndatedRegardlessOfSchedule() {
        for (LocalDate end : new LocalDate[] {null, TODAY.minusDays(1), TODAY.plusDays(10)}) {
            Task recurring = task("recurring", null, TaskStatus.OPEN);
            recurring.setRecurringPattern("1:2,4");
            recurring.setStartDate(TODAY.minusDays(14));
            recurring.setRecurrenceEndDate(end);
            assertEquals(List.of(recurring), roots(build(List.of(recurring)), Section.NO_DEADLINE));
        }
    }

    @Test
    void recurringDeadlineAndStatusTakePrecedenceOverSchedule() {
        Task recurring = task("recurring", TODAY.minusDays(1), TaskStatus.DELAYED);
        recurring.setRecurringPattern("4:1");
        recurring.setRecurrenceEndDate(TODAY.minusDays(7));
        assertEquals(List.of(recurring), roots(build(List.of(recurring)), Section.OVERDUE));
        recurring.updateStatus(TaskStatus.COMPLETED);
        assertEquals(List.of(recurring), roots(build(List.of(recurring)), Section.COMPLETED));
    }

    @Test
    void matchingChildKeepsFullFamilyAndNonmatchingUrgentMember() {
        Task parent = task("parent", TODAY.plusDays(30), TaskStatus.OPEN);
        Task match = child("match", parent, null, TaskStatus.COMPLETED);
        Task urgent = child("urgent", parent, TODAY.minusDays(2), TaskStatus.OPEN);
        Task other = task("other", TODAY, TaskStatus.OPEN);
        Map<Section, List<Family>> sections = TaskListAgenda.build(TODAY,
                List.of(parent, match, urgent, other), task -> task == match);
        assertEquals(List.of(parent, urgent, match), sections.get(Section.OVERDUE).getFirst().tasks());
        assertTrue(sections.get(Section.TODAY).isEmpty());
        assertEquals(1, sections.values().stream().mapToInt(List::size).sum());
    }

    @Test
    void matchingParentKeepsChildrenAndUnmatchedFamiliesAreExcluded() {
        Task parent = task("parent", TODAY, TaskStatus.OPEN);
        Task child = child("child", parent, null, TaskStatus.CANCELLED);
        List<Task> input = List.of(child, parent);
        assertEquals(List.of(parent, child), TaskListAgenda.build(TODAY, input, task -> task == parent)
                .get(Section.TODAY).getFirst().tasks());
        assertTrue(TaskListAgenda.build(TODAY, input, task -> false).values().stream().allMatch(List::isEmpty));
        assertEquals(List.of(child, parent), input);
    }

    @Test
    void emptyInputAndMissingParentDoNotLoseTasks() {
        assertTrue(build(List.of()).values().stream().allMatch(List::isEmpty));
        Task orphan = task("orphan", TODAY, TaskStatus.OPEN);
        orphan.setParentId("missing");
        assertEquals(List.of(orphan), roots(build(List.of(orphan)), Section.TODAY));
        assertFalse(Section.TODAY.collapsed);
    }

    private Map<Section, List<Family>> build(List<Task> tasks) {
        return TaskListAgenda.build(TODAY, tasks, task -> true);
    }

    private List<Task> roots(Map<Section, List<Family>> sections, Section section) {
        return sections.get(section).stream().map(Family::parent).toList();
    }

    private Task task(String title, LocalDate deadline, TaskStatus status) {
        return new Task(title, title, "", "Study", new TaskPriority(1), deadline, status, 0);
    }

    private Task child(String title, Task parent, LocalDate deadline, TaskStatus status) {
        Task child = task(title, deadline, status);
        child.setParentId(parent.getId());
        return child;
    }
}
