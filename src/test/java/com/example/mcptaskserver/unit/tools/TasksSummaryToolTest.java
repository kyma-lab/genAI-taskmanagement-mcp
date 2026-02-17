package com.example.mcptaskserver.unit.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.mcptaskserver.dto.TaskSummaryDto;
import com.example.mcptaskserver.mcp.tools.TasksSummaryTool;
import com.example.mcptaskserver.service.TaskService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TasksSummaryToolTest {

    @Mock
    private TaskService taskService;

    private TasksSummaryTool tasksSummaryTool;

    @BeforeEach
    void setUp() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        tasksSummaryTool = new TasksSummaryTool(taskService, objectMapper, meterRegistry);
    }

    @Test
    void execute_shouldDelegateToTaskService() {
        // Given
        TaskSummaryDto summary = createSummary(100L, 50L, 30L, 20L);
        when(taskService.generateSummary()).thenReturn(summary);

        // When
        McpSchema.CallToolResult result = tasksSummaryTool.execute(Map.of());

        // Then
        verify(taskService).generateSummary();
        assertThat(result).isNotNull();
    }

    @Test
    void execute_shouldReturnFormattedJson() {
        // Given
        TaskSummaryDto summary = createSummary(100L, 50L, 30L, 20L);
        when(taskService.generateSummary()).thenReturn(summary);

        // When
        McpSchema.CallToolResult result = tasksSummaryTool.execute(Map.of());

        // Then
        assertThat(result.content()).hasSize(1);
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        
        assertThat(content).contains("totalCount");
        assertThat(content).contains("100");
        assertThat(content).contains("TODO");
        assertThat(content).contains("50");
        assertThat(content).contains("IN_PROGRESS");
        assertThat(content).contains("30");
        assertThat(content).contains("DONE");
        assertThat(content).contains("20");
    }

    @Test
    void execute_shouldHandleZeroTasks() {
        // Given
        TaskSummaryDto summary = createSummary(0L, 0L, 0L, 0L);
        when(taskService.generateSummary()).thenReturn(summary);

        // When
        McpSchema.CallToolResult result = tasksSummaryTool.execute(Map.of());

        // Then
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("totalCount");
        assertThat(content).contains("0");
    }

    @Test
    void execute_shouldFormatCountByStatusMap() {
        // Given
        TaskSummaryDto summary = createSummary(150L, 100L, 25L, 25L);
        when(taskService.generateSummary()).thenReturn(summary);

        // When
        McpSchema.CallToolResult result = tasksSummaryTool.execute(Map.of());

        // Then
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("countByStatus");
        assertThat(content).contains("TODO");
        assertThat(content).contains("100");
        assertThat(content).contains("IN_PROGRESS");
        assertThat(content).contains("25");
        assertThat(content).contains("DONE");
    }

    @Test
    void execute_shouldNotRequireArguments() {
        // Given
        TaskSummaryDto summary = createSummary(10L, 5L, 3L, 2L);
        when(taskService.generateSummary()).thenReturn(summary);

        // When
        McpSchema.CallToolResult result = tasksSummaryTool.execute(new HashMap<>());

        // Then
        assertThat(result).isNotNull();
        verify(taskService).generateSummary();
    }

    @Test
    void execute_shouldNotLeakInternalErrorDetails_whenServiceThrowsUnexpectedly() {
        // Given
        when(taskService.generateSummary())
            .thenThrow(new RuntimeException("HikariPool-1 - Connection is not available, request timed out after 30000ms"));

        // When
        McpSchema.CallToolResult result = tasksSummaryTool.execute(Map.of());

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).doesNotContain("HikariPool");
        assertThat(content).doesNotContain("30000ms");
        assertThat(content).contains("Internal error");
    }

    private TaskSummaryDto createSummary(long total, long todo, long inProgress, long done) {
        Map<String, Long> countByStatus = new HashMap<>();
        countByStatus.put("TODO", todo);
        countByStatus.put("IN_PROGRESS", inProgress);
        countByStatus.put("DONE", done);
        
        return new TaskSummaryDto(total, countByStatus, null, null, java.time.LocalDateTime.now());
    }
}
