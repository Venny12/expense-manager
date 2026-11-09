import sqlite3
from pathlib import Path

import pytest

PROJECT_ROOT = Path(__file__).resolve().parents[3]
VALIDATION_DIR = PROJECT_ROOT / "sql" / "validation"

QUERY_FILES = [
    "01_expenses_without_user.sql",
    "02_expenses_without_approval.sql",
    "03_pending_with_review_data.sql",
    "04_decided_missing_review_data.sql",
    "05_shared_contract_violations.sql",
]

@pytest.fixture
def seeded_db(tmp_path):
    # Temporary database created from schema.sql and seed.sql.
    connection = sqlite3.connect(tmp_path / "validation.db")
    connection.execute("PRAGMA foreign_keys = ON")
    for name in ("schema.sql", "seed.sql"):
        script = (PROJECT_ROOT / "sql" / name).read_text(encoding="utf-8-sig")
        connection.executescript(script)
    try:
        yield connection
    finally:
        connection.close()

def run_query(connection, file_name):
    # Run one validation query file and return its rows.
    sql = (VALIDATION_DIR / file_name).read_text(encoding="utf-8")
    return connection.execute(sql).fetchall()


@pytest.mark.integration
@pytest.mark.smoke
@pytest.mark.parametrize("file_name", QUERY_FILES)
def test_validation_query_returns_no_rows_for_seed_data(seeded_db, file_name):
    # Covers: FR-S1, FR-S5 - sample data passes every validation query.
    assert run_query(seeded_db, file_name) == []


@pytest.mark.integration
@pytest.mark.negative
def test_query_1_finds_expense_without_user(seeded_db):
    # validation query 1 detects an orphan expense.
    seeded_db.commit()
    seeded_db.execute("PRAGMA foreign_keys = OFF")
    seeded_db.execute(
        "INSERT INTO expenses (id, user_id, category_id, amount, description, expense_date, created_at)"
        "VALUES (900, 999, 1, 10.00, 'Orphan', '2026-10-01', '2026-10-01 09:00:00')"
    )
    rows = run_query(seeded_db, "01_expenses_without_user.sql")
    assert rows == [(900, 999)]

@pytest.mark.integration
@pytest.mark.negative
def test_query_2_finds_expense_without_approval(seeded_db):
    # validation query 2 detects a missing approval.
    seeded_db.execute(
        "INSERT INTO expenses (id, user_id, category_id, amount, description, expense_date, created_at)"
        "VALUES (901, 3, 1, 10.00, 'No approval', '2026-10-01', '2026-10-01 09:00:00')"
    )
    rows = run_query(seeded_db, "02_expenses_without_approval.sql")
    assert rows == [(901,)]

@pytest.mark.integration
@pytest.mark.negative
def test_query_3_finds_pending_with_reviewer(seeded_db):
    # validation query 3 detects a pending row with a reviewer.
    seeded_db.execute(
        "UPDATE approvals SET reviewer_id = 1, review_date = '2026-10-01'"
        "WHERE id = (SELECT id FROM approvals WHERE status = 'pending'"
        "ORDER BY id LIMIT 1)"
    )
    rows = run_query(seeded_db, "03_pending_with_review_data.sql")
    assert len(rows) == 1
    assert rows[0][2] == 1

@pytest.mark.integration
@pytest.mark.negative
@pytest.mark.parametrize(
    "change",
    [
        "reviewer_id = NULL",
        "review_date = NULL",
        "reviewer_id = 3",
    ],
    ids=["missing_reviewer", "missing_review_date", "reviewer_is_employee"],
)
def test_query_4_finds_bad_decided_rows(seeded_db, change):
    # validation query 4 detects incomplete or bad decisions.
    seeded_db.execute(
        f"UPDATE approvals SET {change}"
        " WHERE id = (SELECT id FROM approvals WHERE status = 'approved' ORDER BY id LIMIT 1)"
    )
    rows = run_query(seeded_db, "04_decided_missing_review_data.sql")
    assert len(rows) == 1

@pytest.mark.integration
@pytest.mark.negative
@pytest.mark.parametrize(
    "bad_date", ["14/08/2026", "Aug 14", "2026-02-30"],
    ids=["slashes", "text", "impossible_date"],
)
def test_query_5_finds_bad_review_date(seeded_db, bad_date):
    # validation query 5 detects bad review dates.
    seeded_db.execute(
        "UPDATE approvals SET review_date = ?"
        " WHERE id = (SELECT id FROM approvals WHERE status = 'approved' ORDER BY id LIMIT 1)",
        (bad_date,),
    )
    rows = run_query(seeded_db, "05_shared_contract_violations.sql")
    assert len(rows) == 1

@pytest.mark.integration
@pytest.mark.negative
def test_query_5_finds_uppercase_status(seeded_db):
    # validation query 5 detects a non-lowercase status.
    seeded_db.commit()
    seeded_db.execute("PRAGMA ignore_check_constraints = ON")
    seeded_db.execute(
        "UPDATE approvals SET status = 'Approved'"
        " WHERE id = (SELECT id FROM approvals WHERE status = 'approved' ORDER BY id LIMIT 1)"
    )
    rows = run_query(seeded_db, "05_shared_contract_violations.sql")
    assert len(rows) == 1
    assert rows[0][2] == "Approved"