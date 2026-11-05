package com.revature.expensemanager;

import java.sql.Connection;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.Console;
import java.util.function.Function;
import java.util.List;
import java.util.ArrayList;
import com.revature.expensemanager.dao.ExpenseDao;
import com.revature.expensemanager.services.ExpenseService;
import com.revature.expensemanager.dao.ReportDao;
import com.revature.expensemanager.services.ReportService;
import com.revature.expensemanager.validation.InputValidator.DateRange;

import com.revature.expensemanager.exceptions.ExpenseManagerException;
import com.revature.expensemanager.validation.InputValidator;
import com.revature.expensemanager.validation.CommentValidator;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;

import com.revature.expensemanager.services.CsvExportService;

// Manager menu
public class Menu {

    private static final Logger LOGGER = Logger.getLogger(Menu.class.getName());

    private static final int MAX_CHOICE = 3;

    private static final String MENU = """

        === Expense Manager (Manager) ===
        1. List pending expenses
        2. Review an expense
        3. Reports
        0. Exit""";

    private final User user;
    private final Connection connection;

    private final Function<String, String> input;

     // Reads input from the console, the same way the login prompts do.
    public Menu(User user, Connection connection, Console console) {
        this(user, connection, prompt -> console.readLine("%s", prompt));
    }

    // Reads input from a Scanner. Used by tests with scripted input.
    public Menu(User user, Connection connection, Scanner scanner) {
        this(user, connection, prompt -> {
            System.out.print(prompt);
            return scanner.hasNextLine() ? scanner.nextLine() : null;
        });
    }

    private Menu(User user, Connection connection,
                 Function<String, String> input) {
        this.user = user;
        this.connection = connection;
        this.input = input;
    }

    // Shows the menu until the user chooses 0.
    public void run() {
        while (true) {
            System.out.println(MENU);
            String line = input.apply("Choice: ");
            //System.out.print("Choice: ");

            if (line == null) {
                System.out.println();
                System.out.println("Application closed.");
                return;
            }

            int choice;

            try {
                choice = InputValidator.parseMenuChoice(line, MAX_CHOICE);
            } catch (ExpenseManagerException error) {
                System.out.println(error.getMessage());
                continue;
            }

            if (choice == 0) {
                System.out.println("Exit.");
                return;
            }

            try {
                switch (choice) {
                    case 1 -> listPending();
                    case 2 -> reviewExpense();
                    case 3 -> reports();
                    default -> System.out.println("Please enter a number from the menu.");
                }
            } catch (ExpenseManagerException error) {
                System.out.println(error.getMessage());
            } catch (Exception error) {
                LOGGER.log(
                    Level.SEVERE,
                    "Unexpected error in menu option " + choice,
                    error
                );
                System.out.println("Unexpected error. See log.");
            }
        }
    }

       /* FR-M1: display all pending expenses, oldest first. */
    private void listPending() {
        ExpenseService service = new ExpenseService(
            new ExpenseDao(connection)
        );

        var expenses = service.listPending(user);

        if (expenses.isEmpty()) {
            System.out.println("No pending expenses.");
            return;
        }

        List<String[]> rows = new ArrayList<>();

        for (PendingExpense expense : expenses) {
            rows.add(new String[] {
                String.valueOf(expense.id()),
                expense.employeeName(),
                expense.expenseDate(),
                expense.category(),
                expense.amount().toPlainString(),
                expense.description()
            });
        }

        System.out.println();
        System.out.println(TableFormatter.format(
            new String[] {"ID", "Employee", "Date", "Category", "Amount", "Description"},
            rows,
            new boolean[] {false, false, false, false, true, false},
            new int[] {0, 0, 0, 0, 0, 40}
        ));

    }

    // approve, deny pending expense with a comment.
    private void reviewExpense() {
      ExpenseService service = new ExpenseService(new ExpenseDao(connection));

        Integer expenseId = askExpenseId();
        if (expenseId == null) {
            return;
        }
        // Stop here if the expense is missing or already decided.
        service.requirePending(user, expenseId);

        String action = askAction();
        if (action == null) {
            return;
        }

        if (action.equals("a")) {
            String comment = askComment(
                "Comment (optional, Enter to skip): ", false
            );
            if (comment == null) {
                return;
            }

            service.approve(user, expenseId, comment);
            System.out.println("Expense " + expenseId + " approved.");
        } else {
            String comment = askComment(
                "Comment (required, 5 to 200 characters): ", true
            );
            if (comment == null) {
                return;
            }

            service.deny(user, expenseId, comment);
            System.out.println("Expense " + expenseId + " denied.");
        }
    }
// Asks until the expense ID is valid (VR-08). Returns null if input ends.
    private Integer askExpenseId() {
        while (true) {
            String line = input.apply("Expense ID: ");
            if (line == null) {
                return null;
            }

            try {
                return InputValidator.parseExpenseId(line);
            } catch (ExpenseManagerException error) {
                System.out.println(error.getMessage());
            }
        }
    }

