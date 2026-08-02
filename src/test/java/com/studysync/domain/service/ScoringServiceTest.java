package com.studysync.domain.service;

import com.studysync.domain.entity.OffDay;
import com.studysync.domain.entity.ProjectSession;
import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.StudySession;
import com.studysync.domain.entity.Task;
import com.studysync.domain.entity.TaskReschedule;
import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.domain.valueobject.TaskStatus;
import com.studysync.integration.drive.GoogleDriveService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScoringServiceTest {

    private HikariDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private StudyService studyService;
    private ScoringService scoringService;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:studysync-scoring-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        dataSource = new HikariDataSource(config);
        jdbcTemplate = new JdbcTemplate(dataSource);
        createTables();

        StudySession.setJdbcTemplate(jdbcTemplate);
        ProjectSession.setJdbcTemplate(jdbcTemplate);
        Task.setJdbcTemplate(jdbcTemplate);
        TaskReschedule.setJdbcTemplate(jdbcTemplate);
        StudyGoal.setJdbcTemplate(jdbcTemplate);
        OffDay.setJdbcTemplate(jdbcTemplate);

        GoogleDriveService googleDriveService = mock(GoogleDriveService.class);
        when(googleDriveService.saveLocally()).thenReturn(true);

        // Entity finders are anchored on the real clock, so the fake current
        // date has to agree with it.
        today = LocalDate.now();
        DateTimeService dateTimeService = mock(DateTimeService.class);
        when(dateTimeService.getCurrentDate()).thenReturn(today);

        studyService = new StudyService(googleDriveService, dateTimeService);
        scoringService = new ScoringService(studyService, dateTimeService);
    }

    @AfterEach
    void tearDown() {
        StudySession.setJdbcTemplate(null);
        ProjectSession.setJdbcTemplate(null);
        Task.setJdbcTemplate(null);
        TaskReschedule.setJdbcTemplate(null);
        StudyGoal.setJdbcTemplate(null);
        OffDay.setJdbcTemplate(null);
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Test
    void longAverageSessionOutscoresShortSelfFlatteredOne() {
        // The defect the formula was rewritten to fix: 20 minutes at focus 5
        // used to beat three hours at focus 3.
        int shortGreatFocus = StudySession.calculatePoints(20, 5, true);
        int longAverageFocus = StudySession.calculatePoints(180, 3, true);

        assertTrue(longAverageFocus > shortGreatFocus,
                longAverageFocus + " should beat " + shortGreatFocus);
        assertEquals(24, shortGreatFocus);
        assertEquals(100, longAverageFocus);

        // Focus scales time rather than replacing it, and a poor session is
        // still worth more than not showing up.
        assertEquals(19, StudySession.calculatePoints(45, 1, true));
        assertEquals(10, StudySession.calculatePoints(0, 5, true));

        // Time stops paying after four hours.
        assertEquals(StudySession.calculatePoints(240, 3, true),
                StudySession.calculatePoints(600, 3, true));
    }

    @Test
    void sessionPointsMatchTheSqlMigrationExactly() {
        // schema.sql recomputes points in SQL on every startup. If the two ever
        // disagree, stored history silently drifts from freshly scored work.
        for (int minutes : new int[] {-30, -1, 0, 5, 20, 45, 61, 90, 121, 239, 240, 600}) {
            for (int focus = 1; focus <= 5; focus++) {
                for (boolean completed : new boolean[] {true, false}) {
                    Integer viaSql = jdbcTemplate.queryForObject("""
                            SELECT (LEAST(GREATEST(COALESCE(?, 0), 0), 240) / 2
                                    * CASE COALESCE(?, 3)
                                          WHEN 1 THEN 40
                                          WHEN 2 THEN 70
                                          WHEN 4 THEN 120
                                          WHEN 5 THEN 140
                                          ELSE 100
                                      END
                                    + 50) / 100
                                + CASE WHEN ? THEN 10 ELSE 0 END
                            """, Integer.class, minutes, focus, completed);

                    assertEquals(StudySession.calculatePoints(minutes, focus, completed), viaSql,
                            "minutes=" + minutes + " focus=" + focus + " completed=" + completed);
                }
            }
        }
    }

    @Test
    void offDaysAreExcludedFromTheWindowAndSkippedByTheStreak() {
        session(today, 60, 4);
        session(today.minusDays(2), 60, 4);
        session(today.minusDays(3), 60, 4);

        studyService.markOffDay(today.minusDays(1), "Holiday");
        studyService.markOffDay(today.minusDays(3), null);

        ScoreBreakdown score = scoringService.scoreForWindow(30);

        // The session on the off day is gone; the two scored ones remain.
        assertEquals(2, score.sessions());
        assertEquals(120, score.minutes());
        assertEquals(28, score.scoringDays());

        // Off days neither break the streak nor extend it: today plus day -2.
        assertEquals(2, scoringService.getStudyStreak());

        studyService.clearOffDay(today.minusDays(3));
        assertEquals(3, scoringService.scoreForWindow(30).sessions());
    }

    @Test
    void aFullyOffWindowReportsZeroScoringDaysRatherThanOne() {
        for (int i = 0; i < 30; i++) {
            studyService.markOffDay(today.minusDays(i), "Break");
        }

        // The profile derives "days marked off" by subtracting this from the
        // window size, so a floor of 1 would under-report by a day.
        assertEquals(0, scoringService.scoreForWindow(30).scoringDays());
    }

    @Test
    void projectOnlyWorkStillCountsTowardsConsistency() {
        projectSession(today, 120);

        ScoreBreakdown score = scoringService.scoreForWindow(30);

        // Points, minutes and the session count all include project work, so
        // the consistency term must too - otherwise a month of project-only
        // work scores as near-idle.
        assertEquals(1, score.sessions());
        assertTrue(score.productivity() > 0, "productivity was " + score.productivity());
    }

    @Test
    void completedTaskScoresOnTimelinessAndPaysForReschedules() {
        Task onTime = completedTask("on-time", today.plusDays(1), today);
        Task late = completedTask("late", today.minusDays(2), today);
        Task noDeadline = completedTask("no-deadline", null, today);
        Task unfinished = task("unfinished", today.minusDays(1));

        assertEquals(20, ScoringService.taskPoints(onTime, 0));
        assertEquals(-10, ScoringService.taskPoints(late, 0));
        assertEquals(0, ScoringService.taskPoints(noDeadline, 0));
        assertEquals(0, ScoringService.taskPoints(unfinished, 0));

        // Pushing the deadline is not a free way to land "on time".
        assertEquals(10, ScoringService.taskPoints(onTime, 2));
        assertEquals(0, ScoringService.taskPoints(onTime, 4));

        // One bad task cannot sink a whole month.
        assertEquals(ScoringService.TASK_MIN_POINTS, ScoringService.taskPoints(late, 10));
    }

    @Test
    void windowScoreAddsSessionsGoalsAndTaskTimeliness() {
        session(today, 60, 3);                       // 30 + 10 completed = 40
        projectSession(today, 60);                   // 30 + 10 completed = 40
        achievedGoal("Finished the chapter", today); // 15

        Task rescheduled = completedTask("shipped-late", today.minusDays(1), today);
        reschedule(rescheduled.getId(), today.minusDays(3), today.minusDays(1));
        // late (-10) plus one reschedule (-5)

        ScoreBreakdown score = scoringService.scoreForWindow(30);

        assertEquals(80, score.sessionPoints());
        assertEquals(15, score.goalPoints());
        assertEquals(-15, score.taskPoints());
        assertEquals(80, score.points());
        assertEquals(2, score.sessions());
        assertEquals(1, score.tasksCompleted());
        assertEquals(1, score.goalsAchieved());

        // A single day's score is the same calculation over one day.
        ScoreBreakdown day = scoringService.scoreForDate(today);
        assertEquals(score.points(), day.points());
        assertTrue(day.productivity() > 0);
    }

    private void session(LocalDate date, int minutes, int focus) {
        StudySession session = new StudySession();
        session.setDate(date);
        session.setDurationMinutes(minutes);
        session.setFocusLevel(focus);
        session.setCompleted(true);
        session.calculateAndSetPoints();
        session.save();
    }

    private void projectSession(LocalDate date, int minutes) {
        jdbcTemplate.update("INSERT INTO projects (id, title, status) VALUES (?, ?, 'ACTIVE')",
                "project-1", "Thesis");
        ProjectSession session = new ProjectSession("project-1");
        session.setDate(date);
        session.setDurationMinutes(minutes);
        session.setCompleted(true);
        session.calculatePoints();
        session.save();
    }

    private void achievedGoal(String description, LocalDate date) {
        StudyGoal goal = new StudyGoal(description);
        goal.setDate(date);
        goal.save();
        StudyGoal.markCurrentAttemptAchieved(goal.getId(), null);
    }

    private Task task(String id, LocalDate deadline) {
        return new Task(id, "Task " + id, "", "Study", new TaskPriority(3), deadline,
                TaskStatus.OPEN, 0).save();
    }

    private Task completedTask(String id, LocalDate deadline, LocalDate completedOn) {
        Task task = task(id, deadline);
        task.markCompleted();
        task.setCompletedAt(completedOn);
        task.save();
        return Task.findById(id).orElseThrow();
    }

    private void reschedule(String taskId, LocalDate from, LocalDate to) {
        new TaskReschedule(taskId, from, to).save();
    }

    private void createTables() {
        jdbcTemplate.execute("""
                CREATE TABLE tasks (
                    id VARCHAR(50) PRIMARY KEY,
                    title VARCHAR(255) NOT NULL,
                    description TEXT,
                    category VARCHAR(100),
                    priority INTEGER DEFAULT 1,
                    deadline DATE,
                    status VARCHAR(20) DEFAULT 'OPEN',
                    points INTEGER DEFAULT 0,
                    recurring_pattern VARCHAR(100),
                    start_date DATE,
                    recurrence_end_date DATE,
                    completed_at DATE,
                    remind_days_before INTEGER,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE task_reschedules (
                    id VARCHAR(50) PRIMARY KEY,
                    task_id VARCHAR(50) NOT NULL,
                    old_deadline DATE,
                    new_deadline DATE NOT NULL,
                    rescheduled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
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
                    goal_id VARCHAR(50),
                    task_id VARCHAR(50),
                    is_active BOOLEAN DEFAULT FALSE,
                    last_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    current_elapsed_minutes INTEGER DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE projects (
                    id VARCHAR(50) PRIMARY KEY,
                    title VARCHAR(255) NOT NULL,
                    status VARCHAR(20) DEFAULT 'ACTIVE'
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE project_sessions (
                    id VARCHAR(50) PRIMARY KEY,
                    project_id VARCHAR(50) NOT NULL,
                    date DATE NOT NULL,
                    start_time TIMESTAMP,
                    end_time TIMESTAMP,
                    duration_minutes INTEGER DEFAULT 0,
                    completed BOOLEAN DEFAULT FALSE,
                    session_title VARCHAR(255),
                    objectives TEXT,
                    progress TEXT,
                    next_steps TEXT,
                    challenges TEXT,
                    notes TEXT,
                    points_earned INTEGER DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE off_days (
                    date DATE PRIMARY KEY,
                    label VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
