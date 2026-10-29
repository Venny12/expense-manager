-- Validation 3: pending approvals that already have a reviewer or review date.
-- A pending row must have no reviewer and no review date.
-- Expected result: zero rows.
SELECT a.id AS approval_id,
       a.expense_id,
       a.reviewer_id,
       a.review_date
FROM approvals a
WHERE a.status = 'pending'
  AND (a.reviewer_id IS NOT NULL OR a.review_date IS NOT NULL);