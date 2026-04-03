package com.studysync.integration.drive;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class PendingDownloadSupport {

    private static final Logger logger = LoggerFactory.getLogger(PendingDownloadSupport.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String MV_DB_SUFFIX = ".mv.db";

    private PendingDownloadSupport() {
    }

    public static Path pendingDatabasePath(Path localDatabasePath) {
        return localDatabasePath.toAbsolutePath().resolveSibling(baseName(localDatabasePath) + ".pending-download.mv.db");
    }

    public static Path pendingDatabasePartialPath(Path localDatabasePath) {
        return localDatabasePath.toAbsolutePath().resolveSibling(baseName(localDatabasePath) + ".pending-download.mv.db.partial");
    }

    public static Path pendingMetadataPath(Path localDatabasePath) {
        return localDatabasePath.toAbsolutePath().resolveSibling(baseName(localDatabasePath) + ".pending-download.json");
    }

    public static Path failedPendingMetadataPath(Path localDatabasePath) {
        return localDatabasePath.toAbsolutePath().resolveSibling(baseName(localDatabasePath) + ".pending-download.json.failed");
    }

    public static Path backupsDirectory(Path localDatabasePath) {
        Path parent = localDatabasePath.toAbsolutePath().getParent();
        return (parent != null ? parent : Path.of(".").toAbsolutePath()).resolve("backups");
    }

    public static String baseName(Path localDatabasePath) {
        String fileName = localDatabasePath.toAbsolutePath().getFileName().toString();
        if (fileName.endsWith(MV_DB_SUFFIX)) {
            return fileName.substring(0, fileName.length() - MV_DB_SUFFIX.length());
        }
        return fileName;
    }

    public static void writeMetadata(Path metadataPath, PendingDownloadMetadata metadata) throws IOException {
        Path parent = metadataPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream output = Files.newOutputStream(metadataPath)) {
            OBJECT_MAPPER.writeValue(output, metadata);
        }
    }

    public static PendingDownloadMetadata readMetadata(Path metadataPath) throws IOException {
        try (InputStream input = Files.newInputStream(metadataPath)) {
            return OBJECT_MAPPER.readValue(input, PendingDownloadMetadata.class);
        }
    }

    public static void moveReplacing(Path source, Path target) throws IOException {
        Path targetParent = target.toAbsolutePath().getParent();
        if (targetParent != null) {
            Files.createDirectories(targetParent);
        }
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void renameMarkerToFailed(Path metadataPath) {
        if (!Files.exists(metadataPath)) {
            return;
        }
        Path failedPath = metadataPath.resolveSibling(metadataPath.getFileName() + ".failed");
        try {
            moveReplacing(metadataPath, failedPath);
        } catch (IOException e) {
            logger.warn("Failed to rename pending download marker {} to failed marker {}: {}",
                    metadataPath, failedPath, e.getMessage());
        }
    }

    public static Path timestampedBackupPath(Path localDatabasePath, Instant now) {
        String fileName = localDatabasePath.toAbsolutePath().getFileName().toString();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withLocale(Locale.ROOT)
                .withZone(java.time.ZoneId.systemDefault());
        return backupsDirectory(localDatabasePath).resolve(baseName(localDatabasePath)
                + "-" + formatter.format(now) + "-pre-download" + fileSuffix(fileName));
    }

    public static void pruneBackups(Path localDatabasePath, int keepCount, Duration maxAge) {
        Path backupDir = backupsDirectory(localDatabasePath);
        if (!Files.isDirectory(backupDir)) {
            return;
        }
        Instant cutoff = Instant.now().minus(maxAge);
        try (Stream<Path> stream = Files.list(backupDir)) {
            List<Path> backups = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(PendingDownloadSupport::lastModifiedSafe).reversed())
                    .toList();
            for (int i = 0; i < backups.size(); i++) {
                Path path = backups.get(i);
                if (i >= keepCount || lastModifiedSafe(path).isBefore(cutoff)) {
                    deleteIgnoringErrors(path);
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to prune backup directory {}: {}", backupDir, e.getMessage());
        }
    }

    public static String sha256Hex(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Instant lastModifiedSafe(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    private static void deleteIgnoringErrors(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            logger.warn("Failed to delete old backup {}: {}", path, e.getMessage());
        }
    }

    private static String fileSuffix(String fileName) {
        if (fileName.endsWith(MV_DB_SUFFIX)) {
            return MV_DB_SUFFIX;
        }
        int dotIndex = fileName.indexOf('.');
        return dotIndex >= 0 ? fileName.substring(dotIndex) : "";
    }
}
