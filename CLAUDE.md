# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.5.5 web application for inventory management ("Trailbreaker Inventory"), using Java 21, PostgreSQL, JPA/Hibernate, Thymeleaf, and Lombok.

**Package structure:**
- Main application: `com.TbInventory`
- Test utilities: `com.example.test`
- Web controllers: `com.TbInventory.web`

## Build & Run Commands

**Run the application with PostgreSQL (default):**
```bash
mvn spring-boot:run
```

**Run the application with H2 in-memory database (for frontend development without external DB):**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**Build:**
```bash
mvn clean install
```

**Run tests (default - uses mock DataSource):**
```bash
mvn test
```

**Run tests against real PostgreSQL database:**
```bash
mvn -P real-db-test test
```

**Run single test:**
```bash
mvn test -Dtest=TbInvApplicationTests#testDatabaseConnection
```

**Run single test against real DB:**
```bash
mvn test -Dtest=TbInvApplicationTests#testDatabaseConnection -Dspring.profiles.active=real
```

**Override DB credentials from CLI:**
```bash
mvn -P real-db-test test -DTEST_DB_URL=jdbc:postgresql://HOST:PORT/DB -DTEST_DB_USERNAME=user -DTEST_DB_PASSWORD=pass
```

## Testing Architecture

**Two-mode testing system:**

1. **Mock mode (default)**: Fast unit tests with a Mockito-backed DataSource. No real database needed.
   - Activated by default when running `mvn test`
   - Configuration: `TbInvApplicationTests.MockDataSourceConfig` provides the mock DataSource
   - Property: `test.datasource.mock=true` (default in `src/test/resources/application.properties`)

2. **Real DB mode (opt-in)**: Integration tests against a live PostgreSQL instance.
   - Activated via Maven profile: `mvn -P real-db-test test`
   - OR via Spring profile: `mvn test -Dspring.profiles.active=real`
   - Configuration: `src/test/resources/application-real.properties`
   - Includes HikariCP fail-fast settings (3-second connection timeout) for quick failure when DB is unavailable

## Database Configuration

**Production database (default profile):**
- File: `src/main/resources/application.properties`
- URL: `jdbc:postgresql://192.168.0.160:5432/tbdatabase`
- Credentials: admin/admin (hardcoded - should be externalized)
- Hibernate DDL: `validate` (does not auto-create schema)

**Development database (dev profile):**
- File: `src/main/resources/application-dev.properties`
- Database: H2 in-memory (`jdbc:h2:mem:testdb`)
- Use for frontend development without external PostgreSQL
- Hibernate DDL: `create-drop` (auto-creates schema on startup)
- H2 console available at `http://localhost:8080/h2-console`

**Test database:**
- Mock mode: No real database, fully mocked DataSource via Mockito
- Real mode: Uses same connection as production by default (from `application-real.properties`)
- Override via environment variables or system properties: `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`

## Key Files & Configuration

**Maven configuration:**
- `pom.xml` - Spring Boot parent 3.5.5, PostgreSQL driver, H2 driver, Lombok annotation processing
- Profile `real-db-test` activates Spring profile `real` for integration tests

**Application configuration:**
- `src/main/resources/application.properties` - default production settings with PostgreSQL
- `src/main/resources/application-dev.properties` - H2 in-memory database for frontend development

**Test configuration:**
- `src/test/resources/application.properties` - defaults for tests (mock=true)
- `src/test/resources/application-real.properties` - real DB settings when `real` profile active
- `TbInvApplicationTests.java:64-80` - MockDataSourceConfig with `@ConditionalOnProperty` ensuring mock is only used when `test.datasource.mock=true`

**Application entry point:**
- `com.TbInventory.TbInvApplication` - standard Spring Boot main class

**Web layer:**
- Controllers in `com.TbInventory.web` package
- Thymeleaf templates in `src/main/resources/templates/`
- Example: `HomeController` serves `/` and `/home` routes, renders `index.html`

## Architecture Notes

- **Three-environment architecture**:
  - Production mode (default): PostgreSQL at 192.168.0.160
  - Dev mode (`-Dspring-boot.run.profiles=dev`): H2 in-memory for frontend work without external DB
  - Test mode: Mock DataSource by default, real DB with `-P real-db-test`
