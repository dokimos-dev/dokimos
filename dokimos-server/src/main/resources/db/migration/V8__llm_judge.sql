-- Server-side LLM judge subsystem: reusable OpenAI-compatible connections and a poll-and-claim job
-- queue for scoring run items. A connection stores exactly one credential source, enforced by the
-- check constraint as a backstop to the request-level validation: either an external credential_ref
-- (an environment variable name) or an inline encrypted_api_key, never both and never neither.
CREATE TABLE llm_connections (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    base_url VARCHAR(512) NOT NULL,
    model VARCHAR(255) NOT NULL,
    credential_ref VARCHAR(255),
    encrypted_api_key TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_llm_connection_credential CHECK ((credential_ref IS NULL) <> (encrypted_api_key IS NULL))
);

-- A job scores every not-yet-evaluated item of a run with one evaluator configuration. The unique
-- constraint on (run_id, evaluator_name) keeps a single job per evaluator per run. Deleting a run
-- cascades to its jobs; deleting a connection is blocked while a job references it.
CREATE TABLE eval_jobs (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES experiment_runs(id) ON DELETE CASCADE,
    connection_id UUID NOT NULL REFERENCES llm_connections(id),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    evaluator_name VARCHAR(255) NOT NULL,
    criteria TEXT NOT NULL,
    evaluation_params VARCHAR(255) NOT NULL,
    min_score DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    max_score DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    threshold DOUBLE PRECISION,
    last_item_id UUID,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_eval_job_run_evaluator UNIQUE (run_id, evaluator_name)
);

-- The worker polls for the oldest pending job; a partial index keeps that scan cheap.
CREATE INDEX idx_eval_jobs_pending ON eval_jobs(created_at) WHERE status = 'PENDING';
CREATE INDEX idx_eval_jobs_run_id ON eval_jobs(run_id);
