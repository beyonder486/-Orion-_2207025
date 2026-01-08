package com.orion;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class OrionApp extends Application {
    
    private WelcomeController welcomeController;

    @Override
    public void start(Stage primaryStage) {
        // Initialize database
        DatabaseManager.initialize();
        
        // Initialize Firebase (optional - will work offline if credentials not found)
        try {
            FirebaseService.getInstance().initialize();
            System.out.println("Firebase collaboration features enabled");
        } catch (Exception e) {
            System.out.println("Firebase not configured - collaboration features disabled");
            System.out.println("To enable: place firebase-credentials.json in resources folder");
        }
        
        try {
            // Load Welcome FXML (with space animation)
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/orion/welcome.fxml"));
            Parent root = loader.load();
            
            // Get the welcome controller for cleanup
            welcomeController = loader.getController();
            
            // Create scene for welcome screen
            Scene scene = new Scene(root, 800, 600);
            primaryStage.setTitle("Orion");
            primaryStage.setScene(scene);
            
            // Add close handler to save session and close database
            primaryStage.setOnCloseRequest(event -> {
                try {
                    // Cleanup welcome controller resources
                    if (welcomeController != null) {
                        welcomeController.cleanup();
                    }
                    
                    // Try to cleanup main controller if it exists
                    Scene currentScene = primaryStage.getScene();
                    if (currentScene != null && currentScene.getRoot() != null) {
                        try {
                            // Get controller from scene's user data if set
                            Object controller = currentScene.getRoot().getUserData();
                            if (controller instanceof OrionController) {
                                ((OrionController) controller).cleanup();
                            }
                        } catch (Exception e) {
                            System.err.println("Error cleaning up controller: " + e.getMessage());
                        }
                    }
                    
                    // Shutdown Firebase connections gracefully
                    if (FirebaseService.getInstance().isInitialized()) {
                        try {
                            FirebaseService.getInstance().shutdown();
                        } catch (Exception e) {
                            System.err.println("Error during Firebase shutdown: " + e.getMessage());
                        }
                    }
                    
                    // Close database
                    DatabaseManager.close();
                    
                    // Force exit to prevent hanging
                    Platform.exit();
                    System.exit(0);
                } catch (Exception e) {
                    System.err.println("Error during shutdown: " + e.getMessage());
                    System.exit(0);
                }
            });
            
            primaryStage.show();
            
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error loading FXML: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
