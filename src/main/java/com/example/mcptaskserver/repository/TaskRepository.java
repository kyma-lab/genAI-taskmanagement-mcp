package com.example.mcptaskserver.repository;

import com.example.mcptaskserver.model.Task;
import com.example.mcptaskserver.model.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository interface for Task entity operations.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * Counts tasks by specific status.
     * 
     * @param status the task status
     * @return count of tasks with given status
     */
    long countByStatus(TaskStatus status);

    /**
     * Aggregates task counts grouped by status.
     * Eliminates N+1 query problem by fetching all status counts in a single query.
     *
     * @return list of [TaskStatus, count] pairs
     */
    @Query("SELECT t.status as status, COUNT(t) as count FROM Task t GROUP BY t.status")
    List<Object[]> countGroupByStatus();

    /**
     * Finds the earliest due date.
     * 
     * @return optional earliest due date
     */
    @Query("SELECT MIN(t.dueDate) FROM Task t WHERE t.dueDate IS NOT NULL")
    Optional<LocalDate> findEarliestDueDate();

    /**
     * Finds the latest due date.
     * 
     * @return optional latest due date
     */
    @Query("SELECT MAX(t.dueDate) FROM Task t WHERE t.dueDate IS NOT NULL")
    Optional<LocalDate> findLatestDueDate();

    /**
     * Finds tasks by status with pagination support.
     * 
     * @param status the task status to filter by
     * @param pageable pagination parameters
     * @return page of tasks with given status
     */
    Page<Task> findByStatus(TaskStatus status, Pageable pageable);

    /**
     * Finds all tasks with pagination support.
     * 
     * @param pageable pagination parameters
     * @return page of all tasks
     */
    Page<Task> findAll(Pageable pageable);
}
