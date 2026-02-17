package com.example.mcptaskserver.integration;

import com.example.mcptaskserver.mcp.tools.TasksFromFileTool;
import com.example.mcptaskserver.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.mcptaskserver.integration.CallToolResultAssert.*;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for TasksFromFileTool.
 */
class TasksFromFileToolIntegrationTest extends AbstractIntegrationTest {
    
    @Autowired
    private TasksFromFileTool tasksFromFileTool;
    
    @Autowired
    private TaskRepository taskRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
    }
    
    @Test
    void shouldImportTasksFromFile() throws Exception {
        // Create test file with tasks
        List<Map<String, Object>> tasks = List.of(
            Map.of("title", "Task 1", "status", "TODO", "description", "Test task 1"),
            Map.of("title", "Task 2", "status", "IN_PROGRESS", "description", "Test task 2"),
            Map.of("title", "Task 3", "status", "DONE", "description", "Test task 3")
        );
        
        Path testFile = tempDir.resolve("test_tasks.json");
        Files.writeString(testFile, objectMapper.writeValueAsString(tasks));
        
        // Execute tool
        Map<String, Object> args = Map.of("filePath", testFile.toString());
        var result = tasksFromFileTool.execute(args);
        
        // Verify result
        assertSuccess(result);
        assertContentContains(result, "\"success\":true");
        assertContentContains(result, "\"totalTasks\":3");
        assertContentContains(result, "jobId");
        
        // Wait for async processing
        await().untilAsserted(() -> 
            assertThat(taskRepository.count()).isEqualTo(3)
        );
        
        // Verify tasks in database
        var savedTasks = taskRepository.findAll();
        assertThat(savedTasks).hasSize(3);
        assertThat(savedTasks).extracting("title")
            .containsExactlyInAnyOrder("Task 1", "Task 2", "Task 3");
    }
    
    @Test
    void shouldHandleLargeBatch() throws Exception {
        // Create file with 1000 tasks
        List<Map<String, Object>> tasks = java.util.stream.IntStream.range(0, 1000)
            .mapToObj(i -> Map.<String, Object>of(
                "title", "Task " + i,
                "status", "TODO",
                "description", "Test task " + i
            ))
            .toList();
        
        Path testFile = tempDir.resolve("large_batch.json");
        Files.writeString(testFile, objectMapper.writeValueAsString(tasks));
        
        // Execute tool
        Map<String, Object> args = Map.of("filePath", testFile.toString());
        var result = tasksFromFileTool.execute(args);
        
        // Verify result
        assertSuccessAndContains(result, "\"totalTasks\":1000");
        
        // Wait for async processing
        await().untilAsserted(() -> 
            assertThat(taskRepository.count()).isEqualTo(1000)
        );
    }
    
    @Test
    void shouldHandleRelativePath() throws Exception {
        // Create test file with relative path
        List<Map<String, Object>> tasks = List.of(
            Map.of("title", "Task 1", "status", "TODO")
        );
        
        Path testFile = Path.of("tasks_1000.json");
        
        // Skip if file doesn't exist (in CI environment)
        if (!Files.exists(testFile)) {
            return;
        }
        
        // Execute tool with relative path
        Map<String, Object> args = Map.of("filePath", "tasks_1000.json");
        var result = tasksFromFileTool.execute(args);
        
        // Verify result
        assertSuccessAndContains(result, "\"success\":true");
    }
    
    @Test
    void shouldRejectMissingFilePath() {
        Map<String, Object> args = Map.of();
        var result = tasksFromFileTool.execute(args);
        
        assertErrorAndContains(result, "Missing required parameter: filePath");
    }
    
    @Test
    void shouldRejectNonExistentFile() {
        // /non/existent/file.json is outside CWD and tmpdir – rejected by whitelist before file I/O
        Map<String, Object> args = Map.of("filePath", "/non/existent/file.json");
        var result = tasksFromFileTool.execute(args);

        assertErrorAndContains(result, "outside of allowed directories");
    }
    
    @Test
    void shouldRejectInvalidJson() throws Exception {
        Path testFile = tempDir.resolve("invalid.json");
        Files.writeString(testFile, "{ invalid json }");
        
        Map<String, Object> args = Map.of("filePath", testFile.toString());
        var result = tasksFromFileTool.execute(args);
        
        assertErrorAndContains(result, "Invalid JSON in file");
    }
    
    @Test
    void shouldRejectEmptyTaskArray() throws Exception {
        Path testFile = tempDir.resolve("empty.json");
        Files.writeString(testFile, "[]");
        
        Map<String, Object> args = Map.of("filePath", testFile.toString());
        var result = tasksFromFileTool.execute(args);
        
        assertErrorAndContains(result, "empty task array");
    }
    
    @Test
    void shouldRejectOversizedBatch() throws Exception {
        // Create file with 5001 tasks (exceeds MAX_BATCH_SIZE)
        List<Map<String, Object>> tasks = java.util.stream.IntStream.range(0, 5001)
            .mapToObj(i -> Map.<String, Object>of("title", "Task " + i, "status", "TODO"))
            .toList();
        
        Path testFile = tempDir.resolve("oversized.json");
        Files.writeString(testFile, objectMapper.writeValueAsString(tasks));
        
        Map<String, Object> args = Map.of("filePath", testFile.toString());
        var result = tasksFromFileTool.execute(args);
        
        assertErrorAndContains(result, "exceeds maximum");
    }
    
    @Test
    void shouldValidateTaskData() throws Exception {
        // Create file with invalid task (missing title)
        List<Map<String, Object>> tasks = List.of(
            Map.of("status", "TODO", "description", "Missing title")
        );
        
        Path testFile = tempDir.resolve("invalid_task.json");
        Files.writeString(testFile, objectMapper.writeValueAsString(tasks));
        
        Map<String, Object> args = Map.of("filePath", testFile.toString());
        var result = tasksFromFileTool.execute(args);
        
        assertErrorAndContains(result, "Invalid task at index 0");
    }
    
    @Test
    void shouldHandleTasksWithDueDate() throws Exception {
        List<Map<String, Object>> tasks = List.of(
            Map.of("title", "Task with date", "status", "TODO", "dueDate", "2026-12-31")
        );
        
        Path testFile = tempDir.resolve("tasks_with_date.json");
        Files.writeString(testFile, objectMapper.writeValueAsString(tasks));
        
        Map<String, Object> args = Map.of("filePath", testFile.toString());
        var result = tasksFromFileTool.execute(args);
        
        assertSuccess(result);
        
        await().untilAsserted(() -> {
            var savedTasks = taskRepository.findAll();
            assertThat(savedTasks).hasSize(1);
            assertThat(savedTasks.get(0).getDueDate()).isNotNull();
        });
    }
}
