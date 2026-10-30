package com.revature.expensemanager.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.sqlite.SQLiteConfig;

public final class Database {

    private Database() {
        // This helper class does not need objects.
    }

    public static String dbPath() {
        String path = System.getenv("EM_DB_PATH");

        if (path == null || path.isBlank()) {
            throw new IllegalStateException("EM_DB_PATH is not set.");
        }

        return path;
    }

    public static Connection connect(String path) throws SQLException {
        if (path == null || path.isBlank()) {
            throw new SQLException("Database path is empty.");
        }

        Path databasePath = Path.of(path).toAbsolutePath().normalize();

        if (!Files.isRegularFile(databasePath)) {
            throw new SQLException(
                "Database file does not exist. Run database setup first."
            );
        }

        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5000);
        config.setOpenMode(org.sqlite.SQLiteOpenMode.READWRITE);
        config.resetOpenMode(org.sqlite.SQLiteOpenMode.CREATE);

        return DriverManager.getConnection(
            "jdbc:sqlite:" + databasePath,
            config.toProperties()
        );
    }
}