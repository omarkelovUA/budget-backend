-- Family sharing is a paid feature (ARCHITECTURE.md §3.4): a family may add members only
-- while one of its admins has an active subscription. Rows come from Google Play
-- purchases, or are granted by hand for testing (source MANUAL); the defaults on id and
-- the timestamps keep such an INSERT short.

CREATE TABLE subscriptions
(
    id             UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    user_id        UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source         TEXT                     NOT NULL CHECK (source IN ('GOOGLE_PLAY', 'MANUAL')),
    purchase_token TEXT UNIQUE,
    product_id     TEXT,
    base_plan_id   TEXT,
    state          TEXT                     NOT NULL
        CHECK (state IN ('PENDING', 'ACTIVE', 'IN_GRACE', 'ON_HOLD', 'PAUSED', 'CANCELED', 'EXPIRED')),
    -- Null: never expires (manual grants).
    expires_at     TIMESTAMP WITH TIME ZONE,
    auto_renewing  BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CHECK (source <> 'GOOGLE_PLAY' OR purchase_token IS NOT NULL)
);

CREATE INDEX subscriptions_user_id_idx ON subscriptions (user_id);
