package dev.dokimos.server.judge;

/**
 * Unchecked failure raised when a judge HTTP call cannot complete. The {@code httpStatus} carries the
 * response status, or {@code -1} for a network or timeout error, so the worker can distinguish
 * retryable failures (5xx, timeout) from non-retryable ones (4xx).
 */
public class JudgeCallException extends RuntimeException {

    /** Status used when the failure is a network error or timeout rather than an HTTP response. */
    public static final int NETWORK_ERROR = -1;

    private final int httpStatus;

    public JudgeCallException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public JudgeCallException(int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    /** Returns true when the failure is worth retrying: a timeout, network error, or 5xx response. */
    public boolean isRetryable() {
        return httpStatus == NETWORK_ERROR || httpStatus >= 500;
    }
}
