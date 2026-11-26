package com.jvn.fx.vn;

import com.jvn.core.assets.AssetType;
import com.jvn.core.menu.config.MenuProfileLoader;
import com.jvn.fx.menu.MenuTheme;
import com.jvn.fx.ui.AeroGlass;
import com.jvn.fx.ui.ProjectFontResolver;
import com.jvn.core.vn.VnNode;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnScene;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.CubicCurve;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import org.slf4j.LoggerFactory;

/** Shows a project-authored choice map over the VN canvas. Routes still run through VNS. */
public final class VnTopicMapView extends StackPane {
  private static final double MAP_WIDTH = 1520;
  private static final double TOPIC_HEIGHT = 44;
  private static final double ROW_STEP = 53;
  private static final double TOPIC_WIDTH = 238;
  private static final double MIN_ZOOM = 0.26;
  private static final double MAX_ZOOM = 2.4;
  private static final double ZOOM_STEP = 1.24;
  private static final String ICONS = "assets/tutorial/icons/";
  private static final PseudoClass SEARCH_MATCH = PseudoClass.getPseudoClass("search-match");
  private static final PseudoClass SEARCH_EXCLUDED = PseudoClass.getPseudoClass("search-excluded");
  private final BorderPane board = new BorderPane();
  private final ScrollPane scroll = new ScrollPane();
  private final Pane graph = new Pane();
  private final Scale zoom = new Scale(1, 1);
  private final Label title = new Label();
  private final Label progress = new Label();
  private final Label zoomLabel = new Label();
  private final TextField search = new TextField();
  private final MenuButton searchResults = new MenuButton("Matches");
  private final Map<String, Image> images = new HashMap<>();
  private final Map<VnTopicMap.Lesson, Button> topicButtons = new HashMap<>();
  private final Runnable onSelect;
  private final MenuButton branchPicker = new MenuButton("Focus a branch");
  private final Button fullscreen = new Button("Full screen");
  private final Button options = new Button("Progress and motion");
  private final Button exit = new Button("Return to title");
  private File project;
  private String mapPath;
  private VnTopicMap map;
  private VnScene activeScene;
  private VnNode activeNode;
  private MenuTheme theme = MenuTheme.defaults();

