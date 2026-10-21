package com.studysync.presentation.ui.components;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Shared task-to-goal interaction for the calendar and daily planner. */
final class PlanningRow {

    private PlanningRow() {
    }

    static HBox create(String title, String reason, String color, String hint,
                       DataFormat format, String id, Runnable plan) {
        Label name = new Label(title);
        TaskStyleUtils.fontSemiBold(name, 12);
        name.setWrapText(true);
        name.setMinWidth(0);
        name.setMaxWidth(Double.MAX_VALUE);
        Label detail = new Label(reason);
        TaskStyleUtils.fontNormal(detail, 11);
        TaskStyleUtils.textColor(detail, color);
        detail.setWrapText(true);
        VBox text = new VBox(4, name, detail);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("cal-plan-item");
        row.setFocusTraversable(true);
        row.setAccessibleText(title + ". " + reason + ". " + hint);
        Tooltip.install(row, new Tooltip(title + "\n" + reason + "\n\n" + hint));
        makeDraggable(row, format, id);
        row.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && event.isStillSincePress()) {
                plan.run();
                event.consume();
            }
        });
        row.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                plan.run();
                event.consume();
            }
        });
        return row;
    }

    static void makeDraggable(Node node, DataFormat format, String id) {
        node.getStyleClass().add("cal-draggable");
        node.setOnDragDetected(event -> {
            Dragboard dragboard = node.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.put(format, id);
            dragboard.setContent(content);
            dragboard.setDragView(node.snapshot(null, null));
            event.consume();
        });
    }
}
