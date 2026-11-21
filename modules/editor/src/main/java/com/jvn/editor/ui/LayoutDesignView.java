package com.jvn.editor.ui;

import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.vn.ui.VnUiLayoutLoader;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import javafx.util.Duration;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.jvn.editor.ui.LayoutStudioWindowManager.Kind;

/** Visual authoring and source share the same editor document and undo history. */
final class LayoutDesignView extends BorderPane implements AutoCloseable {
  private static final Color GUIDE_COLOR = Color.web("#84c7fa");
  private static final List<String> ACTIONS = List.of("noop", "open_menu", "new_game", "load_menu",
      "save_menu", "settings_menu", "history", "back", "main_menu", "quit", "run_script",
      "toggle_skip", "toggle_auto", "gallery", "music_room");
  private final Kind kind;
  private final LayoutRuntimePreview preview;
  private final Supplier<String> source;
  private final Consumer<String> replaceSource;
  private final Consumer<String> status;
  private final Supplier<Map<String, String>> openDocuments;
  private final Canvas overlay = new Canvas();
  private final Group picture;
  private final Scale zoom = new Scale(1, 1, 0, 0);
  private final ScrollPane viewport = new ScrollPane();
  private final Pane desktop = new Pane();
  private final ListView<String> outline = new ListView<>();
  private final VBox inspector = new VBox(8);
  private final ComboBox<String> scene = new ComboBox<>();
  private final ComboBox<String> magnification = new ComboBox<>();
  private final CheckBox guides = new CheckBox("Guides");
  private final CheckBox focusState = new CheckBox("Show focus");
  private final Label resolution = new Label();
  private final Label error = new Label();
  private final PauseTransition refreshTimer = new PauseTransition(Duration.millis(65));
  private final PauseTransition dragTimer = new PauseTransition(Duration.millis(24));
  private @Nullable String selected;
  private String rendered = "";
  private @Nullable Drag drag;
  private @Nullable Rectangle2D draggedBounds;
  private boolean rebuilding;
  private boolean valid;
  private @Nullable File projectRoot;

  private record Drag(Point2D pointer, LayoutRuntimePreview.Element element, boolean resize, String source) {}

  LayoutDesignView(File file, @Nullable File root, Kind kind, Supplier<String> source, Consumer<String> replaceSource,
                   Runnable undo, Runnable redo, Consumer<String> status, Supplier<Map<String, String>> openDocuments) {
    this.kind = kind;
    this.source = source;
    this.replaceSource = replaceSource;
    this.status = status;
    this.openDocuments = openDocuments;
    preview = new LayoutRuntimePreview(file, root, kind);
    projectRoot = root;
    getStyleClass().add("layout-design");
    picture = new Group(preview.frame, overlay);
    picture.setManaged(false);
    picture.getTransforms().add(zoom);
    desktop.getChildren().add(picture);
    desktop.getStyleClass().add("layout-design-desktop");
    viewport.setContent(desktop);
    viewport.setId("layout-design-viewport");
    viewport.getStyleClass().add("layout-design-viewport");
    viewport.viewportBoundsProperty().addListener((obs, old, bounds) -> sizeCanvas());
    setCenter(viewport);
    setTop(buildTools(undo, redo));
    setLeft(buildOutline());
    ScrollPane properties = new ScrollPane(inspector);
    properties.setFitToWidth(true);
    properties.setPrefWidth(270);
    properties.setMinWidth(210);
    properties.getStyleClass().add("layout-design-properties");
    inspector.setPadding(new Insets(12));
    setRight(properties);
    setBottom(error);
    error.getStyleClass().add("layout-design-error");
    error.setWrapText(true);
    error.managedProperty().bind(error.visibleProperty());
    error.setVisible(false);
    refreshTimer.setOnFinished(e -> refresh());
    dragTimer.setOnFinished(e -> renderDraft());
    bindCanvas();
  }

  private Node buildTools(Runnable undo, Runnable redo) {
    scene.setId("layout-preview-scene");
    if (kind == Kind.DIALOGUE_LAYOUT) {
      scene.getItems().setAll("Dialogue", "Narration", "Choices", "NVL", "Bubble");
      scene.setValue("Dialogue");
    }
    scene.setOnAction(e -> { if (!rebuilding) refresh(); });
    magnification.setId("layout-preview-zoom");
    magnification.getItems().setAll("Fit", "50%", "100%", "200%");
    magnification.setValue("Fit");
    magnification.setOnAction(e -> sizeCanvas());
    guides.setSelected(true);
    guides.selectedProperty().addListener((obs, old, value) -> paintGuides());
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox bar = new HBox(8, icon("Undo", CssIcon.undo(), undo), icon("Redo", CssIcon.redo(), redo),
        new Separator(javafx.geometry.Orientation.VERTICAL), scene, spacer, resolution, magnification, guides);
    if (kind == Kind.MENU_SCREEN) {
      scene.setManaged(false);
      scene.setVisible(false);
    }
    if (kind == Kind.MENU_SCREEN) {
      bar.getChildren().add(5, focusState);
      focusState.selectedProperty().addListener((obs, old, value) -> refresh());
    } else if (kind == Kind.DIALOGUE_LAYOUT) {
      MenuButton backdrop = new MenuButton("Preview background");
      backdrop.getItems().addAll(entry("Choose image…", () -> {
        String path = chooseAsset(true);
        if (path != null) {
          preview.setBackdrop(path);
          refresh();
        }
      }), entry("Clear image", () -> {
        preview.setBackdrop(null);
        refresh();
      }));
      bar.getChildren().add(4, backdrop);
    }
    bar.setAlignment(Pos.CENTER_LEFT);
    bar.getStyleClass().add("layout-design-toolbar");
    bar.setPadding(new Insets(7, 12, 7, 12));
    return bar;
  }

