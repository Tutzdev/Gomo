-- What a weekly or monthly list costs at its best single market, one row per list, city and day, so the home
-- page can say "your monthly shopping costs R$ 412 at Bramil today, R$ 18 less than last week". Written by a
-- daily job for the lists of people with a preferred city, and whenever the home page shows the list.
CREATE TABLE recurring_list_snapshots (
    id UUID PRIMARY KEY,
    shopping_list_id UUID NOT NULL REFERENCES shopping_lists (id) ON DELETE CASCADE,
    city_id UUID NOT NULL REFERENCES cities (id) ON DELETE RESTRICT,
    snapshot_day DATE NOT NULL,
    store_id UUID REFERENCES stores (id) ON DELETE SET NULL,
    store_name VARCHAR(200),
    total NUMERIC(12, 2) CHECK (total >= 0),
    complete BOOLEAN NOT NULL,
    priced_items INTEGER NOT NULL CHECK (priced_items >= 0),
    requested_items INTEGER NOT NULL CHECK (requested_items >= 0),
    -- The items and quantities behind the total: a week is compared with another only when the list is the same.
    items_signature VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_recurring_list_snapshot UNIQUE (shopping_list_id, city_id, snapshot_day)
);

CREATE INDEX idx_recurring_list_snapshots_day ON recurring_list_snapshots (snapshot_day);
