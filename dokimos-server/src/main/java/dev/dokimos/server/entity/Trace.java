package dev.dokimos.server.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A single distributed execution trace ingested over OTLP, holding one or more {@link TraceSpan} rows.
 * Lives in a dedicated ingestion store kept separate from the experiment results so a columnar store
 * could later replace it. {@code projectId} is a soft link, set only when the OTLP resource attributes
 * name a known project, so ingestion never fails on an unknown project. {@code expiresAt} drives the
 * retention sweeper.
 */
@Entity
@Table(name = "traces")
public class Trace {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "trace_id", nullable = false, unique = true, length = 64)
    private String traceId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "root_span_name", length = 512)
    private String rootSpanName;

    @Column(name = "span_count", nullable = false)
    private int spanCount;

    @Column(name = "start_time_unix_nano")
    private Long startTimeUnixNano;

    @Column(name = "end_time_unix_nano")
    private Long endTimeUnixNano;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @OneToMany(mappedBy = "trace", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TraceSpan> spans = new ArrayList<>();

    protected Trace() {}

    public Trace(String traceId, Instant createdAt, Instant expiresAt) {
        this.traceId = traceId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.spanCount = 0;
    }

    public UUID getId() {
        return id;
    }

    public String getTraceId() {
        return traceId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getRootSpanName() {
        return rootSpanName;
    }

    public void setRootSpanName(String rootSpanName) {
        this.rootSpanName = rootSpanName;
    }

    public int getSpanCount() {
        return spanCount;
    }

    public void setSpanCount(int spanCount) {
        this.spanCount = spanCount;
    }

    public Long getStartTimeUnixNano() {
        return startTimeUnixNano;
    }

    public void setStartTimeUnixNano(Long startTimeUnixNano) {
        this.startTimeUnixNano = startTimeUnixNano;
    }

    public Long getEndTimeUnixNano() {
        return endTimeUnixNano;
    }

    public void setEndTimeUnixNano(Long endTimeUnixNano) {
        this.endTimeUnixNano = endTimeUnixNano;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public List<TraceSpan> getSpans() {
        return spans;
    }

    /** Attaches a span to this trace and keeps both ends of the association consistent. */
    public void addSpan(TraceSpan span) {
        spans.add(span);
        span.setTrace(this);
    }
}
