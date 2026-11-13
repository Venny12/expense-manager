package com.revature.expensemanager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.revature.expensemanager.dao.ExpenseDao;
import com.revature.expensemanager.exceptions.AuthorizationException;
import com.revature.expensemanager.exceptions.InvalidStateException;
import com.revature.expensemanager.exceptions.NotFoundException;
import com.revature.expensemanager.exceptions.ValidationException;
import com.revature.expensemanager.services.ExpenseService;

/*
FR-M2, VR-06, FR-A4: Approving an expense, cases TC-MGR-013 to TC-MGR-017.

Two groups, matching the Level column of the case file:
  - ApprovalRules (Unit): the DAO is mocked; TC-MGR-014 and TC-MGR-016.
  - ApprovalWithRealDatabase (Integration): the real service, DAO and menu
    run against a real temporary SQLite database; TC-MGR-013, 014, 015, 017.
*/

class ApproveExpenseTest {

    private static final String REVIEW_DATE = "2026-11-10";
    private static final String APPROVAL_RULE = "A comment can be at most 200 characters.";

    private static Clock fixedClock() {
        return Clock.fixed(
            Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC
        );
    }
    // Unit: the DAO is mocked

    @Nested
    @Tag("unit")
    class ApprovalRules {

        private ExpenseDao dao;
        private ExpenseService service;
        private User manager;

        @BeforeEach
        void setUp() {
            dao = mock(ExpenseDao.class);
            service = new ExpenseService(dao, fixedClock());
            manager = new User(10, "manager1", "manager", "Manager One");
        }

        static Stream<Arguments> emptyComments() {
            return Stream.of(
                Arguments.of("TC-MGR-014-empty", ""),
                Arguments.of("TC-MGR-014-null", (String) null),
                Arguments.of("TC-MGR-014-spaces", "     ")
            );
        }

        @Tag("boundary")
        @DisplayName("TC-MGR-014 / VR-06: an empty approval comment is accepted")
        @ParameterizedTest(name = "{0}")
        @MethodSource("emptyComments")
        void emptyApprovalCommentIsAccepted(String caseId, String comment)
                throws Exception {
            when(dao.findStatus(1)).thenReturn("pending");
            when(dao.saveDecision(1, "approved", 10, "", REVIEW_DATE))
                .thenReturn(true);

            service.approve(manager, 1, comment);

            verify(dao).findStatus(1);
            verify(dao).saveDecision(1, "approved", 10, "", REVIEW_DATE);
            verifyNoMoreInteractions(dao);
        }

        static Stream<Arguments> commentLengths() {
            return Stream.of(
                Arguments.of("TC-MGR-016-200-characters", "a".repeat(200), true),
                Arguments.of("TC-MGR-016-201-characters", "a".repeat(201), false),
                Arguments.of(
                    "TC-MGR-016-200-plus-outer-spaces",
                    "  " + "a".repeat(200) + "  ", true
                ),
                Arguments.of(
                    "TC-MGR-016-201-plus-outer-spaces",
                    "  " + "a".repeat(201) + "  ", false
                )
            );
        }

        @Tag("boundary")
        @DisplayName("TC-MGR-016 / VR-06: 200 characters are accepted, 201 are rejected")
        @ParameterizedTest(name = "{0}")
        @MethodSource("commentLengths")
        void approvalCommentLengthLimit(
            String caseId, String comment, boolean accepted
        ) throws Exception {
            if (accepted) {
                String expected = comment.trim();
                when(dao.findStatus(1)).thenReturn("pending");
                when(dao.saveDecision(1, "approved", 10, expected, REVIEW_DATE))
                    .thenReturn(true);

                service.approve(manager, 1, comment);

                verify(dao).saveDecision(
                    1, "approved", 10, expected, REVIEW_DATE
                );
            } else {
                ValidationException error = assertThrows(
                    ValidationException.class,
                    () -> service.approve(manager, 1, comment)
                );

                assertEquals(APPROVAL_RULE, error.getMessage());
                // Rejected before the database is touched.
                verifyNoInteractions(dao);
            }
        }

        @Tag("negative")
        @DisplayName("FR-A4: an employee cannot approve an expense")
        @Test
        void employeeCannotApprove() {
            User employee = new User(1, "alice", "employee", "Alice");

            AuthorizationException error = assertThrows(
                AuthorizationException.class,
                () -> service.approve(employee, 1, "Fine")
            );

            assertEquals(
                "Only managers can approve or deny expenses.",
                error.getMessage()
            );
            verifyNoInteractions(dao);
        }
    }

    // Integration: real SQLite database

    // A row of the approvals table, as stored.
    private record Approval(String status, Integer reviewerId, String comment, String reviewDate) {
    }

