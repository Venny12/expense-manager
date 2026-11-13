"""FR-E1, VR-01–04: submission cases TC-EMP-012–029."""

import sqlite3
from datetime import date, timedelta

import pytest

from employee_app import menu, services, validators
from employee_app.db import get_connection


pytestmark = pytest.mark.integration

TODAY = date(2026, 11, 9)

AMOUNT_ERROR = (
    "Amount must be greater than 0, at most 10000.00, "
    "and contain at most 2 decimal places."
)
DESCRIPTION_ERROR = (
    "Description must contain 1–200 characters after trimming."
)
CATEGORY_ERROR = "Choose a category number from the list."
DATE_ERROR = (
    "Date must be a real date in YYYY-MM-DD format, "
    "between today and 90 days ago."
)


@pytest.fixture
def submission_app(db_connection, monkeypatch):
    """Use a fresh database and a fixed date for each test."""
    db_connection.row_factory = sqlite3.Row

    cursor = db_connection.execute(
        """
        INSERT INTO users (username, password, role, full_name)
        VALUES (?, ?, ?, ?)
        """,
        ("submission_employee", "TestPassword123!", "employee", "Test Employee"),
    )
    user = {"id": cursor.lastrowid, "role": "employee"}

    cursor = db_connection.execute(
        "INSERT INTO categories (name) VALUES (?)",
        ("Travel",),
    )
    category_id = cursor.lastrowid
    db_connection.commit()

    path = db_connection.execute("PRAGMA database_list").fetchone()[2]

    class FixedDate(date):
        @classmethod
        def today(cls):
            return cls(TODAY.year, TODAY.month, TODAY.day)

    monkeypatch.setattr(validators, "date", FixedDate)

    def connection_factory():
        return get_connection(path)

    return db_connection, user, category_id, connection_factory


def assert_empty(connection):
    """Neither half of a submission may exist before saving."""
    assert connection.execute(
        "SELECT COUNT(*) FROM expenses"
    ).fetchone()[0] == 0
    assert connection.execute(
        "SELECT COUNT(*) FROM approvals"
    ).fetchone()[0] == 0


def assert_saved(connection, user, category_id, amount, description, day):
    """Check the submitted fields and the linked pending approval."""
    expenses = connection.execute("SELECT * FROM expenses").fetchall()
    approvals = connection.execute("SELECT * FROM approvals").fetchall()

    assert len(expenses) == 1
    assert len(approvals) == 1

    expense = expenses[0]
    approval = approvals[0]

    assert expense["user_id"] == user["id"]
    assert expense["category_id"] == category_id
    assert expense["amount"] == pytest.approx(float(amount))
    assert expense["description"] == description
    assert expense["expense_date"] == day

    assert approval["expense_id"] == expense["id"]
    assert approval["status"] == "pending"
    assert approval["reviewer_id"] is None
    assert approval["comment"] is None
    assert approval["review_date"] is None

    return expense["id"]


@pytest.mark.parametrize(
    "amount, days_ago",
    [
        pytest.param("25.50", 0, id="TC-EMP-012"),

    ],
)
def test_valid_submission_is_saved(
    submission_app, amount, days_ago, monkeypatch, capsys
):
    """FR-E1, VR-01/04: save valid fields and show the new ID."""
    connection, user, category_id, factory = submission_app
    day = (TODAY - timedelta(days=days_ago)).isoformat()

    answers = iter([amount, str(category_id), "Taxi fare", day])
    monkeypatch.setattr("builtins.input", lambda prompt: next(answers))

    menu.handle_submit(factory, user)

    expense_id = assert_saved(
        connection, user, category_id, amount, "Taxi fare", day
    )
    assert (
        f"Expense submitted. ID: {expense_id}. Status: pending."
        in capsys.readouterr().out
    )


