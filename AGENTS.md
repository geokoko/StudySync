# StudySync

JavaFX 25 + Spring Boot 3.5 desktop app on an H2 file database, with optional
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

Use the Gradle 9.8.2 wrapper on Java 25; no `JAVA_HOME` is needed when the
default `java` is 25. Compilation uses a JDK 25 toolchain (found locally or
downloaded by the foojay resolver). H2 is pinned in `build.gradle` so database
files keep their format; do not let a Spring Boot upgrade move it.

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
- Keep the legacy `projects` / `project_sessions` tables intact. The
  `projects-to-tasks` marker prevents re-import; `tasks.legacy_project_id`
  additionally prevents duplicates if startup stops before writing the marker.
  Never re-import edits made by an older build over the new task data.

## Google Drive sync

Syncs the whole H2 file. OAuth client credentials live in
`config/google/drive.properties` (gitignored; template beside it).

- Service mutations mark the database dirty after commit
  (`TransactionSynchronization.afterCommit`: `markDirtyAndSaveLocally` in
  `TaskService`, `flushLocally` in `StudyService`). The flag
  is how sync knows there are local changes to upload.
- A download is never applied to the open database:
  `GoogleDriveService.stageDownloadFromDrive` stages it beside the DB file, and
  `bootstrap/PendingDownloadApplier` swaps it in at the next launch, before
  Spring opens H2. There is no live reload, and nothing is downloaded
  automatically at startup.
- Startup maintenance (marking delayed tasks, sweeping overdue goal attempts,
  normalizing achieved goal checklists)
  is derived data every machine recomputes, so it must not set the dirty flag:
  otherwise merely opening the app looks like unsaved edits and raises a false
  sync conflict. `StudySyncUI.runStartupMaintenance` wraps it in
  `GoogleDriveService.runStartupMaintenanceWithoutDirtyTracking`, and the goal
  sweep and checklist repair save through `StudyService.saveLocallyWithoutDirtyFlag`
  (its javadoc says why the wrapper alone is not enough). It runs only once the startup
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
- Postponement is independent of the deadline. Only an explicit `tasks.resume_on`
  date can automatically resume `POSTPONED` work; NULL means resume manually.
  Never infer it from legacy deadlines. `TaskService.setTaskResumeDate` saves or
  clears it; leaving `POSTPONED` clears it. On return, the deadline determines
  whether the task is `OPEN` or `DELAYED`.
- What a calendar day lists is decided by `CalendarAgenda` alone, never by
  `TaskService.getTasksForDate`: that answers the planner's question, so it
  repeats a late task on every day and judges past days by today's status. The
  rules are in `CalendarAgenda`'s class doc. A day cell folds those lines into
  one per task with a goal count (`CalendarAgenda.byTask`); the day panel still
  lists every goal.
- Task-list urgency and family ordering are decided by `TaskListAgenda`.
  Its class doc defines family/filter placement and the seven-day window;
  recurring tasks without a deadline stay in No deadline.
- The planner's "Needs a plan" list is decided by `PlannerAgenda` alone. Active
  tasks stay available for additional goals after planning; a subtask's goal also
  marks its parent as planned. Missed goals remain individually retryable only
  while their task is active. The calendar's "To plan" list excludes planned work,
  applying the same parent rule through `CalendarAgenda.withParents`.

### Subtasks and task notes

- Hierarchy/status changes go through `TaskService`, including form saves:
  a task has at most one parent level. Recurrence belongs to each task alone;
  never inherit or require matching schedules between parents and children.
  Completing the last unfinished non-cancelled child auto-completes only a
  non-recurring parent, in the same transaction with the same completion date.
  Handling an occurrence through a goal is not permanent task completion.
  Reopening or adding unfinished work reopens a completed parent.
- Manual parent completion completes all unfinished children on the same date
  in one transaction; cancelled children and existing completion dates survive.
  The UI confirms the unfinished count and how many are recurring and will stop
  repeating, using `TaskService.previewTaskCompletion`.
- Parent deletion confirms the current child count (also for bulk deletion),
  checks it again in the transaction, and deletes children before parents.
  The parent FK deliberately does not cascade. Each deletion uses normal task
  semantics: goals/sessions are unlinked; links/reschedules are deleted.
- Only leaf tasks earn timeliness points, even if all of a parent's children
  are cancelled. Progress excludes cancelled children from both numerator and
  denominator; an empty denominator displays 0% and never auto-completes.
- Keep each task's stored estimate independent. A parent's non-cancelled child
  estimate sum is derived, never stored over its own planned budget. No child
  estimates means no sum; an explicit zero is still an estimate.
- Notes preserve Markdown whitespace. Task links are ordered and validated by
  `TaskService` on save and before opening via the injected `HostServices`.

### Definition of done

- A goal's checklist is `study_goals.done_criteria`, kept on the parent goal
  (not the attempt) so a retry continues from earlier ticks. Only
  `StudyGoal.parseCriteria` / `serializeCriteria` know its `[x] ` / `[ ] `
  line format.
- The checkbox and the checklist mirror each other, so a goal with a checklist
  is achieved exactly when every line is ticked. Ticking the last criterion
  achieves the current attempt and unticking one reopens it, keeping the other
  ticks (`StudyService.setGoalCriterionDone`). Ticking the checkbox ticks every
  line and unticking it clears them (`updateStudyGoalAchievement`); a goal it
  cannot change keeps its ticks. A checklist edit re-applies the rule, except
  that an earlier day's achieved goal stays achieved with its lines ticked
  (reopening would leave a past pending attempt that is swept to missed). A
  missed or dropped attempt can be neither achieved nor ticked; a retry
  continues from its ticks. When those ticks are already complete, a retry or
  reopen must say what to do (`StudyService.CompletedChecklist`): start the
  checklist over, or mark the goal done with an attempt dated today. The
  planner asks in the retry row; date dialogs use `GoalActions.ChecklistChoice`.
  A goal without a checklist uses the checkbox alone. There is no partial credit.
- Goal forms reuse `GoalEditFields` and `TaskStyleUtils.criteriaArea` /
  `CRITERIA_LABEL` / `criteriaChecklist` / `criteriaProgress`. A task's
  `done_criteria` is plain text, not a checklist.
- Goal outcome actions are shared by `GoalActions`. Marking an attempt missed
  targets its exact attempt ID and leaves the goal retryable. Dropping a goal
  keeps the persisted parent status `ABANDONED`, records the optional
  `study_goals.drop_reason`, and changes only a pending attempt to `DROPPED`.
  Earlier misses remain `MISSED`; dropped attempts earn no goal credit and are
  excluded from completion-rate denominators. Reopening explicitly creates a
  new dated attempt, preserving history, checklist ticks and session links.

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
