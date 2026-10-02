CREATE TABLE collection_review_items (
    source_id UUID NOT NULL REFERENCES data_sources(id),
    store_id UUID NOT NULL REFERENCES stores(id),
    source_reference VARCHAR(2048) NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    offer JSONB NOT NULL,
    PRIMARY KEY (source_id, store_id, source_reference)
);
