package com.orion;

import java.io.File;
import java.sql.*;

public class SessionService {

    public static class SessionData {
        public String lastFilePath;
        public String lastWorkspace;
        public String theme;
        public int fontSize;
        public String currentProjectId; // Active collaborative project

        public SessionData() {
            this.theme = "Dark";
            this.fontSize = 14;
        }
    }

    public static SessionData loadSession(int userId) {
        SessionData data = new SessionData();
        
        String sql = "SELECT last_file_path, last_workspace, theme, font_size, current_project_id FROM user_sessions WHERE user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    data.lastFilePath = rs.getString("last_file_path");
                    data.lastWorkspace = rs.getString("last_workspace");
                    data.theme = rs.getString("theme");
                    data.fontSize = rs.getInt("font_size");
                    data.currentProjectId = rs.getString("current_project_id");
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to load session: " + e.getMessage());
        }
        
        return data;
    }

    public static void saveSession(int userId, String lastFilePath, String lastWorkspace, String theme, int fontSize, String currentProjectId) {
        // SQLite doesn't support ON CONFLICT with foreign key, so we use UPDATE or INSERT
        try (Connection conn = DatabaseManager.getConnection()) {
            // First try to update
            String updateSql = """
                UPDATE user_sessions 
                SET last_file_path = ?, last_workspace = ?, theme = ?, font_size = ?, current_project_id = ?, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
            """;
            
            try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                pstmt.setString(1, lastFilePath);
                pstmt.setString(2, lastWorkspace);
                pstmt.setString(3, theme);
                pstmt.setInt(4, fontSize);
                pstmt.setString(5, currentProjectId);
                pstmt.setInt(6, userId);
                
                int rowsAffected = pstmt.executeUpdate();
                
                // If no rows were updated, insert a new record
                if (rowsAffected == 0) {
                    String insertSql = """
                        INSERT INTO user_sessions (user_id, last_file_path, last_workspace, theme, font_size, current_project_id)
                        VALUES (?, ?, ?, ?, ?, ?)
                    """;
                    
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setInt(1, userId);
                        insertStmt.setString(2, lastFilePath);
                        insertStmt.setString(3, lastWorkspace);
                        insertStmt.setString(4, theme);
                        insertStmt.setInt(5, fontSize);
                        insertStmt.setString(6, currentProjectId);
                        insertStmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to save session: " + e.getMessage());
        }
    }

    public static void updateLastFile(int userId, String filePath) {
        String sql = "UPDATE user_sessions SET last_file_path = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, filePath);
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to update last file: " + e.getMessage());
        }
    }

    public static void updateLastWorkspace(int userId, String workspacePath) {
        String sql = "UPDATE user_sessions SET last_workspace = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, workspacePath);
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to update last workspace: " + e.getMessage());
        }
    }
    
    public static void updateCurrentProject(int userId, String projectId) {
        String sql = "UPDATE user_sessions SET current_project_id = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, projectId);
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to update current project: " + e.getMessage());
        }
    }
}
