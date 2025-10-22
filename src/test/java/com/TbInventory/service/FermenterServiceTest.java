package com.TbInventory.service;

import com.TbInventory.model.*;
import com.TbInventory.repository.*;
import com.example.test.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for FermenterService.
 *
 * ========================================
 * HOW TO RUN THESE TESTS:
 * ========================================
 * Run all tests in this file:
 *   mvn test -Dtest=FermenterServiceTest
 *
 * Run a single test:
 *   mvn test -Dtest=FermenterServiceTest#loadTransactionTypes_LoadsAllTypesIntoCache
 *
 * Run all unit tests (any test with @UnitTest annotation):
 *   mvn test -Dgroups=unit
 *
 * Run default tests (includes unit tests):
 *   mvn test
 * ========================================
 *
 * These tests:
 * - Use Mockito to mock repository dependencies
 * - Don't start Spring context (very fast)
 * - Don't hit the database
 * - Focus on testing business logic in isolation
 *
 * Test coverage (27 tests):
 * - Transaction type caching (1 test)
 * - Tank CRUD operations and validation (10 tests)
 * - Batch creation and lifecycle (5 tests)
 * - Transaction processing with quantity updates (9 tests)
 * - Batch completion (2 tests)
 */
@UnitTest
class FermenterServiceTest {

    @Mock
    private FermTankRepository tankRepository;

    @Mock
    private FermBatchRepository batchRepository;

    @Mock
    private FermTransactionRepository transactionRepository;

    @Mock
    private TransactionTypeRepository transactionTypeRepository;

    @Mock
    private UnitTypeRepository unitTypeRepository;

    @InjectMocks
    private FermenterService fermenterService;

    // Test data
    private UnitType volumeUnit;
    private UnitType weightUnit;
    private FermTank testTank;
    private FermBatch testBatch;
    private TransactionType transferInType;
    private TransactionType transferOutType;
    private TransactionType yeastType;
    private TransactionType lysozymeType;
    private TransactionType wasteType;

    @BeforeEach
    void setUp() {
        // Create test units
        volumeUnit = new UnitType("Barrels", "bbls", true);
        volumeUnit.setId(1);

        weightUnit = new UnitType("Grams", "g", false);
        weightUnit.setId(2);

        // Create test transaction types
        transferInType = new TransactionType("Transfer In", "Transfer from previous tank", volumeUnit, true);
        transferInType.setId(1);

        transferOutType = new TransactionType("Transfer Out", "Transfer to next tank", volumeUnit, true);
        transferOutType.setId(2);

        yeastType = new TransactionType("Yeast Addition", "Add yeast to batch", weightUnit, false);
        yeastType.setId(3);

        lysozymeType = new TransactionType("Lysozyme Addition", "Add lysozyme to batch", weightUnit, false);
        lysozymeType.setId(4);

        wasteType = new TransactionType("Waste", "Waste/drain from tank", volumeUnit, true);
        wasteType.setId(5);

        // Create test tank
        testTank = new FermTank("FV-1", new BigDecimal("100.00"), volumeUnit);
        testTank.setId(1);
        testTank.setCurrentQuantity(new BigDecimal("50.00"));

        // Create test batch
        testBatch = new FermBatch(1, "Test IPA", LocalDateTime.now());
        testBatch.setId(1);
    }

    // ==================== Transaction Type Cache Tests ====================

