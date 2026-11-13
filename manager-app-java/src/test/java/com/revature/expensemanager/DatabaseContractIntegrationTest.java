package com.revature.expensemanager;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.revature.expensemanager.dao.ReportDao;
import com.revature.expensemanager.services.ReportService;

/*
Database cases TC-DB-001 to TC-DB-004 and TC-DB-009.

A real temporary SQLite file is built from sql/schema.sql (and sql/seed.sql where the case says "seed"). 
Expected numbers are written out by hand so a change to either script makes a test fail.
TC-DB-005 to TC-DB-008 are PyTest-only cases.
 */
@Tag("integration")
class DatabaseContractIntegrationTest {
    @TempDir
    Path tempFolder;
    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection(
            "jdbc:sqlite:" + tempFolder.resolve("database-contract.db")
        );
        run("PRAGMA foreign_keys = ON");
        runScript("schema.sql");
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.close();
    }

    private void run(String sql) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void runScript(String name) throws Exception {
        Path folder = Path.of("").toAbsolutePath();
        Path script = null;

        while (folder != null && script == null) {
            Path candidate = folder.resolve("sql").resolve(name);
            if (Files.exists(candidate)) {
                script = candidate;
            }
            folder = folder.getParent();
        }

        assertNotNull(script, "Could not find sql/" + name);
        String sql = Files.readString(script, StandardCharsets.UTF_8)
            .replace("﻿", "");

        // The scripts hold no triggers, so splitting on ";" is safe.
        for (String statement : sql.split(";")) {
            if (!statement.isBlank()) {
                run(statement);
            }
        }
    }

    private int count(String sql) throws Exception {
        try (var statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private List<String> strings(String sql) throws Exception {
        List<String> values = new ArrayList<>();
        try (var statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }

    private long addExpense(int userId, double amount, String description)
            throws Exception {
        long id;
        try (var statement = connection.prepareStatement("""
            INSERT INTO expenses (user_id, category_id, amount, description, expense_date, created_at)
            VALUES (?, 1, ?, ?, '2026-11-01', '2026-11-02 09:00:00')
            """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, userId);
            statement.setDouble(2, amount);
            statement.setString(3, description);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                id = keys.getLong(1);
            }
        }
        run("INSERT INTO approvals (expense_id, status) VALUES (" + id + ", 'pending')");
        return id;
    }

    private void addEmployees() throws Exception {
        run("INSERT INTO categories (id, name) VALUES (1, 'Travel')");
        run("""
            INSERT INTO users (id, username, password, role, full_name)
            VALUES (1, 'emp_a', 'x', 'employee', 'Employee A'),
                   (2, 'emp_b', 'x', 'employee', 'Employee B')
            """);
    }

    @Test
    @DisplayName("TC-DB-001: schema creates the required tables and columns")
    void schemaCreatesRequiredTablesAndColumns() throws Exception {
        assertEquals(
            List.of("approvals", "categories", "expenses", "users"),
            strings("""
                SELECT name FROM sqlite_master
                WHERE type = 'table' AND name NOT LIKE 'sqlite_%'
                ORDER BY name
                """)
        );

        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("users", List.of(
            "id", "username", "password", "role", "full_name"));
        expected.put("categories", List.of("id", "name"));
        expected.put("expenses", List.of(
            "id", "user_id", "category_id", "amount", "description",
            "expense_date", "created_at"));
        expected.put("approvals", List.of(
            "id", "expense_id", "status", "reviewer_id", "comment",
            "review_date"));

        for (var entry : expected.entrySet()) {
            assertEquals(
                entry.getValue(),
                strings("SELECT name FROM pragma_table_info('"
                    + entry.getKey() + "')"),
                entry.getKey()
            );
        }
    }

    @Test
    @DisplayName("TC-DB-002: an updated expense is persisted, others unchanged")
    void updatedExpenseIsPersisted() throws Exception {
        addEmployees();
        run("INSERT INTO categories (id, name) VALUES (2, 'Meals')");
        long id = addExpense(1, 12.50, "Old text");

        connection.setAutoCommit(false);
        try (var statement = connection.prepareStatement("""
            UPDATE expenses SET amount = ?, category_id = ?,
                   description = ?, expense_date = ? WHERE id = ?
            """)) {
            statement.setDouble(1, 99.99);
            statement.setInt(2, 2);
            statement.setString(3, "New text");
            statement.setString(4, "2026-11-05");
            statement.setLong(5, id);
            assertEquals(1, statement.executeUpdate());
        }
        connection.commit();
        connection.setAutoCommit(true);

        // A second connection proves the change was committed.
        try (Connection other = DriverManager.getConnection(
                 "jdbc:sqlite:" + tempFolder.resolve("database-contract.db"));
             var statement = other.createStatement();
             ResultSet row = statement.executeQuery(
                 "SELECT amount, category_id, description, expense_date, "
                 + "user_id, created_at FROM expenses WHERE id = " + id)) {
            assertTrue(row.next());
            assertEquals(99.99, row.getDouble("amount"), 0.0001);
            assertEquals(2, row.getInt("category_id"));
            assertEquals("New text", row.getString("description"));
            assertEquals("2026-11-05", row.getString("expense_date"));
            assertEquals(1, row.getInt("user_id"));
            assertEquals("2026-11-02 09:00:00", row.getString("created_at"));
        }
        assertEquals(List.of("pending"), strings(
            "SELECT status FROM approvals WHERE expense_id = " + id));
    }

    @Test
    @DisplayName("TC-DB-003: each expense references the correct employee")
    void expensesReferenceTheCorrectEmployee() throws Exception {
        addEmployees();
        long a1 = addExpense(1, 10, "A1");
        long a2 = addExpense(1, 20, "A2");
        long b1 = addExpense(2, 30, "B1");
        assertEquals(List.of(String.valueOf(a1), String.valueOf(a2)),
            strings("SELECT id FROM expenses WHERE user_id = 1 ORDER BY id"));
        assertEquals(List.of(String.valueOf(b1)),
            strings("SELECT id FROM expenses WHERE user_id = 2 ORDER BY id"));
        assertEquals(List.of("Employee B"), strings("""
            SELECT u.full_name FROM expenses e
            JOIN users u ON u.id = e.user_id WHERE e.id = %d
            """.formatted(b1)));
        assertThrows(java.sql.SQLException.class,
            () -> addExpense(9999, 10, "Orphan"));
    }

    @Test
    @DisplayName("TC-DB-004: seed script loads the expected rows")
    void seedScriptLoadsExpectedRows() throws Exception {
        runScript("seed.sql");
        assertEquals(6, count("SELECT COUNT(*) FROM categories"));
        assertEquals(6, count("SELECT COUNT(*) FROM users"));
        assertEquals(2, count(
            "SELECT COUNT(*) FROM users WHERE role = 'manager'"));
        assertEquals(4, count(
            "SELECT COUNT(*) FROM users WHERE role = 'employee'"));
        assertEquals(25, count("SELECT COUNT(*) FROM expenses"));
        assertEquals(25, count("SELECT COUNT(*) FROM approvals"));
        assertEquals(9, count(
            "SELECT COUNT(*) FROM approvals WHERE status = 'pending'"));
        assertEquals(8, count(
            "SELECT COUNT(*) FROM approvals WHERE status = 'approved'"));
        assertEquals(8, count(
            "SELECT COUNT(*) FROM approvals WHERE status = 'denied'"));
    }
}