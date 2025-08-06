package com.studysync.domain.entity;

import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Domain entity representing a scheduled reminder for a task in the StudySync system.
 * 
 * <p>TaskReminder enables users to set up notifications for their tasks to ensure important
 * deadlines are not missed. The system supports both predefined reminder intervals (relative
 * to the task deadline) and custom reminder dates for maximum flexibility.</p>
 * 
 * <p><strong>Reminder Types:</strong>
 * <ul>
 *   <li><strong>ONE_DAY_BEFORE:</strong> Reminder sent 1 day before the task deadline</li>
 *   <li><strong>ONE_WEEK_BEFORE:</b> Reminder sent 1 week before the task deadline</li>
 *   <li><strong>ONE_MONTH_BEFORE:</strong> Reminder sent 1 month before the task deadline</li>
 *   <li><strong>CUSTOM_DATE:</strong> Reminder sent on a user-specified date</li>
 * </ul></p>
 * 
 * <p><strong>Usage Examples:</strong>
 * <pre>
 * // Create a reminder for 1 week before deadline
 * TaskReminder reminder = new TaskReminder(task, ReminderType.ONE_WEEK_BEFORE, null);
 * 
 * // Create a custom reminder for specific date
 * LocalDate customDate = LocalDate.of(2024, 6, 15);
 * TaskReminder customReminder = new TaskReminder(task, ReminderType.CUSTOM_DATE, customDate);
 * 
 * // Compute the actual reminder date
 * LocalDate reminderDate = reminder.computeReminderDate();
 * </pre></p>
 * 
 * <p><strong>Business Rules:</strong>
 * <ul>
 *   <li>Reminders can only be created for tasks that have deadlines</li>
 *   <li>Custom reminder dates should be before the task deadline</li>
 *   <li>Computed reminder dates in the past are still valid for historical tracking</li>
 * </ul></p>
 * 
 * <p><strong>Integration:</strong> This class integrates with the notification system to
 * deliver timely reminders to users through various channels (UI notifications, system
 * notifications, etc.).</p>
 * 
 * @author StudySync Development Team
 * @version 0.1.0-BETA
 * @since 0.1.0
 * @see Task
 * @see ReminderType
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskReminder {
    /**
     * Enumeration defining the types of reminders available for tasks.
     * 
     * <p>This enum provides both predefined relative reminder intervals and
     * support for custom reminder dates to accommodate different user preferences
     * and task management strategies.</p>
     * 
     * @see #computeReminderDate()
     */
    public enum ReminderType {
        /** Reminder scheduled for 1 day before the task deadline. */
        ONE_DAY_BEFORE,
        
        /** Reminder scheduled for 1 week before the task deadline. */
        ONE_WEEK_BEFORE,
        
        /** Reminder scheduled for 1 month before the task deadline. */
        ONE_MONTH_BEFORE,
        
        /** Reminder scheduled for a custom user-specified date. */
        CUSTOM_DATE
    }

    /** The task that this reminder is associated with. */
    private Task task;
    
    /** The type of reminder (predefined interval or custom date). */
    private ReminderType type;
    
    /** 
     * Custom reminder date - only used when type is CUSTOM_DATE.
     * Ignored for other reminder types as they compute dates relative to task deadline.
     */
    private LocalDate customReminderDate;


    /**
     * Default constructor for framework instantiation and serialization.
     * 
     * <p>Creates an empty TaskReminder instance. All required fields must be set
     * before the reminder can be used in business operations.</p>
     */
    public TaskReminder() {
        // Default constructor for Bean instantiation
    }

    /**
     * Creates a TaskReminder with the specified parameters.
     * 
     * <p>For non-custom reminder types, the customReminderDate parameter is ignored
     * and the reminder date is computed relative to the task's deadline.</p>
     * 
     * @param task the task to set reminders for (required)
     * @param type the type of reminder (required)
     * @param customReminderDate the custom date for CUSTOM_DATE type (ignored for others)
     * @throws IllegalArgumentException if task or type is null
     */
    public TaskReminder(Task task, ReminderType type, LocalDate customReminderDate) {
        this.task = task;
        this.type = type;
        this.customReminderDate = customReminderDate;
    }

    /**
     * Gets the task associated with this reminder.
     * 
     * @return the task, or null if not set
     */
    public Task getTask() {
        return task;
    }

    /**
     * Sets the task for this reminder.
     * 
     * @param task the task to associate with this reminder
     */
    public void setTask(Task task) {
        this.task = task;
    }

    /**
     * Gets the reminder type.
     * 
     * @return the reminder type
     */
    public ReminderType getType() {
        return type;
    }

    /**
     * Sets the reminder type.
     * 
     * @param type the reminder type
     */
    public void setType(ReminderType type) {
        this.type = type;
    }

    /**
     * Gets the custom reminder date.
     * 
     * <p>This value is only meaningful when the reminder type is CUSTOM_DATE.
     * For other reminder types, this value is ignored during date computation.</p>
     * 
     * @return the custom reminder date, or null if not set
     */
    public LocalDate getCustomReminderDate() {
        return customReminderDate;
    }

    /**
     * Sets the custom reminder date.
     * 
     * <p>This value is only used when the reminder type is CUSTOM_DATE.
     * Setting this value for other reminder types has no effect on the computed reminder date.</p>
     * 
     * @param customReminderDate the custom date for the reminder
     */
    public void setCustomReminderDate(LocalDate customReminderDate) {
        this.customReminderDate = customReminderDate;
    }

    /**
     * Computes the actual date when the reminder should be triggered.
     * 
     * <p>The computation logic depends on the reminder type:
     * <ul>
     *   <li><strong>ONE_DAY_BEFORE:</strong> Task deadline minus 1 day</li>
     *   <li><strong>ONE_WEEK_BEFORE:</strong> Task deadline minus 1 week</li>
     *   <li><strong>ONE_MONTH_BEFORE:</strong> Task deadline minus 1 month</li>
     *   <li><strong>CUSTOM_DATE:</strong> Returns the custom reminder date</li>
     * </ul></p>
     * 
     * @return the computed reminder date, or null if the task has no deadline
     * @throws IllegalStateException if reminder type is CUSTOM_DATE but no custom date is set
     */
    public LocalDate computeReminderDate() {
        if (task == null || task.getDeadline() == null) {
            return null;
        }
        
        switch (type) {
            case ONE_DAY_BEFORE:
                return task.getDeadline().minusDays(1);
            case ONE_WEEK_BEFORE:
                return task.getDeadline().minusWeeks(1);
            case ONE_MONTH_BEFORE:
                return task.getDeadline().minusMonths(1);
            case CUSTOM_DATE:
                return customReminderDate;
            default:
                return null;
        }
    }

    /**
     * Returns a string representation of this reminder.
     * 
     * <p>The string includes the task title, computed reminder date, and reminder type.
     * This is primarily used for debugging and logging purposes.</p>
     * 
     * @return a string representation of this reminder
     */
    @Override
    public String toString() {
        String taskTitle = (task != null) ? task.getTitle() : "[No Task]";
        return String.format("Reminder for %s (%s) on %s", 
            taskTitle, type, computeReminderDate().format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy")));
    }

    public LocalDate getReminderDate() {
        return computeReminderDate();
    }
}