    @Nested
    @Tag("integration")
    class ApprovalWithRealDatabase {

        @TempDir
        Path tempFolder;

        private Connection connection;
        private ExpenseService expenses;
        private User manager;

        // Expense 1: Alice, pending.  
        // Expense 2: Bob, pending.
        // Expense 3: Alice, already approved by manager 11 on 2026-11-05.
        private static final Approval EARLIER_DECISION = new Approval(
            "approved", 11, "Earlier decision", "2026-11-05"
        );

        @BeforeEach
        void setUp() throws Exception {
            connection = DriverManager.getConnection(
                "jdbc:sqlite:" + tempFolder.resolve("manager-approve.db")
            );
            execute("PRAGMA foreign_keys = ON");

            createSchema();

            execute("""
                INSERT INTO users (id, username, password, role, full_name)
                VALUES
                    (1, 'alice', 'test123', 'employee', 'Alice'),
                    (2, 'bob', 'test123', 'employee', 'Bob'),
                    (10, 'manager1', 'test123', 'manager', 'Manager One'),
                    (11, 'manager2', 'test123', 'manager', 'Manager Two')
                """);
            execute("INSERT INTO categories (id, name) VALUES (1, 'Travel')");

            addExpense(1, 1, 25.50);
            addExpense(2, 2, 12.00);
            addExpense(3, 1, 9.99);
            execute("""
                UPDATE approvals
                SET status = 'approved', reviewer_id = 11,
                    comment = 'Earlier decision', review_date = '2026-11-05'
                WHERE expense_id = 3
                """);

            manager = new User(10, "manager1", "manager", "Manager One");
            expenses = new ExpenseService(
                new ExpenseDao(connection), fixedClock()
            );
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

        private void addExpense(int id, int employeeId, double amount)
                throws Exception {
            try (var statement = connection.prepareStatement("""
                INSERT INTO expenses
                    (id, user_id, category_id, amount, description,
                     expense_date, created_at)
                VALUES (?, ?, 1, ?, 'Test expense', '2026-11-10',
                        '2026-11-10 12:00:00')
                """)) {
                statement.setInt(1, id);
                statement.setInt(2, employeeId);
                statement.setDouble(3, amount);
                statement.executeUpdate();
            }

            execute("INSERT INTO approvals (expense_id) VALUES (" + id + ")");
        }

        // The stored approval row for one expense; null when there is none.
        private Approval stored(int expenseId) throws Exception {
            try (var statement = connection.prepareStatement(
                "SELECT * FROM approvals WHERE expense_id = ?"
            )) {
                statement.setInt(1, expenseId);

                try (var result = statement.executeQuery()) {
                    if (!result.next()) {
                        return null;
                    }

                    int reviewer = result.getInt("reviewer_id");
                    Integer reviewerId = result.wasNull() ? null : reviewer;

                    return new Approval(
                        result.getString("status"), reviewerId,
                        result.getString("comment"),
                        result.getString("review_date")
                    );
                }
            }
        }

        private int count(String table) throws Exception {
            try (
                var statement = connection.createStatement();
                var result = statement.executeQuery(
                    "SELECT COUNT(*) FROM " + table
                )
            ) {
                result.next();
                return result.getInt(1);
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

        // TC-MGR-013  Approve a pending expense with a comment

        @Test
        @DisplayName("TC-MGR-013 / FR-M2: approving through the menu saves status, reviewer, comment and date")
        void approvingThroughTheMenuSavesTheDecision() throws Exception {
            // The menu uses the real clock, unlike the fixed-clock service.
            LocalDate before = LocalDate.now();

            String output = runMenu("2\n1\na\nLooks good, approved.\n0\n");

            LocalDate after = LocalDate.now();
            Approval saved = stored(1);

            assertTrue(output.contains("Expense 1 approved."));
            assertEquals("approved", saved.status());
            assertEquals(10, saved.reviewerId());
            assertEquals("Looks good, approved.", saved.comment());

            LocalDate reviewDate = LocalDate.parse(saved.reviewDate());
            assertFalse(reviewDate.isBefore(before));
            assertFalse(reviewDate.isAfter(after));
        }

        static Stream<Arguments> commentsStoredAccurately() {
            return Stream.of(
                Arguments.of("Approved, thanks.", "Approved, thanks."),
                Arguments.of("  padded comment  ", "padded comment"),
                Arguments.of(
                    "Said \"yes\" & 'ok' <b>fine</b>",
                    "Said \"yes\" & 'ok' <b>fine</b>"
                ),
                Arguments.of(
                    "'; DROP TABLE approvals;--", "'; DROP TABLE approvals;--"
                ),
                Arguments.of(
                    "Café déjà vu — 日本語", "Café déjà vu — 日本語"
                ),
                Arguments.of("a".repeat(200), "a".repeat(200))
            );
        }

        @DisplayName("TC-MGR-013 / FR-M2: the comment is stored exactly as typed (trimmed)")
        @ParameterizedTest(name = "[{0}]")
        @MethodSource("commentsStoredAccurately")
        void commentIsStoredAccurately(String comment, String expected)
                throws Exception {
            expenses.approve(manager, 1, comment);

            assertEquals(
                new Approval("approved", 10, expected, REVIEW_DATE),
                stored(1)
            );

            // The approvals table is still there and still holds every row.
            assertEquals(3, count("approvals"));
        }

        @Test
        @DisplayName("TC-MGR-013 / FR-M2: approving one expense changes no other row")
        void approvingOneExpenseLeavesTheOthersUnchanged() throws Exception {
            Approval otherPending = stored(2);

            expenses.approve(manager, 1, "Within policy");

            assertEquals(otherPending, stored(2));
            assertEquals("pending", stored(2).status());
            assertEquals(EARLIER_DECISION, stored(3));
            assertEquals(3, count("expenses"));
            assertEquals(3, count("approvals"));
        }

        // TC-MGR-014  Approve with an empty comment

        static Stream<Arguments> emptyAnswers() {
            return Stream.of(
                Arguments.of("TC-MGR-014-enter-only", ""),
                Arguments.of("TC-MGR-014-spaces-only", "     ")
            );
        }

        @Tag("boundary")
        @DisplayName("TC-MGR-014 / VR-06: an empty comment is saved as NULL and the expense is approved")
        @ParameterizedTest(name = "{0}")
        @MethodSource("emptyAnswers")
        void emptyCommentIsStoredAsNull(String caseId, String answer)
                throws Exception {
            String output = runMenu("2\n1\na\n" + answer + "\n0\n");

            Approval saved = stored(1);

            assertTrue(output.contains("Expense 1 approved."));
            assertEquals("approved", saved.status());
            assertEquals(10, saved.reviewerId());
            assertNull(saved.comment());
        }

        // TC-MGR-015  Approve an already Approved expense

        @Tag("negative")
        @Test
        @DisplayName("TC-MGR-015 / FR-M2: an approved expense is refused before the approve/deny prompt")
        void approvedExpenseIsRefusedBeforeAskingForTheAction()
                throws Exception {
            String output = runMenu("2\n3\n0\n");

            assertTrue(output.contains("Expense is no longer pending."));
            assertFalse(output.contains("Approve (a) or deny (d):"));
            assertFalse(output.contains("Comment"));
            assertTrue(output.contains("Exit."));

            assertEquals(EARLIER_DECISION, stored(3));
        }

        // TC-MGR-016, integration side

        @Tag("boundary")
        @Test
        @DisplayName("TC-MGR-016 / VR-06: a 201-character comment re-prompts and nothing is saved")
        void tooLongCommentAsksAgainAndSavesNothing() throws Exception {
            String output = runMenu(
                "2\n1\na\n" + "a".repeat(201) + "\nShort and fine\n0\n"
            );

            assertTrue(output.contains(APPROVAL_RULE));
            assertTrue(output.contains("Expense 1 approved."));
            assertEquals("Short and fine", stored(1).comment());
        }

        // TC-MGR-017  Expense deleted by the employee before the decision

        @Tag("negative")
        @Test
        @DisplayName("TC-MGR-017 / FR-M2: an expense deleted after it was listed gives 'not found' and nothing changes")
        void expenseDeletedAfterListingIsNotFound() throws Exception {
            // The manager has seen the expense in the pending list...
            List<Integer> listedIds = new ArrayList<>();
            expenses.listPending(manager)
                .forEach(expense -> listedIds.add(expense.id()));
            assertTrue(listedIds.contains(1));

            // ...then the employee deletes it (approval row first, as the Employee App does).
            execute("DELETE FROM approvals WHERE expense_id = 1");
            execute("DELETE FROM expenses WHERE id = 1");
            int expensesAfterDelete = count("expenses");
            int approvalsAfterDelete = count("approvals");

            String output = runMenu("2\n1\n0\n");
            assertTrue(output.contains("Expense not found."));
            assertFalse(output.contains("Approve (a) or deny (d):"));

            NotFoundException error = assertThrows(
                NotFoundException.class,
                () -> expenses.approve(manager, 1, "Too late")
            );
            assertEquals("Expense not found.", error.getMessage());
            // Nothing was created or changed.
            assertNull(stored(1));
            assertEquals(expensesAfterDelete, count("expenses"));
            assertEquals(approvalsAfterDelete, count("approvals"));
            assertEquals("pending", stored(2).status());
            assertEquals(EARLIER_DECISION, stored(3));
        }
    }
}