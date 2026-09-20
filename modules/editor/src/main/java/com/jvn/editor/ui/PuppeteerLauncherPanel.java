package com.jvn.editor.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jvn.core.vn.LayeredCharacterResolver;
import com.jvn.core.vn.VnArgTokenizer;
import com.jvn.editor.ui.actioneditor.AnimationProject;
import com.jvn.editor.ui.actioneditor.CodeImporter;
import com.jvn.editor.ui.actioneditor.EntityTrack;
import com.jvn.editor.ui.actioneditor.PropertyType;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Right-panel extension that shows the VNS scene snapshot at the current cursor line
 * and provides a button to launch Puppeteer with that context.
 */
public class PuppeteerLauncherPanel extends VBox {

  private static final Pattern LABEL_PATTERN = Pattern.compile("^\\s*(?:@label|label)\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern INCLUDE_PATTERN = Pattern.compile("^\\s*@include\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern BG_CMD_PATTERN = Pattern.compile("^\\s*\\[(?:bg|background)\\s+(\\S+)]", Pattern.CASE_INSENSITIVE);
  private static final Pattern BG_DECL_PATTERN = Pattern.compile("^\\s*@background\\s+(\\S+)\\s+(.+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern STAGE_PRESET_PATTERN = Pattern.compile("^\\s*@stagepreset\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARACTER_DECL_PATTERN = Pattern.compile(
      "^\\s*@character\\s+(\\S+)\\s+\"[^\"]*\"(?:\\s+(.+))?$",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARACTER_SCALE_OPTION_PATTERN = Pattern.compile(
      "(?:^|\\s)scale\\s*=\\s*([^\\s]+)",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARIMG_PATTERN = Pattern.compile("^\\s*@charimg\\s+(\\S+)\\s+(\\S+)\\s+(.+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARLAYER_PATTERN = Pattern.compile("^\\s*@charlayer\\s+(\\S+)\\s+(\\S+)\\s+(.+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARGROUP_PATTERN = Pattern.compile("^\\s*@chargroup\\s+(\\S+)\\s+(\\S+)\\s+(.+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARPRESET_PATTERN = Pattern.compile("^\\s*@charpreset\\s+(\\S+)\\s+(\\S+)\\s+(.+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern DISPLAY_PRESET_PATTERN = Pattern.compile("^\\s*@displaypreset\\s+(\\S+)(?:\\s+(.+))?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern DISPLAY_PRESET_ENTRY_PATTERN = Pattern.compile("^\\s*([^=\\s]+)\\s*=\\s*(.+)$");
  private static final Pattern POSITION_PATTERN = Pattern.compile("^\\s*@position\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern JES_TIMELINE_PATTERN = Pattern.compile("^\\s*@external\\s+jes_timeline\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXT_STAGE_PATTERN = Pattern.compile("^\\s*@external\\s+stage\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXT_CHAR_SHOW = Pattern.compile("^\\s*@external\\s+char(?:acter)?\\s+(\\S+)\\s+show\\s+(\\S+)(?:\\s+(\\S+))?", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXT_CHAR_HIDE = Pattern.compile("^\\s*@external\\s+char(?:acter)?\\s+(\\S+)\\s+hide", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXT_CHAR_MOVE = Pattern.compile("^\\s*@external\\s+char(?:acter)?\\s+(\\S+)\\s+move\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXT_CHAR_EXPR = Pattern.compile("^\\s*@external\\s+char(?:acter)?\\s+(\\S+)\\s+(?:expression|expr)\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern GROUP_DECL_PATTERN = Pattern.compile("^\\s*@group\\s+(\\S+)(?:\\s+(\\S+))?$", Pattern.CASE_INSENSITIVE);
  private static final Set<String> KNOWN_POSITIONS = Set.of("far_left", "left", "center", "right", "far_right");

  private final Label lblHeader;
  private final Label lblLine;
  private final Label lblLineText;
  private final Label lblLabel;
  private final Label lblBackground;
  private final Label lblStage;
  private final Label lblTimeline;
  private final Label lblSummary;
  private final VBox characterList;
  private final VBox diagnosticsList;
  private final Label lblRegisteredHeader;
  private final Label lblRegisteredSummary;
  private final VBox registeredTimelineCards;
  private final ScrollPane registeredTimelineScroll;
  private final Button btnLaunch;
  private final Button btnLaunchLabelStart;
  private final Button btnLaunchSceneStart;
  private final Button btnOpenTimeline;
  private final Button btnNewBlankTimeline;
  private final Button btnOpenIssue;

  private String currentSource = "";
  private int currentLine = 0;
  private File projectRoot;
  private File activeScriptFile;
  private Consumer<LaunchRequest> onLaunch;
  private Consumer<OpenTarget> onOpenTarget;
  private List<RegisteredAnimation> cachedRegisteredAnimations = List.of();
  private String cachedRegisteredAnimationsRoot = "";
  private long cachedRegisteredAnimationsStamp = Long.MIN_VALUE;
  private String activeEditingTimeline;
  private String selectedTimelineId;
  private java.io.File selectedTimelineFile;

  public PuppeteerLauncherPanel() {
    setSpacing(8);
    setPadding(new Insets(12));
    getStyleClass().add("sidebar-tool-root");

    lblHeader = new Label("Puppeteer Launcher");
    lblHeader.getStyleClass().add("sidebar-tool-title");
    HBox headerRow = new HBox(6, lblHeader, helpButton("What is Puppeteer?", """
        Puppeteer is an animation authoring tool that lets you preview and build \
character animations in real time.

It reads the current cursor position in your VNS script and reconstructs the \
scene state at that exact line — backgrounds, characters, layers, expressions, \
and stage layout — so the preview starts from exactly where your story is.

Common workflow:
  1. Place your cursor somewhere in a .vns script.
  2. Press "Launch @ Cursor" to open Puppeteer with that scene.
  3. Build or adjust an animation on a character.
  4. Save the animation as a .jes timeline file.
  5. Reference it in your script with @external jes_timeline <id>.

The three launch modes differ in where the scene snapshot is taken from:
  • @ Cursor     — exact cursor line (most precise)
  • @ Label Start — first line of the active @label block
  • @ Scene Start — most recent background change in the active label

Registered Animations below shows all .jes timelines discovered under \
scripts/timelines/ in your project, so you can open or reuse them without \
leaving this panel."""));
    HBox.setHgrow(lblHeader, Priority.ALWAYS);
    headerRow.setAlignment(Pos.CENTER_LEFT);

    lblLine = new Label("Line: —");
    lblLine.getStyleClass().add("sidebar-tool-subtitle");

    lblLineText = new Label("");
    lblLineText.setStyle("-fx-text-fill: #e6e6e6; -fx-font-family: monospace; -fx-font-size: 11px;");
    lblLineText.setWrapText(true);
    lblLineText.setMaxWidth(260);

    Label snapshotHeaderLbl = new Label("Scene Snapshot at Cursor");
    snapshotHeaderLbl.getStyleClass().add("sidebar-tool-section-title");
    HBox snapshotHeader = new HBox(6, snapshotHeaderLbl, helpButton("Scene Snapshot", """
        A scene snapshot is Puppeteer's reconstruction of everything visible \
on screen at a specific script line.

It reads your VNS script from the top up to the chosen line and collects:
  • Background — the current stage background image
  • Stage preset — camera framing, scale, and position
  • Timeline — any jes_timeline block active at that line
  • Visible characters — every @charimg, @charlayer, or @charpreset \
command that hasn't been hidden
  • Character expressions and positions

The snapshot is passed to Puppeteer as the starting scene, so you always \
animate from a contextually correct state.

If any required data is missing or ambiguous (e.g. a character referenced \
before being declared), a warning will appear in Snapshot Diagnostics below."""));
    snapshotHeader.setAlignment(Pos.CENTER_LEFT);

    lblLabel = new Label("Label: —");
    lblLabel.getStyleClass().add("sidebar-tool-subtitle");

    lblBackground = new Label("Background: —");
    lblBackground.getStyleClass().add("sidebar-tool-subtitle");

    lblStage = new Label("Stage: —");
    lblStage.getStyleClass().add("sidebar-tool-subtitle");

    lblTimeline = new Label("Timeline: —");
    lblTimeline.getStyleClass().add("sidebar-tool-subtitle");

    lblSummary = new Label("Snapshot: —");
    lblSummary.getStyleClass().add("sidebar-tool-summary");

    Label charsHeaderLbl = new Label("Visible Characters:");
    charsHeaderLbl.getStyleClass().add("sidebar-tool-section-title");
    HBox charsHeader = new HBox(6, charsHeaderLbl, helpButton("Visible Characters", """
        This section lists every character that is visible on screen at the \
chosen snapshot line.

A character is counted as visible if a @charimg, @charlayer, or @charpreset \
command has made it appear and no subsequent @external character <id> hide \
or equivalent command has hidden it before the cursor line.

Each entry shows:
  • Character ID
  • Active expression or layer-set name
  • Screen position (far_left / left / center / right / far_right)

Characters listed here will be present in Puppeteer's starting scene, so \
any animation you author begins from the correct stage layout."""));
    charsHeader.setAlignment(Pos.CENTER_LEFT);

    characterList = new VBox(2);
    characterList.setPadding(new Insets(0, 0, 0, 8));

    Label diagnosticsHeaderLbl = new Label("Snapshot Diagnostics:");
    diagnosticsHeaderLbl.getStyleClass().add("sidebar-tool-section-title");
    HBox diagnosticsHeader = new HBox(6, diagnosticsHeaderLbl, helpButton("Snapshot Diagnostics", """
        Diagnostics report anything that looks wrong or incomplete in the \
scene snapshot at the selected line.

Common warnings:
  • "Character X referenced before declaration" — a @charimg or @charlayer \
uses a character ID that hasn't been set up earlier in the script.
  • "Background not set" — no background has been declared up to this line; \
Puppeteer will open with an empty stage.
  • "Stage preset not found" — the @stagepreset name doesn't match any known \
preset in the project assets.
  • "Timeline binding missing" — a @external jes_timeline reference points \
to a file that doesn't exist on disk.

Click "Jump To Issue" to jump your editor cursor to the first problematic \
line so you can fix it before launching."""));
    diagnosticsHeader.setAlignment(Pos.CENTER_LEFT);

    diagnosticsList = new VBox(2);
    diagnosticsList.setPadding(new Insets(0, 0, 0, 8));

    lblRegisteredHeader = new Label("Registered Animations");
    lblRegisteredHeader.getStyleClass().add("sidebar-tool-section-title");
    HBox registeredHeaderRow = new HBox(6, lblRegisteredHeader, helpButton("Registered Animations", """
        Registered Animations are .jes timeline files that Puppeteer has \
discovered in your project's scripts/timelines/ directory.

A .jes file is created whenever you save an animation inside Puppeteer. \
Each file represents a named animation sequence — character movements, \
expression changes, layer transitions, etc.

To reference a timeline in a .vns script use:
  @external jes_timeline <timeline-id>

You can also write the timeline ID directly in dialogue:
  [jes_timeline my_anim_id]

Each card in this list shows the timeline's ID, file name, and the \
characters it involves. Clicking a card selects it; the Open Timeline \
button then opens it in the editor."""));
    registeredHeaderRow.setAlignment(Pos.CENTER_LEFT);

    lblRegisteredSummary = new Label("No registered timelines found yet.");
    lblRegisteredSummary.getStyleClass().add("sidebar-tool-subtitle");

    registeredTimelineCards = new VBox(8);
    registeredTimelineCards.setFillWidth(true);
    registeredTimelineCards.setPadding(new Insets(2, 2, 2, 2));
    registeredTimelineCards.setStyle("-fx-background-color: transparent;");

    registeredTimelineScroll = new ScrollPane(registeredTimelineCards);
    registeredTimelineScroll.setFitToWidth(true);
    registeredTimelineScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    registeredTimelineScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    registeredTimelineScroll.setPannable(true);
    registeredTimelineScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-background-insets: 0;");
    registeredTimelineScroll.setPrefViewportHeight(260);
    configureTransparentScrollPaneViewport(registeredTimelineScroll);
    VBox.setVgrow(registeredTimelineScroll, Priority.ALWAYS);

    btnLaunch = createActionButton(
        "Launch @ Cursor",
        SidebarToolIcon.nearMe("#f0f0f0"),
        "-fx-background-color: #3a3a3a; -fx-text-fill: #f0f0f0; -fx-font-weight: bold;",
        "Launch Puppeteer with scene snapshot from the current cursor line");
    btnLaunch.setOnAction(e -> {
      if (onLaunch != null) {
        launchAtCursor();
      }
    });

    btnLaunchLabelStart = createActionButton(
        "Launch @ Label Start",
        SidebarToolIcon.label("#e4e4e4"),
        "-fx-background-color: #2e2e2e; -fx-text-fill: #e4e4e4; -fx-font-weight: bold;",
        "Launch Puppeteer from the active label start line");
    btnLaunchLabelStart.setOnAction(e -> {
      if (onLaunch == null) return;
      int labelStartLine = resolveActiveLabelStartLine(currentSource, currentLine);
      onLaunch.accept(new LaunchRequest(buildSnapshot(labelStartLine), null));
    });

    btnLaunchSceneStart = createActionButton(
        "Launch @ Scene Start",
        SidebarToolIcon.movie("#e4e4e4"),
        "-fx-background-color: #2f2f2f; -fx-text-fill: #e4e4e4; -fx-font-weight: bold;",
        "Launch Puppeteer from the most recent background change in the active label");
    btnLaunchSceneStart.setOnAction(e -> {
      if (onLaunch == null) return;
      int sceneStartLine = resolveSceneStartLine(currentSource, currentLine);
      onLaunch.accept(new LaunchRequest(buildSnapshot(sceneStartLine), null));
    });

    btnOpenTimeline = createActionButton(
        "Open Timeline",
        SidebarToolIcon.timeline("#e0e0e0"),
        "-fx-background-color: #2d2d2d; -fx-text-fill: #e0e0e0;",
        "Open the related timeline file or inline block");
    btnOpenTimeline.setOnAction(e -> {
      if (onOpenTarget == null) return;
      if (selectedTimelineFile != null) {
          onOpenTarget.accept(new OpenTarget(selectedTimelineFile, 0));
          return;
      }
      OpenTarget target = resolveTimelineOpenTarget(buildSnapshot(currentLine));
      if (target != null) onOpenTarget.accept(target);
    });

    btnNewBlankTimeline = createActionButton(
        "New Animation",
        SidebarToolIcon.plus("#e0e0e0"),
        "-fx-background-color: #2d2d2d; -fx-text-fill: #e0e0e0;",
        "Create a new blank timeline in Puppeteer");
    btnNewBlankTimeline.setOnAction(e -> {
      if (onLaunch != null) {
        onLaunch.accept(new LaunchRequest(null, null));
      }
    });

    btnOpenIssue = createActionButton(
        "Jump To Issue",
        SidebarToolIcon.warning("#dfdfdf"),
        "-fx-background-color: #303030; -fx-text-fill: #dfdfdf;",
        "Jump to the first launcher issue in the active VNS source");
    btnOpenIssue.setOnAction(e -> {
      if (onOpenTarget == null) return;
      OpenTarget target = resolvePrimaryIssueOpenTarget(buildSnapshot(currentLine));
      if (target != null) onOpenTarget.accept(target);
    });

    HBox actionRow = new HBox(6, btnLaunch, btnLaunchLabelStart, btnLaunchSceneStart);
    HBox.setHgrow(btnLaunch, Priority.ALWAYS);
    HBox.setHgrow(btnLaunchLabelStart, Priority.ALWAYS);
    HBox.setHgrow(btnLaunchSceneStart, Priority.ALWAYS);

    HBox openRow = new HBox(6, btnOpenTimeline, btnNewBlankTimeline, btnOpenIssue);
    HBox.setHgrow(btnOpenTimeline, Priority.ALWAYS);
    HBox.setHgrow(btnNewBlankTimeline, Priority.ALWAYS);
    HBox.setHgrow(btnOpenIssue, Priority.ALWAYS);

    getChildren().addAll(
        headerRow,
        new Separator(),
        lblLine,
        lblLineText,
        new Separator(),
        snapshotHeader,
        lblLabel,
        lblBackground,
        lblStage,
        lblTimeline,
        lblSummary,
        charsHeader,
        characterList,
        new Separator(),
        diagnosticsHeader,
        diagnosticsList,
        new Separator(),
        openRow,
        actionRow,
        new Separator(),
        registeredHeaderRow,
        lblRegisteredSummary,
        registeredTimelineScroll
    );

    updateEmpty();
  }

  private Button helpButton(String title, String body) {
    return SidebarToolHelp.button(this, title, body);
  }

  public void setOnLaunch(Consumer<LaunchRequest> handler) {
    this.onLaunch = handler;
  }

  public void setOnOpenTarget(Consumer<OpenTarget> handler) {
    this.onOpenTarget = handler;
  }

  private void launchAtCursor() {
    if (onLaunch == null) return;
    SceneSnapshot snapshot = buildSnapshot(currentLine);
    if (snapshot == null || !snapshot.hasTimelineContext()) {
      onLaunch.accept(new LaunchRequest(snapshot, null, LaunchTimelineMode.NEW_FROM_SNAPSHOT));
      return;
    }

    VBox body = new VBox(8);
    body.getStyleClass().add("editor-dialog-form");

    Label context = new Label("Timeline: " + describeTimelineContext(snapshot)
        + "\nLine: " + Math.max(1, snapshot.atLine + 1)
        + (snapshot.currentLabel != null && !snapshot.currentLabel.isBlank() ? "\nLabel: " + snapshot.currentLabel : ""));
    context.setStyle("-fx-text-fill: #d8d8d8; -fx-font-size: 12px;");
    context.setWrapText(true);

    Label load = new Label("Load Timeline imports the most recent inline or registered timeline at the cursor, using the current script scene as its stage.");
    load.setStyle("-fx-text-fill: #c9d8ff; -fx-font-size: 11px;");
    load.setWrapText(true);

    Label fresh = new Label("New From Scene opens a blank timeline from the latest reconstructed background and character positions. If a timeline already ran before this cursor, Puppeteer will apply its final transform to the scene only.");
    fresh.setStyle("-fx-text-fill: #d5d5d5; -fx-font-size: 11px;");
    fresh.setWrapText(true);

    body.getChildren().addAll(context, load, fresh);

    Optional<String> action = EditorDialogs.show(
        getScene() == null ? null : getScene().getWindow(),
        "Launch Puppeteer",
        "Choose how Puppeteer should use the timeline found at this cursor.",
        body,
        EditorDialogs.ActionSpec.neutral("cancel", "Cancel", null).defaultFocus(false),
        EditorDialogs.ActionSpec.neutral("new", "New From Scene", null),
        EditorDialogs.ActionSpec.accent("load", "Load Timeline", null).defaultFocus(true));
    if (action.isEmpty() || "cancel".equals(action.get())) return;

    LaunchTimelineMode mode = "new".equals(action.get())
        ? LaunchTimelineMode.NEW_FROM_SNAPSHOT
        : LaunchTimelineMode.LOAD_TIMELINE;
    onLaunch.accept(new LaunchRequest(snapshot, null, mode));
  }

  
  public void setActiveEditingTimeline(String timelineId) {
    this.activeEditingTimeline = timelineId;
    refreshRegisteredAnimations(null);
  }
  public void setProjectRoot(File projectRoot) {
    this.projectRoot = projectRoot;
    invalidateRegisteredAnimationsCache();
    refresh();
  }

  public void setActiveScriptFile(File activeScriptFile) {
    this.activeScriptFile = activeScriptFile;
  }

  public void setSource(String source) {
    this.currentSource = source != null ? source : "";
    refresh();
  }

  public void setCaretLine(int zeroBased) {
    this.currentLine = zeroBased;
    refresh();
  }

  public void clear() {
    currentSource = "";
    currentLine = 0;
    updateEmpty();
  }

  private void updateEmpty() {
    lblLine.setText("Line: —");
    lblLineText.setText("(no VNS file active)");
    lblLabel.setText("Label: —");
    lblBackground.setText("Background: —");
    lblStage.setText("Stage: —");
    lblTimeline.setText("Timeline: —");
    lblSummary.setText("Snapshot: —");
    characterList.getChildren().clear();
    Label none = new Label("—");
    none.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
    characterList.getChildren().add(none);
    diagnosticsList.getChildren().clear();
    Label hint = new Label("Open a .vns file to enable launch actions.");
    hint.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
    diagnosticsList.getChildren().add(hint);
    btnLaunch.setDisable(true);
    btnLaunchLabelStart.setDisable(true);
    btnLaunchSceneStart.setDisable(true);
    selectedTimelineId = null;
    selectedTimelineFile = null;
    btnOpenTimeline.setDisable(true);
    btnOpenIssue.setDisable(true);
    refreshRegisteredAnimations(null);
  }

  private void refresh() {
    if (currentSource.isEmpty()) {
      updateEmpty();
      return;
    }

    String[] lines = currentSource.split("\n", -1);
    int lineIdx = Math.min(currentLine, lines.length - 1);
    lineIdx = Math.max(0, lineIdx);

    lblLine.setText("Line: " + (lineIdx + 1));
    String lineText = lines[lineIdx].trim();
    lblLineText.setText(lineText.length() > 80 ? lineText.substring(0, 80) + "…" : lineText);

    SceneSnapshot snap = buildSnapshot(lineIdx);

    lblLabel.setText("Label: " + (snap.currentLabel != null ? snap.currentLabel : "(before first label)"));
    lblBackground.setText("Background: " + (snap.backgroundId != null ? snap.backgroundId : "—"));
    lblStage.setText("Stage: " + (snap.activeStagePresetId != null ? snap.activeStagePresetId : "—"));
    lblTimeline.setText("Timeline: " + describeTimelineContext(snap));
    lblSummary.setText(
        "Snapshot: " + snap.characters.size() + " character(s)"
            + " • scene start " + (resolveSceneStartLine(currentSource, lineIdx) + 1)
            + " • source line " + (lineIdx + 1));

    characterList.getChildren().clear();
    if (snap.characters.isEmpty()) {
      Label none = new Label("(none visible)");
      none.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
      characterList.getChildren().add(none);
    } else {
      for (CharacterEntry ch : snap.characters) {
        String text = ch.characterId + " @ " + ch.position;
        if (ch.expression != null && !ch.expression.equals("neutral")) {
          text += " [" + ch.expression + "]";
        }
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: #f0b673; -fx-font-size: 11px; -fx-font-family: monospace;");
        characterList.getChildren().add(lbl);
      }
    }

    diagnosticsList.getChildren().clear();
    List<String> diagnostics = buildDiagnostics(snap, projectRoot);
    if (diagnostics.isEmpty()) {
      Label ok = new Label("All visible snapshot assets are mapped.");
      ok.setStyle("-fx-text-fill: #8bd17c; -fx-font-size: 11px;");
      diagnosticsList.getChildren().add(ok);
    } else {
      for (String msg : diagnostics) {
        Label warn = new Label("• " + msg);
        warn.setStyle("-fx-text-fill: #f0b673; -fx-font-size: 11px;");
        warn.setWrapText(true);
        diagnosticsList.getChildren().add(warn);
      }
    }

    btnLaunch.setDisable(false);
    btnLaunchLabelStart.setDisable(false);
    btnLaunchSceneStart.setDisable(false);
    btnOpenTimeline.setDisable(selectedTimelineId == null && resolveTimelineOpenTarget(snap) == null);
    btnOpenIssue.setDisable(resolvePrimaryIssueOpenTarget(snap) == null);
    refreshRegisteredAnimations(snap);
  }

  private static Button createActionButton(String text, Region icon, String style, String tooltip) {
    Button button = new Button(text);
    button.setStyle(style);
    button.setMaxWidth(Double.MAX_VALUE);
    button.setGraphic(icon);
    button.setContentDisplay(ContentDisplay.LEFT);
    button.setGraphicTextGap(8);
    button.setTooltip(new Tooltip(tooltip));
    return button;
  }

  private static void configureTransparentScrollPaneViewport(ScrollPane scrollPane) {
    if (scrollPane == null) return;
    Runnable applyViewportStyle = () -> {
      if (scrollPane.lookup(".viewport") instanceof Region viewport) {
        viewport.setStyle("-fx-background-color: transparent;");
      }
    };
    scrollPane.skinProperty().addListener((obs, oldSkin, newSkin) -> applyViewportStyle.run());
    applyViewportStyle.run();
  }

  private void refreshRegisteredAnimations(SceneSnapshot snapshot) {
    registeredTimelineCards.getChildren().clear();

    if (projectRoot == null) {
      lblRegisteredSummary.setText("Open a project to browse scripts/timelines.");
      registeredTimelineCards.getChildren().add(createPlaceholderCard(
          "No project root is active.",
          "Open a project or VNS file to browse registered Puppeteer timelines."));
      return;
    }

    List<RegisteredAnimation> animations = getRegisteredAnimations();
    String preferredTimelineName = snapshot != null ? snapshot.preferredTimelineName() : null;
    int count = animations.size();
    int clusterCount = countClusters(animations);
    String summary = count == 1
        ? "1 timeline found"
        : count + " timelines found";
    if (clusterCount > 1) {
      summary += " across " + clusterCount + " clusters";
    }
    summary += " in scripts/timelines.";
    if (preferredTimelineName != null && !preferredTimelineName.isBlank()) {
      boolean matched = animations.stream()
          .anyMatch(animation -> preferredTimelineName.equals(resolveRelativeTimelineStem(animation)));
      summary += matched
          ? " Current cursor references one of them."
          : " Current cursor timeline is not registered on disk.";
    }
    lblRegisteredSummary.setText(summary);

    if (animations.isEmpty()) {
      registeredTimelineCards.getChildren().add(createPlaceholderCard(
          "No timelines registered yet.",
          "Save a Puppeteer timeline to scripts/timelines to reopen it directly from this launcher."));
      return;
    }

    Map<String, List<RegisteredAnimation>> byCluster = new LinkedHashMap<>();
    for (RegisteredAnimation animation : animations) {
      byCluster.computeIfAbsent(animation.clusterName(), ignored -> new ArrayList<>()).add(animation);
    }

    boolean showClusterHeaders = byCluster.size() > 1
        || !byCluster.keySet().stream().allMatch(cluster -> "Root".equals(cluster));
    for (Map.Entry<String, List<RegisteredAnimation>> entry : byCluster.entrySet()) {
      if (showClusterHeaders) {
        Label clusterHeader = new Label(entry.getKey());
        clusterHeader.setStyle("-fx-text-fill: #c9c9c9; -fx-font-size: 10px; -fx-font-weight: bold;");
        registeredTimelineCards.getChildren().add(clusterHeader);
      }
      for (RegisteredAnimation animation : entry.getValue()) {
        registeredTimelineCards.getChildren().add(createRegisteredAnimationCard(animation, snapshot));
      }
    }
  }

  private javafx.scene.layout.Region createRegisteredAnimationCard(RegisteredAnimation animation, SceneSnapshot snapshot) {
    String preferredTimelineName = snapshot != null ? snapshot.preferredTimelineName() : null;
    String timelineId = resolveRelativeTimelineStem(animation);
    boolean suggested = preferredTimelineName != null && preferredTimelineName.equals(timelineId);
    boolean importable = animation.importable();
    boolean isEditing = timelineId != null && timelineId.equals(activeEditingTimeline);
    
    String scenePreviewText = shouldShowScenePreview(animation, suggested)
        ? describeScenePreview(snapshot)
        : "";
    String resolvedPreviewText = resolveRegisteredAnimationPreviewText(animation, snapshot);

    Label title = new Label(animation.name());
    title.setStyle("-fx-text-fill: #f2f4f8; -fx-font-size: 11px; -fx-font-weight: bold;");

    HBox titleRow = new HBox(6);
    titleRow.getChildren().add(title);
    
    if (isEditing) {
      javafx.scene.control.ProgressIndicator spinner = new javafx.scene.control.ProgressIndicator();
      spinner.setPrefSize(12, 12);
      spinner.setStyle("-fx-progress-color: #f0a0d0;");
      Label badge = new Label("Currently being edited", spinner);
      badge.setGraphicTextGap(4);
      badge.setStyle("-fx-background-color: #2b2027; -fx-border-color: #f0a0d0; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 2 6 2 4; -fx-text-fill: #f0a0d0; -fx-font-size: 10px; -fx-font-weight: bold;");
      titleRow.getChildren().add(badge);
    } else if (suggested) {
      Label badge = new Label("Cursor Match");
      badge.setStyle("-fx-background-color: #3a3a3a; -fx-background-radius: 999; -fx-padding: 1 7 1 7; -fx-text-fill: #e4e4e4; -fx-font-size: 9px; -fx-font-weight: bold;");
      titleRow.getChildren().add(badge);
    }

    if (!importable) {
      Label badge = new Label("Import Issue");
      badge.setStyle("-fx-background-color: #3a3a3a; -fx-background-radius: 999; -fx-padding: 1 7 1 7; -fx-text-fill: #e0e0e0; -fx-font-size: 9px; -fx-font-weight: bold;");
      titleRow.getChildren().add(badge);
    }

    if (animation.relativePath() != null
        && !animation.relativePath().isBlank()
        && !animation.relativePath().equals(animation.name() + ".jes")) {
      Label badge = new Label(animation.relativePath());
      badge.setStyle("-fx-background-color: #353535; -fx-background-radius: 999; -fx-padding: 1 7 1 7; -fx-text-fill: #cdcdcd; -fx-font-size: 9px;");
      titleRow.getChildren().add(badge);
    }

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    titleRow.getChildren().add(spacer);

    Button copyButton = createCardActionButton(
        SidebarToolIcon.copy("#d5d5d5"),
        "Copy timeline link to clipboard");
    copyButton.setDisable(!importable);
    copyButton.setOnAction(e -> {
      if (importable) {
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString("@external jes_timeline " + timelineId);
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
      }
    });

    Button openButton = createCardActionButton(
        SidebarToolIcon.timeline("#d5d5d5"),
        "Open timeline");
    openButton.setDisable(!importable);
    openButton.setOnAction(e -> {
      if (onLaunch != null && importable) {
        onLaunch.accept(new LaunchRequest(snapshot, timelineId));
      }
    });

    Button renameButton = createCardActionButton(
        SidebarToolIcon.edit("#d5d5d5"),
        "Rename timeline");
    renameButton.setOnAction(e -> renameRegisteredAnimation(animation));

    Button deleteButton = createCardActionButton(
        SidebarToolIcon.delete("#d5d5d5"),
        "Delete timeline");
    deleteButton.setOnAction(e -> deleteRegisteredAnimation(animation));

    titleRow.getChildren().addAll(copyButton, openButton, renameButton, deleteButton);

    Label meta = new Label(animation.statsText());
    meta.setStyle("-fx-text-fill: #a3a3a3; -fx-font-size: 9px;");
    meta.setWrapText(true);

    Label preview = new Label(resolvedPreviewText);
    preview.setStyle("-fx-text-fill: #d5d5d5; -fx-font-size: 9px; -fx-font-family: monospace;");
    preview.setWrapText(true);

    VBox body = new VBox(3, titleRow, meta);
    if (!scenePreviewText.isBlank()) {
      Label scenePreview = new Label(scenePreviewText);
      scenePreview.setStyle("-fx-text-fill: #bdbdbd; -fx-font-size: 9px;");
      scenePreview.setWrapText(true);
      body.getChildren().add(scenePreview);
    }
    body.getChildren().add(preview);
    if (!importable && animation.warningMessage() != null && !animation.warningMessage().isBlank()) {
      Label warning = new Label(animation.warningMessage());
      warning.setStyle("-fx-text-fill: #b0b0b0; -fx-font-size: 9px;");
      warning.setWrapText(true);
      body.getChildren().add(warning);
    }
    body.setMaxWidth(Double.MAX_VALUE);
    body.setPadding(new Insets(8, 10, 8, 10));
    Tooltip.install(body, new Tooltip(animation.file().getName()));
    
    if (isEditing) {
        copyButton.setDisable(true);
        openButton.setDisable(true);
        renameButton.setDisable(true);
        deleteButton.setDisable(true);
        body.getStyleClass().add("sidebar-tool-card-disabled");
    } else if (!importable) {
      body.getStyleClass().add("sidebar-tool-card-disabled");
    } else if (suggested) {
      body.getStyleClass().add("sidebar-tool-card-suggested");
    } else {
      body.getStyleClass().add("sidebar-tool-card");
    }

    javafx.scene.layout.StackPane cardRoot = new javafx.scene.layout.StackPane(body);
    
    boolean isSelected = timelineId != null && timelineId.equals(selectedTimelineId);
    if (isSelected) {
        selectedTimelineFile = animation.file();
        javafx.scene.canvas.Canvas lassoCanvas = new javafx.scene.canvas.Canvas();
        lassoCanvas.setMouseTransparent(true);
        lassoCanvas.widthProperty().bind(body.widthProperty());
        lassoCanvas.heightProperty().bind(body.heightProperty());
        
        double[] offset = {0.0};
        javafx.animation.Timeline lassoTimeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.millis(40), evt -> {
                offset[0] -= 2.2;
                javafx.scene.canvas.GraphicsContext gc = lassoCanvas.getGraphicsContext2D();
                double w = lassoCanvas.getWidth();
                double h = lassoCanvas.getHeight();
                gc.clearRect(0, 0, w, h);
                gc.setStroke(javafx.scene.paint.Color.web("#a0d0f0"));
                gc.setLineWidth(1.5);
                gc.setLineDashes(4, 4);
                gc.setLineDashOffset(offset[0]);
                gc.strokeRoundRect(1, 1, w - 2, h - 2, 8, 8);
            })
        );
        lassoTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        
        cardRoot.getChildren().add(lassoCanvas);
        
        lassoCanvas.sceneProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) lassoTimeline.stop();
            else lassoTimeline.play();
        });
    }

    body.setOnMouseClicked(e -> {
      if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
          if (e.getClickCount() == 2 && !isEditing && importable) {
              if (onLaunch != null) {
                  onLaunch.accept(new LaunchRequest(snapshot, timelineId));
              }
          } else if (e.getClickCount() == 1) {
              if (timelineId != null && !timelineId.equals(selectedTimelineId)) {
                  selectedTimelineId = timelineId;
                  selectedTimelineFile = animation.file();
                  refresh();
              }
          }
      }
    });

    return cardRoot;
  }

  private VBox createPlaceholderCard(String title, String detail) {
    Label titleLabel = new Label(title);
    titleLabel.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 11px; -fx-font-weight: bold;");

    Label detailLabel = new Label(detail);
    detailLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
    detailLabel.setWrapText(true);

    VBox box = new VBox(4, titleLabel, detailLabel);
    box.setPadding(new Insets(12));
    box.setStyle("-fx-background-color: #202020; -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: #373737;");
    return box;
  }

  private static int countClusters(List<RegisteredAnimation> animations) {
    Set<String> clusters = new HashSet<>();
    for (RegisteredAnimation animation : animations) {
      if (animation == null) continue;
      clusters.add(animation.clusterName());
    }
    return clusters.isEmpty() ? 0 : clusters.size();
  }

  private Button createCardActionButton(Region icon, String tooltip) {
    Button button = new Button();
    button.getStyleClass().add("sidebar-tool-btn");
    button.setGraphic(icon);
    button.setTooltip(new Tooltip(tooltip));
    button.setAccessibleText(tooltip);
    button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    button.setFocusTraversable(false);
    return button;
  }

  private void renameRegisteredAnimation(RegisteredAnimation animation) {
    if (animation == null || projectRoot == null) return;
    EditorDialogs.promptText(
        getScene() != null ? getScene().getWindow() : null,
        "Rename Timeline",
        "Rename or move '" + animation.name() + "'",
        "Timeline path",
        resolveRelativeTimelineStem(animation),
        resolveRelativeTimelineStem(animation),
        "Rename").ifPresent(rawName -> {
      try {
        String normalized = normalizeTimelineRelativeStem(rawName);
        Path timelinesRoot = resolveTimelinesDir(projectRoot).toPath().toAbsolutePath().normalize();
        Path source = animation.file().toPath().toAbsolutePath().normalize();
        Path target = timelinesRoot.resolve(normalized + ".jes").normalize();
        if (!target.startsWith(timelinesRoot)) {
          throw new IOException("Timeline path must stay inside scripts/timelines.");
        }
        if (source.equals(target)) return;
        if (Files.exists(target)) {
          throw new IOException("A timeline with that name already exists.");
        }
        Files.createDirectories(target.getParent());
        Files.move(source, target);
        pruneEmptyTimelineDirectories(source.getParent(), timelinesRoot);
        invalidateRegisteredAnimationsCache();
        refresh();
      } catch (IOException ex) {
        showTimelineFileError("Rename Failed", "Could not rename timeline.", ex.getMessage());
      }
    });
  }

  private void deleteRegisteredAnimation(RegisteredAnimation animation) {
    if (animation == null || projectRoot == null) return;
    if (!EditorDialogs.confirm(
        getScene() != null ? getScene().getWindow() : null,
        "Delete Timeline",
        "Delete '" + animation.name() + "'?\nThis removes " + animation.relativePath() + " from scripts/timelines.",
        "Delete",
        true)) {
      return;
    }
    try {
      Path timelinesRoot = resolveTimelinesDir(projectRoot).toPath().toAbsolutePath().normalize();
      Path source = animation.file().toPath().toAbsolutePath().normalize();
      Files.deleteIfExists(source);
      pruneEmptyTimelineDirectories(source.getParent(), timelinesRoot);
      invalidateRegisteredAnimationsCache();
      refresh();
    } catch (IOException ex) {
      showTimelineFileError("Delete Failed", "Could not delete timeline.", ex.getMessage());
    }
  }

  private void showTimelineFileError(String title, String header, String detail) {
    String body = detail == null || detail.isBlank() ? "Unknown error" : detail;
    EditorDialogs.show(
        getScene() != null ? getScene().getWindow() : null,
        title,
        header,
        new Label(body),
        EditorDialogs.ActionSpec.accent("close", "Close", null));
  }

  private static String resolveRelativeTimelineStem(RegisteredAnimation animation) {
    if (animation == null || animation.relativePath() == null || animation.relativePath().isBlank()) {
      return "";
    }
    String relativePath = animation.relativePath().replace('\\', '/');
    return relativePath.endsWith(".jes")
        ? relativePath.substring(0, relativePath.length() - 4)
        : relativePath;
  }

  private static boolean shouldShowScenePreview(RegisteredAnimation animation, boolean suggested) {
    if (animation == null) return false;
    return suggested || isEmptyTimelinePreview(animation.previewText());
  }

  private static String normalizeTimelineRelativeStem(String rawName) throws IOException {
    String normalized = rawName == null ? "" : rawName.trim().replace('\\', '/');
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    if (normalized.endsWith(".jes")) {
      normalized = normalized.substring(0, normalized.length() - 4);
    }
    normalized = normalized.replaceAll("/+", "/");
    normalized = normalized.replaceAll("^/+|/+$", "");
    if (normalized.isBlank()) {
      throw new IOException("Timeline path is empty.");
    }
    if (normalized.contains("..")) {
      throw new IOException("Parent path segments are not allowed.");
    }
    return normalized;
  }

  private static void pruneEmptyTimelineDirectories(Path start, Path timelinesRoot) throws IOException {
    if (start == null || timelinesRoot == null) return;
    Path current = start;
    while (current != null && !current.equals(timelinesRoot)) {
      try (var stream = Files.list(current)) {
        if (stream.findAny().isPresent()) {
          return;
        }
      }
      Files.deleteIfExists(current);
      current = current.getParent();
    }
  }

  private static List<File> collectTimelineFiles(File timelinesDir) {
    if (timelinesDir == null || !timelinesDir.isDirectory()) return List.of();
    List<File> files = new ArrayList<>();
    try (var stream = Files.walk(timelinesDir.toPath())) {
      stream.filter(Files::isRegularFile)
          .filter(path -> {
            String name = path.getFileName().toString();
            return name.endsWith(".jes") && !name.startsWith(".");
          })
          .sorted((left, right) -> left.toString().compareToIgnoreCase(right.toString()))
          .forEach(path -> files.add(path.toFile()));
    } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
    }
    return files;
  }

  private static String relativizeTimelinePath(File timelinesDir, File jesFile) {
    if (timelinesDir == null || jesFile == null) return jesFile == null ? "" : jesFile.getName();
    Path root = timelinesDir.toPath().toAbsolutePath().normalize();
    Path file = jesFile.toPath().toAbsolutePath().normalize();
    if (!file.startsWith(root)) return jesFile.getName();
    return root.relativize(file).toString().replace('\\', '/');
  }

  private static String resolveClusterName(String relativePath) {
    if (relativePath == null || relativePath.isBlank()) return "Root";
    int slash = relativePath.lastIndexOf('/');
    if (slash <= 0) return "Root";
    return relativePath.substring(0, slash);
  }

  private void invalidateRegisteredAnimationsCache() {
    cachedRegisteredAnimations = List.of();
    cachedRegisteredAnimationsRoot = "";
    cachedRegisteredAnimationsStamp = Long.MIN_VALUE;
  }

  private List<RegisteredAnimation> getRegisteredAnimations() {
    if (projectRoot == null) return List.of();
    String rootKey = projectRoot.getAbsoluteFile().toPath().normalize().toString();
    long stamp = computeRegisteredAnimationsStamp(projectRoot);
    if (!rootKey.equals(cachedRegisteredAnimationsRoot) || stamp != cachedRegisteredAnimationsStamp) {
      cachedRegisteredAnimations = discoverRegisteredAnimations(projectRoot);
      cachedRegisteredAnimationsRoot = rootKey;
      cachedRegisteredAnimationsStamp = stamp;
    }
    return cachedRegisteredAnimations;
  }

  static List<RegisteredAnimation> discoverRegisteredAnimations(File projectRoot) {
    File timelinesDir = resolveTimelinesDir(projectRoot);
    if (!timelinesDir.isDirectory()) return List.of();
    List<File> jesFiles = collectTimelineFiles(timelinesDir);
    if (jesFiles.isEmpty()) return List.of();

    List<RegisteredAnimation> animations = new ArrayList<>();
    for (File jesFile : jesFiles) {
      animations.add(summarizeRegisteredAnimation(timelinesDir, jesFile));
    }
    return List.copyOf(animations);
  }

  private static RegisteredAnimation summarizeRegisteredAnimation(File timelinesDir, File jesFile) {
    String filename = jesFile.getName();
    String timelineName = filename.endsWith(".jes") ? filename.substring(0, filename.length() - 4) : filename;
    String relativePath = relativizeTimelinePath(timelinesDir, jesFile);
    String clusterName = resolveClusterName(relativePath);
    String code;
    try {
      code = Files.readString(jesFile.toPath(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      return new RegisteredAnimation(
          timelineName,
          relativePath,
          clusterName,
          jesFile,
          "Could not read timeline file.",
          "Read failed",
          0,
          0,
          0,
          0,
          false,
          ex.getMessage());
    }

    String previewText = extractTimelinePreview(code);
    try {
      AnimationProject project = CodeImporter.importCode(timelineName, code);
      int trackCount = project.getTrackCount();
      int keyframeCount = countKeyframes(project);
      int audioCueCount = project.getAudioCues().size();
      int eventCueCount = project.getEditorEventCues().size();
      String stats = trackCount + " track(s) • "
          + keyframeCount + " keyframe(s) • "
          + formatDuration(project.getTotalDurationMs());
      if (audioCueCount > 0 || eventCueCount > 0) {
        stats += " • " + audioCueCount + " audio • " + eventCueCount + " cue(s)";
      }
      return new RegisteredAnimation(
          timelineName,
          relativePath,
          clusterName,
          jesFile,
          previewText,
          stats,
          trackCount,
          keyframeCount,
          audioCueCount,
          eventCueCount,
          true,
          "");
    } catch (Exception ex) {
      return new RegisteredAnimation(
          timelineName,
          relativePath,
          clusterName,
          jesFile,
          previewText,
          "Import warning • raw preview only",
          0,
          0,
          0,
          0,
          false,
          ex.getMessage());
    }
  }

  static String extractTimelinePreview(String code) {
    if (code == null || code.isBlank()) return "No timeline content yet.";
    List<String> previewLines = new ArrayList<>();
    boolean truncated = false;
    for (String rawLine : code.split("\n")) {
      String trimmed = rawLine == null ? "" : rawLine.trim();
      if (trimmed.isEmpty()) continue;
      if (trimmed.startsWith("//")) continue;
      if ("{".equals(trimmed) || "}".equals(trimmed)) continue;
      if (trimmed.startsWith("timeline")) continue;
      previewLines.add(trimmed);
      if (previewLines.size() >= 2) {
        truncated = true;
        break;
      }
    }
    if (previewLines.isEmpty()) {
      return "Timeline block is empty.";
    }
    if (truncated) {
      int lastIndex = previewLines.size() - 1;
      previewLines.set(lastIndex, previewLines.get(lastIndex) + " …");
    }
    return String.join("\n", previewLines);
  }

  static String describeScenePreview(SceneSnapshot snapshot) {
    if (snapshot == null) return "";
    List<String> lines = new ArrayList<>();
    List<String> sceneBits = new ArrayList<>();
    if (snapshot.currentLabel != null && !snapshot.currentLabel.isBlank()) {
      sceneBits.add("label " + snapshot.currentLabel);
    }
    if (snapshot.backgroundId != null && !snapshot.backgroundId.isBlank()) {
      sceneBits.add("bg " + snapshot.backgroundId);
    }
    if (snapshot.activeStagePresetId != null && !snapshot.activeStagePresetId.isBlank()) {
      sceneBits.add("stage " + snapshot.activeStagePresetId);
    }
    if (!sceneBits.isEmpty()) {
      lines.add("Scene • " + String.join(" • ", sceneBits));
    }
    if (!snapshot.characters.isEmpty()) {
      List<String> entities = new ArrayList<>();
      int shown = Math.min(snapshot.characters.size(), 3);
      for (int i = 0; i < shown; i++) {
        CharacterEntry ch = snapshot.characters.get(i);
        entities.add(formatCharacterPreview(ch));
      }
      if (snapshot.characters.size() > shown) {
        entities.add("+" + (snapshot.characters.size() - shown) + " more");
      }
      lines.add("Entities • " + String.join(", ", entities));
    } else if (!lines.isEmpty()) {
      lines.add("Entities • none visible");
    }
    return String.join("\n", lines);
  }

  static String resolveRegisteredAnimationPreviewText(RegisteredAnimation animation, SceneSnapshot snapshot) {
    String previewText = animation != null ? animation.previewText() : "";
    if (isEmptyTimelinePreview(previewText)) {
      String scenePreview = describeScenePreview(snapshot);
      if (!scenePreview.isBlank()) {
        return "No authored timeline actions yet.\nLaunch uses the scene preview above.";
      }
    }
    return previewText == null || previewText.isBlank() ? "No timeline content yet." : previewText;
  }

  private static boolean isEmptyTimelinePreview(String previewText) {
    if (previewText == null || previewText.isBlank()) return true;
    return "Timeline block is empty.".equals(previewText)
        || "No timeline content yet.".equals(previewText);
  }

  private static String formatCharacterPreview(CharacterEntry character) {
    if (character == null) return "unknown";
    String text = character.characterId + " @ " + character.position;
    if (character.expression != null && !character.expression.isBlank() && !"neutral".equals(character.expression)) {
      text += " [" + character.expression + "]";
    }
    return text;
  }

  private static int countKeyframes(AnimationProject project) {
    if (project == null) return 0;
    int total = 0;
    for (EntityTrack track : project.getTracks()) {
      if (track == null) continue;
      for (PropertyType property : track.getAnimatedProperties()) {
        total += track.getKeyframes(property).size();
      }
    }
    return total;
  }

  private static String formatDuration(double totalDurationMs) {
    if (!Double.isFinite(totalDurationMs) || totalDurationMs <= 0.0) {
      return "0.0s";
    }
    double seconds = totalDurationMs / 1000.0;
    if (seconds >= 10.0) {
      return String.format(Locale.ROOT, "%.0fs", seconds);
    }
    return String.format(Locale.ROOT, "%.1fs", seconds);
  }

  private static File resolveTimelinesDir(File projectRoot) {
    return projectRoot == null ? new File("scripts/timelines") : new File(projectRoot, "scripts/timelines");
  }

  private static long computeRegisteredAnimationsStamp(File projectRoot) {
    File timelinesDir = resolveTimelinesDir(projectRoot);
    if (!timelinesDir.isDirectory()) return Long.MIN_VALUE;
    List<File> jesFiles = collectTimelineFiles(timelinesDir);
    if (jesFiles.isEmpty()) return 0L;
    long stamp = 17L;
    for (File jesFile : jesFiles) {
      stamp = stamp * 31L + jesFile.getAbsolutePath().toLowerCase(Locale.ROOT).hashCode();
      stamp = stamp * 31L + jesFile.length();
      stamp = stamp * 31L + jesFile.lastModified();
    }
    return stamp;
  }

  // --- VNS Scene State Resolver ---

  public static SceneSnapshot resolveSnapshot(String source, int upToLine) {
    return resolveSnapshot(source, upToLine, (String) null, null);
  }

  public static SceneSnapshot resolveSnapshot(
      String source,
      int upToLine,
      String sourceName,
      IncludeSourceResolver includeResolver
  ) {
    source = LayeredCharacterResolver.collapseLayerDirectiveContinuations(source);
    String[] lines = source.split("\n", -1);
    int limit = Math.max(0, Math.min(upToLine, lines.length - 1));

    String currentLabel = null;
    String backgroundId = null;
    String previousBackgroundId = null;
    int backgroundLine = -1;
    Map<String, CharacterEntry> visible = new LinkedHashMap<>();
    Map<String, String> bgPaths = new LinkedHashMap<>();
    Map<String, String> stagePresetPaths = new LinkedHashMap<>();
    Map<String, String> charImgPaths = new LinkedHashMap<>();
    Map<String, Double> characterScales = new LinkedHashMap<>();
    Map<String, Map<String, String>> charLayerPaths = new LinkedHashMap<>();
    Map<String, List<CharacterLayerEntry>> charPresetLayers = new LinkedHashMap<>();
    Map<String, CharacterLayerGroupEntry> charLayerGroups = new LinkedHashMap<>();
    Map<String, SnapshotPosition> customPositions = new LinkedHashMap<>();
    Map<String, List<DisplayPresetEntry>> displayPresets = new LinkedHashMap<>();
    Map<String, String> dynamicGroups = new LinkedHashMap<>();
    String activeStagePresetId = null;

    boolean firstBackgroundFound = false;
    int activeStageLine = -1;
    String referencedTimelineName = null;
    int referencedTimelineLine = -1;

    collectDeclarations(
        source,
        limit,
        sourceName,
        includeResolver,
        bgPaths,
        stagePresetPaths,
        charImgPaths,
        characterScales,
        charLayerPaths,
        charPresetLayers,
        charLayerGroups,
        customPositions,
        displayPresets,
        dynamicGroups,
        new HashSet<>());

    for (int i = 0; i <= limit; i++) {
      String line = lines[i];
      String commandLine = stripInlineComment(line);
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

      Matcher m;

      // Label
      m = LABEL_PATTERN.matcher(line);
      if (m.find()) {
        currentLabel = m.group(1);
        continue;
      }

      // Background command [bg id] or [background id]
      m = BG_CMD_PATTERN.matcher(line);
      if (m.find()) {
        previousBackgroundId = backgroundId;
        backgroundId = m.group(1);
        backgroundLine = i;
        continue;
      }

      String transitionBackground = parseTransitionBackground(commandLine);
      if (transitionBackground != null) {
        previousBackgroundId = backgroundId;
        backgroundId = transitionBackground;
        backgroundLine = i;
        continue;
      }

      // @background declaration — capture id → path mapping
      m = BG_DECL_PATTERN.matcher(line);
      if (m.find()) {
        bgPaths.put(m.group(1), m.group(2).trim());
        continue;
      }

      m = STAGE_PRESET_PATTERN.matcher(line);
      if (m.find()) {
        stagePresetPaths.put(m.group(1), resolveDeclarationPath(sourceName, stripQuotes(m.group(2).trim())));
        continue;
      }

      // @charimg declaration — capture charId+expression → path mapping
      m = CHARIMG_PATTERN.matcher(line);
      if (m.find()) {
        String key = m.group(1) + "/" + m.group(2);
        String pathSpec = m.group(3).trim();
        charImgPaths.put(key, pathSpec);
        List<CharacterLayerEntry> directLayers = directLayerEntries(pathSpec);
        if (!directLayers.isEmpty()) charPresetLayers.put(key, directLayers);
        continue;
      }

      // @group declaration
      m = GROUP_DECL_PATTERN.matcher(trimmed);
      if (m.find()) {
        String targetId = m.group(1).trim();
        String parentId = m.group(2) != null ? m.group(2).trim() : null;
        if (parentId == null || "none".equalsIgnoreCase(parentId) || "root".equalsIgnoreCase(parentId)) {
          dynamicGroups.remove(targetId);
        } else {
          dynamicGroups.put(targetId, parentId);
        }
        continue;
      }

      // @charlayer declaration — capture charId/layerId -> path mapping
      m = CHARLAYER_PATTERN.matcher(line);
      if (m.find()) {
        String charId = m.group(1);
        String layerId = m.group(2);
        String path = m.group(3).trim();
        charLayerPaths.computeIfAbsent(charId, k -> new LinkedHashMap<>()).put(layerId, path);
        continue;
      }

      m = CHARGROUP_PATTERN.matcher(line);
      if (m.find()) {
        String charId = m.group(1);
        String groupId = m.group(2);
        String spec = m.group(3).trim();
        CharacterLayerGroupEntry group = resolveLayerGroupEntry(
            charLayerPaths,
            charLayerGroups,
            charId,
            groupId,
            spec);
        if (group != null && !group.layerIds.isEmpty()) {
          charLayerGroups.put(charId + "/" + groupId, group);
        }
        continue;
      }

      // @charpreset declaration — resolve $layer references into an @charimg-style mapping
      m = CHARPRESET_PATTERN.matcher(line);
      if (m.find()) {
        String charId = m.group(1);
        String expr = m.group(2);
        String spec = m.group(3).trim();
        String resolved = resolvePresetSpec(charLayerPaths, charImgPaths, charLayerGroups, charId, spec);
        List<CharacterLayerEntry> layers = resolvePresetLayerEntries(
            charLayerPaths, charImgPaths, charLayerGroups, charPresetLayers, charId, spec);
        if (!resolved.isBlank()) {
          charImgPaths.put(charId + "/" + expr, resolved);
        }
        if (!layers.isEmpty()) {
          charPresetLayers.put(charId + "/" + expr, layers);
        }
        continue;
      }

      // [show charId pos expression? slot=...? z=...?]
      CharacterEntry showEntry = parseShowCommand(
          commandLine,
          i,
          customPositions,
          charLayerPaths,
          charImgPaths,
          charLayerGroups,
          charPresetLayers);
      if (showEntry != null) {
        putVisible(visible, showEntry);
        continue;
      }

      HideTarget hideTarget = parseHideCommand(commandLine);
      if (hideTarget != null) {
        removeVisible(visible, hideTarget.characterId(), hideTarget.displaySlot());
        continue;
      }

      if (applyDisplayPresetCommand(
          commandLine, i, visible, displayPresets, customPositions,
          charLayerPaths, charImgPaths, charLayerGroups, charPresetLayers)) {
        continue;
      }

      if (applyMoveCommand(
          commandLine,
          i,
          visible,
          customPositions,
          charLayerPaths,
          charImgPaths,
          charLayerGroups,
          charPresetLayers)) {
        continue;
      }

	      if (applyCharacterCommand(
              commandLine,
              i,
              visible,
              customPositions,
              charLayerPaths,
              charImgPaths,
              charLayerGroups,
              charPresetLayers)) {
	        continue;
	      }

      List<String> groupTokens = parseGroupCommand(commandLine);
      if (groupTokens != null) {
        String targetId = groupTokens.get(0);
        String parentId = groupTokens.get(1);
        if (parentId == null || "none".equalsIgnoreCase(parentId) || "root".equalsIgnoreCase(parentId)) {
          dynamicGroups.remove(targetId);
        } else {
          dynamicGroups.put(targetId, parentId);
        }
        continue;
      }

      String stageCommand = parseStageCommand(commandLine);
      if (stageCommand != null) {
        activeStagePresetId = stageCommand.isBlank() ? null : stageCommand;
        activeStageLine = i;
        continue;
      }

      m = JES_TIMELINE_PATTERN.matcher(line);
      if (m.find()) {
        referencedTimelineName = m.group(1);
        referencedTimelineLine = i;
        continue;
      }

      m = EXT_STAGE_PATTERN.matcher(line);
      if (m.find()) {
        String stageId = parseStageTokenList(simpleTokens(m.group(1)));
        if (stageId != null) {
          activeStagePresetId = stageId.isBlank() ? null : stageId;
          activeStageLine = i;
        }
        continue;
      }

      // @external character <id> show <pos> [expr]
      m = EXT_CHAR_SHOW.matcher(line);
      if (m.find()) {
        String charId = m.group(1);
        String pos = m.group(2).toLowerCase(Locale.ROOT);
        String expr = m.group(3) != null ? m.group(3) : "neutral";
        putVisible(visible, new CharacterEntry(charId, pos, expr, i));
        continue;
      }

      // @external character <id> hide
      m = EXT_CHAR_HIDE.matcher(line);
      if (m.find()) {
        removeVisible(visible, m.group(1), null);
        continue;
      }

      // @external character <id> move <pos>
      m = EXT_CHAR_MOVE.matcher(line);
      if (m.find()) {
        String charId = m.group(1);
        String pos = m.group(2).toLowerCase(Locale.ROOT);
        CharacterEntry existing = findVisible(visible, charId, null);
        String expr = existing != null ? existing.expression : "neutral";
        putVisible(visible, existing != null
            ? existing.withPosition(snapshotPosition(pos, customPositions), i)
            : new CharacterEntry(charId, pos, expr, i));
        continue;
      }

      // @external character <id> expr <expression>
      m = EXT_CHAR_EXPR.matcher(line);
      if (m.find()) {
        String charId = m.group(1);
        String expr = m.group(2);
        CharacterEntry existing = findVisible(visible, charId, null);
        String pos = existing != null ? existing.position : "center";
        putVisible(visible, existing != null
            ? existing.withExpression(expr, i)
            : new CharacterEntry(charId, pos, expr, i));
      }
    }

	    List<InlineTimelineContext> inlineTimelineHistory = resolveInlineTimelineContexts(source, limit);
	    InlineTimelineContext inlineTimeline = inlineTimelineHistory.isEmpty()
	        ? null
	        : inlineTimelineHistory.get(inlineTimelineHistory.size() - 1);
	    String inlineTimelineName = inlineTimeline != null ? deriveInlineTimelineName(currentLabel, inlineTimeline.startLine()) : null;

    List<CharacterEntry> scaledCharacters = visible.values().stream()
        .map(entry -> entry.withScale(characterScales.getOrDefault(entry.characterId, 1.0)))
        .toList();

    return new SceneSnapshot(
        currentLabel,
        backgroundId,
        previousBackgroundId,
        backgroundLine,
        scaledCharacters,
        limit,
        bgPaths,
        stagePresetPaths,
        charImgPaths,
        charPresetLayers,
        charLayerGroups,
        activeStagePresetId,
        activeStageLine,
        referencedTimelineName,
        referencedTimelineLine,
        inlineTimeline != null ? inlineTimeline.body() : null,
        inlineTimeline != null ? inlineTimeline.startLine() : -1,
        inlineTimelineName,
        inlineTimelineHistory,
        dynamicGroups);
  }

  private SceneSnapshot buildSnapshot(int lineIdx) {
    return resolveSnapshot(
        currentSource,
        lineIdx,
        activeScriptFile == null ? null : activeScriptFile.getAbsolutePath(),
        this::resolveIncludeSource);
  }

  static int resolveActiveLabelStartLine(String source, int upToLine) {
    if (source == null || source.isBlank()) return 0;
    String[] lines = source.split("\n", -1);
    int limit = Math.max(0, Math.min(upToLine, lines.length - 1));
    int latestLabelLine = 0;
    for (int i = 0; i <= limit; i++) {
      Matcher m = LABEL_PATTERN.matcher(lines[i]);
      if (m.find()) latestLabelLine = i;
    }
    return latestLabelLine;
  }

  static int resolveSceneStartLine(String source, int upToLine) {
    if (source == null || source.isBlank()) return 0;
    String[] lines = source.split("\n", -1);
    int limit = Math.max(0, Math.min(upToLine, lines.length - 1));
    int labelStart = resolveActiveLabelStartLine(source, limit);
    int sceneStart = labelStart;
    for (int i = labelStart; i <= limit; i++) {
      String trimmed = stripInlineComment(lines[i]).trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
      if (BG_CMD_PATTERN.matcher(trimmed).find()) {
        sceneStart = i;
      }
    }
    return sceneStart;
  }

  private OpenTarget resolvePrimaryIssueOpenTarget(SceneSnapshot snapshot) {
    if (snapshot == null || activeScriptFile == null) return null;
    if (snapshot.backgroundId != null && !snapshot.hasBackgroundPathMapping() && snapshot.backgroundLine >= 0) {
      return new OpenTarget(activeScriptFile, snapshot.backgroundLine + 1);
    }
    for (CharacterEntry ch : snapshot.characters) {
      if (ch == null) continue;
      if (!snapshot.hasCharacterPathMapping(ch.characterId, ch.expression) && ch.atLine >= 0) {
        return new OpenTarget(activeScriptFile, ch.atLine + 1);
      }
      if (!ch.customPosition
          && (ch.position == null || !KNOWN_POSITIONS.contains(ch.position.toLowerCase(Locale.ROOT)))) {
        return new OpenTarget(activeScriptFile, ch.atLine + 1);
      }
    }
    if (snapshot.referencedTimelineLine >= 0 && snapshot.resolveTimelineFile(projectRoot) == null) {
      return new OpenTarget(activeScriptFile, snapshot.referencedTimelineLine + 1);
    }
    return null;
  }

  private OpenTarget resolveTimelineOpenTarget(SceneSnapshot snapshot) {
    if (snapshot == null) return null;
    if (snapshot.hasInlineTimeline() && activeScriptFile != null && snapshot.inlineTimelineStartLine >= 0) {
      return new OpenTarget(activeScriptFile, snapshot.inlineTimelineStartLine + 1);
    }
    File timelineFile = snapshot.resolveTimelineFile(projectRoot);
    if (timelineFile != null) {
      return new OpenTarget(timelineFile, 1);
    }
    if (snapshot.referencedTimelineLine >= 0 && activeScriptFile != null) {
      return new OpenTarget(activeScriptFile, snapshot.referencedTimelineLine + 1);
    }
    return null;
  }

  private static String describeTimelineContext(SceneSnapshot snapshot) {
    if (snapshot == null) return "—";
    if (snapshot.hasInlineTimeline()) {
      return snapshot.inlineTimelineName + " (inline block)";
    }
    if (snapshot.referencedTimelineName != null && !snapshot.referencedTimelineName.isBlank()) {
      return snapshot.referencedTimelineName;
    }
    return "—";
  }

  public static String snapshotCharacterGroupName(CharacterEntry character) {
    if (character == null) return "character_preset";
    String characterId = selectorSafeName(character.characterId);
    String expression = selectorSafeName(character.expression == null || character.expression.isBlank()
        ? "neutral"
        : character.expression);
    if (characterId.isBlank()) characterId = "character";
    if (expression.isBlank()) expression = "neutral";
    return characterId + "_" + expression;
  }

  public static String snapshotLayerEntityName(String characterId, String expression, String layerId) {
    String safeCharacter = selectorSafeName(characterId);
    String safeExpression = selectorSafeName(expression == null || expression.isBlank() ? "neutral" : expression);
    String safeLayer = selectorSafeName(layerId);
    if (safeCharacter.isBlank() || safeExpression.isBlank() || safeLayer.isBlank()) return "";
    return safeCharacter + "_" + safeExpression + "_" + safeLayer;
  }

  public static String snapshotStableLayerEntityName(String characterId, String layerId) {
    String safeCharacter = selectorSafeName(characterId);
    String safeLayer = selectorSafeName(layerId);
    if (safeCharacter.isBlank() || safeLayer.isBlank()) return "";
    return safeCharacter + "_" + safeLayer;
  }

  public static String snapshotLayerGroupEntityName(String characterId, String expression, String groupId) {
    List<String> names = equivalentSnapshotLayerGroupEntityNames(characterId, expression, groupId);
    return names.isEmpty() ? "" : names.get(0);
  }

  public static List<String> equivalentSnapshotLayerGroupEntityNames(String characterId, String expression, String groupId) {
    String safeCharacter = selectorSafeName(characterId);
    String safeExpression = selectorSafeName(expression == null || expression.isBlank() ? "neutral" : expression);
    String safeGroup = selectorSafeName(groupId);
    if (safeCharacter.isBlank() || safeExpression.isBlank() || safeGroup.isBlank()) return List.of();
    LinkedHashSet<String> names = new LinkedHashSet<>();
    names.add(safeCharacter + "_" + safeGroup);
    names.add(safeCharacter + "_" + safeExpression + "_" + safeGroup);
    return List.copyOf(names);
  }

	  public static List<CharacterLayerGroupEntry> snapshotLayerGroupChain(
	      String layerId,
	      List<CharacterLayerGroupEntry> groups
	  ) {
	    if (layerId == null || layerId.isBlank() || groups == null || groups.isEmpty()) return List.of();
	    CharacterLayerGroupEntry deepest = null;
	    int deepestDepth = -1;
	    for (CharacterLayerGroupEntry candidate : groups) {
	      if (candidate == null || !candidate.layerIds.contains(layerId)) continue;
	      int depth = snapshotLayerGroupDepth(candidate, groups, new LinkedHashSet<>());
	      if (depth > deepestDepth) {
	        deepest = candidate;
	        deepestDepth = depth;
	      }
	    }
	    if (deepest == null) return List.of();

	    LinkedHashSet<String> seen = new LinkedHashSet<>();
	    List<CharacterLayerGroupEntry> chain = new ArrayList<>();
	    CharacterLayerGroupEntry current = deepest;
	    while (current != null && current.groupId != null && seen.add(current.groupId)) {
	      chain.add(0, current);
	      current = snapshotLayerGroupById(groups, current.parentGroupId);
	    }
	    return List.copyOf(chain);
	  }

	  private static int snapshotLayerGroupDepth(
	      CharacterLayerGroupEntry group,
	      List<CharacterLayerGroupEntry> groups,
	      Set<String> seen
	  ) {
	    if (group == null || group.groupId == null || !seen.add(group.groupId)) return 0;
	    CharacterLayerGroupEntry parent =
	        snapshotLayerGroupById(groups, group.parentGroupId);
	    return parent == null ? 0 : 1 + snapshotLayerGroupDepth(parent, groups, seen);
	  }

	  private static CharacterLayerGroupEntry snapshotLayerGroupById(
	      List<CharacterLayerGroupEntry> groups,
	      String groupId
	  ) {
	    if (groupId == null || groupId.isBlank() || groups == null) return null;
	    for (CharacterLayerGroupEntry group : groups) {
	      if (group != null && groupId.equals(group.groupId)) return group;
	    }
	    return null;
	  }


  /** Names of every rendered occurrence, including repeated layers in a composite. */
  public static List<String> snapshotLayerOccurrenceNames(SceneSnapshot snapshot, CharacterEntry character, String layerId) {
    List<String> names = new ArrayList<>();
    List<CharacterLayerEntry> layers = snapshot.resolveCharacterLayers(character.characterId, character.expression);
    String base = snapshotStableLayerEntityName(character.characterId, layerId);
    for (int i = 0; i < layers.size(); i++) {
      if (layerId.equals(layers.get(i).layerId)) names.add(names.isEmpty() ? base : base + "_" + (i + 2));
    }
    return List.copyOf(names);
  }

  public static List<String> equivalentSnapshotLayerEntityNames(
      SceneSnapshot snapshot,
      CharacterEntry character,
      String layerId
  ) {
    if (character == null || character.characterId == null || character.characterId.isBlank()
        || layerId == null || layerId.isBlank()) {
      return List.of();
    }
    Set<String> names = new java.util.LinkedHashSet<>();
    String safeLayer = selectorSafeName(layerId);
    // Put the expression-independent runtime target first so Puppeteer creates
    // and exports stable tracks instead of baking the current [show] composition
    // into every entity name. Expression-qualified names remain lookup aliases.
    String stableName = snapshotStableLayerEntityName(character.characterId, layerId);
    if (!stableName.isBlank()) names.add(stableName);
    String currentName = snapshotLayerEntityName(character.characterId, character.expression, layerId);
    if (!currentName.isBlank()) names.add(currentName);
    if (snapshot != null && snapshot.characterPresetLayers != null && !snapshot.characterPresetLayers.isEmpty()) {
      String prefix = character.characterId + "/";
      for (Map.Entry<String, List<CharacterLayerEntry>> entry : snapshot.characterPresetLayers.entrySet()) {
        String key = entry.getKey();
        if (key == null || !key.startsWith(prefix) || key.length() <= prefix.length()) continue;
        String expression = key.substring(prefix.length());
        List<CharacterLayerEntry> layers = entry.getValue();
        if (!presetContainsLayer(layers, layerId, safeLayer)) continue;
        String alias = snapshotLayerEntityName(character.characterId, expression, layerId);
        if (!alias.isBlank()) names.add(alias);
      }
    }
    return List.copyOf(names);
  }

  public static List<String> equivalentSnapshotLayerGroupEntityNames(
      SceneSnapshot snapshot,
      CharacterEntry character,
      String groupId
  ) {
    if (character == null || groupId == null || groupId.isBlank()) return List.of();
    return equivalentSnapshotLayerGroupEntityNames(character.characterId, character.expression, groupId);
  }

  public static boolean snapshotCharacterHasLayerGroup(SceneSnapshot snapshot, CharacterEntry character, String groupId) {
    if (snapshot == null || character == null || groupId == null || groupId.isBlank()) return false;
    return snapshot.resolveCharacterLayerGroup(character.characterId, groupId) != null;
  }

  public static boolean snapshotCharacterHasLayer(SceneSnapshot snapshot, CharacterEntry character, String layerId) {
    if (snapshot == null || character == null || layerId == null || layerId.isBlank()) return false;
    List<CharacterLayerEntry> layers = snapshot.resolveCharacterLayers(character.characterId, character.expression);
    return presetContainsLayer(layers, layerId, selectorSafeName(layerId));
  }

  public static String selectorSafeName(String raw) {
    String value = raw == null ? "" : raw.trim();
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-') {
        out.append(ch);
      } else {
        out.append('_');
      }
    }
    String cleaned = out.toString().replaceAll("_+", "_");
    while (cleaned.startsWith("_")) cleaned = cleaned.substring(1);
    while (cleaned.endsWith("_")) cleaned = cleaned.substring(0, cleaned.length() - 1);
    return cleaned;
  }

  private static boolean presetContainsLayer(List<CharacterLayerEntry> layers, String layerId, String safeLayerId) {
    if (layers == null || layers.isEmpty()) return false;
    for (CharacterLayerEntry layer : layers) {
      if (layer == null || layer.layerId == null || layer.layerId.isBlank()) continue;
      if (layer.layerId.equals(layerId)) return true;
      if (!safeLayerId.isBlank() && selectorSafeName(layer.layerId).equals(safeLayerId)) return true;
    }
    return false;
  }

  private static List<String> buildDiagnostics(SceneSnapshot snapshot, File projectRoot) {
    List<String> out = new ArrayList<>();
    if (snapshot == null) return out;

    if (snapshot.backgroundId != null && !snapshot.hasBackgroundPathMapping()) {
      out.add("Background '" + snapshot.backgroundId + "' has no @background path mapping.");
    }

    if (snapshot.activeStagePresetId != null && !snapshot.activeStagePresetId.isBlank()) {
      if (!snapshot.hasStagePresetPathMapping()) {
        out.add("Stage preset '" + snapshot.activeStagePresetId + "' has no @stagepreset path mapping.");
      } else {
        String stagePath = snapshot.resolveStagePresetPath(projectRoot);
        if (stagePath != null && !stagePath.isBlank()) {
          File file = new File(stagePath);
          if (!file.isFile()) {
            out.add("Stage preset '" + snapshot.activeStagePresetId + "' points to a missing file: " + stagePath);
          }
        }
      }
    }

    for (CharacterEntry ch : snapshot.characters) {
      if (ch == null || ch.characterId == null || ch.characterId.isBlank()) continue;
      if (!snapshot.hasCharacterPathMapping(ch.characterId, ch.expression)) {
        String expr = (ch.expression == null || ch.expression.isBlank()) ? "neutral" : ch.expression;
        out.add("Character '" + ch.characterId + "' expression '" + expr + "' has no @charimg/@charpreset mapping.");
      }
      if (!ch.customPosition
          && (ch.position == null || !KNOWN_POSITIONS.contains(ch.position.toLowerCase(Locale.ROOT)))) {
        out.add("Character '" + ch.characterId + "' uses position '" + ch.position + "' (Puppeteer launch falls back to center).");
      }
    }
    if (snapshot.referencedTimelineName != null && !snapshot.referencedTimelineName.isBlank()
        && !snapshot.hasInlineTimeline() && snapshot.resolveTimelineFile(projectRoot) == null) {
      out.add("Timeline '" + snapshot.referencedTimelineName + "' is referenced here, but scripts/timelines/" + snapshot.referencedTimelineName + ".jes was not found.");
    }
    return out;
  }

  private static CharacterEntry parseShowCommand(
      String line,
      int atLine,
      Map<String, SnapshotPosition> customPositions,
      Map<String, Map<String, String>> layerPaths,
      Map<String, String> imagePaths,
      Map<String, CharacterLayerGroupEntry> layerGroups,
      Map<String, List<CharacterLayerEntry>> presetLayers) {
    List<String> tokens = bracketTokens(line);
    if (tokens.size() < 2 || !"show".equalsIgnoreCase(tokens.get(0))) return null;
    String characterId = tokens.get(1);
    if (characterId == null || characterId.isBlank()) return null;
    Placement placement = parsePlacement(tokens, 2, null, null, null, customPositions);
    String expression = resolveSnapshotExpression(
        characterId, placement.expression(), layerPaths, imagePaths, layerGroups, presetLayers);
    return CharacterEntry.fromPlacement(characterId, expression, placement, atLine);
  }

  private static HideTarget parseHideCommand(String line) {
    List<String> tokens = bracketTokens(line);
    if (tokens.size() < 2 || !"hide".equalsIgnoreCase(tokens.get(0))) return null;
    String characterId = null;
    String displaySlot = null;
    for (int i = 1; i < tokens.size(); i++) {
      String token = tokens.get(i);
      if (token.startsWith("@") && token.length() > 1) displaySlot = token.substring(1);
      else if (isOption(token, "slot", "as", "instance", "display", "display_slot", "display-slot")) displaySlot = optionValue(token);
      else if (characterId == null) characterId = token;
    }
    return characterId == null && displaySlot == null ? null : new HideTarget(characterId, displaySlot);
  }

  private static boolean applyMoveCommand(
      String line,
      int atLine,
      Map<String, CharacterEntry> visible,
      Map<String, SnapshotPosition> customPositions,
      Map<String, Map<String, String>> layerPaths,
      Map<String, String> imagePaths,
      Map<String, CharacterLayerGroupEntry> layerGroups,
      Map<String, List<CharacterLayerEntry>> presetLayers) {
    List<String> tokens = bracketTokens(line);
    if (tokens.size() < 2 || !"move".equalsIgnoreCase(tokens.get(0))) return false;
    String characterId = null;
    String displaySlot = null;
    String target = tokens.get(1);
    if (target.startsWith("@")) displaySlot = target.substring(1);
    else if (isOption(target, "slot", "as", "instance", "display", "display_slot", "display-slot")) displaySlot = optionValue(target);
    else characterId = target;
    CharacterEntry existing = findVisible(visible, characterId, displaySlot);
    if (existing == null) return true;
    Placement placement = parsePlacement(
        tokens, 2, existing.snapshotPosition(), existing.expression, existing.displaySlot, customPositions);
    String expression = resolveSnapshotExpression(
        existing.characterId, placement.expression(), layerPaths, imagePaths, layerGroups, presetLayers);
    putVisible(visible, CharacterEntry.fromPlacement(existing.characterId, expression, placement, atLine)
        .withLayerOrder(existing.layerOrder));
    return true;
  }

  private static boolean applyDisplayPresetCommand(
      String line,
      int atLine,
      Map<String, CharacterEntry> visible,
      Map<String, List<DisplayPresetEntry>> displayPresets,
      Map<String, SnapshotPosition> customPositions,
      Map<String, Map<String, String>> layerPaths,
      Map<String, String> imagePaths,
      Map<String, CharacterLayerGroupEntry> layerGroups,
      Map<String, List<CharacterLayerEntry>> presetLayers) {
    List<String> tokens = bracketTokens(line);
    if (tokens.size() < 2) return false;
    String command = tokens.get(0).toLowerCase(Locale.ROOT);
    if (!Set.of("showpreset", "movepreset", "hidepreset").contains(command)) return false;
    List<DisplayPresetEntry> entries = displayPresets.getOrDefault(tokens.get(1), List.of());
    if ("hidepreset".equals(command)) {
      for (DisplayPresetEntry entry : entries) removeVisible(visible, null, entry.displaySlot());
      return true;
    }
    SnapshotPosition override = parsePositionOnly(tokens, 2, customPositions);
    for (DisplayPresetEntry entry : entries) {
      SnapshotPosition position = override != null ? override : entry.position();
      if ("showpreset".equals(command)) {
        String expression = resolveSnapshotExpression(
            entry.characterId(), entry.expression(), layerPaths, imagePaths, layerGroups, presetLayers);
        putVisible(visible, new CharacterEntry(
            entry.characterId(), position.name, expression, atLine, 1.0,
            entry.displaySlot(), position.x, position.y, position.custom, entry.layerOrder()));
      } else {
        CharacterEntry existing = findVisible(visible, null, entry.displaySlot());
        if (existing != null) putVisible(visible, existing.withPosition(position, atLine));
      }
    }
    return true;
  }

  private static boolean applyCharacterCommand(
      String line,
      int atLine,
      Map<String, CharacterEntry> visible,
      Map<String, SnapshotPosition> customPositions,
      Map<String, Map<String, String>> layerPaths,
      Map<String, String> imagePaths,
      Map<String, CharacterLayerGroupEntry> layerGroups,
      Map<String, List<CharacterLayerEntry>> presetLayers) {
    List<String> tokens = bracketTokens(line);
    if (tokens.size() < 2 || !("character".equalsIgnoreCase(tokens.get(0)) || "char".equalsIgnoreCase(tokens.get(0)))) return false;
    String characterId = tokens.get(1);
    if (characterId == null || characterId.isBlank()) return true;
    String command = tokens.size() >= 3 ? tokens.get(2).toLowerCase(Locale.ROOT) : "expression";
    String displaySlot = findOptionValue(tokens, "slot", "as", "instance", "display", "display_slot", "display-slot");
    CharacterEntry existing = findVisible(visible, characterId, displaySlot);
    if ("hide".equals(command)) {
      removeVisible(visible, characterId, displaySlot);
      return true;
    }
    if ("show".equals(command)) {
      Placement placement = parsePlacement(tokens, 3, null, null, displaySlot, customPositions);
      String expression = resolveSnapshotExpression(characterId, placement.expression(), layerPaths, imagePaths, layerGroups, presetLayers);
      putVisible(visible, CharacterEntry.fromPlacement(characterId, expression, placement, atLine));
      return true;
    }
    if (existing == null) return true;
    if ("expression".equals(command) || "expr".equals(command)) {
      String raw = tokens.size() >= 4 ? tokens.get(3) : existing.expression;
      String expression = resolveSnapshotExpression(characterId, raw, layerPaths, imagePaths, layerGroups, presetLayers);
      putVisible(visible, existing.withExpression(expression, atLine));
      return true;
    }
    if (Set.of("move", "position", "pos", "at").contains(command)) {
      int start = "at".equals(command) ? 2 : 3;
      Placement placement = parsePlacement(tokens, start, existing.snapshotPosition(), existing.expression, existing.displaySlot, customPositions);
      String expression = resolveSnapshotExpression(characterId, placement.expression(), layerPaths, imagePaths, layerGroups, presetLayers);
      putVisible(visible, CharacterEntry.fromPlacement(characterId, expression, placement, atLine).withLayerOrder(existing.layerOrder));
    }
    return true;
  }

	  private static List<String> parseGroupCommand(String line) {
	    List<String> tokens = bracketTokens(line);
	    if (tokens.size() < 2 || !"group".equalsIgnoreCase(tokens.get(0))) return null;
	    String targetId = tokens.get(1);
	    if (targetId == null || targetId.isBlank()) return null;
	    String parentId = tokens.size() >= 3 ? tokens.get(2) : null;
	    return Arrays.asList(targetId, parentId);
	  }


  private static Placement parsePlacement(
      List<String> tokens,
      int startIndex,
      SnapshotPosition fallbackPosition,
      String fallbackExpression,
      String fallbackSlot,
      Map<String, SnapshotPosition> customPositions) {
    SnapshotPosition position = fallbackPosition;
    String expression = fallbackExpression;
    boolean expressionExplicit = false;
    String displaySlot = fallbackSlot;
    Integer layerOrder = null;
    for (int i = startIndex; i < tokens.size(); i++) {
      String token = tokens.get(i);
      if (token == null || token.isBlank()) continue;
      String lower = token.toLowerCase(Locale.ROOT);
      if ("at".equals(lower) && i + 1 < tokens.size()) {
        String coordinateToken = tokens.get(++i);
        SnapshotPosition parsed = parseInlinePosition(coordinateToken);
        if (parsed != null) position = parsed;
        Integer inlineLayer = parseInlineLayerOrder(coordinateToken);
        if (inlineLayer != null) layerOrder = inlineLayer;
        continue;
      }
      if (isOption(token, "pos", "position", "at", "coord", "coords", "xy")) {
        SnapshotPosition parsed = snapshotPosition(optionValue(token), customPositions);
        if (parsed == null) parsed = parseInlinePosition(optionValue(token));
        if (parsed != null) position = parsed;
        continue;
      }
      if (isOption(token, "expr", "expression", "preset")) {
        expression = optionValue(token);
        expressionExplicit = true;
        continue;
      }
      if (isOption(token, "slot", "as", "instance", "display", "display_slot", "display-slot")) {
        displaySlot = optionValue(token);
        continue;
      }
      if (isOption(token, "layer", "z", "zorder")) {
        layerOrder = parseInteger(optionValue(token));
        continue;
      }
      SnapshotPosition parsed = snapshotPosition(token, customPositions);
      if (parsed != null) {
        position = parsed;
      } else if (layerOrder == null && token.matches("[-+]?\\d+")) {
        layerOrder = parseInteger(token);
      } else if (!expressionExplicit && !lower.startsWith("ease_") && !lower.startsWith("easing=")) {
        expression = stripQuotes(token);
        expressionExplicit = true;
      }
    }
    if (position == null) position = SnapshotPosition.predefined("center");
    if (expression == null || expression.isBlank()) expression = "neutral";
    return new Placement(position, expression, displaySlot, layerOrder);
  }

  private static SnapshotPosition parsePositionOnly(
      List<String> tokens, int startIndex, Map<String, SnapshotPosition> customPositions) {
    for (int i = startIndex; i < tokens.size(); i++) {
      String token = tokens.get(i);
      if ("at".equalsIgnoreCase(token) && i + 1 < tokens.size()) return parseInlinePosition(tokens.get(i + 1));
      if (isOption(token, "pos", "position")) return snapshotPosition(optionValue(token), customPositions);
      if (isOption(token, "at", "coord", "coords", "xy")) return parseInlinePosition(optionValue(token));
      SnapshotPosition parsed = snapshotPosition(token, customPositions);
      if (parsed != null) return parsed;
    }
    return null;
  }

  private static SnapshotPosition parseDeclaredPosition(String name, String rawCoordinates) {
    if (name == null || name.isBlank() || rawCoordinates == null) return null;
    String[] parts = rawCoordinates.trim().split("\\s+");
    try {
      double x = Double.parseDouble(parts[0]);
      double y = parts.length > 1 ? Double.parseDouble(parts[1]) : -1.0;
      return new SnapshotPosition(name.trim().toLowerCase(Locale.ROOT), x, y, true);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private static SnapshotPosition parseInlinePosition(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String[] parts = stripQuotes(raw).split(",");
    if (parts.length < 1 || parts.length > 3) return null;
    try {
      double x = Double.parseDouble(parts[0].trim());
      double y = parts.length > 1 ? Double.parseDouble(parts[1].trim()) : -1.0;
      return new SnapshotPosition("at_" + parts[0].trim() + "_" + (parts.length > 1 ? parts[1].trim() : "baseline"), x, y, true);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static Integer parseInlineLayerOrder(String raw) {
    if (raw == null) return null;
    String[] parts = stripQuotes(raw).split(",");
    return parts.length == 3 ? parseInteger(parts[2].trim()) : null;
  }

  private static SnapshotPosition snapshotPosition(String raw, Map<String, SnapshotPosition> customPositions) {
    if (raw == null || raw.isBlank()) return null;
    String normalized = stripQuotes(raw).toLowerCase(Locale.ROOT);
    SnapshotPosition predefined = SnapshotPosition.predefined(normalized);
    if (predefined != null) return predefined;
    return customPositions == null ? null : customPositions.get(normalized);
  }

  private static DisplayPresetEntry parseDisplayPresetEntry(
      String rawLine, Map<String, SnapshotPosition> customPositions) {
    if (rawLine == null) return null;
    Matcher matcher = DISPLAY_PRESET_ENTRY_PATTERN.matcher(rawLine.trim());
    if (!matcher.matches()) return null;
    String slot = matcher.group(1).trim();
    List<String> body = VnArgTokenizer.tokenize(matcher.group(2));
    if (body.isEmpty()) return null;
    String characterId = null;
    int start = 0;
    if (isOption(body.get(0), "char", "character", "id", "sprite")) {
      characterId = optionValue(body.get(0));
      start = 1;
    } else {
      characterId = body.get(0);
      start = 1;
    }
    Placement placement = parsePlacement(body, start, null, null, slot, customPositions);
    return new DisplayPresetEntry(
        slot, characterId, placement.position(), placement.expression(), placement.layerOrder());
  }

  private static String resolveSnapshotExpression(
      String characterId,
      String rawExpression,
      Map<String, Map<String, String>> layerPaths,
      Map<String, String> imagePaths,
      Map<String, CharacterLayerGroupEntry> layerGroups,
      Map<String, List<CharacterLayerEntry>> presetLayers) {
    String token = rawExpression == null || rawExpression.isBlank() ? "neutral" : rawExpression.trim();
    if (token.startsWith("@") && token.indexOf('+') < 0 && token.indexOf('$') < 0) return token.substring(1);
    if (token.indexOf('$') < 0 && token.indexOf('+') < 0) return token;
    List<CharacterLayerEntry> layers = new ArrayList<>();
    for (String rawPart : token.split("\\+")) {
      String part = rawPart.trim();
      if (part.startsWith("@")) {
        layers.addAll(presetLayers.getOrDefault(characterId + "/" + part.substring(1), List.of()));
      } else if (part.startsWith("$")) {
        layers.addAll(resolveLayerOrGroupEntries(layerPaths, layerGroups, characterId, part.substring(1)));
      }
    }
    if (layers.isEmpty()) return token;
    String resolvedSpec = layers.stream().map(layer -> layer.path).filter(path -> path != null && !path.isBlank())
        .collect(java.util.stream.Collectors.joining(" | "));
    String base = token.replace('@', ' ').replace('$', ' ').replace('+', ' ').replace(':', ' ').replace('.', ' ')
        .trim().replaceAll("[^A-Za-z0-9_]+", "_").replaceAll("_+", "_");
    if (base.isEmpty()) base = "composite";
    if (!Character.isLetter(base.charAt(0)) && base.charAt(0) != '_') base = "_" + base;
    String expression = "__inline_" + base.toLowerCase(Locale.ROOT) + "_" + Integer.toUnsignedString(resolvedSpec.hashCode(), 36);
    imagePaths.put(characterId + "/" + expression, resolvedSpec);
    presetLayers.put(characterId + "/" + expression, List.copyOf(layers));
    return expression;
  }

  private static void putVisible(Map<String, CharacterEntry> visible, CharacterEntry entry) {
    if (entry == null) return;
    String key = entry.displaySlot == null || entry.displaySlot.isBlank()
        ? "character:" + entry.characterId
        : "slot:" + entry.displaySlot;
    CharacterEntry previous = visible.get(key);
    if (entry.layerOrder == null) entry = entry.withLayerOrder(previous != null && previous.layerOrder != null
        ? previous.layerOrder : snapshotPosition(entry).getDefaultLayerOrder());
    visible.put(key, entry);
  }

  public static com.jvn.core.vn.CharacterPosition snapshotPosition(CharacterEntry character) {
    var predefined = com.jvn.core.vn.CharacterPosition.predefined(character.position);
    return predefined != null ? predefined : com.jvn.core.vn.CharacterPosition.named(
        character.position, character.positionX, character.positionY);
  }

  private static CharacterEntry findVisible(
      Map<String, CharacterEntry> visible, String characterId, String displaySlot) {
    if (displaySlot != null && !displaySlot.isBlank()) return visible.get("slot:" + displaySlot);
    if (characterId == null || characterId.isBlank()) return null;
    CharacterEntry direct = visible.get("character:" + characterId);
    if (direct != null) return direct;
    for (CharacterEntry entry : visible.values()) if (characterId.equals(entry.characterId)) return entry;
    return null;
  }

  private static void removeVisible(
      Map<String, CharacterEntry> visible, String characterId, String displaySlot) {
    if (displaySlot != null && !displaySlot.isBlank()) {
      visible.remove("slot:" + displaySlot);
      return;
    }
    if (characterId == null || characterId.isBlank()) return;
    visible.entrySet().removeIf(entry -> characterId.equals(entry.getValue().characterId));
  }

  private static boolean isOption(String token, String... keys) {
    if (token == null) return false;
    int eq = token.indexOf('=');
    int colon = token.indexOf(':');
    int split = eq > 0 && colon > 0 ? Math.min(eq, colon) : Math.max(eq, colon);
    if (split <= 0) return false;
    String key = token.substring(0, split).trim().toLowerCase(Locale.ROOT);
    return Arrays.stream(keys).anyMatch(key::equals);
  }

  private static String findOptionValue(List<String> tokens, String... keys) {
    for (String token : tokens) if (isOption(token, keys)) return optionValue(token);
    return null;
  }

  private static Integer parseInteger(String raw) {
    try {
      return Integer.valueOf(raw);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private static String parseStageCommand(String line) {
    List<String> tokens = bracketTokens(line);
    if (tokens.isEmpty() || !"stage".equalsIgnoreCase(tokens.get(0))) return null;
    return parseStageTokenList(tokens.subList(1, tokens.size()));
  }

  private static String parseTransitionBackground(String line) {
    List<String> tokens = bracketTokens(line);
    if (tokens.size() < 2 || !"transition".equalsIgnoreCase(tokens.get(0))) return null;
    String background = null;
    boolean typeSeen = false;
    for (int i = 1; i < tokens.size(); i++) {
      String token = tokens.get(i);
      if (isOption(token, "bg", "background")) {
        background = optionValue(token);
      } else if (isOption(token, "type", "kind", "style")) {
        typeSeen = true;
      } else if (isOption(token, "dur", "duration", "ms", "mask", "maskPath", "maskAsset")) {
        // Not a background token.
      } else if (!typeSeen) {
        typeSeen = true;
      } else if (!token.matches("[-+]?\\d+") && background == null) {
        background = token;
      }
    }
    return background == null || background.isBlank() ? null : background;
  }

  private static String parseStageTokenList(List<String> tokens) {
    if (tokens == null || tokens.isEmpty()) return null;
    String firstBare = null;
    for (String rawToken : tokens) {
      String token = rawToken == null ? "" : rawToken.trim();
      if (token.isEmpty()) continue;
      String lower = token.toLowerCase(Locale.ROOT);
      if ("clear".equals(lower) || "off".equals(lower) || "none".equals(lower)) return "";
      int equals = token.indexOf('=');
      if (equals > 0) {
        String key = token.substring(0, equals).trim().toLowerCase(Locale.ROOT);
        String value = stripQuotes(token.substring(equals + 1).trim());
        if (Set.of("preset", "id", "name", "mode").contains(key)) {
          String normalized = value.toLowerCase(Locale.ROOT);
          return "clear".equals(normalized) || "off".equals(normalized) || "none".equals(normalized)
              ? ""
              : value;
        }
      } else if (firstBare == null) {
        firstBare = stripQuotes(token);
      }
    }
    return firstBare;
  }

  private static List<String> simpleTokens(String raw) {
    if (raw == null || raw.isBlank()) return List.of();
    return Arrays.stream(raw.trim().split("\\s+"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  private static List<String> bracketTokens(String line) {
    String raw = stripInlineComment(line).trim();
    if (raw.length() < 2 || raw.charAt(0) != '[' || raw.charAt(raw.length() - 1) != ']') {
      return List.of();
    }
    String inner = raw.substring(1, raw.length() - 1).trim();
    if (inner.isEmpty()) return List.of();
    return VnArgTokenizer.tokenize(inner);
  }

  private static String stripInlineComment(String line) {
    if (line == null || line.isBlank()) return "";
    int comment = line.indexOf('#');
    return comment >= 0 ? line.substring(0, comment) : line;
  }

  private static boolean isKnownPosition(String value) {
    return value != null && KNOWN_POSITIONS.contains(value.toLowerCase(Locale.ROOT));
  }

  private static String resolveDeclarationPath(String sourceName, String declaredPath) {
    String path = declaredPath == null ? "" : declaredPath.trim().replace('\\', '/');
    if (path.isBlank() || path.startsWith("/") || path.contains(":")) return path;
    String src = normalizeSourceName(sourceName);
    int idx = src.lastIndexOf('/');
    if (idx < 0 || "<script>".equals(src)) return path;
    return src.substring(0, idx + 1) + path;
  }

	  private static InlineTimelineContext resolveInlineTimelineContext(String source, int cursorLine) {
	    List<InlineTimelineContext> contexts = resolveInlineTimelineContexts(source, cursorLine);
	    return contexts.isEmpty() ? null : contexts.get(contexts.size() - 1);
	  }

	  private static List<InlineTimelineContext> resolveInlineTimelineContexts(String source, int cursorLine) {
	    if (source == null || source.isBlank()) return List.of();
	    String[] lines = source.split("\n", -1);
	    int limit = Math.max(0, Math.min(cursorLine, lines.length - 1));
	    List<InlineTimelineContext> contexts = new ArrayList<>();
	    for (int i = 0; i <= limit; i++) {
	      String trimmed = stripInlineComment(lines[i]).trim();
	      if (!startsInlineTimeline(trimmed)) continue;
      int openingLine = i;
      int braceDepth = countChar(trimmed, '{');
      int j = i;
      if (braceDepth == 0) {
        boolean foundOpeningBrace = false;
        while (++j < lines.length) {
          String nextTrimmed = stripInlineComment(lines[j]).trim();
          if (nextTrimmed.isEmpty() || nextTrimmed.startsWith("#")) continue;
          if ("{".equals(nextTrimmed)) {
            braceDepth = 1;
            foundOpeningBrace = true;
            break;
          }
          break;
        }
        if (!foundOpeningBrace) {
          continue;
        }
      }
      StringBuilder block = new StringBuilder();
      int endLine = j;
      while (++endLine < lines.length && braceDepth > 0) {
        String line = lines[endLine];
        for (char c : line.toCharArray()) {
          if (c == '{') braceDepth++;
          else if (c == '}') braceDepth--;
        }
        if (braceDepth > 0) {
          block.append(line).append('\n');
        } else {
          int lastBrace = line.lastIndexOf('}');
          if (lastBrace > 0) block.append(line, 0, lastBrace).append('\n');
        }
	      }
	      if (braceDepth == 0) {
	        InlineTimelineContext context = new InlineTimelineContext(openingLine, endLine, block.toString());
	        if (endLine <= limit || (limit >= openingLine && limit <= endLine)) {
	          contexts.add(context);
	        }
	      }
	      i = Math.max(i, endLine);
	    }
	    return contexts;
	  }

  private static boolean startsInlineTimeline(String trimmed) {
    return trimmed.startsWith("timeline") && (trimmed.endsWith("{") || trimmed.equals("timeline"));
  }

  private static int countChar(String value, char ch) {
    int count = 0;
    for (int i = 0; i < value.length(); i++) {
      if (value.charAt(i) == ch) count++;
    }
    return count;
  }

  private static String deriveInlineTimelineName(String currentLabel, int startLine) {
    String base = currentLabel != null && !currentLabel.isBlank() ? currentLabel : "inline_timeline";
    String normalized = base.replaceAll("[^A-Za-z0-9_]+", "_");
    normalized = normalized.replaceAll("_+", "_");
    normalized = normalized.replaceAll("^_+|_+$", "");
    if (normalized.isBlank()) normalized = "inline_timeline";
    return normalized + "_inline_" + Math.max(1, startLine + 1);
  }

  private static void collectDeclarations(
      String source,
      int maxLineInclusive,
      String sourceName,
      IncludeSourceResolver includeResolver,
      Map<String, String> bgPaths,
      Map<String, String> stagePresetPaths,
      Map<String, String> charImgPaths,
      Map<String, Double> characterScales,
      Map<String, Map<String, String>> charLayerPaths,
      Map<String, List<CharacterLayerEntry>> charPresetLayers,
      Map<String, CharacterLayerGroupEntry> charLayerGroups,
      Map<String, SnapshotPosition> customPositions,
      Map<String, List<DisplayPresetEntry>> displayPresets,
      Map<String, String> dynamicGroups,
      Set<String> includeStack
  ) {
    if (source == null || source.isBlank()) return;
    source = LayeredCharacterResolver.collapseLayerDirectiveContinuations(source);
    String normalizedSource = normalizeSourceName(sourceName);
    if (!includeStack.add(normalizedSource)) return;
    try {
      String[] lines = source.split("\n", -1);
      int limit = maxLineInclusive < 0 ? lines.length - 1 : Math.min(maxLineInclusive, lines.length - 1);
      String activeDisplayPresetId = null;
      for (int i = 0; i <= limit; i++) {
        String line = lines[i];
        String trimmed = stripInlineComment(line).trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

        Matcher includeMatcher = INCLUDE_PATTERN.matcher(trimmed);
        if (includeMatcher.matches() && includeResolver != null) {
          String includePath = stripQuotes(includeMatcher.group(1).trim());
          if (!includePath.isEmpty()) {
            try {
              ResolvedInclude resolved = includeResolver.resolve(normalizedSource, includePath);
              if (resolved != null && resolved.sourceText() != null && !resolved.sourceText().isBlank()) {
                collectDeclarations(
                    resolved.sourceText(),
                    -1,
                    resolved.sourceName(),
                    includeResolver,
                    bgPaths,
                    stagePresetPaths,
                    charImgPaths,
                    characterScales,
                    charLayerPaths,
                    charPresetLayers,
                    charLayerGroups,
                    customPositions,
                    displayPresets,
                    dynamicGroups,
                    includeStack);
              }
            } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
            }
          }
          continue;
        }

        Matcher groupMatcher = GROUP_DECL_PATTERN.matcher(trimmed);
        if (groupMatcher.find()) {
          String parent = normalizeGroupParent(groupMatcher.group(2));
          if (parent.isBlank()) dynamicGroups.remove(groupMatcher.group(1));
          else dynamicGroups.put(groupMatcher.group(1), parent);
          continue;
        }

        Matcher backgroundMatcher = BG_DECL_PATTERN.matcher(trimmed);
        if (backgroundMatcher.matches()) {
          bgPaths.put(backgroundMatcher.group(1), backgroundMatcher.group(2).trim());
          continue;
        }

        Matcher stagePresetMatcher = STAGE_PRESET_PATTERN.matcher(trimmed);
        if (stagePresetMatcher.matches()) {
          stagePresetPaths.put(
              stagePresetMatcher.group(1),
              resolveDeclarationPath(normalizedSource, stripQuotes(stagePresetMatcher.group(2).trim())));
          continue;
        }

        Matcher charImgMatcher = CHARIMG_PATTERN.matcher(trimmed);
        if (charImgMatcher.matches()) {
          String key = charImgMatcher.group(1) + "/" + charImgMatcher.group(2);
          String pathSpec = charImgMatcher.group(3).trim();
          charImgPaths.put(key, pathSpec);
          List<CharacterLayerEntry> directLayers = directLayerEntries(pathSpec);
          if (!directLayers.isEmpty()) charPresetLayers.put(key, directLayers);
          continue;
        }

        Matcher characterMatcher = CHARACTER_DECL_PATTERN.matcher(trimmed);
        if (characterMatcher.matches()) {
          double scale = parseCharacterScaleOption(characterMatcher.group(2));
          characterScales.put(characterMatcher.group(1), scale);
          continue;
        }

        Matcher charLayerMatcher = CHARLAYER_PATTERN.matcher(trimmed);
        if (charLayerMatcher.matches()) {
          String charId = charLayerMatcher.group(1);
          String layerId = charLayerMatcher.group(2);
          String path = charLayerMatcher.group(3).trim();
          charLayerPaths.computeIfAbsent(charId, k -> new LinkedHashMap<>()).put(layerId, path);
          continue;
        }

        Matcher charGroupMatcher = CHARGROUP_PATTERN.matcher(trimmed);
        if (charGroupMatcher.matches()) {
          String charId = charGroupMatcher.group(1);
          String groupId = charGroupMatcher.group(2);
          String spec = charGroupMatcher.group(3).trim();
          CharacterLayerGroupEntry group = resolveLayerGroupEntry(
              charLayerPaths,
              charLayerGroups,
              charId,
              groupId,
              spec);
          if (group != null && !group.layerIds.isEmpty()) {
            charLayerGroups.put(charId + "/" + groupId, group);
          }
          continue;
        }

        Matcher charPresetMatcher = CHARPRESET_PATTERN.matcher(trimmed);
        if (charPresetMatcher.matches()) {
          String charId = charPresetMatcher.group(1);
          String expr = charPresetMatcher.group(2);
          String spec = charPresetMatcher.group(3).trim();
          String resolved = resolvePresetSpec(charLayerPaths, charImgPaths, charLayerGroups, charId, spec);
          List<CharacterLayerEntry> layers = resolvePresetLayerEntries(
              charLayerPaths, charImgPaths, charLayerGroups, charPresetLayers, charId, spec);
          if (!resolved.isBlank()) {
            charImgPaths.put(charId + "/" + expr, resolved);
          }
          if (!layers.isEmpty()) {
            charPresetLayers.put(charId + "/" + expr, layers);
          }
          continue;
        }

        Matcher positionMatcher = POSITION_PATTERN.matcher(trimmed);
        if (positionMatcher.matches()) {
          SnapshotPosition position = parseDeclaredPosition(
              positionMatcher.group(1), positionMatcher.group(2));
          if (position != null) customPositions.put(position.name, position);
          continue;
        }

        Matcher displayPresetMatcher = DISPLAY_PRESET_PATTERN.matcher(trimmed);
        if (displayPresetMatcher.matches()) {
          activeDisplayPresetId = displayPresetMatcher.group(1);
          List<DisplayPresetEntry> entries = displayPresets.computeIfAbsent(
              activeDisplayPresetId, ignored -> new ArrayList<>());
          String inlineEntries = displayPresetMatcher.group(2);
          if (inlineEntries != null && !inlineEntries.isBlank()) {
            for (String inlineEntry : inlineEntries.split("\\|")) {
              DisplayPresetEntry parsed = parseDisplayPresetEntry(inlineEntry, customPositions);
              if (parsed != null) entries.add(parsed);
            }
            activeDisplayPresetId = null;
          }
          continue;
        }

        if (activeDisplayPresetId != null) {
          DisplayPresetEntry entry = parseDisplayPresetEntry(trimmed, customPositions);
          if (entry != null) {
            displayPresets.get(activeDisplayPresetId).add(entry);
            continue;
          }
          activeDisplayPresetId = null;
        }
      }
    } finally {
      includeStack.remove(normalizedSource);
    }
  }

  private static double parseCharacterScaleOption(String options) {
    if (options == null || options.isBlank()) return 1.0;
    Matcher matcher = CHARACTER_SCALE_OPTION_PATTERN.matcher(options);
    if (!matcher.find()) return 1.0;
    try {
      double scale = Double.parseDouble(matcher.group(1));
      return Double.isFinite(scale) ? Math.max(0.1, Math.min(3.0, scale)) : 1.0;
    } catch (NumberFormatException ignored) {
      return 1.0;
    }
  }

  private static String resolvePresetSpec(Map<String, Map<String, String>> layersByCharacter,
                                          Map<String, String> expressionsByCharacter,
                                          Map<String, CharacterLayerGroupEntry> groupsByCharacter,
                                          String characterId,
                                          String spec) {
    if (spec == null || spec.isBlank()) return "";
    String[] tokens = spec.split("\\|");
    List<String> resolved = new ArrayList<>();
    for (String token : tokens) {
      if (token == null) continue;
      String part = token.trim();
      if (part.isEmpty()) continue;
      if (part.startsWith("$")) {
        List<CharacterLayerEntry> refs = resolveLayerOrGroupEntries(
            layersByCharacter,
            groupsByCharacter,
            characterId,
            part.substring(1).trim());
        for (CharacterLayerEntry ref : refs) {
          if (ref != null && ref.path != null && !ref.path.isBlank()) {
            resolved.add(ref.path.trim());
          }
        }
      } else if (part.startsWith("@")) {
        LayeredCharacterResolver.CharacterRef ref =
            LayeredCharacterResolver.parseReference(part.substring(1).trim(), characterId);
        String presetPath = expressionsByCharacter.get(ref.characterId() + "/" + ref.localId());
        if (presetPath == null || presetPath.isBlank()) continue;
        resolved.addAll(splitResolvedLayerSpec(presetPath));
      } else {
        resolved.add(part);
      }
    }
    return String.join(" | ", resolved);
  }

  private static List<CharacterLayerEntry> resolvePresetLayerEntries(
      Map<String, Map<String, String>> layersByCharacter,
      Map<String, String> expressionsByCharacter,
      Map<String, CharacterLayerGroupEntry> groupsByCharacter,
      Map<String, List<CharacterLayerEntry>> presetLayersByCharacter,
      String characterId,
      String spec
  ) {
    if (spec == null || spec.isBlank()) return List.of();
    List<CharacterLayerEntry> resolved = new ArrayList<>();
    String[] tokens = spec.split("\\|");
    int directIndex = 1;
    for (String token : tokens) {
      if (token == null) continue;
      String part = token.trim();
      if (part.isEmpty()) continue;
      if (part.startsWith("$")) {
        resolved.addAll(resolveLayerOrGroupEntries(
            layersByCharacter,
            groupsByCharacter,
            characterId,
            part.substring(1).trim()));
      } else if (part.startsWith("@")) {
        LayeredCharacterResolver.CharacterRef ref =
            LayeredCharacterResolver.parseReference(part.substring(1).trim(), characterId);
        List<CharacterLayerEntry> nestedLayers = presetLayersByCharacter == null
            ? null
            : presetLayersByCharacter.get(ref.characterId() + "/" + ref.localId());
        if (nestedLayers != null && !nestedLayers.isEmpty()) {
          resolved.addAll(nestedLayers);
          continue;
        }
        String presetPath = expressionsByCharacter.get(ref.characterId() + "/" + ref.localId());
        List<String> paths = splitResolvedLayerSpec(presetPath);
        int nestedIndex = 1;
        for (String path : paths) {
          resolved.add(new CharacterLayerEntry(ref.localId() + "_" + nestedIndex++, path));
        }
      } else {
        resolved.add(new CharacterLayerEntry("layer" + directIndex++, part));
      }
    }
    return List.copyOf(resolved);
  }

  private static CharacterLayerGroupEntry resolveLayerGroupEntry(
      Map<String, Map<String, String>> layersByCharacter,
      Map<String, CharacterLayerGroupEntry> groupsByCharacter,
      String characterId,
      String groupId,
      String rawSpec
  ) {
    if (characterId == null || characterId.isBlank()
        || groupId == null || groupId.isBlank()
        || rawSpec == null || rawSpec.isBlank()) {
      return null;
    }

    String spec = rawSpec.trim();
    String parentId = "";
    double pivotX = 0.5;
    double pivotY = 1.0;
    boolean hasPivot = false;
    while (!spec.isBlank()) {
      int split = firstWhitespaceIndex(spec);
      String token = split < 0 ? spec : spec.substring(0, split);
      String lower = token.toLowerCase(Locale.ROOT);
      String value = optionValue(token);
      boolean consumed = false;
      if ((lower.startsWith("parent=") || lower.startsWith("parent:")
          || lower.startsWith("in=") || lower.startsWith("in:")) && value != null) {
        parentId = normalizeGroupParent(value);
        consumed = true;
      } else if ((lower.startsWith("pivot=") || lower.startsWith("pivot:")
          || lower.startsWith("origin=") || lower.startsWith("origin:")) && value != null) {
        double[] pivot = parsePivotPair(value);
        if (pivot != null) {
          pivotX = pivot[0];
          pivotY = pivot[1];
          hasPivot = true;
        }
        consumed = true;
      }
      if (!consumed) break;
      spec = split < 0 ? "" : spec.substring(split + 1).trim();
    }

    List<String> layerIds = new ArrayList<>();
    for (String token : spec.split("\\|")) {
      if (token == null) continue;
      String part = token.trim();
      if (part.isEmpty() || !part.startsWith("$")) continue;
      List<CharacterLayerEntry> entries = resolveLayerOrGroupEntries(
          layersByCharacter,
          groupsByCharacter,
          characterId,
          part.substring(1).trim());
      for (CharacterLayerEntry entry : entries) {
        if (entry != null && entry.layerId != null && !entry.layerId.isBlank() && !layerIds.contains(entry.layerId)) {
          layerIds.add(entry.layerId);
        }
      }
    }
    return new CharacterLayerGroupEntry(groupId, parentId, layerIds, pivotX, pivotY, hasPivot);
  }

  private static List<CharacterLayerEntry> resolveLayerOrGroupEntries(
      Map<String, Map<String, String>> layersByCharacter,
      Map<String, CharacterLayerGroupEntry> groupsByCharacter,
      String defaultCharacterId,
      String rawRef
  ) {
    List<LayeredCharacterResolver.LayerMatch> layerMatches =
        LayeredCharacterResolver.resolveLayerMatches(layersByCharacter, defaultCharacterId, rawRef);
    if (!layerMatches.isEmpty()) {
      return layerMatches.stream()
          .map(match -> new CharacterLayerEntry(match.layerId(), match.path()))
          .toList();
    }

    LayeredCharacterResolver.CharacterRef ref = LayeredCharacterResolver.parseReference(rawRef, defaultCharacterId);
    CharacterLayerGroupEntry group = groupsByCharacter == null ? null : groupsByCharacter.get(ref.characterId() + "/" + ref.localId());
    if (group == null || group.layerIds.isEmpty()) return List.of();

    List<CharacterLayerEntry> out = new ArrayList<>();
    for (String layerId : group.layerIds) {
      CharacterLayerEntry entry = resolveLayerEntry(layersByCharacter, ref.characterId(), layerId);
      if (entry != null) out.add(entry);
    }
    return List.copyOf(out);
  }

  private static CharacterLayerEntry resolveLayerEntry(
      Map<String, Map<String, String>> layersByCharacter,
      String defaultCharacterId,
      String rawRef
  ) {
    if (layersByCharacter == null || rawRef == null || rawRef.isBlank()) return null;
    LayeredCharacterResolver.CharacterRef ref = LayeredCharacterResolver.parseReference(rawRef, defaultCharacterId);
    if (ref.characterId() == null || ref.characterId().isBlank()
        || ref.localId() == null || ref.localId().isBlank()) {
      return null;
    }
    Map<String, String> layerMap = layersByCharacter.get(ref.characterId());
    if (layerMap == null || layerMap.isEmpty()) return null;
    for (String candidate : LayeredCharacterResolver.candidateLayerIds(ref.localId())) {
      String path = layerMap.get(candidate);
      if (path != null && !path.isBlank()) {
        return new CharacterLayerEntry(candidate, path.trim());
      }
    }
    return null;
  }

  private static int firstWhitespaceIndex(String value) {
    if (value == null) return -1;
    for (int i = 0; i < value.length(); i++) {
      if (Character.isWhitespace(value.charAt(i))) return i;
    }
    return -1;
  }

  private static String optionValue(String token) {
    if (token == null) return null;
    int eq = token.indexOf('=');
    int colon = token.indexOf(':');
    int sep;
    if (eq > 0 && colon > 0) sep = Math.min(eq, colon);
    else sep = Math.max(eq, colon);
    if (sep <= 0 || sep >= token.length() - 1) return null;
    return stripQuotes(token.substring(sep + 1).trim());
  }

  private static String normalizeGroupParent(String value) {
    String parent = value == null ? "" : stripQuotes(value).trim();
    if (parent.isBlank() || "none".equalsIgnoreCase(parent) || "root".equalsIgnoreCase(parent)) return "";
    return parent;
  }

  private static double[] parsePivotPair(String value) {
    String raw = stripQuotes(value == null ? "" : value.trim());
    String[] parts = raw.split(",", -1);
    if (parts.length != 2) return null;
    try {
      double x = Double.parseDouble(parts[0].trim());
      double y = Double.parseDouble(parts[1].trim());
      if (!Double.isFinite(x) || !Double.isFinite(y)) return null;
      return new double[] {x, y};
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static List<String> splitResolvedLayerSpec(String spec) {
    List<String> resolved = new ArrayList<>();
    if (spec == null || spec.isBlank()) return resolved;
    for (String token : spec.split("\\|")) {
      if (token == null) continue;
      String trimmed = token.trim();
      if (!trimmed.isEmpty()) {
        resolved.add(trimmed);
      }
    }
    return resolved;
  }

  private static List<CharacterLayerEntry> directLayerEntries(String pathSpec) {
    List<String> paths = splitResolvedLayerSpec(pathSpec);
    if (paths.size() < 2) return List.of();
    List<CharacterLayerEntry> layers = new ArrayList<>();
    for (int i = 0; i < paths.size(); i++) {
      String path = paths.get(i);
      String normalized = path.replace('\\', '/');
      int slash = normalized.lastIndexOf('/');
      String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
      int dot = name.lastIndexOf('.');
      if (dot > 0) name = name.substring(0, dot);
      String layerId = selectorSafeName(name);
      layers.add(new CharacterLayerEntry(layerId.isBlank() ? "layer" + (i + 1) : layerId, path));
    }
    return List.copyOf(layers);
  }

  public static SceneSnapshot resolveProjectSnapshot(String source, int line, File script, File root) {
    return resolveSnapshot(source, line, script == null ? null : script.getAbsolutePath(),
        (name, path) -> resolveIncludeSource(name, path, script, root));
  }

  private ResolvedInclude resolveIncludeSource(String sourceName, String includePath) throws IOException {
    return resolveIncludeSource(sourceName, includePath, activeScriptFile, projectRoot);
  }

  private static ResolvedInclude resolveIncludeSource(
      String sourceName, String includePath, File activeScriptFile, File projectRoot) throws IOException {
    String normalized = includePath == null ? "" : includePath.trim().replace('\\', '/');
    if (normalized.isBlank()) {
      throw new IOException("Include path is empty");
    }

    File anchorFile = activeScriptFile;
    if (sourceName != null && !sourceName.isBlank() && !"<script>".equals(sourceName)) {
      File candidate = new File(sourceName);
      if (candidate.isFile()) anchorFile = candidate;
    }
    if (anchorFile == null) {
      throw new IOException("Include resolver unavailable");
    }

    File root = projectRoot;
    if (root == null) {
      root = resolveWorkspaceRoot(anchorFile);
    }
    if (root == null) {
      throw new IOException("Project root unavailable");
    }

    Path rootPath = root.toPath().toAbsolutePath().normalize();
    Path scriptsRoot = ScriptEditorWorkspaceModel.resolveScriptsRoot(root);
    if (scriptsRoot == null) {
      scriptsRoot = rootPath.resolve("scripts");
    }
    scriptsRoot = scriptsRoot.toAbsolutePath().normalize();

    List<Path> candidates = new ArrayList<>();
    if (normalized.startsWith("/")) {
      candidates.add(scriptsRoot.resolve(normalized.substring(1)));
    } else {
      Path sourcePath = anchorFile.toPath().toAbsolutePath().normalize();
      Path sourceParent = sourcePath.getParent();
      if (sourceParent != null) {
        candidates.add(sourceParent.resolve(normalized));
      }
      candidates.add(scriptsRoot.resolve(normalized));
    }
    candidates.add(rootPath.resolve(normalized));

    for (Path candidate : candidates) {
      Path resolved = candidate.toAbsolutePath().normalize();
      if (!resolved.startsWith(rootPath)) continue;
      if (Files.isRegularFile(resolved)) {
        return new ResolvedInclude(resolved.toString(), Files.readString(resolved, StandardCharsets.UTF_8));
      }
    }
    throw new IOException("Included script not found: " + includePath);
  }

  private static File resolveWorkspaceRoot(File scriptFile) {
    if (scriptFile == null) return null;
    Path current = scriptFile.toPath().toAbsolutePath().normalize().getParent();
    while (current != null) {
      Path name = current.getFileName();
      if (name != null && "scripts".equalsIgnoreCase(name.toString())) {
        return current.getParent() == null ? current.toFile() : current.getParent().toFile();
      }
      current = current.getParent();
    }
    return scriptFile.getParentFile();
  }

  private static String normalizeSourceName(String sourceName) {
    if (sourceName == null || sourceName.isBlank()) return "<script>";
    return sourceName.replace('\\', '/');
  }

  private static String stripQuotes(String value) {
    if (value == null) return "";
    String trimmed = value.trim();
    if (trimmed.length() >= 2) {
      char first = trimmed.charAt(0);
      char last = trimmed.charAt(trimmed.length() - 1);
      if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
        return trimmed.substring(1, trimmed.length() - 1).trim();
      }
    }
    return trimmed;
  }

  // --- Data classes ---

  public static class CharacterEntry {
    public final String characterId;
    public final String position;
    public final String expression;
    public final int atLine;
    public final double scale;
    public final String displaySlot;
    public final double positionX;
    public final double positionY;
    public final boolean customPosition;
    public final Integer layerOrder;

    public CharacterEntry(String characterId, String position, String expression, int atLine) {
      this(characterId, position, expression, atLine, 1.0);
    }

    public CharacterEntry(
        String characterId,
        String position,
        String expression,
        int atLine,
        double scale) {
      this(characterId, position, expression, atLine, scale, null, Double.NaN, Double.NaN, false, null);
    }

    public CharacterEntry(
        String characterId,
        String position,
        String expression,
        int atLine,
        double scale,
        String displaySlot,
        double positionX,
        double positionY,
        boolean customPosition,
        Integer layerOrder) {
      this.characterId = characterId;
      this.position = position;
      this.expression = expression;
      this.atLine = atLine;
      this.scale = Double.isFinite(scale) ? Math.max(0.1, Math.min(3.0, scale)) : 1.0;
      this.displaySlot = displaySlot == null || displaySlot.isBlank() ? null : displaySlot.trim();
      this.positionX = positionX;
      this.positionY = positionY;
      this.customPosition = customPosition;
      this.layerOrder = layerOrder;
    }

    public CharacterEntry withScale(double scale) {
      return new CharacterEntry(characterId, position, expression, atLine, scale, displaySlot,
          positionX, positionY, customPosition, layerOrder);
    }

    private CharacterEntry withPosition(SnapshotPosition position, int atLine) {
      return new CharacterEntry(characterId, position.name, expression, atLine, scale, displaySlot,
          position.x, position.y, position.custom, layerOrder);
    }

    private CharacterEntry withExpression(String expression, int atLine) {
      return new CharacterEntry(characterId, position, expression, atLine, scale, displaySlot,
          positionX, positionY, customPosition, layerOrder);
    }

    private CharacterEntry withLayerOrder(Integer layerOrder) {
      return new CharacterEntry(characterId, position, expression, atLine, scale, displaySlot,
          positionX, positionY, customPosition, layerOrder);
    }

    private SnapshotPosition snapshotPosition() {
      return new SnapshotPosition(position, positionX, positionY, customPosition);
    }

    private static CharacterEntry fromPlacement(
        String characterId, String expression, Placement placement, int atLine) {
      SnapshotPosition position = placement.position();
      return new CharacterEntry(characterId, position.name, expression, atLine, 1.0,
          placement.displaySlot(), position.x, position.y, position.custom, placement.layerOrder());
    }
  }

  public static class CharacterLayerEntry {
    public final String layerId;
    public final String path;

    public CharacterLayerEntry(String layerId, String path) {
      this.layerId = layerId == null || layerId.isBlank() ? "layer" : layerId.trim();
      this.path = path == null ? "" : path.trim();
    }
  }

  public static class CharacterLayerGroupEntry {
    public final String groupId;
    public final String parentGroupId;
    public final List<String> layerIds;
    public final double pivotX;
    public final double pivotY;
    public final boolean hasPivot;

    public CharacterLayerGroupEntry(String groupId,
                                    String parentGroupId,
                                    List<String> layerIds,
                                    double pivotX,
                                    double pivotY,
                                    boolean hasPivot) {
      this.groupId = groupId == null || groupId.isBlank() ? "group" : groupId.trim();
      this.parentGroupId = parentGroupId == null ? "" : parentGroupId.trim();
      this.layerIds = layerIds == null ? List.of() : List.copyOf(layerIds);
      this.pivotX = Double.isFinite(pivotX) ? pivotX : 0.5;
      this.pivotY = Double.isFinite(pivotY) ? pivotY : 1.0;
      this.hasPivot = hasPivot;
    }
  }

  public static class SceneSnapshot {
    public final String currentLabel;
    public final String backgroundId;
    public final String previousBackgroundId;
    public final int backgroundLine;
    public final List<CharacterEntry> characters;
    public final int atLine;
    public final Map<String, String> backgroundPaths;
    public final Map<String, String> stagePresetPaths;
    public final Map<String, String> characterImagePaths;
    public final Map<String, List<CharacterLayerEntry>> characterPresetLayers;
    public final Map<String, CharacterLayerGroupEntry> characterLayerGroups;
    public final String activeStagePresetId;
    public final int activeStageLine;
    public final String referencedTimelineName;
	    public final int referencedTimelineLine;
	    public final String inlineTimelineBody;
	    public final int inlineTimelineStartLine;
	    public final String inlineTimelineName;
	    public final List<InlineTimelineContext> inlineTimelineHistory;
      public final Map<String, String> dynamicGroups;

    public SceneSnapshot(String currentLabel,
	                         String backgroundId,
                             String previousBackgroundId,
	                         int backgroundLine,
	                         List<CharacterEntry> characters,
                         int atLine,
                         Map<String, String> backgroundPaths,
                         Map<String, String> characterImagePaths,
                         String referencedTimelineName,
                         int referencedTimelineLine,
                         String inlineTimelineBody,
                         int inlineTimelineStartLine,
                         String inlineTimelineName) {
      this(
          currentLabel,
          backgroundId,
          previousBackgroundId,
	          backgroundLine,
	          characters,
	          atLine,
          backgroundPaths,
          Map.of(),
          characterImagePaths,
          Map.of(),
          Map.of(),
          null,
          -1,
          referencedTimelineName,
          referencedTimelineLine,
	          inlineTimelineBody,
	          inlineTimelineStartLine,
	          inlineTimelineName,
	          List.of(),
            Map.of());
	    }

    public SceneSnapshot(String currentLabel,
	                         String backgroundId,
                             String previousBackgroundId,
	                         int backgroundLine,
	                         List<CharacterEntry> characters,
	                         int atLine,
                         Map<String, String> backgroundPaths,
                         Map<String, String> stagePresetPaths,
                         Map<String, String> characterImagePaths,
                         Map<String, List<CharacterLayerEntry>> characterPresetLayers,
                         String activeStagePresetId,
                         int activeStageLine,
                         String referencedTimelineName,
	                         int referencedTimelineLine,
	                         String inlineTimelineBody,
	                         int inlineTimelineStartLine,
	                         String inlineTimelineName) {
	      this(
	          currentLabel,
	          backgroundId,
              previousBackgroundId,
	          backgroundLine,
	          characters,
	          atLine,
	          backgroundPaths,
	          stagePresetPaths,
	          characterImagePaths,
	          characterPresetLayers,
	          Map.of(),
	          activeStagePresetId,
	          activeStageLine,
	          referencedTimelineName,
	          referencedTimelineLine,
	          inlineTimelineBody,
	          inlineTimelineStartLine,
	          inlineTimelineName,
	          List.of(),
	          Map.of());
	    }

	    public SceneSnapshot(String currentLabel,
		                         String backgroundId,
                                 String previousBackgroundId,
		                         int backgroundLine,
		                         List<CharacterEntry> characters,
		                         int atLine,
	                         Map<String, String> backgroundPaths,
	                         Map<String, String> stagePresetPaths,
	                         Map<String, String> characterImagePaths,
	                         Map<String, List<CharacterLayerEntry>> characterPresetLayers,
	                         Map<String, CharacterLayerGroupEntry> characterLayerGroups,
	                         String activeStagePresetId,
	                         int activeStageLine,
	                         String referencedTimelineName,
	                         int referencedTimelineLine,
	                         String inlineTimelineBody,
	                         int inlineTimelineStartLine,
	                         String inlineTimelineName,
	                         List<InlineTimelineContext> inlineTimelineHistory,
                           Map<String, String> dynamicGroups) {
	      this.currentLabel = currentLabel;
	      this.backgroundId = backgroundId;
          this.previousBackgroundId = previousBackgroundId;
	      this.backgroundLine = backgroundLine;
      this.characters = characters == null ? List.of() : characters;
      this.atLine = atLine;
      this.backgroundPaths = backgroundPaths == null ? Map.of() : backgroundPaths;
      this.stagePresetPaths = stagePresetPaths == null ? Map.of() : stagePresetPaths;
      this.characterImagePaths = characterImagePaths == null ? Map.of() : characterImagePaths;
      this.characterPresetLayers = characterPresetLayers == null ? Map.of() : characterPresetLayers;
      this.characterLayerGroups = characterLayerGroups == null ? Map.of() : characterLayerGroups;
      this.activeStagePresetId = activeStagePresetId;
      this.activeStageLine = activeStageLine;
      this.referencedTimelineName = referencedTimelineName;
      this.referencedTimelineLine = referencedTimelineLine;
	      this.inlineTimelineBody = inlineTimelineBody;
	      this.inlineTimelineStartLine = inlineTimelineStartLine;
	      this.inlineTimelineName = inlineTimelineName;
	      this.inlineTimelineHistory = inlineTimelineHistory == null ? List.of() : List.copyOf(inlineTimelineHistory);
        this.dynamicGroups = dynamicGroups == null ? Map.of() : Map.copyOf(dynamicGroups);
	    }

    public String resolveBackgroundPath() {
      return backgroundId != null ? backgroundPaths.getOrDefault(backgroundId, backgroundId) : null;
    }

    public String resolvePreviousBackgroundPath() {
      return previousBackgroundId != null ? backgroundPaths.getOrDefault(previousBackgroundId, previousBackgroundId) : null;
    }

    public boolean hasBackgroundPathMapping() {
      return backgroundId != null && backgroundPaths.containsKey(backgroundId);
    }

    public boolean hasStagePresetPathMapping() {
      return activeStagePresetId != null && stagePresetPaths.containsKey(activeStagePresetId);
    }

    public String resolveStagePresetPath(File projectRoot) {
      if (activeStagePresetId == null || activeStagePresetId.isBlank()) return "";
      String raw = stagePresetPaths.getOrDefault(activeStagePresetId, "");
      if (raw == null || raw.isBlank()) return "";
      File direct = new File(raw);
      if (direct.isAbsolute()) return direct.getAbsolutePath();
      if (projectRoot != null) {
        File projectRelative = new File(projectRoot, raw);
        if (projectRelative.isFile()) return projectRelative.getAbsolutePath();
      }
      return raw;
    }

    public boolean hasCharacterPathMapping(String characterId, String expression) {
      if (characterId == null || characterId.isBlank()) return false;
      if (expression != null && characterImagePaths.containsKey(characterId + "/" + expression)) {
        return true;
      }
      if (characterImagePaths.containsKey(characterId + "/neutral")) {
        return true;
      }
      for (String key : characterImagePaths.keySet()) {
        if (key != null && key.startsWith(characterId + "/")) return true;
      }
      return false;
    }

    public String resolveCharacterPath(String characterId, String expression) {
      if (expression != null) {
        String key = characterId + "/" + expression;
        if (characterImagePaths.containsKey(key)) return characterImagePaths.get(key);
      }
      String neutralKey = characterId + "/neutral";
      if (characterImagePaths.containsKey(neutralKey)) return characterImagePaths.get(neutralKey);
      for (Map.Entry<String, String> e : characterImagePaths.entrySet()) {
        if (e.getKey().startsWith(characterId + "/")) return e.getValue();
      }
      return characterId;
    }

    public List<CharacterLayerEntry> resolveCharacterLayers(String characterId, String expression) {
      if (characterId == null || characterId.isBlank()) return List.of();
      if (expression != null) {
        List<CharacterLayerEntry> layers = characterPresetLayers.get(characterId + "/" + expression);
        if (layers != null && !layers.isEmpty()) return layers;
      }
      List<CharacterLayerEntry> neutral = characterPresetLayers.get(characterId + "/neutral");
      if (neutral != null && !neutral.isEmpty()) return neutral;
      for (Map.Entry<String, List<CharacterLayerEntry>> entry : characterPresetLayers.entrySet()) {
        if (entry.getKey() != null && entry.getKey().startsWith(characterId + "/")
            && entry.getValue() != null && !entry.getValue().isEmpty()) {
          return entry.getValue();
        }
      }
      return List.of();
    }

    public CharacterLayerGroupEntry resolveCharacterLayerGroup(String characterId, String groupId) {
      if (characterId == null || characterId.isBlank() || groupId == null || groupId.isBlank()) return null;
      return characterLayerGroups.get(characterId + "/" + groupId);
    }

    public List<CharacterLayerGroupEntry> resolveCharacterLayerGroups(String characterId, String expression) {
      if (characterId == null || characterId.isBlank() || characterLayerGroups.isEmpty()) return List.of();
      List<CharacterLayerEntry> visibleLayers = resolveCharacterLayers(characterId, expression);
      Set<String> visibleLayerIds = new LinkedHashSet<>();
      for (CharacterLayerEntry layer : visibleLayers) {
        if (layer != null && layer.layerId != null && !layer.layerId.isBlank()) {
          visibleLayerIds.add(layer.layerId);
        }
      }
      List<CharacterLayerGroupEntry> groups = new ArrayList<>();
      String prefix = characterId + "/";
      for (Map.Entry<String, CharacterLayerGroupEntry> entry : characterLayerGroups.entrySet()) {
        if (entry.getKey() == null || !entry.getKey().startsWith(prefix)) continue;
        CharacterLayerGroupEntry group = entry.getValue();
        if (group == null || group.layerIds.isEmpty()) continue;
        if (visibleLayerIds.isEmpty() || group.layerIds.stream().anyMatch(visibleLayerIds::contains)) {
          groups.add(group);
        }
      }
      // A parent can animate a visible child even when none of its own layers are shown.
      Set<String> active = new LinkedHashSet<>();
      for (CharacterLayerGroupEntry group : groups) {
        CharacterLayerGroupEntry current = group;
        while (current != null && active.add(current.groupId)) {
          current = resolveCharacterLayerGroup(characterId, current.parentGroupId);
        }
      }
      return characterLayerGroups.entrySet().stream()
          .filter(entry -> entry.getKey().startsWith(prefix) && active.contains(entry.getValue().groupId))
          .map(Map.Entry::getValue).toList();
    }

	    public boolean hasInlineTimeline() {
	      return inlineTimelineBody != null && !inlineTimelineBody.isBlank();
	    }

	    public boolean hasInlineTimelineHistory() {
	      return inlineTimelineHistory != null && !inlineTimelineHistory.isEmpty();
	    }

    public boolean hasTimelineContext() {
      return hasInlineTimeline() || (referencedTimelineName != null && !referencedTimelineName.isBlank());
    }

    public String preferredTimelineName() {
      if (hasInlineTimeline() && inlineTimelineName != null && !inlineTimelineName.isBlank()) {
        return inlineTimelineName;
      }
      return referencedTimelineName;
    }

    public File resolveTimelineFile(File projectRoot) {
      if (projectRoot == null || referencedTimelineName == null || referencedTimelineName.isBlank()) {
        return null;
      }
      File file = new File(new File(projectRoot, "scripts/timelines"), referencedTimelineName + ".jes");
      return file.isFile() ? file : null;
    }
  }

  public enum LaunchTimelineMode {
    AUTO,
    LOAD_TIMELINE,
    NEW_FROM_SNAPSHOT
  }

  public record LaunchRequest(SceneSnapshot snapshot, String timelineName, LaunchTimelineMode mode) {
    public LaunchRequest(SceneSnapshot snapshot, String timelineName) {
      this(snapshot, timelineName, LaunchTimelineMode.AUTO);
    }

    public LaunchRequest {
      if (mode == null) mode = LaunchTimelineMode.AUTO;
    }
  }

  public record RegisteredAnimation(
      String name,
      String relativePath,
      String clusterName,
      File file,
      String previewText,
      String statsText,
      int trackCount,
      int keyframeCount,
      int audioCueCount,
      int eventCueCount,
      boolean importable,
      String warningMessage) {}

  public record OpenTarget(File file, int oneBasedLine) {}

  @FunctionalInterface
  interface IncludeSourceResolver {
    ResolvedInclude resolve(String sourceName, String includePath) throws IOException;
  }

	  record ResolvedInclude(String sourceName, String sourceText) {}
	  private record SnapshotPosition(String name, double x, double y, boolean custom) {
      private static SnapshotPosition predefined(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
          case "far_left", "farleft", "fl" -> new SnapshotPosition("far_left", 0.10, -1.0, false);
          case "left", "l" -> new SnapshotPosition("left", 0.25, -1.0, false);
          case "center", "centre", "c" -> new SnapshotPosition("center", 0.50, -1.0, false);
          case "right", "r" -> new SnapshotPosition("right", 0.75, -1.0, false);
          case "far_right", "farright", "fr" -> new SnapshotPosition("far_right", 0.90, -1.0, false);
          default -> null;
        };
      }
    }
	  private record Placement(
        SnapshotPosition position, String expression, String displaySlot, Integer layerOrder) {}
	  private record HideTarget(String characterId, String displaySlot) {}
	  private record DisplayPresetEntry(
        String displaySlot,
        String characterId,
        SnapshotPosition position,
        String expression,
        Integer layerOrder) {}
	  public record InlineTimelineContext(int startLine, int endLine, String body) {}

}
