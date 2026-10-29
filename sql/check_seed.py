import os
import sqlite3

database_path = os.environ.get("EM_DB_PATH")

if not database_path:
    raise SystemExit("EM_DB_PATH is not set.")

with sqlite3.connect(
    f"file:{database_path}?mode=rw", uri=True
) as connection:
    print("Accounts by role:")
    for row in connection.execute(
        "SELECT role, COUNT(*) FROM users GROUP BY role"
    ):
        print(row)

    print("\nExpenses by status:")
    for row in connection.execute(
        "SELECT status, COUNT(*) FROM approvals GROUP BY status"
    ):
        print(row)

    print("\nBoundary values:")
    for row in connection.execute("""
        SELECT id, amount, LENGTH(description),
               expense_date = date('now', 'localtime', '-90 days')
        FROM expenses
        WHERE id IN (1, 2, 3)
        ORDER BY id
    """):
        print(row)

    print("\nForeign key problems:")
    problems = connection.execute(
        "PRAGMA foreign_key_check"
    ).fetchall()
    print(problems)