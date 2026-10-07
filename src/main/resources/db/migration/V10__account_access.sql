-- M12: every account has exactly one owner (a user), who gives family members VISIBLE
-- (see it) or SHARED (see it and record transactions) access. Replaces the user/family
-- owner_type + owner_id pair for accounts; categories and budgets keep it.

ALTER TABLE accounts
    ADD COLUMN owner_user_id UUID REFERENCES users (id),
    ADD COLUMN closed_at     TIMESTAMP WITH TIME ZONE;

CREATE TABLE account_access
(
    account_id UUID NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    level      TEXT NOT NULL CHECK (level IN ('VISIBLE', 'SHARED')),
    PRIMARY KEY (account_id, user_id)
);

CREATE INDEX account_access_user_id_idx ON account_access (user_id);

-- Private accounts keep their user as owner.
UPDATE accounts
SET owner_user_id = owner_id
WHERE owner_type = 'USER';

-- Family accounts go to the family's longest-standing admin...
UPDATE accounts a
SET owner_user_id = (SELECT m.user_id
                     FROM family_members m
                     WHERE m.family_id = a.owner_id
                       AND m.role = 'ADMIN'
                     ORDER BY m.joined_at, m.user_id
                     LIMIT 1)
WHERE a.owner_type = 'FAMILY';

-- ...and every other current member keeps the access they had: SHARED.
INSERT INTO account_access (account_id, user_id, level)
SELECT a.id, m.user_id, 'SHARED'
FROM accounts a
         JOIN family_members m ON m.family_id = a.owner_id
WHERE a.owner_type = 'FAMILY'
  AND m.user_id <> a.owner_user_id;

-- Accounts of already-deleted families: whoever recorded their earliest transaction.
UPDATE accounts a
SET owner_user_id = (SELECT t.recorded_by
                     FROM transactions t
                     WHERE a.id IN (t.account_id, t.from_account_id, t.to_account_id)
                     ORDER BY t.created_at
                     LIMIT 1)
WHERE a.owner_user_id IS NULL;

-- What's left is orphaned and has no transactions, so nothing references it.
DELETE
FROM accounts
WHERE owner_user_id IS NULL;

ALTER TABLE accounts
    ALTER COLUMN owner_user_id SET NOT NULL,
    DROP COLUMN owner_type,
    DROP COLUMN owner_id;

CREATE INDEX accounts_owner_user_id_idx ON accounts (owner_user_id);
