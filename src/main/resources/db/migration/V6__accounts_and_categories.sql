-- account + category modules (M8). owner_id points at users or families depending on
-- owner_type (ARCHITECTURE.md §3.2), so it has no foreign key.

CREATE TABLE accounts
(
    id         UUID PRIMARY KEY,
    owner_type TEXT                     NOT NULL CHECK (owner_type IN ('USER', 'FAMILY')),
    owner_id   UUID                     NOT NULL,
    name       TEXT                     NOT NULL,
    balance    NUMERIC(19, 2)           NOT NULL,
    currency   TEXT                     NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    icon       TEXT                     NOT NULL,
    color      TEXT                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX accounts_owner_idx ON accounts (owner_type, owner_id);

CREATE TABLE categories
(
    id         UUID PRIMARY KEY,
    owner_type TEXT                     NOT NULL CHECK (owner_type IN ('USER', 'FAMILY')),
    owner_id   UUID                     NOT NULL,
    type       TEXT                     NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    name       TEXT                     NOT NULL,
    icon       TEXT                     NOT NULL,
    color      TEXT                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX categories_owner_idx ON categories (owner_type, owner_id);
