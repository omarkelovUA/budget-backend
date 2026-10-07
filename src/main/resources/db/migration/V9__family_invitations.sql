-- Email invitations replace invite codes. The invitee's email is the key: whoever signs in
-- with that (Google-verified) email can accept. Rows are kept after a response for history.

DROP TABLE family_invites;

CREATE TABLE family_invitations
(
    id           UUID PRIMARY KEY,
    family_id    UUID                     NOT NULL REFERENCES families (id) ON DELETE CASCADE,
    email        TEXT                     NOT NULL,
    invited_by   UUID                     NOT NULL REFERENCES users (id),
    status       TEXT                     NOT NULL
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'EXPIRED')),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    responded_at TIMESTAMP WITH TIME ZONE
);

-- At most one open invitation per family and email.
CREATE UNIQUE INDEX family_invitations_pending_uk ON family_invitations (family_id, email)
    WHERE status = 'PENDING';

CREATE INDEX family_invitations_email_idx ON family_invitations (email) WHERE status = 'PENDING';
