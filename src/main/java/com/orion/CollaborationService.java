package com.orion;

import com.google.cloud.firestore.*;
import javafx.application.Platform;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Service for real-time collaboration features using Firestore listeners.
 * Handles file synchronization, user presence tracking, and cursor position sharing.
 */
public class CollaborationService {
    private static final String PROJECTS_COLLECTION = "projects";
    private static final String FILES_SUBCOLLECTION = "files";
    private static final String MEMBERS_SUBCOLLECTION = "members";
    
    private final Firestore firestore;
    private final ProjectService projectService;
    
    // Active listeners for cleanup
    private final Map<String, ListenerRegistration> fileListeners = new ConcurrentHashMap<>();
    private final Map<String, ListenerRegistration> memberListeners = new ConcurrentHashMap<>();
    
    // Current project context
    private String currentProjectId;
    private String currentUserId;
    
    // Cache of current file contents for diff computation
    private final Map<String, String> fileContentCache = new ConcurrentHashMap<>();

    public CollaborationService() {
        this.firestore = FirebaseService.getInstance().getFirestore();
        this.projectService = new ProjectService();
    }

    /**
     * Initialize collaboration for a project.
     * Sets up listeners for real-time updates.
     */
    public void initializeProject(String projectId, String userId) {
        this.currentProjectId = projectId;
        this.currentUserId = userId;
        
        // Set user as online
        projectService.updateMemberPresence(projectId, userId, true, null, 0);
        
        System.out.println("Collaboration initialized for project: " + projectId);
    }

    /**
     * Listen for file content changes in the project.
     * 
     * @param filePath Relative file path in the project
     * @param onFileChange Callback when file content changes
     */
    public void listenToFile(String filePath, Consumer<String> onFileChange) {
        if (currentProjectId == null) {
            System.err.println("Project not initialized. Call initializeProject() first.");
            return;
        }
        
        // Remove existing listener if any
        stopListeningToFile(filePath);
        
        DocumentReference fileDoc = firestore.collection(PROJECTS_COLLECTION)
                .document(currentProjectId)
                .collection(FILES_SUBCOLLECTION)
                .document(sanitizeFilePath(filePath));
        
        ListenerRegistration listener = fileDoc.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                System.err.println("Error listening to file changes: " + error.getMessage());
                return;
            }
            
