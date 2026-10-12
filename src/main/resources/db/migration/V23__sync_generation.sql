-- M29: sync cursors start with this generation (ARCHITECTURE.md §6.5). Transaction ids
-- start over in a cluster restored from a dump, so old cursors would skip changes there.
-- Bump the generation after a restore and every client downloads everything again.

CREATE TABLE sync_generation
(
    id         BOOLEAN PRIMARY KEY DEFAULT true CHECK (id),
    generation INTEGER NOT NULL
);

INSERT INTO sync_generation (generation)
VALUES (1);
