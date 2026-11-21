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

  private static final String[] KEYWORDS = new String[] {
    "scene","entity","component","on","key","do","timeline",
    "wait","move","depth","pivot","rotate","scale","fade","visible","expression","show","hide",
    "replace","event","property","cameraMove","cameraZoom","playAudio","call",
    // common props / literals
    "true","false","rgb","rgba","shape","circle","box",
    "static","sensor","text","image","align","additive"
  };

  public JesCodeEditor() {
    getStyleClass().add("text-editor-root");
    if (!codeArea.getStyleClass().contains("code-area")) {
      codeArea.getStyleClass().add("code-area");
    }
    CodeEditorGutterGuard.install(codeArea);
    codeArea.setParagraphGraphicFactory(line -> {
      Label ln = new Label(String.format("%d", line + 1));
      ln.getStyleClass().add("lineno");
      if (line == lastErrorLine) ln.getStyleClass().add("lineno-error");
      return ln;
    });
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

    completer = new CodeAutoCompleter(codeArea, ctx -> provideSuggestions(ctx));
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

  private List<CodeAutoCompleter.Suggestion> provideSuggestions(CodeAutoCompleter.Context ctx) {
    String p = ctx.prefix == null ? "" : ctx.prefix;
    String pl = p.toLowerCase();
    List<CodeAutoCompleter.Suggestion> out = new ArrayList<>();
    // keywords
    for (String kw : KEYWORDS) if (kw.startsWith(pl)) out.add(new CodeAutoCompleter.Suggestion(kw));
    // components and timeline actions
    for (String comp : List.of("Panel2D","Sprite2D","Label2D","ParticleEmitter2D","PhysicsBody2D","Character2D","Stats","Inventory","Equipment","Ai2D","Button2D","Slider2D")) {
      if (comp.toLowerCase().startsWith(pl)) out.add(new CodeAutoCompleter.Suggestion(comp));
    }
    for (String act : List.of("move","depth","pivot","rotate","scale","fade","visible","expression","show","hide","replace","event","property","walkToTile","cameraMove","cameraZoom","cameraShake","damage","heal","call","loop","parallel","waitForCall","emitParticles","cameraFollow","setParallax","playAudio","stopAudio","label","jump")) {
      if (act.startsWith(pl)) out.add(new CodeAutoCompleter.Suggestion(act));
    }
    for (String builtAction : List.of("toggleDebug","spawnCircle","spawnBox","moveHero","interact","attack")) {
      if (builtAction.toLowerCase().startsWith(pl)) out.add(new CodeAutoCompleter.Suggestion(builtAction));
    }
    for (String name : cachedEntities) if (name.toLowerCase().startsWith(pl)) out.add(new CodeAutoCompleter.Suggestion(name));
    for (String lab : cachedLabels) if (lab.toLowerCase().startsWith(pl)) out.add(new CodeAutoCompleter.Suggestion(lab));
    // if inside quotes and line hints an image value, suggest asset ids
    String line = currentLine(ctx.text, ctx.caret).toLowerCase();
    boolean wantsImage = line.contains("image") || line.contains("texture");
    if (wantsImage) {
      for (String dir : List.of("assets/ui", "assets/backgrounds", "assets/cg", "assets/characters")) {
        for (String id : CodeAutoCompleter.listAssetIds(projectRoot, dir, ".png", ".jpg", ".jpeg", ".webp")) {
          String nm = id.contains("/") ? id.substring(id.lastIndexOf('/')+1) : id;
          if (nm.toLowerCase().startsWith(pl) || id.toLowerCase().startsWith(pl)) out.add(new CodeAutoCompleter.Suggestion(id));
        }
      }
    }
    // de-dup
    if (out.size() > 1) {
      List<String> seen = new ArrayList<>();
      out.removeIf(sug -> { String k = sug.insert; if (seen.contains(k)) return true; seen.add(k); return false; });
    }
    return out;
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
      codeArea.setParagraphGraphicFactory(line -> {
        Label ln = new Label(String.format("%d", line + 1));
        ln.getStyleClass().add("lineno");
        if (line == lastErrorLine) ln.getStyleClass().add("lineno-error");
        return ln;
      });
    });
  }

  private void clearErrorLine() {
    if (lastErrorLine >= 0 && lastErrorLine < codeArea.getParagraphs().size()) {
      codeArea.setParagraphStyle(lastErrorLine, Collections.emptyList());
    }
  }

  private static String currentLine(String text, int caret) {
    if (text == null) return "";
    int s = text.lastIndexOf('\n', Math.max(0, caret-1));
    int e = text.indexOf('\n', caret);
    if (s < 0) s = 0; else s = s + 1;
    if (e < 0) e = text.length();
    return text.substring(s, Math.min(e, text.length()));
  }
}
