package com.studysync.domain.service;

/**
 * Data transfer object for project session end details.
 * Contains information provided by the user when ending a project session.
 */
public record ProjectSessionEnd(
    String sessionTitle,
    String objectives,
    String progress,
    String nextSteps,
    String challenges,
    String notes
) {}
