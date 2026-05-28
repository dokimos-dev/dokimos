-- Convert item_results.input/expected_output/actual_output from TEXT (JSON-as-string) to native
-- JSONB so the columns are queryable. Existing values were written by Jackson and are valid JSON
-- objects, so the cast preserves them. The CASE only guards NULLs (expected_output is nullable).
ALTER TABLE item_results
    ALTER COLUMN input TYPE jsonb USING (CASE WHEN input IS NULL THEN NULL ELSE input::jsonb END);

ALTER TABLE item_results
    ALTER COLUMN expected_output TYPE jsonb USING (CASE WHEN expected_output IS NULL THEN NULL ELSE expected_output::jsonb END);

ALTER TABLE item_results
    ALTER COLUMN actual_output TYPE jsonb USING (CASE WHEN actual_output IS NULL THEN NULL ELSE actual_output::jsonb END);
