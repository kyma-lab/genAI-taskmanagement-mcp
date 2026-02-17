package com.example.mcptaskserver.mcp.tools;


import com.example.mcptaskserver.exception.ResourceNotFoundException;
import com.example.mcptaskserver.exception.ValidationException;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import com.example.mcptaskserver.model.BatchJob;
import com.example.mcptaskserver.service.AsyncBatchService;
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
 * MCP Tool: mcp-job-status
 * 
 * Queries the status of an asynchronous batch job.
 */
@Component
@Slf4j
public class JobStatusTool implements McpTool {

    public static final String TOOL_NAME = "mcp-job-status";
    
    private final AsyncBatchService asyncBatchService;
    private final ObjectMapper objectMapper;
    private final Timer executionTimer;
    private final Counter successCounter;
    private final Counter errorCounter;

    public JobStatusTool(
            AsyncBatchService asyncBatchService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.asyncBatchService = asyncBatchService;
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
                "properties": {
                    "jobId": {
                        "type": "string",
                        "description": "The job ID returned from mcp-tasks"
                    }
                },
                "additionalProperties": false,
                "required": ["jobId"]
            }
            """;
        
        return ToolDefinitionBuilder.buildTool(
            TOOL_NAME,
            "Check the status of an asynchronous batch job. " +
                "Provides progress information and completion status.",
            inputSchema
        );
    }

    /**
     * Executes the tool and retrieves job status.
     * 
     * @param arguments Must contain "jobId"
     * @return ToolResult with job status or error
     */
    @Override
    public CallToolResult execute(McpSyncServerExchange exchange, Map<String, Object> arguments) {
        Timer.Sample sample = Timer.start();

        try (var ignored = MDC.putCloseable("toolName", TOOL_NAME)) {
            log.debug("Executing {}", TOOL_NAME);
            
            // Validate arguments
            if (!arguments.containsKey("jobId")) {
                throw new ValidationException("Missing required parameter: jobId");
            }
            
            String jobId = arguments.get("jobId").toString();
            
            if (jobId == null || jobId.isBlank()) {
                throw new ValidationException("Parameter 'jobId' cannot be empty");
            }
            
            // Retrieve job
            BatchJob job = asyncBatchService.findJob(jobId);
            
            // Build response
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("jobId", job.getId());
            result.put("status", job.getStatus().getValue());
            result.put("totalTasks", job.getTotalTasks());
            result.put("processedTasks", job.getProcessedTasks());
            
            if (job.getDurationMs() != null) {
                result.put("durationMs", job.getDurationMs());
                
                if (job.getProcessedTasks() > 0 && job.getDurationMs() > 0) {
                    long tasksPerSecond = (job.getProcessedTasks() * 1000L) / job.getDurationMs();
                    result.put("tasksPerSecond", tasksPerSecond);
                }
            }
            
            if (job.getErrorMessage() != null) {
                result.put("errorMessage", job.getErrorMessage());
            }
            
            result.put("createdAt", job.getCreatedAt().toString());
            if (job.getCompletedAt() != null) {
                result.put("completedAt", job.getCompletedAt().toString());
            }
            
            // Calculate progress percentage
            if (job.getTotalTasks() > 0) {
                int progressPercent = (job.getProcessedTasks() * 100) / job.getTotalTasks();
                result.put("progressPercent", progressPercent);
            }
            
            String jsonResult = objectMapper.writeValueAsString(result);
            
            // Update metrics
            sample.stop(executionTimer);
            successCounter.increment();
            
            log.debug("Job status retrieved: {}", jobId);
            
            return new CallToolResult(
                List.of(new McpSchema.TextContent(jsonResult)),
                false
            );
            
        } catch (ResourceNotFoundException e) {
            sample.stop(executionTimer);
            errorCounter.increment();
            log.warn("Job not found: {}", e.getMessage());
            return McpErrorResult.notFoundError(e.getMessage());

        } catch (ValidationException e) {
            sample.stop(executionTimer);
            errorCounter.increment();
            log.warn("Validation error: {}", e.getMessage());
            return McpErrorResult.validationError(e.getMessage());

        } catch (Exception e) {
            sample.stop(executionTimer);
            errorCounter.increment();
            log.error("Error retrieving job status", e);
            return McpErrorResult.internalError("Internal error while retrieving job status. Please check server logs.");
        }
    }
}
