"""Business rules for the Employee App."""

import logging

from employee_app.exceptions import (
    AuthenticationError,
    AuthorizationError,
    ValidationError,
    DatabaseError, 
)

logger = logging.getLogger(__name__)

import sqlite3
from datetime import datetime

from employee_app import validators

def login(connection, username, password):
    """Check credentials and allow employee accounts only."""
    if not username.strip() or not password.strip():
        raise ValidationError("Username and password are required.")

    user = connection.execute(
        """
        SELECT id, username, role, full_name
        FROM users
        WHERE username = ? AND password = ?
        """,
        (username, password),
    ).fetchone()

    if user is None:
        logger.warning("Invalid login attempt.")
        raise AuthenticationError("Invalid username or password.")

    if user["role"] != "employee":
        logger.warning("Login refused: account role has no access.")
        raise AuthorizationError(
            "This account does not have access to this app."
        )

    logger.info("Login successful: user_id=%s", user["id"])
    return dict(user)

def require_employee(connection, user):
    """Allow only an existing employee account."""
    if not user or user.get("role") != "employee":
        raise AuthorizationError("Only employees can perform this action.")

    account = connection.execute(
        "SELECT role FROM users WHERE id = ?",
        (user["id"],),
    ).fetchone()

    if account is None or account["role"] != "employee":
        raise AuthorizationError("Only employees can perform this action.")


def get_categories(connection, user):
    """Return the available expense categories."""
    require_employee(connection, user)

    return connection.execute(
        "SELECT id, name FROM categories ORDER BY id"
    ).fetchall()


def submit_expense(
    connection, user, amount, category_id, description,
    expense_date, today=None
):
    """Validate and save an expense with its pending approval."""
    require_employee(connection, user)

    categories = get_categories(connection, user)
    category_ids = {category["id"] for category in categories}

    amount = validators.validate_amount(str(amount))
    category_id = validators.validate_category(
        str(category_id), category_ids
    )
    description = validators.validate_description(description)
    expense_date = validators.validate_expense_date(
        expense_date, today=today
    )

    try:
        # Both records are saved together or both are rolled back.
        with connection:
            cursor = connection.execute(
                """
                INSERT INTO expenses (
                    user_id, category_id, amount,
                    description, expense_date, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                (
                    user["id"],
                    category_id,
                    float(amount),
                    description,
                    expense_date,
                    datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                ),
            )

            expense_id = cursor.lastrowid

            connection.execute(
                """
                INSERT INTO approvals (expense_id, status)
                VALUES (?, ?)
                """,
                (expense_id, "pending"),
            )

    except sqlite3.Error as error:
        logger.exception("Expense submission failed.")
        raise DatabaseError("Could not save the expense.") from error

    logger.info(
        "Expense submitted: expense_id=%s user_id=%s",
        expense_id,
        user["id"],
    )
    return expense_id