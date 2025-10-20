package com.TbInventory.repository;

import com.TbInventory.model.FermBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for FermBatch entities.
 * Provides basic CRUD operations and custom queries for batch management.
 */
@Repository
public interface FermBatchRepository extends JpaRepository<FermBatch, Integer> {

    /**
     * Find all active batches (not completed).
     * Used for main fermenters page to show batches currently in progress.
     * @return List of active batches
     */
    List<FermBatch> findByCompletionDateIsNull();

    /**
     * Find all completed batches (for "Previous Batches" page).
     * Ordered by completion date descending (most recent first).
     * @return List of completed batches, newest first
     */
    List<FermBatch> findByCompletionDateIsNotNullOrderByCompletionDateDesc();

    /**
     * Find all batches for a specific tank (active and completed).
     * Used for tank history view to show all batches that have been in this tank.
     * Ordered by start date descending (most recent first).
     * @param tankId The tank ID
     * @return List of batches for this tank, newest first
     */
    List<FermBatch> findByTankIdOrderByStartDateDesc(Integer tankId);

    /**
     * Find the current active batch for a specific tank.
     * Used when displaying tank details to get the current batch info.
     * @param tankId The tank ID
     * @return Optional containing the active batch if found
     */
    Optional<FermBatch> findByTankIdAndCompletionDateIsNull(Integer tankId);
}
