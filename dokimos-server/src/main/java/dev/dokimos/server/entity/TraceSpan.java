package dev.dokimos.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One span of an ingested {@link Trace}. The flattened OTLP attributes are stored as JSONB. The derived
 * {@code inputText} and {@code outputText} are pulled from well-known attribute keys at ingestion time so
 * an online evaluation can score a span without re-parsing its attributes.
 */
@Entity
@Table(name = "trace_spans")
public class TraceSpan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trace_pk", nullable = false)
    private Trace trace;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "span_id", nullable = false, length = 32)
    private String spanId;

    @Column(name = "parent_span_id", length = 32)
    private String parentSpanId;

    @Column(nullable = false, length = 512)
    private String name;

    @Column(length = 32)
    private String kind;

    @Column(name = "status_code", length = 32)
    private String statusCode;

    @Column(name = "start_time_unix_nano")
    private Long startTimeUnixNano;

    @Column(name = "end_time_unix_nano")
    private Long endTimeUnixNano;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> attributes;

    @Column(name = "input_text", columnDefinition = "text")
    private String inputText;

    @Column(name = "output_text", columnDefinition = "text")
    private String outputText;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TraceSpan() {}

    public TraceSpan(String traceId, String spanId, String name, Instant createdAt) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public Trace getTrace() {
        return trace;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public void setTrace(Trace trace) {
        this.trace = trace;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public String getParentSpanId() {
        return parentSpanId;
    }

    public void setParentSpanId(String parentSpanId) {
        this.parentSpanId = parentSpanId;
    }

    public String getName() {
        return name;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
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

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public String getInputText() {
        return inputText;
    }

    public void setInputText(String inputText) {
        this.inputText = inputText;
    }

    public String getOutputText() {
        return outputText;
    }

    public void setOutputText(String outputText) {
        this.outputText = outputText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
