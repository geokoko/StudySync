package com.studysync.domain.valueobject;

/**
 * Enumeration representing the various states a task can be in during its lifecycle.
 * 
 * <p>This enum defines the possible status values for tasks in the StudySync system,
 * representing the progression from creation to completion. The status helps users
 * track progress and organize their work effectively.</p>
 * 
 * <p>Status transitions typically follow this flow:
 * <pre>
 * OPEN → IN_PROGRESS → COMPLETED
 *   ↓         ↓
 * POSTPONED   ↓
 *   ↓         ↓
 * DELAYED ←───┘
 * </pre></p>
 * 
 * <p><strong>Status Meanings:</strong>
 * <ul>
 *   <li><strong>OPEN:</strong> Task created but not started</li>
 *   <li><strong>IN_PROGRESS:</strong> Task is actively being worked on</li>
 *   <li><strong>POSTPONED:</strong> Task deliberately delayed by user</li>
 *   <li><strong>COMPLETED:</strong> Task finished successfully</li>
 *   <li><strong>DELAYED:</strong> Task missed its deadline (auto-assigned)</li>
 * </ul></p>
 * 
 * @author StudySync Development Team
 * @version 0.1.0-BETA
 * @since 0.1.0
 * @see Task
 */
public enum TaskStatus {
    
    /** 
     * Initial status when a task is created but not yet started.
     * This is the default status for new tasks.
     */
    OPEN("Open", "Task is ready to be started"),
    
    /** 
     * Status indicating the task is currently being worked on.
     * Users manually set this status when they begin work.
     */
    IN_PROGRESS("In Progress", "Task is actively being worked on"),
    
    /** 
     * Status for tasks that have been intentionally delayed by the user.
     * Different from DELAYED which is system-assigned for overdue tasks.
     */
    POSTPONED("Postponed", "Task has been deliberately delayed"),
    
    /** 
     * Final status indicating the task has been successfully completed.
     * Completed tasks may earn points in the gamification system.
     */
    COMPLETED("Completed", "Task has been finished successfully"),
    
    /** 
     * System-assigned status for tasks that have missed their deadline.
     * This status is automatically set by the system for overdue tasks.
     */
    DELAYED("Delayed", "Task has missed its deadline"),
    
    /** 
     * Status indicating the task has been cancelled and will not be completed.
     * Used when a task is no longer needed or relevant.
     */
    CANCELLED("Cancelled", "Task has been cancelled and will not be completed");
    
    /** Human-readable display name for the status. */
    private final String displayName;
    
    /** Detailed description of what this status means. */
    private final String description;
    
    /**
     * Creates a TaskStatus with display name and description.
     * 
     * @param displayName the human-readable name for UI display
     * @param description detailed explanation of the status
     */
    TaskStatus(final String displayName, final String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    /**
     * Gets the human-readable display name for this status.
     * 
     * @return the display name suitable for UI presentation
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Gets the detailed description of this status.
     * 
     * @return a description explaining what this status means
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Determines if this status represents a completed task.
     * 
     * @return true if the status is COMPLETED, false otherwise
     */
    public boolean isCompleted() {
        return this == COMPLETED;
    }
    
    /**
     * Determines if this status represents an active task.
     * Active tasks are those that are not completed and not postponed.
     * 
     * @return true if the task is in an active state
     */
    public boolean isActive() {
        return this == OPEN || this == IN_PROGRESS || this == DELAYED;
    }
    
    /**
     * Determines if this status was system-assigned.
     * Currently only DELAYED status is automatically assigned by the system.
     * 
     * @return true if this status is set automatically by the system
     */
    public boolean isSystemAssigned() {
        return this == DELAYED;
    }
}
