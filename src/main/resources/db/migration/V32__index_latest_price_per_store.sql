-- Newest price per store: the store list hides markets without a current price and the collection
-- status checks each market's freshness, so both read max(collected_at) per store on every request.
CREATE INDEX IF NOT EXISTS idx_price_records_store_collected ON price_records (store_id, collected_at DESC);
