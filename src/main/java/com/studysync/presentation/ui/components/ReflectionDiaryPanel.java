package com.studysync.presentation.ui.components;

import com.studysync.domain.entity.DailyReflection;
import com.studysync.domain.service.DateTimeService;
import com.studysync.domain.service.StudyService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import netscape.javascript.JSObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The diary: every past reflection in one browsable, searchable list on the
 * left; on the right either one day open for writing, or the whole diary
 * rendered as a scrolling thread of dated entries.
 *
 * <p>Entries are markdown — tables included — written as source in the editor
 * and read rendered, the way a note in Obsidian works. They can be exported as
 * one {@code YYYY-MM-DD.md} file per day.</p>
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
    /** Column counts offered by the "Table" button. */
    private static final int[] TABLE_COLUMNS = {2, 3, 4, 5, 6};

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
    private final WebView feed = new WebView();
    private final ToggleButton writeToggle = new ToggleButton("Write");
    private final ToggleButton readToggle = new ToggleButton("Read");
    private final PauseTransition autosave = new PauseTransition(Duration.seconds(1.5));
    /** Held in a field: {@code JSObject.setMember} does not keep the bridge alive. */
    private final DiaryBridge bridge = new DiaryBridge();

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

        MenuButton tableButton = new MenuButton("Table");
        tableButton.setGraphic(TaskStyleUtils.iconLabel("▦", 12));
        tableButton.getStyleClass().add("btn-primary");
        for (int columns : TABLE_COLUMNS) {
            MenuItem item = new MenuItem(columns + " columns");
            int cols = columns;
            item.setOnAction(e -> insertTable(cols));
            tableButton.getItems().add(item);
        }
        tableButton.disableProperty().bind(readToggle.selectedProperty());

        MenuButton exportButton = new MenuButton("Export");
        exportButton.setGraphic(TaskStyleUtils.iconLabel("⇩", 12));
        exportButton.getStyleClass().add("btn-purple");
        MenuItem exportOne = new MenuItem("This entry…");
        exportOne.setOnAction(e -> exportEntry());
        MenuItem exportAll = new MenuItem("All shown entries…");
        exportAll.setOnAction(e -> exportAll());
        exportButton.getItems().addAll(exportOne, exportAll);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, prev, next, dateHeader, spacer, datePicker, today);
        header.setAlignment(Pos.CENTER_LEFT);

        Region toolbarSpacer = new Region();
        HBox.setHgrow(toolbarSpacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, writeToggle, readToggle, toolbarSpacer, tableButton, exportButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        editor.setPromptText("""
                How did today go?

                - What went well?
                - What got in the way?
                - What did you learn?
                - What would you do differently tomorrow?

                Markdown works here: **bold**, # headings, lists, and tables
                (the Table button drops one in). Read shows it rendered.""");
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
        feed.setVisible(false);
        feed.setManaged(false);
        feed.setContextMenuEnabled(false);
        // The rendered page calls back through window.studysync to open a day.
        // Only the document this panel generated gets the bridge — never a page
        // the view was navigated to.
        feed.getEngine().getLoadWorker().stateProperty().addListener((obs, was, state) -> {
            if (state == Worker.State.SUCCEEDED && isGeneratedPage()) {
                try {
                    JSObject window = (JSObject) feed.getEngine().executeScript("window");
                    window.setMember("studysync", bridge);
                } catch (RuntimeException ex) {
                    // Reading still works; the sidebar remains the way in.
                }
            }
        });

        StackPane body = new StackPane(editor, feed);
        VBox.setVgrow(body, Priority.ALWAYS);

        TaskStyleUtils.fontNormal(wordCount, 11);
        wordCount.setTextFill(Color.web(TaskStyleUtils.COLOR_MUTED));
        TaskStyleUtils.fontNormal(status, 11);

        Region footSpacer = new Region();
        HBox.setHgrow(footSpacer, Priority.ALWAYS);
        HBox footer = new HBox(10, wordCount, footSpacer, status);
        footer.setAlignment(Pos.CENTER_LEFT);

        pane.getChildren().addAll(header, toolbar, body, footer);
        return pane;
    }

    // ── Behaviour ───────────────────────────────────────────────

    /** True while the view holds the document this panel built, not a remote page. */
    private boolean isGeneratedPage() {
        String location = feed.getEngine().getLocation();
        return location == null || location.isEmpty() || location.startsWith("about:");
    }

    /** Navigating to a specific day means writing in it — reading is the thread. */
    private void goTo(LocalDate date) {
        open(date);
        writeToggle.setSelected(true);
        editor.requestFocus();
    }

    /**
     * Flushes the open entry, then swaps the editor over to {@code date}.
     * A failed save aborts the move, so the unsaved text stays on screen.
     */
    private void open(LocalDate date) {
        if (!flush()) {
            datePicker.setValue(openDate);
            syncSelection();
            return;
        }
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

    /**
     * Persists the editor's text when it differs from what is stored.
     *
     * @return {@code false} when the write failed — the caller must not move
     *         off this day or act on the stored entries, the editor still
     *         holds the only copy of the text
     */
    private boolean flush() {
        autosave.stop();
        if (openDate == null) return true;

        String text = editor.getText() == null ? "" : editor.getText();
        if (text.equals(savedText)) return true;

        try {
            studyService.saveReflectionText(openDate, text);
            savedText = text;
            report(text.isBlank() ? "Entry deleted" : "Saved " + LocalTime.now().format(SAVED_AT_FORMAT),
                    TaskStyleUtils.COLOR_SUCCESS);
            reloadEntries();
            return true;
        } catch (Exception ex) {
            report("Not saved, text kept here: " + ex.getMessage(), TaskStyleUtils.COLOR_DANGER);
            return false;
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
            if (!flush()) {
                writeToggle.setSelected(true);
                return;
            }
            renderFeed();
        }
        feed.setVisible(reading);
        feed.setManaged(reading);
        editor.setVisible(!reading);
        editor.setManaged(!reading);
        wordCount.setVisible(!reading);
    }

    /**
     * Renders every entry that survives the current search, newest first — the
     * diary read back as one markdown document. Each date links back to its day.
     */
    private void renderFeed() {
        StringBuilder body = new StringBuilder();
        // ponytail: one document holding every entry, no virtualisation. Page it
        // by month if a few thousand entries ever make loading it sluggish.
        for (DailyReflection entry : matches) {
            body.append("<article><h2><a href=\"#\" onclick=\"studysync.open('")
                    .append(entry.getDate())
                    .append("'); return false;\">")
                    .append(describe(entry.getDate()))
                    .append("</a></h2>")
                    .append(Markdown.toHtml(entry.getReflectionText()))
                    .append("</article><hr>");
        }
        if (body.isEmpty()) {
            body.append("<p class=\"empty\">")
                    .append(searchField.getText() == null || searchField.getText().isBlank()
                            ? "Nothing written yet — switch to Write and start today's entry."
                            : "No entries match that search.")
                    .append("</p>");
        }
        feed.getEngine().loadContent(Markdown.page(body.toString()));
    }

    /** Drops a markdown table skeleton in at the caret. */
    private void insertTable(int columns) {
        int caret = editor.getCaretPosition();
        editor.insertText(caret, Markdown.tableSkeleton(columns));
        editor.requestFocus();
    }

    /** Writes the open day to a single {@code .md} file the user picks. */
    private void exportEntry() {
        if (!flush()) return;
        String text = editor.getText() == null ? "" : editor.getText();
        if (text.isBlank()) {
            report("Nothing to export for this day", TaskStyleUtils.COLOR_MUTED);
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export reflection");
        chooser.setInitialFileName(openDate + ".md");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown", "*.md"));
        File target = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
        if (target == null) {
            return;
        }
        try {
            Files.writeString(target.toPath(), endWithNewline(text));
            report("Exported to " + target.getName(), TaskStyleUtils.COLOR_SUCCESS);
        } catch (IOException ex) {
            report("Export failed: " + ex.getMessage(), TaskStyleUtils.COLOR_DANGER);
        }
    }

    /**
     * Writes every entry the search currently shows into a folder, one
     * {@code YYYY-MM-DD.md} per day — an Obsidian vault of daily notes.
     */
    private void exportAll() {
        if (!flush()) return;
        List<DailyReflection> exportable = new ArrayList<>(matches);
        if (exportable.isEmpty()) {
            report("No entries to export", TaskStyleUtils.COLOR_MUTED);
            return;
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Export " + exportable.size() + " reflections to folder");
        File folder = chooser.showDialog(getScene() == null ? null : getScene().getWindow());
        if (folder == null) {
            return;
        }

        List<String> clashes = new ArrayList<>();
        for (DailyReflection entry : exportable) {
            if (Files.exists(folder.toPath().resolve(entry.getDate() + ".md"))) {
                clashes.add(entry.getDate() + ".md");
            }
        }
        if (!clashes.isEmpty() && !confirmOverwrite(clashes, folder)) {
            return;
        }

        int written = 0;
        try {
            for (DailyReflection entry : exportable) {
                Path file = folder.toPath().resolve(entry.getDate() + ".md");
                String text = entry.getReflectionText() == null ? "" : entry.getReflectionText();
                Files.writeString(file, endWithNewline(text));
                written++;
            }
            report("Exported " + written + " entries to " + folder.getName(), TaskStyleUtils.COLOR_SUCCESS);
        } catch (IOException ex) {
            report("Stopped after " + written + " entries: " + ex.getMessage(), TaskStyleUtils.COLOR_DANGER);
        }
    }

    /** Export never silently replaces notes that are already in the folder. */
    private boolean confirmOverwrite(List<String> clashes, File folder) {
        String sample = String.join(", ", clashes.subList(0, Math.min(5, clashes.size())));
        if (clashes.size() > 5) {
            sample += ", …";
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                clashes.size() + " file(s) in " + folder.getName() + " will be overwritten: "
                        + sample + "\n\nContinue?",
                ButtonType.CANCEL, ButtonType.OK);
        confirm.initOwner(getScene() == null ? null : getScene().getWindow());
        confirm.setHeaderText(null);
        confirm.setTitle("Overwrite existing notes?");
        return confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    /** Exported files end with exactly one newline, without touching the markdown. */
    private static String endWithNewline(String text) {
        return text.endsWith("\n") ? text : text + System.lineSeparator();
    }

    private void report(String message, String color) {
        status.setText(message);
        status.setTextFill(Color.web(color));
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
    public boolean flushPendingChanges() {
        return flush();
    }

    @Override
    public Node getView() {
        return this;
    }

    /**
     * Exposed to the rendered diary page as {@code window.studysync}, so a date
     * in the thread opens that day in the editor. Must stay public for the
     * WebView bridge to reach it.
     */
    public final class DiaryBridge {
        /** @param isoDate the entry's date, as {@code YYYY-MM-DD} */
        public void open(String isoDate) {
            Platform.runLater(() -> goTo(LocalDate.parse(isoDate)));
        }
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

            String text = Markdown.previewLine(entry.getReflectionText());
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
