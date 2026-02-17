package com.example.mcptaskserver.dto;

import com.example.mcptaskserver.model.Task;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read-only DTO for exposing tasks via MCP Resources API.
 */
@Builder
public record TaskResourceDto(
    Long id,
    String title,
    String description,
    String status,
    LocalDate dueDate,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    /**
     * Creates a TaskResourceDto from a Task entity.
     *
     * @param task the task entity
     * @return the resource DTO
     */
    public static TaskResourceDto fromEntity(Task task) {
        return TaskResourceDto.builder()
            .id(task.getId())
            .title(task.getTitle())
            .description(task.getDescription())
            .status(task.getStatus().getValue())
            .dueDate(task.getDueDate())
            .createdAt(task.getCreatedAt())
            .updatedAt(task.getUpdatedAt())
            .build();
    }
}
