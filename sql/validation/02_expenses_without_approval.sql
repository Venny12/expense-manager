-- Validation 2: expenses with no approval row.
-- Every expense must have exactly one approval row.
-- Expected result: zero rows.
SELECT e.id AS expense_id
FROM expenses e
LEFT JOIN approvals a ON a.expense_id = e.id
WHERE a.id IS NULL;