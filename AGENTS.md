# StudySync - Agent Reference

## Overview

StudySync is a **JavaFX 21 + Spring Boot 3.2.0** desktop application for personal study and task management. It uses an **H2 file-based database** with optional **Google Drive sync**. There is no FXML — all UI is built programmatically in Java.

## Build & Run

| Command | Purpose |
|---|---|
| `./gradlew compileJava` | Compile only (fast verification) |
| `./gradlew run` | Launch the desktop app |
| `./gradlew test` | Run unit tests (JUnit 5 + Mockito) |
| `./gradlew integrationTest` | Run integration tests |
| `./gradlew check` | Run tests + Checkstyle |
| `./gradlew jacocoTestReport` | Generate code coverage report |

- **Java 21** (toolchain auto-downloads via foojay-resolver)
- **Gradle 8.12.1** (use the wrapper `./gradlew`)
- `application.yml` is gitignored — copy `application.yml.template` to `application.yml`
- Google Drive integration requires a `config/google/drive.properties` file with OAuth credentials (also gitignored)

## Architecture

```
src/main/java/com/studysync/
├── StudySyncApplication.java          # Spring Boot entry point → launches JavaFX
├── application/
│   └── StudySyncJavaFXApp.java        # JavaFX Application subclass (init/start/stop)
├── config/
│   ├── ActiveRecordConfig.java        # @PostConstruct: injects JdbcTemplate into entity classes
│   ├── DatabaseConfig.java            # JdbcTemplate + transaction manager beans
│   ├── DatabaseReloadService.java     # H2 SHUTDOWN/reconnect for live Drive sync
│   └── GoogleDriveConfiguration.java  # Spring beans for Drive integration classes
├── domain/
│   ├── entity/                        # Active Record entities (see below)
│   ├── exception/
│   │   └── ValidationException.java
│   ├── service/                       # Business logic services (see below)
│   └── valueobject/
│       ├── TaskStatus.java            # OPEN, IN_PROGRESS, POSTPONED, COMPLETED, DELAYED, CANCELLED
│       ├── TaskPriority.java          # Enum for task priority levels
│       ├── TaskCategory.java          # Enum for built-in task categories
│       └── ProjectStatus.java         # Enum for project status
├── integration/drive/                 # Google Drive OAuth + sync (see below)
└── presentation/ui/
    ├── StudySyncUI.java               # Main UI: header, TabPane, modal overlay, panel wiring
    └── components/
        ├── RefreshablePanel.java      # Interface: getView() + updateDisplay()
        ├── TaskStyleUtils.java        # Shared task status styling (border color, badge bg, emoji)
        │                              # + CSS-safe font helpers (fontBold/fontNormal/fontSemiBold/fontItalic/fontEmoji)
        │                              # + overdue/due-today/missed badge factories
        ├── CalendarViewPanel.java     # Monthly calendar with tasks and study goals
        ├── StudyPlannerPanel.java     # Daily study planner with sessions, goals, tasks
        ├── ReflectionDiaryPanel.java  # Daily reflection diary with sidebar navigation
        ├── ProjectManagementPanel.java # Project CRUD with categories and timed sessions
        ├── TaskManagementPanel.java   # Task CRUD with filters, sorting, bulk actions
        └── ProfileViewPanel.java      # Profile/analytics + Google Drive sync controls

src/main/resources/
├── schema.sql                         # H2 DDL (CREATE IF NOT EXISTS + migrations)
├── styles.css                         # JavaFX CSS (global text overrides + reusable classes)
├── application.yml.template           # Config template (gitignored application.yml)
├── logback-spring.xml
├── icon.png
└── fonts/
    ├── Roboto-Regular.ttf
    ├── Roboto-Bold.ttf
    └── NotoEmoji-Regular.ttf
```

## Startup Sequence

1. `StudySyncApplication.main()` runs `GoogleDriveBootstrap.initialize()` (pre-Spring, downloads DB from Drive if configured)
2. Spring Boot context starts (`@SpringBootApplication`)
3. `ActiveRecordConfig.@PostConstruct` injects `JdbcTemplate` into all entity classes via static setters
4. `Application.launch(StudySyncJavaFXApp.class)` starts the JavaFX lifecycle
5. `StudySyncJavaFXApp.init()` retrieves the Spring context and loads custom fonts
6. `StudySyncJavaFXApp.start()` gets `StudySyncUI` bean from Spring, calls `studySyncUI.start(primaryStage)`
7. `StudySyncUI.start()` forces Modena stylesheet, creates TabPane with all panels, processes delayed goals, checks Drive sync

## Active Record Pattern

Entities have a **static `JdbcTemplate`** field set at startup by `ActiveRecordConfig`. All database operations are static methods on the entity classes (e.g., `Task.findAll()`, `Task.findById(id)`, `task.save()`, `task.delete()`).

### Entities