  private Node buildOutline() {
    outline.setId("layout-design-outline");
    outline.setCellFactory(view -> new ListCell<>() {
      @Override protected void updateItem(String id, boolean empty) {
        super.updateItem(id, empty);
        MenuItemSpec item = empty ? null : menuItem(id);
        setText(empty ? null : switch (id) {
          case "@title" -> "Title";
          case "@footer" -> "Footer";
          case "@list" -> "Automatic rows";
          default -> item == null || item.label() == null ? id : item.label();
        });
      }
    });
    outline.getSelectionModel().selectedItemProperty().addListener((obs, before, after) -> {
      if (!rebuilding) select(after);
    });
    VBox side = new VBox(8, heading(kind == Kind.MENU_SCREEN ? "Menu items" : "Elements"), outline);
    side.setPrefWidth(192);
    side.setMinWidth(164);
    side.setPadding(new Insets(12, 8, 12, 8));
    side.getStyleClass().add("layout-design-outline");
    VBox.setVgrow(outline, Priority.ALWAYS);
    if (kind == Kind.MENU_SCREEN || kind == Kind.DIALOGUE_LAYOUT) {
      Button duplicate = icon("Duplicate selected item", CssIcon.copy(), this::duplicate);
      Button up = icon("Move item up", CssIcon.arrowUp(), () -> reorder(-1));
      Button down = icon("Move item down", CssIcon.arrowDown(), () -> reorder(1));
      Button remove = icon("Remove selected element", CssIcon.delete(), this::remove);
      remove.disableProperty().bind(outline.getSelectionModel().selectedItemProperty().isNull());
      MenuButton add = new MenuButton("Add", CssIcon.plus());
      add.setId("layout-add-element");
      if (kind == Kind.MENU_SCREEN) {
        add.getItems().addAll(entry("Button", () -> addItem("Button", null)),
            entry("Heading", () -> addItem("Heading", "section")), entry("Text", () -> addItem("Text", "body")),
            entry("Note", () -> addItem("Note", "note")));
        for (Button button : List.of(duplicate, up, down)) {
          button.disableProperty().bind(outline.getSelectionModel().selectedItemProperty().isNull());
        }
        side.getChildren().addAll(new HBox(5, add, duplicate, remove), new HBox(5, up, down));
      } else {
        add.getItems().add(entry("Dialogue button", this::addDialogueButton));
        side.getChildren().add(new HBox(5, add, remove));
      }
    }
    return side;
  }

  private static Button icon(String label, Node icon, Runnable run) {
    Button button = new Button("", icon);
    button.setAccessibleText(label);
    button.setTooltip(new Tooltip(label));
    button.setOnAction(e -> run.run());
    return button;
  }

  private static MenuItem entry(String label, Runnable action) {
    MenuItem item = new MenuItem(label);
    item.setOnAction(e -> action.run());
    return item;
  }

  void sourceChanged() { refreshTimer.playFromStart(); }

  void commitInspectorField() {
    if (getScene() == null) return;
    Node focus = getScene().getFocusOwner();
    while (focus != null && focus != inspector) {
      if (focus instanceof TextField text && text.getId() != null && text.getId().startsWith("layout-prop-")) {
        text.fireEvent(new javafx.event.ActionEvent());
        return;
      }
      if (focus instanceof ComboBox<?> combo && combo.isEditable() && combo.getId() != null && combo.getId().startsWith("layout-prop-")) {
        combo.commitValue();
        return;
      }
      focus = focus.getParent();
    }
  }

  void setRoot(@Nullable File root) {
    projectRoot = root;
    preview.setRoot(root);
    refresh();
  }

  void refresh() {
    refreshTimer.stop();
    if (drag != null) return;
    if (!render(source.get())) return;
    rebuilding = true;
    try {
      if (kind == Kind.MENU_LAYOUT || kind == Kind.MENU_STYLE) {
        scene.getItems().setAll(preview.screens);
        scene.setValue(preview.screenId);
      }
      List<String> ids = switch (kind) {
        case MENU_SCREEN -> preview.screen == null ? List.of() : menuIds();
        case MENU_LAYOUT -> preview.elements.keySet().stream().filter(id -> id.startsWith("@")).toList();
        default -> new ArrayList<>(preview.elements.keySet());
      };
      if (!ids.contains(selected)) selected = null;
      outline.getItems().setAll(ids);
      outline.getSelectionModel().select(selected);
    } finally {
      rebuilding = false;
    }
    buildInspector();
    sizeCanvas();
  }

