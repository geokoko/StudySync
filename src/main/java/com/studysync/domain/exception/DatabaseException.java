package com.studysync.domain.exception;

/**
 * Exception for database-related operations.
 * Thrown when database operations fail or encounter errors.
 */
public class DatabaseException extends TaskManagerException {
    
    public static final String CONNECTION_FAILED = "DB_CONNECTION_FAILED";
    public static final String QUERY_EXECUTION_FAILED = "DB_QUERY_FAILED";
    public static final String SCHEMA_INITIALIZATION_FAILED = "DB_SCHEMA_INIT_FAILED";
    public static final String DATA_PERSISTENCE_FAILED = "DB_PERSISTENCE_FAILED";
    
    public DatabaseException(String message, String errorCode) {
        super(message, errorCode);
    }
    
    public DatabaseException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
    
    public static DatabaseException connectionFailed(Throwable cause) {
        return new DatabaseException("Failed to establish database connection", CONNECTION_FAILED, cause);
    }
    
    public static DatabaseException queryExecutionFailed(String query, Throwable cause) {
        return new DatabaseException("Failed to execute query: " + query, QUERY_EXECUTION_FAILED, cause);
    }
    
    public static DatabaseException schemaInitializationFailed(Throwable cause) {
        return new DatabaseException("Failed to initialize database schema", SCHEMA_INITIALIZATION_FAILED, cause);
    }
    
    public static DatabaseException dataPersistenceFailed(String entity, Throwable cause) {
        return new DatabaseException("Failed to persist " + entity + " data", DATA_PERSISTENCE_FAILED, cause);
    }
}