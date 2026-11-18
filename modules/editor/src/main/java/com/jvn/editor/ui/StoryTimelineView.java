package com.jvn.editor.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;

import com.jvn.core.project.StoryMapPaths;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.script.MultipleParseErrorsException;
import com.jvn.core.vn.script.VnParseException;
import com.jvn.core.vn.script.VnScriptParser;

public class StoryTimelineView extends BorderPane {
  static final Pattern ARC_DSL_LINE = Pattern.compile(
      "^\\s*arc\\s+(?:\"((?:[^\"\\\\]|\\\\.)+)\"|(\\S+))"
          + "(?:\\s+script\\s+\"((?:[^\"\\\\]|\\\\.)*)\")?"
          + "(?:\\s+entry\\s+\"((?:[^\"\\\\]|\\\\.)*)\")?"
          + "(?:\\s+cluster\\s+\"((?:[^\"\\\\]|\\\\.)*)\")?"
          + "(?:\\s+priority\\s+(-?\\d+))?"
          + "(?:\\s+color\\s+\"((?:[^\"\\\\]|\\\\.)*)\")?"
          + "(?:\\s+tags\\s+\"((?:[^\"\\\\]|\\\\.)*)\")?"
          + "(?:\\s+at\\s+(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?))?"
          + "\\s*$",
      Pattern.CASE_INSENSITIVE
  );
  static final Pattern LINK_DSL_LINE = Pattern.compile("^\\s*link\\s+(.+?)\\s*->\\s*(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern SIMPLE_TOKEN = Pattern.compile("^[A-Za-z0-9_./-]+$");
  private static final Pattern LEGACY_ARC = Pattern.compile("^ARC\\|", Pattern.CASE_INSENSITIVE);
  private static final Pattern LEGACY_LINK = Pattern.compile("^LINK\\|", Pattern.CASE_INSENSITIVE);

  static final class ParsedTimeline {
    final List<Arc> arcs;
    final List<Link> links;

    ParsedTimeline(List<Arc> arcs, List<Link> links) {
      this.arcs = arcs;
      this.links = links;
    }
  }

  static final class LinkEndpoint {
    final String arc;
    final String label;
    final int arcStart;
    final int arcEnd;
    final int labelStart;
    final int labelEnd;

    LinkEndpoint(String arc, String label, int arcStart, int arcEnd, int labelStart, int labelEnd) {
      this.arc = arc;
      this.label = label;
      this.arcStart = arcStart;
      this.arcEnd = arcEnd;
      this.labelStart = labelStart;
      this.labelEnd = labelEnd;
    }
  }

  public static class Arc {
    public String name;
    public String script;
    public String entryLabel;
    public String cluster;
    public int priority;
    public String color;
    public String tags;
    public double x = 40;
    public double y = 40;
    public String toLine() {
      return "ARC|" + nn(name) + "|" + nn(script) + "|" + nn(entryLabel) + "|" + x + "|" + y
          + "|" + nn(cluster) + "|" + priority + "|" + nn(color) + "|" + nn(tags);
    }
  }

  private void validate() {
    StringBuilder sb = new StringBuilder();
    Set<String> arcNames = new LinkedHashSet<>();
    Set<String> dupNames = new LinkedHashSet<>();
    // Check arcs
    for (Arc a : arcs.getItems()) {
      if (a == null) continue;
      String arcName = nn(a.name).trim();
      if (arcName.isBlank()) {
        sb.append("Arc with empty name detected.\n");
      } else if (!arcNames.add(arcName.toLowerCase(Locale.ROOT))) {
        dupNames.add(arcName);
      }
      File f = resolveFile(a.script);
      if (f == null || !f.exists()) {
        sb.append("Missing script for arc '").append(a.name).append("': ").append(a.script).append("\n");
        continue;
      }
      if (a.entryLabel != null && !a.entryLabel.isBlank()) {
        sb.append(validateLabel(
            f,
            a.entryLabel,
            "Arc '" + a.name + "'",
            "missing entry label '" + a.entryLabel + "'"));
      }
      if (a.color != null && !a.color.isBlank()) {
        try {
          javafx.scene.paint.Color.web(a.color);
        } catch (Exception ex) {
          sb.append("Arc '").append(a.name).append("' has invalid color value '").append(a.color).append("'\n");
        }
      }
    }
    for (String dn : dupNames) {
      sb.append("Duplicate arc name detected: ").append(dn).append("\n");
    }
    // Check links
    for (Link l : links.getItems()) {
      if (l == null) continue;
      Arc ta = findArc(l.toArc);
      if (ta == null) { sb.append("Link to unknown arc: ").append(l.toArc).append("\n"); continue; }
      File f = resolveFile(ta.script);
      if (f == null || !f.exists()) { sb.append("Link target arc script missing: ").append(ta.script).append("\n"); continue; }
      String lab = (l.toLabel != null && !l.toLabel.isBlank()) ? l.toLabel : ta.entryLabel;
      if (lab != null && !lab.isBlank()) {
        sb.append(validateLabel(
            f,
            lab,
            "Link target " + l.toArc + ":" + lab,
            "label missing"));
      }
    }
    if (sb.length() == 0) {
      EditorDialogs.info(getScene() == null ? null : getScene().getWindow(), "Validate", "Story map OK");
    } else {
      EditorDialogs.showTextBlock(
          getScene() == null ? null : getScene().getWindow(),
          "Validation Issues",
          "Review the issues below before continuing.",
          sb.toString(),
          "Close");
    }
  }

  private String validateLabel(File vnsFile, String label, String context, String missingDetail) {
    if (vnsFile == null || label == null || label.isBlank()) return "";
    String sourceName = sourceNameForValidation(vnsFile);
    try (FileInputStream in = new FileInputStream(vnsFile)) {
      VnScriptParser parser = new VnScriptParser();
      VnScenario scenario = parser.parse(
          in,
          sourceName,
          includePath -> openVnsIncludeForValidation(vnsFile, sourceName, includePath));
      if (scenario.getLabelIndex(label) != null) return "";
      return context + " " + missingDetail + "\n";
    } catch (MultipleParseErrorsException ex) {
      return context + " could not be parsed while checking label '" + label + "':\n"
          + formatParseErrors(ex) + "\n";
    } catch (Exception ex) {
      String message = ex.getMessage();
      if (message == null || message.isBlank()) message = ex.getClass().getSimpleName();
      return context + " could not be parsed while checking label '" + label + "': "
          + message + "\n";
    }
  }

  private String formatParseErrors(MultipleParseErrorsException ex) {
    StringBuilder out = new StringBuilder();
    List<VnParseException> errors = ex.getErrors();
    if (errors == null || errors.isEmpty()) {
      return "- " + ex.getMessage();
    }
    int shown = 0;
    for (VnParseException error : errors) {
      if (error == null) continue;
      if (shown >= 8) {
        out.append("- ... ").append(errors.size() - shown).append(" more parse issue(s)\n");
        break;
      }
      String source = error.getSourceName();
      String detail = error.getDetailMessage();
      if (detail == null || detail.isBlank()) detail = error.getMessage();
      out.append("- ");
      if (source != null && !source.isBlank()) {
        out.append(source);
        if (error.getLineNumber() > 0) out.append(":").append(error.getLineNumber());
        out.append(": ");
      }
      out.append(detail == null || detail.isBlank() ? "Parse error" : detail).append("\n");
      shown++;
    }
    return out.toString();
  }

  private String sourceNameForValidation(File vnsFile) {
    if (vnsFile == null) return "<script>";
    try {
      Path file = vnsFile.toPath().toAbsolutePath().normalize();
      File rootFile = validationProjectRoot(vnsFile);
      if (rootFile != null) {
        Path root = rootFile.toPath().toAbsolutePath().normalize();
        Path scriptsRoot = root.resolve("scripts").normalize();
        if (file.startsWith(scriptsRoot)) {
          return scriptsRoot.relativize(file).toString().replace('\\', '/');
        }
        if (file.startsWith(root)) {
          return root.relativize(file).toString().replace('\\', '/');
        }
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
    return vnsFile.getName();
  }

  private InputStream openVnsIncludeForValidation(File sourceFile, String sourceName, String includePath) throws IOException {
    String normalized = includePath == null ? "" : includePath.trim().replace('\\', '/');
    if (normalized.isBlank()) {
      throw new IOException("Include path is empty");
    }

    File rootFile = validationProjectRoot(sourceFile);
    if (rootFile == null) {
      throw new IOException("Project root not available for include: " + includePath);
    }
    Path root = rootFile.toPath().toAbsolutePath().normalize();
    Path scriptsRoot = root.resolve("scripts").normalize();
    if (!Files.isDirectory(scriptsRoot)) {
      scriptsRoot = root;
    }

    List<Path> candidates = new ArrayList<>();
    if (normalized.startsWith("/")) {
      candidates.add(scriptsRoot.resolve(normalized.substring(1)));
    } else {
      if (sourceName != null && sourceName.contains("/")) {
        Path sourceBase = scriptsRoot.resolve(sourceName).normalize().getParent();
        if (sourceBase != null) candidates.add(sourceBase.resolve(normalized));
      }
      candidates.add(scriptsRoot.resolve(normalized));
    }
    candidates.add(root.resolve(normalized));

    for (Path candidate : candidates) {
      if (candidate == null) continue;
      Path resolved = candidate.toAbsolutePath().normalize();
      if (!resolved.startsWith(root)) continue;
      if (Files.isRegularFile(resolved)) {
        return Files.newInputStream(resolved);
      }
    }
    throw new IOException("Included script not found: " + includePath);
  }

  private File validationProjectRoot(File sourceFile) {
    if (projectRoot != null) return projectRoot;
    if (sourceFile != null) {
      Path current = sourceFile.toPath().toAbsolutePath().normalize().getParent();
      while (current != null) {
        Path name = current.getFileName();
        if (name != null && "scripts".equalsIgnoreCase(name.toString())) {
          Path parent = current.getParent();
          return (parent != null ? parent : current).toFile();
        }
        current = current.getParent();
      }
    }
    if (timelineFile != null && timelineFile.getParentFile() != null) {
      File config = timelineFile.getParentFile().getParentFile();
      if (config != null && "config".equalsIgnoreCase(config.getName())) {
        File root = config.getParentFile();
        if (root != null) return root;
      }
    }
    return sourceFile == null ? null : sourceFile.getParentFile();
  }
  public static class Link {
    public String fromArc;
    public String fromLabel;
    public String toArc;
    public String toLabel;
    public String toLine() { return "LINK|" + nn(fromArc) + "|" + nn(fromLabel) + "|" + nn(toArc) + "|" + nn(toLabel); }
  }

  private final ListView<Arc> arcs = new ListView<>();
  private final ListView<Link> links = new ListView<>();
  private final StoryGraphPane graph = new StoryGraphPane();
  private final ScrollPane graphScroll = new ScrollPane(graph);
  private final Label graphHint = new Label("Add an arc or drag a .vns script here to start your story map.");
  private static final double MIN_ZOOM = 0.6;
  private static final double MAX_ZOOM = 2.0;
  private double zoomLevel = 1.0;
  private ComboBox<String> clusterFilter;
  private File projectRoot;
  private File timelineFile;
  private Consumer<Arc> onRunArc;
  private Consumer<Link> onRunLink;
  private Runnable onChanged;
  private final javafx.animation.Timeline autoRefreshTimer = new javafx.animation.Timeline(
      new javafx.animation.KeyFrame(javafx.util.Duration.seconds(2.0), e -> autoRefreshFromDisk()));
  private long lastTimelineModified = -1L;

  public StoryTimelineView() {
    getStyleClass().add("sidebar-tool-root");
    arcs.getStyleClass().add("timeline-list");
    links.getStyleClass().add("timeline-list");
    graphHint.getStyleClass().add("timeline-empty-hint");
    graphHint.setWrapText(true);
    graphHint.setMaxWidth(440);
    graphHint.setMouseTransparent(true);
    StackPane.setAlignment(graphHint, Pos.CENTER);
    StackPane.setMargin(graphHint, new Insets(12));

    arcs.setCellFactory(v -> new ListCell<>() {
      @Override protected void updateItem(Arc a, boolean empty) {
        super.updateItem(a, empty);
        if (empty || a == null) {
          setText(null);
          return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(nn(a.name));
        if (a.priority != 0) sb.append("  p").append(a.priority);
        if (a.cluster != null && !a.cluster.isBlank()) sb.append("  #").append(a.cluster);
        if (a.tags != null && !a.tags.isBlank()) sb.append("  [").append(a.tags).append("]");
        sb.append("  [").append(nn(a.script));
        if (a.entryLabel != null && !a.entryLabel.isBlank()) sb.append(" :: ").append(a.entryLabel);
        sb.append("]");
        setText(sb.toString());
      }
    });
    links.setCellFactory(v -> new ListCell<>() {
      @Override protected void updateItem(Link l, boolean empty) {
        super.updateItem(l, empty);
        setText(empty || l == null ? null : formatEndpoint(l.fromArc, l.fromLabel) + "  ->  " + formatEndpoint(l.toArc, l.toLabel));
      }
    });
    arcs.setPlaceholder(new Label("No arcs yet."));
    links.setPlaceholder(new Label("No links yet."));
    arcs.setOnMouseClicked(e -> {
      if (e.getClickCount() == 2 && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) openArc();
    });
    links.setOnMouseClicked(e -> {
      if (e.getClickCount() == 2 && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) runSelectedLink();
    });
    arcs.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
        deleteSelected();
      } else if (e.getCode() == KeyCode.ENTER) {
        openArc();
      }
    });
    links.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
        deleteSelected();
      } else if (e.getCode() == KeyCode.ENTER) {
        runSelectedLink();
      }
    });
    ContextMenu arcMenu = new ContextMenu();
    MenuItem miArcOpen = new MenuItem("Open Script");
    miArcOpen.setOnAction(e -> openArc());
    MenuItem miArcEdit = new MenuItem("Edit Arc...");
    miArcEdit.setOnAction(e -> editArc());
    MenuItem miArcDelete = new MenuItem("Delete Arc");
    miArcDelete.setOnAction(e -> deleteSelected());
    arcMenu.getItems().addAll(miArcOpen, miArcEdit, miArcDelete);
    arcs.setContextMenu(arcMenu);
    ContextMenu linkMenu = new ContextMenu();
    MenuItem miLinkRun = new MenuItem("Open Target Arc");
    miLinkRun.setOnAction(e -> runSelectedLink());
    MenuItem miLinkEdit = new MenuItem("Edit Link...");
    miLinkEdit.setOnAction(e -> editLink());
    MenuItem miLinkCopy = new MenuItem("Copy Goto");
    miLinkCopy.setOnAction(e -> copyGoto());
    MenuItem miLinkDelete = new MenuItem("Delete Link");
    miLinkDelete.setOnAction(e -> deleteSelected());
    linkMenu.getItems().addAll(miLinkRun, miLinkEdit, miLinkCopy, miLinkDelete);
    links.setContextMenu(linkMenu);

    // Graph area
    graphScroll.setFitToWidth(false);
    graphScroll.setFitToHeight(false);
    graphScroll.setPannable(true);
    graphScroll.getStyleClass().add("story-map-scroll");
    StackPane graphPane = new StackPane(graphScroll, graphHint);
    graphPane.getStyleClass().add("timeline-graph-pane");

    // Bottom tabs
    TabPane listTabs = new TabPane();
    listTabs.getStyleClass().add("timeline-lists-tabs");
    Tab arcsTab = new Tab("Arcs", arcs);
    arcsTab.setClosable(false);
    Tab linksTab = new Tab("Links", links);
    linksTab.setClosable(false);
    listTabs.getTabs().addAll(arcsTab, linksTab);
    listTabs.setMinHeight(170);
    listTabs.setPrefHeight(220);

    // Split layout
    SplitPane rootSplit = new SplitPane();
    rootSplit.getStyleClass().add("sidebar-tool-split");
    rootSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
    rootSplit.getItems().addAll(graphPane, listTabs);
    rootSplit.setDividerPositions(0.76);
    setCenter(rootSplit);

    // Toolbar
    Button bAddArc = iconButton("Add Arc", SidebarToolIcon.plus(), e -> addArc());
    Button bAddLink = iconButton("Add Link", SidebarToolIcon.link(), e -> addLink());
    Button bEdit = iconButton("Edit", SidebarToolIcon.edit(), e -> editSelected());
    Button bOpen = iconButton("Open", SidebarToolIcon.folder(), e -> openArc());
    Button bDelete = iconButton("Delete", SidebarToolIcon.delete(), e -> deleteSelected());
    Button bCopyGoto = iconButton("Copy Goto", SidebarToolIcon.copy(), e -> copyGoto());
    Button bRefresh = iconButton("Refresh", SidebarToolIcon.refresh(), e -> syncFromDisk(true));
    Button bAuto = iconButton("Auto Layout", SidebarToolIcon.auto(), e -> { graph.autoLayout(); onGraphChanged(); });
    Button bFit = iconButton("Fit", SidebarToolIcon.rectSelect(), e -> zoomToFit());
    Button bValidate = iconButton("Validate", SidebarToolIcon.check(), e -> validate());
    TextField tfSearch = new TextField();
    tfSearch.setPromptText("Find arc...");
    tfSearch.setAccessibleText("Find arc");
    tfSearch.getStyleClass().add("sidebar-tool-search-field");
    tfSearch.setTooltip(new Tooltip("Find and highlight story arcs by name"));
    tfSearch.setMinWidth(72);
    tfSearch.textProperty().addListener((o, ov, nv) -> graph.highlight(nv));
    HBox.setHgrow(tfSearch, Priority.ALWAYS);
    bAddArc.setTooltip(new Tooltip("Create a story arc from a .vns script"));
    bAddLink.setTooltip(new Tooltip("Connect two arcs"));
    bEdit.setTooltip(new Tooltip("Edit selected arc or link"));
    bOpen.setTooltip(new Tooltip("Open selected arc script"));
    bDelete.setTooltip(new Tooltip("Delete selected arc or link"));
    bCopyGoto.setTooltip(new Tooltip("Copy [goto arc:label] snippet"));
    bRefresh.setTooltip(new Tooltip("Reload the story map and remove links to deleted scripts"));
    bAuto.setTooltip(new Tooltip("Auto-arrange arc nodes"));
    bFit.setTooltip(new Tooltip("Fit graph to viewport"));
    bValidate.setTooltip(new Tooltip("Validate scripts and entry labels"));

    clusterFilter = new ComboBox<>();
    clusterFilter.setMinWidth(80);
    clusterFilter.setPrefWidth(112);
    clusterFilter.setMaxWidth(112);
    clusterFilter.setPromptText("All");
    clusterFilter.setTooltip(new Tooltip("Filter story arcs by cluster"));
    clusterFilter.valueProperty().addListener((o,ov,nv) -> {
      if (nv == null || nv.equals("All")) graph.setFilterCluster(null); else graph.setFilterCluster(nv);
    });

    Label titleLabel = new Label("Story Map");
    titleLabel.getStyleClass().add("sidebar-tool-title");
    HBox titleRow = new HBox(6, titleLabel, SidebarToolHelp.button(this, "Story Map", """
        The Story Map organises .vns scripts into named arcs and links so you can \
see and edit the structure of a project at a higher level than a single script.

Arcs point at script files and optional entry labels. Links represent story \
transitions between arcs and can copy goto snippets back into script text.

Use the graph to drag arcs into place, run or select nodes, and inspect the \
flow between story sections. Drop .vns files onto the graph to create arcs \
quickly, then use Validate to catch missing scripts or labels."""));
    titleRow.setAlignment(Pos.CENTER_LEFT);

    Label clusterLabel = new Label("Cluster");
    clusterLabel.getStyleClass().add("sidebar-tool-subtitle");
    clusterLabel.setMinWidth(Region.USE_PREF_SIZE);
    clusterLabel.setLabelFor(clusterFilter);
    HBox filters = new HBox(8, tfSearch, clusterLabel, clusterFilter);
    filters.setAlignment(Pos.CENTER_LEFT);
    FlowPane actions = new FlowPane(4, 4,
        bAddArc, bAddLink, bEdit, bOpen, bDelete, bCopyGoto, bRefresh, bAuto, bFit, bValidate);
    actions.setAlignment(Pos.CENTER_LEFT);

    VBox toolbar = new VBox(6, titleRow, filters, actions);
    toolbar.getStyleClass().add("sidebar-tool-header");
    setTop(toolbar);
    setGraphZoom(1.0);

    // Graph actions wiring
    graph.setOnRunArc(a -> { if (onRunArc != null) onRunArc.accept(a); });
    graph.setOnSelectArc(a -> {
      if (a == null) return;
      links.getSelectionModel().clearSelection();
      arcs.getSelectionModel().select(a);
    });
    graph.setOnRunLink(l -> { if (onRunLink != null) onRunLink.accept(l); });
    graph.setOnSelectLink(l -> {
      if (l == null) return;
      arcs.getSelectionModel().clearSelection();
      links.getSelectionModel().select(l);
    });
    graph.setOnGraphChanged(this::onGraphChanged);
    graph.setOnLayoutCommitted(this::onGraphChanged);
    graph.setOnDeleteArc(a -> { if (a != null) { removeArcAndLinks(a.name); onGraphChanged(); } });
    graph.setOnInteractionActive(active -> graphScroll.setPannable(!active));
    graph.setSimpleLinkMode(true);
    arcs.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
      graph.selectArc(nv == null ? null : nv.name);
    });
    links.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
      graph.selectLink(nv);
    });

    // Wheel zoom with Ctrl/Cmd
    graphScroll.addEventFilter(ScrollEvent.SCROLL, e -> {
      if (e.isControlDown() || e.isShortcutDown()) {
        double step = (e.getDeltaY() > 0) ? 0.1 : -0.1;
        setGraphZoom(zoomLevel + step);
        e.consume();
      }
    });

    // Drag-and-drop .vns files onto graph to create arcs
    graph.setOnDragOver(e -> {
      Dragboard db = e.getDragboard();
      if (db.hasFiles()) {
        boolean ok = db.getFiles().stream().anyMatch(f -> f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".vns"));
        if (ok) { e.acceptTransferModes(TransferMode.COPY); e.consume(); }
      }
    });
    graph.setOnDragDropped(e -> {
      Dragboard db = e.getDragboard();
      boolean success = false;
      if (db.hasFiles()) {
        for (File f : db.getFiles()) {
          String name = f.getName().toLowerCase(java.util.Locale.ROOT);
          if (!name.endsWith(".vns")) continue;
          addArcFromFile(f);
          success = true;
        }
      }
      e.setDropCompleted(success); e.consume();
    });
    autoRefreshTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
    sceneProperty().addListener((obs, oldScene, newScene) -> {
      if (newScene == null) {
        autoRefreshTimer.stop();
      } else {
        syncFromDisk(true);
        autoRefreshTimer.play();
      }
    });
    updateGraphHint();
  }

  private static Button iconButton(String text, Region icon,
                                   javafx.event.EventHandler<javafx.event.ActionEvent> onAction) {
    Button button = new Button(text, icon);
    button.getStyleClass().add("sidebar-tool-btn");
    button.setContentDisplay(ContentDisplay.LEFT);
    button.setGraphicTextGap(6);
    button.setMinWidth(Region.USE_PREF_SIZE);
    button.setOnAction(onAction);
    return button;
  }

  public void setProjectRoot(File dir) {
    this.projectRoot = dir;
    syncFromDisk(true);
  }

  public void setOnRunArc(Consumer<Arc> c) { this.onRunArc = c; }
  public void setOnRunLink(Consumer<Link> c) { this.onRunLink = c; }
  public void setOnChanged(Runnable r) { this.onChanged = r; }
  public void setTimelineFile(File f) { this.timelineFile = f; syncFromDisk(true); }
  public List<Arc> getArcs() { return new ArrayList<>(arcs.getItems()); }
  public List<Link> getLinks() { return new ArrayList<>(links.getItems()); }
  public Arc findArc(String name) {
    for (Arc a : arcs.getItems()) if (a != null && name != null && name.equals(a.name)) return a; return null;
  }

  private void editSelected() {
    if (arcs.getSelectionModel().getSelectedItem() != null) {
      editArc();
      return;
    }
    if (links.getSelectionModel().getSelectedItem() != null) {
      editLink();
    }
  }

  private void editArc() {
    Arc arc = arcs.getSelectionModel().getSelectedItem();
    if (arc == null) return;
    ArcDraft draft = showArcDialog("Edit Arc", arc, false);
    if (draft == null) return;
    String oldName = arc.name;
    applyArcDraft(arc, draft);
    if (oldName != null && !oldName.equals(arc.name)) renameArcReferences(oldName, arc.name);
    onGraphChanged();
  }

  private ArcDraft showArcDialog(String title, Arc seed, boolean lockScript) {
    Arc source = seed == null ? new Arc() : seed;
    GridPane g = new GridPane();
    g.setHgap(8);
    g.setVgap(8);
    g.setPadding(new Insets(10));
    TextField tfName = new TextField(nn(source.name));
    TextField tfScript = new TextField(nn(source.script));
    tfScript.setEditable(!lockScript);
    tfScript.setDisable(lockScript);
    TextField tfEntry = new TextField(nn(source.entryLabel));
    TextField tfCluster = new TextField(nn(source.cluster));
    TextField tfPriority = new TextField(String.valueOf(source.priority));
    TextField tfColor = new TextField(nn(source.color));
    TextField tfTags = new TextField(nn(source.tags));
    Button bBrowse = new Button("Browse...");
    bBrowse.setDisable(lockScript);
    bBrowse.setOnAction(e -> {
      FileChooser fc = new FileChooser();
      fc.setTitle("Select VNS Script");
      fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("VNS scripts", "*.vns"));
      File f = fc.showOpenDialog(getScene() == null ? null : getScene().getWindow());
      if (f != null) tfScript.setText(toRelative(f));
    });
    javafx.scene.layout.HBox scriptRow = new javafx.scene.layout.HBox(6, tfScript, bBrowse);
    javafx.scene.layout.HBox.setHgrow(tfScript, javafx.scene.layout.Priority.ALWAYS);
    g.addRow(0, new Label("Name"), tfName);
    g.addRow(1, new Label("Script"), scriptRow);
    g.addRow(2, new Label("Entry Label"), tfEntry);
    g.addRow(3, new Label("Cluster"), tfCluster);
    g.addRow(4, new Label("Priority"), tfPriority);
    g.addRow(5, new Label("Color"), tfColor);
    g.addRow(6, new Label("Tags"), tfTags);
    tfColor.setPromptText("#ff8844");
    tfTags.setPromptText("comma,separated,tags");
    tfPriority.setPromptText("0");

    var res = EditorDialogs.show(
        getScene() == null ? null : getScene().getWindow(),
        title,
        lockScript ? "Configure the arc." : "Configure the arc and choose its script.",
        g,
        tfName,
        EditorDialogs.ActionSpec.neutral("cancel", "Cancel", null),
        EditorDialogs.ActionSpec.accent("save", title.startsWith("Add") ? "Add" : "Save", null));
    if (res.isEmpty() || !"save".equals(res.get())) return null;

    String newName = tfName.getText() == null ? "" : tfName.getText().trim();
    if (newName.isEmpty()) return null;
    ArcDraft out = new ArcDraft();
    out.name = newName;
    out.script = tfScript.getText() == null ? "" : tfScript.getText().trim();
    out.entryLabel = tfEntry.getText() == null ? "" : tfEntry.getText().trim();
    out.cluster = tfCluster.getText() == null ? "" : tfCluster.getText().trim();
    out.priority = parseInt(tfPriority.getText(), 0);
    out.color = tfColor.getText() == null ? "" : tfColor.getText().trim();
    out.tags = tfTags.getText() == null ? "" : tfTags.getText().trim();
    return out;
  }

  private void editLink() {
    Link l = links.getSelectionModel().getSelectedItem();
    if (l == null || arcs.getItems().isEmpty()) return;
    GridPane g = new GridPane();
    g.setHgap(6); g.setVgap(6); g.setPadding(new Insets(8));
    ComboBox<Arc> fromArc = new ComboBox<>();
    fromArc.getItems().setAll(arcs.getItems());
    configureArcCombo(fromArc);
    TextField fromLabel = new TextField(nn(l.fromLabel));
    ComboBox<Arc> toArc = new ComboBox<>();
    toArc.getItems().setAll(arcs.getItems());
    configureArcCombo(toArc);
    TextField toLabel = new TextField(nn(l.toLabel));
    fromArc.setValue(findArc(l.fromArc));
    toArc.setValue(findArc(l.toArc));
    g.addRow(0, new Label("From Arc"), fromArc);
    g.addRow(1, new Label("From Label"), fromLabel);
    g.addRow(2, new Label("To Arc"), toArc);
    g.addRow(3, new Label("To Label"), toLabel);
    var res = EditorDialogs.show(
        getScene() == null ? null : getScene().getWindow(),
        "Edit Link",
        "Update the connection between arcs.",
        g,
        fromArc,
        EditorDialogs.ActionSpec.neutral("cancel", "Cancel", null),
        EditorDialogs.ActionSpec.accent("save", "Save", null));
    if (res.isEmpty() || !"save".equals(res.get())) return;
    Arc fa = fromArc.getValue();
    Arc ta = toArc.getValue();
    if (fa == null || ta == null) return;
    l.fromArc = fa.name;
    l.fromLabel = fromLabel.getText() == null ? "" : fromLabel.getText().trim();
    l.toArc = ta.name;
    l.toLabel = toLabel.getText() == null ? "" : toLabel.getText().trim();
    onGraphChanged();
  }

  private void configureArcCombo(ComboBox<Arc> combo) {
    combo.setCellFactory(v -> new ListCell<>() {
      @Override protected void updateItem(Arc item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : nn(item.name));
      }
    });
    combo.setButtonCell(new ListCell<>() {
      @Override protected void updateItem(Arc item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : nn(item.name));
      }
    });
  }

  private static final class ArcDraft {
    String name;
    String script;
    String entryLabel;
    String cluster;
    int priority;
    String color;
    String tags;
  }

  private void applyArcDraft(Arc target, ArcDraft draft) {
    if (target == null || draft == null) return;
    target.name = nn(draft.name).trim();
    target.script = nn(draft.script).trim();
    target.entryLabel = nn(draft.entryLabel).trim();
    target.cluster = nn(draft.cluster).trim();
    target.priority = draft.priority;
    target.color = nn(draft.color).trim();
    target.tags = nn(draft.tags).trim();
  }

  private String suggestArcName(String base) {
    String root = (base == null || base.isBlank()) ? "Arc" : base.trim();
    Set<String> names = new LinkedHashSet<>();
    for (Arc arc : arcs.getItems()) {
      if (arc == null || arc.name == null) continue;
      names.add(arc.name.toLowerCase(Locale.ROOT));
    }
    if (!names.contains(root.toLowerCase(Locale.ROOT))) return root;
    int i = 2;
    while (true) {
      String candidate = root + " " + i;
      if (!names.contains(candidate.toLowerCase(Locale.ROOT))) return candidate;
      i++;
    }
  }

  private static int parseInt(String raw, int fallback) {
    try {
      if (raw == null || raw.isBlank()) return fallback;
      return Integer.parseInt(raw.trim());
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return fallback;
    }
  }

  private void renameArcReferences(String oldName, String newName) {
    for (Link li : links.getItems()) {
      if (li == null) continue;
      if (oldName.equals(li.fromArc)) li.fromArc = newName;
      if (oldName.equals(li.toArc)) li.toArc = newName;
    }
  }

  private void addArc() {
    Arc seed = new Arc();
    seed.name = suggestArcName("Arc");
    ArcDraft draft = showArcDialog("Add Arc", seed, false);
    if (draft == null) return;
    Arc a = new Arc();
    applyArcDraft(a, draft);
    arcs.getItems().add(a);
    arcs.getSelectionModel().select(a);
    onGraphChanged();
  }

  private void addLink() {
    if (arcs.getItems().isEmpty()) return;
    GridPane g = new GridPane();
    g.setHgap(6); g.setVgap(6); g.setPadding(new Insets(8));
    ComboBox<Arc> fromArc = new ComboBox<>(); fromArc.getItems().setAll(arcs.getItems()); configureArcCombo(fromArc);
    TextField fromLabel = new TextField();
    ComboBox<Arc> toArc = new ComboBox<>(); toArc.getItems().setAll(arcs.getItems()); configureArcCombo(toArc);
    TextField toLabel = new TextField();
    Arc selectedArc = arcs.getSelectionModel().getSelectedItem();
    if (selectedArc != null) {
      fromArc.setValue(selectedArc);
      for (Arc a : arcs.getItems()) {
        if (a != null && a != selectedArc) {
          toArc.setValue(a);
          break;
        }
      }
    }
    if (fromArc.getValue() == null && !arcs.getItems().isEmpty()) fromArc.setValue(arcs.getItems().get(0));
    if (toArc.getValue() == null && !arcs.getItems().isEmpty()) toArc.setValue(arcs.getItems().get(0));
    g.addRow(0, new Label("From Arc"), fromArc);
    g.addRow(1, new Label("From Label"), fromLabel);
    g.addRow(2, new Label("To Arc"), toArc);
    g.addRow(3, new Label("To Label"), toLabel);
    var res = EditorDialogs.show(
        getScene() == null ? null : getScene().getWindow(),
        "Add Link",
        "Create a connection between two arcs.",
        g,
        fromArc,
        EditorDialogs.ActionSpec.neutral("cancel", "Cancel", null),
        EditorDialogs.ActionSpec.accent("add", "Add", null));
    if (res.isEmpty() || !"add".equals(res.get())) return;
    Arc fa = fromArc.getValue(); Arc ta = toArc.getValue(); if (fa == null || ta == null) return;
    Link l = new Link(); l.fromArc = fa.name; l.fromLabel = fromLabel.getText(); l.toArc = ta.name; l.toLabel = toLabel.getText();
    links.getItems().add(l);
    links.getSelectionModel().select(l);
    onGraphChanged();
  }

  private void deleteSelected() {
    Link link = links.getSelectionModel().getSelectedItem();
    if (link != null) {
      links.getItems().remove(link);
      onGraphChanged();
      return;
    }
    Arc arc = arcs.getSelectionModel().getSelectedItem();
    if (arc != null) {
      removeArcAndLinks(arc.name);
      onGraphChanged();
    }
  }

  private void openArc() {
    Arc a = arcs.getSelectionModel().getSelectedItem();
    if (a == null) return;
    if (onRunArc != null) {
      onRunArc.accept(a);
      return;
    }
    try { java.awt.Desktop.getDesktop().open(resolveFile(a.script)); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
  }

  private void runSelectedLink() {
    Link l = links.getSelectionModel().getSelectedItem();
    if (l == null) return;
    if (onRunLink != null) {
      onRunLink.accept(l);
      return;
    }
    Arc target = findArc(l.toArc);
    if (target != null) {
      arcs.getSelectionModel().select(target);
      openArc();
    }
  }

  private void copyGoto() {
    String snip = null;
    Link l = links.getSelectionModel().getSelectedItem();
    if (l != null) {
      snip = "[goto " + nn(l.toArc) + ":" + nn(l.toLabel) + "]";
    } else {
      Arc a = arcs.getSelectionModel().getSelectedItem();
      if (a != null) snip = "[goto " + nn(a.name) + ":" + nn(a.entryLabel) + "]";
    }
    if (snip == null) return;
    javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
    cc.putString(snip);
    javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
  }

  private void save() {
    File f = timelineFile != null ? timelineFile : defaultTimelineFile();
    if (f == null) return;
    File parent = f.getParentFile();
    if (parent != null && !parent.exists()) parent.mkdirs();
    try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
      pw.print(toDsl());
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    lastTimelineModified = f.exists() ? f.lastModified() : -1L;
  }

  private void load() {
    File f = timelineFile != null ? timelineFile : defaultTimelineFile();
    if (f == null) return;
    if (!f.exists()) {
      lastTimelineModified = -1L;
      return;
    }
    try {
      String text = java.nio.file.Files.readString(f.toPath());
      fromText(text);
      lastTimelineModified = f.lastModified();
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
  }

  private void autoRefreshFromDisk() {
    syncFromDisk(false);
  }

  private void syncFromDisk(boolean forceReload) {
    File f = timelineFile != null ? timelineFile : defaultTimelineFile();
    boolean reloaded = false;
    if (f != null && f.exists()) {
      long modified = f.lastModified();
      if (forceReload || modified != lastTimelineModified) {
        load();
        reloaded = true;
      }
    } else if (forceReload) {
      lastTimelineModified = -1L;
    }

    int removed = pruneStaleReferences();
    if (removed > 0) {
      refreshGraph();
      arcs.refresh();
      links.refresh();
      updateGraphHint();
      updateClusterFilter();
      save();
      if (onChanged != null) onChanged.run();
      return;
    }
    if (reloaded || forceReload) {
      refreshGraph();
      arcs.refresh();
      links.refresh();
      updateGraphHint();
      updateClusterFilter();
    }
  }

  private int pruneStaleReferences() {
    int removed = 0;
    Set<String> validArcNames = new LinkedHashSet<>();
    List<Arc> staleArcs = new ArrayList<>();
    for (Arc arc : arcs.getItems()) {
      if (arc == null) continue;
      if (isMissingScriptArc(arc)) {
        staleArcs.add(arc);
      } else if (arc.name != null && !arc.name.isBlank()) {
        validArcNames.add(arc.name);
      }
    }
    if (!staleArcs.isEmpty()) {
      arcs.getItems().removeAll(staleArcs);
      removed += staleArcs.size();
    }
    int beforeLinks = links.getItems().size();
    links.getItems().removeIf(link -> link == null
        || !validArcNames.contains(link.fromArc)
        || !validArcNames.contains(link.toArc));
    removed += beforeLinks - links.getItems().size();
    return removed;
  }

  private boolean isMissingScriptArc(Arc arc) {
    if (arc == null) return false;
    String script = nn(arc.script).trim();
    if (script.isBlank()) return false;
    File raw = new File(script);
    if (!raw.isAbsolute() && projectRoot == null) return false;
    File file = resolveFile(script);
    return file == null || !file.isFile();
  }

  private File defaultTimelineFile() {
    return StoryMapPaths.resolveForProjectRoot(projectRoot);
  }

  private static String nn(String s) { return s == null ? "" : s; }
  private static String n(String s) { return s == null ? "" : s; }

  private String toRelative(File f) {
    try {
      if (projectRoot == null) return f.getAbsolutePath();
      String root = projectRoot.getCanonicalPath();
      String abs = f.getCanonicalPath();
      if (abs.startsWith(root)) {
        String rel = abs.substring(root.length());
        if (rel.startsWith(File.separator)) rel = rel.substring(1);
        return rel.replace('\\', '/');
      }
      return abs;
    } catch (Exception e) {
      return f.getPath();
    }
  }

  private File resolveFile(String p) {
    if (p == null) return null;
    File f = new File(p);
    if (f.isAbsolute() || projectRoot == null) return f;
    return new File(projectRoot, p);
  }

  private void refreshGraph() {
    graph.setModel(arcs.getItems(), links.getItems());
    updateClusterFilter();
  }

  private void updateGraphHint() {
    boolean empty = arcs.getItems().isEmpty();
    graphHint.setVisible(empty);
    graphHint.setManaged(empty);
  }

  private void onGraphChanged() {
    // sync list views/graph model and persist
    refreshGraph();
    arcs.refresh(); links.refresh();
    updateGraphHint();
    save();
    updateClusterFilter();
    if (onChanged != null) onChanged.run();
  }

  private void updateClusterFilter() {
    if (clusterFilter == null) return;
    java.util.Set<String> names = graph.getClusterNames();
    String sel = clusterFilter.getValue();
    clusterFilter.getItems().setAll(new java.util.ArrayList<>());
    clusterFilter.getItems().add("All");
    for (String n : names) clusterFilter.getItems().add(n);
    if (sel != null && clusterFilter.getItems().contains(sel)) {
      clusterFilter.setValue(sel);
    } else {
      clusterFilter.setValue("All");
    }
  }

  private void removeArcAndLinks(String arcName) {
    if (arcName == null) return;
    Arc target = null;
    for (Arc a : arcs.getItems()) { if (a != null && arcName.equals(a.name)) { target = a; break; } }
    if (target != null) arcs.getItems().remove(target);
    links.getItems().removeIf(l -> arcName.equals(l.fromArc) || arcName.equals(l.toArc));
  }

  private void addArcFromFile(File f) {
    if (f == null) return;
    Arc seed = new Arc();
    seed.name = suggestArcName(stripExt(f.getName()));
    seed.script = toRelative(f);
    ArcDraft draft = showArcDialog("Add Arc", seed, true);
    if (draft == null) return;
    Arc a = new Arc();
    applyArcDraft(a, draft);
    arcs.getItems().add(a);
    arcs.getSelectionModel().select(a);
    onGraphChanged();
  }

  private static String stripExt(String n) {
    if (n == null) return "Arc"; int i = n.lastIndexOf('.'); return (i>0) ? n.substring(0,i) : n;
  }

  public String toDsl() {
    return serializeDsl(arcs.getItems(), links.getItems());
  }

  static String serializeDsl(List<Arc> arcList, List<Link> linkList) {
    StringBuilder sb = new StringBuilder();
    if (arcList != null) for (Arc a : arcList) {
      if (a == null) continue;
      sb.append("arc ").append(quoteAlways(a.name));
      if (a.script != null && !a.script.isBlank()) sb.append(" script ").append(quoteAlways(a.script));
      if (a.entryLabel != null && !a.entryLabel.isBlank()) sb.append(" entry ").append(quoteAlways(a.entryLabel));
      if (a.cluster != null && !a.cluster.isBlank()) sb.append(" cluster ").append(quoteAlways(a.cluster));
      if (a.priority != 0) sb.append(" priority ").append(a.priority);
      if (a.color != null && !a.color.isBlank()) sb.append(" color ").append(quoteAlways(a.color));
      if (a.tags != null && !a.tags.isBlank()) sb.append(" tags ").append(quoteAlways(a.tags));
      sb.append(" at ").append(a.x).append(",").append(a.y);
      sb.append("\n");
    }
    if (linkList != null) for (Link l : linkList) {
      if (l == null) continue;
      sb.append("link ").append(renderEndpoint(l.fromArc, l.fromLabel));
      sb.append(" -> ").append(renderEndpoint(l.toArc, l.toLabel)).append("\n");
    }
    return sb.toString();
  }

  public void fromText(String text) {
    ParsedTimeline parsed = parseTimelineDsl(text);
    arcs.getItems().setAll(parsed.arcs);
    links.getItems().setAll(parsed.links);
    refreshGraph();
    updateGraphHint();
  }

  static ParsedTimeline parseTimelineDsl(String text) {
    List<Arc> alist = new ArrayList<>();
    List<Link> llist = new ArrayList<>();
    if (text == null) text = "";
    String[] lines = text.split("\r?\n");
    for (String line : lines) {
      if (line == null) continue;
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      if (LEGACY_ARC.matcher(s).find()) {
        Arc a = parseLegacyArc(s);
        if (a != null) alist.add(a);
        continue;
      }
      if (LEGACY_LINK.matcher(s).find()) {
        Link l = parseLegacyLink(s);
        if (l != null) llist.add(l);
        continue;
      }
      Matcher ma = ARC_DSL_LINE.matcher(s);
      if (ma.matches()) {
        Arc a = new Arc();
        a.name = unescapeQuotedToken(ma.group(1) != null ? ma.group(1) : ma.group(2));
        a.script = unescapeQuotedToken(ma.group(3));
        a.entryLabel = unescapeQuotedToken(ma.group(4));
        a.cluster = unescapeQuotedToken(ma.group(5));
        a.priority = parseInt(ma.group(6), 0);
        a.color = unescapeQuotedToken(ma.group(7));
        a.tags = unescapeQuotedToken(ma.group(8));
        if (ma.group(9) != null && ma.group(10) != null) {
          a.x = parseDouble(ma.group(9), a.x);
          a.y = parseDouble(ma.group(10), a.y);
        }
        alist.add(a);
        continue;
      }
      Matcher ml = LINK_DSL_LINE.matcher(s);
      if (ml.matches()) {
        LinkEndpoint left = parseLinkEndpoint(ml.group(1));
        LinkEndpoint right = parseLinkEndpoint(ml.group(2));
        if (left.arc.isBlank() || right.arc.isBlank()) continue;
        Link l = new Link();
        l.fromArc = left.arc;
        l.fromLabel = left.label;
        l.toArc = right.arc;
        l.toLabel = right.label;
        llist.add(l);
      }
    }
    return new ParsedTimeline(alist, llist);
  }

  static LinkEndpoint parseLinkEndpoint(String token) {
    if (token == null) return new LinkEndpoint("", "", 0, 0, -1, -1);
    String t = token.trim();
    if (t.isEmpty()) return new LinkEndpoint("", "", 0, 0, -1, -1);

    if (t.startsWith("\"")) {
      ParsedString arc = parseQuotedPart(t, 0);
      if (arc == null) return new LinkEndpoint("", "", 0, 0, -1, -1);
      int idx = skipWs(t, arc.endIndex);
      if (idx >= t.length()) {
        return new LinkEndpoint(arc.value, "", arc.valueStart, arc.valueEnd, -1, -1);
      }
      if (t.charAt(idx) != ':') {
        return new LinkEndpoint(arc.value, "", arc.valueStart, arc.valueEnd, -1, -1);
      }
      ParsedString label = parseLabelPart(t, idx + 1);
      return new LinkEndpoint(
          arc.value,
          label.value,
          arc.valueStart,
          arc.valueEnd,
          label.valueStart,
          label.valueEnd
      );
    }

    int colon = t.indexOf(':');
    if (colon < 0) {
      String arc = t.trim();
      return new LinkEndpoint(arc, "", 0, arc.length(), -1, -1);
    }
    String arc = t.substring(0, colon).trim();
    ParsedString label = parseLabelPart(t, colon + 1);
    return new LinkEndpoint(arc, label.value, 0, arc.length(), label.valueStart, label.valueEnd);
  }

  private static ParsedString parseLabelPart(String raw, int start) {
    int idx = skipWs(raw, start);
    if (idx >= raw.length()) return new ParsedString("", idx, idx, idx);
    if (raw.charAt(idx) == '"') {
      ParsedString quoted = parseQuotedPart(raw, idx);
      if (quoted != null) return quoted;
    }
    String value = raw.substring(idx).trim();
    int vs = idx;
    int ve = idx + value.length();
    return new ParsedString(value, vs, ve, raw.length());
  }

  private static ParsedString parseQuotedPart(String raw, int startQuote) {
    if (raw == null || startQuote < 0 || startQuote >= raw.length() || raw.charAt(startQuote) != '"') return null;
    StringBuilder out = new StringBuilder();
    int i = startQuote + 1;
    while (i < raw.length()) {
      char c = raw.charAt(i);
      if (c == '\\' && i + 1 < raw.length()) {
        i++;
        out.append(unescapeChar(raw.charAt(i)));
        i++;
        continue;
      }
      if (c == '"') {
        return new ParsedString(out.toString(), startQuote + 1, i, i + 1);
      }
      out.append(c);
      i++;
    }
    return new ParsedString(out.toString(), startQuote + 1, raw.length(), raw.length());
  }

  private static int skipWs(String text, int idx) {
    int i = Math.max(0, idx);
    while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
    return i;
  }

  private static char unescapeChar(char c) {
    return switch (c) {
      case 'n' -> '\n';
      case 'r' -> '\r';
      case 't' -> '\t';
      case '"' -> '"';
      case '\\' -> '\\';
      default -> c;
    };
  }

  private static String unescapeQuotedToken(String raw) {
    if (raw == null) return "";
    StringBuilder out = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '\\' && i + 1 < raw.length()) {
        i++;
        out.append(unescapeChar(raw.charAt(i)));
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  private static String escapeQuotedToken(String raw) {
    String s = raw == null ? "" : raw;
    return s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  private static String quoteAlways(String raw) {
    return "\"" + escapeQuotedToken(raw) + "\"";
  }

  private static String quoteIfNeeded(String raw) {
    String s = raw == null ? "" : raw;
    if (s.isBlank()) return quoteAlways(s);
    if (SIMPLE_TOKEN.matcher(s).matches()) return s;
    return quoteAlways(s);
  }

  private static String renderEndpoint(String arc, String label) {
    String out = quoteIfNeeded(nn(arc));
    if (label != null && !label.isBlank()) out += ":" + quoteIfNeeded(label);
    return out;
  }

  private static String formatEndpoint(String arc, String label) {
    String a = nn(arc);
    String l = nn(label);
    return l.isBlank() ? a : (a + ":" + l);
  }

  private static Arc parseLegacyArc(String raw) {
    String[] t = raw.split("\\|", -1);
    if (t.length < 4) return null;
    Arc a = new Arc();
    a.name = n(t[1]);
    a.script = n(t[2]);
    a.entryLabel = n(t[3]);
    if (t.length >= 6) {
      a.x = parseDouble(t[4], a.x);
      a.y = parseDouble(t[5], a.y);
    }
    if (t.length >= 7) a.cluster = n(t[6]);
    if (t.length >= 8) a.priority = parseInt(t[7], 0);
    if (t.length >= 9) a.color = n(t[8]);
    if (t.length >= 10) a.tags = n(t[9]);
    return a;
  }

  private static Link parseLegacyLink(String raw) {
    String[] t = raw.split("\\|", -1);
    if (t.length < 5) return null;
    Link l = new Link();
    l.fromArc = n(t[1]);
    l.fromLabel = n(t[2]);
    l.toArc = n(t[3]);
    l.toLabel = n(t[4]);
    return l;
  }

  private static double parseDouble(String raw, double fallback) {
    try {
      if (raw == null || raw.isBlank()) return fallback;
      return Double.parseDouble(raw.trim());
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return fallback;
    }
  }

  private static final class ParsedString {
    final String value;
    final int valueStart;
    final int valueEnd;
    final int endIndex;

    ParsedString(String value, int valueStart, int valueEnd, int endIndex) {
      this.value = value;
      this.valueStart = valueStart;
      this.valueEnd = valueEnd;
      this.endIndex = endIndex;
    }
  }

  private void zoomToFit() {
    if (arcs.getItems().isEmpty()) return;
    double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
    for (Arc a : arcs.getItems()) {
      if (a == null) continue;
      double width = StoryGraphPane.nodeWidthForArc(a);
      double height = StoryGraphPane.nodeHeightForArc(a);
      minX = Math.min(minX, a.x);
      minY = Math.min(minY, a.y);
      maxX = Math.max(maxX, a.x + width);
      maxY = Math.max(maxY, a.y + height);
    }
    double pad = 120;
    double widthNeeded = (maxX - minX) + pad;
    double heightNeeded = (maxY - minY) + pad;
    double vw = graphScroll.getViewportBounds().getWidth();
    double vh = graphScroll.getViewportBounds().getHeight();
    if (vw <= 0 || vh <= 0) return;
    double s = Math.min(vw / widthNeeded, vh / heightNeeded);
    setGraphZoom(s);
    double contentW = Math.max(widthNeeded * s, vw);
    double contentH = Math.max(heightNeeded * s, vh);
    double centerX = (minX + maxX) / 2 * s;
    double centerY = (minY + maxY) / 2 * s;
    double hx = (centerX - vw / 2) / Math.max(1, (contentW - vw));
    double vy = (centerY - vh / 2) / Math.max(1, (contentH - vh));
    graphScroll.setHvalue(Math.max(0, Math.min(1, hx)));
    graphScroll.setVvalue(Math.max(0, Math.min(1, vy)));
  }

  private void setGraphZoom(double zoom) {
    zoomLevel = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    graph.setScaleX(zoomLevel);
    graph.setScaleY(zoomLevel);
  }
}
