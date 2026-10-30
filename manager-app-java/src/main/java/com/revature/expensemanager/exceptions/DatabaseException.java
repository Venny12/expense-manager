package com.revature.expensemanager.exceptions;

public class DatabaseException extends ExpenseManagerException {

    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}