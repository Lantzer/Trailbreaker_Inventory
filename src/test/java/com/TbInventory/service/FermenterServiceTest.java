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
    private UnitType gallonsUnit;
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

        gallonsUnit = new UnitType("Gallons", "gal", true);
        gallonsUnit.setId(3);

        weightUnit = new UnitType("Grams", "g", false);
        weightUnit.setId(2);

        // Mock unit type lookups that the service uses (lenient because not all tests need it)
        lenient().when(unitTypeRepository.findByAbbreviation("gal")).thenReturn(Optional.of(gallonsUnit));

        // Create test transaction types (matching new IDs from data-dev.sql)
        transferInType = new TransactionType("Cider Addition", "Add apple cider to fermenter", volumeUnit, true, 1);
        transferInType.setId(1);

        yeastType = new TransactionType("Yeast Addition", "Pitch yeast to start fermentation", weightUnit, false, 0);
        yeastType.setId(2);

        lysozymeType = new TransactionType("Lysozyme Addition", "Add lysozyme enzyme", weightUnit, false, 0);
        lysozymeType.setId(3);

        transferOutType = new TransactionType("Transfer Out", "Transfer to bright tank", volumeUnit, true, -1);
        transferOutType.setId(4);

        wasteType = new TransactionType("Waste", "Trub/sediment removal", volumeUnit, true, -1);
        wasteType.setId(6);

        // Create test tank
        testTank = new FermTank("FV-1", new BigDecimal("100.00"));
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
        List<FermTank> expectedTanks = Arrays.asList(testTank, new FermTank("FV-2", new BigDecimal("80")));
        when(tankRepository.findByDeletedAtIsNull()).thenReturn(expectedTanks);

        // Act
        List<FermTank> actualTanks = fermenterService.getAllTanks();

        // Assert
        assertEquals(2, actualTanks.size());
        assertEquals(expectedTanks, actualTanks);
        verify(tankRepository, times(1)).findByDeletedAtIsNull();
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
        when(tankRepository.findByLabelAndDeletedAtIsNull("FV-1")).thenReturn(Optional.of(testTank));

        // Act
        FermTank found = fermenterService.getTankByLabel("FV-1");

        // Assert
        assertNotNull(found);
        assertEquals("FV-1", found.getLabel());
        verify(tankRepository).findByLabelAndDeletedAtIsNull("FV-1");
    }

    @Test
    void getTankByLabel_WhenNotExists_ThrowsException() {
        // Arrange
        when(tankRepository.findByLabelAndDeletedAtIsNull("NONEXISTENT")).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fermenterService.getTankByLabel("NONEXISTENT"));

        assertTrue(exception.getMessage().contains("Tank not found"));
        assertTrue(exception.getMessage().contains("NONEXISTENT"));
        verify(tankRepository).findByLabelAndDeletedAtIsNull("NONEXISTENT");
    }

    @Test
    void createTank_WithValidData_CreatesTank() {
        // Arrange
        when(tankRepository.existsByLabel("FV-3")).thenReturn(false);
        when(tankRepository.save(any(FermTank.class))).thenAnswer(invocation -> {
            FermTank tank = invocation.getArgument(0);
            tank.setId(3);
            return tank;
        });

        // Act - Note: capacityUnitId is ignored (capacity is always in gallons)
        FermTank created = fermenterService.createTank("FV-3", new BigDecimal("120"), 1);

        // Assert
        assertNotNull(created);
        assertEquals("FV-3", created.getLabel());
        assertEquals(new BigDecimal("120"), created.getCapacity());
        verify(tankRepository).existsByLabel("FV-3");
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

    // NOTE: Weight unit validation was removed from createTank()
    // Capacity is always in gallons now, so capacityUnitId parameter is ignored
    // This test has been removed since the validation no longer exists in the service

    @Test
    void getAvailableTanks_ReturnsEmptyTanks() {
        // Arrange
        FermTank emptyTank = new FermTank("FV-2", new BigDecimal("80"));
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

        LocalDateTime beforeStart = LocalDateTime.now().minusSeconds(1);

        // Act
        FermBatch created = fermenterService.startBatch(1, "New IPA", 1, new BigDecimal("30.00"), "Initial transfer");

        // Assert
        assertNotNull(created);
        assertEquals("New IPA", created.getBatchName());
        assertNotNull(created.getStartDate());
        assertTrue(created.getStartDate().isAfter(beforeStart), "Start date should be recent");

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
                () -> fermenterService.startBatch(1, "New IPA", 1, new BigDecimal("30"), "Notes"));

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
                () -> fermenterService.startBatch(1, "New IPA", 999, new BigDecimal("30"), "Notes"));

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
        Integer batchId = 1;
        Integer transactionTypeId = transferOutType.getId();
        BigDecimal quantity = new BigDecimal("10.00");
        LocalDateTime transactionDate = LocalDateTime.now();
        Integer userId = 100;
        String notes = "Transferring out";

        fermenterService.addTransaction(batchId, transactionTypeId, quantity, transactionDate, userId, notes);

        // Assert
        assertEquals(new BigDecimal("40.00"), testTank.getCurrentQuantity());
        verify(transactionRepository).save(any(FermTransaction.class));
        verify(tankRepository).save(testTank);
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
                () -> fermenterService.addTransaction(1, wasteType.getId(), new BigDecimal("50.00"), LocalDateTime.now(), 100, "Waste"));

        assertTrue(exception.getMessage().contains("Insufficient"));
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
        when(transactionRepository.save(any(FermTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Setup transaction type cache with waste type
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(wasteType));
        fermenterService.loadTransactionTypes();

        // Act
        FermBatch completed = fermenterService.completeBatch(1);

        // Assert
        assertNotNull(completed.getCompletionDate());
        assertNull(testTank.getCurrentBatchId());
        assertEquals(0, testTank.getCurrentQuantity().compareTo(BigDecimal.ZERO), "Tank should be empty");
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

    // ==================== Quantity Calculation Regression Tests ====================

    @Test
    void addTransaction_MultipleSequentialAdditions_IncreasesQuantityCorrectly() {
        // Arrange - Tank starts at 50, capacity 100
        Integer batchId = 1;
        Integer transactionTypeId = transferInType.getId();
        Integer userId = 100;

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(testBatch));
        when(tankRepository.findById(1)).thenReturn(Optional.of(testTank));
        when(transactionRepository.save(any(FermTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tankRepository.save(any(FermTank.class))).thenAnswer(inv -> inv.getArgument(0));

        // Setup transaction type cache
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(transferInType));
        fermenterService.loadTransactionTypes();

        // Act - Add 10, then 20, then 15 gallons
        BigDecimal firstAddition = new BigDecimal("10.00");
        BigDecimal secondAddition = new BigDecimal("20.00");
        BigDecimal thirdAddition = new BigDecimal("15.00");

        fermenterService.addTransaction(batchId, transactionTypeId, firstAddition, LocalDateTime.now(), userId, "First addition");
        fermenterService.addTransaction(batchId, transactionTypeId, secondAddition, LocalDateTime.now(), userId, "Second addition");
        fermenterService.addTransaction(batchId, transactionTypeId, thirdAddition, LocalDateTime.now(), userId, "Third addition");

        // Assert - Should be 50 + 10 + 20 + 15 = 95
        BigDecimal expectedQuantity = new BigDecimal("95.00");
        assertEquals(expectedQuantity, testTank.getCurrentQuantity());
        verify(transactionRepository, times(3)).save(any(FermTransaction.class));
    }

    @Test
    void addTransaction_MultipleSequentialRemovals_DecreasesQuantityCorrectly() {
        // Arrange - Tank starts at 100, set capacity to 100
        BigDecimal startingQuantity = new BigDecimal("100.00");
        testTank.setCurrentQuantity(startingQuantity);

        Integer batchId = 1;
        Integer transactionTypeId = transferOutType.getId();
        Integer userId = 100;

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(testBatch));
        when(tankRepository.findById(1)).thenReturn(Optional.of(testTank));
        when(transactionRepository.save(any(FermTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tankRepository.save(any(FermTank.class))).thenAnswer(inv -> inv.getArgument(0));

        // Setup transaction type cache
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(transferOutType));
        fermenterService.loadTransactionTypes();

        // Act - Remove 10, then 25, then 5 gallons
        BigDecimal firstRemoval = new BigDecimal("10.00");
        BigDecimal secondRemoval = new BigDecimal("25.00");
        BigDecimal thirdRemoval = new BigDecimal("5.00");

        fermenterService.addTransaction(batchId, transactionTypeId, firstRemoval, LocalDateTime.now(), userId, "First removal");
        fermenterService.addTransaction(batchId, transactionTypeId, secondRemoval, LocalDateTime.now(), userId, "Second removal");
        fermenterService.addTransaction(batchId, transactionTypeId, thirdRemoval, LocalDateTime.now(), userId, "Third removal");

        // Assert - Should be 100 - 10 - 25 - 5 = 60
        BigDecimal expectedQuantity = new BigDecimal("60.00");
        assertEquals(expectedQuantity, testTank.getCurrentQuantity());
        verify(transactionRepository, times(3)).save(any(FermTransaction.class));
    }

    @Test
    void addTransaction_MixOfAdditionsAndRemovals_CalculatesNetChangeCorrectly() {
        // Arrange - Tank starts at 50, capacity 100
        Integer batchId = 1;
        Integer transferInTypeId = transferInType.getId();
        Integer transferOutTypeId = transferOutType.getId();
        Integer userId = 100;

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(testBatch));
        when(tankRepository.findById(1)).thenReturn(Optional.of(testTank));
        when(transactionRepository.save(any(FermTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tankRepository.save(any(FermTank.class))).thenAnswer(inv -> inv.getArgument(0));

        // Setup transaction type cache
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(transferInType, transferOutType));
        fermenterService.loadTransactionTypes();

        // Act - Mix of additions and removals: +30, -20, +10, -5
        BigDecimal firstAddition = new BigDecimal("30.00");
        BigDecimal firstRemoval = new BigDecimal("20.00");
        BigDecimal secondAddition = new BigDecimal("10.00");
        BigDecimal secondRemoval = new BigDecimal("5.00");

        fermenterService.addTransaction(batchId, transferInTypeId, firstAddition, LocalDateTime.now(), userId, "Add 30");
        fermenterService.addTransaction(batchId, transferOutTypeId, firstRemoval, LocalDateTime.now(), userId, "Remove 20");
        fermenterService.addTransaction(batchId, transferInTypeId, secondAddition, LocalDateTime.now(), userId, "Add 10");
        fermenterService.addTransaction(batchId, transferOutTypeId, secondRemoval, LocalDateTime.now(), userId, "Remove 5");

        // Assert - Should be 50 + 30 - 20 + 10 - 5 = 65
        BigDecimal expectedQuantity = new BigDecimal("65.00");
        assertEquals(expectedQuantity, testTank.getCurrentQuantity());
        verify(transactionRepository, times(4)).save(any(FermTransaction.class));
    }

    @Test
    void addTransaction_WithZeroQuantity_DoesNotAffectTankQuantity() {
        // Arrange - Tank starts at 50
        BigDecimal originalQuantity = new BigDecimal("50.00");
        testTank.setCurrentQuantity(originalQuantity);

        // Create a "Note Only" transaction type (affects quantity = false)
        TransactionType noteType = new TransactionType("Note Only", "Add note without quantity change", volumeUnit, false, 0);
        Integer noteTypeId = 7;
        noteType.setId(noteTypeId);

        Integer batchId = 1;
        BigDecimal zeroQuantity = BigDecimal.ZERO;
        LocalDateTime transactionDate = LocalDateTime.now();
        Integer userId = 100;
        String notes = "Just a note";

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(testBatch));
        when(transactionRepository.save(any(FermTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // Setup transaction type cache
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(noteType));
        fermenterService.loadTransactionTypes();

        // Act - Add note with 0 quantity
        fermenterService.addTransaction(batchId, noteTypeId, zeroQuantity, transactionDate, userId, notes);

        // Assert - Quantity should be unchanged
        assertEquals(originalQuantity, testTank.getCurrentQuantity());
        verify(transactionRepository).save(any(FermTransaction.class));
    }

    @Test
    void addTransaction_WithLargeQuantityValues_HandlesCorrectly() {
        // Arrange - Test precision with large decimal values
        BigDecimal startingQuantity = new BigDecimal("999.99");
        BigDecimal largeCapacity = new BigDecimal("2000.00");
        testTank.setCurrentQuantity(startingQuantity);
        testTank.setCapacity(largeCapacity);

        Integer batchId = 1;
        Integer transactionTypeId = transferInType.getId();
        BigDecimal largeAddition = new BigDecimal("234.567");
        LocalDateTime transactionDate = LocalDateTime.now();
        Integer userId = 100;
        String notes = "Large addition";

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(testBatch));
        when(tankRepository.findById(1)).thenReturn(Optional.of(testTank));
        when(transactionRepository.save(any(FermTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tankRepository.save(any(FermTank.class))).thenAnswer(inv -> inv.getArgument(0));

        // Setup transaction type cache
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(transferInType));
        fermenterService.loadTransactionTypes();

        // Act - Add a large precise value
        fermenterService.addTransaction(batchId, transactionTypeId, largeAddition, transactionDate, userId, notes);

        // Assert - Should maintain precision: 999.99 + 234.567 = 1234.557
        BigDecimal expectedQuantity = new BigDecimal("1234.557");
        assertEquals(expectedQuantity, testTank.getCurrentQuantity());
    }

    // ==================== Multi-Tank Isolation Regression Test ====================

    @Test
    void addTransaction_ToMultipleTanksSequentially_UpdatesCorrectTanksOnly() {
        // Arrange - Create 3 tanks with 3 batches
        FermTank tankA = new FermTank("FV-A", new BigDecimal("100.00"));
        tankA.setId(1);
        tankA.setCurrentQuantity(BigDecimal.ZERO);
        tankA.setCurrentBatchId(1);

        FermTank tankB = new FermTank("FV-B", new BigDecimal("100.00"));
        tankB.setId(2);
        tankB.setCurrentQuantity(BigDecimal.ZERO);
        tankB.setCurrentBatchId(2);

        FermTank tankC = new FermTank("FV-C", new BigDecimal("100.00"));
        tankC.setId(3);
        tankC.setCurrentQuantity(BigDecimal.ZERO);
        tankC.setCurrentBatchId(3);

        FermBatch batchA = new FermBatch(1, "Batch A", LocalDateTime.now());
        batchA.setId(1);

        FermBatch batchB = new FermBatch(2, "Batch B", LocalDateTime.now());
        batchB.setId(2);

        FermBatch batchC = new FermBatch(3, "Batch C", LocalDateTime.now());
        batchC.setId(3);

        // Setup mocks for all three tanks/batches
        when(batchRepository.findById(1)).thenReturn(Optional.of(batchA));
        when(batchRepository.findById(2)).thenReturn(Optional.of(batchB));
        when(batchRepository.findById(3)).thenReturn(Optional.of(batchC));
        when(tankRepository.findById(1)).thenReturn(Optional.of(tankA));
        when(tankRepository.findById(2)).thenReturn(Optional.of(tankB));
        when(tankRepository.findById(3)).thenReturn(Optional.of(tankC));
        when(transactionRepository.save(any(FermTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tankRepository.save(any(FermTank.class))).thenAnswer(inv -> inv.getArgument(0));

        // Setup transaction type cache
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(transferInType));
        fermenterService.loadTransactionTypes();

        // Act - Add to multiple tanks sequentially
        Integer batchIdA = 1;
        Integer batchIdB = 2;
        Integer batchIdC = 3;
        Integer transactionTypeId = transferInType.getId();
        Integer userId = 100;

        BigDecimal firstAdditionToA = new BigDecimal("10.00");
        BigDecimal additionToB = new BigDecimal("20.00");
        BigDecimal additionToC = new BigDecimal("30.00");
        BigDecimal secondAdditionToA = new BigDecimal("5.00");

        fermenterService.addTransaction(batchIdA, transactionTypeId, firstAdditionToA, LocalDateTime.now(), userId, "Add to Tank A");
        fermenterService.addTransaction(batchIdB, transactionTypeId, additionToB, LocalDateTime.now(), userId, "Add to Tank B");
        fermenterService.addTransaction(batchIdC, transactionTypeId, additionToC, LocalDateTime.now(), userId, "Add to Tank C");
        fermenterService.addTransaction(batchIdA, transactionTypeId, secondAdditionToA, LocalDateTime.now(), userId, "Add more to Tank A");

        // Assert - Each tank should have correct quantity with NO cross-contamination
        BigDecimal expectedQuantityA = new BigDecimal("15.00");
        BigDecimal expectedQuantityB = new BigDecimal("20.00");
        BigDecimal expectedQuantityC = new BigDecimal("30.00");

        assertEquals(expectedQuantityA, tankA.getCurrentQuantity(), "Tank A should have 10 + 5 = 15");
        assertEquals(expectedQuantityB, tankB.getCurrentQuantity(), "Tank B should have 20");
        assertEquals(expectedQuantityC, tankC.getCurrentQuantity(), "Tank C should have 30");

        // Verify transactions were created for correct batches
        verify(transactionRepository, times(4)).save(any(FermTransaction.class));
    }

    // ==================== Validation Rule Regression Tests ====================

    @Test
    void addTransaction_BringingQuantityToExactCapacity_Succeeds() {
        // Arrange - Tank at 90, capacity 100
        BigDecimal currentQuantity = new BigDecimal("90.00");
        testTank.setCurrentQuantity(currentQuantity);

        Integer batchId = 1;
        Integer transactionTypeId = transferInType.getId();
        BigDecimal quantityToAdd = new BigDecimal("10.00");
        LocalDateTime transactionDate = LocalDateTime.now();
        Integer userId = 100;
        String notes = "Fill to capacity";

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(testBatch));
        when(tankRepository.findById(1)).thenReturn(Optional.of(testTank));
        when(transactionRepository.save(any(FermTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tankRepository.save(any(FermTank.class))).thenAnswer(inv -> inv.getArgument(0));

        // Setup transaction type cache
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(transferInType));
        fermenterService.loadTransactionTypes();

        // Act - Add exactly 10 to reach capacity
        assertDoesNotThrow(() ->
            fermenterService.addTransaction(batchId, transactionTypeId, quantityToAdd, transactionDate, userId, notes)
        );

        // Assert - Should be exactly at capacity
        BigDecimal expectedCapacity = new BigDecimal("100.00");
        assertEquals(expectedCapacity, testTank.getCurrentQuantity());
    }

    @Test
    void addTransaction_BringingQuantityToExactlyZero_Succeeds() {
        // Arrange - Tank has exactly 10
        BigDecimal currentQuantity = new BigDecimal("10.00");
        testTank.setCurrentQuantity(currentQuantity);

        Integer batchId = 1;
        Integer transactionTypeId = transferOutType.getId();
        BigDecimal quantityToRemove = new BigDecimal("10.00");
        LocalDateTime transactionDate = LocalDateTime.now();
        Integer userId = 100;
        String notes = "Empty tank";

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(testBatch));
        when(tankRepository.findById(1)).thenReturn(Optional.of(testTank));
        when(transactionRepository.save(any(FermTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tankRepository.save(any(FermTank.class))).thenAnswer(inv -> inv.getArgument(0));

        // Setup transaction type cache
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(transferOutType));
        fermenterService.loadTransactionTypes();

        // Act - Remove exactly 10 to reach zero
        assertDoesNotThrow(() ->
            fermenterService.addTransaction(batchId, transactionTypeId, quantityToRemove, transactionDate, userId, notes)
        );

        // Assert - Should be exactly zero
        assertEquals(0, testTank.getCurrentQuantity().compareTo(BigDecimal.ZERO), "Quantity should be zero");
    }

    @Test
    void addTransaction_ExceedingCapacityBySmallAmount_ThrowsException() {
        // Arrange - Tank at 95, capacity 100
        BigDecimal currentQuantity = new BigDecimal("95.00");
        testTank.setCurrentQuantity(currentQuantity);

        Integer batchId = 1;
        Integer transactionTypeId = transferInType.getId();
        BigDecimal excessiveQuantity = new BigDecimal("5.01");
        LocalDateTime transactionDate = LocalDateTime.now();
        Integer userId = 100;
        String notes = "Overfill";

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(testBatch));
        when(tankRepository.findById(1)).thenReturn(Optional.of(testTank));

        // Setup transaction type cache
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(transferInType));
        fermenterService.loadTransactionTypes();

        // Act & Assert - Adding 5.01 should fail (would be 100.01)
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            fermenterService.addTransaction(batchId, transactionTypeId, excessiveQuantity, transactionDate, userId, notes)
        );

        assertTrue(exception.getMessage().contains("exceeds tank capacity"));
        verify(tankRepository, never()).save(any(FermTank.class));
    }

    @Test
    void addTransaction_GoingBelowZeroBySmallAmount_ThrowsException() {
        // Arrange - Tank has 5 gallons
        BigDecimal currentQuantity = new BigDecimal("5.00");
        testTank.setCurrentQuantity(currentQuantity);

        Integer batchId = 1;
        Integer transactionTypeId = transferOutType.getId();
        BigDecimal excessiveRemoval = new BigDecimal("5.01");
        LocalDateTime transactionDate = LocalDateTime.now();
        Integer userId = 100;
        String notes = "Remove too much";

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(testBatch));
        when(tankRepository.findById(1)).thenReturn(Optional.of(testTank));

        // Setup transaction type cache
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(transferOutType));
        fermenterService.loadTransactionTypes();

        // Act & Assert - Removing 5.01 should fail (would be -0.01)
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            fermenterService.addTransaction(batchId, transactionTypeId, excessiveRemoval, transactionDate, userId, notes)
        );

        assertTrue(exception.getMessage().contains("Insufficient quantity"));
        verify(tankRepository, never()).save(any(FermTank.class));
    }

    // ==================== Batch Completion Workflow Regression Tests ====================

    @Test
    void completeBatch_SetsCompletionDateCorrectly() {
        // Arrange
        Integer batchId = 1;
        testTank.setCurrentBatchId(batchId);
        testTank.setCurrentQuantity(BigDecimal.ZERO); // No waste to create

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(testBatch));
        when(tankRepository.findById(1)).thenReturn(Optional.of(testTank));
        when(batchRepository.save(any(FermBatch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tankRepository.save(any(FermTank.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime beforeCompletion = LocalDateTime.now().minusSeconds(1);

        // Act
        FermBatch completed = fermenterService.completeBatch(batchId);

        // Assert - Completion date should be set and recent
        assertNotNull(completed.getCompletionDate(), "Completion date should be set");
        assertTrue(completed.getCompletionDate().isAfter(beforeCompletion), "Completion date should be recent");
        assertFalse(completed.isActive(), "Batch should no longer be active");
    }

    @Test
    void completeBatch_WithZeroRemainingVolume_DoesNotCreateWasteTransaction() {
        // Arrange - Tank has zero quantity (everything transferred out)
        Integer batchId = 1;
        BigDecimal zeroQuantity = BigDecimal.ZERO;

        testTank.setCurrentBatchId(batchId);
        testTank.setCurrentQuantity(zeroQuantity);

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(testBatch));
        when(tankRepository.findById(1)).thenReturn(Optional.of(testTank));
        when(batchRepository.save(any(FermBatch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tankRepository.save(any(FermTank.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        fermenterService.completeBatch(batchId);

        // Assert - No waste transaction should be created
        verify(transactionRepository, never()).save(any(FermTransaction.class));
    }

    @Test
    void completeBatch_WithRemainingVolume_CreatesWasteTransactionWithCorrectAmount() {
        // Arrange - Tank has 5 gallons remaining
        Integer batchId = 1;
        BigDecimal remainingVolume = new BigDecimal("5.00");

        testTank.setCurrentBatchId(batchId);
        testTank.setCurrentQuantity(remainingVolume);

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(testBatch));
        when(tankRepository.findById(1)).thenReturn(Optional.of(testTank));
        when(batchRepository.save(any(FermBatch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tankRepository.save(any(FermTank.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(FermTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // Setup transaction type cache with waste type
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(wasteType));
        fermenterService.loadTransactionTypes();

        // Act
        fermenterService.completeBatch(batchId);

        // Assert - Waste transaction should be created with exact remaining amount
        verify(transactionRepository).save(argThat(transaction ->
            transaction.getQuantity().equals(remainingVolume) &&
            transaction.getNotes().contains("Auto-generated waste")
        ));
    }

    @Test
    void completeBatch_ClearsTankAndAllowsNewBatch() {
        // Arrange
        Integer batchId = 1;
        Integer tankId = 1;
        BigDecimal remainingVolume = new BigDecimal("10.00");

        testTank.setCurrentBatchId(batchId);
        testTank.setCurrentQuantity(remainingVolume);

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(testBatch));
        when(tankRepository.findById(tankId)).thenReturn(Optional.of(testTank));
        when(batchRepository.save(any(FermBatch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tankRepository.save(any(FermTank.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(FermTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // Setup transaction type cache
        when(transactionTypeRepository.findAll()).thenReturn(Arrays.asList(wasteType, transferInType));
        fermenterService.loadTransactionTypes();

        // Act - Complete the batch
        fermenterService.completeBatch(batchId);

        // Assert - Tank should be cleared
        assertNull(testTank.getCurrentBatchId(), "Tank should have no current batch");
        assertEquals(0, testTank.getCurrentQuantity().compareTo(BigDecimal.ZERO), "Tank should be empty");

        // Verify we can start a new batch immediately
        Integer newBatchId = 2;
        String newBatchName = "New Batch";
        Integer transactionTypeId = transferInType.getId();
        BigDecimal initialQuantity = new BigDecimal("50.00");
        String notes = "Starting new batch";

        FermBatch newBatch = new FermBatch(tankId, newBatchName, LocalDateTime.now());
        newBatch.setId(newBatchId);

        when(batchRepository.save(any(FermBatch.class))).thenAnswer(inv -> {
            FermBatch batch = inv.getArgument(0);
            batch.setId(newBatchId);
            return batch;
        });

        // Should not throw exception since tank is now available
        assertDoesNotThrow(() ->
            fermenterService.startBatch(tankId, newBatchName, transactionTypeId, initialQuantity, notes)
        );
    }
}
