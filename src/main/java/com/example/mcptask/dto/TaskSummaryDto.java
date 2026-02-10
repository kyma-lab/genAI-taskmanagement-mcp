package com.example.mcptask.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Summary statistics of tasks in the system")
public class TaskSummaryDto {

    @Schema(description = "Total number of tasks", example = "1000")
    private long totalTasks;

    @Schema(description = "Number of tasks per status", example = "{\"TODO\": 250, \"IN_PROGRESS\": 300, \"DONE\": 400, \"CANCELLED\": 50}")
    private Map<String, Long> tasksByStatus;
}
