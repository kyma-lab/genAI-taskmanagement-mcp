package com.example.mcptask.dto;

import com.example.mcptask.model.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO for creating a new task")
public class TaskCreateDto {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Schema(description = "Title of the task", example = "Implement user authentication", required = true)
    private String title;

    @Schema(description = "Detailed description of the task", example = "Add OAuth2 authentication to the application")
    private String description;

    @NotNull(message = "Status is required")
    @Schema(description = "Current status of the task", example = "TODO", required = true)
    private TaskStatus status;

    @Min(value = 1, message = "Priority must be between 1 and 5")
    @Max(value = 5, message = "Priority must be between 1 and 5")
    @Schema(description = "Priority of the task (1=lowest, 5=urgent)", example = "3", minimum = "1", maximum = "5")
    private Integer priority;

    @Schema(description = "Due date for task completion", example = "2026-12-31")
    private LocalDate dueDate;
}
