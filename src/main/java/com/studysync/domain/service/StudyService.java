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

    @Autowired
    public StudyService(GoogleDriveService googleDriveService, DateTimeService dateTimeService) {
        this.googleDriveService = googleDriveService;
        this.dateTimeService = dateTimeService;
    }

    /**
     * Clears cached processing guards so that delayed-goal processing
     * re-runs against the newly loaded database.
     * Must be called after a live database reload (e.g. Google Drive download).
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void resetAfterReload() {
        synchronized (this) {
            lastDelayProcessingDate = null;
        }
        logger.info("StudyService caches reset after DB reload");
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
     * Runs processAllDelayedGoals() at most once per calendar day.
     * Subsequent calls on the same day are no-ops.
     *
     * <p>Public because scoring has to be able to guarantee the sweep has run
     * before it reads attempt outcomes: an overdue attempt still sitting at
     * PENDING counts as neither achieved nor missed, which silently inflates
     * the goal component of the score.</p>
     */
    public void ensureOverdueAttemptsProcessed() {
        LocalDate today = dateTimeService.getCurrentDate();
        synchronized (this) {
            if (!today.equals(lastDelayProcessingDate)) {
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
        if (description == null || description.trim().isEmpty()) {
            throw ValidationException.requiredFieldMissing("description");
        }
        StudyGoal goal = new StudyGoal(null, date, description, false, null, 0, false, 0, taskId);
        goal.save();

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
    public boolean replanGoalForToday(String goalId) {
        Optional<StudyGoal> goalOpt = StudyGoal.findById(goalId);
        if (goalOpt.isEmpty()) {
            logger.warn("Requested retry for study goal '{}' but it did not exist", goalId);
            return false;
        }
        StudyGoal goal = goalOpt.get();
        if (goal.getStatus() == StudyGoal.GoalStatus.ABANDONED || goal.isAchieved()) {
            return false;
        }
        boolean created = StudyGoal.createReplanAttempt(goalId, dateTimeService.getCurrentDate());
        if (created) {
            markDirtyAndSaveLocally("study goal replan");
            logger.info("Created new attempt for goal '{}' on {}", goal.getDescription(), dateTimeService.getCurrentDate());
        }
        return created;
    }

    public boolean planGoalAttempt(String goalId, LocalDate plannedForDate) {
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
        boolean created = StudyGoal.createReplanAttempt(goalId, plannedForDate);
        if (created) {
            markDirtyAndSaveLocally("study goal retry planning");
            logger.info("Created new attempt for goal '{}' on {}", goal.getDescription(), plannedForDate);
        }
        return created;
    }

    public boolean updateStudyGoalDetails(String goalId, String description, LocalDate pendingPlannedForDate) {
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
        boolean updated = StudyGoal.updateDetails(goalId, description, pendingPlannedForDate);
        if (updated) {
            markDirtyAndSaveLocally("study goal details update");
        }
        return updated;
    }

    public void updateStudyGoalAchievement(String goalId, boolean achieved, String reasonIfNot) {
        boolean updated = achieved
                ? StudyGoal.markCurrentAttemptAchieved(goalId, reasonIfNot)
                : StudyGoal.reopenAchievedGoal(goalId);
        if (updated) {
            markDirtyAndSaveLocally("study goal achievement update");
        }
    }

    /**
     * Abandons a study goal while preserving its attempt history for display.
     * Abandoned goals are excluded from future retry planning.
     */
    public boolean markGoalAsFailed(String goalId) {
        if (goalId == null || goalId.isBlank()) {
            throw ValidationException.requiredFieldMissing("goalId");
        }
        boolean abandoned = StudyGoal.abandonGoal(goalId);
        if (abandoned) {
            markDirtyAndSaveLocally("study goal failure update");
            logger.info("Abandoned study goal '{}'", goalId);
            return true;
        } else {
            logger.warn("Requested mark-as-failed for study goal '{}' but it did not exist or was achieved", goalId);
            return false;
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
        // Apply user input from dialog to the session
        session.setFocusLevel(endDetails.getFocusLevel());
        session.setNotes(endDetails.getNotes());
        
        // End the session (calculates points, sets timestamps)
        session.endSession();
        
        // Save to database
        session.save();
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
        List<StudyGoal> todayGoals = getTodayGoals();
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
