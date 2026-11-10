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


# TC-EMP-042  Delete own pending expense

def test_delete_own_pending_expense_removes_the_approval_row_then_the_expense():
    # TC-EMP-042 | FR-E4 - both rows are deleted, the approval first (it points at the expense).
    # Arrange
    db = FakeDb(row=expense())
    # Act
    delete(db)
    # Assert
    assert [sql.split(" where")[0] for sql, _ in db.deletes] == [
        "delete from approvals",
        "delete from expenses",
    ]
    assert db.deletes[0][1] == (101,)
    assert db.deletes[1][1] == (101, EMPLOYEE["id"])

def test_delete_only_applies_to_a_pending_approval_and_the_owners_expense():
    # TC-EMP-042 | FR-S2, FR-A4 - the DELETE statements themselves are limited,
    # the approval must be pending and the expense must belong to the user.
    db = FakeDb(row=expense())
    delete(db)
    approval_sql, expense_sql = db.deletes[0][0], db.deletes[1][0]
    assert "status = 'pending'" in approval_sql
    assert "user_id = ?" in expense_sql

def test_delete_is_one_transaction():
    # TC-EMP-042 | FR-E4 - both DELETEs run inside one 'with connection:' block,
    # so both rows go together or neither does.
    db = FakeDb(row=expense())
    delete(db)
    db.connection.__enter__.assert_called_once()
    db.connection.__exit__.assert_called_once()

def test_delete_logs_who_deleted_which_expense(caplog):
    # FR-S3 - the log records the expense and user ids, not the description.
    db = FakeDb(row=expense())
    with caplog.at_level(logging.INFO, logger="employee_app.services"):
        delete(db)
    assert "Expense deleted: expense_id=101 user_id=3" in caplog.text
    assert "Taxi to airport" not in caplog.text
    

# TC-EMP-043 to TC-EMP-046  Deletes that must be refused

@pytest.mark.negative
def test_delete_another_employees_expense_is_refused():
    # TC-EMP-043 | FR-E4, FR-A4 - nothing is deleted for someone else's expense.
    db = FakeDb(row=expense(user_id=4))
    with pytest.raises(AuthorizationError, match="only change your own"):
        delete(db)
    assert db.deletes == []

@pytest.mark.negative
@pytest.mark.parametrize("status", ["approved", "denied"])
def test_delete_decided_expense_is_refused(status):
    # TC-EMP-044, 045 | FR-E4, FR-S2 - only pending expenses can be deleted.
    db = FakeDb(row=expense(status=status))
    with pytest.raises(InvalidStateError, match="no longer pending"):
        delete(db)
    assert db.deletes == []

@pytest.mark.negative
def test_delete_expense_without_an_approval_row_is_refused():
    # TC-EMP-044 | FR-S1 - a broken record (no approval row) is not deleted.
    db = FakeDb(row=expense(status=None))
    with pytest.raises(InvalidStateError):
        delete(db)
    assert db.deletes == []

@pytest.mark.negative
def test_delete_missing_expense_reports_not_found():
    # TC-EMP-046 | FR-E4 - an unknown ID gives 'not found' and deletes nothing.
    db = FakeDb(row=None)
    with pytest.raises(NotFoundError, match="Expense not found."):
        delete(db)
    assert db.deletes == []

@pytest.mark.negative
def test_ownership_is_checked_before_the_status():
    # TC-EMP-043 | FR-A4 - someone else's decided expense gives an authorization error
    # nothing about its status is revealed.
    db = FakeDb(row=expense(user_id=4, status="approved"))
    with pytest.raises(AuthorizationError):
        delete(db)

@pytest.mark.negative
def test_manager_cannot_delete_an_expense():
    # TC-EMP-043 | FR-A4 - a manager is refused before the database is touched.
    db = FakeDb(row=expense())
    with pytest.raises(AuthorizationError, match="Only employees"):
        delete(db, user=MANAGER)
    db.connection.execute.assert_not_called()

@pytest.mark.negative
def test_role_is_taken_from_the_database_not_from_the_login_dictionary():
    # FR-A4 - a user dict that claims 'employee' is refused if the database says the account is manager.
    db = FakeDb(row=expense(), account_role="manager")
    with pytest.raises(AuthorizationError):
        delete(db)
    assert db.deletes == []
    

