"""Tests for Employee App login prompts and application behavior."""

from unittest.mock import MagicMock, Mock

import pytest

from employee_app import main as app_main


EMPLOYEE = {
    "id": 1,
    "username": "employee_test",
    "role": "employee",
    "full_name": "Test Employee",
}

MANAGER = {
    "id": 2,
    "username": "manager_test",
    "role": "manager",
    "full_name": "Test Manager",
}


@pytest.fixture
def login_app(monkeypatch):
    """Replace external dependencies without opening a real database."""
    connection = Mock()

    database_context = MagicMock()
    database_context.__enter__.return_value = connection
    database_context.__exit__.return_value = False

    monkeypatch.setattr(
        app_main,
        "get_connection",
        Mock(return_value=database_context),
    )

    # Avoid creating folders or changing the application's real log.
    monkeypatch.setattr(app_main, "Path", MagicMock())
    monkeypatch.setattr(app_main.logging, "basicConfig", Mock())

    menu = Mock()
    monkeypatch.setattr(app_main, "run_menu", menu)

    return connection, menu


def provide_credentials(monkeypatch, usernames, passwords):
    """Supply scripted answers to the username and password prompts."""
    username_input = Mock(side_effect=usernames)
    password_input = Mock(side_effect=passwords)

    monkeypatch.setattr("builtins.input", username_input)
    monkeypatch.setattr(app_main, "getpass", password_input)

    return username_input, password_input


@pytest.mark.smoke
def test_successful_login_shows_welcome_and_opens_menu(
    login_app, monkeypatch, capsys
):
    """Covers: FR-A1; TC-EMP-001 — greeting and menu access."""
    connection, menu = login_app
    connection.execute.return_value.fetchone.return_value = EMPLOYEE

    provide_credentials(
        monkeypatch,
        ["employee_test"],
        ["demo123"],
    )

    result = app_main.main()
    output = capsys.readouterr().out

    assert result == 0
    assert "Welcome, Test Employee" in output
    menu.assert_called_once_with(EMPLOYEE)


@pytest.mark.negative
def test_three_failed_logins_return_exit_code_one(
    login_app, monkeypatch, capsys
):
    """Covers: FR-A3; TC-EMP-005 — stop after three failures."""
    connection, menu = login_app
    connection.execute.return_value.fetchone.return_value = None

    username_input, password_input = provide_credentials(
        monkeypatch,
        ["employee_test"] * 3,
        ["wrong_password"] * 3,
    )

    result = app_main.main()
    output = capsys.readouterr().out

    assert result == 1
    assert output.count("Invalid username or password.") == 3
    assert output.count("Too many failed attempts.") == 1
    assert "Welcome," not in output
    assert username_input.call_count == 3
    assert password_input.call_count == 3
    menu.assert_not_called()


@pytest.mark.boundary
def test_two_failed_logins_then_success(
    login_app, monkeypatch, capsys
):
    """Covers: FR-A1, FR-A3; TC-EMP-010 — third attempt succeeds."""
    connection, menu = login_app
    connection.execute.return_value.fetchone.side_effect = [
        None,
        None,
        EMPLOYEE,
    ]

    provide_credentials(
        monkeypatch,
        ["employee_test"] * 3,
        ["wrong_password", "wrong_password", "demo123"],
    )

    result = app_main.main()
    output = capsys.readouterr().out

    assert result == 0
    assert output.count("Invalid username or password.") == 2
    assert "Welcome, Test Employee" in output
    assert "Too many failed attempts." not in output
    menu.assert_called_once_with(EMPLOYEE)
