package com.example.mcptask.repository;

import com.example.mcptask.model.Task;
import com.example.mcptask.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    
    /**
     * Count tasks by status
     */
    long countByStatus(TaskStatus status);
    
    /**
     * Get summary statistics for all tasks
     */
    @Query("SELECT t.status as status, COUNT(t) as count FROM Task t GROUP BY t.status")
    Map<TaskStatus, Long> getTaskCountByStatus();
}
