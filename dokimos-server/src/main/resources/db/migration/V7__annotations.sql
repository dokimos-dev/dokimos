-- Human review verdicts on run item results. Exactly one annotation per item result, enforced by
-- the unique constraint on item_result_id. ON DELETE CASCADE so deleting a run (which cascades to
-- its item results) also removes the annotations attached to them. The optional
-- overridden_expected_output holds a reviewer's corrected expected output for later promotion into
-- a dataset version.
CREATE TABLE annotations (
    id UUID PRIMARY KEY,
    item_result_id UUID NOT NULL REFERENCES item_results(id) ON DELETE CASCADE,
    verdict VARCHAR(255) NOT NULL,
    overridden_expected_output JSONB,
    note TEXT,
    created_by VARCHAR(255),
    tenant_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_annotation_item_result UNIQUE (item_result_id)
);

CREATE INDEX idx_annotations_item_result_id ON annotations(item_result_id);
