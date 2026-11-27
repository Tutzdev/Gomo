-- O Gomo só compara preços coletados das lojas online. Saem os preços digitados à mão: o preço anotado na
-- prateleira (STORE_VISIT, V52/V55), o preço enviado pelo usuário (USER_CONTRIBUTION, V7), o preço da gôndola
-- digitado no modo "No mercado" (V49) e a sugestão de produto (V53). Os mercados sem loja online de Barra Mansa
-- (V52) saem junto, porque nunca teriam preço.

DELETE FROM admin_audit_entries
WHERE action IN ('CONTRIBUTION_APPROVED', 'CONTRIBUTION_REJECTED', 'STORE_VISIT_RECORDED',
                 'STORE_VISIT_VALIDITY_CHANGED', 'PRODUCT_SUGGESTION_APPROVED', 'PRODUCT_SUGGESTION_REJECTED');

DROP TABLE product_suggestions;
DROP TABLE price_contributions CASCADE;

CREATE TEMPORARY TABLE manual_stores ON COMMIT DROP AS
SELECT id, supermarket_chain_id FROM stores WHERE price_channel = 'IN_STORE';

DELETE FROM alert_notifications
WHERE store_id IN (SELECT id FROM manual_stores)
   OR price_record_id IN (SELECT id FROM price_records WHERE origin_type <> 'SOURCE');

DELETE FROM price_records
WHERE origin_type <> 'SOURCE' OR store_id IN (SELECT id FROM manual_stores);

DELETE FROM favorite_stores WHERE store_id IN (SELECT id FROM manual_stores);
DELETE FROM purchases WHERE store_id IN (SELECT id FROM manual_stores);
DELETE FROM collection_review_items WHERE store_id IN (SELECT id FROM manual_stores);
DELETE FROM collection_runs WHERE store_id IN (SELECT id FROM manual_stores);
DELETE FROM stores WHERE id IN (SELECT id FROM manual_stores);

DELETE FROM supermarket_chains chain
WHERE chain.id IN (SELECT supermarket_chain_id FROM manual_stores)
  AND NOT EXISTS (SELECT 1 FROM stores s WHERE s.supermarket_chain_id = chain.id);

DELETE FROM data_sources source
WHERE source.code = 'in_store_price_check'
  AND NOT EXISTS (SELECT 1 FROM supermarket_chains c WHERE c.source_id = source.id)
  AND NOT EXISTS (SELECT 1 FROM stores s WHERE s.source_id = source.id)
  AND NOT EXISTS (SELECT 1 FROM products p WHERE p.source_id = source.id)
  AND NOT EXISTS (SELECT 1 FROM product_source_references r WHERE r.source_id = source.id)
  AND NOT EXISTS (SELECT 1 FROM price_records r WHERE r.source_id = source.id)
  AND NOT EXISTS (SELECT 1 FROM collection_runs r WHERE r.source_id = source.id)
  AND NOT EXISTS (SELECT 1 FROM collection_review_items r WHERE r.source_id = source.id);

ALTER TABLE stores
    DROP COLUMN price_channel,
    DROP COLUMN in_store_price_valid_days;

ALTER TABLE purchase_items DROP COLUMN price_source;

CREATE OR REPLACE FUNCTION record_latest_price() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO latest_prices (store_id, product_id, price_record_id, collected_at, recorded_at, valid_until,
                               availability, regular_price, promotional_price, promotion_condition,
                               promotion_valid_until)
    VALUES (NEW.store_id, NEW.product_id, NEW.id, NEW.collected_at, NEW.recorded_at, NEW.valid_until,
            NEW.availability, NEW.regular_price, NEW.promotional_price, NEW.promotion_condition,
            NEW.promotion_valid_until)
    ON CONFLICT (store_id, product_id) DO UPDATE SET
        price_record_id = EXCLUDED.price_record_id,
        collected_at = EXCLUDED.collected_at,
        recorded_at = EXCLUDED.recorded_at,
        valid_until = EXCLUDED.valid_until,
        availability = EXCLUDED.availability,
        regular_price = EXCLUDED.regular_price,
        promotional_price = EXCLUDED.promotional_price,
        promotion_condition = EXCLUDED.promotion_condition,
        promotion_valid_until = EXCLUDED.promotion_valid_until
    WHERE (EXCLUDED.collected_at, EXCLUDED.recorded_at, EXCLUDED.price_record_id)
        > (latest_prices.collected_at, latest_prices.recorded_at, latest_prices.price_record_id);
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION forget_latest_price() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    DELETE FROM latest_prices
    WHERE store_id = OLD.store_id AND product_id = OLD.product_id AND price_record_id = OLD.id;
    IF FOUND THEN
        INSERT INTO latest_prices (store_id, product_id, price_record_id, collected_at, recorded_at, valid_until,
                                   availability, regular_price, promotional_price, promotion_condition,
                                   promotion_valid_until)
        SELECT store_id, product_id, id, collected_at, recorded_at, valid_until, availability,
               regular_price, promotional_price, promotion_condition, promotion_valid_until
        FROM price_records
        WHERE store_id = OLD.store_id AND product_id = OLD.product_id
        ORDER BY collected_at DESC, recorded_at DESC, id DESC
        LIMIT 1;
    END IF;
    RETURN OLD;
END
$$;

ALTER TABLE latest_prices DROP COLUMN origin_type;

ALTER TABLE price_records
    DROP COLUMN origin_type,
    DROP COLUMN contribution_id;
