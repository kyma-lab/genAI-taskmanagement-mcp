package com.example.mcptaskserver.integration;

import com.example.mcptaskserver.mcp.tools.TasksTool;
import com.example.mcptaskserver.repository.BatchJobRepository;
import com.example.mcptaskserver.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.awaitility.Awaitility;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.mcptaskserver.integration.CallToolResultAssert.*;

class TasksToolIntegrationTest extends AbstractIntegrationTest {
    
    @Autowired
    private TasksTool tasksTool;
    
    @Autowired
    private TaskRepository taskRepository;
    
    @Autowired
    private BatchJobRepository batchJobRepository;
    
    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        batchJobRepository.deleteAll();
    }
    
    @Test
    void shouldInsertSingleTask() {
        Map<String, Object> args = Map.of(
            "tasks", List.of(
                Map.of("title", "Test Task", "description", "Test Description", "status", "TODO")
            )
        );
        
        var result = tasksTool.execute(args);

        assertSuccessAndContains(result, "\"totalTasks\":1");
        Awaitility.await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(taskRepository.count()).isEqualTo(1));
    }
    
    @Test
    void shouldInsertBatchOf100Tasks() {
        List<Map<String, Object>> tasks = java.util.stream.IntStream.range(0, 100)
            .mapToObj(i -> Map.<String, Object>of("title", "Task " + i, "status", "TODO"))
            .toList();
        
        var result = tasksTool.execute(Map.of("tasks", tasks));
        
        assertSuccessAndContains(result, "\"totalTasks\":100");
        Awaitility.await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(taskRepository.count()).isEqualTo(100));
    }
    
    @Test
    void shouldRejectMissingRequiredField() {
        Map<String, Object> args = Map.of(
            "tasks", List.of(Map.of("title", "No Status Task"))
        );
        
        var result = tasksTool.execute(args);
        
        assertErrorAndContains(result, "status");
    }
    
    @Test
    void shouldRejectTitleExceedingMaxLength() {
        String longTitle = "x".repeat(256);
        Map<String, Object> args = Map.of(
            "tasks", List.of(Map.of("title", longTitle, "status", "TODO"))
        );
        
        var result = tasksTool.execute(args);
        
        assertErrorAndContains(result, "255");
    }
    
    @Test
    void shouldRejectEmptyTasksArray() {
        var result = tasksTool.execute(Map.of("tasks", List.of()));
        
        assertError(result);
        assertThat(getContent(result).toLowerCase()).contains("empty");
    }
    
    @Test
    void shouldRejectMissingTasksParameter() {
        var result = tasksTool.execute(Map.of());
        
        assertErrorAndContains(result, "tasks");
    }
}
