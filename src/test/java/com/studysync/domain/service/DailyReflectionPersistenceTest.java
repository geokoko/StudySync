package com.studysync.domain.service;

import com.studysync.domain.entity.DailyReflection;
import com.studysync.integration.drive.GoogleDriveService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DailyReflectionPersistenceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 28);

    private HikariDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private StudyService studyService;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:studysync-reflections-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        dataSource = new HikariDataSource(config);
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
            CREATE TABLE daily_reflections (
                id VARCHAR(50) PRIMARY KEY,
                date DATE NOT NULL UNIQUE,
                overall_focus_level INTEGER DEFAULT 3,
                what_to_change_tomorrow TEXT,
                completed_sessions INTEGER DEFAULT 0,
                total_goals_achieved INTEGER DEFAULT 0,
                notes TEXT,
                reflection_text TEXT,
                deserve_reward BOOLEAN DEFAULT FALSE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
        DailyReflection.setJdbcTemplate(jdbcTemplate);

        GoogleDriveService googleDriveService = mock(GoogleDriveService.class);
        when(googleDriveService.saveLocally()).thenReturn(true);
        DateTimeService dateTimeService = mock(DateTimeService.class);
        when(dateTimeService.getCurrentDate()).thenReturn(DAY);

        studyService = new StudyService(googleDriveService, dateTimeService);
    }

    @AfterEach
    void tearDown() {
        DailyReflection.setJdbcTemplate(null);
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Test
    void secondSaveForSameDayOverwritesInsteadOfDuplicating() {
        DailyReflection first = new DailyReflection();
        first.setDate(DAY);
        first.setReflectionText("Morning thoughts");
        first.save();

        // A fresh entity for the same day — what the planner's quick box builds.
        DailyReflection second = new DailyReflection();
        second.setDate(DAY);
        second.setReflectionText("Evening thoughts");
        second.save();

        List<DailyReflection> all = DailyReflection.findAll();
        assertEquals(1, all.size());
        assertEquals("Evening thoughts", all.get(0).getReflectionText());
    }

    @Test
    void saveReflectionTextUpsertsThenDeletesWhenBlanked() {
        studyService.saveReflectionText(DAY, "Got through the whole chapter.");
        assertEquals("Got through the whole chapter.",
                DailyReflection.findByDate(DAY).orElseThrow().getReflectionText());

        studyService.saveReflectionText(DAY, "Actually, half a chapter.");
        assertEquals("Actually, half a chapter.",
                DailyReflection.findByDate(DAY).orElseThrow().getReflectionText());
        assertEquals(1, DailyReflection.findAll().size());

        studyService.saveReflectionText(DAY, "   ");
        assertTrue(DailyReflection.findByDate(DAY).isEmpty());
    }

    @Test
    void markdownWhitespaceSurvivesTheRoundTrip() {
        // Leading spaces make a code block, trailing ones a hard line break:
        // trimming either would render something the user did not write.
        String entry = "# Day\n\n    indented code block\n\nline with break  \nnext line\n";

        studyService.saveReflectionText(DAY, entry);

        assertEquals(entry, DailyReflection.findByDate(DAY).orElseThrow().getReflectionText());
    }
}
