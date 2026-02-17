package com.example.mcptaskserver.util;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Utility for managing correlation IDs in STDIO mode.
 * Generates unique correlation IDs for tracking operations across async batch jobs.
 */
public class CorrelationIdContext {
    
    private static final String CORRELATION_ID_KEY = "correlationId";
    
    /**
     * Get current correlation ID from MDC or generate a new one.
     */
    public static String getCurrentCorrelationId() {
        String correlationId = MDC.get(CORRELATION_ID_KEY);
        if (correlationId == null) {
            correlationId = generateCorrelationId();
            MDC.put(CORRELATION_ID_KEY, correlationId);
        }
        return correlationId;
    }
    
    /**
     * Generate a new correlation ID.
     */
    public static String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Set correlation ID in MDC.
     */
    public static void setCorrelationId(String correlationId) {
        MDC.put(CORRELATION_ID_KEY, correlationId);
    }
    
    /**
     * Clear correlation ID from MDC.
     */
    public static void clearCorrelationId() {
        MDC.remove(CORRELATION_ID_KEY);
    }
}
