package com.jvn.editor.ui;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import com.jvn.core.project.ProjectDependencyValidator;
import com.jvn.editor.ui.build.ProjectManifestService;
import com.jvn.editor.ui.build.BuildArtifactService;
import com.jvn.editor.ui.build.BuildCliFormatter;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.geometry.Pos;
import javafx.scene.paint.Color;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Rotate;
import javafx.stage.FileChooser;
import javafx.util.Duration;

/**
 * Popup panel for packaging the currently opened JVN game project.
 */
public class GameBuildPublisherView extends BorderPane {
  public record BuildRequest(String taskName, String[] args, String title) {}

  static String buildCliCommand(String taskName, List<String> args) {
    return BuildCliFormatter.buildCliCommand(taskName, args);
  }

  static List<ArtifactSummary> summarizeArtifacts(File outDir) {
    return BuildArtifactService.summarizeArtifacts(outDir).stream()
        .map(artifact -> new ArtifactSummary(
            artifact.name(),
            artifact.bytes(),
            artifact.lastModifiedMillis(),
            artifact.checksumAvailable()))
        .toList();
  }

  static String formatArtifactInventory(List<ArtifactSummary> artifacts) {
    List<BuildArtifactService.ArtifactSummary> serviceArtifacts = artifacts == null
        ? List.of()
        : artifacts.stream()
            .map(artifact -> new BuildArtifactService.ArtifactSummary(
                artifact.name(),
                artifact.bytes(),
                artifact.lastModifiedMillis(),
                artifact.checksumAvailable()))
            .toList();
    return BuildArtifactService.formatArtifactInventory(serviceArtifacts);
  }

  private final File workspaceRoot;
  private File projectRoot;
  private final Consumer<BuildRequest> onBuildRequested;

  private final TextField projectField = new TextField();
  private final TextField nameField = new TextField();
  private final TextField versionField = new TextField();
  private final ComboBox<TargetChoice> targetBox = new ComboBox<>();
  private final ComboBox<PackageMode> formatBox = new ComboBox<>();
  private final ComboBox<NativeTypeChoice> nativeTypeBox = new ComboBox<>();
  private final ComboBox<String> releaseProfileBox = new ComboBox<>();
  private final ComboBox<String> packageVariantBox = new ComboBox<>();
  private final Label nativeTypeFieldLabel = new Label("Native Type");
  private final Label manifestLabel = new Label();
  private final Label outputLabel = new Label();
  private final Label validationLabel = new Label();
  private final Label releaseConfigLabel = new Label();
  private final Label buildPlanTitleLabel = new Label();
  private final Label buildPlanBodyLabel = new Label();
  private final Label buildPlanHintLabel = new Label();
  private final Label formatBadgeLabel = new Label();
  private final Label targetBadgeLabel = new Label();
  private final Label runtimeBadgeLabel = new Label();
  private final Label releaseBadgeLabel = new Label();
  private final Label statusLabel = new Label();
  private final Label configurationNoticeLabel = new Label();
  private final Label validationNoticeLabel = new Label();
  private final Label commandPreviewLabel = new Label();
  private final Label artifactInventoryLabel = new Label();
  private final Label dependencySummaryLabel = new Label("Dependency report: not scanned yet.");
  private final Label dependencyErrorsBadgeLabel = new Label("Errors 0");
  private final Label dependencyWarningsBadgeLabel = new Label("Warnings 0");
  private final Label dependencyInfoBadgeLabel = new Label("Info 0");
  private final VBox dependencyReportBox = new VBox(8);
  private final CheckBox offlineModeCheck = new CheckBox("Offline");
  private final CheckBox refreshRuntimeCheck = new CheckBox("Refresh runtime");
  private final CheckBox normalizedArchive = new CheckBox("Normalize ZIP entry order and timestamps");
  private final CheckBox storeArchive = new CheckBox("Store ZIP entries without compression");
  private final CheckBox strictDependencies = new CheckBox("Fail dependency checks on warnings");
  private final CheckBox stacktrace = new CheckBox("Print stack traces on failure");
  private final ComboBox<String> runtimeImage = new ComboBox<>();
  private final Map<String, CheckBox> testChoices = new java.util.LinkedHashMap<>();
  private Button shipBuildButton;
  private Button buildSelectedButton;
  private Button buildAllButton;
  private Button preflightButton;
  private Button dependencyScanButton;
  private Button dependencyConsoleButton;
  private Button copyDependencyReportButton;
  private Button clearDependencyReportButton;
  private Button releaseButton;
  private Button smokeTestButton;
  private Button updateBundleButton;
  private Button storeBundleButton;
  private Button copyShipCliButton;
  private Button copyCliButton;
  private Button openProjectButton;
  private Button openReleaseConfigButton;
  private Button revealManifestButton;
  private Button copyManifestPathButton;
  private Button revealRuntimeCacheButton;
  private Button clearRuntimeCacheButton;
  private Button cleanOutputButton;
  private Button refreshArtifactsButton;
  private Button presetPortableButton;
  private Button presetDesktopButton;
  private Button presetNativeButton;
  private final AnimatedPresetArrowIndicator presetPortableIndicator = new AnimatedPresetArrowIndicator();
  private final AnimatedPresetArrowIndicator presetDesktopIndicator = new AnimatedPresetArrowIndicator();
  private final AnimatedPresetArrowIndicator presetNativeIndicator = new AnimatedPresetArrowIndicator();
  private Button btnBrowseOutputDir;
  private Button btnResetOutputDir;
  private Button zipOutputButton;
  private final TextField outputDirField = new TextField();
  private final TextField gameIconField = new TextField();
  private final Label nativeReleaseSummaryLabel = new Label();
  private final VBox nativeReleaseBox = new VBox(8);
  private final FlowPane validationActionsRow = new FlowPane(8, 8);
  private final FlowPane nativeReleaseActionsRow = new FlowPane(8, 8);
  private Button chooseGameIconButton;
  private Button clearGameIconButton;
  private Button nativeReleaseButton;
  private Button nativeReleaseConfigButton;
  private Button nativeCopyChecklistButton;
  private Button nativeRevealIconButton;
  private Button nativeRevealBuildsButton;
  private Button nativeVerifyButton;
  private File customOutputDir = null;
  private ProjectDependencyValidator.Report dependencyReport = null;
  private Task<ProjectDependencyValidator.Report> dependencyScanTask = null;

  public GameBuildPublisherView(File workspaceRoot, File projectRoot, Consumer<BuildRequest> onBuildRequested) {
    this.workspaceRoot = workspaceRoot;
    this.projectRoot = projectRoot;
    this.onBuildRequested = onBuildRequested;
    getStyleClass().add("build-publisher-root");
    buildUi();
    loadProject(projectRoot);
  }

  public void setProjectRoot(File projectRoot) {
    this.projectRoot = projectRoot;
    loadProject(projectRoot);
  }

