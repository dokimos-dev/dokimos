-- Convert item_results.input/expected_output/actual_output from TEXT (JSON-as-string) to native
-- JSONB so the columns are queryable. Existing values were normally written by Jackson and are
-- valid JSON, but a removed toString() fallback could in theory have produced non-JSON output
-- like "{key=value}". A direct cast would hard-abort the migration on such a row. The temp
-- function below tries the cast and, on failure, wraps the original value as a JSON string via
-- to_jsonb, so legacy bad rows are preserved (not silently dropped) and the migration cannot
-- fail on a malformed row.
CREATE OR REPLACE FUNCTION pg_temp.try_jsonb(t text) RETURNS jsonb AS $$
BEGIN
    IF t IS NULL THEN
        RETURN NULL;
    END IF;
    BEGIN
        RETURN t::jsonb;
    EXCEPTION WHEN others THEN
        RETURN to_jsonb(t);
    END;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

ALTER TABLE item_results
    ALTER COLUMN input TYPE jsonb USING pg_temp.try_jsonb(input);

ALTER TABLE item_results
    ALTER COLUMN expected_output TYPE jsonb USING pg_temp.try_jsonb(expected_output);

ALTER TABLE item_results
    ALTER COLUMN actual_output TYPE jsonb USING pg_temp.try_jsonb(actual_output);
