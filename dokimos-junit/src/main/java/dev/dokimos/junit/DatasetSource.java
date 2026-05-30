package dev.dokimos.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.ArgumentsSource;

/**
 * Provides {@code Example}s from a {@code Dataset} as arguments to a
 * parameterized test.
 *
 * <p>
 * Supports multiple source types:
 * <ul>
 * <li>{@code classpath:datasets/dataset.json} - classpath JSON resource</li>
 * <li>{@code classpath:datasets/dataset.jsonl} - classpath JSONL resource</li>
 * <li>{@code file:path/to/dataset.json} - file path</li>
 * <li>{@code path/to/dataset.json} - file path (default)</li>
 * <li>Inline JSON via {@link #json()}</li>
 * <li>Inline JSONL via {@link #jsonl()}</li>
 * </ul>
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * @ParameterizedTest
 * @DatasetSource("classpath:datasets/qa.json")
 * void testQa(Example example) {
 *     String answer = aiService.generate(example.input());
 *     Assertions.assertEval(example.toTestCase(answer), evaluators);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ArgumentsSource(DatasetArgumentsProvider.class)
@ExtendWith(DatasetRunExtension.class)
public @interface DatasetSource {

    /**
     * URI of the dataset to load.
     * Supports: classpath:, file:, or plain file paths.
     */
    String value() default "";

    /**
     * Inline JSON dataset. Use this for small/quick tests only.
     */
    String json() default "";

    /**
     * Inline JSONL dataset. Each line is a JSON object.
     * Use this for small/quick tests only.
     */
    String jsonl() default "";

    /**
     * Name of the run opened when a {@link DatasetReporter} field is present.
     * Defaults to the test method name when left empty.
     */
    String experimentName() default "";

    /**
     * Run metadata as alternating key-value pairs (for example
     * {@code {"model", "gpt-4", "temperature", "0"}}). Forwarded to the reporter when a
     * {@link DatasetReporter} field is present. Defaults to no metadata.
     */
    String[] metadata() default {};
}
