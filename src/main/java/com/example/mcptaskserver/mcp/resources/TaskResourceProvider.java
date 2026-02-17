package com.example.mcptaskserver.mcp.resources;

import com.example.mcptaskserver.audit.AuditEvent;
import com.example.mcptaskserver.audit.AuditEventType;
import com.example.mcptaskserver.audit.AuditLogger;
import com.example.mcptaskserver.dto.TaskResourceDto;
import com.example.mcptaskserver.dto.TaskSummaryDto;
import com.example.mcptaskserver.exception.ResourceNotFoundException;
import com.example.mcptaskserver.model.Task;
import com.example.mcptaskserver.service.TaskService;
import com.example.mcptaskserver.util.CorrelationIdContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP Resource Provider for task-related resources.
 * Exposes tasks and statistics as MCP resources with URIs:
 * - task://all - List all tasks
 * - task://{id} - Get single task by ID
 * - db://stats - Database statistics
 */
@Component
@Slf4j
public class TaskResourceProvider {

    private final TaskService taskService;
    private final ObjectMapper objectMapper;
    private final AuditLogger auditLogger;
    private final Timer resourceReadTimer;
    private final Counter resourceSuccessCounter;
    private final Counter resourceErrorCounter;
    private final Counter resourceNotFoundCounter;

    private static final Pattern TASK_ID_PATTERN = Pattern.compile("^task://([0-9]+)$");

    @Value("${mcp.resource.max-tasks:1000}")
    private int resourceMaxTasks;

    public TaskResourceProvider(
            TaskService taskService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            AuditLogger auditLogger) {
        this.taskService = taskService;
        this.objectMapper = objectMapper;
        this.auditLogger = auditLogger;

        // Metrics
        this.resourceReadTimer = Timer.builder("mcp.resource.read")
            .description("Resource read execution time")
            .register(meterRegistry);
        this.resourceSuccessCounter = Counter.builder("mcp.resource.success")
            .description("Successful resource reads")
            .register(meterRegistry);
        this.resourceErrorCounter = Counter.builder("mcp.resource.error")
            .description("Failed resource reads")
            .register(meterRegistry);
        this.resourceNotFoundCounter = Counter.builder("mcp.resource.notfound")
            .description("Resource not found")
            .register(meterRegistry);
    }

    /**
     * Returns static resource specifications (fixed URIs, no template variables).
     * These are registered via addResource() and discoverable via listResources().
     *
     * @return list of sync static resource specifications
     */
    public List<McpServerFeatures.SyncResourceSpecification> getStaticResourceSpecifications() {
        return List.of(
            // task://all - All tasks
            new McpServerFeatures.SyncResourceSpecification(
                new McpSchema.Resource(
                    "task://all",               // uri
                    "All Tasks",                // name
                    "Retrieves up to " + resourceMaxTasks + " tasks from the database as a JSON array. " +
                    "For paginated access to all tasks use the mcp-tasks-list tool.",  // description
                    "application/json",         // mimeType
                    null                        // annotations
                ),
                (exchange, request) -> handleReadResource(request.uri())
            ),

            // db://stats - Database statistics
            new McpServerFeatures.SyncResourceSpecification(
                new McpSchema.Resource(
                    "db://stats",               // uri
                    "Database Statistics",      // name
                    "Returns database statistics including task counts by status",  // description
                    "application/json",         // mimeType
                    null                        // annotations
                ),
                (exchange, request) -> handleReadResource(request.uri())
            )
        );
    }

    /**
     * Returns resource template specifications (URIs with template variables).
     * These are registered via addResourceTemplate() and discoverable via listResourceTemplates().
     *
     * @return list of sync resource template specifications
     */
    public List<McpServerFeatures.SyncResourceTemplateSpecification> getTemplateResourceSpecifications() {
        return List.of(
            // task://{id} - Single task by ID
            new McpServerFeatures.SyncResourceTemplateSpecification(
                new McpSchema.ResourceTemplate(
                    "task://{id}",              // uriTemplate
                    "Task by ID",               // name
                    "Retrieves a single task by its ID",  // description
                    "application/json",         // mimeType
                    null                        // annotations
                ),
                (exchange, request) -> handleReadResource(request.uri())
            )
        );
    }

