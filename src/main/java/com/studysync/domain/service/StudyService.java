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

    @Transactional(readOnly = true)
    public List<StudyGoal> getStudyGoalsForDate(LocalDate date) {
        return StudyGoal.findByDate(date);
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
        if (description == null || description.trim().isEmpty()) {
            throw ValidationException.requiredFieldMissing("description");
        }
        StudyGoal goal = new StudyGoal(null, date, description, false, null);
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

    public void deleteStudyGoal(String goalId) {
        StudyGoal.deleteById(goalId);
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
     * Process unachieved goals from a specific date and transfer them to the next day.
     * Applies point penalties and marks goals as delayed.
     * 
     * @param fromDate the date to process
     * @param toDate the date to transfer goals to (usually next day)
     * @return number of goals transferred
     */
    public int transferUnachievedGoals(LocalDate fromDate, LocalDate toDate) {
        List<StudyGoal> unachievedGoals = StudyGoal.findUnachievedByDate(fromDate);
        int transferredCount = 0;
        
        for (StudyGoal goal : unachievedGoals) {
            // Calculate penalty for this delay
            int penalty = goal.calculateDelayPenalty();
            
            // Create transferred goal
            StudyGoal transferred = goal.transferToNextDay(toDate, penalty);
            transferred.save();
            
            // Mark original goal as processed (could delete or mark differently)
            // For now, we'll keep the original goal but could add a processed flag
            
            transferredCount++;
        }
        
        return transferredCount;
    }
    
    /**
     * Automatically process yesterday's unachieved goals and transfer them to today.
     * This should be called daily (e.g., via scheduled task or on app startup).
     * 
     * @return number of goals transferred
     */
    public int processYesterdayGoals() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate today = LocalDate.now();
        
        return transferUnachievedGoals(yesterday, today);
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
