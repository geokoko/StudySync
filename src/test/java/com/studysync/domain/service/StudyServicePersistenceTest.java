package com.studysync.domain.service;

import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.StudySession;
import com.studysync.integration.drive.GoogleDriveService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudyServicePersistenceTest {

    private HikariDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private GoogleDriveService googleDriveService;
    private DateTimeService dateTimeService;
    private StudyService studyService;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:studysync-persistence-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        dataSource = new HikariDataSource(config);
        jdbcTemplate = new JdbcTemplate(dataSource);

        createStudySessionsTable();
        createStudyGoalsTable();

        StudySession.setJdbcTemplate(jdbcTemplate);
        StudyGoal.setJdbcTemplate(jdbcTemplate);

        googleDriveService = mock(GoogleDriveService.class);
        when(googleDriveService.saveLocally()).thenReturn(true);

        dateTimeService = mock(DateTimeService.class);
        when(dateTimeService.getCurrentDate()).thenReturn(LocalDate.of(2026, 3, 28));

        studyService = new StudyService(googleDriveService, dateTimeService);
    }

    @AfterEach
    void tearDown() {
        StudySession.setJdbcTemplate(null);
        StudyGoal.setJdbcTemplate(null);
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Test
    void startStudySessionPersistsActiveSessionAndFlushesLocally() {
        StudySession session = studyService.startStudySession();

        assertTrue(session.isActive());
        assertFalse(session.isCompleted());
        assertNotNull(session.getStartTime());

        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM study_sessions WHERE id = ? AND completed = FALSE AND is_active = TRUE",
                Integer.class,
                session.getId());
        assertEquals(1, activeCount);

        verify(googleDriveService).markLocalDbDirty();
        verify(googleDriveService).saveLocally();
    }

    @Test
    void endStudySessionUpdatesExistingRowToCompletedAndFlushesLocally() {
        StudySession session = studyService.startStudySession();
        session.setSessionText("Recovered notes");

        reset(googleDriveService);
        when(googleDriveService.saveLocally()).thenReturn(true);

        studyService.endStudySession(session, new StudySessionEnd(2, "Wrapped up chapter"));

        StudySession stored = StudySession.findById(session.getId()).orElseThrow();
        assertTrue(stored.isCompleted());
        assertFalse(stored.isActive());
        assertEquals(2, stored.getFocusLevel());
        assertEquals("Wrapped up chapter", stored.getNotes());
        assertEquals("Recovered notes", stored.getSessionText());
        assertNotNull(stored.getEndTime());
        assertTrue(stored.getDurationMinutes() >= 0);

        verify(googleDriveService).markLocalDbDirty();
        verify(googleDriveService).saveLocally();
    }

    @Test
    void findByDateRestoresPersistedIncompleteSessionMetadata() {
        LocalDate date = LocalDate.of(2026, 3, 27);
        LocalDateTime lastUpdate = LocalDateTime.of(2026, 3, 27, 18, 45);

        jdbcTemplate.update("""
                INSERT INTO study_sessions (
                    id, date, start_time, end_time, duration_minutes, completed, focus_level,
                    confidence_level, notes, session_text, is_active, last_update_time,
                    current_elapsed_minutes, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                "active-session", date, LocalDateTime.of(2026, 3, 27, 18, 0), null, 45, false,
                3, 3, "In progress", "Partial notes", true, lastUpdate, 45);

        List<StudySession> sessions = StudySession.findByDate(date);

        assertEquals(1, sessions.size());
        StudySession restored = sessions.getFirst();
        assertFalse(restored.isCompleted());
        assertTrue(restored.isActive());
        assertEquals(lastUpdate, restored.getLastUpdateTime());
        assertEquals("Partial notes", restored.getSessionText());
    }

    @Test
    void updateStudyGoalAchievementFlushesCompletedGoalLocally() {
        StudyGoal goal = new StudyGoal("Finish mock exam");
        goal.setDate(LocalDate.of(2026, 3, 27));
        goal.save();

        reset(googleDriveService);
        when(googleDriveService.saveLocally()).thenReturn(true);

        studyService.updateStudyGoalAchievement(goal.getId(), true, null);

        StudyGoal stored = StudyGoal.findById(goal.getId()).orElseThrow();
        assertTrue(stored.isAchieved());

        Integer achievedAttempts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM study_goal_attempts WHERE goal_id = ? AND outcome = 'ACHIEVED'",
                Integer.class,
                goal.getId());
        assertEquals(1, achievedAttempts);

        verify(googleDriveService).markLocalDbDirty();
        verify(googleDriveService).saveLocally();
    }

    @Test
    void overdueGoalAttemptBecomesMissedAndCanBeReplanned() {
        StudyGoal goal = new StudyGoal("Review missed topic");
        goal.setDate(LocalDate.of(2026, 3, 27));
        goal.save();

        reset(googleDriveService);
        when(googleDriveService.saveLocally()).thenReturn(true);

        StudyService.GoalDelayProcessingResult result = studyService.processAllDelayedGoals();

        assertEquals(1, result.updatedGoals());
        assertEquals(0, result.failedGoals());
        assertEquals(1, StudyGoal.findDelayedAndNotReplanned().size());

        studyService.replanGoalForToday(goal.getId());

        List<StudyGoal> todayGoals = studyService.getStudyGoalsForDate(LocalDate.of(2026, 3, 28));
        assertEquals(1, todayGoals.size());
        assertEquals(2, todayGoals.getFirst().getAttemptNumber());
        assertEquals(1, todayGoals.getFirst().getMissedAttemptCount());

        Integer attempts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM study_goal_attempts WHERE goal_id = ?",
                Integer.class,
                goal.getId());
        assertEquals(2, attempts);
    }

    @Test
    void explicitlyAbandonedGoalIsNotOfferedForRetry() {
        StudyGoal goal = new StudyGoal("Drop this goal");
        goal.setDate(LocalDate.of(2026, 3, 27));
        goal.save();

        studyService.processAllDelayedGoals();
        assertEquals(1, StudyGoal.findDelayedAndNotReplanned().size());

        assertTrue(studyService.markGoalAsFailed(goal.getId()));

        StudyGoal stored = StudyGoal.findById(goal.getId()).orElseThrow();
        assertEquals(StudyGoal.GoalStatus.ABANDONED, stored.getStatus());
        assertTrue(stored.isAbandonedExplicitly());
        assertTrue(StudyGoal.findDelayedAndNotReplanned().isEmpty());
    }

    @Test
    void taskGoalDetailsCanBeEditedAndRetriedForChosenDate() {
        studyService.addStudyGoal("Read chapter", LocalDate.of(2026, 3, 27), "task-1");

        StudyGoal pending = StudyGoal.findByTaskId("task-1").getFirst();
        assertTrue(studyService.updateStudyGoalDetails(
                pending.getId(), "Read chapter and write notes", LocalDate.of(2026, 3, 29)));

        StudyGoal edited = StudyGoal.findById(pending.getId()).orElseThrow();
        assertEquals("Read chapter and write notes", edited.getDescription());
        assertEquals(LocalDate.of(2026, 3, 29), edited.getDate());

        jdbcTemplate.update("""
                UPDATE study_goal_attempts
                SET outcome = 'MISSED', outcome_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE goal_id = ?
                """, edited.getId());

        assertTrue(studyService.planGoalAttempt(edited.getId(), LocalDate.of(2026, 4, 2)));

        List<StudyGoal> attempts = StudyGoal.findByTaskId("task-1");
        assertEquals(2, attempts.size());
        StudyGoal latest = StudyGoal.findById(edited.getId()).orElseThrow();
        assertEquals(StudyGoal.AttemptOutcome.PENDING, latest.getAttemptOutcome());
        assertEquals(LocalDate.of(2026, 4, 2), latest.getDate());
        assertEquals(2, latest.getAttemptNumber());
    }

    @Test
    void getActiveSessionFindsSessionThatStartedPreviousDay() {
        LocalDate sessionDate = LocalDate.of(2026, 3, 27);
        LocalDateTime startTime = LocalDateTime.of(2026, 3, 27, 23, 50);
        LocalDateTime lastUpdate = LocalDateTime.of(2026, 3, 28, 0, 10);

        jdbcTemplate.update("""
                INSERT INTO study_sessions (
                    id, date, start_time, end_time, duration_minutes, completed, focus_level,
                    confidence_level, notes, session_text, is_active, last_update_time,
                    current_elapsed_minutes, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                "overnight-session", sessionDate, startTime, null, 20, false,
                3, 3, "Still running", "Overnight notes", true, lastUpdate, 20);

        StudySession restored = studyService.getActiveSession().orElseThrow();

        assertEquals("overnight-session", restored.getId());
        assertEquals(sessionDate, restored.getDate());
        assertEquals(startTime, restored.getStartTime());
        assertTrue(restored.isActive());
        assertFalse(restored.isCompleted());
    }

    private void createStudySessionsTable() {
        jdbcTemplate.execute("""
                CREATE TABLE study_sessions (
                    id VARCHAR(50) PRIMARY KEY,
                    date DATE NOT NULL,
                    start_time TIMESTAMP,
                    end_time TIMESTAMP,
                    duration_minutes INTEGER DEFAULT 0,
                    completed BOOLEAN DEFAULT FALSE,
                    focus_level INTEGER DEFAULT 3,
                    confidence_level INTEGER DEFAULT 3,
                    notes TEXT,
                    subject VARCHAR(255),
                    topic VARCHAR(255),
                    location VARCHAR(255),
                    outcome_expected TEXT,
                    actual_work TEXT,
                    what_helped TEXT,
                    what_distracted TEXT,
                    improvement_note TEXT,
                    points_earned INTEGER DEFAULT 0,
                    session_text TEXT,
                    is_active BOOLEAN DEFAULT FALSE,
                    last_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    current_elapsed_minutes INTEGER DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private void createStudyGoalsTable() {
        jdbcTemplate.execute("""
                CREATE TABLE study_goals (
                    id VARCHAR(50) PRIMARY KEY,
                    date DATE NOT NULL,
                    description TEXT NOT NULL,
                    achieved BOOLEAN DEFAULT FALSE,
                    reason_if_not_achieved TEXT,
                    days_delayed INTEGER DEFAULT 0,
                    is_delayed BOOLEAN DEFAULT FALSE,
                    points_deducted INTEGER DEFAULT 0,
                    task_id VARCHAR(50),
                    replanned_for_date DATE,
                    failed BOOLEAN DEFAULT FALSE,
                    status VARCHAR(20) DEFAULT 'ACTIVE',
                    abandoned_explicitly BOOLEAN DEFAULT FALSE,
                    achieved_attempt_id VARCHAR(50),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE study_goal_attempts (
                    id VARCHAR(50) PRIMARY KEY,
                    goal_id VARCHAR(50) NOT NULL,
                    planned_for_date DATE NOT NULL,
                    replanned_from_attempt_id VARCHAR(50),
                    outcome VARCHAR(20) DEFAULT 'PENDING',
                    reason_if_not_achieved TEXT,
                    outcome_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
