package com.revature.expensemanager.dao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.revature.expensemanager.ReportRow;
import com.revature.expensemanager.ReportType;
import com.revature.expensemanager.validation.InputValidator.DateRange;


/** Reads spending totals for the manager reports. */
public class ReportDao {

    // The name column differs between the two reports. Both strings are
    // fixed in this class, so no user input is ever added to the SQL.
    private static final String EMPLOYEE_SQL = buildSql(
        "u.full_name", "JOIN users u ON u.id = e.user_id"
    );

    private static final String CATEGORY_SQL = buildSql(
        "c.name", "JOIN categories c ON c.id = e.category_id"
    );

    private final Connection connection;

    public ReportDao(Connection connection) {
        this.connection = connection;
    }

    /**
     * Totals per employee or per category. A null range means all dates.
     * The range is inclusive: both the start and end day are counted.
     */
    public List<ReportRow> findTotals(ReportType type, DateRange range)
            throws SQLException {
        String sql = type == ReportType.EMPLOYEE ? EMPLOYEE_SQL : CATEGORY_SQL;
        String start = range == null ? null : range.start().toString();
        String end = range == null ? null : range.end().toString();

        List<ReportRow> rows = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setText(statement, 1, start);
            setText(statement, 2, start);
            setText(statement, 3, end);
            setText(statement, 4, end);

            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    rows.add(new ReportRow(
                        results.getString("name"),
                        results.getInt("expense_count"),
                        money(results.getDouble("total_amount")),
                        money(results.getDouble("approved_amount"))
                    ));
                }
            }
        }

        return rows;
    }

    private static String buildSql(String nameColumn, String nameJoin) {
        return """
            SELECT %s AS name,
                   COUNT(*) AS expense_count,
                   SUM(e.amount) AS total_amount,
                   COALESCE(SUM(CASE WHEN a.status = 'approved'
                                     THEN e.amount END), 0) AS approved_amount
            FROM expenses e
            %s
            JOIN approvals a ON a.expense_id = e.id
            WHERE (? IS NULL OR e.expense_date >= ?)
              AND (? IS NULL OR e.expense_date <= ?)
            GROUP BY %s
            ORDER BY %s COLLATE NOCASE ASC
            """.formatted(nameColumn, nameJoin, nameColumn, nameColumn);
    }

    private static void setText(PreparedStatement statement, int index,
                                String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    // Amounts are stored as REAL, so they are rounded to 2 decimals here.
    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