  private void buildUi() {
    Label title = new Label("Build & Publish");
    title.getStyleClass().add("build-publisher-title");
    presetPortableButton = button("Portable · needs Java", ButtonTone.SECONDARY, true);
    presetPortableButton.setOnAction(e -> applyPreset(PackageMode.PORTABLE_ZIP, currentTargetToken()));
    presetDesktopButton = button("Desktop · all OSes", ButtonTone.SECONDARY, true);
    presetDesktopButton.setOnAction(e -> applyPreset(PackageMode.BUNDLED_RUNTIME_ZIP, "all"));
    presetNativeButton = button("Installer · this OS", ButtonTone.SECONDARY, true);
    presetNativeButton.setOnAction(e -> applyPreset(PackageMode.NATIVE_PACKAGE, currentTargetToken()));

    FlowPane presetRow = new FlowPane(8, 8, presetPortableButton, presetDesktopButton, presetNativeButton);
    presetRow.setAlignment(Pos.CENTER_LEFT);

    projectField.setEditable(false);
    styleField(projectField);
    nameField.setPromptText("Game title");
    styleField(nameField);
    versionField.setPromptText("0.1.0");
    styleField(versionField);

    targetBox.getItems().setAll(desktopTargetChoices());
    targetBox.getSelectionModel().select(0);
    targetBox.setTooltip(new Tooltip(targetBox.getValue().description()));
    styleField(targetBox);
    targetBox.setMaxWidth(Double.MAX_VALUE);

    formatBox.getItems().setAll(PackageMode.supportedValues());
    formatBox.getSelectionModel().select(PackageMode.PORTABLE_ZIP);
    styleField(formatBox);
    formatBox.setMaxWidth(Double.MAX_VALUE);

    nativeTypeBox.getItems().setAll(currentNativeTypeChoices());
    nativeTypeBox.getSelectionModel().selectFirst();
    styleField(nativeTypeBox);
    nativeTypeBox.setMaxWidth(Double.MAX_VALUE);

    releaseProfileBox.setEditable(true);
    styleField(releaseProfileBox);
    releaseProfileBox.setMaxWidth(Double.MAX_VALUE);
    releaseProfileBox.getEditor().setPromptText("default");
    releaseProfileBox.setTooltip(new Tooltip("Named signing and publishing settings from config/release/jvn-release.properties."));
    packageVariantBox.setEditable(true);
    styleField(packageVariantBox);
    packageVariantBox.setMaxWidth(Double.MAX_VALUE);
    packageVariantBox.getEditor().setPromptText("standard");
    packageVariantBox.setTooltip(new Tooltip("Optional content edition such as standard, demo, or deluxe."));

    GridPane form = new GridPane();
    form.setHgap(10);
    form.setVgap(10);
    form.getStyleClass().add("build-publisher-form");
    form.add(label("Project"), 0, 0);
    form.add(projectField, 1, 0);
    form.add(label("Game Name"), 0, 1);
    form.add(nameField, 1, 1);
    form.add(label("Version"), 0, 2);
    form.add(versionField, 1, 2);
    form.add(label("Target"), 0, 3);
    form.add(describedField(targetBox, "Which operating system receives this build."), 1, 3);
    form.add(label("Package"), 0, 4);
    form.add(describedField(formatBox, "Portable, self-contained desktop, or native installer."), 1, 4);
    nativeTypeFieldLabel.getStyleClass().add("layout-launcher-field-label");
    form.add(nativeTypeFieldLabel, 0, 5);
    form.add(describedField(nativeTypeBox, "Installer type supported by this machine."), 1, 5);
    form.add(label("Release Profile"), 0, 6);
    form.add(describedField(releaseProfileBox, "Signing and publishing recipe. Use default for ordinary builds."), 1, 6);
    form.add(label("Package Variant"), 0, 7);
    form.add(describedField(packageVariantBox, "Content edition, for example standard or demo."), 1, 7);
    form.getColumnConstraints().add(new javafx.scene.layout.ColumnConstraints(120));
    javafx.scene.layout.ColumnConstraints fill = new javafx.scene.layout.ColumnConstraints();
    fill.setHgrow(Priority.ALWAYS);
    form.getColumnConstraints().add(fill);

    manifestLabel.setWrapText(true);
    manifestLabel.getStyleClass().addAll("build-publisher-meta-line", "build-publisher-copy");
    outputLabel.setWrapText(true);
    outputLabel.getStyleClass().addAll("build-publisher-meta-line", "build-publisher-copy", "build-publisher-path");
    validationLabel.setWrapText(true);
    validationLabel.getStyleClass().addAll("build-publisher-note", "build-publisher-note-status");
    releaseConfigLabel.setWrapText(true);
    releaseConfigLabel.getStyleClass().addAll("build-publisher-meta-line", "build-publisher-copy");
    buildPlanTitleLabel.setWrapText(true);
    buildPlanTitleLabel.getStyleClass().add("build-publisher-plan-title");
    buildPlanBodyLabel.setWrapText(true);
    buildPlanBodyLabel.getStyleClass().add("build-publisher-copy");
    buildPlanHintLabel.setWrapText(true);
    buildPlanHintLabel.getStyleClass().addAll("build-publisher-note", "build-publisher-note-status");
    styleBadge(formatBadgeLabel);
    styleBadge(targetBadgeLabel);
    styleBadge(runtimeBadgeLabel);
    styleBadge(releaseBadgeLabel);
    statusLabel.setWrapText(true);
    statusLabel.getStyleClass().addAll("build-publisher-note", "build-publisher-note-status");
    styleWorkflowNotice(configurationNoticeLabel);
    styleWorkflowNotice(validationNoticeLabel);
    commandPreviewLabel.setWrapText(true);
    commandPreviewLabel.getStyleClass().addAll("build-publisher-command-preview", "build-publisher-path");
    artifactInventoryLabel.setWrapText(true);
    artifactInventoryLabel.getStyleClass().addAll("build-publisher-artifact-inventory", "build-publisher-path");
    dependencySummaryLabel.setWrapText(true);
    dependencySummaryLabel.getStyleClass().addAll("build-publisher-dependency-summary", "build-publisher-copy");
    styleBadge(dependencyErrorsBadgeLabel);
    styleBadge(dependencyWarningsBadgeLabel);
    styleBadge(dependencyInfoBadgeLabel);
    setBadgeTone(dependencyErrorsBadgeLabel, "error");
    setBadgeTone(dependencyWarningsBadgeLabel, "warn");
    setBadgeTone(dependencyInfoBadgeLabel, "default");
    dependencyReportBox.getStyleClass().add("build-publisher-dependency-report");
    styleOption(offlineModeCheck);
    styleOption(refreshRuntimeCheck);

    outputDirField.setEditable(false);
    outputDirField.setFocusTraversable(false);
    styleField(outputDirField);
    outputDirField.setPromptText("(workspace default)");
    btnBrowseOutputDir = button("Browse...", ButtonTone.SECONDARY, false);
    btnBrowseOutputDir.setOnAction(e -> browseOutputDir());
    btnResetOutputDir = button("Reset", ButtonTone.SECONDARY, false);
    btnResetOutputDir.setOnAction(e -> resetOutputDir());
    btnResetOutputDir.setDisable(true);
    Label outputDirLabel = label("Output Folder");
    HBox outputDirRow = new HBox(6, outputDirLabel, outputDirField, btnBrowseOutputDir, btnResetOutputDir);
    outputDirRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(outputDirField, Priority.ALWAYS);
    outputDirLabel.setMinWidth(120);

    gameIconField.setEditable(false);
    gameIconField.setFocusTraversable(false);
    gameIconField.setPromptText("No package icon selected");
    styleField(gameIconField);
    chooseGameIconButton = button("Choose Icon", ButtonTone.SECONDARY, false);
    chooseGameIconButton.setGraphic(AeroIcon.of(AeroIcon.Kind.IMAGE_ATTRIBUTES, 18));
    chooseGameIconButton.setOnAction(e -> chooseGameIcon());
    clearGameIconButton = button("Clear", ButtonTone.SECONDARY, false);
    clearGameIconButton.setOnAction(e -> clearGameIcon());
    Label gameIconLabel = label("Game Icon");
    HBox gameIconRow = new HBox(6, gameIconLabel, gameIconField, chooseGameIconButton, clearGameIconButton);
    gameIconRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(gameIconField, Priority.ALWAYS);
    gameIconLabel.setMinWidth(120);
    Label gameIconHelp = new Label("Choose a square transparent PNG (512x512 or larger). JVN generates the native " + nativeIconExtension() + " icon.");
    gameIconHelp.setWrapText(true);
    gameIconHelp.getStyleClass().add("build-publisher-field-help");

    VBox projectCard = card("Game details", form, manifestLabel);

    FlowPane badgeRow = new FlowPane(6, 6, formatBadgeLabel, targetBadgeLabel, runtimeBadgeLabel, releaseBadgeLabel);
    badgeRow.setAlignment(Pos.CENTER_LEFT);

    openProjectButton = button("Open Project", ButtonTone.SECONDARY, false);
    openProjectButton.setGraphic(AeroIcon.of(AeroIcon.Kind.OPEN_PROJECT, 18));
    openProjectButton.setOnAction(e -> openProjectFolder());
    openReleaseConfigButton = button("Create Release Config", ButtonTone.SECONDARY, false);
    openReleaseConfigButton.setOnAction(e -> openReleaseConfig());
    revealRuntimeCacheButton = button("Reveal Runtime Cache", ButtonTone.SECONDARY, false);
    revealRuntimeCacheButton.setOnAction(e -> revealRuntimeCache());
    clearRuntimeCacheButton = button("Clear Runtime Cache", ButtonTone.DANGER, false);
    clearRuntimeCacheButton.setOnAction(e -> clearRuntimeCache());

    FlowPane utilitiesRow = new FlowPane(8, 8, openProjectButton, openReleaseConfigButton, revealRuntimeCacheButton, clearRuntimeCacheButton);
    utilitiesRow.setAlignment(Pos.CENTER_LEFT);

    VBox planCard = card("Release preview", badgeRow, buildPlanTitleLabel, buildPlanBodyLabel,
        buildPlanHintLabel, outputLabel, outputDirRow, gameIconRow, gameIconHelp,
        validationLabel, releaseConfigLabel, utilitiesRow);

    shipBuildButton = button("Ship Build", ButtonTone.PRIMARY, false);
    shipBuildButton.setGraphic(AeroIcon.buildProject(22));
    shipBuildButton.getStyleClass().add("build-publisher-cta");
    shipBuildButton.setAccessibleHelp("Builds the selected package plan and writes its release manifest.");
    shipBuildButton.setOnAction(e -> shipSelectedBuild());
    buildSelectedButton = button("Build Selected", ButtonTone.PRIMARY, false);
    buildSelectedButton.setOnAction(e -> buildSelectedTarget());
    buildAllButton = button("Build All Targets", ButtonTone.PRIMARY, false);
    buildAllButton.setOnAction(e -> buildAllTargets());
    preflightButton = button("Package Preflight", ButtonTone.SECONDARY, false);
    preflightButton.setGraphic(AeroIcon.of(AeroIcon.Kind.DIAGNOSTICS, 18));
    preflightButton.setOnAction(e -> runPreflight());
    preflightButton.setTooltip(new Tooltip("Check the manifest, target, output, and release settings."));
    dependencyScanButton = button("Scan Game Content", ButtonTone.PRIMARY, false);
    dependencyScanButton.setGraphic(AeroIcon.of(AeroIcon.Kind.ASSETS, 18));
    dependencyScanButton.setOnAction(e -> runDependencyScan());
    dependencyScanButton.setTooltip(new Tooltip("Find missing assets, broken links, and unused media."));
    dependencyConsoleButton = button("Run Console Scan", ButtonTone.SECONDARY, false);
    dependencyConsoleButton.setOnAction(e -> runDependencyScanInConsole());
    copyDependencyReportButton = button("Copy Report", ButtonTone.SECONDARY, false);
    copyDependencyReportButton.setOnAction(e -> copyDependencyReport());
    clearDependencyReportButton = button("Clear Report", ButtonTone.SECONDARY, false);
    clearDependencyReportButton.setOnAction(e -> clearDependencyReport());
    releaseButton = button("Run Release Hooks", ButtonTone.SECONDARY, false);
    releaseButton.setGraphic(AeroIcon.of(AeroIcon.Kind.MANIFEST, 18));
    releaseButton.setOnAction(e -> runReleaseProfile());
    nativeReleaseButton = button("Sign & Release Package", ButtonTone.PRIMARY, false);
    nativeReleaseButton.setOnAction(e -> runReleaseProfile());
    nativeReleaseConfigButton = button("Release Settings", ButtonTone.SECONDARY, false);
    nativeReleaseConfigButton.setOnAction(e -> openReleaseConfig());
    nativeCopyChecklistButton = button("Copy Checklist", ButtonTone.SECONDARY, false);
    nativeCopyChecklistButton.setOnAction(e -> copyNativeReleaseChecklist());
    nativeRevealIconButton = button("Reveal Icon", ButtonTone.SECONDARY, false);
    nativeRevealIconButton.setOnAction(e -> revealGameIcon());
    nativeRevealBuildsButton = button("Reveal Builds", ButtonTone.SECONDARY, false);
    nativeRevealBuildsButton.setOnAction(e -> revealBuilds());
    nativeVerifyButton = button("Verify Package", ButtonTone.SECONDARY, false);
    nativeVerifyButton.setOnAction(e -> runPackagedArtifactSmokeTest());
    smokeTestButton = button("Verify Packaged Build", ButtonTone.SECONDARY, false);
    smokeTestButton.setGraphic(AeroIcon.of(AeroIcon.Kind.INSPECTOR, 18));
    smokeTestButton.setOnAction(e -> runPackagedArtifactSmokeTest());
    updateBundleButton = button("Build Signed Update", ButtonTone.SECONDARY, false);
    updateBundleButton.setOnAction(e -> writeSignedUpdateBundle());
    storeBundleButton = button("Build Store Bundle", ButtonTone.SECONDARY, false);
    storeBundleButton.setOnAction(e -> writeStoreBundle());
    copyShipCliButton = button("Copy Ship Command", ButtonTone.SECONDARY, false);
    copyShipCliButton.setOnAction(e -> copyShipCommand());
    copyCliButton = button("Copy Build Command", ButtonTone.SECONDARY, false);
    copyCliButton.setOnAction(e -> copyCommand());
    Button reveal = button("Reveal Builds", ButtonTone.SECONDARY, false);
    reveal.setOnAction(e -> revealBuilds());
    revealManifestButton = button("Reveal Manifest", ButtonTone.SECONDARY, false);
    revealManifestButton.setOnAction(e -> revealReleaseManifest());
    copyManifestPathButton = button("Copy Manifest Path", ButtonTone.SECONDARY, false);
    copyManifestPathButton.setOnAction(e -> copyReleaseManifestPath());
    Button notes = button("Copy Publish Notes", ButtonTone.SECONDARY, false);
    notes.setOnAction(e -> copyPublishNotes());
    cleanOutputButton = button("Clean Artifacts", ButtonTone.DANGER, false);
    cleanOutputButton.setOnAction(e -> cleanBuildArtifacts());
    refreshArtifactsButton = button("Refresh Artifacts", ButtonTone.SECONDARY, false);
    refreshArtifactsButton.setGraphic(RefreshIcon.compact());
    refreshArtifactsButton.setOnAction(e -> refreshArtifactInventory());

    FlowPane optionsRow = new FlowPane(12, 8, offlineModeCheck, refreshRuntimeCheck);
    optionsRow.setAlignment(Pos.CENTER_LEFT);
    optionsRow.getStyleClass().add("build-publisher-options");

    zipOutputButton = button("Zip Output Folder", ButtonTone.SECONDARY, false);
    zipOutputButton.setOnAction(e -> zipOutputFolder());
    zipOutputButton.setDisable(true);

    Label buildOnlyHelp = new Label("Create packages without writing the complete release manifest or running the shipping plan.");
    buildOnlyHelp.setWrapText(true);
    buildOnlyHelp.getStyleClass().add("build-publisher-copy");
    FlowPane buildOnlyRow = new FlowPane(8, 8, buildSelectedButton, buildAllButton);
    buildOnlyRow.setAlignment(Pos.CENTER_LEFT);
    TitledPane buildOnlyPane = disclosure("Build only (advanced)", new VBox(8, buildOnlyHelp, buildOnlyRow));
    FlowPane releaseWorkflowRow = new FlowPane(8, 8, smokeTestButton, updateBundleButton, storeBundleButton, releaseButton);
    releaseWorkflowRow.setAlignment(Pos.CENTER_LEFT);
    FlowPane buildUtilityRow = new FlowPane(8, 8, copyShipCliButton, copyCliButton, reveal,
        revealManifestButton, copyManifestPathButton, refreshArtifactsButton, zipOutputButton, notes);
    buildUtilityRow.setAlignment(Pos.CENTER_LEFT);
    FlowPane buildDangerRow = new FlowPane(8, 8, cleanOutputButton);
    buildDangerRow.setAlignment(Pos.CENTER_LEFT);
    VBox advancedReleaseContent = new VBox(10, releaseWorkflowRow);
    TitledPane advancedReleasePane = disclosure("Advanced release workflows", advancedReleaseContent);
    VBox artifactToolsContent = new VBox(10, commandPreviewLabel, buildUtilityRow,
        artifactInventoryLabel, buildDangerRow);
    TitledPane artifactToolsPane = disclosure("Commands, manifests & artifact tools", artifactToolsContent);

    nativeReleaseSummaryLabel.setWrapText(true);
    nativeReleaseSummaryLabel.getStyleClass().add("build-publisher-copy");
    Label nativeReleaseTitle = new Label("Finish native release");
    nativeReleaseTitle.getStyleClass().add("build-publisher-section-label");
    nativeReleaseActionsRow.getChildren().setAll(nativeReleaseButton, nativeReleaseConfigButton,
        nativeCopyChecklistButton, nativeRevealIconButton, nativeRevealBuildsButton, nativeVerifyButton);
    nativeReleaseActionsRow.setAlignment(Pos.CENTER_LEFT);
    nativeReleaseBox.getChildren().setAll(nativeReleaseTitle, nativeReleaseSummaryLabel, nativeReleaseActionsRow);
    nativeReleaseBox.getStyleClass().add("build-publisher-native-release");

    VBox releaseCard = card("Release actions", nativeReleaseBox, buildOnlyPane, advancedReleasePane);
    artifactToolsPane.setExpanded(true);

    FlowPane dependencyBadgeRow = new FlowPane(6, 6, dependencyErrorsBadgeLabel, dependencyWarningsBadgeLabel, dependencyInfoBadgeLabel);
    dependencyBadgeRow.setAlignment(Pos.CENTER_LEFT);
    Label dependencyActionsHelp = new Label("Scan content first to catch broken project references. Package preflight checks the selected release configuration.");
    dependencyActionsHelp.setWrapText(true);
    dependencyActionsHelp.getStyleClass().add("build-publisher-copy");
    validationActionsRow.getChildren().setAll(dependencyScanButton, preflightButton);
    validationActionsRow.setAlignment(Pos.CENTER_LEFT);
    FlowPane dependencyUtilityRow = new FlowPane(8, 8, dependencyConsoleButton,
        copyDependencyReportButton, clearDependencyReportButton);
    dependencyUtilityRow.setAlignment(Pos.CENTER_LEFT);
    TitledPane dependencyToolsPane = disclosure("More validation tools", dependencyUtilityRow);
    VBox dependencyCard = card("Validation status", dependencySummaryLabel, dependencyBadgeRow,
        dependencyActionsHelp, validationActionsRow, dependencyToolsPane, dependencyReportBox);
    renderDependencyPlaceholder("Scan game content to inspect missing media, scripts, menus, stage presets, timelines, packaging blockers, and unused media.");

    runtimeImage.getItems().setAll("Automatic", "JRE", "JDK");
    runtimeImage.getSelectionModel().selectFirst();
    runtimeImage.setAccessibleText("Bundled Java runtime");
    HBox imageRow = new HBox(10, label("Bundled Java image"), runtimeImage);
    imageRow.setAlignment(Pos.CENTER_LEFT);
    VBox packaging = card("Packaging options", optionsRow, imageRow, normalizedArchive, storeArchive, stacktrace);
    HBox header = new HBox(10, AeroIcon.buildProject(34), title, new Region(), shipBuildButton);
    HBox.setHgrow(header.getChildren().get(2), Priority.ALWAYS);
    header.setAlignment(Pos.CENTER_LEFT);
    header.getStyleClass().add("sidebar-tool-header");
    setTop(header);
    GridPane packageDetails = new GridPane();
    packageDetails.setHgap(14);
    packageDetails.setVgap(10);
    packageDetails.add(projectCard, 0, 0);
    packageDetails.add(packaging, 0, 1);
    GridPane.setHgrow(projectCard, Priority.ALWAYS);
    GridPane.setHgrow(packaging, Priority.ALWAYS);
    packageDetails.widthProperty().addListener((obs, oldWidth, width) -> {
      boolean wide = width.doubleValue() >= 840;
      GridPane.setColumnIndex(packaging, wide ? 1 : 0);
      GridPane.setRowIndex(packaging, wide ? 0 : 1);
    });
    TabPane pages = new TabPane(
        page("Package", AeroIcon.Kind.BUILD, presetRow, packageDetails, configurationNoticeLabel),
        page("Checks", AeroIcon.Kind.DIAGNOSTICS, testPlan(), dependencyCard, validationNoticeLabel),
        page("Release", AeroIcon.Kind.MANIFEST, planCard, releaseCard),
        page("Artifacts", AeroIcon.Kind.ASSETS, artifactToolsPane));
    pages.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    setCenter(pages);
    statusLabel.getStyleClass().add("sidebar-tool-header");
    setBottom(statusLabel);
    for (CheckBox option : List.of(normalizedArchive, storeArchive, stacktrace, strictDependencies)) {
      option.selectedProperty().addListener((obs, oldValue, newValue) -> refreshFormState());
    }
    runtimeImage.valueProperty().addListener((obs, oldValue, newValue) -> refreshFormState());

    nameField.textProperty().addListener((obs, oldValue, newValue) -> refreshFormState());
    versionField.textProperty().addListener((obs, oldValue, newValue) -> refreshFormState());
    targetBox.valueProperty().addListener((obs, oldValue, newValue) -> {
      targetBox.setTooltip(newValue == null ? null : new Tooltip(newValue.description()));
      refreshFormState();
    });
    formatBox.valueProperty().addListener((obs, oldValue, newValue) -> {
      refreshTargetChoicesForMode(newValue);
      refreshFormState();
    });
    nativeTypeBox.valueProperty().addListener((obs, oldValue, newValue) -> refreshFormState());
    releaseProfileBox.valueProperty().addListener((obs, oldValue, newValue) -> refreshFormState());
    releaseProfileBox.getEditor().textProperty().addListener((obs, oldValue, newValue) -> refreshFormState());
    packageVariantBox.valueProperty().addListener((obs, oldValue, newValue) -> refreshFormState());
    packageVariantBox.getEditor().textProperty().addListener((obs, oldValue, newValue) -> refreshFormState());
    offlineModeCheck.selectedProperty().addListener((obs, oldValue, newValue) -> refreshFormState());
    refreshRuntimeCheck.selectedProperty().addListener((obs, oldValue, newValue) -> refreshFormState());
  }

  private Label label(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("layout-launcher-field-label");
    return label;
  }

