-- Server-owned versioned datasets. A dataset is a named container with one or more
-- immutable versions; each version owns an ordered list of items keyed by ordinal so
-- runs can pair items across versions by (dataset_version_id, ordinal) or by item id.
CREATE TABLE datasets (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    tenant_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_datasets_name ON datasets(name);

CREATE TABLE dataset_versions (
    id UUID PRIMARY KEY,
    dataset_id UUID NOT NULL REFERENCES datasets(id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(255),
    item_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_dataset_version UNIQUE (dataset_id, version)
);

CREATE INDEX idx_dataset_versions_dataset_id ON dataset_versions(dataset_id);

CREATE TABLE dataset_items (
    id UUID PRIMARY KEY,
    dataset_version_id UUID NOT NULL REFERENCES dataset_versions(id) ON DELETE CASCADE,
    ordinal INTEGER NOT NULL,
    inputs JSONB NOT NULL,
    expected_outputs JSONB,
    metadata JSONB,
    CONSTRAINT uq_dataset_item_ordinal UNIQUE (dataset_version_id, ordinal)
);

CREATE INDEX idx_dataset_items_version_id ON dataset_items(dataset_version_id);

-- Link runs to the dataset version they executed against. ON DELETE SET NULL so deleting
-- a dataset version (or its parent dataset, which cascades) leaves the historical run
-- intact but unlinked; the run's items are preserved as point-in-time evidence.
ALTER TABLE experiment_runs ADD COLUMN dataset_version_id UUID REFERENCES dataset_versions(id) ON DELETE SET NULL;
CREATE INDEX idx_experiment_runs_dataset_version_id ON experiment_runs(dataset_version_id);
