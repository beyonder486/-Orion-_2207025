package com.orion;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    // Simple in-memory user database (replace with real authentication later)
    private static final Map<String, String> USERS = new HashMap<>();
    
    static {
        USERS.put("admin", "admin");
        USERS.put("user", "pass");
        USERS.put("dev", "dev123");
    }

    @FXML
    public void initialize() {
        // Clear error label initially
        errorLabel.setText("");
        
        // Focus on username field
        usernameField.requestFocus();
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        
        // Clear previous error
        errorLabel.setText("");
        
        // Validate input
        if (username == null || username.trim().isEmpty()) {
            errorLabel.setText("Please enter a username");
            return;
        }
        
        if (password == null || password.isEmpty()) {
            errorLabel.setText("Please enter a password");
            return;
        }
        
        // Authenticate
        if (authenticate(username, password)) {
            openMainEditor();
        } else {
            errorLabel.setText("Invalid username or password");
            passwordField.clear();
            passwordField.requestFocus();
        }
    }
    
    private boolean authenticate(String username, String password) {
        // Check against stored credentials
        String storedPassword = USERS.get(username);
        return storedPassword != null && storedPassword.equals(password);
    }
    
    private void openMainEditor() {
        try {
            // Load main editor FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/orion/main.fxml"));
            Parent root = loader.load();
            
            // Get controller and pass stage reference
            OrionController controller = loader.getController();
            Stage stage = (Stage) usernameField.getScene().getWindow();
            controller.setStage(stage);
            
            // Create scene and load CSS
            Scene scene = new Scene(root, 900, 700);
            scene.getStylesheets().add(getClass().getResource("/com/orion/syntax.css").toExternalForm());
            
            // Set the new scene
            stage.setScene(scene);
            stage.setTitle("Orion Code Editor");
            
        } catch (Exception e) {
            errorLabel.setText("Error loading editor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
