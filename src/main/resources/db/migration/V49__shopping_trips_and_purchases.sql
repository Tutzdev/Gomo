-- "No mercado" mode: an item is checked when it goes into the cart. The check belongs to the list, so whoever
-- shops with it sees the same marks; it is cleared when the purchase that bought every item is recorded.
ALTER TABLE shopping_list_items
    ADD COLUMN checked_at TIMESTAMPTZ,
    ADD COLUMN checked_by UUID REFERENCES app_users (id) ON DELETE SET NULL;

-- A purchase the person marked as done ("Terminei a compra"), with the prices of that moment, for the monthly
-- savings report. The id comes from the client, so retrying a purchase sent from a phone with a weak signal
-- records it once.
CREATE TABLE purchases (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    shopping_list_id UUID REFERENCES shopping_lists (id) ON DELETE SET NULL,
    list_name VARCHAR(120) NOT NULL,
    store_id UUID NOT NULL REFERENCES stores (id) ON DELETE RESTRICT,
    city_id UUID NOT NULL REFERENCES cities (id) ON DELETE RESTRICT,
    purchased_at TIMESTAMPTZ NOT NULL,
    total_paid NUMERIC(12, 2) NOT NULL CHECK (total_paid >= 0),
    -- Only the items with a price in at least two stores of the city are compared with the city average.
    compared_items INTEGER NOT NULL CHECK (compared_items >= 0),
    compared_paid NUMERIC(12, 2) NOT NULL CHECK (compared_paid >= 0),
    compared_city_average NUMERIC(12, 2) NOT NULL CHECK (compared_city_average >= 0),
    items_without_price INTEGER NOT NULL CHECK (items_without_price >= 0)
);

CREATE INDEX idx_purchases_user_time ON purchases (user_id, purchased_at DESC, id);

CREATE TABLE purchase_items (
    id UUID PRIMARY KEY,
    purchase_id UUID NOT NULL REFERENCES purchases (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    product_name VARCHAR(200) NOT NULL,
    quantity NUMERIC(12, 3) NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL CHECK (unit_price > 0),
    -- COLLECTED: the price Gomo had for the store; SHELF: the price the person saw on the shelf and typed.
    price_source VARCHAR(16) NOT NULL CHECK (price_source IN ('COLLECTED', 'SHELF')),
    city_average_price NUMERIC(12, 2) CHECK (city_average_price > 0),
    city_stores INTEGER NOT NULL CHECK (city_stores >= 0)
);

CREATE INDEX idx_purchase_items_purchase ON purchase_items (purchase_id);