- **Fail-fast connections**: HikariCP configured with 3-second connection timeout in real DB test mode for quick failure when database is unavailable
- **Conditional configuration**: The mock DataSource is only registered when `test.datasource.mock=true` via `@ConditionalOnProperty(matchIfMissing=true)`, ensuring clean separation between mock and real DB test modes

---

## 🚧 Current Development: Fermenters Feature

**Status:** Planning/Design Phase - Database schema corrected, ready for implementation

### Database Schema

The corrected PostgreSQL schema is documented in: **`src/database_schema_corrected.md`**

This schema includes:
- **5 core tables**: `unit_type`, `transaction_type`, `ferm_tank`, `ferm_batch`, `ferm_transaction`
- **Foreign key constraints** for referential integrity
- **Check constraints** for data validation (positive quantities, capacity limits)
- **Partial unique indexes** for business rules (one yeast/lysozyme per batch)
- **Audit fields** on all tables (`created_at`, `updated_at`)
- **Seed data** for lookup tables (units and transaction types)

**Key design decisions:**
- ✅ Business logic in **service layer** (not database triggers)
- ✅ PostgreSQL-compliant syntax (not MySQL)
- ✅ Consistent snake_case naming in DB, camelCase in Java
- ✅ Transaction type caching pattern for performance

### Implementation Roadmap

**Phase 1: Domain Layer - JPA Entities (5 files to create)**
1. `src/main/java/com/TbInventory/model/UnitType.java`
2. `src/main/java/com/TbInventory/model/TransactionType.java`
3. `src/main/java/com/TbInventory/model/FermTank.java`
4. `src/main/java/com/TbInventory/model/FermBatch.java`
5. `src/main/java/com/TbInventory/model/FermTransaction.java`

**Entity requirements:**
- Use Lombok `@Data`, `@NoArgsConstructor`
- JPA annotations: `@Entity`, `@Table`, `@Id`, `@GeneratedValue`
- Jakarta validation: `@NotBlank`, `@Min`, etc.
- Null-safe business logic methods (following `Item.java` pattern)
- `@ManyToOne` relationships for foreign keys

**Example structure** (see `database_schema_corrected.md` for full entity code):
```java
@Entity
@Table(name = "ferm_tank")
@Data
@NoArgsConstructor
public class FermTank {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String label;
    private BigDecimal currentQuantity = BigDecimal.ZERO;
    private BigDecimal capacity;

    @ManyToOne
    @JoinColumn(name = "capacity_unit_id")
    private UnitType capacityUnit;

    // Business logic
    public boolean isLowCapacity(int thresholdPercent) { ... }
}
```

---

**Phase 2: Data Access Layer - Repositories (5 files to create)**
1. `src/main/java/com/TbInventory/repository/UnitTypeRepository.java`
2. `src/main/java/com/TbInventory/repository/TransactionTypeRepository.java`
3. `src/main/java/com/TbInventory/repository/FermTankRepository.java`
4. `src/main/java/com/TbInventory/repository/FermBatchRepository.java`
5. `src/main/java/com/TbInventory/repository/FermTransactionRepository.java`

**Repository requirements:**
- Extend `JpaRepository<Entity, Integer>`
- Use `@Repository` annotation
- Follow `ItemRepository.java` pattern
- Custom queries using `@Query` for complex lookups

**Key queries to implement:**
```java
// FermTankRepository
List<FermTank> findByLabel(String label);
List<FermTank> findByCurrentBatchIdNotNull(); // Active tanks
boolean existsByLabel(String label);

// FermBatchRepository
List<FermBatch> findByCompletionDateIsNull(); // Active batches
List<FermBatch> findByTankIdOrderByStartDateDesc(Integer tankId);

// FermTransactionRepository
@Query("SELECT t FROM FermTransaction t WHERE t.batch.id = :batchId ORDER BY t.transactionDate DESC")
List<FermTransaction> findByBatchIdOrderByDateDesc(@Param("batchId") Integer batchId);
```

---

**Phase 3: Service Layer - Business Logic (1 file to create)**

**File:** `src/main/java/com/TbInventory/service/FermenterService.java`

