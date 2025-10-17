# Database Scheme

dbdiagram.io

List of potential tables:
- Ferm tanks
    - Ferm batches
- Bright tanks
    - Bright batches
    - Additions (Added 30 bbls from fermentor #3.., added 10 bbls of FJ blend..)
- Keg/Canning Records
- Inventory Items
    - Flavors
    - Sizes
    - Quantity
- Outgoing Orders
    - Outgoing Order Items
- Clients
- Cooler locations (capacity of shelf)
- Keg Transfers

## Fermentor Tank

## fermTank
- Label of what the batch will be: Left Turn, Cosmic, etc
- Stores and references ID of the current batch in the tank
- Current quantity, handled by service layer logic
- Capacity, used for service layer logic to ensure
- Capacity unit still needed, not sure what unit to use.

## fermBatch

- Tracks what tank the batch is/was made in for record keeping
- Tracks the date the the batch was first started with service layer logic code.


(Potentially just use service layer logic instead)
Both yeastDate and lysoDate have the following behavior:
-Updated with SQL Trigger after each SQL Insert in fermAddition.    
-Limited to a single Yeast addition per batch. Limited in database with SQL constraint

- Tracks date the batch was completed (Last amount was transferred to tank, marked as waste, etc). Handled with service layer logic.

SQL Constraint
```SQL
-- Add unique constraint to fermAddition table
ALTER TABLE fermAddition 
ADD CONSTRAINT unique_yeast_per_batch 
UNIQUE (batchFermId, additionType) 
WHERE additionType IN (yeast_type_id, lyso_type_id);
```

SQL Trigger
```SQL
CREATE TRIGGER update_batch_dates 
AFTER INSERT ON fermAddition  -- Runs after each INSERT on fermAddition
FOR EACH ROW                  -- Runs once per inserted row
BEGIN
    -- NEW refers to the row being inserted
    IF NEW.additionType = (SELECT id FROM additionType WHERE typeName = 'yeast') THEN
        UPDATE fermBatch 
        SET yeastDate = NEW.dateAdded 
        WHERE batchFermId = NEW.batchFermId;
    END IF;
    
    IF NEW.additionType = (SELECT id FROM additionType WHERE typeName = 'lysozyme') THEN
        UPDATE fermBatch 
        SET lysoDate = NEW.dateAdded 
        WHERE batchFermId = NEW.batchFermId;
    END IF;
END;
```

## fermTransaction
- Used for tracking additions and removals from fermentor batches
- Stores what batch the transaction relates too
- Tracks type of transaction
- Quantity of transaction amount
- Date that the transaction occured
- UserID of who made the transaction
- Any notes the user had about it
- If the transaction was to a bright tank, we reference the bright tank

Notes will be used to say who a sale was too, if we moved to kitchen, or if we drained the remaining of a tank.

For displaying a list of transaction, we will query the fermTransaction table instead of redundant storing and additional tables
```java
@Query("SELECT t FROM FermTransaction t 
WHERE t.batchFermId = :batchId 
ORDER BY t.dateAdded")
List<FermTransaction> getBatchTransactions(@Param("batchId") int batchId);
```
For displaying, to avoid having to query transactionType for each transaction, we can run an initial query to get the transactionTypes and cache them in an array, using that to display the correct transaction type name.

```java
@Service
public class BatchActivityService {
    
    // This Map lives in memory
    private Map<Integer, String> transactionTypeCache = new HashMap<>();
    
    @PostConstruct  // Runs once when application starts
    public void loadTransactionTypes() {
        List<TransactionType> types = transactionTypeRepository.findAll();
        
        // Put all transaction types in memory Map
        for (TransactionType type : types) {
            transactionTypeCache.put(type.getTransactionTypeId(), type.getTypeName());
        }
        
        // Now cache contains: {1="yeast_addition", 2="hc_addition", 3="transfer_out"}
    }
    
    public List<BatchActivityDto> getBatchActivity(int batchId) {
        List<FermTransaction> transactions = fermTransactionRepository.getBatchTransactions(batchId);
        
        return transactions.stream()
            .map(t -> new BatchActivityDto(
                transactionTypeCache.get(t.getTransactionTypeId()), // Gets from memory, not DB!
                t.getDateAdded(),
                t.getQuantity()
            ))
            .collect(toList());
    }
}
```

## transactionType
- Helper table for fermTransaction
- TypeName
- Description
- Unit that the transaction will use

## unitType
- Name of unit
- Abbreviation to use (useless?)
- isVolume column is used to know if we need to add/remove quantity from tank (overcomplicated?)
