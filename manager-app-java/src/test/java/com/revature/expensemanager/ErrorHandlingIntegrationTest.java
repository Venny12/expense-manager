package com.revature.expensemanager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.IOError;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/*
FR-A4, FR-S4, VR-09: menu choices and application errors, cases TC-MGR-038 and TC-MGR-040 to TC-MGR-045

 * The real Main, AuthenticationService and Menu run against a real temporary SQLite database (schema.sql).
 * Only the keyboard (a scripted Console), the screen and the log folder are replaced. 
 * Every test also checks that no stack trace reaches the screen.
 */

@Tag("integration")
class ErrorHandlingIntegrationTest {
    private static final String MANAGER_PASSWORD = "ManagerPass123!";
    private static final String MENU_RULE = "Please enter a number from the menu.";
    private static final String UNEXPECTED = "Unexpected error. See log.";
    @TempDir
    Path tempFolder;

    private Path databaseFile;

    private PrintStream originalOut;
    private ByteArrayOutputStream screen;

    private Handler[] originalHandlers;
    private boolean originalUseParentHandlers;
    /*
     * A scripted keyboard. readLine answers come from one list, readPassword answers from another. 
     * The prompts the app showed are recorded. If
     * failAtPrompt is set, asking that prompt throws the given failure
     * (an unexpected exception, or a Ctrl+C interrupt).
     */
    private static final class Keyboard {
        private final Deque<String> lines;
        private final Deque<String> passwords;
        private final String failAtPrompt;
        private final Throwable failure;
        private final List<String> prompts = new ArrayList<>();

        Keyboard(List<String> lines, List<String> passwords) {
            this(lines, passwords, null, null);
        }

        // An unexpected exception at the given prompt.
        Keyboard(List<String> lines, List<String> passwords,
                 String failAtPrompt) {
            this(lines, passwords, failAtPrompt,
                 new UnsupportedOperationException("simulated failure"));
        }

        Keyboard(List<String> lines, List<String> passwords,
                 String failAtPrompt, Throwable failure) {
            this.lines = new ArrayDeque<>(lines);
            this.passwords = new ArrayDeque<>(passwords);
            this.failAtPrompt = failAtPrompt;
            this.failure = failure;
        }

        Console console() {
            return mock(Console.class, invocation -> {
                String method = invocation.getMethod().getName();
                Object[] arguments = invocation.getArguments();

                if (method.equals("readLine") && arguments.length > 0) {
                    String prompt = (String) arguments[arguments.length - 1];
                    prompts.add(prompt);

                    if (prompt.equals(failAtPrompt)) {
                        throw failure;
                    }

                    return lines.pollFirst();
                }

                if (method.equals("readPassword") && arguments.length > 0) {
                    String prompt = (String) arguments[arguments.length - 1];
                    prompts.add(prompt);

                    if (prompt.equals(failAtPrompt)) {
                        throw failure;
                    }

                    String password = passwords.pollFirst();
                    return password == null ? null : password.toCharArray();
                }

                return null;
            });
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        databaseFile = tempFolder.resolve("manager-session.db");

        createDatabase();

        // Main replaces the root logger's handlers; remember them to restore.
        Logger rootLogger = Logger.getLogger("");
        originalHandlers = rootLogger.getHandlers();
        originalUseParentHandlers = rootLogger.getUseParentHandlers();

        originalOut = System.out;
        screen = new ByteArrayOutputStream();
        System.setOut(new PrintStream(screen, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);

        // Close the log file Main opened, so the temporary folder can be
        // deleted (an open file blocks deletion on Windows).
        Logger rootLogger = Logger.getLogger("");

        for (Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
            handler.close();
        }

        for (Handler handler : originalHandlers) {
            rootLogger.addHandler(handler);
        }

        rootLogger.setUseParentHandlers(originalUseParentHandlers);
    }

    private void createDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(
            "jdbc:sqlite:" + databaseFile
        )) {
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

            String sql = Files.readString(schema, StandardCharsets.UTF_8).replace("", "");

            try (var statement = connection.createStatement()) {
                for (String part : sql.split(";")) {
                    if (!part.isBlank()) {
                        statement.execute(part);
                    }
                }
                statement.execute("""
                    INSERT INTO users (id, username, password, role, full_name)
                    VALUES
                        (1, 'alice', 'AlicePass123!', 'employee', 'Alice'),
                        (2, 'bob', 'BobPass123!', 'employee', 'Bob'),
                        (10, 'manager1', '%s', 'manager', 'Manager One')
                    """.formatted(MANAGER_PASSWORD));
                statement.execute("""
                    INSERT INTO categories (id, name)
                    VALUES (1, 'Travel'), (2, 'Meals')
                    """);
                statement.execute("""
                    INSERT INTO expenses
                        (id, user_id, category_id, amount, description,
                         expense_date, created_at)
                    VALUES
                        (1, 1, 1, 25.50, 'Taxi to airport', '2026-11-08',
                         '2026-11-09 09:00:00'),
                        (2, 2, 2, 12.50, 'Team lunch', '2026-11-05',
                         '2026-11-09 09:00:00'),
                        (3, 1, 2, 9.99, 'Coffee', '2026-11-01',
                         '2026-11-09 09:00:00')
                    """);
                statement.execute("""
                    INSERT INTO approvals (expense_id, status, reviewer_id,
                                           comment, review_date)
                    VALUES
                        (1, 'pending', NULL, NULL, NULL),
                        (2, 'pending', NULL, NULL, NULL),
                        (3, 'approved', 10, 'Fine', '2026-11-09')
                    """);
            }
        }
    }

