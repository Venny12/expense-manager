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
