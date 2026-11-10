"""Unit tests for deleting an expense: TC-EMP-042 to TC-EMP-047 (FR-E4, FR-S2, FR-A4).

The database is MOCKED, so no SQLite file is used.  

Two layers are tested:
services.delete_expense / get_own_pending_expense - the rules and the SQL sent;
menu.handle_delete - the confirmation prompt (only 'y' deletes) and re-asking.

FakeDb stands in for the connection: it answers the SELECT queries with the rows
we choose and records every DELETE, so a test can check what was (or was not) removed.
"""

import logging
from unittest.mock import MagicMock, patch

import pytest

from employee_app import menu, services
from employee_app.exceptions import (
    AuthorizationError,
    InvalidStateError,
    NotFoundError,
)

from employee_app.validators import EXPENSE_ID_RULE

EMPLOYEE = {"id": 3, "username": "employee_maya", "full_name": "Maya Singh", "role": "employee"}
MANAGER = {"id": 1, "username": "manager_asha", "full_name": "Asha Patel", "role": "manager"}

def expense(user_id=3, status="pending"):
    # The row the database returns for expense 101.
    return {"id": 101, "user_id": user_id, "category_id": 1, "category": "Travel", "amount": 25.5, 
            "description": "Taxi to airport", "expense_date": "2026-11-01", "status": status}

class FakeDb:
    # A fake connection that records the DELETEs the service tries to run.
    def __init__(self, row=None, account_role="employee"):
        self.row = row
        self.account_role = account_role
        self.deletes = []  # (normalised sql, parameters)
        self.connection = MagicMock()
        self.connection.execute.side_effect = self._execute

    def _execute(self, sql, parameters=()):
        text = " ".join(sql.split()).lower()
        cursor = MagicMock()
        if text.startswith("select role from users"):
            cursor.fetchone.return_value = {"role": self.account_role}
        elif "from expenses e" in text:
            cursor.fetchone.return_value = self.row
        elif text.startswith("delete "):
            self.deletes.append((text, parameters))
            cursor.rowcount = 1
        else:
            raise AssertionError(f"Unexpected SQL in a delete: {text}")
        return cursor

def delete(db, user=EMPLOYEE):
    services.delete_expense(db.connection, user, 101)

