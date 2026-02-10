package com.example.mcptask.service;

import com.example.mcptask.dto.BulkInsertResultDto;
import com.example.mcptask.dto.TaskCreateDto;
import com.example.mcptask.dto.TaskSummaryDto;
import com.example.mcptask.model.Task;
import com.example.mcptask.model.TaskStatus;
import com.example.mcptask.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskRepository taskRepository;

    /**
     * Bulk insert tasks with optimized batching
     * Uses Hibernate batch processing for performance
     */
    @Transactional
    public BulkInsertResultDto bulkInsert(List<TaskCreateDto> taskDtos) {
        log.info("Starting bulk insert of {} tasks", taskDtos.size());
        
        long startTime = System.currentTimeMillis();
        
        // Convert DTOs to entities
        List<Task> tasks = taskDtos.stream()
                .map(this::convertToEntity)
                .collect(Collectors.toList());
        
        // Batch save all tasks
        List<Task> savedTasks = taskRepository.saveAll(tasks);
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("Bulk insert completed: {} tasks in {}ms ({} tasks/sec)", 
                 savedTasks.size(), duration, (savedTasks.size() * 1000.0 / duration));
        
        return BulkInsertResultDto.builder()
                .inserted(savedTasks.size())
                .message(String.format("Successfully inserted %d tasks in %dms", savedTasks.size(), duration))
                .build();
    }

    /**
     * Get summary statistics for all tasks
     */
    @Transactional(readOnly = true)
    public TaskSummaryDto getSummary() {
        long totalTasks = taskRepository.count();
        
        Map<String, Long> tasksByStatus = new HashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            long count = taskRepository.countByStatus(status);
            tasksByStatus.put(status.name(), count);
        }
        
        return TaskSummaryDto.builder()
                .totalTasks(totalTasks)
                .tasksByStatus(tasksByStatus)
                .build();
    }

    private Task convertToEntity(TaskCreateDto dto) {
        return Task.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .status(dto.getStatus())
                .priority(dto.getPriority())
                .dueDate(dto.getDueDate())
                .build();
    }
}
