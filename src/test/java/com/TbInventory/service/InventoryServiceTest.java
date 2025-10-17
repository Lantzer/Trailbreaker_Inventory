package com.TbInventory.service;

import com.TbInventory.model.Item;
import com.TbInventory.repository.ItemRepository;
import com.TbInventory.service.InventoryService.ItemNotFoundException;
import com.TbInventory.service.InventoryService.ItemAlreadyExistsException;
import com.example.test.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for InventoryService.
 *
 * These tests:
 * - Use Mockito to mock the ItemRepository dependency
 * - Don't start Spring context (very fast)
 * - Don't hit the database
 * - Focus on testing business logic in isolation
 *
 * This is the ideal pattern for service layer testing:
 * - Tests run in milliseconds
 * - Easy to set up different scenarios
 * - Clear what's being tested (just the service logic)
 */
@UnitTest
class InventoryServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Item testItem;

    @BeforeEach
    void setUp() {
        testItem = new Item("Test Laptop", 10);
        testItem.setId(1L);
    }

    @Test
    void getAllItems_ReturnsListFromRepository() {
        // Arrange
        List<Item> expectedItems = Arrays.asList(
                new Item("Laptop", 10),
                new Item("Mouse", 50)
        );
        when(itemRepository.findAll()).thenReturn(expectedItems);

        // Act
        List<Item> actualItems = inventoryService.getAllItems();

        // Assert
        assertEquals(2, actualItems.size());
        assertEquals(expectedItems, actualItems);
        verify(itemRepository, times(1)).findAll();
    }

    @Test
    void findById_WhenExists_ReturnsItem() {
        // Arrange
        when(itemRepository.findById(1L)).thenReturn(Optional.of(testItem));

        // Act
        Item found = inventoryService.findById(1L);

        // Assert
        assertNotNull(found);
        assertEquals("Test Laptop", found.getName());
        assertEquals(10, found.getQuantity());
        verify(itemRepository).findById(1L);
    }

    @Test
    void findById_WhenNotExists_ThrowsException() {
        // Arrange
        when(itemRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        ItemNotFoundException exception = assertThrows(ItemNotFoundException.class,
                () -> inventoryService.findById(999L));

        assertTrue(exception.getMessage().contains("999"));
        verify(itemRepository).findById(999L);
    }

    @Test
    void findLowStockItems_FiltersCorrectly() {
        // Arrange
        List<Item> lowStockItems = Arrays.asList(
                new Item("Keyboard", 2),
                new Item("Monitor", 5)
        );
        when(itemRepository.findByQuantityLessThan(10)).thenReturn(lowStockItems);

        // Act
        List<Item> result = inventoryService.findLowStockItems(10);

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(item -> item.getQuantity() < 10));
        verify(itemRepository).findByQuantityLessThan(10);
    }

    @Test
    void findLowStockItems_WithNegativeThreshold_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.findLowStockItems(-1));

        // Verify repository was never called
        verify(itemRepository, never()).findByQuantityLessThan(anyInt());
    }

    @Test
    void createItem_WhenNameDoesNotExist_SavesAndReturnsItem() {
        // Arrange
        Item newItem = new Item("New Laptop", 15);
        when(itemRepository.existsByNameIgnoreCase("New Laptop")).thenReturn(false);
        when(itemRepository.save(any(Item.class))).thenReturn(newItem);

        // Act
        Item created = inventoryService.createItem("New Laptop", 15);

        // Assert
        assertNotNull(created);
        assertEquals("New Laptop", created.getName());
        assertEquals(15, created.getQuantity());
        verify(itemRepository).existsByNameIgnoreCase("New Laptop");
        verify(itemRepository).save(any(Item.class));
    }

    @Test
    void createItem_WhenNameExists_ThrowsException() {
        // Arrange
        when(itemRepository.existsByNameIgnoreCase("Existing Item")).thenReturn(true);

        // Act & Assert
        assertThrows(ItemAlreadyExistsException.class,
                () -> inventoryService.createItem("Existing Item", 10));

        verify(itemRepository).existsByNameIgnoreCase("Existing Item");
        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    void updateStock_UpdatesAndSavesItem() {
        // Arrange
        when(itemRepository.findById(1L)).thenReturn(Optional.of(testItem));
        when(itemRepository.save(any(Item.class))).thenReturn(testItem);

        // Act
        Item updated = inventoryService.updateStock(1L, 25);

        // Assert
        assertEquals(25, updated.getQuantity());
        verify(itemRepository).findById(1L);
        verify(itemRepository).save(testItem);
    }

    @Test
    void updateStock_WithNegativeQuantity_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.updateStock(1L, -5));

        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    void addStock_IncreasesQuantityCorrectly() {
        // Arrange
        when(itemRepository.findById(1L)).thenReturn(Optional.of(testItem));
        when(itemRepository.save(any(Item.class))).thenReturn(testItem);

        // Act
        inventoryService.addStock(1L, 5);

        // Assert
        assertEquals(15, testItem.getQuantity()); // Original 10 + 5
        verify(itemRepository).save(testItem);
    }

    @Test
    void removeStock_DecreasesQuantityCorrectly() {
        // Arrange
        when(itemRepository.findById(1L)).thenReturn(Optional.of(testItem));
        when(itemRepository.save(any(Item.class))).thenReturn(testItem);

        // Act
        inventoryService.removeStock(1L, 5);

        // Assert
        assertEquals(5, testItem.getQuantity()); // Original 10 - 5
        verify(itemRepository).save(testItem);
    }

    @Test
    void removeStock_WhenInsufficientStock_ThrowsException() {
        // Arrange
        when(itemRepository.findById(1L)).thenReturn(Optional.of(testItem));

        // Act & Assert
        assertThrows(IllegalStateException.class,
                () -> inventoryService.removeStock(1L, 100));

        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    void deleteItem_WhenExists_DeletesSuccessfully() {
        // Arrange
        when(itemRepository.existsById(1L)).thenReturn(true);

        // Act
        inventoryService.deleteItem(1L);

        // Assert
        verify(itemRepository).existsById(1L);
        verify(itemRepository).deleteById(1L);
    }

    @Test
    void deleteItem_WhenNotExists_ThrowsException() {
        // Arrange
        when(itemRepository.existsById(999L)).thenReturn(false);

        // Act & Assert
        assertThrows(ItemNotFoundException.class,
                () -> inventoryService.deleteItem(999L));

        verify(itemRepository).existsById(999L);
        verify(itemRepository, never()).deleteById(anyLong());
    }
}
