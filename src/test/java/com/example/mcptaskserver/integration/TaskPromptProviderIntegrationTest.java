package com.example.mcptaskserver.integration;

import com.example.mcptaskserver.mcp.prompts.TaskPromptProvider;
import com.example.mcptaskserver.mcp.tools.TasksTool;
import com.example.mcptaskserver.repository.TaskRepository;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaskPromptProviderIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TaskPromptProvider taskPromptProvider;

    @Autowired
    private TasksTool tasksTool;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    @Test
    void shouldRegisterExactlyThreePrompts() {
        List<McpServerFeatures.SyncPromptSpecification> specs =
            taskPromptProvider.getPromptSpecifications();

        assertThat(specs).hasSize(3);
    }

    @Test
    void shouldRegisterAllExpectedPromptNames() {
        List<String> names = taskPromptProvider.getPromptSpecifications().stream()
            .map(spec -> spec.prompt().name())
            .toList();

        assertThat(names).containsExactlyInAnyOrder(
            "create-tasks-from-description",
            "summarize-tasks-by-status",
            "task-report-template"
        );
    }

    // -------------------------------------------------------------------------
    // create-tasks-from-description
    // -------------------------------------------------------------------------

    @Test
    void createTasksPrompt_shouldReturnUserRoleMessage() {
        McpSchema.GetPromptResult result = invokePrompt(
            "create-tasks-from-description",
            Map.of("description", "Build a login page with form validation")
        );

        assertThat(result).isNotNull();
        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().getFirst().role()).isEqualTo(McpSchema.Role.USER);
    }

    @Test
    void createTasksPrompt_shouldEmbedDescriptionInMessage() {
        String description = "Implement OAuth2 authentication";

        McpSchema.GetPromptResult result = invokePrompt(
            "create-tasks-from-description",
            Map.of("description", description)
        );

        String text = extractText(result);
        assertThat(text).contains(description);
    }

    @Test
    void createTasksPrompt_shouldHandleMissingArgumentGracefully() {
        McpSchema.GetPromptResult result = invokePrompt(
            "create-tasks-from-description",
            Map.of()
        );

        assertThat(result).isNotNull();
        assertThat(result.messages()).isNotEmpty();
    }

    // -------------------------------------------------------------------------
    // summarize-tasks-by-status
    // -------------------------------------------------------------------------

    @Test
    void summarizePrompt_withoutStatus_shouldReturnOverallSummary() {
        seedTasks();

        McpSchema.GetPromptResult result = invokePrompt(
            "summarize-tasks-by-status",
            Map.of()
        );

        assertThat(result).isNotNull();
        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().getFirst().role()).isEqualTo(McpSchema.Role.USER);
        assertThat(result.description()).contains("summary");
    }

    @Test
    void summarizePrompt_withStatus_shouldMentionStatusInDescription() {
        seedTasks();

        McpSchema.GetPromptResult result = invokePrompt(
            "summarize-tasks-by-status",
            Map.of("status", "TODO")
        );

        assertThat(result).isNotNull();
        assertThat(result.description()).containsIgnoringCase("TODO");
    }

    @Test
    void summarizePrompt_withStatus_shouldEmbedStatsInMessage() {
        seedTasks();

        McpSchema.GetPromptResult result = invokePrompt(
            "summarize-tasks-by-status",
            Map.of("status", "IN_PROGRESS")
        );

        String text = extractText(result);
        assertThat(text).containsAnyOf("IN_PROGRESS", "statistics", "task");
    }

    // -------------------------------------------------------------------------
    // task-report-template
    // -------------------------------------------------------------------------

    @Test
    void reportPrompt_defaultFormat_shouldReturnUserMessage() {
        seedTasks();

        McpSchema.GetPromptResult result = invokePrompt(
            "task-report-template",
            Map.of()
        );

        assertThat(result).isNotNull();
        assertThat(result.messages()).hasSize(1);
        // USER role ensures universal MCP client compatibility (no AI-prefill pattern)
        assertThat(result.messages().getFirst().role()).isEqualTo(McpSchema.Role.USER);
    }

    @Test
    void reportPrompt_briefFormat_shouldProduceBriefTemplate() {
        seedTasks();

        McpSchema.GetPromptResult result = invokePrompt(
            "task-report-template",
            Map.of("format", "brief")
        );

        String text = extractText(result);
        assertThat(text).containsIgnoringCase("brief");
        assertThat(result.description()).containsIgnoringCase("brief");
    }

    @Test
    void reportPrompt_detailedFormat_shouldProduceDetailedTemplate() {
        seedTasks();

        McpSchema.GetPromptResult result = invokePrompt(
            "task-report-template",
            Map.of("format", "detailed")
        );

        String text = extractText(result);
        assertThat(text).containsIgnoringCase("detailed");
        assertThat(text).containsIgnoringCase("recommendations");
    }

    @Test
    void reportPrompt_shouldEmbedTotalCountFromDatabase() {
        seedTasks();

        McpSchema.GetPromptResult result = invokePrompt(
            "task-report-template",
            Map.of("format", "detailed")
        );

        String text = extractText(result);
        // 3 tasks were seeded
        assertThat(text).contains("3");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void seedTasks() {
        List<Map<String, Object>> tasks = List.of(
            Map.of("title", "Task 1", "status", "TODO"),
            Map.of("title", "Task 2", "status", "IN_PROGRESS"),
            Map.of("title", "Task 3", "status", "DONE")
        );
        tasksTool.execute(Map.of("tasks", tasks));
        Awaitility.await().atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> assertThat(taskRepository.count()).isEqualTo(3));
    }

    private McpSchema.GetPromptResult invokePrompt(String name, Map<String, Object> args) {
        return taskPromptProvider.getPromptSpecifications().stream()
            .filter(spec -> spec.prompt().name().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown prompt: " + name))
            .promptHandler()
            .apply(null, new McpSchema.GetPromptRequest(name, args));
    }

    private String extractText(McpSchema.GetPromptResult result) {
        return result.messages().stream()
            .map(msg -> {
                if (msg.content() instanceof McpSchema.TextContent tc) {
                    return tc.text();
                }
                return "";
            })
            .reduce("", String::concat);
    }
}
