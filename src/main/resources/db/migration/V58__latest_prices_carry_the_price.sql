-- O preço atual passa a morar em latest_prices. Ler o preço em price_records era uma busca avulsa por linha
-- (~95 mil, 14 s em produção) e a página de ofertas estourava o tempo.
ALTER TABLE latest_prices
    ADD COLUMN regular_price NUMERIC(12, 2),
    ADD COLUMN promotional_price NUMERIC(12, 2),
    ADD COLUMN promotion_condition VARCHAR(500),
    ADD COLUMN promotion_valid_until TIMESTAMPTZ;

CREATE OR REPLACE FUNCTION record_latest_price() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO latest_prices (store_id, product_id, price_record_id, collected_at, recorded_at, valid_until,
                               availability, origin_type, regular_price, promotional_price, promotion_condition,
                               promotion_valid_until)
    VALUES (NEW.store_id, NEW.product_id, NEW.id, NEW.collected_at, NEW.recorded_at, NEW.valid_until,
            NEW.availability, NEW.origin_type, NEW.regular_price, NEW.promotional_price, NEW.promotion_condition,
            NEW.promotion_valid_until)
    ON CONFLICT (store_id, product_id) DO UPDATE SET
        price_record_id = EXCLUDED.price_record_id,
        collected_at = EXCLUDED.collected_at,
        recorded_at = EXCLUDED.recorded_at,
        valid_until = EXCLUDED.valid_until,
        availability = EXCLUDED.availability,
        origin_type = EXCLUDED.origin_type,
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
                                   availability, origin_type, regular_price, promotional_price,
                                   promotion_condition, promotion_valid_until)
        SELECT store_id, product_id, id, collected_at, recorded_at, valid_until, availability, origin_type,
               regular_price, promotional_price, promotion_condition, promotion_valid_until
        FROM price_records
        WHERE store_id = OLD.store_id AND product_id = OLD.product_id
        ORDER BY collected_at DESC, recorded_at DESC, id DESC
        LIMIT 1;
    END IF;
    RETURN OLD;
END
$$;

-- Uma busca pela chave por linha (12 s em produção). Sozinho, o planner lê os 6 GB de price_records (34 s),
-- perto demais do limite do health check do deploy.
SET LOCAL enable_hashjoin = off;
SET LOCAL enable_mergejoin = off;

UPDATE latest_prices latest
SET regular_price = r.regular_price,
    promotional_price = r.promotional_price,
    promotion_condition = r.promotion_condition,
    promotion_valid_until = r.promotion_valid_until
FROM price_records r
WHERE r.id = latest.price_record_id;

ALTER TABLE latest_prices ALTER COLUMN regular_price SET NOT NULL;
