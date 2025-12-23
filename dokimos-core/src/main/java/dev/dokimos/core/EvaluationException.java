package dev.dokimos.core;

/**
 * Thrown when an evaluation cannot be executed successfully.
 */
public class EvaluationException extends RuntimeException {

    public EvaluationException(String message) {
        super(message);
    }
    
    public EvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
