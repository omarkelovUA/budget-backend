-- M16: the UI theme. 'system' follows the device's light/dark preference.
ALTER TABLE users
    ADD COLUMN theme TEXT NOT NULL DEFAULT 'system' CHECK (theme IN ('light', 'dark', 'system'));
