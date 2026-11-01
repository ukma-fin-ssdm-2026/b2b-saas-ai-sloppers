CREATE TABLE organization_subscription (
    organization_id  uuid         NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    user_id          uuid         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, user_id)
);

CREATE INDEX organization_subscription_user_idx
    ON organization_subscription (user_id);

ALTER TABLE organization_subscription ENABLE ROW LEVEL SECURITY;
