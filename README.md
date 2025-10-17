# Trailbreaker Inventory

Spring Boot 3.5.5 web application for inventory management using Java 21, PostgreSQL, JPA/Hibernate, Thymeleaf, and Lombok.

## Running the Application

**With PostgreSQL (default):**
```bash
mvn spring-boot:run
```

**With H2 in-memory database (for frontend development without external DB):**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
When running in dev mode, the H2 console is available at `http://localhost:8080/h2-console`

## Testing

This project uses a **layered testing strategy** following the test pyramid principle: many fast unit tests, fewer integration tests.

### Test Organization

Tests are organized into three categories using custom annotations:

1. **`@UnitTest`** - Pure unit tests (fastest)
   - Test a single class in isolation
   - Use Mockito to mock dependencies
   - No Spring context loaded
   - Run in milliseconds
   - Example: `InventoryServiceTest` (14 tests)

2. **`@FastTest`** - Fast integration tests with mocked infrastructure
   - Test web layer with MockMvc or context loading
   - Use mocked DataSource (no real database)
   - Minimal Spring context
   - Run in seconds
   - Example: `HomeControllerTest` (3 tests), `TbInvApplicationTests` (2 tests)

3. **`@IntegrationTest`** - Full integration tests (slowest)
   - Test full stack with real PostgreSQL database
   - Complete Spring context
   - Test actual database queries and transactions
   - Run in tens of seconds
   - Example: `InventoryIntegrationTest` (10 tests)

### Running Tests

**Fast development workflow (default):**
```bash
mvn test
```
Runs only `@UnitTest` and `@FastTest` (~19 tests in ~24 seconds). No database required.

**Run all tests including integration:**
```bash
mvn test -P all-tests
```
Runs all tests including `@IntegrationTest`. Requires PostgreSQL to be running.

**Run only integration tests:**
```bash
mvn test -P integration-tests
```
Runs only `@IntegrationTest` tests against real database.

**Run tests by specific tag:**
```bash
# Only unit tests (fastest)
mvn test -Dgroups=unit

# Only fast tests
mvn test -Dgroups=fast

# Only integration tests
mvn test -Dgroups=integration
```

**Run a single test:**
```bash
mvn test -Dtest=HomeControllerTest#homeRoute_ReturnsCorrectViewAndModel
```

### Test Pyramid Structure

```
     /\           Integration Tests (10)
    /  \          - Real database
   /____\         - Full Spring context
  /      \        - Slowest, most comprehensive
 /________\
/  Fast    \      Fast Tests (5)
/   Tests   \     - Mocked infrastructure
/____________\    - Partial Spring context
/              \
/  Unit Tests  \  Unit Tests (14)
/_______________\ - Pure logic testing
                  - No Spring, no DB
                  - Fastest execution
```

**Best Practices:**
- Use `mvn test` during development for instant feedback
- Run `mvn test -P all-tests` before committing
- CI/CD should run all tests including integration tests
- Write more unit tests than integration tests (faster feedback loop)

## Database Configuration

The application supports three database configurations:

1. **Production (default)**: PostgreSQL at 192.168.0.160:5432
   - Configuration: `src/main/resources/application.properties`

2. **Development (dev profile)**: H2 in-memory database
   - Configuration: `src/main/resources/application-dev.properties`
   - Use for frontend work without external database

3. **Test (mock by default)**: Mockito-backed DataSource or real PostgreSQL
   - Mock: Default for `mvn test`
   - Real: Use `-P real-db-test` flag

## Build

```bash
mvn clean install
```

## Key Files

**Application:**
- `pom.xml` - Maven configuration with PostgreSQL, H2 drivers, and test profiles
- `src/main/resources/application.properties` - Production database settings
- `src/main/resources/application-dev.properties` - H2 dev database settings
- `src/main/java/com/TbInventory/model/Item.java` - JPA entity with validation
- `src/main/java/com/TbInventory/repository/ItemRepository.java` - Spring Data JPA repository
- `src/main/java/com/TbInventory/service/InventoryService.java` - Business logic layer

**Test Configuration:**
- `src/test/java/com/example/test/FastTest.java` - Custom annotation for fast tests
- `src/test/java/com/example/test/UnitTest.java` - Custom annotation for unit tests
- `src/test/java/com/example/test/IntegrationTest.java` - Custom annotation for integration tests
- `src/test/resources/application.properties` - Test defaults (mock mode)
- `src/test/resources/application-real.properties` - Real DB test settings

**Example Tests:**
- `src/test/java/com/TbInventory/service/InventoryServiceTest.java` - Unit tests (@UnitTest)
- `src/test/java/com/TbInventory/web/HomeControllerTest.java` - Controller tests (@FastTest)
- `src/test/java/com/TbInventory/integration/InventoryIntegrationTest.java` - Integration tests (@IntegrationTest)

## Notes

- Keep `mvn test` fast by using the mock default
- Use `-P real-db-test` for integration testing against real PostgreSQL
- Use dev profile for frontend development without external database dependencies
- Consider externalizing production credentials (currently hardcoded as admin/admin)
