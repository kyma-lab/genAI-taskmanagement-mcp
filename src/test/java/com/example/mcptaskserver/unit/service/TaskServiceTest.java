package com.example.mcptaskserver.unit.service;

import com.example.mcptaskserver.dto.TaskSummaryDto;
import com.example.mcptaskserver.model.TaskStatus;
import com.example.mcptaskserver.repository.TaskRepository;
import com.example.mcptaskserver.service.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    @Test
    void generateSummary_shouldAggregateAllStatuses_whenAllStatusesPresent() {
        // Given
        List<Object[]> mockData = Arrays.asList(
                new Object[]{TaskStatus.TODO, 10L},
                new Object[]{TaskStatus.IN_PROGRESS, 5L},
                new Object[]{TaskStatus.DONE, 15L}
        );
        when(taskRepository.countGroupByStatus()).thenReturn(mockData);

        // When
        TaskSummaryDto summary = taskService.generateSummary();

        // Then
        assertThat(summary).isNotNull();
        assertThat(summary.totalCount()).isEqualTo(30L);
        
        Map<String, Long> countByStatus = summary.countByStatus();
        assertThat(countByStatus).hasSize(3);
        assertThat(countByStatus.get("TODO")).isEqualTo(10L);
        assertThat(countByStatus.get("IN_PROGRESS")).isEqualTo(5L);
        assertThat(countByStatus.get("DONE")).isEqualTo(15L);
    }

    @Test
    void generateSummary_shouldFillZeroForMissingStatuses_whenSomeStatusesMissing() {
        // Given
        List<Object[]> mockData = Collections.singletonList(
                new Object[]{TaskStatus.TODO, 10L}
        );
        when(taskRepository.countGroupByStatus()).thenReturn(mockData);

        // When
        TaskSummaryDto summary = taskService.generateSummary();

        // Then
        assertThat(summary.totalCount()).isEqualTo(10L);
        
        Map<String, Long> countByStatus = summary.countByStatus();
        assertThat(countByStatus.get("TODO")).isEqualTo(10L);
        assertThat(countByStatus.get("IN_PROGRESS")).isEqualTo(0L);
        assertThat(countByStatus.get("DONE")).isEqualTo(0L);
    }

    @Test
    void generateSummary_shouldReturnZeroForAll_whenNoTasks() {
        // Given
        when(taskRepository.countGroupByStatus()).thenReturn(Collections.emptyList());

        // When
        TaskSummaryDto summary = taskService.generateSummary();

        // Then
        assertThat(summary.totalCount()).isEqualTo(0L);
        
        Map<String, Long> countByStatus = summary.countByStatus();
        assertThat(countByStatus.get("TODO")).isEqualTo(0L);
        assertThat(countByStatus.get("IN_PROGRESS")).isEqualTo(0L);
        assertThat(countByStatus.get("DONE")).isEqualTo(0L);
    }

    @Test
    void generateSummary_shouldCalculateCorrectTotal_whenMultipleStatuses() {
        // Given
        List<Object[]> mockData = Arrays.asList(
                new Object[]{TaskStatus.TODO, 100L},
                new Object[]{TaskStatus.DONE, 50L}
        );
        when(taskRepository.countGroupByStatus()).thenReturn(mockData);

        // When
        TaskSummaryDto summary = taskService.generateSummary();

        // Then
        assertThat(summary.totalCount()).isEqualTo(150L);
    }

    @Test
    void generateSummary_shouldMapStatusNamesToStrings() {
        // Given
        List<Object[]> mockData = Collections.singletonList(
                new Object[]{TaskStatus.IN_PROGRESS, 25L}
        );
        when(taskRepository.countGroupByStatus()).thenReturn(mockData);

        // When
        TaskSummaryDto summary = taskService.generateSummary();

        // Then
        Map<String, Long> countByStatus = summary.countByStatus();
        assertThat(countByStatus).containsKey("IN_PROGRESS");
        assertThat(countByStatus.get("IN_PROGRESS")).isEqualTo(25L);
    }
}
