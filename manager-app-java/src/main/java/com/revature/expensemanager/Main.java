package com.revature.expensemanager;

import java.io.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import com.revature.expensemanager.db.Database;
import com.revature.expensemanager.exceptions.AuthenticationException;
import com.revature.expensemanager.exceptions.AuthorizationException;
import com.revature.expensemanager.exceptions.ExpenseManagerException;
import com.revature.expensemanager.exceptions.ValidationException;
import com.revature.expensemanager.services.AuthenticationService;

/** Starts the Manager App and handles login prompts. */
public class Main {

    private static final Logger LOGGER =
        Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        System.exit(run());
    }

    private static int run() {
        // Run the app from the manager-app-java folder.
        Path logFolder = Path.of("..", "logs").toAbsolutePath().normalize();

        try {
            Files.createDirectories(logFolder);

            FileHandler handler = new FileHandler(
                logFolder.resolve("manager_app.log").toString(),
                true
            );
            handler.setFormatter(new SimpleFormatter());

            Logger rootLogger = Logger.getLogger("");
            rootLogger.setUseParentHandlers(false);

            for (var existingHandler : rootLogger.getHandlers()) {
                rootLogger.removeHandler(existingHandler);
            }

            rootLogger.addHandler(handler);

        } catch (IOException | SecurityException error) {
            System.out.println("Cannot open the application log.");
            return 1;
        }

        Console console = System.console();

        if (console == null) {
            System.out.println(
                "Run this app using the java command in a terminal."
            );
            return 1;
        }

        try (
            Connection connection = Database.connect(Database.dbPath())
        ) {
            AuthenticationService service =
                new AuthenticationService(connection);

            int failures = 0;

            while (failures < 3) {
                String username = console.readLine("Username: ");
                char[] passwordCharacters =
                    console.readPassword("Password: ");

                if (username == null || passwordCharacters == null) {
                    System.out.println("Application closed.");
                    return 0;
                }

                String password = new String(passwordCharacters);
                Arrays.fill(passwordCharacters, '\0');

                try {
                    User user = service.login(username, password);

                    System.out.println("Welcome, " + user.fullName());

                    // Connect the manager menu here later.
                    return 0;

                } catch (ValidationException error) {
                    System.out.println(error.getMessage());

                } catch (AuthenticationException error) {
                    System.out.println(error.getMessage());
                    failures++;

                } catch (AuthorizationException error) {
                    System.out.println(error.getMessage());
                    return 1;
                }
            }

            System.out.println("Too many failed attempts.");
            return 1;

        } catch (ExpenseManagerException error) {
            LOGGER.severe(error.getMessage());
            System.out.println(error.getMessage());
            return 1;

        } catch (SQLException | IllegalStateException error) {
            LOGGER.severe("Cannot open or close the database.");
            System.out.println(
                "Cannot access the database. Check EM_DB_PATH and the database file."
            );
            return 1;

        } catch (Exception error) {
            LOGGER.log(
                java.util.logging.Level.SEVERE,
                "Unexpected application error.",
                error
            );
            System.out.println("Unexpected error. See log.");
            return 1;
        }
    }
}