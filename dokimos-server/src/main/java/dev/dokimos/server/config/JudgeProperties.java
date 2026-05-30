package dev.dokimos.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tuning knobs for the background judge worker, bound from the {@code dokimos.judge} prefix.
 * {@code pollIntervalMs} sets how often the worker polls for claimable jobs, {@code maxAttempts} the
 * retry ceiling per job, {@code pageSize} the number of items scored per page, and
 * {@code claimTimeoutMs} how long a claimed job may run before it is treated as orphaned and requeued.
 */
@Component
@ConfigurationProperties(prefix = "dokimos.judge")
public class JudgeProperties {

    private long pollIntervalMs = 5000;
    private int maxAttempts = 3;
    private int pageSize = 50;
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

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public long getClaimTimeoutMs() {
        return claimTimeoutMs;
    }

    public void setClaimTimeoutMs(long claimTimeoutMs) {
        this.claimTimeoutMs = claimTimeoutMs;
    }
}
