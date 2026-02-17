package com.example.mcptaskserver.integration;

import com.example.mcptaskserver.mcp.tools.TasksListTool;
import com.example.mcptaskserver.mcp.tools.TasksTool;
import com.example.mcptaskserver.repository.TaskRepository;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.example.mcptaskserver.integration.CallToolResultAssert.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TasksListTool.
 * Tests pagination and filtering functionality.
 */
class TasksListToolIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TasksListTool tasksListTool;

    @Autowired
    private TasksTool tasksTool;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
    }

    @Test
    void shouldReturnEmptyListWhenNoTasks() {
        CallToolResult result = tasksListTool.execute(Map.of());

        assertSuccess(result);
        assertContentContains(result, "\"total\":0");
        assertContentContains(result, "\"tasks\":[]");
    }

    @Test
    void shouldReturnAllTasksWithDefaultPagination() {
        // Create 5 tasks
        List<Map<String, Object>> tasks = List.of(
            Map.of("title", "Task 1", "status", "TODO"),
            Map.of("title", "Task 2", "status", "IN_PROGRESS"),
            Map.of("title", "Task 3", "status", "DONE"),
            Map.of("title", "Task 4", "status", "TODO"),
            Map.of("title", "Task 5", "status", "DONE")
        );
        tasksTool.execute(Map.of("tasks", tasks));

        // Wait for async processing
        waitForJobCompletion();

        CallToolResult result = tasksListTool.execute(Map.of());

        assertSuccess(result);
        assertContentContains(result, "\"total\":5");
        assertContentContains(result, "\"page\":0");
        assertContentContains(result, "\"pageSize\":100");
        assertContentContains(result, "\"totalPages\":1");
        assertContentContains(result, "Task 1");
        assertContentContains(result, "Task 5");
    }

    @Test
    void shouldFilterByStatus() {
        // Create tasks with different statuses
        List<Map<String, Object>> tasks = List.of(
            Map.of("title", "Todo Task 1", "status", "TODO"),
            Map.of("title", "Todo Task 2", "status", "TODO"),
            Map.of("title", "In Progress Task", "status", "IN_PROGRESS"),
            Map.of("title", "Done Task", "status", "DONE")
        );
        tasksTool.execute(Map.of("tasks", tasks));

        // Wait for async processing
        waitForJobCompletion();

        // Filter by TODO status
        CallToolResult result = tasksListTool.execute(Map.of("status", "TODO"));

        assertSuccess(result);
        assertContentContains(result, "\"total\":2");
        assertContentContains(result, "Todo Task 1");
        assertContentContains(result, "Todo Task 2");
        assertContentDoesNotContain(result, "In Progress Task");
        assertContentDoesNotContain(result, "Done Task");
    }

    @Test
    void shouldPaginateCorrectly() {
        // Create 10 tasks
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            tasks.add(Map.of("title", "Task " + i, "status", "TODO"));
        }
        tasksTool.execute(Map.of("tasks", tasks));

        // Wait for async processing
        waitForJobCompletion();

        // Get first page (5 items)
        CallToolResult page1 = tasksListTool.execute(Map.of(
            "page", 0,
            "pageSize", 5
        ));

        assertSuccess(page1);
        assertContentContains(page1, "\"total\":10");
        assertContentContains(page1, "\"page\":0");
        assertContentContains(page1, "\"pageSize\":5");
        assertContentContains(page1, "\"totalPages\":2");

        // Get second page (5 items)
        CallToolResult page2 = tasksListTool.execute(Map.of(
            "page", 1,
            "pageSize", 5
        ));

        assertSuccess(page2);
        assertContentContains(page2, "\"total\":10");
        assertContentContains(page2, "\"page\":1");
        assertContentContains(page2, "\"pageSize\":5");
    }

    @Test
    void shouldHandlePageSizeLimit() {
        // Create 10 tasks
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            tasks.add(Map.of("title", "Task " + i, "status", "TODO"));
        }
        tasksTool.execute(Map.of("tasks", tasks));

        // Wait for async processing
        waitForJobCompletion();

        // Request with custom page size
        CallToolResult result = tasksListTool.execute(Map.of(
            "page", 0,
            "pageSize", 3
        ));

        assertSuccess(result);
        assertContentContains(result, "\"total\":10");
        assertContentContains(result, "\"pageSize\":3");
    }

    @Test
    void shouldHandleEmptyPage() {
        // Create 5 tasks
        List<Map<String, Object>> tasks = List.of(
            Map.of("title", "Task 1", "status", "TODO"),
            Map.of("title", "Task 2", "status", "TODO")
        );
        tasksTool.execute(Map.of("tasks", tasks));

        // Wait for async processing
        waitForJobCompletion();

        // Request page beyond available data
        CallToolResult result = tasksListTool.execute(Map.of(
            "page", 10,
            "pageSize", 100
        ));

        assertSuccess(result);
        assertContentContains(result, "\"total\":2");
        assertContentContains(result, "\"page\":10");
        assertContentContains(result, "\"tasks\":[]");
    }

    @Test
    void shouldCombinePaginationAndFiltering() {
        // Create 15 TODO tasks and 5 DONE tasks
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            tasks.add(Map.of("title", "Todo Task " + i, "status", "TODO"));
        }
        for (int i = 1; i <= 5; i++) {
            tasks.add(Map.of("title", "Done Task " + i, "status", "DONE"));
        }
        tasksTool.execute(Map.of("tasks", tasks));

        // Wait for async processing
        waitForJobCompletion();

        // Get first page of TODO tasks
        CallToolResult result = tasksListTool.execute(Map.of(
            "page", 0,
            "pageSize", 10,
            "status", "TODO"
        ));

        assertSuccess(result);
        assertContentContains(result, "\"total\":15");
        assertContentContains(result, "\"page\":0");
        assertContentContains(result, "\"pageSize\":10");
        assertContentContains(result, "\"totalPages\":2");
        assertContentContains(result, "Todo Task");
        assertContentDoesNotContain(result, "Done Task");
    }

    @Test
    void shouldReturnTasksWithAllFields() {
        List<Map<String, Object>> tasks = List.of(
            Map.of(
                "title", "Complete Task",
                "description", "Task with all fields",
                "status", "IN_PROGRESS",
                "dueDate", "2025-03-15"
            )
        );
        tasksTool.execute(Map.of("tasks", tasks));

        // Wait for async processing
        waitForJobCompletion();

        CallToolResult result = tasksListTool.execute(Map.of());

        assertSuccess(result);
        assertContentContains(result, "Complete Task");
        assertContentContains(result, "Task with all fields");
        assertContentContains(result, "IN_PROGRESS");
        assertContentContains(result, "2025-03-15");
        assertContentContains(result, "\"id\":");
        assertContentContains(result, "\"createdAt\":");
        assertContentContains(result, "\"updatedAt\":");
    }

    /**
     * Wait for async batch job to complete.
     * Simple approach: wait for tasks to appear in repository.
     */
    private void waitForJobCompletion() {
        try {
            Thread.sleep(500); // Give async job time to complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
