package com.orion;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import javafx.application.Platform;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Service for managing collaborative projects in Firestore.
 * Handles project creation, joining, member management, and unique code generation.
 */
public class ProjectService {
    private static final String PROJECTS_COLLECTION = "projects";
    private static final String MEMBERS_SUBCOLLECTION = "members";
    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom random = new SecureRandom();

    private final Firestore firestore;

    public ProjectService() {
        this.firestore = FirebaseService.getInstance().getFirestore();
    }

    /**
     * Generate a unique 6-character alphanumeric code for project sharing.
     * Format: ABC-123 (with hyphen for readability)
     */
    public String generateUniqueCode() throws ExecutionException, InterruptedException {
        String code;
        boolean isUnique;
        
        do {
            // Generate random 6-character code
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            // Format as ABC-123
            code = sb.substring(0, 3) + "-" + sb.substring(3);
            
            // Check if code already exists
            isUnique = !codeExists(code);
        } while (!isUnique);
        
        return code;
    }

    /**
     * Check if a project code already exists in Firestore.
     */
    private boolean codeExists(String code) throws ExecutionException, InterruptedException {
        Query query = firestore.collection(PROJECTS_COLLECTION)
                .whereEqualTo("code", code)
                .limit(1);
        
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        return !querySnapshot.get().isEmpty();
    }

    /**
     * Create a new collaborative project.
     * 
     * @param name Project name
     * @param ownerId User ID of the project owner
     * @param workspacePath Root folder path
     * @return Created Project object with generated code
     */
    public Project createProject(String name, String ownerId, String workspacePath) 
            throws ExecutionException, InterruptedException {
        String code = generateUniqueCode();
        Project project = new Project(name, code, ownerId, workspacePath);
        
        // Create project document in Firestore
        DocumentReference docRef = firestore.collection(PROJECTS_COLLECTION).document();
        project.setId(docRef.getId());
        
        // Convert to map for Firestore
        Map<String, Object> projectData = new HashMap<>();
        projectData.put("name", project.getName());
        projectData.put("code", project.getCode());
        projectData.put("ownerId", project.getOwnerId());
        projectData.put("memberIds", project.getMemberIds());
        projectData.put("workspacePath", project.getWorkspacePath());
        projectData.put("createdAt", project.getCreatedAt());
        projectData.put("updatedAt", project.getUpdatedAt());
        
        ApiFuture<WriteResult> result = docRef.set(projectData);
        result.get(); // Wait for completion
        
        // Add owner as a member
        addMember(project.getId(), ownerId, 
                UserSession.getInstance().getCurrentUser().getUsername(), 
                ProjectMember.Role.OWNER);
        
        System.out.println("Project created: " + project);
        return project;
    }

    /**
     * Get a project by its ID.
     * 
     * @param projectId The project document ID
     * @return Project if found, null otherwise
     */
    public Project getProject(String projectId) throws ExecutionException, InterruptedException {
        DocumentReference docRef = firestore.collection(PROJECTS_COLLECTION).document(projectId);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        
        if (!document.exists()) {
            return null;
        }
        
        return documentToProject(document);
    }
    
    /**
     * Find a project by its unique share code.
     * 
     * @param code The 6-character share code (e.g., "ABC-123")
     * @return Project if found, null otherwise
     */
    public Project findProjectByCode(String code) throws ExecutionException, InterruptedException {
        Query query = firestore.collection(PROJECTS_COLLECTION)
                .whereEqualTo("code", code)
                .limit(1);
        
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        List<QueryDocumentSnapshot> documents = querySnapshot.get().getDocuments();
        
        if (documents.isEmpty()) {
            return null;
        }
        
        return documentToProject(documents.get(0));
    }

    /**
     * Join an existing project using a share code.
     * 
     * @param code Share code
     * @param userId User ID joining the project
     * @param username Username for display
     * @return The joined Project, or null if code is invalid
     */
    public Project joinProject(String code, String userId, String username) 
            throws ExecutionException, InterruptedException {
        Project project = findProjectByCode(code);
        
        if (project == null) {
            System.err.println("Project not found with code: " + code);
            return null;
        }
        
        // Check if user is already a member
        if (project.isMember(userId)) {
            System.out.println("User already a member of this project.");
            return project;
        }
        
        // Add user to project members list
        project.addMember(userId);
        updateProject(project);
        
        // Add member document
        addMember(project.getId(), userId, username, ProjectMember.Role.EDITOR);
        
        System.out.println("User " + username + " joined project: " + project.getName());
        return project;
    }

