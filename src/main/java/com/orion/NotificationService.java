package com.orion;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for sending notifications to mobile app users.
 * Creates notification documents in Firestore that the Android app listens to.
 */
public class NotificationService {
    private final Firestore firestore;
    private static final String NOTIFICATIONS_COLLECTION = "notifications";
    
    public NotificationService() {
        this.firestore = FirebaseService.getInstance().getFirestore();
    }
    
    /**
     * Send a notification to a user about a project submission.
     * 
     * @param recipientId The ID of the user to notify (project author)
     * @param projectId The ID of the pending project
     * @param projectTitle The title of the project
     * @param developerName The name of the developer who submitted
     * @param githubRepoLink The GitHub repository link
     * @return CompletableFuture<Void>
     */
    public CompletableFuture<Void> sendProjectSubmissionNotification(
            String recipientId, 
            String projectId, 
            String projectTitle,
            String developerName,
            String githubRepoLink) {
        
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        try {
            // Create notification document
            Map<String, Object> notification = new HashMap<>();
            notification.put("recipientId", recipientId);
            notification.put("type", "PROJECT_SUBMITTED");
            notification.put("title", "Project Submitted for Review");
            notification.put("message", developerName + " has submitted a solution for '" + projectTitle + "'");
            notification.put("projectId", projectId);
            notification.put("projectTitle", projectTitle);
            notification.put("developerName", developerName);
            notification.put("githubRepoLink", githubRepoLink);
            notification.put("read", false);
            notification.put("timestamp", FieldValue.serverTimestamp());
            notification.put("createdAt", FieldValue.serverTimestamp());
            
            // Add to Firestore
            ApiFuture<DocumentReference> addFuture = firestore.collection(NOTIFICATIONS_COLLECTION).add(notification);
            
            addFuture.addListener(() -> {
                try {
                    DocumentReference docRef = addFuture.get();
                    System.out.println("Notification created with ID: " + docRef.getId());
                    future.complete(null);
                } catch (Exception e) {
                    System.err.println("Failed to create notification: " + e.getMessage());
                    future.completeExceptionally(e);
                }
            }, Runnable::run);
            
        } catch (Exception e) {
            System.err.println("Error sending notification: " + e.getMessage());
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Send a notification to a developer that their project was rejected.
     * 
     * @param recipientId The ID of the developer
     * @param projectId The ID of the pending project
     * @param projectTitle The title of the project
     * @param rejectionReason Optional reason for rejection
     * @return CompletableFuture<Void>
     */
    public CompletableFuture<Void> sendProjectRejectionNotification(
            String recipientId, 
            String projectId, 
            String projectTitle,
            String rejectionReason) {
        
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        try {
            // Create notification document
            Map<String, Object> notification = new HashMap<>();
            notification.put("recipientId", recipientId);
            notification.put("type", "PROJECT_REJECTED");
            notification.put("title", "Project Submission Rejected");
            notification.put("message", "Your submission for '" + projectTitle + "' was not accepted. Please revise and resubmit.");
            notification.put("projectId", projectId);
            notification.put("projectTitle", projectTitle);
            if (rejectionReason != null && !rejectionReason.isEmpty()) {
                notification.put("rejectionReason", rejectionReason);
            }
            notification.put("read", false);
            notification.put("timestamp", FieldValue.serverTimestamp());
            notification.put("createdAt", FieldValue.serverTimestamp());
            
            // Add to Firestore
            ApiFuture<DocumentReference> addFuture = firestore.collection(NOTIFICATIONS_COLLECTION).add(notification);
            
            addFuture.addListener(() -> {
                try {
                    DocumentReference docRef = addFuture.get();
                    System.out.println("Rejection notification created with ID: " + docRef.getId());
                    future.complete(null);
                } catch (Exception e) {
                    System.err.println("Failed to create rejection notification: " + e.getMessage());
                    future.completeExceptionally(e);
                }
            }, Runnable::run);
            
        } catch (Exception e) {
            System.err.println("Error sending rejection notification: " + e.getMessage());
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Send a notification to a developer that their project was accepted.
     * 
     * @param recipientId The ID of the developer
     * @param projectId The ID of the pending project
     * @param projectTitle The title of the project
     * @return CompletableFuture<Void>
     */
    public CompletableFuture<Void> sendProjectAcceptanceNotification(
            String recipientId, 
            String projectId, 
            String projectTitle) {
        
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        try {
            // Create notification document
            Map<String, Object> notification = new HashMap<>();
            notification.put("recipientId", recipientId);
            notification.put("type", "PROJECT_ACCEPTED");
            notification.put("title", "Project Accepted!");
            notification.put("message", "Congratulations! Your submission for '" + projectTitle + "' has been accepted.");
            notification.put("projectId", projectId);
            notification.put("projectTitle", projectTitle);
            notification.put("read", false);
            notification.put("timestamp", FieldValue.serverTimestamp());
            notification.put("createdAt", FieldValue.serverTimestamp());
            
            // Add to Firestore
            ApiFuture<DocumentReference> addFuture = firestore.collection(NOTIFICATIONS_COLLECTION).add(notification);
            
            addFuture.addListener(() -> {
                try {
                    DocumentReference docRef = addFuture.get();
                    System.out.println("Acceptance notification created with ID: " + docRef.getId());
                    future.complete(null);
                } catch (Exception e) {
                    System.err.println("Failed to create acceptance notification: " + e.getMessage());
                    future.completeExceptionally(e);
                }
            }, Runnable::run);
            
        } catch (Exception e) {
            System.err.println("Error sending acceptance notification: " + e.getMessage());
            future.completeExceptionally(e);
        }
        
        return future;
    }
}
