ALTER TABLE products ADD COLUMN comparison_family varchar(500);
ALTER TABLE products ADD COLUMN comparison_identity_version integer NOT NULL DEFAULT 0;
CREATE INDEX idx_products_comparison_family ON products (comparison_family);
