package com.example.mcptask.controller;

import com.example.mcptask.dto.BulkInsertResultDto;
import com.example.mcptask.dto.TaskCreateDto;
import com.example.mcptask.dto.TaskSummaryDto;
import com.example.mcptask.model.TaskStatus;
import com.example.mcptask.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(McpController.class)
@DisplayName("McpController Unit Tests")
class McpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskService taskService;

    @Test
    @DisplayName("GET /mcp/help should return MCP server capabilities")
    void shouldReturnMcpHelp() throws Exception {
        mockMvc.perform(get("/mcp/help"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.server").value("MCP Task Server"))
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.endpoints").exists())
                .andExpect(jsonPath("$.openapi").value("/v3/api-docs"));
    }

    @Test
    @DisplayName("GET /mcp/schema/tasks should return Task JSON schema")
    void shouldReturnTaskSchema() throws Exception {
        mockMvc.perform(get("/mcp/schema/tasks"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.title").value("Task"))
                .andExpect(jsonPath("$.type").value("object"))
                .andExpect(jsonPath("$.properties.title").exists())
                .andExpect(jsonPath("$.properties.status").exists())
                .andExpect(jsonPath("$.properties.status.enum").isArray())
                .andExpect(jsonPath("$.required").isArray())
                .andExpect(jsonPath("$.required[0]").value("title"))
                .andExpect(jsonPath("$.required[1]").value("status"));
    }

    @Test
    @DisplayName("POST /mcp/tasks should insert tasks successfully")
    void shouldInsertTasksSuccessfully() throws Exception {
        // Given
        List<TaskCreateDto> tasks = List.of(
                createTaskDto("Task 1", TaskStatus.TODO),
                createTaskDto("Task 2", TaskStatus.IN_PROGRESS)
        );

        BulkInsertResultDto mockResult = BulkInsertResultDto.builder()
                .inserted(2)
                .message("Successfully inserted 2 tasks")
                .build();

        when(taskService.bulkInsert(any())).thenReturn(mockResult);

        // When & Then
        mockMvc.perform(post("/mcp/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tasks)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inserted").value(2))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /mcp/tasks with empty list should return bad request")
    void shouldReturnBadRequestForEmptyTaskList() throws Exception {
        mockMvc.perform(post("/mcp/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.inserted").value(0))
                .andExpect(jsonPath("$.message").value("Task list is empty"));
    }

    @Test
    @DisplayName("POST /mcp/tasks with invalid task should return validation error")
    void shouldReturnValidationErrorForInvalidTask() throws Exception {
        // Task missing required 'title' field
        String invalidTask = """
                [{
                    "description": "Test task",
                    "status": "TODO"
                }]
                """;

        mockMvc.perform(post("/mcp/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidTask))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /mcp/tasks/summary should return task statistics")
    void shouldReturnTaskSummary() throws Exception {
        // Given
        TaskSummaryDto mockSummary = TaskSummaryDto.builder()
                .totalTasks(100)
                .tasksByStatus(Map.of(
                        "TODO", 25L,
                        "IN_PROGRESS", 30L,
                        "DONE", 40L,
                        "CANCELLED", 5L
                ))
                .build();

        when(taskService.getSummary()).thenReturn(mockSummary);

        // When & Then
        mockMvc.perform(get("/mcp/tasks/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(100))
                .andExpect(jsonPath("$.tasksByStatus.TODO").value(25))
                .andExpect(jsonPath("$.tasksByStatus.IN_PROGRESS").value(30))
                .andExpect(jsonPath("$.tasksByStatus.DONE").value(40))
                .andExpect(jsonPath("$.tasksByStatus.CANCELLED").value(5));
    }

    private TaskCreateDto createTaskDto(String title, TaskStatus status) {
        return TaskCreateDto.builder()
                .title(title)
                .status(status)
                .priority(3)
                .dueDate(LocalDate.now().plusDays(7))
                .build();
    }
}
