"""Input validation rules.

Each function returns the cleaned value or raises ValidationError with the
rule text, so the menu can show it and ask again.
"""

import re
from datetime import date, timedelta
from decimal import Decimal

from employee_app.exceptions import ValidationError

EXPENSE_ID_RULE = "Expense ID must be a positive whole number."
MENU_RULE = "Please enter a number from the menu."


def validate_expense_id(text):
    """VR-08: return a positive whole number."""
    text = (text or "").strip()
    if not re.fullmatch(r"[0-9]+", text) or int(text) <= 0:
        raise ValidationError(EXPENSE_ID_RULE)
    return int(text)


def validate_menu_choice(text, valid_choices):
    """VR-09: return a whole number that is shown in the menu."""
    text = (text or "").strip()
    if not re.fullmatch(r"[0-9]+", text) or int(text) not in valid_choices:
        raise ValidationError(MENU_RULE)
    return int(text)

def validate_amount(text):
    """VR-01: amount must be greater than 0 and at most 10000."""
    text = (text or "").strip()

    rule = (
        "Amount must be greater than 0, at most 10000.00, "
        "and contain at most 2 decimal places."
    )

    if not re.fullmatch(r"[0-9]+(?:\.[0-9]{1,2})?", text):
        raise ValidationError(rule)

    amount = Decimal(text)

    if amount <= 0 or amount > Decimal("10000.00"):
        raise ValidationError(rule)

    return amount


def validate_description(text):
    """VR-02: description must contain 1–200 characters after trimming."""
    description = (text or "").strip()

    if not 1 <= len(description) <= 200:
        raise ValidationError(
            "Description must contain 1–200 characters after trimming."
        )

    return description


def validate_category(text, valid_category_ids):
    """VR-03: category must be a number from the displayed list."""
    text = (text or "").strip()
    rule = "Choose a category number from the list."

    if not re.fullmatch(r"[0-9]+", text):
        raise ValidationError(rule)

    category_id = int(text)

    if category_id not in valid_category_ids:
        raise ValidationError(rule)

    return category_id


def validate_expense_date(text, today=None):
    """VR-04: accept a real date between today and 90 days ago."""
    today = today if today is not None else date.today()
    text = (text or "").strip()

    # Pressing Enter when submitting an expense means today.
    if text == "":
        return today.isoformat()

    rule = (
        "Date must be a real date in YYYY-MM-DD format, "
        "between today and 90 days ago."
    )

    if not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}", text):
        raise ValidationError(rule)

    try:
        expense_date = date.fromisoformat(text)
    except ValueError:
        raise ValidationError(rule) from None

    if not today - timedelta(days=90) <= expense_date <= today:
        raise ValidationError(rule)

    return expense_date.isoformat()