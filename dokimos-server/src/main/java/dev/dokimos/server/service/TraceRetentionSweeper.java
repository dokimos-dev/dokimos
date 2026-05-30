package dev.dokimos.server.service;

import dev.dokimos.server.repository.TraceRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodically deletes traces whose retention window has closed. Spans and online eval jobs cascade away
 * via their foreign keys. The schedule mirrors the judge worker's {@code @Scheduled} pattern, driven by
 * {@code dokimos.trace.sweep-interval-ms}.
 */
@Component
public class TraceRetentionSweeper {

    private static final Logger LOGGER = LoggerFactory.getLogger(TraceRetentionSweeper.class);

    private final TraceRepository traceRepository;

    public TraceRetentionSweeper(TraceRepository traceRepository) {
        this.traceRepository = traceRepository;
    }

    /** Deletes expired traces. The fixed delay is read from {@code dokimos.trace.sweep-interval-ms}. */
    @Scheduled(fixedDelayString = "${dokimos.trace.sweep-interval-ms:3600000}")
    @Transactional
    public void sweep() {
        int deleted = traceRepository.deleteExpired(Instant.now());
        if (deleted > 0) {
            LOGGER.info("Retention sweep deleted {} expired trace(s)", deleted);
        }
    }
}
