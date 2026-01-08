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
     * Update file content in Firestore.
     * This will trigger listeners for other users.
     * 
     * @param filePath Relative file path
     * @param content New file content
     */
    public void updateFileContent(String filePath, String content) {
        if (currentProjectId == null || currentUserId == null) {
            System.err.println("Project not initialized.");
            return;
        }
        
        DocumentReference fileDoc = firestore.collection(PROJECTS_COLLECTION)
                .document(currentProjectId)
                .collection(FILES_SUBCOLLECTION)
                .document(sanitizeFilePath(filePath));
        
        Map<String, Object> fileData = new HashMap<>();
        fileData.put("path", filePath);
        fileData.put("content", content);
        fileData.put("lastModifiedBy", currentUserId);
        fileData.put("lastModifiedAt", FieldValue.serverTimestamp());
        
        fileDoc.set(fileData, SetOptions.merge());
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
            projectService.updateMemberPresence(currentProjectId, currentUserId, 
                    false, null, 0);
            
            // Remove all listeners
            fileListeners.values().forEach(ListenerRegistration::remove);
            fileListeners.clear();
            
            memberListeners.values().forEach(ListenerRegistration::remove);
            memberListeners.clear();
            
            System.out.println("Left project: " + currentProjectId);
            currentProjectId = null;
            currentUserId = null;
        }
    }

    /**
     * Clean up all listeners and connections.
     */
    public void shutdown() {
        leaveProject();
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
