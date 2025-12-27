package com.orion;

import com.kodedu.terminalfx.TerminalBuilder;
import com.kodedu.terminalfx.TerminalTab;
import com.kodedu.terminalfx.config.TerminalConfig;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import javafx.stage.Popup;
import javafx.scene.layout.VBox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Collection;
import javafx.collections.*;

public class OrionController {

    @FXML private CodeArea codeArea;
    @FXML private AnchorPane terminalPane;
    @FXML private Label statusLabel;
    @FXML private ListView<String> openFilesListView;
    @FXML private TreeView<File> fileTreeView;
    @FXML private SplitPane splitPane;

    private Stage stage;
    private File currentFile;
    private File currentFolder;
    private ObservableList<String> openFiles = FXCollections.observableArrayList();
    private Map<String, String> fileContents = new HashMap<>();
    private Map<String, File> fileObjects = new HashMap<>();
    
    private AutoComplete autoComplete;
    private Popup autocompletePopup;
    private ListView<String> suggestionsList;
    private int autocompleteStartPos = -1;
    
    // Settings
    private String currentTheme = "Dark";
    private int currentFontSize = 14;
    
    // Terminal
    private TerminalTab terminal;
    private TabPane terminalTabPane;
    
    // Legacy console for code output
    private CodeRunner.ProcessHandler currentProcess;

