package com.studysync.integration.drive;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Persists the Drive revision that the local database was last synchronized with. */
public final class DriveSyncStateStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(DriveSyncStateStore.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DriveSyncStateStore() {
    }

    public static Optional<DriveSyncState> read(final Path localDatabasePath) {
        Path statePath = statePath(localDatabasePath);
        if (!Files.exists(statePath)) {
            return Optional.empty();
        }
        try (InputStream input = Files.newInputStream(statePath)) {
            return Optional.of(OBJECT_MAPPER.readValue(input, DriveSyncState.class));
        } catch (IOException e) {
            LOGGER.warn("Unable to read Drive sync state {}: {}", statePath, e.getMessage());
            return Optional.empty();
        }
    }

    public static void write(final Path localDatabasePath, final DriveSyncState state) throws IOException {
        Path statePath = statePath(localDatabasePath);
        Path temporaryPath = statePath.resolveSibling(statePath.getFileName() + ".tmp");
        Path parent = statePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream output = Files.newOutputStream(temporaryPath)) {
            OBJECT_MAPPER.writeValue(output, state);
        }
        PendingDownloadSupport.moveReplacing(temporaryPath, statePath);
    }

    public static Path statePath(final Path localDatabasePath) {
        return localDatabasePath.toAbsolutePath().resolveSibling(
                PendingDownloadSupport.baseName(localDatabasePath) + ".drive-sync-state.json");
    }
}
