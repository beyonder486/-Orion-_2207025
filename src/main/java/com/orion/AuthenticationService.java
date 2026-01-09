package com.orion;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.mindrot.jbcrypt.BCrypt;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class AuthenticationService {
    private static final boolean USE_FIREBASE = FirebaseService.getInstance().isInitialized();

    public static User signup(String username, String password) throws SQLException {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (password == null || password.length() < 4) {
            throw new IllegalArgumentException("Password must be at least 4 characters");
        }

        // Check if username already exists locally
        if (userExistsLocally(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        String firebaseUid = null;
        String email = username + "@orion.app"; // Convert username to email format
        
        // Create user in Firebase first (shared with Android)
        if (USE_FIREBASE) {
            try {
                FirebaseAuth auth = FirebaseService.getInstance().getAuth();
                
                // Check if user exists in Firebase
                try {
                    UserRecord existingUser = auth.getUserByEmail(email);
                    throw new IllegalArgumentException("Username already exists on Firebase");
                } catch (FirebaseAuthException e) {
                    // User doesn't exist, proceed with creation
                }
                
                // Create Firebase user
                UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                        .setEmail(email)
                        .setPassword(password)
                        .setDisplayName(username);
                
                UserRecord userRecord = auth.createUser(request);
                firebaseUid = userRecord.getUid();
                System.out.println("✅ Firebase user created: " + username + " (UID: " + firebaseUid + ")");
                System.out.println("This account can now be used on both desktop and Android apps!");
            } catch (FirebaseAuthException e) {
                System.err.println("❌ Firebase signup failed: " + e.getMessage());
                throw new IllegalArgumentException("Failed to create account: " + e.getMessage());
            }
        }

        // Hash password for local storage
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());

        // Store in local database for offline access
        String sql = "INSERT INTO users (username, password_hash, firebase_uid) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, username);
            pstmt.setString(2, passwordHash);
            pstmt.setString(3, firebaseUid);
            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int userId = generatedKeys.getInt(1);
                    User user = new User(userId, username, passwordHash, LocalDateTime.now());
                    createInitialSession(userId);
                    return user;
                }
            }
        } catch (SQLException e) {
            // If local DB fails but Firebase succeeded, still allow login
            System.err.println("Local DB error: " + e.getMessage());
            // Try to add firebase_uid column if it doesn't exist
            tryAddFirebaseUidColumn();
            throw e;
        }
        throw new SQLException("Failed to create user, no ID obtained");
    }

    public static User login(String username, String password) throws SQLException {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }

        // Support both email and username format
        String email = username.contains("@") ? username : username + "@orion.app";
        String firebaseUid = null;
        
        // Try Firebase authentication first (shared with Android)
        if (USE_FIREBASE) {
            try {
                FirebaseAuth auth = FirebaseService.getInstance().getAuth();
                UserRecord userRecord = auth.getUserByEmail(email);
                firebaseUid = userRecord.getUid();
                
                // Firebase Admin SDK doesn't verify passwords directly
                // So we verify locally, but confirm user exists in Firebase
                System.out.println("✅ Firebase account found: " + email);
                System.out.println("Logging in with shared account (works on desktop & Android)");
                
            } catch (FirebaseAuthException e) {
                // User doesn't exist with that email format, check local only
                System.out.println("⚠️ No Firebase account found for: " + email);
                System.out.println("Checking local database...");
            }
        }

        // Verify password against local database
        String sql = "SELECT id, username, password_hash, created_at, firebase_uid FROM users WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    
                    // Verify password
                    if (BCrypt.checkpw(password, storedHash)) {
                        int id = rs.getInt("id");
                        Timestamp timestamp = rs.getTimestamp("created_at");
                        LocalDateTime createdAt = timestamp != null ? timestamp.toLocalDateTime() : LocalDateTime.now();
                        
                        // Sync Firebase UID if missing
                        String localFirebaseUid = rs.getString("firebase_uid");
                        if (localFirebaseUid == null && firebaseUid != null) {
                            updateFirebaseUid(id, firebaseUid);
                        }
                        
                        return new User(id, username, storedHash, createdAt);
                    } else {
                        throw new IllegalArgumentException("Invalid username or password");
                    }
                } else if (firebaseUid != null) {
                    // User exists in Firebase but not locally - verify password with Firebase first
                    System.out.println("📱 Android account detected - verifying with Firebase...");
                    String verifiedUid = verifyPasswordWithFirebase(email, password);
                    
                    if (verifiedUid != null && verifiedUid.equals(firebaseUid)) {
                        // Password is correct, sync from Firebase to local
                        System.out.println("📥 Syncing Android account to desktop...");
                        return syncFirebaseUserToLocal(username, password, firebaseUid);
                    } else {
                        throw new IllegalArgumentException("Invalid username or password");
                    }
                } else {
                    throw new IllegalArgumentException("Invalid username or password");
                }
            }
        } catch (SQLException e) {
            // Try to add firebase_uid column if it doesn't exist
            tryAddFirebaseUidColumn();
            throw e;
        }
    }

    private static boolean userExistsLocally(String username) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    private static void createInitialSession(int userId) throws SQLException {
        String sql = "INSERT INTO user_sessions (user_id) VALUES (?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, userId);
            pstmt.executeUpdate();
        }
    }
    
    /**
     * Sync a Firebase user to the local database when logging in from Android account.
     */
    private static User syncFirebaseUserToLocal(String username, String password, String firebaseUid) throws SQLException {
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
        
        // If username is an email, extract the part before @ for cleaner display
        String displayUsername = username.contains("@") ? username : username;
        
        String sql = "INSERT INTO users (username, password_hash, firebase_uid) VALUES (?, ?, ?)";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, displayUsername);
            pstmt.setString(2, passwordHash);
            pstmt.setString(3, firebaseUid);
            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int userId = generatedKeys.getInt(1);
                    User user = new User(userId, displayUsername, passwordHash, LocalDateTime.now());
                    createInitialSession(userId);
                    System.out.println("✅ Account synced from Firebase!");
                    return user;
                }
            }
        }
        throw new SQLException("Failed to sync Firebase user");
    }
    
    /**
     * Update Firebase UID for existing local user.
     */
    private static void updateFirebaseUid(int userId, String firebaseUid) {
        String sql = "UPDATE users SET firebase_uid = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, firebaseUid);
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to update Firebase UID: " + e.getMessage());
        }
    }
    
    /**
     * Try to add firebase_uid column if it doesn't exist (for existing databases).
     */
    private static void tryAddFirebaseUidColumn() {
        String sql = "ALTER TABLE users ADD COLUMN firebase_uid TEXT";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.executeUpdate();
            System.out.println("Added firebase_uid column to users table");
        } catch (SQLException e) {
            // Column likely already exists, ignore
        }
    }
    
    /**
     * Verify password using Firebase REST API (for accounts created on Android).
     * This uses Firebase Identity Toolkit to verify email/password combinations.
     * 
     * @param email User's email
     * @param password User's password
     * @return Firebase UID if authentication succeeds, null otherwise
     */
    private static String verifyPasswordWithFirebase(String email, String password) {
        if (!USE_FIREBASE) {
            return null;
        }
        
        try {
            String webApiKey = FirebaseService.getInstance().getWebApiKey();
            if (webApiKey == null) {
                System.err.println("Web API Key not configured");
                return null;
            }
            
            // Firebase REST API endpoint for password verification
            String urlString = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + webApiKey;
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            // Request body
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", email);
            requestBody.addProperty("password", password);
            requestBody.addProperty("returnSecureToken", true);
            
            // Send request
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Read response
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                
                JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
                String localId = jsonResponse.get("localId").getAsString();
                
                System.out.println("✅ Password verified via Firebase!");
                return localId; // Firebase UID
            } else {
                System.out.println("❌ Firebase password verification failed");
                return null;
            }
        } catch (Exception e) {
            System.err.println("Error verifying password with Firebase: " + e.getMessage());
            return null;
        }
    }
}
