"""FR-E3, FR-A4, FR-S2: Editing an expense, cases TC-EMP-035 to TC-EMP-039 and 041.
These tests use a real temporary SQLite database (schema.sql) and the real
menu and service code.  Nothing is mocked except the keyboard input.
The mocked-database tests for the same feature are in tests/unit/test_edit_expense.py.
"""

import sqlite3
from types import SimpleNamespace

import pytest

from employee_app import menu, services
from employee_app.db import get_connection
from employee_app.exceptions import (
    AuthorizationError,
    InvalidStateError,
    NotFoundError,
    ValidationError,
)

pytestmark = pytest.mark.integration

def add_user(connection, username, role, full_name):
    cursor = connection.execute(
        "INSERT INTO users (username, password, role, full_name) VALUES (?, ?, ?, ?)",
        (username, "TestPassword123!", role, full_name),
    )
    return {"id": cursor.lastrowid, "username": username, "role": role,
            "full_name": full_name}

def add_expense(connection, user, category_id, amount, description, day,
                status="pending", reviewer=None, comment=None):
    # Insert an expense and its approval row; return the expense id.
    expense_id = connection.execute(
        "INSERT INTO expenses (user_id, category_id, amount, description, "
        "expense_date, created_at) VALUES (?, ?, ?, ?, ?, '2026-11-01 09:00:00')",
        (user["id"], category_id, amount, description, day),
    ).lastrowid
    connection.execute(
        "INSERT INTO approvals (expense_id, status, reviewer_id, comment, review_date) "
        "VALUES (?, ?, ?, ?, ?)",
        (expense_id, status, reviewer["id"] if reviewer else None, comment,
         "2026-11-05" if status != "pending" else None),
    )
    return expense_id


@pytest.fixture
def edit_app(db_connection):
    # Employee A (pending, approved, denied), employee B (pending), a manager.
    connection = db_connection
    connection.row_factory = sqlite3.Row
    travel = connection.execute("INSERT INTO categories (name) VALUES ('Travel')").lastrowid
    meals = connection.execute("INSERT INTO categories (name) VALUES ('Meals')").lastrowid

    manager = add_user(connection, "edit_manager", "manager", "Edit Manager")
    a = add_user(connection, "edit_a", "employee", "Employee A")
    b = add_user(connection, "edit_b", "employee", "Employee B")

    ids = SimpleNamespace()
    ids.a_pending = add_expense(connection, a, travel, 25.50, "Taxi", "2026-11-02")
    ids.a_other_pending = add_expense(connection, a, travel, 9.00, "Bus", "2026-11-03")
    ids.a_approved = add_expense(connection, a, meals, 12.00, "Team lunch", "2026-11-04",
                                 "approved", manager, "Approved, thanks.")
    ids.a_denied = add_expense(connection, a, meals, 80.00, "Hotel", "2026-11-05",
                               "denied", manager, "Missing receipt.")
    ids.b_pending = add_expense(connection, b, travel, 40.00, "Bus pass", "2026-11-06")
    connection.commit()

    path = connection.execute("PRAGMA database_list").fetchone()[2]
    return SimpleNamespace(
        connection=connection, ids=ids, a=a, b=b, manager=manager,
        travel=travel, meals=meals,
        factory=lambda: get_connection(path),
    )


def snapshot(app):
    # Every expense and approval row, so 'nothing changed' is one comparison.
    rows = lambda table: [tuple(r) for r in app.connection.execute(f"SELECT * FROM {table} ORDER BY id")]
    return rows("expenses"), rows("approvals")

def stored(app, expense_id):
    # The expense row and its approval row as dictionaries.
    
    expense = app.connection.execute("SELECT * FROM expenses WHERE id = ?", (expense_id,)).fetchone()
    approval = app.connection.execute("SELECT * FROM approvals WHERE expense_id = ?", (expense_id,)).fetchone()
    return dict(expense), dict(approval)

def edit_on_screen(app, monkeypatch, capsys, user, answers):
    # Run the real Edit screen with scripted answers; return what it printed.
    remaining = iter(answers)
    monkeypatch.setattr("builtins.input", lambda prompt="": next(remaining))
    menu.handle_edit(app.factory, user)
    assert list(remaining) == [], "the screen did not ask for every scripted answer"
    return capsys.readouterr().out

