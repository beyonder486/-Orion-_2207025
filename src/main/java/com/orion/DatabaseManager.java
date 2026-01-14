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
                current_project_id TEXT,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """;
        
        String createFileSnapshotsTable = """
            CREATE TABLE IF NOT EXISTS file_snapshots (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                project_id TEXT NOT NULL,
                file_path TEXT NOT NULL,
                content TEXT NOT NULL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(project_id, file_path)
            )
        """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createUsersTable);
            stmt.execute(createUserSessionsTable);
            stmt.execute(createFileSnapshotsTable);
            
            // Add current_project_id column if it doesn't exist (for existing databases)
            try {
                stmt.execute("ALTER TABLE user_sessions ADD COLUMN current_project_id TEXT");
                System.out.println("Added current_project_id column to user_sessions table");
            } catch (SQLException e) {
                // Column already exists, ignore
            }
            
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
    
    /**
     * Save file snapshot to database (last saved state)
     */
    public static void saveFileSnapshot(String projectId, String filePath, String content) {
        String sql = "INSERT OR REPLACE INTO file_snapshots (project_id, file_path, content, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, projectId);
            pstmt.setString(2, filePath);
            pstmt.setString(3, content);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to save file snapshot: " + e.getMessage());
        }
    }
    
    /**
     * Get last saved content of a file from database
     */
    public static String getFileSnapshot(String projectId, String filePath) {
        String sql = "SELECT content FROM file_snapshots WHERE project_id = ? AND file_path = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, projectId);
            pstmt.setString(2, filePath);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("content");
            }
        } catch (SQLException e) {
            System.err.println("Failed to get file snapshot: " + e.getMessage());
        }
        return null;
    }
}
