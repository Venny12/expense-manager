"""FR-E4, FR-A4: Deleting an expense, cases TC-EMP-042 to TC-EMP-046.
These tests use a real temporary SQLite database (schema.sql) and the real menu and service code.  
Nothing is mocked except the keyboard input.
"""

import sqlite3
from types import SimpleNamespace

import pytest

from employee_app import menu, services
from employee_app.db import get_connection
from employee_app.exceptions import AuthorizationError, InvalidStateError, NotFoundError

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
def delete_app(db_connection):
    # Employee A (two pending, approved, denied), employee B (pending), a manager.
    connection = db_connection
    connection.row_factory = sqlite3.Row
    travel = connection.execute("INSERT INTO categories (name) VALUES ('Travel')").lastrowid
    manager = add_user(connection, "delete_manager", "manager", "Delete Manager")
    a = add_user(connection, "delete_a", "employee", "Employee A")
    b = add_user(connection, "delete_b", "employee", "Employee B")

    ids = SimpleNamespace()
    ids.a_pending = add_expense(connection, a, travel, 25.50, "Taxi", "2026-11-02")
    ids.a_other_pending = add_expense(connection, a, travel, 9.00, "Bus", "2026-11-03")
    ids.a_approved = add_expense(connection, a, travel, 12.00, "Team lunch", "2026-11-04", "approved", manager, "Approved, thanks.")
    ids.a_denied = add_expense(connection, a, travel, 80.00, "Hotel", "2026-11-05", "denied", manager, "Missing receipt.")
    ids.b_pending = add_expense(connection, b, travel, 40.00, "Bus pass", "2026-11-06")
    connection.commit()

    path = connection.execute("PRAGMA database_list").fetchone()[2]
    return SimpleNamespace(
        connection=connection, ids=ids, a=a, b=b, manager=manager,
        factory=lambda: get_connection(path),
    )

def snapshot(app):
    # Every expense and approval row, so 'nothing changed' is one comparison.
    rows = lambda table: [tuple(r) for r in app.connection.execute(f"SELECT * FROM {table} ORDER BY id")]
    return rows("expenses"), rows("approvals")

def exists(app, expense_id):
    # (expense row present, approval row present) for one expense id.
    return tuple(
        app.connection.execute(sql, (expense_id,)).fetchone()[0] == 1
        for sql in ("SELECT COUNT(*) FROM expenses WHERE id = ?",
                    "SELECT COUNT(*) FROM approvals WHERE expense_id = ?")
    )

def delete_on_screen(app, monkeypatch, capsys, user, answers):
    # Run the real Delete screen with scripted answers; return what it printed.
    remaining = iter(answers)
    monkeypatch.setattr("builtins.input", lambda prompt="": next(remaining))
    menu.handle_delete(app.factory, user)
    assert list(remaining) == [], "the screen did not ask for every scripted answer"
    return capsys.readouterr().out

# TC-EMP-042  Delete own Pending expense

def test_confirmed_delete_removes_the_expense_and_its_approval_row(delete_app, monkeypatch, capsys):
    # TC-EMP-042 | FR-E4 - both rows are gone after answering y.
    app, expense_id = delete_app, delete_app.ids.a_pending
    output = delete_on_screen(app, monkeypatch, capsys, app.a, [str(expense_id), "y"])
    assert "Expense %d deleted." % expense_id in output
    assert exists(app, expense_id) == (False, False)

def test_deleted_expense_no_longer_appears_in_view_expenses(delete_app):
    # TC-EMP-042 | FR-E2, FR-E4 - View Expenses no longer lists it.
    app, ids = delete_app, delete_app.ids
    with app.factory() as connection:
        services.delete_expense(connection, app.a, ids.a_pending)
    with app.factory() as connection:
        listed = {row["id"] for row in services.view_expenses(connection, app.a)}
    assert listed == {ids.a_other_pending, ids.a_approved, ids.a_denied}

def test_delete_removes_exactly_one_expense_and_leaves_every_other_row(delete_app):
    # TC-EMP-042 | FR-E4, FR-A4 - other expenses and approvals are untouched.
    app, expense_id = delete_app, delete_app.ids.a_pending
    before_expenses, before_approvals = snapshot(app)
    with app.factory() as connection:
        services.delete_expense(connection, app.a, expense_id)
    after_expenses, after_approvals = snapshot(app)
    assert after_expenses == [r for r in before_expenses if r[0] != expense_id]
    assert after_approvals == [r for r in before_approvals if r[1] != expense_id]

