package com.example.mcptask.controller;

import com.example.mcptask.dto.BulkInsertResultDto;
import com.example.mcptask.dto.TaskCreateDto;
import com.example.mcptask.dto.TaskSummaryDto;
import com.example.mcptask.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "MCP Task Server", description = "Model Context Protocol endpoints for AI-powered task data injection")
public class McpController {

    private final TaskService taskService;

    @GetMapping("/help")
    @Operation(
        summary = "Get MCP server capabilities",
        description = "Returns a description of all available MCP endpoints for AI agents"
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved help information")
    public ResponseEntity<Map<String, Object>> help() {
        Map<String, Object> help = new HashMap<>();
        help.put("server", "MCP Task Server");
        help.put("version", "1.0.0");
        help.put("description", "AI-powered task data injection service");
        
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("GET /mcp/help", "Get this help information");
        endpoints.put("GET /mcp/schema/tasks", "Get the Task entity JSON schema");
        endpoints.put("POST /mcp/tasks", "Bulk insert tasks (accepts JSON array)");
        endpoints.put("GET /mcp/tasks/summary", "Get task statistics by status");
        endpoints.put("GET /v3/api-docs", "OpenAPI specification (AI-readable)");
        endpoints.put("GET /swagger-ui.html", "Interactive API documentation");
        
        help.put("endpoints", endpoints);
        help.put("openapi", "/v3/api-docs");
        
        return ResponseEntity.ok(help);
    }

    @GetMapping("/schema/tasks")
    @Operation(
        summary = "Get Task schema",
        description = "Returns the JSON schema for the Task entity. AI agents should inspect this before generating tasks."
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved schema")
    public ResponseEntity<Map<String, Object>> getTaskSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("$schema", "http://json-schema.org/draft-07/schema#");
        schema.put("title", "Task");
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        properties.put("title", Map.of(
            "type", "string",
            "description", "Title of the task (required, max 255 characters)",
            "maxLength", 255
        ));
        
        properties.put("description", Map.of(
            "type", "string",
            "description", "Detailed description of the task (optional)"
        ));
        
        properties.put("status", Map.of(
            "type", "string",
            "description", "Current status of the task (required)",
            "enum", List.of("TODO", "IN_PROGRESS", "DONE", "CANCELLED")
        ));
        
        properties.put("priority", Map.of(
            "type", "integer",
            "description", "Priority level (1=lowest, 5=urgent)",
            "minimum", 1,
            "maximum", 5
        ));
        
        properties.put("dueDate", Map.of(
            "type", "string",
            "format", "date",
            "description", "Due date in YYYY-MM-DD format (optional)",
            "example", "2026-12-31"
        ));
        
        schema.put("properties", properties);
        schema.put("required", List.of("title", "status"));
        
        Map<String, String> hint = new HashMap<>();
        hint.put("note", "For better API documentation, see /v3/api-docs (OpenAPI format)");
        hint.put("swagger-ui", "/swagger-ui.html");
        schema.put("_hint", hint);
        
        return ResponseEntity.ok(schema);
    }

    @PostMapping(value = "/tasks", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Bulk insert tasks",
        description = "Insert multiple tasks at once. Optimized for high-volume inserts (1000+ tasks). Uses Hibernate batch processing for performance."
    )
    @ApiResponse(
        responseCode = "201",
        description = "Tasks successfully inserted",
        content = @Content(schema = @Schema(implementation = BulkInsertResultDto.class))
    )
    @ApiResponse(responseCode = "400", description = "Invalid task data")
    public ResponseEntity<BulkInsertResultDto> bulkInsertTasks(
            @Valid @RequestBody List<@Valid TaskCreateDto> tasks) {
        
        if (tasks.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(BulkInsertResultDto.builder()
                            .inserted(0)
                            .message("Task list is empty")
                            .build());
        }
        
        log.info("Received bulk insert request for {} tasks", tasks.size());
        BulkInsertResultDto result = taskService.bulkInsert(tasks);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/tasks/summary")
    @Operation(
        summary = "Get task summary statistics",
        description = "Returns aggregate statistics: total task count and breakdown by status"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Successfully retrieved summary",
        content = @Content(schema = @Schema(implementation = TaskSummaryDto.class))
    )
    public ResponseEntity<TaskSummaryDto> getTasksSummary() {
        TaskSummaryDto summary = taskService.getSummary();
        return ResponseEntity.ok(summary);
    }
}
