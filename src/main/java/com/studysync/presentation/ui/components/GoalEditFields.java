package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.service.StudyService;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.time.LocalDate;
import java.util.stream.Collectors;

/**
 * The fields every goal form shares: description, the "done when" checklist
 * and the planned date. Build one, drop {@link #view()} into any dialog, then
 * call {@link #save} or {@link #create} from its confirm button.
 */
final class GoalEditFields {

    private final TextArea description;
    private final TextArea criteria;
    private final DatePicker date;
    private final boolean canEditDate;
    private final Label error = new Label();
    private final VBox view;

    /**
     * @param goal        the goal to edit, or {@code null} to plan a new one
     * @param defaultDate planned date offered for a new goal
     */
    GoalEditFields(StudyGoal goal, LocalDate defaultDate) {
        boolean isNew = goal == null;

        description = new TextArea(isNew || goal.getDescription() == null ? "" : goal.getDescription());
        description.setPromptText("Goal description");
        description.setPrefRowCount(3);
        description.setWrapText(true);

        criteria = TaskStyleUtils.criteriaArea(isNew ? null : goal.getCriteria().stream()
                .map(StudyGoal.Criterion::text).collect(Collectors.joining("\n")));

        // Only a pending attempt has a date worth moving; achieved and missed ones are history.
        canEditDate = isNew || goal.getAttemptOutcome() == StudyGoal.AttemptOutcome.PENDING;
        date = new DatePicker(isNew ? defaultDate : goal.getDate());
        date.setMaxWidth(Double.MAX_VALUE);
        date.setDisable(!canEditDate);

        Label dateHint = new Label(canEditDate
                ? "Choose when this attempt should appear in the planner."
                : "Only pending attempts can be rescheduled. Use Plan to create a new dated attempt.");
        TaskStyleUtils.fontNormal(dateHint, 11);
        dateHint.setTextFill(Color.web(TaskStyleUtils.COLOR_MUTED));
        dateHint.setWrapText(true);

        error.setGraphic(TaskStyleUtils.iconLabel("⚠", 12));
        error.setTextFill(Color.web(TaskStyleUtils.COLOR_DANGER));
        error.setWrapText(true);
        error.setVisible(false);
        error.setManaged(false);

        view = new VBox(12,
                new Label("Description:"), description,
                new Label(TaskStyleUtils.CRITERIA_LABEL), criteria,
                new Label("Planned date:"), date, dateHint,
                error);
    }

    VBox view() {
        return view;
    }

    /** Shows a validation message under the fields (the dialog stays open). */
    void showError(String message) {
        error.setText(message);
        error.setVisible(true);
        error.setManaged(true);
    }

    /** Applies the fields to an existing goal; throws with a user-facing message when invalid. */
    void save(StudyService studyService, StudyGoal goal) {
        studyService.updateStudyGoalDetails(goal.getId(), validDescription(),
                canEditDate ? validDate() : null, criteria.getText());
    }

    /** Creates a new goal from the fields, optionally linked to a task. */
    void create(StudyService studyService, String taskId) {
        studyService.addStudyGoal(validDescription(), validDate(), taskId, criteria.getText());
    }

    private String validDescription() {
        String text = description.getText().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Description is required.");
        }
        return text;
    }

    private LocalDate validDate() {
        if (date.getValue() == null) {
            throw new IllegalArgumentException("Planned date is required.");
        }
        return date.getValue();
    }
}
