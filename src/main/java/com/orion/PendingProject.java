package com.orion;

import java.util.Date;

/**
 * Model class representing a pending project from the mobile app.
 * Stored in Firestore under the 'pendingProjects' collection.
 * Field names match the Android app structure.
 */
public class PendingProject {
    private String id;              // Firestore document ID
    private String postTitle;       // Title of the post (from Android app)
    private String postDescription; // Post description (from Android app)
    private String postId;          // Post ID reference
    private String projectId;       // Project ID reference
    private String applicationId;   // Application ID
    private String authorId;        // ID of the user who created it
    private String authorName;      // Name of the author
    private String developerId;     // ID of the assigned developer
    private String developerName;   // Name of the developer
    private String status;          // Status: "PENDING", "IN_PROGRESS", "SUBMITTED_FOR_REVIEW", "REJECTED", "COMPLETED"
    private String statusEnum;      // Status enum (from Android app)
    private String githubRepoLink;  // GitHub repository link
    private Date acceptedAt;        // When the project was accepted
    private Date submittedAt;       // When the project was submitted for review
    private Date completedAt;       // When the project was completed

    public PendingProject() {
        // Default constructor required for Firestore
    }

    public PendingProject(String postTitle, String postDescription, String authorId, String developerId) {
        this.postTitle = postTitle;
        this.postDescription = postDescription;
        this.authorId = authorId;
        this.developerId = developerId;
        this.status = "PENDING";
        this.statusEnum = "PENDING";
        this.acceptedAt = new Date();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPostTitle() {
        return postTitle;
    }

    public void setPostTitle(String postTitle) {
        this.postTitle = postTitle;
    }

    public String getPostDescription() {
        return postDescription;
    }

    public void setPostDescription(String postDescription) {
        this.postDescription = postDescription;
    }

    public String getPostId() {
        return postId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getDeveloperId() {
        return developerId;
    }

    public void setDeveloperId(String developerId) {
        this.developerId = developerId;
    }

    public String getDeveloperName() {
        return developerName;
    }

    public void setDeveloperName(String developerName) {
        this.developerName = developerName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.statusEnum = status;
        if ("SUBMITTED_FOR_REVIEW".equals(status)) {
            this.submittedAt = new Date();
        } else if ("COMPLETED".equals(status)) {
            this.completedAt = new Date();
        }
    }

    public String getStatusEnum() {
        return statusEnum;
    }

    public void setStatusEnum(String statusEnum) {
        this.statusEnum = statusEnum;
    }

    public Date getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Date acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Date getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Date completedAt) {
        this.completedAt = completedAt;
    }

    public String getGithubRepoLink() {
        return githubRepoLink;
    }

    public void setGithubRepoLink(String githubRepoLink) {
        this.githubRepoLink = githubRepoLink;
    }

    public Date getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Date submittedAt) {
        this.submittedAt = submittedAt;
    }

    @Override
    public String toString() {
        return "PendingProject{" +
                "id='" + id + '\'' +
                ", postTitle='" + postTitle + '\'' +
                ", status='" + status + '\'' +
                ", developerId='" + developerId + '\'' +
                '}';
    }
}
