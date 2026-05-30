package dev.dokimos.server.repository;

import dev.dokimos.server.entity.TraceSpan;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TraceSpanRepository extends JpaRepository<TraceSpan, UUID> {

    /** Returns the spans of a trace ordered by start time, nulls last for spans missing a timestamp. */
    List<TraceSpan> findByTrace_IdOrderByStartTimeUnixNanoAsc(UUID tracePk);
}
