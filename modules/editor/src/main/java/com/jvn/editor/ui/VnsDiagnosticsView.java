package com.jvn.editor.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Side panel showing live diagnostics for the active script or DSL file. */
public class VnsDiagnosticsView extends BorderPane {
  private static final String SEVERITY_ALL = "All";
  private static final String SEVERITY_ERRORS = "Errors";
  private static final String SEVERITY_WARNINGS = "Warnings";
  private static final String CATEGORY_ALL = "All categories";
  private static final String CATEGORY_SYNTAX = "Syntax";
  private static final String CATEGORY_FLOW = "Flow";
  private static final String CATEGORY_ASSETS = "Assets";
  private static final String CATEGORY_SCRIPTS = "Scripts";
  private static final String CATEGORY_CONTENT = "Content";

  private enum SortMode { LINE, SEVERITY }
  private SortMode sortMode = SortMode.LINE;

  private final Label titleLabel = new Label("Diagnostics");
  private final Label fileLabel = new Label("No active file");
  private final Label summaryLabel = new Label("Open a script or DSL file to see diagnostics.");
  private final Label statsLabel = new Label("");
  private final Label healthLabel = new Label("Waiting");
  private final ToggleButton allCountButton = new ToggleButton("All 0");
  private final ToggleButton errorCountLabel = new ToggleButton("0 Errors");
  private final ToggleButton warningCountLabel = new ToggleButton("0 Warnings");
  private final Label filteredCountLabel = new Label("No file");
  private final Label placeholderLabel = new Label("Open a script or DSL file to see diagnostics.");
  private final TextField filterField = new TextField();
  private final ComboBox<String> categoryFilter = new ComboBox<>(
      FXCollections.observableArrayList(
          CATEGORY_ALL, CATEGORY_SYNTAX, CATEGORY_FLOW, CATEGORY_ASSETS, CATEGORY_SCRIPTS, CATEGORY_CONTENT)
  );
  private final Button openSelectedButton =
      actionButton("Open", DiagnosticsToolbarIcon.Kind.OPEN);
  private final Button copyDiagnosticsButton =
      actionButton("Copy Report", DiagnosticsToolbarIcon.Kind.COPY_REPORT);
  private final Button clearFilterButton =
      actionButton("Clear Filter", DiagnosticsToolbarIcon.Kind.CLEAR_FILTER);
  private final Button refreshButton =
      actionButton("Rescan", DiagnosticsToolbarIcon.Kind.RESCAN);
  private final Button prevButton =
      actionButton("Prev", DiagnosticsToolbarIcon.Kind.PREVIOUS);
  private final Button nextButton =
      actionButton("Next", DiagnosticsToolbarIcon.Kind.NEXT);
  private final Button sortButton =
      actionButton("By Line", DiagnosticsToolbarIcon.Kind.SORT_LINE);
  private final ListView<DiagnosticRow> listView = new ListView<>();

  private final List<DiagnosticRow> allRows = new ArrayList<>();
  private boolean hasActiveFile;
  private Consumer<Integer> onOpenLine;
  private Consumer<OpenTarget> onOpenTarget;
  private Runnable onRefresh;

