import sqlite3
from pathlib import Path

import pytest


@pytest.fixture
def db_connection(tmp_path):
    """Create a fresh temporary database for each test."""
    project_root = Path(__file__).resolve().parents[2]
    schema_path = project_root / "sql" / "schema.sql"

    database_path = tmp_path / "test_expenses.db"
    connection = sqlite3.connect(database_path)
    connection.execute("PRAGMA foreign_keys = ON")

    try:
        schema = schema_path.read_text(encoding="utf-8-sig")
        connection.executescript(schema)
        yield connection
    finally:
        connection.close()