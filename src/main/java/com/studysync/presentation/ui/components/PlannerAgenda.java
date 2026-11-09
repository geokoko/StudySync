package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.Task;
import com.studysync.domain.service.MissedOccurrence;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * The planner's available work, evaluated against an explicit today. Unlike the
 * calendar, overdue work stays visible on every selected day after its deadline.
 * Carry-forward, reminders and undated tasks are today-only; missed goals can be retried on
 * today or any later day.
 *
 * <p>A dated attempt marks its own task and its parent as planned (work on a subtask
 * is work on the family), never its siblings. Active tasks remain available for more
 * goals, even if planned work is their only reason to appear. Multiple reasons become
 * one task row; missed goals remain individually retryable until that goal is planned,
 * but only while their task is active: a completed, cancelled or postponed task's
 * missed goals are moot. Retry candidates and unhandled occurrences come from the
 * existing domain queries so goal history
 * (including achieved retries) remains the source of truth.</p>
 *
 * <p>Tasks are laid out by family ({@link Plan}). A listed subtask joins a {@link Block}
 * headed by its parent: by the parent's own plannable entry when the parent is listed
 * ({@link Block#header()}), otherwise by its title alone; the subtasks follow in
 * {@link TaskListAgenda#TASK_ORDER}. When a parent is listed, each
 * of its active subtasks without a reason of its own joins the block as a {@link Kind#SUBTASK}
 * ("due Fri" within seven days of the selected date, "due Nov 11" later, or "no deadline"),
 * so the family can be planned from one place. Blocks come first, ordered by their most urgent
 * member, then one block of standalone tasks. A block goes under "Coming up" only when every
 * member listed for its own reason is a {@link Kind#REMINDER}; standalone reminders go there
 * too. Missed goals without a task stay apart in {@link Plan#taskless()}.</p>
 *
 * <p>Separately, {@link #scheduled} answers "which recurring tasks are on this day", on any
 * day: the schedule decides, never deadlines, lateness or carry-forward, and each occurrence
 * is judged only by its own task's goals dated that day.</p>
 */
final class PlannerAgenda {

    enum Kind {
        OVERDUE, DUE, MISSED, RETRY, RECURRING, OPEN, REMINDER, PLANNED,
        /** An active subtask listed only because its parent is. */
        SUBTASK
    }

    record Entry(Kind kind, String title, String reason, Task task, List<StudyGoal> retries,
                 LocalDate relevantDate) {
        String key() {
            return task == null ? "goal:" + retries.getFirst().getId() : "task:" + task.getId();
        }

        /** The tile's title: inside a family block the parent's title is the block's heading. */
        String tileTitle() {
            return task == null ? title : task.getTitle();
        }
    }

    /**
     * Entries shown together. A family block has a {@code parent} (listed or not) and lists the
     * parent's entry first, if any; the standalone block has no parent.
     */
    record Block(Task parent, List<Entry> entries) {
        boolean isFamily() {
            return parent != null;
        }

        /** The listed parent's own entry, which heads its family block; null otherwise. */
        Entry header() {
            return isFamily() && !entries.isEmpty() && entries.getFirst().task() == parent
                    ? entries.getFirst() : null;
        }

        /** The block's tiles: every entry but the {@link #header()}. */
        List<Entry> tiles() {
            return header() == null ? entries : entries.subList(1, entries.size());
        }
    }

    /**
     * The planner's layout: {@code entries} holds every entry in urgency order; {@code taskless}
     * the missed goals without a task; {@code needed} and {@code comingUp} the task blocks,
     * families first and then at most one standalone block.
     */
    record Plan(List<Entry> entries, List<Entry> taskless, List<Block> needed, List<Block> comingUp) {
    }

    /** Where a scheduled occurrence stands on its day, declared in display order. */
    enum Occurrence {
        /** No goal for it yet, today or later. */
        NOT_PLANNED,
        /** A past day with no goal for it, or only missed goals. */
        MISSED,
        /** At least one goal still pending. */
        PLANNED,
        /** No goal pending and at least one achieved. */
        DONE,
        /** Its only goals that day were dropped. */
        DROPPED
    }

    /**
     * A recurring task whose schedule lands on the selected day, judged by its own goals dated
     * that day (as {@code StudyGoal.hasHandledGoalForTaskOccurrence} judges an occurrence).
     *
     * @param detail what that day's goals amount to, such as "planned" or "1 of 2 done"
     */
    record Scheduled(Task task, String title, Occurrence state, String detail) {
        String key() {
            return "task:" + task.getId();
        }
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE");
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMM d");
    private static final int WEEK = 7;
    private static final Comparator<StudyGoal> RETRY_ORDER = Comparator.comparing(StudyGoal::getDate)
            .thenComparing(StudyGoal::getDescription, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(StudyGoal::getId);
    private static final Comparator<Entry> ORDER = Comparator.comparing(Entry::kind)
            .thenComparing(Entry::relevantDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(entry -> entry.task() != null && entry.task().getPriority() != null
                    ? -entry.task().getPriority().stars() : 0)
            .thenComparing(Entry::title, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Entry::key);

    private PlannerAgenda() {
    }

    static Plan build(LocalDate date, LocalDate today, List<Task> tasks,
                             List<StudyGoal> attempts, List<StudyGoal> retryable,
                             List<MissedOccurrence> missed, BiPredicate<Task, LocalDate> isOccurrence) {
        Map<String, Task> tasksById = new LinkedHashMap<>();
        tasks.forEach(task -> tasksById.putIfAbsent(task.getId(), task));
        Set<String> plannedTasks = CalendarAgenda.withParents(attempts.stream()
                .filter(goal -> date.equals(goal.getDate()))
                .filter(goal -> !goal.isDropped() && !goal.isDroppedAttempt())
                .map(StudyGoal::getTaskId).collect(Collectors.toSet()), tasksById.values());
        Set<String> plannedGoals = attempts.stream().filter(goal -> date.equals(goal.getDate()))
                .map(StudyGoal::getId).collect(Collectors.toSet());
        Map<String, List<StudyGoal>> retriesByTask = new LinkedHashMap<>();
        List<Entry> entries = new ArrayList<>();
        if (!date.isBefore(today)) {
            retryable.stream().filter(goal -> goal.getStatus() == StudyGoal.GoalStatus.ACTIVE
                            && goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.MISSED
                            // Missed before the planned day: on a later day that includes today's misses.
                            && goal.getDate().isBefore(date) && !plannedGoals.contains(goal.getId()))
                    .sorted(RETRY_ORDER).forEach(goal -> {
                        if (!tasksById.containsKey(goal.getTaskId())) {
                            entries.add(new Entry(Kind.RETRY, goal.getDescription(), "retry", null,
                                    List.of(goal), goal.getDate()));
                        } else {
                            retriesByTask.computeIfAbsent(goal.getTaskId(), key -> new ArrayList<>()).add(goal);
                        }
                    });
        }
        Set<String> missedTaskIds = missed.stream()
                .filter(occurrence -> occurrence.missedDate().equals(today.minusDays(1)))
                .map(occurrence -> occurrence.task().getId()).collect(Collectors.toSet());

        for (Task task : tasksById.values()) {
            List<String> reasons = new ArrayList<>();
            Kind kind = null;
            LocalDate relevantDate = null;
            LocalDate deadline = task.getDeadline();
            boolean active = CalendarAgenda.isActive(task.getStatus());
            boolean started = !task.isRecurring() || task.getStartDate() == null
                    || !date.isBefore(task.getStartDate());
            if (active && started && deadline != null && !deadline.isAfter(date)) {
                kind = deadline.isBefore(date) ? Kind.OVERDUE : Kind.DUE;
                reasons.add(deadline.isBefore(date)
                        ? ChronoUnit.DAYS.between(deadline, date) + "d late"
                        : date.equals(today) ? "due today" : "due " + date.format(DAY));
                relevantDate = deadline;
            }
            if (active && task.isRecurring() && date.equals(today) && missedTaskIds.contains(task.getId())) {
                if (kind == null) {
                    kind = Kind.MISSED;
                    relevantDate = today.minusDays(1);
                }
                reasons.add("missed " + today.minusDays(1).format(DAY));
            }
            List<StudyGoal> retries = active ? retriesByTask.getOrDefault(task.getId(), List.of()) : List.of();
            if (!retries.isEmpty()) {
                if (kind == null) {
                    kind = Kind.RETRY;
                    relevantDate = retries.getFirst().getDate();
                }
                reasons.add(retries.size() == 1 ? "retry" : retries.size() + " retries");
            }
            if (active && isOccurrence.test(task, date)) {
                if (kind == null) {
                    kind = Kind.RECURRING;
                    relevantDate = date;
                }
                reasons.add("scheduled " + date.format(DAY));
            }
            if (active && !task.isRecurring() && deadline == null && date.equals(today)) {
                if (kind == null) {
                    kind = Kind.OPEN;
                }
                reasons.add("no deadline");
            }
            if (active && date.equals(today) && deadline != null && deadline.isAfter(date)
                    && task.isReminderDue(date)) {
                if (kind == null) {
                    kind = Kind.REMINDER;
                    relevantDate = deadline;
                }
                reasons.add("due " + deadline.format(DateTimeFormatter.ofPattern("EEE, MMM d")));
            }
            if (active && plannedTasks.contains(task.getId())) {
                if (kind == null) {
                    kind = Kind.PLANNED;
                    relevantDate = date;
                }
                reasons.add("work planned");
            }
            if (kind != null) {
                entries.add(new Entry(kind, CalendarAgenda.taskTitle(task, tasksById),
                        String.join(" · ", reasons), task, List.copyOf(retries), relevantDate));
            }
        }
        addUnplannedSubtasks(date, entries, tasksById);
        return layout(entries.stream().sorted(ORDER).toList(), tasksById);
    }

    /** A listed parent brings along its active subtasks that have no reason of their own. */
    private static void addUnplannedSubtasks(LocalDate date, List<Entry> entries, Map<String, Task> tasksById) {
        Set<String> listed = entries.stream().filter(entry -> entry.task() != null)
                .map(entry -> entry.task().getId()).collect(Collectors.toSet());
        for (Task child : tasksById.values()) {
            if (child.getParentId() != null && listed.contains(child.getParentId())
                    && !listed.contains(child.getId()) && CalendarAgenda.isActive(child.getStatus())) {
                entries.add(new Entry(Kind.SUBTASK, CalendarAgenda.taskTitle(child, tasksById),
                        dueText(child.getDeadline(), date), child, List.of(), child.getDeadline()));
            }
        }
    }

    /**
     * The recurring tasks scheduled on {@code date}: every active one whose schedule lands there,
     * plus any other with its own goal dated that day, so a past day keeps work done on a task
     * since completed. Ordered by {@link Occurrence}, then title.
     */
    static List<Scheduled> scheduled(LocalDate date, LocalDate today, List<Task> tasks,
                                     List<StudyGoal> attempts, BiPredicate<Task, LocalDate> isOccurrence) {
        Map<String, Task> tasksById = new LinkedHashMap<>();
        tasks.forEach(task -> tasksById.putIfAbsent(task.getId(), task));
        Map<String, List<StudyGoal>> goalsByTask = attempts.stream()
                .filter(goal -> date.equals(goal.getDate()) && goal.getTaskId() != null)
                .collect(Collectors.groupingBy(StudyGoal::getTaskId));
        List<Scheduled> scheduled = new ArrayList<>();
        for (Task task : tasksById.values()) {
            List<StudyGoal> goals = goalsByTask.getOrDefault(task.getId(), List.of());
            if (!task.isRecurring() || !isOccurrence.test(task, date)
                    || !CalendarAgenda.isActive(task.getStatus()) && goals.isEmpty()) {
                continue;
            }
            scheduled.add(occurrence(task, CalendarAgenda.taskTitle(task, tasksById), goals, date.isBefore(today)));
        }
        scheduled.sort(Comparator.comparing(Scheduled::state)
                .thenComparing(Scheduled::title, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Scheduled::key));
        return List.copyOf(scheduled);
    }

    private static Scheduled occurrence(Task task, String title, List<StudyGoal> goals, boolean past) {
        // One entry per goal: a goal missed and retried on the same day has two attempts that day,
        // and it counts as its best outcome there (achieved, else pending, else missed).
        Map<String, StudyGoal> byGoal = new LinkedHashMap<>();
        goals.stream().filter(goal -> !goal.isDroppedAttempt()).forEach(goal ->
                byGoal.merge(goal.getId(), goal, (first, second) ->
                        outcomeRank(second) < outcomeRank(first) ? second : first));
        List<StudyGoal> kept = List.copyOf(byGoal.values());
        if (kept.isEmpty()) {
            return !goals.isEmpty() ? new Scheduled(task, title, Occurrence.DROPPED, "dropped")
                    : past ? new Scheduled(task, title, Occurrence.MISSED, "not done")
                    : new Scheduled(task, title, Occurrence.NOT_PLANNED, "not planned");
        }
        long done = kept.stream().filter(goal -> goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.ACHIEVED).count();
        long pending = kept.stream().filter(goal -> goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.PENDING).count();
        String progress = done + " of " + kept.size() + " done";
        if (pending > 0) {
            return new Scheduled(task, title, Occurrence.PLANNED,
                    done > 0 ? progress : kept.size() == 1 ? "planned" : kept.size() + " goals planned");
        }
        if (done > 0) {
            return new Scheduled(task, title, Occurrence.DONE, done == kept.size() ? "done" : progress);
        }
        return new Scheduled(task, title, Occurrence.MISSED, kept.size() == 1 ? "goal missed" : kept.size() + " goals missed");
    }

    private static int outcomeRank(StudyGoal attempt) {
        StudyGoal.AttemptOutcome outcome = attempt.getAttemptOutcome();
        return outcome == StudyGoal.AttemptOutcome.ACHIEVED ? 0 : outcome == StudyGoal.AttemptOutcome.PENDING ? 1 : 2;
    }

    private static String dueText(LocalDate deadline, LocalDate date) {
        if (deadline == null) {
            return "no deadline";
        }
        boolean thisWeek = deadline.isAfter(date) && !deadline.isAfter(date.plusDays(WEEK));
        return "due " + deadline.format(thisWeek ? DAY : MONTH_DAY);
    }

    private static Plan layout(List<Entry> entries, Map<String, Task> tasksById) {
        List<Entry> taskless = entries.stream().filter(entry -> entry.task() == null).toList();
        Map<String, List<Entry>> byFamily = new LinkedHashMap<>();
        entries.stream().filter(entry -> entry.task() != null).forEach(entry -> byFamily
                .computeIfAbsent(familyKey(entry.task(), tasksById), key -> new ArrayList<>()).add(entry));
        List<Block> needed = new ArrayList<>();
        List<Block> comingUp = new ArrayList<>();
        List<Entry> standaloneNeeded = new ArrayList<>();
        List<Entry> standaloneComingUp = new ArrayList<>();
        for (Map.Entry<String, List<Entry>> family : byFamily.entrySet()) {
            List<Entry> members = family.getValue();
            Task parent = tasksById.get(family.getKey());
            boolean hasSubtask = members.stream().anyMatch(entry -> entry.task() != parent);
            boolean reminder = members.stream().filter(entry -> entry.kind() != Kind.SUBTASK)
                    .allMatch(entry -> entry.kind() == Kind.REMINDER);
            if (!hasSubtask) {
                (reminder ? standaloneComingUp : standaloneNeeded).addAll(members);
                continue;
            }
            List<Entry> ordered = members.stream().sorted(Comparator
                    .comparing((Entry entry) -> entry.task() != parent)
                    .thenComparing(Entry::task, TaskListAgenda.TASK_ORDER)).toList();
            (reminder ? comingUp : needed).add(new Block(parent, ordered));
        }
        Comparator<Block> urgency = Comparator.comparing(
                block -> block.entries().stream().min(ORDER).orElseThrow(), ORDER);
        needed.sort(urgency);
        comingUp.sort(urgency);
        if (!standaloneNeeded.isEmpty()) {
            needed.add(new Block(null, List.copyOf(standaloneNeeded)));
        }
        if (!standaloneComingUp.isEmpty()) {
            comingUp.add(new Block(null, List.copyOf(standaloneComingUp)));
        }
        return new Plan(entries, taskless, List.copyOf(needed), List.copyOf(comingUp));
    }

    /** A subtask belongs to its parent's family; a task whose parent is not loaded stands alone. */
    private static String familyKey(Task task, Map<String, Task> tasksById) {
        return task.getParentId() != null && tasksById.containsKey(task.getParentId())
                ? task.getParentId() : task.getId();
    }
}
