package com.example.mcptaskserver.mcp.tools;


import com.example.mcptaskserver.dto.TaskSummaryDto;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import com.example.mcptaskserver.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP Tool: mcp-tasks-summary
 * 
 * Returns statistics about tasks in the database.
 * Used by AI agents to verify successful task insertion.
 */
@Component
@Slf4j
public class TasksSummaryTool implements McpTool {

    public static final String TOOL_NAME = "mcp-tasks-summary";
    
    private final TaskService taskService;
    private final ObjectMapper objectMapper;
    private final Timer executionTimer;
    private final Counter successCounter;
    private final Counter errorCounter;

    public TasksSummaryTool(
            TaskService taskService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
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
            "Returns statistics about tasks in the database. " +
                "Includes total count, counts by status, and date ranges. " +
                "Use this to verify successful task insertion.",
            inputSchema
        );
    }

    /**
     * Executes the tool and returns task statistics.
     * 
     * @param arguments Tool arguments (ignored for this tool)
     * @return ToolResult with summary or error
     */
    @Override
    public CallToolResult execute(McpSyncServerExchange exchange, Map<String, Object> arguments) {
        Timer.Sample sample = Timer.start();

        try (var ignored = MDC.putCloseable("toolName", TOOL_NAME)) {
            log.info("Executing {}", TOOL_NAME);
            
            TaskSummaryDto summary = taskService.generateSummary();
            String jsonResult = objectMapper.writeValueAsString(summary);
            
            sample.stop(executionTimer);
            successCounter.increment();
            
            log.debug("Summary generated: {} total tasks", summary.totalCount());
            return new CallToolResult(
                List.of(new McpSchema.TextContent(jsonResult)),
                false
            );
            
        } catch (Exception e) {
            sample.stop(executionTimer);
            errorCounter.increment();
            
            log.error("Error generating summary", e);
            return McpErrorResult.internalError("Internal error while generating summary. Please check server logs.");
        }
    }


}
