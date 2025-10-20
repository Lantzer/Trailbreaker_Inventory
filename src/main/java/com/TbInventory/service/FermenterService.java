package com.TbInventory.service;

import com.TbInventory.model.*;
import com.TbInventory.repository.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service layer for fermenter management.
 * Handles business logic for tanks, batches, and transactions.
 */
@Service
@Transactional
public class FermenterService {

    private final FermTankRepository tankRepository;
    private final FermBatchRepository batchRepository;
    private final FermTransactionRepository transactionRepository;
    private final TransactionTypeRepository transactionTypeRepository;
    private final UnitTypeRepository unitTypeRepository;

    /**
     * Cache of transaction types loaded on startup for performance.
     * Key: transaction type ID, Value: TransactionType entity
     */
    private Map<Integer, TransactionType> transactionTypeCache = new HashMap<>();

    /**
     * Constructor with dependency injection.
     */
    public FermenterService(FermTankRepository tankRepository,
                           FermBatchRepository batchRepository,
                           FermTransactionRepository transactionRepository,
                           TransactionTypeRepository transactionTypeRepository,
                           UnitTypeRepository unitTypeRepository) {
        this.tankRepository = tankRepository;
        this.batchRepository = batchRepository;
        this.transactionRepository = transactionRepository;
        this.transactionTypeRepository = transactionTypeRepository;
        this.unitTypeRepository = unitTypeRepository;
    }

    /**
     * Load all transaction types into cache on service startup.
     * This runs once when the application starts.
     */
    @PostConstruct
    public void loadTransactionTypes() {
        transactionTypeRepository.findAll().forEach(type ->
            transactionTypeCache.put(type.getId(), type)
        );
    }

    // ==================== Tank Management Methods ====================

    /**
     * Get all tanks.
     * @return List of all fermenter tanks
     */
    public List<FermTank> getAllTanks() {
        return tankRepository.findAll();
    }

    /**
     * Get tank by ID.
     * @param id Tank ID
     * @return The tank
     * @throws RuntimeException if tank not found
     */
    public FermTank getTankById(Integer id) {
        return tankRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Tank not found with ID: " + id));
    }

    /**
     * Get tank by label (for label-based URLs).
     * @param label Tank label (e.g., "FV-1")
     * @return The tank
     * @throws RuntimeException if tank not found
     */
    public FermTank getTankByLabel(String label) {
        return tankRepository.findByLabel(label)
            .orElseThrow(() -> new RuntimeException("Tank not found with label: " + label));
    }

    /**
     * Create a new tank.
     * @param label Tank label (e.g., "FV-1")
     * @param capacity Tank capacity
     * @param capacityUnitId Unit type ID for capacity (must be volume unit)
     * @return The created tank
     * @throws RuntimeException if validation fails
     */
    public FermTank createTank(String label, BigDecimal capacity, Integer capacityUnitId) {
        // Validation: Check for duplicate label
        if (tankRepository.existsByLabel(label)) {
            throw new RuntimeException("Tank with label '" + label + "' already exists");
        }

        // Validation: Capacity unit must be a volume unit
        UnitType unit = unitTypeRepository.findById(capacityUnitId)
            .orElseThrow(() -> new RuntimeException("Unit type not found: " + capacityUnitId));

        if (!unit.getIsVolume()) {
            throw new RuntimeException("Tank capacity must use volume units (barrels, gallons), not weight units");
        }

        FermTank tank = new FermTank(label, capacity, unit);
        return tankRepository.save(tank);
    }

    /**
     * Get all available (empty) tanks.
     * @return List of tanks without active batches
     */
    public List<FermTank> getAvailableTanks() {
        return tankRepository.findByCurrentBatchIdIsNull();
    }

    /**
     * Get all active tanks (with batches).
     * @return List of tanks with active batches
     */
    public List<FermTank> getActiveTanks() {
        return tankRepository.findByCurrentBatchIdIsNotNull();
    }

    // ==================== Batch Management Methods ====================

    /**
     * Start a new batch in a tank with an initial transaction.
     * This implements Option A: Batch creation = First transaction.
     *
     * @param tankId Tank ID
     * @param batchName Batch name (e.g., "Left Turn IPA")
     * @param startDate Batch start date (user-selected)
     * @param transactionTypeId Initial transaction type (e.g., "Transfer In")
     * @param initialQuantity Initial quantity added to tank
     * @param userId User ID creating the batch
     * @param notes Optional notes
     * @return The created batch
     * @throws RuntimeException if validation fails
     */
    public FermBatch startBatch(Integer tankId, String batchName, LocalDateTime startDate,
                               Integer transactionTypeId, BigDecimal initialQuantity,
                               Integer userId, String notes) {
        // 1. Validate tank exists and is empty
        FermTank tank = getTankById(tankId);
        if (tank.getCurrentBatchId() != null) {
            throw new RuntimeException("Tank " + tank.getLabel() + " already has an active batch");
        }

        // 2. Create the batch
        FermBatch batch = new FermBatch(tankId, batchName, startDate);
        batch = batchRepository.save(batch);

        // 3. Create the initial transaction
        TransactionType txnType = transactionTypeCache.get(transactionTypeId);
        if (txnType == null) {
            throw new RuntimeException("Invalid transaction type: " + transactionTypeId);
        }

        FermTransaction transaction = new FermTransaction(
            batch.getId(),
            txnType,
            initialQuantity,
            startDate, // Use batch start date for first transaction
            userId,
            notes
        );
        transactionRepository.save(transaction);

        // 4. Update tank with batch and quantity
        tank.setCurrentBatchId(batch.getId());
        if (transaction.affectsTankQuantity()) {
            tank.setCurrentQuantity(initialQuantity);
        }
        tankRepository.save(tank);

        return batch;
    }

