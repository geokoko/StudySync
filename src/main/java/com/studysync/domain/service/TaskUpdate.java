package com.studysync.domain.service;

import com.studysync.domain.valueobject.TaskPriority;
import java.time.LocalDate;

public record TaskUpdate(
    String title,
    String description,
    String category,
    TaskPriority priority,
    LocalDate deadline,
    String recurringPattern,
    LocalDate startDate,
    LocalDate recurrenceEndDate,
    Integer remindDaysBefore
) {
    /** Convenience constructor without recurring pattern or start date (preserves existing). */
    public TaskUpdate(String title, String description, String category, TaskPriority priority, LocalDate deadline) {
        this(title, description, category, priority, deadline, null, null, null, null);
    }

    /** Convenience constructor without start date (preserves existing). */
    public TaskUpdate(String title, String description, String category, TaskPriority priority, LocalDate deadline,
                      String recurringPattern) {
        this(title, description, category, priority, deadline, recurringPattern, null, null, null);
    }

    /** Convenience constructor without a reminder offset (preserves existing). */
    public TaskUpdate(String title, String description, String category, TaskPriority priority, LocalDate deadline,
                      String recurringPattern, LocalDate startDate, LocalDate recurrenceEndDate) {
        this(title, description, category, priority, deadline, recurringPattern, startDate, recurrenceEndDate, null);
    }

    /** Convenience constructor without an end-of-recurrence date (preserves existing). */
    public TaskUpdate(String title, String description, String category, TaskPriority priority, LocalDate deadline,
                      String recurringPattern, LocalDate startDate) {
        this(title, description, category, priority, deadline, recurringPattern, startDate, null, null);
    }
}
