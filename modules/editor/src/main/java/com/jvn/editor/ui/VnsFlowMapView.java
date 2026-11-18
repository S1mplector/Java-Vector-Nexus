package com.jvn.editor.ui;

import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Consumer;

/** Visual label-flow graph for a single VNS script. */
public class VnsFlowMapView extends BorderPane {
  private static final double NODE_WIDTH = 170;
  private static final double NODE_HEIGHT = 52;
  private static final double X_GAP = 230;
  private static final double Y_GAP = 90;
  private static final double MARGIN_X = 24;
  private static final double MARGIN_Y = 24;

  private final Label titleLabel = new Label("Label Flow Map");
  private final Label fileLabel = new Label("No active .vns file");
  private final Label summaryLabel = new Label("Open a .vns script to inspect label flow.");
  private final Label zoomLabel = new Label("100%");
  private final Pane graphPane = new Pane();
  private final Group graphGroup = new Group(graphPane);
  private final StackPane graphSurface = new StackPane(graphGroup);
  private final ScrollPane scrollPane = new ScrollPane(graphSurface);

  private Consumer<Integer> onOpenLine;
  private double zoom = 1.0;

  public VnsFlowMapView() {
    getStyleClass().add("sidebar-tool-root");
    titleLabel.getStyleClass().add("sidebar-tool-title");
    fileLabel.getStyleClass().add("sidebar-tool-subtitle");
    summaryLabel.getStyleClass().add("sidebar-tool-summary");
    summaryLabel.setWrapText(true);

    HBox titleRow = new HBox(6, titleLabel, SidebarToolHelp.button(this, "Label Flow Map", """
        The Label Flow Map visualises the @label graph of the active .vns \
script as a navigable node diagram.

Each node represents a label block. Directed edges show which labels are \
reachable from choices, jumps (@goto), or fall-through. This helps you:
  • Spot dead-end labels that can never be reached
  • Find labels that have no outgoing connections (potential dead ends)
  • Trace the story path from a given label without reading raw script

Clicking a node jumps the script editor to that label's opening line. Orange \
nodes are unreachable from the start label; red dashed nodes are undefined targets.

Use the toolbar to zoom, fit the graph, or return to 100%. Ctrl/Cmd + mouse \
wheel zooms around the graph while normal scrolling and dragging pan it. The map \
refreshes automatically when you edit or switch the active .vns file."""));
    titleRow.setAlignment(Pos.CENTER_LEFT);
    Region titleSpacer = new Region();
    HBox.setHgrow(titleSpacer, Priority.ALWAYS);
    Button zoomOut = toolbarButton("−", "Zoom out", () -> setZoom(zoom - 0.1));
    Button zoomIn = toolbarButton("+", "Zoom in", () -> setZoom(zoom + 0.1));
    Button actualSize = toolbarButton("100%", "Reset graph zoom", () -> setZoom(1.0));
    Button fit = toolbarButton("Fit", "Fit the entire graph in the viewport", this::fitGraph);
    zoomLabel.getStyleClass().add("sidebar-tool-subtitle");
    zoomLabel.setMinWidth(38);
    HBox toolbar = new HBox(5, zoomOut, zoomLabel, zoomIn, actualSize, fit);
    toolbar.setAlignment(Pos.CENTER_LEFT);
    titleRow.getChildren().add(titleSpacer);

    FlowPane legend = new FlowPane(10, 4,
        legendItem("●", "Start", "#8bd17c"),
        legendItem("●", "Reachable", "#5e718f"),
        legendItem("●", "Unreachable", "#f0b673"),
        legendItem("●", "Undefined", "#f38ba8"));
    legend.setAlignment(Pos.CENTER_LEFT);
    VBox header = new VBox(6, titleRow, fileLabel, summaryLabel, toolbar, legend);
    header.getStyleClass().add("sidebar-tool-header");
    setTop(header);

    graphPane.setMinSize(420, 320);
    graphPane.setPrefSize(780, 520);
    graphSurface.setAlignment(Pos.TOP_LEFT);
    updateZoomSurface();

    scrollPane.setFitToHeight(true);
    scrollPane.setFitToWidth(false);
    scrollPane.setPannable(true);
    scrollPane.setOnScroll(event -> {
      if (!event.isShortcutDown()) return;
      setZoom(zoom + (event.getDeltaY() > 0 ? 0.1 : -0.1));
      event.consume();
    });
    setCenter(scrollPane);
  }

