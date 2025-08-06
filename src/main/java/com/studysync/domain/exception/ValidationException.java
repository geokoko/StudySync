package com.studysync.domain.exception;

/**
 * Exception for validation errors in business logic.
 */
public class ValidationException extends TaskManagerException {
    
    public static final String INVALID_INPUT = "VALIDATION_INVALID_INPUT";
    public static final String REQUIRED_FIELD_MISSING = "VALIDATION_REQUIRED_FIELD";
    public static final String INVALID_DATE_RANGE = "VALIDATION_INVALID_DATE_RANGE";
    
    public ValidationException(String message, String errorCode) {
        super(message, errorCode);
    }
    
    public ValidationException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
    
    public static ValidationException requiredFieldMissing(String fieldName) {
        return new ValidationException("Required field missing: " + fieldName, REQUIRED_FIELD_MISSING);
    }
    
    public static ValidationException invalidInput(String fieldName, String value) {
        return new ValidationException("Invalid input for " + fieldName + ": " + value, INVALID_INPUT);
    }
    
    public static ValidationException invalidDateRange(String startDate, String endDate) {
        return new ValidationException("Invalid date range: start=" + startDate + ", end=" + endDate, INVALID_DATE_RANGE);
    }
}