package com.jvn.editor.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class JavaCodeEditor extends BorderPane {

  public record Diagnostic(int line, String key, String message, String quickFix, Severity severity) {
    public enum Severity { INFO, WARNING, ERROR }

    public Diagnostic(int line, String key, String message, String quickFix) {
      this(line, key, message, quickFix, Severity.WARNING);
    }
  }

  private final CodeArea codeArea = new CodeArea();
  private Consumer<String> onTextChanged;
  private boolean suppressEvent = false;
  private EditorSearchBar searchBar;
  private boolean searchBarVisible = false;
  private boolean dslMode = false;
  private final Map<Integer, Diagnostic> diagnosticsByLine = new HashMap<>();
  private final List<Diagnostic> diagnosticsList = new ArrayList<>();
  private ListView<String> diagnosticsPanel;
  private boolean diagnosticsPanelVisible = false;
  private Consumer<Diagnostic> onQuickFixRequested;
  private double fontSizePx = 13.0;
  private final Label statusPrimaryLabel = new Label("Ready");
  private final Label statusSecondaryLabel = new Label("Ln 1, Col 1");
  private final VBox bottomStack = new VBox();

  private static final String[] KEYWORDS = new String[] {
    "abstract","assert","break","case","catch","class","const","continue",
    "default","do","else","enum","extends","final","finally","for","goto","if",
    "implements","import","instanceof","interface","native","new","package","private",
    "protected","public","return","static","strictfp","super","switch","synchronized","this",
    "throw","throws","transient","try","volatile","while"
  };

  private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
  private static final String ANNOTATION_PATTERN = "@[A-Za-z_]\\w*";
  private static final String TYPE_PATTERN = "\\b(?:boolean|byte|char|short|int|long|float|double|void"
    + "|String|Object|List|Map|Set|Optional|File|Path|var)\\b";
  private static final String CONSTANT_PATTERN = "\\b(?:true|false|null)\\b";
  private static final String PAREN_PATTERN = "[(){}\\[\\]]";
  private static final String COLON_COMMA_PATTERN = "[;.,]";
  private static final String STRING_PATTERN = "\"([^\\\\\"]|\\\\.)*\"";
  private static final String CHAR_PATTERN = "'([^\\\\']|\\\\.)'";
  private static final String NUMBER_PATTERN = "-?\\b\\d+(?:\\.\\d+)?[fFdDlL]?\\b";
  private static final String SL_COMMENT_PATTERN = "//[^\\n]*";
  private static final String ML_COMMENT_PATTERN = "/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/";

  private static final Pattern PATTERN = Pattern.compile(
      "(?<MLCOMMENT>"   + ML_COMMENT_PATTERN   + ")"
    + "|(?<SLCOMMENT>"  + SL_COMMENT_PATTERN   + ")"
    + "|(?<STRING>"     + STRING_PATTERN        + ")"
    + "|(?<CHARLITERAL>"+ CHAR_PATTERN          + ")"
    + "|(?<ANNOTATION>" + ANNOTATION_PATTERN    + ")"
    + "|(?<CONSTANT>"   + CONSTANT_PATTERN      + ")"
    + "|(?<TYPE>"       + TYPE_PATTERN          + ")"
    + "|(?<KEYWORD>"    + KEYWORD_PATTERN       + ")"
    + "|(?<NUMBER>"     + NUMBER_PATTERN        + ")"
    + "|(?<PAREN>"      + PAREN_PATTERN         + ")"
    + "|(?<PUNCT>"      + COLON_COMMA_PATTERN   + ")"
  );

  public JavaCodeEditor() {
    getStyleClass().add("text-editor-root");
    if (!codeArea.getStyleClass().contains("code-area")) {
      codeArea.getStyleClass().add("code-area");
    }
    IntFunction<Node> lineNumberFactory = LineNumberFactory.get(codeArea);
    codeArea.setParagraphGraphicFactory(line -> buildGutterGraphic(line, lineNumberFactory));
    codeArea.textProperty().addListener((obs, oldText, newText) -> {
      applyHighlighting(newText);
      updateStatusBar();
      if (!suppressEvent && onTextChanged != null) onTextChanged.accept(newText);
    });
    applyHighlighting("");

    VirtualizedScrollPane<CodeArea> sp = new VirtualizedScrollPane<>(codeArea);
    setCenter(sp);

    String css = EditorTheme.stylesheetUrl();
    if (!css.isEmpty()) {
      getStylesheets().add(css);
      codeArea.getStylesheets().add(css);
    }

    setupSearchBar();
    setupDiagnosticsPanel();
    setupStatusBar();
    applyFontSize();
  }

  private Node buildGutterGraphic(int paragraphIndex, IntFunction<Node> lineNumberFactory) {
    int lineNo = paragraphIndex + 1;
    Node lineNum = lineNumberFactory.apply(paragraphIndex);
    Diagnostic diag = diagnosticsByLine.get(lineNo);
    if (diag == null) return CodeEditorGutter.wrap(lineNum);

    String icon = switch (diag.severity()) {
      case ERROR -> "\u274c";
      case WARNING -> "\u26a0";
      case INFO -> "\u2139";
    };
    String color = switch (diag.severity()) {
      case ERROR -> "#e74c3c";
      case WARNING -> "#f0b673";
      case INFO -> "#b5b5b5";
    };
    Label marker = new Label(icon);
    marker.setStyle("-fx-font-size: 10px; -fx-text-fill: " + color + "; -fx-padding: 0 2 0 0;");
    marker.setMinWidth(14);
    marker.setPrefWidth(14);

    StringBuilder tip = new StringBuilder();
    tip.append(diag.key()).append(": ").append(diag.message());
    if (diag.quickFix() != null && !diag.quickFix().isBlank()) {
      tip.append("\nQuick fix: ").append(diag.quickFix());
    }
    Tooltip tt = new Tooltip(tip.toString());
    tt.setWrapText(true);
    tt.setMaxWidth(400);
    marker.setTooltip(tt);

    marker.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
      if (e.getClickCount() == 2 && onQuickFixRequested != null) {
        onQuickFixRequested.accept(diag);
        e.consume();
      }
    });

    StackPane numberWithMarker = new StackPane(lineNum, marker);
    marker.setViewOrder(-2); // Keep the action above the line number’s painted background.
    StackPane.setAlignment(marker, Pos.CENTER_LEFT);
    StackPane.setMargin(marker, new Insets(0, 0, 0, 3));
    return CodeEditorGutter.wrap(numberWithMarker);
  }

  private void setupDiagnosticsPanel() {
    diagnosticsPanel = new ListView<>();
    diagnosticsPanel.setMaxHeight(120);
    diagnosticsPanel.setPrefHeight(100);
    diagnosticsPanel.getStyleClass().add("code-editor-diagnostics-list");
    diagnosticsPanel.setVisible(false);
    diagnosticsPanel.setManaged(false);
    diagnosticsPanel.setOnMouseClicked(e -> {
      String selected = diagnosticsPanel.getSelectionModel().getSelectedItem();
      if (selected != null && e.getClickCount() == 1) {
        int lineNo = extractLineFromDiagnosticText(selected);
        if (lineNo > 0) goToLine(lineNo);
      }
    });
  }

  private void setupStatusBar() {
    HBox statusBar = new HBox(10);
    statusBar.getStyleClass().add("code-editor-status-bar");
    statusBar.setPadding(new Insets(4, 10, 4, 10));
    statusBar.setAlignment(Pos.CENTER_LEFT);

    statusPrimaryLabel.getStyleClass().add("code-editor-status-primary");
    statusSecondaryLabel.getStyleClass().add("code-editor-status-secondary");

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    statusBar.getChildren().addAll(statusPrimaryLabel, spacer, statusSecondaryLabel);

    bottomStack.getChildren().setAll(diagnosticsPanel, statusBar);
    setBottom(bottomStack);

    codeArea.caretPositionProperty().addListener((obs, oldVal, newVal) -> updateStatusBar());
    codeArea.currentParagraphProperty().addListener((obs, oldVal, newVal) -> updateStatusBar());
    updateStatusBar();
  }

  private void updateStatusBar() {
    int line = codeArea.getCurrentParagraph() + 1;
    int col = codeArea.getCaretColumn() + 1;
    int lines = Math.max(1, codeArea.getParagraphs().size());
    int selected = codeArea.getSelectedText() == null ? 0 : codeArea.getSelectedText().length();
    long errors = diagnosticsList.stream().filter(d -> d.severity() == Diagnostic.Severity.ERROR).count();
    long warnings = diagnosticsList.stream().filter(d -> d.severity() == Diagnostic.Severity.WARNING).count();
    if (errors > 0 || warnings > 0) {
      statusPrimaryLabel.setText(errors + " errors, " + warnings + " warnings");
    } else {
      statusPrimaryLabel.setText("No diagnostics");
    }
    String selection = selected > 0 ? "  |  Sel " + selected : "";
    statusSecondaryLabel.setText("Ln " + line + ", Col " + col + "  |  " + lines + " lines" + selection);
  }

  private void setupSearchBar() {
    searchBar = new EditorSearchBar();
    searchBar.setCodeArea(codeArea);
    searchBar.setOnClose(this::hideSearchBar);
    searchBar.setVisible(false);
    searchBar.setManaged(false);

    addEventFilter(KeyEvent.KEY_PRESSED, e -> {
      if ((e.isMetaDown() || e.isControlDown()) && e.getCode() == KeyCode.F) {
        showSearchBar();
        e.consume();
      } else if (e.getCode() == KeyCode.ESCAPE && searchBarVisible) {
        hideSearchBar();
        e.consume();
      }
    });
  }

  public void showSearchBar() {
    if (!searchBarVisible) {
      setTop(searchBar);
      searchBar.setVisible(true);
      searchBar.setManaged(true);
      searchBarVisible = true;
    }
    String selected = codeArea.getSelectedText();
    if (selected != null && !selected.isEmpty() && !selected.contains("\n")) {
      searchBar.setSearchText(selected);
    }
    searchBar.focus();
  }

  public void hideSearchBar() {
    if (searchBarVisible) {
      setTop(null);
      searchBar.setVisible(false);
      searchBar.setManaged(false);
      searchBarVisible = false;
      codeArea.requestFocus();
    }
  }

  public String getText() { return codeArea.getText(); }
  public void setText(String s) { codeArea.replaceText(s == null ? "" : s); }

  /** Applies a visual authoring operation as one independent text edit. */
  void replaceDocumentEdit(String next) {
    String before = codeArea.getText();
    int start = 0;
    int beforeEnd = before.length();
    int afterEnd = next.length();
    while (start < Math.min(beforeEnd, afterEnd) && before.charAt(start) == next.charAt(start)) start++;
    while (beforeEnd > start && afterEnd > start && before.charAt(beforeEnd - 1) == next.charAt(afterEnd - 1)) {
      beforeEnd--;
      afterEnd--;
    }
    if (start == beforeEnd && start == afterEnd) return;
    codeArea.getUndoManager().preventMerge();
    codeArea.replaceText(start, beforeEnd, next.substring(start, afterEnd));
    codeArea.getUndoManager().preventMerge();
  }
  public void setTextNoEvent(String s) {
    try {
      suppressEvent = true;
      setText(s);
    } finally {
      suppressEvent = false;
    }
  }
  public void undo() { codeArea.undo(); }
  public void redo() { codeArea.redo(); }

  public void setFontSizePx(double fontSizePx) {
    this.fontSizePx = Math.max(8.0, Math.min(30.0, fontSizePx));
    applyFontSize();
  }

  private void applyFontSize() {
    codeArea.setStyle("-fx-font-size: " + (int) fontSizePx + "px;");
  }
  public void setOnTextChanged(Consumer<String> c) { this.onTextChanged = c; }
  public void setOnQuickFixRequested(Consumer<Diagnostic> c) { this.onQuickFixRequested = c; }

  /**
   * Set diagnostics to display as gutter markers and in the diagnostics panel.
   * Each diagnostic is keyed by line number; only one per line is shown in the gutter.
   * Pass null or empty list to clear.
   */
  public void setDiagnostics(List<Diagnostic> diagnostics) {
    diagnosticsByLine.clear();
    diagnosticsList.clear();
    if (diagnostics != null) {
      for (Diagnostic d : diagnostics) {
        diagnosticsList.add(d);
        Diagnostic existing = diagnosticsByLine.get(d.line());
        if (existing == null || d.severity().ordinal() > existing.severity().ordinal()) {
          diagnosticsByLine.put(d.line(), d);
        }
      }
    }
    // Refresh gutter by forcing paragraph graphic recalculation
    IntFunction<Node> lineNumberFactory = LineNumberFactory.get(codeArea);
    codeArea.setParagraphGraphicFactory(line -> buildGutterGraphic(line, lineNumberFactory));

    // Update diagnostics panel
    diagnosticsPanel.getItems().clear();
    for (Diagnostic d : diagnosticsList) {
      String prefix = switch (d.severity()) {
        case ERROR -> "\u274c";
        case WARNING -> "\u26a0";
        case INFO -> "\u2139";
      };
      String text = prefix + " L" + d.line() + " [" + d.key() + "] " + d.message();
      if (d.quickFix() != null && !d.quickFix().isBlank()) {
        text += "  \u2192 " + d.quickFix();
      }
      diagnosticsPanel.getItems().add(text);
    }
    boolean hasIssues = !diagnosticsList.isEmpty();
    if (hasIssues && !diagnosticsPanelVisible) {
      diagnosticsPanel.setVisible(true);
      diagnosticsPanel.setManaged(true);
      diagnosticsPanelVisible = true;
    } else if (!hasIssues && diagnosticsPanelVisible) {
      diagnosticsPanel.setVisible(false);
      diagnosticsPanel.setManaged(false);
      diagnosticsPanelVisible = false;
    }
    updateStatusBar();
  }

  /**
   * Navigate the editor caret to the given 1-indexed line number.
   */
  public void goToLine(int lineNo) {
    int idx = lineNo - 1;
    if (idx < 0 || idx >= codeArea.getParagraphs().size()) return;
    codeArea.moveTo(idx, 0);
    codeArea.requestFollowCaret();
    codeArea.requestFocus();
  }

  public List<Diagnostic> getDiagnostics() {
    return Collections.unmodifiableList(diagnosticsList);
  }

  private static int extractLineFromDiagnosticText(String text) {
    if (text == null) return -1;
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("L(\\d+)").matcher(text);
    if (m.find()) {
      try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    }
    return -1;
  }

  /**
   * Switch this editor to DSL properties-based syntax highlighting
   * instead of Java keyword highlighting. Call once after construction.
   */
  public void useDslHighlighting() {
    dslMode = true;
    applyHighlighting(codeArea.getText());
  }

  private void applyHighlighting(String text) {
    String safe = text == null ? "" : text;
    if (dslMode) {
      codeArea.setStyleSpans(0, DslSyntaxHighlighter.properties().computeHighlighting(safe));
    } else {
      codeArea.setStyleSpans(0, computeHighlighting(safe));
    }
  }

  private static StyleSpans<Collection<String>> computeHighlighting(String text) {
    Matcher matcher = PATTERN.matcher(text);
    int lastKwEnd = 0;
    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
    while (matcher.find()) {
      String styleClass =
        matcher.group("MLCOMMENT")   != null ? "comment"    :
        matcher.group("SLCOMMENT")   != null ? "comment"    :
        matcher.group("STRING")      != null ? "string"     :
        matcher.group("CHARLITERAL") != null ? "string"     :
        matcher.group("ANNOTATION")  != null ? "annotation" :
        matcher.group("CONSTANT")    != null ? "constant"   :
        matcher.group("TYPE")        != null ? "type"       :
        matcher.group("KEYWORD")     != null ? "keyword"    :
        matcher.group("NUMBER")      != null ? "number"     :
        matcher.group("PAREN")       != null ? "punct"      :
        matcher.group("PUNCT")       != null ? "punct"      : null;
      assert styleClass != null;
      spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
      spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
      lastKwEnd = matcher.end();
    }
    spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
    return spansBuilder.create();
  }
}