# Menu: TC-EMP-042 (confirm) and TC-EMP-047 (anything but y cancels)

CURRENT = expense()

def make_factory():
    # A connection factory that works with 'with factory() as connection:'.
    factory = MagicMock()
    factory.return_value.__enter__.return_value = MagicMock()
    factory.return_value.__exit__.return_value = False
    return factory

def run_delete(monkeypatch, answers, find=None):
    # Run menu.handle_delete with typed 'answers'; return (prompts, delete mock).
    remaining = iter(answers)
    prompts = []

    def fake_input(prompt=""):
        prompts.append(prompt)
        return next(remaining)

    monkeypatch.setattr("builtins.input", fake_input)
    delete_mock = MagicMock()
    with patch.object(menu.services, "get_own_pending_expense",
                      side_effect=find, return_value=CURRENT), \
         patch.object(menu.services, "delete_expense", delete_mock):
        menu.handle_delete(make_factory(), EMPLOYEE)
    return prompts, delete_mock


@pytest.mark.parametrize("answer", ["y", "Y", " y "], ids=["y", "capital-Y", "y-with-spaces"])
def test_delete_happens_when_the_answer_is_y(monkeypatch, capsys, answer):
    # TC-EMP-042 | FR-E4 - 'y' or 'Y' (spaces are trimmed) deletes the expense.
    _prompts, delete_mock = run_delete(monkeypatch, ["101", answer])

    delete_mock.assert_called_once()
    assert delete_mock.call_args.args[1:] == (EMPLOYEE, 101)
    assert "Expense 101 deleted." in capsys.readouterr().out

def test_delete_shows_the_expense_before_asking_for_confirmation(monkeypatch, capsys):
    # TC-EMP-042 | FR-E4 - the employee sees which expense will be deleted.
    prompts, _delete_mock = run_delete(monkeypatch, ["101", "n"])
    assert "Expense 101: Travel, 25.50, Taxi to airport" in capsys.readouterr().out
    assert prompts == ["Expense ID to delete: ", "Delete this expense? (y/n): "]

@pytest.mark.negative
@pytest.mark.parametrize("answer",
    ["n", "N", "", " ", "yes", "YES", "no", "x", "0", "1", "yy"],
    ids=["n", "capital-N", "enter", "spaces", "yes", "capital-YES", "no",
         "letter", "zero", "one", "double-y"],
)
def test_delete_is_cancelled_unless_the_answer_is_y(monkeypatch, capsys, answer):
    # TC-EMP-047 | FR-E4 - only 'y' or 'Y' confirms; every other answer cancels.
    _prompts, delete_mock = run_delete(monkeypatch, ["101", answer])
    delete_mock.assert_not_called()
    output = capsys.readouterr().out
    assert "Deletion cancelled." in output
    assert "deleted." not in output

@pytest.mark.negative
def test_delete_asks_again_for_an_invalid_expense_id(monkeypatch, capsys):
    # TC-EMP-046 | VR-08 - 0, -1, text and empty are refused before any lookup.
    _prompts, delete_mock = run_delete(monkeypatch, ["0", "-1", "abc", "", "101", "y"])
    assert capsys.readouterr().out.count(EXPENSE_ID_RULE) == 4
    assert delete_mock.call_args.args[2] == 101


@pytest.mark.negative
@pytest.mark.parametrize("error",
    [NotFoundError("Expense not found."),
     AuthorizationError("You can only change your own expenses."),
     InvalidStateError("Expense is no longer pending.")],
    ids=["not-found", "not-yours", "not-pending"],
)
def test_delete_stops_at_once_when_the_expense_cannot_be_deleted(monkeypatch, error):
    # TC-EMP-043 to 046 - no confirmation is asked and nothing is deleted; the
    # error goes up to the menu loop, which prints it.
    with pytest.raises(type(error), match=str(error)):
        # only the ID is typed: asking for a confirmation would fail the test
        run_delete(monkeypatch, ["101"], find=error)