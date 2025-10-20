package com.TbInventory.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a transaction for a fermenter batch (additions, removals, transfers, notes).
 *
 * Examples:
 * - Yeast Addition: 50g
 * - Transfer Out: 10 bbls to Bright Tank
 * - Waste/Drain: 2 bbls
 * - Lysozyme Addition: 25g
 */
@Entity
@Table(name = "ferm_transaction")
@Data
@NoArgsConstructor
public class FermTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "batch_id", nullable = false)
    private Integer batchId;

    @NotNull(message = "Transaction type is required")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "transaction_type_id", nullable = false)
    private TransactionType transactionType;

    @NotNull(message = "Quantity is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "user_id")
    private Integer userId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Reference to bright tank if this is a transfer transaction.
     * Null for non-transfer transactions.
     */
    @Column(name = "bright_tank_id")
    private Integer brightTankId;

    /**
     * Constructor for creating a new transaction.
     * Frontend allows user to select transaction date (defaults to now).
     */
    public FermTransaction(Integer batchId, TransactionType transactionType, BigDecimal quantity,
                           LocalDateTime transactionDate, Integer userId, String notes) {
        this.batchId = batchId;
        this.transactionType = transactionType;
        this.quantity = quantity;
        this.transactionDate = transactionDate != null ? transactionDate : LocalDateTime.now();
        this.userId = userId;
        this.notes = notes;
    }

    /**
     * Checks if this transaction affects tank quantity.
     * Delegates to the transaction type's configuration.
     * @return true if this transaction should update tank quantity
     */
    public boolean affectsTankQuantity() {
        return transactionType != null && transactionType.getAffectsTankQuantity();
    }
}
