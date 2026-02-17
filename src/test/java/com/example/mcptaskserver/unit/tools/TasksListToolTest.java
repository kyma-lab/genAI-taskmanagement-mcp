package com.example.mcptaskserver.unit.tools;

import com.example.mcptaskserver.dto.TaskListResponseDto;
import com.example.mcptaskserver.mcp.tools.TasksListTool;
import com.example.mcptaskserver.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TasksListToolTest {

    @Mock
    private TaskService taskService;

    private TasksListTool tasksListTool;

    @BeforeEach
    void setUp() {
        tasksListTool = new TasksListTool(taskService, new ObjectMapper(), new SimpleMeterRegistry());
    }

    @Test
    void execute_shouldReturnTaskListWithDefaults_whenNoArgumentsProvided() {
        // Given
        TaskListResponseDto response = new TaskListResponseDto(List.of(), 0, 0, 100, 0);
        when(taskService.listTasks(0, 100, null)).thenReturn(response);

        // When
        CallToolResult result = tasksListTool.execute(Map.of());

        // Then
        assertThat(result.isError()).isFalse();
        verify(taskService).listTasks(0, 100, null);
    }

    @Test
    void execute_shouldPassPaginationArguments() {
        // Given
        TaskListResponseDto response = new TaskListResponseDto(List.of(), 0, 2, 50, 0);
        when(taskService.listTasks(2, 50, null)).thenReturn(response);

        // When
        CallToolResult result = tasksListTool.execute(Map.of("page", 2, "pageSize", 50));

        // Then
        assertThat(result.isError()).isFalse();
        verify(taskService).listTasks(2, 50, null);
    }

    @Test
    void execute_shouldPassStatusFilter() {
        // Given
        TaskListResponseDto response = new TaskListResponseDto(List.of(), 0, 0, 100, 0);
        when(taskService.listTasks(0, 100, "TODO")).thenReturn(response);

        // When
        CallToolResult result = tasksListTool.execute(Map.of("status", "TODO"));

        // Then
        assertThat(result.isError()).isFalse();
        verify(taskService).listTasks(0, 100, "TODO");
    }

    @Test
    void execute_shouldReturnErrorResult_whenServiceThrowsIllegalArgumentException() {
        // Given - user-facing validation error (e.g., invalid page size)
        when(taskService.listTasks(anyInt(), anyInt(), any()))
            .thenThrow(new IllegalArgumentException("Page size must be between 1 and 1000"));

        // When
        CallToolResult result = tasksListTool.execute(Map.of("pageSize", 9999));

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("Page size must be between 1 and 1000");
    }

    @Test
    void execute_shouldNotLeakInternalErrorDetails_whenServiceThrowsUnexpectedly() {
        // Given
        when(taskService.listTasks(anyInt(), anyInt(), any()))
            .thenThrow(new RuntimeException("FATAL: remaining connection slots are reserved for pg_superuser: host=internal-db:5432"));

        // When
        CallToolResult result = tasksListTool.execute(Map.of());

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).doesNotContain("FATAL");
        assertThat(content).doesNotContain("internal-db");
        assertThat(content).contains("Internal error");
    }

    @Test
    void execute_shouldHandleNullPageArgGracefully() {
        // Given - null value for page falls back to default 0
        TaskListResponseDto response = new TaskListResponseDto(List.of(), 0, 0, 100, 0);
        when(taskService.listTasks(0, 100, null)).thenReturn(response);

        Map<String, Object> args = new HashMap<>();
        args.put("page", null);

        // When
        CallToolResult result = tasksListTool.execute(args);

        // Then
        assertThat(result.isError()).isFalse();
        verify(taskService).listTasks(0, 100, null);
    }
}
