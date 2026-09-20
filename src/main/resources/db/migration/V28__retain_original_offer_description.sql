ALTER TABLE price_records ADD COLUMN source_product_name VARCHAR(200);
-- Old observations stay unknown: the current canonical name is not necessarily the retailer's description.
