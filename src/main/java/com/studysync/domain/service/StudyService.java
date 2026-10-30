package com.studysync.domain.service;

import com.studysync.domain.exception.ValidationException;
import com.studysync.domain.entity.DailyReflection;
import com.studysync.domain.entity.OffDay;
import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.StudySession;
import com.studysync.domain.entity.Task;
import com.studysync.domain.valueobject.TaskStatus;
import com.studysync.domain.service.StudySessionEnd;
import com.studysync.integration.drive.GoogleDriveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Simplified service layer for study-related operations.
 * Uses Active Record pattern - models handle their own persistence.
 */
@Service
@Transactional
public class StudyService {
    private static final Logger logger = LoggerFactory.getLogger(StudyService.class);
    
    private final GoogleDriveService googleDriveService;
    private final DateTimeService dateTimeService;

    /** Guards processAllDelayedGoals() so the full scan runs at most once per calendar day. */
    private LocalDate lastDelayProcessingDate;

    /** While true, reading goals does not sweep overdue attempts; see {@link #holdOverdueSweep()}. */
    private boolean overdueSweepHeld;

    @Autowired
    public StudyService(GoogleDriveService googleDriveService, DateTimeService dateTimeService) {
        this.googleDriveService = googleDriveService;
        this.dateTimeService = dateTimeService;
    }

    private void markDirtyAndSaveLocally(String operation) {
        flushLocally(operation, true);
    }

    /**
     * Persist to disk without flagging unsaved local changes.
     *
     * <p>For derived maintenance - work every machine recomputes for itself on
     * startup. Flagging it makes a machine that merely opened the app look like
     * it has edits waiting to be uploaded, which is enough to raise a sync
     * conflict against a Drive copy that is genuinely ahead.</p>
     *
     * <p>Suppressing the flag around the call site instead would not work: the
     * flush is deferred to {@code afterCommit}, and the surrounding transaction
     * commits after any such wrapper has already exited.</p>
     */
    private void saveLocallyWithoutDirtyFlag(String operation) {
        flushLocally(operation, false);
    }

