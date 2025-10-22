package com.TbInventory.repository;

import com.TbInventory.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for TransactionType entities.
 * Provides basic CRUD operations via JpaRepository.
 *
 * Note: Transaction types are loaded and cached on service startup using findAll().
 */
@Repository
public interface TransactionTypeRepository extends JpaRepository<TransactionType, Integer> {

    /**
     * Find transaction type by name (e.g., "Transfer In", "Yeast Addition").
     * Used in tests to find or create reference data.
     * @param typeName Transaction type name
     * @return Optional containing transaction type if found
     */
    Optional<TransactionType> findByTypeName(String typeName);
}
