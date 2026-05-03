package com.studysync.domain.service;

import com.studysync.domain.exception.ValidationException;
import com.studysync.domain.entity.DailyReflection;
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
import java.time.LocalDateTime;
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

    private void markDirty() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    googleDriveService.markLocalDbDirty();
                }
            });
        } else {
            googleDriveService.markLocalDbDirty();
        }
    }

    private void markDirtyAndSaveLocally(String operation) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    googleDriveService.markLocalDbDirty();
                    if (!googleDriveService.saveLocally()) {
                        logger.warn("Local checkpoint failed after {}", operation);
                    }
                }
            });
        } else {
            googleDriveService.markLocalDbDirty();
            if (!googleDriveService.saveLocally()) {
                logger.warn("Local checkpoint failed after {}", operation);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<StudySession> getStudySessions() {
        return StudySession.findAll();
    }

    @Transactional(readOnly = true)
    public List<StudyGoal> getStudyGoals() {
        return StudyGoal.findAll();
    }

    public List<StudyGoal> getStudyGoalsForDate(LocalDate date) {
        ensureDelayedGoalsProcessedToday();
        return StudyGoal.findByDateIncludingDelayed(date);
    }

    /**
     * Get all study goals for a date including failed ones.
     * Used by calendar view which shows the complete history for each day.
     */
    public List<StudyGoal> getAllGoalsForDate(LocalDate date) {
        ensureDelayedGoalsProcessedToday();
        return StudyGoal.findAllByDateIncludingDelayed(date);
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

    /**
     * Get study goals planned for a future date.
     * Skips delay processing since future dates cannot have delayed goals.
     * 
     * @param date a future date to retrieve planned goals for
     * @return list of study goals planned for that date
     * @throws ValidationException if {@code date} is null or not strictly in the future
     */
    @Transactional(readOnly = true)
    public List<StudyGoal> getStudyGoalsForFutureDate(LocalDate date) {
        if (date == null) {
            throw ValidationException.requiredFieldMissing("date");
        }
        if (!date.isAfter(dateTimeService.getCurrentDate())) {
            throw ValidationException.invalidDateRange(
                date.toString(), "a future date (use getStudyGoalsForDate for past/present)");
        }
        return StudyGoal.findByDate(date);
    }

    @Transactional(readOnly = true)
    public List<DailyReflection> getDailyReflections() {
        return DailyReflection.findAll();
    }

    public List<StudyGoal> getTodayGoals() {
        ensureDelayedGoalsProcessedToday();
        return StudyGoal.findByDate(dateTimeService.getCurrentDate());
    }

    /**
     * Runs processAllDelayedGoals() at most once per calendar day.
     * Subsequent calls on the same day are no-ops.
     */
    private void ensureDelayedGoalsProcessedToday() {
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
     * Reschedules a delayed goal to appear on today's date exactly once.
     * The goal's achieved status is not changed. If the user does not complete
     * it today it will not carry forward again — it is a one-shot reschedule.
     *
     * @param goalId ID of the goal to reschedule
     */
    public void replanGoalForToday(String goalId) {
        StudyGoal.findById(goalId).ifPresent(goal -> {
            if (goal.isAchieved()) {
                return; // Already done
            }
            boolean created = StudyGoal.createReplanAttempt(goalId, dateTimeService.getCurrentDate());
            if (created) {
                markDirtyAndSaveLocally("study goal replan");
                logger.info("Created new attempt for goal '{}' on {}", goal.getDescription(), dateTimeService.getCurrentDate());
            }
        });
    }

    public boolean planGoalAttempt(String goalId, LocalDate plannedForDate) {
        if (goalId == null || goalId.isBlank()) {
            throw ValidationException.requiredFieldMissing("goalId");
        }
        if (plannedForDate == null) {
            throw ValidationException.requiredFieldMissing("plannedForDate");
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
     * Soft-deletes a study goal by marking it as failed.
     * The goal is preserved for historical display on its planned date.
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
            logger.warn("Requested mark-as-failed for study goal '{}' but it did not exist", goalId);
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
        StudySession session = new StudySession();
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

    public void deleteDailyReflection(LocalDate date) {
        boolean deleted = DailyReflection.deleteByDate(date);
        if (deleted) {
            markDirtyAndSaveLocally("daily reflection deletion");
        }
    }

    @Transactional(readOnly = true)
    public boolean reflectionExistsForDate(LocalDate date) {
        return DailyReflection.findByDate(date).isPresent();
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
    public List<StudySession> getSessionsInDateRange(LocalDate startDate, LocalDate endDate) {
        return StudySession.findInDateRange(startDate, endDate);
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
    // DELAYED GOAL MANAGEMENT
    // ================================================================
    
    /**
     * Process all goals and update delay penalties for overdue goals.
     * Only unachieved goals with dates in the past are considered delayed.
     * Goals overdue by two weeks or more are marked as FAILED (never deleted).
     * This should be called daily (e.g., via scheduled task or on app startup).
     *
     * @return summary of how many goals were updated or marked failed
     */
    public GoalDelayProcessingResult processAllDelayedGoals() {
        LocalDate today = dateTimeService.getCurrentDate();
        int missedAttempts = StudyGoal.markPendingAttemptsBefore(today);

        if (missedAttempts > 0) {
            markDirtyAndSaveLocally("delayed goal processing");
            logger.info("Marked {} overdue study goal attempt(s) as MISSED", missedAttempts);
        }

        return new GoalDelayProcessingResult(missedAttempts, 0);
    }

    /**
     * Summary of delayed-goal processing.
     * @param updatedGoals number of existing goals updated with delay metadata
     * @param failedGoals number of goals marked as failed because they exceeded the delay threshold
     */
    public record GoalDelayProcessingResult(int updatedGoals, int failedGoals) {
        public boolean hasChanges() {
            return updatedGoals > 0 || failedGoals > 0;
        }
    }
    
    /**
     * Calculate accumulated penalty for delayed goals.
     * Formula: 5 points for first delay, then 2 points per additional day.
     * 
     * @param totalDaysDelayed total days the goal has been delayed
     * @return total penalty points
     */
    private int calculateAccumulatedPenalty(int totalDaysDelayed) {
        if (totalDaysDelayed <= 0) return 0;
        if (totalDaysDelayed == 1) return 5; // First delay
        return 5 + (totalDaysDelayed - 1) * 2; // Additional days
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
    
    /**
     * Calculate total points deducted from delayed goals.
     */
    @Transactional(readOnly = true)
    public int getTotalDelayPenaltyPoints() {
        return StudyGoal.findDelayed().size();
    }
}
