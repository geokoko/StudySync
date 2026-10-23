package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.service.StudyService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoalActionsTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    @BeforeAll
    static void startJavaFx() throws Exception {
        SessionVisibilityTest.startJavaFx();
        Platform.setImplicitExit(false);
    }

    @Test
    void menusDistinguishMissedAttemptsDroppedGoalsAndFuturePlans() throws Exception {
        onFx(() -> {
            StudyService service = mock(StudyService.class);
            StudyGoal goal = goal(StudyGoal.AttemptOutcome.PENDING, false);
            assertEquals(List.of("Mark attempt as missed", "Drop goal…"), labels(goal, service));
            when(goal.getDate()).thenReturn(TODAY.plusDays(1));
            assertEquals(List.of("Drop goal…"), labels(goal, service));
            assertEquals(List.of("Plan retry…", "Drop goal…"),
                    labels(goal(StudyGoal.AttemptOutcome.MISSED, false), service));
            assertEquals(List.of("Reopen…"), labels(goal(StudyGoal.AttemptOutcome.DROPPED, true), service));
            // Dropping a goal with an earlier miss must not hide its way back.
            assertEquals(List.of("Reopen…"), labels(goal(StudyGoal.AttemptOutcome.MISSED, true), service));
        });
    }

    @Test
    void dropDialogSavesTheOptionalReasonAndRefreshesOnlyAfterSuccess() throws Exception {
        onFx(() -> {
            StudyService service = mock(StudyService.class);
            Runnable refresh = mock(Runnable.class);
            StudyGoal goal = goal(StudyGoal.AttemptOutcome.PENDING, false);
            when(service.dropGoal("goal", "The external API is no longer available.")).thenReturn(true);
            Dialog<ButtonType> dialog = GoalActions.dialog(new Label(), GoalActions.Action.DROP,
                    goal, service, TODAY, refresh);
            interact(dialog, pane -> {
                textArea(pane).setText("The external API is no longer available.");
                snapshot(pane, "drop-goal.png");
                button(pane, "Drop goal").fire();
                assertFalse(dialog.isShowing());
            });
            verify(service).dropGoal("goal", "The external API is no longer available.");
            verify(refresh).run();
        });
    }

    @Test
    void failedSaveKeepsTheDialogOpenAndCancelDoesNotRefresh() throws Exception {
        onFx(() -> {
            StudyService service = mock(StudyService.class);
            Runnable refresh = mock(Runnable.class);
            StudyGoal goal = goal(StudyGoal.AttemptOutcome.PENDING, false);
            when(service.dropGoal("goal", "")).thenThrow(new IllegalStateException("Could not save locally"));
            Dialog<ButtonType> dialog = GoalActions.dialog(new Label(), GoalActions.Action.DROP,
                    goal, service, TODAY, refresh);
            interact(dialog, pane -> {
                button(pane, "Drop goal").fire();
                assertTrue(dialog.isShowing());
                assertTrue(nodes(pane).stream().filter(Label.class::isInstance).map(Label.class::cast)
                        .anyMatch(label -> "Could not save locally".equals(label.getText()) && label.isVisible()));
                button(pane, "Cancel").fire();
            });
            verify(refresh, never()).run();
        });
    }

    @Test
    void markingMissedTargetsTheDisplayedAttemptAndReopeningUsesTheChosenDate() throws Exception {
        onFx(() -> {
            StudyService service = mock(StudyService.class);
            when(service.markGoalAttemptMissed("goal", "attempt")).thenReturn(true);
            Dialog<ButtonType> missed = GoalActions.dialog(new Label(), GoalActions.Action.MISS,
                    goal(StudyGoal.AttemptOutcome.PENDING, false), service, TODAY, () -> { });
            interact(missed, pane -> button(pane, "Mark as missed").fire());
            verify(service).markGoalAttemptMissed("goal", "attempt");

            when(service.reopenDroppedGoal("goal", TODAY.plusDays(2))).thenReturn(true);
            Dialog<ButtonType> reopen = GoalActions.dialog(new Label(), GoalActions.Action.REOPEN,
                    goal(StudyGoal.AttemptOutcome.DROPPED, true), service, TODAY, () -> { });
            interact(reopen, pane -> {
                DatePicker date = nodes(pane).stream().filter(DatePicker.class::isInstance)
                        .map(DatePicker.class::cast).findFirst().orElseThrow();
                assertEquals(TODAY, date.getValue());
                date.setValue(TODAY.plusDays(2));
                button(pane, "Reopen goal").fire();
            });
            verify(service).reopenDroppedGoal("goal", TODAY.plusDays(2));
        });
    }

    private static StudyGoal goal(final StudyGoal.AttemptOutcome outcome, final boolean dropped) {
        StudyGoal goal = mock(StudyGoal.class);
        when(goal.getId()).thenReturn("goal");
        when(goal.getAttemptId()).thenReturn("attempt");
        when(goal.getDescription()).thenReturn("Build a working prototype for the research project");
        when(goal.getDate()).thenReturn(TODAY);
        when(goal.getAttemptOutcome()).thenReturn(outcome);
        when(goal.isDropped()).thenReturn(dropped);
        when(goal.getStatus()).thenReturn(dropped ? StudyGoal.GoalStatus.ABANDONED : StudyGoal.GoalStatus.ACTIVE);
        return goal;
    }

    private static List<String> labels(final StudyGoal goal, final StudyService service) {
        return GoalActions.menu(goal, service, TODAY, () -> { }).getItems().stream().map(MenuItem::getText).toList();
    }

    private static void interact(final Dialog<ButtonType> dialog, final Consumer<DialogPane> action) {
        dialog.getDialogPane().getStylesheets().add(GoalActionsTest.class.getResource("/styles.css").toExternalForm());
        FutureTask<Void> interaction = new FutureTask<>(() -> {
            try {
                action.accept(dialog.getDialogPane());
            } finally {
                if (dialog.isShowing()) {
                    dialog.setResult(ButtonType.CANCEL);
                    dialog.close();
                }
            }
            return null;
        });
        Platform.runLater(interaction);
        dialog.showAndWait();
        try {
            interaction.get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private static void onFx(final Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            action.run();
            return null;
        });
        Platform.runLater(task);
        task.get(15, TimeUnit.SECONDS);
    }

    private static Button button(final Node root, final String text) {
        return nodes(root).stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }

    private static TextArea textArea(final Node root) {
        return nodes(root).stream().filter(TextArea.class::isInstance).map(TextArea.class::cast)
                .findFirst().orElseThrow();
    }

    private static List<Node> nodes(final Node root) {
        List<Node> result = new ArrayList<>(List.of(root));
        if (root instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> result.addAll(nodes(child)));
        }
        return result;
    }

    static void snapshot(final Node node, final String name) {
        try {
            WritableImage image = node.snapshot(null, null);
            BufferedImage rendered = new BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < rendered.getHeight(); y++) {
                for (int x = 0; x < rendered.getWidth(); x++) {
                    rendered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                }
            }
            Path directory = Files.createDirectories(Path.of("build", "test-snapshots"));
            ImageIO.write(rendered, "png", directory.resolve(name).toFile());
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
