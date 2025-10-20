package com.TbInventory.repository;

import com.TbInventory.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for TransactionType entities.
 * Provides basic CRUD operations via JpaRepository.
 *
 * Note: Transaction types are loaded and cached on service startup using findAll().
 */
@Repository
public interface TransactionTypeRepository extends JpaRepository<TransactionType, Integer> {
    // No custom methods needed - use findAll() for caching
}
