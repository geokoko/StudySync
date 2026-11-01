package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.Task;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the planner's goal list is sorted and grouped. Dropped goals are not
 * passed in; the planner keeps them in their own collapsed section.
 *
 * <p>Goals are sorted first and then grouped, so groups appear in the order
 * of their first goal: grouped by task under the Status sort, groups with
 * pending work come first. Goals without a task form a "No task" group that
 * is ordered the same way. Status groups keep a fixed Pending, Done, Missed
 * order. Priority and deadline belong to a goal's task; goals without one
 * sort after those with one.</p>
 */
final class GoalListAgenda {

    enum Group {
        TASK("Task"), STATUS("Status"), NONE("None");

        private final String label;

        Group(final String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    enum Sort {
        STATUS("Status"), PRIORITY("Priority"), DEADLINE("Deadline"), TITLE("Title");

        private final String label;

        Sort(final String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Goal states, declared in display order. */
    enum Status {
        PENDING("Pending"), DONE("Done"), MISSED("Missed");

        final String label;

        Status(final String label) {
            this.label = label;
        }

        static Status of(final StudyGoal goal) {
            if (goal.isAchieved()) {
                return DONE;
            }
            return goal.isFailed() || goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.MISSED ? MISSED : PENDING;
        }
    }

    /** One heading and its goals; {@code title} is null for the ungrouped list. */
    record Section(String title, List<StudyGoal> goals) {
    }

    static final String NO_TASK = "No task";

    private GoalListAgenda() {
    }

    static List<Section> build(final List<StudyGoal> goals, final Map<String, Task> tasksById,
                               final Group group, final Sort sort) {
        List<StudyGoal> sorted = goals.stream().sorted(comparator(sort, tasksById)).toList();
        return switch (group) {
            case NONE -> sorted.isEmpty() ? List.of() : List.of(new Section(null, sorted));
            case STATUS -> byStatus(sorted);
            case TASK -> byTask(sorted, tasksById);
        };
    }

    private static List<Section> byStatus(final List<StudyGoal> sorted) {
        Map<Status, List<StudyGoal>> groups = new LinkedHashMap<>();
        for (Status status : Status.values()) {
            groups.put(status, new ArrayList<>());
        }
        sorted.forEach(goal -> groups.get(Status.of(goal)).add(goal));
        List<Section> sections = new ArrayList<>();
        groups.forEach((status, members) -> {
            if (!members.isEmpty()) {
                sections.add(new Section(status.label, members));
            }
        });
        return sections;
    }

    private static List<Section> byTask(final List<StudyGoal> sorted, final Map<String, Task> tasksById) {
        // A null key collects the goals without a task; LinkedHashMap keeps first-appearance order.
        Map<Task, List<StudyGoal>> groups = new LinkedHashMap<>();
        sorted.forEach(goal -> groups.computeIfAbsent(task(goal, tasksById), key -> new ArrayList<>()).add(goal));
        List<Section> sections = new ArrayList<>();
        groups.forEach((task, members) -> sections.add(new Section(
                task == null ? NO_TASK : CalendarAgenda.taskTitle(task, tasksById), members)));
        return sections;
    }

    private static Comparator<StudyGoal> comparator(final Sort sort, final Map<String, Task> tasksById) {
        Comparator<StudyGoal> status = Comparator.comparing(Status::of);
        // A task without a deadline ties with no task at all; keep goals without a task after it.
        Comparator<StudyGoal> withTaskFirst = Comparator.comparing(goal -> task(goal, tasksById) == null);
        Comparator<StudyGoal> title = Comparator.comparing(StudyGoal::getDescription, String.CASE_INSENSITIVE_ORDER);
        Comparator<StudyGoal> taskTitle = Comparator.comparing(goal -> {
            Task task = task(goal, tasksById);
            return task == null ? null : CalendarAgenda.taskTitle(task, tasksById);
        }, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        return switch (sort) {
            case STATUS -> status.thenComparing(taskTitle).thenComparing(title);
            case PRIORITY -> Comparator.comparingInt((StudyGoal goal) -> {
                Task task = task(goal, tasksById);
                return task == null || task.getPriority() == null ? 0 : task.getPriority().stars();
            }).reversed().thenComparing(status).thenComparing(taskTitle).thenComparing(title);
            case DEADLINE -> Comparator.comparing((StudyGoal goal) -> {
                Task task = task(goal, tasksById);
                return task == null || task.getDeadline() == null ? LocalDate.MAX : task.getDeadline();
            }).thenComparing(withTaskFirst).thenComparing(status).thenComparing(taskTitle).thenComparing(title);
            case TITLE -> title.thenComparing(status);
        };
    }

    private static Task task(final StudyGoal goal, final Map<String, Task> tasksById) {
        return goal.getTaskId() == null ? null : tasksById.get(goal.getTaskId());
    }
}