  private VBox describedField(Node field, String help) {
    Label helpLabel = new Label(help);
    helpLabel.setWrapText(true);
    helpLabel.getStyleClass().add("build-publisher-field-help");
    VBox box = new VBox(3, field, helpLabel);
    box.setFillWidth(true);
    return box;
  }

  private void styleWorkflowNotice(Label notice) {
    notice.setWrapText(true);
    notice.setMaxWidth(Double.MAX_VALUE);
    notice.getStyleClass().addAll("build-publisher-workflow-notice", "build-publisher-workflow-notice-pending");
  }

  private void setWorkflowNotice(Label notice, String tone, String text) {
    notice.setText(text);
    notice.getStyleClass().removeAll(
        "build-publisher-workflow-notice-ready",
        "build-publisher-workflow-notice-pending",
        "build-publisher-workflow-notice-blocked",
        "build-publisher-workflow-notice-running");
    notice.getStyleClass().add("build-publisher-workflow-notice-" + tone);
  }

  private VBox card(String title, Node... nodes) {
    Label label = new Label(title);
    label.getStyleClass().add("build-publisher-card-title");
    VBox box = new VBox(12);
    box.getStyleClass().addAll("build-publisher-card", "layout-launcher-card");
    box.getChildren().add(label);
    box.getChildren().addAll(nodes);
    return box;
  }

  private Tab page(String title, AeroIcon.Kind icon, Node... controls) {
    VBox content = new VBox(10, controls);
    content.getStyleClass().add("build-publisher-content");
    ScrollPane scroll = new ScrollPane(content);
    scroll.setFitToWidth(true);
    Tab tab = new Tab(title, scroll);
    tab.setGraphic(AeroIcon.of(icon, 19));
    return tab;
  }

  private VBox testPlan() {
    FlowPane checks = new FlowPane(16, 10);
    addCheck(checks, "Project manifest and entry", "validateJvnGameProject", true);
    addCheck(checks, "Assets and script references", "validateJvnGameDependencies", true);
    addCheck(checks, "Release configuration", "preflightJvnGameBuild", true);
    addCheck(checks, "Core engine tests", ":core:test", false);
    addCheck(checks, "Scripting tests", ":scripting:test", false);
    addCheck(checks, "Renderer tests", ":fx:test", false);
    addCheck(checks, "Runtime tests", ":runtime:test", false);
    addCheck(checks, "Existing package integrity", "smokeTestJvnGameRelease", false);
    Button run = new Button("Run selected checks", AeroIcon.of(AeroIcon.Kind.RUN, 20));
    run.setOnAction(e -> {
      List<String> tasks = testChoices.entrySet().stream().filter(entry -> entry.getValue().isSelected())
          .map(Map.Entry::getKey).toList();
      if (tasks.isEmpty() || projectRoot == null || !projectRoot.isDirectory() || onBuildRequested == null) return;
      List<String> args = new ArrayList<>(tasks.subList(1, tasks.size()));
      args.addAll(buildGradleArgs());
      onBuildRequested.accept(new BuildRequest(tasks.get(0), args.toArray(String[]::new), "JVN Build Checks"));
      statusLabel.setText("Running " + tasks.size() + " checks in the console.");
    });
    testChoices.values().forEach(check -> check.selectedProperty().addListener((obs, old, value) ->
        run.setDisable(testChoices.values().stream().noneMatch(CheckBox::isSelected))));
    return card("Test plan", checks, strictDependencies, run);
  }

  private void addCheck(FlowPane group, String title, String task, boolean selected) {
    CheckBox check = new CheckBox(title);
    check.setSelected(selected);
    testChoices.put(task, check);
    group.getChildren().add(check);
  }

  private TitledPane disclosure(String title, Node content) {
    TitledPane pane = new TitledPane(title, content);
    pane.setExpanded(false);
    pane.setAnimated(false);
    pane.getStyleClass().add("build-publisher-disclosure");
    return pane;
  }

  private Button button(String text, ButtonTone tone, boolean pill) {
    Button button = new Button(text);
    button.getStyleClass().add("build-publisher-button");
    switch (tone) {
      case PRIMARY -> button.getStyleClass().add("build-publisher-button-primary");
      case DANGER -> button.getStyleClass().add("build-publisher-button-danger");
      case SECONDARY -> button.getStyleClass().add("build-publisher-button-secondary");
    }
    if (pill) button.getStyleClass().add("build-publisher-button-pill");
    button.setMinHeight(34);
    return button;
  }

  private void styleBadge(Label label) {
    label.getStyleClass().add("build-publisher-badge");
    label.setMinHeight(24);
  }

  private void styleField(TextField field) {
    field.getStyleClass().add("layout-launcher-field");
  }

  private void styleField(ComboBox<?> box) {
    box.getStyleClass().add("layout-launcher-field");
  }

  private void styleOption(CheckBox option) {
    option.getStyleClass().add("build-publisher-option");
  }

  private void loadProject(File root) {
    projectField.setText(root == null ? "" : root.getAbsolutePath());
    clearDependencyReport();
    Properties manifest = ProjectManifestService.loadManifest(root);
    releaseProfileBox.getItems().setAll(availableReleaseProfiles(root));
    packageVariantBox.getItems().setAll(availablePackageVariants(root));
    if (manifest == null) {
      nameField.setText(root == null ? "" : root.getName());
      versionField.setText("0.1.0");
      setReleaseProfileSelection(defaultReleaseProfile(root));
      setPackageVariantSelection(defaultPackageVariant(root));
      releaseConfigLabel.setText(releaseConfigText(root));
      manifestLabel.setText("No jvn.project found. Choose a JVN game project before building.");
      statusLabel.setText("Build unavailable: missing jvn.project.");
      refreshFormState();
      refreshArtifactInventory();
      return;
    }

    String name = manifest.getProperty("name", root == null ? "JVN Game" : root.getName()).trim();
    String version = ProjectManifestService.firstNonBlank(
        manifest.getProperty("version"),
        manifest.getProperty("releaseVersion"),
        manifest.getProperty("build.version"),
        "0.1.0");
    nameField.setText(name.isBlank() ? root.getName() : name);
    versionField.setText(version);
    setReleaseProfileSelection(defaultReleaseProfile(root));
    setPackageVariantSelection(defaultPackageVariant(root));
    releaseConfigLabel.setText(releaseConfigText(root));
    manifestLabel.setText("Manifest: type=" + manifest.getProperty("type", "vn")
        + "  " + ProjectManifestService.manifestEntryText(manifest)
        + "  runtime.ui=" + manifest.getProperty("runtime.ui", "fx")
        + "  runtime.audio=" + manifest.getProperty("runtime.audio", "auto"));
    statusLabel.setText("Ready to build " + nameField.getText() + ".");
    refreshFormState();
    refreshArtifactInventory();
  }

  private void setReleaseProfileSelection(String profile) {
    String value = profile == null || profile.isBlank() ? "default" : profile.trim();
    if (!releaseProfileBox.getItems().contains(value)) {
      releaseProfileBox.getItems().add(value);
    }
    releaseProfileBox.setValue(value);
    releaseProfileBox.getEditor().setText(value);
  }

  private void setPackageVariantSelection(String variant) {
    String value = variant == null || variant.isBlank() ? "standard" : variant.trim();
    if (!packageVariantBox.getItems().contains(value)) packageVariantBox.getItems().add(value);
    packageVariantBox.setValue(value);
    packageVariantBox.getEditor().setText(value);
  }



  private ValidationResult refreshFormState() {
    refreshOutputPreview();
    ValidationResult result = validateForm();
    applyValidation(result);
    refreshPresetButtons();
    refreshBuildPlan(result);
    refreshActionButtons(result);
    refreshUtilityButtons(result);
    refreshCommandPreview(result);
    refreshWorkflowNotices(result);
    refreshNativeReleaseState(result);
    return result;
  }

  private void refreshNativeReleaseState(ValidationResult result) {
    boolean nativeMode = selectedPackageMode() == PackageMode.NATIVE_PACKAGE;
    nativeReleaseBox.setManaged(nativeMode);
    nativeReleaseBox.setVisible(nativeMode);
    String icon = releaseProfileValue(projectRoot, selectedReleaseProfile(), "icon");
    gameIconField.setText(icon);
    clearGameIconButton.setDisable(icon.isBlank());
    chooseGameIconButton.setDisable(projectRoot == null || !projectRoot.isDirectory());
    if (!nativeMode) return;

    Properties properties = loadReleaseConfig(projectRoot);
    String profile = selectedReleaseProfile();
    String os = currentHostOs();
    List<String> steps = new ArrayList<>();
    steps.add(icon.isBlank()
        ? "1. Choose a high-resolution transparent PNG for the game icon."
        : "1. Icon ready: " + icon);
    if ("macos".equals(os)) {
      boolean sign = releaseProfileFlag(properties, profile, "mac.sign");
      boolean notarize = releaseProfileFlag(properties, profile, "mac.notarize");
      String identity = releaseProfileValue(properties, profile, "mac.signingIdentity");
      String notaryProfile = releaseProfileValue(properties, profile, "mac.notarytoolProfile");
      steps.add(sign && !identity.isBlank()
          ? "2. Code signing ready: " + identity
          : "2. Configure mac.sign=true and a Developer ID signing identity in the release profile.");
      steps.add(notarize && !notaryProfile.isBlank()
          ? "3. Notarization ready: keychain profile " + notaryProfile
          : "3. Configure mac.notarize=true and mac.notarytoolProfile, then run the signed release.");
    } else if ("windows".equals(os)) {
      boolean sign = releaseProfileFlag(properties, profile, "win.sign");
      String certificate = releaseProfileValue(properties, profile, "win.certificateFile");
      String subject = releaseProfileValue(properties, profile, "win.subjectName");
      steps.add(sign && (!certificate.isBlank() || !subject.isBlank())
          ? "2. Authenticode signing is configured."
          : "2. Configure win.sign=true and a certificate file or subject name in the release profile.");
      steps.add("3. Run the signed release, then test the installer on a clean Windows account.");
    } else {
      steps.add("2. Configure Linux package metadata in the release profile.");
      steps.add("3. Run the release profile, then verify the package on its target distribution.");
    }
    nativeReleaseSummaryLabel.setText(String.join("\n", steps));
    boolean valid = result != null && result.errors().isEmpty();
    nativeReleaseButton.setDisable(!valid || findReleaseConfig(projectRoot) == null);
    nativeReleaseConfigButton.setText(findReleaseConfig(projectRoot) == null ? "Create Release Settings" : "Release Settings");
    nativeReleaseConfigButton.setDisable(projectRoot == null || !projectRoot.isDirectory());
    nativeCopyChecklistButton.setDisable(projectRoot == null || !projectRoot.isDirectory());
    nativeRevealIconButton.setDisable(icon.isBlank() || !resolveReleaseProfileFile(icon).isFile());
    File[] artifacts = buildDistributionsDir().listFiles(file -> file.isFile() && !file.isHidden());
    boolean hasArtifacts = artifacts != null && artifacts.length > 0;
    nativeRevealBuildsButton.setDisable(!hasArtifacts);
    nativeVerifyButton.setDisable(!valid || !hasArtifacts);
    nativeReleaseButton.setText(switch (os) {
      case "macos" -> "Build Signed & Notarized Package";
      case "windows" -> "Build & Sign Package";
      default -> "Build Release Package";
    });
  }

  private void refreshWorkflowNotices(ValidationResult result) {
    if (result == null || !result.errors().isEmpty()) {
      String issue = result == null || result.errors().isEmpty()
          ? "Complete the release configuration above."
          : result.errors().get(0);
      setWorkflowNotice(configurationNoticeLabel, "blocked", "Configuration needs attention — " + issue);
      setWorkflowNotice(validationNoticeLabel, "pending", "Validation unlocks after the configuration is ready.");
      return;
    }

    setWorkflowNotice(configurationNoticeLabel, "ready",
        "Configuration ready, continue to Validate below.");
    if (dependencyScanTask != null && dependencyScanTask.isRunning()) {
      setWorkflowNotice(validationNoticeLabel, "running", "Scanning game content… Results will appear here when ready.");
    } else if (dependencyReport == null) {
      setWorkflowNotice(validationNoticeLabel, "pending",
          "Next: scan the game content, or run package preflight before shipping.");
    } else if (dependencyReport.errorCount() > 0) {
      setWorkflowNotice(validationNoticeLabel, "blocked",
          "Validation found " + dependencyReport.errorCount() + " blocking issue(s). Fix them before shipping.");
    } else if (dependencyReport.warningCount() > 0) {
      setWorkflowNotice(validationNoticeLabel, "ready",
          "Validation complete with " + dependencyReport.warningCount() + " warning(s), review them, then continue to Build & ship.");
    } else {
      setWorkflowNotice(validationNoticeLabel, "ready",
          "Validation clear, continue to Build & ship below.");
    }
  }

  private void applyValidation(ValidationResult result) {
    PackageMode mode = selectedPackageMode();
    TargetChoice currentTarget = targetBox.getValue();
    boolean targetIsAll = currentTarget != null && "all".equals(currentTarget.outputToken());
    boolean supportsBuildAll = mode != PackageMode.NATIVE_PACKAGE && !targetIsAll;
    nativeTypeBox.setDisable(mode != PackageMode.NATIVE_PACKAGE);
    nativeTypeBox.setManaged(mode == PackageMode.NATIVE_PACKAGE);
    nativeTypeBox.setVisible(mode == PackageMode.NATIVE_PACKAGE);
    nativeTypeFieldLabel.setManaged(mode == PackageMode.NATIVE_PACKAGE);
    nativeTypeFieldLabel.setVisible(mode == PackageMode.NATIVE_PACKAGE);
    refreshRuntimeCheck.setDisable(mode != PackageMode.BUNDLED_RUNTIME_ZIP);
    runtimeImage.setDisable(mode != PackageMode.BUNDLED_RUNTIME_ZIP);
    normalizedArchive.setDisable(mode == PackageMode.NATIVE_PACKAGE);
    storeArchive.setDisable(mode == PackageMode.NATIVE_PACKAGE);

    boolean canBuild = result != null && result.errors().isEmpty();
    if (shipBuildButton != null) shipBuildButton.setDisable(!canBuild);
    if (buildSelectedButton != null) buildSelectedButton.setDisable(!canBuild);
    if (buildAllButton != null) buildAllButton.setDisable(!canBuild || !supportsBuildAll);
    if (buildAllButton != null) {
      buildAllButton.setManaged(supportsBuildAll);
      buildAllButton.setVisible(supportsBuildAll);
    }
    if (preflightButton != null) preflightButton.setDisable(!canBuild);
    if (smokeTestButton != null) smokeTestButton.setDisable(!canBuild);
    if (updateBundleButton != null) updateBundleButton.setDisable(!canBuild);
    if (storeBundleButton != null) storeBundleButton.setDisable(!canBuild);
    boolean canRelease = canBuild && releaseTaskForSelection() != null;
    if (releaseButton != null) releaseButton.setDisable(!canRelease);
    if (copyShipCliButton != null) copyShipCliButton.setDisable(!canBuild);
    if (copyCliButton != null) copyCliButton.setDisable(!canBuild);

    if (result == null) {
      validationLabel.setText("");
      setNoteTone(validationLabel, "status");
      return;
    }
    if (!result.errors().isEmpty()) {
      validationLabel.setText(formatValidationMessages("Blocked", result.errors()));
      setNoteTone(validationLabel, "error");
      statusLabel.setText("Build unavailable: " + result.errors().get(0));
      setNoteTone(statusLabel, "error");
      return;
    }
    if (!result.warnings().isEmpty()) {
      validationLabel.setText(formatValidationMessages("Warnings", result.warnings()));
      setNoteTone(validationLabel, "warn");
      setNoteTone(statusLabel, "status");
      return;
    }
    validationLabel.setText("Validated: project, manifest, entry file, target, and output folder are ready.");
    setNoteTone(validationLabel, "ok");
    setNoteTone(statusLabel, "status");
  }

