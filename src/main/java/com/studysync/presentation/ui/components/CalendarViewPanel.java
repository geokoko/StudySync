package com.studysync.presentation.ui.components;

import com.studysync.domain.service.StudyService;
import com.studysync.domain.service.TaskService;
import com.studysync.domain.service.ScoreBreakdown;
import com.studysync.domain.service.ScoringService;
import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.StudySession;
import com.studysync.domain.entity.Task;
import com.studysync.domain.entity.DailyReflection;
import com.studysync.domain.entity.OffDay;
import com.studysync.presentation.ui.components.CalendarAgenda.Entry;
import com.studysync.presentation.ui.components.CalendarAgenda.Kind;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.event.ActionEvent;
import javafx.scene.Node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Month or week calendar that names what is planned on each day and, for past
 * days, what actually happened. A side panel shows the selected day, and the
 * active tasks that have no goal planned yet: dragging one onto a day plans
 * it, and dragging a planned goal to another day moves it.
 */
public class CalendarViewPanel extends BorderPane implements RefreshablePanel {
    private static final Logger logger = LoggerFactory.getLogger(CalendarViewPanel.class);

    private enum ViewMode { MONTH, WEEK }

    private final StudyService studyService;
    private final TaskService taskService;
    private final ScoringService scoringService;

    // UI Components
    private final Label periodLabel = new Label();
    private final GridPane calendarGrid = new GridPane();
    private final VBox dayPanel = new VBox(16);
    private final VBox toPlanList = new VBox(6);
    private final TabPane sidebar = new TabPane();
    private final Tab dayTab = new Tab("Day");
    private final Tab toPlanTab = new Tab("To plan");

    private ViewMode viewMode = ViewMode.MONTH;
    /** Any day inside the visible month or week. */
    private LocalDate anchor;
    private LocalDate selectedDate;

    // What the visible period was drawn from, kept so selecting a day does
    // not re-query the whole month.
    private final Map<LocalDate, DayData> visibleDays = new LinkedHashMap<>();
    private final Map<LocalDate, VBox> cells = new HashMap<>();
    private final Map<String, StudyGoal> movableGoals = new HashMap<>();
    private final Map<String, Task> tasksById = new LinkedHashMap<>();

    // Calendar layout constants
    private static final int MONTH_ENTRY_LIMIT = 4;
    private static final double MONTH_CELL_MIN_HEIGHT = 112;
    private static final double SIDEBAR_WIDTH = 360;

    // Off day (holiday) styling
    private static final String OFF_DAY_COLOR = "#d68910";
    private static final String OFF_DAY_BG = "#fdf0d5";
    private static final String OFF_DAY_ICON = "⚑";

    private static final PseudoClass TODAY = PseudoClass.getPseudoClass("today");
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass PAST = PseudoClass.getPseudoClass("past");
    private static final PseudoClass OUTSIDE = PseudoClass.getPseudoClass("outside");
    private static final PseudoClass OFF = PseudoClass.getPseudoClass("off");
    private static final PseudoClass DROP = PseudoClass.getPseudoClass("drop");

    private static final DataFormat GOAL_DRAG = dataFormat("application/x-studysync-goal");
    private static final DataFormat TASK_DRAG = dataFormat("application/x-studysync-task");

    private static final DateTimeFormatter DAY_TITLE = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");

    public CalendarViewPanel(StudyService studyService, TaskService taskService,
                             ScoringService scoringService) {
        this.studyService = studyService;
        this.taskService = taskService;
        this.scoringService = scoringService;
        this.anchor = LocalDate.now();
        this.selectedDate = anchor;

        getStyleClass().add("panel-bg-alt");

        Node calendarCard = createCalendarCard();
        VBox main = new VBox(12, createToolbar(), calendarCard, createLegend());
        main.setPadding(new Insets(16));
        main.setMinWidth(480);
        VBox.setVgrow(calendarCard, Priority.ALWAYS);

        Node side = createSidebar();
        SplitPane split = new SplitPane(main, side);
        split.getStyleClass().add("transparent-bg");
        split.setDividerPositions(0.74);
        // Window resizes go to the calendar; the side panel keeps its width.
        SplitPane.setResizableWithParent(side, false);
        setCenter(split);

        updateDisplay();
    }

    private static DataFormat dataFormat(String mimeType) {
        DataFormat existing = DataFormat.lookupMimeType(mimeType);
        return existing != null ? existing : new DataFormat(mimeType);
    }

    // ================================================================
    // LAYOUT
    // ================================================================

