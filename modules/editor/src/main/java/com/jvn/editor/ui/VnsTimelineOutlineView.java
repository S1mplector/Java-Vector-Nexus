package com.jvn.editor.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.jvn.core.assets.AsyncAssetLoader;

/**
 * Side panel listing {@code timeline { ... } } blocks found in the active VNS
 * script, in document order, with line range and approximate duration.
 * Block boundaries are supplied by {@link VnsCodeEditor#computeTimelineOutlineEntries()}
 * (reusing its existing brace-scanner) rather than re-scanning the script text.
 */
public class VnsTimelineOutlineView extends BorderPane {
  private final Label titleLabel = new Label("Timeline Outline");
  private final Label fileLabel = new Label("No active file");
  private final Label summaryLabel = new Label("Open a .vns script to see its timeline blocks.");
  private final Label placeholderLabel = new Label("Open a .vns script to see its timeline blocks.");
  private final ListView<OutlineRow> listView = new ListView<>();

  private final List<OutlineRow> rows = new ArrayList<>();
  private final PauseTransition refreshDebounce = new PauseTransition(Duration.millis(300));
  private final AtomicLong generation = new AtomicLong();
  private boolean hasActiveFile;
  private Consumer<Integer> onOpenLine;
  private String lastText = "";

  public VnsTimelineOutlineView() {
    getStyleClass().addAll("vns-timeline-outline-root", "sidebar-tool-root");
    titleLabel.getStyleClass().addAll("vns-timeline-outline-title", "sidebar-tool-title");
    titleLabel.setGraphic(AeroIcon.of(AeroIcon.Kind.TIMELINE_OUTLINE, 20));
    titleLabel.setGraphicTextGap(7);
    fileLabel.getStyleClass().addAll("vns-timeline-outline-file", "sidebar-tool-subtitle");
    summaryLabel.getStyleClass().addAll("vns-timeline-outline-summary", "sidebar-tool-summary");
    summaryLabel.setWrapText(true);

    placeholderLabel.getStyleClass().add("vns-timeline-outline-placeholder");
    placeholderLabel.setWrapText(true);
    listView.setPlaceholder(placeholderLabel);
    listView.getStyleClass().add("vns-timeline-outline-list");
    listView.setCellFactory(lv -> new OutlineCell());
    listView.setOnMouseClicked(e -> {
      if (e.getClickCount() < 2) return;
      openSelectedRow();
    });
    listView.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER) {
        openSelectedRow();
        e.consume();
      }
    });
    installRowContextMenu();

    HBox titleRow = new HBox(8, titleLabel, SidebarToolHelp.button(this, "Timeline Outline", """
        Lists every timeline { ... } block found in the active .vns script, in \
document order, with its line range and approximate duration.

Double-click or press Enter on an entry to scroll the editor to that \
timeline block's start line. The list refreshes automatically a short \
while after you stop typing, so duration parsing never blocks editing."""));
    titleRow.setAlignment(Pos.CENTER_LEFT);
    titleRow.getStyleClass().add("vns-timeline-outline-title-row");

    VBox header = new VBox(6, titleRow, fileLabel, summaryLabel);
    header.getStyleClass().add("sidebar-tool-header");

    setTop(header);
    setCenter(listView);
    setPadding(new Insets(0));
  }

  public void setOnOpenLine(Consumer<Integer> onOpenLine) {
    this.onOpenLine = onOpenLine;
  }

  public void clear() {
    hasActiveFile = false;
    generation.incrementAndGet();
    fileLabel.setText("No active file");
    fileLabel.setTooltip(null);
    summaryLabel.setText("Open a .vns script to see its timeline blocks.");
    rows.clear();
    listView.getItems().clear();
    updatePlaceholder();
  }

  /**
   * Schedules a debounced refresh of the outline from the given editor's
   * current text. Safe to call on every keystroke — the actual block
   * parsing (which calls into {@code TimelineDataParser}) runs off the FX
   * thread after a short idle period, and a generation counter discards any
   * result that is no longer the latest request.
   */
  public void scheduleRefresh(File scriptFile, VnsCodeEditor editor) {
    if (scriptFile == null || editor == null) {
      clear();
      return;
    }
    hasActiveFile = true;
    fileLabel.setText(scriptFile.getName());
    fileLabel.setTooltip(new javafx.scene.control.Tooltip(scriptFile.getAbsolutePath()));

    long gen = generation.incrementAndGet();
    refreshDebounce.setOnFinished(e -> runRefresh(gen, editor));
    refreshDebounce.playFromStart();
  }

  private void runRefresh(long gen, VnsCodeEditor editor) {
    List<VnsCodeEditor.TimelineOutlineEntry> entries = editor.computeTimelineOutlineEntries();
    String text = editor.getText();
    lastText = text;
    AsyncAssetLoader.getExecutor().execute(() -> {
      List<OutlineRow> computed = new ArrayList<>();
      for (VnsCodeEditor.TimelineOutlineEntry entry : entries) {
        if (generation.get() != gen) return;
        String block = safeSubstring(text, entry.startOffset(), entry.endOffset());
        String duration = VnsCodeEditor.formatTimelineOutlineDuration(block);
        computed.add(new OutlineRow(entry, duration));
      }
      if (generation.get() != gen) return;
      Platform.runLater(() -> {
        if (generation.get() != gen) return;
        applyRows(computed);
      });
    });
  }

  private static String safeSubstring(String text, int start, int end) {
    if (text == null) return "";
    int len = text.length();
    int s = Math.max(0, Math.min(start, len));
    int en = Math.max(s, Math.min(end, len));
    return text.substring(s, en);
  }

  private void applyRows(List<OutlineRow> computed) {
    rows.clear();
    rows.addAll(computed);
    listView.setItems(FXCollections.observableArrayList(rows));
    summaryLabel.setText(rows.isEmpty()
        ? "No timeline blocks found."
        : rows.size() + (rows.size() == 1 ? " timeline block" : " timeline blocks"));
    updatePlaceholder();
  }

  private void updatePlaceholder() {
    if (!hasActiveFile) {
      placeholderLabel.setText("Open a .vns script to see its timeline blocks.");
      return;
    }
    placeholderLabel.setText("No timeline blocks found in this script.");
  }

  private void openSelectedRow() {
    OutlineRow row = listView.getSelectionModel().getSelectedItem();
    if (row == null || onOpenLine == null) return;
    onOpenLine.accept(row.entry().oneBasedStartLine());
  }

  /**
   * Right-click-only "Copy Timeline Block" action, so the block can be
   * copied without manually selecting hundreds of lines in the editor.
   * Selects the row under the cursor first so right-clicking an unselected
   * row still copies the right block.
   */
  private void installRowContextMenu() {
    MenuItem copyItem = new MenuItem("Copy Timeline Block");
    copyItem.setOnAction(e -> copySelectedBlock());
    ContextMenu menu = new ContextMenu(copyItem);
    listView.setContextMenu(menu);
    listView.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
      if (!e.isSecondaryButtonDown()) return;
      OutlineCell cell = findCellFromTarget(e.getTarget());
      if (cell != null && cell.getItem() != null) {
        listView.getSelectionModel().select(cell.getItem());
      }
    });
    copyItem.disableProperty().bind(
        listView.getSelectionModel().selectedItemProperty().isNull());
  }

  private static OutlineCell findCellFromTarget(javafx.event.EventTarget target) {
    javafx.scene.Node node = target instanceof javafx.scene.Node n ? n : null;
    while (node != null) {
      if (node instanceof OutlineCell cell) return cell;
      node = node.getParent();
    }
    return null;
  }

  /** Test-only hook for triggering the context menu's copy action without driving real mouse input. */
  void copySelectedBlockForTest() {
    copySelectedBlock();
  }

  /** Test-only synchronous refresh, bypassing the debounce and background executor for determinism. */
  void refreshSynchronouslyForTest(VnsCodeEditor editor) {
    hasActiveFile = true;
    List<VnsCodeEditor.TimelineOutlineEntry> entries = editor.computeTimelineOutlineEntries();
    String text = editor.getText();
    lastText = text;
    List<OutlineRow> computed = new ArrayList<>();
    for (VnsCodeEditor.TimelineOutlineEntry entry : entries) {
      String block = safeSubstring(text, entry.startOffset(), entry.endOffset());
      computed.add(new OutlineRow(entry, VnsCodeEditor.formatTimelineOutlineDuration(block)));
    }
    applyRows(computed);
  }

  /** Test-only hook to select a row by index before triggering the copy action. */
  void selectRowForTest(int index) {
    listView.getSelectionModel().select(index);
  }

  private void copySelectedBlock() {
    int selectedIndex = listView.getSelectionModel().getSelectedIndex();
    OutlineRow row = listView.getSelectionModel().getSelectedItem();
    if (row == null) return;
    VnsCodeEditor.TimelineOutlineEntry entry = row.entry();
    String block = safeSubstring(lastText, entry.startOffset(), entry.endOffset());
    ClipboardContent content = new ClipboardContent();
    content.putString(block);
    Clipboard.getSystemClipboard().setContent(content);
    showCopyFeedback(selectedIndex, entry.oneBasedStartLine(), entry.oneBasedEndLine());
  }

  private void showCopyFeedback(int rowIndex, int startLine, int endLine) {
    Window window = getScene() == null ? null : getScene().getWindow();
    if (window == null) return;
    OutlineCell cell = findCellAtIndex(rowIndex);
    javafx.geometry.Point2D anchor = cell != null
        ? cell.localToScreen(cell.getWidth() * 0.5, cell.getHeight())
        : listView.localToScreen(listView.getWidth() * 0.5, 24);
    if (anchor == null) return;
    Tooltip tip = new Tooltip("Copied L" + startLine + "–" + endLine + " to clipboard");
    tip.getStyleClass().add("vns-timeline-outline-copy-tooltip");
    tip.show(window, anchor.getX(), anchor.getY());
    PauseTransition hide = new PauseTransition(Duration.millis(1500));
    hide.setOnFinished(e -> tip.hide());
    hide.play();
  }

  private OutlineCell findCellAtIndex(int index) {
    for (javafx.scene.Node node : listView.lookupAll(".vns-timeline-outline-list-cell")) {
      if (node instanceof OutlineCell cell && cell.getIndex() == index) return cell;
    }
    return null;
  }

  private record OutlineRow(VnsCodeEditor.TimelineOutlineEntry entry, String duration) {}

  private static final class OutlineCell extends ListCell<OutlineRow> {
    private final Label locationLabel = new Label();
    private final Label durationLabel = new Label();
    private final HBox content = new HBox(8, locationLabel, durationLabel);

    private OutlineCell() {
      locationLabel.getStyleClass().add("vns-timeline-outline-location");
      durationLabel.getStyleClass().add("vns-timeline-outline-duration");
      content.setAlignment(Pos.CENTER_LEFT);
      content.getStyleClass().add("vns-timeline-outline-row");
      HBox.setHgrow(locationLabel, Priority.ALWAYS);
    }

    @Override
    protected void updateItem(OutlineRow item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setText(null);
        setGraphic(null);
        getStyleClass().remove("vns-timeline-outline-list-cell");
        return;
      }
      if (!getStyleClass().contains("vns-timeline-outline-list-cell")) {
        getStyleClass().add("vns-timeline-outline-list-cell");
      }
      locationLabel.setText("L" + item.entry().oneBasedStartLine() + "–" + item.entry().oneBasedEndLine());
      durationLabel.setText("~" + (item.duration() == null ? "duration unavailable" : item.duration()));
      setText(null);
      setGraphic(content);
    }
  }
}
