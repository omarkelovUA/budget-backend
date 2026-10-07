-- Bumped whenever all of a user's sessions must end at once (e.g. Google reclaiming an
-- account someone registered with an unverified password). Access tokens carry it as
-- the "sv" claim and are rejected once it no longer matches.

ALTER TABLE users
    ADD COLUMN session_version INT NOT NULL DEFAULT 0;
