package com.example.mcptask.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of bulk task insert operation")
public class BulkInsertResultDto {

    @Schema(description = "Number of tasks successfully inserted", example = "1000")
    private int inserted;

    @Schema(description = "Status message", example = "Successfully inserted 1000 tasks")
    private String message;
}
