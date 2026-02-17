package com.example.mcptaskserver.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable audit event record using Java 21 record feature.
 * Represents a structured audit log entry with all relevant context.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEvent(
    String eventType,
    String category,
    String description,
    Instant timestamp,
    String correlationId,
    String apiKeyHash,
    String ipAddress,
    String toolName,
    Map<String, Object> metadata,
    Boolean success,
    String errorMessage
) {
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    
    /**
     * Convert audit event to JSON string for logging.
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            // Fallback to simple string representation
            return String.format("AuditEvent[type=%s, timestamp=%s, correlationId=%s]", 
                eventType, timestamp, correlationId);
        }
    }
    
    /**
     * Create a builder for fluent event construction.
     */
    public static AuditEventBuilder builder() {
        return new AuditEventBuilder();
    }
}
