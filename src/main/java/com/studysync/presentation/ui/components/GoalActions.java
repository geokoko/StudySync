package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.service.StudyService;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

/** Shared goal lifecycle actions in the planner, calendar and task history. */
final class GoalActions {
    enum Action {
        MISS("Mark attempt as missed", "Mark this attempt as missed?", "Mark as missed",
                "This attempt will count as missed. The goal stays available to retry."),
        DROP("Drop goal…", "Drop this goal?", "Drop goal",
                "Stop planning this goal. No new failure penalty or completion credit is added. "
                        + "Earlier attempts, checklist ticks and study time are kept. You can reopen it later."),
        REOPEN("Reopen…", "Reopen this goal", "Reopen goal",
                "Plan a new attempt. Earlier attempts and checklist ticks will be kept."),
        RETRY("Plan retry…", "Plan another attempt", "Plan retry",
                "Choose a date for another attempt. Earlier attempts and checklist ticks will be kept.");

        final String menuLabel;
        final String title;
        final String buttonLabel;
        final String explanation;

        Action(final String menuLabel, final String title, final String buttonLabel, final String explanation) {
            this.menuLabel = menuLabel;
            this.title = title;
            this.buttonLabel = buttonLabel;
            this.explanation = explanation;
        }
    }

    private GoalActions() {
    }

    static MenuButton menu(final StudyGoal goal, final StudyService service, final LocalDate today,
                           final Runnable refresh) {
        MenuButton menu = new MenuButton("More");
        menu.getStyleClass().addAll("btn-gray", "btn-small");
        menu.setAccessibleText("More actions for goal: " + goal.getDescription());
        if (goal.isDropped()) {
            add(menu, Action.REOPEN, goal, service, today, refresh);
        } else if (goal.getStatus() == StudyGoal.GoalStatus.ACTIVE) {
            if (goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.PENDING && !goal.getDate().isAfter(today)) {
                add(menu, Action.MISS, goal, service, today, refresh);
            } else if (goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.MISSED) {
                add(menu, Action.RETRY, goal, service, today, refresh);
            }
            add(menu, Action.DROP, goal, service, today, refresh);
        }
        menu.setVisible(!menu.getItems().isEmpty());
        menu.setManaged(menu.isVisible());
        return menu;
    }

    private static void add(final MenuButton menu, final Action action, final StudyGoal goal,
                            final StudyService service, final LocalDate today, final Runnable refresh) {
        MenuItem item = new MenuItem(action.menuLabel);
        item.setOnAction(event -> dialog(menu, action, goal, service, today, refresh).showAndWait());
        menu.getItems().add(item);
    }

    /** Package-visible so the same real dialog can be exercised without opening a user database. */
    static Dialog<ButtonType> dialog(final Node owner, final Action action, final StudyGoal goal,
                                     final StudyService service, final LocalDate today, final Runnable refresh) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(action.title);
        if (owner.getScene() != null) {
            dialog.initOwner(owner.getScene().getWindow());
        }
        dialog.setHeaderText(action.title);
        Label description = new Label(goal.getDescription());
        description.setWrapText(true);
        TaskStyleUtils.fontSemiBold(description, 13);
        Label explanation = new Label(action.explanation);
        explanation.setWrapText(true);
        // A wrapped label may otherwise shrink to one ellipsized line when the dialog has more content.
        description.setMinHeight(Region.USE_PREF_SIZE);
        explanation.setMinHeight(Region.USE_PREF_SIZE);
        VBox content = new VBox(12, description, explanation);
        content.setPrefWidth(400);

