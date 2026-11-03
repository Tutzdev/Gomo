-- A shopper suggests a product the catalog does not have yet; an admin reviews it.
-- Private to the shopper (not a public record like a price contribution), so it goes with the account.
CREATE TABLE product_suggestions (
    id UUID PRIMARY KEY,
    contributor_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL CHECK (btrim(name) <> ''),
    brand VARCHAR(120),
    package_size VARCHAR(80),
    barcode VARCHAR(14) CHECK (barcode IS NULL OR barcode ~ '^[0-9]{8,14}$'),
    store_id UUID REFERENCES stores(id) ON DELETE SET NULL,
    price NUMERIC(12, 2) CHECK (price IS NULL OR price > 0),
    note VARCHAR(500),
    submitted_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    moderator_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    decided_at TIMESTAMPTZ,
    rejection_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_product_suggestion_decision CHECK (
        (status = 'PENDING' AND decided_at IS NULL AND rejection_reason IS NULL)
        OR (status = 'APPROVED' AND decided_at IS NOT NULL AND rejection_reason IS NULL)
        OR (status = 'REJECTED' AND decided_at IS NOT NULL AND rejection_reason IS NOT NULL)
    )
);

CREATE INDEX idx_product_suggestions_contributor ON product_suggestions(contributor_id, submitted_at DESC);
CREATE INDEX idx_product_suggestions_queue ON product_suggestions(status, submitted_at, id);
