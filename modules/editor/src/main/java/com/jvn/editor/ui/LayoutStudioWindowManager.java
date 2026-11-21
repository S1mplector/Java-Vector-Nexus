package com.jvn.editor.ui;

import java.io.File;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuProfileLoader;
import com.jvn.core.vn.ui.VnUiLayoutLoader;
import com.jvn.core.vn.ui.VnUiLayoutSpec;
import com.jvn.core.vn.ui.VnUiStyleSpec;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Opens visual layout and menu workspaces with source diagnostics, asset helpers,
 * and an authoritative save-and-run workflow.
 */
public class LayoutStudioWindowManager {
  public enum Kind {
    MENU_SCREEN,
    MENU_LAYOUT,
    MENU_STYLE,
    DIALOGUE_LAYOUT
  }

  private final Stage owner;
  private final Consumer<File> runProjectHandler;
  private final Map<String, LayoutStudioWindow> windows = new LinkedHashMap<>();

  public LayoutStudioWindowManager(Stage owner) {
    this(owner, null);
  }

  public LayoutStudioWindowManager(Stage owner, Consumer<File> runProjectHandler) {
    this.owner = owner;
    this.runProjectHandler = runProjectHandler;
  }

  public boolean supports(File file) {
    return detectKind(file) != null;
  }

  public void open(File file, File projectRoot, Consumer<String> statusSink) {
    if (file == null) return;
    Kind kind = detectKind(file);
    if (kind == null) return;

    String key = canonicalPath(file);
    LayoutStudioWindow existing = windows.get(key);
    if (existing != null) {
      existing.setProjectRoot(projectRoot);
      existing.focus();
      if (statusSink != null) statusSink.accept("Focused Studio: " + file.getName());
      return;
    }

    LayoutStudioWindow window = new LayoutStudioWindow(
        owner,
        file,
        kind,
        projectRoot,
        statusSink,
        runProjectHandler,
        () -> openMenuDocuments(resolveProjectRoot(file, projectRoot)),
        () -> windows.remove(key)
    );
    windows.put(key, window);
    window.show();
    if (statusSink != null) statusSink.accept("Opened in Studio: " + file.getName());
  }

  private Map<String, String> openMenuDocuments(File project) {
    Map<String, String> documents = new LinkedHashMap<>();
    for (LayoutStudioWindow window : windows.values()) {
      if (window.kind != Kind.DIALOGUE_LAYOUT && window.dirty
          && canonicalPath(project).equals(canonicalPath(window.resolveProjectRootForRun()))) {
        documents.put(LayoutRuntimePreview.menuPath(window.kind, window.file), window.codeEditor.getText());
      }
    }
    return documents;
  }

  public boolean requestCloseAll() {
    List<LayoutStudioWindow> copy = new ArrayList<>(windows.values());
    for (LayoutStudioWindow window : copy) {
      if (window == null) continue;
      if (!window.requestClose()) return false;
    }
    return true;
  }

  public boolean saveAllDirty(Consumer<String> statusSink) {
    boolean ok = true;
    List<LayoutStudioWindow> copy = new ArrayList<>(windows.values());
    for (LayoutStudioWindow window : copy) {
      if (window == null || !window.isDirty()) continue;
      if (!window.saveIfDirty()) {
        ok = false;
        if (statusSink != null) {
          statusSink.accept("Failed to save Studio file: " + window.fileName());
        }
      }
    }
    return ok;
  }

  private static Kind detectKind(File file) {
    if (file == null) return null;
    String name = file.getName().toLowerCase(Locale.ROOT);
    String path = file.getPath().replace('\\', '/').toLowerCase(Locale.ROOT);

    if (name.endsWith(".menu")) return Kind.MENU_SCREEN;
    if (name.endsWith(".layout") && (path.contains("/config/menu/layouts/") || path.contains("/menu/layouts/") || path.contains("/config/menu/"))) {
      return Kind.MENU_LAYOUT;
    }
    if (name.endsWith(".style") || path.contains("/config/menu/styles/")) return Kind.MENU_STYLE;
    if ("dialogue.layout".equals(name) || (name.endsWith(".layout") && (path.contains("/config/ui/") || path.contains("/config/vn/")))) {
      return Kind.DIALOGUE_LAYOUT;
    }
    return null;
  }

  private static File resolveProjectRoot(File file, File fallback) {
    if (fallback != null && fallback.isDirectory()) return fallback;
    File cursor = file.getAbsoluteFile().getParentFile();
    while (cursor != null) {
      if (new File(cursor, "jvn.project").isFile()) return cursor;
      cursor = cursor.getParentFile();
    }
    return null;
  }

  private static String canonicalPath(File file) {
    if (file == null) return "";
    try {
      return file.getCanonicalPath();
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return file.getAbsolutePath();
    }
  }

  private static void applyLinuxDefaultWindowState(Stage stage) {
    if (stage == null || !isLinux()) return;
    stage.setIconified(false);
    stage.setMaximized(true);
    Platform.runLater(() -> {
      stage.setIconified(false);
      stage.setMaximized(true);
    });
  }

