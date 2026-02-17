package com.example.mcptaskserver.integration;

import com.example.mcptaskserver.model.BatchJob;
import com.example.mcptaskserver.model.JobStatus;
import com.example.mcptaskserver.model.Task;
import com.example.mcptaskserver.model.TaskStatus;
import com.example.mcptaskserver.repository.BatchJobRepository;
import com.example.mcptaskserver.repository.TaskRepository;
import com.example.mcptaskserver.service.AsyncBatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for asynchronous batch processing.
 */
class AsyncBatchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AsyncBatchService asyncBatchService;

    @Autowired
    private BatchJobRepository batchJobRepository;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        batchJobRepository.deleteAll();
    }

    @Test
    void shouldProcessBatchAsynchronously() {
        // Given: Create test tasks
        List<Task> tasks = createTestTasks(100);

        // When: Create job and process async
        BatchJob job = asyncBatchService.createJob(tasks.size());
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);

        asyncBatchService.processAsync(job.getId(), tasks);

        // Then: Wait for completion
        await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> {
                BatchJob updatedJob = asyncBatchService.findJob(job.getId());
                assertThat(updatedJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
                assertThat(updatedJob.getProcessedTasks()).isEqualTo(100);
                assertThat(updatedJob.getDurationMs()).isNotNull();
                assertThat(updatedJob.getCompletedAt()).isNotNull();
            });

        // Verify tasks were inserted
        long taskCount = taskRepository.count();
        assertThat(taskCount).isEqualTo(100);
    }

    @Test
    void shouldHandleMultipleConcurrentBatches() {
        // Given: Create multiple batches
        List<Task> batch1 = createTestTasks(50);
        List<Task> batch2 = createTestTasks(75);
        List<Task> batch3 = createTestTasks(100);

        // When: Submit all batches concurrently
        BatchJob job1 = asyncBatchService.createJob(batch1.size());
        BatchJob job2 = asyncBatchService.createJob(batch2.size());
        BatchJob job3 = asyncBatchService.createJob(batch3.size());

        asyncBatchService.processAsync(job1.getId(), batch1);
        asyncBatchService.processAsync(job2.getId(), batch2);
        asyncBatchService.processAsync(job3.getId(), batch3);

        // Then: Wait for all to complete
        await()
            .atMost(15, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                BatchJob updatedJob1 = asyncBatchService.findJob(job1.getId());
                BatchJob updatedJob2 = asyncBatchService.findJob(job2.getId());
                BatchJob updatedJob3 = asyncBatchService.findJob(job3.getId());

                assertThat(updatedJob1.getStatus()).isEqualTo(JobStatus.COMPLETED);
                assertThat(updatedJob2.getStatus()).isEqualTo(JobStatus.COMPLETED);
                assertThat(updatedJob3.getStatus()).isEqualTo(JobStatus.COMPLETED);
            });

        // Verify total tasks
        long taskCount = taskRepository.count();
        assertThat(taskCount).isEqualTo(225);
    }

    @Test
    void shouldTrackJobProgress() {
        // Given: Create a large batch
        List<Task> tasks = createTestTasks(500);

        // When: Start processing
        BatchJob job = asyncBatchService.createJob(tasks.size());
        asyncBatchService.processAsync(job.getId(), tasks);

        // Then: Job should go through states: PENDING -> RUNNING -> COMPLETED
        await()
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                BatchJob runningJob = asyncBatchService.findJob(job.getId());
                assertThat(runningJob.getStatus())
                    .isIn(JobStatus.RUNNING, JobStatus.COMPLETED);
            });

        await()
            .atMost(15, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                BatchJob completedJob = asyncBatchService.findJob(job.getId());
                assertThat(completedJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
                assertThat(completedJob.getProcessedTasks()).isEqualTo(500);
            });
    }

    @Test
    void shouldHandleEmptyBatch() {
        // Given: Empty batch
        List<Task> tasks = new ArrayList<>();

        // When: Create and process
        BatchJob job = asyncBatchService.createJob(0);
        asyncBatchService.processAsync(job.getId(), tasks);

        // Then: Should complete immediately
        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                BatchJob completedJob = asyncBatchService.findJob(job.getId());
                assertThat(completedJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
                assertThat(completedJob.getProcessedTasks()).isEqualTo(0);
            });
    }

    @Test
    void shouldCalculatePerformanceMetrics() {
        // Given: Create batch
        List<Task> tasks = createTestTasks(200);

        // When: Process
        BatchJob job = asyncBatchService.createJob(tasks.size());
        asyncBatchService.processAsync(job.getId(), tasks);

        // Then: Verify metrics
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                BatchJob completedJob = asyncBatchService.findJob(job.getId());
                assertThat(completedJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
                assertThat(completedJob.getDurationMs()).isGreaterThan(0);
                
                // Calculate throughput
                long tasksPerSecond = (completedJob.getProcessedTasks() * 1000L) 
                    / completedJob.getDurationMs();
                assertThat(tasksPerSecond).isGreaterThan(0);
            });
    }

    /**
     * Creates test tasks with unique titles.
     */
    private List<Task> createTestTasks(int count) {
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tasks.add(Task.builder()
                .title("Async Test Task " + (i + 1))
                .description("Test task for async batch processing")
                .status(TaskStatus.TODO)
                .dueDate(LocalDate.now().plusDays(7))
                .build());
        }
        return tasks;
    }
}
