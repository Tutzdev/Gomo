-- The newest observation of each product in each store, derived from price_records.
-- Every collection appends a row per product and store four times a day, so "the current price" read straight
-- from price_records (DISTINCT ON over the whole history) gets slower every day: the search, the market pages
-- and the comparisons only ever need the newest row. A trigger keeps this table, like daily_lowest_prices, so no
-- writer of price_records (collectors, store visits, approved contributions) can forget it.
--
-- "Newest" follows the order every reader already used: collected_at, then recorded_at, then id. The freshness
-- columns are copied so the search can count the markets with a current price without touching price_records.
CREATE TABLE latest_prices (
    store_id UUID NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    -- No foreign key: the delete trigger below promotes the previous observation, and a cascade would remove
    -- the row before it runs.
    price_record_id UUID NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    availability VARCHAR(16) NOT NULL,
    origin_type VARCHAR(32) NOT NULL,
    PRIMARY KEY (store_id, product_id)
);

CREATE INDEX idx_latest_prices_product ON latest_prices (product_id);

CREATE FUNCTION record_latest_price() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO latest_prices (store_id, product_id, price_record_id, collected_at, recorded_at, valid_until,
                               availability, origin_type)
    VALUES (NEW.store_id, NEW.product_id, NEW.id, NEW.collected_at, NEW.recorded_at, NEW.valid_until,
            NEW.availability, NEW.origin_type)
    ON CONFLICT (store_id, product_id) DO UPDATE SET
        price_record_id = EXCLUDED.price_record_id,
        collected_at = EXCLUDED.collected_at,
        recorded_at = EXCLUDED.recorded_at,
        valid_until = EXCLUDED.valid_until,
        availability = EXCLUDED.availability,
        origin_type = EXCLUDED.origin_type
    WHERE (EXCLUDED.collected_at, EXCLUDED.recorded_at, EXCLUDED.price_record_id)
        > (latest_prices.collected_at, latest_prices.recorded_at, latest_prices.price_record_id);
    RETURN NEW;
END
$$;

CREATE TRIGGER trg_price_records_latest
    AFTER INSERT ON price_records
    FOR EACH ROW EXECUTE FUNCTION record_latest_price();

-- Removing the newest observation (a withdrawn contribution) brings back the one before it.
CREATE FUNCTION forget_latest_price() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    DELETE FROM latest_prices
    WHERE store_id = OLD.store_id AND product_id = OLD.product_id AND price_record_id = OLD.id;
    IF FOUND THEN
        INSERT INTO latest_prices (store_id, product_id, price_record_id, collected_at, recorded_at, valid_until,
                                   availability, origin_type)
        SELECT store_id, product_id, id, collected_at, recorded_at, valid_until, availability, origin_type
        FROM price_records
        WHERE store_id = OLD.store_id AND product_id = OLD.product_id
        ORDER BY collected_at DESC, recorded_at DESC, id DESC
        LIMIT 1;
    END IF;
    RETURN OLD;
END
$$;

CREATE TRIGGER trg_price_records_forget_latest
    AFTER DELETE ON price_records
    FOR EACH ROW EXECUTE FUNCTION forget_latest_price();

-- Backfill. A DISTINCT ON over price_records sorts the whole history, which grows four times a day, and Flyway
-- runs at boot inside the deploy's 90-second health check (the first release of this file was rolled back,
-- most likely for that reason). This walks
-- idx_price_latest instead, one index probe per (store, product) pair, so it costs the number of pairs and not
-- the number of observations.
INSERT INTO latest_prices (store_id, product_id, price_record_id, collected_at, recorded_at, valid_until,
                           availability, origin_type)
WITH RECURSIVE newest AS (
    (SELECT store_id, product_id, id, collected_at, recorded_at, valid_until, availability, origin_type
     FROM price_records
     ORDER BY store_id, product_id, collected_at DESC, recorded_at DESC, id DESC
     LIMIT 1)
    UNION ALL
    SELECT next_pair.*
    FROM newest current_pair
    CROSS JOIN LATERAL (
        SELECT p.store_id, p.product_id, p.id, p.collected_at, p.recorded_at, p.valid_until, p.availability,
               p.origin_type
        FROM price_records p
        WHERE (p.store_id, p.product_id) > (current_pair.store_id, current_pair.product_id)
        ORDER BY p.store_id, p.product_id, p.collected_at DESC, p.recorded_at DESC, p.id DESC
        LIMIT 1) next_pair
)
SELECT store_id, product_id, id, collected_at, recorded_at, valid_until, availability, origin_type
FROM newest;
