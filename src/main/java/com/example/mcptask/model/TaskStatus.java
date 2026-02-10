package com.example.mcptask.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Status of a task")
public enum TaskStatus {
    @Schema(description = "Task is waiting to be started")
    TODO,
    
    @Schema(description = "Task is currently being worked on")
    IN_PROGRESS,
    
    @Schema(description = "Task has been completed")
    DONE,
    
    @Schema(description = "Task has been cancelled")
    CANCELLED
}