  private boolean render(String text) {
    try {
      preview.render(text, scene.getValue(), focusState.isSelected() ? selected : null, openDocuments.get());
      rendered = text;
      error.setVisible(false);
      valid = true;
      return true;
    } catch (IOException | RuntimeException ex) {
      error.setText("Preview could not render. " + ex.getMessage());
      error.setVisible(true);
      valid = false;
      paintGuides();
      return false;
    }
  }

  private @Nullable MenuItemSpec menuItem(@Nullable String id) {
    if (kind != Kind.MENU_SCREEN || preview.screen == null) return null;
    return preview.screen.items().stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
  }

  private List<String> menuIds() {
    return preview.screen.items().stream().map(MenuItemSpec::id).collect(Collectors.toCollection(ArrayList::new));
  }

  private void select(@Nullable String id) {
    if (Objects.equals(selected, id)) return;
    selected = id;
    rebuilding = true;
    try { outline.getSelectionModel().select(id); }
    finally { rebuilding = false; }
    if (focusState.isSelected()) render(source.get());
    buildInspector();
    paintGuides();
  }

  private void sizeCanvas() {
    double width = preview.frame.getWidth(), height = preview.frame.getHeight();
    if (width <= 0 || height <= 0) return;
    overlay.setWidth(width);
    overlay.setHeight(height);
    double visibleW = viewport.getViewportBounds().getWidth();
    double visibleH = viewport.getViewportBounds().getHeight();
    double scale = switch (magnification.getValue()) {
      case "50%" -> .5;
      case "100%" -> 1;
      case "200%" -> 2;
      default -> Math.max(.01, Math.min((visibleW - 28) / width, (visibleH - 28) / height));
    };
    zoom.setX(scale);
    zoom.setY(scale);
    double paneW = Math.max(visibleW, width * scale + 28);
    double paneH = Math.max(visibleH, height * scale + 28);
    desktop.setPrefSize(paneW, paneH);
    picture.setLayoutX(Math.floor((paneW - width * scale) / 2));
    picture.setLayoutY(Math.floor((paneH - height * scale) / 2));
    resolution.setText((int) width + " × " + (int) height);
    paintGuides();
  }

  private void paintGuides() {
    var gc = overlay.getGraphicsContext2D();
    gc.clearRect(0, 0, overlay.getWidth(), overlay.getHeight());
    if (!valid || !guides.isSelected()) return;
    double scale = zoom.getX();
    var current = preview.elements.get(selected);
    if (current == null) return;
    Rectangle2D r = current.bounds();
    gc.setStroke(Color.BLACK);
    gc.setLineWidth(3 / scale);
    gc.strokeRect(r.getMinX(), r.getMinY(), r.getWidth(), r.getHeight());
    gc.setStroke(GUIDE_COLOR);
    gc.setLineWidth(1 / scale);
    gc.strokeRect(r.getMinX(), r.getMinY(), r.getWidth(), r.getHeight());
    if (!resizable(selected)) return;
    double handle = 8 / scale;
    gc.setFill(GUIDE_COLOR);
    gc.fillRect(r.getMaxX() - handle, r.getMaxY() - handle, handle, handle);
  }

  private boolean movable(@Nullable String key) {
    if (kind == Kind.MENU_LAYOUT) return "@title".equals(key) || "@footer".equals(key) || "@list".equals(key);
    return kind == Kind.MENU_SCREEN || (kind == Kind.DIALOGUE_LAYOUT && !"Bubble".equals(key));
  }

  private boolean resizable(@Nullable String key) {
    return movable(key) && kind != Kind.MENU_LAYOUT;
  }

  private boolean atHandle(Rectangle2D rect, double x, double y) {
    return Math.abs(rect.getMaxX() - x) < 10 / zoom.getX() && Math.abs(rect.getMaxY() - y) < 10 / zoom.getY();
  }

