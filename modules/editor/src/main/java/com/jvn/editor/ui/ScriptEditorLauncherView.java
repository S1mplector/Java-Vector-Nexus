package com.jvn.editor.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.jvn.editor.ui.ScriptEditorWorkspaceModel.ScriptFileEntry;
import com.jvn.editor.ui.ScriptEditorWorkspaceModel.WorkspaceSnapshot;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Sidebar text explorer + launcher for the dedicated project text editor window.
 * Designed to behave more like a small IDE explorer than a static launcher card.
 */
public class ScriptEditorLauncherView extends BorderPane {
  private static final DateTimeFormatter MODIFIED_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("0.0");

  private final Label projectLabel = new Label("No project loaded");
  private final Label scriptsRootLabel = new Label("No text workspace detected");
  private final Label scriptsStat = statValue("0");
  private final Label foldersStat = statValue("0");
  private final Label labelsStat = statValue("0");

  private final Button openInEditorButton = new Button("Open in Editor");
  private final Button openWindowButton = new Button("Pop Out Window");
  private final Button newScriptButton = new Button("New File");
  private final Button refreshButton = new Button("Refresh");
  private final Button revealButton = new Button("Reveal");

  private final TextField filterField = new TextField();
  private final TextField searchField = new TextField();
  private final VBox searchResults = new VBox(4);
  private final TreeView<ExplorerNode> explorerTree = new TreeView<>();
  private final Label explorerHint = new Label("Double-click or press Enter to open the selected text file in the main editor.");

  private final Label selectionTitle = new Label("Workspace Overview");
  private final Label selectionPath = new Label("Select a text file to inspect it.");
  private final Label selectionMeta = new Label("");
  private final VBox outlineList = new VBox(4);
  private final VBox includesList = new VBox(4);
  private final VBox includedByList = new VBox(4);

  private File projectRoot;
  private File workspaceRoot;
  private Consumer<String> onStatus;
  private Consumer<File> onOpenFile;
  private java.util.function.BiConsumer<File, Integer> onOpenFileAtLine;
  private double codeEditorFontSize = 13.0;

  private Stage editorWindow;
  private TabPane editorWindowTabs;
  private Label editorWindowStatus;
  private File editorWindowLaunchRoot;

  private WorkspaceSnapshot snapshot = ScriptEditorWorkspaceModel.emptySnapshot();
  private ExplorerNode selectedNode;

  public ScriptEditorLauncherView() {
    buildUi();
    refreshWorkspace();
  }

  public void setProjectRoot(File root) {
    this.projectRoot = root;
    refreshWorkspace();
  }

  public void setWorkspaceRoot(File root) {
    this.workspaceRoot = root;
    refreshWorkspace();
  }

  public void setOnStatus(Consumer<String> onStatus) {
    this.onStatus = onStatus;
  }

  public void setOnOpenFile(Consumer<File> onOpenFile) {
    this.onOpenFile = onOpenFile;
    updateActionState();
  }

  public void setOnOpenFileAtLine(java.util.function.BiConsumer<File, Integer> onOpenFileAtLine) {
    this.onOpenFileAtLine = onOpenFileAtLine;
  }

  public void setCodeEditorFontSize(double fontSizePx) {
    codeEditorFontSize = Math.max(8.0, Math.min(30.0, fontSizePx));
    if (editorWindowTabs == null) return;
    for (Tab tab : editorWindowTabs.getTabs()) {
      Object editor = tab.getProperties().get("editor");
      if (editor instanceof TextEditorSession session) {
        session.setFontSize(codeEditorFontSize);
      }
    }
  }

  public File getProjectRoot() {
    return projectRoot;
  }

  public void launchEditorWindow() {
    launchEditorWindow(selectedNode != null ? selectedNode.file() : null);
  }

  private void buildUi() {
    getStyleClass().addAll("script-editor-launcher-root", "sidebar-tool-root");

    VBox header = new VBox(8);
    header.getStyleClass().addAll("script-editor-launcher-header", "sidebar-tool-header");

    Label titleLabel = new Label("Text Editor");
    titleLabel.getStyleClass().addAll("script-editor-launcher-title", "sidebar-tool-title");
    Region titleSpacer = new Region();
    HBox.setHgrow(titleSpacer, Priority.ALWAYS);
    HBox titleRow = new HBox(
        6,
        titleLabel,
        SidebarToolHelp.button(this, "Text Editor", """
            The Text Editor panel browses and manages all text-based project \
files (.vns scripts, .jes timelines, .jvl layouts, and plain text assets).

From here you can:
  • Browse the full project file tree grouped by type
  • Open any file in the main editor tab by double-clicking it
  • Open the standalone tabbed text window for a distraction-free writing view
  • Create a new .vns script using the project template
  • Refresh the file tree after adding or deleting files externally

The stat chips at the top show a live count of discovered scripts, folders, \
and VNS label blocks across the project, giving you a quick sense of scope.

The Reveal button opens the selected path in JVN's cross-platform Path \
Explorer without relying on operating-system desktop integration."""),
        titleSpacer,
        compactStatChip("Files", scriptsStat),
        compactStatChip("Folders", foldersStat),
        compactStatChip("VNS Labels", labelsStat));
    titleRow.setAlignment(Pos.CENTER_LEFT);

    HBox metaRow = new HBox(
        8,
        compactMetaChip("Project", projectLabel),
        compactMetaChip("Workspace", scriptsRootLabel));
    metaRow.getStyleClass().add("script-editor-launcher-meta-row");
    HBox.setHgrow(metaRow.getChildren().get(0), Priority.NEVER);
    HBox.setHgrow(metaRow.getChildren().get(1), Priority.ALWAYS);

    styleActionButton(openInEditorButton, SidebarToolIcon.list("#e0e0e0"), "Open the selected text file in the main editor", true)
        .getStyleClass().add("script-editor-action-button-compact");
    styleActionButton(openWindowButton, SidebarToolIcon.popOut("#d0d0d0"), "Open the standalone tabbed text window", false)
        .getStyleClass().add("script-editor-action-button-compact");
    styleActionButton(newScriptButton, SidebarToolIcon.plus("#96bf7c"), "Create a new project text file", false)
        .getStyleClass().add("script-editor-action-button-compact");
    styleActionButton(refreshButton, SidebarToolIcon.refresh("#d0d0d0"), "Refresh the text workspace", false)
        .getStyleClass().add("script-editor-action-button-compact");
    styleActionButton(revealButton, SidebarToolIcon.folder("#d0d0d0"), "Reveal the current workspace in JVN Path Explorer", false)
        .getStyleClass().add("script-editor-action-button-compact");

    FlowPane actionsBar = new FlowPane();
    actionsBar.setHgap(6);
    actionsBar.setVgap(6);
    actionsBar.setAlignment(Pos.CENTER_LEFT);
    actionsBar.getStyleClass().add("script-editor-launcher-action-bar");
    actionsBar.getChildren().addAll(openInEditorButton, openWindowButton, newScriptButton, refreshButton, revealButton);

    filterField.setPromptText("Filter files, paths, or labels...");
    filterField.getStyleClass().add("script-editor-field");
    filterField.setTooltip(new Tooltip("Filter the workspace tree by file, path, or VNS label"));
    HBox.setHgrow(filterField, Priority.ALWAYS);

    searchField.setPromptText("Search in text files…");
    searchField.getStyleClass().add("script-editor-field");
    searchField.setTooltip(new Tooltip("Search inside all project text files"));
    HBox.setHgrow(searchField, Priority.ALWAYS);
    searchResults.setPadding(new Insets(4, 0, 0, 0));

    HBox fieldsRow = new HBox(8, filterField, searchField);
    fieldsRow.getStyleClass().add("script-editor-launcher-fields-row");
    fieldsRow.setAlignment(Pos.CENTER_LEFT);

    header.getChildren().addAll(titleRow, metaRow, actionsBar, fieldsRow);

    explorerTree.setShowRoot(true);
    explorerTree.getStyleClass().add("script-editor-tree");
    explorerTree.setTooltip(new Tooltip("Text workspace tree. Double-click or press Enter to open a file."));
    explorerTree.setCellFactory(tree -> new TreeCell<>() {
      @Override
      protected void updateItem(ExplorerNode item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          setGraphic(null);
          return;
        }
        Region icon = ProjectFileIcons.iconFor(ProjectFileIcons.kindFor(
            item.displayName,
            item.directory,
            item.directory && item.relativePath == null));
        Label name = new Label(item.displayName);
        name.getStyleClass().add(item.directory ? "script-editor-tree-dir-label" : "script-editor-tree-file-label");
        HBox row = new HBox(6, icon, name);
        row.setAlignment(Pos.CENTER_LEFT);
        if (item.directory && item.scriptCount > 0) {
          Region spacer = new Region();
          HBox.setHgrow(spacer, Priority.ALWAYS);
          Label badge = new Label(Integer.toString(item.scriptCount));
          badge.getStyleClass().add("script-editor-tree-badge");
          row.getChildren().addAll(spacer, badge);
        }
        setText(null);
        setGraphic(row);
      }
    });

    explorerHint.setWrapText(true);
    explorerHint.getStyleClass().add("script-editor-hint");

    VBox searchResultsCard = new VBox(6, paneHeader("Search Results", "Matches across the workspace", SidebarToolIcon.search("#d0d0d0")), searchResults);
    searchResultsCard.setPadding(new Insets(6));
    searchResultsCard.getStyleClass().add("script-editor-card");
    searchResultsCard.setVisible(false);
    searchResultsCard.setManaged(false);

