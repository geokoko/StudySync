# StudySync Architecture

## Current Architecture (Active Record Pattern + MVC)

### **Active Record Architecture**
```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                       │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐  │
│  │   JavaFX UI     │  │ REST Controllers │  │ RefreshablePanel│
│  │   Components    │  │ (TaskRestController)│ │  Interface     │
│  └─────────────────┘  └─────────────────┘  └─────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                Simplified Service Layer                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐  │
│  │   TaskService   │  │  StudyService   │  │ProjectService│ │
│  │ CategoryService │  │  ScoringService │  │   ...more   │  │
│  │ (Business Logic │  │   Orchestration) │  │             │  │
│  └─────────────────┘  └─────────────────┘  └─────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│            Active Record Domain Models                      │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐  │
│  │      Task       │  │   StudySession  │  │   Project   │  │
│  │  • save()       │  │  • save()       │  │ • save()    │  │
│  │  • delete()     │  │  • delete()     │  │ • delete()  │  │
│  │  • findAll()    │  │  • findAll()    │  │ • findAll() │  │
│  │  • findById()   │  │  • findById()   │  │ • findById()│  │
│  └─────────────────┘  └─────────────────┘  └─────────────┘  │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐  │
│  │ ProjectSession  │  │   TaskPriority  │  │TaskCategory │  │
│  │  OffDay         │  │   TaskStatus    │  │DailyReflection│
│  │  • delete()     │  │  ProjectStatus  │  │   ...more   │  │
│  │  • findAll()    │  │  (Value Objects)│  │             │  │
│  └─────────────────┘  └─────────────────┘  └─────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                    ┌─────────────────┐
                    │   H2 Database   │
                    │  (Direct JDBC)  │
                    └─────────────────┘
```

## Google Drive Sync Flow

The optional Google Drive integration transfers the whole H2 file, with explicit uploads and downloads:

1. **Bootstrap** – before Spring Boot opens H2, `StudySyncApplication` calls `bootstrap/PendingDownloadApplier.applyIfPresent` to apply a previously staged download. It verifies the staged file's size and SHA-256 checksum, backs up the existing database under `data/backups/` (for the default database path), and swaps in the staged file. `GoogleDriveBootstrap` initializes settings and checks for stored credentials; it does not download anything automatically.
2. **Runtime** – StudySync reads and writes the local H2 file. Service mutations mark it dirty and checkpoint it after commit. `Sync to Drive now` in Profile → Google Drive Sync calls `GoogleDriveService.uploadDatabaseSnapshot`, which checkpoints and verifies the local file before uploading. `Download from Drive` calls `GoogleDriveService.stageDownloadFromDrive`, which writes a separate file and integrity metadata beside the database for the next launch. The open database is never replaced or reloaded, and the two copies are never merged.
3. **Shutdown** – when signed in, `StudySyncJavaFXApp` offers **Push to Drive & Exit**, **Save Locally & Exit**, **Exit without Saving**, or **Cancel**. The first two flush pending UI edits; the upload option checkpoints and uploads before exiting, while the local option only checkpoints. Save or upload failure keeps the app open. Without a signed-in account, closing flushes pending edits and saves locally. There is no upload during bean destruction; **Exit without Saving** does not undo data already committed during the session.

OAuth credentials live on the user's machine (`~/.studysync/google`) and the cloud copy resides inside the user's own Drive (`My Drive/StudySync/studysync.mv.db`).

### **Key Characteristics**
- **Active Record Pattern**: Models handle their own database operations
- **Simplified Services**: Services focus on business logic and orchestration
- **No Database Service Layer**: Eliminated unnecessary abstraction layer
- **Self-Managing Models**: `task.save()`, `project.delete()`, `StudySession.findAll()`
- **Mutable Domain Objects**: Task, Project, StudySession are classes with behavior
- **Real-time Session Tracking**: Live timers with Duration-based calculations
- **Direct JDBC**: Models use JdbcTemplate directly for database operations

