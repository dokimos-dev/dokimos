package dev.dokimos.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tuning knobs for trace ingestion and online evaluation, bound from the {@code dokimos.trace} prefix.
 * {@code retentionDays} sets how long an ingested trace is kept before the sweeper deletes it.
 * {@code sweepIntervalMs} sets how often the retention sweeper runs. The {@code eval.*} knobs mirror the
 * judge worker: {@code pollIntervalMs} between polls, {@code maxAttempts} retry ceiling per job, and
 * {@code claimTimeoutMs} before a claimed job is treated as orphaned and requeued.
 */
@Component
@ConfigurationProperties(prefix = "dokimos.trace")
public class TraceProperties {

    private int retentionDays = 30;
    private long sweepIntervalMs = 3_600_000;
    private final Eval eval = new Eval();

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public long getSweepIntervalMs() {
        return sweepIntervalMs;
    }

    public void setSweepIntervalMs(long sweepIntervalMs) {
        this.sweepIntervalMs = sweepIntervalMs;
    }

    public Eval getEval() {
        return eval;
    }

    /** Online trace eval worker knobs, bound from {@code dokimos.trace.eval}. */
    public static class Eval {
        private long pollIntervalMs = 5000;
        private int maxAttempts = 3;
        private long claimTimeoutMs = 600_000;

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getClaimTimeoutMs() {
            return claimTimeoutMs;
        }

        public void setClaimTimeoutMs(long claimTimeoutMs) {
            this.claimTimeoutMs = claimTimeoutMs;
        }
    }
}
