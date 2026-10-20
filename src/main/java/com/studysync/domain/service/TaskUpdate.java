package com.studysync.domain.service;

import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.domain.valueobject.TaskStatus;
import com.studysync.domain.entity.TaskLink;
import java.time.LocalDate;
import java.util.List;

public record TaskUpdate(
    String title,
    String description,
    String category,
    TaskPriority priority,
    LocalDate deadline,
    String recurringPattern,
    LocalDate startDate,
    LocalDate recurrenceEndDate,
    Integer remindDaysBefore,
    /** What finishing the task means; {@code null} keeps the existing text, blank clears it. */
    String doneCriteria,
    /** Null keeps the parent; blank makes the task standalone. */
    String parentId,
    /** Null keeps the estimate; CLEAR_ESTIMATE removes it. */
    Integer estimatedHours,
    /** Null keeps notes; empty text clears them without trimming Markdown. */
    String notes,
    /** Null keeps links; an empty list removes them all. */
    List<TaskLink> links,
    TaskStatus status
) {
    public static final Integer CLEAR_ESTIMATE = -1;

    public TaskUpdate(String title, String description, String category, TaskPriority priority, LocalDate deadline,
                      String recurringPattern, LocalDate startDate, LocalDate recurrenceEndDate,
                      Integer remindDaysBefore, String doneCriteria) {
        this(title, description, category, priority, deadline, recurringPattern, startDate, recurrenceEndDate,
                remindDaysBefore, doneCriteria, null, null, null, null, null);
    }
    /**
     * Value of {@link #remindDaysBefore} that clears an existing reminder.
     *
     * <p>Needed because {@code null} already means "leave alone" for every
     * field on this record, so it cannot also mean "remove the reminder" -
     * without a sentinel, a reminder could be set and edited but never taken
     * off. Mirrors the empty-string convention {@code recurringPattern} uses
     * for the same reason.</p>
     */
    public static final Integer CLEAR_REMINDER = -1;

    /** Convenience constructor without recurring pattern or start date (preserves existing). */
    public TaskUpdate(String title, String description, String category, TaskPriority priority, LocalDate deadline) {
        this(title, description, category, priority, deadline, null, null, null, null, null);
    }

    /** Convenience constructor without start date (preserves existing). */
    public TaskUpdate(String title, String description, String category, TaskPriority priority, LocalDate deadline,
                      String recurringPattern) {
        this(title, description, category, priority, deadline, recurringPattern, null, null, null, null);
    }

    /** Convenience constructor without a reminder offset (preserves existing). */
    public TaskUpdate(String title, String description, String category, TaskPriority priority, LocalDate deadline,
                      String recurringPattern, LocalDate startDate, LocalDate recurrenceEndDate) {
        this(title, description, category, priority, deadline, recurringPattern, startDate, recurrenceEndDate,
                null, null);
    }

    /** Convenience constructor without an end-of-recurrence date (preserves existing). */
    public TaskUpdate(String title, String description, String category, TaskPriority priority, LocalDate deadline,
                      String recurringPattern, LocalDate startDate) {
        this(title, description, category, priority, deadline, recurringPattern, startDate, null, null, null);
    }
}
