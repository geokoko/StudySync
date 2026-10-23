package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.Task;
import com.studysync.domain.valueobject.TaskStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * What a calendar day lists, by name: the plan for today and later, and what
 * actually happened on past days. Parents and children use their own dates;
 * child titles include their parent for context.
 *
 * <p>Deliberately not {@code TaskService.getTasksForDate}. That answers "what
 * should the planner list on this day", so it keeps a late task on every day
 * after its deadline and judges past days by the task's status today. A
 * calendar drawn from it repeats the same overdue task in every cell, future
 * ones included, and loses a task from the days it was worked on as soon as it
 * is completed. Here a late task appears on its deadline and on today only,
 * and a past day is built from what is dated on it.</p>
 */
final class CalendarAgenda {

    /** Kinds of line, declared in display order: the most pressing first. */
    enum Kind {
        /** Unresolved task past its deadline; listed on today only. */
        OVERDUE,
        /** Unresolved task due that day. */
        DUE,
        GOAL_PENDING,
        /** Scheduled occurrence of an unresolved recurring task. */
        RECURRING,
        /** Past occurrence with no goal attempt for it that day. */
        RECURRING_MISSED,
        /** Missed attempt, including misses before a goal was dropped. */
        GOAL_MISSED,
        GOAL_DROPPED,
        GOAL_ACHIEVED,
        /** Task completed that day. */
        COMPLETED
    }

    /**
     * One line of a calendar day.
     *
     * @param title  the task's title, or the goal's description when the goal has no task
     * @param detail the goal's description under its task, or how late an overdue task is
     * @param task   the task the line is about, when there is one
     * @param goal   the goal attempt the line is about, when there is one
     */
    record Entry(Kind kind, String title, String detail, Task task, StudyGoal goal) {

        /** Only a pending attempt can be dragged to another day. */
        boolean movable() {
            return kind == Kind.GOAL_PENDING;
        }

        String text() {
            return detail == null ? title : title + " · " + detail;
        }
    }

    /**
     * What a day cell shows for one task: every line of that day about the
     * task, folded into one (goals become a count). Goals without a task share
     * a line. The day panel still lists each entry.
     *
     * @param kind    the most pressing of the folded entries
     * @param entries the folded entries, in display order
     */
    record TaskLine(Kind kind, String title, String detail, Task task, List<Entry> entries) {

        List<StudyGoal> goals() {
            return entries.stream().map(Entry::goal).filter(goal -> goal != null).toList();
        }

        /** The goal a drag moves: only a line that is nothing but one pending goal can be dragged. */
        StudyGoal movableGoal() {
            return entries.size() == 1 && entries.getFirst().movable() ? entries.getFirst().goal() : null;
        }

        String text() {
            return detail == null ? title : title + " · " + detail;
        }
    }

    private static final Comparator<Entry> DISPLAY_ORDER = Comparator.comparing(Entry::kind)
            .thenComparing(Entry::title, String.CASE_INSENSITIVE_ORDER);
    private static final Comparator<TaskLine> LINE_ORDER = Comparator.comparing(TaskLine::kind)
            .thenComparing(TaskLine::title, String.CASE_INSENSITIVE_ORDER);

    private CalendarAgenda() {
    }

    /**
     * Same notion of "still open" as the planner: postponed and cancelled
     * tasks are out of the plan.
     */
    static boolean isActive(TaskStatus status) {
        return status == TaskStatus.OPEN || status == TaskStatus.IN_PROGRESS || status == TaskStatus.DELAYED;
    }

    /**
     * The tasks that goals on {@code plannedTaskIds} count as work on: each of
     * those tasks and, for a subtask, its parent (work on a subtask is work on
     * the family), never its siblings. Shared by the planner and the calendar's
     * "To plan" list so the two agree on what is planned.
     */
    static Set<String> withParents(Set<String> plannedTaskIds, Collection<Task> tasks) {
        Set<String> covered = new HashSet<>(plannedTaskIds);
        tasks.stream().filter(task -> task.getParentId() != null && plannedTaskIds.contains(task.getId()))
                .forEach(task -> covered.add(task.getParentId()));
        return covered;
    }

    /**
     * @param attempts     goal attempts planned within the range
     * @param isOccurrence whether a recurring task's schedule lands on a date
     * @return every date in {@code [from, to]}, in order, with its lines in display order
     */
    static Map<LocalDate, List<Entry>> build(LocalDate from, LocalDate to, LocalDate today,
                                             List<Task> tasks, List<StudyGoal> attempts,
                                             BiPredicate<Task, LocalDate> isOccurrence) {
        Map<LocalDate, List<Entry>> days = new LinkedHashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            days.put(date, new ArrayList<>());
        }
        Map<String, Task> tasksById = new LinkedHashMap<>();
        tasks.forEach(task -> tasksById.putIfAbsent(task.getId(), task));

