-- M15: two more settings. The UI language (ISO 639-1; the app validates which ones it
-- supports) and the currencies the user works with, which pickers offer. Existing users
-- get Ukrainian, the UI's only language until now; new users always get an explicit one.

ALTER TABLE users
    ADD COLUMN language        TEXT   NOT NULL DEFAULT 'uk' CHECK (language ~ '^[a-z]{2}$'),
    ADD COLUMN used_currencies TEXT[] NOT NULL DEFAULT '{}';

-- The default currency first, then those of the user's own open accounts, oldest first.
UPDATE users u
SET used_currencies = (SELECT (array_agg(c.currency ORDER BY c.rank, c.since))[1:20]
                       FROM (SELECT u.default_currency AS currency, 0 AS rank, NULL::timestamptz AS since
                             UNION ALL
                             SELECT a.currency, 1, min(a.created_at)
                             FROM accounts a
                             WHERE a.owner_user_id = u.id
                               AND a.closed_at IS NULL
                               AND a.transient_recipient_id IS NULL
                               AND a.currency <> u.default_currency
                             GROUP BY a.currency) c);

ALTER TABLE users
    ALTER COLUMN used_currencies DROP DEFAULT,
    ADD CONSTRAINT users_used_currencies_check CHECK (
        cardinality(used_currencies) BETWEEN 1 AND 20
            AND array_to_string(used_currencies, ',') ~ '^[A-Z]{3}(,[A-Z]{3})*$'
            AND default_currency = ANY (used_currencies));
