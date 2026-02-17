package com.example.mcptaskserver.service;

import com.example.mcptaskserver.model.Task;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for optimized batch insert operations.
 * 
 * Uses EntityManager for fine-grained control over flushing and clearing
 * to prevent memory issues during large batch inserts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchInsertService {

    private final EntityManager entityManager;
    
    private static final int BATCH_SIZE = 50;

    /**
     * Performs optimized batch insert of tasks.
     * 
     * Flushes and clears EntityManager every BATCH_SIZE entities to prevent
     * memory issues and leverage Hibernate batching.
     * 
     * This method REQUIRES a transaction context (propagation = MANDATORY).
     * It's designed to be called from async methods that create their own transactions.
     *
     * @param tasks the tasks to insert
     * @return number of tasks inserted
     * @throws IllegalTransactionStateException if called outside a transaction
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int batchInsert(List<Task> tasks) {
        log.debug("Starting batch insert of {} tasks", tasks.size());
        
        for (int i = 0; i < tasks.size(); i++) {
            entityManager.persist(tasks.get(i));
            
            if ((i + 1) % BATCH_SIZE == 0) {
                // Flush and clear every BATCH_SIZE entities
                entityManager.flush();
                entityManager.clear();
                log.trace("Flushed and cleared at index {}", i + 1);
            }
        }
        
        // Flush remaining entities
        entityManager.flush();
        entityManager.clear();
        
        log.debug("Completed batch insert of {} tasks", tasks.size());
        return tasks.size();
    }
}
