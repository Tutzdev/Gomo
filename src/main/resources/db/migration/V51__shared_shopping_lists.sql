-- A list shared by the household: the owner invites with a link; whoever opens it signed in joins the list and
-- edits and checks it off like the owner. Renaming, deleting and sharing stay with the owner.
CREATE TABLE shopping_list_members (
    shopping_list_id UUID NOT NULL REFERENCES shopping_lists (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (shopping_list_id, user_id)
);

CREATE INDEX idx_shopping_list_members_user ON shopping_list_members (user_id);

-- The link of an invite carries a random token shown once; only its hash is kept, like the session tokens.
CREATE TABLE shopping_list_invites (
    id UUID PRIMARY KEY,
    shopping_list_id UUID NOT NULL REFERENCES shopping_lists (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_by UUID NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT ck_shopping_list_invite_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_shopping_list_invites_list ON shopping_list_invites (shopping_list_id);
