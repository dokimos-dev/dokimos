package dev.dokimos.junit;

import dev.dokimos.core.Reporter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static field that supplies the {@link Reporter} for {@link DatasetSource} tests.
 *
 * <p>
 * Run reporting is opt-in. When a test class declares a static field of type {@link Reporter}
 * annotated with {@code @DatasetReporter}, {@link DatasetRunExtension} opens a run for each
 * {@code @DatasetSource} method and reports every parameterized invocation as an item result.
 * When no such field is present, no run is opened and the test behaves as a plain parameterized
 * test.
 *
 * <p>
 * The annotated field must be {@code static} and assignable to {@link Reporter}. Its lifecycle
 * (creation and {@link Reporter#close() closing}) is owned by the test class; the extension never
 * closes a reporter supplied this way.
 *
 * <pre>{@code
 * class QaEvaluationTest {
 *
 *     @DatasetReporter
 *     static final Reporter reporter = new DokimosServerReporter(...);
 *
 *     @ParameterizedTest
 *     @DatasetSource("classpath:datasets/qa.json")
 *     void testQa(Example example) {
 *         ...
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DatasetReporter {}