        TextArea reason = new TextArea();
        reason.setWrapText(true);
        reason.setPrefRowCount(3);
        reason.setPromptText("Not achievable, no longer relevant, or another reason…");
        if (action == Action.DROP) {
            content.getChildren().addAll(new Label("Reason (optional)"), reason);
        }
        DatePicker date = new DatePicker(today);
        date.setMaxWidth(Double.MAX_VALUE);
        date.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(final LocalDate value, final boolean empty) {
                super.updateItem(value, empty);
                setDisable(empty || value == null || value.isBefore(today));
            }
        });
        boolean plansAttempt = action == Action.REOPEN || action == Action.RETRY;
        ChecklistChoice checklist = plansAttempt ? ChecklistChoice.forGoal(goal, date, today) : null;
        if (plansAttempt) {
            content.getChildren().addAll(new Label("Planned date"), date);
        }
        if (checklist != null) {
            // The choice below decides what happens to the ticks, so do not promise to keep them.
            explanation.setText(action == Action.RETRY
                    ? "Choose a date for another attempt. Earlier attempts will be kept."
                    : "Plan a new attempt. Earlier attempts will be kept.");
            content.getChildren().add(checklist.view);
        }
        Label error = new Label();
        error.setWrapText(true);
        error.setVisible(false);
        error.setManaged(false);
        TaskStyleUtils.textColor(error, TaskStyleUtils.COLOR_DANGER);
        content.getChildren().add(error);
        dialog.getDialogPane().setContent(content);
        ButtonType apply = new ButtonType(action.buttonLabel, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, apply);
        dialog.getDialogPane().lookupButton(ButtonType.CANCEL).getStyleClass().add("btn-cancel");
        dialog.getDialogPane().lookupButton(apply).getStyleClass()
                .add(action == Action.DROP ? "btn-gray" : "btn-primary");
        dialog.getDialogPane().lookupButton(apply).addEventFilter(ActionEvent.ACTION, event -> {
            try {
                if (action == Action.REOPEN || action == Action.RETRY) {
                    date.commitValue();
                }
                boolean changed = switch (action) {
                    case MISS -> service.markGoalAttemptMissed(goal.getId(), goal.getAttemptId());
                    case DROP -> service.dropGoal(goal.getId(), reason.getText());
                    case REOPEN -> service.reopenDroppedGoal(goal.getId(), date.getValue(), checklistValue(checklist));
                    case RETRY -> service.planGoalAttempt(goal.getId(), date.getValue(), checklistValue(checklist));
                };
                if (!changed) {
                    throw new IllegalStateException(
                            "This goal has changed. Close this dialog and refresh before trying again.");
                }
            } catch (Exception ex) {
                error.setText(ex.getMessage() == null
                        ? "Could not update this goal. Please try again." : ex.getMessage());
                error.setVisible(true);
                error.setManaged(true);
                event.consume();
            }
        });
        dialog.setOnHidden(event -> {
            if (dialog.getResult() == apply) {
                refresh.run();
            }
        });
        return dialog;
    }

    private static StudyService.CompletedChecklist checklistValue(final ChecklistChoice checklist) {
        return checklist == null ? null : checklist.value();
    }

    /**
     * What a retry or reopen does when every checklist line is already ticked, asked wherever an
     * attempt is planned with a date. Marking the goal done plans the attempt for today, so the
     * date is fixed to today while that option is selected.
     */
    static final class ChecklistChoice {
        final VBox view;
        private final RadioButton markDone = new RadioButton("Mark the goal done today");

        private ChecklistChoice(final DatePicker date, final LocalDate today) {
            RadioButton startOver = new RadioButton("Start the checklist over");
            ToggleGroup group = new ToggleGroup();
            startOver.setToggleGroup(group);
            markDone.setToggleGroup(group);
            startOver.setSelected(true);
            Label prompt = new Label("Every checklist item is already ticked.");
            prompt.setWrapText(true);
            prompt.setMinHeight(Region.USE_PREF_SIZE);
            TaskStyleUtils.fontSemiBold(prompt, 12);
            markDone.selectedProperty().addListener((observable, wasSelected, selected) -> {
                if (selected) {
                    date.setValue(today);
                }
                date.setDisable(selected);
            });
            view = new VBox(6, prompt, startOver, markDone);
        }

        /** The choice for a goal whose checklist is complete, or null when it has open lines. */
        static ChecklistChoice forGoal(final StudyGoal goal, final DatePicker date, final LocalDate today) {
            return goal.isChecklistComplete() ? new ChecklistChoice(date, today) : null;
        }

        StudyService.CompletedChecklist value() {
            return markDone.isSelected() ? StudyService.CompletedChecklist.MARK_DONE
                    : StudyService.CompletedChecklist.START_OVER;
        }
    }
}
