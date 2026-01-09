package com.orion;

import java.util.Date;

/**
 * Model class representing a file change in the project.
 * Tracks who made the change, when, what changed, and the delta.
 */
public class ChangeHistory {
    private String changeId;
    private String projectId;
    private String filePath;
    private String userId;
    private String username;
    private Date timestamp;
    private ChangeType changeType;
    private String delta; // The actual change content (diff)
    private int linesAdded;
    private int linesRemoved;
    
    public enum ChangeType {
        CREATE,     // File created
        MODIFY,     // File modified
        DELETE,     // File deleted
        RENAME      // File renamed
    }
    
    public ChangeHistory() {
        // Default constructor for Firestore
    }
    
    public ChangeHistory(String projectId, String filePath, String userId, String username, 
                        ChangeType changeType, String delta, int linesAdded, int linesRemoved) {
        this.projectId = projectId;
        this.filePath = filePath;
        this.userId = userId;
        this.username = username;
        this.timestamp = new Date();
        this.changeType = changeType;
        this.delta = delta;
        this.linesAdded = linesAdded;
        this.linesRemoved = linesRemoved;
    }
    
    // Getters and Setters
    public String getChangeId() {
        return changeId;
    }
    
    public void setChangeId(String changeId) {
        this.changeId = changeId;
    }
    
    public String getProjectId() {
        return projectId;
    }
    
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
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
    
    public Date getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
    
    public ChangeType getChangeType() {
        return changeType;
    }
    
    public void setChangeType(ChangeType changeType) {
        this.changeType = changeType;
    }
    
    public String getDelta() {
        return delta;
    }
    
    public void setDelta(String delta) {
        this.delta = delta;
    }
    
    public int getLinesAdded() {
        return linesAdded;
    }
    
    public void setLinesAdded(int linesAdded) {
        this.linesAdded = linesAdded;
    }
    
    public int getLinesRemoved() {
        return linesRemoved;
    }
    
    public void setLinesRemoved(int linesRemoved) {
        this.linesRemoved = linesRemoved;
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s - %s (%s) +%d -%d", 
            timestamp, username, filePath, changeType, linesAdded, linesRemoved);
    }
}
