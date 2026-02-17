package com.example.mcptaskserver.audit;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Fluent builder for constructing AuditEvent instances.
 */
public class AuditEventBuilder {
    private AuditEventType eventType;
    private Instant timestamp = Instant.now();
    private String correlationId;
    private String apiKeyHash;
    private String ipAddress;
    private String toolName;
    private final Map<String, Object> metadata = new HashMap<>();
    private Boolean success;
    private String errorMessage;
    
    public AuditEventBuilder eventType(AuditEventType eventType) {
        this.eventType = eventType;
        return this;
    }
    
    public AuditEventBuilder timestamp(Instant timestamp) {
        this.timestamp = timestamp;
        return this;
    }
    
    public AuditEventBuilder correlationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }
    
    public AuditEventBuilder apiKeyHash(String apiKeyHash) {
        this.apiKeyHash = apiKeyHash;
        return this;
    }
    
    public AuditEventBuilder ipAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        return this;
    }
    
    public AuditEventBuilder toolName(String toolName) {
        this.toolName = toolName;
        return this;
    }
    
    public AuditEventBuilder metadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }
    
    public AuditEventBuilder metadata(Map<String, Object> metadata) {
        this.metadata.putAll(metadata);
        return this;
    }
    
    public AuditEventBuilder success(Boolean success) {
        this.success = success;
        return this;
    }
    
    public AuditEventBuilder errorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }
    
    public AuditEvent build() {
        if (eventType == null) {
            throw new IllegalStateException("eventType is required");
        }
        
        return new AuditEvent(
            eventType.name(),
            eventType.getCategory().name(),
            eventType.getDescription(),
            timestamp,
            correlationId,
            apiKeyHash,
            ipAddress,
            toolName,
            metadata.isEmpty() ? null : Map.copyOf(metadata),
            success,
            errorMessage
        );
    }
}
