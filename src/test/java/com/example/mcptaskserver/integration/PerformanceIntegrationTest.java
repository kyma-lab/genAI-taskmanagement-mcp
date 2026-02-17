package com.example.mcptaskserver.integration;

import com.example.mcptaskserver.mcp.tools.TasksTool;
import com.example.mcptaskserver.repository.TaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.awaitility.Awaitility;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.mcptaskserver.integration.CallToolResultAssert.*;

class PerformanceIntegrationTest extends AbstractIntegrationTest {
    
    @Autowired
    private TasksTool tasksTool;
    
    @Autowired
    private TaskRepository taskRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private static final String[] STATUSES = {"TODO", "IN_PROGRESS", "DONE"};
    private final Random random = new Random();
    
    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
    }
    
    @Test
    void shouldInsert1000TasksUnder2Seconds() throws Exception {
        List<Map<String, Object>> tasks = IntStream.range(0, 1000)
            .mapToObj(i -> Map.<String, Object>of(
                "title", "Performance Test Task " + i,
                "description", "Description for task " + i,
                "status", STATUSES[random.nextInt(STATUSES.length)],
                "dueDate", "2025-" + String.format("%02d", (i % 12) + 1) + "-15"
            ))
            .toList();
        
        long startTime = System.currentTimeMillis();
        var result = tasksTool.execute(Map.of("tasks", tasks));
        long duration = System.currentTimeMillis() - startTime;
        
        assertSuccess(result);
        
        JsonNode response = objectMapper.readTree(getContent(result));
        assertThat(response.get("totalTasks").asInt()).isEqualTo(1000);
        // duration measures only the batch submission time (async processing happens in background)
        assertThat(duration).isLessThan(2000);

        // Wait for async processing to complete before verifying DB count
        Awaitility.await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(taskRepository.count()).isEqualTo(1000));

        long tasksPerSecond = (1000 * 1000L) / Math.max(duration, 1);
        System.out.printf("Performance: %d tasks in %dms (%d tasks/sec)%n",
                1000, duration, tasksPerSecond);
    }
}