  public VnTopicMapView(Runnable onSelect) {
    this.onSelect = onSelect;
    setVisible(false);
    managedProperty().bind(visibleProperty());
    setMinSize(0, 0);
    board.getStyleClass().add("vn-topic-map");
    getChildren().add(board);
    fullscreen.textProperty().bind(sceneProperty().flatMap(Scene::windowProperty)
        .flatMap(window -> window instanceof Stage stage ? stage.fullScreenProperty().asObject() : null)
        .map(full -> full ? "Exit full screen" : "Full screen").orElse("Full screen"));

    Group world = new Group(graph);
    world.getTransforms().add(zoom);
    scroll.setContent(new Group(world));
    scroll.setPannable(true);
    graph.setPickOnBounds(true);
    scroll.setMinSize(0, 0);
    scroll.setFocusTraversable(false);
    scroll.addEventFilter(ScrollEvent.SCROLL, event -> {
      if (event.getDeltaY() == 0) return;
      if (!event.isInertia()) {
        zoomAtPointer(zoom.getX() * Math.exp(event.getDeltaY() * 0.003), event.getSceneX(), event.getSceneY());
      }
      event.consume();
    });
    scroll.addEventFilter(ZoomEvent.ZOOM, event -> {
      zoomAtPointer(zoom.getX() * event.getZoomFactor(), event.getSceneX(), event.getSceneY());
      event.consume();
    });
    board.setCenter(scroll);

    title.getStyleClass().add("topic-map-title");
    progress.getStyleClass().add("topic-map-progress");
    search.setPromptText("Find a lesson");
    search.setAccessibleText("Find a lesson on the topic map");
    search.getStyleClass().add("topic-map-search");
    search.setPrefColumnCount(22);
    search.textProperty().addListener((obs, previous, current) -> updateSearch());
    search.setOnAction(event -> {
      var matches = searchResults.getItems();
      if (matches.size() == 1) matches.getFirst().fire();
      else if (!matches.isEmpty()) searchResults.show();
    });
    searchResults.getStyleClass().add("topic-map-search-results");
    searchResults.setDisable(true);
    FlowPane searchRow = new FlowPane(8, 6, search, searchResults);
    VBox heading = new VBox(5, title, progress, searchRow);
    heading.getStyleClass().add("topic-map-heading");
    board.setTop(heading);

    Button out = new Button("−");
    out.setAccessibleText("Zoom out");
    out.setOnAction(event -> zoomCentered(zoom.getX() / ZOOM_STEP));
    Button in = new Button("+");
    in.setAccessibleText("Zoom in");
    in.setOnAction(event -> zoomCentered(zoom.getX() * ZOOM_STEP));
    Button fit = new Button("Fit map");
    fit.setOnAction(event -> fit());
    fullscreen.setOnAction(event -> toggleFullScreen());
    options.setOnAction(event -> select("options", null));
    exit.setOnAction(event -> select("goodbye", null));
    FlowPane controls = new FlowPane(8, 6, out, zoomLabel, in, fit, branchPicker, fullscreen, options, exit);
    controls.setAlignment(Pos.CENTER);
    controls.getStyleClass().add("topic-map-controls");
    Label help = new Label("Drag the map to pan. Scroll or pinch to zoom. Select a lesson to play.");
    help.setWrapText(true);
    help.getStyleClass().add("topic-map-help");
    VBox footer = new VBox(6, controls, help);
    footer.setAlignment(Pos.CENTER);
    footer.getStyleClass().add("topic-map-footer");
    board.setBottom(footer);
    board.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (event.getCode() == KeyCode.F && event.isShortcutDown()) {
        search.requestFocus();
        search.selectAll();
      } else if (event.getCode() == KeyCode.F11) toggleFullScreen();
      else if (event.getCode() == KeyCode.ESCAPE && search.isFocused() && !search.getText().isEmpty()) search.clear();
      else if (search.isFocused()) return;
      else if (event.getCode() == KeyCode.HOME) fit();
      else if (event.getCode() == KeyCode.ADD || event.getCode() == KeyCode.PLUS || event.getCode() == KeyCode.EQUALS) {
        zoomCentered(zoom.getX() * ZOOM_STEP);
      } else if (event.getCode() == KeyCode.SUBTRACT || event.getCode() == KeyCode.MINUS) {
        zoomCentered(zoom.getX() / ZOOM_STEP);
      } else return;
      event.consume();
    });
    setZoom(1);
  }

  public boolean isActive() {
    return activeScene != null;
  }

  /** Called by the player and editor preview as their active scene changes. */
  public void sync(VnScene scene, File root) {
    Object pathValue = scene == null ? null : scene.getState().getVariable(VnTopicMap.VARIABLE);
    if (!(pathValue instanceof String path) || path.isBlank()
        || scene.getActiveError() != null || scene.getState().getTopOverlayScreen() != null
        || scene.getState().getCurrentNode() == null
        || scene.getState().getCurrentNode().getType() != VnNodeType.CHOICE) {
      deactivate();
      return;
    }
    boolean changedMap = !Objects.equals(project, root) || !path.equals(mapPath);
    if (changedMap) {
      project = root;
      mapPath = path;
      images.clear();
      try {
        map = VnTopicMap.load(root, path);
        var assets = VnTopicMap.assets(root);
        theme = MenuTheme.fromAssets(assets, MenuProfileLoader.load(assets));
        applyMenuStyle(theme);
        if (board.getStylesheets().isEmpty()) {
          board.getStylesheets().add(VnTopicMapView.class.getResource("topic-map.css").toExternalForm());
        }
        buildGraph();
        requestLayout();
      } catch (IOException | IllegalArgumentException ex) {
        LoggerFactory.getLogger(VnTopicMapView.class).warn("Unable to open VN topic map {}", path, ex);
        map = null;
      }
    }
    var node = scene.getState().getCurrentNode();
    boolean category = VnTopicMap.choiceIndex(node, "topics") >= 0;
    if (map == null || map.branches().stream().noneMatch(branch ->
        VnTopicMap.choiceIndex(node, branch.target()) >= 0 || category && branch.lessons().stream()
            .anyMatch(lesson -> VnTopicMap.choiceIndex(node, lesson.target()) >= 0))) {
      deactivate();
      return;
    }
    if (!changedMap && activeScene == scene && activeNode == node) return;

    activeScene = scene;
    activeNode = node;
    setVisible(true);
    options.setVisible(category || VnTopicMap.choiceIndex(node, "options") >= 0);
    options.setManaged(options.isVisible());
    exit.setVisible(category || VnTopicMap.choiceIndex(node, "goodbye") >= 0);
    exit.setManaged(exit.isVisible());
    title.setText(map.title());
    branchPicker.setGraphic(graphic(ICONS + "search.png", 18));
    fullscreen.setGraphic(graphic(ICONS + "fullscreen.png", 18));
    options.setGraphic(graphic(ICONS + "settings.png", 18));
    exit.setGraphic(graphic(ICONS + "back.png", 18));
    int completed = 0;
    for (var entry : topicButtons.entrySet()) {
      var lesson = entry.getKey();
      Button button = entry.getValue();
      boolean done = lesson.completed(scene);
      if (done) completed++;
      button.setText(done ? lesson.title() + "  ✓" : lesson.title());
      button.setAccessibleText(lesson.title() + (done ? ", completed" : ""));
    }
    progress.setText(completed + " of " + topicButtons.size() + " lessons completed");
    Platform.runLater(() -> {
      if (activeScene != scene) return;
      if (changedMap) {
        board.applyCss();
        board.layout();
        fit();
        if (zoom.getX() < 0.62) zoomCentered(0.62);
      }
      fullscreen.requestFocus();
    });
  }

  private void deactivate() {
    if (activeScene == null) return;
    activeScene = null;
    activeNode = null;
    setVisible(false);
  }

  private void select(String branch, String lesson) {
    if (activeScene == null || !VnTopicMap.open(activeScene, branch, lesson)) return;
    deactivate();
    onSelect.run();
  }

  private void buildGraph() {
    graph.getChildren().clear();
    topicButtons.clear();
    branchPicker.getItems().clear();
    int split = (map.branches().size() + 1) / 2;
    var left = map.branches().subList(0, split);
    var right = map.branches().subList(split, map.branches().size());
    double height = Math.max(360, Math.max(sideHeight(left), sideHeight(right)) + 40);
    graph.setMinSize(MAP_WIDTH, height);
    graph.setPrefSize(MAP_WIDTH, height);
    graph.setMaxSize(MAP_WIDTH, height);

    buildSide(left, true, height);
    buildSide(right, false, height);
    Label mapTitle = new Label(map.title());
    mapTitle.setWrapText(true);
    mapTitle.setAlignment(Pos.CENTER);
    var portrait = graphic(map.portrait(), 88);
    VBox hub = portrait == null ? new VBox(mapTitle) : new VBox(6, portrait, mapTitle);
    hub.setAlignment(Pos.CENTER);
    hub.getStyleClass().add("topic-map-hub");
    place(hub, 658, height / 2 - 84, 204, 168);
    search.clear();
    updateSearch();
  }

  private void updateSearch() {
    searchResults.hide();
    var matches = searchResults.getItems();
    matches.clear();
    String query = search.getText().strip().toLowerCase(Locale.ROOT);
    if (map != null) {
      var terms = query.split("\\s+");
      for (var branch : map.branches()) {
        for (var lesson : branch.lessons()) {
          Button card = topicButtons.get(lesson);
          String titleAndBranch = (lesson.title() + " " + branch.title()).toLowerCase(Locale.ROOT);
          boolean match = !query.isEmpty() && java.util.Arrays.stream(terms).allMatch(titleAndBranch::contains);
          card.pseudoClassStateChanged(SEARCH_MATCH, match);
          card.pseudoClassStateChanged(SEARCH_EXCLUDED, !query.isEmpty() && !match);
          if (match) {
            MenuItem item = new MenuItem(lesson.title() + " · " + branch.title());
            item.setOnAction(event -> {
              card.requestFocus();
              showCard(card.getLayoutX(), card.getLayoutY());
            });
            matches.add(item);
          }
        }
      }
    }
    searchResults.setDisable(matches.isEmpty());
    searchResults.setText(query.isEmpty() ? "Matches" : matches.isEmpty() ? "No matches" :
        matches.size() == 1 ? "1 match" : matches.size() + " matches");
  }

  private double sideHeight(java.util.List<VnTopicMap.Branch> branches) {
    return branches.stream().mapToDouble(branch -> branchHeight(branch) + 24).sum();
  }

  private double branchHeight(VnTopicMap.Branch branch) {
    return Math.max(TOPIC_HEIGHT, branch.lessons().size() * ROW_STEP);
  }

  private void buildSide(java.util.List<VnTopicMap.Branch> branches, boolean left, double height) {
    double top = (height - sideHeight(branches)) / 2;
    double branchX = left ? 356 : 944;
    double topicX = left ? 34 : 1248;
    for (var branch : branches) {
      double branchY = top + (branchHeight(branch) - ROW_STEP) / 2 + TOPIC_HEIGHT / 2;
      Color color = branch.color().isBlank() ? theme.getAccentColor() : Color.web(branch.color());
      connect(left ? 658 : 862, height / 2, left ? branchX + 220 : branchX, branchY, color, 3.5);

      for (int i = 0; i < branch.lessons().size(); i++) {
        var lesson = branch.lessons().get(i);
        double topicY = top + i * ROW_STEP;
        connect(left ? branchX : branchX + 220, branchY,
            left ? topicX + TOPIC_WIDTH : topicX, topicY + TOPIC_HEIGHT / 2, color, 1.8);
        Button button = new Button(lesson.title(), graphic(lesson.icon(), 26));
        button.setWrapText(true);
        button.getStyleClass().add("topic-map-lesson");
        button.setOnAction(event -> select(branch.target(), lesson.target()));
        button.focusedProperty().addListener((obs, before, focused) -> {
          if (focused && !button.isHover()) showCard(topicX, topicY);
        });
        topicButtons.put(lesson, button);
        place(button, topicX, topicY, TOPIC_WIDTH, TOPIC_HEIGHT);
      }

      Button category = new Button(branch.title(), graphic(branch.icon(), 32));
      category.setWrapText(true);
      category.getStyleClass().add("topic-map-branch");
      category.setOnAction(event -> centerOnSide(left, branchY));
      MenuItem focus = new MenuItem(branch.title(), graphic(branch.icon(), 20));
      focus.setOnAction(event -> centerOnSide(left, branchY));
      branchPicker.getItems().add(focus);
      place(category, branchX, branchY - 28, 220, 56);
      top += branchHeight(branch) + 24;
    }
  }

  private void connect(double x, double y, double endX, double endY, Color color, double width) {
    double mid = (x + endX) / 2;
    CubicCurve edge = new CubicCurve(x, y, mid, y, mid, endY, endX, endY);
    edge.setStroke(color);
    edge.setStrokeWidth(width);
    edge.setFill(null);
    edge.setMouseTransparent(true);
    graph.getChildren().add(0, edge);
  }

  private void place(javafx.scene.layout.Region node, double x, double y, double width, double height) {
    node.setLayoutX(x);
    node.setLayoutY(y);
    node.setPrefSize(width, height);
    graph.getChildren().add(node);
  }

  private Node graphic(String path, int size) {
    if (path == null || path.isBlank()) return null;
    Image image = images.computeIfAbsent(path, key -> {
      try (var stream = VnTopicMap.assets(project).open(AssetType.IMAGE, key)) {
        return new Image(stream, 192, 192, true, true);
      } catch (IOException ex) {
        return null;
      }
    });
    if (image == null || image.isError()) return null;
    ImageView view = new ImageView(image);
    view.setPreserveRatio(true);
    view.setFitWidth(size);
    view.setFitHeight(size);
    view.setMouseTransparent(true);
    return view;
  }

  private void fit() {
    double fitScale = Math.min(scroll.getViewportBounds().getWidth() / MAP_WIDTH,
        scroll.getViewportBounds().getHeight() / graph.getPrefHeight());
    if (!(fitScale > 0)) return;
    setZoom(Math.min(1, fitScale));
    scroll.setHvalue(0.5);
    scroll.setVvalue(0.5);
  }

  private void setZoom(double scale) {
    double value = Math.clamp(scale, MIN_ZOOM, MAX_ZOOM);
    zoom.setX(value);
    zoom.setY(value);
    scroll.layout();
    zoomLabel.setText(Math.round(value * 100) + "%");
  }

  private void zoomCentered(double scale) {
    var area = scroll.getViewportBounds();
    zoomAround(scale, area.getWidth() / 2, area.getHeight() / 2);
  }

  private void zoomAtPointer(double scale, double sceneX, double sceneY) {
    Node viewport = scroll.lookup(".viewport");
    if (viewport == null) return;
    var pivot = viewport.sceneToLocal(sceneX, sceneY);
    zoomAround(scale, pivot.getX(), pivot.getY());
  }

  private void zoomAround(double scale, double x, double y) {
    var area = scroll.getViewportBounds();
    double previous = zoom.getX();
    double worldX = (scroll.getHvalue() * overflow(MAP_WIDTH, area.getWidth()) + x) / previous;
    double worldY = (scroll.getVvalue() * overflow(graph.getPrefHeight(), area.getHeight()) + y) / previous;
    setZoom(scale);
    scrollTo(worldX * zoom.getX() - x, worldY * zoom.getX() - y);
  }

  private void centerOnSide(boolean left, double y) {
    var area = scroll.getViewportBounds();
    setZoom(Math.max(zoom.getX(), Math.min(1, area.getWidth() / 570)));
    scrollTo((left ? 305 : MAP_WIDTH - 305) * zoom.getX() - area.getWidth() / 2,
        y * zoom.getX() - area.getHeight() / 2);
  }

  private void showCard(double x, double y) {
    var area = scroll.getViewportBounds();
    double scale = Math.max(0.90, zoom.getX());
    setZoom(scale);
    scrollTo((x + TOPIC_WIDTH / 2) * scale - area.getWidth() / 2,
        (y + TOPIC_HEIGHT / 2) * scale - area.getHeight() / 2);
  }

  private double overflow(double dimension, double viewport) {
    return Math.max(0, dimension * zoom.getX() - viewport);
  }

  private void scrollTo(double x, double y) {
    var area = scroll.getViewportBounds();
    double width = overflow(MAP_WIDTH, area.getWidth());
    double height = overflow(graph.getPrefHeight(), area.getHeight());
    scroll.setHvalue(width > 0 ? Math.clamp(x / width, 0, 1) : 0.5);
    scroll.setVvalue(height > 0 ? Math.clamp(y / height, 0, 1) : 0.5);
  }

  private void applyMenuStyle(MenuTheme theme) {
    String family = ProjectFontResolver.resolve(project, theme.getItemFont().getFamily(),
        null, 14, "SansSerif").getFamily();
    title.setFont(ProjectFontResolver.resolve(project, theme.interfaceFontFamily(theme.getTitleFontFamily()),
        null, 26, family));
    String colors = "-fx-background-color: " + cssColor(theme.getBackgroundColor()) + ";"
        + "-map-ink: " + cssColor(theme.getItemColor()) + ";"
        + "-map-title: " + cssColor(theme.getTitleColor()) + ";"
        + "-map-hint: " + cssColor(theme.getHintColor()) + ";"
        + "-map-selected: " + cssColor(theme.getItemSelectedColor()) + ";";
    String surfaces;
    if (theme.usesAeroGlass()) {
      surfaces = AeroGlass.BUTTON.cssPalette("-map-button") + AeroGlass.FOCUSED.cssPalette("-map-focus")
          + AeroGlass.TRAY.cssPalette("-map-tray");
    } else {
      String base = cssColor(theme.getBackgroundColor());
      String accent = cssColor(theme.getAccentColor());
      surfaces = "-map-button-fill: " + base + "; -map-button-rim: -map-hint;"
          + "-map-focus-fill: " + base + "; -map-focus-rim: " + accent + ";"
          + "-map-tray-fill: " + base + "; -map-tray-rim: -map-hint;";
    }
    board.setStyle(colors + surfaces + "-fx-font-family: \"" + family.replace("\"", "") + "\";");
  }

  private static String cssColor(Color color) {
    return color.toString().replace("0x", "#");
  }

  private void toggleFullScreen() {
    if (getScene() != null && getScene().getWindow() instanceof Stage stage) {
      stage.setFullScreen(!stage.isFullScreen());
    }
  }
}