    VBox explorerBox = new VBox(
        8,
        paneHeader("Workspace Explorer", "Project scripts and folders", SidebarToolIcon.folder("#d0d0d0")),
        explorerTree,
        explorerHint,
        searchResultsCard);
    explorerBox.setPadding(new Insets(8));
    explorerBox.getStyleClass().addAll("script-editor-launcher-pane", "script-editor-card");
    VBox.setVgrow(explorerTree, Priority.ALWAYS);

    selectionTitle.getStyleClass().add("script-editor-selection-title");
    selectionPath.setWrapText(true);
    selectionPath.getStyleClass().add("script-editor-selection-path");
    selectionMeta.setWrapText(true);
    selectionMeta.getStyleClass().add("script-editor-selection-meta");

    VBox outlineCard = new VBox(6, sectionLabel("Label Outline"), outlineList);
    outlineCard.setPadding(new Insets(8));
    outlineCard.getStyleClass().add("script-editor-card");
    VBox includesCard = new VBox(6, sectionLabel("Includes"), includesList);
    includesCard.setPadding(new Insets(8));
    includesCard.getStyleClass().add("script-editor-card");
    VBox includedByCard = new VBox(6, sectionLabel("Included By"), includedByList);
    includedByCard.setPadding(new Insets(8));
    includedByCard.getStyleClass().add("script-editor-card");
    VBox inspectorBox = new VBox(10, paneHeader("Inspector", "Outline, includes, and references", SidebarToolIcon.list("#d0d0d0")), selectionTitle, selectionPath, selectionMeta,
        outlineCard, includesCard, includedByCard);
    inspectorBox.setPadding(new Insets(8));
    inspectorBox.getStyleClass().addAll("script-editor-launcher-pane", "script-editor-card");

    SplitPane centerSplit = new SplitPane(explorerBox, inspectorBox);
    centerSplit.setOrientation(Orientation.VERTICAL);
    centerSplit.setDividerPositions(0.62);
    centerSplit.getStyleClass().add("script-editor-launcher-split");

    setTop(header);
    setCenter(centerSplit);

