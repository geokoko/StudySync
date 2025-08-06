package com.studysync.domain.service;

import com.studysync.domain.entity.StudySession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Service to import study sessions for previous days.
 */
@Service
@Transactional
public class DataImportService {
    
    private static final Logger logger = LoggerFactory.getLogger(DataImportService.class);
    
    public void importPreviousDaysSessions() {
        logger.info("Importing study sessions for previous days...");
        
        try {
            // Thursday (2025-08-01) - 3 hours total
            createStudySession("2025-08-01", "09:00", "10:30", 90, 4, 4, "Morning study session - good focus");
            createStudySession("2025-08-01", "14:00", "15:30", 90, 3, 4, "Afternoon session - decent progress");
            
            // Friday (2025-08-02) - 3 hours total 
            createStudySession("2025-08-02", "10:00", "12:00", 120, 4, 4, "Long morning session - very productive");
            createStudySession("2025-08-02", "16:00", "17:00", 60, 3, 3, "Short evening review session");
            
            // Saturday (2025-08-03) - 3 hours total
            createStudySession("2025-08-03", "09:30", "11:00", 90, 4, 4, "Weekend morning session - relaxed but focused");
            createStudySession("2025-08-03", "15:30", "17:00", 90, 3, 4, "Afternoon weekend study");
            
            // Sunday (2025-08-04) - 3 hours total
            createStudySession("2025-08-04", "10:00", "11:30", 90, 3, 3, "Sunday morning session - bit tired");
            createStudySession("2025-08-04", "14:00", "15:30", 90, 4, 4, "Sunday afternoon - good recovery");
            
            // Tuesday (2025-08-06) - earlier sessions
            createStudySession("2025-08-06", "09:00", "10:30", 90, 4, 4, "Tuesday morning session - great start");
            createStudySession("2025-08-06", "13:00", "14:30", 90, 4, 3, "Tuesday afternoon session");
            
            logger.info("Successfully imported study sessions for previous days");
            
        } catch (Exception e) {
            logger.error("Error importing study sessions", e);
        }
    }
    
    private void createStudySession(String dateStr, String startTime, String endTime, 
                                  int duration, int focusLevel, int confidenceLevel, String notes) {
        try {
            LocalDate date = LocalDate.parse(dateStr);
            LocalDateTime startDateTime = LocalDateTime.parse(dateStr + "T" + startTime + ":00");
            LocalDateTime endDateTime = LocalDateTime.parse(dateStr + "T" + endTime + ":00");
            
            StudySession session = new StudySession();
            session.setDate(date);
            session.setStartTime(startDateTime);
            session.setEndTime(endDateTime);
            session.setDurationMinutes(duration);
            session.setCompleted(true);
            session.setFocusLevel(focusLevel);
            session.setConfidenceLevel(confidenceLevel);
            session.setNotes(notes);
            session.setSubject("General Study");
            session.setPointsEarned(calculatePoints(duration, focusLevel));
            
            session.save();
            logger.debug("Created session for {} from {} to {}", date, startTime, endTime);
            
        } catch (Exception e) {
            logger.error("Error creating session for {}", dateStr, e);
        }
    }
    
    private int calculatePoints(int duration, int focusLevel) {
        // Simple point calculation: base points per minute * focus multiplier
        return (duration / 2) * focusLevel / 3;
    }
}