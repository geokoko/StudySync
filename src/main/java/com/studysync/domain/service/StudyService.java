package com.studysync.domain.service;

import com.studysync.domain.exception.ValidationException;
import com.studysync.domain.entity.DailyReflection;
import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.StudySession;
import com.studysync.domain.service.StudySessionEnd;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    
    public StudyService() {
        // No dependencies needed - Active Record pattern
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
        // Ensure delayed goals are properly processed before retrieving
        processAllDelayedGoals();
        
        return StudyGoal.findByDateIncludingDelayed(date);
    }

    @Transactional(readOnly = true)
    public List<DailyReflection> getDailyReflections() {
        return DailyReflection.findAll();
    }

    @Transactional(readOnly = true)
    public List<StudyGoal> getTodayGoals() {
        return StudyGoal.findByDate(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<StudySession> getTodaySessions() {
        return StudySession.findByDate(LocalDate.now());
    }

    public void addStudyGoal(String description, LocalDate date) throws ValidationException {
        addStudyGoal(description, date, null);
    }
    
    public void addStudyGoal(String description, LocalDate date, String taskId) throws ValidationException {
        if (description == null || description.trim().isEmpty()) {
            throw ValidationException.requiredFieldMissing("description");
        }
        StudyGoal goal = new StudyGoal(null, date, description, false, null, 0, false, 0, taskId);
        goal.save();
    }

    public void updateStudyGoalAchievement(String goalId, boolean achieved, String reasonIfNot) {
        Optional<StudyGoal> goalOpt = StudyGoal.findById(goalId);
        if (goalOpt.isPresent()) {
            StudyGoal goal = goalOpt.get();
            goal.setAchieved(achieved);
            goal.setReasonIfNotAchieved(reasonIfNot);
            goal.save();
        }
    }

    public boolean deleteStudyGoal(String goalId) {
        return StudyGoal.deleteById(goalId);
    }

    public StudySession startStudySession() {
        StudySession session = new StudySession();
        session.startSession();
        session.save();  // Model handles its own persistence
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
    }

    public void addDailyReflection(DailyReflection reflection) {
        reflection.save();
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
        DailyReflection.deleteByDate(date);
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
        StudySession.deleteById(sessionId);
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
     * This should be called daily (e.g., via scheduled task or on app startup).
     * 
     * @return number of delayed goals processed
     */
    public int processAllDelayedGoals() {
        LocalDate today = LocalDate.now();
        List<StudyGoal> allGoals = StudyGoal.findAll();
        int totalProcessed = 0;
        
        for (StudyGoal goal : allGoals) {
            // Skip if goal is achieved
            if (goal.isAchieved()) {
                continue;
            }
            
            // Check if goal date is in the past (delayed)
            if (goal.getDate().isBefore(today)) {
                // Calculate days delayed and penalty
                int daysDelayed = (int) java.time.temporal.ChronoUnit.DAYS.between(goal.getDate(), today);
                int penalty = calculateAccumulatedPenalty(daysDelayed);
                
                // Update goal with delay information
                goal.setDelayed(true);
                goal.setDaysDelayed(daysDelayed);
                goal.setPointsDeducted(penalty);
                goal.save();
                
                totalProcessed++;
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
        
        return totalProcessed;
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
