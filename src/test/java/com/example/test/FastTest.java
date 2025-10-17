package com.example.test;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class or method as a fast test.
 * Fast tests run quickly (typically < 1 second) and are suitable for frequent execution during development.
 *
 * Fast tests typically:
 * - Use mocked dependencies
 * - Don't require external resources (databases, networks, etc.)
 * - Test isolated components
 *
 * Usage:
 * <pre>
 * {@code @FastTest}
 * {@code @WebMvcTest(HomeController.class)}
 * class HomeControllerTest {
 *     // test methods
 * }
 * </pre>
 *
 * Run only fast tests:
 * mvn test -Dgroups=fast
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag("fast")
public @interface FastTest {
}
