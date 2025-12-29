package dev.dokimos.core;

import java.util.Map;
import java.util.UUID;

/**
 * A no-op implementation of {@link Reporter} that does nothing.
 * <p>
 * This is the default reporter used when no reporter is configured.
 */
public final class NoOpReporter implements Reporter {

    /**
     * Singleton instance of the no-op reporter.
     */
    public static final NoOpReporter INSTANCE = new NoOpReporter();

    private NoOpReporter() {
    }

    @Override
    public RunHandle startRun(String experimentName, Map<String, Object> metadata) {
        return new RunHandle(UUID.randomUUID().toString());
    }

    @Override
    public void reportItem(RunHandle handle, ItemResult result) {
        // no-op
    }

    @Override
    public void completeRun(RunHandle handle, RunStatus status) {
        // no-op
    }

    @Override
    public void flush() {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }
}
