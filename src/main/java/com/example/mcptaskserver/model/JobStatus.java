package com.example.mcptaskserver.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Status of an asynchronous batch job.
 */
@Getter
@RequiredArgsConstructor
public enum JobStatus {
    PENDING("pending"),
    RUNNING("running"),
    COMPLETED("completed"),
    FAILED("failed");

    private final String value;

    public static JobStatus fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("JobStatus value cannot be null");
        }
        
        for (JobStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        
        throw new IllegalArgumentException("Invalid JobStatus value: " + value);
    }
}
