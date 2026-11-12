package com.revature.expensemanager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.PrintStream;
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

/**
 * FR-A1, FR-M1: Scripted session, case TC-MGR-039.
 *
 * Feed input: login, list pending expenses, exit.
 * Expected: the output shows the greeting, the pending table and the goodbye,
 * and the exit code is 0.
 *
 * The real Main, AuthenticationService and Menu run against a real temporary
 * SQLite database (schema.sql). Only the keyboard (a scripted Console), the
 * screen and the log folder are replaced. Main.run(console, logFolder,
 * databasePath) is the package-private entry point that main() also uses.
 */
@Tag("integration")
class ManagerMenuFlowIntegrationTest {

    private static final String MANAGER_PASSWORD = "ManagerPass123!";

    @TempDir
    Path tempFolder;

    private Path databaseFile;

    private PrintStream originalOut;
    private ByteArrayOutputStream screen;

    private Handler[] originalHandlers;
    private boolean originalUseParentHandlers;

    /**
     * A scripted keyboard. readLine answers come from one list (the username
     * prompt and the menu prompts, in the order the app asks), readPassword
     * answers from another. The prompts the app showed are recorded.
     */
    private static final class Keyboard {
        private final Deque<String> lines;
        private final Deque<String> passwords;
        private final List<String> prompts = new ArrayList<>();

        Keyboard(List<String> lines, List<String> passwords) {
            this.lines = new ArrayDeque<>(lines);
            this.passwords = new ArrayDeque<>(passwords);
        }

        Console console() {
            return mock(Console.class, invocation -> {
                String method = invocation.getMethod().getName();
                Object[] arguments = invocation.getArguments();

                // readLine("Username: ") or readLine("%s", "Choice: "):
                // the prompt is always the last argument.
                if (method.equals("readLine") && arguments.length > 0) {
                    prompts.add((String) arguments[arguments.length - 1]);
                    return lines.pollFirst();
                }

                if (method.equals("readPassword") && arguments.length > 0) {
                    prompts.add((String) arguments[arguments.length - 1]);
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

            String sql = Files.readString(schema, StandardCharsets.UTF_8)
                .replace("\uFEFF", "");

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

    // TC-MGR-039  Scripted session: login, pending list, exit

    @DisplayName("TC-MGR-039 / FR-A1, FR-M1: login, list pending expenses, exit - greeting, table, goodbye, exit code 0")
    @Test
    void scriptedSessionLoginPendingListExit() {
        // readLine answers: username, menu choice 1 (pending), menu choice 0 (exit).
        Keyboard keyboard = new Keyboard(
            List.of("manager1", "1", "0"), List.of(MANAGER_PASSWORD)
        );

        int exitCode = Main.run(
            keyboard.console(),
            tempFolder.resolve("logs"),
            () -> databaseFile.toString()
        );

        String output = screen.toString(StandardCharsets.UTF_8);

        assertEquals(0, exitCode);

        // Greeting.
        assertTrue(output.contains("Welcome, Manager One"));

        // The pending table: both pending expenses, no decided one.
        assertTrue(output.contains("Alice"));
        assertTrue(output.contains("Taxi to airport"));
        assertTrue(output.contains("25.50"));
        assertTrue(output.contains("Bob"));
        assertTrue(output.contains("Team lunch"));
        assertTrue(output.contains("12.50"));
        assertFalse(output.contains("Coffee"));

        // Goodbye.
        assertTrue(output.contains("Exit."));

        // In the order of the session; the oldest expense is listed first.
        assertTrue(output.indexOf("Welcome, Manager One") < output.indexOf("Team lunch"));
        assertTrue(output.indexOf("Team lunch") < output.indexOf("Taxi to airport"));
        assertTrue(output.indexOf("Taxi to airport") < output.indexOf("Exit."));

        // The app asked exactly what a user would be asked, in this order.
        assertEquals(
            List.of("Username: ", "Password: ", "Choice: ", "Choice: "),
            keyboard.prompts
        );

        // Nothing went wrong, and the password is nowhere on screen.
        assertFalse(output.contains("Unexpected error"));
        assertFalse(output.contains("Cannot"));
        assertFalse(output.contains(MANAGER_PASSWORD));
    }
}