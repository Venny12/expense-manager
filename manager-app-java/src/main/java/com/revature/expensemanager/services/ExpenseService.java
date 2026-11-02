package com.revature.expensemanager.services;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.revature.expensemanager.PendingExpense;
import com.revature.expensemanager.User;
import com.revature.expensemanager.dao.ExpenseDao;
import com.revature.expensemanager.exceptions.AuthorizationException;
import com.revature.expensemanager.exceptions.DatabaseException;

/** Applies access rules before retrieving expenses. */
public class ExpenseService {

    private static final Logger LOGGER =
        Logger.getLogger(ExpenseService.class.getName());

    private final ExpenseDao expenseDao;

    public ExpenseService(ExpenseDao expenseDao) {
        this.expenseDao = expenseDao;
    }

    public List<PendingExpense> listPending(User user) {
        if (user == null || !"manager".equals(user.role())) {
            throw new AuthorizationException(
                "Only managers can view pending expenses."
            );
        }

        try {
            return expenseDao.findPending();
        } catch (SQLException error) {
            LOGGER.log(
                Level.SEVERE, "Could not load pending expenses.", error
            );

            throw new DatabaseException(
                "Could not load pending expenses.", error
            );
        }
    }
}