package com.orion;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for computing text differences (diffs) between two versions of a file.
 * Implements a simple line-based diff algorithm.
 */
public class DiffUtils {
    
    /**
     * Compute the difference between old and new content.
     * 
     * @param oldContent Original content
     * @param newContent Updated content
     * @return Diff representation in unified diff format
     */
    public static DiffResult computeDiff(String oldContent, String newContent) {
        if (oldContent == null) oldContent = "";
        if (newContent == null) newContent = "";
        
        // If content is identical, no changes
        if (oldContent.equals(newContent)) {
            return new DiffResult("", 0, 0);
        }
        
        String[] oldLines = oldContent.split("\n", -1);
        String[] newLines = newContent.split("\n", -1);
        
        List<String> diffLines = new ArrayList<>();
        int linesAdded = 0;
        int linesRemoved = 0;
        
        // Use simple LCS-based diff
        int maxLen = Math.max(oldLines.length, newLines.length);
        
        for (int i = 0; i < maxLen; i++) {
            String oldLine = i < oldLines.length ? oldLines[i] : null;
            String newLine = i < newLines.length ? newLines[i] : null;
            
            if (oldLine == null && newLine != null) {
                // Line added
                diffLines.add("@" + (i + 1) + " +" + newLine);
                linesAdded++;
            } else if (oldLine != null && newLine == null) {
                // Line removed
                diffLines.add("@" + (i + 1) + " -" + oldLine);
                linesRemoved++;
            } else if (oldLine != null && newLine != null && !oldLine.equals(newLine)) {
                // Line modified - ONLY if they're actually different
                diffLines.add("@" + (i + 1) + " -" + oldLine);
                diffLines.add("@" + (i + 1) + " +" + newLine);
                linesRemoved++;
                linesAdded++;
            }
            // If lines are equal, skip them entirely - don't add to diff
        }
        
        return new DiffResult(String.join("\n", diffLines), linesAdded, linesRemoved);
    }
    
    /**
     * Apply a diff to reconstruct the new content from old content.
     * 
     * @param oldContent Original content
     * @param diff Diff to apply
     * @return Reconstructed content
     */
    public static String applyDiff(String oldContent, String diff) {
        if (diff == null || diff.isEmpty()) {
            return oldContent;
        }
        
        String[] oldLines = oldContent != null ? oldContent.split("\n", -1) : new String[0];
        String[] diffLines = diff.split("\n");
        
        List<String> resultLines = new ArrayList<>();
        int oldIndex = 0;
        
        for (String diffLine : diffLines) {
            if (diffLine.startsWith("  ")) {
                // Unchanged line
                if (oldIndex < oldLines.length) {
                    resultLines.add(oldLines[oldIndex]);
                    oldIndex++;
                }
            } else if (diffLine.startsWith("- ")) {
                // Deleted line - skip it in old content
                oldIndex++;
            } else if (diffLine.startsWith("+ ")) {
                // Added line - add to result
                resultLines.add(diffLine.substring(2));
            }
        }
        
        return String.join("\n", resultLines);
    }
    
    /**
     * Class to hold diff computation results.
     */
    public static class DiffResult {
        private final String diff;
        private final int linesAdded;
        private final int linesRemoved;
        
        public DiffResult(String diff, int linesAdded, int linesRemoved) {
            this.diff = diff;
            this.linesAdded = linesAdded;
            this.linesRemoved = linesRemoved;
        }
        
        public String getDiff() {
            return diff;
        }
        
        public int getLinesAdded() {
            return linesAdded;
        }
        
        public int getLinesRemoved() {
            return linesRemoved;
        }
        
        public boolean hasChanges() {
            return linesAdded > 0 || linesRemoved > 0;
        }
    }
}
