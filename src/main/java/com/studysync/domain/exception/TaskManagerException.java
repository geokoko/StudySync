package com.studysync.domain.exception;

/**
 * Base exception class for all TaskManager-related exceptions.
 * Provides common error handling structure with error codes and messages.
 */
public abstract class TaskManagerException extends Exception {
    
    private final String errorCode;
    
    public TaskManagerException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public TaskManagerException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    @Override
    public String getMessage() {
        return super.getMessage();
    }
}