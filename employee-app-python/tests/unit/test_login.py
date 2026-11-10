"""Unit tests for the Employee App login service."""

from unittest.mock import Mock

import pytest

from employee_app.services import login
from employee_app.exceptions import (
    AuthenticationError,
    AuthorizationError,
    ValidationError,
)


@pytest.mark.smoke
def test_valid_employee_login():
    """Covers: FR-A1; TC-EMP-001 — valid employee credentials."""
    # Arrange
    connection = Mock()
    connection.execute.return_value.fetchone.return_value = {
        "id": 1,
        "username": "employee_test",
        "role": "employee",
        "full_name": "Test Employee",
    }

    # Act
    result = login(connection, "employee_test", "demo123")

    # Assert
    assert result == {
        "id": 1,
        "username": "employee_test",
        "role": "employee",
        "full_name": "Test Employee",
    }

    # Credentials must be passed as SQL parameters.
    assert connection.execute.call_args.args[1] == (
        "employee_test",
        "demo123",
    )
    connection.execute.assert_called_once()


@pytest.mark.negative
@pytest.mark.parametrize(
    "username, password",
    [
        pytest.param(
            "unknown_user",
            "wrong_password",
            id="TC-EMP-002-both-invalid",
        ),
        pytest.param(
            "employee_test",
            "wrong_password",
            id="TC-EMP-003-invalid-password",
        ),
        pytest.param(
            "unknown_user",
            "demo123",
            id="TC-EMP-004-invalid-username",
        ),
    ],
)
def test_invalid_credentials_are_rejected(username, password):
    """Covers: FR-A1; TC-EMP-002, 003, 004 — reject invalid credentials."""
    # Arrange
    connection = Mock()
    connection.execute.return_value.fetchone.return_value = None

    # Act and Assert
    with pytest.raises(AuthenticationError) as error:
        login(connection, username, password)

    assert str(error.value) == "Invalid username or password."
    connection.execute.assert_called_once()


@pytest.mark.negative
@pytest.mark.parametrize(
    "username, password",
    [
        pytest.param("", "", id="TC-EMP-006-both-empty"),
        pytest.param("", "demo123", id="TC-EMP-007-empty-username"),
        pytest.param("employee_test", "", id="TC-EMP-008-empty-password"),
    ],
)
def test_empty_credentials_are_rejected_before_database_lookup(
    username, password
):
    """Covers: FR-A1; TC-EMP-006, 007, 008 — reject empty credentials."""
    # Arrange
    connection = Mock()

    # Act and Assert
    with pytest.raises(ValidationError) as error:
        login(connection, username, password)

    assert str(error.value) == "Username and password are required."
    connection.execute.assert_not_called()


@pytest.mark.negative
def test_manager_cannot_log_in_to_employee_app():
    """Covers: FR-A2; TC-EMP-009 — reject a manager account."""
    # Arrange
    connection = Mock()
    connection.execute.return_value.fetchone.return_value = {
        "id": 2,
        "username": "manager_test",
        "role": "manager",
        "full_name": "Test Manager",
    }

    # Act and Assert
    with pytest.raises(AuthorizationError) as error:
        login(connection, "manager_test", "demo123")

    assert str(error.value) == (
        "This account does not have access to this app."
    )
    connection.execute.assert_called_once()