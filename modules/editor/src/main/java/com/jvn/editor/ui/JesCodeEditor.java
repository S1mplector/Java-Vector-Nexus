package com.jvn.editor.ui;

import com.jvn.core.script.SyntaxRules;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class JesCodeEditor extends BorderPane {
  public enum LintMode {
    JES_DOCUMENT,
    TIMELINE_BLOCK
  }

  private final CodeArea codeArea = new CodeArea();
  private CodeAutoCompleter completer;
  private File projectRoot;
  private final Label lintLabel = new Label();
  private int lastErrorLine = -1;
  private final List<String> cachedEntities = new ArrayList<>();
  private final List<String> cachedLabels = new ArrayList<>();
  private Consumer<String> onTextChanged;
  private boolean suppressTextChanged = false;
  private double fontSizePx = 13.0;
  private LintMode lintMode = LintMode.JES_DOCUMENT;
  private final Label statusLabel = new Label("Ln 1, Col 1");

  public JesCodeEditor() {
    getStyleClass().add("text-editor-root");
    if (!codeArea.getStyleClass().contains("code-area")) {
      codeArea.getStyleClass().add("code-area");
    }
    codeArea.setParagraphGraphicFactory(this::lineNumber);
    codeArea.textProperty().addListener((obs, oldText, newText) -> {
      applyHighlighting(newText);
      applyAnalysis(newText);
      updateStatusBar();
      if (!suppressTextChanged && onTextChanged != null) {
        onTextChanged.accept(newText);
      }
    });
    applyHighlighting("");
    applyAnalysis("");

    VirtualizedScrollPane<CodeArea> sp = new VirtualizedScrollPane<>(codeArea);
    VBox wrapper = new VBox(sp);
    VBox.setVgrow(sp, Priority.ALWAYS);
    sp.setMaxHeight(Double.MAX_VALUE);
    lintLabel.getStyleClass().add("lint-label");
    lintLabel.setText("Ready");
    setCenter(wrapper);
    setupStatusBar();

    String css = EditorTheme.stylesheetUrl();
    if (!css.isEmpty()) {
      getStylesheets().add(css);
      codeArea.getStylesheets().add(css);
    }

    JesCompletion suggestions = new JesCompletion();
    completer = new CodeAutoCompleter(codeArea, ctx ->
        suggestions.suggest(ctx, cachedEntities, cachedLabels, lintMode == LintMode.TIMELINE_BLOCK));
    CodeEditingKeys.install(codeArea, "{}()\"\"", true);
    applyFontSize();
  }

  private void setupStatusBar() {
    HBox statusBar = new HBox(10);
    statusBar.getStyleClass().add("code-editor-status-bar");
    statusBar.setPadding(new Insets(4, 10, 4, 10));
    statusBar.setAlignment(Pos.CENTER_LEFT);
    lintLabel.getStyleClass().add("code-editor-status-primary");
    statusLabel.getStyleClass().add("code-editor-status-secondary");
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    statusBar.getChildren().addAll(lintLabel, spacer, statusLabel);
    setBottom(statusBar);

    codeArea.caretPositionProperty().addListener((obs, oldVal, newVal) -> updateStatusBar());
    codeArea.currentParagraphProperty().addListener((obs, oldVal, newVal) -> updateStatusBar());
    updateStatusBar();
  }

  private void updateStatusBar() {
    int line = codeArea.getCurrentParagraph() + 1;
    int col = codeArea.getCaretColumn() + 1;
    int lines = Math.max(1, codeArea.getParagraphs().size());
    int selected = codeArea.getSelectedText() == null ? 0 : codeArea.getSelectedText().length();
    String selection = selected > 0 ? "  |  Sel " + selected : "";
    statusLabel.setText("Ln " + line + ", Col " + col + "  |  " + lines + " lines" + selection);
  }

  public String getText() { return codeArea.getText(); }
  public void setText(String s) {
    codeArea.replaceText(s == null ? "" : s);
    refreshSyntaxHighlighting();
  }
  public void setProjectRoot(File root) {
    this.projectRoot = root;
    if (completer != null) completer.setProjectRoot(root);
    applyAnalysis(getText());
  }
  public void setTextNoEvent(String s) {
    try {
      suppressTextChanged = true;
      codeArea.replaceText(s == null ? "" : s);
      refreshSyntaxHighlighting();
    } finally {
      suppressTextChanged = false;
    }
  }
  public void setOnTextChanged(Consumer<String> listener) { this.onTextChanged = listener; }
  public void goToLine(int oneBasedLine) {
    int paragraphs = codeArea.getParagraphs().size();
    if (paragraphs <= 0) return;
    int target = Math.max(0, Math.min(paragraphs - 1, oneBasedLine - 1));
    codeArea.moveTo(target, 0);
    codeArea.requestFollowCaret();
    codeArea.requestFocus();
  }
  public void undo() { codeArea.undo(); }
  public void redo() { codeArea.redo(); }
  public void setFontSizePx(double fontSizePx) {
    this.fontSizePx = Math.max(8.0, Math.min(30.0, fontSizePx));
    applyFontSize();
  }

  public void setLintMode(LintMode mode) {
    this.lintMode = mode == null ? LintMode.JES_DOCUMENT : mode;
    applyAnalysis(codeArea.getText());
  }

  public void setLintVisible(boolean visible) {
    lintLabel.setVisible(visible);
    lintLabel.setManaged(visible);
  }

  private void applyFontSize() {
    codeArea.setStyle("-fx-font-size: " + (int) fontSizePx + "px;");
  }

  private void applyHighlighting(String text) {
    codeArea.setStyleSpans(0, computeHighlighting(text == null ? "" : text));
  }

  public void refreshSyntaxHighlighting() {
    applyHighlighting(codeArea.getText());
  }

  private static StyleSpans<Collection<String>> computeHighlighting(String text) {
    return DslSyntaxHighlighter.spans(SyntaxRules.JES, text);
  }

  private void applyAnalysis(String text) {
    JesScriptAnalyzer.Analysis analysis =
        JesScriptAnalyzer.analyze(text, projectRoot, null, toAnalyzerMode(lintMode));
    cachedEntities.clear();
    cachedEntities.addAll(analysis.entityNames());
    cachedLabels.clear();
    cachedLabels.addAll(analysis.timelineLabelNames());
    showAnalysis(analysis);
  }

  private static JesScriptAnalyzer.Mode toAnalyzerMode(LintMode mode) {
    return mode == LintMode.TIMELINE_BLOCK
        ? JesScriptAnalyzer.Mode.TIMELINE_BLOCK
        : JesScriptAnalyzer.Mode.JES_DOCUMENT;
  }

  private void showAnalysis(JesScriptAnalyzer.Analysis analysis) {
    if (analysis == null || analysis.diagnostics().isEmpty()) {
      showLintMessage("No errors", -1);
      return;
    }
    LanguageDiagnostic primary = analysis.diagnostics().stream()
        .filter(d -> d.severity() == LanguageDiagnostic.Severity.ERROR)
        .findFirst()
        .orElseGet(() -> analysis.diagnostics().get(0));
    int errLine = primary.severity() == LanguageDiagnostic.Severity.INFO
        ? -1
        : primary.oneBasedLine();
    showLintMessage(primary.message(), errLine);
  }

  private void showLintMessage(String msg, int errLine) {
    Platform.runLater(() -> {
      lintLabel.setText(msg == null ? "" : msg);
      clearErrorLine();
      if (errLine > 0) {
        lastErrorLine = errLine - 1; // CodeArea is 0-based
        if (lastErrorLine >= 0 && lastErrorLine < codeArea.getParagraphs().size()) {
          codeArea.setParagraphStyle(lastErrorLine, Collections.singleton("error-line"));
        }
      } else {
        lastErrorLine = -1;
      }
      codeArea.setParagraphGraphicFactory(this::lineNumber);
    });
  }

  private javafx.scene.Node lineNumber(int line) {
    Label number = new Label(Integer.toString(line + 1));
    number.getStyleClass().add("lineno");
    if (line == lastErrorLine) number.getStyleClass().add("lineno-error");
    return CodeEditorGutter.wrap(number);
  }

  private void clearErrorLine() {
    if (lastErrorLine >= 0 && lastErrorLine < codeArea.getParagraphs().size()) {
      codeArea.setParagraphStyle(lastErrorLine, Collections.emptyList());
    }
  }
}