  private void refreshPresetButtons() {
    PackageMode mode = selectedPackageMode();
    String targetToken = targetBox.getValue() == null ? "" : targetBox.getValue().outputToken();
    updatePresetButton(
        presetPortableButton,
        presetPortableIndicator,
        mode == PackageMode.PORTABLE_ZIP && currentTargetToken().equals(targetToken));
    updatePresetButton(
        presetDesktopButton,
        presetDesktopIndicator,
        mode == PackageMode.BUNDLED_RUNTIME_ZIP && "all".equals(targetToken));
    updatePresetButton(
        presetNativeButton,
        presetNativeIndicator,
        mode == PackageMode.NATIVE_PACKAGE && currentTargetToken().equals(targetToken));
  }

  private void updatePresetButton(Button button, AnimatedPresetArrowIndicator indicator, boolean selected) {
    if (button == null || indicator == null) return;
    button.getStyleClass().removeAll("build-publisher-button-primary", "build-publisher-button-secondary");
    button.getStyleClass().add(selected ? "build-publisher-button-primary" : "build-publisher-button-secondary");
    button.setGraphic(selected ? indicator : null);
    button.setContentDisplay(selected ? ContentDisplay.LEFT : ContentDisplay.TEXT_ONLY);
    button.setGraphicTextGap(selected ? 6 : 0);
    button.setAccessibleText((selected ? "Selected preset: " : "Preset: ") + button.getText());
    indicator.setActive(selected);
  }

  private void refreshBuildPlan(ValidationResult result) {
    PackageMode mode = selectedPackageMode();
    TargetChoice target = targetBox.getValue();
    String targetText = target == null ? "No target selected" : target.label();
    buildPlanTitleLabel.setText(selectionTitle(mode, target));
    buildPlanBodyLabel.setText(selectionBody(mode, target));
    buildPlanHintLabel.setText(selectionHint(mode, target, result));

    formatBadgeLabel.setText(mode.toString());
    targetBadgeLabel.setText(targetText);
    runtimeBadgeLabel.setText(mode == PackageMode.PORTABLE_ZIP ? "Java 21 Required" : "Java Bundled");

    File releaseConfig = findReleaseConfig(projectRoot);
    String profile = selectedReleaseProfile();
    if (mode == PackageMode.NATIVE_PACKAGE && releaseConfig == null) {
      releaseBadgeLabel.setText("Unsigned");
      setBadgeTone(releaseBadgeLabel, "warn");
    } else if (releaseConfig == null) {
      releaseBadgeLabel.setText("No Release Config");
      setBadgeTone(releaseBadgeLabel, "default");
    } else {
      releaseBadgeLabel.setText("Profile " + profile);
      setBadgeTone(releaseBadgeLabel, "accent");
    }

    setBadgeTone(formatBadgeLabel, mode == PackageMode.BUNDLED_RUNTIME_ZIP ? "accent" : "default");
    setBadgeTone(targetBadgeLabel, "all".equals(target == null ? "" : target.outputToken()) ? "accent" : "default");
    if (result != null && !result.errors().isEmpty()) setBadgeTone(runtimeBadgeLabel, "error");
    else if (result != null && !result.warnings().isEmpty()) setBadgeTone(runtimeBadgeLabel, "warn");
    else setBadgeTone(runtimeBadgeLabel, mode == PackageMode.PORTABLE_ZIP ? "default" : "accent");
  }

  private void refreshActionButtons(ValidationResult result) {
    BuildTaskSelection buildSelection = buildTaskForSelection();
    if (shipBuildButton != null) {
      shipBuildButton.setText(switch (selectedPackageMode()) {
        case PORTABLE_ZIP -> "Ship Portable";
        case BUNDLED_RUNTIME_ZIP -> "Ship Desktop";
        case NATIVE_PACKAGE -> "Ship Native";
      });
    }
    if (buildSelectedButton != null) {
      buildSelectedButton.setText(buildSelection == null ? "Build Selected" : buildActionLabel(buildSelection));
    }
    if (buildAllButton != null) {
      buildAllButton.setText(switch (selectedPackageMode()) {
        case PORTABLE_ZIP -> "Build All Portable Targets";
        case BUNDLED_RUNTIME_ZIP -> "Build All Desktop Bundles";
        case NATIVE_PACKAGE -> "Build All";
      });
    }
    BuildTaskSelection releaseSelection = releaseTaskForSelection();
    if (releaseButton != null) {
      releaseButton.setText(releaseSelection == null ? "Run Release Profile" : releaseActionLabel(releaseSelection));
    }
    if (copyCliButton != null) {
      copyCliButton.setText(result != null && result.errors().isEmpty() ? "Copy Build Command" : "Copy CLI Command");
    }
    if (copyShipCliButton != null) {
      copyShipCliButton.setText(result != null && result.errors().isEmpty() ? "Copy Ship Command" : "Copy Ship CLI");
    }
  }

  private void refreshUtilityButtons(ValidationResult result) {
    if (openProjectButton != null) openProjectButton.setDisable(projectRoot == null || !projectRoot.isDirectory());
    boolean dependencyScanRunning = dependencyScanTask != null && dependencyScanTask.isRunning();
    if (dependencyScanButton != null) {
      dependencyScanButton.setDisable(workspaceRoot == null || !workspaceRoot.isDirectory()
          || projectRoot == null || !projectRoot.isDirectory()
          || dependencyScanRunning);
    }
    if (dependencyConsoleButton != null) {
      dependencyConsoleButton.setDisable(workspaceRoot == null || !workspaceRoot.isDirectory()
          || projectRoot == null || !projectRoot.isDirectory()
          || dependencyScanRunning);
    }
    if (copyDependencyReportButton != null) copyDependencyReportButton.setDisable(dependencyReport == null);
    if (clearDependencyReportButton != null) clearDependencyReportButton.setDisable(dependencyReport == null && !dependencyScanRunning);
    if (openReleaseConfigButton != null) {
      File releaseConfig = findReleaseConfig(projectRoot);
      openReleaseConfigButton.setText(releaseConfig == null ? "Create Release Config" : "Open Release Config");
      openReleaseConfigButton.setDisable(projectRoot == null || !projectRoot.isDirectory());
    }
    boolean allowCache = workspaceRoot != null && workspaceRoot.isDirectory();
    if (revealRuntimeCacheButton != null) revealRuntimeCacheButton.setDisable(!allowCache);
    if (clearRuntimeCacheButton != null) clearRuntimeCacheButton.setDisable(!allowCache || (result != null && !result.errors().isEmpty() && selectedPackageMode() == PackageMode.NATIVE_PACKAGE));
    if (cleanOutputButton != null) cleanOutputButton.setDisable(!allowCache);
    if (refreshArtifactsButton != null) refreshArtifactsButton.setDisable(!allowCache);
    File manifest = releaseManifestJsonFile();
    boolean manifestAvailable = manifest.isFile();
    if (revealManifestButton != null) revealManifestButton.setDisable(!manifestAvailable);
    if (copyManifestPathButton != null) copyManifestPathButton.setDisable(!manifestAvailable);
    if (zipOutputButton != null) {
      File outDir = buildDistributionsDir();
      File[] outFiles = outDir.isDirectory() ? outDir.listFiles(f -> f.isFile() && !f.isHidden()) : null;
      zipOutputButton.setDisable(outFiles == null || outFiles.length == 0);
    }
  }

  private void refreshCommandPreview(ValidationResult result) {
    if (commandPreviewLabel == null) return;
    if (result == null || !result.errors().isEmpty()) {
      commandPreviewLabel.setText("Command: resolve validation issues to preview the Gradle invocation.");
      return;
    }
    BuildTaskSelection selection = buildTaskForSelection();
    if (selection == null) {
      commandPreviewLabel.setText("Command: choose a build target and format.");
      return;
    }
    commandPreviewLabel.setText(
        "Ship: " + BuildCliFormatter.buildCliCommand("assembleJvnGameRelease", buildGradleArgs())
            + "\nBuild only: " + BuildCliFormatter.buildCliCommand(selection.taskName(), buildGradleArgs()));
  }

  private ValidationResult validateForm() {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    PackageMode mode = selectedPackageMode();

    if (workspaceRoot == null || !workspaceRoot.isDirectory()) {
      errors.add("JVN workspace root was not found.");
    }
    if (projectRoot == null || !projectRoot.isDirectory()) {
      errors.add("Open a JVN game project first.");
    } else {
      if (ProjectManifestService.sameCanonical(projectRoot, workspaceRoot)) {
        errors.add("Selected project is the JVN engine workspace, not a game project.");
      }
      if (!projectRoot.getName().equals(projectRoot.getName().trim())) {
        warnings.add("Project folder name has leading or trailing spaces; the build preserves it, but CLI paths are easy to mistype.");
      }
      Properties manifest = ProjectManifestService.loadManifest(projectRoot);
      if (manifest == null) {
        errors.add("Selected project has no readable jvn.project.");
      } else {
        ProjectManifestService.validateManifest(projectRoot, manifest, errors, warnings);
      }
    }

    TargetChoice target = targetBox.getValue();
    if (target == null) {
      errors.add("Choose a build target.");
    } else if (target.outputToken().startsWith("unsupported")) {
      errors.add(target.description());
    } else if (mode == PackageMode.NATIVE_PACKAGE && !"windows-x64".equals(target.outputToken())
        && !"linux-x64".equals(target.outputToken()) && !"macos-x64".equals(target.outputToken())
        && !"macos-aarch64".equals(target.outputToken())) {
      errors.add("Native packages are only available for supported desktop targets.");
    } else if (mode == PackageMode.NATIVE_PACKAGE && !target.outputToken().equals(currentTargetToken())) {
      errors.add("Native packages are host-only. Switch target to Current machine.");
    }

    if (mode == PackageMode.NATIVE_PACKAGE) {
      NativeTypeChoice nativeType = nativeTypeBox.getValue();
      if (nativeType == null) {
        errors.add("Choose a native package type.");
      } else if (nativeType.token().startsWith("unsupported")) {
        errors.add(nativeType.description());
      }
    }

    File outDir = buildDistributionsDir();
    File writableProbe = outDir.exists() ? outDir : outDir.getParentFile();
    if (writableProbe != null && writableProbe.exists() && !writableProbe.canWrite()) {
      errors.add("Build output folder is not writable: " + outDir.getPath());
    }

    String profile = selectedReleaseProfile();
    File releaseConfig = findReleaseConfig(projectRoot);
    if (!profile.isBlank() && !"default".equalsIgnoreCase(profile)) {
      if (releaseConfig == null) {
        errors.add("Release profile '" + profile + "' was requested, but no jvn-release.properties file was found.");
      } else if (!availableReleaseProfiles(projectRoot).contains(profile)) {
        errors.add("Release profile '" + profile + "' was not found in " + releaseConfig.getPath() + ".");
      }
    } else if (releaseConfig == null && mode == PackageMode.NATIVE_PACKAGE) {
      warnings.add("No release profile config found. Native packaging will build unsigned packages without publish commands.");
    }

    String variant = selectedPackageVariant();
    if (!variant.matches("[A-Za-z0-9._-]+")) {
      errors.add("Package variant may use only letters, numbers, dots, underscores, and hyphens.");
    }

    return new ValidationResult(errors, warnings);
  }


  private void refreshOutputPreview() {
    TargetChoice target = targetBox.getValue();
    PackageMode mode = selectedPackageMode();
    String targetId = target == null ? "current-target" : target.outputToken();
    String variant = selectedPackageVariant();
    String variantSuffix = "standard".equalsIgnoreCase(variant) ? "" : "-" + BuildCliFormatter.safeToken(variant);
    String stem = BuildCliFormatter.safeToken(nameField.getText()) + "-" + BuildCliFormatter.safeToken(versionField.getText()) + variantSuffix;
    File outDir = buildDistributionsDir();
    switch (mode) {
      case PORTABLE_ZIP -> {
        if (target != null && "all".equals(targetId)) {
          outputLabel.setText("Output: " + new File(outDir, stem + "-*.zip").getPath());
        } else {
          outputLabel.setText("Output: " + new File(outDir, stem + "-" + targetId + ".zip").getPath());
        }
      }
      case BUNDLED_RUNTIME_ZIP -> {
        if (target != null && "all".equals(targetId)) {
          outputLabel.setText("Output: " + new File(outDir, stem + "-*-runtime.zip").getPath());
        } else {
          outputLabel.setText("Output: " + new File(outDir, stem + "-" + targetId + "-runtime.zip").getPath());
        }
      }
      case NATIVE_PACKAGE -> {
        NativeTypeChoice nativeType = nativeTypeBox.getValue();
        String ext = nativeType == null ? ".pkg" : nativeType.artifactSuffix();
        outputLabel.setText("Output: " + new File(outDir,
            BuildCliFormatter.safeToken(nameField.getText()) + "-"
                + BuildCliFormatter.safeNativeVersionToken(versionField.getText()) + variantSuffix + "-"
                + currentTargetToken() + "-"
                + (nativeType == null ? "native" : nativeType.token()) + ext).getPath());
      }
    }
    updateOutputDirField();
  }

  private void buildSelectedTarget() {
    BuildTaskSelection selection = buildTaskForSelection();
    if (selection == null) return;
    buildTask(selection.taskName(), selection.title());
  }

  private void shipSelectedBuild() {
    if (!canBuild()) return;
    List<String> args = buildGradleArgs();
    if (onBuildRequested != null) {
      onBuildRequested.accept(new BuildRequest("assembleJvnGameRelease", args.toArray(String[]::new), "Ship Game Build"));
    }
    statusLabel.setText("Started ship build. This builds the selected package plan and writes release-manifest.json/Markdown.");
    setNoteTone(statusLabel, "status");
  }

  private String selectionTitle(PackageMode mode, TargetChoice target) {
    if (target == null) return "Choose a target and format";
    return switch (mode) {
      case PORTABLE_ZIP -> "Portable zip for " + target.label();
      case BUNDLED_RUNTIME_ZIP -> "Desktop bundle for " + target.label();
      case NATIVE_PACKAGE -> {
        NativeTypeChoice nativeType = nativeTypeBox.getValue();
        String nativeLabel = nativeType == null ? "native package" : nativeType.label().toLowerCase(Locale.ROOT);
        yield nativeLabel.substring(0, 1).toUpperCase(Locale.ROOT) + nativeLabel.substring(1) + " for " + target.label();
      }
    };
  }

