package com.TbInventory.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a unit of measurement (e.g., barrels, gallons, pounds).
 * Lookup table for volume and weight units.
 */
@Entity
@Table(name = "unit_type")
@Data
@NoArgsConstructor
public class UnitType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "Unit name is required")
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @NotBlank(message = "Abbreviation is required")
    @Column(nullable = false, length = 10)
    private String abbreviation;

    @NotNull(message = "Volume/weight type must be specified")
    @Column(name = "is_volume", nullable = false)
    private Boolean isVolume;

    /**
     * Constructor for creating a new unit type.
     * Front end should ensure isVolume is provided; defaults to true if null.
     */
    public UnitType(String name, String abbreviation, Boolean isVolume) {
        this.name = name;
        this.abbreviation = abbreviation;
        this.isVolume = isVolume != null ? isVolume : true;
    }
}