### **Recurring Tasks**

Tasks support an optional recurrence schedule via a `recurring_pattern` column:

- **Format**: `"intervalWeeks:daysOfWeek"` — e.g. `"1:1,3,5"` = every week on Mon, Wed, Fri; `"2:1,4"` = every 2 weeks on Mon, Thu
- **NULL** means a one-off (non-recurring) task
- `recurrence_end_date` is the last date an occurrence may fall on; NULL means it never ends. Before 0.1.6 this was conflated with `deadline`, which left recurring tasks unable to express a real due date
- `deadline` means the same thing on recurring and one-off tasks: when the task is due. It drives overdue badges and the timeliness part of the score, and deliberately does *not* affect whether an occurrence appears
- `Task.isRecurring()` and `Task.getRecurringSummary()` provide runtime helpers
- `TaskService.isRecurringOccurrence(task, date)` answers "does the schedule land here", which is a different question from `taskSurfacesOn(task, date)`: a late recurring task also surfaces every day past its deadline until resolved. Anything counting missed occurrences must use the former
- When editing, `""` (empty string) in `TaskUpdate.recurringPattern` signals "clear the pattern", while `null` means "keep existing". `TaskUpdate.CLEAR_REMINDER` plays the same role for the reminder offset

### **Scoring**

`ScoringService` is the single place that decides what counts. The calendar's per-day figures and the profile's 30-day figures are both `ScoreBreakdown`s over different date ranges, so the two cannot drift apart the way two hand-written formulas did.

- **Sessions** earn time scaled by focus, so a long average session can never be worth less than a short self-flattered one
- **Achieved goal attempts** earn a flat amount — session points already pay for the effort, this pays for the outcome
- **Completed tasks** earn timeliness only. A flat completion bonus would double-count the sessions and goals that finished the task; whether it landed before its deadline is information nothing else captures. Rescheduling costs points, so "on time" cannot be bought by moving the date
- Stored `points_earned` is treated as **derived** — see Schema Migrations below

### **Off Days**

Days marked off (holiday, sick day, break) are excluded from every global score and shrink the denominators rather than counting as days with no work. The study streak skips them instead of breaking. Per-day figures and the profile charts still show what actually happened on an off day — only the aggregates exclude it.

### **Future Goal Planning**

Study goals can be planned for future dates:

- `StudyService.getGoalsForDate(date)` is the single definition shared by scoring and display, so a day's goal count always matches the list rendered beside it. Future dates skip the overdue sweep — an attempt planned for tomorrow cannot be overdue
- The Study Planner's "Add Goal" dialog includes a DatePicker (today + future only) with quick "Today" / "Tomorrow" buttons
- Calendar and Daily views branch on past/today/future when loading goals

### **Delayed Goal Processing Guard**

`processAllDelayedGoals()` performs a full table scan and write operations. To avoid redundant work on every UI refresh, a `lastDelayProcessingDate` field ensures it runs at most once per calendar day, via `StudyService.ensureOverdueAttemptsProcessed()`.

The sweep saves without setting the Drive dirty flag. It is derived maintenance — every machine recomputes it on startup — so flagging it made simply opening the app look like unsaved local edits, which was enough to raise a sync conflict against a Drive copy that was genuinely ahead.

### **Schema Migrations**

Since `spring.sql.init.mode: always` loads `schema.sql` on every startup:

- Tables use `CREATE TABLE IF NOT EXISTS` (safe for existing DBs)
- New columns are added via `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` at the bottom of `schema.sql`
- Derived data may be recomputed on every startup, provided the computation is idempotent. Session points work this way: the SQL mirrors `StudySession.calculatePoints()` in integer arithmetic, and a test runs every input combination against both to keep them in step
- Genuinely one-shot migrations — moving a value from one column to another — are guarded by a `schema_migrations` marker table. Without it, re-running would eat a value the user set after the migration first ran
- This avoids data loss and supports rolling upgrades without external migration tools
