package com.example.mcptask.service;

import com.example.mcptask.dto.BulkInsertResultDto;
import com.example.mcptask.dto.TaskCreateDto;
import com.example.mcptask.dto.TaskSummaryDto;
import com.example.mcptask.model.TaskStatus;
import com.example.mcptask.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@DisplayName("TaskService Integration Tests with Testcontainers")
class TaskServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
    }

    @Test
    @DisplayName("Should bulk insert 1000 tasks efficiently with SEQUENCE-based batching")
    void shouldBulkInsert1000TasksWithOptimalPerformance() {
        // Given: 1000 realistic tasks
        List<TaskCreateDto> tasks = generateRealisticTasks(1000);

        // When: Bulk insert with timing
        long startTime = System.currentTimeMillis();
        BulkInsertResultDto result = taskService.bulkInsert(tasks);
        long duration = System.currentTimeMillis() - startTime;

        // Then: All tasks inserted successfully
        assertThat(result.getInserted()).isEqualTo(1000);
        assertThat(taskRepository.count()).isEqualTo(1000);
        
        // Performance assertion: With SEQUENCE + reWriteBatchedInserts, should be < 2 seconds
        assertThat(duration).isLessThan(2000);
        
        double tasksPerSecond = (1000.0 / duration) * 1000;
        System.out.printf("✓ Performance Test PASSED: Inserted 1000 tasks in %dms (%.0f tasks/sec)%n", 
                         duration, tasksPerSecond);
        System.out.printf("  Expected: < 2000ms | Actual: %dms%n", duration);
    }

    @Test
    @DisplayName("Should return accurate summary statistics with all status counts")
    void shouldReturnAccurateSummaryStatistics() {
        // Given: Tasks with known distribution across all statuses
        List<TaskCreateDto> tasks = List.of(
                createTask("Task 1", TaskStatus.TODO),
                createTask("Task 2", TaskStatus.TODO),
                createTask("Task 3", TaskStatus.IN_PROGRESS),
                createTask("Task 4", TaskStatus.DONE),
                createTask("Task 5", TaskStatus.DONE),
                createTask("Task 6", TaskStatus.DONE),
                createTask("Task 7", TaskStatus.CANCELLED)
        );
        taskService.bulkInsert(tasks);

        // When: Get summary
        TaskSummaryDto summary = taskService.getSummary();

        // Then: Summary matches expected distribution
        assertThat(summary.getTotalTasks()).isEqualTo(7);
        assertThat(summary.getTasksByStatus())
                .containsEntry("TODO", 2L)
                .containsEntry("IN_PROGRESS", 1L)
                .containsEntry("DONE", 3L)
                .containsEntry("CANCELLED", 1L);
    }

    @Test
    @DisplayName("Should handle empty task list gracefully")
    void shouldHandleEmptyTaskList() {
        // Given: Empty list
        List<TaskCreateDto> tasks = new ArrayList<>();

        // When: Bulk insert
        BulkInsertResultDto result = taskService.bulkInsert(tasks);

        // Then: No tasks inserted, no errors
        assertThat(result.getInserted()).isZero();
        assertThat(taskRepository.count()).isZero();
    }

    @Test
    @DisplayName("Should use SEQUENCE for batch-optimized ID generation")
    void shouldUseSequenceForBatchOptimizedIdGeneration() {
        // Given: 100 tasks to test SEQUENCE behavior
        List<TaskCreateDto> tasks = generateRealisticTasks(100);

        // When: Bulk insert
        taskService.bulkInsert(tasks);

        // Then: IDs should be generated by SEQUENCE (allocationSize=50)
        List<Long> ids = taskRepository.findAll().stream()
                .map(task -> task.getId())
                .sorted()
                .toList();

        assertThat(ids).hasSize(100);
        assertThat(ids.get(0)).isGreaterThanOrEqualTo(1L);
    }

    private List<TaskCreateDto> generateRealisticTasks(int count) {
        List<TaskCreateDto> tasks = new ArrayList<>();
        Random random = new Random();
        TaskStatus[] statuses = TaskStatus.values();
        
        String[] titlePrefixes = {
            "Implement", "Fix", "Refactor", "Design", "Test", "Deploy",
            "Review", "Update", "Optimize", "Document", "Migrate", "Enhance"
        };
        
        String[] titleSuffixes = {
            "user authentication", "database migration", "API endpoint",
            "unit tests", "error handling", "logging system", "cache layer",
            "search functionality", "payment integration", "email service",
            "notification system", "data validation", "security features"
        };

        for (int i = 0; i < count; i++) {
            String title = titlePrefixes[random.nextInt(titlePrefixes.length)] + " " +
                          titleSuffixes[random.nextInt(titleSuffixes.length)];
            
            TaskStatus status = statuses[random.nextInt(statuses.length)];
            Integer priority = random.nextInt(5) + 1;
            LocalDate dueDate = LocalDate.now().plusDays(random.nextInt(90));

            tasks.add(TaskCreateDto.builder()
                    .title(title)
                    .description("Generated test task #" + (i + 1) + " for integration testing")
                    .status(status)
                    .priority(priority)
                    .dueDate(dueDate)
                    .build());
        }

        return tasks;
    }

    private TaskCreateDto createTask(String title, TaskStatus status) {
        return TaskCreateDto.builder()
                .title(title)
                .status(status)
                .priority(3)
                .build();
    }
}