  public void setOnOpenLine(Consumer<Integer> onOpenLine) {
    this.onOpenLine = onOpenLine;
  }

  public void clear() {
    fileLabel.setText("No active .vns file");
    summaryLabel.setText("Open a .vns script to inspect label flow.");
    graphPane.getChildren().clear();
    setZoom(1.0);
  }

  public void setAnalysis(File scriptFile, VnsScriptAnalyzer.Analysis analysis) {
    if (scriptFile == null || analysis == null) {
      clear();
      return;
    }

    fileLabel.setText(scriptFile.getName());
    FlowSummary summary = summarizeAnalysis(analysis);
    summaryLabel.setText(summary.displayText());
    renderGraph(analysis);
  }

  private void renderGraph(VnsScriptAnalyzer.Analysis analysis) {
    graphPane.getChildren().clear();

    List<VnsScriptAnalyzer.LabelNode> labelNodes = analysis.labels();
    if (labelNodes.isEmpty()) {
      Label empty = new Label("No labels found in this script.");
      empty.setLayoutX(24);
      empty.setLayoutY(24);
      graphPane.getChildren().add(empty);
      graphPane.setPrefSize(520, 260);
      updateZoomSurface();
      return;
    }

    Map<String, GraphNode> graphNodes = new LinkedHashMap<>();
    for (VnsScriptAnalyzer.LabelNode node : labelNodes) {
      graphNodes.put(node.name(), new GraphNode(node.name(), node.line(), true, false));
    }

    // Adjacency for depth assignment.
    Map<String, List<VnsScriptAnalyzer.FlowEdge>> outgoing = new HashMap<>();
    for (VnsScriptAnalyzer.FlowEdge edge : analysis.edges()) {
      outgoing.computeIfAbsent(edge.fromLabel(), k -> new ArrayList<>()).add(edge);
    }

    Map<String, Integer> depthByLabel = new HashMap<>();
    ArrayDeque<String> queue = new ArrayDeque<>();
    String start = analysis.startLabel();
    if (start != null && graphNodes.containsKey(start)) {
      depthByLabel.put(start, 0);
      queue.add(start);
    }

    while (!queue.isEmpty()) {
      String current = queue.removeFirst();
      int currentDepth = depthByLabel.getOrDefault(current, 0);
      for (VnsScriptAnalyzer.FlowEdge edge : outgoing.getOrDefault(current, List.of())) {
        if (!edge.definedTarget()) continue;
        if (!graphNodes.containsKey(edge.toLabel())) continue;
        if (depthByLabel.containsKey(edge.toLabel())) continue;
        depthByLabel.put(edge.toLabel(), currentDepth + 1);
        queue.addLast(edge.toLabel());
      }
    }

    Set<String> reachableLabels = Set.copyOf(depthByLabel.keySet());
    for (String reachable : reachableLabels) {
      GraphNode node = graphNodes.get(reachable);
      if (node != null) node.reachable = true;
    }

    int maxDepth = depthByLabel.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    List<GraphNode> unassigned = new ArrayList<>(graphNodes.values());
    unassigned.removeIf(node -> depthByLabel.containsKey(node.label));
    unassigned.sort(Comparator.comparingInt((GraphNode n) -> n.line).thenComparing(n -> n.label, String.CASE_INSENSITIVE_ORDER));
    for (GraphNode node : unassigned) {
      maxDepth++;
      depthByLabel.put(node.label, maxDepth);
    }

    // Add unresolved target nodes so broken jumps are visible.
    for (VnsScriptAnalyzer.FlowEdge edge : analysis.edges()) {
      if (edge.definedTarget()) continue;
      String unresolvedKey = unresolvedNodeKey(edge.toLabel());
      if (!graphNodes.containsKey(unresolvedKey)) {
        GraphNode unresolved = new GraphNode(edge.toLabel(), -1, false, false);
        graphNodes.put(unresolvedKey, unresolved);
      }
      int fromDepth = depthByLabel.getOrDefault(edge.fromLabel(), 0);
      depthByLabel.put(unresolvedKey, Math.max(depthByLabel.getOrDefault(unresolvedKey, 0), fromDepth + 1));
    }

    Map<Integer, List<GraphNode>> layers = new HashMap<>();
    for (Map.Entry<String, GraphNode> entry : graphNodes.entrySet()) {
      int depth = depthByLabel.getOrDefault(entry.getKey(), 0);
      layers.computeIfAbsent(depth, k -> new ArrayList<>()).add(entry.getValue());
    }

    int maxLayerSize = 1;
    for (Map.Entry<Integer, List<GraphNode>> layer : layers.entrySet()) {
      List<GraphNode> nodes = layer.getValue();
      nodes.sort(Comparator.comparingInt((GraphNode n) -> n.line).thenComparing(n -> n.label, String.CASE_INSENSITIVE_ORDER));
      maxLayerSize = Math.max(maxLayerSize, nodes.size());
      for (int i = 0; i < nodes.size(); i++) {
        GraphNode node = nodes.get(i);
        node.x = MARGIN_X + layer.getKey() * X_GAP;
        node.y = MARGIN_Y + i * Y_GAP;
      }
    }

    // Draw edges first.
    for (VnsScriptAnalyzer.FlowEdge edge : analysis.edges()) {
      GraphNode from = graphNodes.get(edge.fromLabel());
      GraphNode to = edge.definedTarget()
          ? graphNodes.get(edge.toLabel())
          : graphNodes.get(unresolvedNodeKey(edge.toLabel()));
      if (from == null || to == null) continue;

      double startX = from.x + NODE_WIDTH;
      double startY = from.y + NODE_HEIGHT / 2.0;
      double endX = to.x;
      double endY = to.y + NODE_HEIGHT / 2.0;

      Line line = new Line(startX, startY, endX, endY);
      line.setStroke(edgeColor(edge));
      line.setStrokeWidth(1.8);
      if (!edge.definedTarget() || edge.kind() == VnsScriptAnalyzer.FlowEdgeKind.FALLTHROUGH) {
        line.getStrokeDashArray().setAll(8.0, 6.0);
      }
      graphPane.getChildren().add(line);

      Polygon arrow = arrowHead(startX, startY, endX, endY, edgeColor(edge));
      graphPane.getChildren().add(arrow);
    }

    // Draw nodes on top.
    for (GraphNode node : graphNodes.values()) {
      Rectangle rect = new Rectangle(NODE_WIDTH, NODE_HEIGHT);
      rect.setArcWidth(10);
      rect.setArcHeight(10);
      rect.setLayoutX(node.x);
      rect.setLayoutY(node.y);

      if (!node.defined) {
        rect.setFill(Color.web("#3a2327"));
        rect.setStroke(Color.web("#f38ba8"));
        rect.getStrokeDashArray().setAll(6.0, 5.0);
      } else if (node.label.equalsIgnoreCase(analysis.startLabel())) {
        rect.setFill(Color.web("#23362a"));
        rect.setStroke(Color.web("#8bd17c"));
      } else if (!node.reachable) {
        rect.setFill(Color.web("#3a3023"));
        rect.setStroke(Color.web("#f0b673"));
        rect.getStrokeDashArray().setAll(6.0, 4.0);
      } else {
        rect.setFill(Color.web("#252c39"));
        rect.setStroke(Color.web("#5e718f"));
      }
      rect.setStrokeWidth(1.6);

      Text label = new Text(node.defined ? node.label : "? " + node.label);
      label.setFill(Color.web("#e6e9f0"));
      label.setLayoutX(node.x + 10);
      label.setLayoutY(node.y + 22);

      Text line = new Text(node.defined ? ("L" + (node.line + 1)) : "Undefined");
      line.setFill(node.defined ? Color.web("#aab2c5") : Color.web("#f0b673"));
      line.setLayoutX(node.x + 10);
      line.setLayoutY(node.y + 40);

      if (node.defined) {
        rect.setCursor(Cursor.HAND);
        label.setCursor(Cursor.HAND);
        line.setCursor(Cursor.HAND);
        rect.setOnMouseClicked(e -> openLine(node, e.getButton()));
        label.setOnMouseClicked(e -> openLine(node, e.getButton()));
        line.setOnMouseClicked(e -> openLine(node, e.getButton()));
      }
      String nodeDescription = node.defined
          ? node.label + ", line " + (node.line + 1)
              + (node.reachable ? ", reachable" : ", unreachable")
          : node.label + ", undefined target";
      rect.setAccessibleText(nodeDescription);
      label.setAccessibleText(nodeDescription);
      line.setAccessibleText(nodeDescription);
      Tooltip nodeTooltip = new Tooltip(nodeDescription + (node.defined ? " — click to open" : ""));
      Tooltip.install(rect, nodeTooltip);
      Tooltip.install(label, nodeTooltip);
      Tooltip.install(line, nodeTooltip);

      graphPane.getChildren().addAll(rect, label, line);
    }

    double maxDepthValue = depthByLabel.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    double width = Math.max(640, MARGIN_X * 2 + (maxDepthValue + 1) * X_GAP);
    double height = Math.max(360, MARGIN_Y * 2 + Math.max(1, maxLayerSize) * Y_GAP + 60);
    graphPane.setPrefSize(width, height);
    updateZoomSurface();
  }

