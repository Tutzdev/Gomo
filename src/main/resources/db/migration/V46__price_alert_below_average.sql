-- A second kind of price alert: instead of a price chosen by the person, "warn me when it is X% below the usual
-- price", where the usual price is the 30-day average of the city's daily lowest price. Existing alerts keep
-- working as TARGET_PRICE.
ALTER TABLE price_alerts
    ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'TARGET_PRICE',
    ADD COLUMN discount_percent INTEGER,
    ALTER COLUMN target_price DROP NOT NULL;

ALTER TABLE price_alerts
    ADD CONSTRAINT ck_price_alerts_kind CHECK (
        (kind = 'TARGET_PRICE' AND target_price IS NOT NULL AND discount_percent IS NULL)
        OR (kind = 'BELOW_AVERAGE' AND target_price IS NULL AND discount_percent BETWEEN 5 AND 50)
    );
