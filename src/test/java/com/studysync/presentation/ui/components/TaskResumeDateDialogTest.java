package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.Task;
import com.studysync.domain.service.CategoryService;
import com.studysync.domain.service.StudyService;
import com.studysync.domain.service.TaskService;
import com.studysync.domain.valueobject.TaskPriority;
import com.studysync.domain.valueobject.TaskStatus;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
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
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskResumeDateDialogTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    @BeforeAll
    static void startJavaFx() throws Exception {
        SessionVisibilityTest.startJavaFx();
        Platform.setImplicitExit(false);
    }

    @Test
    void typedDateAndClearingUseTheResumeFieldWithoutReschedulingTheDeadline() throws Exception {
        onFx(() -> {
            TaskService service = mock(TaskService.class);
            Task task = task();
            var panel = panel(service);
            Dialog<ButtonType> dialog = panel.resumeDateDialog(task);
            interact(dialog, pane -> {
                DatePicker picker = nodes(pane, DatePicker.class).getFirst();
                assertEquals(task.getResumeOn(), picker.getValue());
                picker.getEditor().setText(picker.getConverter().toString(TODAY.plusDays(3)));
                snapshot(pane);
                button(pane, "Save").fire();
                assertFalse(dialog.isShowing());
            });
            verify(service).setTaskResumeDate(task, TODAY.plusDays(3));

            interact(panel.resumeDateDialog(task), pane -> {
                button(pane, "Clear date").fire();
                button(pane, "Save").fire();
            });
            verify(service).setTaskResumeDate(task, null);
            assertEquals(TODAY.minusDays(10), task.getDeadline());
        });
    }

    @Test
    void failureStaysOpenAndCancelSavesNothing() throws Exception {
        onFx(() -> {
            TaskService service = mock(TaskService.class);
            Task task = task();
            var panel = panel(service);
            when(service.setTaskResumeDate(task, task.getResumeOn()))
                    .thenThrow(new IllegalStateException("Could not save the resume date"));
            Dialog<ButtonType> dialog = panel.resumeDateDialog(task);
            interact(dialog, pane -> {
                button(pane, "Save").fire();
                assertTrue(dialog.isShowing());
                assertTrue(nodes(pane, Label.class).stream().anyMatch(label -> label.isVisible()
                        && "Could not save the resume date".equals(label.getText())));
                button(pane, "Cancel").fire();
            });
            clearInvocations(service);
            interact(panel.resumeDateDialog(task), pane -> button(pane, "Cancel").fire());
            verifyNoInteractions(service);
        });
    }

    private static Task task() {
        Task task = new Task("task", "Paused task", "", "Study", new TaskPriority(1),
                TODAY.minusDays(10), TaskStatus.POSTPONED, 0);
        task.setResumeOn(TODAY.plusDays(5));
        return task;
    }

    private static TaskManagementPanel panel(final TaskService service) {
        return new TaskManagementPanel(service, mock(CategoryService.class), mock(StudyService.class),
                null, node -> { }, () -> { });
    }

    private static void onFx(final Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            action.run();
            return null;
        });
        Platform.runLater(task);
        task.get(15, TimeUnit.SECONDS);
    }

    private static void interact(final Dialog<ButtonType> dialog, final Consumer<DialogPane> action) {
        dialog.getDialogPane().getStylesheets().add(
                TaskResumeDateDialogTest.class.getResource("/styles.css").toExternalForm());
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

    private static Button button(final Node root, final String text) {
        return nodes(root, Button.class).stream().filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow();
    }

    private static <T extends Node> List<T> nodes(final Node root, final Class<T> type) {
        List<T> result = new ArrayList<>();
        if (type.isInstance(root)) {
            result.add(type.cast(root));
        }
        if (root instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> result.addAll(nodes(child, type)));
        }
        return result;
    }

    private static void snapshot(final Node node) {
        try {
            var image = node.snapshot(null, null);
            var rendered = new BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < rendered.getHeight(); y++) {
                for (int x = 0; x < rendered.getWidth(); x++) {
                    rendered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                }
            }
            Path directory = Files.createDirectories(Path.of("build", "test-snapshots"));
            ImageIO.write(rendered, "png", directory.resolve("task-resume-date.png").toFile());
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
