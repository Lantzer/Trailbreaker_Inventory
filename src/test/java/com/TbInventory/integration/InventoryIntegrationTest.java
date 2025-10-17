package com.TbInventory.integration;

import com.TbInventory.model.Item;
import com.TbInventory.repository.ItemRepository;
import com.TbInventory.service.InventoryService;
import com.example.test.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Inventory functionality.
 *
 * These tests:
 * - Start the full Spring context
 * - Use a REAL PostgreSQL database (via 'real' profile)
 * - Test multiple components working together
 * - Are slower than unit tests (database I/O)
 *
 * When to write integration tests:
 * - Testing JPA entity relationships
 * - Testing custom queries
 * - Testing transaction behavior
 * - End-to-end workflow validation
 *
 * Run with: mvn test -Dgroups=integration
 * Or: mvn test -P integration-tests
 *
 * NOTE: Requires PostgreSQL to be running at the configured URL.
 */
@IntegrationTest
@Transactional // Rollback after each test to keep DB clean
class InventoryIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ItemRepository itemRepository;

    @AfterEach
    void cleanup() {
        // Clean up test data (though @Transactional should rollback)
        itemRepository.deleteAll();
    }

    @Test
    void createAndRetrieveItem_WorksCorrectly() {
        // Act - Create item through service
        Item created = inventoryService.createItem("Integration Test Laptop", 15);

        // Assert - Verify it was saved with ID
        assertNotNull(created.getId());
        assertEquals("Integration Test Laptop", created.getName());
        assertEquals(15, created.getQuantity());

        // Act - Retrieve it from database
        Item retrieved = inventoryService.findById(created.getId());

        // Assert - Verify data persisted correctly
        assertEquals(created.getId(), retrieved.getId());
        assertEquals("Integration Test Laptop", retrieved.getName());
        assertEquals(15, retrieved.getQuantity());
    }

    @Test
    void findLowStockItems_QueriesDatabaseCorrectly() {
        // Arrange - Create test data
        inventoryService.createItem("High Stock Item", 100);
        inventoryService.createItem("Low Stock Item 1", 5);
        inventoryService.createItem("Low Stock Item 2", 3);
        inventoryService.createItem("Medium Stock Item", 20);

        // Act
        List<Item> lowStockItems = inventoryService.findLowStockItems(10);

        // Assert
        assertEquals(2, lowStockItems.size());
        assertTrue(lowStockItems.stream()
                .allMatch(item -> item.getQuantity() < 10));
        assertTrue(lowStockItems.stream()
                .anyMatch(item -> item.getName().equals("Low Stock Item 1")));
        assertTrue(lowStockItems.stream()
                .anyMatch(item -> item.getName().equals("Low Stock Item 2")));
    }

    @Test
    void updateStock_PersistsChanges() {
        // Arrange
        Item item = inventoryService.createItem("Update Test Item", 10);
        Long itemId = item.getId();

        // Act
        inventoryService.updateStock(itemId, 50);

        // Assert - Clear context and re-fetch to ensure it's from DB
        Item updated = inventoryService.findById(itemId);
        assertEquals(50, updated.getQuantity());
    }

    @Test
    void addStock_PersistsCorrectly() {
        // Arrange
        Item item = inventoryService.createItem("Stock Addition Test", 10);

        // Act
        inventoryService.addStock(item.getId(), 15);

        // Assert
        Item updated = inventoryService.findById(item.getId());
        assertEquals(25, updated.getQuantity()); // 10 + 15
    }

    @Test
    void removeStock_PersistsCorrectly() {
        // Arrange
        Item item = inventoryService.createItem("Stock Removal Test", 50);

        // Act
        inventoryService.removeStock(item.getId(), 20);

        // Assert
        Item updated = inventoryService.findById(item.getId());
        assertEquals(30, updated.getQuantity()); // 50 - 20
    }

    @Test
    void deleteItem_RemovesFromDatabase() {
        // Arrange
        Item item = inventoryService.createItem("Delete Test Item", 10);
        Long itemId = item.getId();

        // Act
        inventoryService.deleteItem(itemId);

        // Assert
        assertFalse(itemRepository.existsById(itemId));
        assertThrows(InventoryService.ItemNotFoundException.class,
                () -> inventoryService.findById(itemId));
    }

    @Test
    void duplicateItemName_ThrowsException() {
        // Arrange
        inventoryService.createItem("Duplicate Test", 10);

        // Act & Assert
        assertThrows(InventoryService.ItemAlreadyExistsException.class,
                () -> inventoryService.createItem("Duplicate Test", 20));
    }

    @Test
    void repositoryCustomQuery_FindsByNameCaseInsensitive() {
        // Arrange
        Item item = inventoryService.createItem("Test Laptop", 10);

        // Act - Test case-insensitive search
        Item found = itemRepository.findByNameIgnoreCase("test laptop");

        // Assert
        assertNotNull(found);
        assertEquals(item.getId(), found.getId());
        assertEquals("Test Laptop", found.getName());
    }

    @Test
    void transactionRollback_OnException() {
        // Arrange
        Item item = inventoryService.createItem("Rollback Test", 10);

        // Act & Assert - Try to remove more stock than available
        assertThrows(IllegalStateException.class,
                () -> inventoryService.removeStock(item.getId(), 100));

        // Verify original quantity unchanged (transaction rolled back)
        Item unchanged = inventoryService.findById(item.getId());
        assertEquals(10, unchanged.getQuantity());
    }
}