  private String selectionBody(PackageMode mode, TargetChoice target) {
    if (target == null) return "Pick a build target to see the release plan.";
    return switch (mode) {
      case PORTABLE_ZIP -> "Fastest export path. Good for internal drops and teams that already control the Java runtime.";
      case BUNDLED_RUNTIME_ZIP -> {
        if ("all".equals(target.outputToken())) {
          yield "Builds the full desktop-bundle set so the game is ready to upload for Windows, Linux, and both macOS variants.";
        }
        yield "Self-contained build with a packaged runtime for " + target.label() + ". Players can unzip and launch without installing Java.";
      }
      case NATIVE_PACKAGE -> "Host-native installer path with release-profile support for signing, notarization, and publish hooks.";
    };
  }

  private String selectionHint(PackageMode mode, TargetChoice target, ValidationResult result) {
    if (result != null && !result.errors().isEmpty()) {
      return "Resolve the blocking issue above before starting the build.";
    }
    if (result != null && !result.warnings().isEmpty()) {
      return "Current note: " + result.warnings().get(0);
    }
    if (target == null) return "No target selected.";
    String targetDescription = target.description();
    return switch (mode) {
      case PORTABLE_ZIP -> targetDescription;
      case BUNDLED_RUNTIME_ZIP -> "all".equals(target.outputToken())
          ? "First bundle builds may download and verify runtimes for each target, then reuse the local cache."
          : targetDescription + " The first build for this target downloads and verifies its runtime cache.";
      case NATIVE_PACKAGE -> targetDescription + " Release hooks only run for a single selected artifact.";
    };
  }

  private String buildActionLabel(BuildTaskSelection selection) {
    String title = selection.title();
    if (title.startsWith("Build Game - ")) {
      return title.replace("Build Game - ", "Build ");
    }
    return title;
  }

  private String releaseActionLabel(BuildTaskSelection selection) {
    String title = selection.title();
    if (title.startsWith("Release Game - ")) {
      return title.replace("Release Game - ", "Release ");
    }
    return title;
  }

  private void buildAllTargets() {
    if (!canBuild()) return;
    BuildTaskSelection selection = switch (selectedPackageMode()) {
      case PORTABLE_ZIP -> new BuildTaskSelection("assembleJvnGamePortable", "Build Game - All Portable Targets");
      case BUNDLED_RUNTIME_ZIP -> new BuildTaskSelection("assembleJvnGameBundledRuntime", "Build Game - All Desktop Bundles");
      case NATIVE_PACKAGE -> null;
    };
    if (selection == null) return;
    buildTask(selection.taskName(), selection.title());
  }

  private void runPreflight() {
    if (!canBuild()) return;
    List<String> args = buildGradleArgs();
    if (onBuildRequested != null) {
      onBuildRequested.accept(new BuildRequest("preflightJvnGameBuild", args.toArray(String[]::new), "Preflight Game Build"));
    }
    statusLabel.setText("Started build preflight. JSON and Markdown reports will be written under build/reports/jvn-game-build/.");
    setNoteTone(statusLabel, "status");
  }

  private void runDependencyScan() {
    if (projectRoot == null || !projectRoot.isDirectory()) {
      statusLabel.setText("Dependency scan unavailable: open a JVN game project first.");
      setNoteTone(statusLabel, "error");
      return;
    }
    if (dependencyScanTask != null && dependencyScanTask.isRunning()) {
      statusLabel.setText("Dependency scan is already running.");
      setNoteTone(statusLabel, "status");
      return;
    }

    File scanRoot = projectRoot;
    dependencyReport = null;
    setDependencyBadges(0, 0, 0);
    dependencySummaryLabel.setText("Scanning " + scanRoot.getAbsolutePath() + "...");
    renderDependencyBusy();
    Task<ProjectDependencyValidator.Report> task = new Task<>() {
      @Override
      protected ProjectDependencyValidator.Report call() {
        return ProjectDependencyValidator.inspect(scanRoot.toPath());
      }
    };
    dependencyScanTask = task;
    refreshUtilityButtons(validateForm());
    refreshWorkflowNotices(validateForm());
    task.setOnSucceeded(e -> {
      if (dependencyScanTask != task) return;
      dependencyScanTask = null;
      dependencyReport = task.getValue();
      renderDependencyReport(dependencyReport);
      statusLabel.setText(dependencyStatusText(dependencyReport));
      setNoteTone(statusLabel, dependencyReport.errorCount() > 0 ? "error"
          : dependencyReport.warningCount() > 0 ? "warn" : "ok");
      refreshUtilityButtons(validateForm());
      refreshWorkflowNotices(validateForm());
    });
    task.setOnFailed(e -> {
      if (dependencyScanTask != task) return;
      dependencyScanTask = null;
      Throwable ex = task.getException();
      dependencyReport = null;
      dependencySummaryLabel.setText("Dependency scan failed.");
      renderDependencyPlaceholder("Could not scan dependencies: " + (ex == null ? "unknown failure" : ex.getMessage()));
      statusLabel.setText("Dependency scan failed: " + (ex == null ? "unknown failure" : ex.getMessage()));
      setNoteTone(statusLabel, "error");
      refreshUtilityButtons(validateForm());
      refreshWorkflowNotices(validateForm());
    });
    Thread thread = new Thread(task, "jvn-build-dependency-scan");
    thread.setDaemon(true);
    thread.start();
  }

  private void runDependencyScanInConsole() {
    if (projectRoot == null || !projectRoot.isDirectory()) {
      statusLabel.setText("Dependency scan unavailable: open a JVN game project first.");
      setNoteTone(statusLabel, "error");
      return;
    }
    List<String> args = dependencyScanArgs();
    if (onBuildRequested != null) {
      onBuildRequested.accept(new BuildRequest("validateJvnGameDependencies", args.toArray(String[]::new), "Validate Game Dependencies"));
    }
    statusLabel.setText("Started dependency scan. Missing assets, bad links, timeline issues, packaging blockers, and unused media will appear in the run console.");
    setNoteTone(statusLabel, "status");
  }

  private void runReleaseProfile() {
    if (!canBuild()) return;
    BuildTaskSelection selection = releaseTaskForSelection();
    if (selection == null) {
      statusLabel.setText("Release profile needs a single selected artifact, not an all-target build.");
      setNoteTone(statusLabel, "warn");
      return;
    }
    List<String> args = buildGradleArgs();
    if (onBuildRequested != null) {
      onBuildRequested.accept(new BuildRequest(selection.taskName(), args.toArray(String[]::new), selection.title()));
    }
    statusLabel.setText("Started " + selection.title() + ".");
    setNoteTone(statusLabel, "status");
  }

  private void runPackagedArtifactSmokeTest() {
    if (!canBuild() || onBuildRequested == null) return;
    List<String> args = buildGradleArgs();
    onBuildRequested.accept(new BuildRequest(
        "smokeTestJvnGameRelease", args.toArray(String[]::new), "Verify Packaged Game"));
    statusLabel.setText("Started packaged-artifact verification.");
    setNoteTone(statusLabel, "status");
  }

  private void writeSignedUpdateBundle() {
    if (!canBuild() || onBuildRequested == null) return;
    List<String> args = buildGradleArgs();
    onBuildRequested.accept(new BuildRequest(
        "writeJvnGameUpdateBundle", args.toArray(String[]::new), "Build Signed Game Update"));
    statusLabel.setText("Started signed update-catalog generation.");
    setNoteTone(statusLabel, "status");
  }

  private void writeStoreBundle() {
    if (!canBuild() || onBuildRequested == null) return;
    List<String> args = buildGradleArgs();
    onBuildRequested.accept(new BuildRequest(
        "assembleJvnGameStoreBundle", args.toArray(String[]::new), "Build Game Store Bundle"));
    statusLabel.setText("Started store upload-bundle generation.");
    setNoteTone(statusLabel, "status");
  }

  private void buildTask(String taskName, String title) {
    if (!canBuild()) return;
    List<String> args = buildGradleArgs();
    if (onBuildRequested != null) {
      onBuildRequested.accept(new BuildRequest(taskName, args.toArray(String[]::new), title));
    }
    statusLabel.setText("Started " + title + ".");
    setNoteTone(statusLabel, "status");
  }

  private void cleanBuildArtifacts() {
    if (workspaceRoot == null || onBuildRequested == null) return;
    onBuildRequested.accept(new BuildRequest("cleanJvnGameDistributions", new String[0], "Clean Game Build Artifacts"));
    statusLabel.setText("Started build artifact cleanup.");
    setNoteTone(statusLabel, "status");
    refreshArtifactInventory();
  }

  private boolean canBuild() {
    ValidationResult result = refreshFormState();
    if (result.errors().isEmpty()) return true;
    statusLabel.setText("Build unavailable: " + result.errors().get(0));
    setNoteTone(statusLabel, "error");
    return false;
  }

  private List<String> buildGradleArgs() {
    List<String> args = new ArrayList<>();
    if (offlineModeCheck.isSelected()) args.add("--offline");
    if (strictDependencies.isSelected()) args.add("-PjvnFailOnWarning=true");
    if (stacktrace.isSelected()) args.add("--stacktrace");
    args.add("-PjvnGameProject=" + projectRoot.getAbsolutePath());
    if (selectedPackageMode() != PackageMode.NATIVE_PACKAGE) {
      if (normalizedArchive.isSelected()) args.add("-PjvnNormalizeArchive=true");
      if (storeArchive.isSelected()) args.add("-PjvnZipCompression=store");
    }
    if (selectedPackageMode() == PackageMode.BUNDLED_RUNTIME_ZIP && !"Automatic".equals(runtimeImage.getValue())) {
      args.add("-PjvnBundledRuntimeImageType=" + runtimeImage.getValue().toLowerCase(Locale.ROOT));
    }
    args.add("-PjvnPackageMode=" + selectedPackageMode().gradleToken());
    TargetChoice target = targetBox.getValue();
    if (target != null) args.add("-PjvnGameTarget=" + target.outputToken());
    String name = nameField.getText() == null ? "" : nameField.getText().trim();
    if (!name.isBlank()) args.add("-PjvnGameName=" + name);
    String version = versionField.getText() == null ? "" : versionField.getText().trim();
    if (!version.isBlank()) args.add("-PjvnGameVersion=" + version);
    String profile = selectedReleaseProfile();
    if (!profile.isBlank()) args.add("-PjvnReleaseProfile=" + profile);
    String variant = selectedPackageVariant();
    if (!variant.isBlank()) args.add("-PjvnPackageVariant=" + variant);
    if (selectedPackageMode() == PackageMode.BUNDLED_RUNTIME_ZIP && refreshRuntimeCheck.isSelected()) {
      args.add("-PjvnRefreshBundledRuntime=true");
    }
    if (selectedPackageMode() == PackageMode.NATIVE_PACKAGE) {
      NativeTypeChoice nativeType = nativeTypeBox.getValue();
      if (nativeType != null && !nativeType.token().isBlank() && !nativeType.token().startsWith("unsupported")) {
        args.add("-PjvnNativePackageType=" + nativeType.token());
      }
    }
    if (customOutputDir != null) {
      args.add("-PjvnBuildOutputDir=" + customOutputDir.getAbsolutePath());
    }
    return args;
  }

  private List<String> dependencyScanArgs() {
    List<String> args = new ArrayList<>();
    if (offlineModeCheck.isSelected()) args.add("--offline");
    if (strictDependencies.isSelected()) args.add("-PjvnFailOnWarning=true");
    if (stacktrace.isSelected()) args.add("--stacktrace");
    args.add("-PjvnGameProject=" + projectRoot.getAbsolutePath());
    args.add("-PjvnShowInfo=true");
    return args;
  }

  private void renderDependencyBusy() {
    ProgressIndicator spinner = new ProgressIndicator();
    spinner.setMaxSize(24, 24);
    Label busy = new Label("Scanning project dependency graph...");
    busy.getStyleClass().add("build-publisher-copy");
    HBox row = new HBox(10, spinner, busy);
    row.setAlignment(Pos.CENTER_LEFT);
    row.getStyleClass().add("build-publisher-dependency-placeholder");
    dependencyReportBox.getChildren().setAll(row);
  }

  private void renderDependencyPlaceholder(String message) {
    Label placeholder = new Label(message == null || message.isBlank() ? "No dependency report yet." : message);
    placeholder.setWrapText(true);
    placeholder.getStyleClass().addAll("build-publisher-dependency-placeholder", "build-publisher-copy");
    dependencyReportBox.getChildren().setAll(placeholder);
  }

  private void renderDependencyReport(ProjectDependencyValidator.Report report) {
    if (report == null) {
      setDependencyBadges(0, 0, 0);
      dependencySummaryLabel.setText("Dependency report: not scanned yet.");
      renderDependencyPlaceholder("Run Scan Dependencies to inspect the current game project.");
      return;
    }

    setDependencyBadges(report.errorCount(), report.warningCount(), report.infoCount());
    dependencySummaryLabel.setText("Dependency scan complete for " + report.projectRoot()
        + " — " + report.errorCount() + " error(s), "
        + report.warningCount() + " warning(s), " + report.infoCount() + " info.");

    dependencyReportBox.getChildren().clear();
    if (report.findings().isEmpty()) {
      Label ok = new Label("No dependency issues found. Project assets and shipping references look clean.");
      ok.setWrapText(true);
      ok.getStyleClass().addAll("build-publisher-dependency-placeholder", "build-publisher-note", "build-publisher-note-ok");
      dependencyReportBox.getChildren().add(ok);
      return;
    }

    Map<ProjectDependencyValidator.Severity, List<ProjectDependencyValidator.Finding>> grouped =
        new EnumMap<>(ProjectDependencyValidator.Severity.class);
    for (ProjectDependencyValidator.Severity severity : ProjectDependencyValidator.Severity.values()) {
      grouped.put(severity, new ArrayList<>());
    }
    for (ProjectDependencyValidator.Finding finding : report.findings()) {
      grouped.get(finding.severity()).add(finding);
    }

    addDependencyGroup(ProjectDependencyValidator.Severity.ERROR, grouped.get(ProjectDependencyValidator.Severity.ERROR));
    addDependencyGroup(ProjectDependencyValidator.Severity.WARNING, grouped.get(ProjectDependencyValidator.Severity.WARNING));
    addDependencyGroup(ProjectDependencyValidator.Severity.INFO, grouped.get(ProjectDependencyValidator.Severity.INFO));
  }

  private void addDependencyGroup(
      ProjectDependencyValidator.Severity severity,
      List<ProjectDependencyValidator.Finding> findings
  ) {
    if (findings == null || findings.isEmpty()) return;
    VBox group = new VBox(7);
    group.getStyleClass().addAll("build-publisher-dependency-group", dependencyGroupStyle(severity));
    Label title = new Label(dependencyGroupTitle(severity) + " (" + findings.size() + ")");
    title.getStyleClass().add("build-publisher-dependency-group-title");
    group.getChildren().add(title);
    for (ProjectDependencyValidator.Finding finding : findings) {
      group.getChildren().add(buildDependencyFindingRow(finding));
    }
    dependencyReportBox.getChildren().add(group);
  }

