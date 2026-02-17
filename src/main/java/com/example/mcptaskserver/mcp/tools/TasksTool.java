package com.example.mcptaskserver.mcp.tools;

import com.example.mcptaskserver.audit.AuditEvent;
import com.example.mcptaskserver.audit.AuditEventType;
import com.example.mcptaskserver.audit.AuditLogger;
import com.example.mcptaskserver.dto.TaskDto;
import com.example.mcptaskserver.exception.ValidationException;
import com.example.mcptaskserver.util.CorrelationIdContext;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import com.example.mcptaskserver.model.Task;
import com.example.mcptaskserver.model.TaskStatus;
import com.example.mcptaskserver.model.BatchJob;
import com.example.mcptaskserver.service.AsyncBatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.task.TaskRejectedException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Tool: mcp-tasks
 * 
 * Bulk inserts tasks into the database.
 * Optimized for high throughput (~2000 tasks/second).
 */
@Component
@Slf4j
public class TasksTool implements McpTool {

    public static final String TOOL_NAME = "mcp-tasks";
    private static final int MAX_BATCH_SIZE = 5000;
    
    private final AsyncBatchService asyncBatchService;
    private final Validator validator;
    private final ObjectMapper objectMapper;
    private final AuditLogger auditLogger;
    private final Timer executionTimer;
    private final Counter successCounter;
    private final Counter errorCounter;
    private final Counter tasksInsertedCounter;

    public TasksTool(
            AsyncBatchService asyncBatchService,
            Validator validator,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            AuditLogger auditLogger) {
        this.asyncBatchService = asyncBatchService;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.auditLogger = auditLogger;
        
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
        
        this.tasksInsertedCounter = Counter.builder("mcp.tasks.inserted.total")
            .description("Total number of tasks inserted")
            .register(meterRegistry);
    }

    /**
     * Returns tool definition for MCP registration.
     */
    @Override
    public Tool toolDefinition() {
        String inputSchema = """
            {
                "type": "object",
                "properties": {
                    "tasks": {
                        "type": "array",
                        "description": "Array of task objects to insert",
                        "items": {
                            "type": "object",
                            "properties": {
                                "title": {
                                    "type": "string",
                                    "description": "Short task title (required, max 255 characters)"
                                },
                                "description": {
                                    "type": "string",
                                    "description": "Detailed task description (optional, max 2000 characters)"
                                },
                                "status": {
                                    "type": "string",
                                    "enum": ["TODO", "IN_PROGRESS", "DONE"],
                                    "description": "Task status (required)"
                                },
                                "dueDate": {
                                    "type": "string",
                                    "format": "date",
                                    "description": "Due date in yyyy-MM-dd format (optional, e.g. 2025-12-31)"
                                }
                            },
                            "additionalProperties": false,
                            "required": ["title", "status"]
                        }
                    }
                },
                "additionalProperties": false,
                "required": ["tasks"]
            }
            """;
        
        return ToolDefinitionBuilder.buildTool(
            TOOL_NAME,
            "Asynchronously bulk insert tasks into the database. " +
                "Returns immediately with a job ID. Use mcp-job-status to check progress. " +
                "Maximum batch size is " + MAX_BATCH_SIZE + " tasks.",
            inputSchema
        );
    }

