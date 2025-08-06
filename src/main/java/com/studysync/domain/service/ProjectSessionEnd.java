package com.studysync.domain.service;

/**
 * Data transfer object for project session end details.
 * Contains information provided by the user when ending a project session.
 */
public class ProjectSessionEnd {
    private final String sessionTitle;
    private final String objectives;
    private final String progress;
    private final String nextSteps;
    private final String challenges;
    private final String notes;
    
    public ProjectSessionEnd(String sessionTitle, String objectives, String progress,
                           String nextSteps, String challenges, String notes) {
        this.sessionTitle = sessionTitle;
        this.objectives = objectives;
        this.progress = progress;
        this.nextSteps = nextSteps;
        this.challenges = challenges;
        this.notes = notes;
    }
    
    public String getSessionTitle() {
        return sessionTitle;
    }
    
    public String getObjectives() {
        return objectives;
    }
    
    public String getProgress() {
        return progress;
    }
    
    public String getNextSteps() {
        return nextSteps;
    }
    
    public String getChallenges() {
        return challenges;
    }
    
    public String getNotes() {
        return notes;
    }
    
    @Override
    public String toString() {
        return "ProjectSessionEnd{sessionTitle='" + sessionTitle + "', objectives='" + objectives + 
               "', progress='" + progress + "', nextSteps='" + nextSteps + 
               "', challenges='" + challenges + "', notes='" + notes + "'}";
    }
}
