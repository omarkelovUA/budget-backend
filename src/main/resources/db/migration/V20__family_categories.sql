-- M22: family categories. A member other than the admin can add one; added_by keeps who,
-- until the admin has seen the message about it. A family's admin reviews which of their
-- categories the family sees once, when the first other member arrives; families that
-- already have members are past that point.

ALTER TABLE categories
    ADD COLUMN added_by UUID REFERENCES users (id) ON DELETE SET NULL;

CREATE INDEX categories_added_by_idx ON categories (owner_id) WHERE added_by IS NOT NULL;

ALTER TABLE families
    ADD COLUMN categories_reviewed_at TIMESTAMP WITH TIME ZONE;

UPDATE families f
SET categories_reviewed_at = now()
WHERE (SELECT count(*) FROM family_members m WHERE m.family_id = f.id) > 1;
