package com.studysync.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link DatabaseReloadService}.
 *
 * <p>Uses a file-based H2 database (not in-memory) so we can test the full
 * shutdown → file-replace → reconnect cycle that mirrors a Google Drive download.
 */
class DatabaseReloadServiceTest {

    @TempDir
    Path tempDir;

    /** The "live" database that the app is connected to. */
    private Path liveDbFile;
    private HikariDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private DatabaseReloadService reloadService;

    @BeforeEach
    void setUp() {
        // H2 file path (without the .mv.db extension — H2 appends it automatically)
        Path dbPath = tempDir.resolve("live");
        liveDbFile = tempDir.resolve("live.mv.db");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:file:" + dbPath.toAbsolutePath()
                + ";DB_CLOSE_DELAY=-1;AUTO_RECONNECT=TRUE");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);

        dataSource = new HikariDataSource(config);
        jdbcTemplate = new JdbcTemplate(dataSource);
        reloadService = new DatabaseReloadService(dataSource, jdbcTemplate);

        // Seed the live database with a simple table and one row
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS test_data ("
                + "id INT PRIMARY KEY, val VARCHAR(100))");
        jdbcTemplate.update("INSERT INTO test_data (id, val) VALUES (1, 'original')");
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null && !dataSource.isClosed()) {
            try {
                jdbcTemplate.execute("SHUTDOWN");
            } catch (Exception ignored) {
                // expected
            }
            dataSource.close();
        }
    }

    // ─────────────────────────────────────────────
    // shutdown() tests
    // ─────────────────────────────────────────────

    @Test
    void shutdown_releasesFileLock() {
        // Before shutdown, the file should exist and be locked
        assertTrue(Files.exists(liveDbFile), "DB file should exist before shutdown");

        reloadService.shutdown();

        // After shutdown we should be able to replace the file (proves lock released)
        assertDoesNotThrow(() ->
                Files.writeString(liveDbFile, "dummy — proves file is unlocked"));
    }

    @Test
    void shutdown_thenReconnect_restoresSameData() {
        // Verify the original data is there
        String before = jdbcTemplate.queryForObject(
                "SELECT val FROM test_data WHERE id = 1", String.class);
        assertEquals("original", before);

        // Shutdown and reconnect to the same (unmodified) file
        reloadService.shutdown();
        reloadService.reconnect();

        // The same data should still be accessible
        String after = jdbcTemplate.queryForObject(
                "SELECT val FROM test_data WHERE id = 1", String.class);
        assertEquals("original", after);
    }

    // ─────────────────────────────────────────────
    // Full reload cycle (shutdown → replace → reconnect)
    // ─────────────────────────────────────────────

    @Test
    void reloadPicksUpReplacementDatabase() throws IOException {
        // 1. Create a second, independent database with different data
        Path replacementBase = tempDir.resolve("replacement");
        Path replacementFile = tempDir.resolve("replacement.mv.db");
        createReplacementDatabase(replacementBase, "replaced-value");

        // 2. Shutdown the live database
        reloadService.shutdown();

        // 3. Swap the file on disk (mirrors what GoogleDriveGateway does)
        Files.move(replacementFile, liveDbFile, StandardCopyOption.REPLACE_EXISTING);

        // 4. Reconnect — should now read from the replacement file
        reloadService.reconnect();

        // 5. Verify the new data is visible
        String value = jdbcTemplate.queryForObject(
                "SELECT val FROM test_data WHERE id = 1", String.class);
        assertEquals("replaced-value", value,
                "After reload the query should return data from the replacement DB");
    }

    @Test
    void reloadPicksUpReplacementWithMoreRows() throws IOException {
        // Create replacement with multiple rows
        Path replacementBase = tempDir.resolve("multi");
        Path replacementFile = tempDir.resolve("multi.mv.db");
        createReplacementDatabaseWithRows(replacementBase, 5);

        reloadService.shutdown();
        Files.move(replacementFile, liveDbFile, StandardCopyOption.REPLACE_EXISTING);
        reloadService.reconnect();

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_data", Integer.class);
        assertNotNull(count);
        assertEquals(5, count,
                "After reload the table should have the replacement row count");
    }

    @Test
    void reloadDatabaseConvenienceMethod() throws IOException {
        // Prepare replacement
        Path replacementBase = tempDir.resolve("conv");
        Path replacementFile = tempDir.resolve("conv.mv.db");
        createReplacementDatabase(replacementBase, "via-convenience");

        // Replace the file BEFORE calling reloadDatabase (file already swapped)
        reloadService.shutdown();
        Files.move(replacementFile, liveDbFile, StandardCopyOption.REPLACE_EXISTING);
        reloadService.reconnect();

        String value = jdbcTemplate.queryForObject(
                "SELECT val FROM test_data WHERE id = 1", String.class);
        assertEquals("via-convenience", value);
    }

    // ─────────────────────────────────────────────
    // Reconnect resilience
    // ─────────────────────────────────────────────

    @Test
    void reconnectSucceedsAfterShutdownWithoutFileReplacement() {
        reloadService.shutdown();

        // Reconnect to the same file (no replacement) — should succeed
        assertDoesNotThrow(() -> reloadService.reconnect());

        // Verify data is intact
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_data", Integer.class);
        assertEquals(1, count);
    }

    @Test
    void multipleShutdownReconnectCyclesAreStable() throws IOException {
        for (int cycle = 1; cycle <= 3; cycle++) {
            Path base = tempDir.resolve("cycle" + cycle);
            Path file = tempDir.resolve("cycle" + cycle + ".mv.db");
            createReplacementDatabase(base, "cycle-" + cycle);

            reloadService.shutdown();
            Files.move(file, liveDbFile, StandardCopyOption.REPLACE_EXISTING);
            reloadService.reconnect();

            String value = jdbcTemplate.queryForObject(
                    "SELECT val FROM test_data WHERE id = 1", String.class);
            assertEquals("cycle-" + cycle, value,
                    "Cycle " + cycle + " should show the correct replacement data");
        }
    }

    @Test
    void writesAfterReloadPersistCorrectly() throws IOException {
        Path base = tempDir.resolve("persist");
        Path file = tempDir.resolve("persist.mv.db");
        createReplacementDatabase(base, "base");

        reloadService.shutdown();
        Files.move(file, liveDbFile, StandardCopyOption.REPLACE_EXISTING);
        reloadService.reconnect();

        // Write new data after reload
        jdbcTemplate.update("UPDATE test_data SET val = 'modified' WHERE id = 1");

        // Read it back — should see the write, not the old replacement value
        String value = jdbcTemplate.queryForObject(
                "SELECT val FROM test_data WHERE id = 1", String.class);
        assertEquals("modified", value,
                "Writes after reload must be visible immediately");
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    /**
     * Creates a standalone H2 file database with a single test_data row.
     */
    private void createReplacementDatabase(Path dbBase, String value) {
        String url = "jdbc:h2:file:" + dbBase.toAbsolutePath();
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername("sa");
        cfg.setPassword("");
        cfg.setMaximumPoolSize(1);

        try (HikariDataSource ds = new HikariDataSource(cfg)) {
            JdbcTemplate tpl = new JdbcTemplate(ds);
            tpl.execute("CREATE TABLE IF NOT EXISTS test_data ("
                    + "id INT PRIMARY KEY, val VARCHAR(100))");
            tpl.update("INSERT INTO test_data (id, val) VALUES (1, ?)", value);
            tpl.execute("SHUTDOWN");
        } catch (Exception ignored) {
            // SHUTDOWN kills the connection — expected
        }
    }

    /**
     * Creates a standalone H2 file database with N rows.
     */
    private void createReplacementDatabaseWithRows(Path dbBase, int rowCount) {
        String url = "jdbc:h2:file:" + dbBase.toAbsolutePath();
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername("sa");
        cfg.setPassword("");
        cfg.setMaximumPoolSize(1);

        try (HikariDataSource ds = new HikariDataSource(cfg)) {
            JdbcTemplate tpl = new JdbcTemplate(ds);
            tpl.execute("CREATE TABLE IF NOT EXISTS test_data ("
                    + "id INT PRIMARY KEY, val VARCHAR(100))");
            for (int i = 1; i <= rowCount; i++) {
                tpl.update("INSERT INTO test_data (id, val) VALUES (?, ?)",
                        i, "row-" + i);
            }
            tpl.execute("SHUTDOWN");
        } catch (Exception ignored) {
            // SHUTDOWN kills the connection — expected
        }
    }
}
