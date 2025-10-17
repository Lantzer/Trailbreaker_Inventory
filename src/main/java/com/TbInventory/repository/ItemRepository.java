package com.TbInventory.repository;

import com.TbInventory.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for Item entity.
 * Spring Data JPA automatically provides implementation.
 */
@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {

    /**
     * Find items with quantity below the specified threshold.
     */
    List<Item> findByQuantityLessThan(Integer threshold);

    /**
     * Find item by name (case-insensitive).
     */
    @Query("SELECT i FROM Item i WHERE LOWER(i.name) = LOWER(:name)")
    Item findByNameIgnoreCase(@Param("name") String name);

    /**
     * Check if an item with the given name exists.
     */
    boolean existsByNameIgnoreCase(String name);
}
