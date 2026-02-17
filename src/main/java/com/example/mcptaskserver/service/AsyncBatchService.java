package com.example.mcptaskserver.service;

import com.example.mcptaskserver.audit.AuditEvent;
import com.example.mcptaskserver.audit.AuditEventType;
import com.example.mcptaskserver.audit.AuditLogger;
import com.example.mcptaskserver.util.CorrelationIdContext;
import com.example.mcptaskserver.exception.ResourceNotFoundException;
import com.example.mcptaskserver.model.BatchJob;
import com.example.mcptaskserver.model.JobStatus;
import com.example.mcptaskserver.model.Task;
import com.example.mcptaskserver.repository.BatchJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * Service for asynchronous batch processing.
 * Emits SSE events for job progress when HTTP transport is enabled.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncBatchService {

    private final BatchInsertService batchInsertService;
    private final BatchJobRepository batchJobRepository;
    private final AuditLogger auditLogger;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Recovers stuck jobs (PENDING or RUNNING) on startup.
     * Jobs in these states after server restart indicate the server crashed during processing.
     *
     * Uses {@code @EventListener(ApplicationReadyEvent)} instead of {@code @PostConstruct}
     * so that Spring AOP proxies are fully active and {@code @Transactional} is honoured.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverStuckJobs() {
        List<BatchJob> stuckJobs = batchJobRepository.findByStatusIn(
            List.of(JobStatus.PENDING, JobStatus.RUNNING)
        );
        
        if (!stuckJobs.isEmpty()) {
            log.warn("Found {} stuck jobs on startup, marking as FAILED", stuckJobs.size());
            for (BatchJob job : stuckJobs) {
                job.setStatus(JobStatus.FAILED);
                job.setErrorMessage("Server restarted during processing");
                job.setCompletedAt(LocalDateTime.now());
                batchJobRepository.save(job);
            }
            log.info("Recovered {} stuck jobs", stuckJobs.size());
        }
    }

    /**
     * Creates a new batch job in PENDING status.
     * 
     * @param totalTasks number of tasks to process
     * @return the created batch job
     */
    public BatchJob createJob(int totalTasks) {
        BatchJob job = BatchJob.builder()
            .status(JobStatus.PENDING)
            .totalTasks(totalTasks)
            .processedTasks(0)
            .build();
        
        BatchJob savedJob = batchJobRepository.save(job);
        logBatchJobCreated(savedJob);
        return savedJob;
    }

    /**
     * Asynchronously processes a batch of tasks.
     * Updates job status and metrics during processing.
     *
     * Uses REQUIRES_NEW propagation to ensure this async method creates its own transaction,
     * independent of the calling transaction.
     *
     * @param jobId the job ID
     * @param tasks the tasks to insert
     */
    @Async("batchExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processAsync(String jobId, List<Task> tasks) {
        processAsync(jobId, tasks, null);
    }

    /**
     * Asynchronously processes a batch of tasks with optional progress notifications.
     * The progressCallback is called with 0 when processing starts and 100 when complete.
     * Notifications are best-effort: failures are logged at DEBUG level and ignored.
     *
     * Uses REQUIRES_NEW propagation to ensure this async method creates its own transaction,
     * independent of the calling transaction.
     *
     * @param jobId            the job ID
     * @param tasks            the tasks to insert
     * @param progressCallback called with progress percentage (0–100); may be null
     */
    @Async("batchExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processAsync(String jobId, List<Task> tasks, Consumer<Integer> progressCallback) {
        long startTime = System.currentTimeMillis();
        BatchJob job = null;

        try {
            log.info("Starting async batch processing for job {}", jobId);

            // Fetch job once at the start
            job = batchJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));

            // Update status to RUNNING
            job.setStatus(JobStatus.RUNNING);
            batchJobRepository.save(job);
            logBatchJobStarted(job);
            notifyProgress(progressCallback, 0);

            // Process batch (batchInsert participates in this transaction via MANDATORY propagation)
            int processed = batchInsertService.batchInsert(tasks);
            long duration = System.currentTimeMillis() - startTime;

            // Update status to COMPLETED (reuse same job object)
            job.setStatus(JobStatus.COMPLETED);
            job.setProcessedTasks(processed);
            job.setDurationMs(duration);
            job.setCompletedAt(LocalDateTime.now());
            batchJobRepository.save(job);
            notifyProgress(progressCallback, 100);
            eventPublisher.publishEvent(new TasksInsertedEvent(this, jobId, processed));

            log.info("Completed async batch processing for job {}: {} tasks in {}ms",
                jobId, processed, duration);

            logBatchJobCompleted(job);

        } catch (Exception e) {
            log.error("Error processing batch job {}", jobId, e);

            // Update status to FAILED (reuse job if available, otherwise fetch)
            if (job == null) {
                job = batchJobRepository.findById(jobId).orElse(null);
            }
            if (job != null) {
                job.setStatus(JobStatus.FAILED);
                job.setErrorMessage(e.getMessage());
                job.setDurationMs(System.currentTimeMillis() - startTime);
                job.setCompletedAt(LocalDateTime.now());
                batchJobRepository.save(job);
                logBatchJobFailed(job, e);
            }
        }
    }

    private void notifyProgress(Consumer<Integer> callback, int percent) {
        if (callback != null) {
            try {
                callback.accept(percent);
            } catch (Exception e) {
                log.debug("Progress notification failed (best-effort): {}", e.getMessage());
            }
        }
    }

    /**
     * Log batch job created audit event.
     */
    private void logBatchJobCreated(BatchJob job) {
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.BATCH_JOB_CREATED)
            .correlationId(CorrelationIdContext.getCurrentCorrelationId())
            .success(true)
            .metadata("jobId", job.getId())
            .metadata("totalTasks", job.getTotalTasks())
            .build();
        auditLogger.log(event);
    }

    /**
     * Log batch job started audit event.
     */
    private void logBatchJobStarted(BatchJob job) {
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.BATCH_JOB_STARTED)
            .correlationId(CorrelationIdContext.getCurrentCorrelationId())
            .success(true)
            .metadata("jobId", job.getId())
            .metadata("totalTasks", job.getTotalTasks())
            .build();
        auditLogger.log(event);
    }

    /**
     * Log batch job completed audit event.
     */
    private void logBatchJobCompleted(BatchJob job) {
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.BATCH_JOB_COMPLETED)
            .correlationId(CorrelationIdContext.getCurrentCorrelationId())
            .success(true)
            .metadata("jobId", job.getId())
            .metadata("totalTasks", job.getTotalTasks())
            .metadata("processedTasks", job.getProcessedTasks())
            .metadata("durationMs", job.getDurationMs())
            .build();
        auditLogger.log(event);
    }

    /**
     * Log batch job failed audit event.
     */
    private void logBatchJobFailed(BatchJob job, Exception error) {
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.BATCH_JOB_FAILED)
            .correlationId(CorrelationIdContext.getCurrentCorrelationId())
            .success(false)
            .errorMessage(error.getMessage())
            .metadata("jobId", job.getId())
            .metadata("totalTasks", job.getTotalTasks())
            .metadata("processedTasks", job.getProcessedTasks())
            .build();
        auditLogger.log(event);
    }

    /**
     * Marks an existing job as FAILED immediately.
     * Called when the async executor rejects the submitted task (queue full),
     * preventing the job from staying stuck in PENDING indefinitely.
     *
     * @param jobId  the job to mark
     * @param reason human-readable failure reason
     */
    public void markJobFailed(String jobId, String reason) {
        batchJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(reason);
            job.setCompletedAt(LocalDateTime.now());
            batchJobRepository.save(job);
            log.warn("Job {} marked as FAILED: {}", jobId, reason);
        });
    }

    /**
     * Retrieves job status.
     *
     * @param jobId the job ID
     * @return the batch job
     */
    public BatchJob findJob(String jobId) {
        return batchJobRepository.findById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
    }
}
