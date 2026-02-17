package com.example.mcptaskserver.integration;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import com.example.mcptaskserver.mcp.tools.TasksTool;
import com.example.mcptaskserver.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.awaitility.Awaitility;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentInsertIntegrationTest extends AbstractIntegrationTest {
    
    @Autowired
    private TasksTool tasksTool;
    
    @Autowired
    private TaskRepository taskRepository;
    
    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
    }
    
    @Test
    void shouldHandleConcurrentBatchInserts() throws Exception {
        int threadCount = 5;
        int tasksPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<CallToolResult>> futures = new ArrayList<>();
        
        for (int t = 0; t < threadCount; t++) {
            final int threadNum = t;
            Future<CallToolResult> future = executor.submit(() -> {
                try {
                    List<Map<String, Object>> tasks = new ArrayList<>();
                    for (int i = 0; i < tasksPerThread; i++) {
                        tasks.add(Map.of(
                            "title", "Thread-" + threadNum + "-Task-" + i,
                            "status", "TODO"
                        ));
                    }
                    return tasksTool.execute(Map.of("tasks", tasks));
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }
        
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Verify all inserts succeeded
        for (Future<CallToolResult> future : futures) {
            CallToolResult result = future.get();
            assertThat(result.isError()).isFalse();
        }
        
        // Wait for all async batch jobs to complete, then verify total count
        Awaitility.await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(taskRepository.count()).isEqualTo((long) threadCount * tasksPerThread));

        long totalTasks = taskRepository.count();
        
        System.out.printf("Concurrent insert: %d threads x %d tasks = %d total%n",
            threadCount, tasksPerThread, totalTasks);
    }
}
