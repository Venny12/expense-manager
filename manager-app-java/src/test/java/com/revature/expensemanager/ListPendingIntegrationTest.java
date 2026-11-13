package com.revature.expensemanager;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.revature.expensemanager.dao.ExpenseDao;
import com.revature.expensemanager.exceptions.AuthorizationException;
import com.revature.expensemanager.services.ExpenseService;

/*
FR-M1, FR-A4: Listing pending expenses, cases TC-MGR-011 and TC-MGR-012.

The real ExpenseService, ExpenseDao and Menu run against a real temporary SQLite database (schema.sql). 
Only the keyboard input and the screen are replaced.

 * Pending expenses in the test data, oldest first:
 *   2 Bob   2026-11-05 Meals    12.50  Team lunch
 *   3 Carol 2026-11-05 Lodging  80.00  Hotel
 *   1 Alice 2026-11-08 Travel   25.50  Taxi to airport
 * Decided expenses that must never be listed:
 *   4 Alice approved "Coffee",  5 Bob denied "Bus pass"
 */

@Tag("integration")
class ListPendingIntegrationTest {
    @TempDir
    Path tempFolder;

    private Connection connection;
    private ExpenseService expenses;
    private User manager;

    private static final PendingExpense TEAM_LUNCH = new PendingExpense(
        2, "Bob", "2026-11-05", "Meals", new BigDecimal("12.50"), "Team lunch"
    );
    private static final PendingExpense HOTEL = new PendingExpense(
        3, "Carol", "2026-11-05", "Lodging", new BigDecimal("80.00"), "Hotel"
    );
    private static final PendingExpense TAXI = new PendingExpense(
        1, "Alice", "2026-11-08", "Travel", new BigDecimal("25.50"),
        "Taxi to airport"
    );

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection(
            "jdbc:sqlite:" + tempFolder.resolve("manager-list.db")
        );
        connection.createStatement().execute("PRAGMA foreign_keys = ON");

        createSchema();

        execute("""
            INSERT INTO users (id, username, password, role, full_name)
            VALUES
                (1, 'alice', 'test123', 'employee', 'Alice'),
                (2, 'bob', 'test123', 'employee', 'Bob'),
                (3, 'carol', 'test123', 'employee', 'Carol'),
                (10, 'manager1', 'test123', 'manager', 'Manager One')
            """);
        execute("""
            INSERT INTO categories (id, name)
            VALUES (1, 'Travel'), (2, 'Meals'), (3, 'Lodging')
            """);
        addExpense(1, 1, 1, 25.50, "Taxi to airport", "2026-11-08", "pending");
        addExpense(2, 2, 2, 12.50, "Team lunch", "2026-11-05", "pending");
        addExpense(3, 3, 3, 80.00, "Hotel", "2026-11-05", "pending");
        addExpense(4, 1, 2, 9.99, "Coffee", "2026-11-01", "approved");
        addExpense(5, 2, 1, 40.00, "Bus pass", "2026-11-02", "denied");

        manager = new User(10, "manager1", "manager", "Manager One");

        Clock clock = Clock.fixed(
            Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC
        );
        expenses = new ExpenseService(new ExpenseDao(connection), clock);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    private void createSchema() throws Exception {
        // Find the project's schema from either the root or Maven folder.
        Path folder = Path.of("").toAbsolutePath();
        Path schema = null;

        while (folder != null) {
            Path candidate = folder.resolve("sql/schema.sql");

            if (Files.exists(candidate)) {
                schema = candidate;
                break;
            }

            folder = folder.getParent();
        }
        assertNotNull(schema, "Could not find sql/schema.sql");

        String sql = Files.readString(schema, StandardCharsets.UTF_8)
            .replace("", "");

        for (String statement : sql.split(";")) {
            if (!statement.isBlank()) {
                execute(statement);
            }
        }
    }

