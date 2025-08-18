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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
    is_active BOOLEAN DEFAULT FALSE,
    last_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    current_elapsed_minutes INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE SET NULL
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

CREATE INDEX IF NOT EXISTS idx_projects_status ON projects(status);
CREATE INDEX IF NOT EXISTS idx_projects_category ON projects(category);

CREATE INDEX IF NOT EXISTS idx_study_sessions_date ON study_sessions(date);
CREATE INDEX IF NOT EXISTS idx_project_sessions_date ON project_sessions(date);
CREATE INDEX IF NOT EXISTS idx_project_sessions_project_id ON project_sessions(project_id);

CREATE INDEX IF NOT EXISTS idx_study_goals_date ON study_goals(date);
CREATE INDEX IF NOT EXISTS idx_study_goals_is_delayed ON study_goals(is_delayed);
CREATE INDEX IF NOT EXISTS idx_daily_reflections_date ON daily_reflections(date);