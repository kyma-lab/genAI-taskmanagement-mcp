package com.example.mcptaskserver.audit;

/**
 * Categories of audit events for organizational and filtering purposes.
 */
public enum AuditCategory {
    /**
     * Security-related events (authentication, authorization, rate limiting)
     */
    SECURITY,
    
    /**
     * MCP tool invocations
     */
    TOOL,
    
    /**
     * Data modification operations (task creation, updates, deletions)
     */
    DATA,
    
    /**
     * Administrative operations (startup, shutdown, configuration changes)
     */
    ADMIN,
    
    /**
     * Error conditions and exceptions
     */
    ERROR
}
