package com.example.mcptaskserver.unit.tools;


import com.example.mcptaskserver.audit.AuditEvent;
import com.example.mcptaskserver.audit.AuditLogger;
import com.example.mcptaskserver.mcp.tools.TasksFromFileTool;
import com.example.mcptaskserver.model.BatchJob;
import com.example.mcptaskserver.model.JobStatus;
import com.example.mcptaskserver.service.AsyncBatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskRejectedException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TasksFromFileToolTest {

    @Mock
    private AsyncBatchService asyncBatchService;

    @Mock
    private Validator validator;

    @Mock
    private AuditLogger auditLogger;

    private TasksFromFileTool tasksFromFileTool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        objectMapper = new ObjectMapper();
        tasksFromFileTool = new TasksFromFileTool(asyncBatchService, validator, objectMapper, meterRegistry, auditLogger);
    }

    @Test
    void execute_shouldRejectDirectoryTraversal() {
        // Given – ../tasks.json normalises to parent-of-CWD, which is outside the allowed whitelist
        Map<String, Object> args = Map.of("filePath", "../tasks.json");

        // When
        CallToolResult result = tasksFromFileTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("outside of allowed directories");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/root/file.json", "/sys/test.json", "/proc/test.json"})
    void execute_shouldRejectSystemDirectoriesWithJsonExtension(String filePath) {
        // Given – absolute paths with .json extension that lie outside CWD and tmpdir
        Map<String, Object> args = Map.of("filePath", filePath);

        // When
        CallToolResult result = tasksFromFileTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("outside of allowed directories");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/etc/passwd", "/var/log/syslog"})
    void execute_shouldRejectSystemPathsWithoutJsonExtension(String filePath) {
        // Given – paths without .json extension are rejected by the extension check first
        Map<String, Object> args = Map.of("filePath", filePath);

        // When
        CallToolResult result = tasksFromFileTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("Only .json files are allowed");
    }

    @Test
    void execute_shouldRejectHomeDirectoryExpansion() {
        // Given
        Map<String, Object> args = Map.of("filePath", "~/tasks.json");

        // When
        CallToolResult result = tasksFromFileTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("Home directory expansion");
    }

    @ParameterizedTest
    @ValueSource(strings = {"tasks.txt", "tasks.xml", "tasks.yaml", "tasks.csv"})
    void execute_shouldRejectNonJsonExtension(String filePath) {
        // Given
        Map<String, Object> args = Map.of("filePath", filePath);

        // When
        CallToolResult result = tasksFromFileTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("Only .json files are allowed");
    }

    @Test
    void execute_shouldRejectNullFilePath() {
        // Given
        Map<String, Object> args = Map.of();

        // When
        CallToolResult result = tasksFromFileTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("Missing required parameter");
    }

    @Test
    void execute_shouldRejectBlankFilePath() {
        // Given
        Map<String, Object> args = Map.of("filePath", "");

        // When
        CallToolResult result = tasksFromFileTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).containsAnyOf("cannot be empty", "cannot be null or blank");
    }

    @Test
    void execute_shouldRejectWhitespaceOnlyFilePath() {
        // Given
        Map<String, Object> args = Map.of("filePath", "   ");

        // When
        CallToolResult result = tasksFromFileTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).containsAnyOf("cannot be empty", "cannot be null or blank");
    }

    @Test
    void execute_shouldRejectMultipleDirectoryTraversals() {
        // Given – ../../etc/passwd has no .json extension; extension check fires first
        Map<String, Object> args = Map.of("filePath", "../../etc/passwd");

        // When
        CallToolResult result = tasksFromFileTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("Only .json files are allowed");
    }

    @Test
    void execute_shouldRejectRelativePathWithTraversal() {
        // Given – no .json extension; extension check fires before whitelist check
        Map<String, Object> args = Map.of("filePath", "./config/../../../etc/passwd");

        // When
        CallToolResult result = tasksFromFileTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("Only .json files are allowed");
    }

    @Test
    void execute_shouldRejectRelativePathWithTraversalAndJsonExtension() {
        // Given – has .json extension; whitelist check rejects after Path.normalize() resolves ..
        Map<String, Object> args = Map.of("filePath", "./config/../../../etc/passwd.json");

        // When
        CallToolResult result = tasksFromFileTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("outside of allowed directories");
    }

    @Test
    void execute_shouldNotLeakJacksonClassNames_whenJsonHasWrongFieldType(@TempDir Path tempDir) throws IOException {
        // Given - 'title' is a JSON object; Jackson readValue cannot map Object to String
        // and produces a message containing internal type names like java.lang.String
        Path tasksFile = tempDir.resolve("tasks.json");
        Files.writeString(tasksFile, "[{\"title\": {\"nested\": \"bad\"}, \"status\": \"TODO\"}]");
        Map<String, Object> args = Map.of("filePath", tasksFile.toString());

        // When
        CallToolResult result = tasksFromFileTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).doesNotContain("java.lang");
        assertThat(content).doesNotContain("MismatchedInputException");
        assertThat(content).doesNotContain("JsonMappingException");
        assertThat(content).contains("VALIDATION_ERROR");
    }

    @Test
    void execute_shouldNotLeakSyntaxDetails_whenJsonIsMalformed(@TempDir Path tempDir) throws IOException {
        // Given - broken JSON (unclosed array)
        Path tasksFile = tempDir.resolve("tasks.json");
        Files.writeString(tasksFile, "[{\"title\": \"Task\", \"status\": \"TODO\"");
        Map<String, Object> args = Map.of("filePath", tasksFile.toString());

        // When
        CallToolResult result = tasksFromFileTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).doesNotContain("JsonParseException");
        assertThat(content).doesNotContain("com.fasterxml");
        assertThat(content).contains("VALIDATION_ERROR");
    }

    @Test
    void execute_shouldNotLeakInternalErrorDetails_whenServiceThrowsUnexpectedly(@TempDir Path tempDir) throws IOException {
        // Given - a valid JSON file in an allowed directory (tmpdir)
        Path tasksFile = tempDir.resolve("tasks.json");
        Files.writeString(tasksFile, "[{\"title\":\"Test Task\",\"status\":\"TODO\"}]");
        when(validator.validate(any())).thenReturn(Set.of());
        when(asyncBatchService.createJob(1))
            .thenThrow(new RuntimeException("DataSource connection refused: internal-db-host:5432/taskdb"));

        Map<String, Object> args = Map.of("filePath", tasksFile.toString());

        // When
        CallToolResult result = tasksFromFileTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).doesNotContain("DataSource");
        assertThat(content).doesNotContain("internal-db-host");
        assertThat(content).contains("Internal error");
    }

    @Test
    void execute_shouldRejectAbsoluteSystemPath() {
        // Given – absolute path outside CWD and tmpdir; whitelist check rejects
        Map<String, Object> args = Map.of("filePath", "/etc/tasks.json");

        // When
        CallToolResult result = tasksFromFileTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("outside of allowed directories");
    }

    @Test
    void execute_shouldLogAuditStartAndSuccess_onSuccessfulImport(@TempDir Path tempDir) throws IOException {
        // Given
        Path tasksFile = tempDir.resolve("tasks.json");
        Files.writeString(tasksFile, "[{\"title\":\"Audit Task\",\"status\":\"TODO\"}]");
        when(validator.validate(any())).thenReturn(Set.of());
        BatchJob job = new BatchJob();
        job.setId("job-audit-1");
        job.setStatus(JobStatus.PENDING);
        job.setTotalTasks(1);
        when(asyncBatchService.createJob(1)).thenReturn(job);

        Map<String, Object> args = Map.of("filePath", tasksFile.toString());

        // When
        CallToolResult result = tasksFromFileTool.execute(args);

        // Then
        assertThat(result.isError()).isFalse();
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger, atLeast(2)).log(eventCaptor.capture());
        List<AuditEvent> events = eventCaptor.getAllValues();
        assertThat(events).anyMatch(e -> "TOOL_INVOCATION_START".equals(e.eventType()));
        assertThat(events).anyMatch(e -> "TOOL_INVOCATION_SUCCESS".equals(e.eventType()));
    }

    @Test
    void execute_shouldLogAuditFailure_onValidationError() {
        // Given – missing filePath triggers validation error before any job is created
        Map<String, Object> args = Map.of();

        // When
        CallToolResult result = tasksFromFileTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).log(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("TOOL_INVOCATION_FAILURE");
    }

    @Test
    void execute_shouldMarkJobFailedAndReturnError_whenExecutorRejectsTask(@TempDir Path tempDir) throws IOException {
        // Given
        Path tasksFile = tempDir.resolve("tasks.json");
        Files.writeString(tasksFile, "[{\"title\":\"Task\",\"status\":\"TODO\"}]");
        when(validator.validate(any())).thenReturn(Set.of());
        BatchJob job = new BatchJob();
        job.setId("job-rejected");
        job.setStatus(JobStatus.PENDING);
        job.setTotalTasks(1);
        when(asyncBatchService.createJob(1)).thenReturn(job);
        doThrow(new TaskRejectedException("Queue full"))
            .when(asyncBatchService).processAsync(eq("job-rejected"), any(), any());

        Map<String, Object> args = Map.of("filePath", tasksFile.toString());

        // When
        CallToolResult result = tasksFromFileTool.execute(args);

        // Then – error returned to caller and job is marked failed
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("busy");
        verify(asyncBatchService).markJobFailed(eq("job-rejected"), any());
    }
}
