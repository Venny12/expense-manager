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