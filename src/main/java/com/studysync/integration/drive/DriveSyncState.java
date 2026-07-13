package com.studysync.integration.drive;

/**
 * The Google Drive revision known to match the current local database.
 *
 * @param remoteModifiedTimeEpochMillis last-modified time reported by Drive, or null before first sync
 * @param localChangesPending whether this machine has changes not included in that revision
 */
public record DriveSyncState(Long remoteModifiedTimeEpochMillis, boolean localChangesPending) {
}
