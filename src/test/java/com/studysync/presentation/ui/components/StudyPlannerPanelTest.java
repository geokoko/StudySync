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
import javafx.scene.AccessibleAction;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
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
        // The focus test shows and closes a window; closing the last one must not stop JavaFX.
        Platform.setImplicitExit(false);
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
                // Grouped by task by default: a task's goals stay under its heading, not in each title.
                assertTrue(labels.indexOf("Parent / Subtask") < labels.indexOf("Read chapter"));
                assertTrue(labels.indexOf("Read chapter") < labels.indexOf("Finished chapter"));
                assertTrue(labels.indexOf("Finished chapter") < labels.indexOf("No task"));
                assertTrue(labels.indexOf("No task") < labels.indexOf("Unlinked goal"));
                assertFalse(labels.contains("Task: Parent / Subtask"));
                assertNotNull(button(panel, "+ Add goal"));
            }
        });
    }

    @Test
    void taskGoalGroupsKeepTheirSummaryAndCollapseChoiceAcrossRefreshesAndRegrouping() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                StudyGoal done = fixture.goal("Finished chapter", fixture.child, TODAY);
                done.setAchieved(true);
                StudyGoal dropped = mock(StudyGoal.class);
                when(dropped.getId()).thenReturn("dropped");
                when(dropped.getDescription()).thenReturn("Dropped chapter");
                when(dropped.getTaskId()).thenReturn(fixture.child.getId());
                when(dropped.getDate()).thenReturn(TODAY);
                when(dropped.getStatus()).thenReturn(StudyGoal.GoalStatus.ABANDONED);
                when(dropped.getAttemptOutcome()).thenReturn(StudyGoal.AttemptOutcome.DROPPED);
                when(dropped.isDropped()).thenReturn(true);
                fixture.attempts.addAll(List.of(done, dropped,
                        fixture.goal("Parent goal", fixture.parent, TODAY), fixture.goal("Unlinked", null, TODAY)));
                StudyPlannerPanel panel = fixture.panel();
                TitledPane child = taskGoalGroup(panel, "Parent / Subtask");
                assertTrue(child.isExpanded());
                assertTrue(labelTexts(child.getGraphic()).contains("2 planned \u00B7 1 done"));

                child.setExpanded(false);
                assertTrue(taskGoalGroup(panel, "Parent").isExpanded());
                taskGoalGroup(panel, "No task").setExpanded(false);
                fixture.goal.setAchieved(true);
                fixture.child.setTitle("Renamed subtask");
                panel.updateDisplay();

                child = taskGoalGroup(panel, "Parent / Renamed subtask");
                assertFalse(child.isExpanded());
                assertTrue(labelTexts(child.getGraphic()).contains("2 planned \u00B7 2 done"));
                assertTrue(child.getAccessibleText().contains("2 planned \u00B7 2 done"));
                assertFalse(taskGoalGroup(panel, "No task").isExpanded());

                ComboBox<?> group = nodes(panel).stream().filter(ComboBox.class::isInstance)
                        .map(node -> (ComboBox<?>) node)
                        .filter(combo -> "Group goals by".equals(combo.getAccessibleText())).findFirst().orElseThrow();
                group.getSelectionModel().select(1);
                assertFalse(nodes(goalsCard(panel)).stream()
                        .anyMatch(node -> node.getStyleClass().contains("planner-task-goals")));
                group.getSelectionModel().select(0);
                assertFalse(taskGoalGroup(panel, "Parent / Renamed subtask").isExpanded());
                taskGoalGroup(panel, "Parent / Renamed subtask").setExpanded(true);
                panel.updateDisplay();
                assertTrue(taskGoalGroup(panel, "Parent / Renamed subtask").isExpanded());
            }
        });
    }

    @Test
    void taskGroupsWithTheSameTitleCollapseIndependently() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                fixture.child.setParentId(null);
                fixture.child.setTitle("Same title");
                fixture.parent.setTitle("Same title");
                fixture.attempts.add(fixture.goal("Parent goal", fixture.parent, TODAY));
                StudyPlannerPanel panel = fixture.panel();
                TitledPane child = nodes(goalsCard(panel)).stream().filter(TitledPane.class::isInstance)
                        .map(TitledPane.class::cast).filter(pane -> labelTexts(pane.getContent())
                                .contains("Read chapter")).findFirst().orElseThrow();
                child.setExpanded(false);
                panel.updateDisplay();

                List<TitledPane> groups = nodes(goalsCard(panel)).stream().filter(TitledPane.class::isInstance)
                        .map(TitledPane.class::cast).toList();
                assertEquals(2, groups.size());
                assertEquals(1, groups.stream().filter(TitledPane::isExpanded).count());
                assertTrue(groups.stream().filter(TitledPane::isExpanded)
                        .anyMatch(pane -> labelTexts(pane.getContent()).contains("Parent goal")));
            }
        });
    }

    @Test
    void longTaskHeadersKeepCountsVisibleAndSupportAccessibleDisclosure() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                fixture.child.setTitle("A very long task title ".repeat(8));
                StudyPlannerPanel panel = fixture.panel();
                new Scene(panel, 900, 800).getStylesheets()
                        .add(getClass().getResource("/styles.css").toExternalForm());
                panel.applyCss();
                panel.layout();
                TitledPane group = nodes(goalsCard(panel)).stream().filter(TitledPane.class::isInstance)
                        .map(TitledPane.class::cast).findFirst().orElseThrow();
                Label count = nodes(group.getGraphic()).stream().filter(Label.class::isInstance)
                        .map(Label.class::cast).filter(label -> "1 planned \u00B7 0 done".equals(label.getText()))
                        .findFirst().orElseThrow();
                assertTrue(count.getWidth() >= count.prefWidth(-1) - 0.5);
                assertTrue(count.localToScene(count.getBoundsInLocal()).getMaxX()
                        <= goalsCard(panel).localToScene(goalsCard(panel).getLayoutBounds()).getMaxX());

                group.executeAccessibleAction(AccessibleAction.COLLAPSE);
                assertFalse(group.isExpanded());
                panel.layout();
                assertFalse(group.getContent().isVisible());
                assertTrue(count.isVisible());
                group.executeAccessibleAction(AccessibleAction.EXPAND);
                assertTrue(group.isExpanded());
            }
        });
    }

    @Test
    void goalsCanBeRegroupedByStatusOrShownUngroupedWithTheirTaskUnderTheTitle() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                StudyGoal achieved = fixture.goal("Finished chapter", fixture.child, TODAY);
                achieved.setAchieved(true);
                fixture.attempts.add(fixture.goal("Unlinked goal", null, TODAY));
                fixture.attempts.add(achieved);
                StudyPlannerPanel panel = fixture.panel();
                ComboBox<?> group = nodes(panel).stream().filter(ComboBox.class::isInstance)
                        .map(node -> (ComboBox<?>) node)
                        .filter(combo -> "Group goals by".equals(combo.getAccessibleText())).findFirst().orElseThrow();

                group.getSelectionModel().select(1);
                List<String> labels = labelTexts(goalsCard(panel));
                assertEquals(GoalListAgenda.Group.STATUS, group.getValue());
                assertTrue(labels.indexOf("Pending") < labels.indexOf("Read chapter"));
                assertTrue(labels.indexOf("Unlinked goal") < labels.indexOf("Done"));
                assertTrue(labels.indexOf("Done") < labels.indexOf("Finished chapter"));
                assertTrue(labels.contains("Task: Parent / Subtask"));

                group.getSelectionModel().select(2);
                labels = labelTexts(goalsCard(panel));
                assertFalse(labels.contains("Pending"));
                assertFalse(labels.contains("No task"));
                assertTrue(labels.indexOf("Unlinked goal") < labels.indexOf("Finished chapter"));
                assertTrue(labels.contains("Task: Parent / Subtask"));
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
    void aLongSessionGoalDoesNotWidenThePlannerPastTheSmallestWindow() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                fixture.goal.setDescription("Revise the introduction and tighten every paragraph ".repeat(8));
                StudySession running = new StudySession();
                running.setGoalId(fixture.goal.getId());
                running.setTaskId(fixture.child.getId());
                running.startSession();
                when(fixture.study.getActiveSession()).thenReturn(Optional.of(running));

                StudyPlannerPanel panel = fixture.panel();
                Scene scene = new Scene(panel, 900, 600);
                scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
                panel.applyCss();
                panel.layout();

                // 900 is the main window's minimum width.
                assertTrue(panel.getTop().minWidth(-1) < 900, "session bar min width " + panel.getTop().minWidth(-1));
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
    void planningAGoalKeepsTheTaskAvailableToPlanASecondGoal() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                fixture.attempts.clear();
                doAnswer(invocation -> {
                    fixture.attempts.add(fixture.goal(invocation.getArgument(0), fixture.child,
                            invocation.getArgument(1)));
                    fixture.child.setStatus(TaskStatus.IN_PROGRESS);
                    return null;
                }).when(fixture.study).addStudyGoal(anyString(), any(), eq(fixture.child.getId()), anyString());
                StudyPlannerPanel panel = fixture.panel();

                for (String description : List.of("Read chapter", "Write summary")) {
                    Node row = planningTile(panel, "Parent / Subtask");
                    assertNotNull(row.getOnDragDetected());
                    row.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                            false, false, false, false));
                    TextArea field = nodes(fixture.modal).stream().filter(TextArea.class::isInstance)
                            .map(TextArea.class::cast).findFirst().orElseThrow();
                    field.setText(description);
                    button(fixture.modal, "Add Goal").fire();
                    verify(fixture.study).addStudyGoal(description, TODAY, fixture.child.getId(), "");
                }

                assertEquals(2, fixture.attempts.size());
                assertTrue(labelTexts(panel).containsAll(List.of("Read chapter", "Write summary")));
                assertTrue(labelTexts(planningTile(panel, "Parent / Subtask")).contains("due today · work planned"));
            }
        });
    }

    @Test
    void planningAParentCanPlanTheGoalForOneOfItsActiveSubtasks() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                fixture.attempts.clear();
                fixture.parent.setDeadline(TODAY.minusDays(2));
                Task later = Fixture.task("later", "Later subtask", TODAY.plusDays(4));
                later.setParentId(fixture.parent.getId());
                Task done = Fixture.task("done", "Done subtask", TODAY.plusDays(1));
                done.setParentId(fixture.parent.getId());
                done.setStatus(TaskStatus.COMPLETED);
                when(fixture.tasks.getTasks()).thenReturn(List.of(fixture.parent, later, done, fixture.child));
                StudyPlannerPanel panel = fixture.panel();

                planningTile(panel, "Parent").fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,
                        "", "", KeyCode.ENTER, false, false, false, false));
                ComboBox<?> target = nodes(fixture.modal).stream().filter(ComboBox.class::isInstance)
                        .map(node -> (ComboBox<?>) node).findFirst().orElseThrow();
                assertEquals(List.of(fixture.parent, fixture.child, later), target.getItems());
                assertEquals(fixture.parent, target.getValue());
                assertTrue(labelTexts(fixture.modal).contains("Add goal for: Parent"));

                target.getSelectionModel().select(target.getItems().indexOf(fixture.child));
                assertTrue(labelTexts(fixture.modal).contains("Add goal for: Parent / Subtask"));
                nodes(fixture.modal).stream().filter(TextArea.class::isInstance).map(TextArea.class::cast)
                        .findFirst().orElseThrow().setText("Solve problem 1");
                button(fixture.modal, "Add Goal").fire();
                verify(fixture.study).addStudyGoal("Solve problem 1", TODAY, fixture.child.getId(), "");

                planningTile(panel, "Parent / Subtask").fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,
                        "", "", KeyCode.ENTER, false, false, false, false));
                assertTrue(nodes(fixture.modal).stream().noneMatch(ComboBox.class::isInstance),
                        "a subtask has no subtasks to choose from");
            }
        });
    }

    @Test
    void planningOneRetryLeavesTheOtherRetryAndNewGoalActionAvailable() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                fixture.attempts.clear();
                fixture.child.setDeadline(TODAY.plusDays(5));
                fixture.goal.setDate(TODAY.minusDays(2));
                fixture.goal.setFailed(true);
                StudyGoal second = fixture.goal("Write summary", fixture.child, TODAY.minusDays(1));
                second.setFailed(true);
                List<StudyGoal> retries = new ArrayList<>(List.of(fixture.goal, second));
                fixture.goalQueries.when(StudyGoal::findDelayedAndNotReplanned)
                        .thenAnswer(invocation -> List.copyOf(retries));
                when(fixture.study.replanGoalForToday(anyString(), any())).thenAnswer(invocation -> {
                    StudyGoal retry = retries.removeFirst();
                    assertEquals(retry.getId(), invocation.getArgument(0));
                    StudyGoal planned = fixture.goal(retry.getDescription(), fixture.child, TODAY);
                    planned.setId(retry.getId());
                    fixture.attempts.add(planned);
                    return true;
                });
                StudyPlannerPanel panel = fixture.panel();

                for (StudyGoal retry : List.of(fixture.goal, second)) {
                    planningTile(panel, "Parent / Subtask").fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,
                            "", "", KeyCode.ENTER, false, false, false, false));
                    assertTrue(labelTexts(fixture.modal).contains(retry.getDescription()));
                    assertNotNull(button(fixture.modal, "+ New goal"));
                    button(fixture.modal, "Plan retry today").fire();
                    verify(fixture.study).replanGoalForToday(retry.getId(), null);
                }

                planningTile(panel, "Parent / Subtask").fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,
                        "", "", KeyCode.ENTER, false, false, false, false));
                assertTrue(labelTexts(fixture.modal).contains("Add goal for: Parent / Subtask"));
                assertNotNull(button(fixture.modal, "Add Goal"));
                verify(fixture.study, never()).addStudyGoal(anyString(), any(), any(), any());
            }
        });
    }

    @Test
    void retryDialogStaysOpenAcrossActionsUntilDone() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                fixture.attempts.clear();
                fixture.child.setDeadline(TODAY.plusDays(5));
                fixture.goal.setDate(TODAY.minusDays(2));
                fixture.goal.setFailed(true);
                StudyGoal second = fixture.goal("Write summary", fixture.child, TODAY.minusDays(1));
                second.setFailed(true);
                List<StudyGoal> retries = new ArrayList<>(List.of(fixture.goal, second));
                fixture.goalQueries.when(StudyGoal::findDelayedAndNotReplanned)
                        .thenAnswer(invocation -> List.copyOf(retries));
                when(fixture.study.replanGoalForToday(fixture.goal.getId(), null)).thenAnswer(invocation -> {
                    retries.remove(fixture.goal);
                    return true;
                });
                StudyPlannerPanel panel = fixture.panel();
                planningTile(panel, "Parent / Subtask").fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,
                        "", "", KeyCode.ENTER, false, false, false, false));

                button(fixture.modal, "Plan retry today").fire();
                verify(fixture.study).replanGoalForToday(fixture.goal.getId(), null);
                assertTrue(labelTexts(fixture.modal).contains("Planned \u201CRead chapter\u201D for today."));
                assertFalse(labelTexts(fixture.modal).contains("Read chapter"));
                assertTrue(labelTexts(fixture.modal).contains("Write summary"));

                button(fixture.modal, "Edit").fire();
                assertTrue(labelTexts(fixture.modal).contains("Edit goal"));
                button(fixture.modal, "Cancel").fire();
                assertTrue(labelTexts(fixture.modal).contains("Plan retry: Parent / Subtask"));
                button(fixture.modal, "Edit").fire();
                button(fixture.modal, "Save").fire();
                verify(fixture.study).updateStudyGoalDetails(eq(second.getId()), eq("Write summary"), any(), any());
                assertTrue(labelTexts(fixture.modal).contains("Goal saved."));

                button(fixture.modal, "+ New goal").fire();
                nodes(fixture.modal).stream().filter(TextArea.class::isInstance).map(TextArea.class::cast)
                        .findFirst().orElseThrow().setText("Outline answers");
                button(fixture.modal, "Add Goal").fire();
                verify(fixture.study).addStudyGoal(eq("Outline answers"), eq(TODAY), eq(fixture.child.getId()), any());
                assertTrue(labelTexts(fixture.modal).contains("Added a new goal."));
                assertTrue(labelTexts(fixture.modal).contains("Write summary"));

                // A goal that changed elsewhere reports in the dialog and leaves the refreshed list.
                when(fixture.study.replanGoalForToday(second.getId(), null)).thenAnswer(invocation -> {
                    retries.remove(second);
                    return false;
                });
                button(fixture.modal, "Plan retry today").fire();
                assertTrue(labelTexts(fixture.modal).contains(
                        "This goal is no longer ready to retry. The list has been updated."));
                assertTrue(labelTexts(fixture.modal).contains("No missed goals left to retry."));

                button(fixture.modal, "Done").fire();
                assertNull(fixture.modal);
            }
        });
    }

    @Test
    void cancellingNewGoalAfterARefreshFailureDoesNotRestoreStaleRetries() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                fixture.attempts.clear();
                fixture.goal.setDate(TODAY.minusDays(1));
                fixture.goal.setFailed(true);
                fixture.goalQueries.when(StudyGoal::findDelayedAndNotReplanned).thenReturn(List.of(fixture.goal));
                when(fixture.study.replanGoalForToday(fixture.goal.getId(), null)).thenReturn(true);
                StudyPlannerPanel panel = fixture.panel();
                planningTile(panel, "Parent / Subtask").fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,
                        "", "", KeyCode.ENTER, false, false, false, false));
                when(fixture.tasks.getTasks()).thenThrow(new IllegalStateException("reload failed"));
                button(fixture.modal, "Plan retry today").fire();

                button(fixture.modal, "+ New goal").fire();
                button(fixture.modal, "Cancel").fire();

                assertTrue(labelTexts(fixture.modal).contains("The planner could not refresh: reload failed"));
                assertFalse(nodes(fixture.modal).stream().anyMatch(node -> node instanceof Button action
                        && "Plan retry today".equals(action.getText())));
                button(fixture.modal, "Done").fire();
                assertNull(fixture.modal);
            }
        });
    }

    @ParameterizedTest
    @CsvSource({"retry,tasks", "edit,tasks", "add,tasks", "retry,progress", "edit,progress", "add,progress"})
    void retryDialogReportsRefreshFailuresAfterEverySave(String action, String failingRefresh) throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                fixture.attempts.clear();
                fixture.goal.setDate(TODAY.minusDays(1));
                fixture.goal.setFailed(true);
                fixture.goalQueries.when(StudyGoal::findDelayedAndNotReplanned).thenReturn(List.of(fixture.goal));
                when(fixture.study.replanGoalForToday(fixture.goal.getId(), null)).thenReturn(true);
                StudyPlannerPanel panel = fixture.panel();
                planningTile(panel, "Parent / Subtask").fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,
                        "", "", KeyCode.ENTER, false, false, false, false));
                if (action.equals("edit")) {
                    button(fixture.modal, "Edit").fire();
                } else if (action.equals("add")) {
                    button(fixture.modal, "+ New goal").fire();
                    nodes(fixture.modal).stream().filter(TextArea.class::isInstance).map(TextArea.class::cast)
                            .findFirst().orElseThrow().setText("New goal");
                }
                if (failingRefresh.equals("tasks")) {
                    when(fixture.tasks.getTasks()).thenThrow(new IllegalStateException("reload failed"));
                } else {
                    when(fixture.study.calculateDailyProgress()).thenThrow(new IllegalStateException("reload failed"));
                }

                button(fixture.modal, switch (action) {
                    case "edit" -> "Save";
                    case "add" -> "Add Goal";
                    default -> "Plan retry today";
                }).fire();

                assertTrue(labelTexts(fixture.modal).contains(
                        "Changes saved. The planner could not refresh: reload failed"));
                assertTrue(labelTexts(fixture.modal).contains(
                        "Retry list unavailable. Close and reopen this dialog to refresh."));
                assertFalse(labelTexts(fixture.modal).contains("No missed goals left to retry."));
                assertFalse(labelTexts(fixture.modal).contains("Goal saved."));
                assertFalse(labelTexts(fixture.modal).contains("Added a new goal."));
                assertFalse(nodes(fixture.modal).stream().anyMatch(node -> node instanceof Button button
                        && ("Plan retry today".equals(button.getText()) || "Save".equals(button.getText())
                        || "Add Goal".equals(button.getText()))));
                switch (action) {
                    case "edit" -> verify(fixture.study).updateStudyGoalDetails(eq(fixture.goal.getId()),
                            eq("Read chapter"), any(), any());
                    case "add" -> verify(fixture.study).addStudyGoal(eq("New goal"), eq(TODAY),
                            eq(fixture.child.getId()), any());
                    default -> verify(fixture.study).replanGoalForToday(fixture.goal.getId(), null);
                }
                button(fixture.modal, "Done").fire();
                assertNull(fixture.modal);
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"add", "edit"})
    void standaloneGoalDialogsCloseAfterSavingEvenWhenRefreshFails(String action) throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                StudyPlannerPanel panel = fixture.panel();
                if (action.equals("add")) {
                    button(panel, "+ Add goal").fire();
                    nodes(fixture.modal).stream().filter(TextArea.class::isInstance).map(TextArea.class::cast)
                            .findFirst().orElseThrow().setText("New goal");
                } else {
                    button(panel, "Edit").fire();
                }
                when(fixture.tasks.getTasks()).thenThrow(new IllegalStateException("reload failed"));

                button(fixture.modal, action.equals("add") ? "Add Goal" : "Save").fire();

                assertNull(fixture.modal);
                assertTrue(labelTexts(panel).contains("The planner could not refresh: reload failed"));
                if (action.equals("add")) {
                    verify(fixture.study).addStudyGoal(eq("New goal"), eq(TODAY), any(), any());
                } else {
                    verify(fixture.study).updateStudyGoalDetails(eq(fixture.goal.getId()), eq("Read chapter"),
                            any(), any());
                }
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"tasks", "progress"})
    void successfulRefreshClearsPreviousPlannerError(String failingRefresh) throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                StudyPlannerPanel panel = fixture.panel();
                button(panel, "+ Add goal").fire();
                nodes(fixture.modal).stream().filter(TextArea.class::isInstance).map(TextArea.class::cast)
                        .findFirst().orElseThrow().setText("New goal");
                if (failingRefresh.equals("tasks")) {
                    when(fixture.tasks.getTasks()).thenThrow(new IllegalStateException("reload failed"));
                } else {
                    when(fixture.study.calculateDailyProgress()).thenThrow(new IllegalStateException("reload failed"));
                }
                button(fixture.modal, "Add Goal").fire();

                Label error = nodes(panel).stream().filter(Label.class::isInstance).map(Label.class::cast)
                        .filter(label -> "The planner could not refresh: reload failed".equals(label.getText()))
                        .findFirst().orElseThrow();
                assertTrue(error.isVisible());
                assertTrue(error.isManaged());

                doReturn(List.of(fixture.parent, fixture.child)).when(fixture.tasks).getTasks();
                doReturn(0).when(fixture.study).calculateDailyProgress();
                button(panel, "+ Add goal").fire();
                nodes(fixture.modal).stream().filter(TextArea.class::isInstance).map(TextArea.class::cast)
                        .findFirst().orElseThrow().setText("Another goal");
                button(fixture.modal, "Add Goal").fire();

                assertNull(fixture.modal);
                assertFalse(error.isVisible());
                assertFalse(error.isManaged());
            }
        });
    }

    @Test
    void standaloneGoalDialogsStillCloseAfterSaving() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                StudyPlannerPanel panel = fixture.panel();
                button(panel, "+ Add goal").fire();
                nodes(fixture.modal).stream().filter(TextArea.class::isInstance).map(TextArea.class::cast)
                        .findFirst().orElseThrow().setText("Skim chapter two");
                button(fixture.modal, "Add Goal").fire();
                verify(fixture.study).addStudyGoal(eq("Skim chapter two"), eq(TODAY), any(), any());
                assertNull(fixture.modal);

                button(panel, "Edit").fire();
                button(fixture.modal, "Save").fire();
                verify(fixture.study).updateStudyGoalDetails(eq(fixture.goal.getId()), eq("Read chapter"),
                        any(), any());
                assertNull(fixture.modal);
            }
        });
    }

    @Test
    void retryButtonsKeepTheirFullLabelsInANarrowDialog() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                fixture.attempts.clear();
                fixture.child.setDeadline(TODAY.plusDays(5));
                fixture.goal.setDate(TODAY.minusDays(1));
                fixture.goal.setFailed(true);
                fixture.goalQueries.when(StudyGoal::findDelayedAndNotReplanned).thenReturn(List.of(fixture.goal));
                StudyPlannerPanel panel = fixture.panel();
                planningTile(panel, "Parent / Subtask").fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,
                        "", "", KeyCode.ENTER, false, false, false, false));
                Scene scene = new Scene(new javafx.scene.layout.StackPane(fixture.modal), 300, 600);
                scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
                scene.getRoot().applyCss();
                scene.getRoot().layout();

                List<javafx.scene.control.Labeled> actions = nodes(fixture.modal).stream()
                        .filter(node -> node instanceof Button || node instanceof MenuButton)
                        .map(javafx.scene.control.Labeled.class::cast).filter(Node::isManaged).toList();
                assertTrue(actions.stream().anyMatch(action -> "Plan retry today".equals(action.getText())));
                actions.forEach(action -> assertTrue(action.getWidth() >= action.prefWidth(-1) - 0.5,
                        action.getText() + " is narrower than its label"));
            }
        });
    }

    @Test
    void retryingACompleteChecklistAsksInTheRowBeforePlanning() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                fixture.attempts.clear();
                fixture.child.setDeadline(TODAY.plusDays(5));
                fixture.goal.setDate(TODAY.minusDays(1));
                fixture.goal.setFailed(true);
                fixture.goal.setDoneCriteria("[x] Read");
                fixture.goalQueries.when(StudyGoal::findDelayedAndNotReplanned).thenReturn(List.of(fixture.goal));
                when(fixture.study.replanGoalForToday(fixture.goal.getId(),
                        StudyService.CompletedChecklist.START_OVER)).thenReturn(true);
                StudyPlannerPanel panel = fixture.panel();
                planningTile(panel, "Parent / Subtask").fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,
                        "", "", KeyCode.ENTER, false, false, false, false));

                button(fixture.modal, "Plan retry today").fire();
                assertTrue(labelTexts(fixture.modal).contains("Every checklist item is already ticked."));
                verify(fixture.study, never()).replanGoalForToday(anyString(), any());
                button(fixture.modal, "Cancel").fire();
                assertFalse(labelTexts(fixture.modal).contains("Every checklist item is already ticked."));

                button(fixture.modal, "Plan retry today").fire();
                button(fixture.modal, "Start over").fire();
                verify(fixture.study).replanGoalForToday(fixture.goal.getId(),
                        StudyService.CompletedChecklist.START_OVER);
                assertTrue(labelTexts(fixture.modal).contains(
                        "Planned “Read chapter” for today with its checklist cleared."));
            }
        });
    }

    @Test
    void anEarlierDaysAchievedGoalIsReadOnlyInThePlanner() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                LocalDate yesterday = TODAY.minusDays(1);
                fixture.attempts.clear();
                fixture.goal.setDate(yesterday);
                fixture.goal.setDoneCriteria("[x] Read\n[x] Notes");
                fixture.goal.setAchieved(true);
                when(fixture.study.getAllGoalsForDate(yesterday)).thenReturn(List.of(fixture.goal));
                StudyPlannerPanel panel = fixture.panel();

                nodes(panel).stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> button.getGraphic() instanceof Label icon && "◀".equals(icon.getText()))
                        .findFirst().orElseThrow().fire();

                List<CheckBox> checks = nodes(goalsCard(panel)).stream().filter(CheckBox.class::isInstance)
                        .map(CheckBox.class::cast).toList();
                CheckBox achieved = checks.stream()
                        .filter(check -> "Achieve goal: Read chapter".equals(check.getAccessibleText()))
                        .findFirst().orElseThrow();
                assertTrue(achieved.isSelected());
                assertTrue(achieved.isDisabled());
                List<CheckBox> lines = checks.stream().filter(check -> check != achieved).toList();
                assertEquals(2, lines.size());
                assertTrue(lines.stream().allMatch(line -> line.isSelected() && line.isDisabled()));

                // A screen reader acts through accessible actions, which ignore mouse transparency.
                lines.forEach(line -> line.executeAccessibleAction(AccessibleAction.FIRE));
                assertTrue(lines.stream().allMatch(CheckBox::isSelected));
                verify(fixture.study, never()).setGoalCriterionDone(anyString(), anyInt(), anyBoolean());
            }
        });
    }

    @Test
    void closingTheRetryDialogReturnsFocusToItsRebuiltTile() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                fixture.attempts.clear();
                fixture.child.setDeadline(TODAY.plusDays(5));
                fixture.goal.setDate(TODAY.minusDays(1));
                fixture.goal.setFailed(true);
                fixture.goalQueries.when(StudyGoal::findDelayedAndNotReplanned).thenReturn(List.of(fixture.goal));
                StudyPlannerPanel panel = fixture.panel();
                Scene scene = new Scene(panel, 1100, 800);
                // A scene only tracks keyboard focus once it is in a window.
                javafx.stage.Stage stage = new javafx.stage.Stage();
                stage.setScene(scene);
                stage.show();
                Node opened = planningTile(panel, "Parent / Subtask");
                opened.requestFocus();
                opened.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,
                        "", "", KeyCode.ENTER, false, false, false, false));
                Node rebuilt = planningTile(panel, "Parent / Subtask");
                assertFalse(opened == rebuilt, "opening the dialog rebuilds the planning tiles");

                button(fixture.modal, "Done").fire();

                assertNull(fixture.modal);
                assertEquals(rebuilt, scene.getFocusOwner());
                stage.close();
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
                when(fixture.study.replanGoalForToday(fixture.goal.getId(), null)).thenReturn(true);
                StudyPlannerPanel panel = fixture.panel();
                Node row = nodes(panel).stream().filter(node -> node.isFocusTraversable()
                        && node.getStyleClass().contains("cal-plan-item")).findFirst().orElseThrow();
                row.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                        false, false, false, false));
                button(fixture.modal, "Plan retry today").fire();
                verify(fixture.study).replanGoalForToday(fixture.goal.getId(), null);
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
                assertTrue(checks.stream().allMatch(CheckBox::isDisabled));
                checks.forEach(CheckBox::fire);
                verify(fixture.study, never()).updateStudyGoalAchievement(anyString(), anyBoolean(), any());
                verify(fixture.study, never()).setGoalCriterionDone(anyString(), anyInt(), anyBoolean());
            }
        });
    }

    @Test
    void droppedGoalsAreCollapsedAndCanReopenWithoutOfferingCompletionOrASession() throws Exception {
        onFx(() -> {
            try (Fixture fixture = new Fixture()) {
                StudyGoal dropped = mock(StudyGoal.class);
                when(dropped.getId()).thenReturn("dropped");
                when(dropped.getDescription()).thenReturn("Build a prototype using the unavailable API");
                when(dropped.getTaskId()).thenReturn(fixture.child.getId());
                when(dropped.getDate()).thenReturn(TODAY);
                when(dropped.getAttemptNumber()).thenReturn(1);
                when(dropped.getAttemptOutcome()).thenReturn(StudyGoal.AttemptOutcome.DROPPED);
                when(dropped.getStatus()).thenReturn(StudyGoal.GoalStatus.ABANDONED);
                when(dropped.isDropped()).thenReturn(true);
                when(dropped.isDroppedAttempt()).thenReturn(true);
                when(dropped.getDropReason()).thenReturn("The API was discontinued.");
                fixture.attempts.add(dropped);
                StudyPlannerPanel panel = fixture.panel();
                Scene scene = new Scene(panel, 900, 700);
                scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
                panel.applyCss();
                panel.layout();
                TitledPane history = nodes(panel).stream().filter(TitledPane.class::isInstance)
                        .map(TitledPane.class::cast).filter(pane -> pane.getText().equals("Dropped (1)"))
                        .findFirst().orElseThrow();
                assertFalse(history.isExpanded());
                assertTrue(nodes(history.getContent()).stream().filter(CheckBox.class::isInstance)
                        .map(CheckBox.class::cast).allMatch(CheckBox::isDisabled));
                assertTrue(nodes(history.getContent()).stream().filter(Button.class::isInstance)
                        .map(Button.class::cast).noneMatch(button -> "Start session".equals(button.getText())));
                MenuButton actions = nodes(history.getContent()).stream().filter(MenuButton.class::isInstance)
                        .map(MenuButton.class::cast).findFirst().orElseThrow();
                assertEquals(List.of("Reopen…"), actions.getItems().stream().map(MenuItem::getText).toList());
                assertTrue(labelTexts(history.getContent()).contains("Reason: The API was discontinued."));
                GoalActionsTest.snapshot(panel, "planner-goal-outcomes.png");
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

    /** The goals card alone: the attempt overview above it also has Pending and Done labels. */
    private static Node goalsCard(Node panel) {
        return nodes(panel).stream().filter(node -> node.getStyleClass().contains("planner-goals"))
                .findFirst().orElseThrow();
    }

    private static TitledPane taskGoalGroup(Node panel, String title) {
        return nodes(goalsCard(panel)).stream().filter(TitledPane.class::isInstance).map(TitledPane.class::cast)
                .filter(pane -> title.equals(pane.getText())).findFirst().orElseThrow();
    }

    private static List<String> labelTexts(Node root) {
        return nodes(root).stream().filter(Label.class::isInstance).map(node -> ((Label) node).getText()).toList();
    }

    private static Node planningTile(final Node root, final String title) {
        return nodes(root).stream().filter(node -> node.getStyleClass().contains("plan-tile")
                && labelTexts(node).contains(title)).findFirst().orElseThrow();
    }

    private static List<Node> nodes(Node root) {
        List<Node> result = new ArrayList<>();
        result.add(root);
        if (root instanceof TitledPane pane) {
            if (pane.getGraphic() != null) {
                result.addAll(nodes(pane.getGraphic()));
            }
            if (pane.getContent() != null) {
                result.addAll(nodes(pane.getContent()));
            }
        } else if (root instanceof ScrollPane scroll) {
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
