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
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.revature.expensemanager.dao.ReportDao;
import com.revature.expensemanager.services.ReportService;
import com.revature.expensemanager.validation.InputValidator.DateRange;

/*
FR-M3, VR-07: Report totals and the date filter, cases TC-MGR-029 to TC-MGR-034 
(TC-MGR-032 and TC-MGR-034 also have unit tests in DateFilterUnitTest).

The real ReportService, ReportDao and Menu run against a real temporary SQLite 
database (schema.sql). Only the keyboard input and the screen are replaced. 
*/

@Tag("integration")
class ReportIntegrationTest {

    private static final String DATE_FORMAT_RULE = "Dates must be in YYYY-MM-DD format.";
    private static final String DATE_ORDER_RULE = "The start date must be on or before the end date.";

    @TempDir
    Path tempFolder;

    private Connection connection;
    private ReportService reports;
    private User manager;

    private static ReportRow row(
        String name, int count, String total, String approved
    ) {
        return new ReportRow(
            name, count, new BigDecimal(total), new BigDecimal(approved)
        );
    }

    private static DateRange range(String start, String end) {
        return new DateRange(LocalDate.parse(start), LocalDate.parse(end));
    }

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection(
            "jdbc:sqlite:" + tempFolder.resolve("manager-report.db")
        );
        execute("PRAGMA foreign_keys = ON");

        createSchema();

        execute("""
            INSERT INTO users (id, username, password, role, full_name)
            VALUES
                (1, 'alice', 'test123', 'employee', 'Alice'),
                (2, 'bob', 'test123', 'employee', 'Bob'),
                (10, 'manager1', 'test123', 'manager', 'Manager One')
            """);
        execute("""
            INSERT INTO categories (id, name)
            VALUES (1, 'Travel'), (2, 'Meals')
            """);

        addExpense(1, 1, 1, 10.00, "2026-09-30", "pending");
        addExpense(2, 1, 2, 20.00, "2026-10-01", "approved");
        addExpense(3, 2, 1, 30.00, "2026-10-15", "pending");
        addExpense(4, 2, 2, 40.00, "2026-10-15", "denied");
        addExpense(5, 1, 1, 50.00, "2026-10-16", "approved");
        addExpense(6, 2, 2, 60.00, "2026-10-31", "approved");
        addExpense(7, 1, 1, 70.00, "2026-11-01", "pending");
        addExpense(8, 2, 1, 5.00, "2026-08-12", "pending");

