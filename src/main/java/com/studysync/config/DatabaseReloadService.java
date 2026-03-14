package com.studysync.config;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

/**
 * Service that shuts down and reopens the H2 database in-place, allowing the
 * underlying file to be replaced at runtime (e.g. after a Google Drive download).
 *
 * <p>The reload cycle is:
 * <ol>
 *   <li>{@code SHUTDOWN} — H2 closes its engine (caches flushed, file lock released).</li>
 *   <li>Evict all pooled connections and wait for active connections to drain.</li>
 *   <li>A test query (with retries) forces HikariCP to create a fresh connection,
 *       which makes H2 open the (now-replaced) database file.</li>
 *   <li>Schema migrations ({@code schema.sql}) are re-applied to ensure the
 *       downloaded database has all required columns/indexes.</li>
 * </ol>
 */
@Service
public class DatabaseReloadService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseReloadService.class);

    /** Maximum time (ms) to wait for active connections to drain after SHUTDOWN. */
    private static final long DRAIN_TIMEOUT_MS = 3000;
    /** Interval (ms) between drain-wait polls. */
    private static final long DRAIN_POLL_MS = 100;
    /** Number of reconnect attempts before giving up. */
    private static final int RECONNECT_RETRIES = 5;
    /** Delay (ms) between reconnect retries. */
    private static final long RECONNECT_RETRY_DELAY_MS = 500;

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public DatabaseReloadService(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Shuts down the H2 engine and evicts all pooled connections, releasing the
     * file lock so the {@code .mv.db} file can be safely replaced on any OS.
     * Blocks until all active connections have drained (up to a timeout).
     * Must be followed by a call to {@link #reconnect()} once the file is ready.
     */
    public void shutdown() {
        logger.info("Shutting down H2 database (releasing file lock)…");

        // 1. Shut down H2 — all pooled connections become invalid
        try {
            jdbcTemplate.execute("SHUTDOWN");
        } catch (Exception e) {
            // The executing connection is killed by H2's SHUTDOWN — expected
            logger.debug("H2 SHUTDOWN completed (exception expected): {}", e.getMessage());
        }

        // 2. Evict connections and wait for active ones to drain
        if (dataSource instanceof HikariDataSource hikari) {
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            if (pool != null) {
                pool.softEvictConnections();

                // Wait for all active (checked-out) connections to be returned
                // and evicted, so H2 fully releases the file lock.
                long deadline = System.currentTimeMillis() + DRAIN_TIMEOUT_MS;
                while (pool.getActiveConnections() > 0
                        && System.currentTimeMillis() < deadline) {
                    try {
                        Thread.sleep(DRAIN_POLL_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                int remaining = pool.getActiveConnections();
                if (remaining > 0) {
                    logger.warn("Timed out waiting for {} active connection(s) to drain "
                            + "after SHUTDOWN; file lock may not be released", remaining);
                }
            }
        }

        logger.info("H2 shutdown complete");
    }

    /**
     * Reconnects to the H2 database file (which may have been replaced since
     * {@link #shutdown()}) and re-applies schema migrations.
     *
     * <p>Uses retries because HikariCP may still hand out stale connections on
     * the first attempt if soft-eviction hasn't fully propagated.
     */
    public void reconnect() {
        logger.info("Reconnecting to H2 database…");

        // Retry loop: stale pooled connections may cause the first attempt(s) to
        // fail until HikariCP discards them and creates a fresh connection.
        Exception lastException = null;
        for (int attempt = 1; attempt <= RECONNECT_RETRIES; attempt++) {
            try {
                // Get a raw connection and explicitly validate it to bypass any
                // HikariCP caching that might return a stale wrapper.
                try (Connection conn = dataSource.getConnection()) {
                    if (conn.isValid(2)) {
                        logger.info("Database connection established (attempt {})", attempt);
                        break;
                    }
                    throw new RuntimeException("Connection.isValid() returned false");
                }
            } catch (Exception e) {
                lastException = e;
                logger.debug("Reconnect attempt {}/{} failed: {}", attempt,
                        RECONNECT_RETRIES, e.getMessage());
                if (attempt < RECONNECT_RETRIES) {
                    try {
                        Thread.sleep(RECONNECT_RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    // Re-evict to clear any remaining stale connections
                    if (dataSource instanceof HikariDataSource hikari) {
                        HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
                        if (pool != null) {
                            pool.softEvictConnections();
                        }
                    }
                }
            }
        }

        // Final verification: can we actually query?
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (result == null || result != 1) {
                throw new RuntimeException("SELECT 1 returned unexpected result: " + result);
            }
        } catch (Exception e) {
            String msg = "Database reconnect failed after " + RECONNECT_RETRIES
                    + " attempts — application may need a restart";
            logger.error(msg, lastException != null ? lastException : e);
            throw new RuntimeException(msg, e);
        }

        // Run idempotent schema.sql to apply any missing migrations
        runMigrations();

        logger.info("Database reconnected and ready");
    }

    /**
     * Convenience method: shuts down H2, then immediately reconnects.
     * Use when the file has already been replaced before this call.
     */
    public void reloadDatabase() {
        shutdown();
        reconnect();
    }

    /**
     * Re-applies {@code schema.sql} (CREATE IF NOT EXISTS / ALTER ADD IF NOT EXISTS)
     * so that a downloaded database from an older schema version gets upgraded.
     */
    private void runMigrations() {
        try {
            Resource schemaResource = resolveSchemaResource();
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(schemaResource);
            populator.setContinueOnError(true); // individual failures are non-fatal
            populator.setSeparator(";");
            populator.execute(dataSource);
            logger.info("Schema migrations re-applied after database reload using {} "
                    + "(continueOnError=true; individual statement failures were silently ignored)",
                    schemaResource.getDescription());
        } catch (Exception e) {
            logger.error("Failed to re-apply schema.sql after reload"
                    + " — the database may be missing columns/tables", e);
        }
    }

    private Resource resolveSchemaResource() {
        Resource classpathSchema = new ClassPathResource("schema.sql",
                DatabaseReloadService.class.getClassLoader());
        if (classpathSchema.exists()) {
            return classpathSchema;
        }

        Path installedSchema = Path.of(System.getProperty("user.home"),
                ".local", "share", "studysync", "resources", "schema.sql");
        if (Files.exists(installedSchema)) {
            logger.warn("schema.sql not found on classpath; "
                    + "falling back to installed resource file: {}", installedSchema);
            return new FileSystemResource(installedSchema);
        }

        Path projectSchema = Path.of("src", "main", "resources", "schema.sql")
                .toAbsolutePath();
        if (Files.exists(projectSchema)) {
            logger.warn("schema.sql not found on classpath; "
                    + "falling back to project resource file: {}", projectSchema);
            return new FileSystemResource(projectSchema);
        }

        throw new IllegalStateException(
                "schema.sql not found in classpath, installed resources, or project resources");
    }
}
