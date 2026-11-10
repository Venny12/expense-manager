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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.revature.expensemanager.dao.ExpenseDao;
import com.revature.expensemanager.dao.ReportDao;
import com.revature.expensemanager.exceptions.InvalidStateException;
import com.revature.expensemanager.services.ExpenseService;
import com.revature.expensemanager.services.ReportService;

@Tag("integration")
class AssignedManagerIntegrationTest {

    @TempDir
    Path tempFolder;

    private Connection connection;
    private ExpenseService expenses;
    private User manager;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection(
            "jdbc:sqlite:" + tempFolder.resolve("manager-tests.db")
        );

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
            .replace("\uFEFF", "");

        // This project's schema contains simple statements without triggers.
        for (String statement : sql.split(";")) {
            if (!statement.isBlank()) {
                execute(statement);
            }
        }

        execute("""
            INSERT INTO users
                (id, username, password, role, full_name)
            VALUES
                (1, 'employee1', 'test123', 'employee', 'Alice'),
                (2, 'employee2', 'test123', 'employee', 'Bob'),
                (10, 'manager1', 'test123', 'manager', 'Manager One'),
                (11, 'manager2', 'test123', 'manager', 'Manager Two')
            """);

        execute("""
            INSERT INTO categories (id, name)
            VALUES (1, 'Travel'), (2, 'Meals')
            """);

        manager = new User(10, "manager1", "manager", "Manager One");

        Clock clock = Clock.fixed(
            Instant.parse("2026-11-10T12:00:00Z"),
            ZoneOffset.UTC
        );

