import sqlite3

import pytest


def test_valid_user_is_saved(db_connection):
    """- accepts a valid user."""
    db_connection.execute(
        """
        INSERT INTO users (username, password, role, full_name)
        VALUES (?, ?, ?, ?)
        """,
        ("employee_test", "demo123", "employee", "Test Employee"),
    )

    result = db_connection.execute(
        "SELECT role, full_name FROM users WHERE username = ?",
        ("employee_test",),
    ).fetchone()

    assert result == ("employee", "Test Employee")


@pytest.mark.negative
def test_duplicate_username_is_rejected(db_connection):
    """- usernames must be unique."""
    db_connection.execute(
        """
        INSERT INTO users (username, password, role, full_name)
        VALUES (?, ?, ?, ?)
        """,
        ("employee_test", "demo123", "employee", "First Employee"),
    )

    with pytest.raises(sqlite3.IntegrityError):
        db_connection.execute(
            """
            INSERT INTO users (username, password, role, full_name)
            VALUES (?, ?, ?, ?)
            """,
            ("employee_test", "demo123", "employee", "Second Employee"),
        )


@pytest.mark.negative
def test_invalid_role_is_rejected(db_connection):
    """ - only employee and manager roles are allowed."""
    with pytest.raises(sqlite3.IntegrityError):
        db_connection.execute(
            """
            INSERT INTO users (username, password, role, full_name)
            VALUES (?, ?, ?, ?)
            """,
            ("invalid_user", "demo123", "admin", "Test User"),
        )


@pytest.mark.negative
def test_missing_full_name_is_rejected(db_connection):
    """- full_name cannot be NULL."""
    with pytest.raises(sqlite3.IntegrityError):
        db_connection.execute(
            """
            INSERT INTO users (username, password, role, full_name)
            VALUES (?, ?, ?, ?)
            """,
            ("employee_test", "demo123", "employee", None),
        )


@pytest.mark.boundary
@pytest.mark.parametrize(
    "amount, accepted",
    [
        (0, False),
        (-0.01, False),
        (0.01, True),
        (10000.00, True),
        (10000.01, False),
    ],
)
def test_expense_amount_limits(db_connection, amount, accepted):
    """Covers: - database expense amount limits."""
    # Create the user and category required by the expense.
    db_connection.execute(
        """
        INSERT INTO users (id, username, password, role, full_name)
        VALUES (?, ?, ?, ?, ?)
        """,
        (1, "employee_test", "demo123", "employee", "Test Employee"),
    )

    db_connection.execute(
        "INSERT INTO categories (id, name) VALUES (?, ?)",
        (1, "Travel"),
    )

    sql = """
        INSERT INTO expenses (
            user_id, category_id, amount,
            description, expense_date, created_at
        )
        VALUES (?, ?, ?, ?, ?, ?)
    """

    values = (
        1,
        1,
        amount,
        "Business train ticket",
        "2026-10-29",
        "2026-10-29 09:00:00",
    )

    if accepted:
        db_connection.execute(sql, values)

        saved_amount = db_connection.execute(
            "SELECT amount FROM expenses"
        ).fetchone()[0]

        assert saved_amount == amount
    else:
        with pytest.raises(sqlite3.IntegrityError):
            db_connection.execute(sql, values)


