package dev.dokimos.core;

/**
 * Thrown when a dataset cannot be correctly resolved or loading fails.
 */
public class DatasetResolutionException extends RuntimeException {

    public DatasetResolutionException(String message) {
        super(message);
    }

    public DatasetResolutionException(String message, Throwable cause) {
        super(message, cause);
    }

}
