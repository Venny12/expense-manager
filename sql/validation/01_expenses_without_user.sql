-- Validation 1: expenses whose user does not exist.
-- Expected result: zero rows.
SELECT e.id AS expense_id,
       e.user_id
FROM expenses e
LEFT JOIN users u ON u.id = e.user_id
WHERE u.id IS NULL;