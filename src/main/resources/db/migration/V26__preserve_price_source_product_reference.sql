ALTER TABLE price_records ADD COLUMN source_product_reference varchar(2048);

-- Backfill only unambiguous references; never guess between local SKUs.
WITH unique_references AS (
    SELECT product_id, source_id, min(source_reference) AS reference
    FROM product_source_references GROUP BY product_id, source_id HAVING count(*) = 1
)
UPDATE price_records price SET source_product_reference = reference.reference
FROM unique_references reference
WHERE price.product_id = reference.product_id AND price.source_id = reference.source_id;
