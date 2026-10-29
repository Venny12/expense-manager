"""Small data classes shared by the Employee App."""

from dataclasses import dataclass


@dataclass(frozen=True)
class User:
    """The logged-in user. The login code creates one after a valid login."""

    id: int
    username: str
    full_name: str
    role: str