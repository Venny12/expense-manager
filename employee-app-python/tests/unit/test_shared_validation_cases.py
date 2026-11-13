"""Shared validation cases (Section 9.5), Python side.

The same shared-test-data/validation_cases.csv is run by the Java suite
(SharedValidationCasesTest). Both apps must give the same answers.

Fields:
  expense_id   VR-08  expected true = accepted
  menu_choice  VR-09  menu with choices 0-3 (valid in both apps)
  login        FR-A1  input is "username|password"; expected true = the
                      empty-input check passes (the request then goes on to
                      the database), false = rejected as empty input
"""

import csv
from pathlib import Path
from unittest.mock import Mock

import pytest

from employee_app import validators
from employee_app.exceptions import AuthenticationError, ValidationError
from employee_app.services import login

CSV_FILE = (Path(__file__).resolve().parents[3]/ "shared-test-data" / "validation_cases.csv")
MENU_CHOICES = {0, 1, 2, 3}

def load_cases():
    with CSV_FILE.open(newline="", encoding="utf-8") as file:
        return [
            pytest.param(r["field"], r["input"], r["expected"] == "true",
                         id=f'{r["field"]}[{r["input"]}]')
            for r in csv.DictReader(file)
        ]

def passes_empty_login_check(text):
    username, password = text.split("|", 1)
    connection = Mock()
    connection.execute.return_value.fetchone.return_value = None
    try:
        login(connection, username, password)
    except ValidationError:
        return False
    except AuthenticationError:
        # Got past the empty-input check; the database found no such user.
        return True
    return True

def accepted_by_python_app(field, text):
    if field == "login":
        return passes_empty_login_check(text)
    try:
        if field == "expense_id":
            validators.validate_expense_id(text)
        else:
            validators.validate_menu_choice(text, MENU_CHOICES)
    except ValidationError:
        return False
    return True

@pytest.mark.smoke
def test_shared_file_is_not_empty():
    assert len(load_cases()) >= 20

@pytest.mark.parametrize("field, text, expected", load_cases())
def test_python_app_agrees_with_shared_case(field, text, expected):
    assert accepted_by_python_app(field, text) is expected