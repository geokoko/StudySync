package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.DailyReflection;
import com.studysync.domain.service.DateTimeService;
import com.studysync.domain.service.StudyService;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The diary: every past reflection in one browsable, searchable list on the
 * left; on the right either one day open for writing, or the whole diary as a
 * scrolling thread of dated entries.
 *
 * <p>Entries save themselves — a short pause in typing, leaving the editor,
 * or moving to another day all flush the text. Clearing an entry deletes it.</p>
 */
public class ReflectionDiaryPanel extends BorderPane implements RefreshablePanel {

    private static final DateTimeFormatter HEADER_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
    private static final DateTimeFormatter LIST_FORMAT =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy");
    private static final DateTimeFormatter SAVED_AT_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm");

    private final StudyService studyService;
    private final DateTimeService dateTimeService;

    private final ObservableList<DailyReflection> entries = FXCollections.observableArrayList();
    private final FilteredList<DailyReflection> matches = new FilteredList<>(entries);
    private final ListView<DailyReflection> entryList = new ListView<>(matches);
    private final TextField searchField = new TextField();
    private final Label entryCount = new Label();
    private final DatePicker datePicker = new DatePicker();
    private final Label dateHeader = new Label();
    private final TextArea editor = new TextArea();
    private final Label wordCount = new Label();
    private final Label status = new Label();
    private final ScrollPane feed = new ScrollPane();
    private final ToggleButton writeToggle = new ToggleButton("Write");
    private final ToggleButton readToggle = new ToggleButton("Read");
    private final PauseTransition autosave = new PauseTransition(Duration.seconds(1.5));

    private LocalDate openDate;
    /** Text currently persisted for {@link #openDate}; the editor is dirty when it differs. */
    private String savedText = "";
    /** Set while the editor is being populated, so that doesn't count as typing. */
    private boolean populating;

    public ReflectionDiaryPanel(StudyService studyService, DateTimeService dateTimeService) {
        this.studyService = studyService;
        this.dateTimeService = dateTimeService;
        this.openDate = dateTimeService.getCurrentDate();

        getStyleClass().addAll("tab-content-area", "panel-bg-warm");
        setPadding(new Insets(20));
        setLeft(buildSidebar());
        setCenter(buildEditor());
        BorderPane.setMargin(getCenter(), new Insets(0, 0, 0, 20));

        autosave.setOnFinished(e -> flush());
        dateTimeService.addDateChangeListener(this::onDateChanged);

        reloadEntries();
        open(openDate);
    }

    // ── Layout ──────────────────────────────────────────────────

    private VBox buildSidebar() {
        VBox sidebar = new VBox(12);
        sidebar.getStyleClass().add("section-card");
        sidebar.setPadding(new Insets(15));
        sidebar.setPrefWidth(300);
        sidebar.setMinWidth(300);

        Label title = new Label("Diary");
        title.setGraphic(TaskStyleUtils.iconLabel("✎", 20));
        TaskStyleUtils.fontBold(title, 20);

        searchField.setPromptText("Search entries…");
        searchField.textProperty().addListener((obs, old, text) -> applyFilter(text));

        TaskStyleUtils.fontNormal(entryCount, 11);
        entryCount.setTextFill(Color.web(TaskStyleUtils.COLOR_MUTED));

        entryList.setCellFactory(view -> new EntryCell());
        entryList.setPlaceholder(new Label("No reflections yet."));
        entryList.getSelectionModel().selectedItemProperty().addListener((obs, old, entry) -> {
            if (entry != null && !entry.getDate().equals(openDate)) {
                goTo(entry.getDate());
            }
        });
        VBox.setVgrow(entryList, Priority.ALWAYS);

        sidebar.getChildren().addAll(title, searchField, entryCount, entryList);
        return sidebar;
    }

