package com.TbInventory.repository;

import com.TbInventory.model.UnitType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for UnitType entities.
 * Provides basic CRUD operations via JpaRepository.
 */
@Repository
public interface UnitTypeRepository extends JpaRepository<UnitType, Integer> {

    /**
     * Find all volume units (for tank capacity dropdowns).
     * Used in frontend to show only volume units when creating/editing tanks.
     * @return List of unit types where isVolume = true
     */
    List<UnitType> findByIsVolumeTrue();

    /**
     * Find unit type by abbreviation (e.g., "bbls", "g").
     * Used in tests to find or create reference data.
     * @param abbreviation Unit abbreviation
     * @return Optional containing unit type if found
     */
    Optional<UnitType> findByAbbreviation(String abbreviation);
}
