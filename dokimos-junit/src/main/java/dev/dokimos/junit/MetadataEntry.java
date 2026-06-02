package dev.dokimos.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A single typed run-metadata key-value pair for {@link DatasetSource#entries()}.
 *
 * <p>
 * Prefer this over the alternating-string {@link DatasetSource#metadata()} form: the key and value
 * are named explicitly, so a malformed pairing cannot slip past compilation.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MetadataEntry {

    /**
     * The metadata key.
     */
    String key();

    /**
     * The metadata value.
     */
    String value();
}
