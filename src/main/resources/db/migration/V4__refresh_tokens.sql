-- security module (M7). Only a SHA-256 hash of each refresh token is stored.

CREATE TABLE refresh_tokens
(
    id         UUID PRIMARY KEY,
    user_id    UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash TEXT                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX refresh_tokens_token_hash_uk ON refresh_tokens (token_hash);
CREATE INDEX refresh_tokens_user_id_idx ON refresh_tokens (user_id);
