package com.jvn.editor.ui;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.CodeArea;

import com.jvn.core.animation.TimelineData;
import com.jvn.core.animation.TimelineDataParser;

import javafx.collections.ListChangeListener;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 * Whole-script overview with navigation, semantic markers, and the current viewport.
 */
public final class VnsCodeMinimap extends StackPane {
  public enum DiagnosticSeverity { ERROR, WARNING, INFO }

  public record DiagnosticMarker(int line, DiagnosticSeverity severity, String message) {}
  public record TimelineBlock(int startLine, int endLine, boolean folded) {}

  private record TimelineSummary(String durationLabel, int trackCount, int actionCount) {}

  private enum LineKind {
    EMPTY,
    COMMENT,
    LABEL,
    DIRECTIVE,
    COMMAND,
    SPEAKER,
    CHOICE,
    TIMELINE,
    TEXT
  }

  private record LineSummary(LineKind kind, String label, String speaker, int length) {}

  private static final Pattern LABEL_PATTERN =
      Pattern.compile("^\\s*@label\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern SPEAKER_PATTERN =
      Pattern.compile("^\\s*([^\\s#:][^:]{0,30}):");
  private static final Pattern TIMELINE_PATTERN =
      Pattern.compile("^\\s*timeline\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern TIMELINE_ACTION_PATTERN = Pattern.compile(
      "^\\s*(move|depth|pivot|rotate|scale|mirror|fade|visible|brightness|exposure|expression|show|hide|replace|scene|cameraMove|cameraZoom|property|event|playAudio)\\b",
      Pattern.CASE_INSENSITIVE);

  private static final double MAP_WIDTH = 108;
  private static final double MAP_INSET = 5;
  private static final double MARKER_RAIL = 15;
  private static final int SPEAKER_LOOKBACK_LINES = 15;

  private final CodeArea codeArea;
  private final Canvas canvas = new Canvas();
  private final Region viewport = new Region();
  private final ListChangeListener<String> themeChange = change -> invalidateArtwork();
  private final Tooltip tooltip = new Tooltip("JVN script map");
  private String cachedText = "";
  private LineSummary[] cachedLines = new LineSummary[0];
  private List<DiagnosticMarker> diagnostics = List.of();
  private Set<Integer> bookmarks = Set.of();
  private List<TimelineBlock> timelines = List.of();
  private Map<TimelineBlock, TimelineSummary> timelineSummaries = Map.of();
  private double lineHeight = 1;
  private boolean artworkDirty = true;

  private record VisibleRange(int startLine, int endLine) {}

  public VnsCodeMinimap(CodeArea codeArea) {
    this.codeArea = codeArea;
    getStyleClass().add("code-editor-minimap");
    setAccessibleText("VNS script minimap");
    setMinWidth(MAP_WIDTH);
    setPrefWidth(MAP_WIDTH);
    setMaxWidth(MAP_WIDTH);
    setMinHeight(0);

    Pane track = new Pane(viewport, canvas);
    track.getStyleClass().add("code-minimap-track");
    track.setMinSize(0, 0);
    viewport.getStyleClass().add("code-minimap-viewport");
    viewport.setManaged(false);
    viewport.setMouseTransparent(true);
    canvas.setManaged(false);
    canvas.setCursor(Cursor.HAND);
    getChildren().add(track);

    canvas.widthProperty().bind(track.widthProperty());
    canvas.heightProperty().bind(track.heightProperty());
    canvas.widthProperty().addListener((obs, oldValue, newValue) -> invalidateArtwork());
    canvas.heightProperty().addListener((obs, oldValue, newValue) -> invalidateArtwork());
    sceneProperty().addListener((obs, previous, current) -> {
      if (previous != null) previous.getStylesheets().removeListener(themeChange);
      if (current != null) current.getStylesheets().addListener(themeChange);
      invalidateArtwork();
    });
    canvas.setOnMousePressed(this::navigate);
    canvas.setOnMouseDragged(this::navigate);
    canvas.setOnMouseMoved(this::updateTooltip);
    canvas.setOnScroll(event -> {
      codeArea.scrollYBy(-event.getDeltaY());
      event.consume();
    });
    Tooltip.install(canvas, tooltip);
  }

  public void setSnapshot(String text,
                          Collection<DiagnosticMarker> diagnostics,
                          Collection<Integer> bookmarks,
                          Collection<TimelineBlock> timelines) {
    String safeText = text == null ? "" : text;
    boolean textChanged = !safeText.equals(cachedText);
    if (textChanged) {
      cachedText = safeText;
      cachedLines = analyzeLines(safeText);
    }
    List<DiagnosticMarker> newDiagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    Set<Integer> newBookmarks = bookmarks == null ? Set.of() : new HashSet<>(bookmarks);
    List<TimelineBlock> newTimelines = timelines == null ? List.of() : List.copyOf(timelines);
    boolean timelinesChanged = !newTimelines.equals(this.timelines);
    artworkDirty |= textChanged || timelinesChanged || !newDiagnostics.equals(this.diagnostics)
        || !newBookmarks.equals(this.bookmarks);
    this.diagnostics = newDiagnostics;
    this.bookmarks = newBookmarks;
    if (textChanged || timelinesChanged) {
      this.timelines = newTimelines;
      this.timelineSummaries = computeTimelineSummaries(newTimelines, cachedText);
    }
  }

  private static Map<TimelineBlock, TimelineSummary> computeTimelineSummaries(
      List<TimelineBlock> blocks, String text) {
    if (blocks.isEmpty()) return Map.of();
    Map<TimelineBlock, TimelineSummary> out = new HashMap<>();
    String[] lines = text.split("\\n", -1);
    for (TimelineBlock block : blocks) {
      out.put(block, summarizeTimelineBlock(block, lines));
    }
    return out;
  }

  private static TimelineSummary summarizeTimelineBlock(TimelineBlock block, String[] lines) {
    int start = Math.max(0, Math.min(block.startLine(), lines.length - 1));
    int end = Math.max(start, Math.min(block.endLine(), lines.length - 1));
    StringBuilder blockText = new StringBuilder();
    int actionCount = 0;
    for (int i = start; i <= end; i++) {
      String line = lines[i];
      blockText.append(line).append('\n');
      if (TIMELINE_ACTION_PATTERN.matcher(line.strip()).find()) {
        actionCount++;
      }
    }
    try {
      TimelineData data = TimelineDataParser.parse("_minimap_hover", blockText.toString());
      return new TimelineSummary(formatDuration(data.getDurationMs()), data.getTracks().size(), actionCount);
    } catch (Exception ex) {
      return new TimelineSummary("duration n/a", 0, actionCount);
    }
  }

  private static String formatDuration(double durationMs) {
    if (durationMs < 1000.0) {
      return String.format(Locale.ROOT, "%.0f ms", durationMs);
    }
    return String.format(Locale.ROOT, "%.2f s", durationMs / 1000.0);
  }

  private void invalidateArtwork() {
    artworkDirty = true;
    redraw();
  }

  public void redraw() {
    double width = canvas.getWidth();
    double height = canvas.getHeight();
    double usableHeight = height - MAP_INSET * 2;
    int totalLines = cachedLines.length;
    boolean visible = totalLines > 0 && usableHeight > 0 && width > MARKER_RAIL + MAP_INSET * 2;
    viewport.setVisible(visible);
    GraphicsContext gc = canvas.getGraphicsContext2D();
    if (!visible) {
      gc.clearRect(0, 0, width, height);
      return;
    }

    lineHeight = Math.min(4, usableHeight / totalLines);
    if (artworkDirty) {
      artworkDirty = false;
      gc.clearRect(0, 0, width, height);
      boolean light = EditorTheme.theme() == EditorTheme.Theme.LIGHT;
      double railX = width - MARKER_RAIL;
      gc.setFill(Color.web(light ? "#b4c5d2" : "#626970", 0.42));
      gc.fillRect(railX, MAP_INSET, 1, usableHeight);
      drawTimelineBands(gc, railX, totalLines, light);
      drawScriptTexture(gc, railX, totalLines, light);
      drawMarkers(gc, railX, totalLines, light);
    }

    VisibleRange range = visibleRange(totalLines);
    double viewportHeight = Math.max(8, (range.endLine() - range.startLine() + 1) * lineHeight);
    viewportHeight = Math.min(viewportHeight, usableHeight);
    double y = Math.min(yForLine(range.startLine()), height - MAP_INSET - viewportHeight);
    viewport.resizeRelocate(3, y, width - 6, viewportHeight);
  }

  public int totalLines() {
    return cachedLines.length;
  }

  private void drawTimelineBands(GraphicsContext gc, double railX, int totalLines, boolean light) {
    for (TimelineBlock block : timelines) {
      int start = clampLine(block.startLine(), totalLines);
      int end = clampLine(block.endLine(), totalLines);
      double y = yForLine(start);
      double height = Math.max(1, (end - start + 1) * lineHeight);
      gc.setFill(Color.web(light ? "#5489aa" : "#7794ad", block.folded() ? 0.12 : 0.19));
      gc.fillRoundRect(5, y, railX - 8, height, 3, 3);
      gc.setFill(Color.web(light ? "#417395" : "#889fb3", block.folded() ? 0.45 : 0.68));
      gc.fillRect(6, y, 1, height);
    }
  }

  private void drawScriptTexture(GraphicsContext gc, double railX, int totalLines, boolean light) {
    double height = Math.clamp(lineHeight * 0.52, 0.85, 1.6);
    double contentWidth = railX - 22;
    int stride = Math.max(1, (int) Math.ceil(0.7 / lineHeight));
    for (int i = 0; i < totalLines; i += stride) {
      LineSummary line = cachedLines[i];
      if (line.kind() == LineKind.EMPTY) continue;
      double y = yForLine(i) + (lineHeight - height) / 2;
      double width = contentWidth * Math.clamp(line.length() / 88.0, 0.12, 1);
      double x = switch (line.kind()) {
        case SPEAKER, CHOICE -> 16;
        case COMMENT, TEXT -> 13;
        default -> 10;
      };
      gc.setFill(lineColor(line.kind(), light));
      gc.fillRoundRect(x, y, width, height, 1, 1);
    }
    gc.setFill(lineColor(LineKind.LABEL, light));
    for (int i = 0; i < totalLines; i++) {
      if (cachedLines[i].kind() == LineKind.LABEL) {
        gc.fillRect(5, yForLine(i) + Math.max(0, (lineHeight - 1.5) / 2), 3, 1.5);
      }
    }
  }

  private void drawMarkers(GraphicsContext gc, double railX, int totalLines, boolean light) {
    gc.setFill(Color.web(light ? "#54759e" : "#9bb8d3"));
    for (int line : bookmarks) {
      double y = yForLine(clampLine(line, totalLines)) + lineHeight / 2;
      gc.fillPolygon(new double[]{railX + 3, railX + 6, railX + 3},
          new double[]{y - 2, y, y + 2}, 3);
    }
    // Errors take precedence when several marks share the same pixels.
    for (DiagnosticSeverity severity : List.of(DiagnosticSeverity.INFO, DiagnosticSeverity.WARNING, DiagnosticSeverity.ERROR)) {
      gc.setFill(switch (severity) {
        case INFO -> Color.web(light ? "#497894" : "#8faac5");
        case WARNING -> Color.web(light ? "#ad6927" : "#dbaa6c");
        case ERROR -> Color.web(light ? "#a83841" : "#dd7b85");
      });
      for (DiagnosticMarker marker : diagnostics) {
        if (marker.severity() != severity) continue;
        double y = yForLine(clampLine(marker.line(), totalLines)) + lineHeight / 2;
        gc.fillRoundRect(railX + 7, y - 1, 5, 2, 1, 1);
      }
    }
  }

  private static Color lineColor(LineKind kind, boolean light) {
    return switch (kind) {
      case LABEL -> Color.web(light ? "#8a6334" : "#c2a47d");
      case DIRECTIVE, COMMAND, TIMELINE -> Color.web(light ? "#4c6e8a" : "#8da7bb");
      case SPEAKER, CHOICE -> Color.web(light ? "#466b5d" : "#92aa9b");
      case TEXT -> Color.web(light ? "#6b7d8b" : "#879097");
      case COMMENT -> Color.web(light ? "#a4b0b8" : "#62686e");
      case EMPTY -> Color.TRANSPARENT;
    };
  }

  private void navigate(MouseEvent event) {
    int totalLines = cachedLines.length;
    if (!event.isPrimaryButtonDown() || totalLines == 0 || !viewport.isVisible()) return;
    int targetLine = lineAtY(event.getY(), totalLines);
    codeArea.showParagraphAtTop(targetLine);
    codeArea.moveTo(targetLine, 0);
    codeArea.requestFocus();
    event.consume();
  }

  private void updateTooltip(MouseEvent event) {
    int totalLines = cachedLines.length;
    if (totalLines == 0 || lineHeight <= 0) {
      tooltip.setText("JVN script map");
      return;
    }
    int line = lineAtY(event.getY(), totalLines);
    tooltip.setText(tooltipTextForLine(line));
  }

  String tooltipTextForLine(int line) {
    LineSummary summary = cachedLines[line];
    TimelineBlock timeline = timelineBlockAt(line);
    StringBuilder text = new StringBuilder("Line ").append(line + 1);
    if (timeline != null) {
      TimelineSummary timelineSummary = timelineSummaries.get(timeline);
      text.append(" | timeline lines ").append(timeline.startLine() + 1)
          .append('-').append(timeline.endLine() + 1);
      if (timelineSummary != null) {
        text.append(" | ").append(timelineSummary.durationLabel())
            .append(" | ").append(timelineSummary.trackCount())
            .append(timelineSummary.trackCount() == 1 ? " track" : " tracks")
            .append(" | ").append(timelineSummary.actionCount())
            .append(timelineSummary.actionCount() == 1 ? " action" : " actions");
      }
      if (timeline.folded()) {
        text.append(" | folded");
      }
    } else if (summary.kind() == LineKind.LABEL) {
      text.append(" | @label ").append(summary.label());
    } else if (summary.kind() == LineKind.SPEAKER) {
      text.append(" | dialogue: ").append(summary.speaker());
    } else if (summary.kind() == LineKind.TEXT || summary.kind() == LineKind.CHOICE) {
      String nearbySpeaker = nearbySpeaker(line);
      if (nearbySpeaker != null) {
        text.append(" | dialogue near: ").append(nearbySpeaker);
      } else {
        text.append(" | ").append(summary.kind().name().toLowerCase(Locale.ROOT));
      }
    } else {
      text.append(" | ").append(summary.kind().name().toLowerCase(Locale.ROOT));
    }
    return text.toString();
  }

  private TimelineBlock timelineBlockAt(int line) {
    for (TimelineBlock block : timelines) {
      if (line >= block.startLine() && line <= block.endLine()) {
        return block;
      }
    }
    return null;
  }

  private String nearbySpeaker(int line) {
    int floor = Math.max(0, line - SPEAKER_LOOKBACK_LINES);
    for (int i = line; i >= floor; i--) {
      if (cachedLines[i].kind() == LineKind.SPEAKER) {
        return cachedLines[i].speaker();
      }
    }
    return null;
  }

  private int clampLine(int line, int totalLines) {
    if (totalLines <= 0) return 0;
    return Math.max(0, Math.min(line, totalLines - 1));
  }

  private VisibleRange visibleRange(int totalLines) {
    int start;
    int end;
    try {
      start = codeArea.firstVisibleParToAllParIndex();
      end = codeArea.lastVisibleParToAllParIndex();
    } catch (Exception ex) {
      start = Math.max(0, codeArea.getCurrentParagraph() - 10);
      end = Math.min(totalLines - 1, codeArea.getCurrentParagraph() + 30);
    }
    start = clampLine(start, totalLines);
    end = clampLine(end, totalLines);
    if (end < start) end = start;
    return new VisibleRange(start, end);
  }

  private double yForLine(int line) {
    return MAP_INSET + line * lineHeight;
  }

  private int lineAtY(double y, int totalLines) {
    return clampLine((int) ((y - MAP_INSET) / lineHeight), totalLines);
  }

  private static LineSummary[] analyzeLines(String text) {
    String[] lines = text == null || text.isEmpty() ? new String[0] : text.split("\\n", -1);
    LineSummary[] summaries = new LineSummary[lines.length];
    for (int i = 0; i < lines.length; i++) {
      summaries[i] = analyzeLine(lines[i]);
    }
    return summaries;
  }

  private static LineSummary analyzeLine(String raw) {
    String line = raw == null ? "" : raw.strip();
    if (line.isEmpty()) return new LineSummary(LineKind.EMPTY, "", "", 0);

    Matcher label = LABEL_PATTERN.matcher(line);
    if (label.find()) {
      return new LineSummary(LineKind.LABEL, label.group(1), "", line.length());
    }
    if (TIMELINE_PATTERN.matcher(line).find()) {
      return new LineSummary(LineKind.TIMELINE, "", "", line.length());
    }
    if (line.startsWith("#")) {
      return new LineSummary(LineKind.COMMENT, "", "", line.length());
    }
    if (line.startsWith("@")) {
      return new LineSummary(LineKind.DIRECTIVE, "", "", line.length());
    }
    if (line.startsWith("[")) {
      return new LineSummary(LineKind.COMMAND, "", "", line.length());
    }
    if (line.startsWith(">")) {
      return new LineSummary(LineKind.CHOICE, "", "", line.length());
    }
    Matcher speaker = SPEAKER_PATTERN.matcher(line);
    if (speaker.find()) {
      return new LineSummary(LineKind.SPEAKER, "", speaker.group(1).trim(), line.length());
    }
    return new LineSummary(LineKind.TEXT, "", "", line.length());
  }
}
