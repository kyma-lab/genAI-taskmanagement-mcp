package com.example.mcptaskserver.integration;

import com.example.mcptaskserver.mcp.tools.TasksSummaryTool;
import com.example.mcptaskserver.mcp.tools.TasksTool;
import com.example.mcptaskserver.repository.TaskRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.mcptaskserver.integration.CallToolResultAssert.*;

class SummaryToolIntegrationTest extends AbstractIntegrationTest {
    
    @Autowired
    private TasksSummaryTool summaryTool;
    
    @Autowired
    private TasksTool tasksTool;
    
    @Autowired
    private TaskRepository taskRepository;
    
    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
    }
    
    @Test
    void shouldReturnEmptySummaryWhenNoTasks() {
        var result = summaryTool.execute(Map.of());
        
        assertSuccessAndContains(result, "\"totalCount\":0");
    }
    
    @Test
    void shouldReturnCorrectCountsByStatus() {
        List<Map<String, Object>> tasks = List.of(
            Map.of("title", "Task 1", "status", "TODO"),
            Map.of("title", "Task 2", "status", "TODO"),
            Map.of("title", "Task 3", "status", "IN_PROGRESS"),
            Map.of("title", "Task 4", "status", "DONE")
        );
        tasksTool.execute(Map.of("tasks", tasks));
        // Wait for async batch insertion to complete before querying summary
        Awaitility.await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(taskRepository.count()).isEqualTo(4));

        var result = summaryTool.execute(Map.of());
        
        assertSuccess(result);
        assertContentContains(result, "\"totalCount\":4");
        assertContentContains(result, "\"TODO\":2");
        assertContentContains(result, "\"IN_PROGRESS\":1");
        assertContentContains(result, "\"DONE\":1");
    }
    
    @Test
    void shouldIncludeGeneratedTimestamp() {
        var result = summaryTool.execute(Map.of());
        
        assertSuccessAndContains(result, "\"generatedAt\"");
    }
}
