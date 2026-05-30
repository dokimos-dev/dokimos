-- Tenant data isolation. Adds the tenant_id seam to the child and config tables that did not yet
-- carry it, and makes the project name unique per tenant instead of globally so two tenants can each
-- own a "default" project. Every new column is nullable with no backfill: with no tenants provisioned
-- yet, all existing rows are legitimately shared (null tenant), which the scoped reads treat as visible
-- to everyone. The system principal (no-key and legacy single-key deployments) reads unrestricted and
-- stamps null, so existing deployments behave exactly as before.

-- Child tables loaded by id straight from user input, previously with no tenant column at all. Each is
-- stamped from its parent on write so a scoped parent query and a scoped child query agree.
ALTER TABLE item_results ADD COLUMN tenant_id VARCHAR(255);
ALTER TABLE eval_results ADD COLUMN tenant_id VARCHAR(255);
ALTER TABLE dataset_versions ADD COLUMN tenant_id VARCHAR(255);
ALTER TABLE dataset_items ADD COLUMN tenant_id VARCHAR(255);
ALTER TABLE trace_spans ADD COLUMN tenant_id VARCHAR(255);

-- Config entity scoped by its own tenant_id, one uniform rule (no "scope by owning project").
ALTER TABLE llm_connections ADD COLUMN tenant_id VARCHAR(255);

-- Per-tenant project names. Drop the global unique on projects.name and replace it with a unique on
-- (name, tenant_id) plus a partial unique on name for the shared (null-tenant) rows, since a unique
-- over (name, tenant_id) does not constrain rows whose tenant_id is null in Postgres.
ALTER TABLE projects DROP CONSTRAINT projects_name_key;
ALTER TABLE projects ADD CONSTRAINT uq_project_name_tenant UNIQUE (name, tenant_id);
CREATE UNIQUE INDEX uq_project_name_shared ON projects(name) WHERE tenant_id IS NULL;

-- Indexes on the filtered tables. The scoped read predicate is tenant_id = :t OR tenant_id IS NULL, so
-- an index on tenant_id keeps the per-tenant scan selective on the high-volume tables.
CREATE INDEX idx_projects_tenant_id ON projects(tenant_id);
CREATE INDEX idx_experiments_tenant_id ON experiments(tenant_id);
CREATE INDEX idx_experiment_runs_tenant_id ON experiment_runs(tenant_id);
CREATE INDEX idx_item_results_tenant_id ON item_results(tenant_id);
CREATE INDEX idx_eval_results_tenant_id ON eval_results(tenant_id);
CREATE INDEX idx_datasets_tenant_id ON datasets(tenant_id);
CREATE INDEX idx_dataset_versions_tenant_id ON dataset_versions(tenant_id);
CREATE INDEX idx_dataset_items_tenant_id ON dataset_items(tenant_id);
CREATE INDEX idx_traces_tenant_id ON traces(tenant_id);
CREATE INDEX idx_trace_spans_tenant_id ON trace_spans(tenant_id);
CREATE INDEX idx_annotations_tenant_id ON annotations(tenant_id);
CREATE INDEX idx_alert_webhooks_tenant_id ON alert_webhooks(tenant_id);
CREATE INDEX idx_trace_eval_rules_tenant_id ON trace_eval_rules(tenant_id);
CREATE INDEX idx_llm_connections_tenant_id ON llm_connections(tenant_id);
