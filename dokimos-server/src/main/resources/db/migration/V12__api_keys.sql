-- Scoped API keys for role-based access control. Only the SHA-256 hex hash of each key is stored
-- (key_hash), never the raw key; the raw key is returned to the caller exactly once at creation. Each
-- key carries a role bounding what the caller may do (VIEWER reads, EDITOR also writes, ADMIN also
-- manages keys) and an optional tenant_id (the tenant seam, nullable for now). Disabled keys are kept
-- for their audit trail but rejected at authentication time. The legacy single DOKIMOS_API_KEY remains
-- supported independently of this table and maps to ADMIN.
CREATE TABLE api_keys (
    id UUID PRIMARY KEY,
    key_hash VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    tenant_id VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_api_key_role CHECK (role IN ('VIEWER', 'EDITOR', 'ADMIN'))
);

-- Authentication looks up enabled keys by hash; the partial index keeps that hot path cheap.
CREATE INDEX idx_api_keys_enabled_hash ON api_keys(key_hash) WHERE enabled = TRUE;
