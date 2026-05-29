-- Link each item result to the dataset_item it was evaluated against so a per-case run comparison
-- can pair baseline vs candidate items by stable id rather than by ordinal alone. Nullable because
-- ad-hoc runs (no server dataset) and legacy rows have no item to point at. ON DELETE SET NULL so
-- deleting a dataset version (which cascades to its items) leaves historical item results intact
-- but unlinked, preserving them as point-in-time evidence.
ALTER TABLE item_results
    ADD COLUMN dataset_item_id UUID REFERENCES dataset_items(id) ON DELETE SET NULL;

CREATE INDEX idx_item_results_dataset_item_id ON item_results(dataset_item_id);
