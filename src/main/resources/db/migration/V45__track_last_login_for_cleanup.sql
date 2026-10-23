-- Last sign-in, so the periodic cleanup never removes an unverified account that is still in use.
-- Existing accounts start from their newest session, when there is one.
ALTER TABLE app_users ADD COLUMN last_login_at TIMESTAMPTZ;

UPDATE app_users u SET last_login_at = t.last_login
FROM (SELECT user_id, max(created_at) AS last_login FROM auth_tokens GROUP BY user_id) t
WHERE t.user_id = u.id;

-- The cleanup removes expired sessions and old free-plan usage by date, across all users.
CREATE INDEX idx_auth_tokens_expiration ON auth_tokens(expires_at);
CREATE INDEX idx_comparison_usage_date ON comparison_usage(usage_date);
CREATE INDEX idx_app_users_unverified_created ON app_users(created_at) WHERE email_verified_at IS NULL;
