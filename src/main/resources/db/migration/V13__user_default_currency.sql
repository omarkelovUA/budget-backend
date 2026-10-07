-- M13: user settings, starting with the default currency (picked from the client's region
-- at sign-up). Existing users get UAH, the app's original currency.

ALTER TABLE users
    ADD COLUMN default_currency TEXT NOT NULL DEFAULT 'UAH' CHECK (default_currency ~ '^[A-Z]{3}$');
