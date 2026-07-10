package com.travelmemory.dto;

public class CleanupResult {

    private long scannedCount;
    private long referencedCount;
    private long recentCount;
    private long candidateCount;
    private long deletedCount;
    private long failedCount;
    private long skippedCount;
    private long candidateBytes;
    private long deletedBytes;
    private long durationMillis;
    private boolean dryRun;
    private String message;

    public long getScannedCount() { return scannedCount; }
    public void setScannedCount(long scannedCount) { this.scannedCount = scannedCount; }
    public long getReferencedCount() { return referencedCount; }
    public void setReferencedCount(long referencedCount) { this.referencedCount = referencedCount; }
    public long getRecentCount() { return recentCount; }
    public void setRecentCount(long recentCount) { this.recentCount = recentCount; }
    public long getCandidateCount() { return candidateCount; }
    public void setCandidateCount(long candidateCount) { this.candidateCount = candidateCount; }
    public long getDeletedCount() { return deletedCount; }
    public void setDeletedCount(long deletedCount) { this.deletedCount = deletedCount; }
    public long getFailedCount() { return failedCount; }
    public void setFailedCount(long failedCount) { this.failedCount = failedCount; }
    public long getSkippedCount() { return skippedCount; }
    public void setSkippedCount(long skippedCount) { this.skippedCount = skippedCount; }
    public long getCandidateBytes() { return candidateBytes; }
    public void setCandidateBytes(long candidateBytes) { this.candidateBytes = candidateBytes; }
    public long getDeletedBytes() { return deletedBytes; }
    public void setDeletedBytes(long deletedBytes) { this.deletedBytes = deletedBytes; }
    public long getDurationMillis() { return durationMillis; }
    public void setDurationMillis(long durationMillis) { this.durationMillis = durationMillis; }
    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
