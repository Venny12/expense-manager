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

# TC-EMP-035  Edit own Pending expense

def test_edit_own_pending_expense_saves_every_field_and_stays_pending(edit_app, monkeypatch, capsys):
    # TC-EMP-035 | FR-E3 - changed values persist; the status stays pending.
    app, expense_id = edit_app, edit_app.ids.a_pending
    output = edit_on_screen(app, monkeypatch, capsys, app.a,
                            [str(expense_id), "30.75", str(app.meals), "New taxi", "2026-11-07"])

    expense, approval = stored(app, expense_id)
    assert "Expense %d updated." % expense_id in output
    assert (expense["amount"], expense["category_id"], expense["description"],
            expense["expense_date"]) == (30.75, app.meals, "New taxi", "2026-11-07")
    assert approval["status"] == "pending"
    assert (approval["reviewer_id"], approval["comment"], approval["review_date"]) == (None, None, None)

def test_edit_changes_only_the_edited_expense_and_keeps_ownership(edit_app, monkeypatch, capsys):
    # TC-EMP-035 | FR-E3, FR-A4 - no other row is touched; owner and created_at stay.
    app, expense_id = edit_app, edit_app.ids.a_pending
    before_expenses, before_approvals = snapshot(app)
    edit_on_screen(app, monkeypatch, capsys, app.a,
                   [str(expense_id), "30.75", str(app.meals), "New taxi", "2026-11-07"])
    after_expenses, after_approvals = snapshot(app)
    assert after_approvals == before_approvals
    changed = [(b, a) for b, a in zip(before_expenses, after_expenses) if b != a]
    assert len(after_expenses) == len(before_expenses)
    assert len(changed) == 1
    old, new = changed[0]
    assert (new[0], new[1], new[-1]) == (old[0], old[1], old[-1])  # id, user_id, created_at

# TC-EMP-041  Enter keeps the current value

def test_enter_keeps_current_values_and_only_the_description_changes(edit_app, monkeypatch, capsys):
    # TC-EMP-041 | FR-E3 - Enter at every prompt except the description.
    app, expense_id = edit_app, edit_app.ids.a_pending
    edit_on_screen(app, monkeypatch, capsys, app.a,
                   [str(expense_id), "", "", "Airport taxi", ""])
    expense, approval = stored(app, expense_id)
    assert (expense["amount"], expense["category_id"], expense["expense_date"]) == (
        25.50, app.travel, "2026-11-02")
    assert expense["description"] == "Airport taxi"
    assert approval["status"] == "pending"

def test_enter_at_every_prompt_leaves_the_stored_record_unchanged(edit_app, monkeypatch, capsys):
    # TC-EMP-041 | FR-E3 - keeping everything saves the same values.
    app = edit_app
    before = snapshot(app)
    output = edit_on_screen(app, monkeypatch, capsys, app.a,
                            [str(app.ids.a_pending), "", "", "", ""])
    assert snapshot(app) == before
    assert "updated." in output

def test_edit_prompts_show_the_stored_values(edit_app, monkeypatch, capsys):
    # TC-EMP-041 | FR-E3 - the current values read from the database are shown.
    app = edit_app
    seen = []
    answers = iter([str(app.ids.a_pending), "", "", "", ""])

    def fake_input(prompt=""):
        seen.append(prompt)
        return next(answers)

    monkeypatch.setattr("builtins.input", fake_input)
    menu.handle_edit(app.factory, app.a)

    assert seen[1:] == [
        "Amount [25.50]: ",
        f"Category number [{app.travel} - Travel]: ",
        "Description [Taxi]: ",
        "Expense date [2026-11-02]: ",
    ]

# TC-EMP-036  Another employee's expense

def test_edit_of_another_employees_expense_is_denied_and_nothing_changes(edit_app):
    # TC-EMP-036 | FR-A4 - Employee A cannot change Employee B's pending expense.
    app = edit_app
    before = snapshot(app)

    with app.factory() as connection:
        with pytest.raises(AuthorizationError, match="only change your own"):
            services.update_expense(connection, app.a, app.ids.b_pending,
                                    "1.00", app.travel, "Hijacked", "2026-11-07")
    assert snapshot(app) == before

def test_menu_stops_at_once_for_another_employees_expense(edit_app, monkeypatch, capsys):
    # TC-EMP-036 | FR-A4 - the screen refuses before asking for any new value.
    app = edit_app
    before = snapshot(app)
    answers = iter([str(app.ids.b_pending)])  # a second prompt would raise StopIteration
    monkeypatch.setattr("builtins.input", lambda prompt="": next(answers))

    with pytest.raises(AuthorizationError):
        menu.handle_edit(app.factory, app.a)

    assert snapshot(app) == before