  private Button toolbarButton(String text, String tooltip, Runnable action) {
    Button button = new Button(text);
    button.setTooltip(new Tooltip(tooltip));
    button.setAccessibleText(tooltip);
    button.setOnAction(event -> action.run());
    button.setFocusTraversable(false);
    return button;
  }

  private Label legendItem(String marker, String text, String color) {
    Label label = new Label(marker + " " + text);
    label.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 10px;");
    return label;
  }

  private void setZoom(double requestedZoom) {
    zoom = Math.max(0.1, Math.min(2.0, requestedZoom));
    zoom = Math.round(zoom * 10.0) / 10.0;
    graphGroup.setScaleX(zoom);
    graphGroup.setScaleY(zoom);
    zoomLabel.setText(Math.round(zoom * 100) + "%");
    updateZoomSurface();
  }

  private void fitGraph() {
    double graphWidth = Math.max(1, graphPane.getPrefWidth());
    double graphHeight = Math.max(1, graphPane.getPrefHeight());
    double viewportWidth = Math.max(1, scrollPane.getViewportBounds().getWidth() - 16);
    double viewportHeight = Math.max(1, scrollPane.getViewportBounds().getHeight() - 16);
    setZoom(Math.min(viewportWidth / graphWidth, viewportHeight / graphHeight));
    Platform.runLater(() -> {
      scrollPane.setHvalue(0);
      scrollPane.setVvalue(0);
    });
  }

