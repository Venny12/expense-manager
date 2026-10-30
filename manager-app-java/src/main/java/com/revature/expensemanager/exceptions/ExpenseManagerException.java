package com.revature.expensemanager.exceptions;

public class ExpenseManagerException extends RuntimeException {

    public ExpenseManagerException(String message) {
        super(message);
    }

    public ExpenseManagerException(String message, Throwable cause) {
        super(message, cause);
    }
}