    /**
     * Handles a resource read request.
     *
     * @param uri the resource URI
     * @return the read resource result
     */
    private McpSchema.ReadResourceResult handleReadResource(String uri) {
        String correlationId = CorrelationIdContext.getCurrentCorrelationId();
        
        // Audit: Start
        logAuditEvent(AuditEventType.RESOURCE_READ_START, correlationId, uri, true, null);

        return resourceReadTimer.record(() -> {
            try {
                McpSchema.ResourceContents contents = readResourceContents(uri);

                // Metrics & Audit: Success
                resourceSuccessCounter.increment();
                logAuditEvent(AuditEventType.RESOURCE_READ_SUCCESS, correlationId, uri, true, null);

                return new McpSchema.ReadResourceResult(List.of(contents));
                
            } catch (ResourceNotFoundException e) {
                // Metrics & Audit: Not Found
                resourceNotFoundCounter.increment();
                logAuditEvent(AuditEventType.RESOURCE_NOT_FOUND, correlationId, uri, false, e.getMessage());
                throw e;
                
            } catch (Exception e) {
                // Metrics & Audit: Error
                resourceErrorCounter.increment();
                logAuditEvent(AuditEventType.RESOURCE_READ_FAILURE, correlationId, uri, false, e.getMessage());
                throw new RuntimeException("Resource read failed: " + uri, e);
            }
        });
    }

    /**
     * Reads the contents of a resource by URI.
     */
    private McpSchema.ResourceContents readResourceContents(String uri) throws JsonProcessingException {
        return switch (uri) {
            case "task://all" -> readAllTasks();
            case "db://stats" -> readDatabaseStats();
            default -> {
                Matcher matcher = TASK_ID_PATTERN.matcher(uri);
                if (matcher.matches()) {
                    long id = Long.parseLong(matcher.group(1));
                    yield readTaskById(id);
                }
                throw new ResourceNotFoundException(uri, "Unknown resource URI pattern");
            }
        };
    }

    private McpSchema.ResourceContents readAllTasks() throws JsonProcessingException {
        List<Task> tasks = taskService.findAll(resourceMaxTasks);
        List<TaskResourceDto> taskDtos = tasks.stream()
            .map(TaskResourceDto::fromEntity)
            .toList();
        
        String json = objectMapper.writeValueAsString(taskDtos);
        
        return new McpSchema.TextResourceContents(
            "task://all",           // uri
            "application/json",     // mimeType
            json                    // text
        );
    }

    private McpSchema.ResourceContents readTaskById(long id) throws JsonProcessingException {
        Task task = taskService.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "task://" + id, 
                "Task with ID " + id + " not found"
            ));
        
        TaskResourceDto taskDto = TaskResourceDto.fromEntity(task);
        String json = objectMapper.writeValueAsString(taskDto);
        
        return new McpSchema.TextResourceContents(
            "task://" + id,         // uri
            "application/json",     // mimeType
            json                    // text
        );
    }

    private McpSchema.ResourceContents readDatabaseStats() throws JsonProcessingException {
        TaskSummaryDto stats = taskService.generateSummary();
        String json = objectMapper.writeValueAsString(stats);
        
        return new McpSchema.TextResourceContents(
            "db://stats",           // uri
            "application/json",     // mimeType
            json                    // text
        );
    }

    private void logAuditEvent(AuditEventType eventType, String correlationId,
                               String uri, boolean success, String errorMessage) {
        AuditEvent event = AuditEvent.builder()
            .eventType(eventType)
            .correlationId(correlationId)
            .success(success)
            .errorMessage(errorMessage)
            .metadata(Map.of("uri", uri))
            .build();
        auditLogger.log(event);
    }
}
