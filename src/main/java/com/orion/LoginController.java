package com.orion;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.SQLException;

public class LoginController {

    @FXML private StackPane rootPane;
    @FXML private ImageView backgroundImage;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label errorLabel;
    @FXML private Label titleLabel;
    @FXML private Button loginButton;
    @FXML private Button signupButton;
    @FXML private Button toggleButton;
    @FXML private VBox confirmPasswordBox;

    private boolean isSignupMode = false;

    @FXML
    public void initialize() {
        // Bind background image to window size
        if (backgroundImage != null && rootPane != null) {
            backgroundImage.fitWidthProperty().bind(rootPane.widthProperty());
            backgroundImage.fitHeightProperty().bind(rootPane.heightProperty());
        }
        
        // Clear error label initially
        errorLabel.setText("");
        
        // Hide confirm password field initially (login mode)
        if (confirmPasswordBox != null) {
            confirmPasswordBox.setVisible(false);
            confirmPasswordBox.setManaged(false);
        }
        
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
        
        try {
            // Authenticate using AuthenticationService
            User user = AuthenticationService.login(username, password);
            
            // Set user session
            UserSession.getInstance().setCurrentUser(user);
            
            // Open main editor
            openMainEditor(username);
        } catch (IllegalArgumentException e) {
            errorLabel.setText(e.getMessage());
            passwordField.clear();
            passwordField.requestFocus();
        } catch (SQLException e) {
            errorLabel.setText("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @FXML
    private void handleSignup() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        
        // Clear previous error
        errorLabel.setText("");
        
        // Validate input
        if (username == null || username.trim().isEmpty()) {
            errorLabel.setText("Please enter a username");
            return;
        }
        
        if (password == null || password.length() < 4) {
            errorLabel.setText("Password must be at least 4 characters");
            return;
        }
        
        if (!password.equals(confirmPassword)) {
            errorLabel.setText("Passwords do not match");
            confirmPasswordField.clear();
            confirmPasswordField.requestFocus();
            return;
        }
        
        try {
            // Create user using AuthenticationService
            User user = AuthenticationService.signup(username, password);
            
            // Set user session
            UserSession.getInstance().setCurrentUser(user);
            
            // Open main editor
            openMainEditor(username);
        } catch (IllegalArgumentException e) {
            errorLabel.setText(e.getMessage());
        } catch (SQLException e) {
            errorLabel.setText("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @FXML
    private void handleToggleMode() {
        isSignupMode = !isSignupMode;
        
        if (isSignupMode) {
            // Switch to signup mode
            titleLabel.setText("Sign Up");
            loginButton.setVisible(false);
            loginButton.setManaged(false);
            signupButton.setVisible(true);
            signupButton.setManaged(true);
            confirmPasswordBox.setVisible(true);
            confirmPasswordBox.setManaged(true);
            toggleButton.setText("Already have an account? Login");
        } else {
            // Switch to login mode
            titleLabel.setText("Sign In");
            loginButton.setVisible(true);
            loginButton.setManaged(true);
            signupButton.setVisible(false);
            signupButton.setManaged(false);
            confirmPasswordBox.setVisible(false);
            confirmPasswordBox.setManaged(false);
            toggleButton.setText("Don't have an account? Sign Up");
        }
        
        // Clear fields and error
        usernameField.clear();
        passwordField.clear();
        if (confirmPasswordField != null) {
            confirmPasswordField.clear();
        }
        errorLabel.setText("");
        usernameField.requestFocus();
    }
    
    private void openMainEditor(String username) {
        try {
            // Load main editor FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("main.fxml"));
            Parent root = loader.load();
            
            // Get controller and pass stage reference
            OrionController controller = loader.getController();
            Stage stage = (Stage) usernameField.getScene().getWindow();
            controller.setStage(stage);
            
            // Set username and restore session
            controller.setUsername(username);
            controller.restoreSession();
            
            // Create scene and clear any previous stylesheets
            Scene scene = new Scene(root, 900, 700);
            scene.getStylesheets().clear(); // Clear welcome.css or any other CSS
            
            // Store controller reference for cleanup
            root.setUserData(controller);
            
            // Set the new scene
            stage.setScene(scene);
            stage.setTitle("Orion Code Editor - " + username);
            
        } catch (Exception e) {
            errorLabel.setText("Error loading editor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
