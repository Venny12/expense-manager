package com.revature.expensemanager;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.revature.expensemanager.exceptions.AuthenticationException;
import com.revature.expensemanager.exceptions.AuthorizationException;
import com.revature.expensemanager.exceptions.DatabaseException;
import com.revature.expensemanager.exceptions.ValidationException;
import com.revature.expensemanager.services.AuthenticationService;

/*
FR-S3: Passwords are never logged or printed, case TC-MGR-010.
The real AuthenticationService runs against a real temporary SQLite database. 
Logging is written to a temporary file, set up the way Main sets it up, 
and the file and the screen output are searched afterwards.
*/

@Tag("integration")
class LoggingIntegrationTest {

    private static final String CORRECT_PASSWORD = "CorrectSecret123!";
    private static final String WRONG_PASSWORD = "S3cretPass!";

    @TempDir
    Path tempFolder;

    private Connection connection;
    private AuthenticationService service;

    private Path logFile;
    private FileHandler fileHandler;
    private Handler[] originalHandlers;
    private Level originalLevel;
    private boolean originalUseParentHandlers;

    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream screen;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection(
            "jdbc:sqlite:" + tempFolder.resolve("login-logging.db")
        );

        createSchema();

        execute("""
            INSERT INTO users (id, username, password, role, full_name)
            VALUES
                (10, 'manager1', '%s', 'manager', 'Manager One'),
                (1, 'employee1', 'EmployeePass123!', 'employee', 'Alice')
            """.formatted(CORRECT_PASSWORD));

        service = new AuthenticationService(connection);

        // Log to a temporary file, as Main does, and keep the screen quiet.
        logFile = tempFolder.resolve("manager_app.log");
        fileHandler = new FileHandler(logFile.toString(), true);
        fileHandler.setFormatter(new SimpleFormatter());

        Logger rootLogger = Logger.getLogger("");
        originalHandlers = rootLogger.getHandlers();
        originalLevel = rootLogger.getLevel();
        originalUseParentHandlers = rootLogger.getUseParentHandlers();

        for (Handler handler : originalHandlers) {
            rootLogger.removeHandler(handler);
        }

        rootLogger.setLevel(Level.INFO);
        rootLogger.addHandler(fileHandler);

        // Everything printed to the screen during a test is collected.
        originalOut = System.out;
        originalErr = System.err;
        screen = new ByteArrayOutputStream();
        PrintStream capture = new PrintStream(screen, true, StandardCharsets.UTF_8);
        System.setOut(capture);
        System.setErr(capture);
    }

    @AfterEach
    void tearDown() throws Exception {
        System.setOut(originalOut);
        System.setErr(originalErr);

        Logger rootLogger = Logger.getLogger("");
        rootLogger.removeHandler(fileHandler);
        fileHandler.close();

        for (Handler handler : originalHandlers) {
            rootLogger.addHandler(handler);
        }

        rootLogger.setLevel(originalLevel);
        rootLogger.setUseParentHandlers(originalUseParentHandlers);

        connection.close();
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
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String logText() throws Exception {
        fileHandler.flush();
        return Files.readString(logFile, StandardCharsets.UTF_8);
    }

    private String screenText() {
        return screen.toString(StandardCharsets.UTF_8);
    }

    private void assertPasswordIsNotShown(String password) throws Exception {
        assertFalse(
            logText().contains(password),
            "The password was written to the log."
        );
        assertFalse(
            screenText().contains(password),
            "The password was printed on screen."
        );
    }

    // TC-MGR-010
    @Tag("negative")
    @DisplayName("TC-MGR-010 / FR-S3: a wrong password is not written to the log or the screen")
    @Test
    void wrongPasswordIsNeverLoggedOrPrinted() throws Exception {
        assertThrows(
            AuthenticationException.class,
            () -> service.login("manager1", WRONG_PASSWORD)
        );
        // The failure itself is logged, so a clean search means something.
        assertTrue(logText().contains("Invalid login attempt."));
        assertPasswordIsNotShown(WRONG_PASSWORD);
    }

    @Tag("negative")
    @DisplayName("TC-MGR-010 / FR-S3: neither password is logged when a failure is followed by a success")
    @Test
    void failureThenSuccessNeverLogsEitherPassword() throws Exception {
        assertThrows(
            AuthenticationException.class,
            () -> service.login("manager1", WRONG_PASSWORD)
        );
        User user = service.login("manager1", CORRECT_PASSWORD);

        assertEquals(10, user.id());
        assertTrue(logText().contains("Invalid login attempt."));
        assertTrue(logText().contains("Manager login successful: user_id=10"));
        assertPasswordIsNotShown(WRONG_PASSWORD);
        assertPasswordIsNotShown(CORRECT_PASSWORD);
    }

    @Tag("negative")
    @DisplayName("TC-MGR-010 / FR-S3: a database failure during login does not log the password")
    @Test
    void databaseFailureDuringLoginNeverLogsThePassword() throws Exception {
        connection.close();

        assertThrows(
            DatabaseException.class,
            () -> service.login("manager1", WRONG_PASSWORD)
        );

        assertTrue(logText().contains("Database operation failed during login."));
        assertPasswordIsNotShown(WRONG_PASSWORD);
    }

}