    private void flushLocally(String operation, boolean markDirty) {
        Runnable flush = () -> {
            if (markDirty) {
                googleDriveService.markLocalDbDirty();
            }
            if (!googleDriveService.saveLocally()) {
                logger.warn("Local checkpoint failed after {}", operation);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    flush.run();
                }
            });
        } else {
            flush.run();
        }
    }

    @Transactional(readOnly = true)
    public List<StudyGoal> getStudyGoals() {
        return StudyGoal.findAll();
    }

    public List<StudyGoal> getStudyGoalsForDate(LocalDate date) {
        ensureOverdueAttemptsProcessed();
        return StudyGoal.findByDate(date);
    }

    /**
     * Get all study goals for a date including failed ones.
     * Used by calendar view which shows the complete history for each day.
     */
    public List<StudyGoal> getAllGoalsForDate(LocalDate date) {
        ensureOverdueAttemptsProcessed();
        return StudyGoal.findAllByDate(date);
    }

    /**
     * Get all study goals planned for a future date including failed ones.
     */
    @Transactional(readOnly = true)
    public List<StudyGoal> getAllGoalsForFutureDate(LocalDate date) {
        if (date == null) {
            throw ValidationException.requiredFieldMissing("date");
        }
        if (!date.isAfter(dateTimeService.getCurrentDate())) {
            throw ValidationException.invalidDateRange(
                date.toString(), "a future date");
        }
        return StudyGoal.findAllByDate(date);
    }



    public List<StudyGoal> getTodayGoals() {
        ensureOverdueAttemptsProcessed();
        return StudyGoal.findByDate(dateTimeService.getCurrentDate());
    }

    /**
     * Stops reads from sweeping overdue attempts until {@link #releaseOverdueSweep()}.
     *
     * <p>The UI holds the sweep while it starts, because building the panels reads
     * goals before the startup Drive check has decided whether maintenance may run:
     * with a newer Drive copy or a conflict, this database must not be changed.</p>
     */
    public synchronized void holdOverdueSweep() {
        overdueSweepHeld = true;
    }

    /** Lets reads sweep overdue attempts again, once startup maintenance is allowed. */
    public synchronized void releaseOverdueSweep() {
        overdueSweepHeld = false;
    }

    /**
     * Runs processAllDelayedGoals() at most once per calendar day.
     * Subsequent calls on the same day are no-ops, as are calls while the sweep
     * is held.
     *
     * <p>Public because scoring has to be able to guarantee the sweep has run
     * before it reads attempt outcomes: an overdue attempt still sitting at
     * PENDING counts as neither achieved nor missed, which silently inflates
     * the goal component of the score.</p>
     */
    public void ensureOverdueAttemptsProcessed() {
        LocalDate today = dateTimeService.getCurrentDate();
        synchronized (this) {
            if (!overdueSweepHeld && !today.equals(lastDelayProcessingDate)) {
                processAllDelayedGoals();
                lastDelayProcessingDate = today;
            }
        }
    }

    @Transactional(readOnly = true)
    public List<StudySession> getTodaySessions() {
        return StudySession.findByDate(dateTimeService.getCurrentDate());
    }

    public void addStudyGoal(String description, LocalDate date) {
        addStudyGoal(description, date, null);
    }
    
    public void addStudyGoal(String description, LocalDate date, String taskId) {
        addStudyGoal(description, date, taskId, null);
    }

    /**
     * @param doneCriteriaLines what "done" means, one criterion per line; blank or null for none
     */
    public void addStudyGoal(String description, LocalDate date, String taskId, String doneCriteriaLines) {
        if (description == null || description.trim().isEmpty()) {
            throw ValidationException.requiredFieldMissing("description");
        }
        StudyGoal goal = new StudyGoal(null, date, description, false, null, 0, false, 0, taskId);
        goal.setDoneCriteria(StudyGoal.criteriaFromLines(doneCriteriaLines, null));
        goal.save();
        reconcileAchievementWithChecklist(goal, goal.getCriteria());

        // When a goal is created for an OPEN task, automatically transition it
        // to IN_PROGRESS to reflect that active work has been planned.
        if (taskId != null && !taskId.isBlank()) {
            Task.findById(taskId).ifPresent(task -> {
                if (task.getStatus() == TaskStatus.OPEN) {
                    boolean updated = Task.updateStatus(taskId, TaskStatus.IN_PROGRESS);
                    if (updated) {
                        logger.info("Auto-transitioned task '{}' from OPEN to IN_PROGRESS after goal creation",
                                task.getTitle());
                    } else {
                        logger.warn("Failed to auto-transition task '{}' (id={}) to IN_PROGRESS",
                                task.getTitle(), taskId);
                    }
                }
            });
        }

        markDirtyAndSaveLocally("study goal creation");
    }

    /**
     * Returns active goals whose latest attempt was missed and which do not
     * already have a pending attempt. Task status is intentionally ignored here:
     * the parent goal lifecycle is the source of truth for retry eligibility.
     *
     * @return list of goals the user can choose to retry today
     */
    @Transactional(readOnly = true)
    public List<StudyGoal> getDelayedGoalsForReplanning(final String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return List.of();
        }
        return StudyGoal.findDelayedAndNotReplanned().stream()
                .filter(goal -> taskId.equals(goal.getTaskId()))
                .toList();
    }

    /**
     * Returns active unlinked goals whose latest attempt was missed and which
     * do not already have a pending attempt.
     *
     * @return list of unlinked goals the user can choose to retry today
     */
    @Transactional(readOnly = true)
    public List<StudyGoal> getUnlinkedDelayedGoalsForReplanning() {
        return StudyGoal.findDelayedAndNotReplanned().stream()
                .filter(goal -> goal.getTaskId() == null || goal.getTaskId().isBlank())
                .toList();
    }

    /**
     * Creates a new pending attempt for an active missed goal on today's date.
     * Achieved and abandoned goals are not retryable.
     *
     * @param goalId ID of the goal to retry
     * @return {@code true} when a new pending retry attempt was created
     */
    /**
     * What a new attempt does with a checklist that has no open line left. Starting it pending
     * with every line ticked would break the checklist rule, so retries and reopens of such a
     * goal must choose; for a goal with open lines the choice is ignored.
     */
    public enum CompletedChecklist {
        /** Clear every tick, so the new attempt has the whole checklist to do again. */
        START_OVER,
        /** The work is already done: plan the new attempt for today and achieve it. */
        MARK_DONE
    }

    public boolean replanGoalForToday(String goalId) {
        return replanGoalForToday(goalId, null);
    }

    public boolean replanGoalForToday(String goalId, CompletedChecklist choice) {
        Optional<StudyGoal> goalOpt = StudyGoal.findById(goalId);
        if (goalOpt.isEmpty()) {
            logger.warn("Requested retry for study goal '{}' but it did not exist", goalId);
            return false;
        }
        StudyGoal goal = goalOpt.get();
        if (goal.getStatus() == StudyGoal.GoalStatus.ABANDONED || goal.isAchieved()) {
            return false;
        }
        requireChecklistChoice(goal, choice);
        boolean created = StudyGoal.createReplanAttempt(goalId, dateTimeService.getCurrentDate());
        if (created) {
            applyChecklistChoice(goal, choice);
            markDirtyAndSaveLocally("study goal replan");
            logger.info("Created new attempt for goal '{}' on {}", goal.getDescription(), dateTimeService.getCurrentDate());
        }
        return created;
    }

    public boolean planGoalAttempt(String goalId, LocalDate plannedForDate) {
        return planGoalAttempt(goalId, plannedForDate, null);
    }

    /** {@link CompletedChecklist#MARK_DONE} plans the attempt for today, whatever date was picked. */
    public boolean planGoalAttempt(String goalId, LocalDate plannedForDate, CompletedChecklist choice) {
        if (goalId == null || goalId.isBlank()) {
            throw ValidationException.requiredFieldMissing("goalId");
        }
        if (plannedForDate == null) {
            throw ValidationException.requiredFieldMissing("plannedForDate");
        }
        if (plannedForDate.isBefore(dateTimeService.getCurrentDate())) {
            throw ValidationException.invalidDateRange(
                plannedForDate.toString(), "today or a future date");
        }
        Optional<StudyGoal> goalOpt = StudyGoal.findById(goalId);
        if (goalOpt.isEmpty()) {
            logger.warn("Requested retry for study goal '{}' but it did not exist", goalId);
            return false;
        }
        StudyGoal goal = goalOpt.get();
        if (goal.getStatus() == StudyGoal.GoalStatus.ABANDONED || goal.isAchieved()) {
            return false;
        }
        requireChecklistChoice(goal, choice);
        LocalDate attemptDate = attemptDate(goal, choice, plannedForDate);
        boolean created = StudyGoal.createReplanAttempt(goalId, attemptDate);
        if (created) {
            applyChecklistChoice(goal, choice);
            markDirtyAndSaveLocally("study goal retry planning");
            logger.info("Created new attempt for goal '{}' on {}", goal.getDescription(), attemptDate);
        }
        return created;
    }

    /**
     * @param doneCriteriaLines new checklist, one criterion per line; {@code null} keeps the
     *                          existing one, blank removes it. Ticks survive for unchanged lines.
     */
    public boolean updateStudyGoalDetails(String goalId, String description, LocalDate pendingPlannedForDate,
                                          String doneCriteriaLines) {
        if (goalId == null || goalId.isBlank()) {
            throw ValidationException.requiredFieldMissing("goalId");
        }
        if (description == null || description.trim().isEmpty()) {
            throw ValidationException.requiredFieldMissing("description");
        }
        Optional<StudyGoal> goalOpt = StudyGoal.findById(goalId);
        if (goalOpt.isEmpty()) {
            logger.warn("Requested update for study goal '{}' but it did not exist", goalId);
            return false;
        }
        StudyGoal goal = goalOpt.get();
        if (goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.PENDING && pendingPlannedForDate == null) {
            throw ValidationException.requiredFieldMissing("plannedForDate");
        }
        // A pending attempt moved into the past would be swept to MISSED on the next
        // refresh, which is not what "reschedule" means; planGoalAttempt refuses the same.
        if (pendingPlannedForDate != null && pendingPlannedForDate.isBefore(dateTimeService.getCurrentDate())) {
            throw ValidationException.invalidDateRange(pendingPlannedForDate.toString(), "today or a future date");
        }
        String doneCriteria = doneCriteriaLines == null
                ? goal.getDoneCriteria()
                : StudyGoal.criteriaFromLines(doneCriteriaLines, goal.getDoneCriteria(), !checklistTicksLocked(goal));
        boolean updated = StudyGoal.updateDetails(goalId, description, pendingPlannedForDate, doneCriteria);
        if (updated) {
            if (doneCriteriaLines != null) {
                reconcileAchievementWithChecklist(goal, StudyGoal.parseCriteria(doneCriteria));
            }
            markDirtyAndSaveLocally("study goal details update");
        }
        return updated;
    }

    /**
     * Keeps "achieved exactly when the checklist is complete" on creation and checklist edits. A pending goal
     * whose remaining open lines were removed is achieved; an achieved goal that gained an open line
     * reopens. An achieved attempt from an earlier day is history: reopening it would leave a pending
     * attempt in the past that the next refresh sweeps to missed, so its lines are ticked instead.
     * Missed and dropped goals are left alone.
     */
    private void reconcileAchievementWithChecklist(StudyGoal goal, List<StudyGoal.Criterion> criteria) {
        if (criteria.isEmpty() || checklistTicksLocked(goal)) {
            return;
        }
        boolean complete = criteria.stream().allMatch(StudyGoal.Criterion::done);
        if (complete && !goal.isAchieved()) {
            applyAchievement(goal.getId(), true, null);
        } else if (!complete && goal.isAchieved()) {
            if (goal.isAchievedBefore(dateTimeService.getCurrentDate())) {
                StudyGoal.setAllCriteriaDone(goal.getId(), true);
            } else {
                applyAchievement(goal.getId(), false, null);
            }
        }
    }

    private static boolean checklistTicksLocked(StudyGoal goal) {
        return goal.isDropped() || goal.isDroppedAttempt()
                || goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.MISSED;
    }

    /**
     * Idempotent upgrade maintenance, called only after the startup Drive check permits mutations.
     * Like the overdue sweep, this derived repair must checkpoint without setting the dirty flag,
     * including when its transaction commits after the UI's maintenance wrapper has returned.
     */
    public int normalizeAchievedGoalChecklists() {
        int changed = StudyGoal.normalizeAchievedChecklists();
        if (changed > 0) {
            saveLocallyWithoutDirtyFlag("achieved goal checklist normalization");
        }
        return changed;
    }

    /**
     * Ticks or unticks one line of a goal's checklist. Ticking the last open
     * line achieves the current attempt; unticking any line on an achieved
     * goal reopens it and keeps the other ticks. Ticking a line never reopens
     * anything. A missed or dropped goal, or an unknown goal or index, changes
     * nothing; a retry continues from the ticks the missed attempt left.
     * Unticking a line of an earlier day's achieved goal changes nothing either.
     */
    public void setGoalCriterionDone(String goalId, int index, boolean done) {
        Optional<StudyGoal> goal = StudyGoal.findById(goalId);
        if (goal.isEmpty() || checklistTicksLocked(goal.get())
                || (!done && goal.get().isAchievedBefore(dateTimeService.getCurrentDate()))) {
            return;
        }
        Optional<List<StudyGoal.Criterion>> changed = StudyGoal.setCriterionDone(goalId, index, done);
        if (changed.isEmpty()) {
            return;
        }
        boolean allDone = changed.get().stream().allMatch(StudyGoal.Criterion::done);
        if (allDone) {
            applyAchievement(goalId, true, null);
        } else if (!done) {
            applyAchievement(goalId, false, null); // no-op unless the goal is achieved
        }
        markDirtyAndSaveLocally("study goal criterion update");
    }

    /**
     * The goal checkbox. Its checklist mirrors it: achieving ticks every line
     * and reopening clears them, so a goal is achieved exactly when its
     * checklist is complete. A goal that cannot change (dropped, missed,
     * not achieved, achieved on an earlier day) keeps its ticks.
     */
    public void updateStudyGoalAchievement(String goalId, boolean achieved, String reasonIfNot) {
        if (applyAchievement(goalId, achieved, reasonIfNot)) {
            StudyGoal.setAllCriteriaDone(goalId, achieved);
            markDirtyAndSaveLocally("study goal achievement update");
        }
    }

    /**
     * The one place that flips a goal's current attempt between achieved and pending. It never
     * reopens an earlier day's achievement ({@link StudyGoal#isAchievedBefore}).
     */
    private boolean applyAchievement(String goalId, boolean achieved, String reasonIfNot) {
        if (achieved) {
            return StudyGoal.markCurrentAttemptAchieved(goalId, reasonIfNot);
        }
        LocalDate today = dateTimeService.getCurrentDate();
        return StudyGoal.findById(goalId).filter(goal -> !goal.isAchievedBefore(today)).isPresent()
                && StudyGoal.reopenAchievedGoal(goalId);
    }

    /** A missed attempt stays retryable; it does not drop the goal. */
    public boolean markGoalAttemptMissed(String goalId, String attemptId) {
        if (goalId == null || goalId.isBlank() || attemptId == null || attemptId.isBlank()) {
            throw ValidationException.requiredFieldMissing("goal and attempt");
        }
        boolean changed = StudyGoal.markAttemptMissed(goalId, attemptId, dateTimeService.getCurrentDate());
        if (changed) {
            markDirtyAndSaveLocally("study goal attempt missed");
        }
        return changed;
    }

    /** Drop a goal without adding a failure; recorded misses and session points survive. */
    public boolean dropGoal(String goalId, String reason) {
        if (goalId == null || goalId.isBlank()) {
            throw ValidationException.requiredFieldMissing("goalId");
        }
        boolean changed = StudyGoal.dropGoal(goalId, reason == null || reason.isBlank() ? null : reason.strip());
        if (changed) {
            markDirtyAndSaveLocally("study goal dropped");
        }
        return changed;
    }

    public boolean reopenDroppedGoal(String goalId, LocalDate plannedForDate) {
        return reopenDroppedGoal(goalId, plannedForDate, null);
    }

    /** {@link CompletedChecklist#MARK_DONE} reopens the goal for today, whatever date was picked. */
    public boolean reopenDroppedGoal(String goalId, LocalDate plannedForDate, CompletedChecklist choice) {
        if (goalId == null || goalId.isBlank()) {
            throw ValidationException.requiredFieldMissing("goalId");
        }
        if (plannedForDate == null) {
            throw ValidationException.requiredFieldMissing("plannedForDate");
        }
        if (plannedForDate.isBefore(dateTimeService.getCurrentDate())) {
            throw ValidationException.invalidDateRange(plannedForDate.toString(), "today or a future date");
        }
        Optional<StudyGoal> goal = StudyGoal.findById(goalId);
        if (goal.isEmpty()) {
            return false;
        }
        requireChecklistChoice(goal.get(), choice);
        boolean changed = StudyGoal.reopenDroppedGoal(goalId, attemptDate(goal.get(), choice, plannedForDate));
        if (changed) {
            applyChecklistChoice(goal.get(), choice);
            markDirtyAndSaveLocally("study goal reopened");
        }
        return changed;
    }

    private static void requireChecklistChoice(StudyGoal goal, CompletedChecklist choice) {
        if (choice == null && goal.isChecklistComplete()) {
            throw new ValidationException("Every checklist item is already ticked. Choose whether to start the "
                    + "checklist over or mark the goal done.", ValidationException.INVALID_INPUT);
        }
    }

    private LocalDate attemptDate(StudyGoal goal, CompletedChecklist choice, LocalDate plannedForDate) {
        return choice == CompletedChecklist.MARK_DONE && goal.isChecklistComplete()
                ? dateTimeService.getCurrentDate() : plannedForDate;
    }

    /** Runs in the caller's transaction, right after the new attempt was created. */
    private void applyChecklistChoice(StudyGoal goal, CompletedChecklist choice) {
        if (choice == null || !goal.isChecklistComplete()) {
            return;
        }
        if (choice == CompletedChecklist.START_OVER) {
            StudyGoal.setAllCriteriaDone(goal.getId(), false);
        } else {
            applyAchievement(goal.getId(), true, null);
        }
    }

    /**
     * Permanently deletes a study goal from the database.
     */
    public boolean deleteStudyGoal(String goalId) {
        if (goalId == null || goalId.isBlank()) {
            throw ValidationException.requiredFieldMissing("goalId");
        }
        boolean deleted = StudyGoal.deleteById(goalId);
        if (deleted) {
            markDirtyAndSaveLocally("study goal deletion");
            logger.info("Permanently deleted study goal '{}'", goalId);
        } else {
            logger.warn("Requested deletion for study goal '{}' but it did not exist", goalId);
        }
        return deleted;
    }

    public StudySession startStudySession() {
        return startStudySession(null, null);
    }

    /**
     * Starts a study session optionally linked to a goal and/or task (issue #17).
     *
     * @param goalId optional ID of the study goal this session works on
     * @param taskId optional ID of the task this session works on
     * @return the started session
     */
    public StudySession startStudySession(String goalId, String taskId) {
        StudySession session = new StudySession();
        session.setGoalId(goalId);
        session.setTaskId(taskId);
        session.startSession();
        session.save();
        markDirtyAndSaveLocally("study session start");
        logger.info("Started study session {} at {}", session.getId(), session.getStartTime());
        return session;
    }

    public void endStudySession(StudySession session, StudySessionEnd endDetails) {
        // Calculates points and timestamps; a failed save leaves the session running.
        session.endAndSave(endDetails.getFocusLevel(), endDetails.getNotes());
        markDirtyAndSaveLocally("study session completion");
        logger.info("Completed study session {} on {} for {} minutes (focus={})",
                session.getId(), session.getDate(), session.getDurationMinutes(), session.getFocusLevel());
    }

    public void addDailyReflection(DailyReflection reflection) {
        reflection.save();
        markDirtyAndSaveLocally("daily reflection save");
    }

    @Transactional(readOnly = true)
    public Optional<DailyReflection> getTodayReflection() {
        return DailyReflection.findByDate(dateTimeService.getCurrentDate());
    }

    @Transactional(readOnly = true)
    public Optional<DailyReflection> getDailyReflectionForDate(LocalDate date) {
        return DailyReflection.findByDate(date);
    }

    @Transactional(readOnly = true)
    public List<DailyReflection> getRecentDailyReflections(int days) {
        return DailyReflection.findRecent(days);
    }

    /** Every reflection ever written, most recent first. */
    @Transactional(readOnly = true)
    public List<DailyReflection> getAllDailyReflections() {
        return DailyReflection.findAll();
    }

    /**
     * Writes the reflection text for a date; blank text deletes the entry.
     * Every reflection editor goes through here so an existing row keeps its
     * other fields instead of being replaced by a blank entity.
     *
     * <p>The text is stored exactly as written — reflections are markdown, and
     * leading whitespace is significant there (indented code blocks, nested
     * list items), so blankness decides deletion but never rewrites the text.</p>
     */
    public void saveReflectionText(LocalDate date, String text) {
        if (text == null || text.isBlank()) {
            deleteDailyReflection(date);
            return;
        }
        DailyReflection reflection = DailyReflection.findByDate(date).orElseGet(DailyReflection::new);
        reflection.setDate(date);
        reflection.setReflectionText(text);
        addDailyReflection(reflection);
    }

    public void deleteDailyReflection(LocalDate date) {
        boolean deleted = DailyReflection.deleteByDate(date);
        if (deleted) {
            markDirtyAndSaveLocally("daily reflection deletion");
        }
    }


    @Transactional(readOnly = true)
    public int calculateDailyProgress() {
        List<StudyGoal> todayGoals = getAllGoalsForDate(dateTimeService.getCurrentDate()).stream()
                .filter(goal -> !goal.isDroppedAttempt()).toList();
        if (todayGoals.isEmpty()) {
            return 0;
        }

        long achievedGoals = todayGoals.stream().filter(StudyGoal::isAchieved).count();
        return (int) Math.round((double) achievedGoals / todayGoals.size() * 100);
    }

    public void deleteStudySession(String sessionId) {
        boolean deleted = StudySession.deleteById(sessionId);
        if (deleted) {
            markDirtyAndSaveLocally("study session deletion");
        }
    }

    @Transactional(readOnly = true)
    public List<StudySession> getSessionsForDate(LocalDate date) {
        return StudySession.findByDate(date);
    }

    @Transactional(readOnly = true)
    public Optional<StudySession> getActiveSession() {
        return StudySession.findActiveSession();
    }


    @Transactional(readOnly = true)
    public List<StudySession> getRecentStudySessions(int days) {
        return StudySession.findRecent(days);
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, List<StudySession>> getSessionsGroupedByDate(int days) {
        return StudySession.findRecent(days).stream()
                .collect(Collectors.groupingBy(StudySession::getDate, Collectors.toList()));
    }
    
    // ================================================================
    // OFF DAYS (HOLIDAYS) AND GLOBAL SCORING
    // ================================================================

    /**
     * Off days and their labels in a date range, for calendar rendering.
     *
     * @param start first day of the range, inclusive
     * @param end last day of the range, inclusive
     * @return map of off day to label
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, String> getOffDays(LocalDate start, LocalDate end) {
        return OffDay.findLabelsInRange(start, end);
    }

    /**
     * The off-day label of a single date.
     *
     * @param date the date to check
     * @return the label, or empty when the date is a normal day
     */
    @Transactional(readOnly = true)
    public Optional<String> getOffDayLabel(LocalDate date) {
        return OffDay.labelFor(date);
    }

    /**
     * Marks a day as off. Work already logged on it is kept but stops counting
     * towards global scores.
     *
     * @param date the day to mark
     * @param label optional description such as "Holiday" or "Sick day"
     */
    public void markOffDay(LocalDate date, String label) {
        if (date == null) {
            throw ValidationException.requiredFieldMissing("date");
        }
        OffDay.mark(date, label);
        markDirtyAndSaveLocally("off day marking");
    }

    /**
     * Turns an off day back into a normal, scored day.
     *
     * @param date the day to clear
     * @return {@code true} when the day was marked off before
     */
    public boolean clearOffDay(LocalDate date) {
        if (date == null) {
            throw ValidationException.requiredFieldMissing("date");
        }
        boolean cleared = OffDay.unmark(date);
        if (cleared) {
            markDirtyAndSaveLocally("off day removal");
        }
        return cleared;
    }

    /**
     * The goal attempts planned for a calendar day.
     *
     * <p>Scoring and display share this one definition so the count under a
     * day's score always matches the list rendered next to it.</p>
     *
     * <p>Not {@code readOnly}: for today and the past this first runs the
     * once-per-day sweep that marks overdue attempts as missed, which writes.
     * Future dates skip the sweep - an attempt planned for tomorrow cannot be
     * overdue.</p>
     *
     * @param date the day to list goals for
     * @return goal attempts planned for that day
     */
    @Transactional
    public List<StudyGoal> getGoalsForDate(LocalDate date) {
        if (!date.isAfter(dateTimeService.getCurrentDate())) {
            ensureOverdueAttemptsProcessed();
        }
        return StudyGoal.findAllByDate(date);
    }

    /**
     * {@link #getGoalsForDate} for a whole range in one query, so a calendar
     * month does not cost a query per day.
     *
     * @return goal attempts planned within {@code [from, to]}, by date
     */
    @Transactional
    public List<StudyGoal> getGoalsInRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw ValidationException.requiredFieldMissing(from == null ? "from" : "to");
        }
        if (!from.isAfter(dateTimeService.getCurrentDate())) {
            ensureOverdueAttemptsProcessed();
        }
        return StudyGoal.findAllInRange(from, to);
    }

    /**
     * Tasks with a goal already planned for today or later - everything else
     * active is still waiting to be planned.
     */
    @Transactional
    public Set<String> getTaskIdsWithPlannedGoals() {
        ensureOverdueAttemptsProcessed();
        return StudyGoal.findTaskIdsWithPendingAttemptsFrom(dateTimeService.getCurrentDate());
    }

    // ================================================================
    // DELAYED GOAL MANAGEMENT
    // ================================================================
    
    /**
     * Marks pending goal attempts planned before today as missed.
     * This should be called daily, such as during startup, so overdue attempts
     * become retryable without deleting the parent goal or applying legacy delay
     * penalties.
     *
     * @return summary of how many pending attempts were marked missed
     */
    public GoalDelayProcessingResult processAllDelayedGoals() {
        LocalDate today = dateTimeService.getCurrentDate();
        int missedAttempts = StudyGoal.markPendingAttemptsBefore(today);

        if (missedAttempts > 0) {
            saveLocallyWithoutDirtyFlag("delayed goal processing");
            logger.info("Marked {} overdue study goal attempt(s) as MISSED", missedAttempts);
        }

        return new GoalDelayProcessingResult(missedAttempts);
    }

    /**
     * Summary of overdue attempt processing.
     * @param missedAttempts number of pending attempts marked as missed
     */
    public record GoalDelayProcessingResult(int missedAttempts) {
        public boolean hasChanges() {
            return missedAttempts > 0;
        }
    }
    
    /**
     * Get delayed goals for today with their delay information.
     */
    @Transactional(readOnly = true)
    public List<StudyGoal> getTodayDelayedGoals() {
        return StudyGoal.findDelayedByDate(dateTimeService.getCurrentDate());
    }
    
    /**
     * Get all delayed goals across all dates.
     */
    @Transactional(readOnly = true)
    public List<StudyGoal> getAllDelayedGoals() {
        return StudyGoal.findDelayed();
    }
}
