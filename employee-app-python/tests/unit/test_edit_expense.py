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
