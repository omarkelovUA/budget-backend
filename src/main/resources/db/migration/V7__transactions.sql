-- transaction module (M8). Foreign keys without ON DELETE CASCADE: an account or category
-- with transactions can't be deleted (the API answers 409 *-in-use).

CREATE TABLE transactions
(
    id              UUID PRIMARY KEY,
    kind            TEXT                     NOT NULL CHECK (kind IN ('PAYMENT', 'TRANSFER')),
    amount          NUMERIC(19, 2)           NOT NULL CHECK (amount > 0),
    occurred_on     DATE                     NOT NULL,
    recorded_by     UUID                     NOT NULL REFERENCES users (id),
    account_id      UUID REFERENCES accounts (id),
    category_id     UUID REFERENCES categories (id),
    from_account_id UUID REFERENCES accounts (id),
    to_account_id   UUID REFERENCES accounts (id),
    note            TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT transactions_shape_ck CHECK (
        (kind = 'PAYMENT' AND account_id IS NOT NULL AND category_id IS NOT NULL
            AND from_account_id IS NULL AND to_account_id IS NULL)
        OR (kind = 'TRANSFER' AND from_account_id IS NOT NULL AND to_account_id IS NOT NULL
            AND from_account_id <> to_account_id AND account_id IS NULL AND category_id IS NULL))
);

CREATE INDEX transactions_account_id_idx ON transactions (account_id);
CREATE INDEX transactions_from_account_id_idx ON transactions (from_account_id);
CREATE INDEX transactions_to_account_id_idx ON transactions (to_account_id);
CREATE INDEX transactions_category_period_idx ON transactions (category_id, occurred_on);
