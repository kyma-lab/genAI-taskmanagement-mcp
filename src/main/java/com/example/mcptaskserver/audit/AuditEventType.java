package com.example.mcptaskserver.audit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Specific types of audit events with their associated categories.
 */
@Getter
@RequiredArgsConstructor
public enum AuditEventType {
    // Security events
    AUTH_SUCCESS(AuditCategory.SECURITY, "Authentication successful"),
    AUTH_FAILURE(AuditCategory.SECURITY, "Authentication failed"),
    RATE_LIMIT_EXCEEDED(AuditCategory.SECURITY, "Rate limit exceeded"),
    INVALID_API_KEY(AuditCategory.SECURITY, "Invalid API key provided"),
    
    // Tool invocation events
    TOOL_INVOCATION_START(AuditCategory.TOOL, "Tool invocation started"),
    TOOL_INVOCATION_SUCCESS(AuditCategory.TOOL, "Tool invocation completed successfully"),
    TOOL_INVOCATION_FAILURE(AuditCategory.TOOL, "Tool invocation failed"),
    
    // Resource events
    RESOURCE_READ_START(AuditCategory.TOOL, "Resource read started"),
    RESOURCE_READ_SUCCESS(AuditCategory.TOOL, "Resource read completed"),
    RESOURCE_READ_FAILURE(AuditCategory.TOOL, "Resource read failed"),
    RESOURCE_NOT_FOUND(AuditCategory.TOOL, "Resource not found"),
    
    // Prompt events
    PROMPT_GET_START(AuditCategory.TOOL, "Prompt get started"),
    PROMPT_GET_SUCCESS(AuditCategory.TOOL, "Prompt get completed"),
    PROMPT_GET_FAILURE(AuditCategory.TOOL, "Prompt get failed"),

    // Data modification events
    TASKS_CREATED(AuditCategory.DATA, "Tasks created"),
    BATCH_JOB_CREATED(AuditCategory.DATA, "Batch job created"),
    BATCH_JOB_STARTED(AuditCategory.DATA, "Batch job processing started"),
    BATCH_JOB_COMPLETED(AuditCategory.DATA, "Batch job completed"),
    BATCH_JOB_FAILED(AuditCategory.DATA, "Batch job failed"),
    
    // Administrative events
    SERVER_STARTUP(AuditCategory.ADMIN, "Server started"),
    SERVER_SHUTDOWN(AuditCategory.ADMIN, "Server shutdown"),
    HEALTH_CHECK_FAILED(AuditCategory.ADMIN, "Health check failed"),
    
    // Error events
    VALIDATION_ERROR(AuditCategory.ERROR, "Validation error occurred"),
    DATABASE_ERROR(AuditCategory.ERROR, "Database error occurred"),
    ERROR_OCCURRED(AuditCategory.ERROR, "Unexpected error occurred");
    
    private final AuditCategory category;
    private final String description;
}
