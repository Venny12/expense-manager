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


# TC-EMP-048 / TC-EMP-049  Approved and Denied expenses with the manager comment

@pytest.mark.parametrize(
    "key, status, amount, comment, review_date",
    [
        ("a_approved", "approved", 12.50, "Approved, thanks.", "2026-11-05"),
        ("a_denied", "denied", 80.00, "Missing receipt.", "2026-11-03"),
    ],
    ids=["TC-EMP-048-approved", "TC-EMP-049-denied"],
)
def test_history_returns_status_reviewer_and_exact_comment(
    history_app, key, status, amount, comment, review_date
):
    # TC-EMP-048, TC-EMP-049 | FR-E5 - status, reviewer, date and the exact
    # stored comment come back for a decided expense.
    rows = {row["expense_id"]: row for row in history(history_app, history_app.a)}

    assert rows[getattr(history_app.ids, key)] == {
        "expense_id": getattr(history_app.ids, key),
        "amount": amount,
        "status": status,
        "reviewer": "Maya Manager",
        "review_date": review_date,
        "comment": comment,
    }


@pytest.mark.parametrize(
    "status, comment",
    [("approved", "Approved, thanks."), ("denied", "Missing receipt.")],
    ids=["TC-EMP-048-approved", "TC-EMP-049-denied"],
)
def test_menu_history_prints_status_and_exact_comment(
    history_app, capsys, status, comment
):
    # TC-EMP-048, TC-EMP-049 | FR-E5 - the screen shows the status and the
    # manager comment on the decided expense's row.
    output = history_on_screen(history_app, capsys, history_app.a)
    line = next(l for l in output.splitlines() if comment in l)
    assert status in line
    assert "Maya Manager" in line


@pytest.mark.parametrize(
    "comment",
    [
        "x" * 500,
        "Cafe receipt: d#*0",
        "'; DROP TABLE expenses; --",
        'He said "no" & <b>stop</b>',
        "Line one\nLine two",
    ],
    ids=["long", "unicode", "sql-text", "quotes-and-markup", "newline"],
)
def test_history_returns_the_stored_comment_unchanged(history_app, comment):
    # TC-EMP-048, TC-EMP-049 | FR-E5 - whatever the manager wrote comes back
    # exactly as stored, and the table survives.
    expense_id = add_expense(
        history_app.connection, history_app.a, 20.00, "denied",
        history_app.manager, comment, "2026-11-06",
    )
    history_app.connection.commit()
    count = history_app.connection.execute("SELECT COUNT(*) FROM expenses").fetchone()[0]
    rows = {row["expense_id"]: row for row in history(history_app, history_app.a)}
    assert rows[expense_id]["comment"] == comment
    assert history_app.connection.execute("SELECT COUNT(*) FROM expenses").fetchone()[0] == count

# What the history lists and what it leaves out
def test_history_leaves_out_pending_and_other_employees_expenses(history_app):
    # FR-E5, FR-A4 - A sees only A's decided expenses.
    ids = history_app.ids
    listed = {row["expense_id"] for row in history(history_app, history_app.a)}
    assert listed == {ids.a_approved, ids.a_denied}

def test_history_lists_newest_review_first_and_higher_id_breaks_ties(history_app):
    # FR-E5 - newest review date first; the same date: the newer approval first.
    ids = history_app.ids
    same_day_first = add_expense(history_app.connection, history_app.a, 1.00, "approved", history_app.manager, "one", "2026-11-09")
    same_day_second = add_expense(history_app.connection, history_app.a, 2.00, "denied", history_app.manager, "two", "2026-11-09")
    history_app.connection.commit()
    order = [row["expense_id"] for row in history(history_app, history_app.a)]
    assert order == [same_day_second, same_day_first, ids.a_approved, ids.a_denied]

@pytest.mark.parametrize("comment", [None, "", "   "], ids=["null", "empty", "blank"])
def test_missing_or_blank_comment_is_shown_as_no_comment(history_app, comment):
    # TC-EMP-048, TC-EMP-049 | FR-E5 - a decision without a comment still shows.
    expense_id = add_expense(history_app.connection, history_app.a, 7.00, "approved", history_app.manager, comment, "2026-11-08")
    history_app.connection.commit()
    rows = {row["expense_id"]: row for row in history(history_app, history_app.a)}
    assert rows[expense_id]["comment"] == "(no comment)"

def test_decision_without_a_reviewer_shows_an_empty_reviewer(history_app):
    # FR-E5 - a decided row with no reviewer does not crash the history.
    expense_id = add_expense(history_app.connection, history_app.a, 7.00, "approved", None, "Imported", "2026-11-08")
    history_app.connection.commit()
    rows = {row["expense_id"]: row for row in history(history_app, history_app.a)}
    assert rows[expense_id]["reviewer"] == ""

# Nothing to review
def test_employee_with_only_pending_expenses_has_no_history(history_app, capsys):
    # FR-E5 - the service returns nothing and the screen says so.
    assert history(history_app, history_app.c) == []
    output = history_on_screen(history_app, capsys, history_app.c)
    assert "No reviewed expenses found." in output

# Access to manager is denied
def test_manager_cannot_view_employee_history(history_app):
    # FR-A4 - only employees may call the employee history.
    with pytest.raises(AuthorizationError):
        history(history_app, history_app.manager)


def test_employee_dict_with_a_manager_database_role_is_refused(history_app):
    # FR-A4 - the role is checked in the database, not just in the dict.
    fake = dict(history_app.manager, role="employee")
    with pytest.raises(AuthorizationError):
        history(history_app, fake)
        