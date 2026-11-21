package com.jvn.editor.ui;

import com.jvn.core.script.SyntaxRules;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jvn.core.animation.TimelineData;
import com.jvn.core.animation.TimelineDataParser;
import com.jvn.core.assets.AsyncAssetLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

public class VnsCodeEditor extends BorderPane {
  private static final Logger log = LoggerFactory.getLogger(VnsCodeEditor.class);
  private final CodeArea codeArea = new CodeArea();
  private final Label lintLabel = new Label("No issues");
  private CodeAutoCompleter completer;
  private File projectRoot;
  private List<Issue> issues = List.of();
  private int highlightedIssueLine = -1;
  private Issue.Severity highlightedIssueSeverity = Issue.Severity.ERROR;
  private Consumer<String> onTextChanged;
  private IntConsumer onEditTimelineRequested;
  private Consumer<String> onLaunchFromHere;
  private IntConsumer onLaunchFromCursor;
  private EditorSearchBar searchBar;
  private boolean searchBarVisible = false;
  private final Label statusBarLabel = new Label("Ln 1, Col 1");
  private Consumer<Integer> onCaretLineChanged;
  private Consumer<Integer> onStoryboardLineRequested;
  private boolean storyboardModeActive = false;
  private int storyboardCursorLine = -1;
  private boolean storyboardDragActive = false;

  // Code folding
  private final Set<Integer> foldedRegionStarts = new HashSet<>();
  private Runnable onFoldStateChanged;
  private List<FoldRegion> foldRegionCache = List.of();
  private boolean foldRegionCacheDirty = true;
  // Tracks which paragraph indices currently have the collapse style applied.
  // Used to avoid iterating all paragraphs on every text change.
  private final Set<Integer> appliedCollapseParagraphs = new HashSet<>();
  // Bookmarks
  private final TreeSet<Integer> bookmarks = new TreeSet<>();
  // Zoom
  private double fontSizePx = 13.0;
  // Word wrap
  private boolean wordWrapEnabled = false;
  // Threshold for the "large timeline block" performance hint
  private int largeTimelineBlockActionThreshold =
      EditorPreferences.DEFAULT_LARGE_TIMELINE_BLOCK_ACTION_THRESHOLD;
  // Minimap
  private VnsCodeMinimap minimap;
  private VirtualizedScrollPane<CodeArea> mainScrollPane;
  // Breadcrumb
  private final Label breadcrumbLabel = new Label("");
  // Diff snapshot
  private String savedTextSnapshot = "";
  // Split editor
  private boolean splitActive = false;
  private CodeArea splitCodeArea;
  private SplitPane splitPane;

  // Debounce for parse/analysis — avoids parsing on every keystroke
  private final PauseTransition analysisDebounce = new PauseTransition(Duration.millis(300));
  private String pendingAnalysisText = "";
  private final AtomicLong parseGeneration = new AtomicLong(0);
  // Debounce for syntax highlighting — runs the full regex off the FX thread
  private final PauseTransition highlightDebounce = new PauseTransition(Duration.millis(30));
  private final AtomicLong highlightGeneration = new AtomicLong(0);
  // Pending flags — prevent duplicate Platform.runLater calls from stacking up
  private boolean foldRefreshPending = false;
  private boolean minimapRedrawPending = false;
  // Shared popup for timeline hover preview (lazy init, single instance)
  private Popup timelinePreviewPopup;
  private Label timelinePreviewContent;
  private final PauseTransition previewHideDelay = new PauseTransition(Duration.millis(150));
  // Timeline navigation overlay (skip to top / bottom of block while scrolling)
  private VBox timelineNavOverlay;
  private Label timelineNavTitle;
  private Label timelineNavProgress;
  private Label timelineNavMeta;
  private Label timelineNavSummary;
  private Button timelineNavTopButton;
  private Button timelineNavBottomButton;
  private FoldRegion activeScrollTimeline;
  private String activeScrollTimelineKey = "";
  private final Map<String, TimelineNavSummary> timelineNavSummaryCache = new HashMap<>();
  private final PauseTransition timelineNavHideDelay = new PauseTransition(Duration.millis(2500));
  private FadeTransition timelineNavFade;
  private TranslateTransition timelineNavSlide;
  private boolean timelineNavShowing = false;

  private static final Pattern DIALOGUE_LINE_PATTERN = Pattern.compile("^(?:[^\\s#:][^:]{0,30}):");
  private static final Pattern LABEL_PATTERN = Pattern.compile("^\\s*(?:@label|label)\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern TIMELINE_SCAN_PATTERN = Pattern.compile(
      "^\\s*timeline(?:\\s*\\{|\\s*(?:#.*|//.*)?$)", Pattern.CASE_INSENSITIVE);
  private static final Pattern TIMELINE_ACTION_SCAN_PATTERN = Pattern.compile(
      "^\\s*(move|depth|pivot|rotate|scale|mirror|fade|visible|brightness|exposure|expression|show|hide|replace|scene|cameraMove|cameraZoom|property|event|playAudio)\\b",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern TIMELINE_TARGET_SCAN_PATTERN = Pattern.compile(
      "^\\s*(?:move|depth|pivot|rotate|scale|mirror|fade|visible|brightness|exposure|expression|show|hide|replace|playAudio)\\s+\"([^\"]+)\"",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern BG_DECL_PATTERN = Pattern.compile("^\\s*@background\\s+(\\S+)\\s+(.+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern COMMAND_PATTERN = Pattern.compile("^\\s*\\[(.+)]\\s*$");
  private static final Pattern CHOICE_IF_SUFFIX_PATTERN = Pattern.compile("^(.*)\\[if\\s+(.+)]\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern IF_GOTO_PATTERN = Pattern.compile("^(.+?)\\s+goto\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern PARSE_LINE_PATTERN = Pattern.compile("\\bat line (\\d+)\\b", Pattern.CASE_INSENSITIVE);
  private static final String RTFX_COLLAPSE_STYLE_CLASS = "collapse";
  private static final String LEGACY_FOLDED_STYLE_CLASS = "folded";

  private final File sourceFile;
  private List<CompletionDeclaration> includedSymbols = List.of();

  public VnsCodeEditor() { this(null); }

  VnsCodeEditor(File sourceFile) {
    this.sourceFile = sourceFile;
    getStyleClass().add("text-editor-root");
    if (!codeArea.getStyleClass().contains("code-area")) {
      codeArea.getStyleClass().add("code-area");
    }
    codeArea.setParagraphGraphicFactory(this::makeLineNumberLabel);
    codeArea.textProperty().addListener((obs, oldText, newText) -> {
      String value = newText == null ? "" : newText;
      foldRegionCacheDirty = true;
      timelineNavSummaryCache.clear();
      activeScrollTimelineKey = "";
      applyAnalysis(value);
      if (onTextChanged != null) onTextChanged.accept(value);
      if (!foldedRegionStarts.isEmpty() && !foldRefreshPending) {
        foldRefreshPending = true;
        Platform.runLater(() -> { foldRefreshPending = false; refreshFoldedRegionStyles(); });
      }
      scheduleMinimapRedraw();
    });

    mainScrollPane = new VirtualizedScrollPane<>(codeArea);

    minimap = new VnsCodeMinimap(codeArea);

    // Redraw minimap and update nav overlay when the user scrolls
    codeArea.estimatedScrollYProperty().addListener((obs, o, n) -> {
      scheduleMinimapRedraw();
      updateTimelineNavOverlay();
    });

    // Timeline nav overlay (skip to top/bottom of block while scrolling)
    timelineNavOverlay = buildTimelineNavOverlay();
    StackPane codeWithOverlay = new StackPane(mainScrollPane, timelineNavOverlay);
    StackPane.setAlignment(timelineNavOverlay, Pos.BOTTOM_RIGHT);
    StackPane.setMargin(timelineNavOverlay, new Insets(0, 10, 14, 0));

    HBox codeAndMinimap = new HBox(codeWithOverlay, minimap);
    HBox.setHgrow(codeWithOverlay, Priority.ALWAYS);
    codeAndMinimap.heightProperty().addListener((obs, o, n) -> {
      minimap.setPrefHeight(n.doubleValue());
      scheduleMinimapRedraw();
    });

    setCenter(codeAndMinimap);
    scheduleMinimapRedraw();

    // Breadcrumb bar
    breadcrumbLabel.getStyleClass().add("code-editor-status-secondary");
    breadcrumbLabel.setMaxWidth(Double.MAX_VALUE);

    String css = EditorTheme.stylesheetUrl();
    if (!css.isEmpty()) {
      getStylesheets().add(css);
      codeArea.getStylesheets().add(css);
    }

    completer = new CodeAutoCompleter(codeArea, ctx -> provideSuggestions(ctx, includedSymbols));

    setupSearchBar();
    setupStatusBar();
    setupBreadcrumb();
    setupSelectionHighlighting();
    setupBracketMatching();
    setupHoverTooltips();
    setupCodeFolding();

    codeArea.setOnContextMenuRequested(e -> {
      ContextMenu menu = new ContextMenu();
      MenuItem launchItem = new MenuItem("Run from cursor (F5)");
      launchItem.setOnAction(a -> launchFromCursor());
      menu.getItems().add(launchItem);
      MenuItem launchLabelItem = new MenuItem("Run from current label");
      launchLabelItem.setOnAction(a -> launchFromCurrentLabel());
      menu.getItems().add(launchLabelItem);
      MenuItem launchStartItem = new MenuItem("Launch from start (Shift+F5)");
      launchStartItem.setOnAction(a -> { if (onLaunchFromHere != null) onLaunchFromHere.accept(null); });
      menu.getItems().add(launchStartItem);
      menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
      MenuItem goToSymbol = new MenuItem("Go to Symbol... (Ctrl+Shift+O)");
      goToSymbol.setOnAction(a -> showGoToSymbol());
      menu.getItems().add(goToSymbol);
      FoldRegion timelineRegion = findTimelineRegionForParagraph(codeArea.getCurrentParagraph());
      boolean hasFoldedTimelineBlocks = hasFoldedTimelineBlocks();
      if (timelineRegion != null || hasFoldedTimelineBlocks) {
        if (timelineRegion != null) {
          MenuItem editTimeline = new MenuItem("Edit Timeline in Puppeteer");
          editTimeline.setDisable(onEditTimelineRequested == null);
          editTimeline.setOnAction(a -> editTimelineAtCaret());
          menu.getItems().add(editTimeline);
          MenuItem foldTimeline = new MenuItem(foldedRegionStarts.contains(timelineRegion.startLine())
              ? "Unfold Timeline Block"
              : "Fold Timeline Block");
          foldTimeline.setOnAction(a -> toggleFold(timelineRegion.startLine()));
          menu.getItems().add(foldTimeline);
        }
        if (hasFoldedTimelineBlocks) {
          MenuItem unfoldTimelines = new MenuItem("Unfold All Timeline Blocks");
          unfoldTimelines.setOnAction(a -> unfoldAllTimelineBlocks());
          menu.getItems().add(unfoldTimelines);
        }
      }
      MenuItem toggleComment = new MenuItem("Toggle Comment (Ctrl+/)");
      toggleComment.setOnAction(a -> toggleLineComment());
      menu.getItems().add(toggleComment);
      // Quick-fix items
      Issue issue = issueAt(codeArea.getCaretPosition());
      if (issue != null) {
        ContextMenu fixMenu = buildQuickFixMenu(issue);
        if (!fixMenu.getItems().isEmpty()) {
          menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
          menu.getItems().addAll(fixMenu.getItems());
        }
      }
      menu.show(codeArea, e.getScreenX(), e.getScreenY());
      e.consume();
    });

    addEventFilter(KeyEvent.KEY_PRESSED, e -> {
      if (e.getCode() == KeyCode.F5 && !e.isControlDown() && !e.isMetaDown()) {
        if (e.isShiftDown()) {
          if (onLaunchFromHere != null) onLaunchFromHere.accept(null);
        } else {
          launchFromCursor();
        }
        e.consume();
      }
    });
    addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> storyboardDragActive = false);

    setupIdeKeyBindings();
    CodeEditingKeys.install(codeArea, "[]\"\"", false);

    applyAnalysis("");
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
      } else if ((e.isMetaDown() || e.isControlDown()) && e.getCode() == KeyCode.H) {
        showSearchBar();
        searchBar.showReplace(true);
        searchBar.focusReplace();
        e.consume();
      } else if (e.getCode() == KeyCode.ESCAPE && searchBarVisible) {
        hideSearchBar();
        e.consume();
      }
    });
  }

  private void setupStatusBar() {
    HBox statusBar = new HBox(12);
    statusBar.getStyleClass().add("code-editor-status-bar");
    statusBar.setAlignment(Pos.CENTER_LEFT);
    statusBar.setPadding(new Insets(3, 10, 3, 10));
    statusBarLabel.getStyleClass().add("code-editor-status-secondary");
    lintLabel.getStyleClass().add("lint-label");
    lintLabel.getStyleClass().add("code-editor-status-primary");
    HBox.setHgrow(lintLabel, Priority.ALWAYS);
    lintLabel.setMaxWidth(Double.MAX_VALUE);
    statusBar.getChildren().addAll(lintLabel, breadcrumbLabel, statusBarLabel);
    setBottom(statusBar);

    codeArea.caretPositionProperty().addListener((obs, oldVal, newVal) -> updateStatusBar());
    codeArea.currentParagraphProperty().addListener((obs, oldVal, newVal) -> updateStatusBar());
  }

  private void updateStatusBar() {
    int line = codeArea.getCurrentParagraph() + 1;
    int col = codeArea.getCaretColumn() + 1;
    String text = codeArea.getText();
    int labelCount = 0;
    if (text != null && !text.isEmpty()) {
      Matcher m = Pattern.compile("(?m)^\\s*@label\\s+").matcher(text);
      while (m.find()) labelCount++;
    }
    statusBarLabel.setText("Ln " + line + ", Col " + col + "  |  Labels: " + labelCount);
  }

  private void setupIdeKeyBindings() {
    addEventFilter(KeyEvent.KEY_PRESSED, e -> {
      boolean ctrl = e.isControlDown() || e.isMetaDown();
      // Ctrl+Shift+O — Go to Symbol
      if (ctrl && e.isShiftDown() && e.getCode() == KeyCode.O) {
        showGoToSymbol(); e.consume(); return;
      }
      // Ctrl+/ — Toggle comment
      if (ctrl && e.getCode() == KeyCode.SLASH) {
        toggleLineComment(); e.consume(); return;
      }
      // Ctrl+D — Duplicate line
      if (ctrl && !e.isShiftDown() && e.getCode() == KeyCode.D) {
        duplicateLine(); e.consume(); return;
      }
      // Alt+Up — Move line up
      if (e.isAltDown() && !ctrl && e.getCode() == KeyCode.UP) {
        moveLineUp(); e.consume(); return;
      }
      // Alt+Down — Move line down
      if (e.isAltDown() && !ctrl && e.getCode() == KeyCode.DOWN) {
        moveLineDown(); e.consume(); return;
      }
      // Ctrl+G — Go to Line
      if (ctrl && !e.isShiftDown() && e.getCode() == KeyCode.G) {
        showGoToLineDialog(); e.consume(); return;
      }
      // Ctrl+Shift+K — Delete Line
      if (ctrl && e.isShiftDown() && e.getCode() == KeyCode.K) {
        deleteLine(); e.consume(); return;
      }
      // Tab / Shift+Tab — Block indent/outdent (only when multi-line selection)
      if (e.getCode() == KeyCode.TAB && !ctrl && !e.isAltDown()) {
        int selS = codeArea.getSelection().getStart();
        int selE = codeArea.getSelection().getEnd();
        if (selS != selE) {
          if (e.isShiftDown()) blockOutdent(); else blockIndent();
          e.consume(); return;
        }
      }
      // Ctrl+J — Snippet Palette
      if (ctrl && !e.isShiftDown() && e.getCode() == KeyCode.J) {
        showSnippetPalette(); e.consume(); return;
      }
      // Ctrl+= — Zoom In
      if (ctrl && e.getCode() == KeyCode.EQUALS) {
        zoomIn(); e.consume(); return;
      }
      // Ctrl+- — Zoom Out
      if (ctrl && e.getCode() == KeyCode.MINUS) {
        zoomOut(); e.consume(); return;
      }
      // Ctrl+Shift+P — Command Palette
      if (ctrl && e.isShiftDown() && e.getCode() == KeyCode.P) {
        showCommandPalette(); e.consume(); return;
      }
      // Ctrl+F2 — Toggle Bookmark
      if (ctrl && e.getCode() == KeyCode.F2) {
        toggleBookmark(); e.consume(); return;
      }
      // F2 — Jump to Next Bookmark
      if (!ctrl && !e.isShiftDown() && !e.isAltDown() && e.getCode() == KeyCode.F2) {
        jumpToNextBookmark(); e.consume(); return;
      }
      // Ctrl+\ — Toggle Split Editor
      if (ctrl && e.getCode() == KeyCode.BACK_SLASH) {
        toggleSplitEditor(); e.consume(); return;
      }
      // Ctrl+Shift+W — Toggle Word Wrap
      if (ctrl && e.isShiftDown() && e.getCode() == KeyCode.W) {
        toggleWordWrap(); e.consume(); return;
      }
      // Ctrl+Shift+D — Show Diff View
      if (ctrl && e.isShiftDown() && e.getCode() == KeyCode.D) {
        showDiffView(); e.consume(); return;
      }
      // Ctrl+Shift+[ — Toggle current timeline fold
      if (ctrl && e.isShiftDown() && e.getCode() == KeyCode.OPEN_BRACKET) {
        toggleTimelineBlockAtCaret(); e.consume(); return;
      }
      // Ctrl+Shift+] — Unfold all timeline blocks
      if (ctrl && e.isShiftDown() && e.getCode() == KeyCode.CLOSE_BRACKET) {
        unfoldAllTimelineBlocks(); e.consume(); return;
      }
      // Ctrl+Shift+Down — Next Dialogue Line
      if (ctrl && e.isShiftDown() && e.getCode() == KeyCode.DOWN) {
        jumpToNextDialogueLine(); e.consume(); return;
      }
      // Ctrl+Shift+Up — Previous Dialogue Line
      if (ctrl && e.isShiftDown() && e.getCode() == KeyCode.UP) {
        jumpToPreviousDialogueLine(); e.consume(); return;
      }
      // Ctrl+V / Cmd+V — auto-fold any timeline blocks introduced by the paste
      if (ctrl && e.getCode() == KeyCode.V) {
        int preOffset = codeArea.getCaretPosition();
        Platform.runLater(() -> autoFoldPastedTimelines(preOffset));
        // Don't consume — let normal paste proceed
      }
    });
  }

