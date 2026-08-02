-- StudySync Database Schema
-- This file is automatically loaded by Spring Boot on startup

-- ===================================
-- Tasks Table
-- ===================================
CREATE TABLE IF NOT EXISTS tasks (
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===================================
-- Task Reschedules Table
-- ===================================
-- Full history of task deadline changes: one row per reschedule.
-- For DELAYED tasks a reschedule sets a fresh due date; for POSTPONED
-- tasks the new deadline acts as the "resume on" date.
CREATE TABLE IF NOT EXISTS task_reschedules (
    id VARCHAR(50) PRIMARY KEY,
    task_id VARCHAR(50) NOT NULL,
    old_deadline DATE,
    new_deadline DATE NOT NULL,
    rescheduled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
);

-- ===================================
-- Projects Table
-- ===================================
CREATE TABLE IF NOT EXISTS projects (
    id VARCHAR(50) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    priority INTEGER DEFAULT 1,
    start_date DATE,
    deadline DATE,
    completion_date DATE,
    progress_percentage INTEGER DEFAULT 0,
    estimated_hours INTEGER,
    actual_hours INTEGER,
    total_minutes_worked INTEGER,
    total_sessions_count INTEGER,
    last_worked_on TIMESTAMP,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===================================
-- Study Sessions Table
-- ===================================
CREATE TABLE IF NOT EXISTS study_sessions (
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
    -- goal_id/task_id FKs are added in the migrations section below, after
    -- the study_goals table exists.
);

-- ===================================
-- Project Sessions Table
-- ===================================
CREATE TABLE IF NOT EXISTS project_sessions (
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
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

-- ===================================
-- Study Goals Table
-- ===================================
CREATE TABLE IF NOT EXISTS study_goals (
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
    status VARCHAR(20) DEFAULT 'ACTIVE',
    abandoned_explicitly BOOLEAN DEFAULT FALSE,
    achieved_attempt_id VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE SET NULL
);

-- ===================================
-- Study Goal Attempts Table
-- ===================================
CREATE TABLE IF NOT EXISTS study_goal_attempts (
    id VARCHAR(50) PRIMARY KEY,
    goal_id VARCHAR(50) NOT NULL,
    planned_for_date DATE NOT NULL,
    replanned_from_attempt_id VARCHAR(50),
    outcome VARCHAR(20) DEFAULT 'PENDING',
    reason_if_not_achieved TEXT,
    outcome_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (goal_id) REFERENCES study_goals(id) ON DELETE CASCADE,
    FOREIGN KEY (replanned_from_attempt_id) REFERENCES study_goal_attempts(id) ON DELETE SET NULL
);

-- ===================================
-- Daily Reflections Table
-- ===================================
CREATE TABLE IF NOT EXISTS daily_reflections (
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
);

-- ===================================
-- Off Days Table
-- ===================================
-- Days the user marked as off / holiday. Sessions and goals dated on one of
-- these days never count towards global scores.
CREATE TABLE IF NOT EXISTS off_days (
    date DATE PRIMARY KEY,
    label VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===================================
-- Task Categories Table (Optional for future use)
-- ===================================
CREATE TABLE IF NOT EXISTS task_categories (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert default categories (H2 compatible - using MERGE for upsert)
MERGE INTO task_categories (id, name, description) 
VALUES 
    ('work-cat', 'Work', 'Work-related tasks'),
    ('personal-cat', 'Personal', 'Personal tasks'),
    ('study-cat', 'Study', 'Learning and study tasks'),
    ('health-cat', 'Health', 'Health and wellness tasks');

-- ===================================
-- Indexes for better performance
-- ===================================
CREATE INDEX IF NOT EXISTS idx_tasks_category ON tasks(category);
CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);
CREATE INDEX IF NOT EXISTS idx_tasks_deadline ON tasks(deadline);
CREATE INDEX IF NOT EXISTS idx_task_reschedules_task_id ON task_reschedules(task_id);

CREATE INDEX IF NOT EXISTS idx_projects_status ON projects(status);
CREATE INDEX IF NOT EXISTS idx_projects_category ON projects(category);

CREATE INDEX IF NOT EXISTS idx_study_sessions_date ON study_sessions(date);
CREATE INDEX IF NOT EXISTS idx_project_sessions_date ON project_sessions(date);
CREATE INDEX IF NOT EXISTS idx_project_sessions_project_id ON project_sessions(project_id);

CREATE INDEX IF NOT EXISTS idx_study_goals_date ON study_goals(date);
CREATE INDEX IF NOT EXISTS idx_study_goals_is_delayed ON study_goals(is_delayed);
CREATE INDEX IF NOT EXISTS idx_study_goal_attempts_goal_id ON study_goal_attempts(goal_id);
CREATE INDEX IF NOT EXISTS idx_study_goal_attempts_date ON study_goal_attempts(planned_for_date);
CREATE INDEX IF NOT EXISTS idx_study_goal_attempts_outcome ON study_goal_attempts(outcome);
CREATE INDEX IF NOT EXISTS idx_daily_reflections_date ON daily_reflections(date);

-- ===================================
-- Schema Migrations (for existing databases)
-- ===================================
-- Add recurring_pattern column to tasks table for existing databases.
-- For new databases the column is already in the CREATE TABLE above.
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS recurring_pattern VARCHAR(100);

-- Add start_date column for recurring tasks (recurrence anchor / first occurrence).
-- For new databases the column is already in the CREATE TABLE above.
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS start_date DATE;

-- Add replanned_for_date to study_goals to support one-shot manual rescheduling.
-- When set, the goal appears on that date only and is excluded from automatic delay carry-forward.
ALTER TABLE study_goals ADD COLUMN IF NOT EXISTS replanned_for_date DATE;

-- Add failed flag to study_goals. Failed goals are kept for historical logging
-- but excluded from active planner views.
ALTER TABLE study_goals ADD COLUMN IF NOT EXISTS failed BOOLEAN DEFAULT FALSE;

-- Link study sessions to an optional goal/task (issue #17). Named constraints
-- keep the ALTERs idempotent for both fresh and migrated databases.
ALTER TABLE study_sessions ADD COLUMN IF NOT EXISTS goal_id VARCHAR(50);
ALTER TABLE study_sessions ADD COLUMN IF NOT EXISTS task_id VARCHAR(50);
ALTER TABLE study_sessions ADD CONSTRAINT IF NOT EXISTS fk_study_sessions_goal
    FOREIGN KEY (goal_id) REFERENCES study_goals(id) ON DELETE SET NULL;
ALTER TABLE study_sessions ADD CONSTRAINT IF NOT EXISTS fk_study_sessions_task
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE SET NULL;

-- Add per-attempt goal lifecycle columns. Legacy columns remain intentionally:
-- schema.sql is re-run on every startup, so destructive DROP COLUMN migrations
-- would make the compatibility backfill below unsafe on later launches.
ALTER TABLE study_goals ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE';
ALTER TABLE study_goals ADD COLUMN IF NOT EXISTS abandoned_explicitly BOOLEAN DEFAULT FALSE;
ALTER TABLE study_goals ADD COLUMN IF NOT EXISTS achieved_attempt_id VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_study_goals_status ON study_goals(status);

-- Backfill the first attempt from the legacy study_goals row. Existing attempt
-- rows are left untouched so this migration is safe to re-run.
INSERT INTO study_goal_attempts (
    id, goal_id, planned_for_date, replanned_from_attempt_id, outcome,
    reason_if_not_achieved, outcome_at, created_at, updated_at
)
SELECT
    id || '-attempt-1',
    id,
    date,
    NULL,
    CASE
        WHEN replanned_for_date IS NOT NULL THEN 'MISSED'
        WHEN failed = TRUE THEN 'MISSED'
        WHEN achieved = TRUE THEN 'ACHIEVED'
        ELSE 'PENDING'
    END,
    reason_if_not_achieved,
    CASE
        WHEN replanned_for_date IS NOT NULL OR failed = TRUE OR achieved = TRUE THEN updated_at
        ELSE NULL
    END,
    created_at,
    updated_at
FROM study_goals g
WHERE NOT EXISTS (
    SELECT 1 FROM study_goal_attempts a WHERE a.goal_id = g.id
);

-- Backfill the explicit re-plan attempt when the legacy row had one. If the
-- re-planned goal was eventually achieved, the achieved outcome belongs to the
-- re-plan date, while the original date remains a missed attempt.
INSERT INTO study_goal_attempts (
    id, goal_id, planned_for_date, replanned_from_attempt_id, outcome,
    reason_if_not_achieved, outcome_at, created_at, updated_at
)
SELECT
    id || '-attempt-2',
    id,
    replanned_for_date,
    id || '-attempt-1',
    CASE
        WHEN achieved = TRUE THEN 'ACHIEVED'
        WHEN failed = TRUE THEN 'MISSED'
        ELSE 'PENDING'
    END,
    reason_if_not_achieved,
    CASE
        WHEN achieved = TRUE OR failed = TRUE THEN updated_at
        ELSE NULL
    END,
    updated_at,
    updated_at
FROM study_goals g
WHERE replanned_for_date IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM study_goal_attempts a WHERE a.id = g.id || '-attempt-2'
  )
  -- Only chain onto a backfilled attempt-1. Goals whose attempts were created
  -- through the app have UUID attempt ids, and inserting attempt-2 for them
  -- would violate the replanned_from_attempt_id FK and abort schema init.
  AND EXISTS (
      SELECT 1 FROM study_goal_attempts a WHERE a.id = g.id || '-attempt-1'
  );

UPDATE study_goals g
SET achieved_attempt_id = (
    SELECT a.id
    FROM study_goal_attempts a
    WHERE a.goal_id = g.id AND a.outcome = 'ACHIEVED'
    ORDER BY a.planned_for_date DESC, a.created_at DESC
    LIMIT 1
)
WHERE g.achieved_attempt_id IS NULL
  AND COALESCE(g.abandoned_explicitly, FALSE) = FALSE
  AND EXISTS (
      SELECT 1 FROM study_goal_attempts a
      WHERE a.goal_id = g.id AND a.outcome = 'ACHIEVED'
  );

-- Explicitly abandoned goals stay abandoned: this backfill must not undo a
-- user's abandon action on the next startup.
UPDATE study_goals g
SET status = 'ACHIEVED'
WHERE COALESCE(g.abandoned_explicitly, FALSE) = FALSE
  AND EXISTS (
    SELECT 1 FROM study_goal_attempts a
    WHERE a.goal_id = g.id AND a.outcome = 'ACHIEVED'
);

-- Legacy "failed" goals were missed attempts, not explicit abandonment.
-- Keep missed-only parent goals active so users can plan another retry.
UPDATE study_goals g
SET status = 'ACTIVE', failed = FALSE
WHERE status = 'ABANDONED'
  AND COALESCE(abandoned_explicitly, FALSE) = FALSE
  AND NOT EXISTS (
      SELECT 1 FROM study_goal_attempts a
      WHERE a.goal_id = g.id AND a.outcome = 'ACHIEVED'
  );

-- ===================================
-- Scoring Migrations
-- ===================================
-- Completion date of a task. Drives the timeliness component of the score and
-- decides which day the task's points land on.
--
-- Tasks completed before this column existed are deliberately left NULL rather
-- than backfilled. No column records when they were finished - `updated_at` is
-- never written by the app, so it holds the row's insert time, and `created_at`
-- holds whatever the last save stamped. Guessing would hand every legacy task a
-- fabricated +20 or -10 timeliness score and land those points on an arbitrary
-- day. A NULL scores zero, which is the honest answer for work finished before
-- anyone was measuring.
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS completed_at DATE;

-- Points are derived data, so they are recomputed from duration/focus on every
-- startup. This keeps historical sessions on the same scale as new ones and is
-- idempotent: same inputs always produce the same points.
-- Integer arithmetic only, so it matches StudySession.calculatePoints() exactly
-- (a floating-point version would round differently in edge cases).
UPDATE study_sessions
SET points_earned =
    (LEAST(GREATEST(COALESCE(duration_minutes, 0), 0), 240) / 2
        * CASE COALESCE(focus_level, 3)
              WHEN 1 THEN 40
              WHEN 2 THEN 70
              WHEN 4 THEN 120
              WHEN 5 THEN 140
              ELSE 100
          END
        + 50) / 100
    + CASE WHEN completed THEN 10 ELSE 0 END;

-- Same shape for project sessions, which carry no focus rating.
UPDATE project_sessions
SET points_earned = LEAST(GREATEST(COALESCE(duration_minutes, 0), 0), 240) / 2
    + CASE WHEN completed THEN 10 ELSE 0 END;

CREATE INDEX IF NOT EXISTS idx_tasks_completed_at ON tasks(completed_at);

-- Recurring tasks used to overload `deadline` as the end-of-recurrence date,
-- which left them unable to express a real due date. The two are separate now.
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS recurrence_end_date DATE;

-- One-shot migrations need a marker: every other statement in this file is
-- either additive or recomputes derived data, so re-running is harmless, but
-- moving `deadline` into `recurrence_end_date` would eat a genuine deadline
-- set on a recurring task after the split.
CREATE TABLE IF NOT EXISTS schema_migrations (
    id VARCHAR(100) PRIMARY KEY,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

UPDATE tasks
SET recurrence_end_date = deadline, deadline = NULL
WHERE recurring_pattern IS NOT NULL
  AND deadline IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM schema_migrations WHERE id = 'split-recurrence-end-from-deadline');

MERGE INTO schema_migrations (id) VALUES ('split-recurrence-end-from-deadline');

-- Project work was stored only as whole hours (actual_hours), so every save
-- truncated the minutes and every reload multiplied the loss. Session counts
-- and last-worked-on were never stored at all.
ALTER TABLE projects ADD COLUMN IF NOT EXISTS total_minutes_worked INTEGER;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS total_sessions_count INTEGER;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS last_worked_on TIMESTAMP;

-- Recover the exact figures once, from the sessions themselves - they were
-- never lossy - falling back to the truncated actual_hours for work that has no
-- surviving session rows. GREATEST because deleting a session used to leave the
-- project total untouched, so actual_hours can legitimately exceed the sum.
--
-- One-shot and marker-guarded. `total_sessions_count` cannot guard it: the row
-- mapper reads a NULL count as 0 and Project.save() writes that 0 straight back,
-- so a NULL-based guard would let this destructive statement fire again on a
-- later startup and overwrite figures the app had since maintained itself.
UPDATE projects p
SET total_sessions_count = (
        SELECT COUNT(*) FROM project_sessions s WHERE s.project_id = p.id AND s.completed = TRUE),
    total_minutes_worked = GREATEST(
        COALESCE(p.actual_hours, 0) * 60,
        (SELECT COALESCE(SUM(s.duration_minutes), 0) FROM project_sessions s
         WHERE s.project_id = p.id AND s.completed = TRUE))
WHERE NOT EXISTS (SELECT 1 FROM schema_migrations WHERE id = 'recover-project-work-totals');

MERGE INTO schema_migrations (id) VALUES ('recover-project-work-totals');

-- How many days before its deadline a task should start reminding, or NULL for
-- no reminder. The reminder date is derived rather than stored: the deadline is
-- already here, and a stored date would silently go stale the moment the
-- deadline moved.
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS remind_days_before INTEGER;

-- tasks.updated_at is gone: no write path ever set it, so it only ever held the
-- row's insert time while looking like a modification timestamp - which is what
-- made an earlier completed_at backfill read it as one.
ALTER TABLE tasks DROP COLUMN IF EXISTS updated_at;
