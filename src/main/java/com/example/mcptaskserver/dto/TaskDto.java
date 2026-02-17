package com.example.mcptaskserver.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for Task input.
 * 
 * Includes validation constraints to prevent invalid data from AI agents.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDto {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "TODO|IN_PROGRESS|DONE", message = "Status must be TODO, IN_PROGRESS, or DONE")
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;

    private Long id;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