  public VnsDiagnosticsView() {
    getStyleClass().addAll("vns-diagnostics-root", "sidebar-tool-root");
    titleLabel.getStyleClass().addAll("vns-diagnostics-title", "sidebar-tool-title");
    fileLabel.getStyleClass().addAll("vns-diagnostics-file", "sidebar-tool-subtitle");
    summaryLabel.getStyleClass().addAll("vns-diagnostics-summary", "sidebar-tool-summary");
    summaryLabel.setWrapText(true);
    statsLabel.getStyleClass().add("vns-diagnostics-stats");
    statsLabel.setWrapText(true);
    statsLabel.setVisible(false);
    statsLabel.setManaged(false);
    statsLabel.textProperty().addListener((obs, old, text) -> {
      boolean hasText = text != null && !text.isBlank();
      statsLabel.setVisible(hasText);
      statsLabel.setManaged(hasText);
    });
    filteredCountLabel.getStyleClass().add("vns-diagnostics-filter-count");
    healthLabel.getStyleClass().addAll("vns-diagnostics-health", "vns-diagnostics-health-waiting");
    allCountButton.getStyleClass().addAll("vns-diagnostics-chip", "vns-diagnostics-chip-all");
    errorCountLabel.getStyleClass().addAll("vns-diagnostics-chip", "vns-diagnostics-chip-error");
    warningCountLabel.getStyleClass().addAll("vns-diagnostics-chip", "vns-diagnostics-chip-warning");
    ToggleGroup severityGroup = new ToggleGroup();
    configureSeverityButton(allCountButton, severityGroup, SEVERITY_ALL);
    configureSeverityButton(errorCountLabel, severityGroup, SEVERITY_ERRORS);
    configureSeverityButton(warningCountLabel, severityGroup, SEVERITY_WARNINGS);
    allCountButton.setSelected(true);

    filterField.setPromptText("Filter diagnostics...");
    filterField.setTooltip(new Tooltip("Search message, code, source, or line"));
    filterField.getStyleClass().addAll("vns-diagnostics-filter", "sidebar-tool-search-field");
    filterField.textProperty().addListener((obs, oldValue, newValue) -> applyFilter());
    filterField.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ESCAPE) {
        clearFilters();
        e.consume();
      }
    });

    categoryFilter.setValue(CATEGORY_ALL);
    categoryFilter.setFocusTraversable(false);
    categoryFilter.setPrefWidth(134);
    categoryFilter.setMinWidth(Region.USE_PREF_SIZE);
    categoryFilter.getStyleClass().add("vns-diagnostics-category-filter");
    categoryFilter.setButtonCell(new javafx.scene.control.ListCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty ? "" : item);
      }
    });
    categoryFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> applyFilter());

    placeholderLabel.getStyleClass().add("vns-diagnostics-placeholder");
    placeholderLabel.setWrapText(true);
    listView.setPlaceholder(placeholderLabel);
    listView.getStyleClass().add("vns-diagnostics-list");
    listView.setCellFactory(lv -> new DiagnosticCell(lv));
    listView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> updateActionState());
    listView.setOnMouseClicked(e -> {
      if (e.getClickCount() < 2) return;
      openSelectedRow();
    });
    listView.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER) {
        openSelectedRow();
        e.consume();
      } else if (e.getCode() == KeyCode.F4) {
        navigateDiagnostic(e.isShiftDown() ? -1 : 1);
        e.consume();
      } else if (e.getCode() == KeyCode.C && e.isShortcutDown()) {
        DiagnosticRow selected = listView.getSelectionModel().getSelectedItem();
        if (selected != null) copySingleDiagnostic(selected);
        e.consume();
      }
    });

    openSelectedButton.setOnAction(e -> openSelectedRow());
    copyDiagnosticsButton.setOnAction(e -> copyVisibleDiagnostics());
    clearFilterButton.setOnAction(e -> clearFilters());
    refreshButton.setGraphic(RefreshIcon.of(20));
    refreshButton.setTooltip(new Tooltip("Run diagnostics again for the active editor"));
    refreshButton.setOnAction(e -> {
      if (onRefresh != null) onRefresh.run();
    });

    prevButton.setTooltip(new Tooltip("Previous diagnostic (wraps around)"));
    prevButton.setOnAction(e -> navigateDiagnostic(-1));
    nextButton.setTooltip(new Tooltip("Next diagnostic (wraps around)"));
    nextButton.setOnAction(e -> navigateDiagnostic(+1));
    sortButton.setMinWidth(Region.USE_PREF_SIZE);
    sortButton.setTooltip(new Tooltip("Sorted by line number — click to sort errors first"));
    sortButton.setOnAction(e -> {
      sortMode = sortMode == SortMode.LINE ? SortMode.SEVERITY : SortMode.LINE;
      boolean bySeverity = sortMode == SortMode.SEVERITY;
      sortButton.setText(bySeverity ? "By Severity" : "By Line");
      sortButton.setGraphic(DiagnosticsToolbarIcon.of(bySeverity
          ? DiagnosticsToolbarIcon.Kind.SORT_SEVERITY
          : DiagnosticsToolbarIcon.Kind.SORT_LINE));
      sortButton.setTooltip(new Tooltip(bySeverity
          ? "Errors shown first — click to sort by line number"
          : "Sorted by line number — click to sort errors first"));
      applyFilter();
    });

    javafx.scene.layout.Region titleSpacer = new javafx.scene.layout.Region();
    HBox.setHgrow(titleSpacer, Priority.ALWAYS);
    HBox titleRow = new HBox(8, titleLabel, SidebarToolHelp.button(this, "VNS Diagnostics", """
        The Diagnostics panel scans the active script or DSL file and reports \
errors and warnings in real time.

Severity levels:
  • Error   — something that will prevent the script from running correctly \
(e.g. an undefined character ID, a missing @label target)
  • Warning — a potential problem that won't block execution but may produce \
unexpected results (e.g. unreachable labels, duplicate keys)
Clicking a diagnostic entry jumps the editor cursor to the affected line so \
you can fix it immediately. Use the filter bar to narrow results by keyword \
or category. F4 and Shift+F4 move between findings. Copy Report exports the \
visible, source-aware result set for sharing."""),
        titleSpacer, healthLabel);
    titleRow.setAlignment(Pos.CENTER_LEFT);
    titleRow.getStyleClass().add("vns-diagnostics-title-row");

    HBox countsRow = new HBox(6, allCountButton, errorCountLabel, warningCountLabel);
    countsRow.setAlignment(Pos.CENTER_LEFT);
    countsRow.getStyleClass().add("vns-diagnostics-severity-row");

    HBox filterRow = new HBox(8, filterField, categoryFilter);
    filterRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(filterField, Priority.ALWAYS);
    filterRow.getStyleClass().add("vns-diagnostics-filter-row");

    FlowPane actionRow = new FlowPane(
        6,
        6,
        sortButton,
        refreshButton,
        openSelectedButton,
        prevButton,
        nextButton,
        copyDiagnosticsButton,
        clearFilterButton,
        filteredCountLabel);
    actionRow.setAlignment(Pos.CENTER_LEFT);
    actionRow.getStyleClass().add("vns-diagnostics-action-row");

    VBox header = new VBox(6, titleRow, fileLabel, summaryLabel, statsLabel, countsRow, filterRow, actionRow);
    header.getStyleClass().add("sidebar-tool-header");
    setTop(header);
    setCenter(listView);
    setPadding(new Insets(0));
    updateActionState();
  }

  public void setOnOpenLine(Consumer<Integer> onOpenLine) {
    this.onOpenLine = onOpenLine;
  }

  public void setOnOpenTarget(Consumer<OpenTarget> onOpenTarget) {
    this.onOpenTarget = onOpenTarget;
  }

  public void setOnRefresh(Runnable onRefresh) {
    this.onRefresh = onRefresh;
    updateActionState();
  }

  public void clear() {
    hasActiveFile = false;
    fileLabel.setText("No active file");
    fileLabel.setTooltip(null);
    summaryLabel.setText("Open a script or DSL file to see diagnostics.");
    statsLabel.setText("");
    allCountButton.setText("All 0");
    errorCountLabel.setText("0 Errors");
    warningCountLabel.setText("0 Warnings");
    setHealth("Waiting", "waiting");
    allRows.clear();
    listView.getItems().clear();
    updatePlaceholder(0);
    updateActionState();
  }

  public void setAnalysis(File scriptFile, VnsScriptAnalyzer.Analysis analysis) {
    if (scriptFile == null || analysis == null) {
      clear();
      return;
    }

    List<Diagnostic> diagnostics = new ArrayList<>();
    for (VnsScriptAnalyzer.Diagnostic issue : analysis.diagnostics()) {
      // Advisory hints (e.g. large-timeline-block performance notices) surface inline
      // in the editor gutter/minimap only — this panel is strictly errors/warnings.
      if (issue.info()) continue;
      diagnostics.add(Diagnostic.fromVns(issue));
    }
    setDiagnostics(scriptFile, "VNS", analysis.source(), buildStatsSummary(analysis.stats()), diagnostics);
  }

  public void setDiagnostics(File file,
                             String languageLabel,
                             String source,
                             String statsSummary,
                             List<Diagnostic> diagnostics) {
    if (file == null) {
      clear();
      return;
    }

    String label = languageLabel == null || languageLabel.isBlank() ? "File" : languageLabel.trim();
    hasActiveFile = true;
    fileLabel.setText(file.getName());
    fileLabel.setTooltip(new javafx.scene.control.Tooltip(file.getAbsolutePath()));
    statsLabel.setText(statsSummary == null ? "" : statsSummary);
    allRows.clear();

    int errors = 0;
    int warnings = 0;
    List<Diagnostic> safeDiagnostics = diagnostics == null ? List.of() : diagnostics;
    for (Diagnostic issue : safeDiagnostics) {
      if (issue == null) continue;
      allRows.add(buildRow(source, issue));
      if (issue.warning()) warnings++; else errors++;
    }
    errorCountLabel.setText(errors + (errors == 1 ? " Error" : " Errors"));
    warningCountLabel.setText(warnings + (warnings == 1 ? " Warning" : " Warnings"));
    allCountButton.setText("All " + (errors + warnings));

    if (errors == 0 && warnings == 0) {
      summaryLabel.setText(label + ": no issues found.");
      setHealth("Clean", "clean");
    } else if (errors > 0 && warnings > 0) {
      summaryLabel.setText(label + ": " + errors + " errors, " + warnings + " warnings");
      setHealth(errors + " blocking", "error");
    } else if (errors > 0) {
      summaryLabel.setText(label + ": " + errors + (errors == 1 ? " error" : " errors"));
      setHealth(errors + " blocking", "error");
    } else {
      summaryLabel.setText(label + ": " + warnings + (warnings == 1 ? " warning" : " warnings"));
      setHealth(warnings + " to review", "warning");
    }

    applyFilter();
  }

  private void applyFilter() {
    String query = filterField.getText();
    String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    DiagnosticRow previousSelection = listView.getSelectionModel().getSelectedItem();
    List<DiagnosticRow> filtered = new ArrayList<>();
    for (DiagnosticRow row : allRows) {
      if (!matchesSeverity(row)) continue;
      if (!matchesCategory(row)) continue;
      if (!normalized.isEmpty() && !row.searchText().contains(normalized)) continue;
      filtered.add(row);
    }
    if (sortMode == SortMode.SEVERITY) {
      filtered.sort(Comparator.comparingInt((DiagnosticRow r) -> r.issue().warning() ? 1 : 0)
          .thenComparingInt(DiagnosticRow::oneBasedLine));
    }
    listView.setItems(FXCollections.observableArrayList(filtered));
    if (previousSelection != null && filtered.contains(previousSelection)) {
      listView.getSelectionModel().select(previousSelection);
    } else if (!filtered.isEmpty()) {
      listView.getSelectionModel().selectFirst();
    }
    updatePlaceholder(filtered.size());
    updateActionState();
  }

  private void openSelectedRow() {
    DiagnosticRow row = listView.getSelectionModel().getSelectedItem();
    if (row == null) return;

    OpenTarget target = row.toOpenTarget();
    if (onOpenTarget != null) {
      onOpenTarget.accept(target);
      return;
    }
    if (onOpenLine != null) {
      onOpenLine.accept(target.oneBasedLine());
    }
  }

  private boolean matchesSeverity(DiagnosticRow row) {
    ToggleButton selectedButton = selectedSeverityButton();
    String selected = selectedButton == null ? SEVERITY_ALL : String.valueOf(selectedButton.getUserData());
    if (SEVERITY_ERRORS.equals(selected)) {
      return !row.issue().warning();
    }
    if (SEVERITY_WARNINGS.equals(selected)) {
      return row.issue().warning();
    }
    return true;
  }

  private boolean matchesCategory(DiagnosticRow row) {
    String selected = categoryFilter.getValue();
    return selected == null
        || CATEGORY_ALL.equals(selected)
        || selected.equals(categoryForKind(row.issue().kind()));
  }

  private void clearFilters() {
    boolean changed = filterField.getText() != null && !filterField.getText().isBlank();
    ToggleButton selectedButton = selectedSeverityButton();
    changed = changed || selectedButton != allCountButton;
    changed = changed || !CATEGORY_ALL.equals(categoryFilter.getValue());
    filterField.clear();
    allCountButton.setSelected(true);
    categoryFilter.setValue(CATEGORY_ALL);
    if (!changed) applyFilter();
  }

  private void navigateDiagnostic(int delta) {
    var items = listView.getItems();
    if (items.isEmpty()) return;
    int current = listView.getSelectionModel().getSelectedIndex();
    int next = current < 0 ? 0 : (current + delta + items.size()) % items.size();
    listView.getSelectionModel().select(next);
    listView.scrollTo(next);
    openSelectedRow();
  }

  private void copySingleDiagnostic(DiagnosticRow row) {
    StringBuilder out = new StringBuilder();
    out.append(row.level()).append(" ").append(row.locationText()).append(" ")
        .append(row.kindLabel()).append(": ").append(row.issue().message());
    if (!row.sourceLine().isBlank()) {
      out.append(System.lineSeparator()).append("  ").append(row.sourceLine());
    }
    ClipboardContent content = new ClipboardContent();
    content.putString(out.toString().strip());
    Clipboard.getSystemClipboard().setContent(content);
  }

  private void copyVisibleDiagnostics() {
    List<DiagnosticRow> rows = listView.getItems();
    if (rows == null || rows.isEmpty()) return;

    StringBuilder out = new StringBuilder();
    out.append("Diagnostics — ").append(fileLabel.getText()).append(System.lineSeparator());
    if (!summaryLabel.getText().isBlank()) {
      out.append(summaryLabel.getText()).append(System.lineSeparator());
    }
    if (!statsLabel.getText().isBlank()) {
      out.append(statsLabel.getText()).append(System.lineSeparator());
    }
    out.append(System.lineSeparator());
    for (DiagnosticRow row : rows) {
      out.append(row.level())
          .append(" ")
          .append(row.locationText())
          .append(" ")
          .append(row.kindLabel())
          .append(": ")
          .append(row.issue().message());
      if (!row.sourceLine().isBlank()) {
        out.append(System.lineSeparator())
            .append("  ")
            .append(row.sourceLine());
      }
      out.append(System.lineSeparator());
    }

    ClipboardContent content = new ClipboardContent();
    content.putString(out.toString().strip());
    Clipboard.getSystemClipboard().setContent(content);
    summaryLabel.setText("Copied " + rows.size() + " visible diagnostic" + (rows.size() == 1 ? "." : "s."));
  }

  private void updatePlaceholder(int filteredCount) {
    if (!hasActiveFile) {
      placeholderLabel.setText("Open a script or DSL file to see diagnostics.");
      filteredCountLabel.setText("No file");
      return;
    }
    if (allRows.isEmpty()) {
      placeholderLabel.setText("No issues found for this file.");
      filteredCountLabel.setText("0 issues");
      return;
    }
    if (filteredCount == 0) {
      placeholderLabel.setText("No diagnostics match the current filter.");
      filteredCountLabel.setText("Showing 0 of " + allRows.size());
      return;
    }
    filteredCountLabel.setText("Showing " + filteredCount + " of " + allRows.size());
  }

  private void updateActionState() {
    boolean hasVisibleRows = !listView.getItems().isEmpty();
    boolean hasSelection = listView.getSelectionModel().getSelectedItem() != null;
    boolean hasFilter = (filterField.getText() != null && !filterField.getText().isBlank())
        || selectedSeverityButton() != allCountButton
        || !CATEGORY_ALL.equals(categoryFilter.getValue());
    refreshButton.setDisable(!hasActiveFile || onRefresh == null);
    openSelectedButton.setDisable(!hasSelection);
    prevButton.setDisable(!hasVisibleRows);
    nextButton.setDisable(!hasVisibleRows);
    copyDiagnosticsButton.setDisable(!hasVisibleRows);
    clearFilterButton.setDisable(!hasFilter);
  }

  private ToggleButton selectedSeverityButton() {
    if (allCountButton.getToggleGroup() == null) return allCountButton;
    if (allCountButton.getToggleGroup().getSelectedToggle() instanceof ToggleButton button) {
      return button;
    }
    return allCountButton;
  }

  private void setHealth(String text, String tone) {
    healthLabel.setText(text == null ? "" : text);
    healthLabel.getStyleClass().removeAll(
        "vns-diagnostics-health-waiting",
        "vns-diagnostics-health-clean",
        "vns-diagnostics-health-warning",
        "vns-diagnostics-health-error");
    healthLabel.getStyleClass().add("vns-diagnostics-health-" + tone);
  }

  private static DiagnosticRow buildRow(String source, Diagnostic issue) {
    int line = Math.max(1, issue.line() + 1);
    int column = computeOneBasedColumn(source, issue.line(), issue.start());
    String level = issue.warning() ? "Warning" : "Error";
    String kindLabel = formatKind(issue.kind());
    String location = "L" + line + ":" + column;
    String display = level + "  " + location + "  " + kindLabel + "  " + issue.message();
    SourcePreview preview = buildSourcePreview(source, issue.line(), column);
    String searchText = buildSearchText(issue, line, column, level, kindLabel, preview.sourceLine());
    return new DiagnosticRow(issue, line, column, level, kindLabel, display, searchText, preview.sourceLine(), preview.caretLine());
  }

  static int computeOneBasedColumn(String source, int zeroBasedLine, int offset) {
    String text = source == null ? "" : source;
    int clampedOffset = Math.max(0, Math.min(offset, text.length()));
    int lineStart = lineStartOffset(text, zeroBasedLine);
    return Math.max(1, clampedOffset - lineStart + 1);
  }

  static int lineStartOffset(String source, int zeroBasedLine) {
    if (source == null || source.isEmpty() || zeroBasedLine <= 0) return 0;
    int targetLine = zeroBasedLine;
    int currentLine = 0;
    for (int i = 0; i < source.length(); i++) {
      if (source.charAt(i) != '\n') continue;
      currentLine++;
      if (currentLine == targetLine) {
        return i + 1;
      }
    }
    return source.length();
  }

  static String buildSearchText(VnsScriptAnalyzer.Diagnostic issue,
                                int oneBasedLine,
                                int oneBasedColumn,
                                String level,
                                String kindLabel) {
    return buildSearchText(Diagnostic.fromVns(issue), oneBasedLine, oneBasedColumn, level, kindLabel, "");
  }

  static String buildSearchText(Diagnostic issue,
                                int oneBasedLine,
                                int oneBasedColumn,
                                String level,
                                String kindLabel,
                                String sourceLine) {
    return (
        level + " " +
        issue.kind() + " " +
        kindLabel + " " +
        issue.message() + " " +
        sourceLine + " " +
        "line " + oneBasedLine + " column " + oneBasedColumn + " " +
        "l" + oneBasedLine + ":" + oneBasedColumn
    ).toLowerCase(Locale.ROOT);
  }

  static SourcePreview buildSourcePreview(String source, int zeroBasedLine, int oneBasedColumn) {
    String line = sourceLine(source, zeroBasedLine).replace('\t', ' ');
    if (line.isBlank()) {
      return new SourcePreview("(blank line)", "^");
    }

    int caretIndex = Math.max(0, oneBasedColumn - 1);
    int maxWidth = 140;
    if (line.length() <= maxWidth) {
      return new SourcePreview(line, " ".repeat(Math.min(caretIndex, line.length())) + "^");
    }

    int windowStart = Math.max(0, caretIndex - 56);
    windowStart = Math.min(windowStart, Math.max(0, line.length() - maxWidth));
    int windowEnd = Math.min(line.length(), windowStart + maxWidth);
    String prefix = windowStart > 0 ? "..." : "";
    String suffix = windowEnd < line.length() ? "..." : "";
    String visible = prefix + line.substring(windowStart, windowEnd) + suffix;
    int visibleCaret = Math.max(0, caretIndex - windowStart + prefix.length());
    return new SourcePreview(visible, " ".repeat(Math.min(visibleCaret, visible.length())) + "^");
  }

  static String sourceLine(String source, int zeroBasedLine) {
    if (source == null || source.isEmpty() || zeroBasedLine < 0) return "";
    int start = lineStartOffset(source, zeroBasedLine);
    if (start >= source.length()) return "";
    int end = source.indexOf('\n', start);
    if (end < 0) end = source.length();
    if (end > start && source.charAt(end - 1) == '\r') end--;
    return source.substring(start, end);
  }

  private static String formatKind(String kind) {
    if (kind == null || kind.isBlank()) return "Issue";
    String normalized = kind.replace('_', ' ').trim();
    if (normalized.isEmpty()) return "Issue";
    return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
  }

  static String categoryForKind(String kind) {
    String normalized = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
    if (containsAny(normalized, "asset", "background", "image", "audio", "voice", "sfx", "bgm")) {
      return CATEGORY_ASSETS;
    }
    if (containsAny(normalized, "script", "include", "interop", "provider", "jes")) {
      return CATEGORY_SCRIPTS;
    }
    if (containsAny(normalized, "label", "flow", "jump", "goto", "return", "unreachable", "branch")) {
      return CATEGORY_FLOW;
    }
    if (containsAny(normalized, "parse", "syntax", "invalid", "malformed", "duplicate", "unknown")) {
      return CATEGORY_SYNTAX;
    }
    return CATEGORY_CONTENT;
  }

  private static boolean containsAny(String value, String... tokens) {
    if (value == null || value.isBlank() || tokens == null) return false;
    for (String token : tokens) {
      if (token != null && !token.isBlank() && value.contains(token)) return true;
    }
    return false;
  }

  private void configureSeverityButton(ToggleButton button, ToggleGroup group, String severity) {
    button.setToggleGroup(group);
    button.setUserData(severity);
    button.setMnemonicParsing(false);
    button.setFocusTraversable(false);
    button.setOnAction(e -> applyFilter());
    button.setTooltip(new Tooltip("Show " + severity.toLowerCase(Locale.ROOT) + " diagnostics"));
  }

  private static String buildStatsSummary(VnsScriptAnalyzer.ScriptStats stats) {
    if (stats == null) return "";
    String minutes = stats.estimatedPlaytimeMinutes() <= 0
        ? "<0.1 min"
        : String.format(Locale.ROOT, "%.1f min", stats.estimatedPlaytimeMinutes());
    return stats.wordCount() + " words"
        + " | " + stats.dialogueLineCount() + " dialogue"
        + " | " + stats.choiceBranchCount() + " choices"
        + " | " + stats.labelCount() + " labels"
        + " | ~" + minutes;
  }

  private static Button actionButton(String text, DiagnosticsToolbarIcon.Kind iconKind) {
    Button button = new Button(text);
    button.getStyleClass().addAll("sidebar-tool-btn", "vns-diagnostics-action-button");
    button.setGraphic(DiagnosticsToolbarIcon.of(iconKind));
    button.setGraphicTextGap(6);
    button.setMnemonicParsing(false);
    button.setFocusTraversable(false);
    return button;
  }

  record SourcePreview(String sourceLine, String caretLine) {
  }

  public record Diagnostic(boolean warning,
                           String kind,
                           String message,
                           int start,
                           int end,
                           int line) {
    public Diagnostic {
      kind = kind == null || kind.isBlank() ? "issue" : kind.trim();
      message = message == null ? "" : message.trim();
      start = Math.max(0, start);
      end = Math.max(start, end);
      line = Math.max(0, line);
    }

    public static Diagnostic error(String kind, String message, int start, int end, int line) {
      return new Diagnostic(false, kind, message, start, end, line);
    }

    public static Diagnostic warning(String kind, String message, int start, int end, int line) {
      return new Diagnostic(true, kind, message, start, end, line);
    }

    static Diagnostic fromVns(VnsScriptAnalyzer.Diagnostic issue) {
      if (issue == null) return error("issue", "", 0, 0, 0);
      return new Diagnostic(issue.warning(), issue.kind(), issue.message(), issue.start(), issue.end(), issue.line());
    }
  }

  public record OpenTarget(int oneBasedLine,
                           int oneBasedColumn,
                           int startOffset,
                           int endOffset,
                           Diagnostic issue) {
  }

  private record DiagnosticRow(Diagnostic issue,
                               int oneBasedLine,
                               int oneBasedColumn,
                               String level,
                               String kindLabel,
                               String displayText,
                               String searchText,
                               String sourceLine,
                               String caretLine) {
    String locationText() {
      return "L" + oneBasedLine + ":" + oneBasedColumn;
    }

    OpenTarget toOpenTarget() {
      return new OpenTarget(oneBasedLine, oneBasedColumn, issue.start(), issue.end(), issue);
    }
  }

  private final class DiagnosticCell extends ListCell<DiagnosticRow> {
    private final Label severityBadge = new Label();
    private final Label locationBadge = new Label();
    private final Label kindBadge = new Label();
    private final Label messageLabel = new Label();
    private final Label sourceLabel = new Label();
    private final Label caretLabel = new Label();
    private final Label hintLabel = new Label("Enter or double-click to jump");
    private final HBox metaRow = new HBox(6, severityBadge, locationBadge, kindBadge);
    private final VBox content = new VBox(6, metaRow, messageLabel, sourceLabel, caretLabel, hintLabel);
    private final ContextMenu rowMenu;

    private DiagnosticCell(ListView<DiagnosticRow> parentList) {
      severityBadge.getStyleClass().add("vns-diagnostics-badge");
      locationBadge.getStyleClass().addAll("vns-diagnostics-badge", "vns-diagnostics-badge-location");
      kindBadge.getStyleClass().addAll("vns-diagnostics-badge", "vns-diagnostics-badge-kind");
      messageLabel.getStyleClass().add("vns-diagnostics-message");
      messageLabel.setWrapText(true);
      if (parentList != null) {
        messageLabel.prefWidthProperty().bind(parentList.widthProperty().subtract(60));
      }
      sourceLabel.getStyleClass().add("vns-diagnostics-source-line");
      sourceLabel.setWrapText(true);
      caretLabel.getStyleClass().add("vns-diagnostics-caret-line");
      hintLabel.getStyleClass().add("vns-diagnostics-hint");
      hintLabel.visibleProperty().bind(selectedProperty());
      hintLabel.managedProperty().bind(selectedProperty());
      metaRow.setAlignment(Pos.CENTER_LEFT);
      content.getStyleClass().add("vns-diagnostics-row");
      content.setFillWidth(true);

      MenuItem jumpItem = new MenuItem("Jump to Line");
      jumpItem.setOnAction(e -> {
        DiagnosticRow row = getItem();
        if (row != null) { listView.getSelectionModel().select(row); openSelectedRow(); }
      });
      MenuItem copyItem = new MenuItem("Copy Diagnostic");
      copyItem.setOnAction(e -> {
        DiagnosticRow row = getItem();
        if (row != null) copySingleDiagnostic(row);
      });
      rowMenu = new ContextMenu(jumpItem, copyItem);
    }

    @Override
    protected void updateItem(DiagnosticRow item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setText(null);
        setGraphic(null);
        setContextMenu(null);
        getStyleClass().remove("vns-diagnostics-cell");
        return;
      }
      setContextMenu(rowMenu);

      if (!getStyleClass().contains("vns-diagnostics-cell")) {
        getStyleClass().add("vns-diagnostics-cell");
      }
      content.getStyleClass().removeAll("vns-diagnostics-row-error", "vns-diagnostics-row-warning");
      severityBadge.getStyleClass().removeAll("vns-diagnostics-badge-error", "vns-diagnostics-badge-warning");
      content.getStyleClass().add(item.issue().warning() ? "vns-diagnostics-row-warning" : "vns-diagnostics-row-error");
      severityBadge.getStyleClass().add(item.issue().warning() ? "vns-diagnostics-badge-warning" : "vns-diagnostics-badge-error");

      severityBadge.setText(item.level());
      locationBadge.setText(item.locationText());
      kindBadge.setText(item.kindLabel());
      messageLabel.setText(item.issue().message());
      sourceLabel.setText(item.sourceLine());
      caretLabel.setText(item.caretLine());

      setText(null);
      setGraphic(content);
    }
  }
}
