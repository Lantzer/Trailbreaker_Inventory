package com.TbInventory.repository;

import com.TbInventory.model.FermTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for FermTransaction entities.
 * Provides basic CRUD operations and custom queries for transaction management.
 */
@Repository
public interface FermTransactionRepository extends JpaRepository<FermTransaction, Integer> {

    /**
     * Find all transactions for a batch, ordered by date descending.
     * Used on batch details page to show complete transaction history.
     * @param batchId The batch ID
     * @return List of transactions for this batch, newest first
     */
    List<FermTransaction> findByBatchIdOrderByTransactionDateDesc(Integer batchId);

    /**
     * Find transactions for a batch filtered by transaction type, ordered by date descending.
     * Used when user filters transactions (e.g., "Show only Transfer Out transactions").
     * @param batchId The batch ID
     * @param transactionTypeId The transaction type ID to filter by
     * @return List of filtered transactions for this batch, newest first
     */
    List<FermTransaction> findByBatchIdAndTransactionType_IdOrderByTransactionDateDesc(Integer batchId, Integer transactionTypeId);
}
