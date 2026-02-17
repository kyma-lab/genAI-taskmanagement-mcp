package com.example.mcptaskserver.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Data Transfer Object for task summary statistics.
 * Returned by mcp-tasks-summary tool.
 *
 * Java 21 record for immutability and reduced boilerplate.
 */
public record TaskSummaryDto(
    long totalCount,
    Map<String, Long> countByStatus,
    LocalDate earliestDueDate,
    LocalDate latestDueDate,
    LocalDateTime generatedAt
) {
    /**
     * Builder for backward compatibility with existing code.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private long totalCount;
        private Map<String, Long> countByStatus;
        private LocalDate earliestDueDate;
        private LocalDate latestDueDate;
        private LocalDateTime generatedAt;
        
        public Builder totalCount(long totalCount) {
            this.totalCount = totalCount;
            return this;
        }
        
        public Builder countByStatus(Map<String, Long> countByStatus) {
            this.countByStatus = countByStatus;
            return this;
        }
        
        public Builder earliestDueDate(LocalDate earliestDueDate) {
            this.earliestDueDate = earliestDueDate;
            return this;
        }
        
        public Builder latestDueDate(LocalDate latestDueDate) {
            this.latestDueDate = latestDueDate;
            return this;
        }
        
        public Builder generatedAt(LocalDateTime generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }
        
        public TaskSummaryDto build() {
            return new TaskSummaryDto(totalCount, countByStatus,
                earliestDueDate, latestDueDate, generatedAt);
        }
    }
}