            if (snapshot != null && snapshot.exists()) {
                String content = snapshot.getString("content");
                String lastModifiedBy = snapshot.getString("lastModifiedBy");
                
                // Don't apply changes made by the current user to avoid infinite loops
                if (!currentUserId.equals(lastModifiedBy) && content != null) {
                    Platform.runLater(() -> onFileChange.accept(content));
                }
            }
        });
        
        fileListeners.put(filePath, listener);
        System.out.println("Listening to file: " + filePath);
    }

    /**
     * Stop listening to file changes.
     */
    public void stopListeningToFile(String filePath) {
        ListenerRegistration listener = fileListeners.remove(filePath);
        if (listener != null) {
            listener.remove();
            System.out.println("Stopped listening to file: " + filePath);
        }
    }

    /**
     * Update file content in Firestore using delta-based approach.
     * Only stores the changes (diff) instead of full content.
     * 
     * @param filePath Relative file path
     * @param newContent New file content
     * @param username Username of the person making the change
     */
    public void updateFileContent(String filePath, String newContent, String username) {
        if (currentProjectId == null || currentUserId == null) {
            System.err.println("Project not initialized.");
            return;
        }
        
        // Get previous content from SQLite (last saved state)
        String oldContent = DatabaseManager.getFileSnapshot(currentProjectId, filePath);
        if (oldContent == null) {
            oldContent = "";
        }
        
        // Compute diff between last saved state and new content
        DiffUtils.DiffResult diffResult = DiffUtils.computeDiff(oldContent, newContent);
        
        // Only save if there are actual changes
        if (!diffResult.hasChanges()) {
            System.out.println("No changes detected for file: " + filePath);
            return;
        }
        
        System.out.println("Changes detected: +" + diffResult.getLinesAdded() + " -" + diffResult.getLinesRemoved());
        
        // Save new content to SQLite as the new baseline
        DatabaseManager.saveFileSnapshot(currentProjectId, filePath, newContent);
        
        // Update in-memory cache for real-time sync
        fileContentCache.put(filePath, newContent);
        
        // Save ONLY the delta/changes to Firebase history
        ChangeHistory change = new ChangeHistory(
            currentProjectId, 
            filePath, 
            currentUserId, 
            username,
            oldContent.isEmpty() ? ChangeHistory.ChangeType.CREATE : ChangeHistory.ChangeType.MODIFY,
            diffResult.getDiff(),
            diffResult.getLinesAdded(),
            diffResult.getLinesRemoved()
        );
        
        saveChangeHistory(change);
        
        // For real-time sync, update the file document with latest content
        // (needed for new collaborators to get the current state)
        DocumentReference fileDoc = firestore.collection(PROJECTS_COLLECTION)
                .document(currentProjectId)
                .collection(FILES_SUBCOLLECTION)
                .document(sanitizeFilePath(filePath));
        
        Map<String, Object> fileData = new HashMap<>();
        fileData.put("path", filePath);
        fileData.put("content", newContent); // Current state for sync
        fileData.put("lastModifiedBy", currentUserId);
        fileData.put("lastModifiedAt", FieldValue.serverTimestamp());
        
        fileDoc.set(fileData, SetOptions.merge());
    }
    
    /**
     * Save change history to Firestore.
     */
    private void saveChangeHistory(ChangeHistory change) {
        CollectionReference historyRef = firestore.collection(PROJECTS_COLLECTION)
                .document(currentProjectId)
                .collection("changeHistory");
        
        Map<String, Object> historyData = new HashMap<>();
        historyData.put("projectId", change.getProjectId());
        historyData.put("filePath", change.getFilePath());
        historyData.put("userId", change.getUserId());
        historyData.put("username", change.getUsername());
        historyData.put("timestamp", FieldValue.serverTimestamp());
        historyData.put("changeType", change.getChangeType().toString());
        historyData.put("delta", change.getDelta());
        historyData.put("linesAdded", change.getLinesAdded());
        historyData.put("linesRemoved", change.getLinesRemoved());
        
        historyRef.add(historyData);
    }
    
    /**
     * Load file content into cache for diff computation.
     */
    public void loadFileIntoCache(String filePath, String content) {
        fileContentCache.put(filePath, content);
        // Also save to SQLite as baseline
        if (currentProjectId != null) {
            DatabaseManager.saveFileSnapshot(currentProjectId, filePath, content);
        }
    }

    /**
     * Listen for presence updates (who's online, what they're editing).
     * 
     * @param onPresenceChange Callback with list of online members
     */
    public void listenToPresence(Consumer<Map<String, ProjectMember>> onPresenceChange) {
        if (currentProjectId == null) {
            System.err.println("Project not initialized.");
            return;
        }
        
        CollectionReference membersRef = firestore.collection(PROJECTS_COLLECTION)
                .document(currentProjectId)
                .collection(MEMBERS_SUBCOLLECTION);
        
        ListenerRegistration listener = membersRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                System.err.println("Error listening to presence: " + error.getMessage());
                return;
            }
            
            if (snapshot != null) {
                Map<String, ProjectMember> members = new HashMap<>();
                
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    ProjectMember member = new ProjectMember();
                    member.setUserId(doc.getString("userId"));
                    member.setUsername(doc.getString("username"));
                    member.setRole(ProjectMember.Role.valueOf(doc.getString("role")));
                    member.setOnline(doc.getBoolean("isOnline"));
                    member.setCurrentFile(doc.getString("currentFile"));
                    Long cursorPos = doc.getLong("cursorPosition");
                    member.setCursorPosition(cursorPos != null ? cursorPos.intValue() : 0);
                    
                    members.put(member.getUserId(), member);
                }
                
                Platform.runLater(() -> onPresenceChange.accept(members));
            }
        });
        
        memberListeners.put("presence", listener);
        System.out.println("Listening to member presence updates");
    }

    /**
     * Update current user's file and cursor position.
     */
    public void updateMyPresence(String currentFile, int cursorPosition) {
        if (currentProjectId == null || currentUserId == null) {
            return;
        }
        
        projectService.updateMemberPresence(currentProjectId, currentUserId, 
                true, currentFile, cursorPosition);
    }

    /**
     * Broadcast that the user is typing (for showing typing indicators).
     */
    public void broadcastTyping(String filePath, boolean isTyping) {
        if (currentProjectId == null || currentUserId == null) {
            return;
        }
        
        DocumentReference typingDoc = firestore.collection(PROJECTS_COLLECTION)
                .document(currentProjectId)
                .collection("typing")
                .document(currentUserId);
        
        if (isTyping) {
            Map<String, Object> typingData = new HashMap<>();
            typingData.put("userId", currentUserId);
            typingData.put("filePath", filePath);
            typingData.put("timestamp", FieldValue.serverTimestamp());
            typingDoc.set(typingData);
        } else {
            typingDoc.delete();
        }
    }

    /**
     * Listen for typing indicators from other users.
     */
    public void listenToTyping(Consumer<Map<String, String>> onTypingChange) {
        if (currentProjectId == null) {
            return;
        }
        
        CollectionReference typingRef = firestore.collection(PROJECTS_COLLECTION)
                .document(currentProjectId)
                .collection("typing");
        
        ListenerRegistration listener = typingRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                System.err.println("Error listening to typing: " + error.getMessage());
                return;
            }
            
            if (snapshot != null) {
                Map<String, String> typingUsers = new HashMap<>();
                
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    String userId = doc.getString("userId");
                    String filePath = doc.getString("filePath");
                    
                    if (!currentUserId.equals(userId)) {
                        typingUsers.put(userId, filePath);
                    }
                }
                
                Platform.runLater(() -> onTypingChange.accept(typingUsers));
            }
        });
        
        memberListeners.put("typing", listener);
    }

    /**
     * Set user as offline when leaving the project.
     */
    public void leaveProject() {
        if (currentProjectId != null && currentUserId != null) {
            System.out.println("Leaving project: " + currentProjectId);
            
            // Set user as offline
            try {
                projectService.updateMemberPresence(currentProjectId, currentUserId, 
                        false, null, 0);
            } catch (Exception e) {
                System.err.println("Error updating presence: " + e.getMessage());
            }
            
            // Remove all file listeners
            System.out.println("Removing " + fileListeners.size() + " file listeners...");
            fileListeners.values().forEach(listener -> {
                try {
                    listener.remove();
                } catch (Exception e) {
                    System.err.println("Error removing file listener: " + e.getMessage());
                }
            });
            fileListeners.clear();
            
            // Remove all member listeners
            System.out.println("Removing " + memberListeners.size() + " member listeners...");
            memberListeners.values().forEach(listener -> {
                try {
                    listener.remove();
                } catch (Exception e) {
                    System.err.println("Error removing member listener: " + e.getMessage());
                }
            });
            memberListeners.clear();
            
            // Clear cache
            fileContentCache.clear();
            
            System.out.println("Left project successfully");
            currentProjectId = null;
            currentUserId = null;
        }
    }

    /**
     * Clean up all listeners and connections.
     */
    public void shutdown() {
        System.out.println("Shutting down CollaborationService...");
        leaveProject();
        System.out.println("CollaborationService shutdown complete");
    }

    /**
     * Sanitize file path for use as Firestore document ID.
     * Firestore document IDs cannot contain certain characters like '/', '\', etc.
     */
    private String sanitizeFilePath(String filePath) {
        return filePath.replace("/", "_").replace("\\", "_").replace(":", "_");
    }

    // Getters
    public String getCurrentProjectId() {
        return currentProjectId;
    }

    public String getCurrentUserId() {
        return currentUserId;
    }
}
