"""FR-A1, FR-E2: Scripted menu session, case TC-EMP-050.
The real main(), login, menu and services run against a real temporary SQLite database. 
Only the keyboard (input and getpass), the log folder and the logging set-up are replaced.
"""

from types import SimpleNamespace
from unittest.mock import Mock

import pytest

from employee_app import main as app_main

pytestmark = pytest.mark.integration

@pytest.fixture
def session_app(db_connection, tmp_path, monkeypatch):
    # A database with two employees; main() is pointed at it.
    connection = db_connection
    connection.execute("INSERT INTO categories (name) VALUES ('Travel')")
    connection.execute(
        "INSERT INTO users (username, password, role, full_name) VALUES "
        "('menu_employee', 'MenuPassword1!', 'employee', 'Menu Employee'), "
        "('other_employee', 'OtherPassword1!', 'employee', 'Other Employee')"
    )
    for user_id, description, amount, day in [
        (1, "Taxi to airport", 25.50, "2026-11-08"),
        (1, "Hotel", 80.00, "2026-11-03"),
        (2, "Someone else's lunch", 14.25, "2026-11-07"),
    ]:
        connection.execute(
            "INSERT INTO expenses (user_id, category_id, amount, description, "
            "expense_date, created_at) VALUES (?, 1, ?, ?, ?, '2026-11-09 09:00:00')",
            (user_id, amount, description, day),
        )
    connection.execute("INSERT INTO approvals (expense_id) VALUES (1), (2), (3)")
    connection.commit()

    database_path = connection.execute("PRAGMA database_list").fetchone()[2]
    monkeypatch.setenv("EM_DB_PATH", database_path)

    # Keep the application log inside the temporary folder.
    fake_source = Mock()
    fake_source.resolve.return_value.parents = [tmp_path] * 3
    monkeypatch.setattr(app_main, "Path", Mock(return_value=fake_source))
    monkeypatch.setattr(app_main.logging, "basicConfig", Mock())

    return SimpleNamespace(connection=connection)

def run_session(monkeypatch, username, password, menu_answers):
    # Feed a scripted session to the real main() and return its exit code.
    monkeypatch.setattr("builtins.input", Mock(side_effect=[username, *menu_answers]))
    monkeypatch.setattr(app_main, "getpass", Mock(return_value=password))
    return app_main.main()

# TC-EMP-050  Scripted session: login, view, exit
@pytest.mark.smoke
def test_scripted_session_login_view_expenses_and_exit(
    session_app, monkeypatch, capsys
):
    # TC-EMP-050 | FR-A1, FR-E2 - greeting, expense table, goodbye, exit code 0.
    # Menu answers: 2 = View my expenses, 0 = filter All, 0 = Exit.
    exit_code = run_session(monkeypatch, "menu_employee", "MenuPassword1!", ["2", "0", "0"])

    output = capsys.readouterr().out

    assert exit_code == 0
    assert "Welcome, Menu Employee" in output
    assert "ID" in output and "Status" in output
    assert "25.50" in output and "80.00" in output
    assert "pending" in output
    assert "Exit." in output
    # The three parts appear in the order of the session.
    assert (output.index("Welcome, Menu Employee")
            < output.index("25.50")
            < output.index("Exit."))

def test_scripted_session_shows_only_the_logged_in_employees_expenses(session_app, monkeypatch, capsys):
    # TC-EMP-050 | FR-A4 - the other employee's 14.25 never reaches the table.
    run_session(monkeypatch, "menu_employee", "MenuPassword1!", ["2", "0", "0"])
    output = capsys.readouterr().out
    assert "14.25" not in output

def test_scripted_session_without_viewing_prints_greeting_and_goodbye_only(session_app, monkeypatch, capsys):
    # TC-EMP-050 | FR-A1 - login then Exit: no table, exit code 0.
    exit_code = run_session(monkeypatch, "menu_employee", "MenuPassword1!", ["0"])
    output = capsys.readouterr().out
    assert exit_code == 0
    assert "Welcome, Menu Employee" in output
    assert "Exit." in output
    assert "Status" not in output