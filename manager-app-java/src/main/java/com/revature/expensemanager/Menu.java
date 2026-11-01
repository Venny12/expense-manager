package com.revature.expensemanager;

import java.sql.Connection;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private final Scanner scanner;

    public Menu(User user, Connection connection, Scanner scanner) {
        this.user = user;
        this.connection = connection;
        this.scanner = scanner;
    }

    // Shows the menu until the user chooses 0.
    public void run() {
        while (true) {
            System.out.println(MENU);
            System.out.print("Choice: ");

            if (!scanner.hasNextLine()) {
                System.out.println();
                System.out.println("Application closed.");
                return;
            }

            int choice;

            try {
                choice = InputValidator.parseMenuChoice(
                    scanner.nextLine(), MAX_CHOICE
                );
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
    private void listPending() {
        System.out.println("List pending expenses is not available yet.");
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