def test_deleting_the_same_expense_twice_is_not_found_the_second_time(delete_app):
    # TC-EMP-042, TC-EMP-046 | FR-E4 - the ID no longer exists after the delete.
    app, expense_id = delete_app, delete_app.ids.a_pending
    with app.factory() as connection:
        services.delete_expense(connection, app.a, expense_id)
    after_first = snapshot(app)
    with app.factory() as connection:
        with pytest.raises(NotFoundError):
            services.delete_expense(connection, app.a, expense_id)
    assert snapshot(app) == after_first

# TC-EMP-043  Another employee's expense

def test_delete_of_another_employees_expense_is_denied_and_nothing_changes(delete_app):
    # TC-EMP-043 | FR-A4 - Employee A cannot delete Employee B's pending expense.
    app = delete_app
    before = snapshot(app)
    with app.factory() as connection:
        with pytest.raises(AuthorizationError, match="only change your own"):
            services.delete_expense(connection, app.a, app.ids.b_pending)
    assert snapshot(app) == before
    assert exists(app, app.ids.b_pending) == (True, True)

def test_menu_stops_before_confirmation_for_another_employees_expense(delete_app, monkeypatch):
    # TC-EMP-043 | FR-A4 - the screen refuses before asking "Delete this expense?".
    app = delete_app
    before = snapshot(app)
    answers = iter([str(app.ids.b_pending)])  # a confirmation prompt would raise StopIteration
    monkeypatch.setattr("builtins.input", lambda prompt="": next(answers))

    with pytest.raises(AuthorizationError):
        menu.handle_delete(app.factory, app.a)

    assert snapshot(app) == before

# TC-EMP-044 / TC-EMP-045  Approved and Denied expenses
@pytest.mark.parametrize(
    "key",
    ["a_approved", "a_denied"],
    ids=["TC-EMP-044-approved", "TC-EMP-045-denied"],
)
def test_delete_of_a_decided_expense_is_refused_and_both_rows_remain(delete_app, key):
    # TC-EMP-044, TC-EMP-045 | FR-E4 - only pending expenses can be deleted.
    app = delete_app
    before = snapshot(app)
    with app.factory() as connection:
        with pytest.raises(InvalidStateError, match="no longer pending"):
            services.delete_expense(connection, app.a, getattr(app.ids, key))

    assert snapshot(app) == before
    assert exists(app, getattr(app.ids, key)) == (True, True)

@pytest.mark.parametrize("key", ["a_approved", "a_denied"], ids=["approved", "denied"])
def test_menu_refuses_a_decided_expense_before_asking_to_confirm(delete_app, monkeypatch, key):
    # TC-EMP-044, TC-EMP-045 | FR-E4 - the refusal comes straight after the ID.
    app = delete_app
    before = snapshot(app)
    answers = iter([str(getattr(app.ids, key))])
    monkeypatch.setattr("builtins.input", lambda prompt="": next(answers))

    with pytest.raises(InvalidStateError):
        menu.handle_delete(app.factory, app.a)

    assert snapshot(app) == before

# TC-EMP-046  Missing expense ID

def test_delete_of_a_missing_expense_is_not_found_and_nothing_changes(delete_app):
    # TC-EMP-046 | FR-E4 - ID 999999 does not exist.
    app = delete_app
    before = snapshot(app)
    with app.factory() as connection:
        with pytest.raises(NotFoundError, match="Expense not found."):
            services.delete_expense(connection, app.a, 999999)

    assert snapshot(app) == before

def test_menu_reports_a_missing_expense_before_asking_to_confirm(delete_app, monkeypatch):
    # TC-EMP-046 | FR-E4 - the Delete screen refuses straight after the ID.
    app = delete_app
    before = snapshot(app)
    answers = iter(["999999"])
    monkeypatch.setattr("builtins.input", lambda prompt="": next(answers))

    with pytest.raises(NotFoundError, match="Expense not found."):
        menu.handle_delete(app.factory, app.a)
    assert snapshot(app) == before

# Related checks with a real database
def test_manager_cannot_delete_an_employees_pending_expense(delete_app):
    # FR-A4 - only employees may delete; the manager's attempt changes nothing.
    app = delete_app
    before = snapshot(app)
    with app.factory() as connection:
        with pytest.raises(AuthorizationError):
            services.delete_expense(connection, app.manager, app.ids.a_pending)
    assert snapshot(app) == before