        expenses = new ExpenseService(new ExpenseDao(connection), clock);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    private void execute(String sql) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void addExpense(
        int id, int employeeId, int categoryId, double amount
    ) throws Exception {
        try (var statement = connection.prepareStatement("""
            INSERT INTO expenses
                (id, user_id, category_id, amount, description,
                 expense_date, created_at)
            VALUES (?, ?, ?, ?, 'Test expense', '2026-11-10',
                    '2026-11-10 12:00:00')
            """)) {

            statement.setInt(1, id);
            statement.setInt(2, employeeId);
            statement.setInt(3, categoryId);
            statement.setDouble(4, amount);
            statement.executeUpdate();
        }

        try (var statement = connection.prepareStatement(
            "INSERT INTO approvals (expense_id) VALUES (?)"
        )) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

    private void checkDecision(
        int expenseId, String status, Integer reviewerId,
        String comment, String reviewDate
    ) throws Exception {
        try (var statement = connection.prepareStatement(
            "SELECT * FROM approvals WHERE expense_id = ?"
        )) {
            statement.setInt(1, expenseId);

            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(status, result.getString("status"));

                int actualReviewer = result.getInt("reviewer_id");

                if (reviewerId == null) {
                    assertTrue(result.wasNull());
                } else {
                    assertEquals(reviewerId.intValue(), actualReviewer);
                }

                assertEquals(comment, result.getString("comment"));
                assertEquals(reviewDate, result.getString("review_date"));
                assertFalse(result.next());
            }
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

    private int countOccurrences(String text, String value) {
        return text.split(Pattern.quote(value), -1).length - 1;
    }

    @Test
    @DisplayName("FR-M2 / TC-MGR-018: denial saves the manager and comment")
    void denyPendingExpense() throws Exception {
        addExpense(1, 1, 1, 25.50);

        // The menu uses the real clock, unlike our fixed-clock service.
        LocalDate before = LocalDate.now();

        String output = runMenu(
            "2\n1\nd\nOutside policy\n0\n"
        );

        LocalDate after = LocalDate.now();

        assertTrue(output.contains("Expense 1 denied."));

        String savedDate;
        try (
            var statement = connection.createStatement();
            var result = statement.executeQuery(
                "SELECT review_date FROM approvals WHERE expense_id = 1"
            )
        ) {
            assertTrue(result.next());
            savedDate = result.getString("review_date");
        }

        assertNotNull(savedDate);
        LocalDate reviewDate = LocalDate.parse(savedDate);
        assertFalse(reviewDate.isBefore(before));
        assertFalse(reviewDate.isAfter(after));

        checkDecision(
            1, "denied", manager.id(), "Outside policy", savedDate
        );
    }

    @Tag("negative")
    @ParameterizedTest(name = "Invalid comment: [{0}]")
    @ValueSource(strings = {"", "abcd", "     "})
    @DisplayName("VR-05 / TC-MGR-019, 020, 022: invalid denial re-prompts")
    void invalidDenialCommentAsksAgain(String comment) throws Exception {
        addExpense(1, 1, 1, 25.50);

        // Input ends before a valid decision is supplied.
        String output = runMenu("2\n1\nd\n" + comment + "\n");

        assertTrue(output.contains(
            "A denial needs a comment of 5 to 200 characters."
        ));

        assertEquals(
            2,
            countOccurrences(
                output,
                "Comment (required, 5 to 200 characters): "
            )
        );

        checkDecision(1, "pending", null, null, null);
    }

    @Test
    @Tag("boundary")
    @DisplayName("VR-05 / TC-MGR-021: 200 accepted; 201 rejected")
    void checkMaximumDenialLength() throws Exception {
        addExpense(1, 1, 1, 25.50);
        addExpense(2, 2, 2, 10.00);

        expenses.deny(manager, 1, "a".repeat(200));

        checkDecision(
            1, "denied", manager.id(), "a".repeat(200), "2026-11-10"
        );

        String output = runMenu(
            "2\n2\nd\n" + "a".repeat(201) + "\n"
        );

        assertTrue(output.contains(
            "A denial needs a comment of 5 to 200 characters."
        ));

        checkDecision(2, "pending", null, null, null);
    }

    @Tag("negative")
    @ParameterizedTest
    @CsvSource({
        "approved, true",
        "denied, false"
    })
    @DisplayName("FR-M2 / TC-MGR-023: decided records cannot change")
    void cannotChangeDecidedExpense(
        String originalStatus, boolean tryDenial
    ) throws Exception {
        addExpense(1, 1, 1, 25.50);

        if (originalStatus.equals("approved")) {
            expenses.approve(manager, 1, "First decision");
        } else {
            expenses.deny(manager, 1, "First decision");
        }

        InvalidStateException error = assertThrows(
            InvalidStateException.class,
            () -> {
                if (tryDenial) {
                    expenses.deny(manager, 1, "Second decision");
                } else {
                    expenses.approve(manager, 1, "Second decision");
                }
            }
        );

        assertEquals(
            "Expense is no longer pending.", error.getMessage()
        );

        checkDecision(
            1, originalStatus, manager.id(),
            "First decision", "2026-11-10"
        );
    }

    @Test
    @Tag("negative")
    @DisplayName("FR-M2 / TC-MGR-024: missing ID stops before action prompt")
    void missingExpenseStopsBeforeActionPrompt() throws Exception {
        String output = runMenu("2\n999\n0\n");

        assertTrue(output.contains("Expense not found."));
        assertFalse(output.contains("Approve (a) or deny (d):"));

        try (
            var statement = connection.createStatement();
            var result = statement.executeQuery(
                "SELECT COUNT(*) FROM approvals"
            )
        ) {
            assertTrue(result.next());
            assertEquals(0, result.getInt(1));
        }
    }

    @Tag("negative")
    @ParameterizedTest(name = "Invalid action: [{0}]")
    @ValueSource(strings = {"x", "", "ad"})
    @DisplayName("FR-M2 / TC-MGR-025: invalid action re-prompts")
    void invalidActionAsksAgain(String action) throws Exception {
        addExpense(1, 1, 1, 25.50);

        String output = runMenu("2\n1\n" + action + "\n");

        assertTrue(output.contains(
            "Please enter a to approve or d to deny."
        ));

        assertEquals(
            2,
            countOccurrences(output, "Approve (a) or deny (d): ")
        );

        checkDecision(1, "pending", null, null, null);
    }

    @Test
    @Tag("negative")
    @DisplayName("FR-M2 / TC-MGR-026: second manager cannot replace decision")
    void secondDecisionKeepsFirstDecision() throws Exception {
        addExpense(1, 1, 1, 25.50);
        expenses.approve(manager, 1, "Within policy");

        User secondManager =
            new User(11, "manager2", "manager", "Manager Two");

        assertThrows(
            InvalidStateException.class,
            () -> expenses.deny(secondManager, 1, "Outside policy")
        );

        checkDecision(
            1, "approved", manager.id(), "Within policy", "2026-11-10"
        );
    }

    @ParameterizedTest(name = "Report by {0}")
    @ValueSource(strings = {"EMPLOYEE", "CATEGORY"})
    @DisplayName("FR-M3 / TC-MGR-027, 028: reports match database values")
    void reportsMatchDatabase(String reportName) throws Exception {
        addExpense(1, 1, 1, 10.25);
        addExpense(2, 1, 2, 20.50);
        addExpense(3, 2, 1, 30.75);
        addExpense(4, 2, 2, 40.00);

        expenses.approve(manager, 1, "Within policy");
        expenses.deny(manager, 2, "Outside policy");
        expenses.approve(manager, 4, "Within policy");
        // Expense 3 stays pending.

        ReportType type = ReportType.valueOf(reportName);
        ReportService reports =
            new ReportService(new ReportDao(connection));

        List<ReportRow> rows = reports.report(manager, type, null);

        assertEquals(2, rows.size());

        for (ReportRow row : rows) {
            checkReportAgainstDatabase(type, row);
        }

        assertEquals(
            type == ReportType.EMPLOYEE
                ? List.of("Alice", "Bob")
                : List.of("Meals", "Travel"),
            rows.stream().map(ReportRow::name).toList()
        );

        String choice = type == ReportType.EMPLOYEE ? "1" : "2";
        String output = runMenu("3\n" + choice + "\nn\nn\n0\n");

        assertTrue(output.contains(
            "Spending by " + type.columnTitle().toLowerCase()
        ));
        assertTrue(output.contains("Total Amount"));
        assertTrue(output.contains("Total Approved"));

        for (ReportRow row : rows) {
            String expectedRow =
                row.name() + "|" +
                row.count() + "|" +
                row.totalAmount().toPlainString() + "|" +
                row.approvedAmount().toPlainString();

            boolean found = output.lines()
                .map(line -> line
                    .replace('│', '|')
                    .replace('┃', '|')
                    .replaceAll("\\s+", "")
                )
                .anyMatch(line -> line.equals("|" + expectedRow + "|"));

            assertTrue(
                found,
                "Missing report row: " + expectedRow +
                "\nActual output:\n" + output
            );
        }
    }

    private void checkReportAgainstDatabase(
        ReportType type, ReportRow row
    ) throws Exception {
        // Calculate totals from individual records, independently of ReportDao.
        String sql = type == ReportType.EMPLOYEE
            ? """
                SELECT e.amount, a.status
                FROM expenses e
                JOIN approvals a ON a.expense_id = e.id
                JOIN users u ON u.id = e.user_id
                WHERE u.full_name = ?
                """
            : """
                SELECT e.amount, a.status
                FROM expenses e
                JOIN approvals a ON a.expense_id = e.id
                JOIN categories c ON c.id = e.category_id
                WHERE c.name = ?
                """;

        int count = 0;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal approved = BigDecimal.ZERO;

        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, row.name());

            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    BigDecimal amount = result.getBigDecimal("amount");
                    count++;
                    total = total.add(amount);

                    if ("approved".equals(result.getString("status"))) {
                        approved = approved.add(amount);
                    }
                }
            }
        }

        assertEquals(count, row.count());
        assertEquals(total.setScale(2), row.totalAmount());
        assertEquals(approved.setScale(2), row.approvedAmount());
    }
}