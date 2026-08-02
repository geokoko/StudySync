package com.studysync.domain.entity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A calendar day the user marked as off (holiday, sick day, break).
 * Work logged on such a day is kept and still shown in the calendar, but it is
 * excluded from global scores - see the scoring methods on
 * {@link com.studysync.domain.service.StudyService}.
 *
 * <p>Uses Active Record pattern - handles its own database operations.
 * The date is the primary key, so a day is either off or not; there is no
 * per-row state worth instantiating.</p>
 *
 * @since 0.1.6
 */
public final class OffDay {
    private static final Logger logger = LoggerFactory.getLogger(OffDay.class);

    /** Used when a day is marked off without a label. */
    public static final String DEFAULT_LABEL = "Off day";

    private static JdbcTemplate jdbcTemplate;

    private OffDay() {
    }

    public static void setJdbcTemplate(JdbcTemplate template) {
        jdbcTemplate = template;
    }

    /**
     * Marks a day as off, replacing any label already stored for it.
     *
     * @param date the day to mark
     * @param label optional description; blank falls back to {@link #DEFAULT_LABEL}
     */
    public static void mark(LocalDate date, String label) {
        requireTemplate();
        String text = (label == null || label.isBlank()) ? DEFAULT_LABEL : label.trim();
        jdbcTemplate.update("MERGE INTO off_days (date, label) VALUES (?, ?)", date, text);
        logger.info("Marked {} as an off day ({})", date, text);
    }

    /**
     * Removes the off-day mark from a day.
     *
     * @param date the day to clear
     * @return {@code true} when the day was marked off before
     */
    public static boolean unmark(LocalDate date) {
        requireTemplate();
        boolean removed = jdbcTemplate.update("DELETE FROM off_days WHERE date = ?", date) > 0;
        if (removed) {
            logger.info("Removed off-day mark from {}", date);
        }
        return removed;
    }

    /**
     * The label of a single off day.
     *
     * @param date the day to look up
     * @return the label, or empty when the day is a normal day
     */
    public static Optional<String> labelFor(LocalDate date) {
        requireTemplate();
        if (date == null) {
            return Optional.empty();
        }
        return jdbcTemplate.queryForList("SELECT label FROM off_days WHERE date = ?", String.class, date)
                .stream()
                .findFirst()
                .map(OffDay::labelOrDefault);
    }

    /**
     * Off days and their labels between two dates, both inclusive.
     * One query per rendered month instead of one per day cell.
     *
     * @param start first day of the range
     * @param end last day of the range
     * @return map of off day to label
     */
    public static Map<LocalDate, String> findLabelsInRange(LocalDate start, LocalDate end) {
        requireTemplate();
        Map<LocalDate, String> labels = new HashMap<>();
        for (Map.Entry<LocalDate, String> entry : jdbcTemplate.query(
                "SELECT date, label FROM off_days WHERE date BETWEEN ? AND ?",
                (rs, rowNum) -> Map.entry(rs.getDate("date").toLocalDate(), labelOrDefault(rs.getString("label"))),
                start, end)) {
            labels.put(entry.getKey(), entry.getValue());
        }
        return labels;
    }

    /**
     * Every off day, for filtering dated records out of global scores.
     *
     * @return set of all days marked off
     */
    public static Set<LocalDate> findAllDates() {
        requireTemplate();
        return new HashSet<>(jdbcTemplate.query("SELECT date FROM off_days",
                (rs, rowNum) -> rs.getDate("date").toLocalDate()));
    }

    private static String labelOrDefault(String label) {
        return (label == null || label.isBlank()) ? DEFAULT_LABEL : label;
    }

    private static void requireTemplate() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized. Make sure Spring context is loaded.");
        }
    }
}
