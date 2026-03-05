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
    LocalDate startDate
) {
    /** Convenience constructor without recurring pattern or start date (preserves existing). */
    public TaskUpdate(String title, String description, String category, TaskPriority priority, LocalDate deadline) {
        this(title, description, category, priority, deadline, null, null);
    }

    /** Convenience constructor without start date (preserves existing). */
    public TaskUpdate(String title, String description, String category, TaskPriority priority, LocalDate deadline, String recurringPattern) {
        this(title, description, category, priority, deadline, recurringPattern, null);
    }
}
