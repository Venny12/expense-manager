"""Reviewing decided expenses, cases TC-EMP-048 and TC-EMP-049.
These tests use a real temporary SQLite database (schema.sql) and the real service and menu code.  
Nothing is mocked except the keyboard output capture.
"""

import sqlite3
from types import SimpleNamespace

import pytest

from employee_app import menu, services
from employee_app.db import get_connection
from employee_app.exceptions import AuthorizationError

pytestmark = pytest.mark.integration

def add_user(connection, username, role, full_name):
    cursor = connection.execute(
        "INSERT INTO users (username, password, role, full_name) VALUES (?, ?, ?, ?)",
        (username, "TestPassword123!", role, full_name),
    )
    return {"id": cursor.lastrowid, "username": username, "role": role,
            "full_name": full_name}


def add_expense(connection, user, amount, status="pending", reviewer=None,
                comment=None, review_date=None, description="Test expense"):
    """Insert an expense and its approval row; return the expense id."""
    expense_id = connection.execute(
        "INSERT INTO expenses (user_id, category_id, amount, description, "
        "expense_date, created_at) VALUES (?, 1, ?, ?, '2026-11-01', '2026-11-01 09:00:00')",
        (user["id"], amount, description),
    ).lastrowid
    connection.execute(
        "INSERT INTO approvals (expense_id, status, reviewer_id, comment, review_date) "
        "VALUES (?, ?, ?, ?, ?)",
        (expense_id, status, reviewer["id"] if reviewer else None, comment, review_date),
    )
    return expense_id


@pytest.fixture
def history_app(db_connection):
    """Employees A, B, C and a manager, each with a different mix of expenses.

    Employee A:  approved (with comment), denied (with comment), pending
    Employee B:  approved                      (must never appear for A)
    Employee C:  one pending expense only      (nothing to review yet)
    """
    connection = db_connection
    connection.row_factory = sqlite3.Row
    connection.execute("INSERT INTO categories (name) VALUES ('Travel')")

    manager = add_user(connection, "history_manager", "manager", "Maya Manager")
    a = add_user(connection, "employee_a", "employee", "Employee A")
    b = add_user(connection, "employee_b", "employee", "Employee B")
    c = add_user(connection, "employee_c", "employee", "Employee C")

    ids = SimpleNamespace()
    ids.a_approved = add_expense(connection, a, 12.50, "approved", manager, "Approved, thanks.", "2026-11-05")
    ids.a_denied = add_expense(connection, a, 80.00, "denied", manager, "Missing receipt.", "2026-11-03")
    ids.a_pending = add_expense(connection, a, 9.99)
    ids.b_approved = add_expense(connection, b, 15.00, "approved", manager, "Other employee comment", "2026-11-04")
    ids.c_pending = add_expense(connection, c, 5.00)
    connection.commit()

    path = connection.execute("PRAGMA database_list").fetchone()[2]
    return SimpleNamespace(
        connection=connection, ids=ids, manager=manager, a=a, b=b, c=c,
        factory=lambda: get_connection(path),
    )

def history(app, user):
    # Call the real service the way the menu does.
    with app.factory() as connection:
        return services.get_review_history(connection, user)


def history_on_screen(app, capsys, user):
    # Run the real menu handler and return what it prints.
    menu.handle_history(app.factory, user)
    return capsys.readouterr().out