  // ─── Go to Symbol ────────────────────────────────────────────────
  private void showGoToSymbol() {
    List<String> labels = extractLabels(codeArea.getText());
    if (labels.isEmpty()) return;

    Popup popup = new Popup();
    popup.setAutoHide(true);

    TextField filterField = new TextField();
    filterField.setPromptText("Go to label...");
    filterField.setPrefWidth(300);

    ListView<String> listView = new ListView<>();
    listView.setPrefWidth(300);
    listView.setPrefHeight(Math.min(labels.size() * 26 + 4, 260));
    listView.getItems().addAll(labels);

    filterField.textProperty().addListener((obs, ov, nv) -> {
      String filter = nv == null ? "" : nv.toLowerCase(Locale.ROOT);
      listView.getItems().clear();
      for (String lab : labels) {
        if (lab.toLowerCase(Locale.ROOT).contains(filter)) {
          listView.getItems().add(lab);
        }
      }
      if (!listView.getItems().isEmpty()) listView.getSelectionModel().selectFirst();
    });

    Runnable commit = () -> {
      String sel = listView.getSelectionModel().getSelectedItem();
      popup.hide();
      if (sel == null) return;
      jumpToLabel(sel);
    };

    filterField.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER) { commit.run(); e.consume(); }
      else if (e.getCode() == KeyCode.ESCAPE) { popup.hide(); e.consume(); }
      else if (e.getCode() == KeyCode.DOWN) { listView.getSelectionModel().selectNext(); e.consume(); }
      else if (e.getCode() == KeyCode.UP) { listView.getSelectionModel().selectPrevious(); e.consume(); }
    });
    listView.setOnMouseClicked(e -> { if (e.getClickCount() == 2) commit.run(); });

    javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(2, filterField, listView);
    box.setPadding(new Insets(6));
    box.setStyle("-fx-background-color: #1e1e1e; -fx-border-color: #3a3a3a; -fx-border-width: 1; -fx-background-radius: 4; -fx-border-radius: 4;");
    popup.getContent().add(box);

    codeArea.getCaretBounds().ifPresent(b -> {
      double x = b.getMinX() + codeArea.getScene().getWindow().getX();
      double y = b.getMaxY() + codeArea.getScene().getWindow().getY();
      popup.show(codeArea.getScene().getWindow(), x, y);
    });
    if (!popup.isShowing()) {
      popup.show(codeArea.getScene().getWindow(),
          codeArea.getScene().getWindow().getX() + 100,
          codeArea.getScene().getWindow().getY() + 100);
    }
    filterField.requestFocus();
    if (!listView.getItems().isEmpty()) listView.getSelectionModel().selectFirst();
  }

  private void jumpToLabel(String label) {
    if (label == null) return;
    String text = codeArea.getText();
    if (text == null) return;
    Pattern p = Pattern.compile("(?m)^\\s*@label\\s+" + Pattern.quote(label) + "\\b");
    Matcher m = p.matcher(text);
    if (m.find()) {
      codeArea.moveTo(m.start());
      codeArea.requestFollowCaret();
      codeArea.requestFocus();
    }
  }

  // ─── Comment Toggle ──────────────────────────────────────────────
  private void toggleLineComment() {
    int startPara = codeArea.getCurrentParagraph();
    int endPara = startPara;

    // If there's a multi-line selection, cover all selected paragraphs
    int selStart = codeArea.getSelection().getStart();
    int selEnd = codeArea.getSelection().getEnd();
    if (selStart != selEnd) {
      startPara = codeArea.offsetToPosition(selStart, org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor();
      endPara = codeArea.offsetToPosition(selEnd, org.fxmisc.richtext.model.TwoDimensional.Bias.Backward).getMajor();
    }

    // Check if all lines already have comments
    boolean allCommented = true;
    for (int i = startPara; i <= endPara; i++) {
      String line = codeArea.getParagraph(i).getText();
      if (!line.stripLeading().startsWith("#")) {
        allCommented = false;
        break;
      }
    }

    // Apply toggle from bottom to top to preserve offsets
    for (int i = endPara; i >= startPara; i--) {
      String line = codeArea.getParagraph(i).getText();
      int lineStart = codeArea.getAbsolutePosition(i, 0);
      int lineEnd = lineStart + line.length();
      if (allCommented) {
        // Remove first # (and optional trailing space)
        int hashIdx = line.indexOf('#');
        if (hashIdx >= 0) {
          int removeEnd = hashIdx + 1;
          if (removeEnd < line.length() && line.charAt(removeEnd) == ' ') removeEnd++;
          codeArea.replaceText(lineStart + hashIdx, lineStart + removeEnd, "");
        }
      } else {
        codeArea.insertText(lineStart, "# ");
      }
    }
  }

  // ─── Duplicate Line ──────────────────────────────────────────────
  private void duplicateLine() {
    int para = codeArea.getCurrentParagraph();
    String line = codeArea.getParagraph(para).getText();
    int lineEnd = codeArea.getAbsolutePosition(para, line.length());
    codeArea.insertText(lineEnd, "\n" + line);
    // Move caret to the duplicated line
    codeArea.moveTo(para + 1, codeArea.getCaretColumn());
  }

  // ─── Move Line Up/Down ───────────────────────────────────────────
  private void moveLineUp() {
    int para = codeArea.getCurrentParagraph();
    if (para <= 0) return;
    int col = codeArea.getCaretColumn();
    String currentLine = codeArea.getParagraph(para).getText();
    String aboveLine = codeArea.getParagraph(para - 1).getText();
    int currentStart = codeArea.getAbsolutePosition(para - 1, 0);
    int currentEnd = codeArea.getAbsolutePosition(para, currentLine.length());
    codeArea.replaceText(currentStart, currentEnd, currentLine + "\n" + aboveLine);
    codeArea.moveTo(para - 1, Math.min(col, currentLine.length()));
  }

  private void moveLineDown() {
    int para = codeArea.getCurrentParagraph();
    if (para >= codeArea.getParagraphs().size() - 1) return;
    int col = codeArea.getCaretColumn();
    String currentLine = codeArea.getParagraph(para).getText();
    String belowLine = codeArea.getParagraph(para + 1).getText();
    int currentStart = codeArea.getAbsolutePosition(para, 0);
    int currentEnd = codeArea.getAbsolutePosition(para + 1, belowLine.length());
    codeArea.replaceText(currentStart, currentEnd, belowLine + "\n" + currentLine);
    codeArea.moveTo(para + 1, Math.min(col, currentLine.length()));
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
      searchBar.showReplace(false);
      searchBarVisible = false;
      codeArea.requestFocus();
    }
  }

  public org.fxmisc.richtext.CodeArea getCodeArea() {
    return codeArea;
  }

  public String getText() {
    return codeArea.getText();
  }

  public void setText(String s) {
    String next = s == null ? "" : s;
    savedTextSnapshot = next;
    int prevCaret = codeArea.getCaretPosition();
    int prevAnchor = codeArea.getAnchor();
    codeArea.replaceText(next);
    int len = codeArea.getLength();
    int caret = Math.max(0, Math.min(prevCaret, len));
    int anchor = Math.max(0, Math.min(prevAnchor, len));
    codeArea.selectRange(anchor, caret);
  }

  public void setOnEditTimelineRequested(IntConsumer listener) {
    onEditTimelineRequested = listener;
  }

  /** Opens the containing block only; never silently selects an earlier timeline. */
  public boolean editTimelineAtCaret() {
    FoldRegion region = findTimelineRegionForParagraph(codeArea.getCurrentParagraph());
    if (region == null || onEditTimelineRequested == null) return false;
    onEditTimelineRequested.accept(region.startLine());
    return true;
  }

  /** Registers a preset and activates it at the current scene beat as one undoable edit. */
  public void insertStagePreset(String id, String scriptRelativePath) {
    int line = codeArea.getCurrentParagraph();
    FoldRegion timeline = findTimelineRegionForParagraph(line);
    if (timeline != null) line = timeline.startLine();
    String before = codeArea.getParagraph(line).getText();
    String indent = before.substring(0, before.length() - before.stripLeading().length());
    String declaration = "@stagepreset " + id + " " + scriptRelativePath;
    String command = indent + "[stage " + id + "]";
    int start = codeArea.getAbsolutePosition(line, 0);
    if (timeline == null && before.stripLeading().matches("(?i)\\[stage(?:\\s+[^]]*)?].*")) {
      codeArea.replaceText(start, start + before.length(), declaration + "\n" + command);
    } else if (timeline == null && (before.stripLeading().startsWith("@") || before.stripLeading().startsWith("[bg ")
        || before.stripLeading().startsWith("[show "))) {
      start += before.length() + 1;
      codeArea.insertText(start - 1, "\n" + declaration + "\n" + command);
    } else {
      codeArea.insertText(start, declaration + "\n" + command + "\n");
    }
    codeArea.moveTo(start + declaration.length() + 1 + command.length());
  }

  public void setOnTextChanged(Consumer<String> listener) {
    this.onTextChanged = listener;
  }

  public void setOnLaunchFromHere(Consumer<String> listener) {
    this.onLaunchFromHere = listener;
  }

  public void setOnLaunchFromCursor(IntConsumer listener) {
    this.onLaunchFromCursor = listener;
  }

  /** Launches the script at the nearest label at or above the caret. */
  public void launchFromCurrentLabel() {
    launchFromHere();
  }

  /** Launches the script at the first executable node at or after the caret line. */
  public void launchFromCursor() {
    if (onLaunchFromCursor != null) {
      onLaunchFromCursor.accept(codeArea.getCurrentParagraph() + 1);
    }
  }

  /** Launches the script through the configured callback from its entry point. */
  public void launchFromStart() {
    if (onLaunchFromHere != null) onLaunchFromHere.accept(null);
  }

  /** Opens the VNS label navigator at the editor caret. */
  public void showSymbolNavigator() {
    showGoToSymbol();
  }

  /** Opens the VNS snippet picker at the editor caret. */
  public void showSnippetPicker() {
    showSnippetPalette();
  }

  private static final Pattern LABEL_SCAN_PATTERN = Pattern.compile("^\\s*@label\\s+(\\S+)", Pattern.CASE_INSENSITIVE);

  private void launchFromHere() {
    if (onLaunchFromHere == null) return;
    int cursorLine = codeArea.getCurrentParagraph();
    String text = codeArea.getText();
    if (text == null || text.isEmpty()) {
      onLaunchFromHere.accept(null);
      return;
    }
    String[] lines = text.split("\\n", -1);
    for (int i = Math.min(cursorLine, lines.length - 1); i >= 0; i--) {
      Matcher matcher = LABEL_SCAN_PATTERN.matcher(lines[i]);
      if (matcher.find()) {
        onLaunchFromHere.accept(matcher.group(1));
        return;
      }
    }
    onLaunchFromHere.accept(null);
  }

  public int getCurrentLine() {
    return codeArea.getCurrentParagraph();
  }

  public void setOnCaretLineChanged(Consumer<Integer> listener) {
    codeArea.currentParagraphProperty().addListener((obs, oldVal, newVal) -> {
      if (listener != null && newVal != null) listener.accept(newVal.intValue());
    });
  }

  public void setOnFoldStateChanged(Runnable listener) {
    this.onFoldStateChanged = listener;
  }

  public void setOnStoryboardLineRequested(Consumer<Integer> listener) {
    this.onStoryboardLineRequested = listener;
  }

  public void setStoryboardModeActive(boolean active) {
    if (storyboardModeActive == active) return;
    storyboardModeActive = active;
    if (!active) {
      storyboardDragActive = false;
      storyboardCursorLine = -1;
    }
    Platform.runLater(() -> codeArea.setParagraphGraphicFactory(this::makeLineNumberLabel));
  }

  public void setStoryboardCursorLine(int oneBasedLine) {
    int normalized = oneBasedLine <= 0 ? -1 : oneBasedLine;
    if (storyboardCursorLine == normalized) return;
    storyboardCursorLine = normalized;
    Platform.runLater(() -> codeArea.setParagraphGraphicFactory(this::makeLineNumberLabel));
  }

  public int getStoryboardCursorLine() {
    return storyboardCursorLine;
  }

  private void requestStoryboardLine(int oneBasedLine) {
    storyboardCursorLine = Math.max(1, oneBasedLine);
    if (onStoryboardLineRequested != null) {
      onStoryboardLineRequested.accept(storyboardCursorLine);
    }
    Platform.runLater(() -> codeArea.setParagraphGraphicFactory(this::makeLineNumberLabel));
  }

  public void goToLine(int oneBasedLine) {
    int paragraphCount = codeArea.getParagraphs().size();
    if (paragraphCount <= 0) return;
    int target = Math.max(0, Math.min(paragraphCount - 1, oneBasedLine - 1));
    codeArea.moveTo(target, 0);
    codeArea.requestFollowCaret();
    codeArea.requestFocus();
  }

  public void scrollToLineIfNeeded(int oneBasedLine) {
    int paragraphCount = codeArea.getParagraphs().size();
    if (paragraphCount <= 0 || oneBasedLine <= 0) return;
    int target = Math.max(0, Math.min(paragraphCount - 1, oneBasedLine - 1));
    int visibleStart = codeArea.firstVisibleParToAllParIndex();
    int visibleEnd = codeArea.lastVisibleParToAllParIndex();
    if (target >= visibleStart && target <= visibleEnd) return;
    codeArea.showParagraphAtTop(Math.max(0, target - 5));
  }

  public void goToOffset(int offset) {
    String text = codeArea.getText();
    int length = text == null ? 0 : text.length();
    int target = Math.max(0, Math.min(length, offset));
    codeArea.moveTo(target);
    codeArea.requestFollowCaret();
    codeArea.requestFocus();
  }

  public void goToRange(int startOffset, int endOffset) {
    String text = codeArea.getText();
    int length = text == null ? 0 : text.length();
    int start = Math.max(0, Math.min(length, startOffset));
    int end = Math.max(0, Math.min(length, endOffset));
    if (end < start) end = start;

    if (start == end) {
      goToOffset(start);
      return;
    }

    codeArea.selectRange(start, end);
    codeArea.requestFollowCaret();
    codeArea.requestFocus();
  }

  public void setProjectRoot(File root) {
    this.projectRoot = root;
    includedSymbols = List.of();
    if (completer != null) completer.setProjectRoot(root);
    applyAnalysis(getText());
  }

  private static String linenoStyleClassForSeverity(Issue.Severity severity) {
    return switch (severity) {
      case WARNING -> "lineno-warning";
      case INFO -> "lineno-hint";
      case ERROR -> "lineno-error";
    };
  }

  private static String lineStyleClassForSeverity(Issue.Severity severity) {
    return switch (severity) {
      case WARNING -> "warning-line";
      case INFO -> "hint-line";
      case ERROR -> "error-line";
    };
  }

  private javafx.scene.Node makeLineNumberLabel(int line) {
    Label ln = new Label(String.format("%d", line + 1));
    ln.getStyleClass().add("lineno");
    if (line == highlightedIssueLine) {
      ln.getStyleClass().add(linenoStyleClassForSeverity(highlightedIssueSeverity));
    }

    boolean isStoryboardLine = storyboardModeActive && storyboardCursorLine == line + 1;

    // Bookmark indicator
    boolean isBookmarked = bookmarks.contains(line);

    // Fold indicator — check if this line starts a fold region
    FoldRegion foldRegion = findFoldRegionStartingAtLine(line);
    boolean isFoldable = foldRegion != null;

    if (!isFoldable && !isBookmarked && !storyboardModeActive) return CodeEditorGutter.wrap(ln);

    HBox gutter = new HBox(2);
    gutter.setAlignment(Pos.CENTER_LEFT);
    gutter.getStyleClass().add("lineno");
    if (line == highlightedIssueLine) {
      gutter.getStyleClass().add(linenoStyleClassForSeverity(highlightedIssueSeverity));
    }

    if (storyboardModeActive) {
      Label cursor = new Label(isStoryboardLine ? "\u25B6" : "\u25E6");
      cursor.setStyle(
          isStoryboardLine
              ? "-fx-text-fill: #f0c27a; -fx-font-size: 10px; -fx-padding: 0 2 0 0; -fx-cursor: v_resize;"
              : "-fx-text-fill: #6a7a8e; -fx-font-size: 9px; -fx-padding: 0 2 0 0; -fx-cursor: hand;");
      cursor.setTooltip(new Tooltip(isStoryboardLine
          ? "Drag to scrub storyboard position"
          : "Click to jump storyboard here  •  Drag to scrub"));
      if (!isStoryboardLine) {
        cursor.setOnMouseEntered(e -> cursor.setStyle(
            "-fx-text-fill: #a0b0c0; -fx-font-size: 9px; -fx-padding: 0 2 0 0; -fx-cursor: hand;"));
        cursor.setOnMouseExited(e -> {
          if (!storyboardDragActive) cursor.setStyle(
              "-fx-text-fill: #6a7a8e; -fx-font-size: 9px; -fx-padding: 0 2 0 0; -fx-cursor: hand;");
        });
      }
      cursor.setOnMousePressed(e -> {
        if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
        storyboardDragActive = true;
        requestStoryboardLine(line + 1);
        e.consume();
      });
      gutter.setOnMouseEntered(e -> {
        if (storyboardDragActive) {
          requestStoryboardLine(line + 1);
        }
      });
      gutter.setOnMouseReleased(e -> {
        if (storyboardDragActive && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
          storyboardDragActive = false;
          requestStoryboardLine(line + 1);
          e.consume();
        }
      });
      gutter.getChildren().add(cursor);
    }

    if (isFoldable) {
      boolean folded = foldedRegionStarts.contains(line);
      Label foldBtn = new Label(folded ? "\u25B6" : "\u25BE");
      foldBtn.getStyleClass().add("code-fold-toggle");
      if (foldRegion.kind() == FoldKind.TIMELINE && folded) {
        foldBtn.setOnMouseEntered(e -> showTimelinePreviewPopup(foldRegion, foldBtn));
        foldBtn.setOnMouseExited(e -> scheduleHideTimelinePreviewPopup());
      } else {
        foldBtn.setTooltip(new Tooltip(folded ? "Unfold section" : "Fold section"));
      }
      foldBtn.setOnMouseClicked(e -> toggleFold(line));
      gutter.getChildren().add(foldBtn);
    }

    if (isBookmarked) {
      Label dot = new Label("\u25CF");
      dot.setStyle("-fx-text-fill: #b5b5b5; -fx-font-size: 9px; -fx-padding: 0 1 0 1;");
      gutter.getChildren().add(dot);
    }

    Label number = new Label(String.format("%d", line + 1));
    number.getStyleClass().add("lineno-text");
    gutter.getChildren().add(number);

    return CodeEditorGutter.wrap(gutter);
  }

  void insertSnippet(String s) {
    int pos = codeArea.getCaretPosition();
    codeArea.insertText(pos, s);
  }

  private void applyAnalysis(String text) {
    String safe = text == null ? "" : text;
    pendingAnalysisText = safe;

    // Highlight debounce — fires after 30 ms of idle, runs the full regex off the FX thread
    // so rapid typing never blocks the event loop.
    highlightDebounce.setOnFinished(he -> {
      String snapshot = pendingAnalysisText;
      List<Issue> issueSnapshot = issues;
      long gen = highlightGeneration.incrementAndGet();
      AsyncAssetLoader.getExecutor().execute(() -> {
        StyleSpans<Collection<String>> spans = computeHighlightingWithIssues(snapshot, issueSnapshot);
        if (highlightGeneration.get() == gen) {
          Platform.runLater(() -> {
            try { codeArea.setStyleSpans(0, spans); } catch (Exception ignored) {}
          });
        }
      });
    });
    highlightDebounce.playFromStart();

    // Issue parse debounce — 300 ms, recomputes issues and highlighting fully off the FX thread
    analysisDebounce.setOnFinished(e -> {
      String snapshot = pendingAnalysisText;
      long generation = parseGeneration.incrementAndGet();
      AsyncAssetLoader.getExecutor().execute(() -> {
        List<CompletionDeclaration> symbols = new ArrayList<>();
        List<Issue> computed = computeIssues(snapshot, source -> readDeclarations(source, symbols));
        if (parseGeneration.get() != generation) return;
        StyleSpans<Collection<String>> spans = computeHighlightingWithIssues(snapshot, computed);
        if (parseGeneration.get() == generation) {
          Platform.runLater(() -> {
            if (parseGeneration.get() != generation || !snapshot.equals(pendingAnalysisText)) return;
            includedSymbols = List.copyOf(symbols);
            issues = computed;
            try { codeArea.setStyleSpans(0, spans); } catch (Exception ignored) {}
            refreshIssuePresentation();
          });
        }
      });
    });
    analysisDebounce.playFromStart();
  }

  private static List<Span> syntaxSpans(String text) {
    List<Span> spans = new ArrayList<>();
    SyntaxRules.VNS.scan(text, (start, end, style) ->
        spans.add(new Span(start, end, style.isEmpty() ? List.of() : List.of(style))));
    return spans;
  }

  private StyleSpans<Collection<String>> computeHighlightingWithIssues(String text, List<Issue> currentIssues) {
    List<Span> spans = syntaxSpans(text);

    if (currentIssues != null) {
      for (Issue issue : currentIssues) {
        String cls = switch (issue.severity) {
          case WARNING -> "warning";
          case INFO -> "hint";
          case ERROR -> "error";
        };
        spans = overlay(spans, issue.start, issue.end, cls);
      }
    }

    StyleSpansBuilder<Collection<String>> out = new StyleSpansBuilder<>();
    for (Span s : compress(spans)) {
      out.add(s.styles, Math.max(0, s.end - s.start));
    }
    return out.create();
  }

  private void refreshIssuePresentation() {
    int prevLine = highlightedIssueLine;
    highlightedIssueLine = -1;
    highlightedIssueSeverity = Issue.Severity.ERROR;

    if (prevLine >= 0 && prevLine < codeArea.getParagraphs().size()) {
      removeParagraphStyleClasses(prevLine, Set.of("warning-line", "error-line", "hint-line"));
    }

    Issue firstError = null;
    Issue firstWarning = null;
    Issue firstHint = null;
    int errors = 0;
    int warnings = 0;
    int hints = 0;

    for (Issue issue : issues) {
      switch (issue.severity) {
        case WARNING -> {
          warnings++;
          if (firstWarning == null) firstWarning = issue;
        }
        case INFO -> {
          hints++;
          if (firstHint == null) firstHint = issue;
        }
        case ERROR -> {
          errors++;
          if (firstError == null) firstError = issue;
        }
      }
    }

    if (errors == 0 && warnings == 0 && hints == 0) {
      lintLabel.setText("No issues");
    } else {
      StringBuilder summary = new StringBuilder();
      if (errors > 0) summary.append(errors).append(errors == 1 ? " error" : " errors");
      if (warnings > 0) {
        if (summary.length() > 0) summary.append(", ");
        summary.append(warnings).append(warnings == 1 ? " warning" : " warnings");
      }
      if (hints > 0) {
        if (summary.length() > 0) summary.append(", ");
        summary.append(hints).append(hints == 1 ? " hint" : " hints");
      }
      Issue first = firstError != null ? firstError : firstWarning != null ? firstWarning : firstHint;
      if (first != null && first.message != null && !first.message.isBlank()) {
        summary.append(" - ").append(first.message);
      }
      lintLabel.setText(summary.toString());
    }

    Issue focusIssue = firstError != null ? firstError : firstWarning;
    if (focusIssue != null && focusIssue.line >= 0) {
      highlightedIssueLine = focusIssue.line;
      highlightedIssueSeverity = focusIssue.severity;
      if (highlightedIssueLine < codeArea.getParagraphs().size()) {
        removeParagraphStyleClasses(highlightedIssueLine, Set.of("warning-line", "error-line", "hint-line"));
        addParagraphStyleClass(highlightedIssueLine, lineStyleClassForSeverity(highlightedIssueSeverity));
      }
    }

    Platform.runLater(() -> codeArea.setParagraphGraphicFactory(this::makeLineNumberLabel));
    scheduleMinimapRedraw();
  }

  /**
   * Delegates to VnsScriptAnalyzer.analyze() to avoid duplicated lint logic.
   * Converts VnsScriptAnalyzer.Diagnostic to local Issue type for UI integration.
   */
  private List<Issue> computeIssues(String text, Consumer<String> onIncludedSource) {
    VnsScriptAnalyzer.Analysis analysis = VnsScriptAnalyzer.analyze(
        text, projectRoot, sourceFile, largeTimelineBlockActionThreshold, onIncludedSource);
    List<Issue> out = new ArrayList<>();

    for (VnsScriptAnalyzer.Diagnostic diag : analysis.diagnostics()) {
      Issue issue = switch (diag.severity()) {
        case ERROR -> Issue.error(
            diag.kind(), diag.message(), diag.start(), diag.end(), diag.line(),
            diag.label(), diag.assetPath(), diag.blockEnd());
        case WARNING -> Issue.warning(
            diag.kind(), diag.message(), diag.start(), diag.end(), diag.line(),
            diag.label(), diag.assetPath(), diag.blockEnd());
        case INFO -> Issue.info(
            diag.kind(), diag.message(), diag.start(), diag.end(), diag.line(),
            diag.label(), diag.assetPath(), diag.blockEnd());
      };
      out.add(issue);
    }

    return out;
  }

  private List<Issue> computeUnreachableLabelIssues(String source,
                                                    Map<String, LabelDef> labels,
                                                    List<LabelRef> refs,
                                                    List<LineInfo> lines) {
    if (labels.isEmpty()) return List.of();

    List<LabelDef> orderedLabels = new ArrayList<>(labels.values());
    orderedLabels.sort((a, b) -> Integer.compare(a.line, b.line));

    Map<String, Set<String>> edges = new HashMap<>();
    for (LabelDef def : orderedLabels) {
      edges.put(def.name, new HashSet<>());
    }

    for (LabelDef def : orderedLabels) {
      int idx = orderedLabels.indexOf(def);
      int startLine = def.line + 1;
      int endLine = idx + 1 < orderedLabels.size() ? orderedLabels.get(idx + 1).line : lines.size();

      boolean terminal = false;
      for (int i = startLine; i < endLine; i++) {
        if (i < 0 || i >= lines.size()) continue;
        String trimmed = lines.get(i).trimmed();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

        if (trimmed.startsWith(">")) {
          LabelRef cr = extractChoiceReference(lines.get(i), trimmed.substring(1).trim());
          if (cr != null && labels.containsKey(cr.label)) {
            edges.get(def.name).add(cr.label);
          }
          continue;
        }

        Matcher cm = COMMAND_PATTERN.matcher(trimmed);
        if (!cm.matches()) continue;
        String body = cm.group(1).trim();
        if (body.isEmpty()) continue;

        String[] parts = body.split("\\s+", 2);
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1].trim() : "";

        if ("jump".equals(cmd)) {
          String tgt = firstToken(arg);
          if (labels.containsKey(tgt)) edges.get(def.name).add(tgt);
          terminal = true;
          break;
        }
        if ("end".equals(cmd)) {
          terminal = true;
          break;
        }
        if ("if".equals(cmd)) {
          Matcher m = IF_GOTO_PATTERN.matcher(arg);
          if (m.matches()) {
            String tgt = m.group(2).trim();
            if (labels.containsKey(tgt)) edges.get(def.name).add(tgt);
          }
          continue;
        }
        if ("choice".equals(cmd)) {
          String[] segs = arg.split("\\|");
          for (String seg : segs) {
            LabelRef cr = extractChoiceReference(lines.get(i), seg == null ? "" : seg.trim());
            if (cr != null && labels.containsKey(cr.label)) {
              edges.get(def.name).add(cr.label);
            }
          }
        }
      }

      if (!terminal && idx + 1 < orderedLabels.size()) {
        edges.get(def.name).add(orderedLabels.get(idx + 1).name);
      }
    }

    String start = orderedLabels.get(0).name;
    Set<String> reachable = new HashSet<>();
    ArrayDeque<String> queue = new ArrayDeque<>();
    queue.add(start);

    while (!queue.isEmpty()) {
      String current = queue.removeFirst();
      if (!reachable.add(current)) continue;
      for (String nxt : edges.getOrDefault(current, Set.of())) {
        if (!reachable.contains(nxt)) queue.addLast(nxt);
      }
    }

    List<Issue> out = new ArrayList<>();
    for (int i = 0; i < orderedLabels.size(); i++) {
      LabelDef def = orderedLabels.get(i);
      if (reachable.contains(def.name)) continue;

      int blockEnd = i + 1 < orderedLabels.size() ? orderedLabels.get(i + 1).tokenStart : source.length();
      out.add(Issue.warning(
          "unreachable_label",
          "Unreachable label: " + def.name,
          def.tokenStart,
          def.tokenEnd,
          def.line,
          def.name,
          null,
          blockEnd
      ));
    }

    return out;
  }

  private void addChoiceReference(LineInfo line, String segment, List<LabelRef> refs) {
    LabelRef ref = extractChoiceReference(line, segment);
    if (ref != null) refs.add(ref);
  }

  private LabelRef extractChoiceReference(LineInfo line, String segment) {
    if (segment == null || segment.isBlank()) return null;
    String work = segment.trim();

    Matcher suffix = CHOICE_IF_SUFFIX_PATTERN.matcher(work);
    if (suffix.matches()) {
      work = suffix.group(1).trim();
    }

    int arrow = work.indexOf("->");
    if (arrow < 0) return null;

    String right = work.substring(arrow + 2).trim();
    if (right.isEmpty()) return null;

    String target = firstToken(right);
    int targetInLine = safeIndexOf(line.text, target, 0);
    int start = line.start + targetInLine;
    return new LabelRef(target, start, start + target.length(), line.index);
  }

  private boolean assetExists(String path) {
    if (path == null || path.isBlank()) return false;
    File resolved = resolveAssetPath(path);
    return resolved != null && resolved.exists() && resolved.isFile();
  }

  private File resolveAssetPath(String rawPath) {
    if (rawPath == null || rawPath.isBlank()) return null;
    String normalized = rawPath.trim().replace('\\', '/');
    File direct = new File(normalized);
    if (direct.isAbsolute()) return direct;
    if (projectRoot == null) return null;

    List<String> candidates = new ArrayList<>();
    candidates.add(normalized);

    String rel = normalized;
    if (rel.startsWith("./")) rel = rel.substring(2);
    if (rel.startsWith("game/images/")) rel = rel.substring("game/images/".length());
    if (rel.startsWith("images/")) rel = rel.substring("images/".length());
    if (rel.startsWith("assets/")) rel = rel.substring("assets/".length());

    candidates.add("assets/" + rel);
    candidates.add("assets/images/" + rel);
    candidates.add("assets/backgrounds/" + rel);

    String fileName = rel.contains("/") ? rel.substring(rel.lastIndexOf('/') + 1) : rel;
    if (!fileName.isBlank()) {
      candidates.add("assets/backgrounds/" + fileName);
      candidates.add("assets/images/backgrounds/" + fileName);
      candidates.add("game/images/backgrounds/" + fileName);
    }

    for (String candidate : candidates) {
      File f = new File(projectRoot, candidate);
      if (f.exists()) return f;
    }
    return new File(projectRoot, normalized);
  }

  private List<String> listBackgroundAssets() {
    if (projectRoot == null) return List.of();
    List<String> files = new ArrayList<>();
    collectFiles(files, projectRoot.toPath().resolve("assets/backgrounds"));
    collectFiles(files, projectRoot.toPath().resolve("assets/images/backgrounds"));
    collectFiles(files, projectRoot.toPath().resolve("game/images/backgrounds"));
    files.sort(String.CASE_INSENSITIVE_ORDER);
    return files;
  }

  private void collectFiles(List<String> out, Path dir) {
    try {
      if (dir == null || !Files.exists(dir) || !Files.isDirectory(dir)) return;
      try (var stream = Files.walk(dir, 4)) {
        stream.filter(Files::isRegularFile).forEach(p -> {
          String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
          if (!(n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp"))) return;
          Path rel = projectRoot.toPath().relativize(p);
          out.add(rel.toString().replace('\\', '/'));
        });
      }
    } catch (Exception e) {
      log.warn("Failed to collect image assets: {}", e.getMessage());
    }
  }

  private ContextMenu buildQuickFixMenu(Issue issue) {
    ContextMenu menu = new ContextMenu();
    if (issue == null) return menu;

    if ("undefined_label".equals(issue.kind) && issue.label != null) {
      MenuItem create = new MenuItem("Create label '" + issue.label + "'");
      create.setOnAction(e -> createMissingLabel(issue.label, issue));
      menu.getItems().add(create);

      List<String> labels = extractLabels(codeArea.getText());
      if (!labels.isEmpty()) {
        MenuItem replace = new MenuItem("Change to existing label...");
        replace.setOnAction(e -> replaceUndefinedLabel(issue, labels));
        menu.getItems().add(replace);
      }
    }

    if ("missing_asset".equals(issue.kind)) {
      List<String> assets = listBackgroundAssets();
      if (!assets.isEmpty()) {
        MenuItem choose = new MenuItem("Choose existing asset...");
        choose.setOnAction(e -> replaceAssetPath(issue, assets));
        menu.getItems().add(choose);
      }
    }

    if ("unreachable_label".equals(issue.kind) && issue.blockEnd > issue.start) {
      MenuItem removeBlock = new MenuItem("Delete unreachable block");
      removeBlock.setOnAction(e -> deleteIssueBlock(issue));
      menu.getItems().add(removeBlock);
    }

    return menu;
  }

  private void createMissingLabel(String label, Issue issue) {
    if (label == null || label.isBlank()) return;
    String text = codeArea.getText();
    List<LineInfo> lines = splitLines(text);
    int targetLine = issue != null ? issue.line : Math.max(0, codeArea.getCurrentParagraph());
    targetLine = Math.max(0, Math.min(targetLine, lines.size() - 1));

    int insertOffset = lineInsertOffset(lines, targetLine + 1);
    String insertion =
        System.lineSeparator() +
        "@label " + label + System.lineSeparator() +
        "narrator \"TODO: implement this branch.\"" + System.lineSeparator();
    codeArea.insertText(insertOffset, insertion);
  }

  private void replaceUndefinedLabel(Issue issue, List<String> labels) {
    if (issue == null || labels == null || labels.isEmpty()) return;
    EditorDialogs.choose(
        getScene() == null ? null : getScene().getWindow(),
        "Replace Label",
        "Select the label to use for this reference.",
        labels,
        labels.get(0),
        value -> value,
        "Replace")
        .ifPresent(value -> replaceIssueRange(issue, value));
  }

  private void replaceAssetPath(Issue issue, List<String> assets) {
    if (issue == null || assets == null || assets.isEmpty()) return;
    EditorDialogs.choose(
        getScene() == null ? null : getScene().getWindow(),
        "Replace Asset Path",
        "Select the asset path to use.",
        assets,
        assets.get(0),
        value -> value,
        "Replace")
        .ifPresent(value -> replaceIssueRange(issue, value));
  }

  private Issue issueAt(int caret) {
    if (issues == null) return null;
    for (Issue issue : issues) {
      if (caret >= issue.start && caret <= issue.end) return issue;
    }
    return null;
  }

  private static final Pattern COMPLETION_DECLARATION = Pattern.compile(
      "(?im)^[ \\t]*(@character|@charimg|@charpreset|@position|@var|@background|@?label)[ \\t]+([^\\s=]+)(?:[ \\t]+([^\\s=]+))?");
  private static final Set<String> CLOSED_COMMANDS = Set.of("end", "return", "else", "endif", "/if", "save", "quickload", "jes_pop", "settings",
      "bgm_pause", "bgm_resume", "bgm_stop", "sfx_stop", "voice_stop", "audio_stop_all", "audio_pause_all", "audio_resume_all");

  record CompletionDeclaration(String type, String id, String detail) {}

  static void readDeclarations(String text, List<CompletionDeclaration> out) {
    Matcher declarations = COMPLETION_DECLARATION.matcher(text);
    while (declarations.find()) {
      out.add(new CompletionDeclaration(declarations.group(1).toLowerCase(Locale.ROOT),
          declarations.group(2), declarations.group(3)));
    }
  }

  static List<CodeAutoCompleter.Suggestion> provideSuggestions(CodeAutoCompleter.Context ctx,
                                                               List<CompletionDeclaration> included) {
    int start = ctx.text.lastIndexOf('\n', ctx.caret - 1) + 1;
    String line = ctx.text.substring(start, ctx.caret).stripLeading();
    String prefix = ctx.prefix;
    List<CodeAutoCompleter.Suggestion> out = new ArrayList<>();
    if (line.startsWith("@") && line.equals(prefix)) {
      for (String directive : SyntaxRules.vnsDirectives()) addMatchingSuggestion(out, prefix, "@" + directive + " ");
      return out;
    }
    if (line.startsWith("[") && line.equals(prefix)) {
      for (String cmd : SyntaxRules.vnsCommands()) {
        addMatchingSuggestion(out, prefix, "[" + cmd + (CLOSED_COMMANDS.contains(cmd) ? "]" : " "));
      }
      for (String snippet : List.of("[call hud ", "[call jes_timeline ", "[jes call ")) addMatchingSuggestion(out, prefix, snippet);
      return out;
    }
    boolean choiceTarget = line.startsWith(">") && line.contains("->")
        && line.substring(line.lastIndexOf("->") + 2).stripLeading().equals(prefix);
    if (!choiceTarget && (!line.startsWith("[") || line.contains("]") || CodeAutoCompleter.openQuote(line, line.length()) >= 0)) {
      return out;
    }
    String before = line.substring(0, line.length() - prefix.length()).stripTrailing();
    String[] words = before.substring(1).split("\\s+");
    String command = words[0].toLowerCase(Locale.ROOT);
    boolean first = words.length == 1;
    boolean actor = command.equals("show") || command.equals("move") || command.equals("hide");
    String kind = "";
    String valuePrefix = "";
    if (choiceTarget || first && Set.of("jump", "goto", "call", "gosub").contains(command)) kind = "label";
    else if (first && actor) kind = "@character";
    else if (first && Set.of("bg", "background").contains(command)) kind = "@background";
    else if (first && Set.of("set", "inc", "dec", "toggle", "mul", "div", "clear").contains(command)) kind = "@var";
    else if (actor && !first && prefix.startsWith("expr=")) { kind = "expression"; valuePrefix = "expr="; }
    else if (actor && !first && prefix.startsWith("pos=")) { kind = "@position"; valuePrefix = "pos="; }
    else if (command.equals("transition") && prefix.startsWith("bg=")) { kind = "@background"; valuePrefix = "bg="; }

    if (!kind.isEmpty()) {
      List<CompletionDeclaration> declarations = new ArrayList<>();
      readDeclarations(ctx.text, declarations);
      declarations.addAll(included);
      Set<String> names = new LinkedHashSet<>();
      for (CompletionDeclaration declaration : declarations) {
        String type = declaration.type();
        String id = declaration.id();
        if (kind.equals(type) || kind.equals("label") && type.equals("@label")) names.add(id);
        if (kind.equals("expression") && (type.equals("@charimg") || type.equals("@charpreset"))
            && id.equals(words[1]) && declaration.detail() != null) names.add(declaration.detail());
      }
      for (String name : names) addMatchingSuggestion(out, prefix, valuePrefix + name);
    }
    if (choiceTarget || first && actor || kind.equals("expression") && !out.isEmpty()) return out;
    out.addAll(contextualCommandSuggestions(ctx.text, ctx.caret, prefix));
    Set<String> seen = new HashSet<>();
    out.removeIf(suggestion -> !seen.add(suggestion.insert));
    return out;
  }

  static List<CodeAutoCompleter.Suggestion> contextualCommandSuggestions(String text, int caret, String prefix) {
    String command = currentBracketCommandName(text, caret);
    if (command == null) return List.of();

    String normalizedPrefix = prefix == null ? "" : prefix.trim();
    List<CodeAutoCompleter.Suggestion> out = new ArrayList<>();

    switch (command) {
      case "show" -> {
        addMatchingSuggestion(out, normalizedPrefix, "pos=");
        addMatchingSuggestion(out, normalizedPrefix, "expr=");
        addMatchingSuggestion(out, normalizedPrefix, "layer=");
        addMatchingSuggestion(out, normalizedPrefix, "at=");
        addMatchingSuggestion(out, normalizedPrefix, "slot=");
        addMatchingSuggestion(out, normalizedPrefix, "pos=center");
        addMatchingSuggestion(out, normalizedPrefix, "pos=left");
        addMatchingSuggestion(out, normalizedPrefix, "pos=right");
        addMatchingSuggestion(out, normalizedPrefix, "pos=far_left");
        addMatchingSuggestion(out, normalizedPrefix, "pos=far_right");
      }
      case "move" -> {
        addMatchingSuggestion(out, normalizedPrefix, "pos=");
        addMatchingSuggestion(out, normalizedPrefix, "expr=");
        addMatchingSuggestion(out, normalizedPrefix, "ease=");
        addMatchingSuggestion(out, normalizedPrefix, "dur=");
        addMatchingSuggestion(out, normalizedPrefix, "at=");
        addMatchingSuggestion(out, normalizedPrefix, "slot=");
        addMatchingSuggestion(out, normalizedPrefix, "pos=center");
        addMatchingSuggestion(out, normalizedPrefix, "pos=left");
        addMatchingSuggestion(out, normalizedPrefix, "pos=right");
        addMatchingSuggestion(out, normalizedPrefix, "pos=far_left");
        addMatchingSuggestion(out, normalizedPrefix, "pos=far_right");
        addMatchingSuggestion(out, normalizedPrefix, "ease=linear");
        addMatchingSuggestion(out, normalizedPrefix, "ease=easeIn");
        addMatchingSuggestion(out, normalizedPrefix, "ease=easeOut");
        addMatchingSuggestion(out, normalizedPrefix, "ease=easeInOut");
        addMatchingSuggestion(out, normalizedPrefix, "dur=500");
      }
      case "showpreset" -> {
        addMatchingSuggestion(out, normalizedPrefix, "pos=");
        addMatchingSuggestion(out, normalizedPrefix, "at=");
        addMatchingSuggestion(out, normalizedPrefix, "pos=center");
      }
      case "movepreset" -> {
        addMatchingSuggestion(out, normalizedPrefix, "pos=");
        addMatchingSuggestion(out, normalizedPrefix, "at=");
        addMatchingSuggestion(out, normalizedPrefix, "ease=");
        addMatchingSuggestion(out, normalizedPrefix, "dur=");
        addMatchingSuggestion(out, normalizedPrefix, "ease=easeOut");
        addMatchingSuggestion(out, normalizedPrefix, "dur=500");
      }
      case "transition" -> {
        addMatchingSuggestion(out, normalizedPrefix, "type=");
        addMatchingSuggestion(out, normalizedPrefix, "dur=");
        addMatchingSuggestion(out, normalizedPrefix, "bg=");
        addMatchingSuggestion(out, normalizedPrefix, "type=fade");
        addMatchingSuggestion(out, normalizedPrefix, "type=dissolve");
        addMatchingSuggestion(out, normalizedPrefix, "type=crossfade");
        addMatchingSuggestion(out, normalizedPrefix, "type=slide_left");
        addMatchingSuggestion(out, normalizedPrefix, "type=slide_right");
        addMatchingSuggestion(out, normalizedPrefix, "type=wipe");
        addMatchingSuggestion(out, normalizedPrefix, "dur=500");
      }
      case "particles", "weather", "pfx", "fx" -> {
        addMatchingSuggestion(out, normalizedPrefix, "preset=");
        addMatchingSuggestion(out, normalizedPrefix, "intensity=");
        addMatchingSuggestion(out, normalizedPrefix, "layer=");
        addMatchingSuggestion(out, normalizedPrefix, "opacity=");
        addMatchingSuggestion(out, normalizedPrefix, "speed=");
        addMatchingSuggestion(out, normalizedPrefix, "wind=");
        addMatchingSuggestion(out, normalizedPrefix, "duration=");
        addMatchingSuggestion(out, normalizedPrefix, "tint=");
        addMatchingSuggestion(out, normalizedPrefix, "preset=rain");
        addMatchingSuggestion(out, normalizedPrefix, "preset=snow");
        addMatchingSuggestion(out, normalizedPrefix, "preset=sakura");
        addMatchingSuggestion(out, normalizedPrefix, "preset=fireflies");
        addMatchingSuggestion(out, normalizedPrefix, "preset=dust");
        addMatchingSuggestion(out, normalizedPrefix, "preset=leaves");
        addMatchingSuggestion(out, normalizedPrefix, "preset=stop");
        addMatchingSuggestion(out, normalizedPrefix, "intensity=0.5");
        addMatchingSuggestion(out, normalizedPrefix, "layer=100");
      }
      default -> {
        return List.of();
      }
    }

    return out;
  }

  private static void addMatchingSuggestion(List<CodeAutoCompleter.Suggestion> out, String prefix, String insert) {
    String normalizedPrefix = prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
    if (normalizedPrefix.isEmpty() || insert.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) {
      out.add(new CodeAutoCompleter.Suggestion(insert));
    }
  }

  private static String currentBracketCommandName(String text, int caret) {
    if (text == null || text.isEmpty()) return null;
    int safeCaret = Math.max(0, Math.min(caret, text.length()));
    if (safeCaret <= 0) return null;

    int lineStart = text.lastIndexOf('\n', safeCaret - 1) + 1;
    int open = text.lastIndexOf('[', safeCaret - 1);
    if (open < lineStart) return null;
    int close = text.lastIndexOf(']', safeCaret - 1);
    if (close > open) return null;

    String segment = text.substring(open + 1, safeCaret).trim();
    if (segment.isEmpty()) return null;

    int split = 0;
    while (split < segment.length() && !Character.isWhitespace(segment.charAt(split))) split++;
    String command = segment.substring(0, split).trim().toLowerCase(Locale.ROOT);
    return switch (command) {
      case "show", "move", "showpreset", "movepreset", "transition", "particles", "weather", "pfx", "fx" -> command;
      case "particle" -> "particles";
      default -> null;
    };
  }

  private List<String> extractLabels(String text) {
    List<String> res = new ArrayList<>();
    if (text == null || text.isBlank()) return res;

    Matcher modern = Pattern.compile("(?m)^\\s*@label\\s+(\\S+)", Pattern.CASE_INSENSITIVE).matcher(text);
    while (modern.find()) {
      res.add(modern.group(1));
    }

    Matcher legacy = Pattern.compile("(?m)^\\s*label\\s+(\\S+)", Pattern.CASE_INSENSITIVE).matcher(text);
    while (legacy.find()) {
      String name = legacy.group(1);
      if (!res.contains(name)) res.add(name);
    }

    return res;
  }

  private int parseLineFromMessage(String message) {
    if (message == null || message.isBlank()) return -1;
    Matcher m = PARSE_LINE_PATTERN.matcher(message);
    if (!m.find()) return -1;
    try {
      return Integer.parseInt(m.group(1));
    } catch (Exception e) {
      log.warn("Failed to parse line from message: {} - {}", message, e.getMessage());
      return -1;
    }
  }

  private static List<LineInfo> splitLines(String text) {
    List<LineInfo> out = new ArrayList<>();
    if (text == null) {
      out.add(new LineInfo(0, 0, 0, ""));
      return out;
    }
    int lineIndex = 0;
    int start = 0;
    for (int i = 0; i <= text.length(); i++) {
      if (i == text.length() || text.charAt(i) == '\n') {
        String line = text.substring(start, i);
        out.add(new LineInfo(lineIndex, start, i, line));
        lineIndex++;
        start = i + 1;
      }
    }
    if (out.isEmpty()) out.add(new LineInfo(0, 0, 0, ""));
    return out;
  }

  private static int[] lineBounds(String text, int zeroBasedLine) {
    if (zeroBasedLine < 0) return new int[] {0, 0};
    int currentLine = 0;
    int start = 0;
    for (int i = 0; i <= text.length(); i++) {
      if (i == text.length() || text.charAt(i) == '\n') {
        if (currentLine == zeroBasedLine) {
          return new int[] {start, i};
        }
        currentLine++;
        start = i + 1;
      }
    }
    return new int[] {Math.max(0, text.length() - 1), text.length()};
  }

  private int lineInsertOffset(List<LineInfo> lines, int zeroBasedLine) {
    if (lines == null || lines.isEmpty()) return 0;
    int idx = Math.max(0, Math.min(zeroBasedLine, lines.size()));
    if (idx >= lines.size()) {
      return lines.get(lines.size() - 1).end;
    }
    return lines.get(idx).start;
  }

  private void replaceIssueRange(Issue issue, String replacement) {
    if (issue == null) return;
    String text = codeArea.getText();
    int start = Math.max(0, Math.min(issue.start, text.length()));
    int end = Math.max(start, Math.min(issue.end, text.length()));
    codeArea.replaceText(start, end, replacement != null ? replacement : "");
  }

  private void deleteIssueBlock(Issue issue) {
    if (issue == null) return;
    String text = codeArea.getText();
    List<LineInfo> lines = splitLines(text);
    if (lines.isEmpty()) return;

    int startLine = Math.max(0, Math.min(issue.line, lines.size() - 1));
    int endOffset = Math.max(issue.start, issue.blockEnd);
    endOffset = Math.max(0, Math.min(endOffset, text.length()));

    int endLine = startLine;
    for (LineInfo line : lines) {
      if (endOffset >= line.start && endOffset <= line.end) {
        endLine = line.index;
        break;
      }
      if (line.start <= endOffset) endLine = line.index;
    }
    endLine = Math.max(startLine, Math.min(endLine + 1, lines.size()));

    int startOffset = lines.get(startLine).start;
    int deleteTo = endLine >= lines.size()
        ? text.length()
        : lines.get(endLine).start;
    codeArea.replaceText(startOffset, deleteTo, "");
  }

  private static String firstToken(String value) {
    if (value == null) return "";
    String t = value.trim();
    if (t.isEmpty()) return "";
    int sp = t.indexOf(' ');
    return sp < 0 ? t : t.substring(0, sp);
  }

  private static int safeIndexOf(String text, String needle, int fallback) {
    if (text == null || needle == null || needle.isEmpty()) return fallback;
    int idx = text.indexOf(needle);
    return idx >= 0 ? idx : fallback;
  }

  private static List<Span> overlay(List<Span> base, int start, int end, String cls) {
    if (end <= start) return base;
    List<Span> out = new ArrayList<>();
    for (Span span : base) {
      if (span.end <= start || span.start >= end) {
        out.add(span);
        continue;
      }
      if (span.start < start) {
        out.add(new Span(span.start, start, span.styles));
      }
      int os = Math.max(span.start, start);
      int oe = Math.min(span.end, end);
      List<String> merged = new ArrayList<>(span.styles);
      if (!merged.contains(cls)) merged.add(cls);
      out.add(new Span(os, oe, merged));
      if (span.end > end) {
        out.add(new Span(end, span.end, span.styles));
      }
    }
    return out;
  }

  private static List<Span> compress(List<Span> spans) {
    if (spans.isEmpty()) return spans;
    List<Span> out = new ArrayList<>();
    Span current = spans.get(0);
    for (int i = 1; i < spans.size(); i++) {
      Span next = spans.get(i);
      if (current.end == next.start && current.styles.equals(next.styles)) {
        current = new Span(current.start, next.end, current.styles);
      } else {
        out.add(current);
        current = next;
      }
    }
    out.add(current);
    return out;
  }

  private static final class Span {
    final int start;
    final int end;
    final List<String> styles;

    Span(int start, int end, List<String> styles) {
      this.start = start;
      this.end = end;
      this.styles = styles;
    }
  }

  private static final class LineInfo {
    final int index;
    final int start;
    final int end;
    final String text;

    LineInfo(int index, int start, int end, String text) {
      this.index = index;
      this.start = start;
      this.end = end;
      this.text = text;
    }

    String trimmed() {
      return text == null ? "" : text.trim();
    }
  }

  private static final class LabelDef {
    final String name;
    final int line;
    final int tokenStart;
    final int tokenEnd;

    LabelDef(String name, int line, int tokenStart, int tokenEnd) {
      this.name = name;
      this.line = line;
      this.tokenStart = tokenStart;
      this.tokenEnd = tokenEnd;
    }
  }

  private static final class LabelRef {
    final String label;
    final int start;
    final int end;
    final int line;

    LabelRef(String label, int start, int end, int line) {
      this.label = label;
      this.start = start;
      this.end = end;
      this.line = line;
    }
  }

  private static final class Issue {
    enum Severity { ERROR, WARNING, INFO }

    final String kind;
    final String message;
    final int start;
    final int end;
    final int line;
    final Severity severity;
    final String label;
    final String assetPath;
    final int blockEnd;

    private Issue(String kind,
                  String message,
                  int start,
                  int end,
                  int line,
                  Severity severity,
                  String label,
                  String assetPath,
                  int blockEnd) {
      this.kind = kind;
      this.message = message;
      this.start = Math.max(0, start);
      this.end = Math.max(this.start, end);
      this.line = Math.max(0, line);
      this.severity = severity;
      this.label = label;
      this.assetPath = assetPath;
      this.blockEnd = blockEnd;
    }

    boolean isWarning() {
      return severity == Severity.WARNING;
    }

    boolean isInfo() {
      return severity == Severity.INFO;
    }

    static Issue error(String kind,
                       String message,
                       int start,
                       int end,
                       int line,
                       String label,
                       String assetPath,
                       int blockEnd) {
      return new Issue(kind, message, start, end, line, Severity.ERROR, label, assetPath, blockEnd);
    }

    static Issue warning(String kind,
                         String message,
                         int start,
                         int end,
                         int line,
                         String label,
                         String assetPath,
                         int blockEnd) {
      return new Issue(kind, message, start, end, line, Severity.WARNING, label, assetPath, blockEnd);
    }

    static Issue info(String kind,
                      String message,
                      int start,
                      int end,
                      int line,
                      String label,
                      String assetPath,
                      int blockEnd) {
      return new Issue(kind, message, start, end, line, Severity.INFO, label, assetPath, blockEnd);
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Breadcrumb Bar — shows current @label scope
  // ═══════════════════════════════════════════════════════════════════
  private void setupBreadcrumb() {
    HBox breadcrumbBar = new HBox(breadcrumbLabel);
    breadcrumbBar.setAlignment(Pos.CENTER_LEFT);
    breadcrumbBar.setStyle("-fx-background-color: #0e0e0e; -fx-border-color: #2a2a2a; -fx-border-width: 0 0 1 0;");
    breadcrumbBar.setPadding(new Insets(0));
    // We place the breadcrumb between top (search bar) and the code area.
    // We insert it as the top of a VBox that also holds the search bar.
    // Instead, we use a simpler approach: put it as part of the bottom status bar.
    // Actually, integrate into the status bar at the left side.
    codeArea.caretPositionProperty().addListener((obs, ov, nv) -> updateBreadcrumb());
    codeArea.currentParagraphProperty().addListener((obs, ov, nv) -> updateBreadcrumb());
  }

  private void updateBreadcrumb() {
    int para = codeArea.getCurrentParagraph();
    String text = codeArea.getText();
    if (text == null || text.isEmpty()) { breadcrumbLabel.setText(""); return; }
    String[] lines = text.split("\\n", -1);
    String currentLabel = null;
    for (int i = Math.min(para, lines.length - 1); i >= 0; i--) {
      Matcher m = LABEL_SCAN_PATTERN.matcher(lines[i]);
      if (m.find()) { currentLabel = m.group(1); break; }
    }
    breadcrumbLabel.setText(currentLabel != null ? "@ " + currentLabel : "");
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Selection Occurrence Highlighting
  // ═══════════════════════════════════════════════════════════════════
  private String lastSelHighlight = "";

  private void setupSelectionHighlighting() {
    codeArea.selectionProperty().addListener((obs, ov, nv) -> {
      Platform.runLater(this::highlightSelectionOccurrences);
    });
  }

  private void highlightSelectionOccurrences() {
    String sel = codeArea.getSelectedText();
    if (sel == null || sel.length() < 2 || sel.contains("\n")) {
      if (!lastSelHighlight.isEmpty()) {
        lastSelHighlight = "";
        applyAnalysis(codeArea.getText());
      }
      return;
    }
    String word = sel.trim();
    if (word.isEmpty() || word.equals(lastSelHighlight)) return;
    lastSelHighlight = word;
    // Re-apply highlighting with selection overlays
    String text = codeArea.getText();
    if (text == null) return;
    List<Issue> currentIssues = issues;
    List<Span> spans = syntaxSpans(text);
    if (currentIssues != null) {
      for (Issue issue : currentIssues) {
        String cls = switch (issue.severity) {
          case WARNING -> "warning";
          case INFO -> "hint";
          case ERROR -> "error";
        };
        spans = overlay(spans, issue.start, issue.end, cls);
      }
    }
    // Overlay selection highlight on all occurrences
    int idx = 0;
    while ((idx = text.indexOf(word, idx)) >= 0) {
      spans = overlay(spans, idx, idx + word.length(), "sel-highlight");
      idx += word.length();
    }
    StyleSpansBuilder<Collection<String>> out = new StyleSpansBuilder<>();
    for (Span s : compress(spans)) {
      out.add(s.styles, Math.max(0, s.end - s.start));
    }
    try { codeArea.setStyleSpans(0, out.create()); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Bracket/Block Matching Highlight
  // ═══════════════════════════════════════════════════════════════════
  private int lastMatchA = -1, lastMatchB = -1;

  private void setupBracketMatching() {
    codeArea.caretPositionProperty().addListener((obs, ov, nv) -> {
      Platform.runLater(this::highlightMatchingBracket);
    });
  }

  private void highlightMatchingBracket() {
    // Clear previous bracket match styling is handled by re-analysis
    String text = codeArea.getText();
    if (text == null || text.isEmpty()) return;
    int caret = codeArea.getCaretPosition();
    int newA = -1, newB = -1;

    // Check char at caret and caret-1
    for (int offset : new int[]{0, -1}) {
      int pos = caret + offset;
      if (pos < 0 || pos >= text.length()) continue;
      char ch = text.charAt(pos);
      if (ch == '[') {
        int match = findMatchingBracket(text, pos, '[', ']', 1);
        if (match >= 0) { newA = pos; newB = match; break; }
      } else if (ch == ']') {
        int match = findMatchingBracket(text, pos, ']', '[', -1);
        if (match >= 0) { newA = pos; newB = match; break; }
      }
    }

    if (newA != lastMatchA || newB != lastMatchB) {
      lastMatchA = newA;
      lastMatchB = newB;
      // Don't re-apply full analysis — bracket highlighting is cosmetic feedback only
    }
  }

  private int findMatchingBracket(String text, int pos, char open, char close, int dir) {
    int depth = 0;
    for (int i = pos; i >= 0 && i < text.length(); i += dir) {
      char c = text.charAt(i);
      if (c == open) depth++;
      else if (c == close) depth--;
      if (depth == 0) return i;
    }
    return -1;
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Hover Tooltips for VNS commands
  // ═══════════════════════════════════════════════════════════════════
  private static final Map<String, String> VNS_COMMAND_DOCS = new HashMap<>();
  static {
    VNS_COMMAND_DOCS.put("bg", "Set background image. Usage: [bg image_name]");
    VNS_COMMAND_DOCS.put("background", "Set background image. Usage: [background image_name]");
    VNS_COMMAND_DOCS.put("show", "Show a character sprite. Usage: [show character_id pos=center expr=neutral layer=10] or [show character_id center]\n\n"
        + "expr= changes crossfade over 180ms by default. Use dur=0 for an instant swap, or dur=500 for a slower fade.");
    VNS_COMMAND_DOCS.put("move", "Move an existing character sprite or display slot. Usage: [move character_id pos=right expr=smile ease=easeInOut dur=500], [move slot=head at=0.5,0.7], or [move @head at 0.5,0.7]\n\n"
        + "expr= changes crossfade over 180ms by default (dur=0 swaps instantly, dur=500 crossfades over 500ms).");
    VNS_COMMAND_DOCS.put("hide", "Hide a character sprite or display slot. Usage: [hide character], [hide slot=head], or [hide @head]");
    VNS_COMMAND_DOCS.put("showpreset", "Show every slot in a display preset. Usage: [showpreset preset_id] or [showpreset preset_id at 0.5,1.0]");
    VNS_COMMAND_DOCS.put("movepreset", "Move every slot in a display preset. Usage: [movepreset preset_id at 0.5,0.72 ease_out_quad 240]");
    VNS_COMMAND_DOCS.put("hidepreset", "Hide every slot in a display preset. Usage: [hidepreset preset_id]");
    VNS_COMMAND_DOCS.put("jump", "Jump to a label. Usage: [jump label_name]");
    VNS_COMMAND_DOCS.put("end", "End the current scenario. Usage: [end]");
    VNS_COMMAND_DOCS.put("wait", "Pause execution. Usage: [wait seconds]");
    VNS_COMMAND_DOCS.put("bgm", "Play background music. Usage: [bgm audio_file]");
    VNS_COMMAND_DOCS.put("bgm_stop", "Stop background music. Usage: [bgm_stop]");
    VNS_COMMAND_DOCS.put("bgm_crossfade", "Crossfade to new BGM. Usage: [bgm_crossfade audio duration]");
    VNS_COMMAND_DOCS.put("bgm_fadeout", "Fade out BGM. Usage: [bgm_fadeout duration]");
    VNS_COMMAND_DOCS.put("sfx", "Play sound effect. Usage: [sfx audio_file]");
    VNS_COMMAND_DOCS.put("voice", "Play voice clip. Usage: [voice audio_file]");
    VNS_COMMAND_DOCS.put("particles", "Start or stop particle effects. Usage: [particles preset=rain intensity=0.5 layer=100 opacity=0.8 wind=20] or [particles snow texture=assets/vfx/snow.png size=1.5 prewarm=3000]");
    VNS_COMMAND_DOCS.put("weather", "Weather alias for particles. Usage: [weather preset=snow intensity=0.4 layer=120 duration=3000] or [weather stop]");
    VNS_COMMAND_DOCS.put("pfx", "Short alias for particles. Usage: [pfx snow intensity=0.5 speed=2 texture=assets/vfx/snow.png size=1.5 prewarm=3000]");
    VNS_COMMAND_DOCS.put("fx", "Short alias for particles. Usage: [fx rain intensity=0.7 speed=1.2 tint=#88aaff]");
    VNS_COMMAND_DOCS.put("set", "Set a variable. Usage: [set var_name value]");
    VNS_COMMAND_DOCS.put("inc", "Increment a numeric variable. Usage: [inc var_name amount]");
    VNS_COMMAND_DOCS.put("dec", "Decrement a numeric variable. Usage: [dec var_name amount]");
    VNS_COMMAND_DOCS.put("mul", "Multiply a numeric variable. Usage: [mul var_name amount]");
    VNS_COMMAND_DOCS.put("div", "Divide a numeric variable. Usage: [div var_name amount]");
    VNS_COMMAND_DOCS.put("toggle", "Toggle a boolean variable. Usage: [toggle var_name]");
    VNS_COMMAND_DOCS.put("clear", "Clear a variable. Usage: [clear var_name]");
    VNS_COMMAND_DOCS.put("persistent", "Manage persistent variables across saves. Usage: [persistent set key value], [persistent load key], or [persistent reset]");
    VNS_COMMAND_DOCS.put("if", "Conditional branch. Usage: [if condition] ... [endif]");
    VNS_COMMAND_DOCS.put("elif", "Else-if branch. Usage: [elif condition]");
    VNS_COMMAND_DOCS.put("else", "Else branch. Usage: [else]");
    VNS_COMMAND_DOCS.put("endif", "End conditional block. Usage: [endif]");
    VNS_COMMAND_DOCS.put("choice", "Present choices. Usage: > text -> label");
    VNS_COMMAND_DOCS.put("transition", "Screen transition. Usage: [transition type=fade dur=500 bg=room] or [transition fade]");
    VNS_COMMAND_DOCS.put("stage", "Apply a stage lighting preset. Usage: [stage preset_id dur=500]");
    VNS_COMMAND_DOCS.put("volume", "Set volume. Usage: [volume channel level]");
    VNS_COMMAND_DOCS.put("call", "Call a subroutine label when used as [call label], or an interop provider when used as [call provider payload].");
    VNS_COMMAND_DOCS.put("plugin", "Run a registered plugin command. Usage: [plugin command.id arguments...]");
    VNS_COMMAND_DOCS.put("gosub", "Call a subroutine label. Usage: [gosub label]");
    VNS_COMMAND_DOCS.put("return", "Return from subroutine. Usage: [return]");
    VNS_COMMAND_DOCS.put("flag", "Set a boolean flag. Usage: [flag name]");
    VNS_COMMAND_DOCS.put("unflag", "Clear a boolean flag. Usage: [unflag name]");
    VNS_COMMAND_DOCS.put("hud", "Show/hide HUD element. Usage: [hud element state]");
    VNS_COMMAND_DOCS.put("mode", "Switch runtime presentation mode. Usage: [mode dialogue standard], [mode nvl on], or [mode bubble toggle]");
    VNS_COMMAND_DOCS.put("visualizer", "Control the in-scene audio visualizer. Usage: [visualizer on], [visualizer set color=#7de2ff z=-15], or [visualizer off]");
    VNS_COMMAND_DOCS.put("viz", "Shorthand alias for [visualizer]. Usage: [viz toggle]");
    VNS_COMMAND_DOCS.put("java", "Execute Java code. Usage: [java class.method]");
    VNS_COMMAND_DOCS.put("jes", "Execute JES command. Usage: [jes command]");
    VNS_COMMAND_DOCS.put("goto", "Navigate to a local label or another script label. Usage: [goto label] or [goto script:label]");
    VNS_COMMAND_DOCS.put("load", "Replace the current VNS script. Usage: [load scripts/story/chapter2.vns]");
    VNS_COMMAND_DOCS.put("phone", "Run phone UI commands. Usage: [phone open] or [phone chat contact_id]");
    VNS_COMMAND_DOCS.put("menu", "Open a menu. Usage: [menu menu_name]");
    VNS_COMMAND_DOCS.put("@scenario", "Declare scenario metadata. Usage: @scenario name");
    VNS_COMMAND_DOCS.put("@character", "Declare a character with optional persistent sprite scale and speaker color. Usage: @character id \"Display Name\" scale=1.15 color=#RRGGBB");
    VNS_COMMAND_DOCS.put("@background", "Declare a background alias. Usage: @background alias path");
    VNS_COMMAND_DOCS.put("@label", "Define a jump target. Usage: @label name");
    VNS_COMMAND_DOCS.put("@var", "Declare a variable. Usage: @var name default_value");
    VNS_COMMAND_DOCS.put("@define", "Define a constant. Usage: @define name value");
    VNS_COMMAND_DOCS.put("@include", "Include another script. Usage: @include path");
    VNS_COMMAND_DOCS.put("@charimg", "Declare character image. Usage: @charimg char_id path");
    VNS_COMMAND_DOCS.put("@charlayer", "Declare character layer. Usage: @charlayer char_id layer path");
    VNS_COMMAND_DOCS.put("@chargroup", "Declare movable character layer group. Usage: @chargroup char_id group_id [parent=id] [pivot=x,y] $layer | $other_layer");
    VNS_COMMAND_DOCS.put("@charpreset", "Declare character preset. Usage: @charpreset char_id preset layers");
    VNS_COMMAND_DOCS.put("@displaypreset", "Declare a reusable set of display-slot sprites. Usage: @displaypreset bust, then body = body_sprite center z=0");
    VNS_COMMAND_DOCS.put("@stagepreset", "Declare a stage lighting preset file. Usage: @stagepreset id path/to/preset.stagepreset");
    VNS_COMMAND_DOCS.put("@position", "Declare a custom character position. Usage: @position id x [y]");
    VNS_COMMAND_DOCS.put("@bind", "Bind a Java interop variable. Usage: @bind Type:variableName");
    VNS_COMMAND_DOCS.put("@jimport", "Import a Java package or class for VNS Java interop. Usage: @jimport com.example.GameHooks");
    VNS_COMMAND_DOCS.put("@external", "Declare an editor/runtime interop cue. Usage: @external jes_timeline timeline_id");
  }

  /** Package-private accessor for tests: returns the hover doc text for a command/directive key, or null. */
  static String commandHoverDoc(String key) {
    return VNS_COMMAND_DOCS.get(key);
  }

  private final Tooltip hoverTooltip = new Tooltip();

  private void setupHoverTooltips() {
    hoverTooltip.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: #e6e6e6; -fx-font-size: 12px; "
        + "-fx-border-color: #3a3a3a; -fx-border-width: 1; -fx-padding: 6 10 6 10;");
    hoverTooltip.setWrapText(true);
    hoverTooltip.setMaxWidth(520);

    codeArea.setMouseOverTextDelay(java.time.Duration.ofMillis(400));
    codeArea.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, e -> {
      int charIdx = e.getCharacterIndex();
      String text = codeArea.getText();
      if (text == null || charIdx < 0 || charIdx >= text.length()) return;

      // Extract word under cursor
      String word = extractWordAt(text, charIdx);
      if (word.isEmpty()) return;

      if ("timeline".equalsIgnoreCase(word)) {
        FoldRegion timelineRegion = findTimelineRegionAtOffset(charIdx);
        if (timelineRegion != null) {
          hoverTooltip.setText(buildTimelinePreview(timelineRegion));
          hoverTooltip.show(codeArea, e.getScreenPosition().getX() + 10, e.getScreenPosition().getY() + 20);
          return;
        }
      }

      // Check command inside [ ]
      String lookupKey = word.toLowerCase(Locale.ROOT);
      // Also check for @ directives
      if (charIdx > 0 && text.charAt(charIdx - 1) == '@') lookupKey = "@" + lookupKey;
      // Also try the word itself if it starts with @
      if (word.startsWith("@")) lookupKey = word.toLowerCase(Locale.ROOT);

      String doc = VNS_COMMAND_DOCS.get(lookupKey);
      if (doc != null) {
        hoverTooltip.setText(doc);
        hoverTooltip.show(codeArea, e.getScreenPosition().getX() + 10, e.getScreenPosition().getY() + 20);
      }
    });
    codeArea.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_END, e -> {
      hoverTooltip.hide();
    });
  }

  private String extractWordAt(String text, int idx) {
    int start = idx, end = idx;
    while (start > 0 && isWordChar(text.charAt(start - 1))) start--;
    while (end < text.length() && isWordChar(text.charAt(end))) end++;
    return text.substring(start, end);
  }

  private boolean isWordChar(char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '@';
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Code Folding — collapse @label sections and inline timelines
  // ═══════════════════════════════════════════════════════════════════
  private enum FoldKind { LABEL, TIMELINE }

  private record FoldRegion(int startLine, int endLine, int startOffset, int endOffset, FoldKind kind) {
    boolean containsLine(int line) {
      return line >= startLine && line <= endLine;
    }

    boolean containsOffset(int offset) {
      return offset >= startOffset && offset < endOffset;
    }
  }

  /**
   * A single {@code timeline { ... }} block's location within the document,
   * for consumers (e.g. an outline view) that need block boundaries without
   * duplicating this class's brace-scanning.
   */
  public record TimelineOutlineEntry(int oneBasedStartLine, int oneBasedEndLine, int startOffset, int endOffset) {}

  /**
   * Returns the document-order list of {@code timeline { ... }} blocks in the
   * current text, reusing the same cached brace-scan {@link #computeFoldRegions()}
   * already performs for code folding — callers must not re-scan the raw text
   * themselves.
   */
  public List<TimelineOutlineEntry> computeTimelineOutlineEntries() {
    List<TimelineOutlineEntry> entries = new ArrayList<>();
    for (FoldRegion region : computeFoldRegions()) {
      if (region.kind() != FoldKind.TIMELINE) continue;
      entries.add(new TimelineOutlineEntry(
          region.startLine() + 1, region.endLine() + 1, region.startOffset(), region.endOffset()));
    }
    return entries;
  }

  /**
   * Returns the currently-folded {@code timeline { ... }} blocks as stable keys (document-order
   * ordinal among timeline blocks + a hash of the block's header line), suitable for persisting
   * across script reopens. Line-number-based fold state ({@link #foldedRegionStarts}) does not
   * survive edits that shift lines, so callers must use this instead of that set directly.
   */
  public List<VnsFoldStateStore.FoldedBlockKey> exportFoldedTimelineBlocks() {
    List<VnsFoldStateStore.FoldedBlockKey> keys = new ArrayList<>();
    int ordinal = 0;
    for (FoldRegion region : computeFoldRegions()) {
      if (region.kind() != FoldKind.TIMELINE) continue;
      if (foldedRegionStarts.contains(region.startLine())) {
        keys.add(new VnsFoldStateStore.FoldedBlockKey(ordinal, timelineHeaderHash(region)));
      }
      ordinal++;
    }
    return keys;
  }

  /**
   * Applies previously exported fold state to the current document. Keys whose ordinal no
   * longer exists, or whose header hash no longer matches the block at that ordinal, are
   * dropped silently — this is what prevents folding an unrelated block after edits move or
   * insert timelines above the folded one.
   */
  public void restoreFoldedTimelineBlocks(List<VnsFoldStateStore.FoldedBlockKey> keys) {
    if (keys == null || keys.isEmpty()) return;
    Map<Integer, String> wanted = new HashMap<>();
    for (VnsFoldStateStore.FoldedBlockKey key : keys) {
      wanted.put(key.ordinal(), key.headerHash());
    }
    boolean changed = false;
    int ordinal = 0;
    for (FoldRegion region : computeFoldRegions()) {
      if (region.kind() != FoldKind.TIMELINE) continue;
      String wantedHash = wanted.get(ordinal);
      if (wantedHash != null && wantedHash.equals(timelineHeaderHash(region))
          && !foldedRegionStarts.contains(region.startLine())) {
        foldedRegionStarts.add(region.startLine());
        changed = true;
      }
      ordinal++;
    }
    if (changed) {
      refreshFoldedRegionStyles();
      Platform.runLater(() -> codeArea.setParagraphGraphicFactory(this::makeLineNumberLabel));
      scheduleMinimapRedraw();
    }
  }

  private String timelineHeaderHash(FoldRegion region) {
    String header = codeArea.getParagraph(region.startLine()).getText();
    return Integer.toHexString(header.hashCode());
  }

  void toggleTimelineFoldAtLineForTest(int line) {
    toggleFold(line);
  }

  /**
   * Parses the given timeline block's text and returns its approximate
   * duration already formatted (ms below one second, seconds above),
   * matching the format used by the timeline hover preview/summary. Returns
   * {@code null} if the block fails to parse.
   */
  public static String formatTimelineOutlineDuration(String blockText) {
    if (blockText == null || blockText.isBlank()) return null;
    try {
      TimelineData data = TimelineDataParser.parse("_vns_editor_outline", blockText);
      return formatTimelineDurationStatic(data.getDurationMs());
    } catch (Exception ex) {
      return null;
    }
  }

  private static String formatTimelineDurationStatic(double durationMs) {
    if (durationMs < 1000.0) {
      return String.format(Locale.ROOT, "%.0f ms", durationMs);
    }
    return String.format(Locale.ROOT, "%.2f s", durationMs / 1000.0);
  }

  private record TimelineNavTarget(FoldRegion region, int ordinal, int total, int visibleStart, int visibleEnd) {}

  private record TimelineNavSummary(String detail, String targets) {}

  private void setupCodeFolding() {
    // Code folding is managed through the gutter (makeLineNumberLabel).
    // RichTextFX collapses paragraphs when their paragraph style includes "collapse".
  }

  private List<FoldRegion> computeFoldRegions() {
    if (!foldRegionCacheDirty) return foldRegionCache;
    foldRegionCacheDirty = false;
    String text = pendingAnalysisText.isEmpty() ? codeArea.getText() : pendingAnalysisText;
    if (text.isEmpty()) {
      foldRegionCache = List.of();
      return foldRegionCache;
    }

    int[] lineStarts = computeLineStarts(text);
    String[] lines = text.split("\\n", -1);
    List<FoldRegion> regions = new ArrayList<>();

    addLabelFoldRegions(text, lines, lineStarts, regions);
    addTimelineFoldRegions(text, lines, lineStarts, regions);
    regions.sort((a, b) -> {
      int byLine = Integer.compare(a.startLine(), b.startLine());
      if (byLine != 0) return byLine;
      return a.kind().compareTo(b.kind());
    });
    foldRegionCache = Collections.unmodifiableList(regions);
    return foldRegionCache;
  }

  private void addLabelFoldRegions(String text, String[] lines, int[] lineStarts, List<FoldRegion> regions) {
    int lastLabelLine = -1;
    for (int i = 0; i < lines.length; i++) {
      if (LABEL_SCAN_PATTERN.matcher(lines[i]).find()) {
        if (lastLabelLine >= 0 && i - lastLabelLine > 1) {
          int endLine = i - 1;
          regions.add(new FoldRegion(
              lastLabelLine,
              endLine,
              lineStarts[lastLabelLine],
              lineEndOffset(text, lineStarts, endLine),
              FoldKind.LABEL));
        }
        lastLabelLine = i;
      }
    }
    if (lastLabelLine >= 0 && lines.length - 1 > lastLabelLine) {
      int endLine = lines.length - 1;
      regions.add(new FoldRegion(
          lastLabelLine,
          endLine,
          lineStarts[lastLabelLine],
          lineEndOffset(text, lineStarts, endLine),
          FoldKind.LABEL));
    }
  }

  static boolean hasTimelineFoldRegionStartingAt(String text, int line) {
    if (text == null || text.isBlank() || line < 0) return false;
    int[] lineStarts = computeLineStarts(text);
    String[] lines = text.split("\\n", -1);
    List<FoldRegion> regions = new ArrayList<>();
    addTimelineFoldRegions(text, lines, lineStarts, regions);
    for (FoldRegion region : regions) {
      if (region.kind() == FoldKind.TIMELINE && region.startLine() == line && region.endLine() > line) {
        return true;
      }
    }
    return false;
  }

  private static void addTimelineFoldRegions(String text, String[] lines, int[] lineStarts, List<FoldRegion> regions) {
    for (int i = 0; i < lines.length; i++) {
      Matcher m = TIMELINE_SCAN_PATTERN.matcher(lines[i]);
      if (!m.find()) continue;

      int startOffset = lineStarts[i] + m.start();
      int keywordEndOffset = lineStarts[i] + timelineKeywordEnd(lines[i], m.start(), m.end());
      int openBrace = findTimelineOpeningBrace(text, lineStarts, i, keywordEndOffset);
      if (openBrace < 0) continue;

      int closeBrace = findMatchingBrace(text, openBrace);
      if (closeBrace < 0) continue;

      int endLine = lineForOffset(lineStarts, closeBrace);
      if (endLine <= i) continue;
      regions.add(new FoldRegion(i, endLine, startOffset, Math.min(text.length(), closeBrace + 1), FoldKind.TIMELINE));
    }
  }

  private static int timelineKeywordEnd(String line, int matchStart, int fallbackEnd) {
    if (line == null) return fallbackEnd;
    int i = Math.max(0, Math.min(matchStart, line.length()));
    while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
    int end = i + "timeline".length();
    if (end <= line.length() && line.regionMatches(true, i, "timeline", 0, "timeline".length())) {
      return end;
    }
    return fallbackEnd;
  }

  private FoldRegion findFoldRegionStartingAtLine(int line) {
    FoldRegion fallback = null;
    for (FoldRegion region : computeFoldRegions()) {
      if (region.startLine() != line) continue;
      if (region.kind() == FoldKind.TIMELINE) return region;
      fallback = region;
    }
    return fallback;
  }

  private FoldRegion findTimelineRegionForParagraph(int paragraph) {
    FoldRegion match = null;
    for (FoldRegion region : computeFoldRegions()) {
      if (region.kind() == FoldKind.TIMELINE && region.containsLine(paragraph)) {
        match = region;
      }
    }
    return match;
  }

  private FoldRegion findTimelineRegionAtOffset(int offset) {
    FoldRegion match = null;
    for (FoldRegion region : computeFoldRegions()) {
      if (region.kind() == FoldKind.TIMELINE && region.containsOffset(offset)) {
        match = region;
      }
    }
    return match;
  }

  private boolean hasFoldedTimelineBlocks() {
    if (foldedRegionStarts.isEmpty()) return false;
    for (FoldRegion region : computeFoldRegions()) {
      if (region.kind() == FoldKind.TIMELINE && foldedRegionStarts.contains(region.startLine())) {
        return true;
      }
    }
    return false;
  }

  private static int[] computeLineStarts(String text) {
    List<Integer> starts = new ArrayList<>();
    starts.add(0);
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n') {
        starts.add(i + 1);
      }
    }
    int[] out = new int[starts.size()];
    for (int i = 0; i < starts.size(); i++) out[i] = starts.get(i);
    return out;
  }

  private static int lineEndOffset(String text, int[] lineStarts, int line) {
    if (line < 0 || line >= lineStarts.length) return text.length();
    int nextLine = line + 1;
    if (nextLine < lineStarts.length) {
      return Math.max(lineStarts[line], lineStarts[nextLine] - 1);
    }
    return text.length();
  }

  private static int lineForOffset(int[] lineStarts, int offset) {
    if (lineStarts.length == 0) return 0;
    int clamped = Math.max(0, offset);
    int lo = 0;
    int hi = lineStarts.length - 1;
    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      int start = lineStarts[mid];
      int next = mid + 1 < lineStarts.length ? lineStarts[mid + 1] : Integer.MAX_VALUE;
      if (clamped < start) {
        hi = mid - 1;
      } else if (clamped >= next) {
        lo = mid + 1;
      } else {
        return mid;
      }
    }
    return Math.max(0, Math.min(lineStarts.length - 1, hi));
  }

  private static int findTimelineOpeningBrace(String text, int[] lineStarts, int line, int keywordEndOffset) {
    int sameLineEnd = lineEndOffset(text, lineStarts, line);
    for (int i = keywordEndOffset; i < sameLineEnd && i < text.length(); i++) {
      if (text.charAt(i) == '{') return i;
    }

    int i = sameLineEnd;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (c == '{') return i;
      if (c == '\n' || Character.isWhitespace(c)) {
        i++;
        continue;
      }
      if (c == '#') {
        i = skipLine(text, i);
        continue;
      }
      if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
        i = skipLine(text, i + 2);
        continue;
      }
      return -1;
    }
    return -1;
  }

  private static int findMatchingBrace(String text, int openBraceOffset) {
    int depth = 0;
    boolean inString = false;
    boolean escaped = false;
    boolean inLineComment = false;

    for (int i = openBraceOffset; i < text.length(); i++) {
      char c = text.charAt(i);

      if (inLineComment) {
        if (c == '\n') inLineComment = false;
        continue;
      }

      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      }

      if (c == '"') {
        inString = true;
        continue;
      }
      if (c == '#') {
        inLineComment = true;
        continue;
      }
      if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
        inLineComment = true;
        i++;
        continue;
      }
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) return i;
        if (depth < 0) return -1;
      }
    }
    return -1;
  }

  private static int skipLine(String text, int offset) {
    int i = Math.max(0, offset);
    while (i < text.length() && text.charAt(i) != '\n') i++;
    return i;
  }

  private void toggleFold(int paragraph) {
    FoldRegion region = findFoldRegionStartingAtLine(paragraph);
    if (region == null) return;
    if (foldedRegionStarts.contains(paragraph)) {
      foldedRegionStarts.remove(paragraph);
    } else {
      foldedRegionStarts.add(paragraph);
    }
    refreshFoldedRegionStyles();
    Platform.runLater(() -> codeArea.setParagraphGraphicFactory(this::makeLineNumberLabel));
    scheduleMinimapRedraw();
    notifyFoldStateChanged();
  }

  private void toggleTimelineBlockAtCaret() {
    FoldRegion region = findTimelineRegionForParagraph(codeArea.getCurrentParagraph());
    if (region == null) return;
    toggleFold(region.startLine());
  }

  private void unfoldAllTimelineBlocks() {
    boolean changed = false;
    for (FoldRegion region : computeFoldRegions()) {
      if (region.kind() == FoldKind.TIMELINE) {
        changed |= foldedRegionStarts.remove(region.startLine());
      }
    }
    if (changed) {
      refreshFoldedRegionStyles();
      Platform.runLater(() -> codeArea.setParagraphGraphicFactory(this::makeLineNumberLabel));
      scheduleMinimapRedraw();
      notifyFoldStateChanged();
    }
  }

  private void autoFoldPastedTimelines(int preInsertOffset) {
    int postOffset = codeArea.getCaretPosition();
    if (postOffset <= preInsertOffset) return;
    boolean changed = false;
    for (FoldRegion region : computeFoldRegions()) {
      if (region.kind() != FoldKind.TIMELINE) continue;
      if (foldedRegionStarts.contains(region.startLine())) continue;
      // Fold regions whose start falls within the pasted range
      if (region.startOffset() >= preInsertOffset && region.startOffset() < postOffset) {
        foldedRegionStarts.add(region.startLine());
        changed = true;
      }
    }
    if (changed) {
      refreshFoldedRegionStyles();
      Platform.runLater(() -> codeArea.setParagraphGraphicFactory(this::makeLineNumberLabel));
      scheduleMinimapRedraw();
      notifyFoldStateChanged();
    }
  }

  private void notifyFoldStateChanged() {
    if (onFoldStateChanged != null) onFoldStateChanged.run();
  }

  private void refreshFoldedRegionStyles() {
    int paragraphCount = codeArea.getParagraphs().size();

    // Build the desired set of collapsed paragraphs from the current fold state.
    Set<Integer> desired = new HashSet<>();
    Set<Integer> validStarts = new HashSet<>();
    for (FoldRegion region : computeFoldRegions()) {
      if (!foldedRegionStarts.contains(region.startLine())) continue;
      validStarts.add(region.startLine());
      int end = Math.min(region.endLine(), paragraphCount - 1);
      for (int i = region.startLine() + 1; i <= end; i++) {
        desired.add(i);
      }
    }
    foldedRegionStarts.retainAll(validStarts);

    // Remove collapse style only from paragraphs that no longer need it.
    // This avoids iterating all paragraphs (previously O(total)) — now O(currently-collapsed).
    for (int p : appliedCollapseParagraphs) {
      if (!desired.contains(p)) {
        removeParagraphStyleClasses(p, Set.of(RTFX_COLLAPSE_STYLE_CLASS, LEGACY_FOLDED_STYLE_CLASS));
      }
    }
    // Add collapse style only to paragraphs that newly need it.
    for (int p : desired) {
      if (!appliedCollapseParagraphs.contains(p)) {
        addParagraphStyleClass(p, RTFX_COLLAPSE_STYLE_CLASS);
      }
    }
    appliedCollapseParagraphs.clear();
    appliedCollapseParagraphs.addAll(desired);
  }

  private void addParagraphStyleClass(int paragraph, String styleClass) {
    if (paragraph < 0 || paragraph >= codeArea.getParagraphs().size() || styleClass == null || styleClass.isBlank()) {
      return;
    }
    LinkedHashSet<String> styles = new LinkedHashSet<>(codeArea.getParagraph(paragraph).getParagraphStyle());
    if (styles.add(styleClass)) {
      codeArea.setParagraphStyle(paragraph, styles);
    }
  }

  private void removeParagraphStyleClass(int paragraph, String styleClass) {
    if (paragraph < 0 || paragraph >= codeArea.getParagraphs().size() || styleClass == null || styleClass.isBlank()) {
      return;
    }
    LinkedHashSet<String> styles = new LinkedHashSet<>(codeArea.getParagraph(paragraph).getParagraphStyle());
    if (styles.remove(styleClass)) {
      codeArea.setParagraphStyle(paragraph, styles);
    }
  }

  private void removeParagraphStyleClasses(int paragraph, Set<String> styleClasses) {
    if (paragraph < 0 || paragraph >= codeArea.getParagraphs().size() || styleClasses == null || styleClasses.isEmpty()) {
      return;
    }
    LinkedHashSet<String> styles = new LinkedHashSet<>(codeArea.getParagraph(paragraph).getParagraphStyle());
    if (styles.removeAll(styleClasses)) {
      codeArea.setParagraphStyle(paragraph, styles);
    }
  }

  // ─── Timeline nav overlay ─────────────────────────────────────────

  private VBox buildTimelineNavOverlay() {
    timelineNavTitle = new Label("Timeline");
    timelineNavTitle.getStyleClass().add("timeline-nav-title");
    timelineNavTitle.setMaxWidth(Double.MAX_VALUE);
    timelineNavProgress = new Label("");
    timelineNavProgress.getStyleClass().add("timeline-nav-progress");
    HBox.setHgrow(timelineNavTitle, Priority.ALWAYS);
    HBox titleRow = new HBox(12, timelineNavTitle, timelineNavProgress);
    titleRow.setAlignment(Pos.CENTER_LEFT);
    timelineNavMeta = new Label("");
    timelineNavMeta.getStyleClass().add("timeline-nav-meta");
    timelineNavSummary = new Label("");
    timelineNavSummary.getStyleClass().add("timeline-nav-summary");
    timelineNavMeta.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
    timelineNavSummary.setWrapText(false);
    timelineNavSummary.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);

    timelineNavTopButton = timelineNavButton("Top");
    timelineNavBottomButton = timelineNavButton("Bottom");
    timelineNavTopButton.setOnAction(e -> navigateToTimelineEdge(true));
    timelineNavBottomButton.setOnAction(e -> navigateToTimelineEdge(false));

    HBox buttons = new HBox(5, timelineNavTopButton, timelineNavBottomButton);
    buttons.setAlignment(Pos.CENTER_RIGHT);
    VBox box = new VBox(2, titleRow, timelineNavMeta, timelineNavSummary, buttons);
    box.getStyleClass().add("timeline-nav-overlay");
    box.setMaxWidth(360);
    box.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
    box.setFillWidth(true);
    box.setVisible(false);
    box.setManaged(false);
    box.setOpacity(0.0);
    box.setTranslateY(8.0);
    box.setOnMouseEntered(e -> timelineNavHideDelay.stop());
    box.setOnMouseExited(e -> {
      if (activeScrollTimeline != null) timelineNavHideDelay.playFromStart();
    });
    timelineNavHideDelay.setOnFinished(e -> hideTimelineNavOverlay());
    return box;
  }

  private Button timelineNavButton(String text) {
    Button button = new Button(text);
    button.getStyleClass().add("timeline-nav-button");
    button.setFocusTraversable(false);
    return button;
  }

  private void updateTimelineNavOverlay() {
    if (timelineNavOverlay == null) return;

    int visibleStart, visibleEnd;
    try {
      visibleStart = codeArea.firstVisibleParToAllParIndex();
      visibleEnd = codeArea.lastVisibleParToAllParIndex();
    } catch (Exception ignored) {
      return;
    }
    if (visibleStart < 0 || visibleEnd < 0 || visibleEnd < visibleStart) return;

    TimelineNavTarget target = findTimelineNavTarget(visibleStart, visibleEnd);
    if (target == null) {
      activeScrollTimeline = null;
      activeScrollTimelineKey = "";
      timelineNavHideDelay.stop();
      hideTimelineNavOverlay();
      return;
    }

    activeScrollTimeline = target.region();
    updateTimelineNavContent(target);
    showTimelineNavOverlay();
    timelineNavHideDelay.playFromStart();
  }

  private TimelineNavTarget findTimelineNavTarget(int visibleStart, int visibleEnd) {
    List<FoldRegion> regions = computeFoldRegions();
    int visibleLines = Math.max(1, visibleEnd - visibleStart + 1);
    int visibleCenter = visibleStart + visibleLines / 2;
    FoldRegion best = null;
    int bestScore = Integer.MIN_VALUE;
    int bestOrdinal = -1;
    int total = 0;

    for (FoldRegion region : regions) {
      if (region.kind() != FoldKind.TIMELINE) continue;
      total++;
      if (foldedRegionStarts.contains(region.startLine())) continue;

      int timelineLines = Math.max(1, region.endLine() - region.startLine() + 1);
      if (timelineLines <= Math.max(18, visibleLines + 2)) continue;

      int overlapStart = Math.max(region.startLine(), visibleStart);
      int overlapEnd = Math.min(region.endLine(), visibleEnd);
      int overlap = overlapEnd - overlapStart + 1;
      if (overlap <= 0) continue;

      boolean centerInside = region.containsLine(visibleCenter);
      boolean viewportMostlyInside = overlap >= Math.max(3, (int) Math.ceil(visibleLines * 0.45));
      if (!centerInside && !viewportMostlyInside) continue;

      int score = overlap;
      if (centerInside) score += 10_000;
      if (region.startLine() < visibleStart && region.endLine() > visibleEnd) score += 2_000;
      if (score > bestScore) {
        best = region;
        bestScore = score;
        bestOrdinal = total;
      }
    }

    if (best == null) return null;
    return new TimelineNavTarget(best, bestOrdinal, total, visibleStart, visibleEnd);
  }

  private void updateTimelineNavContent(TimelineNavTarget target) {
    FoldRegion region = target.region();
    String key = timelineNavKey(region);
    int visibleCenter = target.visibleStart() + Math.max(1, target.visibleEnd() - target.visibleStart() + 1) / 2;
    int currentLine = Math.max(region.startLine(), Math.min(region.endLine(), visibleCenter));
    int lineCount = Math.max(1, region.endLine() - region.startLine() + 1);
    int percent = lineCount <= 1
        ? 100
        : (int) Math.round(((currentLine - region.startLine()) * 100.0) / (lineCount - 1));

    if (!key.equals(activeScrollTimelineKey)) {
      activeScrollTimelineKey = key;
      TimelineNavSummary summary = timelineNavSummary(region);
      timelineNavTitle.setText("Timeline " + target.ordinal() + " of " + target.total());
      timelineNavMeta.setText("Lines " + (region.startLine() + 1) + "-" + (region.endLine() + 1)
          + "  |  " + lineCount + " lines  |  " + summary.detail());
      timelineNavSummary.setText(summary.targets());
    }

    timelineNavTitle.setText("Timeline " + target.ordinal() + " of " + target.total());
    timelineNavProgress.setText(percent + "%  through");
    timelineNavTopButton.setDisable(region.startLine() >= target.visibleStart()
        && region.startLine() <= target.visibleEnd());
    timelineNavBottomButton.setDisable(region.endLine() >= target.visibleStart()
        && region.endLine() <= target.visibleEnd());
  }

  private TimelineNavSummary timelineNavSummary(FoldRegion region) {
    String key = timelineNavKey(region);
    TimelineNavSummary cached = timelineNavSummaryCache.get(key);
    if (cached != null) return cached;

    String text = codeArea.getText();
    if (text == null || region.startOffset() < 0 || region.endOffset() > text.length()) {
      TimelineNavSummary fallback = new TimelineNavSummary("unparsed", "No timeline details available.");
      timelineNavSummaryCache.put(key, fallback);
      return fallback;
    }

    String block = text.substring(region.startOffset(), region.endOffset());
    int actionCount = countTimelineActions(block);
    TimelineNavSummary summary;
    try {
      TimelineData data = TimelineDataParser.parse("_vns_editor_nav", block);
      StringBuilder detail = new StringBuilder(formatTimelineDuration(data.getDurationMs()))
          .append("  |  ")
          .append(data.getTracks().size()).append(data.getTracks().size() == 1 ? " track" : " tracks");
      if (actionCount > 0) {
        detail.append("  |  ").append(actionCount).append(actionCount == 1 ? " action" : " actions");
      }
      String targets = summarizeTimelineTargets(data, block);
      summary = new TimelineNavSummary(detail.toString(),
          targets.isBlank() ? "No named layer targets detected." : "Targets: " + targets);
    } catch (Exception ex) {
      String detail = actionCount > 0
          ? actionCount + (actionCount == 1 ? " action" : " actions")
          : "parser preview unavailable";
      summary = new TimelineNavSummary(detail, "Preview parser could not read this block yet.");
    }
    timelineNavSummaryCache.put(key, summary);
    return summary;
  }

  private String timelineNavKey(FoldRegion region) {
    return region == null
        ? ""
        : region.startLine() + ":" + region.endLine() + ":" + region.startOffset() + ":" + region.endOffset();
  }

  private void navigateToTimelineEdge(boolean top) {
    FoldRegion region = activeScrollTimeline;
    if (region == null) return;
    int line = top ? region.startLine() : region.endLine();
    if (top) {
      codeArea.showParagraphAtTop(line);
    } else {
      codeArea.showParagraphAtBottom(line);
    }
    codeArea.moveTo(line, 0);
    codeArea.requestFocus();
    Platform.runLater(this::updateTimelineNavOverlay);
  }

  private void showTimelineNavOverlay() {
    if (timelineNavShowing && timelineNavOverlay.isVisible()) return;
    playTimelineNavAnimation(true);
  }

  private void hideTimelineNavOverlay() {
    if (!timelineNavShowing && !timelineNavOverlay.isVisible()) return;
    playTimelineNavAnimation(false);
  }

  private void playTimelineNavAnimation(boolean showing) {
    timelineNavShowing = showing;
    if (timelineNavFade != null) timelineNavFade.stop();
    if (timelineNavSlide != null) timelineNavSlide.stop();

    if (showing) {
      timelineNavOverlay.setManaged(true);
      timelineNavOverlay.setVisible(true);
    }

    timelineNavFade = new FadeTransition(Duration.millis(showing ? 140 : 120), timelineNavOverlay);
    timelineNavFade.setFromValue(timelineNavOverlay.getOpacity());
    timelineNavFade.setToValue(showing ? 1.0 : 0.0);
    timelineNavFade.setInterpolator(Interpolator.EASE_OUT);
    timelineNavFade.setOnFinished(e -> {
      if (!showing) {
        timelineNavOverlay.setVisible(false);
        timelineNavOverlay.setManaged(false);
      }
    });

    timelineNavSlide = new TranslateTransition(Duration.millis(showing ? 160 : 120), timelineNavOverlay);
    timelineNavSlide.setFromY(timelineNavOverlay.getTranslateY());
    timelineNavSlide.setToY(showing ? 0.0 : 8.0);
    timelineNavSlide.setInterpolator(Interpolator.EASE_OUT);

    timelineNavFade.play();
    timelineNavSlide.play();
  }

  private void showTimelinePreviewPopup(FoldRegion region, javafx.scene.Node anchor) {
    previewHideDelay.stop();
    if (timelinePreviewPopup == null) {
      timelinePreviewContent = new Label();
      timelinePreviewContent.setWrapText(true);
      timelinePreviewContent.setMaxWidth(400);
      timelinePreviewContent.setStyle(
          "-fx-background-color: #1e2030; -fx-text-fill: #cdd6f4; -fx-font-size: 11px;" +
          "-fx-padding: 8 10 8 10; -fx-font-family: monospace;");
      timelinePreviewPopup = new Popup();
      timelinePreviewPopup.setAutoHide(true);
      timelinePreviewPopup.getContent().add(timelinePreviewContent);
    }
    timelinePreviewContent.setText(buildTimelinePreview(region));
    javafx.geometry.Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
    if (bounds != null) {
      timelinePreviewPopup.show(anchor, bounds.getMaxX() + 6, bounds.getMinY());
    }
  }

  private void scheduleHideTimelinePreviewPopup() {
    previewHideDelay.setOnFinished(e -> {
      if (timelinePreviewPopup != null) timelinePreviewPopup.hide();
    });
    previewHideDelay.playFromStart();
  }

  private String buildTimelinePreview(FoldRegion region) {
    String text = codeArea.getText();
    if (text == null || region == null || region.startOffset() < 0 || region.endOffset() > text.length()) {
      return "Timeline block";
    }

    String block = text.substring(region.startOffset(), region.endOffset());
    int lineCount = Math.max(1, region.endLine() - region.startLine() + 1);
    int actionCount = countTimelineActions(block);
    StringBuilder preview = new StringBuilder();

    try {
      TimelineData data = TimelineDataParser.parse("_vns_editor_preview", block);
      preview.append("Timeline block")
          .append("  |  ").append(formatTimelineDuration(data.getDurationMs()))
          .append("  |  ").append(data.getTracks().size()).append(data.getTracks().size() == 1 ? " track" : " tracks");
      if (actionCount > 0) {
        preview.append("  |  ").append(actionCount).append(actionCount == 1 ? " action" : " actions");
      }
      if (!data.getAudioCues().isEmpty()) {
        preview.append("  |  ").append(data.getAudioCues().size()).append(data.getAudioCues().size() == 1 ? " audio cue" : " audio cues");
      }
      if (!data.getEventCues().isEmpty()) {
        preview.append("  |  ").append(data.getEventCues().size()).append(data.getEventCues().size() == 1 ? " event" : " events");
      }

      String targets = summarizeTimelineTargets(data, block);
      if (!targets.isBlank()) {
        preview.append("\nTargets: ").append(targets);
      }
    } catch (Exception ex) {
      preview.append("Timeline block")
          .append("  |  ").append(lineCount).append(lineCount == 1 ? " line" : " lines");
      if (actionCount > 0) {
        preview.append("  |  ").append(actionCount).append(actionCount == 1 ? " action" : " actions");
      }
      preview.append("\nPreview parser could not read this block yet.");
    }

    String snippet = buildTimelineSnippet(block);
    if (!snippet.isBlank()) {
      preview.append("\n\n").append(snippet);
    }
    return preview.toString();
  }

  private String summarizeTimelineTargets(TimelineData data, String block) {
    LinkedHashSet<String> targets = new LinkedHashSet<>();
    if (data != null) {
      for (TimelineData.Track track : data.getTracks()) {
        String name = track.getEntityName();
        if (name != null && !name.isBlank()) {
          targets.add(name);
        }
      }
    }
    if (targets.isEmpty()) {
      for (String line : block.split("\\R")) {
        Matcher m = TIMELINE_TARGET_SCAN_PATTERN.matcher(line);
        if (m.find()) targets.add(m.group(1));
        if (TIMELINE_ACTION_SCAN_PATTERN.matcher(line).find()
            && (line.trim().startsWith("cameraMove") || line.trim().startsWith("cameraZoom"))) {
          targets.add("__camera__");
        }
      }
    }
    if (targets.isEmpty()) return "";

    StringBuilder out = new StringBuilder();
    int index = 0;
    int total = targets.size();
    for (String target : targets) {
      if (index >= 5) {
        out.append(", +").append(total - index).append(" more");
        break;
      }
      if (index > 0) out.append(", ");
      out.append(target);
      index++;
    }
    return out.toString();
  }

  private int countTimelineActions(String block) {
    int count = 0;
    for (String line : block.split("\\R")) {
      if (TIMELINE_ACTION_SCAN_PATTERN.matcher(line).find()) count++;
    }
    return count;
  }

  private String buildTimelineSnippet(String block) {
    String[] lines = block.split("\\R", -1);
    List<String> previewLines = new ArrayList<>();
    int nonBlank = 0;
    for (String line : lines) {
      String trimmed = line.strip();
      if (trimmed.isEmpty()) continue;
      nonBlank++;
      if (previewLines.size() >= 8) continue;
      previewLines.add(trimForPreview(trimmed, 86));
    }
    if (nonBlank > previewLines.size()) {
      previewLines.add("...");
    }
    return String.join("\n", previewLines);
  }

  private String trimForPreview(String value, int maxChars) {
    if (value == null) return "";
    if (value.length() <= maxChars) return value;
    return value.substring(0, Math.max(0, maxChars - 3)) + "...";
  }

  private String formatTimelineDuration(double durationMs) {
    if (durationMs < 1000.0) {
      return String.format(Locale.ROOT, "%.0f ms", durationMs);
    }
    return String.format(Locale.ROOT, "%.2f s", durationMs / 1000.0);
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Go-to-Line (Ctrl+G)
  // ═══════════════════════════════════════════════════════════════════
  private void showGoToLineDialog() {
    EditorDialogs.promptText(
        getScene() == null ? null : getScene().getWindow(),
        "Go to Line",
        "Jump to a specific line in the current script.",
        "Line number",
        String.valueOf(codeArea.getCurrentParagraph() + 1),
        "1",
        "Go").ifPresent(val -> {
      try {
        int line = Integer.parseInt(val.trim());
        goToLine(line);
      } catch (NumberFormatException ignored) { // reason: malformed numeric text input; caller uses fallback value
        }
    });
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Delete Line (Ctrl+Shift+K)
  // ═══════════════════════════════════════════════════════════════════
  private void deleteLine() {
    int para = codeArea.getCurrentParagraph();
    int paraCount = codeArea.getParagraphs().size();
    if (paraCount <= 0) return;
    int lineStart = codeArea.getAbsolutePosition(para, 0);
    int lineEnd;
    if (para < paraCount - 1) {
      lineEnd = codeArea.getAbsolutePosition(para + 1, 0);
    } else {
      String lineText = codeArea.getParagraph(para).getText();
      lineEnd = lineStart + lineText.length();
      // Also remove the preceding newline if not the first line
      if (para > 0 && lineStart > 0) lineStart--;
    }
    codeArea.replaceText(lineStart, lineEnd, "");
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Block Indent / Outdent (Tab / Shift+Tab)
  // ═══════════════════════════════════════════════════════════════════
  private void blockIndent() {
    int selStart = codeArea.getSelection().getStart();
    int selEnd = codeArea.getSelection().getEnd();
    int startPara = codeArea.offsetToPosition(selStart, org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor();
    int endPara = codeArea.offsetToPosition(selEnd, org.fxmisc.richtext.model.TwoDimensional.Bias.Backward).getMajor();
    for (int i = endPara; i >= startPara; i--) {
      int pos = codeArea.getAbsolutePosition(i, 0);
      codeArea.insertText(pos, "  ");
    }
  }

  private void blockOutdent() {
    int selStart = codeArea.getSelection().getStart();
    int selEnd = codeArea.getSelection().getEnd();
    int startPara = codeArea.offsetToPosition(selStart, org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor();
    int endPara = codeArea.offsetToPosition(selEnd, org.fxmisc.richtext.model.TwoDimensional.Bias.Backward).getMajor();
    for (int i = endPara; i >= startPara; i--) {
      String line = codeArea.getParagraph(i).getText();
      int pos = codeArea.getAbsolutePosition(i, 0);
      if (line.startsWith("  ")) {
        codeArea.replaceText(pos, pos + 2, "");
      } else if (line.startsWith(" ")) {
        codeArea.replaceText(pos, pos + 1, "");
      } else if (line.startsWith("\t")) {
        codeArea.replaceText(pos, pos + 1, "");
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Snippet Palette (Ctrl+J)
  // ═══════════════════════════════════════════════════════════════════
  private static final String[][] VNS_SNIPPETS = {
      {"Dialogue", "Speaker: \"Dialogue text here.\""},
      {"Narration", "narrator \"Narration text here.\""},
      {"Choice Block", "> Option A -> label_a\n> Option B -> label_b"},
      {"Label", "@label label_name"},
      {"Jump", "[jump label_name]"},
      {"Goto", "[goto label_name]"},
      {"Background", "[bg background_name]"},
      {"Show Character", "[show character_id pos=center expr=neutral layer=10]"},
      {"Move Character", "[move character_id pos=right expr=smile ease=easeInOut dur=500]"},
      {"Hide Character", "[hide character_id]"},
      {"Play BGM", "[bgm music_file]"},
      {"Stop BGM", "[bgm_stop]"},
      {"Play SFX", "[sfx sound_file]"},
      {"Play Voice", "[voice voice_file]"},
      {"Particles", "[particles preset=rain intensity=0.5 layer=100 opacity=0.8 wind=20]"},
      {"Weather", "[weather preset=snow intensity=0.4 layer=120 duration=3000]"},
      {"Audio Visualizer", "[visualizer on bars=48]"},
      {"Wait", "[wait 1.0]"},
      {"Transition", "[transition type=fade dur=500 bg=background_name]"},
      {"Stage Preset", "@stagepreset preset_id config/stage/preset.stagepreset"},
      {"Apply Stage", "[stage preset_id dur=500]"},
      {"Custom Position", "@position balcony 0.30 0.60"},
      {"Dialogue Mode", "[mode dialogue standard]"},
      {"If Block", "[if condition]\n  # true branch\n[endif]"},
      {"If-Else Block", "[if condition]\n  # true branch\n[else]\n  # false branch\n[endif]"},
      {"Set Variable", "[set variable_name value]"},
      {"Increment Variable", "[inc variable_name 1]"},
      {"Decrement Variable", "[dec variable_name 1]"},
      {"Multiply Variable", "[mul variable_name 1]"},
      {"Toggle Variable", "[toggle flag_name]"},
      {"Clear Variable", "[clear variable_name]"},
      {"Persistent Variable", "[persistent set key value]"},
      {"Flag", "[flag flag_name]"},
      {"Java Bind", "@bind String:playerName"},
      {"Java Import", "@jimport com.example.GameHooks"},
      {"Puppeteer Timeline Cue", "@external jes_timeline timeline_id"},
      {"Phone Open", "[phone open]"},
      {"Character Decl", "@character id \"Display Name\" scale=1.0"},
      {"Background Decl", "@background alias path/to/image.png"},
      {"Include", "@include path/to/script.vns"},
      {"End", "[end]"},
  };

  private void showSnippetPalette() {
    Popup popup = new Popup();
    popup.setAutoHide(true);

    TextField filterField = new TextField();
    filterField.setPromptText("Insert snippet...");
    filterField.setPrefWidth(350);

    ListView<String> listView = new ListView<>();
    listView.setPrefWidth(350);
    listView.setPrefHeight(Math.min(VNS_SNIPPETS.length * 26 + 4, 300));
    for (String[] snip : VNS_SNIPPETS) listView.getItems().add(snip[0]);

    filterField.textProperty().addListener((obs, ov, nv) -> {
      String filter = nv == null ? "" : nv.toLowerCase(Locale.ROOT);
      listView.getItems().clear();
      for (String[] snip : VNS_SNIPPETS) {
        if (snip[0].toLowerCase(Locale.ROOT).contains(filter)) listView.getItems().add(snip[0]);
      }
      if (!listView.getItems().isEmpty()) listView.getSelectionModel().selectFirst();
    });

    Runnable commit = () -> {
      String sel = listView.getSelectionModel().getSelectedItem();
      popup.hide();
      if (sel == null) return;
      for (String[] snip : VNS_SNIPPETS) {
        if (snip[0].equals(sel)) {
          int pos = codeArea.getCaretPosition();
          codeArea.insertText(pos, snip[1]);
          codeArea.requestFocus();
          break;
        }
      }
    };

    filterField.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER) { commit.run(); e.consume(); }
      else if (e.getCode() == KeyCode.ESCAPE) { popup.hide(); e.consume(); }
      else if (e.getCode() == KeyCode.DOWN) { listView.getSelectionModel().selectNext(); e.consume(); }
      else if (e.getCode() == KeyCode.UP) { listView.getSelectionModel().selectPrevious(); e.consume(); }
    });
    listView.setOnMouseClicked(e -> { if (e.getClickCount() == 2) commit.run(); });

    VBox box = new VBox(2, filterField, listView);
    box.setPadding(new Insets(6));
    box.setStyle("-fx-background-color: #1e1e1e; -fx-border-color: #3a3a3a; -fx-border-width: 1; -fx-background-radius: 4; -fx-border-radius: 4;");
    popup.getContent().add(box);

    codeArea.getCaretBounds().ifPresent(b -> {
      double x = b.getMinX() + codeArea.getScene().getWindow().getX();
      double y = b.getMaxY() + codeArea.getScene().getWindow().getY();
      popup.show(codeArea.getScene().getWindow(), x, y);
    });
    if (!popup.isShowing()) {
      popup.show(codeArea.getScene().getWindow(),
          codeArea.getScene().getWindow().getX() + 100,
          codeArea.getScene().getWindow().getY() + 100);
    }
    filterField.requestFocus();
    if (!listView.getItems().isEmpty()) listView.getSelectionModel().selectFirst();
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Zoom (Ctrl+= / Ctrl+-)
  // ═══════════════════════════════════════════════════════════════════
  private void zoomIn() {
    fontSizePx = Math.min(fontSizePx + 1, 30);
    applyZoom();
  }

  private void zoomOut() {
    fontSizePx = Math.max(fontSizePx - 1, 8);
    applyZoom();
  }

  private void applyZoom() {
    codeArea.setStyle("-fx-font-size: " + (int) fontSizePx + "px;");
    statusBarLabel.setText(statusBarLabel.getText().replaceFirst("\\s*\\|\\s*Zoom:.*", "")
        + "  |  Zoom: " + (int) fontSizePx + "px");
  }

  public void setFontSizePx(double fontSizePx) {
    this.fontSizePx = Math.max(8.0, Math.min(30.0, fontSizePx));
    applyZoom();
    if (splitCodeArea != null) {
      splitCodeArea.setStyle("-fx-font-size: " + (int) this.fontSizePx + "px;");
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Command Palette (Ctrl+Shift+P)
  // ═══════════════════════════════════════════════════════════════════
  private static final String[][] COMMANDS = {
      {"Find", "Open find bar (Ctrl+F)"},
      {"Find and Replace", "Open find & replace (Ctrl+H)"},
      {"Go to Symbol", "Jump to @label (Ctrl+Shift+O)"},
      {"Go to Line", "Jump to line number (Ctrl+G)"},
      {"Toggle Timeline Fold", "Collapse/expand timeline block under caret (Ctrl+Shift+[)"},
      {"Edit Timeline in Puppeteer", "Reopen the timeline containing the cursor for visual editing"},
      {"Unfold All Timeline Blocks", "Expand folded timeline blocks (Ctrl+Shift+])"},
      {"Toggle Comment", "Comment/uncomment lines (Ctrl+/)"},
      {"Duplicate Line", "Duplicate current line (Ctrl+D)"},
      {"Delete Line", "Remove current line (Ctrl+Shift+K)"},
      {"Move Line Up", "Move line up (Alt+Up)"},
      {"Move Line Down", "Move line down (Alt+Down)"},
      {"Insert Snippet", "Open snippet palette (Ctrl+J)"},
      {"Toggle Bookmark", "Set/clear bookmark (Ctrl+F2)"},
      {"Next Bookmark", "Jump to next bookmark (F2)"},
      {"Next Dialogue Line", "Jump to next speaker line, skipping timelines (Ctrl+Shift+Down)"},
      {"Previous Dialogue Line", "Jump to previous speaker line, skipping timelines (Ctrl+Shift+Up)"},
      {"Zoom In", "Increase font size (Ctrl+=)"},
      {"Zoom Out", "Decrease font size (Ctrl+-)"},
      {"Toggle Word Wrap", "Toggle line wrapping (Ctrl+Shift+W)"},
      {"Toggle Split Editor", "Split/unsplit view (Ctrl+\\)"},
      {"Show Diff", "Compare with saved version (Ctrl+Shift+D)"},
  };

  /** Opens the searchable VNS editor command palette. */
  public void showCommandPalette() {
    Popup popup = new Popup();
    popup.setAutoHide(true);

    TextField filterField = new TextField();
    filterField.setPromptText("Type a command...");
    filterField.setPrefWidth(400);

    ListView<String> listView = new ListView<>();
    listView.setPrefWidth(400);
    listView.setPrefHeight(Math.min(COMMANDS.length * 28 + 4, 350));
    for (String[] cmd : COMMANDS) listView.getItems().add(cmd[0] + "  —  " + cmd[1]);

    filterField.textProperty().addListener((obs, ov, nv) -> {
      String filter = nv == null ? "" : nv.toLowerCase(Locale.ROOT);
      listView.getItems().clear();
      for (String[] cmd : COMMANDS) {
        if (cmd[0].toLowerCase(Locale.ROOT).contains(filter) || cmd[1].toLowerCase(Locale.ROOT).contains(filter)) {
          listView.getItems().add(cmd[0] + "  —  " + cmd[1]);
        }
      }
      if (!listView.getItems().isEmpty()) listView.getSelectionModel().selectFirst();
    });

    Runnable commit = () -> {
      String sel = listView.getSelectionModel().getSelectedItem();
      popup.hide();
      if (sel == null) return;
      String cmdName = sel.contains("  —  ") ? sel.substring(0, sel.indexOf("  —  ")) : sel;
      executeCommand(cmdName);
    };

    filterField.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER) { commit.run(); e.consume(); }
      else if (e.getCode() == KeyCode.ESCAPE) { popup.hide(); e.consume(); }
      else if (e.getCode() == KeyCode.DOWN) { listView.getSelectionModel().selectNext(); e.consume(); }
      else if (e.getCode() == KeyCode.UP) { listView.getSelectionModel().selectPrevious(); e.consume(); }
    });
    listView.setOnMouseClicked(e -> { if (e.getClickCount() == 2) commit.run(); });

    VBox box = new VBox(2, filterField, listView);
    box.setPadding(new Insets(6));
    box.setStyle("-fx-background-color: #1e1e1e; -fx-border-color: #3a3a3a; -fx-border-width: 1; -fx-background-radius: 4; -fx-border-radius: 4;");
    popup.getContent().add(box);

    // Position at top-center of editor
    Platform.runLater(() -> {
      try {
        double wx = codeArea.getScene().getWindow().getX();
        double wy = codeArea.getScene().getWindow().getY();
        double ww = codeArea.getScene().getWindow().getWidth();
        popup.show(codeArea.getScene().getWindow(), wx + (ww - 400) / 2, wy + 60);
      } catch (Exception ex) {
        popup.show(codeArea.getScene().getWindow(), 100, 100);
      }
      filterField.requestFocus();
    });
  }

  private void executeCommand(String name) {
    switch (name) {
      case "Find" -> showSearchBar();
      case "Find and Replace" -> { showSearchBar(); searchBar.showReplace(true); }
      case "Go to Symbol" -> showGoToSymbol();
      case "Go to Line" -> showGoToLineDialog();
      case "Toggle Timeline Fold" -> toggleTimelineBlockAtCaret();
      case "Edit Timeline in Puppeteer" -> editTimelineAtCaret();
      case "Unfold All Timeline Blocks" -> unfoldAllTimelineBlocks();
      case "Toggle Comment" -> toggleLineComment();
      case "Duplicate Line" -> duplicateLine();
      case "Delete Line" -> deleteLine();
      case "Move Line Up" -> moveLineUp();
      case "Move Line Down" -> moveLineDown();
      case "Insert Snippet" -> showSnippetPalette();
      case "Toggle Bookmark" -> toggleBookmark();
      case "Next Bookmark" -> jumpToNextBookmark();
      case "Next Dialogue Line" -> jumpToNextDialogueLine();
      case "Previous Dialogue Line" -> jumpToPreviousDialogueLine();
      case "Zoom In" -> zoomIn();
      case "Zoom Out" -> zoomOut();
      case "Toggle Word Wrap" -> toggleWordWrap();
      case "Toggle Split Editor" -> toggleSplitEditor();
      case "Show Diff" -> showDiffView();
      default -> {}
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Bookmarks (Ctrl+F2 toggle, F2 next)
  // ═══════════════════════════════════════════════════════════════════
  private void toggleBookmark() {
    int para = codeArea.getCurrentParagraph();
    if (bookmarks.contains(para)) {
      bookmarks.remove(para);
    } else {
      bookmarks.add(para);
    }
    Platform.runLater(() -> codeArea.setParagraphGraphicFactory(this::makeLineNumberLabel));
    scheduleMinimapRedraw();
  }

  private void jumpToNextBookmark() {
    if (bookmarks.isEmpty()) return;
    int current = codeArea.getCurrentParagraph();
    Integer next = bookmarks.higher(current);
    if (next == null) next = bookmarks.first();
    if (next != null) {
      codeArea.moveTo(next, 0);
      codeArea.requestFollowCaret();
      codeArea.requestFocus();
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Dialogue Line Navigation (Ctrl+Shift+Down/Up)
  // ═══════════════════════════════════════════════════════════════════
  private boolean isDialogueLine(int paragraphIndex) {
    if (paragraphIndex < 0 || paragraphIndex >= codeArea.getParagraphs().size()) return false;
    String line = codeArea.getParagraph(paragraphIndex).getText();
    return DIALOGUE_LINE_PATTERN.matcher(line).find();
  }

  public void jumpToNextDialogueLine() {
    int count = codeArea.getParagraphs().size();
    if (count <= 0) return;
    int current = codeArea.getCurrentParagraph();
    for (int i = 1; i <= count; i++) {
      int candidate = (current + i) % count;
      if (isDialogueLine(candidate)) {
        codeArea.moveTo(candidate, 0);
        codeArea.requestFollowCaret();
        codeArea.requestFocus();
        return;
      }
    }
  }

  public void jumpToPreviousDialogueLine() {
    int count = codeArea.getParagraphs().size();
    if (count <= 0) return;
    int current = codeArea.getCurrentParagraph();
    for (int i = 1; i <= count; i++) {
      int candidate = ((current - i) % count + count) % count;
      if (isDialogueLine(candidate)) {
        codeArea.moveTo(candidate, 0);
        codeArea.requestFollowCaret();
        codeArea.requestFocus();
        return;
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Split Editor (Ctrl+\)
  // ═══════════════════════════════════════════════════════════════════
  private void toggleSplitEditor() {
    if (splitActive) {
      // Remove split
      splitActive = false;
      javafx.scene.Node center = getCenter();
      if (center instanceof SplitPane sp) {
        // Get the first item (code+minimap HBox) back
        if (!sp.getItems().isEmpty()) {
          javafx.scene.Node primary = sp.getItems().get(0);
          setCenter(primary);
        }
      }
      splitCodeArea = null;
      splitPane = null;
    } else {
      // Create split
      splitActive = true;
      splitCodeArea = new CodeArea();
      splitCodeArea.setEditable(false);
      splitCodeArea.replaceText(codeArea.getText());
      String splitCss = EditorTheme.stylesheetUrl();
      if (!splitCss.isEmpty()) splitCodeArea.getStylesheets().add(splitCss);
      splitCodeArea.setStyle("-fx-font-size: " + (int) fontSizePx + "px;");
      // Sync text from primary to split
      codeArea.textProperty().addListener((obs, ov, nv) -> {
        if (splitCodeArea != null && nv != null) {
          splitCodeArea.replaceText(nv);
        }
      });
      VirtualizedScrollPane<CodeArea> splitScroll = new VirtualizedScrollPane<>(splitCodeArea);

      javafx.scene.Node current = getCenter();
      splitPane = new SplitPane(current, splitScroll);
      splitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
      splitPane.setDividerPositions(0.5);
      setCenter(splitPane);
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Word Wrap Toggle (Ctrl+Shift+W)
  // ═══════════════════════════════════════════════════════════════════
  /** Toggles line wrapping and returns the new state for toolbar controls. */
  public boolean toggleWordWrap() {
    wordWrapEnabled = !wordWrapEnabled;
    codeArea.setWrapText(wordWrapEnabled);
    return wordWrapEnabled;
  }

  public boolean isWordWrapEnabled() {
    return wordWrapEnabled;
  }

  /** Applies the global default without treating it as a toolbar toggle. */
  public void setWordWrapEnabled(boolean enabled) {
    wordWrapEnabled = enabled;
    codeArea.setWrapText(enabled);
  }

  /** Applies the global default without treating it as a toolbar toggle. */
  public void setLargeTimelineBlockActionThreshold(int threshold) {
    int clamped = Math.max(0, threshold);
    if (clamped == largeTimelineBlockActionThreshold) return;
    largeTimelineBlockActionThreshold = clamped;
    applyAnalysis(codeArea.getText());
  }

  public void setMinimapVisible(boolean visible) {
    if (minimap != null) {
      minimap.setVisible(visible);
      minimap.setManaged(visible);
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Diff View (Ctrl+Shift+D) — compare with saved snapshot
  // ═══════════════════════════════════════════════════════════════════
  /** Compares the current script with the last saved or loaded snapshot. */
  public void showDiffView() {
    String current = codeArea.getText();
    if (current == null) current = "";
    String saved = savedTextSnapshot;
    if (saved == null) saved = "";

    String[] currentLines = current.split("\\n", -1);
    String[] savedLines = saved.split("\\n", -1);

    StringBuilder diff = new StringBuilder();
    int maxLines = Math.max(currentLines.length, savedLines.length);
    for (int i = 0; i < maxLines; i++) {
      String cl = i < currentLines.length ? currentLines[i] : "";
      String sl = i < savedLines.length ? savedLines[i] : "";
      if (cl.equals(sl)) {
        diff.append("  ").append(cl).append("\n");
      } else {
        if (i < savedLines.length && !sl.isEmpty()) diff.append("- ").append(sl).append("\n");
        if (i < currentLines.length && !cl.isEmpty()) diff.append("+ ").append(cl).append("\n");
      }
    }

    Stage diffStage = new Stage();
    diffStage.setTitle("Diff — Current vs Saved");

    TextArea diffArea = new TextArea(diff.toString());
    diffArea.setEditable(false);
    diffArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px; -fx-control-inner-background: #121212; -fx-text-fill: #e6e6e6;");

    BorderPane root = new BorderPane(diffArea);
    root.setStyle("-fx-background-color: #121212;");
    Label info = new Label("Lines prefixed with '-' are from saved version, '+' are current changes.");
    info.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px; -fx-padding: 4 10 4 10;");
    root.setBottom(info);

    Scene scene = new Scene(root, 800, 600);
    EditorTheme.apply(scene);
    diffStage.setScene(scene);
    diffStage.show();
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Minimap — JVN script overview with semantic markers
  // ═══════════════════════════════════════════════════════════════════

  private void scheduleMinimapRedraw() {
    if (minimapRedrawPending) return;
    minimapRedrawPending = true;
    Platform.runLater(() -> {
      minimapRedrawPending = false;
      refreshMinimap();
    });
  }

  private void refreshMinimap() {
    if (minimap == null) return;
    minimap.setSnapshot(
        codeArea.getText(),
        minimapDiagnostics(),
        bookmarks,
        minimapTimelineBlocks());
    minimap.redraw();
  }

  private List<VnsCodeMinimap.DiagnosticMarker> minimapDiagnostics() {
    if (issues == null || issues.isEmpty()) return List.of();
    List<VnsCodeMinimap.DiagnosticMarker> out = new ArrayList<>();
    for (Issue issue : issues) {
      VnsCodeMinimap.DiagnosticSeverity severity = switch (issue.severity) {
        case WARNING -> VnsCodeMinimap.DiagnosticSeverity.WARNING;
        case INFO -> VnsCodeMinimap.DiagnosticSeverity.INFO;
        case ERROR -> VnsCodeMinimap.DiagnosticSeverity.ERROR;
      };
      out.add(new VnsCodeMinimap.DiagnosticMarker(issue.line, severity, issue.message));
    }
    return out;
  }

  private List<VnsCodeMinimap.TimelineBlock> minimapTimelineBlocks() {
    List<VnsCodeMinimap.TimelineBlock> out = new ArrayList<>();
    for (FoldRegion region : computeFoldRegions()) {
      if (region.kind() != FoldKind.TIMELINE) continue;
      out.add(new VnsCodeMinimap.TimelineBlock(
          region.startLine(),
          region.endLine(),
          foldedRegionStarts.contains(region.startLine())));
    }
    return out;
  }
}
