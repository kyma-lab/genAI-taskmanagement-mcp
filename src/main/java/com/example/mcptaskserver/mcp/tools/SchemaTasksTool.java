package com.example.mcptaskserver.mcp.tools;


import com.example.mcptaskserver.service.SchemaService;
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

import java.util.List;
import java.util.Map;

/**
 * MCP Tool: mcp-schema-tasks
 * 
 * Returns the JSON Schema for Task objects.
 * AI agents call this first to understand the structure and constraints.
 */
@Component
@Slf4j
public class SchemaTasksTool implements McpTool {

    public static final String TOOL_NAME = "mcp-schema-tasks";
    
    private final SchemaService schemaService;
    private final ObjectMapper objectMapper;
    private final Timer executionTimer;
    private final Counter successCounter;
    private final Counter errorCounter;

    public SchemaTasksTool(
            SchemaService schemaService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.schemaService = schemaService;
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
            "Returns the JSON Schema for Task objects. " +
                "Use this to understand the required structure and validation rules " +
                "before creating tasks.",
            inputSchema
        );
    }

    /**
     * Executes the tool and returns the Task JSON Schema.
     * 
     * @param arguments Tool arguments (ignored for this tool)
     * @return CallToolResult with schema or error
     */
    @Override
    public CallToolResult execute(McpSyncServerExchange exchange, Map<String, Object> arguments) {
        Timer.Sample sample = Timer.start();

        try (var ignored = MDC.putCloseable("toolName", TOOL_NAME)) {
            log.info("Executing {}", TOOL_NAME);
            
            Map<String, Object> schema = schemaService.generateTaskSchema();
            String jsonResult = objectMapper.writeValueAsString(schema);
            
            sample.stop(executionTimer);
            successCounter.increment();
            
            log.debug("Schema generated successfully");
            
            // SDK CallToolResult format: content list + isError flag
            return new CallToolResult(
                List.of(new McpSchema.TextContent(jsonResult)),
                false
            );
            
        } catch (Exception e) {
            sample.stop(executionTimer);
            errorCounter.increment();
            
            log.error("Error generating schema", e);
            return McpErrorResult.internalError("Internal error while generating schema. Please check server logs.");
        }
    }


}
