package com.jvn.editor.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class ProjectExplorerView extends VBox {
  private final Label header = new Label("Project");
  private final TextField filter = new TextField();
  private final javafx.scene.layout.StackPane treeContainer = new javafx.scene.layout.StackPane();
  private final VBox emptyStateBox = new VBox(8);
  private final TreeView<File> tree = new TreeView<>();
  private File rootDir;
  private Consumer<File> onOpenFile;
  private Consumer<File> onRunProject;
  private Consumer<File> onBuildProject;

  public ProjectExplorerView() {
    setSpacing(8);
    setPadding(new Insets(10));
    getStyleClass().addAll("project-explorer-root", "sidebar-tool-root");
    header.getStyleClass().addAll("project-explorer-header", "sidebar-tool-title");
    filter.setPromptText("Filter files...");
    filter.getStyleClass().add("project-explorer-filter");
    filter.setTooltip(new Tooltip("Filter project files by name"));
    filter.textProperty().addListener((o, ov, nv) -> refresh());
    filter.setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ESCAPE && !filter.getText().isEmpty()) {
        filter.clear();
        event.consume();
      }
    });

    tree.setShowRoot(true);
    tree.getStyleClass().add("project-explorer-tree");
    tree.setTooltip(new Tooltip("Project file tree. Double-click a file to open it."));
    
    Label emptyTitle = new Label("No project selected");
    emptyTitle.getStyleClass().add("sidebar-empty-title");
    Label emptyMessage = new Label("Open a project folder or create a new one to see files here.");
    emptyMessage.setWrapText(true);
    emptyMessage.getStyleClass().add("project-empty-copy");
    emptyStateBox.getChildren().addAll(emptyTitle, emptyMessage);
    emptyStateBox.setAlignment(Pos.CENTER);
    emptyStateBox.setPadding(new Insets(16));
    emptyStateBox.getStyleClass().add("project-empty-state");
    
    treeContainer.getChildren().add(emptyStateBox);
    VBox.setVgrow(treeContainer, Priority.ALWAYS);

    ContextMenu ctx = new ContextMenu();
    MenuItem miOpen = new MenuItem("Open");
    MenuItem miReveal = new MenuItem("Reveal in JVN Path Explorer");
    MenuItem miNewJes = new MenuItem("New JES Script...");
    MenuItem miNewVns = new MenuItem("New VNS Script...");
    MenuItem miNewJava = new MenuItem("New Java Class...");
    MenuItem miNewFolder = new MenuItem("New Folder...");
    MenuItem miRename = new MenuItem("Rename...");
    MenuItem miDelete = new MenuItem("Delete");
    ctx.getItems().addAll(miOpen, new SeparatorMenuItem(), miNewJes, miNewVns, miNewJava, miNewFolder, new SeparatorMenuItem(), miRename, miDelete, new SeparatorMenuItem(), miReveal);
    tree.setContextMenu(ctx);

    tree.setCellFactory(tv -> new TreeCell<>() {
      private final Label nameLabel = new Label();
      private final Region rootIcon = ProjectFileIcons.iconFor(ProjectFileIcons.Kind.ROOT);
      private final Region spacer = new Region();
      private final Button buildButton =
          new Button("Build", projectActionGraphic(AeroIcon.buildProject(22)));
      private final Button runButton =
          new Button("Run", projectActionGraphic(AeroIcon.runProject(22)));
      private final HBox rootRow = new HBox(6, rootIcon, nameLabel, spacer, buildButton, runButton);
      {
        rootRow.setAlignment(Pos.CENTER_LEFT);
        rootRow.getStyleClass().add("project-explorer-root-row");
        nameLabel.getStyleClass().add("project-explorer-root-name");
        HBox.setHgrow(spacer, Priority.ALWAYS);
        rootRow.prefWidthProperty().bind(widthProperty().subtract(24));
        buildButton.getStyleClass().add("project-run-button");
        buildButton.setContentDisplay(ContentDisplay.LEFT);
        buildButton.setGraphicTextGap(6);
        buildButton.setFocusTraversable(false);
        buildButton.setAccessibleText("Build Project");
        buildButton.setTooltip(new Tooltip("Open Build & Publish for this project"));
        buildButton.setOnAction(e -> {
          File current = getItem();
          if (current == null) return;
          File project = rootDir != null ? rootDir : current;
          if (onBuildProject != null) onBuildProject.accept(project);
          e.consume();
        });
        runButton.getStyleClass().add("project-run-button");
        runButton.setContentDisplay(ContentDisplay.LEFT);
        runButton.setGraphicTextGap(6);
        runButton.setFocusTraversable(false);
        runButton.setAccessibleText("Run Project");
        runButton.setTooltip(new Tooltip("Run this project in JVN Runtime"));
        runButton.setOnAction(e -> {
          File current = getItem();
          if (current == null) return;
          File project = rootDir != null ? rootDir : current;
          if (onRunProject != null) onRunProject.accept(project);
          e.consume();
        });
      }

      @Override protected void updateItem(File item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          setGraphic(null);
          return;
        }

        String display = item.getName().isEmpty() ? item.getAbsolutePath() : item.getName();
        TreeItem<File> ti = getTreeItem();
        boolean isRootNode = ti != null && ti.getParent() == null && sameFile(item, rootDir);
        if (isRootNode) {
          setText(null);
          nameLabel.setText(display);
          setGraphic(rootRow);
        } else {
          Label fileLabel = new Label(display);
          fileLabel.getStyleClass().add(item.isDirectory()
              ? "new-project-wizard-tree-dir-label"
              : "new-project-wizard-tree-file-label");
          HBox row = new HBox(6, ProjectFileIcons.iconFor(ProjectFileIcons.kindFor(item, rootDir)), fileLabel);
          row.setAlignment(Pos.CENTER_LEFT);
          setText(null);
          setGraphic(row);
        }
      }
    });

    tree.setOnMouseClicked(e -> {
      if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
        TreeItem<File> it = tree.getSelectionModel().getSelectedItem();
        if (it != null && it.getValue() != null && it.getValue().isFile()) openFile(it.getValue());
      }
    });
    tree.setOnKeyPressed(event -> {
      TreeItem<File> item = tree.getSelectionModel().getSelectedItem();
      if (event.getCode() == KeyCode.ENTER && item != null && item.getValue() != null) {
        if (item.getValue().isFile()) openFile(item.getValue());
        else item.setExpanded(!item.isExpanded());
        event.consume();
      } else if (event.getCode() == KeyCode.F5) {
        refresh();
        event.consume();
      }
    });

    miOpen.setOnAction(e -> {
      File f = getSelectedFile();
      if (f != null && f.isFile()) openFile(f);
    });
    miReveal.setOnAction(e -> revealSelected());
    miNewJes.setOnAction(e -> createJesInSelected());
    miNewVns.setOnAction(e -> createVnsInSelected());
    miNewJava.setOnAction(e -> createJavaInProject());
    miNewFolder.setOnAction(e -> createFolderInSelected());
    miRename.setOnAction(e -> renameSelected());
    miDelete.setOnAction(e -> deleteSelected());

    Region headerSpacer = new Region();
    HBox.setHgrow(headerSpacer, Priority.ALWAYS);
    Button help = SidebarToolHelp.button(this, "Project Explorer", """
        Browse and manage the current project's files. Double-click a file or press Enter to open
        it. Enter on a folder expands or collapses it; F5 rescans the tree; Escape clears the filter.

        Right-click a file or folder to create scripts/folders, rename, delete, or reveal it in JVN
        Path Explorer. The project root row provides Run and Build actions. Hidden dot-directories
        are intentionally omitted from the tree.""");
    Button refreshButton = new Button();
    refreshButton.getStyleClass().addAll(
        "project-explorer-refresh-button", "aero-help-button", "aero-icon-button");
    refreshButton.setGraphic(AeroIcon.of(AeroIcon.Kind.REFRESH, 24));
    refreshButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    refreshButton.setMinSize(30, 30);
    refreshButton.setPrefSize(30, 30);
    refreshButton.setMaxSize(30, 30);
    refreshButton.setFocusTraversable(false);
    refreshButton.setTooltip(new Tooltip("Rescan project files (F5)"));
    refreshButton.setAccessibleText("Refresh project files");
    refreshButton.setOnAction(event -> refresh());
    HBox headerRow = new HBox(6, header, help, headerSpacer, refreshButton);
    headerRow.setAlignment(Pos.CENTER_LEFT);
    getChildren().addAll(headerRow, filter, treeContainer);
  }

  private static Region projectActionGraphic(AeroIcon icon) {
    javafx.scene.layout.StackPane holder = new javafx.scene.layout.StackPane(icon);
    holder.setMinSize(22, 22);
    holder.setPrefSize(22, 22);
    holder.setMaxSize(22, 22);
    holder.setMouseTransparent(true);
    return holder;
  }

  public void setRootDirectory(File dir) {
    this.rootDir = dir;
    refresh();
  }

  public void setOnOpenFile(Consumer<File> c) { this.onOpenFile = c; }
  public void setOnRunProject(Consumer<File> c) { this.onRunProject = c; }
  public void setOnBuildProject(Consumer<File> c) { this.onBuildProject = c; }

  public File getSelectedFile() {
    TreeItem<File> it = tree.getSelectionModel().getSelectedItem();
    return it == null ? null : it.getValue();
  }

  public void refresh() {
    if (rootDir == null) { 
      tree.setRoot(null);
      if (!treeContainer.getChildren().contains(emptyStateBox)) {
          treeContainer.getChildren().setAll(emptyStateBox);
      }
      return; 
    } else {
      if (!treeContainer.getChildren().contains(tree)) {
          treeContainer.getChildren().setAll(tree);
      }
    }
    String q = filter.getText(); if (q == null) q = ""; String qq = q.toLowerCase(Locale.ROOT);
    TreeItem<File> root = buildTreeFiltered(rootDir, qq);
    tree.setRoot(root);
    if (root != null) root.setExpanded(true);
  }

  private TreeItem<File> buildTreeFiltered(File dir, String queryLower) {
    if (dir == null || !dir.exists()) return null;
    if (dir.isFile()) return new TreeItem<>(dir);
    List<TreeItem<File>> children = new ArrayList<>();
    File[] files = dir.listFiles();
    if (files != null) {
      java.util.Arrays.sort(files, (a,b) -> a.getName().compareToIgnoreCase(b.getName()));
      for (File f : files) {
        if (f.getName().startsWith(".")) continue;
        TreeItem<File> child = buildTreeFiltered(f, queryLower);
        if (child == null) continue;
        boolean match = f.getName().toLowerCase(Locale.ROOT).contains(queryLower) || hasAnyMatchingDescendant(child, queryLower);
        if (queryLower.isEmpty() || match) children.add(child);
      }
    }
    if (!Objects.equals(dir, rootDir) && children.isEmpty() && !dir.getName().toLowerCase(Locale.ROOT).contains(queryLower)) return null;
    TreeItem<File> node = new TreeItem<>(dir);
    node.getChildren().setAll(children);
    return node;
  }

  private boolean hasAnyMatchingDescendant(TreeItem<File> node, String queryLower) {
    if (node == null) return false;
    if (node.getValue() != null && node.getValue().getName().toLowerCase(Locale.ROOT).contains(queryLower)) return true;
    for (TreeItem<File> c : node.getChildren()) if (hasAnyMatchingDescendant(c, queryLower)) return true;
    return false;
  }

  private void openFile(File f) {
    if (onOpenFile != null) { onOpenFile.accept(f); return; }
    EditorPathExplorer.show(getScene() == null ? null : getScene().getWindow(), f);
  }

  private boolean sameFile(File a, File b) {
    if (a == null || b == null) return false;
    return a.getAbsoluteFile().equals(b.getAbsoluteFile());
  }

  private void revealSelected() {
    File f = getSelectedFile(); if (f == null) return;
    EditorPathExplorer.show(getScene() == null ? null : getScene().getWindow(), f);
  }

  private File currentTargetDirectory() {
    File sel = getSelectedFile();
    if (sel == null) return rootDir;
    return sel.isDirectory() ? sel : sel.getParentFile();
  }

  private void createJesInSelected() {
    File dir = currentTargetDirectory(); if (dir == null) return;
    Optional<String> res = EditorDialogs.promptText(
        getScene() != null ? getScene().getWindow() : null,
        "New JES Script",
        "Create a new JES script",
        "File name (without extension)",
        "scene",
        "scene",
        "Create");
    if (res.isEmpty()) return;
    String base = res.get().trim(); if (base.isEmpty()) return;
    File f = new File(dir, base.endsWith(".jes") ? base : base + ".jes");
    if (f.exists()) return;
    try (FileWriter fw = new FileWriter(f)) {
      String sceneName = base.replaceAll("\\.jes$", "");
      fw.write("scene \"" + sceneName + "\" {\n}\n");
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    refresh();
    selectPath(f);
  }

  private void createVnsInSelected() {
    File dir = currentTargetDirectory(); if (dir == null) return;
    Optional<String> res = EditorDialogs.promptText(
        getScene() != null ? getScene().getWindow() : null,
        "New VNS Script",
        "Create a new VNS script",
        "File name (without extension)",
        "story",
        "story",
        "Create");
    if (res.isEmpty()) return;
    String base = res.get().trim(); if (base.isEmpty()) return;
    File f = new File(dir, base.endsWith(".vns") ? base : base + ".vns");
    if (f.exists()) return;
    try (FileWriter fw = new FileWriter(f)) {
      String scen = base.replaceAll("\\.vns$", "");
      fw.write("# New VN Script\n" +
               "@scenario " + scen + "\n\n" +
               "@character narrator \"Narrator\"\n\n" +
               "Narrator: Hello!\n\n" +
               "[end]\n");
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    refresh();
    selectPath(f);
  }

  private void createFolderInSelected() {
    File dir = currentTargetDirectory(); if (dir == null) return;
    Optional<String> res = EditorDialogs.promptText(
        getScene() != null ? getScene().getWindow() : null,
        "New Folder",
        "Create a new folder",
        "Folder name",
        "new-folder",
        "new-folder",
        "Create");
    if (res.isEmpty()) return;
    String name = res.get().trim(); if (name.isEmpty()) return;
    File f = new File(dir, name);
    if (!f.exists()) f.mkdirs();
    refresh();
    selectPath(f);
  }

  private void renameSelected() {
    File f = getSelectedFile(); if (f == null || Objects.equals(f, rootDir)) return;
    Optional<String> res = EditorDialogs.promptText(
        getScene() != null ? getScene().getWindow() : null,
        "Rename",
        "Rename " + f.getName(),
        "New name",
        f.getName(),
        f.getName(),
        "Rename");
    if (res.isEmpty()) return;
    String nn = res.get().trim(); if (nn.isEmpty()) return;
    File nf = new File(f.getParentFile(), nn);
    boolean ok = f.renameTo(nf);
    if (ok) { refresh(); selectPath(nf); }
  }

  private void deleteSelected() {
    File f = getSelectedFile(); if (f == null || Objects.equals(f, rootDir)) return;
    if (!EditorDialogs.confirm(
        getScene() != null ? getScene().getWindow() : null,
        "Confirm Delete",
        "Delete '" + f.getName() + "'?",
        "Delete",
        true)) {
      return;
    }
    try { deleteRecursive(f.toPath()); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    refresh();
  }

  private void deleteRecursive(Path p) throws Exception {
    if (Files.isDirectory(p)) {
      try (var s = Files.list(p)) { s.forEach(pp -> { try { deleteRecursive(pp); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            } }); }
    }
    Files.deleteIfExists(p);
  }

  private void createJavaInProject() {
    File srcRoot = detectJavaSrcRoot(rootDir);
    if (srcRoot == null) srcRoot = currentTargetDirectory();
    Optional<String> pkgRes = EditorDialogs.promptText(
        getScene() != null ? getScene().getWindow() : null,
        "New Java Class",
        "Create a new Java class",
        "Package",
        "com.jvn.game",
        "com.jvn.game",
        "Next");
    if (pkgRes.isEmpty()) return;
    String pkg = pkgRes.get().trim();
    Optional<String> clsRes = EditorDialogs.promptText(
        getScene() != null ? getScene().getWindow() : null,
        "New Java Class",
        "Create a new Java class",
        "Class name",
        "MyClass",
        "MyClass",
        "Create");
    if (clsRes.isEmpty()) return;
    String cls = clsRes.get().trim();
    File destDir = pkg.isEmpty() ? srcRoot : new File(srcRoot, pkg.replace('.', File.separatorChar));
    destDir.mkdirs();
    File f = new File(destDir, cls + ".java");
    if (f.exists()) return;
    try (FileWriter fw = new FileWriter(f)) {
      if (!pkg.isEmpty()) fw.write("package " + pkg + ";\n\n");
      fw.write("public class " + cls + " {\n}\n");
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    refresh();
    selectPath(f);
  }

  private File detectJavaSrcRoot(File base) {
    if (base == null) return null;
    File m = new File(base, "src/main/java");
    if (m.exists() && m.isDirectory()) return m;
    File[] subs = base.listFiles();
    if (subs != null) for (File s : subs) {
      File d = detectJavaSrcRoot(s);
      if (d != null) return d;
    }
    return null;
  }

  private void selectPath(File f) {
    if (f == null) return;
    List<File> chain = new ArrayList<>();
    File cur = f;
    while (cur != null) { chain.add(0, cur); if (Objects.equals(cur, rootDir)) break; cur = cur.getParentFile(); }
    TreeItem<File> node = tree.getRoot();
    for (int i = 1; i < chain.size() && node != null; i++) {
      File want = chain.get(i);
      node.setExpanded(true);
      TreeItem<File> next = null;
      for (TreeItem<File> c : node.getChildren()) if (c.getValue().equals(want)) { next = c; break; }
      node = next;
    }
    if (node != null) tree.getSelectionModel().select(node);
  }
}
