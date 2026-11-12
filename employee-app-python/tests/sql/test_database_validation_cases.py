"""SQL validation cases TC-DB-005 to TC-DB-008.

Each case runs on the seed data plus expenses submitted through the real
employee service, then checks the query returns 0 rows. 

A second test per case damages the data on purpose to show the query can find a problem.
"""

from datetime import date
from pathlib import Path

import pytest
import sqlite3

from employee_app import services

pytestmark = pytest.mark.integration

SQL_DIR = Path(__file__).resolve().parents[3] / "sql"

EXACTLY_ONE_APPROVAL = """
    SELECT e.id, COUNT(a.id) AS approval_rows
    FROM expenses e LEFT JOIN approvals a ON a.expense_id = e.id
    GROUP BY e.id HAVING COUNT(a.id) <> 1
"""
DECIDED_WITHOUT_REVIEW = """
    SELECT id FROM approvals WHERE status IN ('approved', 'denied')
      AND (reviewer_id IS NULL OR review_date IS NULL)
"""
PENDING_WITH_REVIEW = """
    SELECT id FROM approvals WHERE status = 'pending'
      AND (reviewer_id IS NOT NULL OR comment IS NOT NULL
           OR review_date IS NOT NULL)
"""
INVALID_STATUS = """
    SELECT id, status FROM approvals
    WHERE status NOT IN ('pending', 'approved', 'denied')
"""


@pytest.fixture
def db(db_connection):
    db_connection.executescript(
        (SQL_DIR / "seed.sql").read_text(encoding="utf-8-sig"))
    db_connection.row_factory = sqlite3.Row
    user = {"id": 3, "role": "employee"}
    for text in ("Taxi", "Lunch"):
        services.submit_expense(db_connection, user, "15.00", 1, text,
                                "2026-11-05", today=date(2026, 11, 9))
    db_connection.commit()
    db_connection.row_factory = None
    return db_connection


def rows(db, sql):
    return db.execute(sql).fetchall()


# TC-DB-005
def test_every_expense_has_exactly_one_approval(db):
    assert rows(db, EXACTLY_ONE_APPROVAL) == []


@pytest.mark.negative
def test_exactly_one_approval_query_finds_expense_without_approval(db):
    db.execute("INSERT INTO expenses (id, user_id, category_id, amount, "
               "description, expense_date, created_at) VALUES "
               "(900, 3, 1, 5, 'x', '2026-11-01', '2026-11-01 09:00:00')")
    assert rows(db, EXACTLY_ONE_APPROVAL) == [(900, 0)]


# TC-DB-006
def test_decided_approvals_have_reviewer_and_review_date(db):
    assert rows(db, DECIDED_WITHOUT_REVIEW) == []


@pytest.mark.negative
def test_decided_query_finds_missing_reviewer(db):
    db.execute("UPDATE approvals SET reviewer_id = NULL WHERE expense_id = 10")
    assert len(rows(db, DECIDED_WITHOUT_REVIEW)) == 1


# TC-DB-007
def test_pending_approvals_have_no_review_data(db):
    assert rows(db, PENDING_WITH_REVIEW) == []


@pytest.mark.negative
def test_pending_query_finds_comment_on_pending(db):
    db.execute("UPDATE approvals SET comment = 'x' WHERE expense_id = 1")
    assert len(rows(db, PENDING_WITH_REVIEW)) == 1


# TC-DB-008
def test_only_valid_lowercase_statuses_exist(db):
    assert rows(db, INVALID_STATUS) == []


@pytest.mark.negative
def test_status_query_finds_bad_status_when_check_is_bypassed(tmp_path):
    # The CHECK constraint normally blocks this, so build a table without it.
    c = sqlite3.connect(tmp_path / "x.db")
    c.execute("CREATE TABLE approvals (id INTEGER, status TEXT)")
    c.execute("INSERT INTO approvals VALUES (1, 'Approved')")
    assert rows(c, INVALID_STATUS) == [(1, "Approved")]
    c.close()