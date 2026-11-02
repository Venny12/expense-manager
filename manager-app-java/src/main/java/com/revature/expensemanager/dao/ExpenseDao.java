package com.revature.expensemanager.dao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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
}