package com.orion;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Model class representing a collaborative project.
 * Stored in Firestore under the 'projects' collection.
 */
public class Project {
    private String id;              // Firestore document ID
    private String name;            // Project name
    private String code;            // Unique 6-character share code (e.g., "ABC123")
    private String ownerId;         // User ID of the project owner
    private List<String> memberIds; // List of user IDs who have access
    private String workspacePath;   // Root folder path of the project
    private Date createdAt;         // Project creation timestamp
    private Date updatedAt;         // Last update timestamp

    public Project() {
        // Default constructor required for Firestore
        this.memberIds = new ArrayList<>();
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }

    public Project(String name, String code, String ownerId, String workspacePath) {
        this();
        this.name = name;
        this.code = code;
        this.ownerId = ownerId;
        this.workspacePath = workspacePath;
        this.memberIds.add(ownerId); // Owner is automatically a member
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.updatedAt = new Date();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public List<String> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(List<String> memberIds) {
        this.memberIds = memberIds;
        this.updatedAt = new Date();
    }

    public void addMember(String userId) {
        if (!memberIds.contains(userId)) {
            memberIds.add(userId);
            this.updatedAt = new Date();
        }
    }

    public void removeMember(String userId) {
        memberIds.remove(userId);
        this.updatedAt = new Date();
    }

    public boolean isMember(String userId) {
        return memberIds.contains(userId);
    }

    public boolean isOwner(String userId) {
        return ownerId != null && ownerId.equals(userId);
    }

    public String getWorkspacePath() {
        return workspacePath;
    }

    public void setWorkspacePath(String workspacePath) {
        this.workspacePath = workspacePath;
        this.updatedAt = new Date();
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "Project{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", code='" + code + '\'' +
                ", ownerId='" + ownerId + '\'' +
                ", memberCount=" + memberIds.size() +
                ", workspacePath='" + workspacePath + '\'' +
                '}';
    }
}
