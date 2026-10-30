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
    """Display the employee's expenses with an optional status filter."""
    print("\nView expenses")
    print("0. All")
    print("1. Pending")
    print("2. Approved")
    print("3. Denied")

    choice = _ask(
        "Filter: ",
        lambda text: validators.validate_menu_choice(
            text, {0, 1, 2, 3}
        ),
    )

    statuses = {
        0: None,
        1: "pending",
        2: "approved",
        3: "denied",
    }

    with conn_factory() as connection:
        expenses = services.view_expenses(
            connection, user, statuses[choice]
        )

    if not expenses:
        print("No expenses found.")
        return

    print(
        f"\n{'ID':<6} {'Date':<12} {'Category':<18} "
        f"{'Amount':>10} {'Status':<10}"
    )

    for expense in expenses:
        print(
            f"{expense['id']:<6} "
            f"{expense['expense_date']:<12} "
            f"{expense['category']:<18} "
            f"{expense['amount']:>10.2f} "
            f"{expense['status']:<10}"
        )


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