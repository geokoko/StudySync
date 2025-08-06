package com.studysync.domain.service;

/**
 * Data transfer object for study session end details.
 * Contains information provided by the user when ending a study session.
 */
public class StudySessionEnd {
    private final int focusLevel;
    private final String notes;
    
    public StudySessionEnd(int focusLevel, String notes) {
        if (focusLevel < 1 || focusLevel > 5) {
            throw new IllegalArgumentException("Focus level must be between 1 and 5");
        }
        this.focusLevel = focusLevel;
        this.notes = notes;
    }
    
    public int getFocusLevel() {
        return focusLevel;
    }
    
    public String getNotes() {
        return notes;
    }
    
    @Override
    public String toString() {
        return "StudySessionEnd{focusLevel=" + focusLevel + ", notes='" + notes + "'}";
    }
}
