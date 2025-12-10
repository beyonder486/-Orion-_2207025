package com.orion;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class OrionController {

    @FXML private CodeArea codeArea;
    @FXML private TextArea consoleArea;
    @FXML private Label statusLabel;
    @FXML private SplitPane splitPane;

    private Stage stage;
    private File currentFile;

    @FXML
    public void initialize() {
        System.out.println("Controller initialized!");
        System.out.println("CodeArea: " + codeArea);
        
        // Add line numbers
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        
        // Apply syntax highlighting on every text change
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            System.out.println("Text changed, applying highlighting...");
            applySyntaxHighlighting();
        });
        
        // Test with sample code
        codeArea.replaceText("def hello():\n    print(\"Hello World\")  # comment\n    x = 42");
        applySyntaxHighlighting();
    }
    
    private void applySyntaxHighlighting() {
        try {
            String fileExtension = currentFile != null ? currentFile.getName() : ".py";
            String text = codeArea.getText();
            System.out.println("Highlighting text: " + text.substring(0, Math.min(50, text.length())));
            
            var highlighting = SyntaxHighlighter.computeHighlighting(text, fileExtension);
            codeArea.setStyleSpans(0, highlighting);
            System.out.println("Highlighting applied!");
        } catch (Exception e) {
            System.err.println("Error applying highlighting: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void handleNew() {
        codeArea.clear();
        currentFile = null;
        statusLabel.setText("New File");
        stage.setTitle("Orion Code Editor - Untitled");
    }

    @FXML
    private void handleOpen() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open File");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("All Supported", "*.py", "*.java", "*.c", "*.cpp", "*.js"),
            new FileChooser.ExtensionFilter("Python Files", "*.py"),
            new FileChooser.ExtensionFilter("Java Files", "*.java"),
            new FileChooser.ExtensionFilter("C/C++ Files", "*.c", "*.cpp"),
            new FileChooser.ExtensionFilter("JavaScript Files", "*.js"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            try {
                String content = Files.readString(file.toPath());
                codeArea.replaceText(content);
                currentFile = file;
                statusLabel.setText("Opened: " + file.getName());
                stage.setTitle("Orion Code Editor - " + file.getName());
                applySyntaxHighlighting();
            } catch (IOException e) {
                showError("Error opening file: " + e.getMessage());
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
        fileChooser.setTitle("Save File As");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Python Files", "*.py"),
            new FileChooser.ExtensionFilter("Java Files", "*.java"),
            new FileChooser.ExtensionFilter("C/C++ Files", "*.c", "*.cpp"),
            new FileChooser.ExtensionFilter("JavaScript Files", "*.js"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            saveToFile(file);
            currentFile = file;
            stage.setTitle("Orion Code Editor - " + file.getName());
        }
    }

    private void saveToFile(File file) {
        try {
            Files.writeString(file.toPath(), codeArea.getText());
            statusLabel.setText("Saved: " + file.getName());
        } catch (IOException e) {
            showError("Error saving file: " + e.getMessage());
        }
    }

    @FXML
    private void handleRun() {
        if (currentFile == null) {
            showError("Please save the file before running.");
            return;
        }

        consoleArea.setText("Running " + currentFile.getName() + "...\n");
        consoleArea.appendText("=".repeat(50) + "\n");

        new Thread(() -> {
            try {
                String output = CodeRunner.runCode(currentFile.getAbsolutePath(), codeArea.getText());
                Platform.runLater(() -> consoleArea.appendText(output));
            } catch (Exception e) {
                Platform.runLater(() -> consoleArea.appendText("Error: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    private void handleToggleConsole() {
        if (splitPane.getItems().contains(consoleArea)) {
            splitPane.getItems().remove(consoleArea);
        } else {
            splitPane.getItems().add(consoleArea);
            splitPane.setDividerPositions(0.7);
        }
    }

    @FXML
    private void handleExit() {
        Platform.exit();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
