package com.revature.expensemanager.exceptions;
public class InvalidStateException extends ExpenseManagerException{
    public InvalidStateException(String message){
        super(message);
    }
}