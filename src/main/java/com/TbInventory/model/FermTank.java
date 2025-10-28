package com.TbInventory.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Represents a physical fermenter tank.
 * Capacity is always stored in gallons.
 *
 * TODO: Create edit tank page in frontend to allow updating:
 *       - label
 *       - capacity (in gallons)
 */
@Entity
@Table(name = "ferm_tank")
@Data
@NoArgsConstructor
public class FermTank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "Tank label is required")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$",
             message = "Tank label must contain only letters, numbers, hyphens, and underscores (URL-safe)")
    @Column(nullable = false, unique = true, length = 100)
    private String label;

    @Column(name = "current_batch_id")
    private Integer currentBatchId;

    @Column(name = "current_quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal currentQuantity = BigDecimal.ZERO;

    @NotNull(message = "Capacity is required")
    @Min(value = 0, message = "Capacity must be positive")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal capacity; // Always in gallons

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Soft delete this tank (marks as deleted without removing from database).
     */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    /**
     * Restore a soft-deleted tank.
     */
    public void restore() {
        this.deletedAt = null;
    }

    /**
     * Check if this tank is soft-deleted.
     */
    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    /**
     * Constructor for creating a new tank.
     * Capacity is always in gallons.
     */
    public FermTank(String label, BigDecimal capacity) {
        this.label = label;
        this.capacity = capacity;
        this.currentQuantity = BigDecimal.ZERO;
    }

    /**
     * Checks if the tank is below a given capacity threshold percentage.
     * @param thresholdPercent The threshold percentage (0-100)
     * @return true if current quantity is below the threshold
     */
    public boolean isLowCapacity(int thresholdPercent) {
        if (capacity == null || capacity.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }
        BigDecimal percentFull = getPercentFull();
        return percentFull.compareTo(BigDecimal.valueOf(thresholdPercent)) < 0;
    }

    /**
     * Calculates the percentage of tank capacity currently filled.
     * @return Percentage filled (0-100)
     */
    public BigDecimal getPercentFull() {
        if (capacity == null || capacity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (currentQuantity == null) {
            return BigDecimal.ZERO;
        }
        return currentQuantity
                .divide(capacity, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Checks if the tank is currently empty (no active batch).
     * @return true if no batch is assigned
     */
    public boolean isEmpty() {
        return currentBatchId == null;
    }
}
