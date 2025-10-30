package com.TbInventory.integration;

import com.TbInventory.model.*;
import com.TbInventory.repository.*;
import com.TbInventory.service.FermenterService;
import com.example.test.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Fermenter functionality.
 *
 * ========================================
 * HOW TO RUN THESE TESTS:
 * ========================================
 * IMPORTANT: These tests require a database connection!
 *
 * Option 1 - Run with PostgreSQL (requires DB running at 192.168.0.160:5432):
 *   mvn -P real-db-test test -Dtest=FermenterIntegrationTest
 *
 * Option 2 - Run all integration tests with PostgreSQL:
 *   mvn test -Dgroups=integration
 *
 * Option 3 - Run with H2 in-memory database (easier, no external DB needed):
 *   First, ensure H2 profile is active, then run:
 *   mvn test -Dtest=FermenterIntegrationTest -Dspring.profiles.active=dev
 *
 * Note: Integration tests are SLOWER than unit tests (start full Spring context)
 * ========================================
 *
 * These tests:
 * - Start the full Spring context
 * - Use a REAL database (PostgreSQL or H2)
 * - Test @PostConstruct cache loading from database
 * - Test full workflows with real DB operations
 * - Automatically rollback changes (due to @Transactional)
 *
 * Test coverage (2 tests):
 * - Transaction type cache loading from database
 * - Full batch workflow (create → transactions → complete)
 */
@IntegrationTest
@Transactional // Rollback after each test to keep DB clean
class FermenterIntegrationTest {

    @Autowired
    private FermenterService fermenterService;

    @Autowired
    private FermTankRepository tankRepository;

    @Autowired
    private FermBatchRepository batchRepository;

    @Autowired
    private FermTransactionRepository transactionRepository;

    @Autowired
    private TransactionTypeRepository transactionTypeRepository;

    @Autowired
    private UnitTypeRepository unitTypeRepository;

    private UnitType volumeUnit;
    private UnitType weightUnit;
    private TransactionType transferInType;
    private TransactionType yeastType;
    private FermTank testTank;

    @BeforeEach
    void setUp() {
        // Create test data in the real database

        // Create units if they don't exist
        volumeUnit = unitTypeRepository.findByAbbreviation("bbls")
            .orElseGet(() -> {
                UnitType unit = new UnitType("Barrels", "bbls", true);
                return unitTypeRepository.save(unit);
            });

        weightUnit = unitTypeRepository.findByAbbreviation("g")
            .orElseGet(() -> {
                UnitType unit = new UnitType("Grams", "g", false);
                return unitTypeRepository.save(unit);
            });

        // Create transaction types if they don't exist (matching new IDs from data-dev.sql)
        transferInType = transactionTypeRepository.findByTypeName("Cider Addition")
            .orElseGet(() -> {
                TransactionType type = new TransactionType("Cider Addition",
                    "Add apple cider to fermenter", volumeUnit, true, 1);
                return transactionTypeRepository.save(type);
            });

        yeastType = transactionTypeRepository.findByTypeName("Yeast Addition")
            .orElseGet(() -> {
                TransactionType type = new TransactionType("Yeast Addition",
                    "Pitch yeast to start fermentation", weightUnit, false, 0);
                return transactionTypeRepository.save(type);
            });

        // Create test tank
        testTank = new FermTank("INT-TEST-TANK-1", new BigDecimal("100"));
        testTank = tankRepository.save(testTank);
    }

    @AfterEach
    void cleanup() {
        // Clean up test data (transactions, batches, tanks)
        // Order matters due to foreign keys
        transactionRepository.deleteAll();
        batchRepository.deleteAll();
        tankRepository.deleteAll();
        // Note: Don't delete unit types or transaction types - they're reference data
    }

    // ==================== Transaction Type Cache Tests ====================

    @Test
    void postConstruct_LoadsTransactionTypesFromDatabase() {
        // The @PostConstruct method runs when Spring creates the service bean
        // By the time this test runs, the cache should already be populated

        // We can't directly inspect the private cache, but we can verify it works
        // by using methods that depend on it

        // Verify we can find transaction types that exist in the database
        assertDoesNotThrow(() -> {
            // Start a batch using a real transaction type from the database
            FermBatch batch = fermenterService.startBatch(
                testTank.getId(),
                "Integration Test Batch",
                transferInType.getId(), // This ID must be in the cache
                new BigDecimal("50.00"),
                "Testing cache"
            );
            assertNotNull(batch);
        });
    }

    // ==================== Full Workflow Integration Test ====================

    @Test
    void fullBatchWorkflow_WithCachedTypes_WorksCorrectly() {
        // This tests the entire workflow using cached transaction types

        // Step 1: Start batch (uses cache for transferInType)
        FermBatch batch = fermenterService.startBatch(
            testTank.getId(),
            "Full Workflow IPA",
            transferInType.getId(),
            new BigDecimal("80.00"),
            "Starting batch"
        );

        // Verify tank was updated
        FermTank tank = fermenterService.getTankById(testTank.getId());
        assertEquals(batch.getId(), tank.getCurrentBatchId());
        assertEquals(new BigDecimal("80.00"), tank.getCurrentQuantity());

        // Step 2: Add yeast (uses cache for yeastType)
        fermenterService.addTransaction(
            batch.getId(),
            yeastType.getId(),
            new BigDecimal("200.00"),
            LocalDateTime.now(),
            1,
            "Pitching yeast"
        );

        // Step 3: Complete batch
        FermBatch completed = fermenterService.completeBatch(batch.getId());
        assertNotNull(completed.getCompletionDate());

        // Verify tank was cleared
        tank = fermenterService.getTankById(testTank.getId());
        assertNull(tank.getCurrentBatchId());
        assertEquals(BigDecimal.ZERO, tank.getCurrentQuantity());
    }
}
