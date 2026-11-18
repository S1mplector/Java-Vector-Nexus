package com.jvn.editor.ui;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Graph view for StoryTimelineView. Nodes are draggable arc cards and links are
 * curved connectors with lightweight label chips.
 */
public class StoryGraphPane extends Pane {
  static final double NODE_HEIGHT = 62.0;
  static final double MIN_NODE_WIDTH = 164.0;
  static final double MAX_NODE_WIDTH = 234.0;
  static final double GRID_STEP = 120.0;
  static final double GRID_MAJOR_STEP = 480.0;
  static final double COLUMN_GAP = 190.0;
  static final double ROW_GAP = 34.0;
  static final double CLUSTER_GAP = 86.0;
  static final double CLUSTER_PAD_X = 24.0;
  static final double CLUSTER_PAD_Y = 18.0;
  static final double CLUSTER_HEADER_HEIGHT = 34.0;
  static final double CONTENT_MARGIN = 56.0;
  private static final double DRAG_SNAP_STEP = 20.0;

  static final class LayoutPosition {
    final double x;
    final double y;

    LayoutPosition(double x, double y) {
      this.x = x;
      this.y = y;
    }
  }

  private static final class GraphBounds {
    final double minX;
    final double minY;
    final double maxX;
    final double maxY;

    GraphBounds(double minX, double minY, double maxX, double maxY) {
      this.minX = minX;
      this.minY = minY;
      this.maxX = maxX;
      this.maxY = maxY;
    }

    double width() { return Math.max(0.0, maxX - minX); }
    double height() { return Math.max(0.0, maxY - minY); }
  }

  public static class NodeView extends Group {
    final StoryTimelineView.Arc arc;
    final Rectangle rect;
    final Rectangle headerTint;
    final Rectangle priorityBadge;
    final Text title;
    final Text subtitle;
    final Text priorityText;
    final Circle inHandle;
    final Circle outHandle;
    final double cardWidth;
    final double cardHeight;
    final boolean denseDefault;
    double dragDX;
    double dragDY;
    boolean movedSincePress;
    Consumer<javafx.scene.input.MouseEvent> mousePressedHook;
    Consumer<javafx.scene.input.MouseEvent> mouseDraggedHook;
    Consumer<javafx.scene.input.MouseEvent> mouseReleasedHook;
    Consumer<Boolean> interactionHook;

    NodeView(StoryTimelineView.Arc arc, Color accent, boolean denseDefault) {
      this.arc = arc;
      this.denseDefault = denseDefault;
      this.cardWidth = nodeWidthForArc(arc);
      this.cardHeight = nodeHeightForArc(arc);

      Color edge = accent == null ? Color.web("#5c6b84") : accent.interpolate(Color.WHITE, 0.36);
      Color fill = accent == null ? Color.web("#223042") : Color.color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0.22);
      Color band = accent == null ? Color.web("#2a3d55", 0.30) : Color.color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0.28);

      rect = new Rectangle(cardWidth, cardHeight, fill);
      rect.setArcWidth(16);
      rect.setArcHeight(16);
      rect.setStroke(edge);
      rect.setStrokeWidth(1.35);

      headerTint = new Rectangle(cardWidth, 22, band);
      headerTint.setArcWidth(16);
      headerTint.setArcHeight(16);
      headerTint.setMouseTransparent(true);

