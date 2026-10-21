# StudySync

JavaFX 21 + Spring Boot 3.2 desktop app on an H2 file database, with optional
Google Drive sync. There is no FXML: all UI is built in Java code.

## Rules

- Never commit, push, force-push, or create/delete tags without explicit user
  confirmation first. Pushing a `v*` tag publishes a GitHub Release
  (`.github/workflows/release.yml`).
- Before running a git commit, push or tag command, or any other irreversible
  or external command, show the exact command and wait for approval.
- Before changing code for a feature, enhancement or fix, switch to a new
  descriptively named branch (e.g. `feature/calendar-integration`,
  `fix/task-deletion-bug`) and keep working on it until the feature is
  complete. When a substantial piece of work is done, stop and ask for
  confirmation and code review before committing and pushing.

## Build

Use the Gradle 8.12.1 wrapper, started with JDK 21 via `JAVA_HOME`. The Java 21
toolchain only controls compilation, not the JVM that runs Gradle, and Gradle
8.12.1 cannot run on Java 24 or later (Java 25 fails with `Unsupported class
file major version 69`). See README, "Build from source".

| Command | Purpose |
|---|---|
| `./gradlew compileJava` | Fast compile check |
| `./gradlew test` | All tests; also writes the JaCoCo report |
| `./gradlew check` | Tests + Checkstyle (`config/checkstyle/checkstyle.xml`) |
| `./gradlew run` | Launch the desktop app |

- Tests start the JavaFX platform (`SessionVisibilityTest`), so they need a
  display; headless, run `xvfb-run -a ./gradlew test` as CI does.
- Checkstyle only warns (`severity` is `warning`, and CI runs it with
  `continue-on-error`), and the code base carries many existing warnings. Its
  output is not a gate; do not mass-fix it.

`src/main/resources/application.yml` is gitignored: create it from
`application.yml.template` in the same directory.

## Persistence

- Active Record: each entity in `domain/entity` holds a static `JdbcTemplate`
  that `config/ActiveRecordConfig` sets at startup, and does its own database
  work: queries and bulk updates are static methods (`Task.findAll()`,
  `Task.updateStatus(...)`), saving or deleting one row is an instance method
  (`task.save()`).
- The database is the H2 file `./data/studysync.mv.db`.
- `schema.sql` runs on every startup (`spring.sql.init.mode: always`), so every
  statement must be safe to re-run: `CREATE TABLE IF NOT EXISTS`,
  `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`, or recomputing derived data. A
  one-shot migration that moves user data must be guarded by a
  `schema_migrations` row, as `split-recurrence-end-from-deadline` is.
- Tests that need tables (`*PersistenceTest`, `ScoringServiceTest`) inline their
  own DDL instead of loading `schema.sql`; mirror schema changes there.
- `projects.total_minutes_worked` is authoritative; `actual_hours` is a
  rounded-down convenience copy.

## Google Drive sync

Syncs the whole H2 file. OAuth client credentials live in
`config/google/drive.properties` (gitignored; template beside it).

- Service mutations mark the database dirty after commit
  (`TransactionSynchronization.afterCommit`: `markDirtyAndSaveLocally` in
  `TaskService` / `ProjectService`, `flushLocally` in `StudyService`). The flag
  is how sync knows there are local changes to upload.
- A download is never applied to the open database:
  `GoogleDriveService.stageDownloadFromDrive` stages it beside the DB file, and
  `bootstrap/PendingDownloadApplier` swaps it in at the next launch, before
  Spring opens H2. There is no live reload, and nothing is downloaded
  automatically at startup.
- Startup maintenance (marking delayed tasks, sweeping overdue goal attempts)
  is derived data every machine recomputes, so it must not set the dirty flag:
  otherwise merely opening the app looks like unsaved edits and raises a false
  sync conflict. `StudySyncUI.runStartupMaintenance` wraps it in
  `GoogleDriveService.runStartupMaintenanceWithoutDirtyTracking`, and the goal
  sweep saves through `StudyService.saveLocallyWithoutDirtyFlag` (its javadoc
  says why the wrapper alone is not enough). It runs only once the startup
  Drive check reports up to date, local newer, unknown or disabled; never with
  a newer Drive copy, a conflict, or a staged download pending. Reading goals
  also sweeps overdue attempts, so `StudySyncUI` holds that sweep
  (`StudyService.holdOverdueSweep`) before building the panels and releases it
  in `runStartupMaintenance`. If startup defers maintenance, the header re-check
  (also run when Profile closes) resumes it once sync is up to date, local newer
  or disabled. An unknown re-check does not resolve an earlier conflict. Pending
  or failed staged downloads still block maintenance, which runs only once.
- User-facing flow (manual upload/download in the Profile window, upload offer
  on close): README, "Google Drive Sync Setup".

## UI styling

