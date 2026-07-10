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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