    private int run(Keyboard keyboard) {
        return Main.run(
            keyboard.console(),
            tempFolder.resolve("logs"),
            () -> databaseFile.toString()
        );
    }

    private String output() {
        return screen.toString(StandardCharsets.UTF_8);
    }

    private static void assertNoStackTrace(String output) {
        assertFalse(output.contains("Exception"), output);
        assertFalse(output.contains("\tat "), output);
    }

    private String log() throws Exception {
        return Files.readString(
            tempFolder.resolve("logs").resolve("manager_app.log")
        );
    }

    private int countOf(String text, String part) {
        return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }

    private int pendingCount() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                 "jdbc:sqlite:" + databaseFile);
             var statement = connection.createStatement();
             var rows = statement.executeQuery(
                 "SELECT COUNT(*) FROM approvals WHERE status = 'pending'")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    // TC-MGR-038  Missing database file
    @DisplayName("TC-MGR-038 / FR-S4: missing database file - friendly message, no stack trace, exit code 1")
    @Test
    void missingDatabaseFileGivesFriendlyMessage() {
        Path missing = tempFolder.resolve("does-not-exist.db");
        Keyboard keyboard = new Keyboard(List.of(), List.of());

        int exitCode = Main.run(
            keyboard.console(),
            tempFolder.resolve("logs"),
            () -> missing.toString()
        );

        assertEquals(1, exitCode);
        assertTrue(output().contains(
            "Cannot access the database. Check EM_DB_PATH and the database file."
        ));
        assertNoStackTrace(output());
        assertFalse(Files.exists(missing));
        assertEquals(List.of(), keyboard.prompts);
    }

    // TC-MGR-040  Invalid main-menu choice
    @DisplayName("TC-MGR-040 / VR-09: invalid menu choices show the error and the menu again, 0 exits normally")
    @Test
    void invalidMenuChoicesShowErrorAndMenuAgain() throws Exception {
        int pendingBefore = pendingCount();
        // 9 and 4 are above the largest choice (3); the rest are not numbers.
        Keyboard keyboard = new Keyboard(
            List.of("manager1", "abc", "9", "-1", "1.5", "", "0"),
            List.of(MANAGER_PASSWORD)
        );

        int exitCode = run(keyboard);

        assertEquals(0, exitCode);
        assertEquals(5, countOf(output(), MENU_RULE));
        assertTrue(output().contains("Exit."));
        assertNoStackTrace(output());
        // Login (2 prompts) + 5 invalid choices + exit: no feature prompt.
        assertEquals(8, keyboard.prompts.size());
        assertFalse(keyboard.prompts.contains("Expense ID: "));
        assertEquals(pendingBefore, pendingCount());
    }

    // TC-MGR-041  Error in one menu option does not end the session
    @DisplayName("TC-MGR-041 / FR-S4: a missing expense ID in Review does not end the session")
    @Test
    void errorInOneMenuOptionDoesNotEndTheSession() {
        // 2 = Review, 999999 = missing ID, 1 = pending list, 0 = exit.
        Keyboard keyboard = new Keyboard(
            List.of("manager1", "2", "999999", "1", "0"),
            List.of(MANAGER_PASSWORD)
        );

        int exitCode = run(keyboard);
        String output = output();

        assertEquals(0, exitCode);
        assertTrue(output.contains("Expense not found."), output);
        assertTrue(output.contains("Taxi to airport"), output);
        assertTrue(output.indexOf("Expense not found.")
            < output.indexOf("Taxi to airport"), output);
        assertTrue(output.contains("Exit."), output);
        assertNoStackTrace(output);
    }

    // TC-MGR-042  Unexpected error inside a menu option
    @DisplayName("TC-MGR-042 / FR-S4: unexpected error in a menu option is logged and the menu returns")
    @Test
    void unexpectedErrorInAMenuOptionIsLoggedAndMenuReturns() throws Exception {
        // Option 2 asks "Expense ID: "; the keyboard fails there.
        Keyboard keyboard = new Keyboard(
            List.of("manager1", "2", "0"),
            List.of(MANAGER_PASSWORD),
            "Expense ID: "
        );

        int exitCode = run(keyboard);

        assertEquals(0, exitCode);
        assertTrue(output().contains(UNEXPECTED));
        assertTrue(output().contains("Exit."));
        assertNoStackTrace(output());
        assertTrue(log().contains("Unexpected error in menu option 2"));
        assertTrue(log().contains("simulated failure"));
    }

    // TC-MGR-043  Unexpected error while the application runs
    @DisplayName("TC-MGR-043 / FR-S4: unexpected error at login is logged, exit code 1")
    @Test
    void unexpectedErrorWhileRunningIsLoggedWithExitCode1() throws Exception {
        Keyboard keyboard = new Keyboard(
            List.of(), List.of(), "Username: "
        );

        int exitCode = run(keyboard);

        assertEquals(1, exitCode);
        assertTrue(output().contains(UNEXPECTED));
        assertNoStackTrace(output());
        assertTrue(log().contains("Unexpected application error."));
        assertTrue(log().contains("simulated failure"));
    }

    // TC-MGR-044  Ctrl+C ends the app cleanly
    private static Throwable ctrlC() {
        return new UncheckedIOException(new IOException("interrupted"));
    }

    private void assertClosedCleanly(int exitCode) throws Exception {
        assertEquals(0, exitCode);
        assertTrue(output().contains("Application closed."), output());
        assertFalse(output().contains(UNEXPECTED), output());
        assertNoStackTrace(output());
        assertFalse(log().contains("Unexpected application error."));
        assertFalse(log().contains("Unexpected error in menu option"));
    }

    @DisplayName("TC-MGR-044 / FR-S4: Ctrl+C at the Username prompt - Application closed, exit code 0")
    @Test
    void ctrlCAtUsernamePromptEndsCleanly() throws Exception {
        Keyboard keyboard = new Keyboard(
            List.of(), List.of(), "Username: ", ctrlC()
        );

        assertClosedCleanly(run(keyboard));
    }

    @DisplayName("TC-MGR-044 / FR-S4: Ctrl+C at the Password prompt - Application closed, exit code 0")
    @Test
    void ctrlCAtPasswordPromptEndsCleanly() throws Exception {
        Keyboard keyboard = new Keyboard(
            List.of("manager1"), List.of(MANAGER_PASSWORD),
            "Password: ", ctrlC()
        );

        assertClosedCleanly(run(keyboard));
    }

    @DisplayName("TC-MGR-044 / FR-S4: Ctrl+C at the menu prompt - Application closed, exit code 0")
    @Test
    void ctrlCAtMenuPromptEndsCleanly() throws Exception {
        Keyboard keyboard = new Keyboard(
            List.of("manager1"), List.of(MANAGER_PASSWORD),
            "Choice: ", ctrlC()
        );

        assertClosedCleanly(run(keyboard));
        assertTrue(output().contains("Welcome, Manager One"));
    }

    @DisplayName("TC-MGR-044 / FR-S4: Ctrl+C inside Review an expense - Application closed, not back to the menu")
    @Test
    void ctrlCInsideAMenuOptionEndsCleanly() throws Exception {
        Keyboard keyboard = new Keyboard(
            List.of("manager1", "2"), List.of(MANAGER_PASSWORD),
            "Expense ID: ", ctrlC()
        );

        assertClosedCleanly(run(keyboard));
        // The menu was shown once only: the app did not return to it.
        assertEquals(1, countOf(output(), "=== Expense Manager (Manager) ==="));
    }

    @DisplayName("TC-MGR-044 / FR-S4: the console may report Ctrl+C as an IOError - Application closed, exit code 0")
    @Test
    void ctrlCReportedAsIoErrorEndsCleanly() throws Exception {
        Keyboard keyboard = new Keyboard(
            List.of("manager1"), List.of(MANAGER_PASSWORD),
            "Choice: ", new IOError(new IOException("interrupted"))
        );

        assertClosedCleanly(run(keyboard));
    }
}