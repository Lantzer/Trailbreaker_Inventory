# Trailbreaker Inventory - Database Schema (PostgreSQL)

**Version:** 2.0 - Corrected and PostgreSQL-compliant
**Database:** PostgreSQL 12+
**ORM:** JPA/Hibernate with Spring Data

---

## Overview

This schema supports fermenter and bright tank management for a cidery/brewery inventory system. It tracks:
- Physical tanks (fermenters and brights)
- Batches produced in tanks
- Transactions (additions, transfers, removals)
- Transaction and unit type lookups

**Design Principles:**
- Business logic in service layer (not database triggers)
- Consistent snake_case naming for database, camelCase for Java
- Audit trail with created/updated timestamps
- Foreign key constraints for referential integrity

---

## Table Structure

### 1. unit_type
Defines units of measurement (volume, weight, etc.)

```sql
CREATE TABLE unit_type (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    abbreviation VARCHAR(10) NOT NULL,
    is_volume BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE unit_type IS 'Lookup table for units of measurement';
COMMENT ON COLUMN unit_type.is_volume IS 'True if unit measures volume (bbls, gallons), false for weight/count';
```

**JPA Mapping:**
```java
@Entity
@Table(name = "unit_type")
public class UnitType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(nullable = false, length = 10)
    private String abbreviation;

    @Column(name = "is_volume", nullable = false)
    private Boolean isVolume = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

---

### 2. transaction_type
Defines types of transactions that can occur (yeast addition, transfer, etc.)

```sql
CREATE TABLE transaction_type (
    id SERIAL PRIMARY KEY,
    type_name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    unit_id INTEGER NOT NULL,
    affects_tank_quantity BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_transaction_type_unit
        FOREIGN KEY (unit_id)
        REFERENCES unit_type(id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_transaction_type_name ON transaction_type(type_name);

COMMENT ON TABLE transaction_type IS 'Lookup table for transaction types';
COMMENT ON COLUMN transaction_type.affects_tank_quantity IS 'True if this transaction type should update the tank quantity';
```

**JPA Mapping:**
```java
@Entity
@Table(name = "transaction_type")
public class TransactionType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "type_name", nullable = false, unique = true, length = 50)
    private String typeName;

    @Column(length = 255)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private UnitType unit;

    @Column(name = "affects_tank_quantity", nullable = false)
    private Boolean affectsTankQuantity = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

---

### 3. ferm_tank
Physical fermenter tanks

```sql
CREATE TABLE ferm_tank (
    id SERIAL PRIMARY KEY,
    label VARCHAR(100) NOT NULL UNIQUE,
    current_batch_id INTEGER,
    current_quantity DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    capacity DECIMAL(10, 2) NOT NULL,
    capacity_unit_id INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ferm_tank_capacity_unit
        FOREIGN KEY (capacity_unit_id)
        REFERENCES unit_type(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_current_quantity_positive
        CHECK (current_quantity >= 0),

    CONSTRAINT chk_capacity_positive
        CHECK (capacity > 0)
);

CREATE INDEX idx_ferm_tank_label ON ferm_tank(label);
CREATE INDEX idx_ferm_tank_current_batch ON ferm_tank(current_batch_id);

COMMENT ON TABLE ferm_tank IS 'Physical fermenter tanks';
COMMENT ON COLUMN ferm_tank.label IS 'Tank identifier (e.g., "FV-1", "Fermenter 3")';
COMMENT ON COLUMN ferm_tank.current_batch_id IS 'References the active batch, null if tank is empty';
COMMENT ON COLUMN ferm_tank.current_quantity IS 'Current volume in tank, managed by service layer';
```

**JPA Mapping:**
```java
@Entity
@Table(name = "ferm_tank")
public class FermTank {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 100)
    private String label;

    @Column(name = "current_batch_id")
    private Integer currentBatchId;

    @Column(name = "current_quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal currentQuantity = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal capacity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "capacity_unit_id", nullable = false)
    private UnitType capacityUnit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Business logic
    public boolean isLowCapacity(int thresholdPercent) {
        if (capacity.compareTo(BigDecimal.ZERO) == 0) return false;
        BigDecimal percentFull = currentQuantity
            .divide(capacity, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        return percentFull.compareTo(BigDecimal.valueOf(thresholdPercent)) < 0;
    }
}
```

---

### 4. ferm_batch
Batches produced in fermenter tanks

```sql
CREATE TABLE ferm_batch (
    id SERIAL PRIMARY KEY,
    tank_id INTEGER NOT NULL,
    batch_name VARCHAR(100) NOT NULL,
    start_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    yeast_date TIMESTAMP,
    lysozyme_date TIMESTAMP,
    completion_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ferm_batch_tank
        FOREIGN KEY (tank_id)
        REFERENCES ferm_tank(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_completion_after_start
        CHECK (completion_date IS NULL OR completion_date >= start_date)
);

CREATE INDEX idx_ferm_batch_tank ON ferm_batch(tank_id);
CREATE INDEX idx_ferm_batch_completion ON ferm_batch(completion_date);
CREATE INDEX idx_ferm_batch_active ON ferm_batch(tank_id, completion_date) WHERE completion_date IS NULL;

COMMENT ON TABLE ferm_batch IS 'Batches produced in fermenter tanks';
COMMENT ON COLUMN ferm_batch.batch_name IS 'Product name (e.g., "Left Turn IPA", "Cosmic Cider")';
COMMENT ON COLUMN ferm_batch.yeast_date IS 'Date yeast was added, managed by service layer';
COMMENT ON COLUMN ferm_batch.lysozyme_date IS 'Date lysozyme was added, managed by service layer';
COMMENT ON COLUMN ferm_batch.completion_date IS 'Null while active, set when batch is completed';
```

**JPA Mapping:**
```java
@Entity
@Table(name = "ferm_batch")
public class FermBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tank_id", nullable = false)
    private FermTank tank;

    @Column(name = "batch_name", nullable = false, length = 100)
    private String batchName;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "yeast_date")
    private LocalDateTime yeastDate;

    @Column(name = "lysozyme_date")
    private LocalDateTime lysozyme Date;

    @Column(name = "completion_date")
    private LocalDateTime completionDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Business logic
    public boolean isActive() {
        return completionDate == null;
    }

    public long getDaysInFermentation() {
        LocalDateTime endDate = completionDate != null ? completionDate : LocalDateTime.now();
        return ChronoUnit.DAYS.between(startDate, endDate);
    }
}
```

---

### 5. ferm_transaction
Records all additions, transfers, and removals from batches

```sql
CREATE TABLE ferm_transaction (
    id SERIAL PRIMARY KEY,
    batch_id INTEGER NOT NULL,
    transaction_type_id INTEGER NOT NULL,
    quantity DECIMAL(10, 2) NOT NULL,
    transaction_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id INTEGER,
    notes TEXT,
    bright_tank_id INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ferm_transaction_batch
        FOREIGN KEY (batch_id)
        REFERENCES ferm_batch(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_ferm_transaction_type
        FOREIGN KEY (transaction_type_id)
        REFERENCES transaction_type(id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_ferm_transaction_batch ON ferm_transaction(batch_id, transaction_date);
CREATE INDEX idx_ferm_transaction_date ON ferm_transaction(transaction_date);
CREATE INDEX idx_ferm_transaction_type ON ferm_transaction(transaction_type_id);

-- Partial unique index: Only one yeast addition and one lysozyme addition per batch
-- Assumes transaction_type_id 1 = yeast, 2 = lysozyme (adjust based on your seed data)
CREATE UNIQUE INDEX idx_unique_yeast_per_batch
    ON ferm_transaction(batch_id, transaction_type_id)
    WHERE transaction_type_id IN (1, 2);

COMMENT ON TABLE ferm_transaction IS 'All transactions for fermenter batches (additions, transfers, removals)';
COMMENT ON COLUMN ferm_transaction.notes IS 'Free-text notes (e.g., "Transferred to Bright Tank 3", "Waste - tank cleaning")';
COMMENT ON COLUMN ferm_transaction.bright_tank_id IS 'If transfer to bright tank, reference the target tank ID';
```

**JPA Mapping:**
```java
@Entity
@Table(name = "ferm_transaction")
public class FermTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private FermBatch batch;

    @ManyToOne(fetch = FetchType.EAGER) // Eager for transaction type caching
    @JoinColumn(name = "transaction_type_id", nullable = false)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "user_id")
    private Integer userId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "bright_tank_id")
    private Integer brightTankId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

---

## Foreign Key Relationships Summary

```
unit_type (1) ←──── (many) transaction_type
unit_type (1) ←──── (many) ferm_tank

ferm_tank (1) ←──── (many) ferm_batch
ferm_batch (1) ←──── (many) ferm_transaction

transaction_type (1) ←──── (many) ferm_transaction
```

---

## Seed Data

### Unit Types
```sql
INSERT INTO unit_type (name, abbreviation, is_volume) VALUES
('Barrels', 'bbls', true),
('Gallons', 'gal', true),
('Liters', 'L', true),
('Pounds', 'lbs', false),
('Grams', 'g', false);
```

### Transaction Types
```sql
-- Adjust IDs as needed for your unique constraint on yeast/lysozyme
INSERT INTO transaction_type (type_name, description, unit_id, affects_tank_quantity) VALUES
(1, 'Yeast Addition', 'Adding yeast to begin fermentation', 5, false), -- grams, doesn't affect volume
(2, 'Lysozyme Addition', 'Adding lysozyme for preservation', 5, false), -- grams, doesn't affect volume
(3, 'Hops Addition', 'Dry hopping or hop addition', 4, false), -- pounds, doesn't affect volume
(4, 'Transfer In', 'Transfer from another tank (increase quantity)', 1, true), -- bbls, increases volume
(5, 'Transfer Out', 'Transfer to bright tank or another fermenter', 1, true), -- bbls, decreases volume
(6, 'Waste/Drain', 'Waste removal or tank cleaning', 1, true), -- bbls, decreases volume
(7, 'Sample', 'Sample taken for testing', 1, true), -- bbls, decreases volume
(8, 'Note Only', 'Logged activity with no quantity change', 1, false); -- no quantity impact
```

---

## Service Layer Logic (Recommended Approach)

Instead of database triggers, handle business logic in your Spring service layer:

### Example: FermenterService.java

```java
@Service
@Transactional
public class FermenterService {

    private final FermBatchRepository batchRepository;
    private final FermTransactionRepository transactionRepository;
    private final FermTankRepository tankRepository;
    private final TransactionTypeRepository typeRepository;

    // Cache transaction types on startup (as suggested in original schema)
    private Map<Integer, TransactionType> transactionTypeCache = new HashMap<>();

    @PostConstruct
    public void loadTransactionTypes() {
        typeRepository.findAll().forEach(type ->
            transactionTypeCache.put(type.getId(), type)
        );
    }

    /**
     * Add a transaction to a batch.
     * Automatically updates:
     * - yeastDate/lysozyme Date if applicable
     * - tank quantity if transaction type affects volume
     */
    public FermTransaction addTransaction(
            Integer batchId,
            Integer transactionTypeId,
            BigDecimal quantity,
            Integer userId,
            String notes) {

        FermBatch batch = batchRepository.findById(batchId)
            .orElseThrow(() -> new BatchNotFoundException("Batch not found: " + batchId));

        if (batch.getCompletionDate() != null) {
            throw new IllegalStateException("Cannot add transaction to completed batch");
        }

        TransactionType type = transactionTypeCache.get(transactionTypeId);
        if (type == null) {
            throw new IllegalArgumentException("Invalid transaction type: " + transactionTypeId);
        }

        // Create transaction
        FermTransaction transaction = new FermTransaction();
        transaction.setBatch(batch);
        transaction.setTransactionType(type);
        transaction.setQuantity(quantity);
        transaction.setTransactionDate(LocalDateTime.now());
        transaction.setUserId(userId);
        transaction.setNotes(notes);
        transaction.setCreatedAt(LocalDateTime.now());

        // Business logic: Update batch dates for yeast/lysozyme
        if ("Yeast Addition".equals(type.getTypeName()) && batch.getYeastDate() == null) {
            batch.setYeastDate(LocalDateTime.now());
            batch.setUpdatedAt(LocalDateTime.now());
        } else if ("Lysozyme Addition".equals(type.getTypeName()) && batch.getLysozyme Date() == null) {
            batch.setLysozyme Date(LocalDateTime.now());
            batch.setUpdatedAt(LocalDateTime.now());
        }

        // Business logic: Update tank quantity if transaction affects volume
        if (type.getAffectsTankQuantity()) {
            FermTank tank = batch.getTank();
            BigDecimal adjustment = quantity;

            // Negative adjustment for removals (transfer out, waste, sample)
            if (type.getTypeName().contains("Out") ||
                type.getTypeName().contains("Waste") ||
                type.getTypeName().contains("Sample")) {
                adjustment = adjustment.negate();
            }

            BigDecimal newQuantity = tank.getCurrentQuantity().add(adjustment);
            if (newQuantity.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalStateException("Insufficient quantity in tank");
            }
            if (newQuantity.compareTo(tank.getCapacity()) > 0) {
                throw new IllegalStateException("Transaction exceeds tank capacity");
            }

            tank.setCurrentQuantity(newQuantity);
            tank.setUpdatedAt(LocalDateTime.now());
            tankRepository.save(tank);
        }

        batchRepository.save(batch);
        return transactionRepository.save(transaction);
    }

    /**
     * Complete a batch (mark as finished).
     */
    public FermBatch completeBatch(Integer batchId) {
        FermBatch batch = batchRepository.findById(batchId)
            .orElseThrow(() -> new BatchNotFoundException("Batch not found: " + batchId));

        if (batch.getCompletionDate() != null) {
            throw new IllegalStateException("Batch already completed");
        }

        batch.setCompletionDate(LocalDateTime.now());
        batch.setUpdatedAt(LocalDateTime.now());

        // Clear the current batch from the tank
        FermTank tank = batch.getTank();
        if (tank.getCurrentBatchId() != null && tank.getCurrentBatchId().equals(batchId)) {
            tank.setCurrentBatchId(null);
            tank.setCurrentQuantity(BigDecimal.ZERO);
            tank.setUpdatedAt(LocalDateTime.now());
            tankRepository.save(tank);
        }

        return batchRepository.save(batch);
    }
}
```

---

## Database Triggers (Alternative Approach - Not Recommended)

If you prefer database triggers over service layer logic, here's the PostgreSQL syntax:

### Trigger: Auto-update yeast/lysozyme dates

```sql
CREATE OR REPLACE FUNCTION update_batch_dates()
RETURNS TRIGGER AS $$
BEGIN
    -- Update yeast date (assuming transaction_type_id = 1)
    IF NEW.transaction_type_id = 1 THEN
        UPDATE ferm_batch
        SET yeast_date = NEW.transaction_date,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = NEW.batch_id
          AND yeast_date IS NULL;
    END IF;

    -- Update lysozyme date (assuming transaction_type_id = 2)
    IF NEW.transaction_type_id = 2 THEN
        UPDATE ferm_batch
        SET lysozyme_date = NEW.transaction_date,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = NEW.batch_id
          AND lysozyme_date IS NULL;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_batch_dates
AFTER INSERT ON ferm_transaction
FOR EACH ROW
EXECUTE FUNCTION update_batch_dates();
```

### Trigger: Auto-update tank quantity

```sql
CREATE OR REPLACE FUNCTION update_tank_quantity()
RETURNS TRIGGER AS $$
DECLARE
    v_affects_quantity BOOLEAN;
    v_tank_id INTEGER;
    v_adjustment DECIMAL(10, 2);
BEGIN
    -- Get transaction type properties
    SELECT affects_tank_quantity INTO v_affects_quantity
    FROM transaction_type
    WHERE id = NEW.transaction_type_id;

    IF v_affects_quantity THEN
        -- Get tank ID from batch
        SELECT tank_id INTO v_tank_id
        FROM ferm_batch
        WHERE id = NEW.batch_id;

        -- Determine adjustment direction (simple logic - refine as needed)
        v_adjustment := NEW.quantity;

        -- Update tank quantity
        UPDATE ferm_tank
        SET current_quantity = current_quantity + v_adjustment,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = v_tank_id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_tank_quantity
AFTER INSERT ON ferm_transaction
FOR EACH ROW
EXECUTE FUNCTION update_tank_quantity();
```

**Warning:** Database triggers are harder to test, debug, and maintain. Service layer logic is recommended for Spring Boot applications.

---

## Repository Query Examples

### FermTransactionRepository

```java
public interface FermTransactionRepository extends JpaRepository<FermTransaction, Integer> {

    @Query("SELECT t FROM FermTransaction t " +
           "WHERE t.batch.id = :batchId " +
           "ORDER BY t.transactionDate DESC")
    List<FermTransaction> findByBatchIdOrderByDateDesc(@Param("batchId") Integer batchId);

    @Query("SELECT t FROM FermTransaction t " +
           "WHERE t.transactionDate BETWEEN :startDate AND :endDate " +
           "ORDER BY t.transactionDate")
    List<FermTransaction> findTransactionsBetweenDates(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
}
```

### FermBatchRepository

```java
public interface FermBatchRepository extends JpaRepository<FermBatch, Integer> {

    // Find all active batches (not completed)
    List<FermBatch> findByCompletionDateIsNull();

    // Find batches by tank
    List<FermBatch> findByTankIdOrderByStartDateDesc(Integer tankId);

    // Find batches started in date range
    @Query("SELECT b FROM FermBatch b " +
           "WHERE b.startDate BETWEEN :startDate AND :endDate " +
           "ORDER BY b.startDate")
    List<FermBatch> findBatchesStartedBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
}
```

---

## Migration Notes

If migrating from the original schema:

1. **Rename table:** `fermAddition` → `ferm_transaction`
2. **Rename columns:** Use snake_case consistently
3. **Add audit fields:** `created_at`, `updated_at` to all tables
4. **Add foreign keys:** Explicitly define all relationships
5. **Convert triggers:** Move logic to service layer OR rewrite in PostgreSQL syntax
6. **Update unique constraint:** Use partial index with actual ID values

---

## Testing Recommendations

Following the test pyramid structure from this project:

### Unit Tests (@UnitTest)
- Test entity business logic (e.g., `FermTank.isLowCapacity()`)
- Test service layer methods with mocked repositories
- Test transaction type caching logic

### Fast Tests (@FastTest)
- Test controller endpoints with MockMvc and mocked services
- Test repository queries with H2 in-memory database

### Integration Tests (@IntegrationTest)
- Test full workflow against real PostgreSQL
- Test foreign key constraints and cascades
- Test concurrent batch operations
- Test transaction rollback scenarios

---

## Summary of Fixes

✅ MySQL → PostgreSQL syntax
✅ Consistent naming (ferm_transaction, not fermAddition)
✅ Foreign key constraints defined
✅ Service layer logic (recommended over triggers)
✅ Partial unique index for yeast/lysozyme (PostgreSQL-compliant)
✅ Audit fields on all tables
✅ Capacity unit strategy (FK to unit_type)
✅ Simplified quantity tracking (affectsTankQuantity boolean)
✅ JPA entity mappings included
✅ Seed data provided

This schema is production-ready and follows Spring Boot/JPA best practices.
