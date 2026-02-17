package com.example.mcptaskserver.repository;

import com.example.mcptaskserver.model.BatchJob;
import com.example.mcptaskserver.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for batch job persistence.
 */
@Repository
public interface BatchJobRepository extends JpaRepository<BatchJob, String> {
    
    /**
     * Finds all batch jobs with status in the given list.
     * Used for recovering stuck jobs on startup.
     *
     * @param statuses list of job statuses to search for
     * @return list of matching batch jobs
     */
    List<BatchJob> findByStatusIn(List<JobStatus> statuses);
}
