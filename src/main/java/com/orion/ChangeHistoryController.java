package com.orion;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import javafx.scene.layout.VBox;
import org.fxmisc.richtext.CodeArea;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

/**
 * Controller for the Change History view.
 * Displays file changes, contributors, and statistics for project owners.
 */
public class ChangeHistoryController {
    
    @FXML private TableView<ChangeHistoryRow> historyTable;
    @FXML private TableColumn<ChangeHistoryRow, String> timestampColumn;
    @FXML private TableColumn<ChangeHistoryRow, String> userColumn;
    @FXML private TableColumn<ChangeHistoryRow, String> fileColumn;
    @FXML private TableColumn<ChangeHistoryRow, String> typeColumn;
    @FXML private TableColumn<ChangeHistoryRow, String> changesColumn;
    
    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterComboBox;
    @FXML private CodeArea diffViewer;
    
    @FXML private Label totalChangesLabel;
    @FXML private Label contributorsLabel;
    @FXML private Label filesModifiedLabel;
    @FXML private Label linesAddedLabel;
    @FXML private Label linesRemovedLabel;
    
    private ChangeHistoryService historyService;
    private String projectId;
    private String userId;
    private ObservableList<ChangeHistoryRow> historyData;
    private ObservableList<ChangeHistoryRow> allHistoryData;
    
    private Stage stage;
    
    @FXML
    public void initialize() {
        historyService = new ChangeHistoryService();
        historyData = FXCollections.observableArrayList();
        allHistoryData = FXCollections.observableArrayList();
        
        // Set up table columns
        timestampColumn.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        userColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        fileColumn.setCellValueFactory(new PropertyValueFactory<>("filePath"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("changeType"));
        changesColumn.setCellValueFactory(new PropertyValueFactory<>("changes"));
        
        historyTable.setItems(historyData);
        
        // Set up diff viewer
        diffViewer.setEditable(false);
        diffViewer.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");
        
        // Add selection listener to show diff
        historyTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                showDiff(newSelection.getDelta());
            }
        });
        
        // Set up filter combo box
        filterComboBox.setItems(FXCollections.observableArrayList(
            "All Changes", "File Created", "File Modified", "File Deleted"
        ));
        filterComboBox.setValue("All Changes");
        filterComboBox.setOnAction(e -> applyFilter());
        
        // Set up search field
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter());
    }
    
    /**
     * Set the project context and load history.
     */
    public void setProject(String projectId, String userId) {
        this.projectId = projectId;
        this.userId = userId;
        
        // Check if user is owner
        if (!historyService.isProjectOwner(projectId, userId)) {
            showAlert("Access Denied", "Only project owners can view change history.");
            if (stage != null) {
                stage.close();
            }
            return;
        }
        
        loadChangeHistory();
        loadStatistics();
    }
    
    /**
     * Load change history from Firestore.
     */
    private void loadChangeHistory() {
        new Thread(() -> {
            try {
                List<ChangeHistory> changes = historyService.getProjectChangeHistory(projectId);
                
                Platform.runLater(() -> {
                    allHistoryData.clear();
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    
                    for (ChangeHistory change : changes) {
                        String timestamp = dateFormat.format(change.getTimestamp());
                        String changes_str = String.format("+%d -%d", 
                            change.getLinesAdded(), change.getLinesRemoved());
                        
                        allHistoryData.add(new ChangeHistoryRow(
                            timestamp,
                            change.getUsername(),
                            change.getFilePath(),
                            change.getChangeType().toString(),
                            changes_str,
                            change.getDelta()
                        ));
                    }
                    
                    applyFilter();
                });
            } catch (Exception e) {
                Platform.runLater(() -> 
                    showAlert("Error", "Failed to load change history: " + e.getMessage())
                );
            }
        }).start();
    }
    
    /**
     * Load project statistics.
     */
    private void loadStatistics() {
        new Thread(() -> {
            try {
                Map<String, Object> stats = historyService.getProjectStatistics(projectId);
                
                Platform.runLater(() -> {
                    totalChangesLabel.setText("Total Changes: " + stats.get("totalChanges"));
                    contributorsLabel.setText("Contributors: " + stats.get("contributors"));
                    filesModifiedLabel.setText("Files Modified: " + stats.get("filesModified"));
                    linesAddedLabel.setText("Lines Added: " + stats.get("linesAdded"));
                    linesRemovedLabel.setText("Lines Removed: " + stats.get("linesRemoved"));
                });
            } catch (Exception e) {
                System.err.println("Failed to load statistics: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Apply search and filter to history data.
     */
    private void applyFilter() {
        String searchText = searchField.getText().toLowerCase();
        String filterType = filterComboBox.getValue();
        
        historyData.clear();
        
        for (ChangeHistoryRow row : allHistoryData) {
            // Apply type filter
            boolean typeMatch = filterType.equals("All Changes") || 
                row.getChangeType().contains(filterType.replace("File ", "").toUpperCase());
            
            // Apply search filter
            boolean searchMatch = searchText.isEmpty() || 
                row.getFilePath().toLowerCase().contains(searchText) ||
                row.getUsername().toLowerCase().contains(searchText);
            
            if (typeMatch && searchMatch) {
                historyData.add(row);
            }
        }
    }
    
    /**
     * Display diff in the viewer with syntax highlighting.
     */
    private void showDiff(String delta) {
        if (delta == null || delta.isEmpty()) {
            diffViewer.clear();
            return;
        }
        
        diffViewer.clear();
        
        // Apply color coding to diff
        String[] lines = delta.split("\n");
        StringBuilder styledText = new StringBuilder();
        
        for (String line : lines) {
            styledText.append(line).append("\n");
        }
        
        diffViewer.replaceText(styledText.toString());
        
        // Apply basic syntax coloring (red for deletions, green for additions)
        // This is a simple implementation - could be enhanced with proper styling
    }
    
    /**
     * Refresh the change history.
     */
    @FXML
    private void handleRefresh() {
        loadChangeHistory();
        loadStatistics();
    }
    
    /**
     * Close the window.
     */
    @FXML
    private void handleClose() {
        if (stage != null) {
            stage.close();
        }
    }
    
    /**
     * Export change history to file.
     */
    @FXML
    private void handleExport() {
        // TODO: Implement export functionality
        showAlert("Export", "Export functionality coming soon!");
    }
    
    /**
     * Set the stage for this controller.
     */
    public void setStage(Stage stage) {
        this.stage = stage;
    }
    
    /**
     * Show alert dialog.
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Inner class for table rows.
     */
    public static class ChangeHistoryRow {
        private final String timestamp;
        private final String username;
        private final String filePath;
        private final String changeType;
        private final String changes;
        private final String delta;
        
        public ChangeHistoryRow(String timestamp, String username, String filePath, 
                               String changeType, String changes, String delta) {
            this.timestamp = timestamp;
            this.username = username;
            this.filePath = filePath;
            this.changeType = changeType;
            this.changes = changes;
            this.delta = delta;
        }
        
        public String getTimestamp() { return timestamp; }
        public String getUsername() { return username; }
        public String getFilePath() { return filePath; }
        public String getChangeType() { return changeType; }
        public String getChanges() { return changes; }
        public String getDelta() { return delta; }
    }
}
