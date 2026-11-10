"""Unit tests for editing an expense: TC-EMP-035 to TC-EMP-041 (FR-E3, FR-S2, FR-A4).

The database is MOCKED, so no SQLite file is used.  Two layers are tested:

services.update_expense / get_own_pending_expense - the rules and the SQL sent;
menu.handle_edit - the prompts, "Enter keeps the value" and re-asking.

FakeDb stands in for the connection: it answers the SELECT queries with the rows
we choose and records every UPDATE, so a test can check what was (or was not) written.
"""

import logging
from datetime import date
from decimal import Decimal
from unittest.mock import MagicMock, patch

import pytest

from employee_app import menu, services

from employee_app.exceptions import (
    AuthorizationError,
    InvalidStateError,
    NotFoundError,
    ValidationError,
)
from employee_app.validators import EXPENSE_ID_RULE

TODAY = date(2026, 11, 10)
EMPLOYEE = {"id": 3, "username": "employee_maya", "full_name": "Maya Singh", "role": "employee"}
MANAGER = {"id": 1, "username": "manager_asha", "full_name": "Asha Patel", "role": "manager"}
CATEGORIES = [{"id": 1, "name": "Travel"}, {"id": 2, "name": "Meals"}]

def expense(user_id=3, status="pending"):
    # The row the database returns for expense 101.
    return {"id": 101, "user_id": user_id, "category_id": 1, "category": "Travel",
            "amount": 25.5, "description": "Taxi to airport",
            "expense_date": "2026-11-01", "status": status}

class FakeDb:
    # A fake connection that records the UPDATEs the service tries to run.

    def __init__(self, row=None, account_role="employee"):
        self.row = row
        self.account_role = account_role
        self.updates = []  # (normalised sql, parameters)
        self.connection = MagicMock()
        self.connection.execute.side_effect = self._execute

    def _execute(self, sql, parameters=()):
        text = " ".join(sql.split()).lower()
        cursor = MagicMock()
        if text.startswith("select role from users"):
            cursor.fetchone.return_value = {"role": self.account_role}
        elif text.startswith("select id, name from categories"):
            cursor.fetchall.return_value = CATEGORIES
        elif "from expenses e" in text:
            cursor.fetchone.return_value = self.row
        elif text.startswith("update expenses"):
            self.updates.append((text, parameters))
            cursor.rowcount = 1
        else:
            raise AssertionError(f"Unexpected SQL in an edit: {text}")
        return cursor


def edit(db, user=EMPLOYEE, amount="30.00", category=2, description="New taxi", expense_date="2026-11-02"):
    services.update_expense(db.connection, user, 101, amount, category, description, expense_date, today=TODAY)


# TC-EMP-035  Edit own pending expense

def test_edit_own_pending_expense_sends_the_new_values():
    # TC-EMP-035 | FR-E3 - one UPDATE with the new values for this user's expense.
    db = FakeDb(row=expense())
    edit(db)
    (sql, parameters), = db.updates
    assert parameters == (30.0, 2, "New taxi", "2026-11-02", 101, EMPLOYEE["id"])
    assert "where id = ? and user_id = ?" in sql


def test_edit_update_only_applies_while_the_approval_is_pending():
    # TC-EMP-035 | FR-S2 - the UPDATE itself only applies to a pending expense.
    db = FakeDb(row=expense())
    edit(db)
    sql, _parameters = db.updates[0]
    assert "status = 'pending'" in sql


def test_edit_never_changes_the_approval_status():
    # TC-EMP-035 | FR-E3 - the status stays pending: no approvals row is written.
    db = FakeDb(row=expense())
    edit(db)
    assert all(text.startswith("update expenses") for text, _ in db.updates)
    assert len(db.updates) == 1


def test_edit_is_saved_in_a_transaction():
    # TC-EMP-035 | FR-E3 - the UPDATE runs inside 'with connection:' (commit).
    db = FakeDb(row=expense())
    edit(db)
    db.connection.__enter__.assert_called_once()
    db.connection.__exit__.assert_called_once()


def test_edit_logs_success_without_the_description(caplog):
    # FR-S3 - the log says who edited what, but not the text description.
    db = FakeDb(row=expense())
    with caplog.at_level(logging.INFO, logger="employee_app.services"):
        edit(db, description="Secret client dinner")
    assert "Expense edited: expense_id=101 user_id=3" in caplog.text
    assert "Secret client dinner" not in caplog.text


# TC-EMP-036 to TC-EMP-039  Edits that must be refused

@pytest.mark.negative
def test_edit_another_employees_expense_is_refused():
    # TC-EMP-036 | FR-E3, FR-A4 - nothing is written for someone else's expense.
    # Arrange
    db = FakeDb(row=expense(user_id=4))
    # Act
    with pytest.raises(AuthorizationError, match="only change your own"):
        edit(db)
    # Assert
    assert db.updates == []

@pytest.mark.negative
@pytest.mark.parametrize("status", ["approved", "denied"])
def test_edit_decided_expense_is_refused(status):
    # TC-EMP-037, 038 | FR-E3, FR-S2 - approved and denied expenses are final.
    db = FakeDb(row=expense(status=status))
    with pytest.raises(InvalidStateError, match="no longer pending"):
        edit(db)
    assert db.updates == []

@pytest.mark.negative
def test_edit_expense_without_an_approval_row_is_refused():
    # TC-EMP-037 | FR-S1 - a broken record (no approval row) is not editable.
    db = FakeDb(row=expense(status=None))
    with pytest.raises(InvalidStateError):
        edit(db)
    assert db.updates == []

@pytest.mark.negative
def test_edit_missing_expense_reports_not_found():
    # TC-EMP-039 | FR-E3 - an unknown ID gives 'not found' and writes nothing.
    db = FakeDb(row=None)
    with pytest.raises(NotFoundError, match="Expense not found."):
        edit(db)
    assert db.updates == []

@pytest.mark.negative
def test_ownership_is_checked_before_the_new_values():
    # TC-EMP-036 | FR-A4 - someone else's expense gives an authorization error
    # even when the new values are also invalid (amount is negative).
    db = FakeDb(row=expense(user_id=4))
    with pytest.raises(AuthorizationError):
        edit(db, amount="-5")

@pytest.mark.negative
def test_manager_cannot_edit_an_expense():
    # TC-EMP-036 | FR-A4 - a manager is refused before the database is touched.
    db = FakeDb(row=expense())
    with pytest.raises(AuthorizationError, match="Only employees"):
        edit(db, user=MANAGER)
    # checks whether the mocked execute() method was called zero times.
    db.connection.execute.assert_not_called()

@pytest.mark.negative
def test_role_is_taken_from_the_database_not_from_the_login_dictionary():
    # FR-A4 - a user dict that claims 'employee' is refused if the database says
    # the account is a manager.
    db = FakeDb(row=expense(), account_role="manager")
    with pytest.raises(AuthorizationError):
        edit(db)
    assert db.updates == []
