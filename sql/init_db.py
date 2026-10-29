import os
import sqlite3
from pathlib import Path

project_root = Path(__file__).resolve().parent.parent

database_path = os.environ.get("EM_DB_PATH")

if not database_path:
    raise SystemExit("EM_DB_PATH is not set.")

schema = (project_root / "sql/schema.sql").read_text(
    encoding="utf-8-sig"
)
seed = (project_root / "sql/seed.sql").read_text(
    encoding="utf-8-sig"
)

with sqlite3.connect(database_path, timeout=5) as connection:
    connection.execute("PRAGMA foreign_keys = ON")

    tables = {
        row[0]
        for row in connection.execute(
            "SELECT name FROM sqlite_master WHERE type = 'table'"
        )
    }

    required_tables = {"users", "categories", "expenses", "approvals"}

    if not tables:
        connection.executescript(schema)
    elif tables != required_tables:
        raise SystemExit("Unexpected database tables. Setup stopped.")

    # Add sample data only when all four tables are empty.
    counts = [
        connection.execute("SELECT COUNT(*) FROM users").fetchone()[0],
        connection.execute("SELECT COUNT(*) FROM categories").fetchone()[0],
        connection.execute("SELECT COUNT(*) FROM expenses").fetchone()[0],
        connection.execute("SELECT COUNT(*) FROM approvals").fetchone()[0],
    ]

    if any(counts):
        print("Database already contains data. Sample data was not added.")
    else:
        connection.executescript(seed)
        print("Sample data added successfully.")

    print("Users:", connection.execute(
        "SELECT COUNT(*) FROM users"
    ).fetchone()[0])

    print("Categories:", connection.execute(
        "SELECT COUNT(*) FROM categories"
    ).fetchone()[0])

    print("Expenses:", connection.execute(
        "SELECT COUNT(*) FROM expenses"
    ).fetchone()[0])

    print("Approvals:", connection.execute(
        "SELECT COUNT(*) FROM approvals"
    ).fetchone()[0])