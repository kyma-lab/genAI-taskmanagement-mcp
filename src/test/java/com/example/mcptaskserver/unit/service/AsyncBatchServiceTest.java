package com.example.mcptaskserver.unit.service;

import com.example.mcptaskserver.audit.AuditLogger;
import com.example.mcptaskserver.exception.ResourceNotFoundException;
import com.example.mcptaskserver.model.BatchJob;
import com.example.mcptaskserver.model.JobStatus;
import com.example.mcptaskserver.model.Task;
import com.example.mcptaskserver.repository.BatchJobRepository;
import com.example.mcptaskserver.service.AsyncBatchService;
import com.example.mcptaskserver.service.BatchInsertService;
import com.example.mcptaskserver.service.TasksInsertedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncBatchServiceTest {

    @Mock
    private BatchJobRepository batchJobRepository;

    @Mock
    private BatchInsertService batchInsertService;

    @Mock
    private AuditLogger auditLogger;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AsyncBatchService asyncBatchService;

    @Test
    void recoverStuckJobs_shouldMarkPendingJobsAsFailed() {
        // Given
        BatchJob pendingJob = createBatchJob("job-1", JobStatus.PENDING);
        when(batchJobRepository.findByStatusIn(Arrays.asList(JobStatus.PENDING, JobStatus.RUNNING)))
                .thenReturn(Collections.singletonList(pendingJob));

        // When
        asyncBatchService.recoverStuckJobs();

        // Then
        ArgumentCaptor<BatchJob> jobCaptor = ArgumentCaptor.forClass(BatchJob.class);
        verify(batchJobRepository).save(jobCaptor.capture());
        
        BatchJob savedJob = jobCaptor.getValue();
        assertThat(savedJob.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(savedJob.getErrorMessage()).contains("Server restarted during processing");
    }

    @Test
    void recoverStuckJobs_shouldMarkRunningJobsAsFailed() {
        // Given
        BatchJob runningJob = createBatchJob("job-2", JobStatus.RUNNING);
        when(batchJobRepository.findByStatusIn(Arrays.asList(JobStatus.PENDING, JobStatus.RUNNING)))
                .thenReturn(Collections.singletonList(runningJob));

        // When
        asyncBatchService.recoverStuckJobs();

        // Then
        ArgumentCaptor<BatchJob> jobCaptor = ArgumentCaptor.forClass(BatchJob.class);
        verify(batchJobRepository).save(jobCaptor.capture());
        
        BatchJob savedJob = jobCaptor.getValue();
        assertThat(savedJob.getStatus()).isEqualTo(JobStatus.FAILED);
    }

    @Test
    void recoverStuckJobs_shouldNotModifyCompletedJobs() {
        // Given
        when(batchJobRepository.findByStatusIn(Arrays.asList(JobStatus.PENDING, JobStatus.RUNNING)))
                .thenReturn(Collections.emptyList());

        // When
        asyncBatchService.recoverStuckJobs();

        // Then
        verify(batchJobRepository, never()).save(any());
    }

    @Test
    void recoverStuckJobs_shouldHandleMultipleStuckJobs() {
        // Given
        BatchJob pendingJob = createBatchJob("job-1", JobStatus.PENDING);
        BatchJob runningJob = createBatchJob("job-2", JobStatus.RUNNING);
        when(batchJobRepository.findByStatusIn(Arrays.asList(JobStatus.PENDING, JobStatus.RUNNING)))
                .thenReturn(Arrays.asList(pendingJob, runningJob));

        // When
        asyncBatchService.recoverStuckJobs();

        // Then
        verify(batchJobRepository, times(2)).save(any(BatchJob.class));
    }

    @Test
    void recoverStuckJobs_shouldHandleEmptyList() {
        // Given
        when(batchJobRepository.findByStatusIn(Arrays.asList(JobStatus.PENDING, JobStatus.RUNNING)))
                .thenReturn(Collections.emptyList());

        // When
        asyncBatchService.recoverStuckJobs();

        // Then
        verify(batchJobRepository, never()).save(any());
    }

    @Test
    void createJob_shouldCreateJobWithPendingStatus() {
        // Given
        BatchJob savedJob = createBatchJob("job-1", JobStatus.PENDING);
        savedJob.setTotalTasks(100);
        when(batchJobRepository.save(any(BatchJob.class))).thenReturn(savedJob);

        // When
        BatchJob result = asyncBatchService.createJob(100);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(result.getTotalTasks()).isEqualTo(100);
        
        ArgumentCaptor<BatchJob> jobCaptor = ArgumentCaptor.forClass(BatchJob.class);
        verify(batchJobRepository).save(jobCaptor.capture());
        
        BatchJob capturedJob = jobCaptor.getValue();
        assertThat(capturedJob.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(capturedJob.getTotalTasks()).isEqualTo(100);
        assertThat(capturedJob.getProcessedTasks()).isEqualTo(0);
    }

    @Test
    void findJob_shouldReturnJob_whenJobExists() {
        // Given
        BatchJob job = createBatchJob("job-1", JobStatus.COMPLETED);
        when(batchJobRepository.findById("job-1")).thenReturn(Optional.of(job));

        // When
        BatchJob result = asyncBatchService.findJob("job-1");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("job-1");
    }

    @Test
    void findJob_shouldThrowException_whenJobDoesNotExist() {
        // Given
        when(batchJobRepository.findById("job-1")).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> asyncBatchService.findJob("job-1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Job not found");
    }

    @Test
    void processAsync_shouldPublishTasksInsertedEvent_onSuccess() {
        // Given
        BatchJob job = createBatchJob("job-1", JobStatus.PENDING);
        job.setTotalTasks(10);
        List<Task> tasks = Collections.nCopies(10, new Task());
        when(batchJobRepository.findById("job-1")).thenReturn(Optional.of(job));
        when(batchJobRepository.save(any())).thenReturn(job);
        when(batchInsertService.batchInsert(tasks)).thenReturn(10);

        // When
        asyncBatchService.processAsync("job-1", tasks, null);

        // Then
        ArgumentCaptor<TasksInsertedEvent> eventCaptor = ArgumentCaptor.forClass(TasksInsertedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        TasksInsertedEvent event = eventCaptor.getValue();
        assertThat(event.getJobId()).isEqualTo("job-1");
        assertThat(event.getTaskCount()).isEqualTo(10);
    }

    @Test
    void processAsync_shouldNotPublishEvent_onFailure() {
        // Given
        BatchJob job = createBatchJob("job-1", JobStatus.PENDING);
        job.setTotalTasks(5);
        List<Task> tasks = Collections.nCopies(5, new Task());
        when(batchJobRepository.findById("job-1")).thenReturn(Optional.of(job));
        when(batchJobRepository.save(any())).thenReturn(job);
        when(batchInsertService.batchInsert(any())).thenThrow(new RuntimeException("DB error"));

        // When
        asyncBatchService.processAsync("job-1", tasks, null);

        // Then
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void markJobFailed_shouldUpdateStatusAndErrorMessage() {
        // Given
        BatchJob job = createBatchJob("job-1", JobStatus.PENDING);
        when(batchJobRepository.findById("job-1")).thenReturn(Optional.of(job));
        when(batchJobRepository.save(any())).thenReturn(job);

        // When
        asyncBatchService.markJobFailed("job-1", "Executor queue full. Please retry later.");

        // Then
        ArgumentCaptor<BatchJob> captor = ArgumentCaptor.forClass(BatchJob.class);
        verify(batchJobRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(captor.getValue().getErrorMessage()).contains("queue full");
        assertThat(captor.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    void markJobFailed_shouldDoNothing_whenJobDoesNotExist() {
        // Given
        when(batchJobRepository.findById("missing")).thenReturn(Optional.empty());

        // When
        asyncBatchService.markJobFailed("missing", "some reason");

        // Then – no save, no exception
        verify(batchJobRepository, never()).save(any());
    }

    private BatchJob createBatchJob(String id, JobStatus status) {
        BatchJob job = new BatchJob();
        job.setId(id);
        job.setStatus(status);
        job.setTotalTasks(0);
        job.setProcessedTasks(0);
        job.setCreatedAt(LocalDateTime.now());
        return job;
    }
}
