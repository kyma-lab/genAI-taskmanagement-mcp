package com.example.mcptaskserver.service;

import com.example.mcptaskserver.dto.TaskListResponseDto;
import com.example.mcptaskserver.dto.TaskSummaryDto;
import com.example.mcptaskserver.model.Task;
import com.example.mcptaskserver.model.TaskStatus;
import com.example.mcptaskserver.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for task CRUD operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskRepository taskRepository;

    /**
     * Generates summary statistics for all tasks.
     * 
     * @return task summary DTO
     */
    @Transactional(readOnly = true)
    public TaskSummaryDto generateSummary() {
        // Use single aggregated query instead of N+1 queries
        List<Object[]> statusCounts = taskRepository.countGroupByStatus();
        
        Map<String, Long> countByStatus = new HashMap<>();
        long totalCount = 0;
        
        // Process aggregated results
        for (Object[] row : statusCounts) {
            TaskStatus status = (TaskStatus) row[0];
            Long count = (Long) row[1];
            countByStatus.put(status.getValue(), count);
            totalCount += count;
        }
        
        // Ensure all statuses are represented (even if count is 0)
        for (TaskStatus status : TaskStatus.values()) {
            countByStatus.putIfAbsent(status.getValue(), 0L);
        }
        
        return TaskSummaryDto.builder()
            .totalCount(totalCount)
            .countByStatus(countByStatus)
            .earliestDueDate(taskRepository.findEarliestDueDate().orElse(null))
            .latestDueDate(taskRepository.findLatestDueDate().orElse(null))
            .generatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Counts all tasks.
     * 
     * @return total task count
     */
    @Transactional(readOnly = true)
    public long count() {
        return taskRepository.count();
    }

    /**
     * Finds a task by ID.
     *
     * @param id the task ID
     * @return optional containing the task if found
     */
    @Transactional(readOnly = true)
    public Optional<Task> findById(Long id) {
        return taskRepository.findById(id);
    }

    /**
     * Retrieves all tasks.
     *
     * @return list of all tasks
     */
    @Transactional(readOnly = true)
    public List<Task> findAll() {
        return taskRepository.findAll();
    }

    /**
     * Retrieves tasks up to the given limit (bounded query, prevents OOM).
     *
     * @param limit maximum number of tasks to return
     * @return list of tasks
     */
    @Transactional(readOnly = true)
    public List<Task> findAll(int limit) {
        return taskRepository.findAll(PageRequest.of(0, limit)).getContent();
    }

    /**
     * Retrieves tasks with pagination and optional status filter.
     *
     * @param page current page (0-based)
     * @param pageSize number of items per page (max 1000)
     * @param status optional status filter
     * @return paginated task list response
     */
    @Transactional(readOnly = true)
    public TaskListResponseDto listTasks(int page, int pageSize, String status) {
        // Validate and constrain pagination parameters
        if (page < 0) {
            log.warn("Invalid page parameter {}: clamped to 0", page);
            page = 0;
        }
        if (pageSize <= 0) {
            log.warn("Invalid pageSize parameter {}: clamped to default 100", pageSize);
            pageSize = 100;
        }
        if (pageSize > 1000) {
            log.warn("pageSize {} exceeds maximum: clamped to 1000", pageSize);
            pageSize = 1000;
        }

        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("id").ascending());
        Page<Task> taskPage;

        if (status != null && !status.isBlank()) {
            TaskStatus taskStatus = TaskStatus.fromValue(status);
            taskPage = taskRepository.findByStatus(taskStatus, pageable);
        } else {
            taskPage = taskRepository.findAll(pageable);
        }

        return TaskListResponseDto.builder()
            .tasks(taskPage.getContent().stream()
                .map(this::toDto)
                .toList())
            .total(taskPage.getTotalElements())
            .page(taskPage.getNumber())
            .pageSize(taskPage.getSize())
            .totalPages(taskPage.getTotalPages())
            .build();
    }

    private com.example.mcptaskserver.dto.TaskDto toDto(Task task) {
        return com.example.mcptaskserver.dto.TaskDto.builder()
            .id(task.getId())
            .title(task.getTitle())
            .description(task.getDescription())
            .status(task.getStatus().getValue())
            .dueDate(task.getDueDate())
            .createdAt(task.getCreatedAt())
            .updatedAt(task.getUpdatedAt())
            .build();
    }
}
