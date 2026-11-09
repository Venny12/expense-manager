"""Check that login events are logged without exposing passwords."""

import logging
import re
from unittest.mock import Mock

import pytest

from employee_app import main as app_main


@pytest.mark.integration
@pytest.mark.negative
def test_passwords_are_not_logged_or_printed(
    db_connection, tmp_path, monkeypatch, capsys
):
    """Covers: FR-S3; TC-EMP-011 — passwords stay out of logs/output."""
    # Arrange: create a real employee account in the temporary database.
    correct_password = "CorrectSecret123!"
    wrong_password = "S3cretPass!"

    db_connection.execute(
        """
        INSERT INTO users (username, password, role, full_name)
        VALUES (?, ?, ?, ?)
        """,
        (
            "logging_employee",
            correct_password,
            "employee",
            "Logging Employee",
        ),
    )
    db_connection.commit()

    database_path = db_connection.execute(
        "PRAGMA database_list"
    ).fetchone()[2]

    monkeypatch.setenv("EM_DB_PATH", database_path)

    # Redirect main.py's log folder to the temporary project directory.
    # The actual database helper and login function are still used.
    fake_source_path = Mock()
    fake_source_path.resolve.return_value.parents = [
        tmp_path,
        tmp_path,
        tmp_path,
    ]
    monkeypatch.setattr(
        app_main,
        "Path",
        Mock(return_value=fake_source_path),
    )

    # First login fails; the second succeeds.
    monkeypatch.setattr(
        "builtins.input",
        Mock(side_effect=["logging_employee", "logging_employee"]),
    )
    monkeypatch.setattr(
        app_main,
        "getpass",
        Mock(side_effect=[wrong_password, correct_password]),
    )

    # Stop after login rather than testing menu features here.
    menu = Mock()
    monkeypatch.setattr(app_main, "run_menu", menu)

    # Temporarily detach pytest's handlers so the app's basicConfig
    # creates its real file handler. Restore everything afterward.
    root_logger = logging.getLogger()
    original_handlers = root_logger.handlers[:]
    original_level = root_logger.level

    for handler in original_handlers:
        root_logger.removeHandler(handler)

    try:
        # Act: run the real application startup and login flow.
        exit_code = app_main.main()
    finally:
        for handler in root_logger.handlers[:]:
            handler.flush()
            root_logger.removeHandler(handler)
            handler.close()

        for handler in original_handlers:
            root_logger.addHandler(handler)

        root_logger.setLevel(original_level)

    # Assert: the login flow completed successfully after one failure.
    captured = capsys.readouterr()

    assert exit_code == 0
    assert "Invalid username or password." in captured.out
    assert "Welcome, Logging Employee" in captured.out
    menu.assert_called_once()

    # Check the actual log produced by the app.
    log_path = tmp_path / "logs" / "employee_app.log"
    assert log_path.is_file()

    log_text = log_path.read_text(encoding="utf-8")

    # Both login events must be recorded.
    assert "Invalid login attempt." in log_text
    assert "Login successful: user_id=" in log_text

    # Check the timestamp and severity on each login entry.
    login_lines = [
        line
        for line in log_text.splitlines()
        if "Invalid login attempt." in line
        or "Login successful: user_id=" in line
    ]

    assert len(login_lines) == 2

    for line in login_lines:
        assert re.match(
            r"^\d{4}-\d{2}-\d{2} "
            r"\d{2}:\d{2}:\d{2},\d{3} "
            r"(WARNING|INFO) ",
            line,
        )

    # Neither password may appear in the log or application output.
    for password in (correct_password, wrong_password):
        assert password not in log_text
        assert password not in captured.out
        assert password not in captured.err