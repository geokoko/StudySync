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
 * The planner's unplanned work, evaluated against an explicit today. Unlike the
 * calendar, overdue work stays visible on every selected day after its deadline.
 * Carry-forward, reminders, retries and undated tasks are today-only.
 *
 * <p>A dated attempt covers its own task and its parent (work on a subtask is work
 * on the family), never its siblings. Multiple reasons become one task row; multiple
 * missed goals remain individually retryable, but only while their task is active:
 * a completed, cancelled or postponed task's missed goals are moot. Retry candidates
 * and unhandled occurrences come from the existing domain queries so goal history
 * (including achieved retries) remains the source of truth.</p>
 */
final class PlannerAgenda {

    enum Kind {
        OVERDUE, DUE, MISSED, RETRY, RECURRING, OPEN, REMINDER
    }

    record Entry(Kind kind, String title, String reason, Task task, List<StudyGoal> retries,
                 LocalDate relevantDate) {
        String key() {
            return task == null ? "goal:" + retries.getFirst().getId() : "task:" + task.getId();
        }
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE");
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

    static List<Entry> build(LocalDate date, LocalDate today, List<Task> tasks,
                             List<StudyGoal> attempts, List<StudyGoal> retryable,
                             List<MissedOccurrence> missed, BiPredicate<Task, LocalDate> isOccurrence) {
        Map<String, Task> tasksById = new LinkedHashMap<>();
        tasks.forEach(task -> tasksById.putIfAbsent(task.getId(), task));
        Set<String> plannedTasks = CalendarAgenda.withParents(attempts.stream()
                .filter(goal -> date.equals(goal.getDate()))
                .map(StudyGoal::getTaskId).collect(Collectors.toSet()), tasksById.values());
        Set<String> plannedGoals = attempts.stream().filter(goal -> date.equals(goal.getDate()))
                .map(StudyGoal::getId).collect(Collectors.toSet());
        Map<String, List<StudyGoal>> retriesByTask = new LinkedHashMap<>();
        List<Entry> entries = new ArrayList<>();
        if (date.equals(today)) {
            retryable.stream().filter(goal -> goal.getStatus() == StudyGoal.GoalStatus.ACTIVE
                            && goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.MISSED
                            && goal.getDate().isBefore(today) && !plannedGoals.contains(goal.getId()))
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
            if (plannedTasks.contains(task.getId())) {
                continue;
            }
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
            if (kind != null) {
                entries.add(new Entry(kind, CalendarAgenda.taskTitle(task, tasksById),
                        String.join(" · ", reasons), task, List.copyOf(retries), relevantDate));
            }
        }
        return entries.stream().sorted(ORDER).toList();
    }
}
