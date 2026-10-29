-- Validation 4: approved/denied approvals with missing review data,
-- or reviewed by someone who is not a manager.
-- Expected result: zero rows.
SELECT a.id AS approval_id,
       a.expense_id,
       a.status,
       a.reviewer_id,
       a.review_date,
       u.role AS reviewer_role
FROM approvals a
LEFT JOIN users u ON u.id = a.reviewer_id
WHERE a.status IN ('approved', 'denied') AND 
(a.reviewer_id IS NULL OR a.review_date IS NULL OR u.role IS NOT 'manager');