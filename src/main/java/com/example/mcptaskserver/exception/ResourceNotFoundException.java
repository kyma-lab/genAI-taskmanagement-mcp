package com.example.mcptaskserver.exception;

/**
 * Exception thrown when a requested MCP resource is not found.
 */
public class ResourceNotFoundException extends RuntimeException {
    
    public ResourceNotFoundException(String message) {
        super(message);
    }
    
    public ResourceNotFoundException(String uri, String details) {
        super(String.format("Resource not found: %s - %s", uri, details));
    }
}