    @Test
    void loadTransactionTypes_LoadsAllTypesIntoCache() {
        // Arrange - Create multiple transaction types to verify cache stores them by ID
        List<TransactionType> types = Arrays.asList(transferInType, transferOutType, yeastType, lysozymeType, wasteType);
        when(transactionTypeRepository.findAll()).thenReturn(types);

        // Act
        fermenterService.loadTransactionTypes();

        // Assert
        verify(transactionTypeRepository, times(1)).findAll();

        // ENHANCED: Verify cache actually works by using it in subsequent operations
        // The cache is private, but we can verify it's populated by checking that
        // methods using the cache can find all the types by ID

        // Test 1: Verify we can use type ID=1 (Transfer In) from cache
        when(batchRepository.findById(1)).thenReturn(Optional.of(testBatch));
        when(transactionRepository.save(any(FermTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tankRepository.findById(1)).thenReturn(Optional.of(testTank));
        when(tankRepository.save(any(FermTank.class))).thenAnswer(inv -> inv.getArgument(0));

        // This should work because type ID=1 is in cache
        assertDoesNotThrow(() ->
            fermenterService.addTransaction(1, 1, new BigDecimal("10"), LocalDateTime.now(), 100, "Test")
        );

        // Test 2: Verify we can use type ID=3 (Yeast) from cache
        assertDoesNotThrow(() ->
            fermenterService.addTransaction(1, 3, new BigDecimal("50"), LocalDateTime.now(), 100, "Yeast")
        );

        // Test 3: Verify type NOT in cache throws exception
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            fermenterService.addTransaction(1, 999, new BigDecimal("10"), LocalDateTime.now(), 100, "Invalid")
        );
        assertTrue(exception.getMessage().contains("Invalid transaction type"));

        // This proves the cache:
        // 1. Was populated from findAll()
        // 2. Stores types by their ID
        // 3. Can look up types that exist
        // 4. Returns null for types that don't exist
    }

    // ==================== Tank Management Tests ====================

    @Test
    void getAllTanks_ReturnsAllTanks() {
        // Arrange
        List<FermTank> expectedTanks = Arrays.asList(testTank, new FermTank("FV-2", new BigDecimal("80"), volumeUnit));
        when(tankRepository.findAll()).thenReturn(expectedTanks);

        // Act
        List<FermTank> actualTanks = fermenterService.getAllTanks();

        // Assert
        assertEquals(2, actualTanks.size());
        assertEquals(expectedTanks, actualTanks);
        verify(tankRepository, times(1)).findAll();
    }

    @Test
    void getTankById_WhenExists_ReturnsTank() {
        // Arrange
        when(tankRepository.findById(1)).thenReturn(Optional.of(testTank));

        // Act
        FermTank found = fermenterService.getTankById(1);

        // Assert
        assertNotNull(found);
        assertEquals("FV-1", found.getLabel());
        assertEquals(new BigDecimal("100.00"), found.getCapacity());
        verify(tankRepository).findById(1);
    }

    @Test
    void getTankById_WhenNotExists_ThrowsException() {
        // Arrange
        when(tankRepository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fermenterService.getTankById(999));

        assertTrue(exception.getMessage().contains("Tank not found"));
        assertTrue(exception.getMessage().contains("999"));
        verify(tankRepository).findById(999);
    }

    @Test
    void getTankByLabel_WhenExists_ReturnsTank() {
        // Arrange
        when(tankRepository.findByLabel("FV-1")).thenReturn(Optional.of(testTank));

        // Act
        FermTank found = fermenterService.getTankByLabel("FV-1");

        // Assert
        assertNotNull(found);
        assertEquals("FV-1", found.getLabel());
        verify(tankRepository).findByLabel("FV-1");
    }

    @Test
    void getTankByLabel_WhenNotExists_ThrowsException() {
        // Arrange
        when(tankRepository.findByLabel("NONEXISTENT")).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fermenterService.getTankByLabel("NONEXISTENT"));

        assertTrue(exception.getMessage().contains("Tank not found"));
        assertTrue(exception.getMessage().contains("NONEXISTENT"));
        verify(tankRepository).findByLabel("NONEXISTENT");
    }

    @Test
    void createTank_WithValidData_CreatesTank() {
        // Arrange
        when(tankRepository.existsByLabel("FV-3")).thenReturn(false);
        when(unitTypeRepository.findById(1)).thenReturn(Optional.of(volumeUnit));
        when(tankRepository.save(any(FermTank.class))).thenAnswer(invocation -> {
            FermTank tank = invocation.getArgument(0);
            tank.setId(3);
            return tank;
        });

        // Act
        FermTank created = fermenterService.createTank("FV-3", new BigDecimal("120"), 1);

        // Assert
        assertNotNull(created);
        assertEquals("FV-3", created.getLabel());
        assertEquals(new BigDecimal("120"), created.getCapacity());
        assertEquals(volumeUnit, created.getCapacityUnit());
        verify(tankRepository).existsByLabel("FV-3");
        verify(unitTypeRepository).findById(1);
        verify(tankRepository).save(any(FermTank.class));
    }

    @Test
    void createTank_WithDuplicateLabel_ThrowsException() {
        // Arrange
        when(tankRepository.existsByLabel("FV-1")).thenReturn(true);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fermenterService.createTank("FV-1", new BigDecimal("100"), 1));

