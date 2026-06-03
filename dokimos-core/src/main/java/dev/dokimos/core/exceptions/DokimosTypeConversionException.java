package dev.dokimos.core.exceptions;

/**
 * Thrown when a stored output value cannot be converted to the requested type by one of the typed
 * read accessors (for example {@link dev.dokimos.core.EvalTestCase#actualOutputAs(Class)} or {@link
 * dev.dokimos.core.Example#expectedOutputAs(dev.dokimos.core.OutputType)}).
 *
 * <p>This is an unchecked exception: a conversion failure is a programming/data error (the requested
 * type does not match the shape of the stored value), not a recoverable condition callers are
 * expected to handle on every call. The evaluation path catches it and scores the affected item as
 * failed rather than aborting the whole run.
 *
 * <p>The underlying cause (a Jackson conversion error) is attached via {@link #getCause()} for
 * diagnostics, typed as {@link Throwable}.
 */
public class DokimosTypeConversionException extends RuntimeException {

    /**
     * Creates an exception with the given detail message.
     *
     * @param message a human-readable description of the conversion failure
     */
    public DokimosTypeConversionException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given detail message and underlying cause.
     *
     * @param message a human-readable description of the conversion failure
     * @param cause the underlying error that triggered the failure (for example a Jackson conversion
     *     error), typed as {@link Throwable}
     */
    public DokimosTypeConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
