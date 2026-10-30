package com.revature.expensemanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.revature.expensemanager.db.Database;

public class Main {

    public static void main(String[] args) {
        String sql = "SELECT COUNT(*) AS total FROM users";

        try (
            Connection connection = Database.connect(Database.dbPath());
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet results = statement.executeQuery()
        ) {
            if (results.next()) {
                System.out.println("Database connected successfully.");
                System.out.println("Users: " + results.getInt("total"));
            }

        } catch (IllegalStateException | SQLException error) {
            System.out.println(error.getMessage());
            System.exit(1);
        }
    }
}