**Service requirements:**
- Use `@Service` and `@Transactional`
- Follow `InventoryService.java` pattern
- Implement transaction type caching with `@PostConstruct`
- Auto-update batch dates (yeast, lysozyme) on transaction creation
- Auto-update tank quantities based on transaction type
- Validate capacity and quantity constraints

**Key methods to implement:**
```java
@Service
@Transactional
public class FermenterService {
    // Caching (see database_schema_corrected.md lines 96-126)
    private Map<Integer, TransactionType> transactionTypeCache;

    @PostConstruct
    public void loadTransactionTypes() { ... }

    // Tank management
    List<FermTank> getAllTanks();
    FermTank findTankById(Integer id);
    FermTank createTank(String label, BigDecimal capacity, Integer capacityUnitId);

    // Batch management
    FermBatch startBatch(Integer tankId, String batchName);
    FermBatch completeBatch(Integer batchId);
    FermBatch getBatchDetails(Integer batchId);

    // Transaction management
    FermTransaction addTransaction(Integer batchId, Integer transactionTypeId,
                                   BigDecimal quantity, Integer userId, String notes);
    List<FermTransaction> getBatchTransactions(Integer batchId);
}
```

**Business logic to implement:**
1. When adding yeast transaction → update `FermBatch.yeastDate`
2. When adding lysozyme transaction → update `FermBatch.lysozymeDate`
3. When transaction type affects quantity → update `FermTank.currentQuantity`
4. When completing batch → clear tank's `currentBatchId` and reset `currentQuantity`
5. Validate: no transactions on completed batches
6. Validate: quantity doesn't exceed tank capacity
7. Validate: quantity doesn't go negative

**Custom exceptions to create:**
- `TankNotFoundException`
- `BatchNotFoundException`
- `InvalidTransactionException`

---

**Phase 4: Controller Layer - Web Endpoints (1 file to enhance)**

**File:** `src/main/java/com/TbInventory/web/FermentersController.java` (already exists as stub)

**Endpoints to implement:**
```java
@Controller
public class FermentersController {

    private final FermenterService fermenterService;

    // List all tanks
    @GetMapping("/fermenters")
    public String listTanks(Model model) { ... }

    // Tank detail page
    @GetMapping("/fermenters/{id}")
    public String tankDetails(@PathVariable Integer id, Model model) { ... }

    // Create tank form
    @GetMapping("/fermenters/new")
    public String newTankForm(Model model) { ... }

    @PostMapping("/fermenters")
    public String createTank(@Valid TankForm form, BindingResult result) { ... }

    // Start batch
    @PostMapping("/fermenters/{id}/batch")
    public String startBatch(@PathVariable Integer id, @Valid BatchForm form) { ... }

    // Add transaction
    @PostMapping("/fermenters/batch/{batchId}/transaction")
    public String addTransaction(@PathVariable Integer batchId,
                                @Valid TransactionForm form) { ... }

    // Complete batch
    @PostMapping("/fermenters/batch/{batchId}/complete")
    public String completeBatch(@PathVariable Integer batchId) { ... }
}
```

**DTOs/Forms to create:**
- `TankForm` - for creating/editing tanks
- `BatchForm` - for starting new batches
- `TransactionForm` - for adding transactions

---

**Phase 5: View Layer - Thymeleaf Template (1 file to enhance)**

**File:** `src/main/resources/templates/fermenters.html` (currently placeholder)

**View components to implement:**

1. **Tank Grid View** (main page)
   - Bootstrap cards showing each tank
   - Display: label, current batch name, quantity/capacity ratio
   - Visual indicators: color-coded by capacity % (green > 50%, yellow 25-50%, red < 25%)
   - "Empty" badge if no active batch
   - Click to view details

2. **Tank Detail Section/Modal**
   - Tank information (label, capacity)
   - Current batch info (name, start date, days in fermentation)
   - Transaction history table (date, type, quantity, user, notes)
   - Forms for: start batch, add transaction, complete batch

3. **Forms**
   - Create tank form (label, capacity, unit dropdown)
   - Start batch form (batch name)
   - Add transaction form (type dropdown, quantity, notes)

4. **Transaction History Table**
   - Columns: Date, Type, Quantity, User, Notes
   - Sortable by date (newest first)
   - Color-coded: additions (green), removals (red), notes (gray)

