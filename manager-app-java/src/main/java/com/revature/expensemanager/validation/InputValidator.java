package com.revature.expensemanager.validation;

import com.revature.expensemanager.exceptions.ValidationException;

public final class InputValidator {

    private static final String MENU_RULE = "Please enter a number from the menu.";

    private InputValidator() {
    }

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

    public static int parseMenuChoice(String input, int maxChoice) {
        if (!isValidMenuChoice(input, maxChoice)) {
            throw new ValidationException(MENU_RULE);
        }

        return Integer.parseInt(input.trim());
    }
}