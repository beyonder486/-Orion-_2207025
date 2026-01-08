package com.orion;

import java.io.File;
import java.sql.*;

public class DatabaseManager {
    private static final String DB_DIR = System.getProperty("user.home") + File.separator + ".orion";
    private static final String DB_URL = "jdbc:sqlite:" + DB_DIR + File.separator + "orion.db";
    private static Connection connection;

    public static void initialize() {
        try {
            // Create .orion directory if it doesn't exist
            File dbDirectory = new File(DB_DIR);
            if (!dbDirectory.exists()) {
                dbDirectory.mkdirs();
            }

            // Establish connection
            connection = DriverManager.getConnection(DB_URL);
            System.out.println("Database connected: " + DB_URL);

            // Create tables
            createTables();
        } catch (SQLException e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createTables() throws SQLException {
        String createUsersTable = """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """;

        String createUserSessionsTable = """
            CREATE TABLE IF NOT EXISTS user_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                last_file_path TEXT,
                last_workspace TEXT,
                theme TEXT DEFAULT 'Dark',
                font_size INTEGER DEFAULT 14,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createUsersTable);
            stmt.execute(createUserSessionsTable);
            System.out.println("Database tables created successfully");
        }
    }

    public static Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(DB_URL);
            }
        } catch (SQLException e) {
            System.err.println("Failed to get database connection: " + e.getMessage());
        }
        return connection;
    }

    public static void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed");
            }
        } catch (SQLException e) {
            System.err.println("Failed to close database: " + e.getMessage());
        }
    }
}
