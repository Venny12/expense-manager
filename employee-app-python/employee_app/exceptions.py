"""Application-specific errors."""


class ExpenseManagerError(Exception):
    """Base error for the Employee App."""


class ValidationError(ExpenseManagerError):
    """An input breaks a validation rule."""


class AuthenticationError(ExpenseManagerError):
    """Login credentials are incorrect."""


class AuthorizationError(ExpenseManagerError):
    """The user is not allowed to perform an action."""


class NotFoundError(ExpenseManagerError):
    """The requested record does not exist."""


class InvalidStateError(ExpenseManagerError):
    """The record's current status prevents the action."""


class DatabaseError(ExpenseManagerError):
    """A database operation failed."""