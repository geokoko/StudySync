package com.studysync.bootstrap;

import com.studysync.integration.drive.PendingDownloadMetadata;
import com.studysync.integration.drive.PendingDownloadSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;

/**
 * Applies a staged Google Drive database download before Spring or H2 starts.
 */
public final class PendingDownloadApplier {

    private static final Logger logger = LoggerFactory.getLogger(PendingDownloadApplier.class);
    private static final int MAX_BACKUPS = 10;
    private static final Duration BACKUP_MAX_AGE = Duration.ofDays(30);

    private PendingDownloadApplier() {
    }

    public static void applyIfPresent(Path localDatabasePath) {
        Path metadataPath = PendingDownloadSupport.pendingMetadataPath(localDatabasePath);
        if (!Files.exists(metadataPath)) {
            return;
        }

        PendingDownloadMetadata metadata;
        try {
            metadata = PendingDownloadSupport.readMetadata(metadataPath);
        } catch (IOException e) {
            logger.error("Failed to read pending download metadata {}", metadataPath, e);
            PendingDownloadSupport.renameMarkerToFailed(metadataPath);
            return;
        }

        Path stagedPath = PendingDownloadSupport.pendingDatabasePath(localDatabasePath);
        Path livePath = localDatabasePath.toAbsolutePath();
        if (!Files.exists(stagedPath)) {
            handleMissingStagedFile(metadataPath, metadata, livePath);
            return;
        }

        try {
            if (!stagedFileMatches(stagedPath, metadata)) {
                logger.error("Pending download integrity check failed for {}", stagedPath);
                PendingDownloadSupport.renameMarkerToFailed(metadataPath);
                return;
            }

            createBackupIfPresent(livePath);
            PendingDownloadSupport.moveReplacing(stagedPath, livePath);
            Files.deleteIfExists(metadataPath);
            PendingDownloadSupport.pruneBackups(livePath, MAX_BACKUPS, BACKUP_MAX_AGE);
            logger.info("Applied staged Google Drive database download to {}", livePath);
        } catch (Exception e) {
            logger.error("Failed to apply staged Google Drive database download", e);
            PendingDownloadSupport.renameMarkerToFailed(metadataPath);
        }
    }

    private static void handleMissingStagedFile(Path metadataPath, PendingDownloadMetadata metadata, Path livePath) {
        try {
            if (Files.exists(livePath)
                    && Files.size(livePath) == metadata.sizeBytes()
                    && PendingDownloadSupport.sha256Hex(livePath).equals(metadata.sha256())) {
                Files.deleteIfExists(metadataPath);
                logger.info("Pending download marker cleared after detecting an already-applied database swap");
                return;
            }
        } catch (IOException e) {
            logger.warn("Unable to verify live database while resolving pending download marker: {}", e.getMessage());
        }

        logger.error("Pending download marker exists but staged database {} is missing", stagedPathForLog(livePath));
        PendingDownloadSupport.renameMarkerToFailed(metadataPath);
    }

    private static boolean stagedFileMatches(Path stagedPath, PendingDownloadMetadata metadata) throws IOException {
        return Files.size(stagedPath) == metadata.sizeBytes()
                && PendingDownloadSupport.sha256Hex(stagedPath).equals(metadata.sha256());
    }

    private static void createBackupIfPresent(Path livePath) throws IOException {
        if (!Files.exists(livePath)) {
            return;
        }
        Path backupDir = PendingDownloadSupport.backupsDirectory(livePath);
        Files.createDirectories(backupDir);
        Path backupPath = PendingDownloadSupport.timestampedBackupPath(livePath, Instant.now());
        Files.copy(livePath, backupPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        logger.info("Created pre-download backup at {}", backupPath);
    }

    private static Path stagedPathForLog(Path livePath) {
        return PendingDownloadSupport.pendingDatabasePath(livePath);
    }
}
