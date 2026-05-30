package dev.dokimos.server.controller;

import java.time.Instant;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "error", "Not Found",
                        "message", ex.getMessage(),
                        "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "error", "Conflict",
                        "message", ex.getMessage(),
                        "timestamp", Instant.now().toString()));
    }

    /**
     * Maps bean-validation failures on {@code @RequestBody} payloads to a 400, surfacing the first
     * field error message so the caller sees which constraint failed instead of an opaque 500.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "error",
                        "Bad Request",
                        "message",
                        message,
                        "timestamp",
                        Instant.now().toString()));
    }

    /**
     * Maps a missing required query parameter (for example {@code baselineRunId} on the diff
     * endpoint) to a 400 rather than letting it fall through to the generic 500 handler.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "error",
                        "Bad Request",
                        "message",
                        "Missing required parameter: " + ex.getParameterName(),
                        "timestamp",
                        Instant.now().toString()));
    }

    /**
     * Maps an unparseable or type-mismatched request body (for example a malformed UUID) to a 400
     * rather than letting it fall through to the generic 500 handler.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "error", "Bad Request",
                        "message", "Malformed request body",
                        "timestamp", Instant.now().toString()));
    }

    /**
     * Honors the status carried by a {@link ResponseStatusException} (for example a 400 raised by a
     * controller for an unrecognized query-parameter value) instead of letting the generic handler
     * collapse it to a 500.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of(
                        "error", ex.getStatusCode().toString(),
                        "message", ex.getReason() != null ? ex.getReason() : "Request failed",
                        "timestamp", Instant.now().toString()));
    }

    /**
     * Maps a database constraint violation to a 409 rather than a 500. This covers the rare race
     * where two concurrent writes both try to create a row guarded by a unique constraint (for
     * example two simultaneous first-time annotations on the same item result); the loser gets a
     * clean conflict it can retry instead of an opaque server error.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "error", "Conflict",
                        "message", "The resource was modified concurrently; retry the request",
                        "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "Internal Server Error",
                        "message", ex.getMessage(),
                        "timestamp", Instant.now().toString()));
    }
}
