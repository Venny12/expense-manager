-- Validation 5: approvals that break the shared data contract.
--   * status must be lowercase: pending, approved or denied
--   * review_date, when present, must be a real date in YYYY-MM-DD format
-- Expected result: zero rows.
SELECT a.id AS approval_id,
       a.expense_id,
       a.status,
       a.review_date
FROM approvals a
WHERE a.status NOT IN ('pending', 'approved', 'denied')
   OR (a.review_date IS NOT NULL
       AND (a.review_date NOT GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
            OR date(a.review_date) IS NOT a.review_date));