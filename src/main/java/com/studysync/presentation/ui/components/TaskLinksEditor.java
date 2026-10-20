package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.TaskLink;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/** Form rows stay local until the entire task form is saved. */
final class TaskLinksEditor extends VBox {
    private final VBox rows = new VBox(6);
    private final Consumer<String> openUrl;
    private final Consumer<Exception> showError;

    TaskLinksEditor(List<TaskLink> links, Consumer<String> openUrl, Consumer<Exception> showError) {
        super(6);
        this.openUrl = openUrl;
        this.showError = showError;
        links.forEach(this::addRow);
        Button add = new Button("Add link");
        add.getStyleClass().add("btn-small");
        add.setOnAction(event -> addRow(new TaskLink("", "")));
        getChildren().addAll(rows, add);
    }

    List<TaskLink> links() {
        return rows.getChildren().stream().map(node -> ((LinkRow) node).link()).toList();
    }

    private void addRow(TaskLink link) {
        rows.getChildren().add(new LinkRow(link));
    }

    private final class LinkRow extends VBox {
        private final TextField title;
        private final TextField url;

        LinkRow(TaskLink link) {
            super(4);
            title = new TextField(link.title());
            title.setPromptText("Title (optional)");
            url = new TextField(link.url());
            url.setPromptText("https://… (required)");
            HBox.setHgrow(url, Priority.ALWAYS);
            Button open = new Button("Open");
            open.setOnAction(event -> {
                try {
                    openUrl.accept(url.getText());
                } catch (Exception ex) {
                    showError.accept(ex);
                }
            });
            Button up = new Button("↑");
            up.setAccessibleText("Move link up");
            up.setOnAction(event -> move(-1));
            Button down = new Button("↓");
            down.setAccessibleText("Move link down");
            down.setOnAction(event -> move(1));
            Button remove = new Button("Remove");
            remove.setOnAction(event -> rows.getChildren().remove(this));
            for (Button button : List.of(open, up, down, remove)) button.getStyleClass().add("btn-small");
            getChildren().addAll(title, new HBox(4, url, open, up, down, remove));
        }

        private TaskLink link() {
            return new TaskLink(title.getText(), url.getText());
        }

        private void move(int offset) {
            int index = rows.getChildren().indexOf(this);
            int target = index + offset;
            if (target < 0 || target >= rows.getChildren().size()) return;
            rows.getChildren().remove(index);
            rows.getChildren().add(target, this);
        }
    }
}
