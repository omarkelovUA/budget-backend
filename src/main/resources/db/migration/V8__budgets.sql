-- budget module (M8). One budget per owner, category and month.

CREATE TABLE budgets
(
    id             UUID PRIMARY KEY,
    owner_type     TEXT                     NOT NULL CHECK (owner_type IN ('USER', 'FAMILY')),
    owner_id       UUID                     NOT NULL,
    category_id    UUID                     NOT NULL REFERENCES categories (id),
    period         TEXT                     NOT NULL CHECK (period ~ '^\d{4}-(0[1-9]|1[0-2])$'),
    planned_amount NUMERIC(19, 2)           NOT NULL CHECK (planned_amount >= 0),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX budgets_owner_category_period_uk ON budgets (owner_type, owner_id, category_id, period);
