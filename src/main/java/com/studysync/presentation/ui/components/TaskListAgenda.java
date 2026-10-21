package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.Task;
import com.studysync.domain.valueobject.TaskStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Deadline-based task list, keeping each parent and all its children together.
 * Today is separate from the next seven days (tomorrow through today + 7).
 * Recurrence does not invent a deadline: undated recurring tasks stay in
 * No deadline, even when their schedule has ended.
 *
 * <p>The most urgent active member places and orders a family. With no active
 * work, a postponed member puts it in Postponed; otherwise the parent's terminal
 * status decides. Completed/cancelled children never make active work overdue.
 * Filters include a whole family when any member matches, and counts include
 * every displayed task. Children follow their parent in deadline/priority/title
 * order, regardless of status.</p>
 */
final class TaskListAgenda {

    enum Section {
        OVERDUE("Overdue", false),
        TODAY("Today", false),
        THIS_WEEK("This week", false),
        LATER("Later", false),
        NO_DEADLINE("No deadline", false),
        POSTPONED("Postponed", true),
        COMPLETED("Completed", true),
        CANCELLED("Cancelled", true);

        final String title;
        final boolean collapsed;

        Section(String title, boolean collapsed) {
            this.title = title;
            this.collapsed = collapsed;
        }
    }

    record Family(Task parent, List<Task> children, Task urgentMember) {
        List<Task> tasks() {
            return Stream.concat(Stream.of(parent), children.stream()).toList();
        }
    }

    private static final Comparator<Task> TASK_ORDER = Comparator
            .comparing(Task::getDeadline, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(task -> task.getPriority() == null ? 0 : -task.getPriority().stars())
            .thenComparing(Task::getTitle, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(Task::getId);

    private TaskListAgenda() {
    }

    static Map<Section, List<Family>> build(LocalDate today, List<Task> tasks, Predicate<Task> matches) {
        Map<Section, List<Family>> sections = new EnumMap<>(Section.class);
        for (Section section : Section.values()) sections.put(section, new ArrayList<>());

        Map<String, Task> byId = new HashMap<>();
        Map<String, List<Task>> children = new HashMap<>();
        for (Task task : tasks) {
            byId.put(task.getId(), task);
            if (task.getParentId() != null) {
                children.computeIfAbsent(task.getParentId(), id -> new ArrayList<>()).add(task);
            }
        }
        for (Task parent : tasks) {
            if (parent.getParentId() != null && byId.containsKey(parent.getParentId())) continue;
            List<Task> subtasks = children.getOrDefault(parent.getId(), List.of()).stream()
                    .sorted(TASK_ORDER).toList();
            List<Task> members = Stream.concat(Stream.of(parent), subtasks.stream()).toList();
            if (members.stream().noneMatch(matches)) continue;
            Task urgent = members.stream().filter(task -> task.getStatus().isActive())
                    .min(TASK_ORDER).orElseGet(() -> members.stream()
                            .filter(task -> task.getStatus() == TaskStatus.POSTPONED)
                            .min(TASK_ORDER).orElse(parent));
            sections.get(sectionFor(urgent, today)).add(new Family(parent, subtasks, urgent));
        }
        sections.values().forEach(families ->
                families.sort(Comparator.comparing(Family::urgentMember, TASK_ORDER)));
        return sections;
    }

    private static Section sectionFor(Task task, LocalDate today) {
        if (task.getStatus() == TaskStatus.POSTPONED) return Section.POSTPONED;
        if (task.getStatus() == TaskStatus.COMPLETED) return Section.COMPLETED;
        if (task.getStatus() == TaskStatus.CANCELLED) return Section.CANCELLED;
        LocalDate deadline = task.getDeadline();
        if (deadline == null) return Section.NO_DEADLINE;
        if (deadline.isBefore(today)) return Section.OVERDUE;
        if (deadline.equals(today)) return Section.TODAY;
        if (!deadline.isAfter(today.plusDays(7))) return Section.THIS_WEEK;
        return Section.LATER;
    }
}
