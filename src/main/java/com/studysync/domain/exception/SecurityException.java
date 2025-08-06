package com.studysync.domain.exception;

/**
 * Exception for security-related operations.
 */
public class SecurityException extends TaskManagerException {
    
    public static final String ENCRYPTION_FAILED = "SECURITY_ENCRYPTION_FAILED";
    public static final String DECRYPTION_FAILED = "SECURITY_DECRYPTION_FAILED";
    public static final String KEY_GENERATION_FAILED = "SECURITY_KEY_GENERATION_FAILED";
    public static final String AUTHENTICATION_FAILED = "SECURITY_AUTHENTICATION_FAILED";
    
    public SecurityException(String message, String errorCode) {
        super(message, errorCode);
    }
    
    public SecurityException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
    
    public static SecurityException encryptionFailed(Throwable cause) {
        return new SecurityException("Failed to encrypt data", ENCRYPTION_FAILED, cause);
    }
    
    public static SecurityException decryptionFailed(Throwable cause) {
        return new SecurityException("Failed to decrypt data", DECRYPTION_FAILED, cause);
    }
    
    public static SecurityException keyGenerationFailed(Throwable cause) {
        return new SecurityException("Failed to generate encryption key", KEY_GENERATION_FAILED, cause);
    }
    
    public static SecurityException authenticationFailed(String reason) {
        return new SecurityException("Authentication failed: " + reason, AUTHENTICATION_FAILED);
    }
}