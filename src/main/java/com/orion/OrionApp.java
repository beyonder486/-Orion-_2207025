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
        // Allow the app to close when last window is closed
        Platform.setImplicitExit(true);
        
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
                System.out.println("Application closing...");
                
                // Do cleanup in background, don't block
                new Thread(() -> {
                    try {
                        // Close database
                        DatabaseManager.close();
                        
                        // Shutdown Firebase
                        if (FirebaseService.getInstance().isInitialized()) {
                            FirebaseService.getInstance().shutdown();
                        }
                    } catch (Exception e) {
                        // Ignore errors during shutdown
                    }
                }).start();
                
                // Just exit immediately - don't wait for cleanup
                System.exit(0);
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
