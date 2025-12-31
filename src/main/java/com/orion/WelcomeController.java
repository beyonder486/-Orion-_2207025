package com.orion;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;

public class WelcomeController {

    @FXML private StackPane rootPane;
    @FXML private MediaView mediaView;
    @FXML private Button enterButton;
    @FXML private Label logo;

    private MediaPlayer mediaPlayer;

    /* ================= INIT ================= */

    @FXML
    public void initialize() {
        // Load welcome-specific CSS
        try {
            String cssPath = getClass().getResource("/com/orion/welcome.css").toExternalForm();
            rootPane.getScene().getStylesheets().clear();
            rootPane.getScene().getStylesheets().add(cssPath);
        } catch (Exception e) {
            System.err.println("Welcome CSS not found, using defaults");
        }
        
        // Setup video background
        setupVideo();
        
        // Animate logo
        animateLogo();

        // Fade in effect
        rootPane.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.seconds(2), rootPane);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    private void setupVideo() {
        try {
            // Look for video file in resources
            String videoPath = getClass().getResource("/com/orion/space-background.mp4").toExternalForm();
            Media media = new Media(videoPath);
            mediaPlayer = new MediaPlayer(media);
            
            // Configure media player
            mediaPlayer.setAutoPlay(true);
            mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
            mediaPlayer.setMute(true); // Mute by default
            
            // Bind MediaView to container size
            mediaView.setMediaPlayer(mediaPlayer);
            mediaView.fitWidthProperty().bind(rootPane.widthProperty());
            mediaView.fitHeightProperty().bind(rootPane.heightProperty());
            mediaView.setPreserveRatio(false);
            
        } catch (Exception e) {
            System.err.println("Video file not found. Place 'space-background.mp4' in src/main/resources/com/orion/");
            System.err.println("Using fallback background color.");
            rootPane.setStyle("-fx-background-color: linear-gradient(to bottom, #0a0a1e, #050510);");
        }
    }

    /* ================= LOGO ================= */

    private void animateLogo() {
        logo.setOpacity(0);
        logo.setScaleX(0.9);
        logo.setScaleY(0.9);

        FadeTransition fade = new FadeTransition(Duration.seconds(3), logo);
        fade.setFromValue(0);
        fade.setToValue(1);

        ScaleTransition scale = new ScaleTransition(Duration.seconds(3), logo);
        scale.setFromX(0.9);
        scale.setToX(1);
        scale.setFromY(0.9);
        scale.setToY(1);

        new ParallelTransition(fade, scale).play();
    }

    /* ================= VIDEO CLEANUP ================= */

    private void stopVideo() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }
    }

    /* ================= ENTER ================= */

    @FXML
    private void handleEnter() {
        stopVideo();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("login.fxml"));
            Stage stage = (Stage) enterButton.getScene().getWindow();
            stage.setScene(new Scene(root, 700, 500));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
