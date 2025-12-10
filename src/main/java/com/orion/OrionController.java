package com.orion;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;

public class OrionController {

    @FXML
    private TextArea codeArea;

    @FXML
    private TextArea consoleArea;

    @FXML
    private Label statusLabel;

    @FXML
    private SplitPane splitPane;

    private File currentFile;
    private Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void handleNew() {
        codeArea.clear();
        currentFile = null;
        statusLabel.setText("Ready - New File");
        updateTitle();
    }

    @FXML
    private void handleOpen() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open File");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("All Files", "*.*"),
            new FileChooser.ExtensionFilter("Python Files", "*.py"),
            new FileChooser.ExtensionFilter("Java Files", "*.java"),
            new FileChooser.ExtensionFilter("JavaScript Files", "*.js"),
            new FileChooser.ExtensionFilter("C/C++ Files", "*.c", "*.cpp")
        );
        
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            try {
                String content = Files.readString(file.toPath());
                codeArea.setText(content);
                currentFile = file;
                statusLabel.setText("Opened: " + file.getName());
                updateTitle();
            } catch (IOException e) {
                showError("Error opening file", e.getMessage());
            }
        }
    }

    @FXML
    private void handleSave() {
        if (currentFile == null) {
            handleSaveAs();
        } else {
            saveToFile(currentFile);
        }
    }

    @FXML
    private void handleSaveAs() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save File");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("All Files", "*.*"),
            new FileChooser.ExtensionFilter("Python Files", "*.py"),
            new FileChooser.ExtensionFilter("Java Files", "*.java"),
            new FileChooser.ExtensionFilter("JavaScript Files", "*.js"),
            new FileChooser.ExtensionFilter("C/C++ Files", "*.c", "*.cpp")
        );
        
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            saveToFile(file);
            currentFile = file;
            updateTitle();
        }
    }

    private void saveToFile(File file) {
        try {
            Files.writeString(file.toPath(), codeArea.getText());
            statusLabel.setText("Saved: " + file.getName());
        } catch (IOException e) {
            showError("Error saving file", e.getMessage());
        }
    }

    @FXML
    private void handleRun() {
        if (currentFile == null) {
            consoleArea.setText("Please save the file first before running.");
            return;
        }

        consoleArea.clear();
        consoleArea.appendText("Running " + currentFile.getName() + "...\n");
        consoleArea.appendText("=".repeat(50) + "\n");

        new Thread(() -> {
            try {
                String output = CodeRunner.runCode(currentFile.getAbsolutePath(), codeArea.getText());
                Platform.runLater(() -> {
                    consoleArea.appendText(output);
                    consoleArea.appendText("\n" + "=".repeat(50) + "\n");
                    consoleArea.appendText("Execution completed.\n");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    consoleArea.appendText("Error: " + e.getMessage() + "\n");
                });
            }
        }).start();
    }

    @FXML
    private void handleToggleConsole() {
        consoleArea.setVisible(!consoleArea.isVisible());
        consoleArea.setManaged(!consoleArea.isManaged());
    }

    @FXML
    private void handleExit() {
        Platform.exit();
    }

    private void updateTitle() {
        if (stage != null) {
            String title = "Orion Code Editor";
            if (currentFile != null) {
                title += " - " + currentFile.getName();
            }
            stage.setTitle(title);
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