  private void updateZoomSurface() {
    graphSurface.setMinSize(
        Math.max(1, graphPane.getPrefWidth() * zoom),
        Math.max(1, graphPane.getPrefHeight() * zoom));
    graphSurface.setPrefSize(
        Math.max(1, graphPane.getPrefWidth() * zoom),
        Math.max(1, graphPane.getPrefHeight() * zoom));
  }

  static FlowSummary summarizeAnalysis(VnsScriptAnalyzer.Analysis analysis) {
    if (analysis == null) return new FlowSummary(0, 0, 0, 0);
    Map<String, List<VnsScriptAnalyzer.FlowEdge>> outgoing = new HashMap<>();
    for (VnsScriptAnalyzer.FlowEdge edge : analysis.edges()) {
      outgoing.computeIfAbsent(edge.fromLabel(), ignored -> new ArrayList<>()).add(edge);
    }
    Set<String> defined = new HashSet<>();
    for (VnsScriptAnalyzer.LabelNode label : analysis.labels()) defined.add(label.name());
    Set<String> reachable = new HashSet<>();
    ArrayDeque<String> queue = new ArrayDeque<>();
    if (analysis.startLabel() != null && defined.contains(analysis.startLabel())) {
      reachable.add(analysis.startLabel());
      queue.add(analysis.startLabel());
    }
    while (!queue.isEmpty()) {
      String from = queue.removeFirst();
      for (VnsScriptAnalyzer.FlowEdge edge : outgoing.getOrDefault(from, List.of())) {
        if (edge.definedTarget() && defined.contains(edge.toLabel()) && reachable.add(edge.toLabel())) {
          queue.addLast(edge.toLabel());
        }
      }
    }
    int undefined = (int) analysis.edges().stream()
        .filter(edge -> !edge.definedTarget())
        .map(VnsScriptAnalyzer.FlowEdge::toLabel)
        .distinct()
        .count();
    return new FlowSummary(
        analysis.labels().size(), analysis.edges().size(),
        Math.max(0, defined.size() - reachable.size()), undefined);
  }

