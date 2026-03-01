package com.studysync.domain.service;

import com.studysync.domain.valueobject.TaskPriority;
import java.time.LocalDate;

public record TaskUpdate(
    String title,
    String description,
    String category,
    TaskPriority priority,
    LocalDate deadline,
    String recurringPattern
) {
    /** Convenience constructor without recurring pattern (preserves existing). */
    public TaskUpdate(String title, String description, String category, TaskPriority priority, LocalDate deadline) {
        this(title, description, category, priority, deadline, null);
    }
}