    /**
     * Get batch by ID.
     * @param id Batch ID
     * @return The batch
     * @throws RuntimeException if batch not found
     */
    public FermBatch getBatchById(Integer id) {
        return batchRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Batch not found with ID: " + id));
    }

    /**
     * Get all active batches.
     * @return List of active batches
     */
    public List<FermBatch> getActiveBatches() {
        return batchRepository.findByCompletionDateIsNull();
    }

    /**
     * Get all completed batches for history page.
     * @return List of completed batches, newest first
     */
    public List<FermBatch> getCompletedBatches() {
        return batchRepository.findByCompletionDateIsNotNullOrderByCompletionDateDesc();
    }

    // ==================== Transaction Management Methods ====================

    /**
     * Add a transaction to a batch.
     * Automatically updates:
     * - yeastDate/lysozymeDate if applicable
     * - tank quantity if transaction type affects volume
     *
     * @param batchId Batch ID
     * @param transactionTypeId Transaction type ID
     * @param quantity Quantity
     * @param transactionDate Date of transaction (user-selected)
     * @param userId User ID
     * @param notes Optional notes
     * @return The created transaction
     * @throws RuntimeException if validation fails
     */
    public FermTransaction addTransaction(Integer batchId, Integer transactionTypeId,
                                         BigDecimal quantity, LocalDateTime transactionDate,
                                         Integer userId, String notes) {
        // 1. Validate batch exists and is active
        FermBatch batch = getBatchById(batchId);
        if (!batch.isActive()) {
            throw new RuntimeException("Cannot add transaction to completed batch");
        }

        // 2. Get transaction type from cache
        TransactionType type = transactionTypeCache.get(transactionTypeId);
        if (type == null) {
            throw new RuntimeException("Invalid transaction type: " + transactionTypeId);
        }

        // 3. Create transaction
        FermTransaction transaction = new FermTransaction(
            batchId,
            type,
            quantity,
            transactionDate,
            userId,
            notes
        );
        transaction = transactionRepository.save(transaction);

        // 4. Business logic: Update batch dates for yeast/lysozyme
        boolean batchUpdated = false;
        if ("Yeast Addition".equals(type.getTypeName()) && batch.getYeastDate() == null) {
            batch.setYeastDate(transactionDate);
            batchUpdated = true;
        } else if ("Lysozyme Addition".equals(type.getTypeName()) && batch.getLysozymeDate() == null) {
            batch.setLysozymeDate(transactionDate);
            batchUpdated = true;
        }

        if (batchUpdated) {
            batchRepository.save(batch);
        }

        // 5. Business logic: Update tank quantity if transaction affects volume
        if (type.getAffectsTankQuantity()) {
            FermTank tank = getTankById(batch.getTankId());
            BigDecimal adjustment = quantity;

            // Negative adjustment for removals (transfer out, waste, sample)
            if (type.getTypeName().contains("Out") ||
                type.getTypeName().contains("Waste") ||
                type.getTypeName().contains("Sample")) {
                adjustment = adjustment.negate();
            }

            BigDecimal newQuantity = tank.getCurrentQuantity().add(adjustment);

            // Validation: Quantity cannot go negative
            if (newQuantity.compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Insufficient quantity in tank. Current: " +
                    tank.getCurrentQuantity() + ", Attempted removal: " + quantity);
            }

            // Validation: Quantity cannot exceed capacity
            if (newQuantity.compareTo(tank.getCapacity()) > 0) {
                throw new RuntimeException("Transaction exceeds tank capacity. Capacity: " +
                    tank.getCapacity() + ", Resulting quantity: " + newQuantity);
            }

            tank.setCurrentQuantity(newQuantity);
            tankRepository.save(tank);
        }

        return transaction;
    }

    /**
     * Get all transactions for a batch.
     * @param batchId Batch ID
     * @return List of transactions, newest first
     */
    public List<FermTransaction> getBatchTransactions(Integer batchId) {
        return transactionRepository.findByBatchIdOrderByTransactionDateDesc(batchId);
    }

    /**
     * Get transactions for a batch filtered by type.
     * @param batchId Batch ID
     * @param transactionTypeId Transaction type ID to filter by
     * @return List of filtered transactions, newest first
     */
    public List<FermTransaction> getBatchTransactionsByType(Integer batchId, Integer transactionTypeId) {
        return transactionRepository.findByBatchIdAndTransactionType_IdOrderByTransactionDateDesc(
            batchId, transactionTypeId);
    }

    // ==================== Batch Completion Methods ====================

    /**
     * Complete a batch (mark as finished).
     * Clears the tank's current batch reference and resets quantity to zero.
     *
     * @param batchId Batch ID
     * @return The completed batch
     * @throws RuntimeException if batch not found or already completed
     */
    public FermBatch completeBatch(Integer batchId) {
        // 1. Validate batch exists and is active
        FermBatch batch = getBatchById(batchId);
        if (!batch.isActive()) {
            throw new RuntimeException("Batch is already completed");
        }

        // 2. Mark batch as complete
        batch.setCompletionDate(LocalDateTime.now());
        batch = batchRepository.save(batch);

        // 3. Clear the tank's current batch and reset quantity
        FermTank tank = getTankById(batch.getTankId());
        if (tank.getCurrentBatchId() != null && tank.getCurrentBatchId().equals(batchId)) {
            tank.setCurrentBatchId(null);
            tank.setCurrentQuantity(BigDecimal.ZERO);
            tankRepository.save(tank);
        }

        return batch;
    }
}
