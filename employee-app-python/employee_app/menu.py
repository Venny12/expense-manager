"""Employee menu. Only this file (and main.py) uses input() and print()."""

import logging

from employee_app import services, validators
from employee_app.db import get_connection
from employee_app.exceptions import ExpenseManagerError, ValidationError

logger = logging.getLogger(__name__)


MENU = """
=== Expense Manager (Employee) ===
1. Submit expense
2. View my expenses
3. Edit pending expense
4. Delete pending expense
5. Review history
0. Exit"""


def _ask(prompt, validate, keep=None):
    """Ask until the value is valid. If `keep` is given, Enter keeps it."""
    while True:
        text = input(prompt).strip()
        if text == "" and keep is not None:
            return keep
        try:
            return validate(text)
        except ValidationError as err:
            print(err)


def handle_submit(conn_factory, user):
    """Collect expense details and save a pending expense."""
    with conn_factory() as connection:
        categories = services.get_categories(connection, user)

    if not categories:
        print("No expense categories available.")
        return

    amount = _ask("Amount: ", validators.validate_amount)

    print("\nCategories:")
    for category in categories:
        print(f"{category['id']}. {category['name']}")

    category_ids = {category["id"] for category in categories}

    category_id = _ask(
        "Category number: ",
        lambda text: validators.validate_category(text, category_ids),
    )

    description = _ask(
        "Description: ",
        validators.validate_description,
    )

    expense_date = _ask(
        "Expense date (YYYY-MM-DD, Enter = today): ",
        validators.validate_expense_date,
    )

    with conn_factory() as connection:
        expense_id = services.submit_expense(
            connection,
            user,
            amount,
            category_id,
            description,
            expense_date,
        )

    print(f"Expense submitted. ID: {expense_id}. Status: pending.")


def handle_view(conn_factory, user):
    """Placeholder until T5P1-8 (view expenses) is merged."""
    print("View expenses is not available yet.")


def handle_edit(conn_factory, user):
    """Placeholder until T5P1-10 (edit expense) is merged."""
    print("Edit expense is not available yet.")


def handle_delete(conn_factory, user):
    """Placeholder until T5P1-11 (delete expense) is merged."""
    print("Delete expense is not available yet.")


def handle_history(conn_factory, user):
    """Placeholder until T5P1-12 (review history) is merged."""
    print("Review history is not available yet.")


def run_menu(user, conn_factory=get_connection):
    """Show the menu until the user chooses 0. Errors never show a traceback."""
    handlers = {
        1: handle_submit,
        2: handle_view,
        3: handle_edit,
        4: handle_delete,
        5: handle_history,
    }
    while True:
        print(MENU)
        try:
            choice = validators.validate_menu_choice(
                input("Choice: "), {0, 1, 2, 3, 4, 5}
            )
        except ValidationError as err:
            print(err)
            continue
        if choice == 0:
            print("Exit.")
            return
        try:
            handlers[choice](conn_factory, user)
        except ExpenseManagerError as err:
            print(err)
        except Exception:
            logger.exception("Unexpected error in menu option %s", choice)
            print("Unexpected error. See log.")