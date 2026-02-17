package com.example.mcptaskserver.mcp.prompts;

import com.example.mcptaskserver.audit.AuditEvent;
import com.example.mcptaskserver.audit.AuditEventType;
import com.example.mcptaskserver.audit.AuditLogger;
import com.example.mcptaskserver.dto.TaskSummaryDto;
import com.example.mcptaskserver.exception.PromptExecutionException;
import com.example.mcptaskserver.model.TaskStatus;
import com.example.mcptaskserver.service.TaskService;
import com.example.mcptaskserver.util.CorrelationIdContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP Prompt Provider for task-related prompt templates.
 * Exposes structured prompt templates for AI-driven task management:
 * - create-tasks-from-description: generate tasks from a natural language description
 * - summarize-tasks-by-status:     request a task summary, optionally filtered by status
 * - task-report-template:          produce a formatted task report
 */
@Component
@Slf4j
public class TaskPromptProvider {

    private final TaskService taskService;
    private final AuditLogger auditLogger;
    private final Timer promptGetTimer;
    private final Counter promptSuccessCounter;
    private final Counter promptErrorCounter;

    public TaskPromptProvider(
            TaskService taskService,
            MeterRegistry meterRegistry,
            AuditLogger auditLogger) {
        this.taskService = taskService;
        this.auditLogger = auditLogger;

        this.promptGetTimer = Timer.builder("mcp.prompt.get")
            .description("Prompt get execution time")
            .register(meterRegistry);
        this.promptSuccessCounter = Counter.builder("mcp.prompt.success")
            .description("Successful prompt gets")
            .register(meterRegistry);
        this.promptErrorCounter = Counter.builder("mcp.prompt.error")
            .description("Failed prompt gets")
            .register(meterRegistry);
    }

    /**
     * Returns all prompt specifications to be registered with the MCP server.
     *
     * @return list of sync prompt specifications
     */
    public List<McpServerFeatures.SyncPromptSpecification> getPromptSpecifications() {
        return List.of(
            buildCreateTasksFromDescriptionPrompt(),
            buildSummarizeTasksByStatusPrompt(),
            buildTaskReportTemplatePrompt()
        );
    }

    // -------------------------------------------------------------------------
    // Prompt: create-tasks-from-description
    // -------------------------------------------------------------------------

    private McpServerFeatures.SyncPromptSpecification buildCreateTasksFromDescriptionPrompt() {
        McpSchema.Prompt metadata = new McpSchema.Prompt(
            "create-tasks-from-description",
            "Generates structured tasks from a natural language description",
            List.of(
                new McpSchema.PromptArgument("description",
                    "Natural language description of the work to be done", true)
            )
        );

        return new McpServerFeatures.SyncPromptSpecification(
            metadata,
            (exchange, request) -> handlePrompt("create-tasks-from-description", () -> {
                String description = request.arguments() != null
                    ? (String) request.arguments().getOrDefault("description", "") : "";

                String userMessage = """
                    Analyze the following description and create structured tasks.
                    For each task provide: title (short, imperative), description (what & why), \
                    and status (TODO, IN_PROGRESS, or DONE).
                    Return the result as a JSON array of objects with fields: title, description, status.

                    Description:
                    %s
                    """.formatted(description);

                return new McpSchema.GetPromptResult(
                    "Create tasks from: " + description,
                    List.of(new McpSchema.PromptMessage(
                        McpSchema.Role.USER,
                        new McpSchema.TextContent(userMessage)
                    ))
                );
            })
        );
    }

    // -------------------------------------------------------------------------
    // Prompt: summarize-tasks-by-status
    // -------------------------------------------------------------------------

    private McpServerFeatures.SyncPromptSpecification buildSummarizeTasksByStatusPrompt() {
        String validStatuses = Arrays.stream(TaskStatus.values())
            .map(Enum::name)
            .collect(Collectors.joining(", "));

        McpSchema.Prompt metadata = new McpSchema.Prompt(
            "summarize-tasks-by-status",
            "Summarizes tasks, optionally filtered by status",
            List.of(
                new McpSchema.PromptArgument("status",
                    "Optional status filter: " + validStatuses, false)
            )
        );

        return new McpServerFeatures.SyncPromptSpecification(
            metadata,
            (exchange, request) -> handlePrompt("summarize-tasks-by-status", () -> {
                String statusArg = request.arguments() != null
                    ? (String) request.arguments().get("status") : null;

                TaskSummaryDto summary = taskService.generateSummary();

                String statsSection = buildStatsSection(summary);

                String userMessage;
                if (statusArg != null && !statusArg.isBlank()) {
                    long statusCount = summary.countByStatus() != null
                        ? summary.countByStatus().getOrDefault(statusArg, 0L) : 0L;
                    userMessage = """
                        Summarize the following task statistics, focusing on status "%s" (%d tasks).

                        Current task statistics:
                        %s

                        Provide a brief, structured summary highlighting key insights for the "%s" status.
                        """.formatted(statusArg, statusCount, statsSection, statusArg);
                } else {
                    userMessage = """
                        Summarize the following task statistics across all statuses.

                        Current task statistics:
                        %s

                        Provide a brief, structured summary with key insights and recommendations.
                        """.formatted(statsSection);
                }

                return new McpSchema.GetPromptResult(
                    statusArg != null && !statusArg.isBlank()
                        ? "Task summary for status: " + statusArg
                        : "Overall task summary",
                    List.of(new McpSchema.PromptMessage(
                        McpSchema.Role.USER,
                        new McpSchema.TextContent(userMessage)
                    ))
                );
            })
        );
    }

