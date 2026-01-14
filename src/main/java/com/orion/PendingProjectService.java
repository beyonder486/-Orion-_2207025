package com.orion;

import com.google.cloud.firestore.*;
import com.google.api.core.ApiFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing pending projects from mobile app.
 * Handles querying and updating pending projects in Firestore.
 */
public class PendingProjectService {
    private final Firestore firestore;
    private final NotificationService notificationService;
    private static final String COLLECTION_NAME = "pendingProjects";
    
    public PendingProjectService() {
        this.firestore = FirebaseService.getInstance().getFirestore();
        this.notificationService = new NotificationService();
    }
    
    /**
     * Get all pending projects assigned to a specific developer.
     * 
     * @param developerId The ID of the developer
     * @return CompletableFuture with list of pending projects
     */
    public CompletableFuture<List<PendingProject>> getPendingProjectsForDeveloper(String developerId) {
        CompletableFuture<List<PendingProject>> future = new CompletableFuture<>();
        
        try {
            // Simple query - filter completed projects in memory to avoid index requirement
            ApiFuture<QuerySnapshot> query = firestore.collection(COLLECTION_NAME)
                .whereEqualTo("developerId", developerId)
                .get();
                
            query.addListener(() -> {
                try {
                    QuerySnapshot querySnapshot = query.get();
                    List<PendingProject> projects = new ArrayList<>();
                    
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        PendingProject project = doc.toObject(PendingProject.class);
                        if (project != null && !"COMPLETED".equals(project.getStatus())) {
                            project.setId(doc.getId());
                            projects.add(project);
                        }
                    }
                    
                    // Sort by accepted date (newest first)
                    projects.sort((p1, p2) -> {
                        if (p1.getAcceptedAt() == null || p2.getAcceptedAt() == null) return 0;
                        return p2.getAcceptedAt().compareTo(p1.getAcceptedAt());
                    });
                    
                    future.complete(projects);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }, Runnable::run);
            
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Update the status of a pending project.
     * 
     * @param projectId The ID of the project
     * @param newStatus The new status
     * @return CompletableFuture<Void>
     */
    public CompletableFuture<Void> updateProjectStatus(String projectId, String newStatus) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        try {
            DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(projectId);
            ApiFuture<WriteResult> updateFuture = docRef.update(
                "status", newStatus,
                "updatedAt", FieldValue.serverTimestamp()
            );
            
            updateFuture.addListener(() -> {
                try {
                    updateFuture.get();
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }, Runnable::run);
            
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Mark a pending project as completed.
     * 
     * @param projectId The ID of the project
     * @return CompletableFuture<Void>
     */
    public CompletableFuture<Void> markAsCompleted(String projectId) {
        return updateProjectStatus(projectId, "COMPLETED");
    }
    
    /**
     * Mark a pending project as in progress.
     * 
     * @param projectId The ID of the project
     * @return CompletableFuture<Void>
     */
    public CompletableFuture<Void> markAsInProgress(String projectId) {
        return updateProjectStatus(projectId, "IN_PROGRESS");
    }
    
    /**
     * Submit a pending project for review with GitHub repo link.
     * 
     * @param projectId The ID of the project
     * @param githubRepoLink The GitHub repository link
     * @param developerName The name of the developer submitting
     * @return CompletableFuture<Void>
     */
    public CompletableFuture<Void> submitForReview(String projectId, String githubRepoLink, String developerName) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        try {
            // First, get the project details to find the author
            DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(projectId);
            ApiFuture<DocumentSnapshot> getFuture = docRef.get();
            
            getFuture.addListener(() -> {
                try {
                    DocumentSnapshot projectDoc = getFuture.get();
                    if (!projectDoc.exists()) {
                        future.completeExceptionally(new Exception("Project not found"));
                        return;
                    }
                    
                    PendingProject project = projectDoc.toObject(PendingProject.class);
                    if (project == null) {
                        future.completeExceptionally(new Exception("Failed to parse project"));
                        return;
                    }
                    
                    // Update project status and GitHub link
                    ApiFuture<WriteResult> updateFuture = docRef.update(
                        "status", "SUBMITTED_FOR_REVIEW",
                        "statusEnum", "SUBMITTED_FOR_REVIEW",
                        "githubRepoLink", githubRepoLink,
                        "submittedAt", FieldValue.serverTimestamp(),
                        "updatedAt", FieldValue.serverTimestamp()
                    );
                    
                    updateFuture.addListener(() -> {
                        try {
                            updateFuture.get();
                            System.out.println("Project updated to SUBMITTED_FOR_REVIEW");
                            
                            // Send notification to project author
                            String authorId = project.getAuthorId();
                            String projectTitle = project.getPostTitle();
                            
                            if (authorId != null && !authorId.isEmpty()) {
                                notificationService.sendProjectSubmissionNotification(
                                    authorId, 
                                    projectId, 
                                    projectTitle,
                                    developerName,
                                    githubRepoLink
                                ).thenAccept(v -> {
                                    System.out.println("Notification sent to author: " + authorId);
                                    future.complete(null);
                                }).exceptionally(e -> {
                                    System.err.println("Failed to send notification: " + e.getMessage());
                                    // Still complete successfully even if notification fails
                                    future.complete(null);
                                    return null;
                                });
                            } else {
                                System.err.println("No author ID found, notification not sent");
                                future.complete(null);
                            }
                            
                        } catch (Exception e) {
                            future.completeExceptionally(e);
                        }
                    }, Runnable::run);
                    
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }, Runnable::run);
            
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Listen for real-time updates to pending projects for a developer.
     * 
     * @param developerId The ID of the developer
     * @param listener Listener to handle updates
     * @return ListenerRegistration to stop listening
     */
    public ListenerRegistration listenToPendingProjects(String developerId, 
                                                        PendingProjectsListener listener) {
        System.out.println("Starting Firestore listener for developerId: " + developerId);
        System.out.println("Collection: " + COLLECTION_NAME);
        
        return firestore.collection(COLLECTION_NAME)
            .whereEqualTo("developerId", developerId)
            .addSnapshotListener((snapshot, error) -> {
                if (error != null) {
                    System.err.println("Listen failed: " + error.getMessage());
                    error.printStackTrace();
                    listener.onError(error);
                    return;
                }
                
                if (snapshot != null) {
                    System.out.println("Received snapshot with " + snapshot.size() + " documents");
                    List<PendingProject> projects = new ArrayList<>();
                    
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        System.out.println("Document ID: " + doc.getId());
                        System.out.println("  Data: " + doc.getData());
                        
                        PendingProject project = doc.toObject(PendingProject.class);
                        if (project != null) {
                            System.out.println("  Parsed project: " + project.getPostTitle() + ", Status: " + project.getStatus() + ", DevID: " + project.getDeveloperId());
                            
                            if (!"COMPLETED".equals(project.getStatus())) {
                                project.setId(doc.getId());
                                projects.add(project);
                                System.out.println("  -> Added to list");
                            } else {
                                System.out.println("  -> Skipped (completed)");
                            }
                        } else {
                            System.out.println("  -> Failed to parse as PendingProject");
                        }
                    }
                    
                    // Sort by accepted date (newest first)
                    projects.sort((p1, p2) -> {
                        if (p1.getAcceptedAt() == null || p2.getAcceptedAt() == null) return 0;
                        return p2.getAcceptedAt().compareTo(p1.getAcceptedAt());
                    });
                    
                    System.out.println("Final list: " + projects.size() + " pending projects");
                    listener.onUpdate(projects);
                }
            });
    }
    
    /**
     * Interface for listening to pending projects updates.
     */
    public interface PendingProjectsListener {
        void onUpdate(List<PendingProject> projects);
        void onError(Exception e);
    }
}
