-- M17: the OKLCH hue the UI palette derives from; 150 is the default "dollar" green.
ALTER TABLE users
    ADD COLUMN primary_hue SMALLINT NOT NULL DEFAULT 150 CHECK (primary_hue BETWEEN 0 AND 359);
