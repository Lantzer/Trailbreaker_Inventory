package com.TbInventory.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an inventory item in the system.
 */
@Entity
@Table(name = "items")
@Data
@NoArgsConstructor
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Item name is required")
    @Column(nullable = false)
    private String name;

    @Min(value = 0, message = "Quantity cannot be negative")
    @Column(nullable = false)
    private Integer quantity;

    @Column(length = 500)
    private String description;

    public Item(String name, Integer quantity) {
        this.name = name;
        this.quantity = quantity != null ? quantity : 0;
    }

    /**
     * Checks if the item is low in stock (quantity below threshold).
     */
    public boolean isLowStock(int threshold) {
        if (this.quantity == null) {
            return true; // Null quantity is considered low stock
        }
        return this.quantity < threshold;
    }

    /**
     * Adds quantity to the current stock.
     */
    public void addStock(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Cannot add negative stock");
        }
        if (this.quantity == null) {
            this.quantity = 0;
        }
        this.quantity += amount;
    }

    /**
     * Removes quantity from the current stock.
     */
    public void removeStock(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Cannot remove negative stock");
        }
        if (this.quantity == null) {
            this.quantity = 0;
        }
        if (this.quantity < amount) {
            throw new IllegalStateException("Insufficient stock available");
        }
        this.quantity -= amount;
    }
}
