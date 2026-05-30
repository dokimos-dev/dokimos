-- Optional per-item call metrics describing the LLM call that produced an output: prompt and
-- completion tokens, cost, and latency. All nullable so legacy rows and runs that capture no
-- metrics stay valid; no backfill.
ALTER TABLE item_results ADD COLUMN tokens_in INTEGER;
ALTER TABLE item_results ADD COLUMN tokens_out INTEGER;
ALTER TABLE item_results ADD COLUMN cost_usd DOUBLE PRECISION;
ALTER TABLE item_results ADD COLUMN latency_ms BIGINT;

-- Run-level aggregates materialized at run completion, mirroring the existing pass-rate fields.
-- All nullable; absent means no item in the run carried that metric.
ALTER TABLE experiment_runs ADD COLUMN total_tokens_in BIGINT;
ALTER TABLE experiment_runs ADD COLUMN total_tokens_out BIGINT;
ALTER TABLE experiment_runs ADD COLUMN total_cost_usd DOUBLE PRECISION;
ALTER TABLE experiment_runs ADD COLUMN avg_latency_ms DOUBLE PRECISION;
