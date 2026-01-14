package com.orion;

import com.kodedu.terminalfx.TerminalBuilder;
import com.kodedu.terminalfx.TerminalTab;
import com.kodedu.terminalfx.config.TerminalConfig;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.geometry.Pos;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

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
    @FXML private VBox pendingProjectsContainer;
    @FXML private Label pendingCountLabel;

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
    
    // User session
    private String username;
    
    // Collaboration features
    private ProjectService projectService;
    private CollaborationService collaborationService;
    private Project currentProject;
    private Map<String, ProjectMember> onlineMembers = new HashMap<>();
    private Label collaborationStatusLabel;
    private boolean collaborationEnabled = false;
    private String pendingRemoteContent = null; // Stores remote changes awaiting reload
    
    // Pending Projects from mobile app
    private PendingProjectService pendingProjectService;
    private com.google.cloud.firestore.ListenerRegistration pendingProjectsListener;
    
    // Terminal
    private TerminalTab terminal;
    private TabPane terminalTabPane;
    
    // Legacy console for code output
    private CodeRunner.ProcessHandler currentProcess;

    @FXML
    public void initialize() {
        // Initialize collaboration services if Firebase is available
        if (FirebaseService.getInstance().isInitialized()) {
            try {
                projectService = new ProjectService();
                collaborationService = new CollaborationService();
                collaborationEnabled = true;
                System.out.println("Collaboration features initialized");
                
                // Initialize pending projects service
                pendingProjectService = new PendingProjectService();
                System.out.println("Pending projects service initialized");
            } catch (Exception e) {
                System.err.println("Failed to initialize collaboration: " + e.getMessage());
                collaborationEnabled = false;
            }
        }
        
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
            
            // Also apply to the scene for menu styling
            Platform.runLater(() -> {
                if (codeArea.getScene() != null) {
                    codeArea.getScene().getStylesheets().clear();
                    codeArea.getScene().getStylesheets().add(cssPath);
                    System.out.println("CSS applied to scene for menu styling");
                }
            });
            
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
            
            TerminalBuilder terminalBuilder = new TerminalBuilder(terminalConfig);
            terminal = terminalBuilder.newTerminal();
            
            // Make terminal tab daemon to allow app shutdown
            if (terminal != null) {
                terminalTabPane = new TabPane();
                terminalTabPane.getTabs().add(terminal);
                
                AnchorPane.setTopAnchor(terminalTabPane, 0.0);
                AnchorPane.setBottomAnchor(terminalTabPane, 0.0);
                AnchorPane.setLeftAnchor(terminalTabPane, 0.0);
                AnchorPane.setRightAnchor(terminalTabPane, 0.0);
                terminalPane.getChildren().add(terminalTabPane);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to initialize terminal: " + e.getMessage());
            if (statusLabel != null) {
                statusLabel.setText("Terminal unavailable: " + e.getMessage());
            }
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

    public void setUsername(String username) {
        this.username = username;
    }
    
    public void cleanup() {
        System.out.println("Cleaning up OrionController...");
        
        // Save current session
        try {
            saveSession();
        } catch (Exception e) {
            System.err.println("Error saving session: " + e.getMessage());
        }
        
        // Kill any running process
        if (currentProcess != null) {
            try {
                Process proc = currentProcess.getProcess();
                if (proc != null && proc.isAlive()) {
                    System.out.println("Stopping running process...");
                    proc.destroyForcibly();
                }
            } catch (Exception e) {
                System.err.println("Error stopping process: " + e.getMessage());
            }
        }
        
        // Close terminal
        if (terminal != null) {
            try {
                System.out.println("Closing terminal...");
                terminal.getTerminal().onTerminalFxReady(() -> {
                    // Terminal cleanup if needed
                });
            } catch (Exception e) {
                System.err.println("Error closing terminal: " + e.getMessage());
            }
        }
        
        // Close pending projects listener
        if (pendingProjectsListener != null) {
            try {
                System.out.println("Removing pending projects listener...");
                pendingProjectsListener.remove();
            } catch (Exception e) {
                System.err.println("Error removing pending projects listener: " + e.getMessage());
            }
        }
        
        // Close collaboration services and set user offline
        if (collaborationService != null) {
            try {
                System.out.println("Shutting down collaboration service...");
                collaborationService.shutdown();
            } catch (Exception e) {
                System.err.println("Error closing collaboration: " + e.getMessage());
            }
        }
        
        System.out.println("OrionController cleanup complete");
    }
    
    @FXML
    public void handleLogout() {
        try {
            // Cleanup resources
            cleanup();
            
            // Load login screen
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"));
            Parent root = loader.load();
            
            // Clear username and session
            UserSession.getInstance().logout();
            username = null;
            
            // Switch to login scene
            Scene scene = new Scene(root, 700, 500);
            stage.setScene(scene);
            stage.setTitle("Orion - Login");
            
            System.out.println("User logged out successfully");
        } catch (Exception e) {
            System.err.println("Error during logout: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void restoreSession() {
        if (username == null) return;
        
        int userId = UserSession.getInstance().getUserId();
        if (userId == -1) return;
        
        // Load session data
        SessionService.SessionData sessionData = SessionService.loadSession(userId);
        
        // Restore theme
        if (sessionData.theme != null && !sessionData.theme.isEmpty()) {
            currentTheme = sessionData.theme;
            applyTheme(currentTheme);
        }
        
        // Restore font size
        if (sessionData.fontSize > 0) {
            currentFontSize = sessionData.fontSize;
            applyFontSize(currentFontSize);
        }
        
        // Restore workspace folder
        if (sessionData.lastWorkspace != null && !sessionData.lastWorkspace.isEmpty()) {
            File workspaceFolder = new File(sessionData.lastWorkspace);
            if (workspaceFolder.exists() && workspaceFolder.isDirectory()) {
                currentFolder = workspaceFolder;
                loadFolderIntoTree(workspaceFolder);
                updateTerminalDirectory(workspaceFolder);
            }
        }
        
        // Restore collaborative project
        if (collaborationEnabled && sessionData.currentProjectId != null && !sessionData.currentProjectId.isEmpty()) {
            restoreProject(sessionData.currentProjectId, String.valueOf(userId));
        }
        
        // Restore last opened file
        if (sessionData.lastFilePath != null && !sessionData.lastFilePath.isEmpty()) {
            File lastFile = new File(sessionData.lastFilePath);
            if (lastFile.exists() && lastFile.isFile()) {
                openFile(lastFile);
            }
        }
    }
    
    /**
     * Restore a collaborative project from session.
     */
    private void restoreProject(String projectId, String userId) {
        new Thread(() -> {
            try {
                Project project = projectService.getProject(projectId);
                if (project != null && project.isMember(userId)) {
                    currentProject = project;
                    
                    // Initialize collaboration
                    collaborationService.initializeProject(projectId, userId);
                    
                    // Setup presence listener on JavaFX thread
                    Platform.runLater(() -> {
                        setupPresenceListener();
                        statusLabel.setText("Restored project: " + project.getName());
                        System.out.println("Project restored: " + project.getName());
                    });
                } else {
                    System.out.println("Could not restore project - not found or not a member");
                    // Clear invalid project from session
                    SessionService.updateCurrentProject(Integer.parseInt(userId), null);
                }
            } catch (Exception e) {
                System.err.println("Failed to restore project: " + e.getMessage());
            }
        }).start();
    }
    
    public void saveSession() {
        if (username == null) return;
        
        int userId = UserSession.getInstance().getUserId();
        if (userId == -1) return;
        
        String lastFilePath = currentFile != null ? currentFile.getAbsolutePath() : null;
        String lastWorkspace = currentFolder != null ? currentFolder.getAbsolutePath() : null;
        String projectId = currentProject != null ? currentProject.getId() : null;
        
        SessionService.saveSession(userId, lastFilePath, lastWorkspace, currentTheme, currentFontSize, projectId);
    }

    @FXML
    public void handleNew() {
        codeArea.clear();
        currentFile = null;
        statusLabel.setText("New File");
        stage.setTitle("Orion Code Editor - Untitled");
    }

    @FXML
    public void handleOpen() {
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
    public void handleOpenFolder() {
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
            
            // Load into collaboration cache if project is active
            if (collaborationService != null && currentProject != null) {
                String relativePath = getRelativePath(currentProject.getWorkspacePath(), file.getAbsolutePath());
                collaborationService.loadFileIntoCache(relativePath, content);
            }
            
            // Select in list
            if (openFilesListView != null) {
                openFilesListView.getSelectionModel().select(fileName);
            }
            
            // Update terminal directory to file's parent directory
            updateTerminalDirectory(file.getParentFile());
            
            // Enable collaboration sync if in a project
            if (collaborationEnabled && currentProject != null) {
                enableFileSync(file);
            }
            
            // Save session
            saveSession();
        } catch (IOException e) {
            showError("Error opening file: " + e.getMessage());
        }
    }

    @FXML
    public void handleSave() {
        if (currentFile == null) {
            handleSaveAs();
        } else {
            saveToFile(currentFile);
        }
    }

    @FXML
    public void handleSaveAs() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save File As");
        
        // Set initial directory to current folder if available
        if (currentFolder != null && currentFolder.isDirectory()) {
            fileChooser.setInitialDirectory(currentFolder);
        } else if (currentFile != null && currentFile.getParentFile() != null) {
            fileChooser.setInitialDirectory(currentFile.getParentFile());
        }
        
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
            
            // Update currentFolder if we don't have one, or if the file is saved outside current folder
            if (currentFolder == null && file.getParentFile() != null) {
                currentFolder = file.getParentFile();
                loadFolderIntoTree(currentFolder);
            }
            
            stage.setTitle("Orion Code Editor - " + file.getName());
        }
    }

    private void saveToFile(File file) {
        try {
            Files.writeString(file.toPath(), codeArea.getText());
            statusLabel.setText("Saved: " + file.getName());
            
            // Sync to Firestore if collaboration is enabled
            if (collaborationService != null && currentProject != null) {
                String relativePath = getRelativePath(currentProject.getWorkspacePath(), file.getAbsolutePath());
                collaborationService.updateFileContent(relativePath, codeArea.getText(), username != null ? username : "Unknown");
                statusLabel.setText("Saved & synced: " + file.getName());
            }
            
            // Refresh file tree to show new file
            refreshFileTree();
            
            // Save session
            saveSession();
        } catch (IOException e) {
            showError("Error saving file: " + e.getMessage());
        }
    }

    @FXML
    public void handleCloseFile() {
        if (currentFile == null) {
            statusLabel.setText("No file to close");
            return;
        }
        
        String fileName = currentFile.getName();
        
        // Remove from open files list
        if (openFilesListView != null) {
            openFiles.remove(fileName);
            fileContents.remove(fileName);
            fileObjects.remove(fileName);
        }
        
        // Clear the editor
        codeArea.clear();
        currentFile = null;
        statusLabel.setText("Closed: " + fileName);
        stage.setTitle("Orion Code Editor - Untitled");
        
        // If there are other open files, switch to the first one
        if (!openFiles.isEmpty()) {
            String nextFile = openFiles.get(0);
            switchToFile(nextFile);
        }
    }

    @FXML
    public void handleRun() {
        if (currentFile == null) {
            showError("Please save the file before running.");
            return;
        }

        showError("Code execution feature is disabled. Use the terminal to run your code.");
    }

    @FXML
    public void handleSettings() {
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
    public void handleToggleTerminal() {
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
    public void handleClearTerminal() {
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
    public void handleExit() {
        // Trigger the window close which will run the proper cleanup
        if (stage != null) {
            stage.close();
        } else {
            Platform.exit();
        }
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
    
    private void refreshFileTree() {
        if (currentFolder != null && fileTreeView != null) {
            // Store the current expanded state
            TreeItem<File> root = fileTreeView.getRoot();
            java.util.Set<String> expandedPaths = new java.util.HashSet<>();
            
            if (root != null) {
                collectExpandedPaths(root, expandedPaths);
            }
            
            // Reload the tree
            loadFolderIntoTree(currentFolder);
            
            // Restore expanded state
            if (fileTreeView.getRoot() != null) {
                restoreExpandedPaths(fileTreeView.getRoot(), expandedPaths);
            }
        }
    }
    
    private void collectExpandedPaths(TreeItem<File> item, java.util.Set<String> expandedPaths) {
        if (item != null && item.isExpanded() && item.getValue() != null) {
            expandedPaths.add(item.getValue().getAbsolutePath());
            for (TreeItem<File> child : item.getChildren()) {
                collectExpandedPaths(child, expandedPaths);
            }
        }
    }
    
    private void restoreExpandedPaths(TreeItem<File> item, java.util.Set<String> expandedPaths) {
        if (item != null && item.getValue() != null) {
            if (expandedPaths.contains(item.getValue().getAbsolutePath())) {
                item.setExpanded(true);
                for (TreeItem<File> child : item.getChildren()) {
                    restoreExpandedPaths(child, expandedPaths);
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
    
    // ==================== COLLABORATION FEATURES ====================
    
    /**
     * Show all projects the user owns or is a member of.
     */
    @FXML
    public void handleMyProjects() {
        if (!collaborationEnabled) {
            showAlert("Collaboration Unavailable", "Firebase is not configured.");
            return;
        }
        
        User currentUser = UserSession.getInstance().getCurrentUser();
        if (currentUser == null) {
            showAlert("Error", "User session not found.");
            return;
        }
        
        String userId = String.valueOf(currentUser.getId());
        
        // Get all projects for this user
        new Thread(() -> {
            try {
                java.util.List<Project> projects = projectService.getUserProjects(userId);
                
                Platform.runLater(() -> {
                    if (projects.isEmpty()) {
                        showAlert("No Projects", "You are not a member of any projects.\n\nCreate a new project or join an existing one.");
                        return;
                    }
                    
                    // Create dialog to show projects
                    Dialog<Project> dialog = new Dialog<>();
                    dialog.setTitle("My Projects");
                    dialog.setHeaderText("Select a project to open:");
                    
                    VBox content = new VBox(10);
                    content.setPadding(new Insets(10));
                    
                    ToggleGroup group = new ToggleGroup();
                    
                    for (Project project : projects) {
                        RadioButton rb = new RadioButton();
                        rb.setToggleGroup(group);
                        rb.setUserData(project);
                        
                        VBox projectBox = new VBox(5);
                        Label nameLabel = new Label(project.getName());
                        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
                        
                        Label codeLabel = new Label("Code: " + project.getCode());
                        codeLabel.setStyle("-fx-text-fill: #666;");
                        
                        Label pathLabel = new Label("Path: " + project.getWorkspacePath());
                        pathLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");
                        
                        projectBox.getChildren().addAll(nameLabel, codeLabel, pathLabel);
                        
                        HBox projectRow = new HBox(10);
                        projectRow.getChildren().addAll(rb, projectBox);
                        
                        // Add delete button if user is the owner
                        if (project.getOwnerId().equals(userId)) {
                            Region spacer = new Region();
                            HBox.setHgrow(spacer, Priority.ALWAYS);
                            
                            Button deleteBtn = new Button("🗑️ Delete");
                            deleteBtn.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 5 10;");
                            deleteBtn.setOnAction(e -> {
                                // Confirm deletion
                                Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
                                confirmDialog.setTitle("Delete Project");
                                confirmDialog.setHeaderText("Delete \"" + project.getName() + "\"?");
                                confirmDialog.setContentText("This will permanently delete the project and all its data from the database.\nThis action cannot be undone.");
                                
                                confirmDialog.showAndWait().ifPresent(response -> {
                                    if (response == ButtonType.OK) {
                                        // Delete project in background thread
                                        new Thread(() -> {
                                            try {
                                                projectService.deleteProject(project.getId());
                                                
                                                Platform.runLater(() -> {
                                                    // If we're deleting the current project, clear it
                                                    if (currentProject != null && currentProject.getId().equals(project.getId())) {
                                                        currentProject = null;
                                                        statusLabel.setText("Current project deleted");
                                                        stage.setTitle("Orion Code Editor");
                                                    }
                                                    
                                                    // Close the dialog and show success message
                                                    dialog.close();
                                                    showAlert("Success", "Project \"" + project.getName() + "\" has been deleted.");
                                                });
                                            } catch (Exception ex) {
                                                Platform.runLater(() -> {
                                                    showAlert("Error", "Failed to delete project: " + ex.getMessage());
                                                });
                                                ex.printStackTrace();
                                            }
                                        }).start();
                                    }
                                });
                            });
                            
                            projectRow.getChildren().addAll(spacer, deleteBtn);
                        }
                        
                        projectRow.setStyle("-fx-padding: 10; -fx-background-color: #f5f5f5; -fx-background-radius: 5;");
                        
                        // Highlight current project
                        if (currentProject != null && currentProject.getId().equals(project.getId())) {
                            projectRow.setStyle("-fx-padding: 10; -fx-background-color: #d0e8ff; -fx-background-radius: 5;");
                            rb.setSelected(true);
                        }
                        
                        content.getChildren().add(projectRow);
                    }
                    
                    ScrollPane scrollPane = new ScrollPane(content);
                    scrollPane.setFitToWidth(true);
                    scrollPane.setPrefHeight(300);
                    
                    dialog.getDialogPane().setContent(scrollPane);
                    dialog.getDialogPane().setPrefWidth(500);
                    dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
                    
                    dialog.setResultConverter(button -> {
                        if (button == ButtonType.OK && group.getSelectedToggle() != null) {
                            return (Project) group.getSelectedToggle().getUserData();
                        }
                        return null;
                    });
                    
                    dialog.showAndWait().ifPresent(selectedProject -> {
                        openProject(selectedProject, userId);
                    });
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showAlert("Error", "Failed to load projects: " + e.getMessage());
                });
                e.printStackTrace();
            }
        }).start();
    }
    
    /**
     * Open a project and load its workspace.
     */
    private void openProject(Project project, String userId) {
        currentProject = project;
        
        // Initialize collaboration
        collaborationService.initializeProject(project.getId(), userId);
        
        // Setup presence listener
        setupPresenceListener();
        
        // Save project to session
        SessionService.updateCurrentProject(Integer.parseInt(userId), project.getId());
        
        // Open project workspace
        File projectFolder = new File(project.getWorkspacePath());
        if (projectFolder.exists() && projectFolder.isDirectory()) {
            currentFolder = projectFolder;
            loadFolderIntoTree(projectFolder);
            updateTerminalDirectory(projectFolder);
        }
        
        statusLabel.setText("Opened project: " + project.getName() + " (" + project.getCode() + ")");
        stage.setTitle("Orion Code Editor - " + project.getName());
    }
    
    /**
     * Leave the current project.
     */
    @FXML
    public void handleLeaveProject() {
        if (!collaborationEnabled) {
            showAlert("Collaboration Unavailable", "Firebase is not configured.");
            return;
        }
        
        if (currentProject == null) {
            showAlert("No Active Project", "You are not currently in a project.");
            return;
        }
        
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Leave Project");
        confirmAlert.setHeaderText("Leave " + currentProject.getName() + "?");
        confirmAlert.setContentText("You can rejoin later using the project code: " + currentProject.getCode());
        
        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                String projectName = currentProject.getName();
                
                // Leave project in collaboration service
                collaborationService.leaveProject();
                
                // Clear project from session
                User currentUser = UserSession.getInstance().getCurrentUser();
                if (currentUser != null) {
                    SessionService.updateCurrentProject(currentUser.getId(), null);
                }
                
                currentProject = null;
                onlineMembers.clear();
                
                statusLabel.setText("Left project: " + projectName);
                stage.setTitle("Orion Code Editor - " + (username != null ? username : "Untitled"));
            }
        });
    }
    
    /**
     * Create a new collaborative project from the current workspace.
     */
    @FXML
    public void handleCreateProject() {
        if (!collaborationEnabled) {
            showAlert("Collaboration Unavailable", "Firebase is not configured. Please set up Firebase credentials to enable collaboration.");
            return;
        }
        
        if (currentFolder == null) {
            showAlert("No Workspace", "Please open a folder first to create a project.");
            return;
        }
        
        // Prompt for project name
        TextInputDialog dialog = new TextInputDialog(currentFolder.getName());
        dialog.setTitle("Create Collaborative Project");
        dialog.setHeaderText("Create a new collaborative project");
        dialog.setContentText("Project name:");
        
        dialog.showAndWait().ifPresent(projectName -> {
            try {
                User currentUser = UserSession.getInstance().getCurrentUser();
                if (currentUser == null) {
                    showAlert("Error", "User session not found.");
                    return;
                }
                
                // Create project in Firestore
                Project project = projectService.createProject(
                    projectName,
                    String.valueOf(currentUser.getId()),
                    currentFolder.getAbsolutePath()
                );
                
                currentProject = project;
                
                // Initialize collaboration for this project
                collaborationService.initializeProject(
                    project.getId(),
                    String.valueOf(currentUser.getId())
                );
                
                // Setup presence listener
                setupPresenceListener();
                
                // Save project to session
                SessionService.updateCurrentProject(currentUser.getId(), project.getId());
                
                // Show shareable code
                showProjectCode(project);
                
                statusLabel.setText("Project created: " + projectName);
                
            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Error", "Failed to create project: " + e.getMessage());
            }
        });
    }
    
    /**
     * Join an existing project using a share code.
     */
    @FXML
    public void handleJoinProject() {
        if (!collaborationEnabled) {
            showAlert("Collaboration Unavailable", "Firebase is not configured.");
            return;
        }
        
        // Prompt for share code
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Join Project");
        dialog.setHeaderText("Join a collaborative project");
        dialog.setContentText("Enter share code (e.g., ABC-123):");
        
        dialog.showAndWait().ifPresent(code -> {
            try {
                User currentUser = UserSession.getInstance().getCurrentUser();
                if (currentUser == null) {
                    showAlert("Error", "User session not found.");
                    return;
                }
                
                // Join project
                Project project = projectService.joinProject(
                    code.trim().toUpperCase(),
                    String.valueOf(currentUser.getId()),
                    currentUser.getUsername()
                );
                
                if (project == null) {
                    showAlert("Invalid Code", "No project found with code: " + code);
                    return;
                }
                
                currentProject = project;
                
                // Initialize collaboration
                collaborationService.initializeProject(
                    project.getId(),
                    String.valueOf(currentUser.getId())
                );
                
                // Setup presence listener
                setupPresenceListener();
                
                // Save project to session
                SessionService.updateCurrentProject(currentUser.getId(), project.getId());
                
                // Open project workspace
                File projectFolder = new File(project.getWorkspacePath());
                if (projectFolder.exists() && projectFolder.isDirectory()) {
                    currentFolder = projectFolder;
                    loadFolderIntoTree(projectFolder);
                    updateTerminalDirectory(projectFolder);
                }
                
                showAlert("Success", "Joined project: " + project.getName());
                statusLabel.setText("Joined: " + project.getName());
                
            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Error", "Failed to join project: " + e.getMessage());
            }
        });
    }
    
    /**
     * Display the shareable project code.
     */
    private void showProjectCode(Project project) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Project Created");
        alert.setHeaderText("Share this code with collaborators:");
        alert.setContentText("Project Code: " + project.getCode() + "\n\n" +
                "Others can join by clicking File > Join Project and entering this code.");
        
        // Make the code selectable
        Label codeLabel = new Label(project.getCode());
        codeLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #00aaff;");
        
        VBox content = new VBox(10);
        content.getChildren().addAll(
            new Label("Share this code with collaborators:"),
            codeLabel,
            new Label("Others can join by using File > Join Project")
        );
        content.setPadding(new Insets(10));
        
        alert.getDialogPane().setContent(content);
        alert.showAndWait();
    }
    
    /**
     * Setup listener for online members and their activity.
     */
    private void setupPresenceListener() {
        if (collaborationService == null || currentProject == null) {
            return;
        }
        
        collaborationService.listenToPresence(members -> {
            onlineMembers = members;
            updateCollaboratorStatus();
        });
    }
    
    /**
     * Update status label to show online collaborators.
     */
    private void updateCollaboratorStatus() {
        long onlineCount = onlineMembers.values().stream()
                .filter(ProjectMember::isOnline)
                .count();
        
        if (onlineCount > 1) { // More than just the current user
            String names = onlineMembers.values().stream()
                    .filter(ProjectMember::isOnline)
                    .map(ProjectMember::getUsername)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            
            statusLabel.setText("📡 " + onlineCount + " online: " + names);
        }
    }
    
    /**
     * Enable file synchronization for the currently open file.
     */
    private void enableFileSync(File file) {
        if (collaborationService == null || currentProject == null || file == null) {
            return;
        }
        
        String relativePath = getRelativePath(currentProject.getWorkspacePath(), file.getAbsolutePath());
        
        // Listen for remote changes - show notification instead of auto-applying
        collaborationService.listenToFile(relativePath, newContent -> {
            if (!codeArea.getText().equals(newContent)) {
                pendingRemoteContent = newContent;
                
                // Show notification that file was updated by someone else
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("File Updated");
                    alert.setHeaderText("This file was modified by another collaborator");
                    alert.setContentText("Do you want to reload the file? Your unsaved changes will be lost.");
                    
                    ButtonType reloadBtn = new ButtonType("Reload");
                    ButtonType ignoreBtn = new ButtonType("Keep My Version", ButtonBar.ButtonData.CANCEL_CLOSE);
                    alert.getButtonTypes().setAll(reloadBtn, ignoreBtn);
                    
                    alert.showAndWait().ifPresent(response -> {
                        if (response == reloadBtn) {
                            // Apply remote changes
                            int caretPos = codeArea.getCaretPosition();
                            codeArea.replaceText(newContent);
                            if (caretPos <= newContent.length()) {
                                codeArea.moveTo(caretPos);
                            }
                            
                            // Write to disk
                            try {
                                Files.writeString(file.toPath(), newContent);
                                statusLabel.setText("Reloaded: " + file.getName());
                                pendingRemoteContent = null;
                            } catch (IOException e) {
                                System.err.println("Error writing reloaded file: " + e.getMessage());
                            }
                        } else {
                            statusLabel.setText("Ignored remote changes - save to override");
                        }
                    });
                });
            }
        });
        
        // Track cursor position and update presence in real-time
        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            if (currentProject != null) {
                collaborationService.updateMyPresence(relativePath, newPos.intValue());
            }
        });
        
        // Update presence when file is opened
        collaborationService.updateMyPresence(relativePath, codeArea.getCaretPosition());
    }
    
    /**
     * Get relative path from workspace root.
     */
    private String getRelativePath(String basePath, String fullPath) {
        File base = new File(basePath);
        File full = new File(fullPath);
        return base.toURI().relativize(full.toURI()).getPath();
    }
    
    /**
     * Show alert dialog.
     */
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * View all online members in the current project.
     */
    @FXML
    public void handleViewMembers() {
        if (!collaborationEnabled) {
            showAlert("Collaboration Unavailable", "Firebase is not configured.");
            return;
        }
        
        if (currentProject == null) {
            showAlert("No Active Project", "Please create or join a project first.");
            return;
        }
        
        // Create dialog to show members
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Project Members");
        alert.setHeaderText("Project: " + currentProject.getName() + " (" + currentProject.getCode() + ")");
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        if (onlineMembers.isEmpty()) {
            content.getChildren().add(new Label("No members found. Waiting for updates..."));
        } else {
            for (ProjectMember member : onlineMembers.values()) {
                HBox memberBox = new HBox(10);
                memberBox.setStyle("-fx-padding: 5; -fx-background-color: #f0f0f0; -fx-background-radius: 5;");
                
                String status = member.isOnline() ? "🟢" : "⚫";
                String roleIcon = member.getRole() == ProjectMember.Role.OWNER ? "👑" : 
                                 member.getRole() == ProjectMember.Role.EDITOR ? "✏️" : "👁️";
                
                Label statusLabel = new Label(status);
                Label nameLabel = new Label(member.getUsername());
                nameLabel.setStyle("-fx-font-weight: bold;");
                Label roleLabel = new Label(roleIcon + " " + member.getRole());
                
                VBox memberInfo = new VBox(2);
                memberInfo.getChildren().add(nameLabel);
                
                if (member.isOnline() && member.getCurrentFile() != null) {
                    Label fileLabel = new Label("Editing: " + member.getCurrentFile());
                    fileLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");
                    memberInfo.getChildren().add(fileLabel);
                }
                
                memberBox.getChildren().addAll(statusLabel, memberInfo, new Label("  "), roleLabel);
                content.getChildren().add(memberBox);
            }
        }
        
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setMinWidth(400);
        alert.showAndWait();
    }
    
    /**
     * View change history for the current project (owner only).
     */
    @FXML
    public void handleViewChangeHistory() {
        if (!collaborationEnabled) {
            showAlert("Collaboration Unavailable", "Firebase is not configured.");
            return;
        }
        
        if (currentProject == null) {
            showAlert("No Active Project", "Please create or join a project first.");
            return;
        }
        
        try {
            // Get current user ID
            User currentUser = UserSession.getInstance().getCurrentUser();
            if (currentUser == null) {
                showAlert("Error", "User session not found.");
                return;
            }
            
            String userId = String.valueOf(currentUser.getId());
            
            // Load change history view
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("changehistory.fxml")
            );
            javafx.scene.Parent root = loader.load();
            
            ChangeHistoryController controller = loader.getController();
            
            // Create new stage for change history
            Stage historyStage = new Stage();
            historyStage.setTitle("Change History - " + currentProject.getName());
            historyStage.setScene(new javafx.scene.Scene(root, 1000, 700));
            controller.setStage(historyStage);
            
            // Set project context with correct user ID
            controller.setProject(currentProject.getId(), userId);
            
            historyStage.show();
        } catch (Exception e) {
            showError("Failed to open change history: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // ==================== PENDING PROJECTS METHODS ====================
    
    /**
     * Initialize pending projects listener for the current user.
     * Called after user authentication.
     */
    public void initializePendingProjects() {
        if (!collaborationEnabled || pendingProjectService == null) {
            System.out.println("Pending projects not available - collaboration disabled");
            return;
        }
        
        User currentUser = UserSession.getInstance().getCurrentUser();
        if (currentUser == null) {
            System.err.println("Cannot initialize pending projects - no user session");
            return;
        }
        
        // Use Firebase UID for Firestore queries (shared with Android app)
        String firebaseUid = currentUser.getFirebaseUid();
        if (firebaseUid == null || firebaseUid.isEmpty()) {
            System.err.println("No Firebase UID found - cannot sync pending projects with mobile app");
            System.err.println("Please login with an account that was created/synced with Firebase");
            return;
        }
        
        System.out.println("=== INITIALIZING PENDING PROJECTS ===");
        System.out.println("Current User ID (local): " + currentUser.getId());
        System.out.println("Current Firebase UID: " + firebaseUid);
        System.out.println("Current Username: " + currentUser.getUsername());
        
        // Start listening for pending projects updates
        if (pendingProjectsListener != null) {
            pendingProjectsListener.remove();
        }
        
        pendingProjectsListener = pendingProjectService.listenToPendingProjects(firebaseUid, 
            new PendingProjectService.PendingProjectsListener() {
                @Override
                public void onUpdate(List<PendingProject> projects) {
                    System.out.println("Received pending projects update: " + projects.size() + " projects");
                    for (PendingProject p : projects) {
                        System.out.println("  - " + p.getPostTitle() + " (Status: " + p.getStatus() + ", DevID: " + p.getDeveloperId() + ")");
                    }
                    Platform.runLater(() -> updatePendingProjectsUI(projects));
                }
                
                @Override
                public void onError(Exception e) {
                    System.err.println("Error listening to pending projects: " + e.getMessage());
                    e.printStackTrace();
                    Platform.runLater(() -> showError("Failed to load pending projects: " + e.getMessage()));
                }
            });
        
        System.out.println("Pending projects listener initialized for Firebase UID: " + firebaseUid);
    }
    
    /**
     * Update the pending projects UI with the latest data.
     */
    private void updatePendingProjectsUI(List<PendingProject> projects) {
        if (pendingProjectsContainer == null) {
            System.err.println("Pending projects container not found in UI");
            return;
        }
        
        // Clear existing items
        pendingProjectsContainer.getChildren().clear();
        
        // Update count
        if (pendingCountLabel != null) {
            pendingCountLabel.setText("(" + projects.size() + ")");
        }
        
        if (projects.isEmpty()) {
            // Show empty state
            Label emptyLabel = new Label("No pending projects");
            emptyLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px; -fx-padding: 20;");
            pendingProjectsContainer.getChildren().add(emptyLabel);
            return;
        }
        
        // Create UI card for each pending project
        for (PendingProject project : projects) {
            VBox projectCard = createPendingProjectCard(project);
            pendingProjectsContainer.getChildren().add(projectCard);
        }
    }
    
    /**
     * Create a visual card for a pending project.
     */
    private VBox createPendingProjectCard(PendingProject project) {
        VBox card = new VBox(5);
        card.setStyle(
            "-fx-background-color: rgba(45, 45, 48, 0.8); " +
            "-fx-border-color: rgba(255, 255, 255, 0.2); " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 5; " +
            "-fx-background-radius: 5; " +
            "-fx-padding: 10; " +
            "-fx-cursor: hand;"
        );
        
        // Project name
        Label nameLabel = new Label(project.getPostTitle());
        nameLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 13px;");
        nameLabel.setWrapText(true);
        
        // Description
        if (project.getPostDescription() != null && !project.getPostDescription().isEmpty()) {
            Label descLabel = new Label(project.getPostDescription());
            descLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 11px;");
            descLabel.setWrapText(true);
            descLabel.setMaxHeight(40);
            card.getChildren().add(descLabel);
        }
        
        // Status
        HBox statusBox = new HBox(5);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        
        Label statusLabel = new Label(project.getStatus());
        String statusColor = getStatusColor(project.getStatus());
        statusLabel.setStyle(
            "-fx-background-color: " + statusColor + "; " +
            "-fx-text-fill: white; " +
            "-fx-padding: 2 6; " +
            "-fx-font-size: 10px; " +
            "-fx-background-radius: 3;"
        );
        
        statusBox.getChildren().add(statusLabel);
        
        // GitHub Repo Link input field
        TextField githubLinkField = new TextField();
        githubLinkField.setPromptText("Paste GitHub repo link...");
        githubLinkField.setStyle(
            "-fx-background-color: rgba(255, 255, 255, 0.1); " +
            "-fx-text-fill: white; " +
            "-fx-prompt-text-fill: rgba(255, 255, 255, 0.5); " +
            "-fx-font-size: 11px; " +
            "-fx-padding: 4 8; " +
            "-fx-border-color: rgba(255, 255, 255, 0.3); " +
            "-fx-border-radius: 3; " +
            "-fx-background-radius: 3;"
        );
        githubLinkField.setPrefWidth(200);
        
        // Pre-fill if already has a link
        if (project.getGithubRepoLink() != null && !project.getGithubRepoLink().isEmpty()) {
            githubLinkField.setText(project.getGithubRepoLink());
        }
        
        // Action buttons
        HBox buttonBox = new HBox(5);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        
        Button startBtn = new Button("Start");
        startBtn.setStyle("-fx-background-color: #007acc; -fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 4 8;");
        startBtn.setOnAction(e -> handleStartPendingProject(project));
        
        Button submitBtn = new Button("Submit for Review");
        submitBtn.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 4 8;");
        // Initially disabled unless there's already a link
        submitBtn.setDisable(project.getGithubRepoLink() == null || project.getGithubRepoLink().trim().isEmpty());
        submitBtn.setOnAction(e -> handleSubmitPendingProject(project, githubLinkField.getText()));
        
        // Enable submit button only when GitHub link is entered
        githubLinkField.textProperty().addListener((obs, oldVal, newVal) -> {
            submitBtn.setDisable(newVal == null || newVal.trim().isEmpty());
        });
        
        // If project is already submitted or rejected, show different buttons
        if ("SUBMITTED_FOR_REVIEW".equals(project.getStatus())) {
            buttonBox.getChildren().clear();
            Label submittedLabel = new Label("⏳ Awaiting review...");
            submittedLabel.setStyle("-fx-text-fill: #ffc107; -fx-font-size: 11px; -fx-font-weight: bold;");
            buttonBox.getChildren().add(submittedLabel);
        } else if ("REJECTED".equals(project.getStatus())) {
            Label rejectedLabel = new Label("❌ Rejected - Please revise and resubmit");
            rejectedLabel.setStyle("-fx-text-fill: #dc3545; -fx-font-size: 11px; -fx-font-weight: bold;");
            card.getChildren().add(rejectedLabel);
            buttonBox.getChildren().addAll(startBtn, submitBtn);
        } else {
            buttonBox.getChildren().addAll(startBtn, submitBtn);
        }
        
        // Add hover effect
        card.setOnMouseEntered(e -> card.setStyle(
            "-fx-background-color: rgba(60, 60, 65, 0.9); " +
            "-fx-border-color: rgba(255, 255, 255, 0.3); " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 5; " +
            "-fx-background-radius: 5; " +
            "-fx-padding: 10; " +
            "-fx-cursor: hand;"
        ));
        
        card.setOnMouseExited(e -> card.setStyle(
            "-fx-background-color: rgba(45, 45, 48, 0.8); " +
            "-fx-border-color: rgba(255, 255, 255, 0.2); " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 5; " +
            "-fx-background-radius: 5; " +
            "-fx-padding: 10; " +
            "-fx-cursor: hand;"
        ));
        
        card.getChildren().addAll(nameLabel, statusBox, githubLinkField, buttonBox);
        
        return card;
    }
    
    /**
     * Get color for project status.
     */
    private String getStatusColor(String status) {
        switch (status) {
            case "PENDING": return "#6c757d";
            case "IN_PROGRESS": return "#007acc";
            case "SUBMITTED_FOR_REVIEW": return "#ffc107";
            case "REJECTED": return "#dc3545";
            case "COMPLETED": return "#28a745";
            default: return "#6c757d";
        }
    }
    
    /**
     * Get color for priority.
     */
    private String getPriorityColor(String priority) {
        switch (priority) {
            case "LOW": return "#28a745";
            case "MEDIUM": return "#ffc107";
            case "HIGH": return "#dc3545";
            default: return "#6c757d";
        }
    }
    
    /**
     * Handle starting work on a pending project.
     */
    private void handleStartPendingProject(PendingProject project) {
        pendingProjectService.markAsInProgress(project.getId())
            .thenAccept(v -> {
                Platform.runLater(() -> {
                    showInfo("Project '" + project.getPostTitle() + "' marked as in progress.");
                });
            })
            .exceptionally(e -> {
                Platform.runLater(() -> {
                    showError("Failed to update project status: " + e.getMessage());
                });
                return null;
            });
    }
    
    /**
     * Handle completing a pending project.
     */
    private void handleCompletePendingProject(PendingProject project) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Complete Project");
        confirmAlert.setHeaderText("Mark as Completed");
        confirmAlert.setContentText("Are you sure you want to mark '" + project.getPostTitle() + "' as completed?");
        
        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                pendingProjectService.markAsCompleted(project.getId())
                    .thenAccept(v -> {
                        Platform.runLater(() -> {
                            showInfo("Project '" + project.getPostTitle() + "' marked as completed!");
                        });
                    })
                    .exceptionally(e -> {
                        Platform.runLater(() -> {
                            showError("Failed to complete project: " + e.getMessage());
                        });
                        return null;
                    });
            }
        });
    }
    
    /**
     * Handle submitting a pending project for review with GitHub repo link.
     */
    private void handleSubmitPendingProject(PendingProject project, String githubRepoLink) {
        if (githubRepoLink == null || githubRepoLink.trim().isEmpty()) {
            showError("Please enter a GitHub repository link before submitting.");
            return;
        }
        
        // Validate GitHub URL format
        if (!githubRepoLink.toLowerCase().contains("github.com")) {
            Alert confirmAlert = new Alert(Alert.AlertType.WARNING);
            confirmAlert.setTitle("Invalid URL");
            confirmAlert.setHeaderText("The URL doesn't appear to be a GitHub link");
            confirmAlert.setContentText("Do you want to submit anyway?");
            
            if (confirmAlert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }
        
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Submit for Review");
        confirmAlert.setHeaderText("Submit '" + project.getPostTitle() + "' for Review");
        confirmAlert.setContentText(
            "GitHub Repository: " + githubRepoLink + "\n\n" +
            "This will notify the project poster on their mobile app for review. " +
            "They can accept or reject your submission.\n\n" +
            "Are you sure you want to submit?"
        );
        
        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // Get developer name
                String developerName = username != null ? username : "Unknown Developer";
                
                pendingProjectService.submitForReview(project.getId(), githubRepoLink.trim(), developerName)
                    .thenAccept(v -> {
                        Platform.runLater(() -> {
                            showInfo("Project '" + project.getPostTitle() + "' submitted for review!\nThe poster will be notified on their mobile app.");
                        });
                    })
                    .exceptionally(e -> {
                        Platform.runLater(() -> {
                            showError("Failed to submit project: " + e.getMessage());
                        });
                        return null;
                    });
            }
        });
    }
}



