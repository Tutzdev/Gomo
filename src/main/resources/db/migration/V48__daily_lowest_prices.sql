-- The lowest price of each product in each store per day (São Paulo calendar), derived from price_records.
-- Every collection appends a row per product and store, four times a day; the history, the usual price of the
-- alerts and of the verdict, the deals page and the stock-up hints only need one number per day. A trigger keeps
-- it, so no writer of price_records (collectors, approved contributions, admin) can forget it.
--
-- The day price follows PricePolicy: an unconditional promotion still valid when it was collected is the price;
-- an unavailable product has none.
CREATE TABLE daily_lowest_prices (
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    price_day DATE NOT NULL,
    lowest_price NUMERIC(12, 2) NOT NULL CHECK (lowest_price > 0),
    PRIMARY KEY (product_id, store_id, price_day)
);

CREATE INDEX idx_daily_lowest_store_day ON daily_lowest_prices (store_id, price_day);

CREATE FUNCTION record_daily_lowest_price() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.availability <> 'UNAVAILABLE' THEN
        INSERT INTO daily_lowest_prices (product_id, store_id, price_day, lowest_price)
        VALUES (NEW.product_id, NEW.store_id, CAST(NEW.collected_at AT TIME ZONE 'America/Sao_Paulo' AS DATE),
                CASE
                    WHEN NEW.promotional_price IS NOT NULL AND NEW.promotion_condition IS NULL
                         AND NEW.promotion_valid_until > NEW.collected_at THEN NEW.promotional_price
                    ELSE NEW.regular_price
                END)
        ON CONFLICT (product_id, store_id, price_day)
            DO UPDATE SET lowest_price = LEAST(daily_lowest_prices.lowest_price, EXCLUDED.lowest_price);
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER trg_price_records_daily_lowest
    AFTER INSERT ON price_records
    FOR EACH ROW EXECUTE FUNCTION record_daily_lowest_price();

INSERT INTO daily_lowest_prices (product_id, store_id, price_day, lowest_price)
SELECT product_id, store_id, CAST(collected_at AT TIME ZONE 'America/Sao_Paulo' AS DATE),
       MIN(CASE
               WHEN promotional_price IS NOT NULL AND promotion_condition IS NULL
                    AND promotion_valid_until > collected_at THEN promotional_price
               ELSE regular_price
           END)
FROM price_records
WHERE availability <> 'UNAVAILABLE'
GROUP BY product_id, store_id, CAST(collected_at AT TIME ZONE 'America/Sao_Paulo' AS DATE);
