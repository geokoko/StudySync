package com.studysync.domain.service;

import com.studysync.domain.entity.Project;
import com.studysync.domain.entity.ProjectSession;
import com.studysync.domain.valueobject.ProjectStatus;
import com.studysync.domain.service.ProjectSessionEnd;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Simplified service layer for project-related operations.
 * Uses Active Record pattern - models handle their own persistence.
 */
@Service
@Transactional
public class ProjectService {

    @Autowired
    public ProjectService() {
    }

    @Transactional(readOnly = true)
    public List<Project> getProjects() {
        return Project.findAll();
    }

    @Transactional(readOnly = true)
    public List<Project> getActiveProjects() {
        return Project.findActive();
    }

    @Transactional(readOnly = true)
    public Optional<Project> getProjectById(String projectId) {
        return Project.findById(projectId);
    }

    public void addProject(Project project) {
        if (project == null) {
            throw new IllegalArgumentException("Project cannot be null");
        }
        if (project.getTitle() == null || project.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Project title cannot be empty");
        }
        project.save();
    }

    public void updateProject(Project project) {
        if (project == null) {
            throw new IllegalArgumentException("Project cannot be null");
        }
        project.save();
    }

    public void deleteProject(String projectId) {
        // Delete all associated sessions first
        ProjectSession.deleteByProjectId(projectId);
        // Then delete the project
        Project.deleteById(projectId);
    }

    @Transactional(readOnly = true)
    public List<Project> searchProjects(String title, String category, ProjectStatus status) {
        return Project.findAll().stream()
                .filter(p -> title == null || title.isEmpty() || p.getTitle().toLowerCase().contains(title.toLowerCase()))
                .filter(p -> category == null || category.isEmpty() || p.getCategory().equalsIgnoreCase(category))
                .filter(p -> status == null || p.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProjectSession> getProjectSessions() {
        return ProjectSession.findAll();
    }

    @Transactional(readOnly = true)
    public List<ProjectSession> getSessionsForProject(String projectId) {
        return ProjectSession.findByProjectId(projectId);
    }

    public ProjectSession startProjectSession(String projectId) {
        Project project = getProjectById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        ProjectSession session = new ProjectSession(projectId);
        session.setStartTime(LocalDateTime.now());
        session.save();  // Model handles its own persistence
        return session;
    }

    public void endProjectSession(ProjectSession session, ProjectSessionEnd endDetails) {
        ProjectSession existingSession = ProjectSession.findById(session.getId())
                .orElseThrow(() -> new IllegalArgumentException("Project session not found."));

        // Apply user input from dialog to the session
        existingSession.setSessionTitle(endDetails.sessionTitle());
        existingSession.setObjectives(endDetails.objectives());
        existingSession.setProgress(endDetails.progress());
        existingSession.setNextSteps(endDetails.nextSteps());
        existingSession.setChallenges(endDetails.challenges());
        existingSession.setNotes(endDetails.notes());
        
        // End the session (calculates points, sets timestamps)
        existingSession.endSession();
        
        // Save the session
        existingSession.save();

        // Update project statistics
        Project project = getProjectById(existingSession.getProjectId())
                .orElseThrow(() -> new IllegalStateException("Project not found for session"));

        project.addWorkedMinutes(existingSession.getDurationMinutes());
        project.incrementSessionCount();
        project.save();
    }

    public void deleteProjectSession(String sessionId) {
        ProjectSession session = ProjectSession.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Project session not found."));

        // Update project statistics before deleting session
        Project.findById(session.getProjectId()).ifPresent(project -> {
            project.addWorkedMinutes(-session.getDurationMinutes());
            project.setTotalSessionsCount(Math.max(0, project.getTotalSessionsCount() - 1));
            project.save();
        });

        // Delete the session
        ProjectSession.deleteById(sessionId);
    }

    @Transactional(readOnly = true)
    public List<ProjectSession> getTodayProjectSessions() {
        return ProjectSession.findByDate(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<ProjectSession> getProjectSessionsForDate(LocalDate date) {
        return ProjectSession.findByDate(date);
    }

    @Transactional(readOnly = true)
    public List<ProjectSession> getProjectSessionsInDateRange(LocalDate startDate, LocalDate endDate) {
        return ProjectSession.findInDateRange(startDate, endDate);
    }

    @Transactional(readOnly = true)
    public List<ProjectSession> getRecentProjectSessions(int days) {
        return ProjectSession.findRecent(days);
    }

    @Transactional(readOnly = true)
    public long getTotalProjectCount() {
        return Project.countAll();
    }

    @Transactional(readOnly = true)
    public long getActiveProjectCount() {
        return Project.countActive();
    }

    @Transactional(readOnly = true)
    public long getTotalSessionsCount() {
        return ProjectSession.findAll().size();
    }

    @Transactional(readOnly = true)
    public List<Project> getOverdueProjects() {
        return Project.findOverdue();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getProjectSessionCounts() {
        return Project.findAll().stream()
                .collect(Collectors.toMap(Project::getTitle, p -> ProjectSession.countByProjectId(p.getId())));
    }
}