-- Premium access comes from a paid subscription (subscriber) or from a one-time 7-day trial without payment.
ALTER TABLE app_users
    ADD COLUMN premium_trial_started_at TIMESTAMPTZ,
    ADD COLUMN premium_trial_ends_at TIMESTAMPTZ,
    ADD COLUMN preferred_billing_cycle VARCHAR(16);

ALTER TABLE app_users
    ADD CONSTRAINT ck_app_users_billing_cycle CHECK (preferred_billing_cycle IN ('MONTHLY', 'ANNUAL')),
    ADD CONSTRAINT ck_app_users_trial_period CHECK (
        (premium_trial_started_at IS NULL AND premium_trial_ends_at IS NULL)
        OR (premium_trial_started_at IS NOT NULL AND premium_trial_ends_at >= premium_trial_started_at));

-- Free accounts get a few complete comparisons per day. Comparing the same product again on the same day
-- does not use another one, so the primary key is also the deduplication rule.
CREATE TABLE comparison_usage (
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    usage_date DATE NOT NULL,
    subject_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, usage_date, subject_id)
);
