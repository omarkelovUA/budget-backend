-- Categories and budgets of families deleted before FamilyDeleted existed still point at
-- a family that's gone, so nobody can see them. The last member isn't recorded anywhere;
-- the closest stand-in is whoever recorded the category's latest payment.

UPDATE categories c
SET owner_type = 'USER',
    owner_id   = (SELECT t.recorded_by
                  FROM transactions t
                  WHERE t.category_id = c.id
                  ORDER BY t.created_at DESC
                  LIMIT 1)
WHERE c.owner_type = 'FAMILY'
  AND NOT EXISTS (SELECT 1 FROM families f WHERE f.id = c.owner_id)
  AND EXISTS (SELECT 1 FROM transactions t WHERE t.category_id = c.id);

-- A family budget always uses a category of the same family, so it follows its category.
UPDATE budgets b
SET owner_type = 'USER',
    owner_id   = c.owner_id
FROM categories c
WHERE b.category_id = c.id
  AND b.owner_type = 'FAMILY'
  AND c.owner_type = 'USER'
  AND NOT EXISTS (SELECT 1 FROM families f WHERE f.id = b.owner_id);

-- What's left has no transaction history and no one to hand it to.
DELETE
FROM budgets b
WHERE b.owner_type = 'FAMILY'
  AND NOT EXISTS (SELECT 1 FROM families f WHERE f.id = b.owner_id);

DELETE
FROM categories c
WHERE c.owner_type = 'FAMILY'
  AND NOT EXISTS (SELECT 1 FROM families f WHERE f.id = c.owner_id);
