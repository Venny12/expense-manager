"""Database contract cases TC-DB-001 to TC-DB-004 and TC-DB-009.

Every test runs against a real temporary SQLite database built from sql/schema.sql (and sql/seed.sql where the case says "seed").
Expected numbers are written out by hand, not read back from the application, 
so a change to the schema or seed script makes a test fail.
"""

import sqlite3
from datetime import date
from decimal import Decimal
from pathlib import Path

import pytest

from employee_app import services

pytestmark = pytest.mark.integration

SQL_DIR = Path(__file__).resolve().parents[3] / "sql"

REQUIRED_COLUMNS = {
    "users": ["id", "username", "password", "role", "full_name"],
    "categories": ["id", "name"],
    "expenses": ["id", "user_id", "category_id", "amount", "description", "expense_date", "created_at"],
    "approvals": ["id", "expense_id", "status", "reviewer_id", "comment", "review_date"],
}

def run_script(connection, name):
    connection.executescript(
        (SQL_DIR / name).read_text(encoding="utf-8-sig")
    )

def add_user(connection, username, full_name, role="employee"):
    cursor = connection.execute(
        "INSERT INTO users (username, password, role, full_name) "
        "VALUES (?, 'Password123!', ?, ?)",
        (username, role, full_name),
    )
    return cursor.lastrowid

def add_expense(connection, user_id, amount, description="Taxi",
                category_id=1, expense_date="2026-11-01", status="pending"):
    cursor = connection.execute(
        "INSERT INTO expenses (user_id, category_id, amount, description, "
        "expense_date, created_at) VALUES (?, ?, ?, ?, ?, '2026-11-02 09:00:00')",
        (user_id, category_id, amount, description, expense_date),
    )
    connection.execute(
        "INSERT INTO approvals (expense_id, status) VALUES (?, ?)",
        (cursor.lastrowid, status),
    )
    return cursor.lastrowid


@pytest.fixture
def seeded(db_connection):
    run_script(db_connection, "seed.sql")
    return db_connection

@pytest.fixture
def two_employees(db_connection):
    db_connection.execute("INSERT INTO categories (name) VALUES ('Travel')")
    a = add_user(db_connection, "emp_a", "Employee A")
    b = add_user(db_connection, "emp_b", "Employee B")
    return db_connection, a, b

# TC-DB-001
@pytest.mark.smoke
def test_schema_creates_required_tables_and_columns(db_connection):
    tables = {
        row[0] for row in db_connection.execute(
            "SELECT name FROM sqlite_master WHERE type = 'table' "
            "AND name NOT LIKE 'sqlite_%'"
        )
    }
    assert tables == set(REQUIRED_COLUMNS)

    for table, expected in REQUIRED_COLUMNS.items():
        columns = [
            row[1] for row in
            db_connection.execute(f"PRAGMA table_info({table})")
        ]
        assert columns == expected, table


# TC-DB-002
def test_updated_expense_is_persisted_and_other_fields_unchanged(
    db_connection,
):
    db_connection.row_factory = sqlite3.Row
    db_connection.execute("INSERT INTO categories (name) VALUES ('Travel')")
    db_connection.execute("INSERT INTO categories (name) VALUES ('Meals')")
    user_id = add_user(db_connection, "emp_a", "Employee A")
    expense_id = add_expense(db_connection, user_id, 12.50, "Old text")
    db_connection.commit()
    before = dict(db_connection.execute(
        "SELECT * FROM expenses WHERE id = ?", (expense_id,)).fetchone())

    services.update_expense(
        db_connection, {"id": user_id, "role": "employee"}, expense_id,
        "99.99", 2, "New text", "2026-11-05", today=date(2026, 11, 9),
    )

    # A second connection proves the change was committed, not just visible inside the open transaction.
    path = db_connection.execute("PRAGMA database_list").fetchone()[2]
    other = sqlite3.connect(path)
    other.row_factory = sqlite3.Row
    try:
        after = dict(other.execute("SELECT * FROM expenses WHERE id = ?", (expense_id,)).fetchone())
        approval = other.execute("SELECT status FROM approvals WHERE expense_id = ?",
            (expense_id,)).fetchone()[0]
    finally:
        other.close()

    assert after["amount"] == 99.99
    assert after["category_id"] == 2
    assert after["description"] == "New text"
    assert after["expense_date"] == "2026-11-05"
    for unchanged in ("id", "user_id", "created_at"):
        assert after[unchanged] == before[unchanged]
    assert approval == "pending"


# TC-DB-003
def test_each_expense_references_the_correct_employee(two_employees):
    connection, a, b = two_employees
    a_ids = {add_expense(connection, a, 10, "A1"), add_expense(connection, a, 20, "A2")}
    b_ids = {add_expense(connection, b, 30, "B1")}

    def ids_for(employee):
        return {r[0] for r in connection.execute(
            "SELECT id FROM expenses WHERE user_id = ?", (employee,))}

    assert ids_for(a) == a_ids
    assert ids_for(b) == b_ids
    owners = dict(connection.execute(
        "SELECT e.id, u.full_name FROM expenses e "
        "JOIN users u ON u.id = e.user_id"))
    assert {owners[i] for i in a_ids} == {"Employee A"}
    assert {owners[i] for i in b_ids} == {"Employee B"}


@pytest.mark.negative
def test_expense_for_unknown_employee_is_rejected(two_employees):
    connection, _, _ = two_employees
    with pytest.raises(sqlite3.IntegrityError):
        add_expense(connection, 9999, 10)


# TC-DB-004
@pytest.mark.smoke
def test_seed_script_loads_expected_rows(seeded):
    def count(table, where="1=1"):
        return seeded.execute(
            f"SELECT COUNT(*) FROM {table} WHERE {where}").fetchone()[0]

    assert count("categories") == 6
    assert count("users") == 6
    assert count("users", "role = 'manager'") == 2
    assert count("users", "role = 'employee'") == 4
    assert count("expenses") == 25
    assert count("approvals") == 25
    assert count("approvals", "status = 'pending'") == 9
    assert count("approvals", "status = 'approved'") == 8
    assert count("approvals", "status = 'denied'") == 8
    assert [r[0] for r in seeded.execute(
        "SELECT name FROM categories ORDER BY id")] == [
        "Travel", "Meals", "Lodging", "Office Supplies", "Training", "Other"]


# TC-DB-009 (Python has no report service; the Java ReportService is
# compared with SQL in DatabaseReportTotalsIntegrationTest.)
def test_per_employee_totals_match_hand_written_seed_totals(seeded):
    # Independent reference: totals worked out by hand from seed.sql.
    expected = {
        "Maya Singh": (7, Decimal("206.16")),
        "Omar Ali": (6, Decimal("10598.05")),
        "Lina Park": (6, Decimal("286.98")),
        "Noah Wilson": (6, Decimal("929.50")),
    }
    rows = seeded.execute(
        "SELECT u.full_name, COUNT(*), SUM(e.amount) FROM expenses e "
        "JOIN users u ON u.id = e.user_id GROUP BY u.id").fetchall()
    actual = {n: (c, Decimal(str(round(t, 2)))) for n, c, t in rows}
    assert actual == expected