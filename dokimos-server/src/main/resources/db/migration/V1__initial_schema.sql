-- Projects table
CREATE TABLE projects (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_projects_name ON projects(name);

-- Experiments table
CREATE TABLE experiments (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_experiment_project_name UNIQUE (project_id, name)
);

CREATE INDEX idx_experiments_project_id ON experiments(project_id);

-- Experiment runs table
CREATE TABLE experiment_runs (
    id UUID PRIMARY KEY,
    experiment_id UUID NOT NULL REFERENCES experiments(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    config JSONB,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_runs_experiment_id ON experiment_runs(experiment_id);
CREATE INDEX idx_runs_started_at ON experiment_runs(started_at DESC);
CREATE INDEX idx_runs_status ON experiment_runs(status);

-- Item results table
CREATE TABLE item_results (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES experiment_runs(id) ON DELETE CASCADE,
    input TEXT NOT NULL,
    expected_output TEXT,
    actual_output TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_items_run_id ON item_results(run_id);
CREATE INDEX idx_items_created_at ON item_results(created_at);

-- Eval results table
CREATE TABLE eval_results (
    id UUID PRIMARY KEY,
    item_result_id UUID NOT NULL REFERENCES item_results(id) ON DELETE CASCADE,
    evaluator_name VARCHAR(255) NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    threshold DOUBLE PRECISION,
    success BOOLEAN NOT NULL,
    reason TEXT
);

CREATE INDEX idx_evals_item_result_id ON eval_results(item_result_id);
CREATE INDEX idx_evals_success ON eval_results(success);
