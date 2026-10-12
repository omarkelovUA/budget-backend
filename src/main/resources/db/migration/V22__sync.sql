-- M29: change tracking for the mobile app's sync feed (ARCHITECTURE.md §6.5).
-- Every synced row carries the id of the transaction that last wrote it. Clients ask for
-- rows with since <= sync_txid < horizon, where horizon is the oldest transaction still
-- running, so nothing can commit behind a cursor they already hold.

CREATE FUNCTION sync_stamp() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    NEW.sync_txid := pg_current_xact_id();
    RETURN NEW;
END;
$$;

DO
$$
    DECLARE
        t TEXT;
    BEGIN
        FOREACH t IN ARRAY ARRAY ['accounts', 'categories', 'transactions', 'budgets', 'users', 'families']
            LOOP
                EXECUTE format('ALTER TABLE %I ADD COLUMN sync_txid xid8', t);
                EXECUTE format('UPDATE %I SET sync_txid = pg_current_xact_id()', t);
                EXECUTE format('ALTER TABLE %I ALTER COLUMN sync_txid SET NOT NULL', t);
                EXECUTE format('CREATE INDEX %I ON %I (sync_txid)', t || '_sync_txid_idx', t);
                EXECUTE format('CREATE TRIGGER %I BEFORE INSERT OR UPDATE ON %I'
                                   || ' FOR EACH ROW EXECUTE FUNCTION sync_stamp()', t || '_sync_stamp', t);
            END LOOP;
    END;
$$;

-- Deleted rows, so clients can drop them. scope_ids says whose data it was: the feed only
-- shows a tombstone to users whose scope (own id, family id, visible accounts) overlaps.
CREATE TABLE sync_tombstones
(
    entity     TEXT                     NOT NULL,
    id         UUID                     NOT NULL,
    scope_ids  UUID[]                   NOT NULL,
    sync_txid  xid8                     NOT NULL DEFAULT pg_current_xact_id(),
    deleted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX sync_tombstones_sync_txid_idx ON sync_tombstones (sync_txid);
CREATE INDEX sync_tombstones_scope_ids_idx ON sync_tombstones USING GIN (scope_ids);

CREATE FUNCTION sync_tombstone() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    -- Separate branches: PL/pgSQL resolves OLD.<field> against the actual table.
    IF TG_OP = 'DELETE' AND TG_ARGV[0] = 'account' THEN
        INSERT INTO sync_tombstones (entity, id, scope_ids) VALUES ('account', OLD.id, ARRAY [OLD.owner_user_id, OLD.id]);
        RETURN OLD;
    ELSIF TG_OP = 'DELETE' AND TG_ARGV[0] = 'transaction' THEN
        INSERT INTO sync_tombstones (entity, id, scope_ids)
        VALUES ('transaction', OLD.id,
                array_remove(ARRAY [OLD.account_id, OLD.from_account_id, OLD.to_account_id], NULL));
        RETURN OLD;
    ELSIF TG_OP = 'DELETE' THEN
        INSERT INTO sync_tombstones (entity, id, scope_ids) VALUES (TG_ARGV[0], OLD.id, ARRAY [OLD.owner_id]);
        RETURN OLD;
    END IF;
    -- A category or budget moved to another owner is gone for the old one.
    IF OLD.owner_id <> NEW.owner_id THEN
        INSERT INTO sync_tombstones (entity, id, scope_ids) VALUES (TG_ARGV[0], OLD.id, ARRAY [OLD.owner_id]);
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER accounts_sync_tombstone
    AFTER DELETE
    ON accounts
    FOR EACH ROW
EXECUTE FUNCTION sync_tombstone('account');
CREATE TRIGGER transactions_sync_tombstone
    AFTER DELETE
    ON transactions
    FOR EACH ROW
EXECUTE FUNCTION sync_tombstone('transaction');
CREATE TRIGGER categories_sync_tombstone
    AFTER DELETE OR UPDATE OF owner_id
    ON categories
    FOR EACH ROW
EXECUTE FUNCTION sync_tombstone('category');
CREATE TRIGGER budgets_sync_tombstone
    AFTER DELETE OR UPDATE OF owner_id
    ON budgets
    FOR EACH ROW
EXECUTE FUNCTION sync_tombstone('budget');

-- Per-user events: RESET when what a user may see changed (membership, grants), so their
-- client downloads everything again; ACCOUNT_ORDER / CATEGORY_ORDER when their order did.
CREATE TABLE sync_markers
(
    user_id   UUID NOT NULL,
    kind      TEXT NOT NULL CHECK (kind IN ('RESET', 'ACCOUNT_ORDER', 'CATEGORY_ORDER')),
    sync_txid xid8 NOT NULL DEFAULT pg_current_xact_id(),
    PRIMARY KEY (user_id, kind, sync_txid)
);

CREATE FUNCTION sync_mark(user_ids UUID[], marker TEXT) RETURNS void
    LANGUAGE sql AS
$$
INSERT INTO sync_markers (user_id, kind)
SELECT DISTINCT u, marker
FROM unnest(user_ids) AS u
WHERE u IS NOT NULL
ON CONFLICT DO NOTHING;
$$;

CREATE FUNCTION sync_mark_membership() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    old_family UUID := CASE WHEN TG_OP <> 'INSERT' THEN OLD.family_id END;
    new_family UUID := CASE WHEN TG_OP <> 'DELETE' THEN NEW.family_id END;
BEGIN
    PERFORM sync_mark(ARRAY(SELECT user_id FROM family_members WHERE family_id IN (old_family, new_family))
                          || CASE WHEN TG_OP <> 'INSERT' THEN OLD.user_id END
                          || CASE WHEN TG_OP <> 'DELETE' THEN NEW.user_id END, 'RESET');
    RETURN NULL;
END;
$$;

CREATE TRIGGER family_members_sync_mark
    AFTER INSERT OR UPDATE OR DELETE
    ON family_members
    FOR EACH ROW
EXECUTE FUNCTION sync_mark_membership();

CREATE FUNCTION sync_mark_user() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'UPDATE' AND OLD IS NOT DISTINCT FROM NEW THEN
        RETURN NULL;
    END IF;
    PERFORM sync_mark(ARRAY [CASE WHEN TG_OP <> 'INSERT' THEN OLD.user_id END,
                          CASE WHEN TG_OP <> 'DELETE' THEN NEW.user_id END], TG_ARGV[0]);
    RETURN NULL;
END;
$$;

CREATE TRIGGER account_access_sync_mark
    AFTER INSERT OR UPDATE OR DELETE
    ON account_access
    FOR EACH ROW
EXECUTE FUNCTION sync_mark_user('RESET');
CREATE TRIGGER account_positions_sync_mark
    AFTER INSERT OR UPDATE OR DELETE
    ON account_positions
    FOR EACH ROW
EXECUTE FUNCTION sync_mark_user('ACCOUNT_ORDER');
CREATE TRIGGER category_positions_sync_mark
    AFTER INSERT OR UPDATE OR DELETE
    ON category_positions
    FOR EACH ROW
EXECUTE FUNCTION sync_mark_user('CATEGORY_ORDER');
