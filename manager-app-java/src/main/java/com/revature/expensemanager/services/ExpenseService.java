package com.revature.expensemanager.services;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.time.Clock;
import java.time.LocalDate;

import com.revature.expensemanager.PendingExpense;
import com.revature.expensemanager.User;
import com.revature.expensemanager.dao.ExpenseDao;
import com.revature.expensemanager.exceptions.AuthorizationException;
import com.revature.expensemanager.exceptions.DatabaseException;
import com.revature.expensemanager.exceptions.InvalidStateException;
import com.revature.expensemanager.exceptions.NotFoundException;
import com.revature.expensemanager.validation.CommentValidator;

/** Applies access rules before retrieving expenses. */
public class ExpenseService {

    private static final Logger LOGGER =
        Logger.getLogger(ExpenseService.class.getName());

    private final ExpenseDao expenseDao;
    private final Clock clock;

    public ExpenseService(ExpenseDao expenseDao) {
        this(expenseDao, Clock.systemDefaultZone());
    }

    public ExpenseService(ExpenseDao expenseDao, Clock clock) { //The clock can be fixed in tests, so "today" is predictable.
        this.expenseDao = expenseDao;
        this.clock = clock;
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

    // Fails early if the expense cannot be decided (missing or not pending).
    public void requirePending(User user, int expenseId) {
        requireManager(user);

        try {
            String current = expenseDao.findStatus(expenseId);

            if (current == null) {
                throw new NotFoundException("Expense not found.");
            }

            if (!"pending".equals(current)) {
                throw new InvalidStateException("Expense is no longer pending.");
            }
        } catch (SQLException error) {
            LOGGER.log(Level.SEVERE, "Could not check the expense.", error);
            throw new DatabaseException("Could not check the expense.", error);
        }
    }

    // FR-M2: approve a pending expense. The comment is optional.
    public void approve(User user, int expenseId, String comment) {
        requireManager(user);
        String cleanComment = CommentValidator.validateApproval(comment);
        decide(user, expenseId, "approved", cleanComment);
    }

    // FR-M2: deny a pending expense. A comment is required.
    public void deny(User user, int expenseId, String comment) {
        requireManager(user);
        String cleanComment = CommentValidator.validateDenial(comment);
        decide(user, expenseId, "denied", cleanComment);
    }

    // FR-A4: the service checks who is acting, not only the menu.
    private void requireManager(User user) {
        if (user == null || !"manager".equals(user.role())) {
            throw new AuthorizationException(
                "Only managers can approve or deny expenses."
            );
        }
    }

    private void decide(User user, int expenseId, String status,
                        String comment) {
        try {
            String current = expenseDao.findStatus(expenseId);

            if (current == null) {
                throw new NotFoundException("Expense not found.");
            }

            if (!"pending".equals(current)) {
                throw new InvalidStateException(
                    "Expense is no longer pending."
                );
            }

            String reviewDate = LocalDate.now(clock).toString();

            // The update only changes a row that is still pending, so two
            // decisions on the same expense cannot both succeed.
            boolean saved = expenseDao.saveDecision(
                expenseId, status, user.id(), comment, reviewDate
            );

            if (!saved) {
                throw new InvalidStateException(
                    "Expense is no longer pending."
                );
            }

            LOGGER.info(
                "Expense " + status + ": expense_id=" + expenseId
                + " reviewer_id=" + user.id()
            );

        } catch (SQLException error) {
            LOGGER.log(
                Level.SEVERE, "Could not save the decision.", error
            );

            throw new DatabaseException(
                "Could not save the decision.", error
            );
        }
    }






}