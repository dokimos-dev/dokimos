-- The server judge can speak either the Responses API or Chat Completions per connection. New
-- connections default to RESPONSES (set by the application); existing rows are backfilled to
-- CHAT_COMPLETIONS so connections created before this change keep their current behavior.
ALTER TABLE llm_connections
    ADD COLUMN protocol VARCHAR(32) NOT NULL DEFAULT 'CHAT_COMPLETIONS';