@pytest.mark.negative
def test_expense_with_missing_user_is_rejected(db_connection):
    """Covers: FR-S1 - expense must reference an existing user."""
    db_connection.execute(
        "INSERT INTO categories (id, name) VALUES (?, ?)",
        (1, "Travel"),
    )

    with pytest.raises(
        sqlite3.IntegrityError,
        match="FOREIGN KEY constraint failed",
    ):
        db_connection.execute(
            """
            INSERT INTO expenses (
                user_id, category_id, amount,
                description, expense_date, created_at
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (
                999,
                1,
                25.00,
                "Business train ticket",
                "2026-10-29",
                "2026-10-29 09:00:00",
            ),
        )

@pytest.mark.negative
def test_invalid_approval_status_is_rejected(db_connection):
    """Covers: FR-S1, FR-S5 - rejects an unsupported status."""
    db_connection.execute(
        """
        INSERT INTO users (id, username, password, role, full_name)
        VALUES (?, ?, ?, ?, ?)
        """,
        (1, "employee_test", "demo123", "employee", "Test Employee"),
    )

    db_connection.execute(
        "INSERT INTO categories (id, name) VALUES (?, ?)",
        (1, "Travel"),
    )

    db_connection.execute(
        """
        INSERT INTO expenses (
            id, user_id, category_id, amount,
            description, expense_date, created_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        (
            1, 1, 1, 25.00,
            "Business train ticket",
            "2026-10-29",
            "2026-10-29 09:00:00",
        ),
    )

    with pytest.raises(
        sqlite3.IntegrityError,
        match="CHECK constraint failed",
    ):
        db_connection.execute(
            "INSERT INTO approvals (expense_id, status) VALUES (?, ?)",
            (1, "cancelled"),
        )


@pytest.fixture
def expense_database(db_connection):
    """Prepare a valid employee, manager, category, and expense."""
    db_connection.executemany(
        """
        INSERT INTO users (id, username, password, role, full_name)
        VALUES (?, ?, ?, ?, ?)
        """,
        [
            (1, "employee_test", "demo123", "employee", "Test Employee"),
            (2, "manager_test", "demo123", "manager", "Test Manager"),
        ],
    )

    db_connection.execute(
        "INSERT INTO categories (id, name) VALUES (?, ?)",
        (1, "Travel"),
    )

    db_connection.execute(
        """
        INSERT INTO expenses (
            id, user_id, category_id, amount,
            description, expense_date, created_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        (
            1, 1, 1, 25.00, "Business train ticket",
            "2026-10-29", "2026-10-29 09:00:00",
        ),
    )

    return db_connection

@pytest.mark.negative
@pytest.mark.parametrize(
    "sql, values",
    [
        (
            """INSERT INTO expenses (
                user_id, category_id, amount,
                description, expense_date, created_at
            ) VALUES (?, ?, ?, ?, ?, ?)""",
            (
                1, 999, 25.00, "Business train ticket",
                "2026-10-29", "2026-10-29 09:00:00",
            ),
        ),
        (
            "INSERT INTO approvals (expense_id) VALUES (?)",
            (999,),
        ),
        (
            """INSERT INTO approvals (
                expense_id, status, reviewer_id, review_date
            ) VALUES (?, ?, ?, ?)""",
            (1, "approved", 999, "2026-10-29"),
        ),
    ],
    ids=["missing-category", "missing-expense", "missing-reviewer"],
)
def test_remaining_foreign_keys(expense_database, sql, values):
    """Covers: FR-S1 - related records must exist."""
    with pytest.raises(
        sqlite3.IntegrityError,
        match="FOREIGN KEY constraint failed",
    ):
        expense_database.execute(sql, values)


@pytest.mark.negative
def test_duplicate_category_is_rejected(expense_database):
    """Covers: FR-S1 - category names must be unique."""
    with pytest.raises(
        sqlite3.IntegrityError,
        match="UNIQUE constraint failed",
    ):
        expense_database.execute(
            "INSERT INTO categories (name) VALUES (?)",
            ("Travel",),
        )


@pytest.mark.negative
def test_duplicate_approval_is_rejected(expense_database):
    """Covers: FR-S1 - one approval record per expense."""
    expense_database.execute(
        "INSERT INTO approvals (expense_id) VALUES (?)",
        (1,),
    )

    with pytest.raises(
        sqlite3.IntegrityError,
        match="UNIQUE constraint failed",
    ):
        expense_database.execute(
            "INSERT INTO approvals (expense_id) VALUES (?)",
            (1,),
        )


@pytest.mark.smoke
def test_default_approval_is_pending(expense_database):
    """Covers: FR-S1, FR-S5 - default pending approval has no reviewer."""
    expense_database.execute(
        "INSERT INTO approvals (expense_id) VALUES (?)",
        (1,),
    )

    result = expense_database.execute(
        """
        SELECT status, reviewer_id, review_date
        FROM approvals
        WHERE expense_id = ?
        """,
        (1,),
    ).fetchone()

    assert result == ("pending", None, None)


@pytest.mark.parametrize("status", ["approved", "denied"])
def test_decided_status_is_accepted(expense_database, status):
    """Covers: FR-S1, FR-S5 - accepts supported decided statuses."""
    expense_database.execute(
        """
        INSERT INTO approvals (
            expense_id, status, reviewer_id, comment, review_date
        )
        VALUES (?, ?, ?, ?, ?)
        """,
        (1, status, 2, "Reviewed business expense", "2026-10-29"),
    )

    result = expense_database.execute(
        "SELECT status FROM approvals WHERE expense_id = ?",
        (1,),
    ).fetchone()

    assert result == (status,)


@pytest.mark.negative
@pytest.mark.parametrize(
    "table, column",
    [
        ("users", "username"),
        ("users", "password"),
        ("users", "role"),
        ("users", "full_name"),
        ("categories", "name"),
        ("expenses", "user_id"),
        ("expenses", "category_id"),
        ("expenses", "amount"),
        ("expenses", "description"),
        ("expenses", "expense_date"),
        ("expenses", "created_at"),
        ("approvals", "expense_id"),
        ("approvals", "status"),
    ],
)
def test_required_fields_reject_null(expense_database, table, column):
    """Covers: FR-S1 - required database fields reject NULL."""
    expense_database.execute(
        "INSERT INTO approvals (expense_id) VALUES (?)",
        (1,),
    )

    # Table and column names come only from the fixed cases above.
    sql = f'UPDATE "{table}" SET "{column}" = NULL WHERE id = ?'

    record_id = expense_database.execute(
        f'SELECT id FROM "{table}" ORDER BY id LIMIT 1'
    ).fetchone()[0]

    with pytest.raises(
        sqlite3.IntegrityError,
        match="NOT NULL constraint failed",
    ):
        expense_database.execute(sql, (record_id,))