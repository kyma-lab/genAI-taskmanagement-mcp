package com.example.mcptaskserver.audit;

/**
 * Strategy for handling sensitive data in audit logs.
 */
public enum SanitizationStrategy {
    /**
     * Truncate to maximum length and append "..."
     */
    TRUNCATE,
    
    /**
     * Replace entire value with "[REDACTED]"
     */
    REDACT,
    
    /**
     * Log full value (use with caution)
     */
    FULL
}