    wireListeners();
  }

  private void wireListeners() {
    filterField.textProperty().addListener((obs, oldVal, newVal) -> rebuildExplorerTree());
    refreshButton.setOnAction(e -> refreshWorkspace());
    openInEditorButton.setOnAction(e -> openSelectedInEditor());
    openWindowButton.setOnAction(e -> launchEditorWindow(selectedNode != null ? selectedNode.file() : null));
    revealButton.setOnAction(e -> revealSelection());
    newScriptButton.setOnAction(e -> createNewScript());

    explorerTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
      selectedNode = newVal == null ? null : newVal.getValue();
      updateSelectionInspector();
      updateActionState();
    });
    explorerTree.setOnMouseClicked(ev -> {
      if (ev.getClickCount() >= 2 && selectedNode != null && selectedNode.file() != null) {
        openSelectedInEditor();
      }
    });
    explorerTree.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
      if (ev.getCode() == KeyCode.ENTER && selectedNode != null && selectedNode.file() != null) {
        openSelectedInEditor();
        ev.consume();
      } else if (ev.getCode() == KeyCode.F5) {
        refreshWorkspace();
        ev.consume();
      } else if (ev.getCode() == KeyCode.DELETE || ev.getCode() == KeyCode.BACK_SPACE) {
        if (selectedNode != null && selectedNode.file() != null) {
          deleteSelectedScript();
          ev.consume();
        }
      } else if (ev.getCode() == KeyCode.F2) {
        if (selectedNode != null && selectedNode.file() != null) {
          renameSelectedScript();
          ev.consume();
        }
      }
    });

    installContextMenu();

    // Search in text files — debounced via PauseTransition
    javafx.animation.PauseTransition searchDebounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));
    searchField.textProperty().addListener((obs, oldVal, newVal) -> {
      searchDebounce.setOnFinished(ev -> runContentSearch(newVal));
      searchDebounce.playFromStart();
    });
  }

  private void installContextMenu() {
    explorerTree.setContextMenu(null);
    explorerTree.setOnContextMenuRequested(ev -> {
      TreeItem<ExplorerNode> item = explorerTree.getSelectionModel().getSelectedItem();
      if (item == null || item.getValue() == null) return;
      ExplorerNode node = item.getValue();

      ContextMenu ctx = new ContextMenu();

      if (node.file() != null) {
        MenuItem open = menuItem("Open in Editor", SidebarToolIcon.list("#d0d0d0"), this::openSelectedInEditor);
        MenuItem openWindow = menuItem("Open in Text Window", SidebarToolIcon.popOut("#d0d0d0"), () -> launchEditorWindow(node.file()));
        ctx.getItems().addAll(open, openWindow, new SeparatorMenuItem());

        MenuItem rename = menuItem("Rename…", SidebarToolIcon.freehand("#c8c8c8"), this::renameSelectedScript);
        MenuItem duplicate = menuItem("Duplicate", SidebarToolIcon.copy("#c8c8c8"), this::duplicateSelectedScript);
        MenuItem delete = menuItem("Delete…", SidebarToolIcon.clearX("#f06a6a"), this::deleteSelectedScript);
        ctx.getItems().addAll(rename, duplicate, delete, new SeparatorMenuItem());

        MenuItem copyPath = menuItem("Copy Absolute Path", SidebarToolIcon.copy("#c8c8c8"),
            () -> copyToClipboard(node.file().getAbsolutePath()));
        MenuItem copyRelPath = menuItem("Copy Relative Path", SidebarToolIcon.link("#c8c8c8"), () -> {
          if (node.entry != null) copyToClipboard(node.entry.projectRelativePath());
        });
        MenuItem reveal = menuItem("Reveal in JVN Path Explorer", SidebarToolIcon.folder("#d0d0d0"), () -> revealFile(node.file()));
        ctx.getItems().addAll(copyPath, copyRelPath, reveal);
      } else if (node.directory) {
        MenuItem newScript = menuItem("New File Here…", SidebarToolIcon.plus("#8bcf98"), () -> createNewScriptInFolder(node));
        MenuItem reveal = menuItem("Reveal in JVN Path Explorer", SidebarToolIcon.folder("#d0d0d0"), () -> {
          File dir = resolveNodeDirectory(node);
          if (dir != null && dir.exists()) revealFile(dir);
        });
        ctx.getItems().addAll(newScript, reveal);
      }

      if (!ctx.getItems().isEmpty()) {
        ctx.show(explorerTree, ev.getScreenX(), ev.getScreenY());
      }
    });
  }

  private void renameSelectedScript() {
    if (selectedNode == null || selectedNode.file() == null) return;
    File file = selectedNode.file();
    String currentName = file.getName();
    int dot = currentName.lastIndexOf('.');
    String stem = dot > 0 ? currentName.substring(0, dot) : currentName;

    EditorDialogs.promptText(dialogOwner(),
        "Rename File",
        "Rename " + currentName,
        "New name",
        stem,
        stem,
        "Rename").ifPresent(newName -> {
      try {
        File renamed = ScriptEditorWorkspaceModel.renameTextFile(file, newName);
        refreshWorkspace();
        restoreSelection(renamed.getAbsolutePath());
        setStatus("Renamed to " + renamed.getName());
      } catch (IOException ex) {
        showLaunchError("Failed to rename script:\n" + ex.getMessage());
      }
    });
  }

  private void duplicateSelectedScript() {
    if (selectedNode == null || selectedNode.file() == null) return;
    try {
      File copy = ScriptEditorWorkspaceModel.duplicateTextFile(selectedNode.file());
      refreshWorkspace();
      restoreSelection(copy.getAbsolutePath());
      setStatus("Duplicated as " + copy.getName());
    } catch (IOException ex) {
      showLaunchError("Failed to duplicate script:\n" + ex.getMessage());
    }
  }

  private void deleteSelectedScript() {
    if (selectedNode == null || selectedNode.file() == null) return;
    File file = selectedNode.file();
    if (!EditorDialogs.confirm(dialogOwner(),
        "Delete File",
        "Delete " + file.getName() + "?\nThis action cannot be undone.\n" + file.getAbsolutePath(),
        "Delete",
        true)) {
      return;
    }
    try {
      ScriptEditorWorkspaceModel.deleteTextFile(file);
      refreshWorkspace();
      setStatus("Deleted " + file.getName());
    } catch (IOException ex) {
      showLaunchError("Failed to delete file:\n" + ex.getMessage());
    }
  }

  private void createNewScriptInFolder(ExplorerNode folderNode) {
    String prefix = folderNode.relativePath != null ? folderNode.relativePath.toString().replace('\\', '/') + "/" : "";
    String defaultPath = prefix.isBlank()
        ? "scripts/story/new_scene.vns"
        : prefix + (prefix.startsWith("scripts/") ? "new_scene.vns" : "new_file.txt");
    EditorDialogs.promptText(dialogOwner(),
        "New File",
        "Create a new text file in " + folderNode.displayName,
        "Relative path inside project",
        defaultPath,
        defaultPath,
        "Create").ifPresent(input -> {
      File launchRoot = resolveLaunchRoot();
      if (launchRoot == null) return;
      try {
        File created = ScriptEditorWorkspaceModel.createTextFile(launchRoot, input);
        refreshWorkspace();
        restoreSelection(created.getAbsolutePath());
        setStatus("Created " + created.getName());
        if (onOpenFile != null) onOpenFile.accept(created);
      } catch (IOException ex) {
        showLaunchError("Failed to create file:\n" + ex.getMessage());
      }
    });
  }

  private void copyToClipboard(String text) {
    if (text == null || text.isBlank()) return;
    ClipboardContent content = new ClipboardContent();
    content.putString(text);
    Clipboard.getSystemClipboard().setContent(content);
    setStatus("Copied: " + text);
  }

  private void revealFile(File target) {
    if (target == null) return;
    if (EditorPathExplorer.show(getScene() == null ? null : getScene().getWindow(), target)) {
      setStatus("Revealed " + target.getAbsolutePath());
    } else showLaunchError("Failed to reveal:\n" + target.getAbsolutePath());
  }

  private void refreshWorkspace() {
    String previousSelection = selectedNode != null && selectedNode.file() != null
        ? selectedNode.file().getAbsolutePath()
        : null;

    File launchRoot = resolveLaunchRoot();
    snapshot = ScriptEditorWorkspaceModel.index(launchRoot);

    if (launchRoot == null) {
      projectLabel.setText("No project or workspace root available");
      scriptsRootLabel.setText("Load or create a project to browse text files");
    } else {
      String rootKind = projectRoot != null && projectRoot.equals(launchRoot) ? "Project" : "Workspace";
      projectLabel.setText(rootKind + ": " + launchRoot.getName());
      scriptsRootLabel.setText(snapshot.hasContentRoot()
          ? snapshot.contentRoot().toString().replace('\\', '/')
          : "No text workspace found under " + launchRoot.getAbsolutePath());
    }

    scriptsStat.setText(Integer.toString(snapshot.scripts().size()));
    foldersStat.setText(Integer.toString(snapshot.folderCount()));
    labelsStat.setText(Integer.toString(snapshot.totalLabelCount()));

    rebuildExplorerTree();
    if (previousSelection != null) {
      restoreSelection(previousSelection);
    }
    if (explorerTree.getSelectionModel().getSelectedItem() == null) {
      selectFirstLeaf();
    }
    updateSelectionInspector();
    updateActionState();
  }

  private void rebuildExplorerTree() {
    List<ScriptFileEntry> visibleScripts = ScriptEditorWorkspaceModel.filter(snapshot.scripts(), filterField.getText());
    TreeItem<ExplorerNode> rootItem = buildExplorerTree(visibleScripts);
    explorerTree.setRoot(rootItem);
    if (rootItem != null) {
      rootItem.setExpanded(true);
      rootItem.getChildren().forEach(child -> child.setExpanded(true));
    }
    explorerHint.setText(visibleScripts.isEmpty()
        ? "No text files match the current filter."
        : "Double-click or press Enter to open the selected text file in the main editor.");
  }

  private TreeItem<ExplorerNode> buildExplorerTree(List<ScriptFileEntry> scripts) {
    String rootName = snapshot.hasContentRoot() ? snapshot.contentRoot().getFileName().toString() : "project";
    TreeItem<ExplorerNode> root = new TreeItem<>(new ExplorerNode(rootName, true, null, null, scripts.size()));
    Map<Path, TreeItem<ExplorerNode>> folders = new HashMap<>();
    folders.put(Path.of(""), root);

    for (ScriptFileEntry entry : scripts) {
      Path relativePath = Path.of(entry.relativePath());
      Path parent = relativePath.getParent();
      TreeItem<ExplorerNode> parentItem = root;
      if (parent != null) {
        Path walk = Path.of("");
        for (Path part : parent) {
          walk = walk.resolve(part.toString());
          TreeItem<ExplorerNode> existing = folders.get(walk);
          if (existing == null) {
            ExplorerNode node = new ExplorerNode(part.toString(), true, walk, null, 0);
            existing = new TreeItem<>(node);
            folders.put(walk, existing);
            parentItem.getChildren().add(existing);
          }
          parentItem = existing;
        }
      }
      parentItem.getChildren().add(new TreeItem<>(new ExplorerNode(entry.displayName(), false, relativePath, entry, 0)));
    }

    sortTree(root);
    updateDirectoryCounts(root);
    return root;
  }

  private void sortTree(TreeItem<ExplorerNode> item) {
    if (item == null) return;
    item.getChildren().sort(Comparator
        .comparing((TreeItem<ExplorerNode> child) -> !child.getValue().directory)
        .thenComparing(child -> child.getValue().displayName.toLowerCase()));
    item.getChildren().forEach(this::sortTree);
  }

  private int updateDirectoryCounts(TreeItem<ExplorerNode> item) {
    if (item == null || item.getValue() == null) return 0;
    if (!item.getValue().directory) return item.getValue().file() != null ? 1 : 0;
    int total = 0;
    for (TreeItem<ExplorerNode> child : item.getChildren()) {
      total += updateDirectoryCounts(child);
    }
    item.getValue().scriptCount = total;
    return total;
  }

  private void restoreSelection(String absolutePath) {
    TreeItem<ExplorerNode> root = explorerTree.getRoot();
    if (root == null || absolutePath == null || absolutePath.isBlank()) return;
    TreeItem<ExplorerNode> match = findByAbsolutePath(root, absolutePath);
    if (match != null) {
      explorerTree.getSelectionModel().select(match);
    }
  }

  private TreeItem<ExplorerNode> findByAbsolutePath(TreeItem<ExplorerNode> item, String absolutePath) {
    if (item == null || item.getValue() == null) return null;
    File file = item.getValue().file();
    if (file != null && absolutePath.equals(file.getAbsolutePath())) return item;
    for (TreeItem<ExplorerNode> child : item.getChildren()) {
      TreeItem<ExplorerNode> match = findByAbsolutePath(child, absolutePath);
      if (match != null) return match;
    }
    return null;
  }

  private void selectFirstLeaf() {
    TreeItem<ExplorerNode> root = explorerTree.getRoot();
    if (root == null) return;
    TreeItem<ExplorerNode> leaf = firstLeaf(root);
    if (leaf != null) explorerTree.getSelectionModel().select(leaf);
  }

  private TreeItem<ExplorerNode> firstLeaf(TreeItem<ExplorerNode> item) {
    if (item == null) return null;
    if (item.getValue() != null && item.getValue().file() != null) return item;
    for (TreeItem<ExplorerNode> child : item.getChildren()) {
      TreeItem<ExplorerNode> leaf = firstLeaf(child);
      if (leaf != null) return leaf;
    }
    return null;
  }

  private void updateSelectionInspector() {
    outlineList.getChildren().clear();
    includesList.getChildren().clear();
    includedByList.getChildren().clear();
    if (selectedNode == null) {
      selectionTitle.setText("Workspace Overview");
      selectionPath.setText(snapshot.hasContentRoot()
          ? snapshot.contentRoot().toString().replace('\\', '/')
          : "No text workspace available.");
      selectionMeta.setText(snapshot.hasContentRoot()
          ? snapshot.scripts().size() + " files • " + snapshot.folderCount() + " folders • "
              + snapshot.totalLabelCount() + " VNS labels"
          : "Open a project with JVN text files to browse it here.");
      outlineList.getChildren().add(emptyHint("Select a text file to inspect its metadata."));
      includesList.getChildren().add(emptyHint("Select a text file to inspect references."));
      includedByList.getChildren().add(emptyHint("Select a text file to inspect reverse references."));
      return;
    }

    if (selectedNode.file() == null) {
      File folder = resolveNodeDirectory(selectedNode);
      selectionTitle.setText(selectedNode.displayName);
      selectionPath.setText(folder != null ? folder.getAbsolutePath() : "Folder");
      selectionMeta.setText(selectedNode.scriptCount + " files under this folder");
      outlineList.getChildren().add(emptyHint("Select a text file to see its outline."));
      includesList.getChildren().add(emptyHint("Select a text file."));
      includedByList.getChildren().add(emptyHint("Select a text file."));
      return;
    }

    ScriptFileEntry entry = selectedNode.entry;
    selectionTitle.setText(entry.displayName());
    selectionPath.setText(entry.projectRelativePath());
    if (entry.kind() == FileEditorTab.Kind.VNS) {
      selectionMeta.setText(fileKindLabel(entry.kind()) + " • " + entry.lineCount() + " lines • "
          + entry.labelCount() + " labels • " + entry.includeCount() + " includes • "
          + humanFileSize(entry.sizeBytes()) + " • modified " + formatModified(entry.lastModifiedMillis()));
    } else {
      selectionMeta.setText(fileKindLabel(entry.kind()) + " • " + entry.lineCount() + " lines • "
          + humanFileSize(entry.sizeBytes()) + " • modified " + formatModified(entry.lastModifiedMillis()));
    }

    if (entry.kind() != FileEditorTab.Kind.VNS) {
      outlineList.getChildren().add(emptyHint("Structured outline is available for VNS files."));
      includesList.getChildren().add(emptyHint("Only VNS files use @include relationships."));
      includedByList.getChildren().add(emptyHint("Only VNS files participate in reverse include lookup."));
      return;
    }

    if (entry.labelNames().isEmpty()) {
      outlineList.getChildren().add(emptyHint("This VNS file does not declare any @label entries."));
    } else {
      int maxLabels = Math.min(entry.labelNames().size(), 50);
      for (int i = 0; i < maxLabels; i++) {
        String labelName = entry.labelNames().get(i);
        Integer lineNo = entry.labelLineNumbers().get(labelName);
        String lineHint = lineNo != null ? " (L" + lineNo + ")" : "";
        Label label = new Label("@label " + labelName + lineHint);
        label.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 11px; -fx-padding: 2 6 2 6; "
            + "-fx-background-color: #222222; -fx-background-radius: 5; -fx-cursor: hand;");
        label.setOnMouseEntered(ev -> label.setStyle("-fx-text-fill: #efefef; -fx-font-size: 11px; -fx-padding: 2 6 2 6; "
            + "-fx-background-color: #303030; -fx-background-radius: 5; -fx-cursor: hand;"));
        label.setOnMouseExited(ev -> label.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 11px; -fx-padding: 2 6 2 6; "
            + "-fx-background-color: #222222; -fx-background-radius: 5; -fx-cursor: hand;"));
        label.setOnMouseClicked(ev -> {
          if (onOpenFileAtLine != null && lineNo != null) {
            onOpenFileAtLine.accept(entry.file(), lineNo);
            setStatus("Jumped to @label " + labelName + " (L" + lineNo + ")");
          } else if (onOpenFile != null) {
            onOpenFile.accept(entry.file());
            setStatus("Opened " + entry.file().getName() + " (@label " + labelName + ")");
          } else {
            launchEditorWindow(entry.file());
          }
        });
        outlineList.getChildren().add(label);
      }
      if (entry.labelNames().size() > maxLabels) {
        outlineList.getChildren().add(emptyHint("+ " + (entry.labelNames().size() - maxLabels) + " more labels"));
      }
    }

    // Includes
    if (entry.includeTargets().isEmpty()) {
      includesList.getChildren().add(emptyHint("This VNS file does not @include any other VNS files."));
    } else {
      for (String target : entry.includeTargets()) {
        ScriptFileEntry resolved = snapshot.findByRelativePath(target);
        Label link = new Label("@include " + target);
        link.setStyle(depLinkStyle(false));
        link.setOnMouseEntered(ev -> link.setStyle(depLinkStyle(true)));
        link.setOnMouseExited(ev -> link.setStyle(depLinkStyle(false)));
        if (resolved != null) {
          link.setOnMouseClicked(ev -> {
            if (onOpenFile != null) {
              onOpenFile.accept(resolved.file());
              setStatus("Opened included file: " + resolved.displayName());
            }
          });
        } else {
          link.setStyle("-fx-text-fill: #a07050; -fx-font-size: 11px; -fx-padding: 2 6 2 6; "
              + "-fx-background-color: #202020; -fx-background-radius: 5;");
          link.setTooltip(new Tooltip("Could not resolve: " + target));
        }
        includesList.getChildren().add(link);
      }
    }

    // Included by
    List<ScriptFileEntry> dependents = snapshot.includedBy(entry.relativePath());
    if (dependents.isEmpty()) {
      includedByList.getChildren().add(emptyHint("No other VNS files @include this file."));
    } else {
      for (ScriptFileEntry dep : dependents) {
        Label link = new Label(dep.relativePath());
        link.setStyle(depLinkStyle(false));
        link.setOnMouseEntered(ev -> link.setStyle(depLinkStyle(true)));
        link.setOnMouseExited(ev -> link.setStyle(depLinkStyle(false)));
        link.setOnMouseClicked(ev -> {
          if (onOpenFile != null) {
            onOpenFile.accept(dep.file());
            setStatus("Opened dependent file: " + dep.displayName());
          }
        });
        includedByList.getChildren().add(link);
      }
    }
  }

  private void runContentSearch(String query) {
    searchResults.getChildren().clear();
    // Find the searchResultsCard parent
    javafx.scene.Parent card = searchResults.getParent();
    if (query == null || query.isBlank()) {
      if (card != null) { card.setVisible(false); card.setManaged(false); }
      return;
    }
    List<ScriptEditorWorkspaceModel.SearchHit> hits =
        ScriptEditorWorkspaceModel.searchContent(snapshot, query, 50);
    if (hits.isEmpty()) {
      searchResults.getChildren().add(emptyHint("No matches for \"" + query + "\"."));
    } else {
      Label summary = new Label(hits.size() + (hits.size() >= 50 ? "+" : "") + " results");
      summary.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 10px; -fx-padding: 0 0 2 0;");
      searchResults.getChildren().add(summary);
      for (ScriptEditorWorkspaceModel.SearchHit hit : hits) {
        String preview = hit.lineText();
        if (preview.length() > 80) preview = preview.substring(0, 80) + "…";
        Label link = new Label(hit.relativePath() + ":" + hit.lineNumber() + "  " + preview);
        link.setStyle(depLinkStyle(false));
        link.setWrapText(false);
        link.setMaxWidth(Double.MAX_VALUE);
        link.setOnMouseEntered(ev -> link.setStyle(depLinkStyle(true)));
        link.setOnMouseExited(ev -> link.setStyle(depLinkStyle(false)));
        link.setOnMouseClicked(ev -> openFileAtLine(hit.file(), hit.lineNumber()));
        searchResults.getChildren().add(link);
      }
    }
    if (card != null) { card.setVisible(true); card.setManaged(true); }
    setStatus("Search: " + hits.size() + " matches for \"" + query + "\"");
  }

  private static String depLinkStyle(boolean hover) {
    return hover
        ? "-fx-text-fill: #e2d0a9; -fx-font-size: 11px; -fx-padding: 2 6 2 6; "
            + "-fx-background-color: #2b2218; -fx-background-radius: 5; -fx-cursor: hand;"
        : "-fx-text-fill: #c4b28f; -fx-font-size: 11px; -fx-padding: 2 6 2 6; "
            + "-fx-background-color: #211912; -fx-background-radius: 5; -fx-cursor: hand;";
  }

  private void updateActionState() {
    boolean hasLaunchRoot = resolveLaunchRoot() != null;
    boolean hasScriptsRoot = snapshot.hasScriptsRoot();
    boolean hasFileSelection = selectedNode != null && selectedNode.file() != null;

    openInEditorButton.setDisable(!hasFileSelection || onOpenFile == null);
    openWindowButton.setDisable(!hasScriptsRoot);
    newScriptButton.setDisable(!hasLaunchRoot);
    refreshButton.setDisable(!hasLaunchRoot);
    revealButton.setDisable(!(hasScriptsRoot || selectedNode != null));
  }

  private void openSelectedInEditor() {
    if (selectedNode == null || selectedNode.file() == null) return;
    if (onOpenFile != null) {
      onOpenFile.accept(selectedNode.file());
      setStatus("Opened " + selectedNode.entry.projectRelativePath() + " in the main editor");
    } else {
      launchEditorWindow(selectedNode.file());
    }
  }

  private void openFileAtLabel(File file, String labelName, Integer lineNo) {
    if (file == null) return;
    if (onOpenFileAtLine != null && lineNo != null) {
      onOpenFileAtLine.accept(file, lineNo);
      setStatus("Jumped to @label " + labelName + " (L" + lineNo + ")");
    } else if (onOpenFile != null) {
      onOpenFile.accept(file);
      setStatus("Opened " + file.getName() + " (@label " + labelName + ")");
    } else {
      launchEditorWindow(file);
    }
  }

  private void openFileAtLine(File file, int lineNo) {
    if (file == null) return;
    if (onOpenFileAtLine != null && lineNo > 0) {
      onOpenFileAtLine.accept(file, lineNo);
      setStatus("Opened " + file.getName() + " at line " + lineNo);
    } else if (onOpenFile != null) {
      onOpenFile.accept(file);
      setStatus("Opened " + file.getName());
    } else {
      launchEditorWindow(file);
    }
  }

  private void revealSelection() {
    File target = null;
    if (selectedNode != null && selectedNode.file() != null) {
      target = selectedNode.file().getParentFile();
    } else if (selectedNode != null && selectedNode.directory) {
      target = resolveNodeDirectory(selectedNode);
    } else if (snapshot.hasContentRoot()) {
      target = snapshot.contentRoot().toFile();
    }
    if (target == null || !target.exists()) {
      setStatus("Nothing to reveal.");
      return;
    }
    if (EditorPathExplorer.show(getScene() == null ? null : getScene().getWindow(), target)) {
      setStatus("Revealed " + target.getAbsolutePath());
    } else showLaunchError("Failed to reveal path:\n" + target.getAbsolutePath());
  }

  private void createNewScript() {
    File launchRoot = resolveLaunchRoot();
    if (launchRoot == null) {
      showLaunchError("No project or workspace root is available for creating text files.");
      return;
    }

    EditorDialogs.promptText(dialogOwner(),
        "New File",
        "Create a new JVN text file",
        "Relative path inside project",
        "scripts/story/new_scene.vns",
        "scripts/story/new_scene.vns",
        "Create").ifPresent(input -> {
      try {
        File created = ScriptEditorWorkspaceModel.createTextFile(launchRoot, input);
        refreshWorkspace();
        restoreSelection(created.getAbsolutePath());
        setStatus("Ready: " + created.getName());
        if (onOpenFile != null) {
          onOpenFile.accept(created);
          setStatus("Created and opened " + created.getName());
        } else {
          launchEditorWindow(created);
        }
      } catch (IOException ex) {
        showLaunchError("Failed to create file:\n" + ex.getMessage());
      }
    });
  }

  // ─── Standalone IDE Window ─────────────────────────────────────

  private void launchEditorWindow(File initialFile) {
    File launchRoot = resolveLaunchRoot();
    if (launchRoot == null) {
      showLaunchError("No project or workspace root is available for the text editor.");
      return;
    }
    Path contentRoot = ScriptEditorWorkspaceModel.resolveTextWorkspaceRoot(launchRoot);
    if (contentRoot == null) {
      showLaunchError("No text workspace was found under:\n" + launchRoot.getAbsolutePath());
      return;
    }

    if (editorWindow != null && editorWindow.isShowing()) {
      if (initialFile != null && editorWindowTabs != null && editorWindowStatus != null && editorWindowLaunchRoot != null) {
        openFileInTab(editorWindowTabs, initialFile, editorWindowLaunchRoot, editorWindowStatus);
      }
      editorWindow.toFront();
      editorWindow.requestFocus();
      return;
    }

    try {
      editorWindow = new Stage();
      editorWindow.setTitle("JVN Text Editor — " + launchRoot.getName());

      BorderPane root = new BorderPane();
      root.getStyleClass().add("script-editor-window-root");

      TreeView<String> fileTree = new TreeView<>();
      configureStandaloneTree(fileTree);
      TreeItem<String> treeRoot = buildFileTree(contentRoot, contentRoot.getFileName().toString());
      treeRoot.setExpanded(true);
      fileTree.setRoot(treeRoot);
      fileTree.setShowRoot(true);
      fileTree.setPrefWidth(220);
      fileTree.getStyleClass().add("script-editor-window-tree");

      TabPane editorTabs = new TabPane();
      editorTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);
      editorTabs.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);
      editorTabs.getStyleClass().addAll("script-editor-tabs", "sidebar-tab-pane");

      Label windowStatus = new Label("Select a text file to begin editing.");
      windowStatus.getStyleClass().add("script-editor-window-status");

      fileTree.setOnMouseClicked(ev -> {
        if (ev.getClickCount() == 2) {
          TreeItem<String> sel = fileTree.getSelectionModel().getSelectedItem();
          if (sel == null || !sel.isLeaf()) return;
          String path = buildTreePath(sel);
          File target = contentRoot.resolve(path).toFile();
          if (target.exists() && target.isFile()) {
            openFileInTab(editorTabs, target, launchRoot, windowStatus);
          }
        }
      });

      BorderPane filePane = new BorderPane(fileTree);
      filePane.setTop(paneHeader("Files", "Project text workspace", SidebarToolIcon.folder("#d0d0d0")));
      filePane.getStyleClass().addAll("script-editor-window-sidebar", "script-editor-card");

      HBox menuShell = buildStandaloneMenuShell(fileTree, contentRoot, launchRoot, editorTabs, windowStatus, filePane);

      SplitPane split = new SplitPane(filePane, editorTabs);
      split.setDividerPositions(0.22);
      split.getStyleClass().add("script-editor-window-split");
      SplitPane.setResizableWithParent(filePane, false);

      root.setTop(menuShell);
      root.setCenter(split);
      root.setBottom(windowStatus);

      Scene scene = new Scene(root, 1100, 700);
      EditorTheme.apply(scene);

      scene.addEventFilter(KeyEvent.KEY_PRESSED, (KeyEvent ev) -> {
        if ((ev.isMetaDown() || ev.isControlDown()) && ev.getCode() == KeyCode.S) {
          if (ev.isShiftDown()) {
            saveAllTabs(editorTabs, windowStatus);
          } else {
            saveActiveTab(editorTabs, windowStatus);
          }
          ev.consume();
        }
      });

      editorWindowTabs = editorTabs;
      editorWindowStatus = windowStatus;
      editorWindowLaunchRoot = launchRoot;

      editorWindow.setScene(scene);
      editorWindow.setOnCloseRequest(e -> {
        editorWindow = null;
        editorWindowTabs = null;
        editorWindowStatus = null;
        editorWindowLaunchRoot = null;
      });
      editorWindow.show();
      if (initialFile != null) {
        openFileInTab(editorTabs, initialFile, launchRoot, windowStatus);
      }
      setStatus("Opened text editor window for " + launchRoot.getName());
    } catch (Exception ex) {
      editorWindow = null;
      editorWindowTabs = null;
      editorWindowStatus = null;
      editorWindowLaunchRoot = null;
      showLaunchError("Failed to open text editor window:\n" + ex.getMessage());
    }
  }

  private HBox buildStandaloneMenuShell(TreeView<String> fileTree,
                                        Path contentRoot,
                                        File launchRoot,
                                        TabPane editorTabs,
                                        Label windowStatus,
                                        BorderPane filePane) {
    Menu file = new Menu("File");
    file.getItems().addAll(
        menuItem("New VNS Script...", () -> createStandaloneFile(fileTree, contentRoot, launchRoot, editorTabs, windowStatus, ".vns"), "Shortcut+N"),
        menuItem("New JES Timeline...", () -> createStandaloneFile(fileTree, contentRoot, launchRoot, editorTabs, windowStatus, ".jes"), null),
        menuItem("New Text File...", () -> createStandaloneFile(fileTree, contentRoot, launchRoot, editorTabs, windowStatus, ".txt"), null),
        new SeparatorMenuItem(),
        menuItem("Open Selected", () -> openSelectedStandaloneFile(fileTree, contentRoot, launchRoot, editorTabs, windowStatus), "Shortcut+O"),
        menuItem("Open Selected in Main Editor", () -> openSelectedStandaloneInMainEditor(fileTree, contentRoot, windowStatus), null),
        new SeparatorMenuItem(),
        menuItem("Save", () -> saveActiveTab(editorTabs, windowStatus), "Shortcut+S"),
        menuItem("Save All", () -> saveAllTabs(editorTabs, windowStatus), "Shortcut+Shift+S"),
        new SeparatorMenuItem(),
        menuItem("Reveal Active File", () -> revealActiveStandaloneFile(editorTabs, windowStatus), null),
        menuItem("Reveal Workspace", () -> revealFile(contentRoot.toFile()), null),
        new SeparatorMenuItem(),
        menuItem("Close Tab", () -> closeActiveStandaloneTab(editorTabs, windowStatus), "Shortcut+W"),
        menuItem("Close All Tabs", () -> closeAllStandaloneTabs(editorTabs, windowStatus), "Shortcut+Shift+W"),
        new SeparatorMenuItem(),
        menuItem("Close Window", () -> {
          Window window = editorWindow != null ? editorWindow : getScene() == null ? null : getScene().getWindow();
          if (window instanceof Stage stage) stage.close();
        }, null)
    );

    Menu edit = new Menu("Edit");
    edit.getItems().addAll(
        menuItem("Undo", () -> {
          TextEditorSession ed = activeEditor(editorTabs);
          if (ed != null) ed.undo();
        }, "Shortcut+Z"),
        menuItem("Redo", () -> {
          TextEditorSession ed = activeEditor(editorTabs);
          if (ed != null) ed.redo();
        }, "Shortcut+Shift+Z"),
        new SeparatorMenuItem(),
        menuItem("Find / Replace", () -> {
          TextEditorSession ed = activeEditor(editorTabs);
          if (ed != null) ed.showSearchBar();
        }, "Shortcut+F"),
        new SeparatorMenuItem(),
        menuItem("Copy Active Absolute Path", () -> copyActiveStandalonePath(editorTabs, false, launchRoot, windowStatus), null),
        menuItem("Copy Active Relative Path", () -> copyActiveStandalonePath(editorTabs, true, launchRoot, windowStatus), null)
    );

    Menu navigate = new Menu("Navigate");
    navigate.getItems().addAll(
        menuItem("Focus Project Tree", fileTree::requestFocus, null),
        menuItem("Focus Editor", () -> {
          TextEditorSession ed = activeEditor(editorTabs);
          if (ed != null) ed.node().requestFocus();
        }, null),
        new SeparatorMenuItem(),
        menuItem("Next Tab", () -> selectStandaloneTab(editorTabs, 1), null),
        menuItem("Previous Tab", () -> selectStandaloneTab(editorTabs, -1), null),
        new SeparatorMenuItem(),
        menuItem("Select First File", () -> selectFirstStandaloneFile(fileTree, windowStatus), null)
    );

    CheckMenuItem showProjectTree = new CheckMenuItem("Show Project Tree");
    showProjectTree.setSelected(true);
    showProjectTree.setOnAction(e -> {
      boolean show = showProjectTree.isSelected();
      filePane.setVisible(show);
      filePane.setManaged(show);
      windowStatus.setText(show ? "Project tree shown." : "Project tree hidden.");
    });

    Menu view = new Menu("View");
    view.getItems().addAll(
        showProjectTree,
        menuItem("Refresh Project Tree", () -> refreshStandaloneFileTree(fileTree, contentRoot, windowStatus), "Shortcut+R"),
        new SeparatorMenuItem(),
        menuItem("Increase Editor Font", () -> adjustStandaloneFontSize(editorTabs, windowStatus, 1.0), null),
        menuItem("Decrease Editor Font", () -> adjustStandaloneFontSize(editorTabs, windowStatus, -1.0), null),
        menuItem("Reset Editor Font", () -> setStandaloneFontSize(editorTabs, windowStatus, 13.0), null)
    );

    Menu tools = new Menu("Tools");
    tools.getItems().addAll(
        menuItem("Reload Active File From Disk", () -> reloadActiveStandaloneFile(editorTabs, launchRoot, windowStatus), null),
        menuItem("Copy Workspace Path", () -> copyText(contentRoot.toString(), "Workspace path copied.", windowStatus), null),
        menuItem("Refresh Workspace Snapshot", () -> {
          refreshWorkspace();
          refreshStandaloneFileTree(fileTree, contentRoot, windowStatus);
        }, null)
    );

    Menu help = new Menu("Help");
    help.getItems().addAll(
        menuItem("Keyboard Shortcuts", () -> showStandaloneShortcuts(windowStatus), null),
        menuItem("About JVN Text Editor", () -> EditorDialogs.info(
            editorWindow,
            "JVN Text Editor",
            "Project text editor for VNS, JES, Java, story map, theme, and layout files."), null)
    );

    MenuBar menuBar = new MenuBar(file, edit, navigate, view, tools, help);
    menuBar.getStyleClass().add("script-editor-menubar");
    HBox.setHgrow(menuBar, Priority.ALWAYS);

    Label workspaceChip = toolbarChip("Workspace", launchRoot.getName());
    HBox menuShell = new HBox(8, menuBar, workspaceChip);
    menuShell.getStyleClass().addAll("script-editor-toolbar", "script-editor-menu-shell");
    menuShell.setPadding(new Insets(2, 10, 2, 4));
    menuShell.setAlignment(Pos.CENTER_LEFT);
    return menuShell;
  }

  private File resolveLaunchRoot() {
    if (projectRoot != null && projectRoot.isDirectory()) return projectRoot;
    if (workspaceRoot != null && workspaceRoot.isDirectory()) return workspaceRoot;
    return null;
  }

  private void showLaunchError(String message) {
    setStatus(message);
    EditorDialogs.error(
        dialogOwner(),
        "Text Editor",
        "Could not complete the text editor action.\n" + message,
        null,
        "Confirm the target file and project root still exist.",
        "Review the text editor command in Launcher Settings if this uses a custom editor.");
  }

  private Window dialogOwner() {
    if (editorWindow != null) return editorWindow;
    if (getScene() != null) return getScene().getWindow();
    return null;
  }

  private void setStatus(String message) {
    if (onStatus != null && message != null && !message.isBlank()) onStatus.accept(message);
  }

  private TreeItem<String> buildFileTree(Path dir, String displayName) {
    TreeItem<String> item = new TreeItem<>(displayName);
    if (dir == null || !Files.isDirectory(dir)) return item;
    try (var stream = Files.list(dir)) {
      stream.sorted().forEach(p -> {
        String name = p.getFileName().toString();
        if (Files.isDirectory(p)) {
          if (isIgnoredDirectory(p)) return;
          TreeItem<String> child = buildFileTree(p, name);
          if (!child.getChildren().isEmpty()) {
            child.setExpanded(true);
            item.getChildren().add(child);
          }
        } else if (FileEditorTab.supportsTextEditing(p.toFile())) {
          item.getChildren().add(new TreeItem<>(name));
        }
      });
    } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
    }
    return item;
  }

  private String buildTreePath(TreeItem<String> item) {
    List<String> parts = new ArrayList<>();
    TreeItem<String> cur = item;
    while (cur != null && cur.getParent() != null) {
      parts.add(0, cur.getValue());
      cur = cur.getParent();
    }
    return String.join("/", parts);
  }

  private void refreshStandaloneFileTree(TreeView<String> fileTree, Path contentRoot, Label status) {
    if (fileTree == null || contentRoot == null) return;
    TreeItem<String> refreshedRoot = buildFileTree(contentRoot, contentRoot.getFileName().toString());
    refreshedRoot.setExpanded(true);
    fileTree.setRoot(refreshedRoot);
    if (status != null) status.setText("Project tree refreshed.");
  }

  private void openSelectedStandaloneFile(TreeView<String> fileTree,
                                          Path contentRoot,
                                          File launchRoot,
                                          TabPane tabs,
                                          Label status) {
    File target = selectedStandaloneFile(fileTree, contentRoot);
    if (target == null || !target.isFile()) {
      if (status != null) status.setText("Select a file to open.");
      return;
    }
    openFileInTab(tabs, target, launchRoot, status);
  }

  private void openSelectedStandaloneInMainEditor(TreeView<String> fileTree, Path contentRoot, Label status) {
    File target = selectedStandaloneFile(fileTree, contentRoot);
    if (target == null || !target.isFile()) {
      if (status != null) status.setText("Select a file to open in the main editor.");
      return;
    }
    if (onOpenFile != null) {
      onOpenFile.accept(target);
      if (status != null) status.setText("Opened in main editor: " + target.getName());
    } else if (status != null) {
      status.setText("Main editor open action is unavailable.");
    }
  }

  private File selectedStandaloneFile(TreeView<String> fileTree, Path contentRoot) {
    if (fileTree == null || contentRoot == null) return null;
    TreeItem<String> selected = fileTree.getSelectionModel().getSelectedItem();
    if (selected == null || !selected.isLeaf()) return null;
    String path = buildTreePath(selected);
    File target = contentRoot.resolve(path).normalize().toFile();
    return target.isFile() ? target : null;
  }

  private Path selectedStandaloneDirectory(TreeView<String> fileTree, Path contentRoot) {
    if (contentRoot == null) return null;
    TreeItem<String> selected = fileTree == null ? null : fileTree.getSelectionModel().getSelectedItem();
    if (selected == null || selected.getParent() == null) return contentRoot;
    Path resolved = contentRoot.resolve(buildTreePath(selected)).normalize();
    if (Files.isRegularFile(resolved)) {
      Path parent = resolved.getParent();
      return parent == null ? contentRoot : parent;
    }
    return Files.isDirectory(resolved) ? resolved : contentRoot;
  }

  private void createStandaloneFile(TreeView<String> fileTree,
                                    Path contentRoot,
                                    File launchRoot,
                                    TabPane tabs,
                                    Label status,
                                    String extension) {
    Path directory = selectedStandaloneDirectory(fileTree, contentRoot);
    if (directory == null) return;
    String sample = switch (extension) {
      case ".vns" -> "new_scene.vns";
      case ".jes" -> "new_animation.jes";
      default -> "notes.txt";
    };
    var result = EditorDialogs.promptText(
        editorWindow,
        "New File",
        "Create a text file in the selected folder.",
        "File name",
        sample,
        sample,
        "Create");
    if (result.isEmpty()) return;
    String name = result.get().trim().replace('\\', '/');
    if (name.isBlank()) return;
    if (!name.contains(".") && extension != null && !extension.isBlank()) name += extension;
    Path target = directory.resolve(name).normalize();
    if (!target.startsWith(contentRoot)) {
      if (status != null) status.setText("File must stay inside the text workspace.");
      return;
    }
    if (Files.exists(target)) {
      if (status != null) status.setText("File already exists: " + target.getFileName());
      return;
    }
    try {
      Path parent = target.getParent();
      if (parent != null) Files.createDirectories(parent);
      Files.writeString(target, defaultStandaloneContent(extension));
      refreshStandaloneFileTree(fileTree, contentRoot, status);
      openFileInTab(tabs, target.toFile(), launchRoot, status);
    } catch (IOException ex) {
      if (status != null) status.setText("Create failed: " + ex.getMessage());
    }
  }

  private static String defaultStandaloneContent(String extension) {
    return switch (extension) {
      case ".vns" -> "# New scene\n@scenario new_scene\n\n@label start\n";
      case ".jes" -> "// New timeline\n";
      default -> "";
    };
  }

  private void revealActiveStandaloneFile(TabPane tabs, Label status) {
    File file = activeTabFile(tabs);
    if (file == null) {
      if (status != null) status.setText("No active file to reveal.");
      return;
    }
    revealFile(file);
  }

  private void copyActiveStandalonePath(TabPane tabs, boolean relative, File launchRoot, Label status) {
    File file = activeTabFile(tabs);
    if (file == null) {
      if (status != null) status.setText("No active file path to copy.");
      return;
    }
    String text = file.getAbsolutePath();
    if (relative && launchRoot != null) {
      try {
        text = launchRoot.toPath().relativize(file.toPath()).toString().replace('\\', '/');
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      }
    }
    copyText(text, relative ? "Relative path copied." : "Absolute path copied.", status);
  }

  private static File activeTabFile(TabPane tabs) {
    if (tabs == null) return null;
    Tab selected = tabs.getSelectionModel().getSelectedItem();
    if (selected == null) return null;
    Object file = selected.getProperties().get("file");
    return file instanceof File value ? value : null;
  }

  private static void copyText(String text, String message, Label status) {
    if (text == null) return;
    ClipboardContent content = new ClipboardContent();
    content.putString(text);
    Clipboard.getSystemClipboard().setContent(content);
    if (status != null && message != null) status.setText(message);
  }

  private void closeActiveStandaloneTab(TabPane tabs, Label status) {
    if (tabs == null) return;
    Tab selected = tabs.getSelectionModel().getSelectedItem();
    if (selected == null) return;
    if (isTabDirty(selected)) {
      if (status != null) status.setText("Save unsaved changes before closing this tab.");
      return;
    }
    tabs.getTabs().remove(selected);
    if (status != null) status.setText("Closed tab.");
  }

  private void closeAllStandaloneTabs(TabPane tabs, Label status) {
    if (tabs == null) return;
    for (Tab tab : tabs.getTabs()) {
      if (isTabDirty(tab)) {
        if (status != null) status.setText("Save unsaved changes before closing all tabs.");
        return;
      }
    }
    tabs.getTabs().clear();
    if (status != null) status.setText("Closed all tabs.");
  }

  private static void selectStandaloneTab(TabPane tabs, int direction) {
    if (tabs == null || tabs.getTabs().isEmpty()) return;
    int current = tabs.getSelectionModel().getSelectedIndex();
    int next = Math.floorMod(current + direction, tabs.getTabs().size());
    tabs.getSelectionModel().select(next);
  }

  private void selectFirstStandaloneFile(TreeView<String> fileTree, Label status) {
    TreeItem<String> leaf = firstStandaloneLeaf(fileTree == null ? null : fileTree.getRoot());
    if (leaf == null) {
      if (status != null) status.setText("No file found in the project tree.");
      return;
    }
    fileTree.getSelectionModel().select(leaf);
    fileTree.scrollTo(fileTree.getRow(leaf));
    fileTree.requestFocus();
  }

  private TreeItem<String> firstStandaloneLeaf(TreeItem<String> item) {
    if (item == null) return null;
    if (item.isLeaf() && item.getParent() != null) return item;
    for (TreeItem<String> child : item.getChildren()) {
      TreeItem<String> leaf = firstStandaloneLeaf(child);
      if (leaf != null) return leaf;
    }
    return null;
  }

  private void adjustStandaloneFontSize(TabPane tabs, Label status, double delta) {
    setStandaloneFontSize(tabs, status, codeEditorFontSize + delta);
  }

  private void setStandaloneFontSize(TabPane tabs, Label status, double fontSize) {
    codeEditorFontSize = Math.max(8.0, Math.min(30.0, fontSize));
    if (tabs != null) {
      for (Tab tab : tabs.getTabs()) {
        Object editor = tab.getProperties().get("editor");
        if (editor instanceof TextEditorSession session) session.setFontSize(codeEditorFontSize);
      }
    }
    if (status != null) status.setText("Editor font: " + (int) codeEditorFontSize + "px");
  }

  private void reloadActiveStandaloneFile(TabPane tabs, File launchRoot, Label status) {
    if (tabs == null) return;
    Tab selected = tabs.getSelectionModel().getSelectedItem();
    if (selected == null) return;
    if (isTabDirty(selected)) {
      if (status != null) status.setText("Save unsaved changes before reloading from disk.");
      return;
    }
    Object fileObj = selected.getProperties().get("file");
    Object editorObj = selected.getProperties().get("editor");
    if (!(fileObj instanceof File file) || !(editorObj instanceof TextEditorSession editor)) return;
    try {
      String text = Files.readString(file.toPath());
      editor.setText(text);
      selected.getProperties().put("savedContent", text);
      markTabClean(selected);
      if (status != null) {
        String rel = launchRoot == null ? file.getName() : launchRoot.toPath().relativize(file.toPath()).toString().replace('\\', '/');
        status.setText("Reloaded: " + rel);
      }
    } catch (IOException ex) {
      if (status != null) status.setText("Reload failed: " + ex.getMessage());
    }
  }

  private void showStandaloneShortcuts(Label status) {
    EditorDialogs.showTextBlock(
        editorWindow,
        "Keyboard Shortcuts",
        "JVN Text Editor",
        """
        File
        Ctrl/Cmd+N        New VNS script
        Ctrl/Cmd+O        Open selected file
        Ctrl/Cmd+S        Save active file
        Ctrl/Cmd+Shift+S  Save all files
        Ctrl/Cmd+W        Close active tab
        Ctrl/Cmd+Shift+W  Close all clean tabs

        Edit
        Ctrl/Cmd+Z        Undo
        Ctrl/Cmd+Shift+Z  Redo
        Ctrl/Cmd+F        Find / replace
        Ctrl/Cmd+R        Refresh project tree
        """,
        "Close");
    if (status != null) status.setText("Displayed keyboard shortcuts.");
  }

  private void openFileInTab(TabPane tabs, File file, File launchRoot, Label status) {
    for (Tab t : tabs.getTabs()) {
      if (file.getAbsolutePath().equals(t.getUserData())) {
        tabs.getSelectionModel().select(t);
        return;
      }
    }

    String content;
    try {
      content = Files.readString(file.toPath());
    } catch (IOException ex) {
      status.setText("Error reading: " + file.getName());
      return;
    }

    TextEditorSession editor = createTextEditor(file, launchRoot);
    if (editor == null) {
      status.setText("Unsupported text file: " + file.getName());
      return;
    }
    editor.setFontSize(codeEditorFontSize);
    editor.setText(content);

    String baseName = file.getName();
    String rel = launchRoot.toPath().relativize(file.toPath()).toString().replace('\\', '/');

    Tab tab = new Tab(baseName, editor.node());
    tab.setUserData(file.getAbsolutePath());
    tab.setTooltip(new Tooltip(rel));
    tab.getProperties().put("file", file);
    tab.getProperties().put("editor", editor);
    tab.getProperties().put("baseName", baseName);
    tab.getProperties().put("savedContent", content);
    tab.getProperties().put("dirty", Boolean.FALSE);
    markTabClean(tab);

    editor.setOnTextChanged(text -> {
      String saved = (String) tab.getProperties().getOrDefault("savedContent", "");
      boolean wasDirty = isTabDirty(tab);
      boolean nowDirty = !text.equals(saved);
      if (nowDirty != wasDirty) {
        tab.getProperties().put("dirty", nowDirty);
        if (nowDirty) markTabDirty(tab); else markTabClean(tab);
      }
    });

    tabs.getTabs().add(tab);
    tabs.getSelectionModel().select(tab);
    status.setText("Opened: " + rel);
  }

  private static Button toolbarButton(String text, String tooltip) {
    return toolbarButton(text, tooltip, null, false);
  }

  private static Button toolbarButton(String text, String tooltip, Region icon) {
    return toolbarButton(text, tooltip, icon, false);
  }

  private static Button toolbarButton(String text, String tooltip, Region icon, boolean accent) {
    Button btn = new Button(text);
    if (icon != null) {
      btn.setGraphic(icon);
      btn.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
      btn.setGraphicTextGap(7);
      btn.setAlignment(Pos.CENTER_LEFT);
    }
    btn.getStyleClass().add("script-editor-toolbar-button");
    if (accent) btn.getStyleClass().add("script-editor-toolbar-button-primary");
    btn.setTooltip(new Tooltip(tooltip));
    return btn;
  }

  private static TextEditorSession activeEditor(TabPane tabs) {
    Tab sel = tabs.getSelectionModel().getSelectedItem();
    if (sel == null) return null;
    Object ed = sel.getProperties().get("editor");
    return ed instanceof TextEditorSession session ? session : null;
  }

  private void saveActiveTab(TabPane tabs, Label status) {
    Tab sel = tabs.getSelectionModel().getSelectedItem();
    if (sel == null) return;
    saveTab(sel, status);
  }

  private void saveAllTabs(TabPane tabs, Label status) {
    int saved = 0;
    for (Tab t : tabs.getTabs()) {
      if (isTabDirty(t)) {
        saveTab(t, status);
        saved++;
      }
    }
    if (saved == 0) {
      status.setText("All files are already saved.");
    } else {
      status.setText("Saved " + saved + " file" + (saved > 1 ? "s" : "") + ".");
    }
  }

  private void saveTab(Tab tab, Label status) {
    Object fileObj = tab.getProperties().get("file");
    Object edObj = tab.getProperties().get("editor");
    if (!(fileObj instanceof File file) || !(edObj instanceof TextEditorSession editor)) return;
    String text = editor.getText();
    try {
      Files.writeString(file.toPath(), text);
      tab.getProperties().put("savedContent", text);
      tab.getProperties().put("dirty", Boolean.FALSE);
      markTabClean(tab);
      status.setText("Saved: " + file.getName());
    } catch (IOException ex) {
      status.setText("Save failed: " + file.getName() + " — " + ex.getMessage());
    }
  }

  private static boolean isTabDirty(Tab tab) {
    return Boolean.TRUE.equals(tab.getProperties().get("dirty"));
  }

  private static void markTabDirty(Tab tab) {
    String base = (String) tab.getProperties().getOrDefault("baseName", tab.getText());
    tab.setText("\u25CF " + base);
    tab.setStyle("-fx-text-base-color: #f0a050;");
  }

  private static void markTabClean(Tab tab) {
    String base = (String) tab.getProperties().getOrDefault("baseName", tab.getText());
    tab.setText(base);
    tab.setStyle("-fx-text-base-color: #d0d0d0;");
  }

  private static Button styleActionButton(Button button, Region icon, String tooltip, boolean accent) {
    button.setGraphic(icon);
    button.setAlignment(Pos.CENTER_LEFT);
    button.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
    button.setGraphicTextGap(8);
    button.setMaxWidth(Double.MAX_VALUE);
    button.setMinHeight(32);
    button.getStyleClass().removeAll("script-editor-action-button", "script-editor-action-button-accent");
    button.getStyleClass().add("script-editor-action-button");
    if (accent) button.getStyleClass().add("script-editor-action-button-accent");
    if (tooltip != null && !tooltip.isBlank()) button.setTooltip(new Tooltip(tooltip));
    return button;
  }

  private static VBox compactStatChip(String labelText, Label valueLabel) {
    Label label = new Label(labelText);
    label.getStyleClass().add("script-editor-stat-label");
    VBox box = new VBox(1, label, valueLabel);
    box.getStyleClass().add("script-editor-stat-chip");
    box.setAlignment(Pos.CENTER_LEFT);
    return box;
  }

  private static VBox compactMetaChip(String labelText, Label valueLabel) {
    Label label = new Label(labelText);
    label.getStyleClass().add("script-editor-meta-label");
    valueLabel.setWrapText(true);
    valueLabel.getStyleClass().addAll("script-editor-meta-value", "script-editor-launcher-inline-value");
    VBox box = new VBox(1, label, valueLabel);
    box.getStyleClass().add("script-editor-launcher-meta-chip");
    box.setMaxWidth(Double.MAX_VALUE);
    return box;
  }

  private static VBox statCard(String labelText, Label valueLabel) {
    Label label = new Label(labelText);
    label.getStyleClass().add("script-editor-stat-label");
    VBox box = new VBox(2, label, valueLabel);
    box.setPadding(new Insets(8, 10, 8, 10));
    box.getStyleClass().addAll("script-editor-card", "script-editor-stat-card");
    HBox.setHgrow(box, Priority.ALWAYS);
    box.setMaxWidth(Double.MAX_VALUE);
    return box;
  }

  private static Label statValue(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("script-editor-stat-value");
    return label;
  }

  private static VBox labelledMeta(String labelText, Label valueLabel) {
    Label label = new Label(labelText);
    label.getStyleClass().add("script-editor-meta-label");
    valueLabel.setWrapText(true);
    valueLabel.getStyleClass().add("script-editor-meta-value");
    return new VBox(2, label, valueLabel);
  }

  private static Label sectionLabel(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("script-editor-section-label");
    return label;
  }

  private static HBox paneHeader(String title, String subtitle, Region icon) {
    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().add("script-editor-pane-title");

    Label subtitleLabel = new Label(subtitle);
    subtitleLabel.getStyleClass().add("script-editor-pane-subtitle");

    VBox textBox = new VBox(1, titleLabel, subtitleLabel);
    HBox row = new HBox(8, icon, textBox);
    row.setAlignment(Pos.CENTER_LEFT);
    row.getStyleClass().add("script-editor-pane-header");
    return row;
  }

  private static Label emptyHint(String text) {
    Label label = new Label(text);
    label.setWrapText(true);
    label.getStyleClass().add("script-editor-hint");
    return label;
  }

  private static Label toolbarTitle(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("script-editor-toolbar-title");
    return label;
  }

  private static Label toolbarChip(String label, String value) {
    Label chip = new Label(label + "  " + value);
    chip.getStyleClass().add("script-editor-toolbar-chip");
    return chip;
  }

  private static MenuItem menuItem(String text, Region icon, Runnable action) {
    MenuItem item = new MenuItem(text, icon);
    if (action != null) item.setOnAction(e -> action.run());
    return item;
  }

  private static MenuItem menuItem(String text, Runnable action, String accelerator) {
    MenuItem item = new MenuItem(text);
    if (action != null) item.setOnAction(e -> action.run());
    if (accelerator != null && !accelerator.isBlank()) {
      item.setAccelerator(KeyCombination.keyCombination(accelerator));
    }
    return item;
  }

  private static void configureStandaloneTree(TreeView<String> tree) {
    if (tree == null) return;
    tree.getStyleClass().add("script-editor-tree");
    tree.setCellFactory(view -> new TreeCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          setGraphic(null);
          return;
        }
        TreeItem<String> treeItem = getTreeItem();
        boolean directory = treeItem != null && !treeItem.isLeaf();
        boolean root = treeItem != null && treeItem.getParent() == null;
        Region icon = ProjectFileIcons.iconFor(ProjectFileIcons.kindFor(item, directory, root));
        Label label = new Label(item);
        label.getStyleClass().add(directory ? "script-editor-tree-dir-label" : "script-editor-tree-file-label");
        HBox row = new HBox(6, icon, label);
        row.setAlignment(Pos.CENTER_LEFT);
        setText(null);
        setGraphic(row);
      }
    });
  }

  private static boolean isIgnoredDirectory(Path path) {
    if (path == null) return false;
    Path name = path.getFileName();
    if (name == null) return false;
    String lower = name.toString().toLowerCase();
    return lower.equals(".git")
        || lower.equals(".gradle")
        || lower.equals(".idea")
        || lower.equals(".vscode")
        || lower.equals("build")
        || lower.equals("out")
        || lower.equals("bin")
        || lower.equals("target")
        || lower.equals("node_modules");
  }

  private TextEditorSession createTextEditor(File file, File launchRoot) {
    FileEditorTab.Kind kind = FileEditorTab.detectKind(file);
    if (kind == null) return null;
    return switch (kind) {
      case VNS -> {
        VnsCodeEditor editor = new VnsCodeEditor();
        editor.setProjectRoot(launchRoot);
        yield new TextEditorSession() {
          @Override public javafx.scene.Node node() { return editor; }
          @Override public String getText() { return editor.getText(); }
          @Override public void setText(String text) { editor.setText(text); }
          @Override public void setFontSize(double fontSize) { editor.setFontSizePx(fontSize); }
          @Override public void setOnTextChanged(Consumer<String> listener) { editor.setOnTextChanged(listener); }
          @Override public void showSearchBar() { editor.showSearchBar(); }
          @Override public void undo() { editor.getCodeArea().undo(); }
          @Override public void redo() { editor.getCodeArea().redo(); }
        };
      }
      case JES -> {
        JesCodeEditor editor = new JesCodeEditor();
        editor.setProjectRoot(launchRoot);
        yield new TextEditorSession() {
          @Override public javafx.scene.Node node() { return editor; }
          @Override public String getText() { return editor.getText(); }
          @Override public void setText(String text) { editor.setText(text); }
          @Override public void setFontSize(double fontSize) { editor.setFontSizePx(fontSize); }
          @Override public void setOnTextChanged(Consumer<String> listener) { editor.setOnTextChanged(listener); }
          @Override public void undo() { editor.undo(); }
          @Override public void redo() { editor.redo(); }
        };
      }
      case TIMELINE -> {
        TimelineCodeEditor editor = new TimelineCodeEditor();
        editor.setProjectRoot(launchRoot);
        yield new TextEditorSession() {
          @Override public javafx.scene.Node node() { return editor; }
          @Override public String getText() { return editor.getText(); }
          @Override public void setText(String text) { editor.setText(text); }
          @Override public void setFontSize(double fontSize) { editor.setFontSizePx(fontSize); }
          @Override public void setOnTextChanged(Consumer<String> listener) { editor.setOnTextChanged(listener); }
          @Override public void undo() { editor.undo(); }
          @Override public void redo() { editor.redo(); }
        };
      }
      case JAVA, OTHER -> {
        JavaCodeEditor editor = new JavaCodeEditor();
        yield new TextEditorSession() {
          @Override public javafx.scene.Node node() { return editor; }
          @Override public String getText() { return editor.getText(); }
          @Override public void setText(String text) { editor.setText(text); }
          @Override public void setFontSize(double fontSize) { editor.setFontSizePx(fontSize); }
          @Override public void setOnTextChanged(Consumer<String> listener) { editor.setOnTextChanged(listener); }
          @Override public void showSearchBar() { editor.showSearchBar(); }
          @Override public void undo() { editor.undo(); }
          @Override public void redo() { editor.redo(); }
        };
      }
      case THEME, MENU_SCREEN, MENU_LAYOUT, MENU_STYLE, DIALOGUE_LAYOUT -> {
        JavaCodeEditor editor = new JavaCodeEditor();
        editor.useDslHighlighting();
        yield new TextEditorSession() {
          @Override public javafx.scene.Node node() { return editor; }
          @Override public String getText() { return editor.getText(); }
          @Override public void setText(String text) { editor.setText(text); }
          @Override public void setFontSize(double fontSize) { editor.setFontSizePx(fontSize); }
          @Override public void setOnTextChanged(Consumer<String> listener) { editor.setOnTextChanged(listener); }
          @Override public void showSearchBar() { editor.showSearchBar(); }
          @Override public void undo() { editor.undo(); }
          @Override public void redo() { editor.redo(); }
        };
      }
    };
  }

  private static String fileKindLabel(FileEditorTab.Kind kind) {
    if (kind == null) return "Text File";
    return switch (kind) {
      case JES -> "JES Scene";
      case VNS -> "VNS Script";
      case JAVA -> "Java Source";
      case TIMELINE -> "Story Map";
      case THEME -> "Theme";
      case MENU_SCREEN -> "Menu Screen";
      case MENU_LAYOUT -> "Menu Layout";
      case MENU_STYLE -> "Menu Style";
      case DIALOGUE_LAYOUT -> "Dialogue Layout";
      case OTHER -> "Text File";
    };
  }

  private interface TextEditorSession {
    javafx.scene.Node node();
    String getText();
    void setText(String text);
    void setFontSize(double fontSize);
    void setOnTextChanged(Consumer<String> listener);
    default void showSearchBar() {}
    default void undo() {}
    default void redo() {}
  }

  private static String humanFileSize(long sizeBytes) {
    if (sizeBytes < 1024) return sizeBytes + " B";
    if (sizeBytes < 1024 * 1024) return SIZE_FORMAT.format(sizeBytes / 1024.0) + " KB";
    return SIZE_FORMAT.format(sizeBytes / (1024.0 * 1024.0)) + " MB";
  }

  private static String formatModified(long lastModifiedMillis) {
    if (lastModifiedMillis <= 0) return "unknown";
    return MODIFIED_FORMAT.format(Instant.ofEpochMilli(lastModifiedMillis).atZone(ZoneId.systemDefault()));
  }

  private File resolveNodeDirectory(ExplorerNode node) {
    if (node == null || snapshot.contentRoot() == null) return null;
    Path relative = node.relativePath == null ? Path.of("") : node.relativePath;
    return snapshot.contentRoot().resolve(relative).toFile();
  }

  private static final class ExplorerNode {
    private final String displayName;
    private final boolean directory;
    private final Path relativePath;
    private final ScriptFileEntry entry;
    private int scriptCount;

    private ExplorerNode(String displayName, boolean directory, Path relativePath,
        ScriptFileEntry entry, int scriptCount) {
      this.displayName = displayName;
      this.directory = directory;
      this.relativePath = relativePath;
      this.entry = entry;
      this.scriptCount = scriptCount;
    }

    private File file() {
      return entry != null ? entry.file() : null;
    }
  }
}
