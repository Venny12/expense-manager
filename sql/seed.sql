PRAGMA foreign_keys = ON;

BEGIN TRANSACTION;

-- Fictional accounts for this exercise only.
INSERT INTO users (id, username, password, role, full_name)
VALUES
    (1, 'manager_asha', 'demo123', 'manager', 'Asha Patel'),
    (2, 'manager_daniel', 'demo123', 'manager', 'Daniel Chen'),
    (3, 'employee_maya', 'demo123', 'employee', 'Maya Singh'),
    (4, 'employee_omar', 'demo123', 'employee', 'Omar Ali'),
    (5, 'employee_lina', 'demo123', 'employee', 'Lina Park'),
    (6, 'employee_noah', 'demo123', 'employee', 'Noah Wilson');

INSERT INTO categories (id, name)
VALUES
    (1, 'Travel'),
    (2, 'Meals'),
    (3, 'Lodging'),
    (4, 'Office Supplies'),
    (5, 'Training'),
    (6, 'Other');

    -- Sample expenses for all four employees.
-- Dates are relative to the day this script runs.
INSERT INTO expenses (
    id, user_id, category_id, amount,
    description, expense_date, created_at
)
VALUES
    (1, 3, 6, 0.01, 'Small adjustment',
     date('now', 'localtime'), datetime('now', 'localtime')),

    (2, 4, 5, 10000.00, 'Professional training program',
     date('now', 'localtime', '-1 day'), datetime('now', 'localtime')),

    (3, 5, 4, 125.00,
     '12345678901234567890123456789012345678901234567890' ||
     '12345678901234567890123456789012345678901234567890' ||
     '12345678901234567890123456789012345678901234567890' ||
     '12345678901234567890123456789012345678901234567890',
     date('now', 'localtime', '-90 days'), datetime('now', 'localtime')),

    (4, 6, 1, 45.50, 'Train to client office',
     date('now', 'localtime', '-2 days'), datetime('now', 'localtime')),

    (5, 3, 2, 28.75, 'Client meeting lunch',
     date('now', 'localtime', '-3 days'), datetime('now', 'localtime')),

    (6, 4, 3, 180.00, 'Hotel for business trip',
     date('now', 'localtime', '-4 days'), datetime('now', 'localtime')),

    (7, 5, 4, 32.99, 'Office stationery',
     date('now', 'localtime', '-5 days'), datetime('now', 'localtime')),

    (8, 6, 5, 299.00, 'Software testing course',
     date('now', 'localtime', '-6 days'), datetime('now', 'localtime')),

    (9, 3, 6, 15.00, 'Business parking',
     date('now', 'localtime', '-7 days'), datetime('now', 'localtime')),

    (10, 4, 1, 65.25, 'Taxi to client office',
     date('now', 'localtime', '-8 days'), datetime('now', 'localtime')),

    (11, 5, 2, 42.00, 'Team working lunch',
     date('now', 'localtime', '-9 days'), datetime('now', 'localtime')),

    (12, 6, 3, 210.00, 'Conference hotel',
     date('now', 'localtime', '-10 days'), datetime('now', 'localtime')),

    (13, 3, 4, 54.50, 'Keyboard for office',
     date('now', 'localtime', '-11 days'), datetime('now', 'localtime')),

    (14, 4, 5, 150.00, 'Java workshop',
     date('now', 'localtime', '-12 days'), datetime('now', 'localtime')),

    (15, 5, 6, 18.00, 'Document delivery',
     date('now', 'localtime', '-13 days'), datetime('now', 'localtime')),

    (16, 6, 1, 95.00, 'Intercity train ticket',
     date('now', 'localtime', '-14 days'), datetime('now', 'localtime')),

    (17, 3, 2, 23.40, 'Meal during business travel',
     date('now', 'localtime', '-15 days'), datetime('now', 'localtime')),

    (18, 4, 3, 165.00, 'Overnight business accommodation',
     date('now', 'localtime', '-16 days'), datetime('now', 'localtime')),

    (19, 5, 4, 12.99, 'Notebooks',
     date('now', 'localtime', '-17 days'), datetime('now', 'localtime')),

    (20, 6, 5, 85.00, 'SQL training session',
     date('now', 'localtime', '-18 days'), datetime('now', 'localtime')),

    (21, 3, 6, 9.50, 'Printing for client presentation',
     date('now', 'localtime', '-19 days'), datetime('now', 'localtime')),

    (22, 4, 1, 37.80, 'Bus fare for business trip',
     date('now', 'localtime', '-20 days'), datetime('now', 'localtime')),

    (23, 5, 2, 56.00, 'Client dinner',
     date('now', 'localtime', '-21 days'), datetime('now', 'localtime')),

    (24, 6, 3, 195.00, 'Hotel near client site',
     date('now', 'localtime', '-22 days'), datetime('now', 'localtime')),

    (25, 3, 4, 75.00, 'Office headset',
     date('now', 'localtime', '-23 days'), datetime('now', 'localtime'));

-- Every expense must have one matching approval record.

-- Expenses 1–9 are waiting for review.
INSERT INTO approvals (expense_id, status)
SELECT id, 'pending'
FROM expenses
WHERE id BETWEEN 1 AND 9;

-- Expenses 10–17 were approved by manager 1.
INSERT INTO approvals (
    expense_id, status, reviewer_id, comment, review_date
)
SELECT id, 'approved', 1, 'Approved business expense',
       date('now', 'localtime')
FROM expenses
WHERE id BETWEEN 10 AND 17;

-- Expenses 18–25 were denied by manager 2.
INSERT INTO approvals (
    expense_id, status, reviewer_id, comment, review_date
)
SELECT id, 'denied', 2, 'Not covered by company policy',
       date('now', 'localtime')
FROM expenses
WHERE id BETWEEN 18 AND 25;

COMMIT;