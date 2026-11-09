package com.studysync.presentation.ui.components;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The calendar day panel's task lines in a sidebar-like column, resized across their layouts. */
class CalendarTaskLineTest {

    private static final String WEEKLY = "Recurring - Every week on Mon, Tue, Wed, Thu, Fri, Sat, Sun";
    private static final double SPACING = 6;

    @BeforeAll
    static void startJavaFx() throws Exception {
        FutureTask<Void> ready = new FutureTask<>(() -> null);
        try {
            Platform.startup(ready);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(ready);
        }
        ready.get(10, TimeUnit.SECONDS);
    }

    private record Line(CalendarViewPanel.DayTaskLine row, Label name, Label status) { }

    @Test
    void statusesMoveUnderTheNameOnlyWhenNeededAndTheColumnNeverOverflowsOrGoesStale() throws Exception {
        FutureTask<Void> check = new FutureTask<>(() -> {
            List<Line> lines = List.of(line("microlab", "65d late"), line("algorithms", WEEKLY),
                    line("i", WEEKLY), line("W".repeat(60), "2d late"));
            VBox column = new VBox(SPACING);
            column.setPadding(new Insets(16));
            lines.forEach(line -> column.getChildren().add(line.row()));
            ScrollPane scroll = new ScrollPane(column);
            scroll.setFitToWidth(true);
            Scene scene = new Scene(new Group(scroll));
            scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

            for (double width : new double[] {420, 275, 180, 420, 180}) {
                scroll.resize(width, 600);
                // One layout pass, as one pulse does: a stale size would show up here.
                scroll.applyCss();
                scroll.layout();
                String at = "at " + width + "px";

                assertTrue(column.getWidth() <= scroll.getViewportBounds().getWidth() + 0.5,
                        "column overflows " + at);
                double y = column.getPadding().getTop();
                for (Line line : lines) {
                    CalendarViewPanel.DayTaskLine row = line.row();
                    assertEquals(y, row.getLayoutY(), 0.5, line.name().getText() + " position " + at);
                    assertEquals(row.prefHeight(row.getWidth()), row.getHeight(), 0.5,
                            line.name().getText() + " height " + at);
                    Label name = line.name();
                    Label status = line.status();
                    assertTrue(status.getLayoutX() + status.getWidth() <= row.getWidth() + 0.5,
                            name.getText() + " status outside its line " + at + ": x=" + status.getLayoutX()
                                    + " w=" + status.getWidth() + " line=" + row.getWidth() + " insets=" + row.getInsets());
                    assertTrue(row.statusBesideAt(row.getWidth())
                                    ? name.getLayoutX() + name.getWidth() <= status.getLayoutX() + 0.5
                                    : name.getLayoutY() + name.getHeight() <= status.getLayoutY() + 0.5,
                            name.getText() + " name and status overlap " + at);
                    assertTrue(!line.name().isWrapText() || wordsFit(line.name()),
                            line.name().getText() + " breaks mid-word " + at);
                    y += row.getHeight() + SPACING;
                }
            }

            // At 180px a short status still sits beside the name; a full-week schedule does not,
            // even for a one-letter name, and an over-long word is cut short instead of split.
            assertTrue(beside(lines.get(0)));
            assertFalse(beside(lines.get(1)));
            assertFalse(beside(lines.get(2)));
            assertFalse(lines.get(3).name().isWrapText());
            scroll.resize(420, 600);
            scroll.applyCss();
            scroll.layout();
            assertTrue(beside(lines.get(1)), "a moderately long schedule fits beside \"algorithms\" at 420px");
            assertTrue(lines.get(1).name().getText().length() > 1 && lines.get(1).name().isWrapText());
            return null;
        });
        Platform.runLater(check);
        check.get(10, TimeUnit.SECONDS);
    }

    private static Line line(String title, String statusText) {
        Label icon = TaskStyleUtils.iconLabel("↺", 12);
        Label name = new Label(title);
        TaskStyleUtils.fontSemiBold(name, 12);
        Label status = new Label(statusText);
        TaskStyleUtils.fontNormal(status, 11);
        return new Line(new CalendarViewPanel.DayTaskLine(icon, name, status), name, status);
    }

    private static boolean beside(Line line) {
        return line.row().statusBesideAt(line.row().getWidth());
    }

    private static boolean wordsFit(Label label) {
        Text probe = new Text();
        probe.setFont(label.getFont());
        for (String word : label.getText().split("\\s+")) {
            probe.setText(word);
            if (probe.getLayoutBounds().getWidth() > label.getWidth() + 0.5) {
                return false;
            }
        }
        return true;
    }
}
