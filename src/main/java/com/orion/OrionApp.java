package com.orion;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.Files;

public class OrionApp extends Application {

    private TextArea codeArea;
    private TextArea consoleArea;
    private File currentFile;
    private Label statusLabel;

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();

        // Menu Bar
        MenuBar menuBar = createMenuBar(primaryStage);
        root.setTop(menuBar);

        // Code Editor
        codeArea = new TextArea();
        codeArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 14px;");

        // Console
        consoleArea = new TextArea();
        consoleArea.setEditable(false);
        consoleArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px; -fx-control-inner-background: #1e1e1e; -fx-text-fill: #d4d4d4;");
        consoleArea.setPrefHeight(150);

        // Split Pane
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.getItems().addAll(codeArea, consoleArea);
        splitPane.setDividerPositions(0.7);

        root.setCenter(splitPane);

        // Status Bar
        statusLabel = new Label("Ready");
        statusLabel.setPadding(new Insets(5));
        statusLabel.setStyle("-fx-background-color: #e0e0e0;");
        root.setBottom(statusLabel);

        Scene scene = new Scene(root, 900, 700);
        primaryStage.setTitle("Orion Code Editor");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private MenuBar createMenuBar(Stage stage) {
        MenuBar menuBar = new MenuBar();

        // File Menu
        Menu fileMenu = new Menu("File");
        MenuItem newItem = new MenuItem("New");
        MenuItem openItem = new MenuItem("Open");
        MenuItem saveItem = new MenuItem("Save");
        MenuItem saveAsItem = new MenuItem("Save As");

        newItem.setOnAction(e -> newFile());
        openItem.setOnAction(e -> openFile(stage));
        saveItem.setOnAction(e -> saveFile(stage));
        saveAsItem.setOnAction(e -> saveFileAs(stage));

        fileMenu.getItems().addAll(newItem, openItem, saveItem, saveAsItem);

        // Run Menu
        Menu runMenu = new Menu("Run");
        MenuItem runItem = new MenuItem("Run Code");
        runItem.setOnAction(e -> runCode());
        runMenu.getItems().add(runItem);

        menuBar.getMenus().addAll(fileMenu, runMenu);
        return menuBar;
    }

    private void newFile() {
        codeArea.clear();
        currentFile = null;
        statusLabel.setText("New File");
    }

    private void openFile(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open File");
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            try {
                String content = Files.readString(file.toPath());
                codeArea.setText(content);
                currentFile = file;
                statusLabel.setText("Opened: " + file.getName());
            } catch (IOException e) {
                showError("Error opening file: " + e.getMessage());
            }
        }
    }

    private void saveFile(Stage stage) {
        if (currentFile == null) {
            saveFileAs(stage);
        } else {
            try {
                Files.writeString(currentFile.toPath(), codeArea.getText());
                statusLabel.setText("Saved: " + currentFile.getName());
            } catch (IOException e) {
                showError("Error saving file: " + e.getMessage());
            }
        }
    }

    private void saveFileAs(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save File");
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try {
                Files.writeString(file.toPath(), codeArea.getText());
                currentFile = file;
                statusLabel.setText("Saved: " + file.getName());
            } catch (IOException e) {
                showError("Error saving file: " + e.getMessage());
            }
        }
    }

    private void runCode() {
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
                javafx.application.Platform.runLater(() -> {
                    consoleArea.appendText(output);
                    consoleArea.appendText("\n" + "=".repeat(50) + "\n");
                    consoleArea.appendText("Execution completed.\n");
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    consoleArea.appendText("Error: " + e.getMessage() + "\n");
                });
            }
        }).start();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