    private HBox createToolbar() {
        Button prevBtn = new Button();
        prevBtn.setGraphic(TaskStyleUtils.iconLabel("◀", 12));
        prevBtn.getStyleClass().addAll("btn-primary", "btn-small");
        prevBtn.setTooltip(new Tooltip("Previous"));
        prevBtn.setOnAction(e -> shift(-1));

        Button todayBtn = new Button("Today");
        todayBtn.getStyleClass().addAll("btn-success", "btn-small");
        todayBtn.setOnAction(e -> {
            anchor = LocalDate.now();
            selectedDate = anchor;
            refresh();
        });

        Button nextBtn = new Button();
        nextBtn.setGraphic(TaskStyleUtils.iconLabel("▶", 12));
        nextBtn.getStyleClass().addAll("btn-primary", "btn-small");
        nextBtn.setTooltip(new Tooltip("Next"));
        nextBtn.setOnAction(e -> shift(1));

        TaskStyleUtils.fontBold(periodLabel, 22);
        HBox.setMargin(periodLabel, new Insets(0, 0, 0, 8));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ToggleButton monthBtn = new ToggleButton("Month");
        ToggleButton weekBtn = new ToggleButton("Week");
        ToggleGroup modeGroup = new ToggleGroup();
        monthBtn.setToggleGroup(modeGroup);
        weekBtn.setToggleGroup(modeGroup);
        monthBtn.setSelected(true);
        monthBtn.getStyleClass().addAll("planner-view-toggle", "btn-small");
        weekBtn.getStyleClass().addAll("planner-view-toggle", "btn-small");
        monthBtn.setOnAction(e -> setViewMode(ViewMode.MONTH, monthBtn));
        weekBtn.setOnAction(e -> setViewMode(ViewMode.WEEK, weekBtn));

        HBox toolbar = new HBox(6, prevBtn, todayBtn, nextBtn, periodLabel, spacer, monthBtn, weekBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        return toolbar;
    }

    private Node createCalendarCard() {
        calendarGrid.setHgap(4);
        calendarGrid.setVgap(4);

        // Fit to the viewport while it is big enough, scroll once the cells hit their minimum.
        ScrollPane scroll = new ScrollPane(calendarGrid);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.getStyleClass().add("transparent-bg");

        VBox card = new VBox(scroll);
        card.getStyleClass().add("section-card-flat");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return card;
    }

    private Node createLegend() {
        FlowPane legend = new FlowPane(16, 4);
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.getChildren().addAll(
                legendItem(entryIcon(Kind.GOAL_PENDING), TaskStyleUtils.COLOR_PURPLE, "Planned goal"),
                legendItem(entryIcon(Kind.GOAL_ACHIEVED), TaskStyleUtils.COLOR_SUCCESS, "Achieved"),
                legendItem(entryIcon(Kind.GOAL_MISSED), TaskStyleUtils.MISSED_COLOR, "Missed"),
                legendItem(entryIcon(Kind.DUE), TaskStyleUtils.DUE_TODAY_COLOR, "Deadline"),
                legendItem(entryIcon(Kind.RECURRING), TaskStyleUtils.COLOR_PRIMARY, "Recurring"),
                legendItem(entryIcon(Kind.OVERDUE), TaskStyleUtils.OVERDUE_COLOR, "Overdue (on today)"),
                legendItem(entryIcon(Kind.COMPLETED), TaskStyleUtils.COLOR_SUCCESS, "Task completed"),
                legendItem(OFF_DAY_ICON, OFF_DAY_COLOR, "Off day"),
                legendItem("▬", TaskStyleUtils.COLOR_MUTED,
                        "Study time vs. " + ScoringService.DAILY_MINUTES_TARGET / 60 + "h target"));

        Label dragHint = new Label("Drag a planned goal to move it, or a task from \"To plan\" onto a day to plan it.");
        TaskStyleUtils.fontItalic(dragHint, 11);
        TaskStyleUtils.textColor(dragHint, TaskStyleUtils.COLOR_MUTED);
        legend.getChildren().add(dragHint);
        return legend;
    }

    private HBox legendItem(String icon, String color, String text) {
        Label iconLabel = TaskStyleUtils.iconLabel(icon, 12);
        iconLabel.setMinWidth(Region.USE_PREF_SIZE);
        TaskStyleUtils.textColor(iconLabel, color);
        Label textLabel = new Label(text);
        TaskStyleUtils.fontNormal(textLabel, 11);
        TaskStyleUtils.textColor(textLabel, TaskStyleUtils.COLOR_MUTED);
        HBox item = new HBox(4, iconLabel, textLabel);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    private Node createSidebar() {
        dayPanel.setPadding(new Insets(16));
        ScrollPane dayScroll = new ScrollPane(dayPanel);
        dayScroll.setFitToWidth(true);
        dayScroll.getStyleClass().add("transparent-bg");
        dayTab.setContent(dayScroll);

        Label hint = new Label("Active tasks with no goal planned yet. Drag one onto a day to plan it, "
                + "or double-click it to plan it for the selected day.");
        wrap(hint);
        TaskStyleUtils.fontNormal(hint, 11);
        TaskStyleUtils.textColor(hint, TaskStyleUtils.COLOR_MUTED);
        ScrollPane listScroll = new ScrollPane(toPlanList);
        listScroll.setFitToWidth(true);
        listScroll.getStyleClass().add("transparent-bg");
        VBox toPlanBox = new VBox(12, hint, listScroll);
        toPlanBox.setPadding(new Insets(16));
        VBox.setVgrow(listScroll, Priority.ALWAYS);
        toPlanTab.setContent(toPlanBox);

        sidebar.getTabs().addAll(dayTab, toPlanTab);
        sidebar.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        sidebar.getStyleClass().add("cal-sidebar");
        sidebar.setMinWidth(280);
        sidebar.setPrefWidth(SIDEBAR_WIDTH);
        return sidebar;
    }

    private void shift(int periods) {
        anchor = viewMode == ViewMode.MONTH ? anchor.plusMonths(periods) : anchor.plusWeeks(periods);
        refresh();
    }

    private void setViewMode(ViewMode mode, ToggleButton button) {
        if (!button.isSelected()) {
            button.setSelected(true); // a toggle group would otherwise allow no mode at all
        }
        if (viewMode != mode) {
            viewMode = mode;
            // Open the week of the selected day rather than wherever the month started.
            anchor = selectedDate;
            refresh();
        }
    }

    // ================================================================
    // RENDERING
    // ================================================================

    private void refresh() {
        LocalDate today = LocalDate.now();
        LocalDate from;
        LocalDate to;
        if (viewMode == ViewMode.MONTH) {
            YearMonth month = YearMonth.from(anchor);
            from = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            to = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            periodLabel.setText(month.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault())
                    + " " + month.getYear());
        } else {
            from = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            to = from.plusDays(6);
            periodLabel.setText(formatWeek(from, to));
        }

        loadDays(from, to, today);
        renderGrid(from, to, today);
        showDay(selectedDate);
        renderToPlan(today);
    }

    private void loadDays(LocalDate from, LocalDate to, LocalDate today) {
        visibleDays.clear();
        movableGoals.clear();
        tasksById.clear();
        try {
            List<Task> tasks = taskService.getTasks();
            tasks.forEach(task -> tasksById.put(task.getId(), task));
            Map<LocalDate, String> offDays = studyService.getOffDays(from, to);
            Map<LocalDate, List<Entry>> agenda = CalendarAgenda.build(from, to, today, tasks,
                    studyService.getGoalsInRange(from, to), taskService::isRecurringOccurrence);

            agenda.forEach((date, entries) -> {
                visibleDays.put(date, new DayData(date, scoreFor(date, today), entries, offDays.get(date)));
                entries.stream().filter(Entry::movable).forEach(e -> movableGoals.put(e.goal().getId(), e.goal()));
            });
        } catch (Exception e) {
            logger.warn("Unable to load calendar {}..{}", from, to, e);
        }
    }

    /** A single day outside the visible period, e.g. a selection kept while paging. */
    private DayData loadDay(LocalDate date, LocalDate today) {
        try {
            List<Entry> entries = CalendarAgenda.build(date, date, today, taskService.getTasks(),
                    studyService.getGoalsForDate(date), taskService::isRecurringOccurrence).get(date);
            return new DayData(date, scoreFor(date, today), entries, studyService.getOffDayLabel(date).orElse(null));
        } catch (Exception e) {
            logger.warn("Unable to load calendar day {}", date, e);
            return DayData.empty(date);
        }
    }

    // Deliberately one score query per past day in view (up to 42) - the same
    // order of magnitude as before, against an embedded database. If paging
    // ever drags, the fix is a ScoringService.scoreForEachDate(range) that
    // batches the way getGoalsInRange does. Future days have nothing to score.
    private ScoreBreakdown scoreFor(LocalDate date, LocalDate today) {
        if (date.isAfter(today)) {
            return ScoreBreakdown.empty(1);
        }
        try {
            return scoringService.scoreForDate(date);
        } catch (Exception e) {
            logger.warn("Unable to score {}", date, e);
            return ScoreBreakdown.empty(1);
        }
    }

    private void renderGrid(LocalDate from, LocalDate to, LocalDate today) {
        calendarGrid.getChildren().clear();
        calendarGrid.getColumnConstraints().clear();
        calendarGrid.getRowConstraints().clear();
        cells.clear();

        for (int col = 0; col < 7; col++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / 7);
            column.setHgrow(Priority.ALWAYS);
            calendarGrid.getColumnConstraints().add(column);

            Label dayName = new Label(DayOfWeek.of(col + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault()));
            dayName.getStyleClass().add("cal-weekday");
            dayName.setMaxWidth(Double.MAX_VALUE);
            calendarGrid.add(dayName, col, 0);
        }
        calendarGrid.getRowConstraints().add(new RowConstraints());

        long weeks = ChronoUnit.WEEKS.between(from, to.plusDays(1));
        for (int row = 0; row < weeks; row++) {
            RowConstraints constraints = new RowConstraints();
            constraints.setVgrow(Priority.ALWAYS);
            // A week lists every entry, so its row is never shorter than its fullest day
            // (the card scrolls instead); a month shows a few per day and an overflow count.
            constraints.setMinHeight(viewMode == ViewMode.MONTH ? MONTH_CELL_MIN_HEIGHT : Region.USE_PREF_SIZE);
            calendarGrid.getRowConstraints().add(constraints);
        }

        YearMonth month = YearMonth.from(anchor);
        int index = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1), index++) {
            DayData day = visibleDays.getOrDefault(date, DayData.empty(date));
            boolean outside = viewMode == ViewMode.MONTH && !YearMonth.from(date).equals(month);
            VBox cell = createDayCell(day, today, outside);
            cells.put(date, cell);
            calendarGrid.add(cell, index % 7, 1 + index / 7);
        }
    }

    private VBox createDayCell(DayData day, LocalDate today, boolean outsideMonth) {
        LocalDate date = day.date();
        boolean week = viewMode == ViewMode.WEEK;

        VBox cell = new VBox(3);
        cell.getStyleClass().add("cal-cell");
        cell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        cell.pseudoClassStateChanged(TODAY, date.equals(today));
        cell.pseudoClassStateChanged(PAST, date.isBefore(today));
        cell.pseudoClassStateChanged(OUTSIDE, outsideMonth);
        cell.pseudoClassStateChanged(OFF, day.offDayLabel() != null);
        cell.pseudoClassStateChanged(SELECTED, date.equals(selectedDate));

        // The weekday row above already names the day, and a narrow week column has no room for it twice.
        Label number = new Label(String.valueOf(date.getDayOfMonth()));
        number.getStyleClass().add("cal-day-number");
        number.setMinWidth(Region.USE_PREF_SIZE);
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(4, number, headerSpacer);
        header.setAlignment(Pos.CENTER_LEFT);
        if (day.score().minutes() > 0) {
            Label minutes = new Label(formatStudyTime(day.score().minutes()));
            minutes.getStyleClass().add("cal-minutes");
            minutes.setMinWidth(Region.USE_PREF_SIZE);
            header.getChildren().add(minutes);
        }
        cell.getChildren().add(header);

        if (day.offDayLabel() != null) {
            Label offDay = new Label(OFF_DAY_ICON + " " + day.offDayLabel());
            offDay.getStyleClass().add("cal-off-label");
            offDay.setMinWidth(0);
            offDay.setTooltip(new Tooltip("Off day - not counted towards global scores"));
            cell.getChildren().add(offDay);
        }

        List<Entry> entries = day.entries();
        int limit = week ? entries.size() : MONTH_ENTRY_LIMIT;
        // "+1 more" would take the same line as the entry it hides.
        if (entries.size() == limit + 1) {
            limit++;
        }
        entries.stream().limit(limit).forEach(entry -> cell.getChildren().add(entryRow(entry, date, today)));
        if (entries.size() > limit) {
            Label more = new Label("+" + (entries.size() - limit) + " more");
            more.getStyleClass().add("cal-more");
            cell.getChildren().add(more);
        }

        Region filler = new Region();
        VBox.setVgrow(filler, Priority.ALWAYS);
        cell.getChildren().add(filler);
        if (day.score().minutes() > 0) {
            cell.getChildren().add(activityBar(day.score()));
        }

        cell.setOnMouseClicked(e -> {
            if (!e.isStillSincePress()) {
                return;
            }
            select(date);
            if (e.getClickCount() == 2 && !date.isBefore(today)) {
                showAddGoalDialog(date, null);
            }
        });
        installDropTarget(cell, date, today);
        return cell;
    }

    private Node entryRow(Entry entry, LocalDate date, LocalDate today) {
        boolean week = viewMode == ViewMode.WEEK;
        Label icon = TaskStyleUtils.iconLabel(entryIcon(entry.kind()), week ? 12 : 10);
        icon.setMinWidth(Region.USE_PREF_SIZE);
        TaskStyleUtils.textColor(icon, entryColor(entry.kind(), date, today));

        Label text = new Label(entry.text());
        TaskStyleUtils.fontNormal(text, week ? 12 : 11);
        switch (entry.kind()) {
            case OVERDUE -> TaskStyleUtils.textColor(text, TaskStyleUtils.OVERDUE_COLOR);
            case GOAL_ACHIEVED, GOAL_MISSED, COMPLETED, RECURRING_MISSED ->
                    TaskStyleUtils.textColor(text, TaskStyleUtils.COLOR_MUTED);
            default -> { }
        }
        if (week) {
            // Wrap between words only: a word wider than a narrow column would be split
            // mid-word, so such an entry gets one line with an ellipsis (the tooltip has it all).
            text.setWrapText(true);
            text.widthProperty().addListener((obs, old, width) ->
                    text.setWrapText(wordsFit(text, width.doubleValue())));
        }
        text.setMinWidth(0);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(4, icon, text);
        row.setAlignment(week ? Pos.TOP_LEFT : Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.getStyleClass().add("cal-entry");
        Tooltip.install(row, new Tooltip(entryTooltip(entry, date, today)));
        if (entry.movable() && !date.isBefore(today)) {
            makeDraggable(row, GOAL_DRAG, entry.goal().getId());
        }
        return row;
    }

    private static boolean wordsFit(Label label, double width) {
        Text probe = new Text();
        probe.setFont(label.getFont());
        for (String word : label.getText().split("\\s+")) {
            probe.setText(word);
            if (probe.getLayoutBounds().getWidth() > width) {
                return false;
            }
        }
        return true;
    }

    private static String entryIcon(Kind kind) {
        return switch (kind) {
            case OVERDUE -> "⚠";
            case DUE -> "⚑";
            case GOAL_PENDING -> "○";
            case RECURRING, RECURRING_MISSED -> "↺";
            case GOAL_MISSED -> "✕";
            case GOAL_ACHIEVED -> "✓";
            case COMPLETED -> "☑";
        };
    }

    private static String entryColor(Kind kind, LocalDate date, LocalDate today) {
        return switch (kind) {
            case OVERDUE -> TaskStyleUtils.OVERDUE_COLOR;
            case DUE -> date.isBefore(today) ? TaskStyleUtils.OVERDUE_COLOR : TaskStyleUtils.DUE_TODAY_COLOR;
            case GOAL_PENDING -> TaskStyleUtils.COLOR_PURPLE;
            case RECURRING -> TaskStyleUtils.COLOR_PRIMARY;
            case RECURRING_MISSED, GOAL_MISSED -> TaskStyleUtils.MISSED_COLOR;
            case GOAL_ACHIEVED, COMPLETED -> TaskStyleUtils.COLOR_SUCCESS;
        };
    }

    private static String entryLabel(Entry entry, LocalDate date, LocalDate today) {
        return switch (entry.kind()) {
            case OVERDUE -> "Overdue - was due " + entry.task().getDeadline();
            case DUE -> date.isBefore(today) ? "Deadline (passed)" : "Deadline";
            case GOAL_PENDING -> "Planned goal";
            case RECURRING -> "Recurring - " + entry.task().getRecurringSummary();
            case RECURRING_MISSED -> "Missed recurring occurrence";
            case GOAL_MISSED -> entry.goal().getStatus() == StudyGoal.GoalStatus.ABANDONED
                    ? "Abandoned goal" : "Missed goal";
            case GOAL_ACHIEVED -> "Achieved goal";
            case COMPLETED -> "Task completed";
        };
    }

    private static String entryTooltip(Entry entry, LocalDate date, LocalDate today) {
        StringBuilder text = new StringBuilder(entryLabel(entry, date, today)).append('\n').append(entry.title());
        if (entry.detail() != null) {
            text.append('\n').append(entry.detail());
        }
        if (entry.movable() && !date.isBefore(today)) {
            text.append("\n\nDrag to another day to move it.");
        }
        return text.toString();
    }

    private Node activityBar(ScoreBreakdown score) {
        double fraction = Math.min(1.0, (double) score.minutes() / ScoringService.DAILY_MINUTES_TARGET);

        Region track = new Region();
        track.getStyleClass().add("cal-bar-track");
        track.setMaxWidth(Double.MAX_VALUE);
        Region fill = new Region();
        fill.setStyle("-fx-background-color: " + productivityHex(score.productivity())
                + "; -fx-background-radius: 3;");

        StackPane bar = new StackPane(track, fill);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        bar.setMinHeight(6);
        bar.setPrefHeight(6);
        bar.setMaxHeight(6);
        fill.maxWidthProperty().bind(bar.widthProperty().multiply(fraction));

        Tooltip.install(bar, new Tooltip(String.format("%s studied (%.0f%% of the %dh target)%n"
                        + "Productivity %.0f%% · %+d pts",
                formatStudyTime(score.minutes()), fraction * 100, ScoringService.DAILY_MINUTES_TARGET / 60,
                score.productivity(), score.points())));
        return bar;
    }

    private static String formatWeek(LocalDate from, LocalDate to) {
        if (from.getMonth() == to.getMonth()) {
            return from.format(DateTimeFormatter.ofPattern("MMMM d")) + " – "
                    + to.format(DateTimeFormatter.ofPattern("d, yyyy"));
        }
        String start = from.getYear() == to.getYear() ? "MMM d" : "MMM d, yyyy";
        return from.format(DateTimeFormatter.ofPattern(start)) + " – "
                + to.format(DateTimeFormatter.ofPattern("MMM d, yyyy"));
    }

    private String formatStudyTime(int minutes) {
        if (minutes >= 60) {
            int hours = minutes / 60;
            int remainingMinutes = minutes % 60;
            if (remainingMinutes == 0) {
                return hours + "h";
            } else {
                return hours + "h" + remainingMinutes + "m";
            }
        } else {
            return minutes + "m";
        }
    }

    // ================================================================
    // SELECTION, DRAG AND DROP
    // ================================================================

    private void select(LocalDate date) {
        VBox previous = cells.get(selectedDate);
        if (previous != null) {
            previous.pseudoClassStateChanged(SELECTED, false);
        }
        selectedDate = date;
        VBox cell = cells.get(date);
        if (cell != null) {
            cell.pseudoClassStateChanged(SELECTED, true);
        }
        showDay(date);
        sidebar.getSelectionModel().select(dayTab);
    }

    private void makeDraggable(Node node, DataFormat format, String id) {
        node.getStyleClass().add("cal-draggable");
        node.setOnDragDetected(e -> {
            Dragboard dragboard = node.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.put(format, id);
            dragboard.setContent(content);
            dragboard.setDragView(node.snapshot(null, null));
            e.consume();
        });
    }

    /** Only today and later accept drops: a goal cannot be planned into the past. */
    private void installDropTarget(VBox cell, LocalDate date, LocalDate today) {
        if (date.isBefore(today)) {
            return;
        }
        cell.setOnDragOver(e -> {
            if (acceptsDrop(e.getDragboard(), date)) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });
        cell.setOnDragEntered(e -> {
            cell.pseudoClassStateChanged(DROP, acceptsDrop(e.getDragboard(), date));
            e.consume();
        });
        cell.setOnDragExited(e -> {
            cell.pseudoClassStateChanged(DROP, false);
            e.consume();
        });
        cell.setOnDragDropped(e -> {
            Dragboard dragboard = e.getDragboard();
            boolean accepted = acceptsDrop(dragboard, date);
            if (accepted && dragboard.hasContent(GOAL_DRAG)) {
                StudyGoal goal = movableGoals.get((String) dragboard.getContent(GOAL_DRAG));
                // Redraw after the gesture: this cell is replaced by the redraw.
                Platform.runLater(() -> moveGoal(goal, date));
            } else if (accepted) {
                Task task = tasksById.get((String) dragboard.getContent(TASK_DRAG));
                // A modal dialog cannot open in the middle of the drag gesture.
                Platform.runLater(() -> showAddGoalDialog(date, task));
            }
            e.setDropCompleted(accepted);
            e.consume();
        });
    }

    private boolean acceptsDrop(Dragboard dragboard, LocalDate date) {
        if (date.isBefore(LocalDate.now())) {
            return false; // the grid was drawn before midnight
        }
        if (dragboard.hasContent(GOAL_DRAG)) {
            StudyGoal goal = movableGoals.get((String) dragboard.getContent(GOAL_DRAG));
            return goal != null && !goal.getDate().equals(date);
        }
        return dragboard.hasContent(TASK_DRAG) && tasksById.containsKey((String) dragboard.getContent(TASK_DRAG));
    }

    private void moveGoal(StudyGoal goal, LocalDate date) {
        try {
            studyService.updateStudyGoalDetails(goal.getId(), goal.getDescription(), date, null);
        } catch (Exception e) {
            logger.warn("Unable to move goal {} to {}", goal.getId(), date, e);
            showError("Could not move goal", e.getMessage());
        }
        refresh();
    }

    // ================================================================
    // TO PLAN
    // ================================================================

    private void renderToPlan(LocalDate today) {
        toPlanList.getChildren().clear();
        List<Task> unplanned = List.of();
        try {
            Set<String> planned = studyService.getTaskIdsWithPlannedGoals();
            // Recurring tasks are left out: their schedule already plans them.
            unplanned = tasksById.values().stream()
                    .filter(task -> CalendarAgenda.isActive(task.getStatus()))
                    .filter(task -> !task.isRecurring() && !planned.contains(task.getId()))
                    .sorted(Comparator.comparing((Task task) -> task.getDeadline() != null ? task.getDeadline() : LocalDate.MAX)
                            .thenComparing(task -> task.getPriority() != null ? -task.getPriority().stars() : 0)
                            .thenComparing(Task::getTitle, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (Exception e) {
            logger.warn("Unable to load tasks still to plan", e);
        }

        toPlanTab.setText("To plan (" + unplanned.size() + ")");
        if (unplanned.isEmpty()) {
            Label empty = new Label("Every active task has a goal planned.");
            TaskStyleUtils.fontItalic(empty, 12);
            TaskStyleUtils.textColor(empty, TaskStyleUtils.COLOR_MUTED);
            toPlanList.getChildren().add(empty);
            return;
        }
        unplanned.forEach(task -> toPlanList.getChildren().add(toPlanRow(task, today)));
    }

    private Node toPlanRow(Task task, LocalDate today) {
        Label title = new Label(taskTitle(task));
        TaskStyleUtils.fontSemiBold(title, 12);
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);

        LocalDate deadline = task.getDeadline();
        Label due = new Label(dueHint(deadline, today));
        TaskStyleUtils.fontNormal(due, 11);
        due.setMinWidth(Region.USE_PREF_SIZE);
        TaskStyleUtils.textColor(due, deadline == null ? TaskStyleUtils.COLOR_MUTED
                : deadline.isBefore(today) ? TaskStyleUtils.OVERDUE_COLOR
                : !deadline.isAfter(today.plusDays(2)) ? TaskStyleUtils.DUE_TODAY_COLOR
                : TaskStyleUtils.COLOR_MUTED);

        HBox row = new HBox(8, title, due);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("cal-plan-item");
        Tooltip.install(row, new Tooltip(taskTitle(task)
                + "\n\nDrag onto a day to plan a goal for it, or double-click to plan it for the selected day."));
        makeDraggable(row, TASK_DRAG, task.getId());
        row.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.isStillSincePress()) {
                showAddGoalDialog(selectedDate.isBefore(today) ? today : selectedDate, task);
            }
        });
        return row;
    }

    private static String dueHint(LocalDate deadline, LocalDate today) {
        if (deadline == null) {
            return "no deadline";
        }
        long days = ChronoUnit.DAYS.between(today, deadline);
        if (days < 0) {
            return -days + "d late";
        }
        if (days == 0) {
            return "due today";
        }
        if (days == 1) {
            return "due tomorrow";
        }
        if (days < 7) {
            return "due " + deadline.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault());
        }
        return "due " + deadline.format(DateTimeFormatter.ofPattern("MMM d"));
    }

    // ================================================================
    // DAY PANEL
    // ================================================================

    /**
     * A day's numbers, straight from {@link ScoringService}, plus the lines
     * the calendar lists for it.
     */
    private record DayData(LocalDate date, ScoreBreakdown score, List<Entry> entries, String offDayLabel) {

        static DayData empty(LocalDate date) {
            return new DayData(date, ScoreBreakdown.empty(1), List.of(), null);
        }

        int totalSessions() {
            return score.sessions();
        }

        int totalMinutes() {
            return score.minutes();
        }

        int avgFocusLevel() {
            return (int) Math.round(score.avgFocus());
        }
    }

    private void showDay(LocalDate date) {
        LocalDate today = LocalDate.now();
        DayData day = visibleDays.containsKey(date) ? visibleDays.get(date) : loadDay(date, today);
        boolean loggable = !date.isAfter(today);
        dayPanel.getChildren().clear();

        Label title = new Label(date.format(DAY_TITLE));
        TaskStyleUtils.fontBold(title, 18);
        wrap(title);
        dayPanel.getChildren().add(title);

        if (loggable) {
            dayPanel.getChildren().add(createDaySummary(day));
        }

        dayPanel.getChildren().add(createGoalsSection(day, today));

        List<Entry> taskEntries = day.entries().stream().filter(entry -> entry.goal() == null).toList();
        if (!taskEntries.isEmpty()) {
            VBox tasks = new VBox(6, sectionTitle("Tasks", "☑"));
            taskEntries.forEach(entry -> tasks.getChildren().add(createTaskLine(entry, date, today)));
            dayPanel.getChildren().add(tasks);
        }

        if (loggable) {
            dayPanel.getChildren().addAll(createSessionsSection(date), createReflectionSection(date));
            if (day.score().hasActivity()) {
                TitledPane performance = new TitledPane("Points & insights", createPerformanceSection(day));
                performance.setExpanded(false);
                dayPanel.getChildren().add(performance);
            }
        }

        dayPanel.getChildren().add(createOffDaySection(date));
    }

    /** Wraps a label's text; without the min height a ScrollPane can cut it back to one line. */
    private static void wrap(Label label) {
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
    }

    private Label sectionTitle(String text, String icon) {
        Label title = new Label(text);
        title.setGraphic(TaskStyleUtils.iconLabel(icon, 14));
        TaskStyleUtils.fontBold(title, 14);
        return title;
    }

    private Node createDaySummary(DayData day) {
        ScoreBreakdown score = day.score();
        String text;
        if (!score.hasActivity()) {
            text = "Nothing logged.";
        } else {
            text = formatStudyTime(score.minutes()) + " studied · "
                    + score.sessions() + (score.sessions() == 1 ? " session" : " sessions") + " · "
                    + score.goalsAchieved() + "/" + score.goalsTotal() + " goals · "
                    + String.format("%+d pts · %.0f%% productive", score.points(), score.productivity());
        }
        Label summary = new Label(text);
        wrap(summary);
        TaskStyleUtils.fontNormal(summary, 12);
        TaskStyleUtils.textColor(summary, TaskStyleUtils.COLOR_MUTED);
        return summary;
    }

    private Node createGoalsSection(DayData day, LocalDate today) {
        LocalDate date = day.date();
        Label title = sectionTitle("Goals", "◎");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, title, spacer);
        header.setAlignment(Pos.CENTER_LEFT);
        if (!date.isBefore(today)) {
            Button addGoalBtn = new Button("+ Add Goal");
            addGoalBtn.getStyleClass().addAll("btn-purple", "btn-small");
            addGoalBtn.setOnAction(e -> showAddGoalDialog(date, null));
            header.getChildren().add(addGoalBtn);
        }

        VBox section = new VBox(8, header);
        List<StudyGoal> goals = day.entries().stream().map(Entry::goal).filter(goal -> goal != null).toList();
        if (goals.isEmpty()) {
            Label none = new Label(date.isBefore(today) ? "No goals were planned."
                    : "No goals planned yet. Drag a task here from \"To plan\", or add one.");
            wrap(none);
            TaskStyleUtils.fontItalic(none, 12);
            TaskStyleUtils.textColor(none, TaskStyleUtils.COLOR_MUTED);
            section.getChildren().add(none);
        } else {
            goals.forEach(goal -> section.getChildren().add(createStudyGoalBox(goal)));
        }
        return section;
    }

    private Node createTaskLine(Entry entry, LocalDate date, LocalDate today) {
        Label icon = TaskStyleUtils.iconLabel(entryIcon(entry.kind()), 12);
        icon.setMinWidth(Region.USE_PREF_SIZE);
        TaskStyleUtils.textColor(icon, entryColor(entry.kind(), date, today));

        Label name = new Label(entry.title());
        TaskStyleUtils.fontSemiBold(name, 12);
        wrap(name);
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);

        Label kind = new Label(entry.kind() == Kind.OVERDUE ? entry.detail() : entryLabel(entry, date, today));
        TaskStyleUtils.fontNormal(kind, 11);
        TaskStyleUtils.textColor(kind, entryColor(entry.kind(), date, today));
        kind.setMinWidth(Region.USE_PREF_SIZE);

        HBox line = new HBox(6, icon, name, kind);
        line.setAlignment(Pos.CENTER_LEFT);
        line.getStyleClass().add("cal-task-line");
        if (entry.task() != null && entry.task().getCategory() != null && !entry.task().getCategory().isBlank()) {
            Tooltip.install(line, new Tooltip(entry.title() + "\nCategory: " + entry.task().getCategory()));
        }
        return line;
    }

    private Node createSessionsSection(LocalDate date) {
        VBox section = new VBox(8, sectionTitle("Sessions", "▸"));
        List<StudySession> studySessions = studyService.getSessionsForDate(date);
        if (studySessions.isEmpty()) {
            Label none = new Label("No sessions recorded.");
            TaskStyleUtils.fontItalic(none, 12);
            TaskStyleUtils.textColor(none, TaskStyleUtils.COLOR_MUTED);
            section.getChildren().add(none);
            return section;
        }
        studySessions.forEach(session -> section.getChildren().add(createStudySessionBox(session)));
        return section;
    }

    /**
     * Off-day control for a single day. Marking a day off keeps everything
     * logged on it visible here, but takes it out of the global scores shown
     * in the profile panel.
     */
    private VBox createOffDaySection(LocalDate date) {
        VBox section = new VBox(10);
        section.setStyle("-fx-background-color: " + OFF_DAY_BG + "; -fx-background-radius: 10; -fx-padding: 16;");

        String offDayLabel = studyService.getOffDayLabel(date).orElse(null);
        boolean isOffDay = offDayLabel != null;

        Label title = new Label(isOffDay ? "Off day: " + offDayLabel : "Off day / holiday");
        title.setGraphic(TaskStyleUtils.iconLabel(OFF_DAY_ICON, 14));
        wrap(title);
        TaskStyleUtils.fontBold(title, 14);
        TaskStyleUtils.textColor(title, OFF_DAY_COLOR);

        Label hint = new Label(isOffDay
                ? "Nothing on this day counts towards your global scores, and it never breaks your study streak."
                : "Mark this day off (holiday, sick day, break) to keep it out of your global scores.");
        TaskStyleUtils.fontNormal(hint, 12);
        wrap(hint);

        Button toggleBtn = new Button(isOffDay ? "Remove off-day" : "Mark as off-day");
        toggleBtn.setGraphic(TaskStyleUtils.iconLabel(isOffDay ? "✕" : OFF_DAY_ICON, 12));
        toggleBtn.getStyleClass().addAll(isOffDay ? "btn-gray" : "btn-warning", "btn-small");
        toggleBtn.setOnAction(e -> {
            if (isOffDay) {
                studyService.clearOffDay(date);
            } else {
                TextInputDialog prompt = new TextInputDialog(OffDay.DEFAULT_LABEL);
                prompt.initOwner(section.getScene() != null ? section.getScene().getWindow() : null);
                prompt.setTitle("Mark Off Day");
                prompt.setHeaderText("Mark " + date.format(DAY_TITLE) + " as an off day");
                prompt.setContentText("Label:");

                String chosenLabel = prompt.showAndWait().orElse(null);
                if (chosenLabel == null) {
                    return;
                }
                studyService.markOffDay(date, chosenLabel);
            }
            refresh();
        });

        section.getChildren().addAll(title, hint, toggleBtn);
        return section;
    }

    private VBox createReflectionSection(LocalDate date) {
        VBox section = new VBox(10);
        section.setStyle("-fx-background-color: #fff3e0; -fx-background-radius: 10; -fx-padding: 16;");

        Label reflectionTitle = new Label("Daily Reflection");
        reflectionTitle.setGraphic(TaskStyleUtils.iconLabel("✎", 14));
        TaskStyleUtils.fontBold(reflectionTitle, 14);

        try {
            DailyReflection reflection = studyService.getDailyReflectionForDate(date).orElse(null);
            if (reflection != null && reflection.getReflectionText() != null && !reflection.getReflectionText().trim().isEmpty()) {
                TextArea reflectionArea = new TextArea(reflection.getReflectionText());
                reflectionArea.setEditable(false);
                reflectionArea.setPrefRowCount(4);
                reflectionArea.setWrapText(true);
                reflectionArea.setStyle("-fx-background-color: white;");
                section.getChildren().addAll(reflectionTitle, reflectionArea);
            } else {
                Label noReflectionLabel = new Label("No reflection recorded for this day.");
                TaskStyleUtils.fontItalic(noReflectionLabel, 12);
                TaskStyleUtils.textColor(noReflectionLabel, TaskStyleUtils.COLOR_MUTED);
                section.getChildren().addAll(reflectionTitle, noReflectionLabel);
            }
        } catch (Exception e) {
            Label errorLabel = new Label("Unable to load reflection data.");
            TaskStyleUtils.textColor(errorLabel, TaskStyleUtils.COLOR_DANGER);
            section.getChildren().addAll(reflectionTitle, errorLabel);
        }

        return section;
    }

    private VBox createPerformanceSection(DayData dayData) {
        VBox content = new VBox(14);

        // Performance breakdown
        VBox metricsBreakdown = new VBox(10);

        // Where the day's points came from — worth spelling out now that
        // finishing a task late can subtract from the total.
        ScoreBreakdown score = dayData.score();
        if (score.points() != 0 || score.taskPoints() != 0) {
            GridPane pointsGrid = new GridPane();
            pointsGrid.setHgap(20);
            pointsGrid.setVgap(8);

            pointsGrid.add(new Label("Sessions:"), 0, 0);
            pointsGrid.add(new Label(String.format("%+d pts", score.sessionPoints())), 1, 0);

            pointsGrid.add(new Label("Goals achieved:"), 0, 1);
            pointsGrid.add(new Label(String.format("%+d pts", score.goalPoints())), 1, 1);

            pointsGrid.add(new Label("Tasks completed:"), 0, 2);
            Label taskPointsLabel = new Label(String.format("%+d pts", score.taskPoints()));
            TaskStyleUtils.textColor(taskPointsLabel, score.taskPoints() < 0 ? "#c0392b" : "#27ae60");
            pointsGrid.add(taskPointsLabel, 1, 2);

            Label totalLabel = new Label("Day total:");
            TaskStyleUtils.fontBold(totalLabel, 12);
            Label totalValue = new Label(score.points() + " pts");
            TaskStyleUtils.fontBold(totalValue, 12);
            pointsGrid.add(totalLabel, 0, 3);
            pointsGrid.add(totalValue, 1, 3);

            metricsBreakdown.getChildren().add(pointsGrid);

            if (score.taskPoints() < 0) {
                Label hint = new Label("Negative task points mean a task was finished after its deadline, "
                        + "or its deadline was pushed back.");
                TaskStyleUtils.fontItalic(hint, 11);
                TaskStyleUtils.textColor(hint, TaskStyleUtils.COLOR_MUTED);
                wrap(hint);
                metricsBreakdown.getChildren().add(hint);
            }
        }

        // Efficiency metrics
        if (dayData.totalSessions() > 0) {
            double pointsPerMinute = dayData.totalMinutes() > 0 ? (double) dayData.score().points() / dayData.totalMinutes() : 0;
            double pointsPerSession = (double) dayData.score().points() / dayData.totalSessions();
            double avgSessionLength = (double) dayData.totalMinutes() / dayData.totalSessions();

            GridPane efficiencyGrid = new GridPane();
            efficiencyGrid.setHgap(20);
            efficiencyGrid.setVgap(8);

            efficiencyGrid.add(new Label("Points per Minute:"), 0, 0);
            efficiencyGrid.add(new Label(String.format("%.2f", pointsPerMinute)), 1, 0);

            efficiencyGrid.add(new Label("Points per Session:"), 0, 1);
            efficiencyGrid.add(new Label(String.format("%.1f", pointsPerSession)), 1, 1);

            efficiencyGrid.add(new Label("Avg Session Length:"), 0, 2);
            efficiencyGrid.add(new Label(String.format("%.0f min", avgSessionLength)), 1, 2);

            efficiencyGrid.add(new Label("Average Focus:"), 0, 3);
            efficiencyGrid.add(new Label(dayData.avgFocusLevel() + "/5"), 1, 3);

            metricsBreakdown.getChildren().add(efficiencyGrid);
        }

        content.getChildren().addAll(metricsBreakdown, createRecommendationsSection(dayData));
        return content;
    }

    private VBox createRecommendationsSection(DayData dayData) {
        VBox section = new VBox(10);
        section.setStyle("-fx-background-color: #f0fff0; -fx-background-radius: 10; -fx-padding: 16;");

        Label recommendationsTitle = new Label("•  Insights & Recommendations");
        TaskStyleUtils.fontBold(recommendationsTitle, 14);

        VBox recommendations = new VBox(5);

        // Generate recommendations based on performance
        if (dayData.totalSessions() == 0) {
            recommendations.getChildren().add(createRecommendationLabel("• No study activity recorded - consider setting daily study goals"));
        } else {
            if (dayData.avgFocusLevel() < 3) {
                recommendations.getChildren().add(createRecommendationLabel("• Focus level could be improved - try removing distractions"));
            }

            if (dayData.totalMinutes() < 60) {
                recommendations.getChildren().add(createRecommendationLabel("• Consider longer study sessions for better deep work"));
            }

            if (dayData.score().goalsTotal() > 0 && dayData.score().goalsAchieved() < dayData.score().goalsTotal()) {
                recommendations.getChildren().add(createRecommendationLabel("• Some goals were missed - review and adjust goal difficulty"));
            }

            if (dayData.score().productivity() >= 80) {
                recommendations.getChildren().add(createRecommendationLabel("• Excellent productivity day! Keep up the great work!"));
            }
        }

        section.getChildren().addAll(recommendationsTitle, recommendations);
        return section;
    }

    private Label createRecommendationLabel(String text) {
        Label label = new Label(text);
        TaskStyleUtils.fontNormal(label, 12);
        wrap(label);
        return label;
    }

    private static String productivityHex(double score) {
        if (score >= 80) return TaskStyleUtils.COLOR_SUCCESS;
        else if (score >= 60) return TaskStyleUtils.COLOR_PRIMARY;
        else if (score >= 40) return "#f39c12";
        else return TaskStyleUtils.COLOR_DANGER;
    }

    private VBox createStudyGoalBox(StudyGoal goal) {
        VBox goalBox = new VBox(8);
        goalBox.setPadding(new Insets(12));

        String backgroundColor;
        String borderColor;

        if (goal.isFailed()) {
            backgroundColor = "#fdecea";
            borderColor = "#c0392b";
        } else if (goal.isAchieved()) {
            backgroundColor = "#e8f5e8";
            borderColor = "#27ae60";
        } else if (goal.isDelayed()) {
            double intensity = goal.getDelayColorIntensity();
            if (intensity <= 0.3) {
                backgroundColor = "#fff3e0";
                borderColor = "#ff9800";
            } else if (intensity <= 0.6) {
                backgroundColor = "#ffebee";
                borderColor = "#ff5722";
            } else {
                backgroundColor = "#ffebee";
                borderColor = "#f44336";
            }
        } else {
            backgroundColor = "#fff5f5";
            borderColor = "#e74c3c";
        }

        goalBox.setStyle("-fx-background-color: " + backgroundColor + "; -fx-background-radius: 8; -fx-border-color: " + borderColor + "; -fx-border-radius: 8;");

        // Status and description
        String statusIcon;
        String statusText;
        if (goal.isFailed()) {
            statusIcon = "✕";
            statusText = goal.getStatus() == StudyGoal.GoalStatus.ABANDONED ? "Abandoned Goal" : "Missed Goal";
        } else if (goal.isAchieved()) {
            statusIcon = "✅";
            statusText = "Achieved Goal";
        } else if (goal.isDelayed()) {
            statusIcon = "[!] ";
            statusText = "Retry Goal";
        } else {
            statusIcon = "○";
            statusText = "Pending Goal";
        }

        Label statusLabel = new Label(statusIcon + " " + statusText);
        TaskStyleUtils.fontBold(statusLabel, 12);
        if (goal.isFailed()) {
            TaskStyleUtils.textColor(statusLabel, "#c0392b");
        }

        Label descriptionLabel = new Label("◎ " + goal.getDescription());
        TaskStyleUtils.fontNormal(descriptionLabel, 13);
        wrap(descriptionLabel);

        Task linkedTask = goal.getTaskId() != null ? tasksById.get(goal.getTaskId()) : null;

        Label attemptLabel = new Label(formatGoalAttemptSummary(goal));
        wrap(attemptLabel);
        TaskStyleUtils.fontNormal(attemptLabel, 11);
        TaskStyleUtils.textColor(attemptLabel, goal.getMissedAttemptCount() > 0 ? "#ff5722" : "#6c757d");

        goalBox.getChildren().addAll(statusLabel, descriptionLabel);
        if (linkedTask != null) {
            Label taskLabel = new Label("Task: " + taskTitle(linkedTask));
            wrap(taskLabel);
            TaskStyleUtils.fontSemiBold(taskLabel, 11);
            TaskStyleUtils.textColor(taskLabel, TaskStyleUtils.COLOR_PURPLE);
            goalBox.getChildren().add(taskLabel);
        }
        if (!goal.getCriteria().isEmpty()) {
            goalBox.getChildren().add(TaskStyleUtils.criteriaChecklist(goal.getCriteria(), null));
        }
        goalBox.getChildren().add(attemptLabel);

        // Action buttons
        FlowPane actionBox = new FlowPane(8, 6);
        actionBox.setAlignment(Pos.CENTER_RIGHT);

        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().addAll("btn-primary", "btn-small");
        editBtn.setOnAction(e -> showEditGoalDialog(goal));
        actionBox.getChildren().add(editBtn);

        // "Abandon" button — keeps the attempt timeline but stops future replanning.
        if (!goal.isAchieved() && !goal.isFailed()) {
            Button failBtn = new Button("Abandon Goal");
            failBtn.setGraphic(TaskStyleUtils.iconLabel("✖", 12));
            failBtn.getStyleClass().addAll("btn-warning", "btn-small");
            failBtn.setOnAction(e -> {
                Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
                confirmation.initOwner(goalBox.getScene() != null ? goalBox.getScene().getWindow() : null);
                confirmation.setTitle("Abandon Study Goal");
                confirmation.setHeaderText("Abandon this goal?");
                confirmation.setContentText("The goal will stop appearing for re-planning, but its attempt history will be kept.\nGoal: "
                        + goal.getDescription());

                confirmation.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.OK) {
                        studyService.markGoalAsFailed(goal.getId());
                        refresh();
                    }
                });
            });
            actionBox.getChildren().add(failBtn);
        }

        // Delete button — permanent removal
        Button deleteBtn = new Button("Delete");
        deleteBtn.setGraphic(TaskStyleUtils.iconLabel("✕", 12));
        deleteBtn.getStyleClass().addAll("btn-danger", "btn-small");
        deleteBtn.setOnAction(e -> {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.initOwner(goalBox.getScene() != null ? goalBox.getScene().getWindow() : null);
            confirmation.setTitle("Delete Study Goal");
            confirmation.setHeaderText("Permanently delete this study goal?");
            confirmation.setContentText("This cannot be undone.\nGoal: " + goal.getDescription());

            confirmation.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    studyService.deleteStudyGoal(goal.getId());
                    refresh();
                }
            });
        });

        actionBox.getChildren().add(deleteBtn);
        goalBox.getChildren().add(actionBox);

        return goalBox;
    }

    /**
     * Edit a goal's description, done-when checklist and (while pending) its
     * date. The whole period is redrawn afterwards, which also moves the goal
     * to its new day if the date changed.
     */
    private void showEditGoalDialog(StudyGoal goal) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(this.getScene() != null ? this.getScene().getWindow() : null);
        dialog.setTitle("Edit Goal");
        dialog.setHeaderText("Edit goal planned for "
                + goal.getDate().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")));

        GoalEditFields fields = new GoalEditFields(goal, goal.getDate());
        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        pane.setContent(fields.view());

        // Consuming the action keeps the dialog open when the fields do not validate.
        pane.lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                fields.save(studyService, goal);
            } catch (Exception ex) {
                fields.showError(ex.getMessage());
                ev.consume();
            }
        });

        dialog.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt -> refresh());
    }

    static String formatGoalAttemptSummary(StudyGoal goal) {
        long sessions = StudySession.countByGoalId(goal.getId());
        String summary = "Attempt " + goal.getAttemptNumber() + " planned for " + goal.getDate();
        if (sessions > 0) {
            summary += " | Goal: " + sessions + (sessions == 1 ? " linked session" : " linked sessions")
                    + " across all attempts (including incomplete)";
        }
        if (goal.getStatus() == StudyGoal.GoalStatus.ABANDONED) {
            summary += " | parent abandoned";
        }
        return summary + TaskStyleUtils.criteriaProgress(goal.getCriteria());
    }

    private VBox createStudySessionBox(StudySession session) {
        VBox sessionBox = new VBox(8);
        sessionBox.setPadding(new Insets(12));
        sessionBox.setStyle("-fx-background-color: #f8f9ff; -fx-background-radius: 8; -fx-border-color: #3498db; -fx-border-radius: 8;");

        // Time and duration
        String startTime = session.getStartTime() != null ?
            session.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "Unknown";
        String endTime = session.getEndTime() != null ?
            session.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "Unknown";

        Label timeLabel = new Label(startTime + " - " + endTime + " (" + session.getDurationMinutes() + " min)");
        timeLabel.setGraphic(TaskStyleUtils.iconLabel("⏰", 12));
        TaskStyleUtils.fontBold(timeLabel, 12);

        // Focus and points
        Label focusLabel = new Label("Focus: " + "★".repeat(session.getFocusLevel()) + "☆".repeat(5 - session.getFocusLevel()) + " (" + session.getFocusLevel() + "/5)");
        focusLabel.setGraphic(TaskStyleUtils.iconLabel("◎", 11));
        TaskStyleUtils.fontNormal(focusLabel, 11);
        TaskStyleUtils.textColor(focusLabel, "#f39c12");

        Label pointsLabel = new Label(session.getPointsEarned() + " points earned");
        pointsLabel.setGraphic(TaskStyleUtils.iconLabel("♦", 11));
        TaskStyleUtils.fontNormal(pointsLabel, 11);
        TaskStyleUtils.textColor(pointsLabel, TaskStyleUtils.COLOR_SUCCESS);

        sessionBox.getChildren().addAll(timeLabel, focusLabel, pointsLabel);

        // What this session was working on
        Label linkLabel = TaskStyleUtils.sessionLinkLabel(session);
        if (linkLabel != null) {
            sessionBox.getChildren().add(linkLabel);
        }

        // Subject and topic if available
        if (session.getSubject() != null && !session.getSubject().trim().isEmpty()) {
            Label subjectLabel = new Label("📖 Subject: " + session.getSubject());
            TaskStyleUtils.fontNormal(subjectLabel, 11);
            sessionBox.getChildren().add(subjectLabel);
        }

        // Action buttons
        HBox actionBox = new HBox(8);
        actionBox.setAlignment(Pos.CENTER_RIGHT);

        Button deleteBtn = new Button("Delete");
        deleteBtn.setGraphic(TaskStyleUtils.iconLabel("✕", 12));
        deleteBtn.getStyleClass().addAll("btn-danger", "btn-small");
        deleteBtn.setOnAction(e -> {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.initOwner(sessionBox.getScene() != null ? sessionBox.getScene().getWindow() : null);
            confirmation.setTitle("Delete Study Session");
            confirmation.setHeaderText("Are you sure you want to delete this study session?");

            confirmation.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    studyService.deleteStudySession(session.getId());
                    refresh();
                }
            });
        });

        actionBox.getChildren().add(deleteBtn);
        sessionBox.getChildren().add(actionBox);

        return sessionBox;
    }

    /**
     * Shows a dialog to add a new study goal for the specified date.
     * Works for today and future dates to enable planning ahead.
     *
     * @param date the date to add the goal for
     * @param task task to link the goal to up front, e.g. one dropped onto the day; may be {@code null}
     */
    private void showAddGoalDialog(LocalDate date, Task task) {
        Dialog<String> dialog = new Dialog<>();
        dialog.initOwner(this.getScene() != null ? this.getScene().getWindow() : null);
        boolean isFutureDate = date.isAfter(LocalDate.now());

        dialog.setTitle(isFutureDate ? "Plan Goal" : "Add Goal");
        dialog.setHeaderText("Add a goal for " + date.format(DAY_TITLE));

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        Label instructionLabel = new Label(isFutureDate
            ? "Plan what you want to study on this day:"
            : "What do you want to study today?");
        TaskStyleUtils.fontNormal(instructionLabel, 12);

        TextArea goalTextArea = new TextArea();
        goalTextArea.setPromptText("e.g., Complete Chapter 5, Practice 10 problems, Review notes from class...");
        goalTextArea.setPrefRowCount(3);
        goalTextArea.setWrapText(true);

        Label criteriaLabel = new Label(TaskStyleUtils.CRITERIA_LABEL);
        TaskStyleUtils.fontBold(criteriaLabel, 12);
        TextArea criteriaArea = TaskStyleUtils.criteriaArea(null);

        Label taskLabel = new Label("Link to task (optional):");
        TaskStyleUtils.fontBold(taskLabel, 12);
        ComboBox<Task> taskCombo = new ComboBox<>();
        taskCombo.setPromptText("None");
        taskCombo.setMaxWidth(Double.MAX_VALUE);
        taskCombo.getItems().add(null);
        taskCombo.getItems().addAll(taskService.getActiveTasks());
        // A late task is DELAYED, which the active list leaves out, but it is
        // exactly the kind of task that gets dropped onto a day to plan.
        if (task != null && taskCombo.getItems().stream().noneMatch(t -> t != null && t.getId().equals(task.getId()))) {
            taskCombo.getItems().add(task);
        }
        taskCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Task item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "None" : taskTitle(item));
            }
        });
        taskCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Task item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "None" : taskTitle(item));
            }
        });
        if (task != null) {
            taskCombo.getItems().stream()
                    .filter(t -> t != null && t.getId().equals(task.getId()))
                    .findFirst()
                    .ifPresent(taskCombo::setValue);
            // Something to accept or type over, so planning a dropped task is one keystroke.
            goalTextArea.setText(task.getTitle());
            goalTextArea.selectAll();
        }

        content.getChildren().addAll(instructionLabel, goalTextArea, criteriaLabel, criteriaArea, taskLabel, taskCombo);
        dialogPane.setContent(content);

        // Enable/disable OK button based on input
        Button okButton = (Button) dialogPane.lookupButton(ButtonType.OK);
        okButton.setDisable(goalTextArea.getText().trim().isEmpty());
        goalTextArea.textProperty().addListener((observable, oldValue, newValue) -> {
            okButton.setDisable(newValue.trim().isEmpty());
        });
        dialog.setOnShown(e -> goalTextArea.requestFocus());

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return goalTextArea.getText().trim();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(goalDescription -> {
            if (date.isBefore(LocalDate.now())) {
                // The dialog was opened before midnight; goals are never planned into the past.
                showError("Failed to add study goal", date + " has passed. Plan the goal for today or later.");
                refresh();
            } else if (!goalDescription.isEmpty()) {
                try {
                    Task linkedTask = taskCombo.getValue();
                    studyService.addStudyGoal(goalDescription, date,
                            linkedTask != null ? linkedTask.getId() : null, criteriaArea.getText());
                    selectedDate = date;
                    refresh();
                } catch (Exception e) {
                    showError("Failed to add study goal", e.getMessage());
                }
            }
        });
    }

    private String taskTitle(Task task) {
        return CalendarAgenda.taskTitle(task, tasksById);
    }

    private void showError(String header, String message) {
        Alert errorAlert = new Alert(Alert.AlertType.ERROR);
        errorAlert.initOwner(this.getScene() != null ? this.getScene().getWindow() : null);
        errorAlert.setTitle("Error");
        errorAlert.setHeaderText(header);
        errorAlert.setContentText(message);
        errorAlert.showAndWait();
    }

    @Override
    public void updateDisplay() {
        logger.debug("CalendarViewPanel.updateDisplay() called for {} around {}", viewMode, anchor);
        refresh();
    }

    @Override
    public Node getView() {
        return this;
    }
}
