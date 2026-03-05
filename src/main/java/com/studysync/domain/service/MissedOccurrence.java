package com.studysync.domain.service;

import com.studysync.domain.entity.Task;

import java.time.LocalDate;

/**
 * Represents a single missed occurrence of a recurring task — a past
 * scheduled date where no achieved study goal was linked to the task.
 *
 * @param task       the recurring task
 * @param missedDate the specific past date the occurrence was missed
 */
public record MissedOccurrence(Task task, LocalDate missedDate) { }
