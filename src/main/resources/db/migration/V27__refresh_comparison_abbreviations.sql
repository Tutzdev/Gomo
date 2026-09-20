UPDATE products SET comparison_identity_version = 0;
CREATE INDEX idx_products_comparison_family_similarity ON products USING gin (comparison_family gin_trgm_ops);
