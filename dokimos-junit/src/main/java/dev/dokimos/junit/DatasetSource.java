package dev.dokimos.junit;

import org.junit.jupiter.params.provider.ArgumentsSource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provides {@code Example}s from a {@code Dataset} as arguments to a
 * parameterized test.
 *
 * <p>
 * Supports multiple source types:
 * <ul>
 * <li>{@code classpath:datasets/dataset.json} - classpath resource</li>
 * <li>{@code file:path/to/dataset.json} - file path</li>
 * <li>{@code path/to/dataset.json} - file path (default)</li>
 * <li>Inline JSON via {@link #json()}</li>
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

}
