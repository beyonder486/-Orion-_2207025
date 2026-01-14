package com.orion;

import java.util.Date;

/**
 * Model class representing a project member with their role and permissions.
 * Stored as a subcollection under projects/{projectId}/members in Firestore.
 */
public class ProjectMember {
    public enum Role {
        OWNER,   // Full control: can edit, share, delete project
        EDITOR,  // Can edit files and invite others
        VIEWER   // Read-only access
    }

    private String userId;          // Reference to User ID
    private String username;        // Cached username for display
    private Role role;              // Member's role in the project
    private Date joinedAt;          // When the user joined the project
    private boolean isOnline;       // Current online status
    private String currentFile;     // Currently opened file path
    private int cursorPosition;     // Current cursor position in the file

    public ProjectMember() {
        // Default constructor required for Firestore
        this.joinedAt = new Date();
        this.role = Role.VIEWER;
        this.isOnline = false;
    }

    public ProjectMember(String userId, String username, Role role) {
        this();
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Date getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Date joinedAt) {
        this.joinedAt = joinedAt;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public String getCurrentFile() {
        return currentFile;
    }

    public void setCurrentFile(String currentFile) {
        this.currentFile = currentFile;
    }

    public int getCursorPosition() {
        return cursorPosition;
    }

    public void setCursorPosition(int cursorPosition) {
        this.cursorPosition = cursorPosition;
    }

    // Permission checks
    public boolean canEdit() {
        return role == Role.OWNER || role == Role.EDITOR;
    }

    public boolean canInvite() {
        return role == Role.OWNER || role == Role.EDITOR;
    }

    public boolean canDelete() {
        return role == Role.OWNER;
    }

    public boolean canChangePermissions() {
        return role == Role.OWNER;
    }

    @Override
    public String toString() {
        return "ProjectMember{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", role=" + role +
                ", isOnline=" + isOnline +
                ", currentFile='" + currentFile + '\'' +
                '}';
    }
}
