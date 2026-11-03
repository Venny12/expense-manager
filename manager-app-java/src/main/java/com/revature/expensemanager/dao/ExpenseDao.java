package com.revature.expensemanager.dao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.sql.Types;


import com.revature.expensemanager.PendingExpense;

/** Reads expense information from the database. */
public class ExpenseDao {

    private final Connection connection;

    public ExpenseDao(Connection connection) {
        this.connection = connection;
    }

    public List<PendingExpense> findPending() throws SQLException {
        String sql = """
            SELECT e.id, u.full_name AS employee_name,
                   e.expense_date, c.name AS category,
                   e.amount, e.description
            FROM expenses e
            JOIN users u ON u.id = e.user_id
            JOIN categories c ON c.id = e.category_id
            JOIN approvals a ON a.expense_id = e.id
            WHERE a.status = ?
            ORDER BY e.expense_date ASC, e.id ASC
            """;

        List<PendingExpense> expenses = new ArrayList<>();

        try (PreparedStatement statement =
                 connection.prepareStatement(sql)) {

            statement.setString(1, "pending");

            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    BigDecimal amount = BigDecimal.valueOf(
                        results.getDouble("amount")
                    ).setScale(2, RoundingMode.HALF_UP);

                    expenses.add(new PendingExpense(
                        results.getInt("id"),
                        results.getString("employee_name"),
                        results.getString("expense_date"),
                        results.getString("category"),
                        amount,
                        results.getString("description")
                    ));
                }
            }
        }

        return expenses;
    }

    /** Returns the approval status of an expense, or null if there is none. */
    public String findStatus(int expenseId) throws SQLException {
        String sql = "SELECT status FROM approvals WHERE expense_id = ?";

        try (PreparedStatement statement =
                 connection.prepareStatement(sql)) {

            statement.setInt(1, expenseId);

            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? results.getString("status") : null;
            }
        }
    }

    /**
     * Saves a decision only while the expense is still pending.
     * Returns false when nothing was changed, for example because another
     * decision was saved first.
     */
    public boolean saveDecision(int expenseId, String status, int reviewerId,
                                String comment, String reviewDate)
            throws SQLException {
        String sql = """
            UPDATE approvals
            SET status = ?, reviewer_id = ?, comment = ?, review_date = ?
            WHERE expense_id = ? AND status = 'pending'
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(sql)) {

            statement.setString(1, status);
            statement.setInt(2, reviewerId);

            if (comment == null || comment.isBlank()) {
                statement.setNull(3, Types.VARCHAR);
            } else {
                statement.setString(3, comment);
            }

            statement.setString(4, reviewDate);
            statement.setInt(5, expenseId);

            return statement.executeUpdate() == 1;
        }
    }
}
