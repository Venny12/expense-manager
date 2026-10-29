"""Business rules for the Employee App."""

import logging

from employee_app.exceptions import (
    AuthenticationError,
    AuthorizationError,
    ValidationError,
)

logger = logging.getLogger(__name__)


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