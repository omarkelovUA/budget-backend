-- M14: money sent between family members waits in a transient account per ordered pair
-- (sender -> recipient) and currency. The owner is the sender; the recipient collects.

ALTER TABLE accounts
    ADD COLUMN transient_recipient_id UUID REFERENCES users (id);

-- At most one open transient account per sender, recipient and currency.
CREATE UNIQUE INDEX accounts_open_transient_uk ON accounts (owner_user_id, transient_recipient_id, currency)
    WHERE transient_recipient_id IS NOT NULL AND closed_at IS NULL;

CREATE INDEX accounts_transient_recipient_idx ON accounts (transient_recipient_id)
    WHERE transient_recipient_id IS NOT NULL;