  private void openLine(GraphNode node, MouseButton button) {
    if (button != MouseButton.PRIMARY) return;
    if (onOpenLine == null || node.line < 0) return;
    onOpenLine.accept(node.line + 1);
  }

  private Color edgeColor(VnsScriptAnalyzer.FlowEdge edge) {
    if (!edge.definedTarget()) return Color.web("#f38ba8");
    return switch (edge.kind()) {
      case CHOICE -> Color.web("#8bd17c");
      case IF_GOTO -> Color.web("#f0b673");
      case FALLTHROUGH -> Color.web("#6b7280");
      case CALL -> Color.web("#c792ea");
      case JUMP -> Color.web("#66d9ef");
    };
  }

  private Polygon arrowHead(double startX, double startY, double endX, double endY, Color color) {
    Point2D dir = new Point2D(endX - startX, endY - startY);
    if (dir.magnitude() < 0.001) {
      Polygon fallback = new Polygon(endX, endY, endX - 8, endY - 4, endX - 8, endY + 4);
      fallback.setFill(color);
      return fallback;
    }

    Point2D unit = dir.normalize();
    Point2D normal = new Point2D(-unit.getY(), unit.getX());
    Point2D tip = new Point2D(endX, endY);
    Point2D base = tip.subtract(unit.multiply(10));
    Point2D left = base.add(normal.multiply(4.5));
    Point2D right = base.subtract(normal.multiply(4.5));

    Polygon arrow = new Polygon(
        tip.getX(), tip.getY(),
        left.getX(), left.getY(),
        right.getX(), right.getY()
    );
    arrow.setFill(color);
    return arrow;
  }

  private String unresolvedNodeKey(String label) {
    String safe = label == null ? "unknown" : label.trim();
    if (safe.isEmpty()) safe = "unknown";
    return "__unresolved__" + safe.toLowerCase(Locale.ROOT) + "::" + safe;
  }

  private static final class GraphNode {
    final String label;
    final int line;
    final boolean defined;
    boolean reachable;
    double x;
    double y;

    GraphNode(String label, int line, boolean defined, boolean reachable) {
      this.label = label;
      this.line = line;
      this.defined = defined;
      this.reachable = reachable;
    }
  }

  record FlowSummary(int labels, int transitions, int unreachable, int undefined) {
    String displayText() {
      StringBuilder text = new StringBuilder()
          .append(labels).append(labels == 1 ? " label" : " labels")
          .append(", ").append(transitions).append(transitions == 1 ? " transition" : " transitions");
      if (unreachable > 0) text.append("  •  ").append(unreachable).append(" unreachable");
      if (undefined > 0) text.append("  •  ").append(undefined).append(" undefined");
      return text.toString();
    }
  }
}
