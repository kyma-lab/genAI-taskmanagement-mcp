package com.example.mcptaskserver.unit.tools;

import com.example.mcptaskserver.audit.AuditLogger;
import com.example.mcptaskserver.mcp.tools.TasksTool;
import com.example.mcptaskserver.model.BatchJob;
import com.example.mcptaskserver.model.JobStatus;
import com.example.mcptaskserver.service.AsyncBatchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import org.springframework.core.task.TaskRejectedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TasksToolTest {

    @Mock
    private AsyncBatchService asyncBatchService;

    @Mock
    private Validator validator;

    @Mock
    private AuditLogger auditLogger;

    private TasksTool tasksTool;

    @BeforeEach
    void setUp() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        ObjectMapper objectMapper = new ObjectMapper();
        tasksTool = new TasksTool(asyncBatchService, validator, objectMapper, meterRegistry, auditLogger);
    }

    @Test
    void execute_shouldAcceptBatchSize_whenExactlyAtMaxLimit() {
        // Given - exactly 5000 tasks
        List<Map<String, Object>> tasks = createTaskMaps(5000);
        Map<String, Object> args = Map.of("tasks", tasks);
        
        BatchJob mockJob = new BatchJob();
        mockJob.setId("test-job-id");
        mockJob.setStatus(JobStatus.PENDING);
        when(asyncBatchService.createJob(anyInt())).thenReturn(mockJob);

        // When
        CallToolResult result = tasksTool.execute(args);

        // Then - should succeed without validation error
        assertThat(result.isError()).isFalse();
        verify(asyncBatchService).createJob(5000);
    }

    @Test
    void execute_shouldRejectBatchSize_whenOverMaxLimit() {
        // Given - 5001 tasks (over limit)
        List<Map<String, Object>> tasks = createTaskMaps(5001);
        Map<String, Object> args = Map.of("tasks", tasks);

        // When
        CallToolResult result = tasksTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("Batch size");
        assertThat(content).contains("5000");
    }

    @Test
    void execute_shouldRejectEmptyBatch() {
        // Given
        Map<String, Object> args = Map.of("tasks", Collections.emptyList());

        // When
        CallToolResult result = tasksTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).containsIgnoringCase("empty");
    }

    @Test
    void execute_shouldRejectMissingTasksParameter() {
        // Given
        Map<String, Object> args = new HashMap<>();

        // When
        CallToolResult result = tasksTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("Missing required parameter");
    }

    @Test
    void execute_shouldRejectNullTasksParameter() {
        // Given
        Map<String, Object> args = new HashMap<>();
        args.put("tasks", null);

        // When
        CallToolResult result = tasksTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
    }

    @Test
    void execute_shouldNotCallService_whenValidationFails() {
        // Given
        List<Map<String, Object>> tasks = createTaskMaps(5001);
        Map<String, Object> args = Map.of("tasks", tasks);

        // When
        CallToolResult result = tasksTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        verify(asyncBatchService, never()).createJob(anyInt());
    }

    @Test
    void execute_shouldAcceptBatchSize_when1000Tasks() {
        // Given
        List<Map<String, Object>> tasks = createTaskMaps(1000);
        Map<String, Object> args = Map.of("tasks", tasks);
        
        BatchJob mockJob = new BatchJob();
        mockJob.setId("test-job-1000");
        mockJob.setStatus(JobStatus.PENDING);
        when(asyncBatchService.createJob(anyInt())).thenReturn(mockJob);

        // When
        CallToolResult result = tasksTool.execute(args);

        // Then - should succeed without validation error
        assertThat(result.isError()).isFalse();
        verify(asyncBatchService).createJob(1000);
    }

    @Test
    void execute_shouldAcceptBatchSize_when1Task() {
        // Given
        List<Map<String, Object>> tasks = createTaskMaps(1);
        Map<String, Object> args = Map.of("tasks", tasks);
        
        BatchJob mockJob = new BatchJob();
        mockJob.setId("test-job-1");
        mockJob.setStatus(JobStatus.PENDING);
        when(asyncBatchService.createJob(anyInt())).thenReturn(mockJob);

        // When
        CallToolResult result = tasksTool.execute(args);

        // Then - should succeed without validation error
        assertThat(result.isError()).isFalse();
        verify(asyncBatchService).createJob(1);
    }

    @Test
    void execute_shouldNotLeakInternalErrorDetails_whenServiceThrowsUnexpectedly() {
        // Given
        when(asyncBatchService.createJob(anyInt()))
            .thenThrow(new RuntimeException("SQL error: column 'tasks.internal_seq' violates constraint"));
        List<Map<String, Object>> tasks = createTaskMaps(1);
        Map<String, Object> args = Map.of("tasks", tasks);

        // When
        CallToolResult result = tasksTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).doesNotContain("SQL error");
        assertThat(content).doesNotContain("internal_seq");
        assertThat(content).contains("Internal error");
    }

    @Test
    void execute_shouldReturnStructuredJsonError_onValidationFailure() throws Exception {
        // Given
        Map<String, Object> args = new HashMap<>(); // missing "tasks"

        // When
        CallToolResult result = tasksTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String raw = ((McpSchema.TextContent) result.content().get(0)).text();
        JsonNode json = new ObjectMapper().readTree(raw);
        assertThat(json.has("error")).isTrue();
        assertThat(json.has("code")).isTrue();
        assertThat(json.get("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void execute_shouldReturnStructuredJsonError_onInternalFailure() throws Exception {
        // Given
        when(asyncBatchService.createJob(anyInt()))
            .thenThrow(new RuntimeException("unexpected db failure"));
        Map<String, Object> args = Map.of("tasks", createTaskMaps(1));

        // When
        CallToolResult result = tasksTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String raw = ((McpSchema.TextContent) result.content().get(0)).text();
        JsonNode json = new ObjectMapper().readTree(raw);
        assertThat(json.get("code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(json.get("error").asText()).doesNotContain("unexpected db failure");
    }

    @Test
    void execute_shouldNotLeakJacksonClassNames_whenTaskFieldHasWrongType() {
        // Given - 'title' is a nested object; Jackson's convertValue cannot map this to String
        // and produces a message containing internal type names like java.lang.String
        Map<String, Object> badTask = new HashMap<>();
        badTask.put("title", Map.of("nested", "bad-value")); // object instead of string
        badTask.put("status", "TODO");
        Map<String, Object> args = Map.of("tasks", List.of(badTask));

        // When
        CallToolResult result = tasksTool.execute(args);

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).doesNotContain("java.lang");
        assertThat(content).doesNotContain("JsonMappingException");
        assertThat(content).doesNotContain("MismatchedInputException");
        assertThat(content).contains("Invalid task at index 0");
        assertThat(content).contains("VALIDATION_ERROR");
    }

    @Test
    void execute_shouldMarkJobFailedAndReturnError_whenExecutorRejectsTask() {
        // Given
        BatchJob job = new BatchJob();
        job.setId("job-rejected");
        job.setStatus(JobStatus.PENDING);
        job.setTotalTasks(1);
        when(asyncBatchService.createJob(anyInt())).thenReturn(job);
        doThrow(new TaskRejectedException("Queue full"))
            .when(asyncBatchService).processAsync(eq("job-rejected"), any(), any());

        Map<String, Object> args = Map.of("tasks", createTaskMaps(1));

        // When
        CallToolResult result = tasksTool.execute(args);

        // Then – error returned to caller and job is marked failed
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("busy");
        verify(asyncBatchService).markJobFailed(eq("job-rejected"), any());
    }

    private List<Map<String, Object>> createTaskMaps(int count) {
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> task = new HashMap<>();
            task.put("title", "Task " + i);
            task.put("status", "TODO");
            tasks.add(task);
        }
        return tasks;
    }
}
