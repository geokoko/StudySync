package com.studysync.domain.service;

import com.studysync.domain.entity.OffDay;
import com.studysync.domain.entity.ProjectSession;
import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.StudySession;
import com.studysync.domain.entity.Task;
import com.studysync.domain.entity.TaskReschedule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The single place that decides what counts towards a score and how much it is
 * worth. The calendar's per-day figures and the profile's 30-day figures are
 * both {@link ScoreBreakdown}s produced here, over different date ranges.
 *
 * <h3>Points</h3>
 * <ul>
 *   <li><strong>Sessions</strong> earn time scaled by focus - see
 *       {@link StudySession#calculatePoints}.</li>
 *   <li><strong>Achieved goal attempts</strong> earn a flat amount. Effort is
 *       already paid for by session points; this pays for the outcome.</li>
 *   <li><strong>Completed tasks</strong> earn timeliness only. A flat
 *       completion bonus would double-count the sessions and goals that got
 *       the task done, whereas whether it landed before its deadline is
 *       information nothing else captures. Rescheduling costs points too, so a
 *       task cannot be made "on time" just by pushing its deadline.</li>
 * </ul>
 *
 * <h3>Off days</h3>
 * Anything dated on a day the user marked off is excluded, and off days shrink
 * the denominators rather than counting as days with no work.
 */
// Not readOnly: scoring a day asks StudyService for that day's goals, which
// runs the once-per-day overdue-attempt sweep and therefore writes. H2 ignores
// the read-only hint so it would work either way, but the annotation would be
// claiming something untrue.
@Service
@Transactional
public class ScoringService {

    /** Credit for an achieved goal attempt. */
    public static final int GOAL_ACHIEVED_POINTS = 15;

    /** Credit for finishing a task on or before its deadline. */
    public static final int TASK_ON_TIME_POINTS = 20;

    /** Charge for finishing a task after its deadline. */
    public static final int TASK_LATE_PENALTY = -10;

    /** Charge per deadline change, so pushing the date is not a free pass. */
    public static final int TASK_RESCHEDULE_PENALTY = -5;

    /** Worst a single task can score, so one bad task cannot sink a month. */
    public static final int TASK_MIN_POINTS = -15;

    /** Minutes of study in a day that count as a full day's work. */
    private static final int DAILY_MINUTES_TARGET = 120;

    /** How far back {@link #getStudyStreak()} looks for study activity. */
    private static final int STREAK_WINDOW_DAYS = 90;

    private final StudyService studyService;
    private final DateTimeService dateTimeService;

    public ScoringService(StudyService studyService, DateTimeService dateTimeService) {
        this.studyService = studyService;
        this.dateTimeService = dateTimeService;
    }

    /**
     * Score for a single calendar day.
     *
     * @param date the day to score
     * @return the day's breakdown; an off day still reports its figures, they
     *         simply never reach a window score
     */
    public ScoreBreakdown scoreForDate(LocalDate date) {
        if (date == null) {
            return ScoreBreakdown.empty(1);
        }
        return score(List.of(date), studyService.getGoalsForDate(date), 1);
    }

    /**
     * Score for the last {@code days} days, excluding days marked off.
     *
     * @param days size of the window
     * @return the window's breakdown
     */
    public ScoreBreakdown scoreForWindow(int days) {
        Set<LocalDate> offDays = OffDay.findAllDates();
        List<LocalDate> dates = windowDates(days).stream()
                .filter(date -> !offDays.contains(date))
                .toList();
        Set<LocalDate> scored = new HashSet<>(dates);

        List<StudyGoal> goals = StudyGoal.findAll().stream()
                .filter(goal -> scored.contains(goal.getDate()))
                .toList();

        return score(dates, goals, dates.size());
    }

    /**
     * How many of the last {@code days} days count towards scores.
     *
     * @param days size of the window
     * @return the window size minus the off days it contains
     */
    public int countScoringDays(int days) {
        Set<LocalDate> offDays = OffDay.findAllDates();
        return (int) windowDates(days).stream().filter(date -> !offDays.contains(date)).count();
    }

    /** The {@code days} calendar days ending today, off days included. */
    private List<LocalDate> windowDates(int days) {
        LocalDate today = dateTimeService.getCurrentDate();
        return today.minusDays(Math.max(days, 1) - 1L).datesUntil(today.plusDays(1)).toList();
    }

    /**
     * Consecutive study days ending today. Off days neither extend nor break
     * the streak - they are skipped, so a holiday does not cost a streak the
     * user earned.
     *
     * @return streak length in days, capped at the 90-day lookup window
     */
    public int getStudyStreak() {
        Map<LocalDate, List<StudySession>> sessionsByDate = studyService
                .getSessionsGroupedByDate(STREAK_WINDOW_DAYS);
        Set<LocalDate> offDays = OffDay.findAllDates();
        int streak = 0;
        LocalDate date = dateTimeService.getCurrentDate();

        for (int i = 0; i < STREAK_WINDOW_DAYS; i++) {
            if (!offDays.contains(date)) {
                if (sessionsByDate.getOrDefault(date, List.of()).isEmpty()) {
                    break;
                }
                streak++;
            }
            date = date.minusDays(1);
        }
        return streak;
    }

    /**
     * Points a completed task is worth: on time or late, less what its
     * reschedules cost.
     *
     * @param task the task to score
     * @param rescheduleCount how many times its deadline moved
     * @return points, floored at {@link #TASK_MIN_POINTS}; zero when the task is
     *         unfinished or has no deadline to be measured against
     */
    public static int taskPoints(Task task, int rescheduleCount) {
        return task.completedOnTime()
                .map(onTime -> Math.max(TASK_MIN_POINTS,
                        (onTime ? TASK_ON_TIME_POINTS : TASK_LATE_PENALTY)
                                + rescheduleCount * TASK_RESCHEDULE_PENALTY))
                .orElse(0);
    }

    private ScoreBreakdown score(List<LocalDate> dates, List<StudyGoal> goals, int scoringDays) {
        if (dates.isEmpty()) {
            return ScoreBreakdown.empty(Math.max(scoringDays, 1));
        }

        Set<LocalDate> dateSet = new HashSet<>(dates);
        LocalDate from = dates.getFirst();
        LocalDate to = dates.getLast();

        List<StudySession> studySessions = StudySession.findInDateRange(from, to).stream()
                .filter(session -> dateSet.contains(session.getDate()))
                .toList();
        List<ProjectSession> projectSessions = ProjectSession.findInDateRange(from, to).stream()
                .filter(session -> dateSet.contains(session.getDate()))
                .toList();
        List<Task> completedTasks = Task.findCompletedBetween(from, to).stream()
                .filter(task -> dateSet.contains(task.getCompletedAt()))
                .toList();

        int sessionPoints = studySessions.stream().mapToInt(StudySession::getPointsEarned).sum()
                + projectSessions.stream().mapToInt(ProjectSession::getPointsEarned).sum();

        int goalsAchieved = (int) goals.stream().filter(StudyGoal::isAchieved).count();
        int goalsMissed = (int) goals.stream().filter(StudyGoal::isFailed).count();
        int goalPoints = goalsAchieved * GOAL_ACHIEVED_POINTS;

        Map<String, Integer> reschedules = TaskReschedule.countByTaskIds(
                completedTasks.stream().map(Task::getId).collect(Collectors.toSet()));
        int taskPoints = completedTasks.stream()
                .mapToInt(task -> taskPoints(task, reschedules.getOrDefault(task.getId(), 0)))
                .sum();

        int minutes = studySessions.stream().mapToInt(StudySession::getDurationMinutes).sum()
                + projectSessions.stream().mapToInt(ProjectSession::getDurationMinutes).sum();
        double avgFocus = studySessions.isEmpty() ? 0.0
                : studySessions.stream().mapToInt(StudySession::getFocusLevel).average().orElse(0.0);
        long activeDays = studySessions.stream().map(StudySession::getDate).distinct().count();

        double productivity = productivity(minutes, avgFocus, goalsAchieved, goalsMissed, activeDays, scoringDays);

        return new ScoreBreakdown(
                sessionPoints + goalPoints + taskPoints,
                sessionPoints,
                goalPoints,
                taskPoints,
                productivity,
                studySessions.size() + projectSessions.size(),
                minutes,
                avgFocus,
                goalsAchieved,
                goalsMissed,
                goals.size(),
                completedTasks.size(),
                scoringDays);
    }

    /**
     * The 0-100 rating, over one day or thirty. Every part is measured per
     * scoring day, which is what lets the same formula serve both.
     */
    private double productivity(int minutes, double avgFocus, int goalsAchieved, int goalsMissed,
                                long activeDays, int scoringDays) {
        int days = Math.max(scoringDays, 1);

        double timeScore = Math.min(1.0, (double) minutes / (DAILY_MINUTES_TARGET * days)) * 30;
        double focusScore = (avgFocus / 5.0) * 30;
        double consistencyScore = Math.min(1.0, (double) activeDays / days) * 20;

        int decidedGoals = goalsAchieved + goalsMissed;
        double goalScore = decidedGoals > 0 ? ((double) goalsAchieved / decidedGoals) * 20 : 0;

        return timeScore + focusScore + consistencyScore + goalScore;
    }
}
