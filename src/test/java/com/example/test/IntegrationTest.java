package com.example.test;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class as an integration test.
 * Integration tests are slower but test real interactions between components.
 *
 * Integration tests:
 * - Start the full Spring context
 * - Use a real database (PostgreSQL via 'real' profile)
 * - Test multiple components working together
 * - Should be used sparingly (slower execution)
 *
 * This annotation automatically:
 * - Enables @SpringBootTest (full application context)
 * - Activates the 'real' profile (uses PostgreSQL)
 * - Tags the test as 'integration' for selective execution
 *
 * Usage:
 * <pre>
 * {@code @IntegrationTest}
 * {@code @Transactional} // Optional: rollback after each test
 * class InventoryIntegrationTest {
 *     {@code @Autowired}
 *     private ItemRepository repository;
 *
 *     // test methods that hit the real database
 * }
 * </pre>
 *
 * Run only integration tests (requires PostgreSQL):
 * mvn test -Dgroups=integration
 *
 * Or use the Maven profile:
 * mvn test -P integration-tests
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Tag("integration")
@ActiveProfiles("real")
public @interface IntegrationTest {
}
