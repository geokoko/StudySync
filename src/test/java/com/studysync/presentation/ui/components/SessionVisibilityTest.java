package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.StudySession;
import com.studysync.domain.entity.Task;
import javafx.application.Platform;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class SessionVisibilityTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        FutureTask<Void> ready = new FutureTask<>(() -> null);
        Platform.startup(ready);
        ready.get(10, TimeUnit.SECONDS);
    }

    @Test
    void sessionLabelsPreserveEmojiAndOnlyAddTooltipsForTruncation() throws Exception {
        FutureTask<Void> check = new FutureTask<>(() -> {
            StudySession session = new StudySession();
            StudyGoal goal = new StudyGoal("a".repeat(30) + "😀" + "b".repeat(10));
            session.setGoalId(goal.getId());
            try (var goals = mockStatic(StudyGoal.class)) {
                goals.when(() -> StudyGoal.findById(goal.getId())).thenReturn(Optional.of(goal));
                Label longLabel = TaskStyleUtils.sessionLinkLabel(session);
                assertEquals("Goal: " + "a".repeat(30) + "😀…", longLabel.getText());
                assertEquals("Goal: " + goal.getDescription(), longLabel.getTooltip().getText());

                // Forty code points can occupy more than forty UTF-16 units.
                goal.setDescription("😀".repeat(34));
                Label shortLabel = TaskStyleUtils.sessionLinkLabel(session);
                assertEquals("Goal: " + goal.getDescription(), shortLabel.getText());
                assertNull(shortLabel.getTooltip());
            }
            return null;
        });
        Platform.runLater(check);
        check.get(10, TimeUnit.SECONDS);
    }

    @Test
    void missingGoalFallsBackToTaskAndUnlinkedSessionsHaveNoLabel() throws Exception {
        FutureTask<Void> check = new FutureTask<>(() -> {
            StudySession session = new StudySession();
            session.setGoalId("deleted-goal");
            session.setTaskId("task");
            Task task = mock(Task.class);
            when(task.getTitle()).thenReturn("Read chapter");
            try (var goals = mockStatic(StudyGoal.class); var tasks = mockStatic(Task.class)) {
                goals.when(() -> StudyGoal.findById("deleted-goal")).thenReturn(Optional.empty());
                tasks.when(() -> Task.findById("task")).thenReturn(Optional.of(task));
                assertEquals("Task: Read chapter", TaskStyleUtils.sessionLinkLabel(session).getText());
                assertNull(TaskStyleUtils.sessionLinkLabel(new StudySession()));
            }
            return null;
        });
        Platform.runLater(check);
        check.get(10, TimeUnit.SECONDS);
    }

    @Test
    void goalSummaryHidesZeroAndIdentifiesLifetimeCountsIncludingIncompleteSessions() {
        StudyGoal goal = new StudyGoal("Read chapter");
        goal.setDate(LocalDate.of(2026, 9, 7));
        try (var sessions = mockStatic(StudySession.class)) {
            sessions.when(() -> StudySession.countByGoalId(goal.getId())).thenReturn(0L, 1L, 3_000_000_000L);
            assertEquals("Attempt 1 planned for 2026-09-07", CalendarViewPanel.formatGoalAttemptSummary(goal));
            assertTrue(CalendarViewPanel.formatGoalAttemptSummary(goal)
                    .endsWith("Goal: 1 linked session across all attempts (including incomplete)"));
            assertTrue(CalendarViewPanel.formatGoalAttemptSummary(goal)
                    .endsWith("Goal: 3000000000 linked sessions across all attempts (including incomplete)"));
        }
    }
}
