package com.TbInventory.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Represents a batch produced in a fermenter tank.
 *
 * WORKFLOW NOTE: Batches should be created WITH an initial transaction (Option A).
 * The service layer should handle:
 *   1. Create FermBatch
 *   2. Create initial FermTransaction (Transfer In, etc.)
 *   3. Update FermTank.currentBatchId and currentQuantity
 *
 * Transactions are not stored as a collection on this entity for performance.
 * Query transactions via FermTransactionRepository.findByBatchIdOrderByDateDesc(batchId)
 */
@Entity
@Table(name = "ferm_batch")
@Data
@NoArgsConstructor
public class FermBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "tank_id", nullable = false)
    private Integer tankId;

    @NotBlank(message = "Batch name is required")
    @Column(name = "batch_name", nullable = false, length = 100)
    private String batchName;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "completion_date")
    private LocalDateTime completionDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (startDate == null) {
            startDate = LocalDateTime.now();
        }
    }

    /**
     * Constructor for creating a new batch.
     * Frontend allows user to select start date (defaults to today).
     */
    public FermBatch(Integer tankId, String batchName, LocalDateTime startDate) {
        this.tankId = tankId;
        this.batchName = batchName;
        this.startDate = startDate != null ? startDate : LocalDateTime.now();
    }

    /**
     * Checks if the batch is currently active (not completed).
     * @return true if completion date is null
     */
    public boolean isActive() {
        return completionDate == null;
    }

    /**
     * Calculates the number of days the batch has been in fermentation.
     * If completed, calculates from start to completion.
     * If active, calculates from start to now.
     * @return number of days in fermentation
     */
    public long getDaysInFermentation() {
        if (startDate == null) {
            return 0;
        }
        LocalDateTime endDate = completionDate != null ? completionDate : LocalDateTime.now();
        return ChronoUnit.DAYS.between(startDate, endDate);
    }
}
