import sqlite3

import pytest

from employee_app.db import get_connection
from employee_app.exceptions import DatabaseError


pytestmark = pytest.mark.integration


def test_connection_settings(db_connection):
    """- opens the database with required settings."""
    path = db_connection.execute(
        "PRAGMA database_list"
    ).fetchone()[2]

    with get_connection(path) as connection:
        connection.execute("SELECT 1").fetchone()

        assert connection.row_factory is sqlite3.Row
        assert connection.execute(
            "PRAGMA foreign_keys"
        ).fetchone()[0] == 1
        assert connection.execute(
            "PRAGMA busy_timeout"
        ).fetchone()[0] == 5000


def test_successful_changes_are_committed(db_connection):
    """- successful changes persist after closing."""
    path = db_connection.execute(
        "PRAGMA database_list"
    ).fetchone()[2]

    with get_connection(path) as connection:
        connection.execute(
            "INSERT INTO categories (name) VALUES (?)",
            ("Travel",),
        )

    with get_connection(path) as connection:
        result = connection.execute(
            "SELECT name FROM categories"
        ).fetchone()

        assert result["name"] == "Travel"


def test_failed_changes_are_rolled_back(db_connection):
    """ - failed transactions leave no partial changes."""
    path = db_connection.execute(
        "PRAGMA database_list"
    ).fetchone()[2]

    with pytest.raises(RuntimeError, match="Simulated failure"):
        with get_connection(path) as connection:
            connection.execute(
                "INSERT INTO categories (name) VALUES (?)",
                ("Travel",),
            )
            raise RuntimeError("Simulated failure")

    with get_connection(path) as connection:
        count = connection.execute(
            "SELECT COUNT(*) FROM categories"
        ).fetchone()[0]

        assert count == 0


def test_connection_is_closed(db_connection):
    """ - connection is closed after use."""
    path = db_connection.execute(
        "PRAGMA database_list"
    ).fetchone()[2]

    with get_connection(path) as connection:
        connection.execute("SELECT 1")

    with pytest.raises(sqlite3.ProgrammingError):
        connection.execute("SELECT 1")


@pytest.mark.negative
def test_missing_database_is_not_created(tmp_path):
    """- missing database raises a clear application error."""
    path = tmp_path / "missing.db"

    with pytest.raises(DatabaseError, match="Database operation failed"):
        with get_connection(path):
            pytest.fail("A missing database should not open.")

    assert not path.exists()