package com.studysync.integration.drive;

/**
 * Metadata for the current StudySync database file stored on Google Drive.
 */
public record RemoteDatabaseSnapshot(
        String fileId,
        long sizeBytes,
        long modifiedTimeEpochMillis) {
}
