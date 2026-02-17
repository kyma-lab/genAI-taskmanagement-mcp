package com.example.mcptaskserver.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Task status enumeration.
 * 
 * Represents the current state of a task in the workflow.
 */
@Getter
@RequiredArgsConstructor
public enum TaskStatus {
    TODO("TODO"),
    IN_PROGRESS("IN_PROGRESS"),
    DONE("DONE");

    @JsonValue
    private final String value;

    /**
     * Converts a string value to TaskStatus enum.
     * 
     * @param value the string value
     * @return the corresponding TaskStatus
     * @throws IllegalArgumentException if value is invalid
     */
    public static TaskStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TaskStatus value cannot be null or blank");
        }
        
        if (value.length() > 20) {
            throw new IllegalArgumentException("TaskStatus value exceeds maximum length of 20 characters");
        }
        
        String sanitized = value.trim().toUpperCase();
        for (TaskStatus status : values()) {
            if (status.value.equals(sanitized)) {
                return status;
            }
        }
        
        throw new IllegalArgumentException("Invalid TaskStatus value: " + value);
    }
}
