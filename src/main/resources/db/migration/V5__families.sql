-- family module (M7). family_members is keyed by user_id: one family per user in v1.

CREATE TABLE families
(
    id         UUID PRIMARY KEY,
    name       TEXT                     NOT NULL,
    created_by UUID                     NOT NULL REFERENCES users (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE family_members
(
    user_id   UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    family_id UUID                     NOT NULL REFERENCES families (id) ON DELETE CASCADE,
    role      TEXT                     NOT NULL CHECK (role IN ('ADMIN', 'MEMBER')),
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX family_members_family_id_idx ON family_members (family_id);

CREATE TABLE family_invites
(
    code       TEXT PRIMARY KEY,
    family_id  UUID                     NOT NULL REFERENCES families (id) ON DELETE CASCADE,
    created_by UUID                     NOT NULL REFERENCES users (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_by    UUID REFERENCES users (id),
    used_at    TIMESTAMP WITH TIME ZONE
);

CREATE INDEX family_invites_family_id_idx ON family_invites (family_id);