      title = new Text(ellipsize(nodeTitle(arc), denseDefault ? 22 : 24));
      title.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));
      title.setFill(Color.web("#edf3ff"));
      title.setLayoutX(14);
      title.setLayoutY(28);
      title.setMouseTransparent(true);

      subtitle = new Text(ellipsize(nodeSubtitle(arc), denseDefault ? 22 : 28));
      subtitle.setFont(Font.font("System", 10.5));
      subtitle.setFill(Color.web("#b4bfd3"));
      subtitle.setLayoutX(14);
      subtitle.setLayoutY(46);
      subtitle.setMouseTransparent(true);

      String badge = priorityBadgeText(arc);
      priorityText = new Text(badge);
      priorityText.setFont(Font.font("System", FontWeight.BOLD, 10));
      priorityText.setFill(Color.web("#eff7ff"));
      priorityText.setMouseTransparent(true);
      double badgeTextWidth = Math.max(14.0, badge.length() * 6.8);
      double badgeWidth = badge.isBlank() ? 0.0 : badgeTextWidth + 14.0;
      priorityBadge = new Rectangle(badgeWidth, badge.isBlank() ? 0.0 : 18.0);
      priorityBadge.setArcWidth(999);
      priorityBadge.setArcHeight(999);
      priorityBadge.setFill(accent == null ? Color.web("#385577", 0.9) : accent.interpolate(Color.WHITE, 0.12));
      priorityBadge.setStroke(accent == null ? Color.web("#7ca6db") : edge);
      priorityBadge.setStrokeWidth(badge.isBlank() ? 0.0 : 1.0);
      priorityBadge.setMouseTransparent(true);
      if (!badge.isBlank()) {
        priorityBadge.setLayoutX(cardWidth - badgeWidth - 12);
        priorityBadge.setLayoutY(8);
        Bounds bounds = priorityText.getLayoutBounds();
        priorityText.setLayoutX(priorityBadge.getLayoutX() + (badgeWidth - bounds.getWidth()) * 0.5);
        priorityText.setLayoutY(priorityBadge.getLayoutY() + 12.2);
      } else {
        priorityText.setVisible(false);
      }

      inHandle = new Circle(6.5, Color.web("#53657e"));
      outHandle = new Circle(6.5, Color.web("#53657e"));
      inHandle.setStroke(Color.web("#9bb0d1"));
      outHandle.setStroke(Color.web("#9bb0d1"));
      inHandle.setStrokeWidth(1.2);
      outHandle.setStrokeWidth(1.2);
      inHandle.setCenterX(8.0);
      inHandle.setCenterY(cardHeight * 0.5);
      outHandle.setCenterX(cardWidth - 8.0);
      outHandle.setCenterY(cardHeight * 0.5);
      inHandle.setCursor(Cursor.CROSSHAIR);
      outHandle.setCursor(Cursor.CROSSHAIR);

      getChildren().addAll(rect, headerTint, title, subtitle, priorityBadge, priorityText, inHandle, outHandle);
      setCursor(Cursor.HAND);
      setLayoutX(arc.x);
      setLayoutY(arc.y);
      setPickOnBounds(false);
      enableDrag();

      inHandle.setOnMouseEntered(e -> highlightHandle(inHandle, true));
      inHandle.setOnMouseExited(e -> highlightHandle(inHandle, false));
      outHandle.setOnMouseEntered(e -> highlightHandle(outHandle, true));
      outHandle.setOnMouseExited(e -> highlightHandle(outHandle, false));
    }

    double centerX() { return getLayoutX() + cardWidth * 0.5; }
    double centerY() { return getLayoutY() + cardHeight * 0.5; }

    void applySelectionState(boolean selected,
                             boolean highlighted,
                             boolean related,
                             boolean deemphasized,
                             boolean revealDetail) {
      Color baseStroke = parseArcColor(arc.color) == null ? Color.web("#5c6b84") : parseArcColor(arc.color).interpolate(Color.WHITE, 0.36);
      Color activeStroke = selected ? Color.web("#8cd1ff") : (highlighted ? Color.web("#6fc2ff") : baseStroke);
      double strokeWidth = selected ? 2.6 : (highlighted ? 2.0 : 1.35);
      rect.setStroke(activeStroke);
      rect.setStrokeWidth(strokeWidth);
      if (selected) {
        rect.setEffect(new DropShadow(14, Color.color(0.30, 0.63, 1.0, 0.28)));
      } else if (highlighted) {
        rect.setEffect(new DropShadow(10, Color.color(0.22, 0.56, 1.0, 0.20)));
      } else {
        rect.setEffect(null);
      }
      title.setFill(selected ? Color.web("#f7fbff") : Color.web("#edf3ff"));
      boolean showSubtitle = revealDetail && !nn(subtitle.getText()).isBlank();
      subtitle.setVisible(showSubtitle);
      subtitle.setOpacity(showSubtitle ? (selected ? 0.98 : (highlighted ? 0.90 : 0.78)) : 0.0);
      boolean showBadge = !nn(priorityText.getText()).isBlank() && (!denseDefault || revealDetail || selected);
      priorityBadge.setVisible(showBadge);
      priorityText.setVisible(showBadge);
      title.setLayoutY(showSubtitle ? 28 : 36);
      headerTint.setOpacity(showSubtitle ? 1.0 : 0.76);
      setOpacity(deemphasized ? 0.34 : (selected ? 1.0 : (related ? 0.96 : 0.90)));
    }

    private void highlightHandle(Circle handle, boolean hovered) {
      handle.setScaleX(hovered ? 1.18 : 1.0);
      handle.setScaleY(hovered ? 1.18 : 1.0);
      handle.setFill(hovered ? Color.web("#6d84a3") : Color.web("#53657e"));
    }

    private void enableDrag() {
      setOnMousePressed(e -> {
        if (e.getButton() != MouseButton.PRIMARY) return;
        if (interactionHook != null) interactionHook.accept(true);
        Pane parent = (Pane) getParent();
        Point2D p = parent.sceneToLocal(e.getSceneX(), e.getSceneY());
        dragDX = p.getX() - getLayoutX();
        dragDY = p.getY() - getLayoutY();
        movedSincePress = false;
        if (e.getTarget() == outHandle && mousePressedHook != null) {
          mousePressedHook.accept(e);
        }
        e.consume();
      });
      setOnMouseDragged(e -> {
        // MOUSE_DRAGGED reports MouseButton.NONE on JavaFX; the held-button
        // state is the authoritative signal after the initial press.
        if (!e.isPrimaryButtonDown()) return;
        if (e.getTarget() == outHandle) {
          if (mouseDraggedHook != null) mouseDraggedHook.accept(e);
          e.consume();
          return;
        }
        Pane parent = (Pane) getParent();
        Point2D p = parent.sceneToLocal(e.getSceneX(), e.getSceneY());
        double nx = p.getX() - dragDX;
        double ny = p.getY() - dragDY;
        setLayoutX(nx);
        setLayoutY(ny);
        arc.x = nx;
        arc.y = ny;
        movedSincePress = true;
        if (onMoved != null) onMoved.run();
        e.consume();
      });
      setOnMouseReleased(e -> {
        if (mouseReleasedHook != null) mouseReleasedHook.accept(e);
        if (interactionHook != null) interactionHook.accept(false);
        e.consume();
      });
      inHandle.setOnMouseReleased(e -> {
        if (mouseReleasedHook != null) mouseReleasedHook.accept(e);
        if (interactionHook != null) interactionHook.accept(false);
        e.consume();
      });
    }

    Runnable onMoved;
  }

  private final Map<String, NodeView> nodeMap = new HashMap<>();
  private final List<Group> linkViews = new ArrayList<>();
  private final List<Group> clusterViews = new ArrayList<>();
  private final Map<String, Color> clusterColorCache = new HashMap<>();
  private final Set<String> collapsedClusters = new HashSet<>();
  private List<StoryTimelineView.Arc> arcs = new ArrayList<>();
  private List<StoryTimelineView.Link> links = new ArrayList<>();
  private NodeView linkingFrom;
  private Line tempLine;
  private boolean requireShiftToLink = true;
  private String filterCluster;
  private String highlightTerm = "";
  private String selectedArcName;
  private String selectedLinkKey;
  private String hoveredArcName;
  private String hoveredLinkKey;
  private boolean denseMode;
  private static boolean HANDLE_TIP_SHOWN = false;

  private Consumer<StoryTimelineView.Arc> onRunArc;
  private Consumer<StoryTimelineView.Arc> onSelectArc;
  private Consumer<StoryTimelineView.Link> onRunLink;
  private Consumer<StoryTimelineView.Link> onSelectLink;
  private Runnable onGraphChanged;
  private Runnable onLayoutCommitted;
  private Consumer<StoryTimelineView.Arc> onDeleteArc;
  private Consumer<Boolean> onInteractionActive;
  private boolean interactionActive;

  public StoryGraphPane() {
    setPadding(new Insets(8));
    setPrefSize(1400, 920);
    setMinSize(800, 520);
    setFocusTraversable(true);
    getStyleClass().add("story-map-canvas");
    addEventHandler(MouseEvent.MOUSE_PRESSED, e -> requestFocus());
    setOnMouseMoved(e -> {
      if (tempLine != null) {
        updateTempLinkEndpoint(e);
      }
    });
    setOnMouseDragged(e -> {
      if (tempLine != null) {
        updateTempLinkEndpoint(e);
        e.consume();
      }
    });
    setOnMouseReleased(e -> {
      if (linkingFrom != null) {
        finishLinking(nodeAtScene(e.getSceneX(), e.getSceneY(), linkingFrom));
        e.consume();
      } else {
        cancelLinking();
      }
    });
    setOnKeyPressed(this::handleKeyPressed);
  }

  public void setOnRunArc(Consumer<StoryTimelineView.Arc> c) { this.onRunArc = c; }
  public void setOnSelectArc(Consumer<StoryTimelineView.Arc> c) { this.onSelectArc = c; }
  public void setOnRunLink(Consumer<StoryTimelineView.Link> c) { this.onRunLink = c; }
  public void setOnSelectLink(Consumer<StoryTimelineView.Link> c) { this.onSelectLink = c; }
  public void setOnGraphChanged(Runnable r) { this.onGraphChanged = r; }
  public void setOnLayoutCommitted(Runnable r) { this.onLayoutCommitted = r; }
  public void setOnDeleteArc(Consumer<StoryTimelineView.Arc> c) { this.onDeleteArc = c; }
  public void setOnInteractionActive(Consumer<Boolean> c) { this.onInteractionActive = c; }
  public void setSimpleLinkMode(boolean enabled) { this.requireShiftToLink = !enabled; }

  public void setModel(List<StoryTimelineView.Arc> arcs, List<StoryTimelineView.Link> links) {
    this.arcs = arcs == null ? new ArrayList<>() : arcs;
    this.links = links == null ? new ArrayList<>() : links;
    refresh();
  }

  public Set<String> getClusterNames() {
    Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    for (StoryTimelineView.Arc a : arcs) {
      if (a != null && a.cluster != null && !a.cluster.isBlank()) out.add(a.cluster);
    }
    return out;
  }

  public void setFilterCluster(String cluster) {
    this.filterCluster = (cluster == null || cluster.isBlank()) ? null : cluster;
    refresh();
  }

  public String getFilterCluster() { return filterCluster; }

  public void toggleClusterCollapse(String cluster) {
    if (cluster == null || cluster.isBlank()) return;
    if (collapsedClusters.contains(cluster)) {
      collapsedClusters.remove(cluster);
    } else {
      collapsedClusters.add(cluster);
    }
    refresh();
  }

  public void autoLayout() {
    if (arcs == null || arcs.isEmpty()) return;
    Map<String, LayoutPosition> layout = computeAutoLayoutPositions(arcs, links);
    for (StoryTimelineView.Arc arc : arcs) {
      if (arc == null || arc.name == null) continue;
      LayoutPosition pos = layout.get(arc.name);
      if (pos == null) continue;
      arc.x = pos.x;
      arc.y = pos.y;
    }
    refresh();
  }

  public void highlight(String term) {
    highlightTerm = term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
    applySelectionAndHighlightState();
  }

  public void selectArc(String arcName) {
    selectedArcName = (arcName == null || arcName.isBlank()) ? null : arcName;
    selectedLinkKey = null;
    applySelectionAndHighlightState();
  }

  public void selectLink(StoryTimelineView.Link link) {
    selectedLinkKey = link == null ? null : linkKey(link);
    selectedArcName = null;
    applySelectionAndHighlightState();
  }

  static Map<String, LayoutPosition> computeAutoLayoutPositions(List<StoryTimelineView.Arc> arcs,
                                                                List<StoryTimelineView.Link> links) {
    Map<String, StoryTimelineView.Arc> byName = new LinkedHashMap<>();
    Map<String, List<String>> incoming = new HashMap<>();
    Map<String, List<String>> outgoing = new HashMap<>();
    Map<String, Integer> outgoingCount = new HashMap<>();
    if (arcs != null) {
      for (StoryTimelineView.Arc arc : arcs) {
        if (arc == null || arc.name == null || arc.name.isBlank()) continue;
        byName.put(arc.name, arc);
        incoming.computeIfAbsent(arc.name, key -> new ArrayList<>());
        outgoing.computeIfAbsent(arc.name, key -> new ArrayList<>());
      }
    }
    if (links != null) {
      for (StoryTimelineView.Link link : links) {
        if (link == null) continue;
        if (!byName.containsKey(link.fromArc) || !byName.containsKey(link.toArc)) continue;
        incoming.computeIfAbsent(link.toArc, key -> new ArrayList<>()).add(link.fromArc);
        outgoing.computeIfAbsent(link.fromArc, key -> new ArrayList<>()).add(link.toArc);
        outgoingCount.merge(link.fromArc, 1, Integer::sum);
      }
    }

    Map<String, Integer> rankMemo = new HashMap<>();
    for (String name : byName.keySet()) {
      computeRank(name, incoming, rankMemo, new HashSet<>());
    }

    Map<Integer, Double> maxWidthByRank = new HashMap<>();
    for (Map.Entry<String, StoryTimelineView.Arc> entry : byName.entrySet()) {
      int rank = rankMemo.getOrDefault(entry.getKey(), 0);
      maxWidthByRank.merge(rank, nodeWidthForArc(entry.getValue()), Math::max);
    }
    List<Integer> ranks = new ArrayList<>(maxWidthByRank.keySet());
    Collections.sort(ranks);
    Map<Integer, Double> xByRank = new HashMap<>();
    double x = CONTENT_MARGIN;
    for (Integer rank : ranks) {
      xByRank.put(rank, x);
      x += maxWidthByRank.getOrDefault(rank, MIN_NODE_WIDTH) + COLUMN_GAP;
    }

    Map<String, List<StoryTimelineView.Arc>> byCluster = new LinkedHashMap<>();
    Map<String, Integer> clusterFirstSeen = new HashMap<>();
    int firstSeen = 0;
    for (StoryTimelineView.Arc arc : byName.values()) {
      String key = clusterKey(arc);
      byCluster.computeIfAbsent(key, ignored -> new ArrayList<>()).add(arc);
      clusterFirstSeen.putIfAbsent(key, firstSeen++);
    }

    List<String> clusterOrder = new ArrayList<>(byCluster.keySet());
    Comparator<String> clusterComparator = Comparator
        .comparingInt((String key) -> minClusterRank(byCluster.get(key), rankMemo))
        .thenComparingDouble(key -> averageClusterRank(byCluster.get(key), rankMemo))
        .thenComparingInt(key -> clusterFirstSeen.getOrDefault(key, Integer.MAX_VALUE))
        .thenComparing(key -> key, String.CASE_INSENSITIVE_ORDER);
    clusterOrder.sort(clusterComparator);

    Map<String, LayoutPosition> positions = new LinkedHashMap<>();
    double y = CONTENT_MARGIN;
    for (String cluster : clusterOrder) {
      List<StoryTimelineView.Arc> clusterArcs = byCluster.getOrDefault(cluster, List.of());
      Map<Integer, List<StoryTimelineView.Arc>> buckets = new HashMap<>();
      for (StoryTimelineView.Arc arc : clusterArcs) {
        int rank = rankMemo.getOrDefault(arc.name, 0);
        buckets.computeIfAbsent(rank, ignored -> new ArrayList<>()).add(arc);
      }

      double laneTop = y + (cluster.isBlank() ? 0.0 : CLUSTER_HEADER_HEIGHT);
      double laneBottom = laneTop;
      for (Integer rank : ranks) {
        List<StoryTimelineView.Arc> bucket = buckets.get(rank);
        if (bucket == null || bucket.isEmpty()) continue;
        bucket.sort(
            Comparator.<StoryTimelineView.Arc>comparingDouble(arc -> barycenterSortKey(arc, positions, incoming, outgoing))
                .thenComparing(Comparator.comparingInt((StoryTimelineView.Arc arc) -> outgoingCount.getOrDefault(arc.name, 0)).reversed())
                .thenComparing(Comparator.comparingInt((StoryTimelineView.Arc arc) -> arc.priority).reversed())
                .thenComparingDouble(arc -> arc.y)
                .thenComparing(arc -> nn(arc.name), String.CASE_INSENSITIVE_ORDER)
        );

        double bucketY = laneTop;
        double columnX = xByRank.getOrDefault(rank, CONTENT_MARGIN);
        for (StoryTimelineView.Arc arc : bucket) {
          positions.put(arc.name, new LayoutPosition(columnX, bucketY));
          bucketY += nodeHeightForArc(arc) + ROW_GAP + Math.min(12.0, outgoingCount.getOrDefault(arc.name, 0) * 1.5);
        }
        laneBottom = Math.max(laneBottom, bucketY - ROW_GAP);
      }
      if (laneBottom <= laneTop) {
        laneBottom = laneTop + NODE_HEIGHT;
      }
      y = laneBottom + CLUSTER_GAP;
    }
    return positions;
  }

  public static double nodeWidthForArc(StoryTimelineView.Arc arc) {
    String title = nodeTitle(arc);
    int density = Math.min(20, Math.max(8, title.length()));
    double width = 132.0 + density * 3.8;
    if (arc != null && arc.priority != 0) width += 28.0;
    return clamp(width, MIN_NODE_WIDTH, MAX_NODE_WIDTH);
  }

  public static double nodeHeightForArc(StoryTimelineView.Arc arc) {
    return NODE_HEIGHT;
  }

  private void refresh() {
    getChildren().clear();
    nodeMap.clear();
    linkViews.clear();
    clusterViews.clear();

    List<StoryTimelineView.Arc> visibleArcs = visibleArcs();
    Map<String, StoryTimelineView.Arc> visibleByName = new LinkedHashMap<>();
    for (StoryTimelineView.Arc arc : visibleArcs) {
      if (arc != null && arc.name != null && !arc.name.isBlank()) {
        visibleByName.put(arc.name, arc);
      }
    }

    GraphBounds bounds = computeGraphBounds(visibleArcs);
    Group grid = drawGrid(bounds);
    getChildren().add(grid);

    List<StoryTimelineView.Link> visibleLinks = visibleLinks(visibleByName.keySet());
    denseMode = visibleArcs.size() >= 8 || visibleLinks.size() >= 12;

    drawClusters(visibleArcs);

    for (StoryTimelineView.Arc arc : visibleArcs) {
      if (Double.isNaN(arc.x)) arc.x = CONTENT_MARGIN;
      if (Double.isNaN(arc.y)) arc.y = CONTENT_MARGIN;
      NodeView view = new NodeView(arc, parseArcColor(arc.color), denseMode);
      view.onMoved = this::updateLinks;
      view.interactionHook = this::setInteractionActive;
      Tooltip.install(view, new Tooltip(nodeTooltip(arc)));
      view.mousePressedHook = e -> {
        if (e.getButton() == MouseButton.PRIMARY && (!requireShiftToLink || e.isShiftDown())) {
          startLinking(view, e.getSceneX(), e.getSceneY());
        }
      };
      view.mouseDraggedHook = this::updateTempLinkEndpoint;
      view.mouseReleasedHook = e -> handleNodeRelease(view, e);

      ContextMenu menu = new ContextMenu();
      MenuItem miOpen = new MenuItem("Open");
      miOpen.setOnAction(e -> { if (onRunArc != null) onRunArc.accept(arc); });
      MenuItem miRun = new MenuItem("Run from Entry");
      miRun.setOnAction(e -> { if (onRunArc != null) onRunArc.accept(arc); });
      MenuItem miCopyGoto = new MenuItem("Copy Goto (entry)");
      miCopyGoto.setOnAction(e -> copyGotoSnippet(arc));
      MenuItem miRename = new MenuItem("Rename...");
      miRename.setOnAction(e -> renameArc(arc));
      MenuItem miDuplicate = new MenuItem("Duplicate Arc");
      miDuplicate.setOnAction(e -> duplicateArc(arc));
      MenuItem miCluster = new MenuItem("Set Cluster...");
      miCluster.setOnAction(e -> {
        var result = EditorDialogs.promptText(
            getScene() == null ? null : getScene().getWindow(),
            "Cluster",
            "Set the cluster name for this arc.",
            "Cluster name",
            arc.cluster == null ? "" : arc.cluster,
            "",
            "Save");
        if (result.isPresent()) {
          arc.cluster = result.get().trim();
          refresh();
          if (onGraphChanged != null) onGraphChanged.run();
        }
      });
      MenuItem miDelete = new MenuItem("Delete Arc");
      miDelete.setOnAction(e -> {
        deleteArc(arc);
      });
      menu.getItems().addAll(miOpen, miRun, miCopyGoto, miRename, miDuplicate, miCluster, miDelete);

      view.setOnMouseClicked(e -> {
        if (e.getButton() == MouseButton.SECONDARY) {
          selectArcInternal(arc, true);
          menu.show(view, e.getScreenX(), e.getScreenY());
          return;
        }
        menu.hide();
        if (e.getButton() != MouseButton.PRIMARY) return;
        if (e.getTarget() == view.outHandle || e.getTarget() == view.inHandle) return;
        selectArcInternal(arc, true);
        if (e.getClickCount() == 2) {
          renameArc(arc);
        }
      });
      view.setOnMouseEntered(e -> {
        hoveredArcName = arc.name;
        applySelectionAndHighlightState();
      });
      view.setOnMouseExited(e -> {
        if (nn(hoveredArcName).equals(nn(arc.name))) {
          hoveredArcName = null;
          applySelectionAndHighlightState();
        }
      });

      nodeMap.put(arc.name, view);
      getChildren().add(view);
    }

    Map<StoryTimelineView.Link, LinkLayoutMetrics> linkMetrics = buildLinkMetrics(visibleLinks);
    for (StoryTimelineView.Link link : visibleLinks) {
      NodeView from = nodeMap.get(link.fromArc);
      NodeView to = nodeMap.get(link.toArc);
      if (from == null || to == null) continue;
      Group rendered = drawLink(from, to, link, linkMetrics.get(link));
      rendered.getProperties().put("link", link);

      ContextMenu menu = new ContextMenu();
      MenuItem miRun = new MenuItem("Run Link");
      miRun.setOnAction(e -> { if (onRunLink != null) onRunLink.accept(link); });
      MenuItem miReverse = new MenuItem("Reverse Link");
      miReverse.setOnAction(e -> reverseLink(link));
      MenuItem miDelete = new MenuItem("Delete Link");
      miDelete.setOnAction(e -> {
        deleteLink(link);
      });
      menu.getItems().addAll(miRun, miReverse, miDelete);
      Tooltip.install(rendered, new Tooltip(fullLinkSummary(link)));
      rendered.setOnMouseClicked(e -> {
        if (e.getButton() == MouseButton.SECONDARY) {
          selectLinkInternal(link, true);
          menu.show(rendered, e.getScreenX(), e.getScreenY());
          return;
        }
        menu.hide();
        if (e.getButton() != MouseButton.PRIMARY) return;
        selectLinkInternal(link, true);
        if (e.getClickCount() == 2 && onRunLink != null) {
          onRunLink.accept(link);
        }
      });
      rendered.setOnMouseEntered(e -> {
        hoveredLinkKey = linkKey(link);
        applySelectionAndHighlightState();
      });
      rendered.setOnMouseExited(e -> {
        if (nn(hoveredLinkKey).equals(linkKey(link))) {
          hoveredLinkKey = null;
          applySelectionAndHighlightState();
        }
      });
      linkViews.add(rendered);
      getChildren().add(Math.max(1, clusterViews.size() + 1), rendered);
    }

    applySelectionAndHighlightState();
    GraphBounds content = computeGraphBounds(visibleArcs);
    setPrefSize(Math.max(1400.0, content.width() + CONTENT_MARGIN * 2.0), Math.max(920.0, content.height() + CONTENT_MARGIN * 2.0));
  }

  private void handleNodeRelease(NodeView view, javafx.scene.input.MouseEvent event) {
    if (linkingFrom != null) {
      NodeView target = nodeAtScene(event.getSceneX(), event.getSceneY(), linkingFrom);
      finishLinking(target);
    } else if (view.movedSincePress) {
      double nx = Math.round(view.getLayoutX() / DRAG_SNAP_STEP) * DRAG_SNAP_STEP;
      double ny = Math.round(view.getLayoutY() / DRAG_SNAP_STEP) * DRAG_SNAP_STEP;
      view.setLayoutX(nx);
      view.setLayoutY(ny);
      view.arc.x = nx;
      view.arc.y = ny;
      if (view.onMoved != null) view.onMoved.run();
      if (onLayoutCommitted != null) onLayoutCommitted.run();
    }
    view.movedSincePress = false;
    event.consume();
  }

  private void handleKeyPressed(KeyEvent event) {
    if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
      if (selectedLinkKey != null) {
        deleteLink(selectedLink());
      } else if (selectedArcName != null) {
        deleteArc(selectedArc());
      }
      event.consume();
      return;
    }
    if (event.getCode() == KeyCode.F2 && selectedArcName != null) {
      renameArc(selectedArc());
      event.consume();
      return;
    }
    if ((event.isShortcutDown() || event.isControlDown()) && event.getCode() == KeyCode.D && selectedArcName != null) {
      duplicateArc(selectedArc());
      event.consume();
      return;
    }
    if ((event.isShortcutDown() || event.isControlDown()) && event.getCode() == KeyCode.R && selectedLinkKey != null) {
      reverseLink(selectedLink());
      event.consume();
    }
  }

  private StoryTimelineView.Arc selectedArc() {
    if (selectedArcName == null) return null;
    for (StoryTimelineView.Arc arc : arcs) {
      if (arc != null && selectedArcName.equals(arc.name)) return arc;
    }
    return null;
  }

  private StoryTimelineView.Link selectedLink() {
    if (selectedLinkKey == null) return null;
    for (StoryTimelineView.Link link : links) {
      if (link != null && selectedLinkKey.equals(linkKey(link))) return link;
    }
    return null;
  }

  private void deleteArc(StoryTimelineView.Arc arc) {
    if (arc == null) return;
    if (onDeleteArc != null) {
      onDeleteArc.accept(arc);
      return;
    }
    arcs.remove(arc);
    links.removeIf(link -> link != null && (nn(arc.name).equals(link.fromArc) || nn(arc.name).equals(link.toArc)));
    selectedArcName = null;
    refresh();
    if (onGraphChanged != null) onGraphChanged.run();
  }

  private void deleteLink(StoryTimelineView.Link link) {
    if (link == null) return;
    links.remove(link);
    selectedLinkKey = null;
    refresh();
    if (onGraphChanged != null) onGraphChanged.run();
  }

  private void duplicateArc(StoryTimelineView.Arc source) {
    if (source == null) return;
    StoryTimelineView.Arc copy = new StoryTimelineView.Arc();
    copy.name = uniqueArcName(nn(source.name).isBlank() ? "Arc" : source.name + " Copy");
    copy.script = nn(source.script);
    copy.entryLabel = nn(source.entryLabel);
    copy.cluster = nn(source.cluster);
    copy.priority = source.priority;
    copy.color = nn(source.color);
    copy.tags = nn(source.tags);
    copy.x = source.x + 48.0;
    copy.y = source.y + 48.0;
    arcs.add(copy);
    selectedArcName = copy.name;
    selectedLinkKey = null;
    refresh();
    if (onSelectArc != null) onSelectArc.accept(copy);
    if (onGraphChanged != null) onGraphChanged.run();
  }

  private String uniqueArcName(String baseName) {
    String root = nn(baseName).trim();
    if (root.isBlank()) root = "Arc";
    Set<String> names = new LinkedHashSet<>();
    for (StoryTimelineView.Arc arc : arcs) {
      if (arc != null && arc.name != null) names.add(arc.name.toLowerCase(Locale.ROOT));
    }
    if (!names.contains(root.toLowerCase(Locale.ROOT))) return root;
    int i = 2;
    while (true) {
      String candidate = root + " " + i;
      if (!names.contains(candidate.toLowerCase(Locale.ROOT))) return candidate;
      i++;
    }
  }

  private void reverseLink(StoryTimelineView.Link link) {
    if (link == null) return;
    String fromArc = link.fromArc;
    String fromLabel = link.fromLabel;
    link.fromArc = link.toArc;
    link.fromLabel = link.toLabel;
    link.toArc = fromArc;
    link.toLabel = fromLabel;
    selectedLinkKey = linkKey(link);
    refresh();
    if (onSelectLink != null) onSelectLink.accept(link);
    if (onGraphChanged != null) onGraphChanged.run();
  }

  private void drawClusters(List<StoryTimelineView.Arc> visibleArcs) {
    Map<String, double[]> bounds = new LinkedHashMap<>();
    Map<String, Integer> counts = new HashMap<>();
    for (StoryTimelineView.Arc arc : visibleArcs) {
      if (arc == null || arc.cluster == null || arc.cluster.isBlank()) continue;
      double width = nodeWidthForArc(arc);
      double height = nodeHeightForArc(arc);
      double[] clusterBounds = bounds.computeIfAbsent(arc.cluster, ignored -> new double[]{
          arc.x, arc.y, arc.x + width, arc.y + height
      });
      clusterBounds[0] = Math.min(clusterBounds[0], arc.x);
      clusterBounds[1] = Math.min(clusterBounds[1], arc.y);
      clusterBounds[2] = Math.max(clusterBounds[2], arc.x + width);
      clusterBounds[3] = Math.max(clusterBounds[3], arc.y + height);
      counts.merge(arc.cluster, 1, Integer::sum);
    }

    for (Map.Entry<String, double[]> entry : bounds.entrySet()) {
      String name = entry.getKey();
      double[] b = entry.getValue();
      double x = b[0] - CLUSTER_PAD_X;
      double y = b[1] - CLUSTER_PAD_Y - CLUSTER_HEADER_HEIGHT;
      double width = (b[2] - b[0]) + CLUSTER_PAD_X * 2.0;
      double height = (b[3] - b[1]) + CLUSTER_PAD_Y * 2.0 + CLUSTER_HEADER_HEIGHT;

      Color base = colorForCluster(name);
      Rectangle background = new Rectangle(x, y, width, height);
      background.setArcWidth(18);
      background.setArcHeight(18);
      background.setFill(Color.color(base.getRed(), base.getGreen(), base.getBlue(), 0.018));
      background.setStroke(Color.color(base.getRed(), base.getGreen(), base.getBlue(), 0.26));
      background.setStrokeWidth(0.95);

      Rectangle headerChip = new Rectangle(x + 12, y + 10, Math.max(130.0, Math.min(220.0, 60.0 + name.length() * 7.4)), 22);
      headerChip.setArcWidth(999);
      headerChip.setArcHeight(999);
      headerChip.setFill(Color.color(base.getRed(), base.getGreen(), base.getBlue(), 0.12));
      headerChip.setStroke(Color.color(base.getRed(), base.getGreen(), base.getBlue(), 0.34));
      headerChip.setStrokeWidth(1.0);

      String clusterLabel = name + (collapsedClusters.contains(name) ? "  (" + counts.getOrDefault(name, 0) + ")" : "");
      Text label = new Text(ellipsize(clusterLabel, 26));
      label.setFont(Font.font("System", FontWeight.BOLD, 13));
      label.setFill(base.interpolate(Color.WHITE, 0.72));
      label.setX(headerChip.getX() + 12);
      label.setY(headerChip.getY() + 15.2);

      Group group = new Group(background, headerChip, label);
      ContextMenu menu = new ContextMenu();
      MenuItem toggle = new MenuItem(collapsedClusters.contains(name) ? "Expand Cluster" : "Collapse Cluster");
      toggle.setOnAction(e -> toggleClusterCollapse(name));
      menu.getItems().add(toggle);
      Tooltip.install(group, new Tooltip(name + "\n" + counts.getOrDefault(name, 0) + " arcs"));
      group.setOnMouseClicked(event -> {
        if (event.getButton() == MouseButton.SECONDARY) {
          menu.show(group, event.getScreenX(), event.getScreenY());
        } else if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
          toggleClusterCollapse(name);
        }
      });
      clusterViews.add(group);
      getChildren().add(group);
    }
  }

  private Group drawGrid(GraphBounds bounds) {
    Group group = new Group();
    double minX = Math.floor((bounds.minX - CONTENT_MARGIN * 2.0) / GRID_STEP) * GRID_STEP;
    double minY = Math.floor((bounds.minY - CONTENT_MARGIN * 2.0) / GRID_STEP) * GRID_STEP;
    double maxX = Math.ceil((bounds.maxX + CONTENT_MARGIN * 2.0) / GRID_STEP) * GRID_STEP;
    double maxY = Math.ceil((bounds.maxY + CONTENT_MARGIN * 2.0) / GRID_STEP) * GRID_STEP;

    for (double x = minX; x <= maxX; x += GRID_STEP) {
      Line line = new Line(x, minY, x, maxY);
      boolean major = Math.round(x) % Math.round(GRID_MAJOR_STEP) == 0;
      line.getStyleClass().add(major ? "story-map-grid-major" : "story-map-grid-minor");
      line.setStrokeWidth(major ? 0.95 : 0.45);
      line.setMouseTransparent(true);
      group.getChildren().add(line);
    }
    for (double y = minY; y <= maxY; y += GRID_STEP) {
      Line line = new Line(minX, y, maxX, y);
      boolean major = Math.round(y) % Math.round(GRID_MAJOR_STEP) == 0;
      line.getStyleClass().add(major ? "story-map-grid-major" : "story-map-grid-minor");
      line.setStrokeWidth(major ? 0.95 : 0.45);
      line.setMouseTransparent(true);
      group.getChildren().add(line);
    }
    return group;
  }

  private Group drawLink(NodeView from,
                         NodeView to,
                         StoryTimelineView.Link link,
                         LinkLayoutMetrics metrics) {
    double sx = from.getLayoutX() + from.outHandle.getCenterX();
    double sy = from.getLayoutY() + from.outHandle.getCenterY() + fanOffset(metrics.outIndex(), metrics.outCount(), 10.0);
    double ex = to.getLayoutX() + to.inHandle.getCenterX();
    double ey = to.getLayoutY() + to.inHandle.getCenterY() + fanOffset(metrics.inIndex(), metrics.inCount(), 10.0);

    double dx = ex - sx;
    double direction = dx >= 0 ? 1.0 : -1.0;
    double sourceBundle = Math.max(100.0, Math.abs(dx) * 0.52);
    double targetBundle = Math.max(78.0, Math.abs(dx) * 0.36);
    double c1x = sx + direction * sourceBundle;
    double c2x = ex - direction * targetBundle;
    double c1y = sy + fanOffset(metrics.outIndex(), metrics.outCount(), metrics.outCount() > 3 ? 4.0 : 9.0);
    double c2y = ey + fanOffset(metrics.inIndex(), metrics.inCount(), metrics.inCount() > 3 ? -6.0 : -12.0);

    Color linkColor = resolveLinkColor(from.arc, to.arc);
    CubicCurve curve = new CubicCurve(sx, sy, c1x, c1y, c2x, c2y, ex, ey);
    curve.setFill(Color.TRANSPARENT);
    curve.setStroke(linkColor);
    curve.setStrokeWidth(1.2);
    curve.setStrokeLineCap(StrokeLineCap.ROUND);
    curve.setStrokeLineJoin(StrokeLineJoin.ROUND);

    Polygon arrow = buildArrowHead(ex, ey, ex - c2x, ey - c2y, linkColor);
    Group group = new Group(curve, arrow);

    String compactHint = compactLinkHint(link);
    if (!compactHint.isBlank()) {
      Point2D anchor = pointOnCurve(0.38, sx, sy, c1x, c1y, c2x, c2y, ex, ey);
      Point2D tangent = tangentOnCurve(0.38, sx, sy, c1x, c1y, c2x, c2y, ex, ey);
      double normalLength = Math.max(0.001, Math.hypot(tangent.getX(), tangent.getY()));
      double nx = -tangent.getY() / normalLength;
      double ny = tangent.getX() / normalLength;
      double offset = fanOffset(metrics.outIndex(), metrics.outCount(), 18.0);
      Group chip = buildLinkChip(compactHint, linkColor, anchor.getX() + nx * offset, anchor.getY() + ny * offset);
      chip.setVisible(false);
      chip.setOpacity(0.0);
      group.getProperties().put("chip", chip);
      group.getChildren().add(chip);
    }
    return group;
  }

  private Group buildLinkChip(String text, Color accent, double centerX, double centerY) {
    String display = ellipsize(text, 26);
    Text label = new Text(display);
    label.setFont(Font.font("System", FontWeight.SEMI_BOLD, 11));
    label.setFill(Color.web("#d7deeb"));
    double width = Math.max(40.0, display.length() * 6.7 + 14.0);
    Rectangle bg = new Rectangle(width, 18);
    bg.setArcWidth(999);
    bg.setArcHeight(999);
    bg.setFill(Color.color(0.06, 0.09, 0.14, 0.84));
    bg.setStroke(Color.color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0.55));
    bg.setStrokeWidth(0.9);
    bg.setLayoutX(centerX - width * 0.5);
    bg.setLayoutY(centerY - 15.0);
    Bounds bounds = label.getLayoutBounds();
    label.setLayoutX(bg.getLayoutX() + (width - bounds.getWidth()) * 0.5);
    label.setLayoutY(bg.getLayoutY() + 12.6);
    return new Group(bg, label);
  }

  private void updateLinks() {
    // Reposition link curves against the live NodeViews without tearing down
    // and recreating nodes (setModel/refresh) — doing that mid-drag destroys
    // the NodeView currently holding mouse capture and aborts the drag gesture.
    for (Group group : linkViews) {
      getChildren().remove(group);
    }
    linkViews.clear();

    Map<String, StoryTimelineView.Arc> visibleByName = new LinkedHashMap<>();
    for (StoryTimelineView.Arc arc : visibleArcs()) {
      if (arc != null && arc.name != null && !arc.name.isBlank()) {
        visibleByName.put(arc.name, arc);
      }
    }
    List<StoryTimelineView.Link> visibleLinks = visibleLinks(visibleByName.keySet());
    Map<StoryTimelineView.Link, LinkLayoutMetrics> linkMetrics = buildLinkMetrics(visibleLinks);

    for (StoryTimelineView.Link link : visibleLinks) {
      NodeView from = nodeMap.get(link.fromArc);
      NodeView to = nodeMap.get(link.toArc);
      if (from == null || to == null) continue;
      Group rendered = drawLink(from, to, link, linkMetrics.get(link));
      rendered.getProperties().put("link", link);

      ContextMenu menu = new ContextMenu();
      MenuItem miRun = new MenuItem("Run Link");
      miRun.setOnAction(e -> { if (onRunLink != null) onRunLink.accept(link); });
      MenuItem miReverse = new MenuItem("Reverse Link");
      miReverse.setOnAction(e -> reverseLink(link));
      MenuItem miDelete = new MenuItem("Delete Link");
      miDelete.setOnAction(e -> {
        deleteLink(link);
      });
      menu.getItems().addAll(miRun, miReverse, miDelete);
      Tooltip.install(rendered, new Tooltip(fullLinkSummary(link)));
      rendered.setOnMouseClicked(e -> {
        if (e.getButton() == MouseButton.SECONDARY) {
          selectLinkInternal(link, true);
          menu.show(rendered, e.getScreenX(), e.getScreenY());
          return;
        }
        menu.hide();
        if (e.getButton() != MouseButton.PRIMARY) return;
        selectLinkInternal(link, true);
        if (e.getClickCount() == 2 && onRunLink != null) {
          onRunLink.accept(link);
        }
      });
      rendered.setOnMouseEntered(e -> {
        hoveredLinkKey = linkKey(link);
        applySelectionAndHighlightState();
      });
      rendered.setOnMouseExited(e -> {
        if (nn(hoveredLinkKey).equals(linkKey(link))) {
          hoveredLinkKey = null;
          applySelectionAndHighlightState();
        }
      });
      linkViews.add(rendered);
      getChildren().add(Math.max(1, clusterViews.size() + 1), rendered);
    }
    applySelectionAndHighlightState();
  }

  private void updateTempLinkEndpoint(MouseEvent event) {
    if (tempLine == null || event == null) return;
    Point2D p = sceneToLocal(event.getSceneX(), event.getSceneY());
    tempLine.setEndX(p.getX());
    tempLine.setEndY(p.getY());
  }

  private NodeView nodeAtScene(double sceneX, double sceneY, NodeView exclude) {
    List<NodeView> nodes = new ArrayList<>(nodeMap.values());
    Collections.reverse(nodes);
    for (NodeView node : nodes) {
      if (node == null || node == exclude) continue;
      Bounds bounds = node.localToScene(node.getBoundsInLocal());
      if (bounds != null && bounds.contains(sceneX, sceneY)) return node;
    }
    return null;
  }

  private void setInteractionActive(boolean active) {
    if (interactionActive == active) return;
    interactionActive = active;
    if (onInteractionActive != null) onInteractionActive.accept(active);
  }

  private void startLinking(NodeView from, double sceneX, double sceneY) {
    linkingFrom = from;
    setInteractionActive(true);
    tempLine = new Line();
    tempLine.setStroke(Color.web("#9fb3ff"));
    tempLine.getStrokeDashArray().setAll(8.0, 8.0);
    tempLine.setStrokeWidth(1.2);
    double sx = from.getLayoutX() + from.outHandle.getCenterX();
    double sy = from.getLayoutY() + from.outHandle.getCenterY();
    tempLine.setStartX(sx);
    tempLine.setStartY(sy);
    Point2D p = sceneToLocal(sceneX, sceneY);
    tempLine.setEndX(p.getX());
    tempLine.setEndY(p.getY());
    getChildren().add(tempLine);

    nodeMap.values().forEach(node -> {
      if (node != from) {
        node.inHandle.setFill(Color.web("#60a5fa"));
        node.inHandle.setScaleX(1.15);
        node.inHandle.setScaleY(1.15);
      }
    });

    if (!HANDLE_TIP_SHOWN) {
      try {
        HANDLE_TIP_SHOWN = true;
        Tooltip tip = new Tooltip("Drag from the out handle to link arcs\nDrop on the in handle to connect");
        Bounds screen = from.outHandle.localToScreen(from.outHandle.getBoundsInLocal());
        if (screen != null) {
          tip.show(from.outHandle, screen.getMinX(), screen.getMaxY() + 6);
          javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(2400));
          pause.setOnFinished(e -> tip.hide());
          pause.play();
        }
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      }
    }
  }

  private void cancelLinking() {
    if (tempLine != null) getChildren().remove(tempLine);
    tempLine = null;
    linkingFrom = null;
    setInteractionActive(false);
    nodeMap.values().forEach(node -> {
      node.inHandle.setFill(Color.web("#53657e"));
      node.inHandle.setScaleX(1.0);
      node.inHandle.setScaleY(1.0);
    });
  }

  private void finishLinking(NodeView target) {
    if (linkingFrom == null || target == null || linkingFrom == target) {
      cancelLinking();
      return;
    }
    String toLabel = EditorDialogs.promptText(
        getScene() == null ? null : getScene().getWindow(),
        "Link Label",
        "Optional label to jump to on the target arc.",
        "To Label",
        "",
        "",
        "Link").map(String::trim).orElse("");
    StoryTimelineView.Link link = new StoryTimelineView.Link();
    link.fromArc = linkingFrom.arc.name;
    link.fromLabel = "";
    link.toArc = target.arc.name;
    link.toLabel = toLabel;
    links.add(link);
    cancelLinking();
    refresh();
    if (onGraphChanged != null) onGraphChanged.run();
  }

  private void selectArcInternal(StoryTimelineView.Arc arc, boolean notify) {
    selectedArcName = arc == null || arc.name == null || arc.name.isBlank() ? null : arc.name;
    selectedLinkKey = null;
    applySelectionAndHighlightState();
    if (notify && onSelectArc != null && arc != null) {
      onSelectArc.accept(arc);
    }
  }

  private void selectLinkInternal(StoryTimelineView.Link link, boolean notify) {
    selectedLinkKey = link == null ? null : linkKey(link);
    selectedArcName = null;
    applySelectionAndHighlightState();
    if (notify && onSelectLink != null && link != null) {
      onSelectLink.accept(link);
    }
  }

  private void applySelectionAndHighlightState() {
    boolean focusMode = hasFocusContext();
    Set<String> focusArcs = collectFocusArcNames();
    Set<String> relatedArcs = expandRelatedArcNames(focusArcs);
    for (Map.Entry<String, NodeView> entry : nodeMap.entrySet()) {
      StoryTimelineView.Arc arc = entry.getValue().arc;
      boolean selected = selectedArcName != null && selectedArcName.equals(entry.getKey());
      boolean hovered = hoveredArcName != null && hoveredArcName.equals(entry.getKey());
      boolean matched = matchesHighlight(arc);
      boolean focused = focusArcs.contains(entry.getKey());
      boolean related = selected || hovered || matched || relatedArcs.contains(entry.getKey());
      boolean highlighted = matched || hovered || focused;
      boolean deemphasized = focusMode && !related;
      boolean revealDetail = !denseMode || selected || hovered || matched || focused;
      entry.getValue().applySelectionState(selected, highlighted, related, deemphasized, revealDetail);
    }
    for (Group group : linkViews) {
      Object raw = group.getProperties().get("link");
      if (!(raw instanceof StoryTimelineView.Link link)) continue;
      boolean selected = selectedLinkKey != null && selectedLinkKey.equals(linkKey(link));
      boolean hovered = hoveredLinkKey != null && hoveredLinkKey.equals(linkKey(link));
      boolean touchesSelectedArc = touchesArc(link, selectedArcName);
      boolean touchesHoveredArc = touchesArc(link, hoveredArcName);
      boolean highlighted = matchesHighlight(link) || touchesSelectedArc || touchesHoveredArc || hovered;
      Group chip = group.getProperties().get("chip") instanceof Group value ? value : null;
      boolean showChip = chip != null && (selected || hovered || touchesSelectedArc || touchesHoveredArc || matchesHighlight(link));
      for (Node child : group.getChildren()) {
        if (child instanceof CubicCurve curve) {
          curve.setStrokeWidth(selected ? 2.3 : (highlighted ? 1.75 : 0.95));
          curve.setOpacity(selected ? 0.98 : (highlighted ? 0.72 : (focusMode ? 0.08 : (denseMode ? 0.16 : 0.20))));
        } else if (child instanceof Polygon arrow) {
          arrow.setOpacity(selected ? 0.98 : (highlighted ? 0.74 : (focusMode ? 0.10 : (denseMode ? 0.18 : 0.22))));
        }
      }
      if (chip != null) {
        chip.setVisible(showChip);
        chip.setOpacity(showChip ? 1.0 : 0.0);
      }
    }
  }

  private boolean matchesHighlight(StoryTimelineView.Arc arc) {
    if (arc == null) return false;
    if (highlightTerm == null || highlightTerm.isBlank()) return false;
    return nn(arc.name).toLowerCase(Locale.ROOT).contains(highlightTerm)
        || nn(arc.entryLabel).toLowerCase(Locale.ROOT).contains(highlightTerm)
        || nn(arc.script).toLowerCase(Locale.ROOT).contains(highlightTerm)
        || nn(arc.tags).toLowerCase(Locale.ROOT).contains(highlightTerm)
        || nn(arc.cluster).toLowerCase(Locale.ROOT).contains(highlightTerm);
  }

  private boolean matchesHighlight(StoryTimelineView.Link link) {
    if (link == null || highlightTerm == null || highlightTerm.isBlank()) return false;
    return nn(link.fromArc).toLowerCase(Locale.ROOT).contains(highlightTerm)
        || nn(link.toArc).toLowerCase(Locale.ROOT).contains(highlightTerm)
        || nn(link.fromLabel).toLowerCase(Locale.ROOT).contains(highlightTerm)
        || nn(link.toLabel).toLowerCase(Locale.ROOT).contains(highlightTerm);
  }

  private boolean hasFocusContext() {
    return (selectedArcName != null && !selectedArcName.isBlank())
        || (selectedLinkKey != null && !selectedLinkKey.isBlank())
        || (hoveredArcName != null && !hoveredArcName.isBlank())
        || (hoveredLinkKey != null && !hoveredLinkKey.isBlank())
        || (highlightTerm != null && !highlightTerm.isBlank());
  }

  private Set<String> collectFocusArcNames() {
    Set<String> focused = new LinkedHashSet<>();
    if (selectedArcName != null && !selectedArcName.isBlank()) focused.add(selectedArcName);
    if (hoveredArcName != null && !hoveredArcName.isBlank()) focused.add(hoveredArcName);
    for (Group group : linkViews) {
      Object raw = group.getProperties().get("link");
      if (!(raw instanceof StoryTimelineView.Link link)) continue;
      boolean selected = selectedLinkKey != null && selectedLinkKey.equals(linkKey(link));
      boolean hovered = hoveredLinkKey != null && hoveredLinkKey.equals(linkKey(link));
      boolean matched = matchesHighlight(link);
      if (selected || hovered || matched) {
        focused.add(nn(link.fromArc));
        focused.add(nn(link.toArc));
      }
    }
    for (Map.Entry<String, NodeView> entry : nodeMap.entrySet()) {
      if (matchesHighlight(entry.getValue().arc)) {
        focused.add(entry.getKey());
      }
    }
    focused.removeIf(String::isBlank);
    return focused;
  }

  private Set<String> expandRelatedArcNames(Set<String> focusArcs) {
    if (focusArcs == null || focusArcs.isEmpty()) return Set.of();
    Set<String> related = new LinkedHashSet<>(focusArcs);
    for (Group group : linkViews) {
      Object raw = group.getProperties().get("link");
      if (!(raw instanceof StoryTimelineView.Link link)) continue;
      if (focusArcs.contains(link.fromArc) || focusArcs.contains(link.toArc)) {
        related.add(nn(link.fromArc));
        related.add(nn(link.toArc));
      }
    }
    related.removeIf(String::isBlank);
    return related;
  }

  private static boolean touchesArc(StoryTimelineView.Link link, String arcName) {
    if (link == null || arcName == null || arcName.isBlank()) return false;
    return arcName.equals(link.fromArc) || arcName.equals(link.toArc);
  }

  private List<StoryTimelineView.Arc> visibleArcs() {
    List<StoryTimelineView.Arc> out = new ArrayList<>();
    for (StoryTimelineView.Arc arc : arcs) {
      if (arc == null) continue;
      if (filterCluster != null) {
        String cluster = arc.cluster == null ? "" : arc.cluster;
        if (!cluster.equals(filterCluster)) continue;
      }
      if (arc.cluster != null && collapsedClusters.contains(arc.cluster)) continue;
      out.add(arc);
    }
    return out;
  }

  private List<StoryTimelineView.Link> visibleLinks(Set<String> visibleArcNames) {
    List<StoryTimelineView.Link> out = new ArrayList<>();
    for (StoryTimelineView.Link link : links) {
      if (link == null) continue;
      if (!visibleArcNames.contains(link.fromArc) || !visibleArcNames.contains(link.toArc)) continue;
      out.add(link);
    }
    return out;
  }

  private Map<StoryTimelineView.Link, LinkLayoutMetrics> buildLinkMetrics(List<StoryTimelineView.Link> visibleLinks) {
    Map<String, List<StoryTimelineView.Link>> outgoing = new HashMap<>();
    Map<String, List<StoryTimelineView.Link>> incoming = new HashMap<>();
    for (StoryTimelineView.Link link : visibleLinks) {
      outgoing.computeIfAbsent(nn(link.fromArc), ignored -> new ArrayList<>()).add(link);
      incoming.computeIfAbsent(nn(link.toArc), ignored -> new ArrayList<>()).add(link);
    }

    Map<StoryTimelineView.Link, LinkLayoutMetrics> metrics = new HashMap<>();
    for (StoryTimelineView.Link link : visibleLinks) {
      List<StoryTimelineView.Link> outList = outgoing.getOrDefault(nn(link.fromArc), List.of());
      List<StoryTimelineView.Link> inList = incoming.getOrDefault(nn(link.toArc), List.of());
      int outIndex = outList.indexOf(link);
      int inIndex = inList.indexOf(link);
      metrics.put(link, new LinkLayoutMetrics(outIndex, Math.max(1, outList.size()), inIndex, Math.max(1, inList.size())));
    }
    return metrics;
  }

  private GraphBounds computeGraphBounds(List<StoryTimelineView.Arc> visibleArcs) {
    double minX = 0.0;
    double minY = 0.0;
    double maxX = 1200.0;
    double maxY = 800.0;
    for (StoryTimelineView.Arc arc : visibleArcs) {
      double width = nodeWidthForArc(arc);
      double height = nodeHeightForArc(arc);
      minX = Math.min(minX, arc.x - CLUSTER_PAD_X);
      minY = Math.min(minY, arc.y - CLUSTER_HEADER_HEIGHT - CLUSTER_PAD_Y);
      maxX = Math.max(maxX, arc.x + width + CLUSTER_PAD_X);
      maxY = Math.max(maxY, arc.y + height + CLUSTER_PAD_Y + CLUSTER_HEADER_HEIGHT);
    }
    return new GraphBounds(minX, minY, maxX, maxY);
  }

  private void renameArc(StoryTimelineView.Arc arc) {
    String old = arc == null ? null : arc.name;
    var res = EditorDialogs.promptText(
        getScene() == null ? null : getScene().getWindow(),
        "Rename Arc",
        "Rename the selected arc.",
        "Arc name",
        old == null ? "" : old,
        old == null ? "" : old,
        "Rename");
    if (res.isEmpty()) return;
    String next = res.get().trim();
    if (next.isEmpty() || next.equals(old)) return;
    arc.name = next;
    updateLinkArcNames(old, next);
    selectedArcName = next;
    refresh();
    if (onGraphChanged != null) onGraphChanged.run();
  }

  private void updateLinkArcNames(String oldName, String newName) {
    if (oldName == null || newName == null) return;
    for (StoryTimelineView.Link link : links) {
      if (oldName.equals(link.fromArc)) link.fromArc = newName;
      if (oldName.equals(link.toArc)) link.toArc = newName;
    }
  }

  private void copyGotoSnippet(StoryTimelineView.Arc arc) {
    String label = arc == null || arc.entryLabel == null ? "" : arc.entryLabel;
    String snippet = "[goto " + nn(arc == null ? null : arc.name) + ":" + label + "]";
    javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
    cc.putString(snippet);
    javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
  }

  private record LinkLayoutMetrics(int outIndex, int outCount, int inIndex, int inCount) {}

  private static int computeRank(String arcName,
                                 Map<String, List<String>> incoming,
                                 Map<String, Integer> memo,
                                 Set<String> visiting) {
    if (arcName == null || arcName.isBlank()) return 0;
    Integer cached = memo.get(arcName);
    if (cached != null) return cached;
    if (!visiting.add(arcName)) {
      return 0;
    }
    int rank = 0;
    for (String parent : incoming.getOrDefault(arcName, List.of())) {
      rank = Math.max(rank, computeRank(parent, incoming, memo, visiting) + 1);
    }
    visiting.remove(arcName);
    memo.put(arcName, rank);
    return rank;
  }

  private static int minClusterRank(List<StoryTimelineView.Arc> arcs, Map<String, Integer> ranks) {
    int min = Integer.MAX_VALUE;
    for (StoryTimelineView.Arc arc : arcs) {
      min = Math.min(min, ranks.getOrDefault(arc.name, 0));
    }
    return min == Integer.MAX_VALUE ? 0 : min;
  }

  private static double barycenterSortKey(StoryTimelineView.Arc arc,
                                          Map<String, LayoutPosition> positions,
                                          Map<String, List<String>> incoming,
                                          Map<String, List<String>> outgoing) {
    if (arc == null || arc.name == null || arc.name.isBlank()) return Double.POSITIVE_INFINITY;
    double total = 0.0;
    int count = 0;
    for (String parent : incoming.getOrDefault(arc.name, List.of())) {
      LayoutPosition position = positions.get(parent);
      if (position == null) continue;
      total += position.y;
      count++;
    }
    for (String child : outgoing.getOrDefault(arc.name, List.of())) {
      LayoutPosition position = positions.get(child);
      if (position == null) continue;
      total += position.y;
      count++;
    }
    if (count == 0) {
      return Double.isNaN(arc.y) ? 0.0 : arc.y;
    }
    return total / count;
  }

  private static double averageClusterRank(List<StoryTimelineView.Arc> arcs, Map<String, Integer> ranks) {
    if (arcs == null || arcs.isEmpty()) return 0.0;
    double total = 0.0;
    for (StoryTimelineView.Arc arc : arcs) {
      total += ranks.getOrDefault(arc.name, 0);
    }
    return total / arcs.size();
  }

  private static String clusterKey(StoryTimelineView.Arc arc) {
    if (arc == null || arc.cluster == null) return "";
    return arc.cluster.trim();
  }

  private static Polygon buildArrowHead(double ex, double ey, double dx, double dy, Color color) {
    double length = Math.max(0.001, Math.hypot(dx, dy));
    double nx = dx / length;
    double ny = dy / length;
    double arrowLength = 12.0;
    double arrowWidth = 6.0;
    double x1 = ex - arrowLength * nx + arrowWidth * ny;
    double y1 = ey - arrowLength * ny - arrowWidth * nx;
    double x2 = ex - arrowLength * nx - arrowWidth * ny;
    double y2 = ey - arrowLength * ny + arrowWidth * nx;
    Polygon arrow = new Polygon(ex, ey, x1, y1, x2, y2);
    arrow.setFill(color);
    return arrow;
  }

  private static double fanOffset(int index, int count, double spacing) {
    if (count <= 1) return 0.0;
    return (index - (count - 1) * 0.5) * spacing;
  }

  private static Point2D pointOnCurve(double t,
                                      double x0, double y0,
                                      double x1, double y1,
                                      double x2, double y2,
                                      double x3, double y3) {
    double mt = 1.0 - t;
    double x = mt * mt * mt * x0
        + 3 * mt * mt * t * x1
        + 3 * mt * t * t * x2
        + t * t * t * x3;
    double y = mt * mt * mt * y0
        + 3 * mt * mt * t * y1
        + 3 * mt * t * t * y2
        + t * t * t * y3;
    return new Point2D(x, y);
  }

  private static Point2D tangentOnCurve(double t,
                                        double x0, double y0,
                                        double x1, double y1,
                                        double x2, double y2,
                                        double x3, double y3) {
    double mt = 1.0 - t;
    double x = 3 * mt * mt * (x1 - x0)
        + 6 * mt * t * (x2 - x1)
        + 3 * t * t * (x3 - x2);
    double y = 3 * mt * mt * (y1 - y0)
        + 6 * mt * t * (y2 - y1)
        + 3 * t * t * (y3 - y2);
    return new Point2D(x, y);
  }

  private static Color resolveLinkColor(StoryTimelineView.Arc from, StoryTimelineView.Arc to) {
    Color start = parseArcColor(from == null ? null : from.color);
    Color end = parseArcColor(to == null ? null : to.color);
    if (start == null && end == null) return Color.web("#91a0ba", 0.74);
    if (start == null) start = end;
    if (end == null) end = start;
    return start.interpolate(end, 0.5).deriveColor(0, 1.0, 0.96, 0.78);
  }

  private static String priorityBadgeText(StoryTimelineView.Arc arc) {
    if (arc == null || arc.priority == 0) return "";
    return "p" + arc.priority;
  }

  private static String nodeTitle(StoryTimelineView.Arc arc) {
    if (arc == null || arc.name == null || arc.name.isBlank()) return "(unnamed)";
    return arc.name;
  }

  private static String nodeSubtitle(StoryTimelineView.Arc arc) {
    if (arc == null) return "No script";
    List<String> parts = new ArrayList<>();
    if (arc.entryLabel != null && !arc.entryLabel.isBlank()) parts.add("@" + arc.entryLabel);
    if (arc.script != null && !arc.script.isBlank()) parts.add(shortFileName(arc.script));
    if (parts.isEmpty()) return "No script / entry";
    if (parts.size() == 1) return parts.get(0);
    return parts.get(0) + "  ·  " + parts.get(1);
  }

  private static String shortFileName(String raw) {
    if (raw == null || raw.isBlank()) return "";
    String normalized = raw.replace('\\', '/').trim();
    int idx = normalized.lastIndexOf('/');
    String fileName = idx >= 0 ? normalized.substring(idx + 1) : normalized;
    if (fileName.toLowerCase(Locale.ROOT).endsWith(".vns")) {
      return fileName.substring(0, fileName.length() - 4);
    }
    return fileName;
  }

  private static String nodeTooltip(StoryTimelineView.Arc arc) {
    if (arc == null) return "";
    StringBuilder sb = new StringBuilder();
    sb.append(nodeTitle(arc));
    if (arc.priority != 0) sb.append("  p").append(arc.priority);
    if (arc.script != null && !arc.script.isBlank()) sb.append("\nScript: ").append(arc.script);
    if (arc.entryLabel != null && !arc.entryLabel.isBlank()) sb.append("\nEntry: ").append(arc.entryLabel);
    if (arc.cluster != null && !arc.cluster.isBlank()) sb.append("\nCluster: ").append(arc.cluster);
    if (arc.tags != null && !arc.tags.isBlank()) sb.append("\nTags: ").append(arc.tags);
    if (arc.color != null && !arc.color.isBlank()) sb.append("\nColor: ").append(arc.color);
    return sb.toString();
  }

  private static String compactLinkHint(StoryTimelineView.Link link) {
    if (link == null) return "";
    String from = nn(link.fromLabel).trim();
    String to = nn(link.toLabel).trim();
    if (from.isBlank() && to.isBlank()) return "";
    if (!from.isBlank() && (to.isBlank() || isGenericEntryLabel(to))) return from;
    if (from.isBlank()) return isGenericEntryLabel(to) ? "" : to;
    if (to.isBlank()) return from;
    return ellipsize(from, 15) + " -> " + ellipsize(to, 15);
  }

  private static boolean isGenericEntryLabel(String value) {
    String normalized = nn(value).trim().toLowerCase(Locale.ROOT);
    return normalized.equals("start") || normalized.equals("entry") || normalized.equals("begin");
  }

  private static String fullLinkSummary(StoryTimelineView.Link link) {
    if (link == null) return "";
    return renderEndpoint(link.fromArc, link.fromLabel) + " -> " + renderEndpoint(link.toArc, link.toLabel);
  }

  private static String renderEndpoint(String arc, String label) {
    String base = nn(arc).trim();
    String suffix = nn(label).trim();
    if (suffix.isBlank()) return base;
    return base + ":" + suffix;
  }

  private static String linkKey(StoryTimelineView.Link link) {
    if (link == null) return "";
    return nn(link.fromArc) + "|" + nn(link.fromLabel) + "|" + nn(link.toArc) + "|" + nn(link.toLabel);
  }

  private static Color parseArcColor(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Color.web(raw.trim());
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return null;
    }
  }

  private Color colorForCluster(String name) {
    if (name == null) return Color.web("#334155");
    Color cached = clusterColorCache.get(name);
    if (cached != null) return cached;
    int hash = name.hashCode();
    double hue = (hash & 0xffff) / 65535.0 * 360.0;
    Color color = hsl(hue, 0.42, 0.46);
    clusterColorCache.put(name, color);
    return color;
  }

  private static Color hsl(double h, double s, double l) {
    h = (h % 360 + 360) % 360;
    s = Math.max(0, Math.min(1, s));
    l = Math.max(0, Math.min(1, l));
    double c = (1 - Math.abs(2 * l - 1)) * s;
    double x = c * (1 - Math.abs((h / 60.0) % 2 - 1));
    double m = l - c / 2;
    double r = 0, g = 0, b = 0;
    if (h < 60) { r = c; g = x; }
    else if (h < 120) { r = x; g = c; }
    else if (h < 180) { g = c; b = x; }
    else if (h < 240) { g = x; b = c; }
    else if (h < 300) { r = x; b = c; }
    else { r = c; b = x; }
    return new Color(r + m, g + m, b + m, 1.0);
  }

  private static String ellipsize(String raw, int maxChars) {
    String value = raw == null ? "" : raw.trim();
    if (maxChars <= 0 || value.length() <= maxChars) return value;
    if (maxChars <= 1) return value.substring(0, 1);
    return value.substring(0, Math.max(1, maxChars - 1)) + "…";
  }

  private static String nn(String s) {
    return s == null ? "" : s;
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }
}