- `styles.css` is a scene stylesheet on the main and Profile windows, including
  their modal overlays and owned dialogs (JavaFX binds the owner's
  stylesheets). Its first part forces dark text (`#2c3e50`) on every control,
  so text stays readable on our light backgrounds under a dark desktop theme;
  `StudySyncUI.start()` also forces `STYLESHEET_MODENA`. Reusable classes
  (`.btn-*`, `.section-card*`, `.panel-bg*`, `.modal-content*`, `.cal-*`, ...)
  follow under "APPLICATION CSS CLASSES".
- Static, reusable styles go in a CSS class (`getStyleClass().add(...)`);
  `setStyle()` is for styles that vary with data (status colors, date-dependent
  cell colors, progress widths).
- Never set the default text color, or white text on `.btn-*` buttons; the
  stylesheet does both.
- `setTextFill()` has no effect on a Label in those windows: the stylesheet's
  `.label` rule outranks it. Semantic colors (muted `#7f8c8d`, error red,
  status colors) go through `TaskStyleUtils.textColor(label, hex)`, which sets
  an inline `-fx-text-fill`. An icon label set as the graphic inherits that
  colour (`.label .label`, repeated for dialogs), so only the outer label needs
  it. Inside a list cell use the `cell-muted` class instead: an inline colour
  would also beat the rule that turns a selected row's text white.
- Colour a Labeled control through `-fx-text-fill`, never `-fx-fill` on its
  inner `.text` node: a fill set there overwrites the control's text fill, so a
  broad `.text` rule silently disables every class-based text colour.
- Fonts go through the `TaskStyleUtils` font helpers (`fontBold`, `fontEmoji`,
  `iconLabel`, ...), which use inline styles so CSS re-application cannot reset
  them. Task status colors, emoji and badges also come from `TaskStyleUtils`,
  not inline switch expressions.

## Domain rules

- `ScoringService` is the only place that decides what scores and how much;
  the calendar's per-day figures and the profile's 30-day figures are both its
  `ScoreBreakdown`s. The rules are in its class doc and constants.
- Session `points_earned` is derived: `schema.sql` recomputes it on every
  startup in integer SQL that must match `StudySession.calculatePoints()`
  exactly, guarded by `ScoringServiceTest.sessionPointsMatchTheSqlMigrationExactly`
  (which runs the statement from `schema.sql`).
- Off days (`off_days`) are excluded only from aggregates: window scores drop
  them from the denominators and the streak skips them rather than breaking.
  Per-day figures and the profile charts still show what happened.
- A reminder is only `tasks.remind_days_before`; the reminder date is derived
  from the deadline and never stored.
- What a calendar day lists is decided by `CalendarAgenda` alone, never by
  `TaskService.getTasksForDate`: that answers the planner's question, so it
  repeats a late task on every day and judges past days by today's status. The
  rules are in `CalendarAgenda`'s class doc.

### Definition of done

- A goal's checklist is `study_goals.done_criteria`, kept on the parent goal
  (not the attempt) so a retry continues from earlier ticks. Only
  `StudyGoal.parseCriteria` / `serializeCriteria` know its `[x] ` / `[ ] `
  line format.
- Ticking the last criterion achieves the current attempt through the same path
  as the checkbox (`StudyService.setGoalCriterionDone`); unticking one reopens
  an achieved goal. The checkbox also works alone, so a goal can be achieved
  with criteria open or with no checklist. There is no partial credit.
- Goal forms reuse `GoalEditFields` and `TaskStyleUtils.criteriaArea` /
  `CRITERIA_LABEL` / `criteriaChecklist` / `criteriaProgress`. A task's
  `done_criteria` is plain text, not a checklist.

### Recurring tasks

- One row appears on many dates; occurrences are virtual, never spawned rows.
- `recurring_pattern` is `"intervalWeeks:daysOfWeek"`, e.g. `"2:1,4"` = every 2
  weeks on Mon and Thu. Intervals are anchored at `start_date`, falling back to
  `created_at` for old rows (`Task.getRecurrenceAnchor()`).
- `recurrence_end_date` (NULL = forever) ends the schedule. `deadline` means
  the same as on one-off tasks, when the task is due, and does not decide where
  occurrences fall. Before 0.1.6 `deadline` held the recurrence end; a
  `schema_migrations`-guarded migration split them.
- Past its deadline and unresolved, a recurring task goes `DELAYED` and
  surfaces every day until resolved, even after the end of recurrence.
  `TaskService.taskSurfacesOn()` answers "does it show here";
  `isRecurringOccurrence()` answers "does the schedule land here". Counting
  missed occurrences must follow the schedule, never `taskSurfacesOn()`.
- Completing a recurring task is permanent and ends all future occurrences.
- Missed-occurrence carry-forward is shown only in `StudyPlannerPanel`:
  `TaskService.getMissedRecurringOccurrences()` checks yesterday only, and an
  occurrence is handled when `StudyGoal.hasHandledGoalForTaskOccurrence` finds
  an achieved attempt for it. Today's occurrence is never missed.