    @FXML
    public void initialize() {
        // Add line numbers
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        
        // Apply Monokai Vibrant theme
        applyMonokaiVibrantTheme();
        
        // Initialize autocomplete
        autoComplete = new AutoComplete();
        setupAutocomplete();
        
        // Apply syntax highlighting on every text change
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            applySyntaxHighlighting();
        });
        
        // Initialize TerminalFX
        if (terminalPane != null) {
            initializeTerminal();
        }
        
        // Setup file list (only if exists in FXML)
        if (openFilesListView != null) {
            openFilesListView.setItems(openFiles);
            openFilesListView.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    String fileName = openFilesListView.getSelectionModel().getSelectedItem();
                    if (fileName != null) {
                        switchToFile(fileName);
                    }
                }
            });
        }
        
        // Setup file tree view for folder browsing
        if (fileTreeView != null) {
            setupFileTreeView();
        }
        
        applySyntaxHighlighting();
    }

    private void applyMonokaiVibrantTheme() {
        try {
            String cssPath = getClass().getResource("monokai-vibrant.css").toExternalForm();
            System.out.println("Loading CSS from: " + cssPath);
            codeArea.getStylesheets().clear();
            codeArea.getStylesheets().add(cssPath);
            System.out.println("CSS loaded. Stylesheets: " + codeArea.getStylesheets());
        } catch (Exception e) {
            System.err.println("Error loading CSS: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void initializeTerminal() {
        try {
            TerminalConfig terminalConfig = new TerminalConfig();
            terminalConfig.setBackgroundColor(javafx.scene.paint.Color.web("#16171D"));
            terminalConfig.setForegroundColor(javafx.scene.paint.Color.rgb(255, 255, 255, 0.9)); // 90% opacity
            terminalConfig.setCursorColor(javafx.scene.paint.Color.web("#FFFFFF"));
            terminalConfig.setFontSize(14);
            
            // Start in user's home directory by default
            String userHome = System.getProperty("user.home");
            terminalConfig.setWindowsTerminalStarter("cmd.exe /K cd /d \"" + userHome + "\"");
            // terminalConfig.setLinuxTerminalStarter("bash");
            // terminalConfig.setCwd(userHome);
            
            TerminalBuilder terminalBuilder = new TerminalBuilder(terminalConfig);
            terminal = terminalBuilder.newTerminal();
            
            terminalTabPane = new TabPane();
            terminalTabPane.getTabs().add(terminal);
            
            AnchorPane.setTopAnchor(terminalTabPane, 0.0);
            AnchorPane.setBottomAnchor(terminalTabPane, 0.0);
            AnchorPane.setLeftAnchor(terminalTabPane, 0.0);
            AnchorPane.setRightAnchor(terminalTabPane, 0.0);
            terminalPane.getChildren().add(terminalTabPane);
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Error initializing terminal: " + e.getMessage());
        }
    }
        
    
    private void applySyntaxHighlighting() {
        try {
            String fileExtension = currentFile != null ? currentFile.getName() : ".py";
            String text = codeArea.getText();
            
            StyleSpans<Collection<String>> highlighting = SyntaxHighlighter.computeHighlighting(text, fileExtension);
            System.out.println("Applying highlighting. Text length: " + text.length() + ", Spans: " + highlighting.getSpanCount());
            codeArea.setStyleSpans(0, highlighting);
        } catch (Exception e) {
            System.err.println("Error applying syntax highlighting: " + e.getMessage());
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
            openFile(file);
        }
    }
    
    @FXML
    private void handleOpenFolder() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Open Folder");
        
        File folder = dirChooser.showDialog(stage);
        if (folder != null && folder.isDirectory()) {
            currentFolder = folder;
            loadFolderIntoTree(folder);
            statusLabel.setText("Opened folder: " + folder.getName());
            
            // Update terminal directory to opened folder
            updateTerminalDirectory(folder);
        }
    }
    
    private void openFile(File file) {
        try {
            String content = Files.readString(file.toPath());
            String fileName = file.getName();
            // Store file content and object
            fileContents.put(fileName, content);
            fileObjects.put(fileName, file);               
            // Add to list if not already there
            if (openFilesListView != null && !openFiles.contains(fileName)) {
                openFiles.add(fileName);
            }
            // Display content
            codeArea.replaceText(content);
            currentFile = file;
            statusLabel.setText("Opened: " + fileName);
            stage.setTitle("Orion Code Editor - " + fileName);
            applySyntaxHighlighting();
            
            // Select in list
            if (openFilesListView != null) {
                openFilesListView.getSelectionModel().select(fileName);
            }
            
            // Update terminal directory to file's parent directory
            updateTerminalDirectory(file.getParentFile());
        } catch (IOException e) {
            showError("Error opening file: " + e.getMessage());
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

        showError("Code execution feature is disabled. Use the terminal to run your code.");
    }

    @FXML
    private void handleSettings() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Settings");
        dialog.setHeaderText("Orion Editor Settings");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        ComboBox<String> themeCombo = new ComboBox<>();
        themeCombo.getItems().addAll("Light", "Dark", "Monokai", "Solarized");
        themeCombo.setValue(currentTheme);
        
        Spinner<Integer> fontSizeSpinner = new Spinner<>(8, 24, currentFontSize);
        
        grid.add(new Label("Theme:"), 0, 0);
        grid.add(themeCombo, 1, 0);
        grid.add(new Label("Font Size:"), 0, 1);
        grid.add(fontSizeSpinner, 1, 1);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                currentTheme = themeCombo.getValue();
                currentFontSize = fontSizeSpinner.getValue();
                applyTheme(currentTheme);
                applyFontSize(currentFontSize);
                statusLabel.setText("Settings applied: " + currentTheme + " theme, " + currentFontSize + "px font");
            }
        });
    }
    
    private void applyTheme(String theme) {
        String codeAreaStyle;
        
        switch (theme) {
            case "Light":
                codeAreaStyle = "-fx-font-family: 'Consolas', monospace; -fx-font-size: " + currentFontSize + "px; " +
                               "-fx-background-color: #ffffff; -fx-text-fill: #000000;";
                break;
            case "Monokai":
                codeAreaStyle = "-fx-font-family: 'Consolas', monospace; -fx-font-size: " + currentFontSize + "px; " +
                               "-fx-background-color: #272822; -fx-text-fill: #f8f8f2;";
                break;
            case "Solarized":
                codeAreaStyle = "-fx-font-family: 'Consolas', monospace; -fx-font-size: " + currentFontSize + "px; " +
                               "-fx-background-color: #002b36; -fx-text-fill: #839496;";
                break;
            case "Dark":
            default:
                codeAreaStyle = "-fx-font-family: 'Consolas', monospace; -fx-font-size: " + currentFontSize + "px; " +
                               "-fx-background-color: #1e1e1e; -fx-text-fill: #d4d4d4;";
                break;
        }
        
        codeArea.setStyle(codeAreaStyle);
    }
    
    private void applyFontSize(int fontSize) {
        currentFontSize = fontSize;
        applyTheme(currentTheme); // Reapply theme with new font size
    }

    @FXML
    private void handleToggleTerminal() {
        if (terminalTabPane != null) {
            if (splitPane.getItems().contains(terminalTabPane)) {
                splitPane.getItems().remove(terminalTabPane);
            } else {
                splitPane.getItems().add(terminalTabPane);
                splitPane.setDividerPositions(0.7);
            }
        }
    }
    
    @FXML
    private void handleClearTerminal() {
        // Terminal clear command - just inform user
        if (statusLabel != null) {
            statusLabel.setText("To clear terminal, type 'cls' (Windows) or 'clear' (Linux/Mac)");
        }
    }

    @FXML
    private void handleToggleConsole() {
        // Legacy method - redirects to handleToggleTerminal
        handleToggleTerminal();
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

    private void switchToFile(String fileName) {
        // Save current file content before switching
        if (currentFile != null) {
            fileContents.put(currentFile.getName(), codeArea.getText());
        }
        
        // Load selected file
        if (fileContents.containsKey(fileName)) {
            String content = fileContents.get(fileName);
            codeArea.replaceText(content);
            currentFile = fileObjects.get(fileName);
            statusLabel.setText("Switched to: " + fileName);
            stage.setTitle("Orion Code Editor - " + fileName);
            applySyntaxHighlighting();
        }
    }
    
    private void setupAutocomplete() {
        // Create popup
        autocompletePopup = new Popup();
        autocompletePopup.setAutoHide(true);
        
        // Create suggestions list
        suggestionsList = new ListView<>();
        suggestionsList.setPrefWidth(200);
        suggestionsList.setPrefHeight(150);
        suggestionsList.setStyle("-fx-font-size: 12px;");
        
        VBox popupContent = new VBox(suggestionsList);
        popupContent.setStyle("-fx-background-color: black; -fx-border-color: gray; -fx-border-width: 1;");
        autocompletePopup.getContent().add(popupContent);
        
        // Handle Enter key to select suggestion
        suggestionsList.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                String selected = suggestionsList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    insertCompletion(selected);
                    autocompletePopup.hide();
                    event.consume();
                }
            } else if (event.getCode() == KeyCode.ESCAPE) {
                autocompletePopup.hide();
                codeArea.requestFocus();
            }
        });
        
        // Handle mouse click selection
        suggestionsList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selected = suggestionsList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    insertCompletion(selected);
                    autocompletePopup.hide();
                }
            }
        });
        
        // Listen to code area key events
        codeArea.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
            if (event.getCode().isLetterKey() || event.getCode() == KeyCode.BACK_SPACE) {
                handleAutocomplete();
            } else if (event.getCode() == KeyCode.ESCAPE && autocompletePopup.isShowing()) {
                autocompletePopup.hide();
            } else if (event.getCode() == KeyCode.DOWN && autocompletePopup.isShowing()) {
                suggestionsList.requestFocus();
                suggestionsList.getSelectionModel().selectFirst();
                event.consume();
            }
        });
    }
    
    private void handleAutocomplete() {
        int caretPos = codeArea.getCaretPosition();
        String text = codeArea.getText();
        
        // Find the start of the current word
        int wordStart = caretPos;
        while (wordStart > 0 && Character.isLetterOrDigit(text.charAt(wordStart - 1))) {
            wordStart--;
        }
        
        String currentWord = text.substring(wordStart, caretPos);
        System.out.println("Current word: '" + currentWord + "'");
        
        if (currentWord.length() > 0) {
            String fileExtension = currentFile != null ? currentFile.getName() : ".py";
            List<String> suggestions = autoComplete.getSuggestions(currentWord, fileExtension);
            System.out.println("Found " + suggestions.size() + " suggestions: " + suggestions);
            
            if (!suggestions.isEmpty()) {
                autocompleteStartPos = wordStart;
                suggestionsList.getItems().setAll(suggestions);
                
                // Position popup below caret
                var caretBounds = codeArea.getCaretBounds().orElse(null);
                if (caretBounds != null) {
                    var screenBounds = codeArea.localToScreen(caretBounds);
                    if (screenBounds != null) {
                        System.out.println("Showing popup at: " + screenBounds.getMinX() + ", " + screenBounds.getMaxY());
                        autocompletePopup.show(codeArea, 
                            screenBounds.getMinX(), 
                            screenBounds.getMaxY() + 5);
                    } else {
                        System.err.println("screenBounds is null");
                    }
                } else {
                    System.err.println("caretBounds is null");
                }
            } else {
                autocompletePopup.hide();
            }
        } else {
            autocompletePopup.hide();
        }
    }
    
    private void insertCompletion(String completion) {
        int caretPos = codeArea.getCaretPosition();
        String text = codeArea.getText();
        
        // Remove the partial word and insert completion
        String before = text.substring(0, autocompleteStartPos);
        String after = text.substring(caretPos);
        
        codeArea.replaceText(before + completion + after);
        codeArea.moveTo(autocompleteStartPos + completion.length());
    }
    
    private void setupFileTreeView() {
        if (fileTreeView == null) return;
        
        fileTreeView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                TreeItem<File> item = fileTreeView.getSelectionModel().getSelectedItem();
                if (item != null && item.getValue() != null) {
                    File file = item.getValue();
                    if (file.isFile()) {
                        openFile(file);
                    }
                }
            }
        });
        
        fileTreeView.setCellFactory(tv -> new TreeCell<File>() {
            @Override
            protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getName());
                }
            }
        });
    }
    
    private void loadFolderIntoTree(File folder) {
        if (fileTreeView == null) return;
        
        TreeItem<File> rootItem = createTreeItem(folder);
        rootItem.setExpanded(true);
        fileTreeView.setRoot(rootItem);
    }
    
    private TreeItem<File> createTreeItem(File file) {
        TreeItem<File> item = new TreeItem<>(file);
        
        if (file.isDirectory()) {
            item.setExpanded(false);
            // Lazy load children
            item.expandedProperty().addListener((obs, wasExpanded, isNowExpanded) -> {
                if (isNowExpanded && item.getChildren().isEmpty()) {
                    loadChildren(item);
                }
            });
            // Load immediate children
            loadChildren(item);
        }
        
        return item;
    }
    
    private void loadChildren(TreeItem<File> parentItem) {
        File dir = parentItem.getValue();
        if (dir == null || !dir.isDirectory()) return;
        
        File[] files = dir.listFiles();
        if (files != null) {
            parentItem.getChildren().clear();
            
            // Sort: directories first, then files, alphabetically
            java.util.Arrays.sort(files, (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                return f1.getName().compareToIgnoreCase(f2.getName());
            });
            
            for (File file : files) {
                // Skip hidden files
                if (!file.getName().startsWith(".")) {
                    parentItem.getChildren().add(createTreeItem(file));
                }
            }
        }
    }
    
    private void updateTerminalDirectory(File directory) {
        if (terminal != null && directory != null && directory.isDirectory()) {
            String cdCommand = System.getProperty("os.name").toLowerCase().contains("win") 
                ? "cd /d \"" + directory.getAbsolutePath() + "\"\r\n"
                : "cd \"" + directory.getAbsolutePath() + "\"\n";
            
            try {
                terminal.getTerminal().command(cdCommand);
                statusLabel.setText("Terminal directory: " + directory.getAbsolutePath());
            } catch (Exception e) {
                System.err.println("Error updating terminal directory: " + e.getMessage());
            }
        }
    }
}
