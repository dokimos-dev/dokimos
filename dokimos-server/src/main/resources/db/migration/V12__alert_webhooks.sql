-- Regression alerting: per-project webhooks that receive a JSON payload when a completed run shows a
-- significant pass-rate regression against its resolved baseline. The optional secret signs the body
-- with HMAC-SHA256 so receivers can verify authenticity. Deleting a project cascades to its webhooks.
CREATE TABLE alert_webhooks (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    url VARCHAR(2048) NOT NULL,
    secret TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    tenant_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Dispatch resolves a project's enabled webhooks on every regressing run completion.
CREATE INDEX idx_alert_webhooks_project ON alert_webhooks(project_id) WHERE enabled = TRUE;