        assertTrue(exception.getMessage().contains("already exists"));
        assertTrue(exception.getMessage().contains("FV-1"));
        verify(tankRepository).existsByLabel("FV-1");
        verify(tankRepository, never()).save(any(FermTank.class));
    }

    @Test
    void createTank_WithWeightUnit_ThrowsException() {
        // Arrange
        when(tankRepository.existsByLabel("FV-3")).thenReturn(false);
        when(unitTypeRepository.findById(2)).thenReturn(Optional.of(weightUnit));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fermenterService.createTank("FV-3", new BigDecimal("100"), 2));

        assertTrue(exception.getMessage().contains("volume units"));
        assertTrue(exception.getMessage().contains("not weight units"));
        verify(tankRepository, never()).save(any(FermTank.class));
    }

    @Test
    void getAvailableTanks_ReturnsEmptyTanks() {
        // Arrange
        FermTank emptyTank = new FermTank("FV-2", new BigDecimal("80"), volumeUnit);
        emptyTank.setCurrentBatchId(null);
        when(tankRepository.findByCurrentBatchIdIsNull()).thenReturn(Arrays.asList(emptyTank));

        // Act
        List<FermTank> availableTanks = fermenterService.getAvailableTanks();

        // Assert
        assertEquals(1, availableTanks.size());
        assertTrue(availableTanks.get(0).isEmpty());
        verify(tankRepository).findByCurrentBatchIdIsNull();
    }

    @Test
    void getActiveTanks_ReturnsTanksWithBatches() {
        // Arrange
        testTank.setCurrentBatchId(1);
        when(tankRepository.findByCurrentBatchIdIsNotNull()).thenReturn(Arrays.asList(testTank));

        // Act
        List<FermTank> activeTanks = fermenterService.getActiveTanks();

        // Assert
        assertEquals(1, activeTanks.size());
        assertFalse(activeTanks.get(0).isEmpty());
        assertEquals(1, activeTanks.get(0).getCurrentBatchId());
        verify(tankRepository).findByCurrentBatchIdIsNotNull();
    }

    // ==================== Batch Management Tests ====================

    @Test
    void startBatch_CreatesNewBatchAndTransaction_UpdatesTank() {
        // Arrange
        testTank.setCurrentBatchId(null); // Tank is empty
        testTank.setCurrentQuantity(BigDecimal.ZERO);

        when(tankRepository.findById(1)).thenReturn(Optional.of(testTank));
        when(batchRepository.save(any(FermBatch.class))).thenAnswer(invocation -> {
            FermBatch batch = invocation.getArgument(0);
            batch.setId(1);
            return batch;
        });
        when(transactionRepository.save(any(FermTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tankRepository.save(any(FermTank.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Setup transaction type cache
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(transferInType));
        fermenterService.loadTransactionTypes();

        LocalDateTime startDate = LocalDateTime.now();

        // Act
        FermBatch created = fermenterService.startBatch(1, "New IPA", startDate, 1, new BigDecimal("30.00"), 100, "Initial transfer");

        // Assert
        assertNotNull(created);
        assertEquals("New IPA", created.getBatchName());
        assertEquals(startDate, created.getStartDate());

        // Verify batch was saved
        verify(batchRepository).save(any(FermBatch.class));

        // Verify transaction was created
        verify(transactionRepository).save(any(FermTransaction.class));

        // Verify tank was updated twice (once to set batch ID, once to set quantity)
        verify(tankRepository, atLeast(1)).save(testTank);
    }

    @Test
    void startBatch_WhenTankHasActiveBatch_ThrowsException() {
        // Arrange
        testTank.setCurrentBatchId(999); // Tank already has a batch
        when(tankRepository.findById(1)).thenReturn(Optional.of(testTank));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fermenterService.startBatch(1, "New IPA", LocalDateTime.now(), 1, new BigDecimal("30"), 100, "Notes"));

        assertTrue(exception.getMessage().contains("already has an active batch"));
        verify(batchRepository, never()).save(any(FermBatch.class));
        verify(transactionRepository, never()).save(any(FermTransaction.class));
    }

    @Test
    void startBatch_WithInvalidTransactionType_ThrowsException() {
        // Arrange
        testTank.setCurrentBatchId(null);
        when(tankRepository.findById(1)).thenReturn(Optional.of(testTank));
        when(batchRepository.save(any(FermBatch.class))).thenAnswer(invocation -> {
            FermBatch batch = invocation.getArgument(0);
            batch.setId(1);
            return batch;
        });

        // Setup empty transaction type cache (invalid type ID 999 won't be found)
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(transferInType));
        fermenterService.loadTransactionTypes();

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fermenterService.startBatch(1, "New IPA", LocalDateTime.now(), 999, new BigDecimal("30"), 100, "Notes"));

        assertTrue(exception.getMessage().contains("Invalid transaction type"));
        verify(transactionRepository, never()).save(any(FermTransaction.class));
    }

    @Test
    void getBatchById_WhenExists_ReturnsBatch() {
        // Arrange
        when(batchRepository.findById(1)).thenReturn(Optional.of(testBatch));

        // Act
        FermBatch found = fermenterService.getBatchById(1);

        // Assert
        assertNotNull(found);
        assertEquals("Test IPA", found.getBatchName());
        verify(batchRepository).findById(1);
    }

    @Test
    void getBatchById_WhenNotExists_ThrowsException() {
        // Arrange
        when(batchRepository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fermenterService.getBatchById(999));

        assertTrue(exception.getMessage().contains("Batch not found"));
        assertTrue(exception.getMessage().contains("999"));
        verify(batchRepository).findById(999);
    }

    // ==================== Transaction Management Tests ====================

    @Test
    void addTransaction_WithAdditionType_IncreasesQuantity() {
        // Arrange
        testTank.setCurrentQuantity(new BigDecimal("50.00"));
        when(batchRepository.findById(1)).thenReturn(Optional.of(testBatch));
        when(tankRepository.findById(1)).thenReturn(Optional.of(testTank));
        when(transactionRepository.save(any(FermTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tankRepository.save(any(FermTank.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Setup transaction type cache
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(transferInType));
        fermenterService.loadTransactionTypes();

        // Act
        fermenterService.addTransaction(1, 1, new BigDecimal("20.00"), LocalDateTime.now(), 100, "Adding more");

        // Assert
        assertEquals(new BigDecimal("70.00"), testTank.getCurrentQuantity());
        verify(transactionRepository).save(any(FermTransaction.class));
        verify(tankRepository).save(testTank);
    }

    @Test
    void addTransaction_WithRemovalType_DecreasesQuantity() {
        // Arrange
        testTank.setCurrentQuantity(new BigDecimal("50.00"));
        when(batchRepository.findById(1)).thenReturn(Optional.of(testBatch));
        when(tankRepository.findById(1)).thenReturn(Optional.of(testTank));
        when(transactionRepository.save(any(FermTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tankRepository.save(any(FermTank.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Setup transaction type cache with "Transfer Out" type
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(transferOutType));
        fermenterService.loadTransactionTypes();

        // Act
        fermenterService.addTransaction(1, 2, new BigDecimal("10.00"), LocalDateTime.now(), 100, "Transferring out");

        // Assert
        assertEquals(new BigDecimal("40.00"), testTank.getCurrentQuantity());
        verify(transactionRepository).save(any(FermTransaction.class));
        verify(tankRepository).save(testTank);
    }

    @Test
    void addTransaction_WithYeastType_UpdatesYeastDate() {
        // Arrange
        testBatch.setYeastDate(null); // No yeast added yet
        when(batchRepository.findById(1)).thenReturn(Optional.of(testBatch));
        when(transactionRepository.save(any(FermTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(batchRepository.save(any(FermBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Setup transaction type cache
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(yeastType));
        fermenterService.loadTransactionTypes();

        LocalDateTime transactionDate = LocalDateTime.now();

        // Act
        fermenterService.addTransaction(1, 3, new BigDecimal("50.00"), transactionDate, 100, "Adding yeast");

        // Assert
        assertNotNull(testBatch.getYeastDate());
        assertEquals(transactionDate, testBatch.getYeastDate());
        verify(batchRepository).save(testBatch);
    }

    @Test
    void addTransaction_WithLysozymeType_UpdatesLysozymeDate() {
        // Arrange
        testBatch.setLysozymeDate(null); // No lysozyme added yet
        when(batchRepository.findById(1)).thenReturn(Optional.of(testBatch));
        when(transactionRepository.save(any(FermTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(batchRepository.save(any(FermBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Setup transaction type cache
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(lysozymeType));
        fermenterService.loadTransactionTypes();

        LocalDateTime transactionDate = LocalDateTime.now();

        // Act
        fermenterService.addTransaction(1, 4, new BigDecimal("25.00"), transactionDate, 100, "Adding lysozyme");

        // Assert
        assertNotNull(testBatch.getLysozymeDate());
        assertEquals(transactionDate, testBatch.getLysozymeDate());
        verify(batchRepository).save(testBatch);
    }

    @Test
    void addTransaction_WithSubsequentYeast_DoesNotOverrideDate() {
        // Arrange
        LocalDateTime originalYeastDate = LocalDateTime.now().minusDays(5);
        testBatch.setYeastDate(originalYeastDate); // Yeast already added
        when(batchRepository.findById(1)).thenReturn(Optional.of(testBatch));
        when(transactionRepository.save(any(FermTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Setup transaction type cache
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(yeastType));
        fermenterService.loadTransactionTypes();

        // Act
        fermenterService.addTransaction(1, 3, new BigDecimal("10.00"), LocalDateTime.now(), 100, "Adding more yeast");

        // Assert - yeast date should NOT be updated
        assertEquals(originalYeastDate, testBatch.getYeastDate());
        verify(batchRepository, never()).save(testBatch);
    }

    @Test
    void addTransaction_ToCompletedBatch_ThrowsException() {
        // Arrange
        testBatch.setCompletionDate(LocalDateTime.now().minusDays(1)); // Batch is completed
        when(batchRepository.findById(1)).thenReturn(Optional.of(testBatch));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fermenterService.addTransaction(1, 1, new BigDecimal("10.00"), LocalDateTime.now(), 100, "Notes"));

        assertTrue(exception.getMessage().contains("Cannot add transaction to completed batch"));
        verify(transactionRepository, never()).save(any(FermTransaction.class));
    }

    @Test
    void addTransaction_WithInvalidType_ThrowsException() {
        // Arrange
        when(batchRepository.findById(1)).thenReturn(Optional.of(testBatch));

        // Setup empty transaction type cache (invalid type ID 999 won't be found)
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(transferInType));
        fermenterService.loadTransactionTypes();

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fermenterService.addTransaction(1, 999, new BigDecimal("10.00"), LocalDateTime.now(), 100, "Notes"));

        assertTrue(exception.getMessage().contains("Invalid transaction type"));
        verify(transactionRepository, never()).save(any(FermTransaction.class));
    }

    @Test
    void addTransaction_CausingNegativeQuantity_ThrowsException() {
        // Arrange
        testTank.setCurrentQuantity(new BigDecimal("10.00")); // Only 10 bbls in tank
        when(batchRepository.findById(1)).thenReturn(Optional.of(testBatch));
        when(tankRepository.findById(1)).thenReturn(Optional.of(testTank));
        when(transactionRepository.save(any(FermTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Setup transaction type cache with waste type (removes quantity)
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(wasteType));
        fermenterService.loadTransactionTypes();

        // Act & Assert - trying to remove 50 bbls when only 10 available
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fermenterService.addTransaction(1, 5, new BigDecimal("50.00"), LocalDateTime.now(), 100, "Waste"));

        assertTrue(exception.getMessage().contains("Insufficient quantity"));
        verify(tankRepository, never()).save(testTank);
    }

    @Test
    void addTransaction_ExceedingCapacity_ThrowsException() {
        // Arrange
        testTank.setCurrentQuantity(new BigDecimal("90.00")); // Tank is at 90/100 capacity
        testTank.setCapacity(new BigDecimal("100.00"));
        when(batchRepository.findById(1)).thenReturn(Optional.of(testBatch));
        when(tankRepository.findById(1)).thenReturn(Optional.of(testTank));
        when(transactionRepository.save(any(FermTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Setup transaction type cache
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(transferInType));
        fermenterService.loadTransactionTypes();

        // Act & Assert - trying to add 20 bbls to a tank at 90/100
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fermenterService.addTransaction(1, 1, new BigDecimal("20.00"), LocalDateTime.now(), 100, "Transfer"));

        assertTrue(exception.getMessage().contains("exceeds tank capacity"));
        verify(tankRepository, never()).save(testTank);
    }

    // ==================== Batch Completion Tests ====================

    @Test
    void completeBatch_MarksCompleteAndClearsTank() {
        // Arrange
        testTank.setCurrentBatchId(1);
        testTank.setCurrentQuantity(new BigDecimal("50.00"));
        when(batchRepository.findById(1)).thenReturn(Optional.of(testBatch));
        when(tankRepository.findById(1)).thenReturn(Optional.of(testTank));
        when(batchRepository.save(any(FermBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tankRepository.save(any(FermTank.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        FermBatch completed = fermenterService.completeBatch(1);

        // Assert
        assertNotNull(completed.getCompletionDate());
        assertNull(testTank.getCurrentBatchId());
        assertEquals(BigDecimal.ZERO, testTank.getCurrentQuantity());
        verify(batchRepository).save(testBatch);
        verify(tankRepository).save(testTank);
    }

    @Test
    void completeBatch_WhenAlreadyCompleted_ThrowsException() {
        // Arrange
        testBatch.setCompletionDate(LocalDateTime.now().minusDays(1)); // Already completed
        when(batchRepository.findById(1)).thenReturn(Optional.of(testBatch));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fermenterService.completeBatch(1));

        assertTrue(exception.getMessage().contains("already completed"));
        verify(batchRepository, never()).save(any(FermBatch.class));
        verify(tankRepository, never()).save(any(FermTank.class));
    }
}
