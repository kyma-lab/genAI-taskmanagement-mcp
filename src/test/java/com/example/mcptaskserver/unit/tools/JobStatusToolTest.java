package com.example.mcptaskserver.unit.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.mcptaskserver.exception.ResourceNotFoundException;
import com.example.mcptaskserver.mcp.tools.JobStatusTool;
import com.example.mcptaskserver.model.BatchJob;
import com.example.mcptaskserver.model.JobStatus;
import com.example.mcptaskserver.service.AsyncBatchService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobStatusToolTest {

    @Mock
    private AsyncBatchService asyncBatchService;

    private JobStatusTool jobStatusTool;

    @BeforeEach
    void setUp() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        jobStatusTool = new JobStatusTool(asyncBatchService, objectMapper, meterRegistry);
    }

    @Test
    void execute_shouldCalculateTasksPerSecond_when1000TasksIn1000Ms() {
        // Given
        BatchJob job = createCompletedJob(1000, 1000L);
        when(asyncBatchService.findJob("job-1")).thenReturn(job);

        // When
        McpSchema.CallToolResult result = jobStatusTool.execute(Map.of("jobId", "job-1"));

        // Then
        assertThat(result).isNotNull();
        assertThat(result.content()).hasSize(1);
        
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("tasksPerSecond");
        assertThat(content).contains("1000");
    }

    @Test
    void execute_shouldCalculateTasksPerSecond_when2000TasksIn1000Ms() {
        // Given
        BatchJob job = createCompletedJob(2000, 1000L);
        when(asyncBatchService.findJob("job-1")).thenReturn(job);

        // When
        McpSchema.CallToolResult result = jobStatusTool.execute(Map.of("jobId", "job-1"));

        // Then
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("tasksPerSecond");
        assertThat(content).contains("2000");
    }

    @Test
    void execute_shouldCalculateTasksPerSecond_when1000TasksIn500Ms() {
        // Given
        BatchJob job = createCompletedJob(1000, 500L);
        when(asyncBatchService.findJob("job-1")).thenReturn(job);

        // When
        McpSchema.CallToolResult result = jobStatusTool.execute(Map.of("jobId", "job-1"));

        // Then
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("tasksPerSecond");
        assertThat(content).contains("2000");
    }

    @Test
    void execute_shouldNotCalculateTasksPerSecond_when0Tasks() {
        // Given
        BatchJob job = createCompletedJob(0, 1000L);
        when(asyncBatchService.findJob("job-1")).thenReturn(job);

        // When
        McpSchema.CallToolResult result = jobStatusTool.execute(Map.of("jobId", "job-1"));

        // Then
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).doesNotContain("tasksPerSecond");
    }

    @Test
    void execute_shouldNotCalculateTasksPerSecond_when0Duration() {
        // Given
        BatchJob job = createCompletedJob(1000, 0L);
        when(asyncBatchService.findJob("job-1")).thenReturn(job);

        // When
        McpSchema.CallToolResult result = jobStatusTool.execute(Map.of("jobId", "job-1"));

        // Then
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).doesNotContain("tasksPerSecond");
    }

    @Test
    void execute_shouldCalculateProgressPercent_when50Of100Tasks() {
        // Given
        BatchJob job = createRunningJob(50, 100);
        when(asyncBatchService.findJob("job-1")).thenReturn(job);

        // When
        McpSchema.CallToolResult result = jobStatusTool.execute(Map.of("jobId", "job-1"));

        // Then
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("progressPercent");
        assertThat(content).contains("50");
    }

    @Test
    void execute_shouldCalculateProgressPercent_when0Of100Tasks() {
        // Given
        BatchJob job = createRunningJob(0, 100);
        when(asyncBatchService.findJob("job-1")).thenReturn(job);

        // When
        McpSchema.CallToolResult result = jobStatusTool.execute(Map.of("jobId", "job-1"));

        // Then
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("progressPercent");
        assertThat(content).contains("0");
    }

    @Test
    void execute_shouldCalculateProgressPercent_when100Of100Tasks() {
        // Given
        BatchJob job = createRunningJob(100, 100);
        when(asyncBatchService.findJob("job-1")).thenReturn(job);

        // When
        McpSchema.CallToolResult result = jobStatusTool.execute(Map.of("jobId", "job-1"));

        // Then
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("progressPercent");
        assertThat(content).contains("100");
    }

    @Test
    void execute_shouldNotCalculateProgressPercent_when0TotalTasks() {
        // Given
        BatchJob job = createRunningJob(0, 0);
        when(asyncBatchService.findJob("job-1")).thenReturn(job);

        // When
        McpSchema.CallToolResult result = jobStatusTool.execute(Map.of("jobId", "job-1"));

        // Then
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).doesNotContain("progressPercent");
    }

    @Test
    void execute_shouldCalculateProgressPercent_when75Of200Tasks() {
        // Given
        BatchJob job = createRunningJob(75, 200);
        when(asyncBatchService.findJob("job-1")).thenReturn(job);

        // When
        McpSchema.CallToolResult result = jobStatusTool.execute(Map.of("jobId", "job-1"));

        // Then
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("\"progressPercent\":37"); // 75*100/200 = 37
    }

    @Test
    void execute_shouldCalculateBothMetrics_whenCompletedJob() {
        // Given
        BatchJob job = createCompletedJob(500, 1000L);
        when(asyncBatchService.findJob("job-1")).thenReturn(job);

        // When
        McpSchema.CallToolResult result = jobStatusTool.execute(Map.of("jobId", "job-1"));

        // Then
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("tasksPerSecond");
        assertThat(content).contains("500");
        assertThat(content).contains("progressPercent");
        assertThat(content).contains("100");
    }

    @Test
    void execute_shouldReturnNotFoundCode_whenJobDoesNotExist() throws Exception {
        // Given
        when(asyncBatchService.findJob("unknown-job"))
            .thenThrow(new com.example.mcptaskserver.exception.ResourceNotFoundException("Job not found: unknown-job"));

        // When
        McpSchema.CallToolResult result = jobStatusTool.execute(Map.of("jobId", "unknown-job"));

        // Then
        assertThat(result.isError()).isTrue();
        String raw = ((McpSchema.TextContent) result.content().get(0)).text();
        JsonNode json = new ObjectMapper().readTree(raw);
        assertThat(json.get("code").asText()).isEqualTo("NOT_FOUND");
        assertThat(json.get("error").asText()).contains("Job not found");
    }

    @Test
    void execute_shouldNotLeakInternalErrorDetails_whenServiceThrowsUnexpectedly() {
        // Given - RuntimeException (not IllegalArgumentException, which is user-facing "job not found")
        when(asyncBatchService.findJob("job-internal-error"))
            .thenThrow(new RuntimeException("NullPointerException in BatchJobRepository.findById at line 42"));

        // When
        McpSchema.CallToolResult result = jobStatusTool.execute(Map.of("jobId", "job-internal-error"));

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).doesNotContain("NullPointerException");
        assertThat(content).doesNotContain("BatchJobRepository");
        assertThat(content).contains("Internal error");
    }

    private BatchJob createCompletedJob(int processedTasks, long durationMs) {
        BatchJob job = new BatchJob();
        job.setId("job-1");
        job.setStatus(JobStatus.COMPLETED);
        job.setTotalTasks(processedTasks);
        job.setProcessedTasks(processedTasks);
        job.setDurationMs(durationMs);
        
        LocalDateTime now = LocalDateTime.now();
        job.setCreatedAt(now.minusNanos(durationMs * 1_000_000));
        job.setCompletedAt(now);
        
        return job;
    }

    private BatchJob createRunningJob(int processedTasks, int totalTasks) {
        BatchJob job = new BatchJob();
        job.setId("job-1");
        job.setStatus(JobStatus.RUNNING);
        job.setTotalTasks(totalTasks);
        job.setProcessedTasks(processedTasks);
        job.setCreatedAt(LocalDateTime.now().minusSeconds(10));
        
        return job;
    }
}