  private void bindCanvas() {
    overlay.setFocusTraversable(true);
    overlay.setOnMouseMoved(e -> {
      var item = preview.elements.get(selected);
      Cursor cursor = Cursor.DEFAULT;
      if (valid && guides.isSelected() && item != null && movable(selected)) {
        if (resizable(selected) && atHandle(item.bounds(), e.getX(), e.getY())) cursor = Cursor.SE_RESIZE;
        else if (item.bounds().contains(e.getX(), e.getY())) cursor = Cursor.MOVE;
      }
      overlay.setCursor(cursor);
    });
    overlay.setOnMousePressed(e -> {
      if (e.getButton() != MouseButton.PRIMARY || !valid || !guides.isSelected()) return;
      overlay.requestFocus();
      if (!rendered.equals(source.get())) refresh();
      double x = e.getX(), y = e.getY();
      var current = preview.elements.get(selected);
      boolean resize = current != null && resizable(selected) && atHandle(current.bounds(), x, y);
      if (!resize) {
        String hit = null;
        for (var item : preview.elements.entrySet()) {
          if (outline.getItems().contains(item.getKey()) && item.getValue().bounds().contains(x, y)) hit = item.getKey();
        }
        select(hit);
      }
      if (selected != null && movable(selected)) {
        refreshTimer.stop();
        drag = new Drag(new Point2D(x, y), preview.elements.get(selected), resize, source.get());
        draggedBounds = null;
      }
      e.consume();
    });
    overlay.setOnMouseDragged(e -> {
      if (drag == null) return;
      double dx = e.getX() - drag.pointer().getX(), dy = e.getY() - drag.pointer().getY();
      draggedBounds = transform(drag.element().bounds(), dx, dy, drag.resize());
      if (dragTimer.getStatus() != javafx.animation.Animation.Status.RUNNING) dragTimer.playFromStart();
      e.consume();
    });
    overlay.setOnMouseReleased(e -> {
      if (drag == null) return;
      dragTimer.stop();
      Drag previous = drag;
      drag = null;
      if (draggedBounds != null && previous.source().equals(source.get())) {
        apply(geometry(selected, draggedBounds, previous.element().space(), previous.resize()));
      } else refresh();
      draggedBounds = null;
      e.consume();
    });
    overlay.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ESCAPE && drag != null) {
        dragTimer.stop();
        drag = null;
        draggedBounds = null;
        refresh();
        e.consume();
      } else if (e.getCode() == KeyCode.DELETE && selected != null) {
        remove();
        e.consume();
      } else if (selected != null && movable(selected) && preview.elements.containsKey(selected)) {
        double step = e.isShiftDown() ? 10 : 1;
        double x = e.getCode() == KeyCode.LEFT ? -step : e.getCode() == KeyCode.RIGHT ? step : 0;
        double y = e.getCode() == KeyCode.UP ? -step : e.getCode() == KeyCode.DOWN ? step : 0;
        if (x == 0 && y == 0) return;
        var item = preview.elements.get(selected);
        apply(geometry(selected, transform(item.bounds(), x, y, false), item.space(), false));
        e.consume();
      }
    });
  }

  private void renderDraft() {
    if (drag == null || draggedBounds == null) return;
    render(LayoutPropertyDocument.edit(drag.source(),
        geometry(selected, draggedBounds, drag.element().space(), drag.resize())));
    paintGuides();
  }

  private static Rectangle2D transform(Rectangle2D r, double dx, double dy, boolean resize) {
    return resize
        ? new Rectangle2D(r.getMinX(), r.getMinY(), Math.max(8, r.getWidth() + dx), Math.max(8, r.getHeight() + dy))
        : new Rectangle2D(r.getMinX() + dx, r.getMinY() + dy, r.getWidth(), r.getHeight());
  }

  private Map<String, String> geometry(String id, Rectangle2D r, Rectangle2D space, boolean resize) {
    Map<String, String> edits = new LinkedHashMap<>();
    double x = r.getMinX() - space.getMinX(), y = r.getMinY() - space.getMinY();
    double w = r.getWidth(), h = r.getHeight();
    double fx = x / space.getWidth(), fy = y / space.getHeight();
    double fw = w / space.getWidth(), fh = h / space.getHeight();
    if (kind == Kind.MENU_SCREEN) {
      String prefix = "item." + id + ".";
      edits.put(prefix + "boundsX", decimal(clampUnit(fx)));
      edits.put(prefix + "boundsY", decimal(clampUnit(fy)));
      edits.put(prefix + "boundsWidth", decimal(clampUnit(fw)));
      edits.put(prefix + "boundsHeight", decimal(clampUnit(fh)));
    } else if (kind == Kind.MENU_LAYOUT) {
      if ("@title".equals(id)) {
        edits.put("titleX", decimal(clampUnit(fx + fw / 2)));
        edits.put("titleY", decimal(clampUnit(fy)));
      } else if ("@list".equals(id)) {
        edits.put("listXCenter", decimal(clampUnit(fx + fw / 2)));
        edits.put("listYStart", decimal(clampUnit(fy)));
      } else {
        edits.put("hintsX", decimal(clampUnit(fx + fw / 2)));
        edits.put("hintsBottomMargin", decimal(Math.max(0, space.getHeight() - y)));
      }
    } else if ("NVL panel".equals(id)) {
      edits.put("nvlX", decimal(clampUnit(fx)));
      edits.put("nvlY", decimal(clampUnit(fy)));
      if (resize) {
        edits.put("nvlWidth", decimal(clampUnit(fw)));
        edits.put("nvlHeight", decimal(clampUnit(fh)));
      }
    } else if ("Dialogue box".equals(id)) {
      edits.put("textBoxX", decimal(clampUnit(fx)));
      edits.put("textBoxY", decimal(clampUnit(fy)));
      if (resize) {
        edits.put("textBoxWidth", decimal(clampUnit(fw)));
        edits.put("textBoxHeight", decimal(clampUnit(fh)));
      }
    } else if ("Speaker name".equals(id)) {
      edits.put("nameBoxXOffset", decimal(x));
      edits.put("nameBoxYOffset", decimal(y));
      if (resize) {
        edits.put("nameBoxAutoWidth", "false");
        edits.put("nameBoxWidth", decimal(w));
        edits.put("nameBoxHeight", decimal(h));
      }
    } else if ("Choices".equals(id)) {
      edits.put("choiceXCenter", decimal(clampUnit(fx + fw / 2)));
      edits.put("choiceYStart", decimal(clampUnit(fy)));
      edits.put("choiceYAnchor", "0");
      if (resize) {
        edits.put("choiceWidthFactor", decimal(clampUnit(fw)));
        edits.put("choiceHeight", decimal(h));
      }
    } else if (id.startsWith("Button · ")) {
      String key = "textBoxButton." + id.substring("Button · ".length()) + ".";
      edits.put(key + "x", decimal(fx));
      edits.put(key + "y", decimal(fy));
      if (resize) {
        edits.put(key + "width", decimal(fw));
        edits.put(key + "height", decimal(fh));
      }
    }
    return edits;
  }

  private static String decimal(double n) {
    return java.math.BigDecimal.valueOf(n).setScale(7, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
  }

  private static double clampUnit(double value) { return Math.max(0, Math.min(1, value)); }

  private void apply(Map<String, String> edits) {
    if (!valid) return;
    try {
      String before = source.get();
      String after = LayoutPropertyDocument.edit(before, edits);
      if (!before.equals(after)) replaceSource.accept(after);
      refresh();
    } catch (IllegalArgumentException ex) {
      status.accept("Edit could not be applied. " + ex.getMessage());
    }
  }

  private void addItem(String label, @Nullable String role) {
    if (!valid || preview.screen == null) return;
    List<String> ids = menuIds();
    String id = uniqueId(label.toLowerCase(Locale.ROOT), ids);
    ids.add(id);
    Map<String, String> values = new LinkedHashMap<>();
    String prefix = "item." + id + ".";
    values.put("items", String.join(",", ids));
    values.put("itemsMode", "explicit");
    values.put(prefix + "label", label);
    values.put(prefix + "action", "noop");
    if (role != null) {
      values.put(prefix + "renderAs", role);
      values.put(prefix + "enabled", "false");
    }
    selected = id;
    apply(values);
  }

  private void addDialogueButton() {
    if (!valid) return;
    Properties props = LayoutPropertyDocument.read(source.get());
    List<String> ids = csv(props.getProperty("textBoxButton.ids", ""));
    if (ids.isEmpty()) {
      props.stringPropertyNames().stream().filter(k -> k.startsWith("textBoxButton."))
          .map(k -> k.split("\\.")).filter(parts -> parts.length >= 3).map(parts -> parts[1]).distinct().sorted().forEach(ids::add);
    }
    String id = uniqueId("button", ids);
    ids.add(id);
    scene.setValue("Dialogue");
    selected = "Button · " + id;
    String prefix = "textBoxButton." + id + ".";
    apply(Map.of("textBoxButton.ids", String.join(",", ids), prefix + "label", "Button",
        prefix + "action", "history", prefix + "space", "textbox",
        prefix + "x", decimal(Math.max(.02, .86 - (ids.size() - 1) * .13)), prefix + "y", "0.66",
        prefix + "width", "0.12", prefix + "height", "0.27"));
  }

  private void duplicate() {
    MenuItemSpec item = menuItem(selected);
    if (item == null) return;
    List<String> ids = menuIds();
    String id = uniqueId(selected + "_copy", ids);
    ids.add(ids.indexOf(selected) + 1, id);
    String from = "item." + selected + ".", to = "item." + id + ".";
    Properties props = LayoutPropertyDocument.read(source.get());
    Map<String, String> values = new LinkedHashMap<>();
    props.stringPropertyNames().stream().filter(k -> k.startsWith(from)).sorted()
        .forEach(k -> values.put(to + k.substring(from.length()), props.getProperty(k)));
    values.put("items", String.join(",", ids));
    values.put("itemsMode", "explicit");
    values.put(to + "label", item.label() == null ? id : item.label());
    values.put(to + "enabled", Boolean.toString(item.enabled()));
    values.put(to + "action", item.action().actionKey());
    if (item.action().target() != null) values.put(to + "target", item.action().target());
    if (item.styleId() != null) values.put(to + "style", item.styleId());
    var rectangle = preview.elements.get(selected);
    if (rectangle != null && item.boundsX() != null) {
      values.putAll(geometry(id, transform(rectangle.bounds(), 12, 12, false), rectangle.space(), false));
    }
    selected = id;
    apply(values);
  }

  private void remove() {
    if (selected == null || !valid) return;
    List<String> ids;
    String listKey;
    String prefix;
    String id;
    if (kind == Kind.MENU_SCREEN) {
      ids = menuIds();
      listKey = "items";
      id = selected;
      prefix = "item." + id + ".";
    } else if (kind == Kind.DIALOGUE_LAYOUT && selected.startsWith("Button · ")) {
      prefix = "textBoxButton." + selected.substring("Button · ".length()) + ".";
      listKey = "textBoxButton.ids";
      id = selected.substring("Button · ".length());
      ids = csv(LayoutPropertyDocument.read(source.get()).getProperty(listKey, ""));
    } else return;
    ids.remove(id);
    Map<String, String> values = new LinkedHashMap<>();
    values.put(listKey, String.join(",", ids));
    if (kind == Kind.MENU_SCREEN) values.put("itemsMode", "explicit");
    LayoutPropertyDocument.read(source.get()).stringPropertyNames().stream()
        .filter(key -> key.startsWith(prefix)).forEach(key -> values.put(key, null));
    selected = null;
    apply(values);
  }

  private void reorder(int offset) {
    if (kind != Kind.MENU_SCREEN || selected == null) return;
    List<String> ids = menuIds();
    int current = ids.indexOf(selected);
    int next = current + offset;
    if (current < 0 || next < 0 || next >= ids.size()) return;
    Collections.swap(ids, current, next);
    apply(Map.of("items", String.join(",", ids), "itemsMode", "explicit"));
  }

  private static String uniqueId(String base, List<String> ids) {
    String id = base;
    for (int i = 2; ids.contains(id); i++) id = base + "_" + i;
    return id;
  }

  private static List<String> csv(String value) {
    return Arrays.stream(value.split(",")).map(String::strip).filter(s -> !s.isEmpty())
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private void buildInspector() {
    for (Node row : inspector.getChildren()) {
      if (row instanceof VBox box) suppressCommit(box);
    }
    inspector.getChildren().clear();
    Properties props;
    try { props = LayoutPropertyDocument.read(source.get()); }
    catch (IllegalArgumentException ex) { return; }
    switch (kind) {
      case MENU_SCREEN -> {
        MenuItemSpec item = menuItem(selected);
        if (item == null) {
          var screen = preview.screen;
          if (screen == null) return;
          inspector.getChildren().add(heading("Menu"));
          field(props, "Title", "titleText", screen.titleText());
          field(props, "Subtitle", "subtitleText", screen.subtitleText());
          field(props, "Footer", "hintsText", screen.hintsText());
          choose(props, "Layout", "layout", screen.layoutId(), preview.profile.layoutIds().stream().sorted().toList());
          choose(props, "Style", "defaultItemStyle", screen.defaultStyleId(), preview.profile.styleIds().stream().sorted().toList());
          field(props, "Background asset", "backgroundAsset", screen.backgroundAsset());
        } else {
          String prefix = "item." + item.id() + ".";
          Label itemTitle = heading(item.label() == null ? item.id() : item.label());
          itemTitle.setTooltip(new Tooltip("Item ID " + item.id()));
          inspector.getChildren().add(itemTitle);
          field(props, "Label", prefix + "label", item.label());
          choose(props, "Action", prefix + "action", item.action().actionKey(), ACTIONS);
          List<String> targets = "open_menu".equals(item.action().actionKey())
              ? preview.profile.screenIds().stream().sorted().toList() : List.of();
          if (targets.isEmpty()) field(props, "Target", prefix + "target", item.action().target());
          else choose(props, "Target", prefix + "target", item.action().target(), targets);
          toggle("Enabled", prefix + "enabled", item.enabled());
          choose(props, "Render as", prefix + "renderAs", item.extras().getOrDefault("renderAs", item.extras().getOrDefault("role", "button")), List.of("button", "section", "body", "note"));
          choose(props, "Style", prefix + "style", item.styleId(), preview.profile.styleIds().stream().sorted().toList());
          field(props, "Icon", prefix + "icon", item.iconPath());
          field(props, "Button image", prefix + "buttonAsset", item.buttonAssetPath());
          field(props, "Selected image", prefix + "buttonSelectedAsset", item.buttonSelectedAssetPath());
          boundsFields(item, prefix, props);
        }
      }
      case MENU_LAYOUT -> {
        var layout = preview.profile.layout(preview.id);
        inspector.getChildren().add(heading("Menu layout"));
        field(props, "Title X", "titleX", layout.titleX());
        field(props, "Title Y", "titleY", layout.titleY());
        choose(props, "Title alignment", "titleAlign", layout.titleAlign(), List.of("left", "center", "right"));
        field(props, "List start Y", "listYStart", layout.listYStart());
        field(props, "List center X", "listXCenter", layout.listXCenter());
        field(props, "Row height", "lineHeight", layout.lineHeight());
        field(props, "List width", "listWidthFactor", layout.listWidthFactor());
        choose(props, "Text alignment", "textAlign", layout.textAlign(), List.of("left", "center", "right"));
        field(props, "Visible rows", "maxVisibleItems", layout.maxVisibleItems());
        field(props, "Footer X", "hintsX", layout.hintsX());
        choose(props, "Footer alignment", "hintsAlign", layout.hintsAlign(), List.of("left", "center", "right"));
        field(props, "Footer margin", "hintsBottomMargin", layout.hintsBottomMargin());
      }
      case MENU_STYLE -> {
        var style = preview.profile.style(preview.id);
        inspector.getChildren().add(heading("Menu style"));
        field(props, "Background color", "backgroundColor", style.backgroundColor());
        field(props, "Background asset", "backgroundAsset", style.backgroundAssetPath());
        field(props, "Background opacity", "backgroundOpacity", style.backgroundOpacity());
        field(props, "Title color", "titleColor", style.titleColor());
        field(props, "Title font", "titleFontFamily", style.titleFontFamily());
        field(props, "Title size", "titleFontSize", style.titleFontSize());
        field(props, "Item color", "itemColor", style.itemColor());
        field(props, "Selected color", "itemSelectedColor", style.itemSelectedColor());
        field(props, "Disabled color", "itemDisabledColor", style.itemDisabledColor());
        field(props, "Item font", "itemFontFamily", style.itemFontFamily());
        field(props, "Item size", "itemFontSize", style.itemFontSize());
        field(props, "Button image", "buttonAsset", style.buttonAssetPath());
      }
      case DIALOGUE_LAYOUT -> dialogueInspector(props);
    }
  }

  private void boundsFields(MenuItemSpec item, String prefix, Properties props) {
    inspector.getChildren().add(new Separator());
    inspector.getChildren().add(heading("Bounds"));
    var element = preview.elements.get(item.id());
    if (element != null && (item.boundsX() == null || item.boundsY() == null
        || item.boundsWidth() == null || item.boundsHeight() == null)) {
      Button position = new Button("Set explicit bounds");
      position.setOnAction(e -> apply(geometry(item.id(), element.bounds(), element.space(), true)));
      inspector.getChildren().add(position);
      return;
    }
    field(props, "X", prefix + "boundsX", item.boundsX());
    field(props, "Y", prefix + "boundsY", item.boundsY());
    field(props, "Width", prefix + "boundsWidth", item.boundsWidth());
    field(props, "Height", prefix + "boundsHeight", item.boundsHeight());
    Button reset = new Button("Reset to layout");
    reset.setOnAction(e -> {
      Map<String, String> values = new LinkedHashMap<>();
      for (String key : List.of("boundsX", "boundsY", "boundsWidth", "boundsHeight")) values.put(prefix + key, null);
      apply(values);
    });
    inspector.getChildren().add(reset);
  }

  private void dialogueInspector(Properties props) {
    var resolved = VnUiLayoutLoader.parseWithDiagnostics(props, null, null);
    var layout = resolved.layout();
    var style = resolved.style();
    String element = selected == null ? switch (scene.getValue()) {
      case "Choices" -> "Choices";
      case "Bubble" -> "Bubble";
      case "NVL" -> "NVL panel";
      default -> "Dialogue box";
    } : selected;
    inspector.getChildren().add(heading(element));
    switch (element) {
      case "NVL panel" -> {
        field(props, "X", "nvlX", layout.nvlX());
        field(props, "Y", "nvlY", layout.nvlY());
        field(props, "Width", "nvlWidth", layout.nvlWidth());
        field(props, "Height", "nvlHeight", layout.nvlHeight());
        field(props, "Panel padding", "nvlPadding", layout.nvlPadding());
        field(props, "Speaker width", "nvlSpeakerWidth", layout.nvlSpeakerWidth());
        field(props, "Entry gap", "nvlEntryGap", layout.nvlEntryGap());
        field(props, "Panel color", "nvlPanelColor", style.nvlPanelColor());
        field(props, "Panel image", "nvlPanelAsset", style.nvlPanelAssetPath());
        field(props, "Text color", "nvlTextColor", style.nvlTextColor());
      }
      case "Bubble" -> {
        field(props, "Width", "bubbleWidthFactor", layout.bubbleWidthFactor());
        field(props, "Minimum height", "bubbleMinHeight", layout.bubbleMinHeight());
        field(props, "Text padding", "bubbleTextPadding", layout.bubbleTextPadding());
        field(props, "Y offset from speaker", "bubbleYOffset", layout.bubbleYOffset());
        field(props, "Tail size", "bubbleTailSize", layout.bubbleTailSize());
        field(props, "Bubble color", "bubbleColor", style.bubbleColor());
        field(props, "Border color", "bubbleBorderColor", style.bubbleBorderColor());
        field(props, "Bubble image", "bubbleAsset", style.bubbleAssetPath());
        field(props, "Text color", "bubbleTextColor", style.bubbleTextColor());
      }
      case "Speaker name" -> {
        field(props, "X offset", "nameBoxXOffset", layout.nameBoxXOffset());
        field(props, "Y offset", "nameBoxYOffset", layout.nameBoxYOffset());
        field(props, "Width", "nameBoxWidth", layout.nameBoxWidth());
        field(props, "Height", "nameBoxHeight", layout.nameBoxHeight());
        toggle("Fit width to name", "nameBoxAutoWidth", layout.nameBoxAutoWidth());
        field(props, "Name color", "nameTextColor", style.nameTextColor());
        field(props, "Name font", "nameTextFontFamily", style.nameTextFontFamily());
        field(props, "Name size", "nameTextFontSize", style.nameTextFontSize());
      }
      case "Choices" -> {
        field(props, "Center X", "choiceXCenter", layout.choiceXCenter());
        field(props, "Start Y", "choiceYStart", layout.choiceYStart());
        field(props, "Width", "choiceWidthFactor", layout.choiceWidthFactor());
        field(props, "Row height", "choiceHeight", layout.choiceHeight());
        field(props, "Gap", "choiceGap", layout.choiceGap());
        field(props, "Button image", "choiceButtonAsset", style.choiceButtonAssetPath());
        field(props, "Choice color", "choiceTextColor", style.choiceTextColor());
      }
      default -> {
        if (selected != null && selected.startsWith("Button · ")) {
          String key = "textBoxButton." + selected.substring("Button · ".length()) + ".";
          field(props, "Label", key + "label", null);
          choose(props, "Action", key + "action", null, ACTIONS);
          field(props, "Target", key + "target", null);
          choose(props, "Coordinate space", key + "space", null, List.of("textbox", "viewport"));
          for (String property : List.of("x", "y", "width", "height")) field(props, property, key + property, null);
          field(props, "Button image", key + "asset", null);
        } else {
          field(props, "X", "textBoxX", layout.textBoxX());
          field(props, "Y", "textBoxY", layout.textBoxY());
          field(props, "Width", "textBoxWidth", layout.textBoxWidth());
          field(props, "Height", "textBoxHeight", layout.textBoxHeight());
          field(props, "Box color", "textBoxColor", style.textBoxColor());
          field(props, "Box opacity", "textBoxOpacity", style.textBoxOpacity());
          field(props, "Box image", "textBoxAsset", style.textBoxAssetPath());
          field(props, "Text color", "dialogueTextColor", style.dialogueTextColor());
          field(props, "Text font", "dialogueTextFontFamily", style.dialogueTextFontFamily());
          field(props, "Text size", "dialogueTextFontSize", style.dialogueTextFontSize());
        }
      }
    }
  }

  private static Label heading(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("layout-design-heading");
    return label;
  }

  private void field(Properties props, String label, String key, @Nullable Object effective) {
    String value = value(props, key, effective);
    TextField input = new TextField(value);
    input.setId("layout-prop-" + key);
    input.setMaxWidth(Double.MAX_VALUE);
    Runnable commit = () -> {
      if (!Objects.equals(input.getText(), value)) {
        input.setUserData(Boolean.TRUE);
        apply(Map.of(key, input.getText()));
      }
    };
    input.setOnAction(e -> commit.run());
    input.focusedProperty().addListener((obs, old, focused) -> {
      if (!focused && input.getScene() != null && input.getUserData() == null && valid) {
        // Keep a click on a neighboring control from losing its target during the refresh.
        String next = input.getText();
        if (!next.equals(value)) {
          String before = source.get();
          try { replaceSource.accept(LayoutPropertyDocument.edit(before, Map.of(key, next))); }
          catch (IllegalArgumentException ex) { status.accept("Edit could not be applied. " + ex.getMessage()); }
          sourceChanged();
        }
      }
    });
    Node control = input;
    String lower = key.toLowerCase(Locale.ROOT);
    if (lower.endsWith("asset") || lower.endsWith(".icon")) {
      Button browse = icon("Choose project asset", CssIcon.folder("#d6ba7a"), () -> browse(key));
      HBox row = new HBox(5, input, browse);
      HBox.setHgrow(input, Priority.ALWAYS);
      control = row;
    } else if (lower.endsWith("color")) {
      ColorPicker picker = new ColorPicker();
      try { picker.setValue(Color.web(value)); }
      catch (IllegalArgumentException ex) { picker.setValue(Color.TRANSPARENT); }
      picker.getStyleClass().add("layout-design-swatch");
      picker.setAccessibleText(label);
      picker.setOnAction(e -> apply(Map.of(key, "#" + picker.getValue().toString().substring(2))));
      HBox row = new HBox(5, input, picker);
      HBox.setHgrow(input, Priority.ALWAYS);
      control = row;
    }
    labeled(label, input, control);
  }

  private static void suppressCommit(Pane row) {
    for (Node child : row.getChildren()) {
      if (child instanceof TextField input) input.setUserData(Boolean.TRUE);
      else if (child instanceof Pane nested) suppressCommit(nested);
    }
  }

  private void browse(String property) {
    String path = chooseAsset(false);
    if (path != null) apply(Map.of(property, path));
  }

  private @Nullable String chooseAsset(boolean imagesOnly) {
    var chooser = new javafx.stage.FileChooser();
    chooser.setTitle("Choose Project Asset");
    if (projectRoot != null && projectRoot.isDirectory()) chooser.setInitialDirectory(projectRoot);
    AssetPickerSupport.addAssetFilters(chooser);
    if (imagesOnly) {
      var images = chooser.getExtensionFilters().get(1);
      chooser.getExtensionFilters().setAll(images);
    }
    File file = chooser.showOpenDialog(getScene().getWindow());
    if (file == null) return null;
    var path = file.toPath().toAbsolutePath().normalize();
    if (projectRoot != null) {
      var root = projectRoot.toPath().toAbsolutePath().normalize();
      if (path.startsWith(root)) path = root.relativize(path);
    }
    return path.toString().replace(File.separatorChar, '/');
  }

  private void choose(Properties props, String label, String key, @Nullable Object effective, List<String> values) {
    String initial = value(props, key, effective);
    ComboBox<String> input = new ComboBox<>();
    input.setId("layout-prop-" + key);
    input.getItems().setAll(values);
    input.setEditable(true);
    input.setValue(initial);
    input.setMaxWidth(Double.MAX_VALUE);
    input.setOnAction(e -> {
      String value = input.getValue();
      if (value != null && !value.equals(initial)) apply(Map.of(key, value));
    });
    labeled(label, input, input);
  }

  private void toggle(String label, String key, boolean effective) {
    CheckBox input = new CheckBox(label);
    input.setSelected(effective);
    input.setOnAction(e -> apply(Map.of(key, Boolean.toString(input.isSelected()))));
    inspector.getChildren().add(input);
  }

  private void labeled(String label, Control input, Node content) {
    Label caption = new Label(label);
    caption.setLabelFor(input);
    caption.getStyleClass().add("layout-design-caption");
    inspector.getChildren().add(new VBox(3, caption, content));
  }

  private static String value(Properties props, String key, @Nullable Object effective) {
    return props.getProperty(key, effective == null ? "" : effective.toString());
  }

  @Override public void close() {
    refreshTimer.stop();
    dragTimer.stop();
    preview.close();
  }
}