    private VBox buildEditor() {
        VBox pane = new VBox(12);
        pane.getStyleClass().add("section-card");
        pane.setPadding(new Insets(20));

        Button prev = new Button();
        prev.setGraphic(TaskStyleUtils.iconLabel("◀", 12));
        prev.getStyleClass().add("btn-primary");
        prev.setOnAction(e -> goTo(openDate.minusDays(1)));

        Button next = new Button();
        next.setGraphic(TaskStyleUtils.iconLabel("▶", 12));
        next.getStyleClass().add("btn-primary");
        next.setOnAction(e -> goTo(openDate.plusDays(1)));

        Button today = new Button("Today");
        today.getStyleClass().add("btn-purple");
        today.setOnAction(e -> goTo(dateTimeService.getCurrentDate()));

        TaskStyleUtils.fontBold(dateHeader, 18);

        datePicker.setOnAction(e -> {
            LocalDate picked = datePicker.getValue();
            if (picked != null && !picked.equals(openDate)) {
                goTo(picked);
            }
        });

        ToggleGroup modes = new ToggleGroup();
        writeToggle.setToggleGroup(modes);
        readToggle.setToggleGroup(modes);
        writeToggle.setSelected(true);
        modes.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                old.setSelected(true); // never leave both off
                return;
            }
            showFeed(selected == readToggle);
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, prev, next, dateHeader, spacer,
                writeToggle, readToggle, datePicker, today);
        header.setAlignment(Pos.CENTER_LEFT);

        editor.setPromptText("""
                How did today go?

                • What went well?
                • What got in the way?
                • What did you learn?
                • What would you do differently tomorrow?""");
        editor.setWrapText(true);
        editor.setStyle("-fx-font-family: 'Georgia', 'Times New Roman', serif; "
                + "-fx-font-size: 14px; -fx-line-spacing: 0.35em;");
        editor.textProperty().addListener((obs, old, text) -> {
            updateWordCount(text);
            if (populating) return;
            status.setText("Unsaved…");
            status.setTextFill(Color.web(TaskStyleUtils.COLOR_ORANGE));
            autosave.playFromStart();
        });
        editor.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) flush();
        });
        editor.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN).match(e)) {
                flush();
                e.consume();
            }
        });
        feed.setFitToWidth(true);
        feed.getStyleClass().add("section-card-flat");
        feed.setVisible(false);
        feed.setManaged(false);

        StackPane body = new StackPane(editor, feed);
        VBox.setVgrow(body, Priority.ALWAYS);

        TaskStyleUtils.fontNormal(wordCount, 11);
        wordCount.setTextFill(Color.web(TaskStyleUtils.COLOR_MUTED));
        TaskStyleUtils.fontNormal(status, 11);

        Region footSpacer = new Region();
        HBox.setHgrow(footSpacer, Priority.ALWAYS);
        HBox footer = new HBox(10, wordCount, footSpacer, status);
        footer.setAlignment(Pos.CENTER_LEFT);

        pane.getChildren().addAll(header, body, footer);
        return pane;
    }

    // ── Behaviour ───────────────────────────────────────────────

    /** Navigating to a specific day means writing in it — reading is the thread. */
    private void goTo(LocalDate date) {
        open(date);
        writeToggle.setSelected(true);
        editor.requestFocus();
    }

    /** Flushes the open entry, then swaps the editor over to {@code date}. */
    private void open(LocalDate date) {
        flush();
        openDate = date;

        String text = studyService.getDailyReflectionForDate(date)
                .map(DailyReflection::getReflectionText)
                .orElse("");
        savedText = text == null ? "" : text;

        populating = true;
        editor.setText(savedText);
        populating = false;

        dateHeader.setText(describe(date));
        datePicker.setValue(date);
        updateWordCount(savedText);
        status.setText("");
        syncSelection();
    }

    /** Persists the editor's text when it differs from what is stored. */
    private void flush() {
        autosave.stop();
        if (openDate == null) return;

        String text = editor.getText() == null ? "" : editor.getText().trim();
        if (text.equals(savedText.trim())) return;

        try {
            studyService.saveReflectionText(openDate, text);
            savedText = text;
            status.setText(text.isEmpty()
                    ? "Entry deleted"
                    : "Saved " + LocalTime.now().format(SAVED_AT_FORMAT));
            status.setTextFill(Color.web(TaskStyleUtils.COLOR_SUCCESS));
            reloadEntries();
        } catch (Exception ex) {
            status.setText("Not saved: " + ex.getMessage());
            status.setTextFill(Color.web(TaskStyleUtils.COLOR_DANGER));
        }
    }

    private void reloadEntries() {
        try {
            List<DailyReflection> all = studyService.getAllDailyReflections();
            entries.setAll(all);
            entryCount.setText(all.size() == 1 ? "1 entry" : all.size() + " entries");
            syncSelection();
            if (feed.isVisible()) renderFeed();
        } catch (Exception ex) {
            entryCount.setText("Unable to load entries: " + ex.getMessage());
        }
    }

    /** Swaps the right-hand pane between the day editor and the reading thread. */
    private void showFeed(boolean reading) {
        if (reading) {
            flush();
            renderFeed();
        }
        feed.setVisible(reading);
        feed.setManaged(reading);
        editor.setVisible(!reading);
        editor.setManaged(!reading);
        wordCount.setVisible(!reading);
    }

    /**
     * Renders every entry that survives the current search as one dated card,
     * newest first — the diary read back as a thread.
     */
    private void renderFeed() {
        VBox thread = new VBox(16);
        thread.setPadding(new Insets(4, 16, 12, 4));

        // ponytail: builds a card per entry, no virtualisation. Move to a
        // ListView with a wrapping cell if a few thousand entries ever lag.
        for (DailyReflection entry : matches) {
            thread.getChildren().add(feedCard(entry));
        }
        if (thread.getChildren().isEmpty()) {
            Label empty = new Label(searchField.getText() == null || searchField.getText().isBlank()
                    ? "Nothing written yet — switch to Write and start today's entry."
                    : "No entries match that search.");
            TaskStyleUtils.fontItalic(empty, 12);
            empty.setTextFill(Color.web(TaskStyleUtils.COLOR_MUTED));
            thread.getChildren().add(empty);
        }
        feed.setContent(thread);
    }

    /** One entry in the thread: its date, its text, and a click to go edit it. */
    private VBox feedCard(DailyReflection entry) {
        Label when = new Label(describe(entry.getDate()));
        TaskStyleUtils.fontSemiBold(when, 13);
        when.setTextFill(Color.web(TaskStyleUtils.COLOR_PRIMARY));

        Label text = new Label(entry.getReflectionText() == null ? "" : entry.getReflectionText());
        text.setWrapText(true);
        text.setStyle("-fx-font-family: 'Georgia', 'Times New Roman', serif; "
                + "-fx-font-size: 13px; -fx-line-spacing: 0.3em;");

        VBox card = new VBox(6, when, text);
        card.getStyleClass().add("section-card-light");
        card.setPadding(new Insets(12, 14, 12, 14));
        card.setCursor(Cursor.HAND);
        Tooltip.install(card, new Tooltip("Open this day for editing"));
        card.setOnMouseClicked(e -> goTo(entry.getDate()));
        return card;
    }

    private void applyFilter(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        matches.setPredicate(needle.isEmpty() ? null : entry -> {
            String text = entry.getReflectionText();
            return (text != null && text.toLowerCase(Locale.ROOT).contains(needle))
                    || entry.getDate().format(LIST_FORMAT).toLowerCase(Locale.ROOT).contains(needle);
        });
        syncSelection();
        if (feed.isVisible()) renderFeed();
    }

    /** Highlights the open day in the list without re-triggering navigation. */
    private void syncSelection() {
        for (DailyReflection entry : matches) {
            if (entry.getDate().equals(openDate)) {
                entryList.getSelectionModel().select(entry);
                return;
            }
        }
        entryList.getSelectionModel().clearSelection();
    }

    private void updateWordCount(String text) {
        int words = (text == null || text.isBlank()) ? 0 : text.trim().split("\\s+").length;
        wordCount.setText(words == 1 ? "1 word" : words + " words");
    }

    private String describe(LocalDate date) {
        return date.format(HEADER_FORMAT) + relativeSuffix(date);
    }

    private String relativeSuffix(LocalDate date) {
        LocalDate today = dateTimeService.getCurrentDate();
        if (date.equals(today)) return " · Today";
        if (date.equals(today.minusDays(1))) return " · Yesterday";
        return "";
    }

    private void onDateChanged(LocalDate newDate) {
        // Midnight rollover: keep "Today"/"Yesterday" labels honest.
        dateHeader.setText(describe(openDate));
        entryList.refresh();
    }

    @Override
    public void updateDisplay() {
        reloadEntries();
        if (editor.getText() != null && editor.getText().trim().equals(savedText.trim())) {
            open(openDate);
        }
    }

    @Override
    public Node getView() {
        return this;
    }

    /** Date line plus a one-line preview, so the list reads like a diary index. */
    private final class EntryCell extends ListCell<DailyReflection> {
        @Override
        protected void updateItem(DailyReflection entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            Label date = new Label(entry.getDate().format(LIST_FORMAT) + relativeSuffix(entry.getDate()));
            TaskStyleUtils.fontSemiBold(date, 12);

            String text = entry.getReflectionText() == null ? "" : entry.getReflectionText().replaceAll("\\s+", " ").trim();
            Label preview = new Label(text.length() > 70 ? text.substring(0, 70) + "…" : text);
            TaskStyleUtils.fontNormal(preview, 11);
            preview.setTextFill(Color.web(TaskStyleUtils.COLOR_MUTED));

            VBox box = new VBox(2, date, preview);
            box.setPadding(new Insets(4, 0, 4, 0));
            setText(null);
            setGraphic(box);
        }
    }
}
