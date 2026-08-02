package com.studysync.domain.service;

/**
 * Everything the UI needs to show a score for one day or one window of days.
 *
 * <p>Both the calendar's per-day score and the profile's 30-day score come
 * from here, so the two can no longer drift apart the way two hand-written
 * formulas did.</p>
 *
 * @param points total points earned, sessions plus goals plus task timeliness
 * @param sessionPoints share of {@code points} from study and project sessions
 * @param goalPoints share of {@code points} from achieved goal attempts
 * @param taskPoints share of {@code points} from finishing tasks on time or late
 * @param productivity 0-100 rating of the period
 * @param sessions number of study and project sessions
 * @param minutes total minutes logged
 * @param avgFocus mean focus level of study sessions, 0 when there were none
 * @param goalsAchieved achieved goal attempts
 * @param goalsMissed missed or abandoned goal attempts
 * @param goalsTotal goal attempts in the period, including still-pending ones
 * @param tasksCompleted tasks completed in the period
 * @param scoringDays days in the period that count, off days already removed
 */
public record ScoreBreakdown(
    int points,
    int sessionPoints,
    int goalPoints,
    int taskPoints,
    double productivity,
    int sessions,
    int minutes,
    double avgFocus,
    int goalsAchieved,
    int goalsMissed,
    int goalsTotal,
    int tasksCompleted,
    int scoringDays
) {
    /** An empty period, used for error paths and days with nothing on them. */
    public static ScoreBreakdown empty(int scoringDays) {
        return new ScoreBreakdown(0, 0, 0, 0, 0.0, 0, 0, 0.0, 0, 0, 0, 0, scoringDays);
    }

    /** Points per hour logged, the efficiency figure shown in the profile. */
    public double pointsPerHour() {
        return minutes > 0 ? points * 60.0 / minutes : 0.0;
    }

    /** Achieved minus missed goal attempts. Pending attempts count as neither. */
    public int netGoals() {
        return goalsAchieved - goalsMissed;
    }
}
