package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.StudySession;
import com.studysync.domain.entity.Task;
import com.studysync.domain.service.CategoryService;
import com.studysync.domain.service.DateTimeService;
import com.studysync.domain.service.StudyService;
import com.studysync.domain.service.TaskService;
import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.domain.valueobject.TaskStatus;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudyPlannerPanelTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    @BeforeAll
    static void startJavaFx() throws Exception {
        SessionVisibilityTest.startJavaFx();
    }

    @Test
    void timerStaysOutsideScrollAndGoalsIncludeClosedTaskAndUnlinkedAttempts() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                fixture.child.setStatus(TaskStatus.COMPLETED);
                StudyGoal unlinked = fixture.goal("Unlinked goal", null, TODAY);
                StudyGoal achieved = fixture.goal("Finished chapter", fixture.child, TODAY);
                achieved.setAchieved(true);
                fixture.attempts.addAll(List.of(achieved, unlinked));
                StudyPlannerPanel panel = fixture.panel();
                Scene scene = new Scene(panel, 1100, 800);
                scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
                panel.applyCss();
                panel.layout();
                Node bar = panel.getTop();
                double before = bar.localToScene(bar.getBoundsInLocal()).getMinY();
                ((ScrollPane) panel.getCenter()).setVvalue(1);
                panel.layout();
                assertEquals(before, bar.localToScene(bar.getBoundsInLocal()).getMinY());
                assertTrue(bar.isVisible());
                List<String> labels = nodes(panel).stream().filter(Label.class::isInstance)
                        .map(Label.class::cast).map(Label::getText).toList();
                assertTrue(labels.contains("Parent / Subtask"));
                assertTrue(labels.contains("Unlinked goal"));
                assertTrue(labels.indexOf("Unlinked goal") < labels.indexOf("Finished chapter"));
                assertNotNull(button(panel, "+ Add goal"));
            }
        });
    }

    @Test
    void startingFromGoalLinksSessionAndStopUsesExistingDialog() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                StudyPlannerPanel panel = fixture.panel();
                button(panel, "Start session").fire();
                verify(fixture.study).startStudySession(fixture.goal.getId(), fixture.child.getId());
                assertTrue(nodes(panel.getTop()).stream().filter(Label.class::isInstance)
                        .map(Label.class::cast).anyMatch(label -> label.getText().contains("Parent / Subtask")));
                assertFalse(button(panel, "Start Session").isManaged());
                assertTrue(button(panel, "Start session").isDisabled());
                button(panel, "Stop").fire();
                assertNotNull(button(fixture.modal, "OK"));
                button(fixture.modal, "Cancel").fire();
                assertTrue(button(panel, "Stop").isVisible());
                button(panel, "Stop").fire();
                button(fixture.modal, "OK").fire();
                verify(fixture.study).endStudySession(eq(fixture.session), any());
                assertTrue(button(panel, "Start Session").isVisible());
                assertFalse(button(panel, "Stop").isManaged());
            }
        });
    }

    @Test
    void sessionLeftRunningOnATopLevelTaskIsRestoredWhenThePlannerOpens() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                StudySession running = new StudySession();
                running.setTaskId(fixture.parent.getId());
                running.startSession();
                when(fixture.study.getActiveSession()).thenReturn(Optional.of(running));
                fixture.taskQueries.when(() -> Task.findById(fixture.parent.getId()))
                        .thenReturn(Optional.of(fixture.parent));

                StudyPlannerPanel panel = fixture.panel();

                assertTrue(button(panel, "Stop").isVisible());
                assertTrue(button(panel, "Start session").isDisabled());
                assertTrue(labelTexts(panel.getTop()).contains("Parent"));
            }
        });
    }

    @Test
    void restoredSessionNamesTheFamilyAndAMultiLineGoalOnOneLine() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                fixture.goal.setDescription("Read chapter\nthen   summarise it");
                StudySession running = new StudySession();
                running.setGoalId(fixture.goal.getId());
                running.setTaskId(fixture.child.getId());
                running.startSession();
                when(fixture.study.getActiveSession()).thenReturn(Optional.of(running));

                StudyPlannerPanel panel = fixture.panel();

                assertTrue(labelTexts(panel.getTop())
                        .contains("Parent / Subtask · Read chapter then summarise it"));
            }
        });
    }

    @Test
    void stoppedSessionSaveFailureKeepsTimerAndDialogAvailable() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                StudyPlannerPanel panel = fixture.panel();
                button(panel, "Start session").fire();
                doThrow(new IllegalStateException("test save failure")).when(fixture.study)
                        .endStudySession(eq(fixture.session), any());
                button(panel, "Stop").fire();
                button(fixture.modal, "OK").fire();
                assertTrue(button(panel, "Stop").isVisible());
                assertTrue(nodes(fixture.modal).stream().filter(Label.class::isInstance).map(Label.class::cast)
                        .anyMatch(label -> label.getText().contains("test save failure")));
                button(fixture.modal, "Cancel").fire();
                assertTrue(button(panel, "Start session").isDisabled());
            }
        });
    }

    @Test
    void needsPlanKeyboardActionPrefillsTaskAndUsesSelectedDate() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                fixture.attempts.clear();
                StudyPlannerPanel panel = fixture.panel();
                Node row = nodes(panel).stream().filter(node -> node.isFocusTraversable()
                        && node.getStyleClass().contains("cal-plan-item")).findFirst().orElseThrow();
                row.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                        false, false, false, false));
                assertTrue(nodes(fixture.modal).stream().filter(Label.class::isInstance).map(Label.class::cast)
                        .anyMatch(label -> label.getText().contains("Parent / Subtask")));
                TextArea description = nodes(fixture.modal).stream().filter(TextArea.class::isInstance)
                        .map(TextArea.class::cast).findFirst().orElseThrow();
                description.setText("Write introduction");
                button(fixture.modal, "Add Goal").fire();
                verify(fixture.study).addStudyGoal("Write introduction", TODAY, fixture.child.getId(), "");
            }
        });
    }

    @Test
    void retryRowsKeepTheirGoalIdentityInsteadOfCreatingAnotherGoal() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                fixture.attempts.clear();
                fixture.goal.setDate(TODAY.minusDays(1));
                fixture.goal.setFailed(true);
                fixture.goalQueries.when(StudyGoal::findDelayedAndNotReplanned).thenReturn(List.of(fixture.goal));
                when(fixture.study.replanGoalForToday(fixture.goal.getId())).thenReturn(true);
                StudyPlannerPanel panel = fixture.panel();
                Node row = nodes(panel).stream().filter(node -> node.isFocusTraversable()
                        && node.getStyleClass().contains("cal-plan-item")).findFirst().orElseThrow();
                row.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                        false, false, false, false));
                button(fixture.modal, "Plan retry today").fire();
                verify(fixture.study).replanGoalForToday(fixture.goal.getId());
                verify(fixture.study, never()).addStudyGoal(anyString(), any(), any(), any());
            }
        });
    }

    @Test
    void futureNavigationKeepsLiveSessionAndDefaultsNewGoalToShownDay() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                StudyPlannerPanel panel = fixture.panel();
                button(panel, "Start session").fire();
                Button next = nodes(panel).stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> button.getGraphic() instanceof Label label
                                && "\u25B6".equals(label.getText())).findFirst().orElseThrow();
                next.fire();
                verify(fixture.study).getAllGoalsForFutureDate(TODAY.plusDays(1));
                assertTrue(button(panel, "Stop").isVisible());
                button(panel, "+ Add goal").fire();
                DatePicker picker = nodes(fixture.modal).stream().filter(DatePicker.class::isInstance)
                        .map(DatePicker.class::cast).findFirst().orElseThrow();
                assertEquals(TODAY.plusDays(1), picker.getValue());
            }
        });
    }

    @Test
    void missedHistoryCannotToggleAchievementOrChecklistOfLaterRetry() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                fixture.goal.setFailed(true);
                fixture.goal.setDoneCriteria("[ ] Finish chapter");
                StudyPlannerPanel panel = fixture.panel();
                List<CheckBox> checks = nodes(panel).stream().filter(CheckBox.class::isInstance)
                        .map(CheckBox.class::cast).toList();
                assertFalse(checks.isEmpty());
                assertTrue(checks.stream().allMatch(check -> check.isDisabled()
                        || check.isMouseTransparent() && check.getOnAction() == null));
                checks.forEach(CheckBox::fire);
                verify(fixture.study, never()).updateStudyGoalAchievement(anyString(), anyBoolean(), any());
                verify(fixture.study, never()).setGoalCriterionDone(anyString(), anyInt(), anyBoolean());
            }
        });
    }

    private static void onFx(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            action.run();
            return null;
        });
        Platform.runLater(task);
        task.get(15, TimeUnit.SECONDS);
    }

    private static Button button(Node root, String text) {
        return nodes(root).stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }

    private static List<String> labelTexts(Node root) {
        return nodes(root).stream().filter(Label.class::isInstance).map(node -> ((Label) node).getText()).toList();
    }

    private static List<Node> nodes(Node root) {
        List<Node> result = new ArrayList<>();
        result.add(root);
        if (root instanceof ScrollPane scroll) {
            result.addAll(nodes(scroll.getContent()));
        } else if (root instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> result.addAll(nodes(child)));
        }
        return result;
    }

    private static final class Fixture implements AutoCloseable {
        private final StudyService study = mock(StudyService.class);
        private final TaskService tasks = mock(TaskService.class);
        private final DateTimeService dates = mock(DateTimeService.class);
        private final MockedStatic<Task> taskQueries = mockStatic(Task.class);
        private final MockedStatic<StudyGoal> goalQueries = mockStatic(StudyGoal.class);
        private final Task parent = task("parent", "Parent", TODAY.plusDays(3));
        private final Task child = task("child", "Subtask", TODAY);
        private final StudyGoal goal = goal("Read chapter", child, TODAY);
        private final List<StudyGoal> attempts = new ArrayList<>(List.of(goal));
        private final StudySession session = new StudySession();
        private StudyPlannerPanel panel;
        private Node modal;

        Fixture() {
            child.setParentId(parent.getId());
            session.setGoalId(goal.getId());
            session.setTaskId(child.getId());
            session.startSession();
            when(dates.getCurrentDate()).thenReturn(TODAY);
            when(tasks.getTasks()).thenReturn(List.of(parent, child));
            when(tasks.getTasksForDate(any())).thenReturn(List.of(child));
            when(study.getAllGoalsForDate(TODAY)).thenAnswer(invocation -> List.copyOf(attempts));
            when(study.startStudySession(goal.getId(), child.getId())).thenReturn(session);
            goalQueries.when(() -> StudyGoal.parseCriteria(any())).thenCallRealMethod();
            goalQueries.when(() -> StudyGoal.findById(goal.getId())).thenReturn(Optional.of(goal));
            taskQueries.when(() -> Task.findById(child.getId())).thenReturn(Optional.of(child));
        }

        StudyPlannerPanel panel() {
            panel = new StudyPlannerPanel(study, dates, tasks, mock(CategoryService.class),
                    content -> modal = content, () -> modal = null);
            return panel;
        }

        StudyGoal goal(String description, Task task, LocalDate date) {
            return new StudyGoal(null, date, description, false, null, 0, false, 0,
                    task == null ? null : task.getId());
        }

        @Override
        public void close() {
            // Stop the fixture's timeline without running an application or persistence lifecycle.
            if (panel != null && button(panel, "Stop").isVisible()) {
                org.mockito.Mockito.doNothing().when(study).endStudySession(any(), any());
                button(panel, "Stop").fire();
                button(modal, "OK").fire();
            }
            goalQueries.close();
            taskQueries.close();
        }

        private static Task task(String id, String title, LocalDate deadline) {
            return new Task(id, title, "", "Study", new TaskPriority(3), deadline,
                    TaskStatus.OPEN, 0, "", null);
        }
    }
}
