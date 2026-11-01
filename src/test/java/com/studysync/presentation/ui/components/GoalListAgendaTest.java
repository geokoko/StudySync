package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.Task;
import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.domain.valueobject.TaskStatus;
import com.studysync.presentation.ui.components.GoalListAgenda.Group;
import com.studysync.presentation.ui.components.GoalListAgenda.Section;
import com.studysync.presentation.ui.components.GoalListAgenda.Sort;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalListAgendaTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    private final Task thesis = task("thesis", "diploma-thesis", null, 3, TODAY.plusDays(30));
    private final Task algorithms = task("algorithms", "algorithms", null, 5, TODAY.plusDays(2));
    private final Task notes = task("notes", "notes", "thesis", 1, null);
    private final Map<String, Task> tasks = byId(thesis, algorithms, notes);

    @Test
    void byTaskKeepsEachTasksGoalsTogetherAndOrdersGroupsByTheirFirstGoal() {
        StudyGoal research = goal("research", thesis);
        StudyGoal revision = goal("revision", algorithms);
        StudyGoal papers = goal("read papers", thesis);
        StudyGoal plan = goal("plan the course", algorithms);
        StudyGoal meeting = goal("meeting", null);
        StudyGoal summary = goal("summary", notes);
        plan.setAchieved(true);
        summary.setAchieved(true);

        List<Section> sections = GoalListAgenda.build(List.of(research, revision, papers, plan, meeting, summary),
                tasks, Group.TASK, Sort.STATUS);

        assertEquals(List.of("algorithms", "diploma-thesis", GoalListAgenda.NO_TASK, "diploma-thesis / notes"),
                titles(sections));
        assertEquals(List.of(revision, plan), sections.get(0).goals());
        assertEquals(List.of(papers, research), sections.get(1).goals());
        assertEquals(List.of(meeting), sections.get(2).goals());
        assertEquals(List.of(summary), sections.get(3).goals());
    }

    @Test
    void byStatusUsesAFixedOrderAndSkipsEmptyGroups() {
        StudyGoal pending = goal("b pending", thesis);
        StudyGoal missed = goal("missed", thesis);
        StudyGoal done = goal("a done", algorithms);
        missed.setFailed(true);
        done.setAchieved(true);

        List<Section> sections = GoalListAgenda.build(List.of(missed, done, pending), tasks, Group.STATUS, Sort.TITLE);

        assertEquals(List.of("Pending", "Done", "Missed"), titles(sections));
        assertEquals(List.of("Pending"), titles(GoalListAgenda.build(List.of(pending), tasks,
                Group.STATUS, Sort.TITLE)));
    }

    @Test
    void sortsUseTheGoalsTaskAndPutGoalsWithoutOneLast() {
        StudyGoal low = goal("low", notes);
        StudyGoal high = goal("high", algorithms);
        StudyGoal mid = goal("mid", thesis);
        StudyGoal loose = goal("loose", null);
        List<StudyGoal> goals = List.of(low, loose, mid, high);

        assertEquals(List.of(high, mid, low, loose), only(GoalListAgenda.build(goals, tasks, Group.NONE,
                Sort.PRIORITY)));
        assertEquals(List.of(high, mid, low, loose), only(GoalListAgenda.build(goals, tasks, Group.NONE,
                Sort.DEADLINE)));
        assertEquals(List.of(high, loose, low, mid), only(GoalListAgenda.build(goals, tasks, Group.NONE,
                Sort.TITLE)));
    }

    @Test
    void goalsWithoutATaskStayAfterTasksWithoutADeadlineWhateverTheirStatus() {
        Task undated = task("undated", "undated", null, 1, null);
        Map<String, Task> withUndated = byId(thesis, algorithms, notes, undated);
        StudyGoal loosePending = goal("loose", null);
        StudyGoal undatedDone = goal("undated done", undated);
        undatedDone.setAchieved(true);
        List<StudyGoal> goals = List.of(loosePending, undatedDone);

        assertEquals(List.of(undatedDone, loosePending), only(GoalListAgenda.build(goals, withUndated, Group.NONE,
                Sort.DEADLINE)));
    }

    @Test
    void ungroupedListHasOneUntitledSectionAndNothingForNoGoals() {
        List<Section> sections = GoalListAgenda.build(List.of(goal("x", thesis)), tasks, Group.NONE, Sort.STATUS);
        assertEquals(1, sections.size());
        assertNull(sections.getFirst().title());
        assertTrue(GoalListAgenda.build(List.of(), tasks, Group.TASK, Sort.STATUS).isEmpty());
        assertTrue(GoalListAgenda.build(List.of(), tasks, Group.NONE, Sort.STATUS).isEmpty());
    }

    private static List<StudyGoal> only(final List<Section> sections) {
        assertEquals(1, sections.size());
        return sections.getFirst().goals();
    }

    private static List<String> titles(final List<Section> sections) {
        return sections.stream().map(Section::title).toList();
    }

    private static StudyGoal goal(final String description, final Task task) {
        return new StudyGoal(null, TODAY, description, false, null, 0, false, 0,
                task == null ? null : task.getId());
    }

    private static Task task(final String id, final String title, final String parentId, final int stars,
                             final LocalDate deadline) {
        Task task = new Task(id, title, "", "Study", new TaskPriority(stars), deadline,
                TaskStatus.OPEN, 0, "", null);
        task.setParentId(parentId);
        return task;
    }

    private static Map<String, Task> byId(final Task... tasks) {
        Map<String, Task> map = new LinkedHashMap<>();
        for (Task task : tasks) {
            map.put(task.getId(), task);
        }
        return map;
    }
}
