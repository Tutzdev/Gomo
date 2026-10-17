-- Paid Premium through the payment provider. Access lasts until premium_paid_until; each confirmed payment
-- extends it, and cancelling stops renewals without taking away the period already paid.
ALTER TABLE app_users
    ADD COLUMN premium_paid_until TIMESTAMPTZ;

-- A hosted checkout opened by the account. The confirmation webhook carries this id back, which is how a
-- payment is tied to the person without sending account data to the provider.
CREATE TABLE billing_checkouts (
    id VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    billing_cycle VARCHAR(16) NOT NULL CHECK (billing_cycle IN ('MONTHLY', 'ANNUAL')),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_billing_checkouts_user ON billing_checkouts (user_id);

CREATE TABLE billing_subscriptions (
    id VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    billing_cycle VARCHAR(16) NOT NULL CHECK (billing_cycle IN ('MONTHLY', 'ANNUAL')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ,
    CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL))
);

CREATE INDEX ix_billing_subscriptions_user ON billing_subscriptions (user_id, status);

-- Webhook deliveries already applied. Every retry of an event repeats its id, so the key makes them no-ops.
CREATE TABLE billing_webhook_events (
    id VARCHAR(64) PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL
);
