-- How many days the shelf prices of one market without an online store stay current. NULL keeps the default
-- (PRICE_IN_STORE_MAX_AGE, 7 days). A market whose prices change often (a greengrocer) can be set shorter; the
-- default is also the ceiling, since every price query bounds store visits by it.
ALTER TABLE stores
    ADD COLUMN in_store_price_valid_days INTEGER
        CONSTRAINT ck_stores_in_store_price_valid_days CHECK (in_store_price_valid_days IS NULL OR in_store_price_valid_days > 0);
