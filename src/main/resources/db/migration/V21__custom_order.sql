-- M24: each user's own order of the accounts and categories they see. Items without a
-- row come after, in the default order. Rows go with the user or the item.

CREATE TABLE account_positions
(
    user_id    UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id UUID    NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    position   INTEGER NOT NULL,
    PRIMARY KEY (user_id, account_id)
);

CREATE TABLE category_positions
(
    user_id     UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id UUID    NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    position    INTEGER NOT NULL,
    PRIMARY KEY (user_id, category_id)
);
