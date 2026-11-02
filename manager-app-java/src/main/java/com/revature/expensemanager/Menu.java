package com.revature.expensemanager;

import java.sql.Connection;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.Console;
import java.util.function.Function;
import com.revature.expensemanager.dao.ExpenseDao;
import com.revature.expensemanager.services.ExpenseService;

import com.revature.expensemanager.exceptions.ExpenseManagerException;
import com.revature.expensemanager.validation.InputValidator;

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

    /** Placeholder until T5P1-14 (list pending expenses) is merged. */
       /** FR-M1: display all pending expenses, oldest first. */
    private void listPending() {
        ExpenseService service = new ExpenseService(
            new ExpenseDao(connection)
        );

        var expenses = service.listPending(user);

        if (expenses.isEmpty()) {
            System.out.println("No pending expenses.");
            return;
        }

        for (PendingExpense expense : expenses) {
            System.out.println();
            System.out.println("ID: " + expense.id());
            System.out.println("Employee: " + expense.employeeName());
            System.out.println("Date: " + expense.expenseDate());
            System.out.println("Category: " + expense.category());
            System.out.println("Amount: " + expense.amount().toPlainString());
            System.out.println("Description: " + expense.description());
        }
    }

    /** Placeholder until T5P1-16 (approve and deny) is merged. */
    private void reviewExpense() {
        System.out.println("Review an expense is not available yet.");
    }

    /** Placeholder until T5P1-17 (spending reports) is merged. */
    private void reports() {
        System.out.println("Reports are not available yet.");
    }
}