| Entity | Table | Key Static Methods |
|---|---|---|
| `Task` | `tasks` | `findAll`, `findById`, `findByStatus`, `findByCategory`, `findDueBy`, `findOverdue`, `findByPriority`, `search`, `findHighPriority`, `findRecurring`, `findActiveRecurring`, `countByStatus`, `existsById`, `updateStatus`, `deleteById`, `deleteByIds`, `save`, `delete` |
| `StudyGoal` | `study_goals` | `findAll`, `findById`, `findByDate`, `findByDateIncludingDelayed`, `findAchieved`, `findUnachievedByDate`, `findDelayed`, `findDelayedByDate`, `findByTaskIdForDate`, `hasAchievedGoalForTask`, `findUnlinkedForDate`, `countByAchievement`, `deleteById`, `save`, `delete` |
| `StudySession` | `study_sessions` | `findAll`, `findById`, `findByDate`, `findActiveSession`, `save`, `delete` |
| `Project` | `projects` | `findAll`, `findById`, `findByStatus`, `save`, `delete` |
| `ProjectSession` | `project_sessions` | `findAll`, `findById`, `findByProjectId`, `findByDate`, `save`, `delete` |
| `DailyReflection` | `daily_reflections` | `findAll`, `findById`, `findByDate`, `findByDateRange`, `save`, `delete` |
| `Category` | `task_categories` | `findAll`, `findById`, `findByName`, `save`, `delete` |
| `TaskReminder` | (no table — in-memory) | Used by `ReminderService` for scheduling |

### Services

| Service | Responsibility |
|---|---|
| `TaskService` | Task CRUD, filtering, status transitions, bulk operations, recurring task date matching, missed occurrence detection, task statistics, async queries |
| `StudyService` | Study sessions (start/end), study goals, daily reflections, delayed goal processing |
| `ProjectService` | Project CRUD, project sessions (start/end), progress tracking |
| `CategoryService` | Category CRUD for tasks and projects |
| `ReminderService` | In-memory task deadline reminders |
| `DateTimeService` | Date/time utilities, current date provider |

Services call `googleDriveService.markLocalDbDirty()` after mutations (via `TransactionSynchronization.afterCommit`) to track unsaved changes for Drive sync.

### Records (DTOs)

| Record | Used By | Purpose |
|---|---|---|
| `TaskUpdate` | `TaskService` | DTO for partial task updates |
| `StudySessionEnd` | `StudyService` | DTO for ending a study session |
| `ProjectSessionEnd` | `ProjectService` | DTO for ending a project session |
| `MissedOccurrence` | `TaskService` | DTO: a missed past occurrence of a recurring task (task + missedDate) |

## Database

H2 file-based database at `./data/studysync.mv.db`. Schema is in `schema.sql` and uses `CREATE TABLE IF NOT EXISTS` + `ALTER TABLE ADD COLUMN IF NOT EXISTS` for forward-compatible migrations.

### Tables

- **tasks** — id, title, description, category, priority, deadline, status, points, recurring_pattern, start_date, timestamps
- **projects** — id, title, description, category, status, priority, dates, progress, hours, notes, timestamps
- **study_sessions** — id, date, times, duration, completion flags, focus/confidence levels, session notes, active tracking fields, timestamps
- **project_sessions** — id, project_id (FK → projects), date, times, duration, objectives, progress, notes, timestamps
- **study_goals** — id, date, description, achieved, delay tracking fields, task_id (FK → tasks), timestamps
- **daily_reflections** — id, date (UNIQUE), focus level, notes, reflection text, reward flag, timestamps
- **task_categories** — id, name (UNIQUE), description, timestamp; seeded with Work, Personal, Study, Health

## UI Architecture

### Modal System

`StudySyncUI` has a `StackPane overlayLayer` for modals. It exposes `showModal(Node)` and `closeModal()` as `Consumer<Node>` and `Runnable`, passed to panels that need modal dialogs (StudyPlannerPanel, TaskManagementPanel, ProjectManagementPanel).

### Panel Interface

All panels implement `RefreshablePanel`:
- `getView()` — returns the root `Node` (usually a `ScrollPane` wrapping a `VBox`)
- `updateDisplay()` — refreshes data from the database and rebuilds the UI

Panels are instantiated in `StudySyncUI`'s constructor and stored in a `LinkedHashMap<Tab, RefreshablePanel>`. The active panel's `updateDisplay()` is called on tab selection.

### Tab Layout

Default tab order: Calendar View, Study Planner, Reflection Diary, Projects, Tasks. Tabs can be reordered with Ctrl+Left/Right. Profile opens in a separate window.

## CSS Architecture

`styles.css` has two sections:

1. **Lines 1-394: Global text color overrides** — Forces dark text (`#2c3e50`) on all JavaFX controls (`.label`, `.text`, `.text-field`, `.button`, `.combo-box`, `.table-view` cells, etc.) to prevent invisible text when the OS is in dark mode. JavaFX 21's Modena theme auto-detects OS dark mode and sets white text, which clashes with our light backgrounds. We also force `Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA)` in `StudySyncUI.start()`. Includes checkbox selected-state rules (`.check-box:selected .box` / `.check-box:selected .mark`) to keep the tick mark visible.

2. **Lines 395-520: Reusable CSS classes** — Application-specific styles extracted from inline `setStyle()` calls:

| Category | Classes |
|---|---|
| Backgrounds | `.panel-bg`, `.panel-bg-alt`, `.panel-bg-warm` |
| Cards | `.section-card`, `.section-card-light`, `.section-card-flat` |
| Buttons | `.btn-primary`, `.btn-success`, `.btn-danger`, `.btn-cancel`, `.btn-purple`, `.btn-orange`, `.btn-google`, `.btn-gray`, `.btn-success-alt`, `.btn-orange-download`, `.btn-small` |
| Modals | `.modal-content`, `.modal-content-light` |
| Utility | `.transparent-bg` |

### Styling Guidelines

- **Use CSS classes** for static, reusable styles. Apply via `node.getStyleClass().add("class-name")`.
- **Keep `setStyle()` for dynamic styles** — anything that varies by data (task status colors, date-dependent cell colors, progress bar widths).
- **`setTextFill(Color.web("#2c3e50"))`** is the global default — do NOT set it explicitly. Only use `setTextFill()` for semantic colors (muted gray `#7f8c8d`, error red, status-specific colors).
- **`setTextFill(Color.WHITE)` on buttons** is handled by button CSS classes — do NOT set it explicitly when using `.btn-*` classes.
- **Task status styling** is centralized in `TaskStyleUtils` — use its static methods instead of inline switch expressions.
- The `.button` base CSS class already includes `-fx-font-weight: bold; -fx-cursor: hand;`.

## Window Dimensions

- Default size: 1200 x 800 (set via `setWidth`/`setHeight` on the primary stage)
- Minimum size: 900 x 600 (set via `setMinWidth`/`setMinHeight`)
- Stage is centered on screen at startup

## Recurring Tasks

Single-entity virtual-recurrence model — one DB row appears on multiple calendar dates. No task spawning.

- **Pattern format**: `"intervalWeeks:daysOfWeek"` — e.g., `"2:1,4"` = every 2 weeks on Mon (`1`) and Thu (`4`)
- **`startDate`** (`DATE`): First date the task can appear + recurrence interval anchor. Falls back to `createdAt` if null (backward compatibility). Set via `getRecurrenceAnchor()`.
- **`deadline`** on recurring tasks = end-of-recurrence (task stops appearing after this date). NOT a due date.
- **Completing** a recurring task is permanent — stops all future recurrences.
- **Missed occurrence detection**: A past scheduled date is "missed" if no achieved `StudyGoal` is linked to the task (`task_id`) for that date. `TaskService.getMissedRecurringOccurrences()` walks back up to 28 days. Today's occurrence is never considered missed.
- **Carry-forward** of missed occurrences is only shown in StudyPlannerPanel (not in CalendarViewPanel day cells).

## Google Drive Integration

Optional feature for syncing the H2 database file to Google Drive.

### Components

| Class | Role |
|---|---|
| `GoogleDriveBootstrap` | Pre-Spring: loads settings, downloads DB from Drive if remote is newer |
| `GoogleDriveSettingsLoader` | Loads OAuth config from `config/google/drive.properties` |
| `GoogleDriveSettings` | POJO holding client ID, client secret, DB path, token dir |
| `GoogleDriveContextHolder` | ThreadLocal holder for settings between bootstrap and Spring init |
| `GoogleDriveConfiguration` | Spring `@Configuration` — creates beans for credential manager, gateway |
| `GoogleCredentialManager` | OAuth2 credential storage/retrieval (file-based token store) |
| `GoogleDriveGateway` | Low-level Drive API calls (upload, download, list, get metadata) |
| `GoogleDriveService` | High-level service: sign-in/out, sync status check, upload/download, dirty tracking |

### Sync Flow

1. On startup, `GoogleDriveBootstrap` checks if remote DB is newer and downloads it before Spring starts
2. During runtime, services call `googleDriveService.markLocalDbDirty()` after DB mutations
3. User can manually sync from ProfileViewPanel (upload/download buttons)
4. On download: `DatabaseReloadService` shuts down H2, file is replaced, H2 reconnects, schema migrations re-run, all panels refresh
5. On app close: if signed in, user is prompted to upload to Drive

## Testing

- Framework: JUnit 5 + Mockito + spring-boot-starter-test
- Test config: `src/test/resources/application-test.properties`
- Coverage: JaCoCo with HTML/XML reports (excludes config classes and entry points)
- Code style: Checkstyle 10.12.1 (config at `config/checkstyle/checkstyle.xml`)
- No test source files currently exist (only the test resources directory)

## Key Patterns

1. **Active Record** — entities handle their own persistence via static JdbcTemplate methods
2. **Spring-JavaFX bridge** — Spring context starts first, JavaFX `Application.launch()` second; `StudySyncUI` is a Spring `@Component`
3. **Modal overlay** — `StackPane` overlay managed by `StudySyncUI`, passed to panels as `Consumer<Node>` / `Runnable`
4. **Dirty tracking** — services mark DB as dirty after commits; Drive sync uses this to know when upload is needed
5. **Live DB reload** — H2 SHUTDOWN → file replace → reconnect → schema migration → panel refresh (no app restart)
6. **Tab-based navigation** — `TabPane` with lazy `updateDisplay()` on selection change
