package com.example.mcptaskserver.exception;

/**
 * Exception thrown when validation fails.
 * 
 * Used for custom validation logic beyond jakarta.validation.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