    /**
     * Executes the tool and inserts tasks.
     * 
     * @param arguments Must contain "tasks" array
     * @return ToolResult with insert stats or error
     */
    @Override
    @SuppressWarnings("unchecked")
    public CallToolResult execute(McpSyncServerExchange exchange, Map<String, Object> arguments) {
        Timer.Sample sample = Timer.start();
        
        try (var ignored = MDC.putCloseable("toolName", TOOL_NAME)) {
            log.info("Executing {}", TOOL_NAME);
            
            // Validate arguments
            if (!arguments.containsKey("tasks")) {
                throw new ValidationException("Missing required parameter: tasks");
            }
            
            Object tasksObj = arguments.get("tasks");
            if (!(tasksObj instanceof List)) {
                throw new ValidationException("Parameter 'tasks' must be an array");
            }
            
            List<Map<String, Object>> taskMaps = (List<Map<String, Object>>) tasksObj;
            
            if (taskMaps.isEmpty()) {
                throw new ValidationException("Tasks array cannot be empty");
            }
            
            if (taskMaps.size() > MAX_BATCH_SIZE) {
                throw new ValidationException(
                    String.format("Batch size %d exceeds maximum of %d tasks",
                        taskMaps.size(), MAX_BATCH_SIZE)
                );
            }
            
            // Convert and validate tasks
            List<Task> tasks = new ArrayList<>();
            for (int i = 0; i < taskMaps.size(); i++) {
                try {
                    Task task = convertAndValidate(taskMaps.get(i), i);
                    tasks.add(task);
                } catch (ValidationException e) {
                    throw new ValidationException(
                        String.format("Invalid task at index %d: %s", i, e.getMessage()), e
                    );
                } catch (Exception e) {
                    log.warn("Task conversion failed at index {}: {}", i, e.getMessage());
                    throw new ValidationException(
                        String.format("Invalid task at index %d: invalid field type or format", i)
                    );
                }
            }
            
            // Create job and start async processing
            BatchJob job = asyncBatchService.createJob(tasks.size());
            logToolInvocationStart(tasks.size());
            try {
            asyncBatchService.processAsync(job.getId(), tasks, percent -> {
                if (exchange != null) {
                    try {
                        exchange.loggingNotification(
                            McpSchema.LoggingMessageNotification.builder()
                                .level(McpSchema.LoggingLevel.INFO)
                                .logger(TOOL_NAME)
                                .data("Job " + job.getId() + ": " + percent + "% processed")
                                .build()
                        );
                    } catch (Exception ex) {
                        log.debug("Progress notification skipped: {}", ex.getMessage());
                    }
                }
            });
            } catch (TaskRejectedException e) {
                asyncBatchService.markJobFailed(job.getId(), "Executor queue full. Please retry later.");
                sample.stop(executionTimer);
                errorCounter.increment();
                logToolInvocationFailure(e.getMessage());
                return McpErrorResult.internalError("Server is busy. Please retry later.");
            }

            // Update metrics
            sample.stop(executionTimer);
            successCounter.increment();
            tasksInsertedCounter.increment(tasks.size());

            // Build response with job ID
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("jobId", job.getId());
            result.put("status", job.getStatus().getValue());
            result.put("totalTasks", tasks.size());
            result.put("message", "Batch processing started. Use mcp-job-status to check progress.");
            
            String jsonResult = objectMapper.writeValueAsString(result);
            
            try (var mdcJobId = MDC.putCloseable("jobId", job.getId());
                 var mdcCount = MDC.putCloseable("taskCount", String.valueOf(tasks.size()))) {
                log.info("Async batch job created: {} with {} tasks", job.getId(), tasks.size());
            }
            
            logToolInvocationSuccess(job.getId(), tasks.size());
            
            return new CallToolResult(
                List.of(new McpSchema.TextContent(jsonResult)),
                false
            );
            
        } catch (ValidationException e) {
            sample.stop(executionTimer);
            errorCounter.increment();
            log.warn("Validation error: {}", e.getMessage());
            logToolInvocationFailure(e.getMessage());
            return McpErrorResult.validationError(e.getMessage());

        } catch (Exception e) {
            sample.stop(executionTimer);
            errorCounter.increment();
            log.error("Error inserting tasks", e);
            logToolInvocationFailure(e.getMessage());
            return McpErrorResult.internalError("Internal error while inserting tasks. Please check server logs.");
        }
    }

    /**
     * Converts a map to Task entity with validation.
     */
    private Task convertAndValidate(Map<String, Object> taskMap, int index) {
        // Convert to DTO for validation
        TaskDto dto = objectMapper.convertValue(taskMap, TaskDto.class);
        
        // Validate using jakarta.validation
        var violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            String errors = violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Unknown validation error");
            throw new ValidationException(errors);
        }
        
        // Convert to entity
        return Task.builder()
            .title(dto.getTitle())
            .description(dto.getDescription())
            .status(TaskStatus.fromValue(dto.getStatus()))
            .dueDate(dto.getDueDate())
            .build();
    }

    /**
     * Log tool invocation start audit event.
     */
    private void logToolInvocationStart(int taskCount) {
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.TOOL_INVOCATION_START)
            .correlationId(CorrelationIdContext.getCurrentCorrelationId())
            .toolName(TOOL_NAME)
            .metadata("taskCount", taskCount)
            .build();
        auditLogger.log(event);
    }

    /**
     * Log tool invocation success audit event.
     */
    private void logToolInvocationSuccess(String jobId, int taskCount) {
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.TOOL_INVOCATION_SUCCESS)
            .correlationId(CorrelationIdContext.getCurrentCorrelationId())
            .toolName(TOOL_NAME)
            .success(true)
            .metadata("jobId", jobId)
            .metadata("taskCount", taskCount)
            .build();
        auditLogger.log(event);
    }

    /**
     * Log tool invocation failure audit event.
     */
    private void logToolInvocationFailure(String errorMessage) {
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.TOOL_INVOCATION_FAILURE)
            .correlationId(CorrelationIdContext.getCurrentCorrelationId())
            .toolName(TOOL_NAME)
            .success(false)
            .errorMessage(errorMessage)
            .build();
        auditLogger.log(event);
    }
}
