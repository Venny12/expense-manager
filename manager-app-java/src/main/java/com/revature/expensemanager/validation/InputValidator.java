package com.revature.expensemanager.validation;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import com.revature.expensemanager.exceptions.ValidationException;

// VR=07, VR-08, VR-09
public final class InputValidator {

    private static final String MENU_RULE = "Please enter a number from the menu.";
    private static final String EXPENSE_ID_RULE = "Expense ID must be a positive whole number.";
    private static final String DATE_RULE = "Dates must be in YYYY-MM-DD format.";
    private static final String DATE_RANGE_RULE = "The start date must be on or before the end date.";
    // private static final String FUTURE_DATE_RULE = "Dates cannot be in the future.";

    // STRICT makes 2026-02-30 invalid instead of rolling it over to March.
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    
    public record DateRange(LocalDate start, LocalDate end) { // report date range
    }
    
    private InputValidator() { // helper class
    }


    // VR-08 is a positive whole number
    public static boolean isValidExpenseId(String input) {
        if (input == null || !input.trim().matches("[0-9]+")) {
            return false;
        }

        try {
            return Long.parseLong(input.trim()) > 0;
        } catch (NumberFormatException error) {
            return false;
        }
    }

    // VR-09: a whole number from 0 to maxChoice, as shown in the menu.
    public static boolean isValidMenuChoice(String input, int maxChoice) {
        if (input == null || !input.trim().matches("[0-9]+")) {
            return false;
        }

        try {
            return Integer.parseInt(input.trim()) <= maxChoice;
        } catch (NumberFormatException error) {
            return false;
        }
    }

    // VR-09: returns the menu choice, or throws ValidationException with the rule.
    public static int parseMenuChoice(String input, int maxChoice) {
        if (!isValidMenuChoice(input, maxChoice)) {
            throw new ValidationException(MENU_RULE);
        }

        return Integer.parseInt(input.trim());
    }

    // VR-07: a real date written as YYYY-MM-DD.
    public static boolean isValidDate(String input) {
        if (input == null || !input.trim().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            return false;
        }

        try {
            LocalDate.parse(input.trim(), DATE_FORMAT);
            return true;
        } catch (DateTimeParseException error) {
            return false;
        }
    }

    // VR-07: both dates valid, and start on or before end. Same-day ranges are valid.
    public static boolean isValidDateRange(String start, String end) {
        if (!isValidDate(start) || !isValidDate(end)) {
            return false;
        }

        return !LocalDate.parse(start.trim(), DATE_FORMAT)
            .isAfter(LocalDate.parse(end.trim(), DATE_FORMAT));
    }

    // VR-07: returns the date range, or throws ValidationException with the rule.
    public static DateRange parseDateRange(String start, String end) {
        if (!isValidDate(start) || !isValidDate(end)) {
            throw new ValidationException(DATE_RULE);
        }

        LocalDate startDate = LocalDate.parse(start.trim(), DATE_FORMAT);
        LocalDate endDate = LocalDate.parse(end.trim(), DATE_FORMAT);

        if (startDate.isAfter(endDate)) {
            throw new ValidationException(DATE_RANGE_RULE);
        }

        return new DateRange(startDate, endDate);
    }

    // VR-08: returns the expense ID, or throws ValidationException with the rule.
    public static int parseExpenseId(String input) {
        if (!isValidExpenseId(input)) {
            throw new ValidationException(EXPENSE_ID_RULE);
        }

        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException error) {
            throw new ValidationException(EXPENSE_ID_RULE);
        }
    }

}