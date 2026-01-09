package com.orion;

import com.google.cloud.firestore.*;
import javafx.application.Platform;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Service for managing and retrieving change history from Firestore.
 * Provides access to project change logs for owners.
 */
public class ChangeHistoryService {
    private static final String PROJECTS_COLLECTION = "projects";
    private static final String HISTORY_SUBCOLLECTION = "changeHistory";
    
    private final Firestore firestore;
    private final ProjectService projectService;
    
    public ChangeHistoryService() {
        this.firestore = FirebaseService.getInstance().getFirestore();
        this.projectService = new ProjectService();
    }
    
    /**
     * Get all change history for a project.
     * 
     * @param projectId The project ID
     * @return List of change history entries, sorted by timestamp (most recent first)
     */
    public List<ChangeHistory> getProjectChangeHistory(String projectId) throws ExecutionException, InterruptedException {
        CollectionReference historyRef = firestore.collection(PROJECTS_COLLECTION)
                .document(projectId)
                .collection(HISTORY_SUBCOLLECTION);
        
        // Query ordered by timestamp descending
        Query query = historyRef.orderBy("timestamp", Query.Direction.DESCENDING).limit(1000);
        
        List<QueryDocumentSnapshot> documents = query.get().get().getDocuments();
        List<ChangeHistory> changes = new ArrayList<>();
        
        for (QueryDocumentSnapshot doc : documents) {
            ChangeHistory change = documentToChangeHistory(doc);
            changes.add(change);
        }
        
        return changes;
    }
    
    /**
     * Get change history for a specific file.
     * 
     * @param projectId The project ID
     * @param filePath The file path
     * @return List of change history entries for the file
     */
    public List<ChangeHistory> getFileChangeHistory(String projectId, String filePath) 
            throws ExecutionException, InterruptedException {
        CollectionReference historyRef = firestore.collection(PROJECTS_COLLECTION)
                .document(projectId)
                .collection(HISTORY_SUBCOLLECTION);
        
        Query query = historyRef.whereEqualTo("filePath", filePath)
                .orderBy("timestamp", Query.Direction.DESCENDING);
        
        List<QueryDocumentSnapshot> documents = query.get().get().getDocuments();
        List<ChangeHistory> changes = new ArrayList<>();
        
        for (QueryDocumentSnapshot doc : documents) {
            ChangeHistory change = documentToChangeHistory(doc);
            changes.add(change);
        }
        
        return changes;
    }
    
    /**
     * Get change history by user.
     * 
     * @param projectId The project ID
     * @param userId The user ID
     * @return List of changes made by the user
     */
    public List<ChangeHistory> getUserChangeHistory(String projectId, String userId) 
            throws ExecutionException, InterruptedException {
        CollectionReference historyRef = firestore.collection(PROJECTS_COLLECTION)
                .document(projectId)
                .collection(HISTORY_SUBCOLLECTION);
        
        Query query = historyRef.whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING);
        
        List<QueryDocumentSnapshot> documents = query.get().get().getDocuments();
        List<ChangeHistory> changes = new ArrayList<>();
        
        for (QueryDocumentSnapshot doc : documents) {
            ChangeHistory change = documentToChangeHistory(doc);
            changes.add(change);
        }
        
        return changes;
    }
    
    /**
     * Listen for real-time change history updates.
     * 
     * @param projectId The project ID
     * @param onHistoryUpdate Callback when new changes are detected
     * @return ListenerRegistration for cleanup
     */
    public ListenerRegistration listenToChangeHistory(String projectId, 
            Consumer<List<ChangeHistory>> onHistoryUpdate) {
        CollectionReference historyRef = firestore.collection(PROJECTS_COLLECTION)
                .document(projectId)
                .collection(HISTORY_SUBCOLLECTION);
        
        Query query = historyRef.orderBy("timestamp", Query.Direction.DESCENDING).limit(100);
        
        return query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                System.err.println("Error listening to change history: " + error.getMessage());
                return;
            }
            
            if (snapshot != null) {
                List<ChangeHistory> changes = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
                    ChangeHistory change = documentToChangeHistory(doc);
                    changes.add(change);
                }
                
                Platform.runLater(() -> onHistoryUpdate.accept(changes));
            }
        });
    }
    
    /**
     * Get summary statistics for a project.
     * 
     * @param projectId The project ID
     * @return Map of statistics (total changes, contributors, etc.)
     */
    public Map<String, Object> getProjectStatistics(String projectId) 
            throws ExecutionException, InterruptedException {
        List<ChangeHistory> allChanges = getProjectChangeHistory(projectId);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalChanges", allChanges.size());
        
        Set<String> uniqueUsers = new HashSet<>();
        Set<String> uniqueFiles = new HashSet<>();
        int totalLinesAdded = 0;
        int totalLinesRemoved = 0;
        
        for (ChangeHistory change : allChanges) {
            uniqueUsers.add(change.getUserId());
            uniqueFiles.add(change.getFilePath());
            totalLinesAdded += change.getLinesAdded();
            totalLinesRemoved += change.getLinesRemoved();
        }
        
        stats.put("contributors", uniqueUsers.size());
        stats.put("filesModified", uniqueFiles.size());
        stats.put("linesAdded", totalLinesAdded);
        stats.put("linesRemoved", totalLinesRemoved);
        
        return stats;
    }
    
    /**
     * Check if user is project owner.
     * 
     * @param projectId The project ID
     * @param userId The user ID to check
     * @return true if user is the owner
     */
    public boolean isProjectOwner(String projectId, String userId) {
        try {
            Project project = projectService.getProject(projectId);
            return project != null && project.getOwnerId().equals(userId);
        } catch (Exception e) {
            System.err.println("Error checking project ownership: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Convert Firestore document to ChangeHistory object.
     */
    private ChangeHistory documentToChangeHistory(QueryDocumentSnapshot doc) {
        ChangeHistory change = new ChangeHistory();
        change.setChangeId(doc.getId());
        change.setProjectId(doc.getString("projectId"));
        change.setFilePath(doc.getString("filePath"));
        change.setUserId(doc.getString("userId"));
        change.setUsername(doc.getString("username"));
        change.setDelta(doc.getString("delta"));
        
        String changeTypeStr = doc.getString("changeType");
        if (changeTypeStr != null) {
            change.setChangeType(ChangeHistory.ChangeType.valueOf(changeTypeStr));
        }
        
        Long linesAdded = doc.getLong("linesAdded");
        Long linesRemoved = doc.getLong("linesRemoved");
        change.setLinesAdded(linesAdded != null ? linesAdded.intValue() : 0);
        change.setLinesRemoved(linesRemoved != null ? linesRemoved.intValue() : 0);
        
        com.google.cloud.Timestamp timestamp = doc.getTimestamp("timestamp");
        if (timestamp != null) {
            change.setTimestamp(timestamp.toDate());
        }
        
        return change;
    }
}
