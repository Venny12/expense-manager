package com.revature.expensemanager.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

import com.revature.expensemanager.User;
import com.revature.expensemanager.exceptions.AuthenticationException;
import com.revature.expensemanager.exceptions.AuthorizationException;
import com.revature.expensemanager.exceptions.DatabaseException;
import com.revature.expensemanager.exceptions.ValidationException;

/** Checks credentials and restricts access to manager accounts. */
public class AuthenticationService {

    private static final Logger LOGGER =
        Logger.getLogger(AuthenticationService.class.getName());

    private final Connection connection;

    public AuthenticationService(Connection connection) {
        this.connection = connection;
    }

    public User login(String username, String password) {
        if (username == null || username.isBlank()
                || password == null || password.isBlank()) {
            throw new ValidationException(
                "Username and password are required."
            );
        }

        String sql = """
            SELECT id, username, role, full_name
            FROM users
            WHERE username = ? AND password = ?
            """;

        try (
            PreparedStatement statement =
                connection.prepareStatement(sql)
        ) {
            statement.setString(1, username);
            statement.setString(2, password);

            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    LOGGER.warning("Invalid login attempt.");

                    throw new AuthenticationException(
                        "Invalid username or password."
                    );
                }

                if (!"manager".equals(results.getString("role"))) {
                    LOGGER.warning(
                        "Login refused: account role has no access."
                    );

                    throw new AuthorizationException(
                        "This account does not have access to this app."
                    );
                }

                User user = new User(
                    results.getInt("id"),
                    results.getString("username"),
                    results.getString("role"),
                    results.getString("full_name")
                );

                LOGGER.info(
                    "Manager login successful: user_id=" + user.id()
                );

                return user;
            }

        } catch (SQLException error) {
            LOGGER.warning("Database operation failed during login.");

            throw new DatabaseException(
                "Could not check login details.", error
            );
        }
    }
}