@pytest.mark.negative
@pytest.mark.parametrize(
    "field, invalid_value, message",
    [
        pytest.param("amount", "abc", AMOUNT_ERROR, id="TC-EMP-013"),
        pytest.param("category", "999999", CATEGORY_ERROR, id="TC-EMP-014"),
        pytest.param(
            "description", "A" * 201, DESCRIPTION_ERROR, id="TC-EMP-015"
        ),
        pytest.param("date", "2026-11-10", DATE_ERROR, id="TC-EMP-020"),
    ],
)
def test_invalid_submission_reprompts_before_saving(
    submission_app, field, invalid_value, message, monkeypatch, capsys
):
    """FR-E1, VR-01–04: invalid input cannot create a partial record."""
    connection, user, category_id, factory = submission_app

    fields = ["amount", "category", "description", "date"]
    values = ["25.50", str(category_id), "Taxi fare", TODAY.isoformat()]
    index = fields.index(field)

    # Enter an invalid value, then correct it at the repeated prompt.
    answers = values[:index] + [invalid_value] + values[index:]
    prompts = []

    def fake_input(prompt):
        prompts.append(prompt)

        if len(prompts) == index + 2:
            # The app has rejected the value and is asking again.
            assert prompts[-1] == prompts[-2]
            assert_empty(connection)

        return answers.pop(0)

    monkeypatch.setattr("builtins.input", fake_input)

    menu.handle_submit(factory, user)

    output = capsys.readouterr().out
    assert message in output
    assert len(prompts) == 5
    assert answers == []

    # Only the corrected submission is saved.
    assert_saved(
        connection, user, category_id, "25.50", "Taxi fare", TODAY.isoformat()
    )


def test_expense_and_pending_approval_are_saved_together(
    submission_app, monkeypatch, capsys
):
    """FR-E1, TC-EMP-027: save both rows and print their expense ID."""
    connection, user, category_id, factory = submission_app
    answers = iter(["50.00", str(category_id), "Train fare", TODAY.isoformat()])
    monkeypatch.setattr("builtins.input", lambda prompt: next(answers))

    menu.handle_submit(factory, user)

    expense_id = assert_saved(
        connection, user, category_id, "50.00", "Train fare", TODAY.isoformat()
    )
    assert (
        f"Expense submitted. ID: {expense_id}. Status: pending."
        in capsys.readouterr().out
    )


@pytest.mark.negative
def test_approval_failure_rolls_back_and_shows_friendly_error(
    submission_app, monkeypatch, capsys
):
    """FR-E1, TC-EMP-028: failure on the second insert rolls back both."""
    connection, user, category_id, factory = submission_app

    # Force the approval insert to fail after the expense insert succeeds.
    connection.execute(
        """
        CREATE TEMP TRIGGER unused_trigger
        BEFORE INSERT ON approvals
        BEGIN
            SELECT RAISE(ABORT, 'Simulated approval failure');
        END
        """
    )
    # TEMP triggers only affect this connection. Create the trigger in the
    # database instead so the application connection also encounters it.
    connection.execute("DROP TRIGGER unused_trigger")
    connection.execute(
        """
        CREATE TRIGGER fail_approval_insert
        BEFORE INSERT ON approvals
        BEGIN
            SELECT RAISE(ABORT, 'Simulated approval failure');
        END
        """
    )
    connection.commit()

    answers = iter(
        ["1", "25.50", str(category_id), "Taxi fare", TODAY.isoformat(), "0"]
    )
    monkeypatch.setattr("builtins.input", lambda prompt: next(answers))

    menu.run_menu(user, conn_factory=factory)

    output = capsys.readouterr()
    assert "Could not save the expense." in output.out
    assert "Expense submitted." not in output.out
    assert "Traceback" not in output.out
    assert "Traceback" not in output.err
    assert "Exit." in output.out
    assert_empty(connection)


@pytest.mark.negative
def test_special_description_is_stored_as_data(submission_app):
    """FR-E1, TC-EMP-029: SQL-looking text cannot execute as SQL."""
    connection, user, category_id, factory = submission_app
    description = "'; DROP TABLE expenses;-- ."

    expense_id = services.submit_expense(
        connection,
        user,
        "25.50",
        category_id,
        description,
        TODAY.isoformat(),
        today=TODAY,
    )

    assert_saved(
        connection, user, category_id, "25.50", description, TODAY.isoformat()
    )

    # Read the description back exactly, without testing Vennela's view flow.
    stored = connection.execute(
        "SELECT description FROM expenses WHERE id = ?", (expense_id,)
    ).fetchone()
    assert stored["description"] == description