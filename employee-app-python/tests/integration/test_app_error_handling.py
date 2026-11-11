"""FR-A4, FR-S4: menu choices and application errors, TC-EMP-051 to 057.

The real main(), login, menu and services run against a real temporary
SQLite database. Only the keyboard (input and getpass) and the log folder are replaced. 
Every test also checks that no traceback reaches the screen.
"""

from unittest.mock import MagicMock, Mock

import pytest

from employee_app import main as app_main
from employee_app import menu

pytestmark = pytest.mark.integration

MENU_RULE = "Please enter a number from the menu."
UNEXPECTED = "Unexpected error. See log."

@pytest.fixture
def app(db_connection, tmp_path, monkeypatch):
    # One employee with one expense; main() points at this database.
    connection = db_connection
    connection.execute("INSERT INTO categories (name) VALUES ('Travel')")
    connection.execute(
        "INSERT INTO users (username, password, role, full_name) "
        "VALUES ('err_employee', 'ErrPassword1!', 'employee', 'Err Employee')"
    )
    connection.execute(
        "INSERT INTO expenses (user_id, category_id, amount, description, "
        "expense_date, created_at) "
        "VALUES (1, 1, 25.50, 'Taxi', '2026-11-08', '2026-11-09 09:00:00')"
    )
    connection.execute("INSERT INTO approvals (expense_id) VALUES (1)")
    connection.commit()

    database_path = connection.execute("PRAGMA database_list").fetchone()[2]
    monkeypatch.setenv("EM_DB_PATH", database_path)

    # Keep the application log inside the temporary folder.
    fake_source = Mock()
    fake_source.resolve.return_value.parents = [tmp_path] * 3
    monkeypatch.setattr(app_main, "Path", Mock(return_value=fake_source))
    monkeypatch.setattr(app_main.logging, "basicConfig", Mock())

    return connection


def keyboard(monkeypatch, answers, password="ErrPassword1!"):
    # Scripted keyboard. An item that is an exception class is raised.
    fake_input = Mock(side_effect=answers)
    monkeypatch.setattr("builtins.input", fake_input)
    monkeypatch.setattr(app_main, "getpass", Mock(return_value=password))
    return fake_input


def no_traceback(captured):
    assert "Traceback" not in captured.out + captured.err


def expense_rows(connection):
    return connection.execute("SELECT id, amount, description FROM expenses ORDER BY id").fetchall()


# TC-EMP-051
@pytest.mark.negative
def test_missing_database_file_gives_friendly_message(app, tmp_path, monkeypatch, capsys):
    # TC-EMP-051 | FR-S4 - friendly message, no traceback, exit code 1.
    missing = tmp_path / "does_not_exist.db"
    monkeypatch.setenv("EM_DB_PATH", str(missing))
    fake_input = keyboard(monkeypatch, [])

    assert app_main.main() == 1

    captured = capsys.readouterr()
    assert "Database operation failed" in captured.out
    no_traceback(captured)
    assert not missing.exists()
    fake_input.assert_not_called()


# TC-EMP-052
@pytest.mark.negative
def test_invalid_main_menu_choices_show_error_and_menu_again(app, monkeypatch, capsys):
    # TC-EMP-052 | VR-09 - five invalid entries, then 0 ends the session.
    before = expense_rows(app)
    fake_input = keyboard(
        monkeypatch, ["err_employee", "abc", "9", "-1", "1.5", "", "0"]
    )

    assert app_main.main() == 0

    captured = capsys.readouterr()
    assert captured.out.count(MENU_RULE) == 5
    assert "Exit." in captured.out
    no_traceback(captured)
    # No feature prompt was reached: login + 5 invalid + exit = 7 prompts.
    assert fake_input.call_count == 7
    assert expense_rows(app) == before


# TC-EMP-053
def test_error_in_one_menu_option_does_not_end_the_session(app, monkeypatch, capsys):
    # TC-EMP-053 | FR-S4 - edit of a missing ID, then View still works.
    # Answers: 3 = Edit, 999999 = missing ID, 2 = View, 0 = filter All, 0 = Exit.
    keyboard(monkeypatch, ["err_employee", "3", "999999", "2", "0", "0"])

    assert app_main.main() == 0

    captured = capsys.readouterr()
    assert "Expense not found." in captured.out
    assert "25.50" in captured.out
    assert captured.out.index("Expense not found.") < captured.out.index("25.50")
    assert "Exit." in captured.out
    no_traceback(captured)


# TC-EMP-054
@pytest.mark.negative
def test_unexpected_error_in_a_menu_option_is_logged_and_menu_returns(app, monkeypatch, capsys, caplog):
    # TC-EMP-054 | FR-S4 - RuntimeError in option 5, then Exit still works.
    def broken(conn_factory, user):
        raise RuntimeError("simulated failure")

    monkeypatch.setattr(menu, "handle_history", broken)
    keyboard(monkeypatch, ["err_employee", "5", "0"])

    assert app_main.main() == 0

    captured = capsys.readouterr()
    assert UNEXPECTED in captured.out
    assert "Exit." in captured.out
    no_traceback(captured)
    assert any(
        r.levelname == "ERROR" and "Unexpected error in menu option 5" in r.message
        and r.exc_info and r.exc_info[0] is RuntimeError
        for r in caplog.records
    )


# TC-EMP-055
@pytest.mark.negative
def test_unexpected_error_while_running_is_logged_with_exit_code_1(app, monkeypatch, capsys, caplog):
    # TC-EMP-055 | FR-S4 - the login step raises an unexpected exception.
    keyboard(monkeypatch, ["err_employee"])
    monkeypatch.setattr(
        app_main, "login", Mock(side_effect=RuntimeError("simulated failure"))
    )

    assert app_main.main() == 1

    captured = capsys.readouterr()
    assert UNEXPECTED in captured.out
    no_traceback(captured)
    assert any(
        "Unexpected application error" in r.message
        and r.exc_info and r.exc_info[0] is RuntimeError
        for r in caplog.records
    )


# TC-EMP-056
@pytest.mark.negative
@pytest.mark.parametrize("interrupt", [EOFError, KeyboardInterrupt])
@pytest.mark.parametrize("where", ["username prompt", "menu prompt"])
def test_end_of_input_or_ctrl_c_ends_the_app_cleanly(app, monkeypatch, capsys, interrupt, where):
    # TC-EMP-056 | FR-S4 - "Application closed.", no traceback, exit code 0.
    answers = [interrupt] if where == "username prompt" else [
        "err_employee", interrupt,
    ]
    keyboard(monkeypatch, answers)

    assert app_main.main() == 0

    captured = capsys.readouterr()
    assert "Application closed." in captured.out
    no_traceback(captured)


# TC-EMP-057
@pytest.mark.negative
def test_log_folder_that_cannot_be_created_stops_before_login(app, monkeypatch, capsys):
    # TC-EMP-057 | FR-S4 - message, login prompt never shown, exit code 1.
    root = MagicMock()
    root.__truediv__.return_value.mkdir.side_effect = OSError("read-only")
    fake_source = Mock()
    fake_source.resolve.return_value.parents = [root] * 3
    monkeypatch.setattr(app_main, "Path", Mock(return_value=fake_source))
    fake_input = keyboard(monkeypatch, [])

    assert app_main.main() == 1

    captured = capsys.readouterr()
    assert "Cannot open the application log." in captured.out
    no_traceback(captured)
    fake_input.assert_not_called()