        manager = new User(10, "manager1", "manager", "Manager One");
        reports = new ReportService(new ReportDao(connection));
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
            .replace("﻿", "");

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
        String date, String status
    ) throws Exception {
        try (var statement = connection.prepareStatement("""
            INSERT INTO expenses
                (id, user_id, category_id, amount, description,
                 expense_date, created_at)
            VALUES (?, ?, ?, ?, 'Test expense', ?, '2026-11-10 12:00:00')""")) {
            statement.setInt(1, id);
            statement.setInt(2, employeeId);
            statement.setInt(3, categoryId);
            statement.setDouble(4, amount);
            statement.setString(5, date);
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

    private int countOccurrences(String text, String value) {
        return text.split(java.util.regex.Pattern.quote(value), -1).length - 1;
    }

    // TC-MGR-030  No date filter: every expense is counted
    @Test
    @DisplayName("TC-MGR-030 / FR-M3: with no date range every expense is counted, across all months")
    void reportWithoutDateRangeCountsEveryExpense() throws Exception {
        List<ReportRow> byEmployee =
            reports.report(manager, ReportType.EMPLOYEE, null);
        List<ReportRow> byCategory =
            reports.report(manager, ReportType.CATEGORY, null);
        assertEquals(
            List.of(
                row("Alice", 4, "150.00", "70.00"),
                row("Bob", 4, "135.00", "60.00")
            ),
            byEmployee
        );
        assertEquals(
            List.of(
                row("Meals", 3, "120.00", "80.00"),
                row("Travel", 5, "165.00", "50.00")
            ),
            byCategory
        );

        // All 8 expenses appear in each report, and nothing is dropped.
        assertEquals(8, byEmployee.stream().mapToInt(ReportRow::count).sum());
        assertEquals(8, byCategory.stream().mapToInt(ReportRow::count).sum());
    }

    @Test
    @DisplayName("TC-MGR-030 / FR-M3: answering n to the date range prints the report for all dates")
    void menuWithoutDateRangeShowsAllExpenses() {
        String output = runMenu("3\n1\nn\nn\n0\n");
        assertTrue(output.contains("Spending by employee"));
        assertFalse(output.contains("Spending by employee ("));
        assertTrue(output.contains("Alice"));
        assertTrue(output.contains("150.00"));
        assertTrue(output.contains("Bob"));
        assertTrue(output.contains("135.00"));
        assertFalse(output.contains("No data for the selected criteria."));
    }

    @Test
    @DisplayName("TC-MGR-030 / FR-M3: the category report with no date range counts every expense too")
    void menuCategoryReportWithoutDateRangeShowsAllExpenses() {
        String output = runMenu("3\n2\nn\nn\n0\n");
        assertTrue(output.contains("Spending by category"));
        assertFalse(output.contains("Spending by category ("));
        assertTrue(output.contains("Meals"));
        assertTrue(output.contains("120.00"));   // expenses 2, 4 and 6
        assertTrue(output.contains("Travel"));
        assertTrue(output.contains("165.00"));   // expenses 1, 3, 5, 7 and 8 (August to November)
        assertFalse(output.contains("No data for the selected criteria."));
    }

    // TC-MGR-031  Valid date range: both ends are included
    static Stream<Arguments> inclusiveRanges() {
        return Stream.of(
            Arguments.of(
                "TC-MGR-031-whole-october", "2026-10-01", "2026-10-31",
                List.of(
                    row("Alice", 2, "70.00", "70.00"),
                    row("Bob", 3, "130.00", "60.00")
                )
            ),
            Arguments.of(
                "TC-MGR-031-end-day-included", "2026-10-01", "2026-10-15",
                List.of(
                    row("Alice", 1, "20.00", "20.00"),
                    row("Bob", 2, "70.00", "0.00")
                )
            ),
            Arguments.of(
                "TC-MGR-031-start-day-included", "2026-10-16", "2026-10-31",
                List.of(
                    row("Alice", 1, "50.00", "50.00"),
                    row("Bob", 1, "60.00", "60.00")
                )
            ),
            Arguments.of(
                "TC-MGR-031-09-30-to-10-01",
                "2026-09-30", "2026-10-01",
                List.of(row("Alice", 2, "30.00", "20.00"))
            ),
            Arguments.of(
                "TC-MGR-031-10-31-to-11-01",
                "2026-10-31", "2026-11-01",
                List.of(
                    row("Alice", 1, "70.00", "0.00"),
                    row("Bob", 1, "60.00", "60.00")
                )
            )
        );
    }

    @Tag("boundary")
    @DisplayName("TC-MGR-031 / VR-07: the range is inclusive, so the first and last day count and the days outside do not")
    @ParameterizedTest(name = "{0}")
    @MethodSource("inclusiveRanges")
    void dateRangeIncludesItsFirstAndLastDay(
        String caseId, String start, String end, List<ReportRow> expected
    ) throws Exception {
        assertEquals(
            expected,
            reports.report(manager, ReportType.EMPLOYEE, range(start, end))
        );
    }

    @Tag("boundary")
    @DisplayName("TC-MGR-031 / VR-07: October includes 10-01 and 10-31 and excludes 09-30 and 11-01, in both reports")
    @Test
    void octoberRangeExcludesTheDaysOutsideIt() throws Exception {
        DateRange october = range("2026-10-01", "2026-10-31");
        assertEquals(
            List.of(
                row("Alice", 2, "70.00", "70.00"),
                row("Bob", 3, "130.00", "60.00")
            ),
            reports.report(manager, ReportType.EMPLOYEE, october)
        );
        // 09-30 (Travel 10.00), 11-01 (Travel 70.00) and August (Travel 5.00) are left out of Travel.
        assertEquals(
            List.of(
                row("Meals", 3, "120.00", "80.00"),
                row("Travel", 2, "80.00", "50.00")
            ),
            reports.report(manager, ReportType.CATEGORY, october)
        );
    }

    @Tag("boundary")
    @DisplayName("TC-MGR-031 / VR-07: the screen shows the range in its heading and only that month's totals")
    @Test
    void menuShowsTheFilteredReport() {
        String output = runMenu("3\n1\ny\n2026-10-01\n2026-10-31\nn\n0\n");
        assertTrue(output.contains(
            "Spending by employee (2026-10-01 to 2026-10-31)"
        ));
        assertTrue(output.contains("70.00"));    // Alice: expenses 2 and 5
        assertTrue(output.contains("130.00"));   // Bob: expenses 3, 4 and 6
        assertFalse(output.contains("150.00"));  // Alice's all-dates total
        assertFalse(output.contains("135.00"));  // Bob's all-dates total
    }

    // TC-MGR-033  Start date equals end date
    @Tag("boundary")
    @DisplayName("TC-MGR-033 / VR-07: a one-day range counts only that day (10-15 in, 10-16 out)")
    @Test
    void oneDayRangeCountsOnlyThatDay() throws Exception {
        DateRange oneDay = range("2026-10-15", "2026-10-15");
        assertEquals(
            List.of(row("Bob", 2, "70.00", "0.00")),
            reports.report(manager, ReportType.EMPLOYEE, oneDay)
        );
        assertEquals(
            List.of(
                row("Meals", 1, "40.00", "0.00"),
                row("Travel", 1, "30.00", "0.00")
            ),
            reports.report(manager, ReportType.CATEGORY, oneDay)
        );
    }

    @Tag("boundary")
    @DisplayName("TC-MGR-033 / VR-07: the screen accepts a start date equal to the end date")
    @Test
    void menuAcceptsAStartDateEqualToTheEndDate() {
        String output = runMenu("3\n1\ny\n2026-10-15\n2026-10-15\nn\n0\n");
        assertTrue(output.contains(
            "Spending by employee (2026-10-15 to 2026-10-15)"
        ));
        assertTrue(output.contains("Bob"));
        assertTrue(output.contains("70.00"));
        assertFalse(output.contains(DATE_ORDER_RULE));
        // Asked once: the first pair of dates was accepted.
        assertEquals(1, countOccurrences(output, "Start date (YYYY-MM-DD):"));
    }

    // TC-MGR-032  Start date after end date
    @Tag("negative")
    @DisplayName("TC-MGR-032 / VR-07: a start date after the end date is refused and the screen asks again")
    @Test
    void startDateAfterEndDateAsksAgain() {
        String output = runMenu(
            "3\n1\ny\n2026-10-31\n2026-10-01\n2026-10-01\n2026-10-31\nn\n0\n"
        );
        assertTrue(output.contains(DATE_ORDER_RULE));
        assertEquals(2, countOccurrences(output, "Start date (YYYY-MM-DD):"));
        // The report is only printed for the corrected range.
        assertTrue(output.contains(
            "Spending by employee (2026-10-01 to 2026-10-31)"
        ));
        assertFalse(output.contains("2026-10-31 to 2026-10-01"));
    }

    // TC-MGR-034  Invalid date format
    static Stream<Arguments> badDatePairs() {
        return Stream.of(
            Arguments.of("text-start", "abc", "2026-10-31"),
            Arguments.of("text-end", "2026-10-01", "tomorrow"),
            Arguments.of("slashes", "10/01/2026", "10/31/2026"),
            Arguments.of("day-first", "01-10-2026", "31-10-2026"),
            Arguments.of("impossible-month", "2026-13-01", "2026-10-31"),
            Arguments.of("impossible-day", "2026-02-30", "2026-03-01"),
            Arguments.of("empty", "", "")
        );
    }

    @Tag("negative")
    @DisplayName("TC-MGR-034 / VR-07: an invalid date format is refused with the format note and the screen asks again")
    @ParameterizedTest(name = "{0}")
    @MethodSource("badDatePairs")
    void invalidDateFormatAsksAgain(
        String caseId, String start, String end
    ) {
        String output = runMenu(
            "3\n1\ny\n" + start + "\n" + end + "\n"
            + "2026-10-01\n2026-10-31\nn\n0\n"
        );
        assertTrue(output.contains(DATE_FORMAT_RULE));
        assertEquals(2, countOccurrences(output, "Start date (YYYY-MM-DD):"));
        assertTrue(output.contains(
            "Spending by employee (2026-10-01 to 2026-10-31)"
        ));
        assertFalse(output.contains("Unexpected error"));
    }

    // TC-MGR-029  No matching records. The Manager App has no filter for
    // a single employee or category: both reports list every employee or
    // category that has expenses, so "no matching records" means no
    // expenses at all, or none inside the chosen date range.
    @Tag("negative")
    @DisplayName("TC-MGR-029 / FR-M3: a range with no expenses gives an empty report")
    @Test
    void rangeWithNoExpensesGivesAnEmptyReport() throws Exception {
        DateRange nothingHere = range("2027-01-01", "2027-01-31");
        assertEquals(
            List.of(),
            reports.report(manager, ReportType.EMPLOYEE, nothingHere)
        );
        assertEquals(
            List.of(),
            reports.report(manager, ReportType.CATEGORY, nothingHere)
        );
    }

    static Stream<Arguments> criteriaWithNoRecords() {
        return Stream.of(
            Arguments.of("employee-report-future-range", "1", "employee", "2027-01-01", "2027-01-31"),
            Arguments.of("category-report-future-range", "2", "category", "2027-01-01", "2027-01-31"),
            Arguments.of("employee-report-gap-between-expenses", "1", "employee", "2026-09-01", "2026-09-29"),
            Arguments.of("category-report-gap-between-expenses", "2", "category", "2026-09-01", "2026-09-29"),
            Arguments.of("employee-report-before-all-expenses", "1", "employee", "2020-01-01", "2020-12-31"),
            Arguments.of("employee-report-one-day-with-no-expenses", "1", "employee", "2026-10-20", "2026-10-20")
        );
    }

    @Tag("negative")
    @DisplayName("TC-MGR-029 / FR-M3: criteria with no matching expenses print the no-data message, in both reports")
    @ParameterizedTest(name = "{0}")
    @MethodSource("criteriaWithNoRecords")
    void noMatchingExpensesPrintsTheNoDataMessage(
        String caseId, String choice, String reportName,
        String start, String end
    ) {
        String output = runMenu(
            "3\n" + choice + "\ny\n" + start + "\n" + end + "\n0\n"
        );
        assertTrue(output.contains(
            "Spending by " + reportName + " (" + start + " to " + end + ")"
        ));
        assertTrue(output.contains("No data for the selected criteria."));
        assertFalse(output.contains("Export this report to CSV"));
        assertFalse(output.contains("Unexpected error"));
        assertTrue(output.contains("Exit."));
    }

    @Tag("negative")
    @DisplayName("TC-MGR-029 / FR-M3: with no expenses in the database at all the report says there is no data")
    @Test
    void emptyDatabaseGivesTheNoDataMessage() throws Exception {
        execute("DELETE FROM approvals");
        execute("DELETE FROM expenses");
        assertEquals(List.of(), reports.report(manager, ReportType.EMPLOYEE, null));
        assertEquals(List.of(), reports.report(manager, ReportType.CATEGORY, null));
        String output = runMenu("3\n1\nn\n0\n");
        assertTrue(output.contains("No data for the selected criteria."));
        assertFalse(output.contains("Export this report to CSV"));
        assertFalse(output.contains("Unexpected error"));
        assertTrue(output.contains("Exit."));
    }

}