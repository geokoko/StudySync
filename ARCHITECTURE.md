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
│  │ CategoryService │  │ ReminderService │  │   ...more   │  │
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
│  │  • save()       │  │   TaskStatus    │  │DailyReflection│
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

When the optional Google Drive integration is configured, the persistence layer gains an additional offline-first sync loop:

1. **Bootstrap** – before Spring Boot initializes the datasource, the `GoogleDriveBootstrap` downloads the latest `studysync.mv.db` from the signed-in Google Drive account (if cached credentials exist).
2. **Runtime** – StudySync continues to operate against the local H2 file for fast, offline reads/writes. Users can trigger a manual upload from the Profile → Google Drive Sync panel at any time.
3. **Shutdown** – `GoogleDriveService` uploads the updated H2 file to the user's private `StudySync` Drive folder during bean destruction, ensuring multi-device availability without a StudySync backend.

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
- `Task.isRecurring()` and `Task.getRecurringSummary()` provide runtime helpers
- `TaskService.recurringTaskAppliesTo(task, date, referenceMonday)` checks whether a recurring task should appear on a given date
- When editing, `""` (empty string) in `TaskUpdate.recurringPattern` signals "clear the pattern", while `null` means "keep existing"

### **Future Goal Planning**

Study goals can be planned for future dates:

- `StudyService.getStudyGoalsForFutureDate(date)` returns goals for a specific future date without delay processing
- The Study Planner's "Add Goal" dialog includes a DatePicker (today + future only) with quick "Today" / "Tomorrow" buttons
- Calendar and Daily views branch on past/today/future when loading goals

### **Delayed Goal Processing Guard**

`processAllDelayedGoals()` performs a full table scan and write operations. To avoid redundant work on every UI refresh, a `lastDelayProcessingDate` field ensures it runs at most once per calendar day.

### **Schema Migrations**

Since `spring.sql.init.mode: always` loads `schema.sql` on every startup:

- Tables use `CREATE TABLE IF NOT EXISTS` (safe for existing DBs)
- New columns are added via `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` at the bottom of `schema.sql`
- This avoids data loss and supports rolling upgrades without external migration tools