# TC-EMP-037 / TC-EMP-038  Approved and Denied expenses
@pytest.mark.parametrize(
    "key",
    ["a_approved", "a_denied"],
    ids=["TC-EMP-037-approved", "TC-EMP-038-denied"],
)
def test_edit_of_a_decided_expense_is_refused_and_nothing_changes(edit_app, key):
    # TC-EMP-037, TC-EMP-038 | FR-E3 - values, status, reviewer and comment are kept.
    app = edit_app
    before = snapshot(app)
    with app.factory() as connection:
        with pytest.raises(InvalidStateError, match="no longer pending"):
            services.update_expense(connection, app.a, getattr(app.ids, key),
                                    "1.00", app.travel, "Changed", "2026-11-07")

    assert snapshot(app) == before

@pytest.mark.parametrize("key", ["a_approved", "a_denied"], ids=["approved", "denied"])
def test_menu_refuses_a_decided_expense_before_asking_for_values(edit_app, monkeypatch, key):
    # TC-EMP-037, TC-EMP-038 | FR-E3 - the refusal comes straight after the ID.
    app = edit_app
    before = snapshot(app)
    answers = iter([str(getattr(app.ids, key))])
    monkeypatch.setattr("builtins.input", lambda prompt="": next(answers))

    with pytest.raises(InvalidStateError):
        menu.handle_edit(app.factory, app.a)

    assert snapshot(app) == before

# TC-EMP-039  Missing expense ID
def test_edit_of_a_missing_expense_is_not_found_and_nothing_changes(edit_app):
    # TC-EMP-039 | FR-E3 - ID 999999 does not exist.
    app = edit_app
    before = snapshot(app)
    with app.factory() as connection:
        with pytest.raises(NotFoundError, match="Expense not found."):
            services.update_expense(connection, app.a, 999999,
                                    "1.00", app.travel, "Ghost", "2026-11-07")

    assert snapshot(app) == before

def test_menu_reports_a_missing_expense_with_the_not_found_message(edit_app, monkeypatch):
    # TC-EMP-039 | FR-E3 - the Edit screen refuses straight after the ID.
    app = edit_app
    before = snapshot(app)
    answers = iter(["999999"])  # a second prompt would raise StopIteration
    monkeypatch.setattr("builtins.input", lambda prompt="": next(answers))

    with pytest.raises(NotFoundError, match="Expense not found."):
        menu.handle_edit(app.factory, app.a)

    assert snapshot(app) == before

# Related checks with a real database

@pytest.mark.negative
@pytest.mark.parametrize(
    "amount, category, description, day",
    [
        ("-5", 1, "Taxi", "2026-11-07"),
        ("0", 1, "Taxi", "2026-11-07"),
        ("abc", 1, "Taxi", "2026-11-07"),
        ("5", 999, "Taxi", "2026-11-07"),
        ("5", 1, "   ", "2026-11-07"),
        ("5", 1, "Taxi", "2999-01-01"),
        ("5", 1, "Taxi", "07/11/2026"),
    ],
    ids=["negative", "zero", "text", "unknown-category", "blank-description",
         "future-date", "bad-date-format"],
)
def test_invalid_values_are_rejected_and_the_stored_record_is_unchanged(edit_app, amount, category, description, day):
    # TC-EMP-040 (real-database side) | FR-S2 - validation fails before any write.
    app = edit_app
    before = snapshot(app)

    with app.factory() as connection:
        with pytest.raises(ValidationError):
            services.update_expense(connection, app.a, app.ids.a_pending,
                                    amount, category, description, day)

    assert snapshot(app) == before

def test_sql_looking_description_is_stored_as_text_when_editing(edit_app):
    # FR-S2 - the edit uses parameters, so this text cannot run as SQL.
    app, text = edit_app, "'; DROP TABLE expenses;--"

    with app.factory() as connection:
        services.update_expense(connection, app.a, app.ids.a_pending,
                                "5.00", app.travel, text, "2026-11-07")

    expense, _ = stored(app, app.ids.a_pending)
    assert expense["description"] == text
    assert app.connection.execute("SELECT COUNT(*) FROM expenses").fetchone()[0] == 5

def test_manager_cannot_edit_an_employees_pending_expense(edit_app):
    # FR-A4 - only employees may edit; the manager's attempt changes nothing.
    app = edit_app
    before = snapshot(app)

    with app.factory() as connection:
        with pytest.raises(AuthorizationError):
            services.update_expense(connection, app.manager, app.ids.a_pending,
                                    "1.00", app.travel, "Changed", "2026-11-07")

    assert snapshot(app) == before