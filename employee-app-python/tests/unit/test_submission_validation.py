"""Unit tests for expense submission validation: VR-01 to VR-04."""

from datetime import date, timedelta
from decimal import Decimal

import pytest

from employee_app import validators
from employee_app.exceptions import ValidationError


@pytest.mark.boundary
@pytest.mark.parametrize(
    "text, expected",
    [
        ("0.01", Decimal("0.01")),
        ("10000.00", Decimal("10000.00")),
        (" 25.50 ", Decimal("25.50")),
    ],
)
def test_valid_amount_is_accepted(text, expected):
    """VR-01: accept valid amounts, including both limits."""
    assert validators.validate_amount(text) == expected


@pytest.mark.negative
@pytest.mark.parametrize(
    "text",
    ["0", "-0.01", "10000.01", "12.345", "abc", ""],
)
def test_invalid_amount_is_rejected(text):
    """VR-01: reject invalid amounts with the required message."""
    with pytest.raises(ValidationError) as error:
        validators.validate_amount(text)

    assert str(error.value) == (
        "Amount must be greater than 0, at most 10000.00, "
        "and contain at most 2 decimal places."
    )


@pytest.mark.boundary
@pytest.mark.parametrize(
    "text, expected",
    [
        ("A", "A"),
        ("A" * 200, "A" * 200),
        ("  Taxi fare  ", "Taxi fare"),
    ],
)
def test_valid_description_is_accepted(text, expected):
    """VR-02: accept 1–200 characters after trimming."""
    assert validators.validate_description(text) == expected


@pytest.mark.negative
@pytest.mark.parametrize("text", ["", "   ", "A" * 201])
def test_invalid_description_is_rejected(text):
    """VR-02: reject empty or oversized descriptions."""
    with pytest.raises(ValidationError) as error:
        validators.validate_description(text)

    assert str(error.value) == (
        "Description must contain 1–200 characters after trimming."
    )


def test_displayed_category_is_accepted():
    """VR-03: accept a category from the displayed list."""
    assert validators.validate_category(" 2 ", {1, 2, 3}) == 2


@pytest.mark.negative
@pytest.mark.parametrize("text", ["99", "abc", "1.5", ""])
def test_invalid_category_is_rejected(text):
    """VR-03: reject unknown, non-integer, or empty categories."""
    with pytest.raises(ValidationError) as error:
        validators.validate_category(text, {1, 2, 3})

    assert str(error.value) == "Choose a category number from the list."


@pytest.mark.boundary
@pytest.mark.parametrize("days_ago", [0, 90])
def test_date_at_allowed_limits_is_accepted(days_ago):
    """VR-04: accept today and exactly 90 days ago."""
    today = date(2026, 11, 9)
    expense_date = today - timedelta(days=days_ago)

    assert validators.validate_expense_date(
        expense_date.isoformat(), today=today
    ) == expense_date.isoformat()


def test_empty_date_defaults_to_today():
    """VR-04: pressing Enter uses today's date."""
    today = date(2026, 11, 9)

    assert validators.validate_expense_date("", today=today) == "2026-11-09"


@pytest.mark.negative
@pytest.mark.parametrize(
    "text",
    [
        "2026-11-10",  # Tomorrow.
        "2026-08-10",  # 91 days before the fixed test date.
        "2026-02-30",  # Impossible date.
        "09/11/2026",  # Incorrect format.
    ],
)
def test_invalid_expense_date_is_rejected(text):
    """VR-04: reject dates outside the range or required format."""
    with pytest.raises(ValidationError) as error:
        validators.validate_expense_date(text, today=date(2026, 11, 9))

    assert str(error.value) == (
        "Date must be a real date in YYYY-MM-DD format, "
        "between today and 90 days ago."
    )