-- Generic catalog: one row per real-world product ("Coca-Cola 2 L"), linked to every
-- equivalent retailer SKU. Rebuilt after each collection; rows are deactivated, never
-- deleted, so shopping lists keep their references.
CREATE TABLE catalog_items (
    id UUID PRIMARY KEY,
    catalog_key VARCHAR(600) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    brand VARCHAR(120),
    size_label VARCHAR(40) NOT NULL,
    category VARCHAR(120),
    image_url VARCHAR(2048),
    representative_product_id UUID NOT NULL REFERENCES products(id),
    search_text VARCHAR(1200) NOT NULL,
    store_count INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_catalog_items_active_rank ON catalog_items(active, store_count DESC, display_name);
CREATE INDEX idx_catalog_items_search ON catalog_items USING gin (search_text gin_trgm_ops);

CREATE TABLE catalog_item_products (
    product_id UUID PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
    catalog_item_id UUID NOT NULL REFERENCES catalog_items(id) ON DELETE CASCADE
);

CREATE INDEX idx_catalog_item_products_item ON catalog_item_products(catalog_item_id);

ALTER TABLE shopping_list_items ADD COLUMN catalog_item_id UUID REFERENCES catalog_items(id);
CREATE UNIQUE INDEX uq_shopping_list_catalog_item
    ON shopping_list_items(shopping_list_id, catalog_item_id) WHERE catalog_item_id IS NOT NULL;