  private static boolean isLinux() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
  }

  private static String normalizeLineEndings(String text) {
    if (text == null) return "";
    return text.replace("\r\n", "\n").replace('\r', '\n');
  }

  private static String normalize(String value, String fallback) {
    if (value == null) return fallback;
    String t = value.trim();
    return t.isBlank() ? fallback : t;
  }

  private static String sanitizeId(String raw) {
    if (raw == null) return "";
    String t = raw.trim().toLowerCase(Locale.ROOT);
    if (t.isBlank()) return "";
    t = t.replace('-', '_').replace(' ', '_');
    t = t.replaceAll("[^a-z0-9_]", "");
    t = t.replaceAll("_+", "_");
    if (t.startsWith("_")) t = t.substring(1);
    if (t.endsWith("_")) t = t.substring(0, t.length() - 1);
    return t;
  }

  private static final class LayoutStudioWindow {
    private final Stage stage;
    private final File file;
    private final Kind kind;
    private final Consumer<String> externalStatus;
    private final Consumer<File> runProjectHandler;
    private final Runnable onClosed;

    private File projectRoot;
    private boolean syncing;
    private boolean dirty;
    private String savedSnapshot = "";

    private final JavaCodeEditor codeEditor = new JavaCodeEditor();
    private final Label status = new Label("Ready.");
    private final Label fileLabel = new Label();
    private final Label dirtyBadge = new Label();

    private final LayoutDesignView design;
    private final TabPane modes = new TabPane();
    private final Tab designTab = new Tab("Design");

    private final TextField assetPathField = new TextField();
    private final ComboBox<String> assetKeyBox = new ComboBox<>();
    private final TextField assetItemIdField = new TextField();
    private final ComboBox<String> templateBox = new ComboBox<>();
    private final Button applyTemplateButton = new Button("Replace with Template");
    private final Button copyTemplateButton = new Button("Copy Template");
    private final Button openGuideButton = new Button("Open Layout Guide");
    private final ComboBox<String> keyReferenceBox = new ComboBox<>();
    private final Button copyKeyButton = new Button("Copy Key");

    private final Button saveButton = new Button();
    private final Button runButton = new Button();
    private final Button reloadButton = new Button();
    private final Button revealButton = new Button();
    private final Button maximizeButton = new Button();

    private final Button browseAssetButton = new Button();
    private final Button importAssetButton = new Button();
    private final Button revealAssetButton = new Button();
    private final Button clearAssetButton = new Button();
    private final Button copyPathButton = new Button();
    private final Button applyPathButton = new Button();

    LayoutStudioWindow(Stage owner,
                       File file,
                       Kind kind,
                       File projectRoot,
                       Consumer<String> statusSink,
                       Consumer<File> runProjectHandler,
                       Supplier<Map<String, String>> openDocuments,
                       Runnable onClosed) {
      this.file = file;
      this.kind = kind;
      this.projectRoot = projectRoot;
      this.externalStatus = statusSink;
      this.runProjectHandler = runProjectHandler;
      this.onClosed = onClosed;
      codeEditor.useDslHighlighting();
      design = new LayoutDesignView(file, resolveProjectRootForRun(), kind,
          codeEditor::getText, codeEditor::replaceDocumentEdit, codeEditor::undo, codeEditor::redo, this::setStatus, openDocuments);

      this.stage = new Stage();
      stage.setResizable(true);
      stage.setMinWidth(900);
      stage.setMinHeight(570);

      BorderPane root = new BorderPane();
      root.getStyleClass().add("layout-studio-root");
      root.setTop(buildToolbar());
      root.setCenter(buildContent());
      root.setBottom(buildStatusBar());

      Scene scene = new Scene(root, 1460, 900);
      scene.getAccelerators().put(
          new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.S, javafx.scene.input.KeyCombination.SHORTCUT_DOWN),
          this::save
      );
      scene.getAccelerators().put(
          new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.ENTER, javafx.scene.input.KeyCombination.SHORTCUT_DOWN),
          this::saveAndRunProject
      );
      EditorTheme.apply(scene);
      scene.getStylesheets().add(getClass().getResource("/com/jvn/editor/ui/layout-design.css").toExternalForm());
      stage.setScene(scene);
      applyLinuxDefaultWindowState(stage);

      bindSync();
      bindButtons();
      loadFromDisk();
      codeEditor.goToLine(1);

      stage.setOnCloseRequest(e -> {
        if (!confirmCloseIfDirty()) {
          e.consume();
        }
      });
      stage.setOnHidden(e -> {
        design.close();
        if (onClosed != null) onClosed.run();
      });
    }

    void show() {
      stage.show();
      applyLinuxDefaultWindowState(stage);
      Platform.runLater(() -> {
        stage.toFront();
        if (modes.getSelectionModel().getSelectedItem() != designTab) codeEditor.requestFocus();
      });
    }

    void focus() {
      if (!stage.isShowing()) {
        stage.show();
      }
      stage.toFront();
      stage.requestFocus();
    }

    boolean requestClose() {
      if (!confirmCloseIfDirty()) return false;
      stage.hide();
      return true;
    }

    boolean isDirty() {
      return dirty;
    }

    boolean saveIfDirty() {
      if (!dirty) return true;
      return save();
    }

    String fileName() {
      return file == null ? "unknown" : file.getName();
    }

    void setProjectRoot(File projectRoot) {
      this.projectRoot = projectRoot;
      updateFileLabel();
      updateAssetUtilityState();
      design.setRoot(resolveProjectRootForRun());
    }

    private Node buildToolbar() {
      Label title = new Label(studioTitle());
      title.getStyleClass().add("layout-studio-title");

      dirtyBadge.getStyleClass().add("layout-studio-dirty");

      Region spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);

      configureIconButton(saveButton, CssIcon.save("#8cd48c"), "Save");
      configureIconButton(runButton, CssIcon.play("#8cd48c"), "Save and Run Runtime (Ctrl/Cmd+Enter)");
      configureIconButton(reloadButton, CssIcon.refresh("#7ec8e3"), "Reload");
      configureIconButton(revealButton, CssIcon.folder("#d4a8e8"), "Reveal File");
      configureIconButton(maximizeButton, CssIcon.expand("#b0b8c8"), "Maximize / Restore");
      maximizeButton.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));

      HBox row = new HBox(8,
          title,
          dirtyBadge,
          spacer,
          saveButton,
          runButton,
          reloadButton,
          revealButton,
          maximizeButton
      );
      row.getStyleClass().add("layout-studio-toolbar");
      row.setAlignment(Pos.CENTER_LEFT);
      row.setPadding(new Insets(8));

      saveButton.getStyleClass().add("layout-studio-toolbar-button");
      runButton.getStyleClass().add("layout-studio-toolbar-button");
      reloadButton.getStyleClass().add("layout-studio-toolbar-button");
      revealButton.getStyleClass().add("layout-studio-toolbar-button");

      return row;
    }

    private Node buildContent() {
      BorderPane sourceView = new BorderPane(codeEditor);
      sourceView.setRight(buildUtilitiesPane());
      sourceView.getStyleClass().add("layout-studio-center");
      var sourceTab = new Tab("Source", sourceView);
      designTab.setContent(design);
      modes.setId("layout-studio-modes");
      modes.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
      modes.getTabs().addAll(designTab, sourceTab);
      modes.getSelectionModel().selectedItemProperty().addListener((obs, old, mode) -> {
        if (mode == designTab) design.refresh();
        else Platform.runLater(codeEditor::requestFocus);
      });
      stage.focusedProperty().addListener((obs, old, focused) -> {
        if (focused && modes.getSelectionModel().getSelectedItem() == designTab) design.sourceChanged();
      });
      return modes;
    }

    private Node buildUtilitiesPane() {
      Label templateTitle = new Label("Source Templates");
      templateTitle.getStyleClass().add("layout-studio-section-title");
      configureTemplates();
      templateBox.setMaxWidth(Double.MAX_VALUE);
      templateBox.setTooltip(new Tooltip("Choose a valid, commented starting point for this file type"));
      applyTemplateButton.setMaxWidth(Double.MAX_VALUE);
      applyTemplateButton.getStyleClass().add("layout-studio-utility-button");
      applyTemplateButton.setTooltip(new Tooltip("Replace the current source after confirmation"));
      copyTemplateButton.setMaxWidth(Double.MAX_VALUE);
      copyTemplateButton.getStyleClass().add("layout-studio-utility-button");
      copyTemplateButton.setTooltip(new Tooltip("Copy the selected commented template without changing the file"));

      Label referenceTitle = new Label("Key Reference");
      referenceTitle.getStyleClass().add("layout-studio-section-title");
      configureKeyReference();
      keyReferenceBox.setMaxWidth(Double.MAX_VALUE);
      keyReferenceBox.setTooltip(new Tooltip("Accepted keys come from the runtime loader contract"));
      copyKeyButton.setMaxWidth(Double.MAX_VALUE);
      copyKeyButton.getStyleClass().add("layout-studio-utility-button");
      openGuideButton.setMaxWidth(Double.MAX_VALUE);
      openGuideButton.getStyleClass().add("layout-studio-utility-button");

      Label utilTitle = new Label("Asset Utilities");
      utilTitle.getStyleClass().add("layout-studio-section-title");

      assetPathField.setPromptText("assets/ui/button.png");
      AssetPickerSupport.installAssetDrop(assetPathField, this::toRelativePath);
      assetPathField.textProperty().addListener((o, ov, nv) -> updateAssetUtilityState());

      assetItemIdField.setPromptText("menu item id");
      assetItemIdField.textProperty().addListener((o, ov, nv) -> updateAssetUtilityState());

      configureAssetKeys();
      assetKeyBox.valueProperty().addListener((o, ov, nv) -> updateAssetUtilityState());

      VBox form = new VBox(6,
          new Label("Value (Asset Path or Literal)"),
          assetPathField,
          new Label("Apply Key"),
          assetKeyBox
      );
      form.getChildren().add(assetItemIdField);

      Label tip = new Label(assetTip());
      tip.setWrapText(true);
      tip.getStyleClass().add("muted");

      VBox buttons = new VBox(6,
          browseAssetButton,
          importAssetButton,
          revealAssetButton,
          clearAssetButton,
          copyPathButton,
          applyPathButton
      );
      configureIconButton(browseAssetButton, CssIcon.folder("#b0b8c8"), "Browse Asset");
      configureIconButton(importAssetButton, CssIcon.download("#8cd48c"), "Import Asset");
      configureIconButton(revealAssetButton, CssIcon.link("#9cc7ff"), "Reveal Asset");
      configureIconButton(clearAssetButton, CssIcon.clearX("#e07070"), "Clear");
      configureIconButton(copyPathButton, CssIcon.copy("#9cc7ff"), "Copy Path");
      configureIconButton(applyPathButton, CssIcon.check("#8cd48c"), "Apply to File");
      for (Node node : buttons.getChildren()) {
        if (node instanceof Button b) {
          b.setMaxWidth(Region.USE_PREF_SIZE);
          b.getStyleClass().add("layout-studio-utility-button");
        }
      }

      VBox panel = new VBox(10,
          templateTitle,
          templateBox,
          applyTemplateButton,
          copyTemplateButton,
          new Separator(),
          referenceTitle,
          keyReferenceBox,
          copyKeyButton,
          openGuideButton,
          new Separator(),
          utilTitle,
          form,
          buttons,
          new Separator(),
          tip
      );
      panel.getStyleClass().add("layout-studio-utils");
      panel.setPadding(new Insets(10));
      panel.setPrefWidth(320);
      panel.setMinWidth(280);
      updateAssetUtilityState();
      return panel;
    }

    private void configureKeyReference() {
      List<String> keys = new ArrayList<>();
      switch (kind) {
        case MENU_LAYOUT -> keys.addAll(LAYOUT_KEYS);
        case MENU_STYLE -> keys.addAll(STYLE_KEYS);
        case MENU_SCREEN -> {
          keys.addAll(SCREEN_TOP_KEYS);
          SCREEN_ITEM_KEYS.stream().map(field -> "item.<id>." + field).forEach(keys::add);
        }
        case DIALOGUE_LAYOUT -> keys.addAll(dialogueReferenceKeys());
      }
      keys.sort(String.CASE_INSENSITIVE_ORDER);
      keyReferenceBox.getItems().setAll(keys);
      if (!keys.isEmpty()) keyReferenceBox.getSelectionModel().selectFirst();
    }

    private static List<String> dialogueReferenceKeys() {
      List<String> keys = new ArrayList<>();
      try {
        Properties properties = new Properties();
        properties.load(new StringReader(LayoutDslTemplates.defaultDialogueLayoutTemplate()));
        keys.addAll(properties.stringPropertyNames());
      } catch (Exception ignored) {
        // The regression-tested built-in template is expected to parse.
      }
      keys.add("textBoxButton.<id>.label");
      keys.add("textBoxButton.<id>.action");
      keys.add("textBoxButton.<id>.target");
      return keys;
    }

    private void configureTemplates() {
      templateBox.getItems().clear();
      switch (kind) {
        case DIALOGUE_LAYOUT -> templateBox.getItems().addAll("Standard dialogue", "Minimal monochrome");
        case MENU_LAYOUT -> templateBox.getItems().addAll("Standard menu", "Minimal right-side menu");
        case MENU_STYLE -> templateBox.getItems().addAll("Standard menu", "Minimal monochrome");
        case MENU_SCREEN -> templateBox.getItems().add("Standard menu screen");
      }
      templateBox.getSelectionModel().selectFirst();
    }

    private Node buildStatusBar() {
      fileLabel.getStyleClass().add("muted");
      status.getStyleClass().add("layout-studio-status");

      VBox box = new VBox(3, fileLabel, status);
      box.setPadding(new Insets(6, 10, 8, 10));
      box.getStyleClass().add("layout-studio-status-bar");
      updateFileLabel();
      return box;
    }

    private void bindSync() {
      codeEditor.setOnTextChanged(text -> {
        if (syncing) return;
        updateDirtyState();
        refreshCodeDiagnostics(text);
        if (modes.getSelectionModel().getSelectedItem() == designTab) design.sourceChanged();
      });
    }

    private static final Set<String> LAYOUT_KEYS = MenuProfileLoader.knownLayoutFields();
    private static final Set<String> STYLE_KEYS = MenuProfileLoader.knownStyleFields();
    private static final Set<String> SCREEN_TOP_KEYS = MenuProfileLoader.knownScreenFields();
    private static final Set<String> SCREEN_ITEM_KEYS = MenuProfileLoader.knownItemFields();

    private void refreshCodeDiagnostics(String text) {
      List<String> rawIssues;
      switch (kind) {
        case MENU_SCREEN -> rawIssues = mergeRuntimeIssues(
            text,
            DslPropertyDiagnostics.menuScreenIssues(text, SCREEN_TOP_KEYS, SCREEN_ITEM_KEYS),
            menuRuntimeDiagnostics(text, kind));
        case MENU_LAYOUT -> rawIssues = mergeRuntimeIssues(
            text,
            DslPropertyDiagnostics.menuLayoutIssues(text, LAYOUT_KEYS),
            menuRuntimeDiagnostics(text, kind));
        case MENU_STYLE -> rawIssues = mergeRuntimeIssues(
            text,
            DslPropertyDiagnostics.menuStyleIssues(text, STYLE_KEYS),
            menuRuntimeDiagnostics(text, kind));
        case DIALOGUE_LAYOUT -> rawIssues = DslPropertyDiagnostics.dialogueIssues(text, dialogueRuntimeDiagnostics(text));
        default -> rawIssues = List.of();
      };
      codeEditor.setDiagnostics(parseDiagnosticStrings(rawIssues));
    }

    private static List<String> mergeRuntimeIssues(String text, List<String> editorIssues, List<String> runtimeIssues) {
      java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>(editorIssues);
      merged.addAll(DslPropertyDiagnostics.runtimeBackedIssues(text, runtimeIssues));
      return List.copyOf(merged);
    }

    private static List<String> menuRuntimeDiagnostics(String text, Kind kind) {
      try {
        Properties properties = new Properties();
        properties.load(new StringReader(text == null ? "" : text));
        return switch (kind) {
          case MENU_LAYOUT -> MenuProfileLoader.validateLayout(properties, "open layout");
          case MENU_STYLE -> MenuProfileLoader.validateStyle(properties, "open style");
          case MENU_SCREEN -> MenuProfileLoader.validateScreen(properties, "open menu");
          default -> List.of();
        };
      } catch (Exception ex) {
        return List.of("Invalid properties syntax: " + ex.getMessage());
      }
    }

    private static List<String> dialogueRuntimeDiagnostics(String text) {
      try {
        Properties properties = new Properties();
        properties.load(new StringReader(text == null ? "" : text));
        return VnUiLayoutLoader.parseWithDiagnostics(
            properties,
            VnUiLayoutSpec.defaults(),
            VnUiStyleSpec.defaults()
        ).diagnostics();
      } catch (Exception ex) {
        return List.of("L1 dsl: Invalid properties syntax: " + ex.getMessage());
      }
    }

    private static List<JavaCodeEditor.Diagnostic> parseDiagnosticStrings(List<String> raw) {
      if (raw == null || raw.isEmpty()) return List.of();
      List<JavaCodeEditor.Diagnostic> out = new ArrayList<>();
      Pattern linePattern = Pattern.compile("^L(\\d+)\\s+(\\S+?):\\s+(.+?)(?:\\s+Quick fix:\\s+(.+))?$");
      for (String s : raw) {
        if (s == null || s.isBlank()) continue;
        Matcher m = linePattern.matcher(s.trim());
        if (m.matches()) {
          int line = Integer.parseInt(m.group(1));
          String key = m.group(2);
          String message = m.group(3);
          String quickFix = m.group(4);
          JavaCodeEditor.Diagnostic.Severity severity =
              message.toLowerCase(Locale.ROOT).contains("unknown") || message.toLowerCase(Locale.ROOT).contains("invalid")
                  ? JavaCodeEditor.Diagnostic.Severity.ERROR
                  : JavaCodeEditor.Diagnostic.Severity.WARNING;
          out.add(new JavaCodeEditor.Diagnostic(line, key, message, quickFix, severity));
        } else {
          out.add(new JavaCodeEditor.Diagnostic(1, "dsl", s, null));
        }
      }
      return out;
    }

    private void bindButtons() {
      saveButton.setOnAction(e -> save());
      runButton.setOnAction(e -> saveAndRunProject());
      reloadButton.setOnAction(e -> reload());
      revealButton.setOnAction(e -> revealInPathExplorer());

      browseAssetButton.setOnAction(e -> browseExistingAsset());
      importAssetButton.setOnAction(e -> importExternalAsset());
      revealAssetButton.setOnAction(e -> revealSelectedAsset());
      clearAssetButton.setOnAction(e -> {
        assetPathField.clear();
        setStatus("Asset value cleared.");
      });
      copyPathButton.setOnAction(e -> copyAssetPath());
      applyPathButton.setOnAction(e -> applyAssetPathToCode());
      applyTemplateButton.setOnAction(e -> applySelectedTemplate());
      copyTemplateButton.setOnAction(e -> copySelectedTemplate());
      copyKeyButton.setOnAction(e -> copyReferenceKey());
      openGuideButton.setOnAction(e -> openLayoutGuide());
    }

    private void copySelectedTemplate() {
      String selected = templateBox.getValue();
      if (selected == null || selected.isBlank()) return;
      copyToClipboard(selectedTemplate(selected));
      setStatus("Copied source template: " + selected + ".");
    }

    private void copyReferenceKey() {
      String key = keyReferenceBox.getValue();
      if (key == null || key.isBlank()) return;
      copyToClipboard(key + "=");
      setStatus("Copied declaration: " + key + "=");
    }

    private static void copyToClipboard(String value) {
      ClipboardContent content = new ClipboardContent();
      content.putString(value == null ? "" : value);
      Clipboard.getSystemClipboard().setContent(content);
    }

    private void openLayoutGuide() {
      String relative = switch (kind) {
        case DIALOGUE_LAYOUT -> "docs/scripting/ui/layout/components/dialogue-layout.md";
        case MENU_LAYOUT -> "docs/scripting/ui/layout/structure/menu-layouts.md";
        case MENU_STYLE -> "docs/scripting/ui/layout/styling/colors-theming.md";
        case MENU_SCREEN -> "docs/scripting/ui/layout/structure/menu-actions.md";
      };
      File local = new File(System.getProperty("user.dir", "."), relative);
      try {
        if (local.isFile()) {
          java.awt.Desktop.getDesktop().open(local);
        } else {
          java.awt.Desktop.getDesktop().browse(new URI(
              "https://github.com/S1mplector/Java-Vector-Nexus/blob/stable/" + relative));
        }
        setStatus("Opened authoring guide.");
      } catch (Exception ex) {
        setStatus("Could not open guide: " + normalize(ex.getMessage(), "desktop integration unavailable"));
      }
    }

    private void applySelectedTemplate() {
      String selected = templateBox.getValue();
      if (selected == null || selected.isBlank()) return;
      if (!EditorDialogs.confirm(
          stage,
          "Replace layout source?",
          "Replace the current file with the “" + selected + "” template? Unsaved edits will be replaced.",
          "Replace",
          true
      )) return;

      String template = selectedTemplate(selected);
      syncing = true;
      codeEditor.setTextNoEvent(template);
      syncing = false;
      updateDirtyState();
      refreshCodeDiagnostics(template);
      if (modes.getSelectionModel().getSelectedItem() == designTab) design.refresh();
      setStatus("Applied source template: " + selected + ". Review and save when ready.");
      codeEditor.requestFocus();
    }

    private String selectedTemplate(String selected) {
      return switch (kind) {
        case DIALOGUE_LAYOUT -> "Minimal monochrome".equals(selected)
            ? LayoutDslTemplates.minimalMonochromeDialogueLayoutTemplate()
            : LayoutDslTemplates.defaultDialogueLayoutTemplate();
        case MENU_LAYOUT -> "Minimal right-side menu".equals(selected)
            ? LayoutDslTemplates.minimalMonochromeMenuLayoutTemplate()
            : LayoutDslTemplates.defaultMenuLayoutTemplate(MenuProfile.defaultLayout());
        case MENU_STYLE -> "Minimal monochrome".equals(selected)
            ? LayoutDslTemplates.minimalMonochromeMenuStyleTemplate(currentProperty("backgroundAsset"))
            : LayoutDslTemplates.defaultMenuStyleTemplate(MenuProfile.defaultStyle());
        case MENU_SCREEN -> LayoutDslTemplates.defaultMenuScreenTemplate(screenIdFromFile());
      };
    }

    private String currentProperty(String key) {
      try {
        Properties properties = new Properties();
        properties.load(new StringReader(codeEditor.getText()));
        return properties.getProperty(key, "").trim();
      } catch (Exception ignored) {
        return "";
      }
    }

    private String screenIdFromFile() {
      if (file == null) return "main";
      String name = file.getName();
      int dot = name.lastIndexOf('.');
      return sanitizeId(dot > 0 ? name.substring(0, dot) : name);
    }

    private void loadFromDisk() {
      try {
        String text = Files.exists(file.toPath()) ? Files.readString(file.toPath()) : "";
        syncing = true;
        codeEditor.setTextNoEvent(text);
        refreshCodeDiagnostics(text);
        syncing = false;

        savedSnapshot = normalizeLineEndings(text);
        dirty = false;
        updateWindowTitle();
        setStatus("Loaded: " + file.getName());
        if (modes.getSelectionModel().getSelectedItem() == designTab) design.refresh();
      } catch (Exception ex) {
        syncing = false;
        setStatus("Load failed: " + ex.getMessage());
      }
    }

    private boolean save() {
      if (modes.getSelectionModel().getSelectedItem() == designTab) design.commitInspectorField();
      try {
        Path target = file.toPath();
        Path parent = target.getParent();
        if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);

        String content = codeEditor.getText();
        if (content == null) content = "";
        writeAtomically(target, content);

        savedSnapshot = normalizeLineEndings(content);
        dirty = false;
        updateWindowTitle();
        setStatus("Saved: " + file.getName());
        return true;
      } catch (Exception ex) {
        setStatus("Save failed: " + ex.getMessage());
        return false;
      }
    }

    private void saveAndRunProject() {
      if (!save()) {
        setStatus("Run cancelled: save failed.");
        return;
      }
      if (runProjectHandler == null) {
        setStatus("Run is unavailable for this Studio window.");
        return;
      }
      File root = resolveProjectRootForRun();
      if (root == null) {
        setStatus("Run cancelled: project root not found.");
        return;
      }
      setStatus("Running project...");
      try {
        runProjectHandler.accept(root);
      } catch (Exception ex) {
        setStatus("Run failed: " + normalize(ex.getMessage(), "unknown error"));
      }
    }

    private void reload() {
      if (dirty && !confirmDiscard("Reload from disk")) return;
      loadFromDisk();
    }

    private void revealInPathExplorer() {
      File parent = file.getParentFile();
      if (parent == null || !parent.exists()) return;
      if (EditorPathExplorer.show(stage, file)) setStatus("Revealed: " + toRelativePath(file));
      else setStatus("Reveal failed: " + toRelativePath(file));
    }

    private File resolveProjectRootForRun() {
      return resolveProjectRoot(file, projectRoot);
    }

    private void browseExistingAsset() {
      FileChooser chooser = new FileChooser();
      chooser.setTitle("Select Project Asset");
      AssetPickerSupport.addAssetFilters(chooser);
      File init = preferredAssetDirectory();
      if (init != null && init.exists() && init.isDirectory()) chooser.setInitialDirectory(init);
      File selected = chooser.showOpenDialog(stage);
      if (selected == null) return;
      assetPathField.setText(toRelativePath(selected));
      setStatus("Asset selected: " + toRelativePath(selected));
    }

    private void importExternalAsset() {
      FileChooser chooser = new FileChooser();
      chooser.setTitle("Import External Asset");
      AssetPickerSupport.addAssetFilters(chooser);
      File selected = chooser.showOpenDialog(stage);
      if (selected == null) return;

      try {
        File destinationDir = preferredAssetDirectory();
        if (destinationDir == null) {
          assetPathField.setText(selected.getAbsolutePath().replace('\\', '/'));
          setStatus("Asset selected outside project.");
          return;
        }

        if (!destinationDir.exists()) destinationDir.mkdirs();

        File destination = uniqueDestination(destinationDir, selected.getName());
        Path source = selected.toPath().toAbsolutePath().normalize();
        Path destinationPath = destination.toPath().toAbsolutePath().normalize();
        if (source.equals(destinationPath)) {
          String relExisting = toRelativePath(destination);
          assetPathField.setText(relExisting);
          setStatus("Asset already in project: " + relExisting);
          return;
        }

        Files.copy(source, destinationPath, StandardCopyOption.REPLACE_EXISTING);

        String rel = toRelativePath(destination);
        assetPathField.setText(rel);
        setStatus("Imported asset: " + rel);
      } catch (Exception ex) {
        setStatus("Import failed: " + ex.getMessage());
      }
    }

    private void revealSelectedAsset() {
      String path = normalize(assetPathField.getText(), "");
      if (path.isBlank()) {
        setStatus("Value is empty.");
        return;
      }
      File file = new File(path);
      if (!file.isAbsolute() && projectRoot != null) {
        file = new File(projectRoot, path);
      }
      if (AssetPickerSupport.revealFile(file)) {
        setStatus("Revealed: " + path);
      } else {
        setStatus("Could not reveal: " + path);
      }
    }

    private void copyAssetPath() {
      String path = normalize(assetPathField.getText(), "");
      if (path.isBlank()) {
        setStatus("Value is empty.");
        return;
      }
      ClipboardContent content = new ClipboardContent();
      content.putString(path);
      Clipboard.getSystemClipboard().setContent(content);
      setStatus("Copied: " + path);
    }

    private void applyAssetPathToCode() {
      String path = normalize(assetPathField.getText(), "");
      if (path.isBlank()) {
        setStatus("Value is empty.");
        return;
      }

      String keyTemplate = normalize(assetKeyBox.getValue(), "");
      if (keyTemplate.isBlank()) {
        setStatus("No key selected.");
        return;
      }

      String effectiveKey = keyTemplate;
      if (effectiveKey.contains("<itemId>")) {
        String itemId = sanitizeId(assetItemIdField.getText());
        if (itemId.isBlank()) {
          setStatus("Enter a menu item id for this key.");
          return;
        }
        effectiveKey = effectiveKey.replace("<itemId>", itemId);
      }

      String before = codeEditor.getText();
      String after = upsertProperty(before, effectiveKey, path);
      if (!Objects.equals(before, after)) {
        codeEditor.setText(after);
        setStatus("Applied asset to: " + effectiveKey);
      } else {
        setStatus("No change for key: " + effectiveKey);
      }
    }

    private void configureAssetKeys() {
      List<String> keys = new ArrayList<>();
      if (kind == Kind.DIALOGUE_LAYOUT) {
        keys.add("textBoxAsset");
        keys.add("choiceButtonAsset");
        keys.add("choiceButtonHoverAsset");
        keys.add("choiceButtonSelectedAsset");
        keys.add("choiceButtonDisabledAsset");
        keys.add("textBoxButton.<itemId>.asset");
        keys.add("textBoxButton.<itemId>.hoverAsset");
        keys.add("textBoxButton.<itemId>.disabledAsset");
        keys.add("choiceBackgroundColor");
        keys.add("choiceHoverColor");
        keys.add("choiceDisabledColor");
        keys.add("choiceTextColor");
        keys.add("choiceHoverTextColor");
        keys.add("choiceDisabledTextColor");
        keys.add("choiceBorderColor");
        keys.add("choiceHoverBorderColor");
        keys.add("choiceDisabledBorderColor");
        keys.add("choiceCornerRadius");
        keys.add("choiceBorderWidth");
        keys.add("choiceTextBaselineOffset");
      } else if (kind == Kind.MENU_STYLE) {
        keys.add("buttonAsset");
        keys.add("buttonSelectedAsset");
        keys.add("buttonDisabledAsset");
      } else if (kind == Kind.MENU_SCREEN) {
        keys.add("item.<itemId>.bgAsset");
        keys.add("item.<itemId>.bgSelectedAsset");
        keys.add("item.<itemId>.bgDisabledAsset");
        keys.add("item.<itemId>.icon");
        keys.add("item.<itemId>.slotPreviewEnabled");
        keys.add("item.<itemId>.slotPreviewPlaceholderAsset");
        keys.add("item.<itemId>.slotPreviewFrameAsset");
        keys.add("item.<itemId>.slotPreviewX");
        keys.add("item.<itemId>.slotPreviewY");
        keys.add("item.<itemId>.slotPreviewWidth");
        keys.add("item.<itemId>.slotPreviewHeight");
      } else {
        keys.add("# no direct asset key");
      }

      assetKeyBox.getItems().setAll(keys);
      if (!keys.isEmpty()) assetKeyBox.getSelectionModel().select(0);
      boolean usesItemId = kind == Kind.MENU_SCREEN || kind == Kind.DIALOGUE_LAYOUT;
      assetItemIdField.setManaged(usesItemId);
      assetItemIdField.setVisible(usesItemId);
    }

    private void updateAssetUtilityState() {
      String key = normalize(assetKeyBox.getValue(), "");
      String path = normalize(assetPathField.getText(), "");
      String itemId = sanitizeId(assetItemIdField.getText());
      boolean requiresItemId = key.contains("<itemId>");
      boolean hasItemId = !requiresItemId || !itemId.isBlank();
      boolean canApply = !path.isBlank() && !key.isBlank() && !key.startsWith("#") && hasItemId;
      applyPathButton.setDisable(!canApply);
      copyPathButton.setDisable(path.isBlank());
      revealAssetButton.setDisable(path.isBlank());
      clearAssetButton.setDisable(path.isBlank());
    }

    private String assetTip() {
      return switch (kind) {
        case DIALOGUE_LAYOUT -> "Import textbox/choice button skins and map them to dialogue layout keys.\n"
            + "Use textBoxButton.<itemId>.* to map per-textbox action button assets (enter id in the field below).";
        case MENU_STYLE -> "Import button textures and map them to style keys.\n"
            + "Use Save and Run Runtime to validate the authored result.";
        case MENU_SCREEN -> "Assign per-item button/icon assets using item.<itemId> keys.\n"
            + "For save/load screens, use slotPreview* keys to configure inline save thumbnails and frame skins.";
        case MENU_LAYOUT -> "This file is geometry-focused. Asset tools are still available for copying paths into custom properties.";
      };
    }

    private File preferredAssetDirectory() {
      if (projectRoot == null || !projectRoot.isDirectory()) return null;
      // Try kind-specific directories first, fall back to project root
      File candidate = switch (kind) {
        case DIALOGUE_LAYOUT -> new File(projectRoot, "assets/ui");
        case MENU_STYLE -> new File(projectRoot, "assets/ui");
        case MENU_SCREEN -> new File(projectRoot, "assets/ui");
        case MENU_LAYOUT -> new File(projectRoot, "assets");
      };
      return (candidate.exists() && candidate.isDirectory()) ? candidate : projectRoot;
    }

    private File uniqueDestination(File dir, String name) {
      String safeName = normalize(name, "asset.bin");
      File out = new File(dir, safeName);
      if (!out.exists()) return out;

      String base = safeName;
      String ext = "";
      int dot = safeName.lastIndexOf('.');
      if (dot > 0) {
        base = safeName.substring(0, dot);
        ext = safeName.substring(dot);
      }
      int i = 2;
      while (true) {
        File candidate = new File(dir, base + "_" + i + ext);
        if (!candidate.exists()) return candidate;
        i++;
      }
    }

    private String toRelativePath(File pathFile) {
      if (pathFile == null) return "";
      if (projectRoot == null) return pathFile.getAbsolutePath().replace('\\', '/');
      try {
        Path root = projectRoot.toPath().toAbsolutePath().normalize();
        Path abs = pathFile.toPath().toAbsolutePath().normalize();
        if (abs.startsWith(root)) {
          return root.relativize(abs).toString().replace('\\', '/');
        }
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      }
      return pathFile.getAbsolutePath().replace('\\', '/');
    }

    private void updateDirtyState() {
      String current = normalizeLineEndings(codeEditor.getText());
      dirty = !Objects.equals(savedSnapshot, current);
      updateWindowTitle();
    }

    private void updateWindowTitle() {
      String title = "JVN " + studioTitle() + " - " + file.getName();
      if (dirty) title += " *";
      stage.setTitle(title);
      dirtyBadge.setText(dirty ? "Unsaved" : "Saved");
      dirtyBadge.getStyleClass().removeAll("layout-studio-dirty-saved", "layout-studio-dirty-unsaved");
      dirtyBadge.getStyleClass().add(dirty ? "layout-studio-dirty-unsaved" : "layout-studio-dirty-saved");
      updateFileLabel();
    }

    private void updateFileLabel() {
      fileLabel.setText("File: " + toRelativePath(file));
    }

    private String studioTitle() {
      return switch (kind) {
        case MENU_SCREEN -> "Menu Screen Studio";
        case MENU_LAYOUT -> "Menu Layout Studio";
        case MENU_STYLE -> "Menu Style Studio";
        case DIALOGUE_LAYOUT -> "Dialogue Layout Studio";
      };
    }

    private void setStatus(String message) {
      String msg = normalize(message, "Ready.");
      status.setText(msg);
      if (externalStatus != null) externalStatus.accept(msg);
    }

    private static void configureIconButton(Button button, Node icon, String tooltipText) {
      button.setText("");
      button.setGraphic(icon);
      button.setTooltip(new Tooltip(tooltipText));
      button.setMinWidth(30);
      button.setPrefWidth(30);
    }

    private boolean confirmDiscard(String operation) {
      return EditorDialogs.confirm(
          stage,
          "Unsaved Changes",
          operation + "? Unsaved changes will be lost.\nContinue without saving?",
          "Continue",
          true);
    }

    private boolean confirmCloseIfDirty() {
      if (modes.getSelectionModel().getSelectedItem() == designTab) design.commitInspectorField();
      if (!dirty) return true;

      Label detail = new Label("Choose Save to keep changes, Discard to close without saving.");
      detail.getStyleClass().add("editor-dialog-message");
      detail.setWrapText(true);
      Optional<String> result = EditorDialogs.show(
          stage,
          "Unsaved Changes",
          "Save changes to " + file.getName() + " before closing?",
          detail,
          EditorDialogs.ActionSpec.neutral("cancel", "Cancel", null),
          EditorDialogs.ActionSpec.danger("discard", "Discard", null),
          EditorDialogs.ActionSpec.accent("save", "Save", null));
      if (result.isEmpty() || "cancel".equals(result.get())) return false;
      if ("discard".equals(result.get())) return true;
      return save();
    }

    private static String upsertProperty(String originalText, String key, String value) {
      String text = originalText == null ? "" : originalText;
      String k = normalize(key, "");
      if (k.isBlank()) return text;
      String v = value == null ? "" : value;

      String line = k + "=" + v;
      Pattern pattern = Pattern.compile("(?m)^\\s*" + Pattern.quote(k) + "\\s*=.*$");
      Matcher matcher = pattern.matcher(text);
      if (matcher.find()) {
        return matcher.replaceFirst(Matcher.quoteReplacement(line));
      }

      StringBuilder sb = new StringBuilder(text);
      if (!text.isEmpty() && !text.endsWith("\n")) sb.append('\n');
      sb.append(line).append('\n');
      return sb.toString();
    }

    private static void writeAtomically(Path target, String content) throws Exception {
      Path parent = target.getParent();
      if (parent == null) {
        Files.writeString(target, content, StandardCharsets.UTF_8);
        return;
      }

      Path temp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
      try {
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        try {
          Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
          Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
      } finally {
        try {
          Files.deleteIfExists(temp);
        } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
        }
      }
    }

  }
}