**Template structure:**
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
    <th:block th:replace="~{/layouts/base :: head(${title})}"></th:block>
    <body>
        <div th:replace="~{/layouts/base :: header}"></div>
        <div th:replace="~{/layouts/base :: navbar}"></div>

        <main>
            <div class="container">
                <h1 th:text="${title}">Fermenters</h1>

                <!-- Tank Grid -->
                <div class="row">
                    <div class="col-md-4" th:each="tank : ${tanks}">
                        <div class="card" th:classappend="${tank.currentBatchId == null} ? 'border-secondary' : 'border-primary'">
                            <div class="card-body">
                                <h5 class="card-title" th:text="${tank.label}">Tank Label</h5>
                                <p th:if="${tank.currentBatchId != null}">
                                    Batch: <span th:text="${tank.currentBatchName}">Batch Name</span>
                                </p>
                                <p th:if="${tank.currentBatchId == null}" class="text-muted">Empty</p>
                                <p>
                                    Quantity: <span th:text="${tank.currentQuantity}">0</span> /
                                    <span th:text="${tank.capacity}">100</span>
                                    <span th:text="${tank.capacityUnit.abbreviation}">bbls</span>
                                </p>
                                <a th:href="@{/fermenters/{id}(id=${tank.id})}" class="btn btn-sm btn-primary">View Details</a>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Create Tank Button -->
                <a href="/fermenters/new" class="btn btn-success mt-3">Create New Tank</a>
            </div>
        </main>
    </body>
</html>
```

---

**Phase 6: Test Coverage (3 files to create)**

Following the test pyramid structure (see README.md Testing section):

### 1. Unit Tests (@UnitTest)
**File:** `src/test/java/com/TbInventory/service/FermenterServiceTest.java`

**Tests to write (~20 tests):**
- Tank CRUD operations
- Batch lifecycle (start, complete)
- Transaction creation with quantity updates
- Transaction type caching
- Validation (capacity limits, negative quantities, completed batches)
- Custom exceptions

**Example:**
```java
@UnitTest
class FermenterServiceTest {
    @Mock private FermTankRepository tankRepository;
    @Mock private FermBatchRepository batchRepository;
    @Mock private FermTransactionRepository transactionRepository;

    @InjectMocks private FermenterService fermenterService;

    @Test
    void addTransaction_WithYeastType_UpdatesYeastDate() { ... }

    @Test
    void addTransaction_ExceedsCapacity_ThrowsException() { ... }

    @Test
    void completeBatch_ClearsTankReference() { ... }
}
```

### 2. Fast Tests (@FastTest)
**File:** `src/test/java/com/TbInventory/web/FermentersControllerTest.java`

**Tests to write (~8 tests):**
- GET /fermenters returns tank list
- GET /fermenters/{id} returns tank details
- POST /fermenters creates tank
- POST /fermenters/{id}/batch starts batch
- POST /fermenters/batch/{id}/transaction adds transaction
- Form validation errors

**Example:**
```java
@FastTest
@WebMvcTest(FermentersController.class)
class FermentersControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private FermenterService fermenterService;

    @Test
    void listTanks_ReturnsView() throws Exception {
        mockMvc.perform(get("/fermenters"))
            .andExpect(status().isOk())
            .andExpect(view().name("fermenters"));
    }
}
```

### 3. Integration Tests (@IntegrationTest)
**File:** `src/test/java/com/TbInventory/integration/FermenterIntegrationTest.java`

**Tests to write (~10 tests):**
- Full workflow: create tank → start batch → add transactions → complete batch
- Transaction quantity rollup accuracy
- Foreign key constraint validation
- Concurrent batch operations
- Transaction rollback scenarios

**Example:**
```java
@IntegrationTest
@Transactional
class FermenterIntegrationTest {
    @Autowired private FermenterService fermenterService;
    @Autowired private FermTankRepository tankRepository;

