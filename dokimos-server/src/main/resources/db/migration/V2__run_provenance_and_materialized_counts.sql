-- Provenance metadata for experiment runs (all nullable)
ALTER TABLE experiment_runs
    ADD COLUMN name VARCHAR(255),
    ADD COLUMN git_sha VARCHAR(64),
    ADD COLUMN git_branch VARCHAR(255),
    ADD COLUMN triggered_by VARCHAR(255);

-- Materialized pass-rate counts. Written by the single writer (RunService.updateRun)
-- when a run reaches a terminal status. pass_rate is null until then.
ALTER TABLE experiment_runs
    ADD COLUMN item_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN passed_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN pass_rate DOUBLE PRECISION;

-- Multi-tenancy seam. Nullable, not enforced yet.
ALTER TABLE experiment_runs ADD COLUMN tenant_id VARCHAR(255);
ALTER TABLE projects ADD COLUMN tenant_id VARCHAR(255);
ALTER TABLE experiments ADD COLUMN tenant_id VARCHAR(255);

-- Per-eval provenance metadata.
ALTER TABLE eval_results ADD COLUMN metadata JSONB;

-- Backfill materialized counts for existing runs.
-- Only terminal runs are backfilled: the materialized counts are written solely at completion,
-- and a RUNNING run's pass_rate must stay NULL until it reaches a terminal status. RUNNING runs
-- therefore keep their column defaults (item_count 0, passed_count 0, pass_rate NULL).
-- An item passes iff it has at least one eval result AND none of its evals failed,
-- mirroring ItemResultRepository.countItemsWithAllEvalsPassed exactly.
UPDATE experiment_runs r
SET item_count = (
    SELECT COUNT(*) FROM item_results i WHERE i.run_id = r.id
)
WHERE r.status IN ('SUCCESS', 'FAILED', 'CANCELLED');

UPDATE experiment_runs r
SET passed_count = (
    SELECT COUNT(*) FROM item_results i
    WHERE i.run_id = r.id
      AND EXISTS (SELECT 1 FROM eval_results e WHERE e.item_result_id = i.id)
      AND NOT EXISTS (SELECT 1 FROM eval_results e WHERE e.item_result_id = i.id AND e.success = false)
)
WHERE r.status IN ('SUCCESS', 'FAILED', 'CANCELLED');

UPDATE experiment_runs r
SET pass_rate = CASE
    WHEN r.item_count > 0 THEN r.passed_count::double precision / r.item_count
    ELSE NULL
END
WHERE r.status IN ('SUCCESS', 'FAILED', 'CANCELLED');
