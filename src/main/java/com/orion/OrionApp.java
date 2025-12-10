package com.orion;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class OrionApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            // Load FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/orion/main.fxml"));
            Parent root = loader.load();
            
            // Get controller and pass stage reference
            OrionController controller = loader.getController();
            controller.setStage(primaryStage);
            
            // Create scene and load CSS
            Scene scene = new Scene(root, 900, 700);
            scene.getStylesheets().add(getClass().getResource("/com/orion/syntax.css").toExternalForm());
            primaryStage.setTitle("Orion Code Editor");
            primaryStage.setScene(scene);
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