    /**
     * Add a member to a project with specified role.
     */
    private void addMember(String projectId, String userId, String username, ProjectMember.Role role) 
            throws ExecutionException, InterruptedException {
        ProjectMember member = new ProjectMember(userId, username, role);
        
        DocumentReference memberDoc = firestore.collection(PROJECTS_COLLECTION)
                .document(projectId)
                .collection(MEMBERS_SUBCOLLECTION)
                .document(userId);
        
        Map<String, Object> memberData = new HashMap<>();
        memberData.put("userId", member.getUserId());
        memberData.put("username", member.getUsername());
        memberData.put("role", member.getRole().toString());
        memberData.put("joinedAt", member.getJoinedAt());
        memberData.put("isOnline", member.isOnline());
        memberData.put("currentFile", member.getCurrentFile());
        memberData.put("cursorPosition", member.getCursorPosition());
        
        ApiFuture<WriteResult> result = memberDoc.set(memberData);
        result.get();
    }

    /**
     * Get all projects where the user is a member.
     */
    public List<Project> getUserProjects(String userId) throws ExecutionException, InterruptedException {
        Query query = firestore.collection(PROJECTS_COLLECTION)
                .whereArrayContains("memberIds", userId);
        
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        List<QueryDocumentSnapshot> documents = querySnapshot.get().getDocuments();
        
        List<Project> projects = new ArrayList<>();
        for (QueryDocumentSnapshot doc : documents) {
            projects.add(documentToProject(doc));
        }
        
        return projects;
    }

    /**
     * Get all members of a project.
     */
    public List<ProjectMember> getProjectMembers(String projectId) 
            throws ExecutionException, InterruptedException {
        CollectionReference membersRef = firestore.collection(PROJECTS_COLLECTION)
                .document(projectId)
                .collection(MEMBERS_SUBCOLLECTION);
        
        ApiFuture<QuerySnapshot> querySnapshot = membersRef.get();
        List<QueryDocumentSnapshot> documents = querySnapshot.get().getDocuments();
        
        List<ProjectMember> members = new ArrayList<>();
        for (QueryDocumentSnapshot doc : documents) {
            members.add(documentToMember(doc));
        }
        
        return members;
    }

    /**
     * Update project details in Firestore.
     */
    public void updateProject(Project project) throws ExecutionException, InterruptedException {
        DocumentReference docRef = firestore.collection(PROJECTS_COLLECTION)
                .document(project.getId());
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", project.getName());
        updates.put("memberIds", project.getMemberIds());
        updates.put("workspacePath", project.getWorkspacePath());
        updates.put("updatedAt", project.getUpdatedAt());
        
        ApiFuture<WriteResult> result = docRef.update(updates);
        result.get();
    }

    /**
     * Update member's online status and current activity.
     */
    public void updateMemberPresence(String projectId, String userId, boolean isOnline, 
                                    String currentFile, int cursorPosition) {
        DocumentReference memberDoc = firestore.collection(PROJECTS_COLLECTION)
                .document(projectId)
                .collection(MEMBERS_SUBCOLLECTION)
                .document(userId);
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("isOnline", isOnline);
        updates.put("currentFile", currentFile);
        updates.put("cursorPosition", cursorPosition);
        
        memberDoc.update(updates);
    }

    /**
     * Delete a project (owner only).
     */
    public void deleteProject(String projectId) throws ExecutionException, InterruptedException {
        // Delete all members first
        CollectionReference membersRef = firestore.collection(PROJECTS_COLLECTION)
                .document(projectId)
                .collection(MEMBERS_SUBCOLLECTION);
        
        ApiFuture<QuerySnapshot> querySnapshot = membersRef.get();
        List<QueryDocumentSnapshot> documents = querySnapshot.get().getDocuments();
        
        for (QueryDocumentSnapshot doc : documents) {
            doc.getReference().delete().get();
        }
        
        // Delete project document
        DocumentReference projectRef = firestore.collection(PROJECTS_COLLECTION)
                .document(projectId);
        ApiFuture<WriteResult> result = projectRef.delete();
        result.get();
        
        System.out.println("Project deleted: " + projectId);
    }

    // Helper methods to convert Firestore documents to objects
    private Project documentToProject(DocumentSnapshot doc) {
        Project project = new Project();
        project.setId(doc.getId());
        project.setName(doc.getString("name"));
        project.setCode(doc.getString("code"));
        project.setOwnerId(doc.getString("ownerId"));
        project.setMemberIds((List<String>) doc.get("memberIds"));
        project.setWorkspacePath(doc.getString("workspacePath"));
        project.setCreatedAt(doc.getDate("createdAt"));
        project.setUpdatedAt(doc.getDate("updatedAt"));
        return project;
    }

    private ProjectMember documentToMember(DocumentSnapshot doc) {
        ProjectMember member = new ProjectMember();
        member.setUserId(doc.getString("userId"));
        member.setUsername(doc.getString("username"));
        member.setRole(ProjectMember.Role.valueOf(doc.getString("role")));
        member.setJoinedAt(doc.getDate("joinedAt"));
        member.setOnline(doc.getBoolean("isOnline"));
        member.setCurrentFile(doc.getString("currentFile"));
        Long cursorPos = doc.getLong("cursorPosition");
        member.setCursorPosition(cursorPos != null ? cursorPos.intValue() : 0);
        return member;
    }
}
