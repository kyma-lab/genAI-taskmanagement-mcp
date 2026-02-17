package com.example.mcptaskserver.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Central audit logging service.
 * Logs audit events to a dedicated AUDIT logger for separation from application logs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogger {
    
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT");
    
    private final AuditConfig auditConfig;
    
    /**
     * Log an audit event if the category is enabled in configuration.
     */
    public void log(AuditEvent event) {
        if (!auditConfig.isEnabled()) {
            return;
        }
        
        AuditCategory category = AuditCategory.valueOf(event.category());
        if (!auditConfig.isCategoryEnabled(category)) {
            return;
        }
        
        // Add event fields to MDC for structured logging
        try {
            if (event.correlationId() != null) {
                MDC.put("correlationId", event.correlationId());
            }
            if (event.eventType() != null) {
                MDC.put("eventType", event.eventType());
            }
            if (event.category() != null) {
                MDC.put("category", event.category());
            }
            if (event.apiKeyHash() != null) {
                MDC.put("apiKeyHash", event.apiKeyHash());
            }
            if (event.ipAddress() != null) {
                MDC.put("ipAddress", event.ipAddress());
            }
            if (event.toolName() != null) {
                MDC.put("toolName", event.toolName());
            }
            
            // Log the full event as JSON
            AUDIT_LOG.info(event.toJson());
            
        } finally {
            // Clean up MDC
            MDC.remove("eventType");
            MDC.remove("category");
            MDC.remove("apiKeyHash");
            MDC.remove("ipAddress");
            MDC.remove("toolName");
            // Keep correlationId as it may be used by other components
        }
    }
    
    /**
     * Log an audit event using a builder (convenience method).
     */
    public void log(AuditEventBuilder builder) {
        log(builder.build());
    }
    
    /**
     * Sanitize sensitive data according to configuration.
     */
    public String sanitize(String value) {
        if (value == null) {
            return null;
        }
        
        int maxLength = auditConfig.getSensitiveDataMaxLength();
        SanitizationStrategy strategy = auditConfig.getSensitiveDataStrategy();
        
        return switch (strategy) {
            case TRUNCATE -> value.length() > maxLength 
                ? value.substring(0, maxLength) + "..." 
                : value;
            case REDACT -> "[REDACTED]";
            case FULL -> value;
        };
    }
}
