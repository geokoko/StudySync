package com.studysync.integration.drive;

/**
 * Metadata persisted alongside a staged Google Drive database download.
 */
public record PendingDownloadMetadata(
        String fileId,
        long sizeBytes,
        String sha256,
        long remoteModifiedTimeEpochMillis,
        long stagedAtEpochMillis) {
}
