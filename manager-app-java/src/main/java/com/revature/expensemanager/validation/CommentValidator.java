package com.revature.expensemanager.validation;

import com.revature.expensemanager.exceptions.ValidationException;

// Review comment rules
public final class CommentValidator {
    private static final int MAX_LENGTH = 200;
    private static final int MIN_DENIAL_LENGTH = 5;

    private static final String DENIAL_RULE = "A denial needs a comment of 5 to 200 characters.";
    private static final String APPROVAL_RULE = "A comment can be at most 200 characters.";

    private CommentValidator() {
        // This helper class does not need objects.
    }

    // VR-05 required, 5 to 200 characters after trimming.
    public static boolean isValidDenial(String comment) {
        if (comment == null) {
            return false;
        }

        int length = comment.trim().length();
        return length >= MIN_DENIAL_LENGTH && length <= MAX_LENGTH;
    }

    // VR-06: optional, 0 to 200 characters after trimming.
    public static boolean isValidApproval(String comment) {
        return comment == null || comment.trim().length() <= MAX_LENGTH;
    }

    // VR-05: returns the trimmed comment, or throws ValidationException.
    public static String validateDenial(String comment) {
        if (!isValidDenial(comment)) {
            throw new ValidationException(DENIAL_RULE);
        }

        return comment.trim();
    }

    // VR-06: returns the trimmed comment ("" when empty), or throws ValidationException.
    public static String validateApproval(String comment) {
        if (!isValidApproval(comment)) {
            throw new ValidationException(APPROVAL_RULE);
        }

        return comment == null ? "" : comment.trim();
    }
}