  private Node buildDependencyFindingRow(ProjectDependencyValidator.Finding finding) {
    Label severityBadge = new Label(finding.severity().name());
    styleBadge(severityBadge);
    switch (finding.severity()) {
      case ERROR -> setBadgeTone(severityBadge, "error");
      case WARNING -> setBadgeTone(severityBadge, "warn");
      case INFO -> setBadgeTone(severityBadge, "default");
    }

    Label category = new Label(finding.category());
    category.getStyleClass().addAll("build-publisher-finding-category", "build-publisher-path");
    Label location = new Label(finding.location());
    location.getStyleClass().addAll("build-publisher-finding-location", "build-publisher-path");
    location.setWrapText(true);
    HBox meta = new HBox(6, severityBadge, category, location);
    meta.setAlignment(Pos.CENTER_LEFT);

    Label message = new Label(finding.message());
    message.setWrapText(true);
    message.getStyleClass().add("build-publisher-finding-message");
    VBox text = new VBox(4, meta, message);
    if (finding.target() != null && !finding.target().isBlank()) {
      Label target = new Label("Target: " + finding.target());
      target.setWrapText(true);
      target.getStyleClass().addAll("build-publisher-finding-target", "build-publisher-path");
      text.getChildren().add(target);
    }

    Button open = miniButton("Open");
    File openTarget = dependencyOpenTarget(finding);
    open.setDisable(openTarget == null);
    open.setOnAction(e -> openDependencyFinding(finding));
    Button copy = miniButton("Copy");
    copy.setOnAction(e -> copyDependencyFinding(finding));
    HBox actions = new HBox(6, open, copy);
    actions.setAlignment(Pos.TOP_RIGHT);

    HBox row = new HBox(10, text, actions);
    HBox.setHgrow(text, Priority.ALWAYS);
    row.getStyleClass().addAll("build-publisher-finding-row", dependencyRowStyle(finding.severity()));
    return row;
  }

  private Button miniButton(String text) {
    Button button = button(text, ButtonTone.SECONDARY, false);
    button.getStyleClass().add("build-publisher-mini-button");
    button.setMinHeight(26);
    return button;
  }

  private void setDependencyBadges(int errors, int warnings, int info) {
    dependencyErrorsBadgeLabel.setText("Errors " + errors);
    dependencyWarningsBadgeLabel.setText("Warnings " + warnings);
    dependencyInfoBadgeLabel.setText("Info " + info);
  }

  private String dependencyStatusText(ProjectDependencyValidator.Report report) {
    if (report == null) return "Dependency scan did not return a report.";
    if (report.errorCount() > 0) {
      return "Dependency scan found " + report.errorCount() + " packaging blocker(s).";
    }
    if (report.warningCount() > 0) {
      return "Dependency scan found " + report.warningCount() + " warning(s).";
    }
    if (report.infoCount() > 0) {
      return "Dependency scan found " + report.infoCount() + " cleanup note(s), with no blockers.";
    }
    return "Dependency scan passed with no findings.";
  }

  private String dependencyGroupTitle(ProjectDependencyValidator.Severity severity) {
    return switch (severity) {
      case ERROR -> "Packaging Blockers";
      case WARNING -> "Warnings";
      case INFO -> "Cleanup Notes";
    };
  }

  private String dependencyGroupStyle(ProjectDependencyValidator.Severity severity) {
    return switch (severity) {
      case ERROR -> "build-publisher-dependency-group-error";
      case WARNING -> "build-publisher-dependency-group-warning";
      case INFO -> "build-publisher-dependency-group-info";
    };
  }

  private String dependencyRowStyle(ProjectDependencyValidator.Severity severity) {
    return switch (severity) {
      case ERROR -> "build-publisher-finding-row-error";
      case WARNING -> "build-publisher-finding-row-warning";
      case INFO -> "build-publisher-finding-row-info";
    };
  }

  private void clearDependencyReport() {
    if (dependencyScanTask != null && dependencyScanTask.isRunning()) {
      dependencyScanTask.cancel();
    }
    dependencyScanTask = null;
    dependencyReport = null;
    setDependencyBadges(0, 0, 0);
    dependencySummaryLabel.setText("Dependency report: not scanned yet.");
    renderDependencyPlaceholder("Run Scan Dependencies to inspect missing media, scripts, menus, stage presets, timelines, packaging blockers, and unused media.");
    refreshUtilityButtons(validateForm());
    refreshWorkflowNotices(validateForm());
  }

  private void copyDependencyReport() {
    if (dependencyReport == null) {
      statusLabel.setText("No dependency report to copy yet.");
      setNoteTone(statusLabel, "warn");
      return;
    }
    ClipboardContent content = new ClipboardContent();
    content.putString(formatDependencyReport(dependencyReport));
    Clipboard.getSystemClipboard().setContent(content);
    statusLabel.setText("Copied dependency report.");
    setNoteTone(statusLabel, "status");
  }

  private void copyDependencyFinding(ProjectDependencyValidator.Finding finding) {
    ClipboardContent content = new ClipboardContent();
    content.putString(formatDependencyFinding(finding));
    Clipboard.getSystemClipboard().setContent(content);
    statusLabel.setText("Copied dependency finding.");
    setNoteTone(statusLabel, "status");
  }

  private String formatDependencyReport(ProjectDependencyValidator.Report report) {
    StringBuilder out = new StringBuilder();
    out.append("JVN dependency validation\n");
    out.append("Project: ").append(report.projectRoot()).append('\n');
    out.append("Findings: ").append(report.errorCount()).append(" error(s), ")
        .append(report.warningCount()).append(" warning(s), ")
        .append(report.infoCount()).append(" info\n");
    for (ProjectDependencyValidator.Finding finding : report.findings()) {
      out.append(formatDependencyFinding(finding)).append('\n');
    }
    return out.toString();
  }

  private String formatDependencyFinding(ProjectDependencyValidator.Finding finding) {
    StringBuilder out = new StringBuilder();
    out.append('[').append(finding.severity()).append("] ")
        .append(finding.category()).append(' ')
        .append(finding.location()).append(" - ")
        .append(finding.message());
    if (finding.target() != null && !finding.target().isBlank()) {
      out.append(" -> ").append(finding.target());
    }
    return out.toString();
  }

  private void openDependencyFinding(ProjectDependencyValidator.Finding finding) {
    File target = dependencyOpenTarget(finding);
    if (target == null) {
      statusLabel.setText("No local file or folder could be resolved for this finding.");
      setNoteTone(statusLabel, "warn");
      return;
    }
    if (EditorPathExplorer.show(getScene() == null ? null : getScene().getWindow(), target)) {
      statusLabel.setText("Revealed: " + target.getAbsolutePath());
      setNoteTone(statusLabel, "status");
    } else {
      statusLabel.setText("Could not reveal dependency target.");
      setNoteTone(statusLabel, "error");
    }
  }

  private File dependencyOpenTarget(ProjectDependencyValidator.Finding finding) {
    if (projectRoot == null || !projectRoot.isDirectory() || finding == null) return null;
    File location = dependencyLocationFile(finding.location());
    if (location != null) return location;
    File target = dependencyProjectPath(finding.target(), true);
    if (target != null) return target;
    if ("menu".equals(finding.category())) {
      File menuDir = new File(projectRoot, "config/menu");
      return menuDir.exists() ? menuDir : projectRoot;
    }
    return null;
  }

  private File dependencyLocationFile(String location) {
    if (location == null || location.isBlank()) return null;
    String raw = location.trim();
    if (raw.startsWith("menu:")) {
      File menuDir = new File(projectRoot, "config/menu");
      return menuDir.exists() ? menuDir : null;
    }
    if (raw.startsWith("dialogue")) {
      File dialogue = new File(projectRoot, "config/ui/dialogue.layout");
      if (dialogue.isFile()) return dialogue;
    }
    int colon = raw.indexOf(':');
    if (colon > 0) raw = raw.substring(0, colon);
    int hash = raw.indexOf('#');
    if (hash > 0) raw = raw.substring(0, hash);
    return dependencyProjectPath(raw, false);
  }

