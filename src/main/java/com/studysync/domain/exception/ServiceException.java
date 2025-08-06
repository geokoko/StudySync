package com.studysync.domain.exception;

/**
 * Exception for service layer operations.
 */
public class ServiceException extends TaskManagerException {
    
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    public static final String EXTERNAL_API_ERROR = "EXTERNAL_API_ERROR";
    public static final String CONFIGURATION_ERROR = "CONFIGURATION_ERROR";
    
    public ServiceException(String message, String errorCode) {
        super(message, errorCode);
    }
    
    public ServiceException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
    
    public static ServiceException serviceUnavailable(String serviceName, Throwable cause) {
        return new ServiceException("Service unavailable: " + serviceName, SERVICE_UNAVAILABLE, cause);
    }
    
    public static ServiceException externalApiError(String apiName, Throwable cause) {
        return new ServiceException("External API error: " + apiName, EXTERNAL_API_ERROR, cause);
    }
    
    public static ServiceException configurationError(String configProperty, Throwable cause) {
        return new ServiceException("Configuration error: " + configProperty, CONFIGURATION_ERROR, cause);
    }
}