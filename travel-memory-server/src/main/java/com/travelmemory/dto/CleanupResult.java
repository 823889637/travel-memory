package com.travelmemory.dto;

public class CleanupResult {

    private int scannedCount;
    private int referencedCount;
    private int orphanCount;
    private int deletedCount;
    private int skippedCount;
    private int failedCount;
    private boolean dryRun;
    private String message;

    public int getScannedCount() {
        return scannedCount;
    }

    public void setScannedCount(int scannedCount) {
        this.scannedCount = scannedCount;
    }

    public int getReferencedCount() {
        return referencedCount;
    }

    public void setReferencedCount(int referencedCount) {
        this.referencedCount = referencedCount;
    }

    public int getOrphanCount() {
        return orphanCount;
    }

    public void setOrphanCount(int orphanCount) {
        this.orphanCount = orphanCount;
    }

    public int getDeletedCount() {
        return deletedCount;
    }

    public void setDeletedCount(int deletedCount) {
        this.deletedCount = deletedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
