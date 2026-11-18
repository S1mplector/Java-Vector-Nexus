package com.jvn.editor.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

public class EditorSettingsView extends BorderPane {
  private static final String TEXT_EDITOR_LABEL_JVN = "JVN Editor";
  private static final String TEXT_EDITOR_LABEL_SYSTEM = "System Default App";
  private static final String TEXT_EDITOR_LABEL_CUSTOM = "Custom Command";
  private static final String THEME_LABEL_DARK = "Dark";
  private static final String THEME_LABEL_LIGHT = "Light";
  private static final String GRAPHICS_LABEL_AUTO = "Automatic (recommended)";
  private static final String GRAPHICS_LABEL_HARDWARE = "Prefer GPU acceleration";
  private static final String GRAPHICS_LABEL_SOFTWARE = "Software compatibility";
  private static final String ALL_SETTINGS = "All settings";
  private static final Set<EditorStatusBarSegment> COMPACT_STATUS_BAR_SEGMENTS =
      EnumSet.of(
          EditorStatusBarSegment.PRODUCT,
          EditorStatusBarSegment.MESSAGE,
          EditorStatusBarSegment.ACTIVE_FILE,
          EditorStatusBarSegment.DIRTY,
          EditorStatusBarSegment.DIAGNOSTICS);

  private final EditorPreferencesStore store;
  private final ComboBox<String> editorThemeCombo = new ComboBox<>();
  private final Spinner<Integer> codeEditorFontSizeSpinner = new Spinner<>();
  private final Spinner<Integer> editorMaxFpsSpinner = new Spinner<>();
  private final ComboBox<String> graphicsModeCombo = new ComboBox<>();
  private final ComboBox<String> defaultTextEditorCombo = new ComboBox<>();
  private final TextField customTextEditorCommandField = new TextField();
  private final TextField settingsFilterField = new TextField();
  private final CheckBox showWelcomeOnStartupCheck =
      new CheckBox("Show Workspace Hub tab on startup");
  private final CheckBox loadSidebarExtensionsOnDemandCheck =
      new CheckBox("Load sidebar extensions only when opened (lower memory usage)");
  private final CheckBox autoSaveBeforeRunCheck =
      new CheckBox("Save dirty files before project runs");
  private final CheckBox editorRuntimePerfHudCheck =
      new CheckBox("Show runtime performance HUD when launching projects");
  private final CheckBox editorConfirmRunProjectCheck =
      new CheckBox("Confirm before running a project from the editor");
  private final CheckBox vnsWordWrapByDefaultCheck =
      new CheckBox("Wrap long VNS lines by default");
  private final CheckBox vnsMinimapVisibleCheck =
      new CheckBox("Show the VNS script minimap");
  private final Spinner<Integer> largeTimelineBlockActionThresholdSpinner = new Spinner<>();
  private final CheckBox gradleSkipTestsOnRunCheck =
      new CheckBox("Skip Gradle tests for default project runs");
  private final Map<EditorSidebarPanel, ComboBox<EditorPanelPlacement>> panelPlacements =
      new EnumMap<>(EditorSidebarPanel.class);
  private final Map<EditorSidebarPanel, CheckBox> chooserVisibilityChecks =
      new EnumMap<>(EditorSidebarPanel.class);
  private final Map<EditorStatusBarSegment, CheckBox> statusBarSegmentChecks =
      new EnumMap<>(EditorStatusBarSegment.class);
  private final Label statusLabel = new Label("Editor settings loaded");
  private final ComboBox<String> category = new ComboBox<>();
  private final List<Section> sections = new ArrayList<>();
  private final Label noResults = new Label("No settings match your search.");
  private final ScrollPane scrollPane = new ScrollPane();
  private Consumer<EditorPreferences> onPreferencesApplied;

