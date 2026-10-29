"""Input validation rules.

Each function returns the cleaned value or raises ValidationError with the
rule text, so the menu can show it and ask again.
"""

import re

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