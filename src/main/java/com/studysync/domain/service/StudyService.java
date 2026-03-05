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
import java.time.temporal.ChronoUnit;
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

    /** Guards processAllDelayedGoals() so the full scan runs at most once per calendar day. */
    private LocalDate lastDelayProcessingDate;

    @Autowired
    public StudyService(GoogleDriveService googleDriveService) {
        this.googleDriveService = googleDriveService;
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
        if (!date.isAfter(LocalDate.now())) {
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
        return StudyGoal.findByDate(LocalDate.now());
    }

    /**
     * Runs processAllDelayedGoals() at most once per calendar day.
     * Subsequent calls on the same day are no-ops.
     */
    private void ensureDelayedGoalsProcessedToday() {
        LocalDate today = LocalDate.now();
        if (!today.equals(lastDelayProcessingDate)) {
            processAllDelayedGoals();
            lastDelayProcessingDate = today;
        }
    }

    @Transactional(readOnly = true)
    public List<StudySession> getTodaySessions() {
        return StudySession.findByDate(LocalDate.now());
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
                    Task.updateStatus(taskId, TaskStatus.IN_PROGRESS);
                    logger.info("Auto-transitioned task '{}' from OPEN to IN_PROGRESS after goal creation",
                            task.getTitle());
                }
            });
        }

        markDirty();
    }

    /**
     * Returns delayed, unachieved goals that are eligible for manual rescheduling
     * to today. Goals linked to CANCELLED or POSTPONED tasks are excluded.
     *
     * @return list of goals the user can choose to re-plan for today
     */
    @Transactional(readOnly = true)
    public List<StudyGoal> getDelayedGoalsForReplanning() {
        return StudyGoal.findDelayedAndNotReplanned().stream()
                .filter(goal -> {
                    if (goal.getTaskId() == null || goal.getTaskId().isBlank()) {
                        return false; // Only show goals tied to a task
                    }
                    return Task.findById(goal.getTaskId())
                            .map(task -> task.getStatus() != TaskStatus.CANCELLED
                                      && task.getStatus() != TaskStatus.POSTPONED)
                            .orElse(false);
                })
                .collect(Collectors.toList());
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
            if (goal.isAchieved() || goal.getReplannedForDate() != null) {
                return; // Already done or already rescheduled
            }
            goal.setReplannedForDate(LocalDate.now());
            goal.save();
            markDirty();
            logger.info("Rescheduled goal '{}' to appear today ({})", goal.getDescription(), LocalDate.now());
        });
    }

    public void updateStudyGoalAchievement(String goalId, boolean achieved, String reasonIfNot) {
        Optional<StudyGoal> goalOpt = StudyGoal.findById(goalId);
        if (goalOpt.isPresent()) {
            StudyGoal goal = goalOpt.get();
            goal.setAchieved(achieved);
            goal.setReasonIfNotAchieved(reasonIfNot);
            goal.save();
            markDirty();
        }
    }

    public boolean deleteStudyGoal(String goalId) {
        if (goalId == null || goalId.isBlank()) {
            throw ValidationException.requiredFieldMissing("goalId");
        }
        boolean deleted = StudyGoal.deleteById(goalId);
        if (deleted) {
            markDirty();
        } else {
            logger.warn("Requested deletion for study goal '{}' but it did not exist", goalId);
        }
        return deleted;
    }

    public StudySession startStudySession() {
        StudySession session = new StudySession();
        session.startSession();
        session.save();  // Model handles its own persistence
        markDirty();
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
        markDirty();
    }

    public void addDailyReflection(DailyReflection reflection) {
        reflection.save();
        markDirty();
    }

    @Transactional(readOnly = true)
    public Optional<DailyReflection> getTodayReflection() {
        return DailyReflection.findByDate(LocalDate.now());
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
            markDirty();
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
            markDirty();
        }
    }

    @Transactional(readOnly = true)
    public List<StudySession> getSessionsForDate(LocalDate date) {
        return StudySession.findByDate(date);
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
     * Goals overdue by two weeks or more are automatically deleted.
     * This should be called daily (e.g., via scheduled task or on app startup).
     *
     * @return summary of how many goals were updated or auto-removed
     */
    public GoalDelayProcessingResult processAllDelayedGoals() {
        LocalDate today = LocalDate.now();
        List<StudyGoal> allGoals = StudyGoal.findAll();
        int updatedGoals = 0;
        int removedGoals = 0;
        
        for (StudyGoal goal : allGoals) {
            // Skip if goal is achieved
            if (goal.isAchieved()) {
                continue;
            }

            // Skip goals that were manually rescheduled — they had their one chance
            // and should not be picked up by automatic delay carry-forward again.
            if (goal.getReplannedForDate() != null) {
                continue;
            }
            
            // Check if goal date is in the past (delayed)
            if (goal.getDate().isBefore(today)) {
                int daysDelayed = (int) ChronoUnit.DAYS.between(goal.getDate(), today);
                if (daysDelayed >= 14) {
                    String goalLabel = (goal.getDescription() != null && !goal.getDescription().isBlank())
                        ? goal.getDescription()
                        : goal.getId();
                    boolean deleted = StudyGoal.deleteById(goal.getId());
                    if (deleted) {
                        removedGoals++;
                        logger.info("Removed study goal '{}' after {} days overdue",
                                goalLabel, daysDelayed);
                    } else {
                        logger.warn("Failed to auto-remove overdue goal '{}'", goal.getId());
                    }
                    continue;
                }

                int penalty = calculateAccumulatedPenalty(daysDelayed);

                // Update goal with delay information
                goal.setDelayed(true);
                goal.setDaysDelayed(daysDelayed);
                goal.setPointsDeducted(penalty);
                goal.save();

                updatedGoals++;
            } else {
                // Goal is not delayed - ensure delay flags are cleared
                if (goal.isDelayed()) {
                    goal.setDelayed(false);
                    goal.setDaysDelayed(0);
                    goal.setPointsDeducted(0);
                    goal.save();
                }
            }
        }
        
        return new GoalDelayProcessingResult(updatedGoals, removedGoals);
    }

    /**
     * Summary of delayed-goal processing.
     * @param updatedGoals number of existing goals updated with delay metadata
     * @param removedGoals number of goals deleted because they exceeded the delay threshold
     */
    public record GoalDelayProcessingResult(int updatedGoals, int removedGoals) {
        public boolean hasChanges() {
            return updatedGoals > 0 || removedGoals > 0;
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
        return StudyGoal.findDelayedByDate(LocalDate.now());
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
        List<StudyGoal> delayedGoals = StudyGoal.findDelayed();
        return delayedGoals.stream().mapToInt(StudyGoal::getPointsDeducted).sum();
    }
}
