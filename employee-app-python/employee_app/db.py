"""Database connection and transaction helpers."""

import logging
import os
import sqlite3
from contextlib import contextmanager
from pathlib import Path

from employee_app.exceptions import DatabaseError


logger = logging.getLogger(__name__)


def db_path():
    """Read the shared database location from EM_DB_PATH."""
    path = os.environ.get("EM_DB_PATH")

    if not path or not path.strip():
        raise DatabaseError("EM_DB_PATH is not set.")

    return path


@contextmanager
def get_connection(path=None):
    """Open an existing database and manage its transaction."""
    selected_path = path if path is not None else db_path()

    try:
        database_uri = Path(selected_path).resolve().as_uri()
    except (OSError, ValueError) as error:
        raise DatabaseError("Invalid database path.") from error

    connection = None

    try:
        # mode=rw opens an existing database without creating a new one.
        connection = sqlite3.connect(
            database_uri + "?mode=rw",
            uri=True,
            timeout=5,
        )
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = ON")

        with connection:
            yield connection

    except sqlite3.Error as error:
        logger.exception("Database operation failed.")
        raise DatabaseError(
            "Database operation failed. Check the database path and log."
        ) from error

    finally:
        if connection is not None:
            connection.close()