        // A goal attempt for a task already says the task is worked on that
        // day, so the task's recurring line would only repeat it.
        Set<String> taskDaysWithGoal = new HashSet<>();
        for (StudyGoal attempt : attempts) {
            List<Entry> day = days.get(attempt.getDate());
            if (day == null) {
                continue;
            }
            if (attempt.getTaskId() != null && !attempt.isDroppedAttempt()) {
                taskDaysWithGoal.add(attempt.getTaskId() + "|" + attempt.getDate());
            }
            day.add(goalEntry(attempt, tasksById.get(attempt.getTaskId()), tasksById));
        }

        for (Task task : tasks) {
            boolean active = isActive(task.getStatus());
            LocalDate deadline = task.getDeadline();
            if (active && deadline != null) {
                add(days, deadline, new Entry(Kind.DUE, taskTitle(task, tasksById), null, task, null));
                if (deadline.isBefore(today)) {
                    long daysLate = ChronoUnit.DAYS.between(deadline, today);
                    add(days, today, new Entry(Kind.OVERDUE, taskTitle(task, tasksById), daysLate + "d late", task, null));
                }
            }
            if (task.getStatus() == TaskStatus.COMPLETED && task.getCompletedAt() != null) {
                add(days, task.getCompletedAt(), new Entry(Kind.COMPLETED, taskTitle(task, tasksById), null, task, null));
            }
            if (active && task.isRecurring()) {
                for (LocalDate date : days.keySet()) {
                    // The deadline line already covers an occurrence on the due day.
                    if (date.equals(deadline) || taskDaysWithGoal.contains(task.getId() + "|" + date)
                            || !isOccurrence.test(task, date)) {
                        continue;
                    }
                    Kind kind = date.isBefore(today) ? Kind.RECURRING_MISSED : Kind.RECURRING;
                    days.get(date).add(new Entry(kind, taskTitle(task, tasksById), null, task, null));
                }
            }
        }

        days.values().forEach(day -> day.sort(DISPLAY_ORDER));
        return days;
    }

    private static Entry goalEntry(StudyGoal attempt, Task task, Map<String, Task> tasksById) {
        Kind kind = attempt.getAttemptOutcome() == StudyGoal.AttemptOutcome.ACHIEVED ? Kind.GOAL_ACHIEVED
                : attempt.isFailed() ? Kind.GOAL_MISSED
                : attempt.isDroppedAttempt() ? Kind.GOAL_DROPPED
                : Kind.GOAL_PENDING;
        return task != null
                ? new Entry(kind, taskTitle(task, tasksById), attempt.getDescription(), task, attempt)
                : new Entry(kind, attempt.getDescription(), null, null, attempt);
    }

    /** One line per task for a day's entries, most pressing first; see {@link TaskLine}. */
    static List<TaskLine> byTask(List<Entry> day) {
        Map<String, List<Entry>> groups = new LinkedHashMap<>();
        for (Entry entry : day) {
            String key = entry.task() != null ? "task:" + entry.task().getId() : "unlinked";
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
        }
        List<TaskLine> lines = new ArrayList<>();
        groups.values().forEach(entries -> {
            entries.sort(DISPLAY_ORDER);
            Entry first = entries.getFirst();
            List<String> details = new ArrayList<>();
            entries.stream().filter(entry -> entry.kind() == Kind.OVERDUE).findFirst()
                    .ifPresent(entry -> details.add(entry.detail()));
            long total = entries.stream().filter(entry -> entry.goal() != null).count();
            long dropped = entries.stream().filter(entry -> entry.kind() == Kind.GOAL_DROPPED).count();
            long goals = total - dropped;
            long done = entries.stream().filter(entry -> entry.kind() == Kind.GOAL_ACHIEVED).count();
            String title = first.task() != null ? first.title()
                    : total == 1 ? first.title() : "Goals without a task";
            if (first.task() != null || total > 1) {
                if (done == goals && goals > 0) {
                    details.add(count(goals, "goal") + " done");
                } else if (done > 0) {
                    details.add(done + " of " + count(goals, "goal") + " done");
                } else if (goals > 0) {
                    details.add(count(goals, "goal"));
                }
            }
            if (dropped > 0) {
                details.add(count(dropped, "goal") + " dropped");
            }
            lines.add(new TaskLine(first.kind(), title, details.isEmpty() ? null : String.join(" · ", details),
                    first.task(), List.copyOf(entries)));
        });
        lines.sort(LINE_ORDER);
        return lines;
    }

    private static String count(long n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    /** "Parent / Subtask", or the task's own title when it has no parent in {@code tasksById}. */
    static String taskTitle(Task task, Map<String, Task> tasksById) {
        Task parent = task.getParentId() == null ? null : tasksById.get(task.getParentId());
        return parent == null ? task.getTitle() : parent.getTitle() + " / " + task.getTitle();
    }

    private static void add(Map<LocalDate, List<Entry>> days, LocalDate date, Entry entry) {
        List<Entry> day = days.get(date);
        if (day != null) {
            day.add(entry);
        }
    }
}