    // -------------------------------------------------------------------------
    // Prompt: task-report-template
    // -------------------------------------------------------------------------

    private McpServerFeatures.SyncPromptSpecification buildTaskReportTemplatePrompt() {
        McpSchema.Prompt metadata = new McpSchema.Prompt(
            "task-report-template",
            "Generates a formatted task report, optionally in brief or detailed format",
            List.of(
                new McpSchema.PromptArgument("format",
                    "Report format: 'brief' (default) or 'detailed'", false)
            )
        );

        return new McpServerFeatures.SyncPromptSpecification(
            metadata,
            (exchange, request) -> handlePrompt("task-report-template", () -> {
                String format = request.arguments() != null
                    ? (String) request.arguments().getOrDefault("format", "brief") : "brief";
                boolean detailed = "detailed".equalsIgnoreCase(format);

                TaskSummaryDto summary = taskService.generateSummary();
                String statsSection = buildStatsSection(summary);

                String userMessage = detailed
                    ? """
                      Generate a detailed task report using the following data.

                      ## Current Task Statistics
                      - Total tasks: %d
                      - Generated at: %s

                      ## Breakdown by Status
                      %s

                      Structure your report with these sections:
                      1. Overview (totals and date)
                      2. Breakdown by Status
                      3. Analysis (task distribution, blockers, progress trends)
                      4. Recommendations (actionable items based on current state)
                      """.formatted(
                          summary.totalCount(),
                          summary.generatedAt(),
                          statsSection)
                    : """
                      Generate a brief task report using the following data.

                      Total tasks: %d
                      %s

                      Write one paragraph summarising progress and highlighting any urgent items.
                      """.formatted(summary.totalCount(), statsSection);

                return new McpSchema.GetPromptResult(
                    (detailed ? "Detailed" : "Brief") + " task report template",
                    List.of(new McpSchema.PromptMessage(
                        McpSchema.Role.USER,
                        new McpSchema.TextContent(userMessage)
                    ))
                );
            })
        );
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private McpSchema.GetPromptResult handlePrompt(String promptName,
                                                    PromptHandler handler) {
        String correlationId = CorrelationIdContext.getCurrentCorrelationId();
        logAuditEvent(AuditEventType.PROMPT_GET_START, correlationId, promptName, true, null);

        return promptGetTimer.record(() -> {
            try {
                McpSchema.GetPromptResult result = handler.handle();
                promptSuccessCounter.increment();
                logAuditEvent(AuditEventType.PROMPT_GET_SUCCESS, correlationId, promptName, true, null);
                log.debug("Prompt '{}' executed successfully", promptName);
                return result;
            } catch (Exception e) {
                promptErrorCounter.increment();
                logAuditEvent(AuditEventType.PROMPT_GET_FAILURE, correlationId, promptName, false, e.getMessage());
                log.error("Prompt '{}' execution failed", promptName, e);
                // Do not propagate the cause — internal details stay in logs, not in the MCP client response.
                throw new PromptExecutionException(promptName);
            }
        });
    }

    @FunctionalInterface
    private interface PromptHandler {
        McpSchema.GetPromptResult handle();
    }

    private String buildStatsSection(TaskSummaryDto summary) {
        if (summary.countByStatus() == null || summary.countByStatus().isEmpty()) {
            return "  No tasks found.";
        }
        return summary.countByStatus().entrySet().stream()
            .map(e -> "  - " + e.getKey() + ": " + e.getValue())
            .collect(Collectors.joining("\n"));
    }

    private void logAuditEvent(AuditEventType eventType, String correlationId,
                               String promptName, boolean success, String errorMessage) {
        AuditEvent event = AuditEvent.builder()
            .eventType(eventType)
            .correlationId(correlationId)
            .success(success)
            .errorMessage(errorMessage)
            .metadata(Map.of("promptName", promptName))
            .build();
        auditLogger.log(event);
    }
}
