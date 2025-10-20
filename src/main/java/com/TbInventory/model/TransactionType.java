package com.TbInventory.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a type of transaction (e.g., Yeast Addition, Transfer Out, Waste).
 * Lookup table for transaction types.
 */
@Entity
@Table(name = "transaction_type")
@Data
@NoArgsConstructor
public class TransactionType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "Transaction type name is required")
    @Column(name = "type_name", nullable = false, unique = true, length = 50)
    private String typeName;

    @Column(length = 255)
    private String description;

    @NotNull(message = "Unit is required")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "unit_id", nullable = false)
    private UnitType unit;

    /**
     * Indicates whether this transaction type affects the tank's current quantity.
     * Frontend should default this to true for user convenience.
     */
    @NotNull(message = "Must specify if transaction affects tank quantity")
    @Column(name = "affects_tank_quantity", nullable = false)
    private Boolean affectsTankQuantity;

    /**
     * Constructor for creating a new transaction type.
     * Defaults affectsTankQuantity to true if not specified.
     */
    public TransactionType(String typeName, String description, UnitType unit, Boolean affectsTankQuantity) {
        this.typeName = typeName;
        this.description = description;
        this.unit = unit;
        this.affectsTankQuantity = affectsTankQuantity != null ? affectsTankQuantity : true;
    }
}
