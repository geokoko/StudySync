package com.studysync.config;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

/**
 * Service that shuts down and reopens the H2 database in-place, allowing the
 * underlying file to be replaced at runtime (e.g. after a Google Drive download).
 *
 * <p>The reload cycle is:
 * <ol>
 *   <li>{@code SHUTDOWN} — H2 closes its engine (caches flushed, file lock released).</li>
 *   <li>Soft-evict all pooled connections so HikariCP discards them.</li>
 *   <li>A test query forces HikariCP to create a fresh connection, which makes
 *       H2 open the (now-replaced) database file.</li>
 *   <li>Schema migrations ({@code schema.sql}) are re-applied to ensure the
 *       downloaded database has all required columns/indexes.</li>
 * </ol>
 */
@Service
public class DatabaseReloadService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseReloadService.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public DatabaseReloadService(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Shuts down the H2 engine and evicts all pooled connections, releasing the
     * file lock so the {@code .mv.db} file can be safely replaced on any OS.
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

        // 2. Tell HikariCP to discard every idle/returned connection
        if (dataSource instanceof HikariDataSource hikari) {
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            if (pool != null) {
                pool.softEvictConnections();
            }
        }
    }

    /**
     * Reconnects to the H2 database file (which may have been replaced since
     * {@link #shutdown()}) and re-applies schema migrations.
     */
    public void reconnect() {
        logger.info("Reconnecting to H2 database…");

        // Force a fresh connection — H2 opens the (possibly replaced) file
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        } catch (Exception e) {
            logger.error("Failed to reconnect after reload: {}", e.getMessage());
            throw new RuntimeException("Database reload failed — application may need a restart", e);
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
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource("schema.sql"));
            populator.setContinueOnError(true); // individual failures are non-fatal
            populator.setSeparator(";");
            populator.execute(dataSource);
            logger.info("Schema migrations re-applied after database reload (continueOnError=true; individual statement failures were silently ignored)");
        } catch (Exception e) {
            logger.error("Failed to re-apply schema.sql after reload — the database may be missing columns/tables", e);
        }
    }
}
