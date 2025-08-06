package com.studysync.domain.exception;

/**
 * Exception thrown when a requested task cannot be found.
 */
public class TaskNotFoundException extends TaskManagerException {
    
    public static final String TASK_NOT_FOUND = "TASK_NOT_FOUND";
    
    public TaskNotFoundException(String message, String errorCode) {
        super(message, errorCode);
    }
    
    public TaskNotFoundException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
    
    public static TaskNotFoundException forId(String taskId) {
        return new TaskNotFoundException("Task not found with ID: " + taskId, TASK_NOT_FOUND);
    }
    
    public static TaskNotFoundException forTitle(String title) {
        return new TaskNotFoundException("Task not found with title: " + title, TASK_NOT_FOUND);
    }
}