    private void execute(String sql) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void addExpense(
        int id, int employeeId, int categoryId, double amount,
        String description, String date, String status
    ) throws Exception {
        try (var statement = connection.prepareStatement("""
            INSERT INTO expenses
                (id, user_id, category_id, amount, description,
                 expense_date, created_at)
            VALUES (?, ?, ?, ?, ?, ?, '2026-11-10 12:00:00')
            """)) {
            statement.setInt(1, id);
            statement.setInt(2, employeeId);
            statement.setInt(3, categoryId);
            statement.setDouble(4, amount);
            statement.setString(5, description);
            statement.setString(6, date);
            statement.executeUpdate();
        }

        if (status.equals("pending")) {
            execute("INSERT INTO approvals (expense_id) VALUES (" + id + ")");
        } else {
            execute(
                "INSERT INTO approvals (expense_id, status, reviewer_id, "
                + "comment, review_date) VALUES (" + id + ", '" + status
                + "', 10, 'Decided earlier', '2026-11-09')"
            );
        }
    }

    private String runMenu(String answers) {
        PrintStream original = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (
            PrintStream capture =
                new PrintStream(output, true, StandardCharsets.UTF_8);
            Scanner scanner = new Scanner(answers)
        ) {
            System.setOut(capture);
            new Menu(manager, connection, scanner).run();
        } finally {
            System.setOut(original);
        }

        return output.toString(StandardCharsets.UTF_8);
    }

    // TC-MGR-011  View all pending expenses
    @Test
    @DisplayName("TC-MGR-011 / FR-M1: all and only pending expenses are returned, oldest first, with correct details")
    void everyPendingExpenseIsReturnedWithCorrectDetails() {
        List<PendingExpense> listed = expenses.listPending(manager);

        // Equal records, so the details are checked too. Same date: lower ID first.
        assertEquals(List.of(TEAM_LUNCH, HOTEL, TAXI), listed);
    }

    @Test
    @DisplayName("TC-MGR-011 / FR-M1: the screen shows every pending expense and no decided one")
    void menuShowsEveryPendingExpenseAndNoDecidedOne() {
        String output = runMenu("1\n0\n");
        for (PendingExpense expense : List.of(TEAM_LUNCH, HOTEL, TAXI)) {
            assertTrue(output.contains(expense.employeeName()));
            assertTrue(output.contains(expense.expenseDate()));
            assertTrue(output.contains(expense.category()));
            assertTrue(output.contains(expense.amount().toPlainString()));
            assertTrue(output.contains(expense.description()));
        }

        assertTrue(output.contains("Employee"));
        assertFalse(output.contains("Coffee"));
        assertFalse(output.contains("Bus pass"));
        assertFalse(output.contains("No pending expenses."));
        assertTrue(output.contains("Exit."));

        // Oldest first on screen as well.
        assertTrue(output.indexOf("Team lunch") < output.indexOf("Hotel"));
        assertTrue(output.indexOf("Hotel") < output.indexOf("Taxi to airport"));
    }

    @Test
    @DisplayName("FR-A4: an employee cannot list pending expenses")
    void employeeCannotListPendingExpenses() {
        User employee = new User(1, "alice", "employee", "Alice");

        AuthorizationException error = assertThrows(
            AuthorizationException.class,
            () -> expenses.listPending(employee)
        );

        assertEquals(
            "Only managers can view pending expenses.", error.getMessage()
        );
    }

    // TC-MGR-012  No pending expenses (a manual case in the case file; this automated check is an extra)
    @Test
    @Tag("negative")
    @DisplayName("TC-MGR-012 / FR-M1: with nothing pending the screen says so and returns to the menu")
    void menuSaysNoPendingExpensesAndReturnsToTheMenu() throws Exception {
        execute("DELETE FROM approvals WHERE status = 'pending'");
        assertEquals(List.of(), expenses.listPending(manager));
        String output = runMenu("1\n0\n");
        assertTrue(output.contains("No pending expenses."));
        assertFalse(output.contains("Employee"));
        assertFalse(output.contains("Unexpected error"));
        assertTrue(output.contains("Exit."));
    }
}