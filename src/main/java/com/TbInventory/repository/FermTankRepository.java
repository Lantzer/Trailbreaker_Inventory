package com.TbInventory.repository;

import com.TbInventory.model.FermTank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for FermTank entities.
 * Provides basic CRUD operations and custom queries for tank management.
 */
@Repository
public interface FermTankRepository extends JpaRepository<FermTank, Integer> {

    /**
     * Find a tank by its label (e.g., "FV-1").
     * Used for label-based URLs: /fermenters/FV-1
     * @param label The tank label
     * @return Optional containing the tank if found
     */
    Optional<FermTank> findByLabel(String label);

    /**
     * Find all tanks with active batches (currentBatchId is not null).
     * Used to show which tanks are currently in use.
     * @return List of tanks with active batches
     */
    List<FermTank> findByCurrentBatchIdIsNotNull();

    /**
     * Find all empty tanks (currentBatchId is null).
     * Used to show available tanks when starting a new batch.
     * @return List of empty/available tanks
     */
    List<FermTank> findByCurrentBatchIdIsNull();

    /**
     * Check if a tank with the given label already exists.
     * Used for duplicate validation when creating/editing tanks.
     * @param label The tank label to check
     * @return true if a tank with this label exists
     */
    boolean existsByLabel(String label);
}
