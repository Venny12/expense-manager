"""Start the Employee App and handle login prompts."""

import logging
from getpass import getpass
from pathlib import Path

from employee_app.db import get_connection
from employee_app.exceptions import (
    AuthenticationError,
    AuthorizationError,
    ExpenseManagerError,
    ValidationError,
)
from employee_app.services import login


def login_screen(connection):
    """Prompt for login and allow three incorrect attempts."""
    failures = 0

    while failures < 3:
        username = input("Username: ")
        password = getpass("Password: ")

        try:
            user = login(connection, username, password)

        except ValidationError as error:
            print(error)

        except AuthenticationError as error:
            print(error)
            failures += 1

        except AuthorizationError as error:
            print(error)
            return None

        else:
            print(f"Welcome, {user['full_name']}")
            return user

    print("Too many failed attempts.")
    return None


def main():
    """Configure logging, open the database, and start login."""
    project_root = Path(__file__).resolve().parents[2]
    log_folder = project_root / "logs"

    try:
        log_folder.mkdir(exist_ok=True)

        logging.basicConfig(
            filename=log_folder / "employee_app.log",
            level=logging.INFO,
            format="%(asctime)s %(levelname)s %(message)s",
            encoding="utf-8",
        )

    except OSError:
        print("Cannot open the application log.")
        return 1

    try:
        with get_connection() as connection:
            user = login_screen(connection)

            if user is None:
                return 1

            # Connect your teammate's menu here later.

        return 0

    except ExpenseManagerError as error:
        logging.error("Application error: %s", error)
        print(error)
        return 1

    except (EOFError, KeyboardInterrupt):
        print("\nApplication closed.")
        return 0

    except Exception:
        logging.exception("Unexpected application error.")
        print("Unexpected error. See log.")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())