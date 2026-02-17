package com.example.mcptaskserver.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for paginated task lists.
 * Contains tasks and pagination metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskListResponseDto {
    
    /**
     * List of tasks for current page
     */
    private List<TaskDto> tasks;
    
    /**
     * Total number of tasks matching the filter
     */
    private long total;
    
    /**
     * Current page number (0-based)
     */
    private int page;
    
    /**
     * Page size (number of items per page)
     */
    private int pageSize;
    
    /**
     * Total number of pages
     */
    private int totalPages;
}