    // Asks until the answer is a or d. Returns null if input ends.
    private String askAction() {
        while (true) {
            String line = input.apply("Approve (a) or deny (d): ");
            if (line == null) {
                return null;
            }

            String answer = line.trim().toLowerCase();
            if (answer.equals("a") || answer.equals("d")) {
                return answer;
            }

            System.out.println("Please enter a to approve or d to deny.");
        }
    }

    // Asks until the comment is valid (VR-05 or VR-06). Returns null if input ends.
    private String askComment(String prompt, boolean required) {
        while (true) {
            String line = input.apply(prompt);
            if (line == null) {
                return null;
            }

            try {
                return required
                    ? CommentValidator.validateDenial(line)
                    : CommentValidator.validateApproval(line);
            } catch (ExpenseManagerException error) {
                System.out.println(error.getMessage());
            }
        }
    }

    private void reports() {
        ReportService service = new ReportService(new ReportDao(connection));
        System.out.println();
        System.out.println("--- Reports ---");
        System.out.println("1. Spending by employee");
        System.out.println("2. Spending by category");
        System.out.println("0. Back");

        Integer choice = askReportChoice();
        if (choice == null || choice == 0) {
            return;
        }

        ReportType type = choice == 1 ? ReportType.EMPLOYEE : ReportType.CATEGORY;

        Boolean useRange = askYesNo("Limit to a date range? (y/n): ");
        if (useRange == null) {
            return;
        }

        DateRange range = null;
        if (useRange) {
            range = askDateRange();
            if (range == null) {
                return;
            }
        }

        List<ReportRow> rows = service.report(user, type, range);
        printReport(type, range, rows);

                if (rows.isEmpty()) {
            return;
        }

        Boolean export = askYesNo("Export this report to CSV? (y/n): ");

        if (!Boolean.TRUE.equals(export)) {
            return;
        }

        CsvExportService exporter = new CsvExportService(
            Path.of("..", "reports").toAbsolutePath().normalize(),
            Clock.systemDefaultZone()
        );

        try {
            Path savedFile = exporter.export(user, type, rows);
            System.out.println("Report saved to: " + savedFile);

        } catch (IOException | SecurityException error) {
            LOGGER.log(Level.SEVERE, "CSV export failed.", error);
            System.out.println(
                "Could not save the CSV file. "
                + "Check folder permissions or try again."
            );
        }
    }

    private void printReport(ReportType type, DateRange range,
                             List<ReportRow> rows) {
        System.out.println();
        System.out.println(
            "Spending by " + type.columnTitle().toLowerCase()
            + (range == null ? "" : " (" + range.start() + " to " + range.end() + ")")
        );

        if (rows.isEmpty()) {
            System.out.println("No data for the selected criteria.");
            return;
        }

        List<String[]> lines = new ArrayList<>();
        for (ReportRow row : rows) {
            lines.add(new String[] {
                row.name(),
                String.valueOf(row.count()),
                row.totalAmount().toPlainString(),
                row.approvedAmount().toPlainString()
            });
        }

        System.out.println(TableFormatter.format(
            new String[] {type.columnTitle(), "Count", "Total Amount", "Total Approved"},
            lines,
            new boolean[] {false, true, true, true},
            new int[] {0, 0, 0, 0}
        ));
    }

    // Asks until the answer is 0, 1 or 2. Returns null if input ends.
    private Integer askReportChoice() {
        while (true) {
            String line = input.apply("Choice: ");
            if (line == null) {
                return null;
            }

            try {
                return InputValidator.parseMenuChoice(line, 2);
            } catch (ExpenseManagerException error) {
                System.out.println(error.getMessage());
            }
        }
    }

    // Asks until the answer is y or n. Returns null if input ends.
    private Boolean askYesNo(String prompt) {
        while (true) {
            String line = input.apply(prompt);
            if (line == null) {
                return null;
            }

            String answer = line.trim().toLowerCase();
            if (answer.equals("y")) {
                return true;
            }
            if (answer.equals("n")) {
                return false;
            }

            System.out.println("Please enter y or n.");
        }
    }

    // Asks for both dates until they form a valid range (VR-07).
    private DateRange askDateRange() {
        while (true) {
            String start = input.apply("Start date (YYYY-MM-DD): ");
            if (start == null) {
                return null;
            }

            String end = input.apply("End date (YYYY-MM-DD): ");
            if (end == null) {
                return null;
            }

            try {
                return InputValidator.parseDateRange(start, end);
            } catch (ExpenseManagerException error) {
                System.out.println(error.getMessage());
            }
        }
    }

        
}