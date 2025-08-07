package com.studysync.domain.service;

import com.studysync.domain.entity.Task;
import com.studysync.domain.entity.TaskReminder;
import com.studysync.domain.valueobject.TaskStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing task reminders.
 */
@Service
public class ReminderService {
    private final List<TaskReminder> reminders = new ArrayList<>();

    public List<TaskReminder> getReminders() {
        return new ArrayList<>(reminders);
    }

    public List<TaskReminder> getRemindersForTask(String taskId) {
        return reminders.stream()
                .filter(r -> r.getTask().getId().equals(taskId))
                .collect(Collectors.toList());
    }

    public void addReminder(String taskId, TaskReminder.ReminderType type, LocalDate customDate) {
        // In a real application, you'd fetch the Task from a TaskService or TaskRepository
        // For this example, we'll create a dummy task or assume it's passed in.
        // This is a simplification for the purpose of demonstrating reminder logic.
        Task task = new Task(taskId, "Dummy Task", "", "", null, null, TaskStatus.OPEN, 0);

        if (task.getStatus() == TaskStatus.COMPLETED) {
            return;
        }
        
        LocalDate reminderDate = null;
        if (type != TaskReminder.ReminderType.CUSTOM_DATE) {
            if (task.getDeadline() == null) {
                return; // No reminder if no deadline
            }
            switch (type) {
                case ONE_DAY_BEFORE:
                    reminderDate = task.getDeadline().minusDays(1);
                    break;
                case ONE_WEEK_BEFORE:
                    reminderDate = task.getDeadline().minusWeeks(1);
                    break;
                case ONE_MONTH_BEFORE:
                    reminderDate = task.getDeadline().minusMonths(1);
                    break;
            }
        } else {
            reminderDate = customDate;
        }
        
        if (reminderDate != null) {
            reminders.add(new TaskReminder(task, type, reminderDate));
        }
    }

    public void updateReminder(TaskReminder oldReminder, TaskReminder.ReminderType newType, LocalDate newCustomDate) {
        Task task = oldReminder.getTask();
        if (task.getStatus() == TaskStatus.COMPLETED) {
            return;
        }
        
        LocalDate newReminderDate = null;
        if (newType != TaskReminder.ReminderType.CUSTOM_DATE) {
            if (task.getDeadline() == null) {
                return; // No reminder if no deadline
            }
            switch (newType) {
                case ONE_DAY_BEFORE:
                    newReminderDate = task.getDeadline().minusDays(1);
                    break;
                case ONE_WEEK_BEFORE:
                    newReminderDate = task.getDeadline().minusWeeks(1);
                    break;
                case ONE_MONTH_BEFORE:
                    newReminderDate = task.getDeadline().minusMonths(1);
                    break;
            }
        } else {
            newReminderDate = newCustomDate;
        }
        
        if (newReminderDate != null) {
            oldReminder.setType(newType);
            oldReminder.setCustomReminderDate(newReminderDate);
        }
    }

    public void removeRemindersForTask(String taskId) {
        reminders.removeIf(r -> r.getTask().getId().equals(taskId));
    }
}
