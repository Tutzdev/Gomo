-- The price of the last notice, so an alert speaks again only when the price drops below it (or a week later),
-- instead of once per collection while a promotion lasts. Existing alerts start from their newest notification.
ALTER TABLE price_alerts ADD COLUMN last_notified_price NUMERIC(12, 2) CHECK (last_notified_price > 0);

UPDATE price_alerts a SET last_notified_price = latest.unit_price
FROM (
    SELECT DISTINCT ON (alert_id) alert_id, unit_price
    FROM alert_notifications
    ORDER BY alert_id, created_at DESC, id DESC
) latest
WHERE latest.alert_id = a.id;
