package com.studysync.bootstrap;

import com.studysync.integration.drive.PendingDownloadMetadata;
import com.studysync.integration.drive.PendingDownloadSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingDownloadApplierTest {

    @TempDir
    Path tempDir;

    @Test
    void applyIfPresentReplacesLiveDatabaseAndCreatesBackup() throws Exception {
        Path liveDatabase = tempDir.resolve("studysync.mv.db");
        Files.writeString(liveDatabase, "live-db");

        Path stagedDatabase = PendingDownloadSupport.pendingDatabasePath(liveDatabase);
        Files.writeString(stagedDatabase, "drive-db");
        PendingDownloadMetadata metadata = new PendingDownloadMetadata(
                "drive-file",
                Files.size(stagedDatabase),
                PendingDownloadSupport.sha256Hex(stagedDatabase),
                123456789L,
                System.currentTimeMillis());
        PendingDownloadSupport.writeMetadata(PendingDownloadSupport.pendingMetadataPath(liveDatabase), metadata);

        PendingDownloadApplier.applyIfPresent(liveDatabase);

        assertEquals("drive-db", Files.readString(liveDatabase));
        assertFalse(Files.exists(PendingDownloadSupport.pendingMetadataPath(liveDatabase)));
        assertFalse(Files.exists(stagedDatabase));
        try (var backups = Files.list(PendingDownloadSupport.backupsDirectory(liveDatabase))) {
            assertTrue(backups.anyMatch(Files::isRegularFile));
        }
    }

    @Test
    void applyIfPresentMarksFailureWhenStagedFileHashDoesNotMatch() throws Exception {
        Path liveDatabase = tempDir.resolve("studysync.mv.db");
        Files.writeString(liveDatabase, "live-db");

        Path stagedDatabase = PendingDownloadSupport.pendingDatabasePath(liveDatabase);
        Files.writeString(stagedDatabase, "drive-db");
        PendingDownloadMetadata metadata = new PendingDownloadMetadata(
                "drive-file",
                Files.size(stagedDatabase),
                "not-the-right-hash",
                123456789L,
                System.currentTimeMillis());
        Path metadataPath = PendingDownloadSupport.pendingMetadataPath(liveDatabase);
        PendingDownloadSupport.writeMetadata(metadataPath, metadata);

        PendingDownloadApplier.applyIfPresent(liveDatabase);

        assertEquals("live-db", Files.readString(liveDatabase));
        assertTrue(Files.exists(metadataPath.resolveSibling(metadataPath.getFileName() + ".failed")));
    }
}
