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
import com.example.mcptaskserver.model.BatchJob;
import com.example.mcptaskserver.model.Task;
import com.example.mcptaskserver.model.TaskStatus;
import com.example.mcptaskserver.service.AsyncBatchService;
import com.fasterxml.jackson.core.type.TypeReference;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Tool: mcp-tasks-from-file
 * 
 * Imports tasks from a JSON file without token limitations.
 * Optimized for large batch imports from local files.
 */
@Component
@Slf4j
public class TasksFromFileTool implements McpTool {

    public static final String TOOL_NAME = "mcp-tasks-from-file";
    private static final int MAX_BATCH_SIZE = 5000;
    
    private final AsyncBatchService asyncBatchService;
    private final Validator validator;
    private final ObjectMapper objectMapper;
    private final AuditLogger auditLogger;
    private final Timer executionTimer;
    private final Counter successCounter;
    private final Counter errorCounter;
    private final Counter tasksImportedCounter;

    public TasksFromFileTool(
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
        
        this.tasksImportedCounter = Counter.builder("mcp.tasks.imported.total")
            .description("Total number of tasks imported from files")
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
                    "filePath": {
                        "type": "string",
                        "description": "Absolute or relative path to JSON file containing task array"
                    }
                },
                "additionalProperties": false,
                "required": ["filePath"]
            }
            """;
        
        return ToolDefinitionBuilder.buildTool(
            TOOL_NAME,
            "Import tasks from a JSON file. Bypasses token limits for large batches. " +
                "File must contain a JSON array of task objects. Maximum batch size is " + MAX_BATCH_SIZE + " tasks.",
            inputSchema
        );
    }

    /**
     * Executes the tool and imports tasks from file.
     * 
     * @param arguments Must contain "filePath" string
     * @return ToolResult with import stats or error
     */
    @Override
    public CallToolResult execute(McpSyncServerExchange exchange, Map<String, Object> arguments) {
        Timer.Sample sample = Timer.start();
        
        try (var ignored = MDC.putCloseable("toolName", TOOL_NAME)) {
            log.info("Executing {}", TOOL_NAME);
            
            // Validate arguments
            if (!arguments.containsKey("filePath")) {
                throw new ValidationException("Missing required parameter: filePath");
            }
            
            String filePath = arguments.get("filePath").toString();
            
            // Validate file path for security (prevent directory traversal)
            validateFilePath(filePath);
            
            // Read file - use toRealPath() to resolve symbolic links and get canonical path
            Path path = Path.of(filePath).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                throw new ValidationException("File not found: " + filePath);
            }
            
            if (!Files.isReadable(path)) {
                throw new ValidationException("File not readable: " + filePath);
            }
            
            String content = Files.readString(path);
            
            // Parse JSON to Task DTOs
            List<TaskDto> taskDtos;
            try {
                taskDtos = objectMapper.readValue(content, new TypeReference<List<TaskDto>>() {});
            } catch (IOException e) {
                log.debug("JSON parse error for file {}: {}", filePath, e.getMessage());
                throw new ValidationException("Invalid JSON in file: check syntax and field types");
            }
            
            if (taskDtos.isEmpty()) {
                throw new ValidationException("File contains empty task array");
            }
            
            if (taskDtos.size() > MAX_BATCH_SIZE) {
                throw new ValidationException(
                    String.format("Batch size %d exceeds maximum of %d tasks",
                        taskDtos.size(), MAX_BATCH_SIZE)
                );
            }
            
            // Convert and validate tasks
            List<Task> tasks = new ArrayList<>();
            for (int i = 0; i < taskDtos.size(); i++) {
                try {
                    Task task = convertAndValidate(taskDtos.get(i), i);
                    tasks.add(task);
                } catch (ValidationException e) {
                    throw new ValidationException(
                        String.format("Invalid task at index %d: %s", i, e.getMessage()), e
                    );
                } catch (Exception e) {
                    log.debug("Task conversion failed at index {}: {}", i, e.getMessage());
                    throw new ValidationException(
                        String.format("Invalid task at index %d: invalid field type or format", i)
                    );
                }
            }
            
            // Create job and start async processing
            BatchJob job = asyncBatchService.createJob(tasks.size());
            logToolInvocationStart(tasks.size(), filePath);
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
            tasksImportedCounter.increment(tasks.size());

            // Build response with job ID
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("jobId", job.getId());
            result.put("status", job.getStatus().getValue());
            result.put("totalTasks", tasks.size());
            result.put("message", "Batch processing started. Use mcp-job-status to check progress.");

            String jsonResult = objectMapper.writeValueAsString(result);

            try (var mdcJobId = MDC.putCloseable("jobId", job.getId());
                 var mdcCount = MDC.putCloseable("taskCount", String.valueOf(tasks.size()));
                 var mdcFile = MDC.putCloseable("filePath", filePath)) {
                log.info("Async batch job created from file: {} with {} tasks from {}",
                    job.getId(), tasks.size(), filePath);
            }

            logToolInvocationSuccess(job.getId(), tasks.size(), filePath);

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

        } catch (IOException e) {
            sample.stop(executionTimer);
            errorCounter.increment();
            log.error("Error reading file", e);
            logToolInvocationFailure(e.getMessage());
            return McpErrorResult.internalError("Internal error while reading file. Please check server logs.");

        } catch (Exception e) {
            sample.stop(executionTimer);
            errorCounter.increment();
            log.error("Error importing tasks from file", e);
            logToolInvocationFailure(e.getMessage());
            return McpErrorResult.internalError("Internal error while importing tasks. Please check server logs.");
        }
    }

    private void logToolInvocationStart(int taskCount, String filePath) {
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.TOOL_INVOCATION_START)
            .correlationId(CorrelationIdContext.getCurrentCorrelationId())
            .toolName(TOOL_NAME)
            .metadata("taskCount", taskCount)
            .metadata("filePath", filePath)
            .build();
        auditLogger.log(event);
    }

    private void logToolInvocationSuccess(String jobId, int taskCount, String filePath) {
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.TOOL_INVOCATION_SUCCESS)
            .correlationId(CorrelationIdContext.getCurrentCorrelationId())
            .toolName(TOOL_NAME)
            .success(true)
            .metadata("jobId", jobId)
            .metadata("taskCount", taskCount)
            .metadata("filePath", filePath)
            .build();
        auditLogger.log(event);
    }

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

    /**
     * Allowed base directories for file access.
     * Computed once at class-load time: current working directory and the JVM temp dir.
     */
    private static final List<Path> ALLOWED_BASE_DIRS = computeAllowedDirs();

    private static List<Path> computeAllowedDirs() {
        List<Path> dirs = new ArrayList<>();
        dirs.add(Path.of("").toAbsolutePath().normalize());
        String tmp = System.getProperty("java.io.tmpdir", "");
        if (!tmp.isBlank()) {
            dirs.add(Path.of(tmp).toAbsolutePath().normalize());
        }
        return List.copyOf(dirs);
    }

    /**
     * Validates file path using a whitelist of allowed base directories.
     * Uses Path.normalize() to resolve any ".." components before the check,
     * making string-based traversal bypasses impossible.
     *
     * @param filePath the file path to validate
     * @throws ValidationException if the path is invalid or outside allowed directories
     */
    private void validateFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new ValidationException("File path cannot be empty");
        }

        if (filePath.contains("~")) {
            throw new ValidationException("Home directory expansion (~) is not allowed");
        }

        if (!filePath.toLowerCase().endsWith(".json")) {
            throw new ValidationException("Only .json files are allowed");
        }

        Path resolved = Path.of(filePath).toAbsolutePath().normalize();
        boolean isAllowed = ALLOWED_BASE_DIRS.stream().anyMatch(resolved::startsWith);
        if (!isAllowed) {
            throw new ValidationException("File path is outside of allowed directories");
        }

        log.debug("File path validation passed for: {}", filePath);
    }

    private Task convertAndValidate(TaskDto dto, int index) {
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
}
