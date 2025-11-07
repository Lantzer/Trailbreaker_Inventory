package com.TbInventory.service;

import com.TbInventory.model.*;
import com.TbInventory.repository.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
     * Load all transaction types into cache AFTER application is ready.
     * This ensures SQL initialization scripts have run first.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadTransactionTypes() {
        transactionTypeRepository.findAll().forEach(type ->
            transactionTypeCache.put(type.getId(), type)
        );
        System.out.println("Loaded " + transactionTypeCache.size() + " transaction types into cache");
    }

    // ==================== Tank Management Methods ====================

    /**
     * Get all non-deleted tanks (for operational views).
     * @return List of active fermenter tanks
     */
    public List<FermTank> getAllTanks() {
        return tankRepository.findByDeletedAtIsNull();
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
     * Get non-deleted tank by label (for label-based URLs).
     * @param label Tank label (e.g., "FV-1")
     * @return The tank
     * @throws RuntimeException if tank not found or deleted
     */
    public FermTank getTankByLabel(String label) {
        return tankRepository.findByLabelAndDeletedAtIsNull(label)
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
    public FermTank createTank(String label, BigDecimal capacity) {
        // Validation: Check for duplicate label
        if (tankRepository.existsByLabel(label)) {
            throw new RuntimeException("Tank with label '" + label + "' already exists");
        }

        FermTank tank = new FermTank(label, capacity);
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

    // ==================== Admin Tank Management Methods ====================

    /**
     * Get all tanks including soft-deleted ones (admin view).
     * @return List of all tanks
     */
    public List<FermTank> getAllTanksIncludingDeleted() {
        return tankRepository.findAll();
    }

    /**
     * Get all available unit types (for form dropdowns).
     * @return List of all unit types
     */
    public List<UnitType> getAllUnits() {
        return unitTypeRepository.findAll();
    }

    /**
     * Get all volume unit types (for tank capacity dropdowns).
     * @return List of volume units
     */
    public List<UnitType> getVolumeUnits() {
        return unitTypeRepository.findAll().stream()
            .filter(UnitType::getIsVolume)
            .toList();
    }

    /**
     * Update tank details using builder pattern.
     * @param currentLabel Current tank label
     * @param updateRequest Update request with optional fields
     * @return Updated tank
     * @throws RuntimeException if validation fails
     */
    public FermTank updateTank(String currentLabel, TankUpdateRequest updateRequest) {
        FermTank tank = getTankByLabel(currentLabel);

        // Update label if provided
        if (updateRequest.hasLabelUpdate()) {
            String newLabel = updateRequest.getNewLabel();
            // Validation: If changing label, check for duplicates
            if (!currentLabel.equals(newLabel) && tankRepository.existsByLabel(newLabel)) {
                throw new RuntimeException("Tank with label '" + newLabel + "' already exists");
            }
            tank.setLabel(newLabel);
            System.out.println("Updated tank label to " + newLabel);
        }

        // Update capacity if provided
        if (updateRequest.hasCapacityUpdate()) {
            BigDecimal newCapacity = updateRequest.getNewCapacity();

            // Validation: New capacity must be >= current quantity
            if (newCapacity.compareTo(tank.getCurrentQuantity()) < 0) {
                throw new RuntimeException(
                    "New capacity (" + newCapacity + ") cannot be less than current quantity (" + tank.getCurrentQuantity() +
                    "gallons)"
                );
            }

            tank.setCapacity(newCapacity);
            System.out.println("Updated tank capacity to " + newCapacity + " gallons");
            
            // REMOVED: Capacity is always in gallons
            //tank.setCapacityUnit(unit);
        }

        return tankRepository.save(tank);
    }

    /**
     * Soft delete a tank (marks as deleted without removing from database).
     * @param label Tank label
     * @return The deleted tank
     * @throws RuntimeException if tank not found
     */
    public FermTank softDeleteTank(String label) {
        FermTank tank = getTankByLabel(label);
        tank.softDelete();
        return tankRepository.save(tank);
    }

    /**
     * Restore a soft-deleted tank.
     * @param label Tank label
     * @return The restored tank
     * @throws RuntimeException if tank not found
     */
    public FermTank restoreTank(String label) {
        FermTank tank = tankRepository.findByLabel(label)
            .orElseThrow(() -> new RuntimeException("Tank not found with label: " + label));
        tank.restore();
        return tankRepository.save(tank);
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
    public FermBatch startBatch(Integer tankId, String batchName,
                               Integer transactionTypeId, BigDecimal initialQuantity,
                               String notes) {

        //Mock userId for now, should be retrieved from auth context
        Integer userId = 1;

        // 1. Validate tank exists and is empty
        FermTank tank = getTankById(tankId);
        if (tank.getCurrentBatchId() != null) {
            throw new RuntimeException("Tank " + tank.getLabel() + " already has an active batch");
        }

        // 2. Create the batch
        FermBatch batch = new FermBatch(tankId, batchName, LocalDateTime.now());
        batch = batchRepository.save(batch);

        // 3. Create the initial transaction
        TransactionType txnType = transactionTypeCache.get(transactionTypeId);
        if (txnType == null) {
            throw new RuntimeException("Invalid transaction type: " + transactionTypeId);
        }

        // 4. ensure initial quantity does not exceed tank capacity
        if (initialQuantity.compareTo(tank.getCapacity()) > 0) {
            throw new RuntimeException("Initial quantity (" + initialQuantity +
                " gallons) exceeds tank capacity (" + tank.getCapacity() + " gallons)");
        }
        
        // 5. quantityUnit is always gallons for fermenter
        UnitType quantityUnit = unitTypeRepository.findByAbbreviation("gal")
            .orElseThrow(() -> new RuntimeException("Volume unit 'gal' not found"));
        
        // If notes is empty, fill with default "Initial fill"
        if (notes == null || notes.trim().isEmpty()) {
            notes = "Initial fill";
        }

        FermTransaction transaction = new FermTransaction(
            batch.getId(),
            txnType,
            initialQuantity,
            quantityUnit,
            batch.getCreatedAt(), // Use batch start date for first transaction
            userId,
            notes
        );
        transactionRepository.save(transaction);

        // 6. Update tank with batch and quantity
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

    /**
     * Get all fermenter batches (both active and completed).
     * Used for admin view to see all batches.
     * @return List of all batches
     */
    public List<FermBatch> getAllFermenterBatches() {
        return batchRepository.findAll();
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

        // 3. quantityUnit is always gallons for fermenter
        UnitType quantityUnit = unitTypeRepository.findByAbbreviation("gal")
            .orElseThrow(() -> new RuntimeException("Volume unit 'gal' not found"));

        // 4. Create transaction
        FermTransaction transaction = new FermTransaction(
            batchId,
            type,
            quantity,
            quantityUnit,
            LocalDateTime.now(),
            userId,
            notes
        );
        transaction = transactionRepository.save(transaction);

        // 4. Business logic: Update tank quantity if transaction affects volume
        if (type.getAffectsTankQuantity()) {
            FermTank tank = getTankById(batch.getTankId());

            // Apply quantity multiplier (1 for additions, -1 for removals, 0 for notes)
            BigDecimal adjustment = quantity.multiply(new BigDecimal(type.getQuantityMultiplier()));

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
     * Automatically creates a "Waste" transaction for any remaining volume,
     * then clears the tank's current batch reference and resets quantity to zero.
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

        // 2. Get tank and check for remaining volume
        FermTank tank = getTankById(batch.getTankId());

        // 3. quantityUnit is always gallons for fermenter
        UnitType quantityUnit = unitTypeRepository.findByAbbreviation("gal")
            .orElseThrow(() -> new RuntimeException("Volume unit 'gal' not found"));

        // 3. Auto-create waste transaction for remaining volume if any exists
        if (tank.getCurrentQuantity().compareTo(BigDecimal.ZERO) > 0) {
            TransactionType wasteType = transactionTypeCache.get(6); // Waste type ID = 6
            if (wasteType == null) {
                throw new RuntimeException("Waste transaction type not found (ID 6)");
            }

            FermTransaction wasteTransaction = new FermTransaction(
                batchId,
                wasteType,
                tank.getCurrentQuantity(), // Remaining volume
                quantityUnit,
                LocalDateTime.now(),
                // TODO: Get user ID from auth context, for now use null
                null,
                "Auto-generated waste transaction on batch completion"
            );
            transactionRepository.save(wasteTransaction);
        }

        // 4. Mark batch as complete
        batch.setCompletionDate(LocalDateTime.now());
        batch = batchRepository.save(batch);
        
        // 5. Clear the tank's current batch and reset quantity
        if (tank.getCurrentBatchId() != null && tank.getCurrentBatchId().equals(batchId)) {
            tank.setCurrentBatchId(null);
            tank.setCurrentQuantity(BigDecimal.ZERO);
            tankRepository.save(tank);
        }

        return batch;
    }

    // ==================== Fermenter Transfer Methods ====================

    /**
     * Transfer entire remaining volume from source batch to destination tank.
     * Creates TWO transactions atomically:
     *   1. Transfer Out (ID 4) on source batch
     *   2. Transfer In (ID 8) on destination batch
     * Then completes the source batch.
     *
     * @param sourceBatchId Source batch ID
     * @param destinationTankLabel Destination tank label (e.g., "FV-3")
     * @return Array of [transferOut, transferIn] transactions
     * @throws RuntimeException if validation fails or destination invalid
     */
    @Transactional  // CRITICAL: Ensures both transactions or neither
    public FermTransaction[] transferToFermenter(Integer sourceBatchId, String destinationTankLabel) {

        // 1. Validate source batch exists and is active
        FermBatch sourceBatch = getBatchById(sourceBatchId);
        if (!sourceBatch.isActive()) {
            throw new RuntimeException("Cannot transfer from completed batch");
        }

        // 2. Get source tank and validate it has volume
        FermTank sourceTank = getTankById(sourceBatch.getTankId());
        BigDecimal transferQuantity = sourceTank.getCurrentQuantity();
        if (transferQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Source tank is empty - nothing to transfer");
        }

        // 3. Validate destination tank exists and has active batch
        FermTank destTank = getTankByLabel(destinationTankLabel);
        if (destTank.getCurrentBatchId() == null) {
            throw new RuntimeException("Destination tank must have an active batch. Please start a batch first.");
        }
        FermBatch destBatch = getBatchById(destTank.getCurrentBatchId());

        // 4. Validate capacity
        BigDecimal newDestQuantity = destTank.getCurrentQuantity().add(transferQuantity);
        if (newDestQuantity.compareTo(destTank.getCapacity()) > 0) {
            throw new RuntimeException(String.format(
                "Transfer would exceed destination tank capacity (%.1f + %.1f > %.1f gal)",
                destTank.getCurrentQuantity(), transferQuantity, destTank.getCapacity()
            ));
        }

        // 5. Get transaction types from cache
        TransactionType transferOutType = transactionTypeCache.get(4);  // Transfer Out
        TransactionType transferInType = transactionTypeCache.get(8);   // Transfer In (NEW)
        if (transferOutType == null || transferInType == null) {
            throw new RuntimeException("Transfer transaction types not found in cache");
        }

        UnitType gallonsUnit = unitTypeRepository.findByAbbreviation("gal")
            .orElseThrow(() -> new RuntimeException("Gallons unit not found"));

        // 6. Create Transfer Out transaction on source batch
        String transferOutNotes = String.format("Transferred to %s", destTank.getLabel());
        FermTransaction transferOut = new FermTransaction(
            sourceBatch.getId(),
            transferOutType,
            transferQuantity,
            gallonsUnit,
            LocalDateTime.now(),
            1,  // TODO: Get user ID from authentication context
            transferOutNotes
        );
        transferOut.setRelatedTankId(destTank.getId());  // Store destination tank ID
        transferOut = transactionRepository.save(transferOut);

        // 7. Create Transfer In transaction on destination batch
        String transferInNotes = String.format("Tank %s - Batch: %d",
            sourceTank.getLabel(),
            sourceBatch.getId()
        );
        FermTransaction transferIn = new FermTransaction(
            destBatch.getId(),
            transferInType,
            transferQuantity,
            gallonsUnit,
            LocalDateTime.now(),
            1,  // TODO: Get user ID from authentication context
            transferInNotes
        );
        transferIn.setRelatedTankId(sourceTank.getId());  // Store source tank ID
        transferIn = transactionRepository.save(transferIn);

        // 8. Update tank quantities
        sourceTank.setCurrentQuantity(BigDecimal.ZERO);
        destTank.setCurrentQuantity(newDestQuantity);
        tankRepository.save(sourceTank);
        tankRepository.save(destTank);

        // 9. Complete source batch (calls existing completeBatch method)
        completeBatch(sourceBatch.getId());

        // 10. Return both transactions for verification
        return new FermTransaction[] { transferOut, transferIn };
    }
}
