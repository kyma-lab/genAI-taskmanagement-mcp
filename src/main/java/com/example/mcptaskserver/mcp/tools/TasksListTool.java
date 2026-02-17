package com.example.mcptaskserver.mcp.tools;

import com.example.mcptaskserver.dto.TaskListResponseDto;
import com.example.mcptaskserver.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP Tool for retrieving paginated task lists with optional filtering.
 */
@Component
@Slf4j
public class TasksListTool implements McpTool {

    public static final String TOOL_NAME = "mcp-tasks-list";

    private final TaskService taskService;
    private final ObjectMapper objectMapper;
    private final Timer executionTimer;
    private final Counter successCounter;
    private final Counter errorCounter;

    public TasksListTool(TaskService taskService, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.taskService = taskService;
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

    @Override
    public Tool toolDefinition() {
        String inputSchema = """
            {
                "type": "object",
                "properties": {
                    "page": {
                        "type": "integer",
                        "description": "Page number (0-based)",
                        "default": 0,
                        "minimum": 0
                    },
                    "pageSize": {
                        "type": "integer",
                        "description": "Number of items per page (max 1000)",
                        "default": 100,
                        "minimum": 1,
                        "maximum": 1000
                    },
                    "status": {
                        "type": "string",
                        "description": "Optional filter by status",
                        "enum": ["TODO", "IN_PROGRESS", "DONE"]
                    }
                },
                "additionalProperties": false
            }
            """;

        return ToolDefinitionBuilder.buildTool(
            TOOL_NAME,
            "Retrieves a paginated list of tasks with optional status filtering. " +
                "Returns tasks with full details including ID, timestamps, and metadata.",
            inputSchema
        );
    }

    @Override
    public CallToolResult execute(McpSyncServerExchange exchange, Map<String, Object> arguments) {
        Timer.Sample sample = Timer.start();
        try (var ignored = MDC.putCloseable("toolName", TOOL_NAME)) {
            int page = getIntArg(arguments, "page", 0);
            int pageSize = getIntArg(arguments, "pageSize", 100);
            String status = (String) arguments.get("status");

            log.info("Listing tasks: page={}, pageSize={}, status={}", page, pageSize, status);

            TaskListResponseDto response = taskService.listTasks(page, pageSize, status);
            String jsonResult = objectMapper.writeValueAsString(response);

            sample.stop(executionTimer);
            successCounter.increment();

            log.debug("Task list retrieved: {} total tasks, page {} of {}",
                response.getTotal(), response.getPage() + 1, response.getTotalPages());

            return new CallToolResult(List.of(new McpSchema.TextContent(jsonResult)), false);

        } catch (IllegalArgumentException e) {
            sample.stop(executionTimer);
            errorCounter.increment();
            log.error("Invalid arguments for {}: {}", TOOL_NAME, e.getMessage());
            return McpErrorResult.validationError(e.getMessage());
        } catch (Exception e) {
            sample.stop(executionTimer);
            errorCounter.increment();
            log.error("Error executing {}", TOOL_NAME, e);
            return McpErrorResult.internalError("Internal error while retrieving task list. Please check server logs.");
        }
    }

    private int getIntArg(Map<String, Object> arguments, String key, int defaultValue) {
        Object value = arguments.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }
}
