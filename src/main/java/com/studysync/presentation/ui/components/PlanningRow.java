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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Shared task-to-goal interaction for the calendar and daily planner. */
final class PlanningRow {

    /** Node property holding the item's planning key, so a closed dialog can return focus to it. */
    static final String KEY = "studysync.planning-key";

    private PlanningRow() {
    }

    static HBox create(String title, String reason, String color, String hint,
                       DataFormat format, String id, Runnable plan) {
        Label name = new Label(title);
        TaskStyleUtils.fontSemiBold(name, 12);
        name.setWrapText(true);
        name.setMinWidth(0);
        name.setMaxWidth(Double.MAX_VALUE);
        VBox text = new VBox(4, name, reasonLabel(reason, color));
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(text);
        row.setAlignment(Pos.CENTER_LEFT);
        install(row, title, reason, hint, format, id, plan);
        return row;
    }

    /**
     * The same item as a fixed-size tile, for a grid: the title takes up to three
     * lines and the reason sits at the bottom, so tiles in a row line up.
     */
    static VBox tile(String title, String reason, String color, String hint,
                     DataFormat format, String id, Runnable plan) {
        Label name = new Label(title);
        TaskStyleUtils.fontSemiBold(name, 12);
        name.setWrapText(true);
        name.setMinHeight(0);
        name.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(name, Priority.ALWAYS);
        name.setAlignment(Pos.TOP_LEFT);
        Label detail = reasonLabel(reason, color);
        detail.setMinHeight(Region.USE_PREF_SIZE);
        VBox tile = new VBox(6, name, detail);
        tile.getStyleClass().add("plan-tile");
        install(tile, title, reason, hint, format, id, plan);
        return tile;
    }

    private static Label reasonLabel(String reason, String color) {
        Label detail = new Label(reason);
        TaskStyleUtils.fontNormal(detail, 11);
        TaskStyleUtils.textColor(detail, color);
        detail.setWrapText(true);
        return detail;
    }

    private static void install(Region item, String title, String reason, String hint,
                                DataFormat format, String id, Runnable plan) {
        item.getStyleClass().add("cal-plan-item");
        item.getProperties().put(KEY, id);
        item.setFocusTraversable(true);
        item.setAccessibleText(title + ". " + reason + ". " + hint);
        Tooltip.install(item, new Tooltip(title + "\n" + reason + "\n\n" + hint));
        if (format != null) {
            makeDraggable(item, format, id);
        }
        item.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && event.isStillSincePress()) {
                plan.run();
                event.consume();
            }
        });
        item.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                plan.run();
                event.consume();
            }
        });
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