  private File dependencyProjectPath(String raw, boolean allowNearestParent) {
    if (raw == null || raw.isBlank() || projectRoot == null) return null;
    String cleaned = raw.trim().replace('\\', '/');
    int arrow = cleaned.indexOf(" -> ");
    if (arrow >= 0) cleaned = cleaned.substring(0, arrow).trim();
    while (cleaned.startsWith("./")) cleaned = cleaned.substring(2);
    while (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
    if (!looksLikeProjectPath(cleaned)) return null;
    File root = projectRoot.getAbsoluteFile();
    File candidate = new File(root, cleaned).getAbsoluteFile();
    if (!isWithin(root, candidate)) return null;
    if (candidate.exists()) return candidate;
    if (!allowNearestParent) return null;
    File cursor = candidate.getParentFile();
    while (cursor != null && isWithin(root, cursor)) {
      if (cursor.exists()) return cursor;
      cursor = cursor.getParentFile();
    }
    return null;
  }

  private boolean looksLikeProjectPath(String raw) {
    if (raw == null || raw.isBlank()) return false;
    String value = raw.toLowerCase(Locale.ROOT);
    return value.contains("/")
        || value.equals("jvn.project")
        || value.endsWith(".vns")
        || value.endsWith(".jes")
        || value.endsWith(".png")
        || value.endsWith(".jpg")
        || value.endsWith(".jpeg")
        || value.endsWith(".webp")
        || value.endsWith(".gif")
        || value.endsWith(".svg")
        || value.endsWith(".ogg")
        || value.endsWith(".wav")
        || value.endsWith(".mp3")
        || value.endsWith(".flac")
        || value.endsWith(".ttf")
        || value.endsWith(".otf")
        || value.endsWith(".stagepreset")
        || value.endsWith(".properties")
        || value.endsWith(".menu")
        || value.endsWith(".layout")
        || value.endsWith(".style");
  }

  private boolean isWithin(File root, File candidate) {
    try {
      Path rootPath = root.toPath().toRealPath();
      Path candidatePath = candidate.exists()
          ? candidate.toPath().toRealPath()
          : candidate.toPath().toAbsolutePath().normalize();
      return candidatePath.startsWith(rootPath);
    } catch (Exception ex) {
      return false;
    }
  }

  private void copyCommand() {
    if (!canBuild()) return;
    BuildTaskSelection selection = buildTaskForSelection();
    if (selection == null) return;
    ClipboardContent content = new ClipboardContent();
    content.putString(BuildCliFormatter.buildCliCommand(selection.taskName(), buildGradleArgs()));
    Clipboard.getSystemClipboard().setContent(content);
    statusLabel.setText("Copied build command.");
    setNoteTone(statusLabel, "status");
  }

  private void copyShipCommand() {
    if (!canBuild()) return;
    ClipboardContent content = new ClipboardContent();
    content.putString(BuildCliFormatter.buildCliCommand("assembleJvnGameRelease", buildGradleArgs()));
    Clipboard.getSystemClipboard().setContent(content);
    statusLabel.setText("Copied ship command.");
    setNoteTone(statusLabel, "status");
  }

  private void copyPublishNotes() {
    StringBuilder notes = new StringBuilder();
    notes.append("Game: ").append(nameField.getText()).append('\n');
    notes.append("Version: ").append(versionField.getText()).append('\n');
    notes.append("Format: ").append(selectedPackageMode().label).append('\n');
    notes.append("Release profile: ").append(selectedReleaseProfile()).append('\n');
    notes.append("Package variant: ").append(selectedPackageVariant()).append('\n');
    notes.append("Artifacts: ").append(outputLabel.getText().replace("Output: ", "")).append('\n');
    switch (selectedPackageMode()) {
      case PORTABLE_ZIP -> {
        notes.append("Runtime requirement: Java 21 or newer on the player machine.\n");
        notes.append("Targets: windows-x64, linux-x64, macos-x64, macos-aarch64.\n");
      }
      case BUNDLED_RUNTIME_ZIP -> {
        notes.append("Runtime requirement: bundled target runtime, no external Java install needed.\n");
        notes.append("Targets: windows-x64, linux-x64, macos-x64, macos-aarch64.\n");
        notes.append("Runtime cache: the first build for a target downloads and verifies a prebuilt runtime.\n");
      }
      case NATIVE_PACKAGE -> {
        NativeTypeChoice nativeType = nativeTypeBox.getValue();
        notes.append("Native package type: ").append(nativeType == null ? "(unset)" : nativeType.token()).append('\n');
        notes.append("Runtime requirement: bundled runtime image, no external Java install needed.\n");
      }
    }
    ClipboardContent content = new ClipboardContent();
    content.putString(notes.toString());
    Clipboard.getSystemClipboard().setContent(content);
    statusLabel.setText("Copied publish notes.");
    setNoteTone(statusLabel, "status");
  }

  private void revealBuilds() {
    File outDir = buildDistributionsDir();
    if (!outDir.exists()) outDir.mkdirs();
    if (EditorPathExplorer.show(getScene() == null ? null : getScene().getWindow(), outDir)) {
      statusLabel.setText("Revealed build output folder.");
      setNoteTone(statusLabel, "status");
    } else {
      statusLabel.setText("Could not reveal build folder.");
      setNoteTone(statusLabel, "error");
    }
  }

  private void revealReleaseManifest() {
    File manifest = releaseManifestJsonFile();
    File folder = manifest.getParentFile();
    if (folder == null || !folder.isDirectory()) {
      statusLabel.setText("No release manifest folder yet. Run Ship Build first.");
      setNoteTone(statusLabel, "warn");
      return;
    }
    if (EditorPathExplorer.show(getScene() == null ? null : getScene().getWindow(), manifest)) {
      statusLabel.setText("Revealed release manifest.");
      setNoteTone(statusLabel, "status");
    } else {
      statusLabel.setText("Could not reveal release manifest.");
      setNoteTone(statusLabel, "error");
    }
  }

  private void copyReleaseManifestPath() {
    File manifest = releaseManifestJsonFile();
    if (!manifest.isFile()) {
      statusLabel.setText("No release manifest yet. Run Ship Build first.");
      setNoteTone(statusLabel, "warn");
      return;
    }
    ClipboardContent content = new ClipboardContent();
    content.putString(manifest.getAbsolutePath());
    Clipboard.getSystemClipboard().setContent(content);
    statusLabel.setText("Copied release manifest path.");
    setNoteTone(statusLabel, "status");
  }

  private void refreshArtifactInventory() {
    if (artifactInventoryLabel == null) return;
    String manifestNote = releaseManifestJsonFile().isFile()
        ? "\nRelease manifest: " + releaseManifestJsonFile().getAbsolutePath()
        : "\nRelease manifest: not written yet";
    artifactInventoryLabel.setText("Artifacts: "
        + BuildArtifactService.formatArtifactInventory(BuildArtifactService.summarizeArtifacts(buildDistributionsDir()))
        + manifestNote);
  }





  private void openProjectFolder() {
    if (projectRoot == null || !projectRoot.isDirectory()) return;
    if (EditorPathExplorer.show(getScene() == null ? null : getScene().getWindow(), projectRoot)) {
      statusLabel.setText("Revealed project folder.");
      setNoteTone(statusLabel, "status");
    } else {
      statusLabel.setText("Could not reveal project folder.");
      setNoteTone(statusLabel, "error");
    }
  }

  private void openReleaseConfig() {
    File config = findReleaseConfig(projectRoot);
    if (config == null) {
      config = createReleaseConfig();
      if (config == null) return;
    }
    if (EditorPathExplorer.show(getScene() == null ? null : getScene().getWindow(), config)) {
      statusLabel.setText("Revealed release config.");
      setNoteTone(statusLabel, "status");
    } else {
      statusLabel.setText("Could not reveal release config.");
      setNoteTone(statusLabel, "error");
    }
  }

  private File createReleaseConfig() {
    if (projectRoot == null || !projectRoot.isDirectory()) {
      statusLabel.setText("Release config unavailable: open a game project first.");
      setNoteTone(statusLabel, "error");
      return null;
    }
    File config = new File(projectRoot, "config/release/jvn-release.properties");
    try {
      Files.createDirectories(config.toPath().getParent());
      if (!config.exists()) {
        Files.writeString(config.toPath(), defaultReleaseConfigText(nameField.getText()));
      }
      releaseProfileBox.getItems().setAll(availableReleaseProfiles(projectRoot));
      setReleaseProfileSelection("release");
      packageVariantBox.getItems().setAll(availablePackageVariants(projectRoot));
      setPackageVariantSelection(defaultPackageVariant(projectRoot));
      releaseConfigLabel.setText(releaseConfigText(projectRoot));
      statusLabel.setText("Created release profile: " + config.getAbsolutePath());
      setNoteTone(statusLabel, "ok");
      refreshFormState();
      return config;
    } catch (Exception ex) {
      statusLabel.setText("Could not create release config: " + ex.getMessage());
      setNoteTone(statusLabel, "error");
      return null;
    }
  }

  private void chooseGameIcon() {
    if (projectRoot == null || !projectRoot.isDirectory()) return;
    File config = findReleaseConfig(projectRoot);
    if (config == null) config = createReleaseConfig();
    if (config == null) return;
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Choose Game Package Icon");
    chooser.getExtensionFilters().setAll(
        new FileChooser.ExtensionFilter("High-resolution PNG icon", "*.png"),
        new FileChooser.ExtensionFilter("All files", "*.*"));
    File selected = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
    if (selected == null) return;
    installGameIcon(selected);
  }

  void installGameIcon(File selected) {
    try {
      File config = findReleaseConfig(projectRoot);
      if (config == null) config = createReleaseConfig();
      if (config == null) return;
      GamePackageIconService.IconResult icon =
          GamePackageIconService.install(selected, projectRoot, currentHostOs());
      String relativePath = projectRoot.toPath().relativize(icon.platformIcon().toPath())
          .toString().replace(File.separatorChar, '/');
      updateReleaseConfigProperty(config, releaseProfilePropertyKey("icon"), relativePath);
      gameIconField.setText(relativePath);
      statusLabel.setText("Generated " + relativePath + " from " + icon.width() + "x" + icon.height() + " PNG artwork.");
      setNoteTone(statusLabel, "ok");
      refreshFormState();
    } catch (Exception ex) {
      statusLabel.setText("Could not set game icon: " + ex.getMessage());
      setNoteTone(statusLabel, "error");
    }
  }

  Button preflightButtonForTest() {
    return preflightButton;
  }

  Button dependencyScanButtonForTest() {
    return dependencyScanButton;
  }

  FlowPane validationActionsRowForTest() {
    return validationActionsRow;
  }

  FlowPane nativeReleaseActionsRowForTest() {
    return nativeReleaseActionsRow;
  }

  VBox nativeReleaseBoxForTest() {
    return nativeReleaseBox;
  }

  Label nativeReleaseSummaryForTest() {
    return nativeReleaseSummaryLabel;
  }

  TextField gameIconFieldForTest() {
    return gameIconField;
  }

  void selectNativeModeForTest() {
    formatBox.setValue(PackageMode.NATIVE_PACKAGE);
  }

  void selectPortableModeForTest() {
    formatBox.setValue(PackageMode.PORTABLE_ZIP);
  }

  private void clearGameIcon() {
    File config = findReleaseConfig(projectRoot);
    if (config == null) return;
    try {
      updateReleaseConfigProperty(config, releaseProfilePropertyKey("icon"), null);
      gameIconField.clear();
      statusLabel.setText("Game icon removed from the selected release profile.");
      setNoteTone(statusLabel, "status");
      refreshFormState();
    } catch (Exception ex) {
      statusLabel.setText("Could not clear game icon: " + ex.getMessage());
      setNoteTone(statusLabel, "error");
    }
  }

  private void revealGameIcon() {
    String icon = releaseProfileValue(projectRoot, selectedReleaseProfile(), "icon");
    File file = resolveReleaseProfileFile(icon);
    if (!file.isFile()) {
      statusLabel.setText("The configured game icon could not be found.");
      setNoteTone(statusLabel, "error");
      return;
    }
    if (EditorPathExplorer.show(getScene() == null ? null : getScene().getWindow(), file)) {
      statusLabel.setText("Revealed game icon.");
      setNoteTone(statusLabel, "status");
    } else {
      statusLabel.setText("Could not reveal game icon.");
      setNoteTone(statusLabel, "error");
    }
  }

  private File resolveReleaseProfileFile(String path) {
    if (path == null || path.isBlank()) return new File("");
    File file = new File(path);
    return file.isAbsolute() ? file : new File(projectRoot, path);
  }

  private void copyNativeReleaseChecklist() {
    StringBuilder text = new StringBuilder();
    text.append(nameField.getText()).append(" native release checklist\n\n");
    text.append(nativeReleaseSummaryLabel.getText()).append("\n\n");
    File config = findReleaseConfig(projectRoot);
    text.append("Release settings: ")
        .append(config == null ? "not created" : config.getAbsolutePath())
        .append('\n');
    BuildTaskSelection release = releaseTaskForSelection();
    if (release != null) {
      text.append("Release command: ")
          .append(BuildCliFormatter.buildCliCommand(release.taskName(), buildGradleArgs()))
          .append('\n');
    }
    text.append("Artifacts: ").append(buildDistributionsDir().getAbsolutePath()).append('\n');
    ClipboardContent content = new ClipboardContent();
    content.putString(text.toString());
    Clipboard.getSystemClipboard().setContent(content);
    statusLabel.setText("Copied the native release checklist.");
    setNoteTone(statusLabel, "ok");
  }

  private String releaseProfilePropertyKey(String key) {
    return "profile." + selectedReleaseProfile() + "." + key;
  }

  static void updateReleaseConfigProperty(File config, String key, @Nullable String value) throws Exception {
    List<String> lines = Files.exists(config.toPath()) ? Files.readAllLines(config.toPath()) : new ArrayList<>();
    String prefix = key + "=";
    int existing = -1;
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).stripLeading().startsWith(prefix)) {
        existing = i;
        break;
      }
    }
    if (value == null || value.isBlank()) {
      if (existing >= 0) lines.remove(existing);
    } else if (existing >= 0) {
      lines.set(existing, prefix + value);
    } else {
      if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) lines.add("");
      lines.add(prefix + value);
    }
    Files.write(config.toPath(), lines);
  }

  private String nativeIconExtension() {
    return switch (currentHostOs()) {
      case "macos" -> ".icns";
      case "windows" -> ".ico";
      default -> ".png";
    };
  }

  private static String currentHostOs() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("mac")) return "macos";
    if (os.contains("win")) return "windows";
    return "linux";
  }

  private String releaseProfileValue(File root, String profile, String key) {
    return releaseProfileValue(loadReleaseConfig(root), profile, key);
  }

  private static String releaseProfileValue(Properties properties, String profile, String key) {
    for (String candidate : List.of("profile." + profile + "." + key, "profile.default." + key, key)) {
      String value = properties.getProperty(candidate, "").trim();
      if (!value.isBlank()) return value;
    }
    return "";
  }

  private static boolean releaseProfileFlag(Properties properties, String profile, String key) {
    String value = releaseProfileValue(properties, profile, key).toLowerCase(Locale.ROOT);
    return !value.isBlank() && !List.of("0", "false", "no", "off").contains(value);
  }

  static String defaultReleaseConfigText(String gameName) {
    String displayName = gameName == null || gameName.trim().isBlank() ? "JVN Game" : gameName.trim();
    return """
        # JVN desktop shipping profile
        defaultProfile=release
        defaultVariant=standard

        # Package variants can exclude authoring or edition-specific files.
        # variant.demo.exclude.1=assets/bonus/**
        # variant.steam.exclude.1=store/itch/**
        # variant.steam.windows.exclude.1=store/macos/**

        profile.release.vendor=Your Studio
        profile.release.description=%s
        profile.release.aboutUrl=https://example.com
        profile.release.licenseFile=LICENSE

        # Optional platform icons (paths are relative to the game project).
        # profile.release.icon=packaging/icon.png
        # Use icon.icns on macOS, icon.ico on Windows, and icon.png on Linux.

        # macOS signing and notarization. Create the notary profile with:
        # xcrun notarytool store-credentials JVN_NOTARY
        profile.release.mac.packageIdentifier=com.example.game
        profile.release.mac.sign=false
        # profile.release.mac.signingIdentity=Developer ID Application: Your Studio
        # profile.release.mac.installerSignIdentity=Developer ID Installer: Your Studio
        profile.release.mac.notarize=false
        # profile.release.mac.notarytoolProfile=JVN_NOTARY

        # Windows Authenticode signing. Use either certificateFile or subjectName.
        profile.release.win.sign=false
        # profile.release.win.certificateFile=packaging/signing-certificate.pfx
        # profile.release.win.certificatePasswordEnv=JVN_CERTIFICATE_PASSWORD
        # profile.release.win.subjectName=Your Studio

        # Linux package integration.
        profile.release.linux.shortcut=true
        # profile.release.linux.appCategory=Game
        # profile.release.linux.debMaintainer=you@example.com

        # Signed full-artifact update catalog (PKCS#8 private key).
        profile.release.update.enabled=false
        # profile.release.update.baseUrl=https://downloads.example.com/game
        # profile.release.update.privateKey=packaging/update-private-key.pem
        # profile.release.update.publicKey=packaging/update-public-key.pem

        # Store upload layout: generic, itch, or steam.
        profile.release.store.preset=generic
        # profile.release.store.itch.project=account/game
        # profile.release.store.itch.channelPattern={target}
        # profile.release.store.steam.appId=000000
        # profile.release.store.steam.depotId=000001

        # Optional publish command. Supported placeholders include artifact, mode,
        # target, version, gameName, projectDir, and profile.
        # profile.release.publish.command.1=butler push "{artifact}" account/game:channel
        """.formatted(displayName + " built with JVN.");
  }

  private void revealRuntimeCache() {
    File cacheDir = bundledRuntimeDownloadDir();
    File extractDir = bundledRuntimeExtractDir();
    if (!cacheDir.exists()) cacheDir.mkdirs();
    if (!extractDir.exists()) extractDir.mkdirs();
    if (EditorPathExplorer.show(getScene() == null ? null : getScene().getWindow(), cacheDir)) {
      statusLabel.setText("Revealed runtime cache folder.");
      setNoteTone(statusLabel, "status");
    } else {
      statusLabel.setText("Could not reveal runtime cache.");
      setNoteTone(statusLabel, "error");
    }
  }

  private void clearRuntimeCache() {
    if (workspaceRoot == null || onBuildRequested == null) return;
    onBuildRequested.accept(new BuildRequest("clearJvnBundledRuntimeCache", new String[0], "Clear Desktop Runtime Cache"));
    statusLabel.setText("Started runtime cache cleanup.");
    setNoteTone(statusLabel, "status");
  }

  private void applyPreset(PackageMode mode, String targetToken) {
    formatBox.getSelectionModel().select(mode);
    selectTargetByToken(targetToken);
    if (mode == PackageMode.NATIVE_PACKAGE) {
      nativeTypeBox.getSelectionModel().selectFirst();
    }
    statusLabel.setText("Preset applied: " + mode + ".");
    setNoteTone(statusLabel, "status");
    refreshFormState();
  }

  private File workspaceBuildDir() {
    return GradleWorkspaceLayout.buildDir(workspaceRoot == null ? null : workspaceRoot.toPath()).toFile();
  }

  private void browseOutputDir() {
    javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
    chooser.setTitle("Choose Build Output Folder");
    File current = buildDistributionsDir();
    if (current.exists()) chooser.setInitialDirectory(current);
    else if (current.getParentFile() != null && current.getParentFile().exists())
      chooser.setInitialDirectory(current.getParentFile());
    File chosen = chooser.showDialog(getScene() == null ? null : getScene().getWindow());
    if (chosen == null) return;
    customOutputDir = chosen;
    updateOutputDirField();
    refreshFormState();
    refreshArtifactInventory();
  }

  private void resetOutputDir() {
    customOutputDir = null;
    updateOutputDirField();
    refreshFormState();
    refreshArtifactInventory();
  }

  private void updateOutputDirField() {
    if (outputDirField == null) return;
    outputDirField.setText(buildDistributionsDir().getAbsolutePath());
    if (btnResetOutputDir != null) btnResetOutputDir.setDisable(customOutputDir == null);
  }

  private void zipOutputFolder() {
    File outDir = buildDistributionsDir();
    File[] outFiles = outDir.isDirectory() ? outDir.listFiles(f -> f.isFile() && !f.isHidden()) : null;
    if (outFiles == null || outFiles.length == 0) {
      statusLabel.setText("Nothing to zip: output folder is empty or does not exist.");
      setNoteTone(statusLabel, "warn");
      return;
    }
    String stem = BuildCliFormatter.safeToken(nameField.getText()) + "-" + BuildCliFormatter.safeToken(versionField.getText());
    String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.now());
    File parent = outDir.getParentFile() != null ? outDir.getParentFile() : outDir;
    File zipFile = new File(parent, stem + "-builds-" + timestamp + ".zip");
    try {
      BuildArtifactService.zipDirectory(outDir, zipFile);
      statusLabel.setText("Zipped to: " + zipFile.getAbsolutePath());
      setNoteTone(statusLabel, "ok");
    } catch (Exception ex) {
      statusLabel.setText("Zip failed: " + ex.getMessage());
      setNoteTone(statusLabel, "error");
    }
  }


  private File buildDistributionsDir() {
    if (customOutputDir != null) return customOutputDir;
    return new File(workspaceBuildDir(), "distributions/games");
  }

  private File releaseManifestJsonFile() {
    return new File(workspaceBuildDir(), "reports/jvn-game-release/release-manifest.json");
  }

  private File bundledRuntimeDownloadDir() {
    return new File(workspaceBuildDir(), "downloads/jvnRuntime");
  }

  private File bundledRuntimeExtractDir() {
    return new File(workspaceBuildDir(), "vendor-runtimes");
  }

  private void setNoteTone(Label label, String tone) {
    label.getStyleClass().removeAll(
        "build-publisher-note-ok",
        "build-publisher-note-warn",
        "build-publisher-note-error",
        "build-publisher-note-status");
    label.getStyleClass().add(switch (tone) {
      case "ok" -> "build-publisher-note-ok";
      case "warn" -> "build-publisher-note-warn";
      case "error" -> "build-publisher-note-error";
      default -> "build-publisher-note-status";
    });
  }

  private void setBadgeTone(Label label, String tone) {
    label.getStyleClass().removeAll(
        "build-publisher-badge-accent",
        "build-publisher-badge-warn",
        "build-publisher-badge-error");
    switch (tone) {
      case "accent" -> label.getStyleClass().add("build-publisher-badge-accent");
      case "warn" -> label.getStyleClass().add("build-publisher-badge-warn");
      case "error" -> label.getStyleClass().add("build-publisher-badge-error");
      default -> {
      }
    }
  }

  private void selectTargetByToken(String targetToken) {
    if (targetToken == null || targetToken.isBlank()) return;
    for (TargetChoice choice : targetBox.getItems()) {
      if (targetToken.equals(choice.outputToken())) {
        targetBox.getSelectionModel().select(choice);
        return;
      }
    }
  }

  private void refreshTargetChoicesForMode(PackageMode mode) {
    String previousToken = targetBox.getValue() == null ? currentTargetToken() : targetBox.getValue().outputToken();
    List<TargetChoice> choices = mode == PackageMode.NATIVE_PACKAGE
        ? List.of(currentTargetChoice())
        : desktopTargetChoices();
    targetBox.getItems().setAll(choices);
    String desiredToken = mode == PackageMode.NATIVE_PACKAGE ? currentTargetToken() : previousToken;
    selectTargetByToken(desiredToken);
    if (targetBox.getValue() == null) targetBox.getSelectionModel().selectFirst();
  }

  private static TargetChoice currentTargetChoice() {
    return new TargetChoice(
        "Current machine",
        "assembleJvnGamePortableCurrent",
        currentTargetDescription(),
        currentTargetToken());
  }

  private static List<TargetChoice> desktopTargetChoices() {
    return List.of(
        currentTargetChoice(),
        new TargetChoice("Windows x64", "assembleJvnGamePortableWindowsX64", "Builds a Windows desktop artifact.", "windows-x64"),
        new TargetChoice("Linux x64", "assembleJvnGamePortableLinuxX64", "Builds a Linux desktop artifact.", "linux-x64"),
        new TargetChoice("macOS x64", "assembleJvnGamePortableMacosX64", "Builds an Intel macOS desktop artifact.", "macos-x64"),
        new TargetChoice("macOS Apple Silicon", "assembleJvnGamePortableMacosAarch64", "Builds an Apple Silicon macOS desktop artifact.", "macos-aarch64"),
        new TargetChoice("All supported targets", "assembleJvnGamePortable", "Builds Windows x64, Linux x64, macOS x64, and macOS Apple Silicon.", "all"));
  }

  static List<String> supportedDesktopTargetTokens() {
    return desktopTargetChoices().stream().map(TargetChoice::outputToken).distinct().toList();
  }

  private PackageMode selectedPackageMode() {
    PackageMode mode = formatBox.getValue();
    return mode == null ? PackageMode.PORTABLE_ZIP : mode;
  }

  private String selectedReleaseProfile() {
    String value = releaseProfileBox.isEditable() ? releaseProfileBox.getEditor().getText() : releaseProfileBox.getValue();
    return value == null || value.trim().isBlank() ? "default" : value.trim();
  }

  private String selectedPackageVariant() {
    String value = packageVariantBox.isEditable() ? packageVariantBox.getEditor().getText() : packageVariantBox.getValue();
    return value == null || value.trim().isBlank() ? "standard" : value.trim();
  }

  private BuildTaskSelection buildTaskForSelection() {
    PackageMode mode = selectedPackageMode();
    TargetChoice target = targetBox.getValue();
    return switch (mode) {
      case PORTABLE_ZIP -> {
        if (target == null) yield null;
        if ("all".equals(target.outputToken())) {
          yield new BuildTaskSelection("assembleJvnGamePortable", "Build Game - All Portable Targets");
        }
        yield new BuildTaskSelection(target.taskName(), "Build Game - " + target.label());
      }
      case BUNDLED_RUNTIME_ZIP -> {
        if (target == null) yield null;
        if ("all".equals(target.outputToken())) {
          yield new BuildTaskSelection("assembleJvnGameBundledRuntime", "Build Game - All Desktop Bundles");
        }
        yield new BuildTaskSelection(
            "assembleJvnGameBundledRuntime" + targetTaskSuffix(target),
            "Build Game - Desktop Bundle - " + target.label());
      }
      case NATIVE_PACKAGE -> {
        NativeTypeChoice nativeType = nativeTypeBox.getValue();
        String label = nativeType == null ? "Native Package" : nativeType.label();
        yield new BuildTaskSelection("packageJvnGameNativeCurrent", "Build Game - " + label);
      }
    };
  }

  private BuildTaskSelection releaseTaskForSelection() {
    return switch (selectedPackageMode()) {
      case PORTABLE_ZIP -> {
        TargetChoice target = targetBox.getValue();
        if (target == null || "all".equals(target.outputToken()) || !target.outputToken().equals(currentTargetToken())) yield null;
        yield new BuildTaskSelection("releaseJvnGamePortableCurrent", "Release Game - Portable Current Host");
      }
      case BUNDLED_RUNTIME_ZIP -> {
        TargetChoice target = targetBox.getValue();
        if (target == null || "all".equals(target.outputToken())) yield null;
        yield new BuildTaskSelection(
            "releaseJvnGameBundledRuntime" + targetTaskSuffix(target),
            "Release Game - Desktop Bundle - " + target.label());
      }
      case NATIVE_PACKAGE ->
          new BuildTaskSelection("releaseJvnGameNativeCurrent", "Release Game - Native Package");
    };
  }

  private File findReleaseConfig(File root) {
    if (root == null) return null;
    File[] candidates = {
        new File(root, "config/release/jvn-release.properties"),
        new File(root, "config/release/release.properties"),
        new File(root, "release/jvn-release.properties"),
        new File(root, "jvn-release.properties")
    };
    for (File candidate : candidates) {
      if (candidate.isFile()) return candidate;
    }
    return null;
  }

  private Properties loadReleaseConfig(File root) {
    File file = findReleaseConfig(root);
    Properties props = new Properties();
    if (file == null) return props;
    try (FileInputStream in = new FileInputStream(file)) {
      props.load(in);
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return new Properties();
    }
    return props;
  }

  private List<String> availableReleaseProfiles(File root) {
    Properties props = loadReleaseConfig(root);
    List<String> profiles = new ArrayList<>();
    profiles.add("default");
    for (String key : props.stringPropertyNames()) {
      if (!key.startsWith("profile.")) continue;
      String suffix = key.substring("profile.".length());
      String profile = suffix.contains(".") ? suffix.substring(0, suffix.indexOf('.')) : suffix;
      if (profile.isBlank() || "default".equalsIgnoreCase(profile) || profiles.contains(profile)) continue;
      profiles.add(profile);
    }
    return profiles;
  }

  private List<String> availablePackageVariants(File root) {
    Properties props = loadReleaseConfig(root);
    List<String> variants = new ArrayList<>();
    variants.add("standard");
    for (String key : props.stringPropertyNames()) {
      if (!key.startsWith("variant.")) continue;
      String suffix = key.substring("variant.".length());
      String variant = suffix.contains(".") ? suffix.substring(0, suffix.indexOf('.')) : suffix;
      if (variant.isBlank() || variants.contains(variant)) continue;
      variants.add(variant);
    }
    return variants;
  }

  private String defaultPackageVariant(File root) {
    String configured = loadReleaseConfig(root).getProperty("defaultVariant", "standard").trim();
    return configured.isBlank() ? "standard" : configured;
  }

  private String defaultReleaseProfile(File root) {
    Properties props = loadReleaseConfig(root);
    String configured = props.getProperty("defaultProfile", "").trim();
    return configured.isBlank() ? "default" : configured;
  }

  private String releaseConfigText(File root) {
    File config = findReleaseConfig(root);
    if (config == null) return "Release profile config: none";
    List<String> profiles = availableReleaseProfiles(root);
    return "Release profile config: " + config.getPath() + "  profiles=" + String.join(", ", profiles);
  }













  private static String targetTaskSuffix(TargetChoice target) {
    if (target == null) return "";
    return switch (target.outputToken()) {
      case "windows-x64" -> "WindowsX64";
      case "linux-x64" -> "LinuxX64";
      case "macos-x64" -> "MacosX64";
      case "macos-aarch64" -> "MacosAarch64";
      default -> "";
    };
  }



  private static List<NativeTypeChoice> currentNativeTypeChoices() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    List<NativeTypeChoice> types = new ArrayList<>();
    if (os.contains("mac")) {
      types.add(new NativeTypeChoice("DMG Installer", "dmg", ".dmg", "Creates a signed/notarizable macOS disk image."));
      types.add(new NativeTypeChoice("PKG Installer", "pkg", ".pkg", "Creates a macOS installer package."));
      types.add(new NativeTypeChoice("App Image", "app-image", ".zip", "Creates a zipped macOS app bundle."));
      return types;
    }
    if (os.contains("win")) {
      types.add(new NativeTypeChoice("EXE Installer", "exe", ".exe", "Creates a Windows installer executable."));
      types.add(new NativeTypeChoice("MSI Installer", "msi", ".msi", "Creates a Windows MSI installer."));
      types.add(new NativeTypeChoice("App Image", "app-image", ".zip", "Creates a zipped self-contained Windows app image."));
      return types;
    }
    if (os.contains("linux")) {
      types.add(new NativeTypeChoice("DEB Package", "deb", ".deb", "Creates a Debian package for the current Linux host."));
      types.add(new NativeTypeChoice("RPM Package", "rpm", ".rpm", "Creates an RPM package when host tooling is available."));
      types.add(new NativeTypeChoice("App Image", "app-image", ".zip", "Creates a zipped self-contained Linux app image."));
      return types;
    }
    types.add(new NativeTypeChoice("Unsupported", "unsupported-native", ".bin", "Native packaging is not supported on this host OS."));
    return types;
  }

  private static String currentTargetDescription() {
    String token = currentTargetToken();
    if ("unsupported-linux-aarch64".equals(token)) {
      return "Linux aarch64 portable builds are not supported by the current JavaFX runtime classifiers. Choose a specific supported target instead.";
    }
    if (token.startsWith("unsupported")) {
      return "This host OS/arch is not supported as a current-machine portable target. Choose a specific supported target instead.";
    }
    return "Builds only the OS/arch this editor is running on.";
  }

  private static String currentTargetToken() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    if (os.contains("win")) return "windows-x64";
    if (os.contains("linux") && (arch.contains("aarch64") || arch.contains("arm64"))) return "unsupported-linux-aarch64";
    if (os.contains("linux")) return "linux-x64";
    if (os.contains("mac") && (arch.contains("aarch64") || arch.contains("arm64"))) return "macos-aarch64";
    if (os.contains("mac")) return "macos-x64";
    return "unsupported-current";
  }

  private enum PackageMode {
    PORTABLE_ZIP("Portable Zip", "portable"),
    BUNDLED_RUNTIME_ZIP("Desktop Bundle", "bundled"),
    NATIVE_PACKAGE("Native Package", "native");

    private final String label;
    private final String gradleToken;

    PackageMode(String label, String gradleToken) {
      this.label = label;
      this.gradleToken = gradleToken;
    }

    private String gradleToken() {
      return gradleToken;
    }

    private static List<PackageMode> supportedValues() {
      return List.of(PORTABLE_ZIP, BUNDLED_RUNTIME_ZIP, NATIVE_PACKAGE);
    }

    @Override
    public String toString() {
      return label;
    }
  }

  static String formatValidationMessages(String heading, List<String> messages) {
    if (messages == null || messages.isEmpty()) return heading + ": none";
    return heading + ":\n• " + String.join("\n• ", messages);
  }

  private record ValidationResult(List<String> errors, List<String> warnings) {
    private ValidationResult {
      errors = errors == null ? List.of() : List.copyOf(errors);
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
  }

  private record BuildTaskSelection(String taskName, String title) {
  }

  record ArtifactSummary(String name, long bytes, long lastModifiedMillis, boolean checksumAvailable) {
    ArtifactSummary(String name, long bytes, long lastModifiedMillis) {
      this(name, bytes, lastModifiedMillis, false);
    }
  }

  private record TargetChoice(String label, String taskName, String description, String outputToken) {
    @Override
    public String toString() {
      return label;
    }
  }

  private record NativeTypeChoice(String label, String token, String artifactSuffix, String description) {
    @Override
    public String toString() {
      return label;
    }
  }

  private enum ButtonTone {
    PRIMARY,
    SECONDARY,
    DANGER
  }

  private static final class AnimatedPresetArrowIndicator extends StackPane {
    private final StackPane arrow = new StackPane();
    private final Timeline timeline;
    private boolean active;

    private AnimatedPresetArrowIndicator() {
      getStyleClass().add("build-publisher-selected-prompt-arrow");
      setMinSize(18, 16);
      setPrefSize(18, 16);
      setMaxSize(18, 16);
      setPickOnBounds(false);

      Region body = CssIcon.arrowDown("#f28a18");
      body.setScaleX(1.18);
      body.setScaleY(1.18);

      arrow.setMinSize(18, 16);
      arrow.setPrefSize(18, 16);
      arrow.setMaxSize(18, 16);
      arrow.setAlignment(Pos.CENTER);
      arrow.setRotationAxis(Rotate.Y_AXIS);
      arrow.setPickOnBounds(false);
      arrow.getChildren().add(body);
      getChildren().add(arrow);

      resetAnimationState();
      timeline = new Timeline(
          new KeyFrame(Duration.ZERO,
              new KeyValue(arrow.rotateProperty(), 0),
              new KeyValue(arrow.translateYProperty(), -1.4),
              new KeyValue(arrow.scaleXProperty(), 1.0),
              new KeyValue(arrow.opacityProperty(), 1.0)),
          new KeyFrame(Duration.millis(360),
              new KeyValue(arrow.rotateProperty(), 180),
              new KeyValue(arrow.translateYProperty(), 2.0),
              new KeyValue(arrow.scaleXProperty(), 0.58),
              new KeyValue(arrow.opacityProperty(), 0.74)),
          new KeyFrame(Duration.millis(720),
              new KeyValue(arrow.rotateProperty(), 360),
              new KeyValue(arrow.translateYProperty(), -1.4),
              new KeyValue(arrow.scaleXProperty(), 1.0),
              new KeyValue(arrow.opacityProperty(), 1.0)));
      timeline.setCycleCount(Animation.INDEFINITE);
      sceneProperty().addListener((obs, oldScene, newScene) -> {
        if (newScene == null) {
          timeline.stop();
          resetAnimationState();
        } else if (active) {
          timeline.playFromStart();
        }
      });
    }

    private void setActive(boolean active) {
      if (this.active == active) return;
      this.active = active;
      if (!active) {
        timeline.stop();
        resetAnimationState();
      } else if (getScene() != null) {
        timeline.playFromStart();
      }
    }

    private void resetAnimationState() {
      arrow.setRotate(0);
      arrow.setTranslateY(-1.4);
      arrow.setScaleX(1.0);
      arrow.setOpacity(1.0);
    }
  }
}
