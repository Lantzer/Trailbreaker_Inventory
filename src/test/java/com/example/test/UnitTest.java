package com.example.test;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class as a pure unit test.
 * Unit tests are the fastest type of test and should form the majority of your test suite.
 *
 * Unit tests:
 * - Test a single class in isolation
 * - Use Mockito to mock dependencies
 * - Don't start the Spring context
 * - Run in milliseconds
 *
 * This annotation automatically enables Mockito via @ExtendWith(MockitoExtension.class)
 *
 * Usage:
 * <pre>
 * {@code @UnitTest}
 * class InventoryServiceTest {
 *     {@code @Mock}
 *     private ItemRepository repository;
 *
 *     {@code @InjectMocks}
 *     private InventoryService service;
 *
 *     // test methods
 * }
 * </pre>
 *
 * Run only unit tests:
 * mvn test -Dgroups=unit
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(MockitoExtension.class)
@Tag("unit")
public @interface UnitTest {
}