  public EditorSettingsView(EditorPreferencesStore store) {
    this.store = store == null ? new EditorPreferencesStore() : store;
    setPadding(Insets.EMPTY);
    getStyleClass().addAll("editor-preferences-view", "sidebar-tool-root");

    Label title = new Label("Editor Settings");
    title.getStyleClass().add("sidebar-tool-title");
    HBox titleRow = new HBox(6, title, SidebarToolHelp.button(this, "Editor Settings", """
        Choose a category or search across editor preferences. Changes stay in the form as you move between categories.

        ## Apply changes
        - **Save** applies the current values and keeps them for future launches.
        - **Reload** reads the saved preferences again.
        - **Defaults** fills the form with factory values for you to review and save.

        *Graphics mode takes effect after restarting JVN.*
        """));
    titleRow.setAlignment(Pos.CENTER_LEFT);

    Button reloadButton = action("Reload", SidebarToolIcon.refresh(), "Reload saved settings", this::reload);
    Button defaultsButton = action("Defaults", SidebarToolIcon.undo(), "Fill the form with default settings", () -> {
      loadIntoForm(EditorPreferences.defaults());
      statusLabel.setText("Defaults loaded in the form. Save to apply.");
    });
    Button saveButton = action("Save", SidebarToolIcon.save("#5db985"), "Save and apply settings", this::save);
    saveButton.getStyleClass().add("editor-settings-save");
    FlowPane actions = actionRow(saveButton, reloadButton, defaultsButton);

    category.setAccessibleText("Settings category");
    category.setMinWidth(108);
    category.setPrefWidth(138);
    settingsFilterField.setPromptText("Filter settings...");
    settingsFilterField.setAccessibleText("Filter settings");
    settingsFilterField.setTooltip(EditorTooltip.rich("**Search settings** · `Esc` clears the filter"));
    settingsFilterField.setMinWidth(66);
    settingsFilterField.getStyleClass().add("sidebar-tool-search-field");
    HBox navigation = new HBox(8, settingsFilterField, category);
    navigation.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(settingsFilterField, Priority.ALWAYS);

    VBox header = new VBox(6, titleRow, navigation, actions);
    header.getStyleClass().add("sidebar-tool-header");
    setTop(header);

    editorThemeCombo.getItems().addAll(THEME_LABEL_DARK, THEME_LABEL_LIGHT);
    editorThemeCombo.setMaxWidth(Double.MAX_VALUE);
    codeEditorFontSizeSpinner.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(
            EditorPreferences.MIN_CODE_EDITOR_FONT_SIZE,
            EditorPreferences.MAX_CODE_EDITOR_FONT_SIZE,
            EditorPreferences.DEFAULT_CODE_EDITOR_FONT_SIZE));
    codeEditorFontSizeSpinner.setEditable(true);
    editorMaxFpsSpinner.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(
            EditorPreferences.MIN_EDITOR_MAX_FPS,
            EditorPreferences.MAX_EDITOR_MAX_FPS,
            EditorPreferences.DEFAULT_EDITOR_MAX_FPS));
    editorMaxFpsSpinner.setEditable(true);
    editorMaxFpsSpinner.setPromptText("0 = display rate");
    largeTimelineBlockActionThresholdSpinner.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(
            EditorPreferences.MIN_LARGE_TIMELINE_BLOCK_ACTION_THRESHOLD,
            EditorPreferences.MAX_LARGE_TIMELINE_BLOCK_ACTION_THRESHOLD,
            EditorPreferences.DEFAULT_LARGE_TIMELINE_BLOCK_ACTION_THRESHOLD));
    largeTimelineBlockActionThresholdSpinner.setEditable(true);
    graphicsModeCombo.getItems().addAll(
        GRAPHICS_LABEL_AUTO, GRAPHICS_LABEL_HARDWARE, GRAPHICS_LABEL_SOFTWARE);
    graphicsModeCombo.setMaxWidth(Double.MAX_VALUE);
    graphicsModeCombo.setTooltip(new Tooltip(
        "Selects the JavaFX graphics pipeline at the next JVN restart. GPU mode retains software fallback."));
    defaultTextEditorCombo.getItems().addAll(
        TEXT_EDITOR_LABEL_JVN,
        TEXT_EDITOR_LABEL_SYSTEM,
        TEXT_EDITOR_LABEL_CUSTOM);
    defaultTextEditorCombo.setMaxWidth(Double.MAX_VALUE);
    defaultTextEditorCombo.valueProperty().addListener((obs, oldValue, newValue) ->
        updateCustomTextEditorCommandState());
    customTextEditorCommandField.setPromptText("Example: code --reuse-window {file}");

    addSection("Appearance", SidebarToolIcon.palette(),
        "Choose the editor theme and the text size used in code tabs.",
        "dark light scale font",
        field("Editor theme", editorThemeCombo),
        field("Code text size", codeEditorFontSizeSpinner));

    addSection("VNS Authoring", SidebarToolIcon.edit(),
        "These readability and navigation settings also apply to open VNS tabs.",
        "script editor line wrapping timeline actions",
        vnsWordWrapByDefaultCheck, vnsMinimapVisibleCheck,
        field("Large timeline block hint", largeTimelineBlockActionThresholdSpinner));

    addSection("Runtime", SidebarToolIcon.nearMe("#60bddf"),
        "Use 0 FPS to follow the display rate. Graphics mode applies after restarting JVN.",
        "run project launch preview performance frames per second gpu automatic hardware software",
        field("Maximum FPS", editorMaxFpsSpinner),
        field("Graphics mode", graphicsModeCombo),
        editorRuntimePerfHudCheck, autoSaveBeforeRunCheck,
        editorConfirmRunProjectCheck, gradleSkipTestsOnRunCheck);

    addSection("Startup", SidebarToolIcon.home(),
        "Choose what the editor opens or loads at startup.", "workspace launch memory",
        showWelcomeOnStartupCheck, loadSidebarExtensionsOnDemandCheck);

    TilePane segments = new TilePane(10, 4);
    segments.setPrefColumns(2);
    segments.setTileAlignment(Pos.CENTER_LEFT);
    for (EditorStatusBarSegment segment : EditorStatusBarSegment.values()) {
      CheckBox check = new CheckBox(segment.displayName());
      check.setTooltip(new Tooltip(segment.description()));
      statusBarSegmentChecks.put(segment, check);
      segments.getChildren().add(check);
    }
    Button showAllStatusButton = action("Show all", SidebarToolIcon.visibility("#64add5"),
        "Enable all status bar segments", () -> setAllStatusBarSegments(true));
    Button compactStatusButton = action("Compact", SidebarToolIcon.dock("#6dbce1"),
        "Enable only the essential status bar segments", this::applyCompactStatusBarPreset);
    addSection("Status Bar", SidebarToolIcon.dock("#6dbce1"),
        "Choose the readouts in the bottom bar. Data-dependent readouts appear when data is available.",
        "bottom footer readouts segments chips clear show hide",
        actionRow(showAllStatusButton, compactStatusButton), segments);

    addSection("File Opening", SidebarToolIcon.folder(),
        "Choose what opens text files from the Project Explorer.",
        "default text editor custom command external system app",
        field("Default text editor", defaultTextEditorCombo),
        field("Custom command", customTextEditorCommandField));

    VBox panels = new VBox(0);
    for (EditorSidebarPanel panel : EditorSidebarPanel.values()) {
      if (!panel.editableInSettings()) continue;
      ComboBox<EditorPanelPlacement> combo = new ComboBox<>();
      if (panel.supportsDocking()) {
        combo.getItems().addAll(EditorPanelPlacement.values());
      } else {
        combo.getItems().add(EditorPanelPlacement.HIDDEN);
        combo.setDisable(true);
        combo.setTooltip(new Tooltip(panel.displayName() + " opens only as a pop-out tool."));
      }
      combo.getStyleClass().add("sidebar-tool-field");
      combo.setAccessibleText(panel.displayName() + " default placement");
      combo.setPrefWidth(115);
      CheckBox chooserVisible = new CheckBox("Show in chooser");
      chooserVisible.setAccessibleText("Show " + panel.displayName() + " in the New Panel chooser");
      panelPlacements.put(panel, combo);
      chooserVisibilityChecks.put(panel, chooserVisible);

      Label panelTitle = new Label(panel.displayName());
      panelTitle.getStyleClass().add("editor-settings-field-label");
      panelTitle.setWrapText(true);
      FlowPane rowControls = new FlowPane(8, 4, combo, chooserVisible);
      rowControls.setAlignment(Pos.CENTER_LEFT);
      VBox panelRow = new VBox(4, panelTitle, rowControls);
      panelRow.getStyleClass().add("editor-settings-panel-row");
      panels.getChildren().add(panelRow);
    }
    addSection("Sidebar Panels", SidebarToolIcon.grid(),
        "Set where tools open by default and which ones appear in the New Panel chooser.",
        "default left right hidden docking pop out placement", panels);

    VBox content = new VBox(10);
    content.getStyleClass().add("editor-preferences-content");
    content.setMinWidth(0);
    content.setMaxWidth(700);
    content.getChildren().setAll(sections.stream().map(Section::pane).toList());
    noResults.getStyleClass().add("sidebar-tool-summary");
    noResults.setWrapText(true);
    content.getChildren().add(noResults);

    StackPane viewportContent = new StackPane(content);
    viewportContent.setAlignment(Pos.TOP_LEFT);
    scrollPane.setContent(viewportContent);
    scrollPane.setFitToWidth(true);
    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scrollPane.getStyleClass().add("editor-preferences-scroll");
    setCenter(scrollPane);

    statusLabel.getStyleClass().addAll("sidebar-tool-status", "editor-settings-footer");
    statusLabel.setWrapText(true);
    statusLabel.setMaxWidth(Double.MAX_VALUE);
    setBottom(statusLabel);

    category.getItems().add(ALL_SETTINGS);
    category.getItems().addAll(sections.stream().map(Section::name).toList());
    category.setValue(sections.getFirst().name());
    category.valueProperty().addListener((obs, oldValue, newValue) -> {
      if (settingsFilterField.getText().isEmpty()) applySettingsFilter();
      else settingsFilterField.clear();
    });
    settingsFilterField.textProperty().addListener((obs, oldValue, newValue) -> applySettingsFilter());
    settingsFilterField.setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ESCAPE && !settingsFilterField.getText().isBlank()) {
        settingsFilterField.clear();
        event.consume();
      }
    });
    addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (event.isShortcutDown() && event.getCode() == KeyCode.F) {
        settingsFilterField.requestFocus();
        settingsFilterField.selectAll();
        event.consume();
      }
    });
    applySettingsFilter();
    reload();
  }

  public void setOnPreferencesApplied(Consumer<EditorPreferences> onPreferencesApplied) {
    this.onPreferencesApplied = onPreferencesApplied;
  }

  public EditorPreferences getCurrentPreferences() {
    return buildPreferencesFromForm();
  }

  public void reload() {
    loadIntoForm(store.load());
    statusLabel.setText("Editor settings loaded");
  }

  public void loadIntoForm(EditorPreferences preferences) {
    EditorPreferences model = preferences == null ? EditorPreferences.defaults() : preferences.copy();
    editorThemeCombo.setValue(themeLabel(model.getEditorTheme()));
    codeEditorFontSizeSpinner.getValueFactory().setValue(model.getCodeEditorFontSize());
    editorMaxFpsSpinner.getValueFactory().setValue(model.getEditorMaxFps());
    graphicsModeCombo.setValue(graphicsModeLabel(model.getGraphicsMode()));
    defaultTextEditorCombo.setValue(textEditorLabel(model.getDefaultTextEditor()));
    customTextEditorCommandField.setText(model.getCustomTextEditorCommand());
    updateCustomTextEditorCommandState();
    showWelcomeOnStartupCheck.setSelected(model.isShowWelcomeOnStartup());
    loadSidebarExtensionsOnDemandCheck.setSelected(model.isLoadSidebarExtensionsOnDemand());
    autoSaveBeforeRunCheck.setSelected(model.isAutoSaveBeforeRun());
    editorRuntimePerfHudCheck.setSelected(model.isEditorRuntimePerfHud());
    editorConfirmRunProjectCheck.setSelected(model.isEditorConfirmRunProject());
    vnsWordWrapByDefaultCheck.setSelected(model.isVnsWordWrapByDefault());
    vnsMinimapVisibleCheck.setSelected(model.isVnsMinimapVisible());
    largeTimelineBlockActionThresholdSpinner.getValueFactory()
        .setValue(model.getLargeTimelineBlockActionThreshold());
    gradleSkipTestsOnRunCheck.setSelected(model.isGradleSkipTestsOnRun());
    for (EditorStatusBarSegment segment : EditorStatusBarSegment.values()) {
      CheckBox check = statusBarSegmentChecks.get(segment);
      if (check != null) check.setSelected(model.isStatusBarSegmentVisible(segment));
    }
    for (EditorSidebarPanel panel : EditorSidebarPanel.values()) {
      if (!panel.editableInSettings()) continue;
      ComboBox<EditorPanelPlacement> combo = panelPlacements.get(panel);
      if (combo != null) combo.setValue(model.getPlacement(panel));
      CheckBox chooserVisible = chooserVisibilityChecks.get(panel);
      if (chooserVisible != null) chooserVisible.setSelected(model.isVisibleInChooser(panel));
    }
  }

  public void save() {
    EditorPreferences preferences = buildPreferencesFromForm();
    try {
      store.save(preferences);
      statusLabel.setText("Editor settings saved");
      if (onPreferencesApplied != null) onPreferencesApplied.accept(preferences.copy());
    } catch (IOException ex) {
      statusLabel.setText("Failed to save editor settings: " + ex.getMessage());
    }
  }

  private EditorPreferences buildPreferencesFromForm() {
    EditorPreferences preferences = store.load();
    if (preferences == null) preferences = EditorPreferences.defaults();
    else preferences = preferences.copy();
    Integer fontSize = codeEditorFontSizeSpinner.getValue();
    preferences.setCodeEditorFontSize(
        fontSize == null
            ? EditorPreferences.DEFAULT_CODE_EDITOR_FONT_SIZE
            : fontSize.intValue());
    preferences.setEditorTheme(themeValue(editorThemeCombo.getValue()));
    Integer maxFps = editorMaxFpsSpinner.getValue();
    preferences.setEditorMaxFps(
        maxFps == null
            ? EditorPreferences.DEFAULT_EDITOR_MAX_FPS
            : maxFps.intValue());
    preferences.setGraphicsMode(graphicsModeValue(graphicsModeCombo.getValue()));
    preferences.setDefaultTextEditor(textEditorValue(defaultTextEditorCombo.getValue()));
    preferences.setCustomTextEditorCommand(customTextEditorCommandField.getText());
    preferences.setShowWelcomeOnStartup(showWelcomeOnStartupCheck.isSelected());
    preferences.setLoadSidebarExtensionsOnDemand(loadSidebarExtensionsOnDemandCheck.isSelected());
    preferences.setAutoSaveBeforeRun(autoSaveBeforeRunCheck.isSelected());
    preferences.setEditorRuntimePerfHud(editorRuntimePerfHudCheck.isSelected());
    preferences.setEditorConfirmRunProject(editorConfirmRunProjectCheck.isSelected());
    preferences.setVnsWordWrapByDefault(vnsWordWrapByDefaultCheck.isSelected());
    preferences.setVnsMinimapVisible(vnsMinimapVisibleCheck.isSelected());
    Integer largeTimelineBlockActionThreshold = largeTimelineBlockActionThresholdSpinner.getValue();
    preferences.setLargeTimelineBlockActionThreshold(
        largeTimelineBlockActionThreshold == null
            ? EditorPreferences.DEFAULT_LARGE_TIMELINE_BLOCK_ACTION_THRESHOLD
            : largeTimelineBlockActionThreshold);
    preferences.setGradleSkipTestsOnRun(gradleSkipTestsOnRunCheck.isSelected());
    for (EditorStatusBarSegment segment : EditorStatusBarSegment.values()) {
      CheckBox check = statusBarSegmentChecks.get(segment);
      preferences.setStatusBarSegmentVisible(
          segment,
          check == null ? segment.defaultVisible() : check.isSelected());
    }
    for (EditorSidebarPanel panel : EditorSidebarPanel.values()) {
      if (!panel.editableInSettings()) continue;
      ComboBox<EditorPanelPlacement> combo = panelPlacements.get(panel);
      preferences.setPlacement(
          panel,
          combo == null || combo.getValue() == null ? panel.defaultPlacement() : combo.getValue());
      CheckBox chooserVisible = chooserVisibilityChecks.get(panel);
      preferences.setVisibleInChooser(
          panel,
          chooserVisible == null ? panel.defaultVisibleInChooser() : chooserVisible.isSelected());
    }
    return preferences;
  }

  private void setAllStatusBarSegments(boolean visible) {
    for (CheckBox check : statusBarSegmentChecks.values()) {
      if (check != null) check.setSelected(visible);
    }
  }

  private void applyCompactStatusBarPreset() {
    for (EditorStatusBarSegment segment : EditorStatusBarSegment.values()) {
      CheckBox check = statusBarSegmentChecks.get(segment);
      if (check != null) check.setSelected(COMPACT_STATUS_BAR_SEGMENTS.contains(segment));
    }
  }

  private void addSection(String name, Region icon, String summary, String keywords, Node... controls) {
    Label title = new Label(name, icon);
    title.getStyleClass().add("sidebar-tool-section-title");
    title.setGraphicTextGap(7);
    Label description = new Label(summary);
    description.getStyleClass().add("sidebar-tool-summary");
    description.setWrapText(true);

    VBox section = new VBox(8, title, description);
    section.setMinWidth(0);
    section.getStyleClass().add("editor-preferences-section");
    section.getChildren().addAll(controls);
    for (Node control : controls) {
      if (control instanceof CheckBox check) check.setWrapText(true);
    }
    StringBuilder search = new StringBuilder(keywords);
    appendSearchLabels(section, search);
    sections.add(new Section(name, section, normalizeSearchText(search.toString())));
  }

  private static void appendSearchLabels(Node node, StringBuilder search) {
    if (node instanceof Labeled labeled) {
      search.append(' ').append(labeled.getText());
      if (labeled.getTooltip() != null) search.append(' ').append(labeled.getTooltip().getText());
    }
    if (node instanceof Parent parent) {
      for (Node child : parent.getChildrenUnmodifiable()) appendSearchLabels(child, search);
    }
  }

  private void applySettingsFilter() {
    String query = normalizeSearchText(settingsFilterField.getText());
    String[] tokens = query.split("\s+");
    String selected = category.getValue();
    boolean found = false;
    for (Section section : sections) {
      boolean show = query.isBlank()
          ? ALL_SETTINGS.equals(selected) || section.name().equals(selected)
          : Arrays.stream(tokens).allMatch(section.searchText()::contains);
      setDisplayed(section.pane(), show);
      found |= show;
    }
    setDisplayed(noResults, !found);
    scrollPane.setVvalue(0);
  }

  private static void setDisplayed(Node node, boolean shown) {
    node.setManaged(shown);
    node.setVisible(shown);
  }

  private static String normalizeSearchText(String text) {
    return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
  }

  private static VBox field(String text, Region control) {
    Label label = new Label(text);
    label.getStyleClass().add("editor-settings-field-label");
    label.setLabelFor(control);
    if (control instanceof ComboBox<?>) control.getStyleClass().add("sidebar-tool-field");
    control.setMaxWidth(Double.MAX_VALUE);
    return new VBox(4, label, control);
  }

  private static Button action(String text, Region icon, String help, Runnable handler) {
    Button button = new Button(text, icon);
    button.getStyleClass().add("sidebar-tool-btn");
    button.setGraphicTextGap(6);
    button.setOnAction(event -> handler.run());
    button.setTooltip(new Tooltip(help));
    return button;
  }

  private static FlowPane actionRow(Button... buttons) {
    FlowPane row = new FlowPane(4, 4, buttons);
    row.setAlignment(Pos.CENTER_LEFT);
    return row;
  }

  private record Section(String name, VBox pane, String searchText) {}

  private void updateCustomTextEditorCommandState() {
    boolean custom = TEXT_EDITOR_LABEL_CUSTOM.equals(defaultTextEditorCombo.getValue());
    customTextEditorCommandField.setDisable(!custom);
  }

  private static String themeLabel(String value) {
    return EditorPreferences.LAUNCHER_THEME_LIGHT.equals(EditorPreferences.normalizeEditorTheme(value))
        ? THEME_LABEL_LIGHT
        : THEME_LABEL_DARK;
  }

  private static String themeValue(String label) {
    return THEME_LABEL_LIGHT.equals(label)
        ? EditorPreferences.LAUNCHER_THEME_LIGHT
        : EditorPreferences.LAUNCHER_THEME_DARK;
  }

  private static String graphicsModeLabel(String value) {
    return switch (EditorPreferences.normalizeGraphicsMode(value)) {
      case EditorPreferences.GRAPHICS_MODE_HARDWARE -> GRAPHICS_LABEL_HARDWARE;
      case EditorPreferences.GRAPHICS_MODE_SOFTWARE -> GRAPHICS_LABEL_SOFTWARE;
      default -> GRAPHICS_LABEL_AUTO;
    };
  }

  private static String graphicsModeValue(String label) {
    if (GRAPHICS_LABEL_HARDWARE.equals(label)) return EditorPreferences.GRAPHICS_MODE_HARDWARE;
    if (GRAPHICS_LABEL_SOFTWARE.equals(label)) return EditorPreferences.GRAPHICS_MODE_SOFTWARE;
    return EditorPreferences.GRAPHICS_MODE_AUTO;
  }

  private static String textEditorLabel(String value) {
    return switch (EditorPreferences.normalizeTextEditor(value)) {
      case EditorPreferences.TEXT_EDITOR_SYSTEM -> TEXT_EDITOR_LABEL_SYSTEM;
      case EditorPreferences.TEXT_EDITOR_CUSTOM -> TEXT_EDITOR_LABEL_CUSTOM;
      default -> TEXT_EDITOR_LABEL_JVN;
    };
  }

  private static String textEditorValue(String label) {
    if (TEXT_EDITOR_LABEL_SYSTEM.equals(label)) return EditorPreferences.TEXT_EDITOR_SYSTEM;
    if (TEXT_EDITOR_LABEL_CUSTOM.equals(label)) return EditorPreferences.TEXT_EDITOR_CUSTOM;
    return EditorPreferences.TEXT_EDITOR_JVN;
  }
}
