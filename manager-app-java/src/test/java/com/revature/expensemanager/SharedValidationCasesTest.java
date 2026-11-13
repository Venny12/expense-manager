package com.revature.expensemanager;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.revature.expensemanager.exceptions.AuthenticationException;
import com.revature.expensemanager.exceptions.ValidationException;
import com.revature.expensemanager.services.AuthenticationService;
import com.revature.expensemanager.validation.InputValidator;

/*
Shared validation cases (Section 9.5), Java side.

The same shared-test-data/validation_cases.csv is run by the Python suite 
(test_shared_validation_cases.py). Both apps must give the same answers.

Fields:
expense_id   VR-08  expected true = accepted
menu_choice  VR-09  menu with choices 0-3 (valid in both apps)
login        FR-A1  input is "username|password"; expected true = the empty-input 
                    check passes (the request then goes on to the database),
                    false = rejected as empty input
 */

@Tag("unit")
class SharedValidationCasesTest {
    private static final int MAX_MENU_CHOICE = 3;
    @Test
    void sharedFileIsNotEmpty() throws IOException {
        assertTrue(readCases().count() >= 20);
    }

    @ParameterizedTest(name = "{0} [{1}] -> {2}")
    @MethodSource("readCases")
    void javaAppAgreesWithSharedCase(
        String field, String input, boolean expected
    ) throws Exception {
        assertEquals(expected, acceptedByJavaApp(field, input));
    }

    private static boolean acceptedByJavaApp(String field, String input)
            throws Exception {
        return switch (field) {
            case "expense_id" -> InputValidator.isValidExpenseId(input);
            case "menu_choice" ->
                InputValidator.isValidMenuChoice(input, MAX_MENU_CHOICE);
            case "login" -> passesEmptyLoginCheck(input);
            default -> throw new IllegalArgumentException(
                "Unknown field in shared file: " + field);
        };
    }

    private static boolean passesEmptyLoginCheck(String input)
            throws Exception {
        String[] parts = input.split("\\|", -1);

        // An empty users table: a login that passes the empty-input check
        // is then refused as "invalid username or password".
        try (Connection connection =
                 DriverManager.getConnection("jdbc:sqlite::memory:")) {
            connection.createStatement().execute("""
                CREATE TABLE users (id INTEGER PRIMARY KEY, username TEXT,
                    password TEXT, role TEXT, full_name TEXT)
                """);
            try {
                new AuthenticationService(connection)
                    .login(parts[0], parts[1]);
            } catch (ValidationException error) {
                return false;
            } catch (AuthenticationException error) {
                return true;
            }
        }
        return true;
    }

    static Stream<Arguments> readCases() throws IOException {
        Path folder = Path.of("").toAbsolutePath();
        Path file = null;

        while (folder != null && file == null) {
            Path candidate =
                folder.resolve("shared-test-data/validation_cases.csv");
            if (Files.exists(candidate)) {
                file = candidate;
            }
            folder = folder.getParent();
        }

        assertNotNull(file, "Could not find validation_cases.csv");

        List<Arguments> cases = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            List<String> cells = splitCsvLine(line);
            cases.add(Arguments.of(
                cells.get(0), cells.get(1), cells.get(2).equals("true")
            ));
        }
        return cases.stream();
    }

    // Small CSV reader: handles "quoted" cells, which hold spaces or nothing.
    private static List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }
}