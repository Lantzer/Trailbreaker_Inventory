package com.TbInventory.service;

import com.TbInventory.model.Item;
import com.TbInventory.repository.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for inventory management.
 * Contains business logic for managing items.
 */
@Service
@Transactional
public class InventoryService {

    private final ItemRepository itemRepository;

    public InventoryService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /**
     * Get all items in inventory.
     */
    @Transactional(readOnly = true)
    public List<Item> getAllItems() {
        return itemRepository.findAll();
    }

    /**
     * Find an item by ID.
     * @throws ItemNotFoundException if item doesn't exist
     */
    @Transactional(readOnly = true)
    public Item findById(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ItemNotFoundException("Item not found with id: " + id));
    }

    /**
     * Find items with stock below the specified threshold.
     */
    @Transactional(readOnly = true)
    public List<Item> findLowStockItems(int threshold) {
        if (threshold < 0) {
            throw new IllegalArgumentException("Threshold must be non-negative");
        }
        return itemRepository.findByQuantityLessThan(threshold);
    }

    /**
     * Create a new item in inventory.
     * @throws ItemAlreadyExistsException if item with same name exists
     */
    public Item createItem(String name, Integer quantity) {
        if (itemRepository.existsByNameIgnoreCase(name)) {
            throw new ItemAlreadyExistsException("Item already exists with name: " + name);
        }
        Item item = new Item(name, quantity);
        return itemRepository.save(item);
    }

    /**
     * Update stock quantity for an item.
     */
    public Item updateStock(Long id, Integer newQuantity) {
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        Item item = findById(id);
        item.setQuantity(newQuantity);
        return itemRepository.save(item);
    }

    /**
     * Add stock to an existing item.
     */
    public Item addStock(Long id, int amount) {
        Item item = findById(id);
        item.addStock(amount);
        return itemRepository.save(item);
    }

    /**
     * Remove stock from an existing item.
     */
    public Item removeStock(Long id, int amount) {
        Item item = findById(id);
        item.removeStock(amount);
        return itemRepository.save(item);
    }

    /**
     * Delete an item from inventory.
     */
    public void deleteItem(Long id) {
        if (!itemRepository.existsById(id)) {
            throw new ItemNotFoundException("Cannot delete - item not found with id: " + id);
        }
        itemRepository.deleteById(id);
    }

    /**
     * Custom exception for item not found scenarios.
     */
    public static class ItemNotFoundException extends RuntimeException {
        public ItemNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Custom exception for duplicate item scenarios.
     */
    public static class ItemAlreadyExistsException extends RuntimeException {
        public ItemAlreadyExistsException(String message) {
            super(message);
        }
    }
}
