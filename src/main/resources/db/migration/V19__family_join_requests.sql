-- M19: a user alone in their family can ask to join someone else's family, named by that
-- person's email; the family's admins approve or decline. Rows are kept for history.

CREATE TABLE family_join_requests
(
    id           UUID PRIMARY KEY,
    requester_id UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id    UUID                     NOT NULL REFERENCES families (id) ON DELETE CASCADE,
    email        TEXT                     NOT NULL,
    status       TEXT                     NOT NULL
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'EXPIRED')),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    responded_at TIMESTAMP WITH TIME ZONE
);

-- At most one open request per requester.
CREATE UNIQUE INDEX family_join_requests_pending_uk ON family_join_requests (requester_id)
    WHERE status = 'PENDING';

CREATE INDEX family_join_requests_family_id_idx ON family_join_requests (family_id) WHERE status = 'PENDING';
