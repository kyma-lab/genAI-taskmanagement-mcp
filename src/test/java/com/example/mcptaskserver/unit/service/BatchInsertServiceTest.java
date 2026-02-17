package com.example.mcptaskserver.unit.service;

import com.example.mcptaskserver.model.Task;
import com.example.mcptaskserver.model.TaskStatus;
import com.example.mcptaskserver.service.BatchInsertService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchInsertServiceTest {

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private BatchInsertService batchInsertService;

    @Test
    void batchInsert_shouldFlushAndClearEvery50Entities() {
        // Given
        List<Task> tasks = createTasks(150);

        // When
        batchInsertService.batchInsert(tasks);

        // Then
        verify(entityManager, times(150)).persist(any(Task.class));
        verify(entityManager, times(4)).flush(); // At 50, 100, 150, and final
        verify(entityManager, times(4)).clear();
    }

    @Test
    void batchInsert_shouldFlushOnce_whenExactly50Tasks() {
        // Given
        List<Task> tasks = createTasks(50);

        // When
        batchInsertService.batchInsert(tasks);

        // Then
        verify(entityManager, times(50)).persist(any(Task.class));
        verify(entityManager, times(2)).flush(); // At 50 and final
        verify(entityManager, times(2)).clear();
    }

    @Test
    void batchInsert_shouldNotFlush_whenLessThan50Tasks() {
        // Given
        List<Task> tasks = createTasks(49);

        // When
        batchInsertService.batchInsert(tasks);

        // Then
        verify(entityManager, times(49)).persist(any(Task.class));
        verify(entityManager, times(1)).flush(); // Final flush
        verify(entityManager, times(1)).clear();
    }

    @Test
    void batchInsert_shouldFlushTwice_when99Tasks() {
        // Given
        List<Task> tasks = createTasks(99);

        // When
        batchInsertService.batchInsert(tasks);

        // Then
        verify(entityManager, times(99)).persist(any(Task.class));
        verify(entityManager, times(2)).flush(); // At 50 and final
        verify(entityManager, times(2)).clear();
    }

    @Test
    void batchInsert_shouldHandleEmptyList() {
        // Given
        List<Task> tasks = new ArrayList<>();

        // When
        batchInsertService.batchInsert(tasks);

        // Then
        verify(entityManager, never()).persist(any());
        verify(entityManager, times(1)).flush(); // Final flush always happens
        verify(entityManager, times(1)).clear();
    }

    @Test
    void batchInsert_shouldPersistAllTasks() {
        // Given
        List<Task> tasks = createTasks(75);

        // When
        batchInsertService.batchInsert(tasks);

        // Then
        verify(entityManager, times(75)).persist(any(Task.class));
    }

    @Test
    void batchInsert_shouldFlushAtExactBoundaries() {
        // Given - 200 tasks should flush at 50, 100, 150, 200, and final
        List<Task> tasks = createTasks(200);

        // When
        batchInsertService.batchInsert(tasks);

        // Then
        verify(entityManager, times(200)).persist(any(Task.class));
        verify(entityManager, times(5)).flush(); // At 50, 100, 150, 200, and final
        verify(entityManager, times(5)).clear();
    }

    private List<Task> createTasks(int count) {
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Task task = new Task();
            task.setTitle("Task " + i);
            task.setStatus(TaskStatus.TODO);
            tasks.add(task);
        }
        return tasks;
    }
}