    @Test
    void fullBatchWorkflow_UpdatesQuantitiesCorrectly() {
        // Create tank → start batch → add yeast → transfer out → complete
        // Assert: all quantities and dates updated correctly
    }
}
```

---

### Database Setup

Before implementation, create the database schema using the DDL from `database_schema_corrected.md`:

**Option 1: Run SQL manually**
```bash
psql -h 192.168.0.160 -U admin -d tbdatabase -f src/database_schema_corrected.sql
```

**Option 2: Use Hibernate auto-DDL (dev/test only)**
Set `spring.jpa.hibernate.ddl-auto=create` in `application-dev.properties` (already set for H2)

**Option 3: Use Flyway/Liquibase migrations**
- Create migration files from the DDL in `database_schema_corrected.md`
- Recommended for production

---

### Implementation Order

1. ✅ **Schema design** (completed - `database_schema_corrected.md`)
2. ⬜ **Database setup** (run DDL on PostgreSQL - can use H2 for now)
3. ✅ **Phase 1: Entities** (5 files - COMPLETED)
4. ✅ **Phase 2: Repositories** (5 files - COMPLETED)
5. ✅ **Phase 3: Service layer** (1 file - COMPLETED)
6. 🚧 **NEXT: Write tests for Phases 1-3** (CRITICAL - test what we built)
7. ⬜ **Phase 4: Controller** (enhance existing file)
8. ⬜ **Fast tests for controller** (8 tests with MockMvc)
9. ⬜ **Phase 5: View template** (enhance fermenters.html)
10. ⬜ **Integration tests** (10 full-stack tests)
11. ⬜ **Manual testing** (verify with `mvn spring-boot:run`)

**Files created so far:** 11 files (committed: dd433bd)
- ✅ 5 entities (UnitType, TransactionType, FermTank, FermBatch, FermTransaction)
- ✅ 5 repositories (with custom queries)
- ✅ 1 service (FermenterService with complete business logic)

**Files still to create:** ~7 files
- 1 controller (existing stub to enhance)
- 1 template (existing placeholder to enhance)
- 3 test files (PRIORITY)
- 2 DTOs/Forms

---

## 📋 RESUME HERE TOMORROW

**Current Status:** Phases 1-3 complete (backend done), committed to git

**Next Session Tasks:**

### Priority 1: Write Tests for Existing Code (CRITICAL)
Before continuing with controller/views, we MUST test what we've built:

1. **Unit Tests for Service** (`FermenterServiceTest.java`) - ~20 tests
   - Tank CRUD with validation
   - Batch creation with initial transaction
   - Transaction logic (yeast/lysozyme dates, quantity updates)
   - Batch completion
   - Error cases (capacity exceeded, negative quantities, completed batch transactions)

2. **Entity Tests** (optional but recommended)
   - Test business logic methods (`isActive()`, `getDaysInFermentation()`, etc.)
   - Test constructors and null safety

3. **Repository Tests** (optional - can use integration tests instead)
   - Test custom queries work as expected

**Run tests with:** `mvn test` (uses mock DataSource by default)

### Priority 2: Continue Implementation
After tests pass:

4. **Phase 4: Controller** - Enhance `FermentersController.java`
   - Implement endpoints for tank list, details, create
   - Implement batch start/complete endpoints
   - Implement transaction add endpoint
   - Create DTOs/Forms (TankForm, BatchForm, TransactionForm)

5. **Phase 5: Views** - Enhance `fermenters.html`
   - Tank grid with Bootstrap cards
   - Tank detail page with transaction history
   - Forms for tank creation, batch start, transaction add

6. **Phase 6: Full Integration Tests**
   - End-to-end workflow tests
   - Run with: `mvn -P real-db-test test` (requires PostgreSQL)

---

### Quick Reference Commands

```bash
# Run tests (mock mode - fast)
mvn test

# Run specific test
mvn test -Dtest=FermenterServiceTest

# Run with real PostgreSQL (when ready)
mvn -P real-db-test test

# Run application with H2 (no PostgreSQL needed)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**Reference files for patterns:**
- Entity pattern: `src/main/java/com/TbInventory/model/Item.java`
- Repository pattern: `src/main/java/com/TbInventory/repository/ItemRepository.java`
- Service pattern: `src/main/java/com/TbInventory/service/InventoryService.java`
- Controller pattern: `src/main/java/com/TbInventory/web/HomeController.java`
- Template pattern: `src/main/resources/templates/index.html`
- Unit test pattern: `src/test/java/com/TbInventory/service/InventoryServiceTest.java`
- Integration test pattern: `src/test/java/com/TbInventory/integration/InventoryIntegrationTest.java`
