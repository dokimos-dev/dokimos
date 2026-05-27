-- Tracks idempotency keys for committed item batches so a retried POST that already
-- succeeded server side is deduplicated instead of double inserting its items.
-- The composite primary key (run_id, idempotency_key) lets the same key be reused
-- across different runs while rejecting a duplicate within a single run.
CREATE TABLE ingested_batches (
    run_id UUID NOT NULL REFERENCES experiment_runs(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (run_id, idempotency_key)
);
