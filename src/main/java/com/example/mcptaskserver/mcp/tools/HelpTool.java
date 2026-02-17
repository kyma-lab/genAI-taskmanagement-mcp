package com.example.mcptaskserver.mcp.tools;

import com.example.mcptaskserver.dto.ToolHelpDto;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Tool: mcp-help
 * 
 * Returns comprehensive documentation about all available MCP tools.
 * Optimized for AI agent consumption.
 */
@Component
@Slf4j
public class HelpTool implements McpTool {

    public static final String TOOL_NAME = "mcp-help";
    
    private final ObjectMapper objectMapper;
    private final Timer executionTimer;
    private final Counter successCounter;
    private final Counter errorCounter;

    public HelpTool(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        
        this.executionTimer = Timer.builder("mcp.tool.execution")
            .tag("tool", TOOL_NAME)
            .description("Tool execution time")
            .register(meterRegistry);
        
        this.successCounter = Counter.builder("mcp.tool.invocations")
            .tag("tool", TOOL_NAME)
            .tag("result", "success")
            .register(meterRegistry);
        
        this.errorCounter = Counter.builder("mcp.tool.invocations")
            .tag("tool", TOOL_NAME)
            .tag("result", "error")
            .register(meterRegistry);
    }

    /**
     * Returns tool definition for MCP SDK registration.
     */
    @Override
    public Tool toolDefinition() {
        String inputSchema = """
            {
                "type": "object",
                "properties": {},
                "additionalProperties": false,
                "required": []
            }
            """;

        return ToolDefinitionBuilder.buildTool(
            TOOL_NAME,
            "Returns documentation for all available MCP tools. " +
                "Start here to understand available capabilities and workflow.",
            inputSchema
        );
    }

    /**
     * Executes the tool and returns help documentation.
     * 
     * @param arguments Tool arguments (ignored for this tool)
     * @return ToolResult with documentation or error
     */
    @Override
    public CallToolResult execute(McpSyncServerExchange exchange, Map<String, Object> arguments) {
        Timer.Sample sample = Timer.start();

        try (var ignored = MDC.putCloseable("toolName", TOOL_NAME)) {
            log.info("Executing {}", TOOL_NAME);
            
            List<ToolHelpDto> tools = List.of(
                ToolHelpDto.builder()
                    .name("mcp-schema-tasks")
                    .description("Returns JSON Schema for Task objects. Use this first to understand " +
                        "the structure and constraints of task data before generating tasks.")
                    .usage("Call with empty arguments: {}")
                    .example("{}")
                    .returnType("JSON Schema object with properties, types, and constraints")
                    .build(),
                
                ToolHelpDto.builder()
                    .name("mcp-tasks")
                    .description("Bulk insert tasks into the database. Optimized for high throughput " +
                        "(~2000 tasks/second). Maximum batch size is 5000 tasks.")
                    .usage("Provide array of Task objects in 'tasks' parameter")
                    .example("{ \"tasks\": [{ \"title\": \"Example Task\", \"status\": \"TODO\", " +
                        "\"description\": \"Optional description\", \"dueDate\": \"2025-03-15\" }] }")
                    .returnType("{ success: bool, jobId: string, status: string, totalTasks: int, message: string }")
                    .build(),
                
                ToolHelpDto.builder()
                    .name("mcp-tasks-from-file")
                    .description("Import tasks from a JSON file. Bypasses token limits for large batches. " +
                        "File must contain a JSON array of task objects. Maximum batch size is 5000 tasks.")
                    .usage("Provide absolute or relative file path in 'filePath' parameter")
                    .example("{ \"filePath\": \"tasks_1000.json\" }")
                    .returnType("Import result: { success, jobId, status, totalTasks, message }")
                    .build(),
                
                ToolHelpDto.builder()
                    .name("mcp-tasks-summary")
                    .description("Get statistics about tasks in database. Use after insertion " +
                        "to verify tasks were created successfully.")
                    .usage("Call with empty arguments: {}")
                    .example("{}")
                    .returnType("Summary: { totalCount, countByStatus, dateRanges, generatedAt }")
                    .build(),
                
                ToolHelpDto.builder()
                    .name("mcp-tasks-list")
                    .description("Retrieve paginated list of tasks with optional status filtering. " +
                        "Returns full task details including IDs and timestamps.")
                    .usage("Provide optional page, pageSize, and status parameters")
                    .example("{ \"page\": 0, \"pageSize\": 100, \"status\": \"TODO\" }")
                    .returnType("Paginated response: { tasks, total, page, pageSize, totalPages }")
                    .build(),
                
                ToolHelpDto.builder()
                    .name("mcp-job-status")
                    .description("Check the status of an asynchronous batch job. Use this to monitor " +
                        "progress of file-based task imports or large batch operations.")
                    .usage("Provide jobId returned from mcp-tasks-from-file")
                    .example("{ \"jobId\": \"550e8400-e29b-41d4-a716-446655440000\" }")
                    .returnType("Job status: { jobId, status, totalTasks, processedTasks, errorMessage, createdAt, updatedAt }")
                    .build(),
                
                ToolHelpDto.builder()
                    .name("mcp-help")
                    .description("Display this help documentation. Provides information about " +
                        "all available tools and recommended workflow.")
                    .usage("Call with empty arguments: {}")
                    .example("{}")
                    .returnType("Documentation object with tools array and workflow steps")
                    .build()
            );
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("serverName", "mcp-task-server");
            response.put("version", "1.0.0-SNAPSHOT");
            response.put("mcpSpecVersion", "2025-06-18");
            response.put("tools", tools);
            response.put("workflow", List.of(
                "1. Call mcp-help to understand available tools (you are here)",
                "2. Call mcp-schema-tasks to get the Task JSON Schema",
                "3. Generate task data following the schema requirements",
                "4a. Call mcp-tasks with your task array (up to 5000 tasks), OR",
                "4b. Call mcp-tasks-from-file with a JSON file path (recommended for large batches)",
                "5. Use mcp-job-status to check async processing progress",
                "6. Call mcp-tasks-summary to verify successful insertion",
                "7. Use mcp-tasks-list to retrieve and browse tasks with pagination"
            ));
            response.put("tips", List.of(
                "Always check the schema before generating tasks to ensure valid data",
                "For large batches, use mcp-tasks-from-file to bypass token limits",
                "Batch large inserts for better performance (optimal batch: 500-2000)",
                "Status must be one of: TODO, IN_PROGRESS, DONE",
                "Due dates should be in YYYY-MM-DD format",
                "Title is required and limited to 255 characters",
                "Description is optional and limited to 2000 characters"
            ));
            
            String jsonResult = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(response);
            
            sample.stop(executionTimer);
            successCounter.increment();
            
            log.debug("Help generated successfully");
            return new CallToolResult(
                List.of(new McpSchema.TextContent(jsonResult)),
                false
            );
            
        } catch (Exception e) {
            sample.stop(executionTimer);
            errorCounter.increment();
            
            log.error("Error generating help", e);
            return McpErrorResult.internalError("Internal error while generating help. Please check server logs.");
        }
    }


}
