"""FR-E2, FR-A4: Viewing expenses, cases TC-EMP-030 to TC-EMP-034.
These tests use a real temporary SQLite database (schema.sql) and the real
service and menu code.  Nothing is mocked except the keyboard input.
"""

import sqlite3
from types import SimpleNamespace

import pytest

from employee_app import menu, services
from employee_app.db import get_connection
from employee_app.exceptions import AuthorizationError, ValidationError

pytestmark = pytest.mark.integration

def add_user(connection, username, role, full_name):
    cursor = connection.execute(
        "INSERT INTO users (username, password, role, full_name) VALUES (?, ?, ?, ?)",
        (username, "TestPassword123!", role, full_name),
    )
    return {"id": cursor.lastrowid, "username": username, "role": role,
            "full_name": full_name}


def add_expense(connection, user, category_id, amount, description, day,
                status="pending", reviewer_id=None, comment=None):
    """Insert an expense and its approval row; return the expense id."""
    expense_id = connection.execute(
        "INSERT INTO expenses (user_id, category_id, amount, description, "
        "expense_date, created_at) VALUES (?, ?, ?, ?, ?, ?)",
        (user["id"], category_id, amount, description, day, "2026-11-09 09:00:00"),
    ).lastrowid
    review_date = "2026-11-09" if status != "pending" else None
    connection.execute(
        "INSERT INTO approvals (expense_id, status, reviewer_id, comment, review_date) VALUES (?, ?, ?, ?, ?)",
        (expense_id, status, reviewer_id, comment, review_date),
    )
    return expense_id


@pytest.fixture
def view_app(db_connection):
    """Employee A and B with expenses, a manager, and employees with few or none.

    Employee A:  pending (2 rows, same day), approved, denied
    Employee B:  pending, approved            (must never appear for A)
    Employee C:  no expenses at all           (TC-EMP-034)
    Employee D:  one pending expense only     (empty filter results)
    """
    connection = db_connection
    connection.row_factory = sqlite3.Row

    manager = add_user(connection, "view_manager", "manager", "Test Manager")
    a = add_user(connection, "employee_a", "employee", "Employee A")
    b = add_user(connection, "employee_b", "employee", "Employee B")
    c = add_user(connection, "employee_c", "employee", "Employee C")
    d = add_user(connection, "employee_d", "employee", "Employee D")

    travel = connection.execute("INSERT INTO categories (name) VALUES ('Travel')").lastrowid
    meals = connection.execute("INSERT INTO categories (name) VALUES ('Meals')").lastrowid
    lodging = connection.execute("INSERT INTO categories (name) VALUES ('Lodging')").lastrowid

    ids = SimpleNamespace()
    ids.a_pending_old = add_expense(connection, a, travel, 25.50, "Taxi", "2026-11-08")
    ids.a_pending_new = add_expense(connection, a, meals, 9.99, "Coffee", "2026-11-08")
    ids.a_approved = add_expense(connection, a, meals, 12.00, "Team lunch", "2026-11-06",
                                 "approved", manager["id"], "Approved, thanks.")
    ids.a_denied = add_expense(connection, a, lodging, 80.00, "Hotel", "2026-10-20",
                               "denied", manager["id"], "Missing receipt.")
    ids.b_pending = add_expense(connection, b, travel, 40.00, "Bus pass", "2026-11-07")
    ids.b_approved = add_expense(connection, b, meals, 15.00, "Dinner", "2026-11-05",
                                 "approved", manager["id"], "OK")
    ids.d_pending = add_expense(connection, d, travel, 5.00, "Parking", "2026-11-03")
    connection.commit()

    path = connection.execute("PRAGMA database_list").fetchone()[2]
    return SimpleNamespace(
        connection=connection, path=path, ids=ids, manager=manager,
        a=a, b=b, c=c, d=d,
        factory=lambda: get_connection(path),
    )

def view(app, user, status=None):
    """Call the real service the way the menu does."""
    with app.factory() as connection:
        return [dict(row) for row in services.view_expenses(connection, user, status)]

def view_on_screen(app, monkeypatch, capsys, user, choice):
    """Run the real menu with the keyboard answer `choice`; return what is printed."""
    monkeypatch.setattr("builtins.input", lambda prompt="": choice)
    menu.handle_view(app.factory, user)
    return capsys.readouterr().out

def listed_ids(output):
    """The ID column of the printed table (rows start with a whole number)."""
    return [int(line.split()[0]) for line in output.splitlines()
            if line.split() and line.split()[0].isdigit()]

# TC-EMP-030  View all expenses

@pytest.mark.parametrize(
    "owner, expected",
    [
        ("a", ["a_pending_old", "a_pending_new", "a_approved", "a_denied"]),
        ("b", ["b_pending", "b_approved"]),
    ],
    ids=["employee-a", "employee-b"],
)
def test_view_all_returns_only_the_logged_in_employee_expenses(view_app, owner, expected):
    
    # TC-EMP-030 | FR-E2, FR-A4 - each employee sees exactly their own expenses.
    # A sees A's four expenses and none of B's - verify the return data
    rows = view(view_app, getattr(view_app, owner))

    assert {row["id"] for row in rows} == {
        getattr(view_app.ids, name) for name in expected
    }


def test_view_all_is_newest_date_first_with_higher_id_breaking_ties(view_app):
    # TC-EMP-030 | FR-E2 - newest date first; same date: newest record first.
    ids = view_app.ids

    rows = view(view_app, view_app.a)

    assert [row["id"] for row in rows] == [
        ids.a_pending_new, ids.a_pending_old, ids.a_approved, ids.a_denied,
    ]


def test_menu_view_all_prints_only_the_employees_own_rows(
    view_app, monkeypatch, capsys
):
    # TC-EMP-030 | FR-E2, FR-A4 - the screen lists A's rows and none of B's.
    # verify the printed output
    ids = view_app.ids

    output = view_on_screen(view_app, monkeypatch, capsys, view_app.a, "0")

    assert listed_ids(output) == [
        ids.a_pending_new, ids.a_pending_old, ids.a_approved, ids.a_denied,
    ]
    assert "No expenses found." not in output


@pytest.mark.negative
def test_manager_cannot_view_employee_expenses(view_app):
    # TC-EMP-030 | FR-A4 - the role is read from the database; a manager is refused.
    with pytest.raises(AuthorizationError):
        view(view_app, view_app.manager)
