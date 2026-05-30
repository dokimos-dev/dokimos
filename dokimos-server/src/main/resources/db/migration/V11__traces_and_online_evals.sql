-- OTLP trace ingestion plus online evaluation over traces. This is a dedicated ingestion path,
-- deliberately separate from the experiment store (item_results), so a columnar store could later
-- replace these tables without touching the experiment subsystem.

-- A trace groups the spans of one distributed execution. Stored per trace so a list view does not have
-- to scan spans. project_id is a soft link populated only when the OTLP resource attributes name a known
-- project; it is nullable and not a foreign key so ingestion never fails on an unknown project and a
-- columnar replacement is not forced to keep referential integrity. expires_at drives the retention
-- sweeper.
CREATE TABLE traces (
    id UUID PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL UNIQUE,
    project_id UUID,
    tenant_id VARCHAR(255),
    root_span_name VARCHAR(512),
    span_count INT NOT NULL DEFAULT 0,
    start_time_unix_nano BIGINT,
    end_time_unix_nano BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_traces_trace_id ON traces(trace_id);
CREATE INDEX idx_traces_created_at ON traces(created_at);
CREATE INDEX idx_traces_expires_at ON traces(expires_at);
CREATE INDEX idx_traces_project_id ON traces(project_id);

-- One span of a trace. attributes holds the flattened OTLP key/value attributes as JSONB. input_text and
-- output_text are the values derived from well-known attribute keys at ingestion time so an online eval
-- can score a span without re-parsing attributes. Deleting a trace cascades to its spans.
CREATE TABLE trace_spans (
    id UUID PRIMARY KEY,
    trace_pk UUID NOT NULL REFERENCES traces(id) ON DELETE CASCADE,
    trace_id VARCHAR(64) NOT NULL,
    span_id VARCHAR(32) NOT NULL,
    parent_span_id VARCHAR(32),
    name VARCHAR(512) NOT NULL,
    kind VARCHAR(32),
    status_code VARCHAR(32),
    start_time_unix_nano BIGINT,
    end_time_unix_nano BIGINT,
    attributes JSONB,
    input_text TEXT,
    output_text TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_trace_spans_trace_pk ON trace_spans(trace_pk);
CREATE INDEX idx_trace_spans_trace_id ON trace_spans(trace_id);
CREATE INDEX idx_trace_spans_name ON trace_spans(name);

-- A per-project rule that, when an ingested trace contains a matching span, enqueues an online judge
-- evaluation of that span's derived output. match_type selects whether match_value is tested against the
-- span name or against an attribute; for ATTRIBUTE matching, match_key names the attribute. The rule
-- carries the judge configuration (evaluator name, criteria, score range, threshold) and the connection
-- used to call the judge. Deleting a project cascades to its rules; deleting a connection is blocked
-- while a rule references it.
CREATE TABLE trace_eval_rules (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    tenant_id VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    match_type VARCHAR(32) NOT NULL,
    match_key VARCHAR(255),
    match_value VARCHAR(512) NOT NULL,
    connection_id UUID NOT NULL REFERENCES llm_connections(id),
    evaluator_name VARCHAR(255) NOT NULL,
    criteria TEXT NOT NULL,
    min_score DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    max_score DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    threshold DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_trace_eval_rule_project_name UNIQUE (project_id, name)
);

CREATE INDEX idx_trace_eval_rules_project_id ON trace_eval_rules(project_id);

-- The online-eval work queue. A job scores one span's derived output against one rule's judge
-- configuration. It mirrors eval_jobs (poll-and-claim with FOR UPDATE SKIP LOCKED, attempt ceiling,
-- claim recovery, credential-sanitized last_error) but is a separate table so the experiment-coupled
-- eval_jobs schema is not overloaded. Deleting a span or rule cascades to its jobs. The result fields
-- are written when a job succeeds. tenant_id is denormalized from the trace for tenant-scoped reads.
CREATE TABLE trace_eval_jobs (
    id UUID PRIMARY KEY,
    span_pk UUID NOT NULL REFERENCES trace_spans(id) ON DELETE CASCADE,
    rule_id UUID NOT NULL REFERENCES trace_eval_rules(id) ON DELETE CASCADE,
    connection_id UUID NOT NULL REFERENCES llm_connections(id),
    tenant_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    evaluator_name VARCHAR(255) NOT NULL,
    criteria TEXT NOT NULL,
    min_score DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    max_score DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    threshold DOUBLE PRECISION,
    score DOUBLE PRECISION,
    success BOOLEAN,
    reason TEXT,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_trace_eval_job_span_rule UNIQUE (span_pk, rule_id)
);

-- The worker polls for the oldest pending job; a partial index keeps that scan cheap.
CREATE INDEX idx_trace_eval_jobs_pending ON trace_eval_jobs(created_at) WHERE status = 'PENDING';
CREATE INDEX idx_trace_eval_jobs_span_pk ON trace_eval_jobs(span_pk);
CREATE INDEX idx_trace_eval_jobs_rule_id ON trace_eval_jobs(rule_id);
