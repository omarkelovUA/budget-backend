-- M19: every user belongs to a family. Users without one get a one-person family, named
-- in their language, as new users do at sign-up (FamilyService#defaultName).

WITH new_families AS (
    INSERT INTO families (id, name, created_by, created_at)
        SELECT gen_random_uuid(), CASE WHEN u.language = 'uk' THEN 'Родина' ELSE 'Family' END, u.id, now()
        FROM users u
        WHERE NOT EXISTS (SELECT 1 FROM family_members m WHERE m.user_id = u.id)
        RETURNING id, created_by)
INSERT
INTO family_members (user_id, family_id, role, joined_at)
SELECT created_by, id, 'ADMIN', now()
FROM new_families;
