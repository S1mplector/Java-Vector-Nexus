package com.jvn.editor.ui;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import com.jvn.editor.vcs.GitHubApiClient;
import com.jvn.editor.vcs.GitHubTokenStore;
import com.jvn.editor.vcs.GitVcsService;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.Scene;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Team-focused Git control panel for JVN projects.
 */
public class VersionControlView extends BorderPane {
  private static final long REMOTE_CHECK_INTERVAL_MS = 120_000L;
  private static final DateTimeFormatter CHECK_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
  private static final double GUIDE_POPUP_WIDTH = 320.0;

  private final GitVcsService vcs = new GitVcsService();
  private final GitHubTokenStore githubTokenStore = new GitHubTokenStore();
  private final GitHubApiClient githubApiClient = new GitHubApiClient();
  private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "jvn-vcs-worker");
    t.setDaemon(true);
    return t;
  });

  private final Label titleLabel = new Label("Version Control");
  private final Label repoLabel = new Label("No project loaded");
  private final Label toolLabel = new Label("Git: --");
  private final Label branchLabel = new Label("Branch: --");
  private final Label syncLabel = new Label("Sync: --");
  private final Label incomingLabel = new Label("Incoming: --");
  private final Label summaryLabel = new Label("Status: --");
  private final Label nextStepLabel = new Label("Open a project to begin.");
  private final Label lastRemoteCheckLabel = new Label("Online check: --");
  private final Label conflictLabel = new Label();
  private final Label remoteLabel = new Label("Remote: not configured");
  private final Button btnConfigureRemote = new Button("Add Remote");
  private final Button btnRemoveRemote = new Button("Remove");
  private final Label githubTokenStatusLabel = new Label("GitHub token: not connected");
  private final Button btnChangeGitHubToken = new Button("Sign In");
  private final Button btnRemoveGitHubToken = new Button("Remove");
  private final Popup guidePopup = new Popup();
  private final VBox guidePopupRoot = new VBox(0);
  private final Label guideArrowLabel = new Label("", VersionControlIcon.of(VersionControlIcon.Kind.GUIDE_UP, 16));
  private final VBox guideCard = new VBox(4);
  private final Label guideTitleLabel = new Label();
  private final Label guideBodyLabel = new Label();
  private final Button guideCloseButton = new Button("", VersionControlIcon.of(VersionControlIcon.Kind.CLOSE, 16));
  private final BorderPane contentPane = new BorderPane();
  private final StackPane contentStack = new StackPane();
  private final StackPane centerViewStack = new StackPane();
  private final StackPane initializingOverlay = new StackPane();
  private final VBox initializingCard = new VBox(8);
  private final ProgressIndicator initializingSpinner = new ProgressIndicator();
  private final Label initializingTitleLabel = new Label("Initializing version control");
  private final Label initializingBodyLabel = new Label("Reading project status and preparing Git controls.");
  private final Label initTitleLabel = new Label(
      "Start version control",
      AeroIcon.of(AeroIcon.Kind.VERSION_CONTROL, 28));
  private final Label initHintLabel = new Label("Repository is not initialized for this project.");
  private final VBox initBox = new VBox(10);
  private final VBox repositoryTools = new VBox(6);
  private final javafx.scene.control.ToggleButton hints = new javafx.scene.control.ToggleButton("Hints");

  private final VBox setupBox = new VBox(8);

  private final CheckBox chkInitCommit = new CheckBox("Create initial commit");

  private final Button btnRefresh = actionButton(
      "Refresh",
      RefreshIcon.of(20),
      "Refresh the project status and check whether the online repository has new work.",
      "vcs-action-button-neutral");
  private final Button btnInitialize = new Button("Initialize");
  private final Button btnFetch = actionButton(
      "Check Online",
      VersionControlIcon.of(VersionControlIcon.Kind.CHECK_ONLINE),
      "Contact the remote repository and check for work you do not have yet.",
      "vcs-action-button-accent");
  private final Button btnPull = actionButton(
      "Get Updates",
      VersionControlIcon.of(VersionControlIcon.Kind.PULL),
      "Download incoming commits and replay your local work on top.",
      "vcs-action-button-warning");
  private final Button btnPush = actionButton(
      "Send Online",
      VersionControlIcon.of(VersionControlIcon.Kind.PUSH),
      "Upload your saved snapshots to the remote repository.",
      "vcs-action-button-success");
  private final Button btnForcePull = actionButton(
      "Force Pull",
      VersionControlIcon.of(VersionControlIcon.Kind.FORCE_PULL),
      "Discard local commits and changes and make this branch match the remote exactly.",
      "vcs-action-button-danger");
  private final Button btnForcePush = actionButton(
      "Force Push",
      VersionControlIcon.of(VersionControlIcon.Kind.FORCE_PUSH),
      "Overwrite the remote branch with your local history (force-with-lease).",
      "vcs-action-button-danger");
  private final Button btnCommit = actionButton(
      "Save Snapshot",
      VersionControlIcon.of(VersionControlIcon.Kind.SNAPSHOT),
      "Save all current project changes into a local version snapshot.",
      "vcs-action-button-success");
  private final Button btnStash = actionButton(
      "Shelve",
      VersionControlIcon.of(VersionControlIcon.Kind.SHELVE),
      "Temporarily put current changes aside without saving a snapshot.",
      "vcs-action-button-neutral");
  private final Button btnStashPop = actionButton(
      "Restore Shelf",
      VersionControlIcon.of(VersionControlIcon.Kind.RESTORE_SHELF),
      "Bring back the latest shelved changes.",
      "vcs-action-button-neutral");
  private final Button btnStageSelected = iconButton(
      VersionControlIcon.of(VersionControlIcon.Kind.STAGE),
      "Mark the selected file(s) for the next Git commit.");
  private final Button btnUnstageSelected = iconButton(
      VersionControlIcon.of(VersionControlIcon.Kind.UNSTAGE),
      "Remove the selected file(s) from the staged Git area.");
  private final Button btnDiscardSelected = iconButton(
      VersionControlIcon.of(VersionControlIcon.Kind.DISCARD),
      "Permanently discard the selected file change(s).");
  private final Button btnDiffSelected = iconButton(
      VersionControlIcon.of(VersionControlIcon.Kind.DIFF),
      "Show selected file diff(s) in the activity log.");
  private final ToggleButton btnChangesView = new ToggleButton("Changes");
  private final ToggleButton btnGraphView = new ToggleButton("Graph");
  private final ToggleGroup viewModeGroup = new ToggleGroup();
  private final ComboBox<String> cbBranch = new ComboBox<>();
  private final Button btnNewBranch = actionButton(
      "New Branch",
      VersionControlIcon.of(VersionControlIcon.Kind.NEW_BRANCH),
      "Create a branch with the typed name and switch to it.",
      "vcs-action-button-neutral");

  private static Button iconButton(Region iconClass, String tooltip) {
    Button btn = new Button();
    btn.getStyleClass().add("vcs-icon-btn");
    btn.setGraphic(iconClass);
    btn.setTooltip(new Tooltip(tooltip));
    return btn;
  }

  private static Button actionButton(Region iconClass, String tooltip, String... styleClasses) {
    return actionButton(null, iconClass, tooltip, styleClasses);
  }

  private static Button actionButton(String text, Region iconClass, String tooltip, String... styleClasses) {
    Button btn = new Button(text == null ? "" : text);
    btn.getStyleClass().add("vcs-action-button");
    if (styleClasses != null) btn.getStyleClass().addAll(styleClasses);
    btn.setGraphic(iconClass);
    btn.setTooltip(new Tooltip(tooltip));
    btn.setContentDisplay(ContentDisplay.LEFT);
    return btn;
  }

  private final TextField txtCommitMessage = new TextField();
  private final ListView<GitVcsService.StatusEntry> listChanges = new ListView<>();
  private final ListView<GitVcsService.ChangeGraphEntry> listGraph = new ListView<>();
  private final TextArea txtLog = new TextArea();

  private File projectRoot;
  private boolean gitAvailable;
  private boolean repositoryInitialized;
  private boolean busy;
  private boolean currentHasRemote;
  private boolean currentHasUpstream;
  private boolean currentHasConflicts;
  private boolean updatingBranchSelection;
  private boolean statusLoaded;
  private int currentAhead;
  private int currentBehind;
  private int currentChangeCount;
  private long lastRemoteCheckMs = -1L;
  private String lastRemoteCheckDisplay = "Online check: not checked yet";
  private String currentBranch = "";
  private Consumer<String> onOpenRelativePath;
  private Runnable onWorkingTreeChanged;
  private Timeline autoRefreshTimer;
  private boolean disposed;
  private String lastRemoteFailure = "";
  private long lastAuthFailureMs = -1L;
  private String lastAuthFailureMessage = "";
  private String lastGuideKey = "";
  private String dismissedGuideKey = "";
  private boolean guideTabActive = true;
  private String busyActionName = "";
  private final List<Node> currentGuideTargets = new ArrayList<>();

  public VersionControlView() {
    getStyleClass().addAll("version-control-root", "sidebar-tool-root");
    titleLabel.getStyleClass().addAll("vcs-title", "sidebar-tool-title");
    repoLabel.getStyleClass().addAll("vcs-muted", "sidebar-tool-subtitle");
    toolLabel.getStyleClass().add("vcs-muted");
    incomingLabel.getStyleClass().addAll("vcs-sync-chip", "vcs-sync-neutral");
    lastRemoteCheckLabel.getStyleClass().add("vcs-muted");
    nextStepLabel.getStyleClass().addAll("vcs-next-step", "vcs-next-step-info");
    nextStepLabel.setWrapText(true);
    configureGuidePopup();
    configureInitializingOverlay();
    conflictLabel.getStyleClass().add("vcs-conflict");
    conflictLabel.setVisible(false);
    conflictLabel.setManaged(false);
    remoteLabel.getStyleClass().addAll("vcs-remote", "vcs-remote-missing");
    btnConfigureRemote.getStyleClass().add("vcs-text-button");
    btnConfigureRemote.setOnAction(e -> showAddRemoteDialog());
    btnRemoveRemote.getStyleClass().add("vcs-text-button");
    btnRemoveRemote.setOnAction(e -> confirmRemoveRemote());
    btnRemoveRemote.setVisible(false);
    btnRemoveRemote.setManaged(false);
    githubTokenStatusLabel.getStyleClass().addAll("vcs-remote", "vcs-remote-missing");
    updateGitHubTokenStatus();
    initHintLabel.getStyleClass().add("vcs-banner-copy");
    initHintLabel.setWrapText(true);
    txtCommitMessage.getStyleClass().add("vcs-text-field");
    cbBranch.getStyleClass().add("vcs-combo");
    listChanges.getStyleClass().add("vcs-list");
    listGraph.getStyleClass().addAll("vcs-list", "vcs-graph-list");
    txtLog.getStyleClass().add("vcs-log");

    chkInitCommit.setSelected(true);

    txtCommitMessage.setPromptText("Describe what changed...");
    txtCommitMessage.textProperty().addListener((obs, oldValue, newValue) -> updateControlsForState());
    txtCommitMessage.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER && !txtCommitMessage.getText().isBlank()) runCommit();
    });

    txtLog.setEditable(false);
    txtLog.setWrapText(true);
    txtLog.setPrefRowCount(6);

    listChanges.setPlaceholder(new Label("No changed files"));
    listChanges.setCellFactory(lv -> new StatusCell());
    listGraph.setPlaceholder(new Label("No commits are available for the changes graph yet."));
    listGraph.setCellFactory(lv -> new GraphCell());
    listChanges.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    listChanges.setOnMouseClicked(e -> {
      if (e.getClickCount() < 2) return;
      GitVcsService.StatusEntry entry = listChanges.getSelectionModel().getSelectedItem();
      if (entry == null) return;
      openChangedEntry(entry);
    });
    listChanges.setOnKeyPressed(e -> {
      if (e.getCode() != KeyCode.ENTER) return;
      GitVcsService.StatusEntry entry = listChanges.getSelectionModel().getSelectedItem();
      if (entry == null) return;
      openChangedEntry(entry);
    });
    listChanges.getSelectionModel().selectedItemProperty().addListener((obs, oldEntry, newEntry) -> updateControlsForState());
    listChanges.getSelectionModel().getSelectedItems().addListener(
        (javafx.collections.ListChangeListener<GitVcsService.StatusEntry>) change -> updateControlsForState());

    btnChangesView.getStyleClass().addAll("vcs-view-toggle", "vcs-view-toggle-left");
    btnGraphView.getStyleClass().addAll("vcs-view-toggle", "vcs-view-toggle-right");
    btnChangesView.setToggleGroup(viewModeGroup);
    btnGraphView.setToggleGroup(viewModeGroup);
    btnChangesView.setSelected(true);
    btnChangesView.setTooltip(new Tooltip("Show changed files and per-file actions."));
    btnGraphView.setTooltip(new Tooltip("Show the branch and commit graph for project history."));
    viewModeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
      if (newToggle == null) {
        if (oldToggle != null) {
          oldToggle.setSelected(true);
        } else {
          btnChangesView.setSelected(true);
        }
        return;
      }
      updateCenterViewMode();
    });

    btnRefresh.setOnAction(e -> {
      dismissedGuideKey = "";
      refreshStatus(true);
    });
    btnInitialize.setOnAction(e -> initializeRepository());
    btnInitialize.getStyleClass().addAll("vcs-action-button", "vcs-action-button-success");
    btnInitialize.setGraphic(VersionControlIcon.of(VersionControlIcon.Kind.INITIALIZE));
    btnInitialize.setContentDisplay(ContentDisplay.LEFT);
    btnInitialize.setTooltip(new Tooltip("Create Git tracking for this project so snapshots and sync are available."));
    btnFetch.setOnAction(e -> runFetch());
    btnPull.setOnAction(e -> runPull());
    btnPush.setOnAction(e -> runPush());
    btnForcePull.setOnAction(e -> runForcePull());
    btnForcePush.setOnAction(e -> runForcePush());
    btnCommit.setOnAction(e -> runCommit());
    btnStash.setOnAction(e -> runStash());
    btnStashPop.setOnAction(e -> runStashPop());
    btnStageSelected.setOnAction(e -> runStageSelected());
    btnUnstageSelected.setOnAction(e -> runUnstageSelected());
    btnDiscardSelected.setOnAction(e -> runDiscardSelected());
    btnDiffSelected.setOnAction(e -> runDiffSelected());
    btnNewBranch.setOnAction(e -> runCreateBranch());
    cbBranch.setEditable(true);
    cbBranch.setPromptText("Branch name...");
    cbBranch.setOnAction(e -> {
      if (updatingBranchSelection) return;
      String selected = cbBranch.getValue();
      if ((selected == null || selected.isBlank()) && cbBranch.getEditor() != null) {
        selected = cbBranch.getEditor().getText();
      }
      if (selected == null || selected.isBlank() || selected.equals(currentBranch)) return;
      if (cbBranch.getItems().contains(selected)) {
        runSwitchBranch(selected);
      } else {
        appendLog("'" + selected + "' is not an existing branch. Click New Branch to create it.");
      }
    });

    // Sync toolbar: refresh, fetch, pull, push
    var syncRow = new javafx.scene.layout.FlowPane(6, 4, btnFetch, btnPull, btnPush);
    syncRow.setAlignment(Pos.CENTER_LEFT);

    // Danger zone: force pull (hard reset to remote) / force push (force-with-lease)
    var dangerRow = new javafx.scene.layout.FlowPane(6, 4, btnForcePull, btnForcePush);
    var advancedSync = new javafx.scene.control.TitledPane("Advanced sync", dangerRow);
    advancedSync.setExpanded(false);
    advancedSync.setAnimated(false);

    // Stash toolbar
    HBox stashRow = new HBox(6, btnStash, btnStashPop);
    stashRow.setAlignment(Pos.CENTER_LEFT);

    // Combined toolbar
    VBox toolbar = new VBox(6, syncRow, stashRow, advancedSync);
    toolbar.setAlignment(Pos.CENTER_LEFT);
    toolbar.setPadding(new Insets(4, 0, 4, 0));

    // Init controls - styled as prominent warning banner
    HBox initOptionsRow = new HBox(12, chkInitCommit);
    HBox initActionRow = new HBox(6, btnInitialize);
    initTitleLabel.getStyleClass().add("sidebar-tool-title");
    initBox.getStyleClass().add("vcs-status-stack");
    initHintLabel.setWrapText(true);
    initBox.getChildren().addAll(initTitleLabel, initHintLabel, initOptionsRow, initActionRow);

    // Setup guide banner - shown when repo exists but no remote configured
    // Option A: Create GitHub repo directly via GitHub API
    Label optionALabel = new Label("Create on GitHub");
    optionALabel.getStyleClass().add("vcs-option-label");
    ComboBox<String> cbVisibility = new ComboBox<>(FXCollections.observableArrayList("Private", "Public"));
    cbVisibility.setValue("Private");
    cbVisibility.getStyleClass().add("vcs-combo");
    cbVisibility.setMaxWidth(100);
    Button btnCreateGitHub = new Button("Create GitHub Repository");
    btnCreateGitHub.getStyleClass().addAll("vcs-action-button", "vcs-action-button-success");
    btnCreateGitHub.setOnAction(e -> {
      boolean isPrivate = "Private".equals(cbVisibility.getValue());
      showCreateGitHubRepoDialog(isPrivate);
    });
    HBox ghRow = new HBox(8, cbVisibility, btnCreateGitHub);
    ghRow.setAlignment(Pos.CENTER_LEFT);
    Label ghHint = new Label("Sign in with GitHub, or use a personal access token. Creates the repository and sets it as origin.");
    ghHint.getStyleClass().add("vcs-hint");
    VBox optionA = new VBox(4, optionALabel, ghRow, ghHint);

    // Option B: Manually connect existing repo
    Label optionBLabel = new Label("Existing remote");
    optionBLabel.getStyleClass().add("vcs-option-label");
    Button btnSetupRemote = new Button("Connect to Remote Repository");
    btnSetupRemote.getStyleClass().addAll("vcs-action-button", "vcs-action-button-accent");
    btnSetupRemote.setOnAction(e -> showAddRemoteDialog());
    Label manualHint = new Label("Already created a repo on GitHub/GitLab/Bitbucket? Paste its URL.");
    manualHint.getStyleClass().add("vcs-hint");
    VBox optionB = new VBox(4, optionBLabel, btnSetupRemote, manualHint);


    var remoteSetup = new javafx.scene.control.TitledPane("Connect a remote",
        new VBox(8, optionA, optionB));
    remoteSetup.setExpanded(false);
    remoteSetup.setAnimated(false);
    setupBox.getChildren().add(remoteSetup);
    setupBox.setVisible(false);
    setupBox.setManaged(false);

    // Commit row
    HBox commitRow = new HBox(6, txtCommitMessage, btnCommit);
    HBox.setHgrow(txtCommitMessage, Priority.ALWAYS);
    commitRow.setAlignment(Pos.CENTER_LEFT);

    // Branch row
    cbBranch.setMaxWidth(Double.MAX_VALUE);
    HBox branchRow = new HBox(6, cbBranch, btnNewBranch);
    HBox.setHgrow(cbBranch, Priority.ALWAYS);
    branchRow.setAlignment(Pos.CENTER_LEFT);

    // File action toolbar
    HBox fileActionRow = new HBox(4, btnStageSelected, btnUnstageSelected, btnDiscardSelected, btnDiffSelected);
    fileActionRow.setAlignment(Pos.CENTER_LEFT);

    // Header section: one compact status line instead of a stack of separate labels
    Label branchIcon = new Label("", VersionControlIcon.of(VersionControlIcon.Kind.BRANCH, 16));
    HBox branchChip = new HBox(4, branchIcon, branchLabel);
    branchChip.setAlignment(Pos.CENTER_LEFT);
    branchChip.getStyleClass().add("vcs-status-chip");

    syncLabel.getStyleClass().add("vcs-status-chip");
    summaryLabel.getStyleClass().add("vcs-status-chip");
    remoteLabel.getStyleClass().addAll("vcs-status-chip", "vcs-remote");

    var statusRow = new javafx.scene.layout.FlowPane(6, 4, branchChip, syncLabel, summaryLabel);
    statusRow.setAlignment(Pos.CENTER_LEFT);
    statusRow.getStyleClass().add("vcs-status-row");

    githubTokenStatusLabel.getStyleClass().addAll("vcs-status-chip", "vcs-remote");
    btnChangeGitHubToken.getStyleClass().add("vcs-text-button");
    btnChangeGitHubToken.setOnAction(e -> showGitHubDeviceFlowDialog(this::updateGitHubTokenStatus));
    btnRemoveGitHubToken.getStyleClass().add("vcs-text-button");
    btnRemoveGitHubToken.setOnAction(e -> removeGitHubToken());
    Region githubTokenSpacer = new Region();
    HBox.setHgrow(githubTokenSpacer, Priority.ALWAYS);
    HBox githubTokenRow = new HBox(8, githubTokenStatusLabel, githubTokenSpacer, btnChangeGitHubToken, btnRemoveGitHubToken);
    githubTokenRow.setAlignment(Pos.CENTER_LEFT);
    githubTokenRow.getStyleClass().add("vcs-status-row");

    var remoteActions = new javafx.scene.layout.FlowPane(6, 4, remoteLabel, btnConfigureRemote, btnRemoveRemote);
    var repositoryMenu = new javafx.scene.control.TitledPane("Repository and sync",
        new VBox(6, remoteActions, githubTokenRow, setupBox, branchRow, toolbar));
    repositoryMenu.setExpanded(false);
    repositoryMenu.setAnimated(false);
    VBox statusBox = new VBox(4, statusRow, conflictLabel);
    statusBox.getStyleClass().add("vcs-status-stack");

    HBox titleRow = new HBox(6, titleLabel, SidebarToolHelp.button(this, "Version Control", """
        The Version Control panel provides Git integration for your JVN project.

Status bar:
  • The pill row shows the current branch, unsynced snapshot counts, local \
change count, and remote connection state
  • The banner below it tells you the recommended next action

Core workflow:
  • Select a file in the Changes list, then use the stage / unstage / \
discard / diff buttons above it
  • Write a commit message and click Save Snapshot to record it
  • Check Online, Get Updates, and Send Online sync your local snapshots \
with the remote repository

Branch management:
  • Use the branch row to create or switch branches

Remote setup:
  If your project doesn't have a remote yet, the panel will guide you through \
connecting to GitHub or another host. Option A creates a new GitHub \
repository directly using a personal access token (paste it once; it is \
encrypted and stored locally). Option B connects an existing remote by \
pasting its URL.

Switch to the Graph view to see commit history. The Log below shows the \
result of your most recent actions."""));
    titleRow.setAlignment(Pos.CENTER_LEFT);

    hints.setAccessibleText("Show contextual version control hints");
    hints.setTooltip(new Tooltip("Show a hint for the next Git action"));
    hints.setOnAction(e -> { dismissedGuideKey = ""; updateGuidance(); });
    Region titleSpace = new Region();
    HBox.setHgrow(titleSpace, Priority.ALWAYS);
    titleRow.getChildren().addAll(titleSpace, btnRefresh, hints);
    VBox identityBox = new VBox(2, titleRow, repoLabel);
    repositoryTools.getChildren().setAll(statusBox, repositoryMenu, commitRow);

    VBox top = new VBox(6, identityBox, initBox, nextStepLabel, repositoryTools);
    top.getStyleClass().add("sidebar-tool-header");

    Label logLabel = new Label("Log");
    logLabel.getStyleClass().add("vcs-section-label");
    HBox viewModeRow = new HBox(0, btnChangesView, btnGraphView);
    viewModeRow.getStyleClass().add("vcs-view-mode");
    viewModeRow.setAlignment(Pos.CENTER_LEFT);
    VBox changesView = new VBox(4, fileActionRow, listChanges);
    VBox graphView = new VBox(4, sectionLabel("Commit history"), listGraph);
    changesView.getStyleClass().add("vcs-center-view");
    graphView.getStyleClass().add("vcs-center-view");
    centerViewStack.getChildren().setAll(changesView, graphView);
    VBox.setVgrow(listChanges, Priority.ALWAYS);
    VBox.setVgrow(listGraph, Priority.ALWAYS);
    VBox.setVgrow(changesView, Priority.ALWAYS);
    VBox.setVgrow(graphView, Priority.ALWAYS);

    VBox logBox = new VBox(6, logLabel, txtLog);
    SplitPane centerSplit = new SplitPane(centerViewStack, logBox);
    centerSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
    centerSplit.getStyleClass().add("sidebar-tool-split");
    centerSplit.setDividerPositions(0.75);
    SplitPane.setResizableWithParent(logBox, true);
    VBox.setVgrow(centerSplit, Priority.ALWAYS);

    VBox center = new VBox(6, viewModeRow, centerSplit);
    center.visibleProperty().bind(repositoryTools.visibleProperty());
    center.managedProperty().bind(center.visibleProperty());
    center.getStyleClass().add("vcs-center");
    center.setPadding(new Insets(0, 10, 10, 10));
    updateCenterViewMode();

    contentPane.setTop(top);
    contentPane.setCenter(center);
    contentStack.getChildren().addAll(contentPane, initializingOverlay);
    StackPane.setAlignment(initializingOverlay, Pos.CENTER);
    setCenter(contentStack);

    setInitControlsVisible(false, null);
    updateToolAvailabilityLabel(false);
    setBusy(false);

    // Auto-refresh every 30 seconds
    autoRefreshTimer = new Timeline(new KeyFrame(Duration.seconds(30), e -> {
      if (!busy && projectRoot != null) refreshStatus();
    }));
    autoRefreshTimer.setCycleCount(Timeline.INDEFINITE);
    autoRefreshTimer.play();
  }

  private void configureGuidePopup() {
    guidePopup.setAutoHide(false);
    guidePopup.setHideOnEscape(true);
    guideArrowLabel.getStyleClass().add("vcs-guide-arrow");
    guideCard.getStyleClass().add("vcs-guide-card");
    guideTitleLabel.getStyleClass().add("vcs-guide-title");
    guideBodyLabel.getStyleClass().add("vcs-guide-body");
    guideBodyLabel.setWrapText(true);
    guideBodyLabel.setMaxWidth(GUIDE_POPUP_WIDTH - 30.0);
    guideCloseButton.getStyleClass().add("vcs-guide-close");
    guideCloseButton.setTooltip(new Tooltip("Dismiss this guidance."));
    guideCloseButton.setOnAction(e -> {
      dismissedGuideKey = lastGuideKey;
      guidePopup.hide();
    });
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox guideHeader = new HBox(8, guideTitleLabel, spacer, guideCloseButton);
    guideHeader.setAlignment(Pos.CENTER_LEFT);
    guideCard.getChildren().addAll(guideHeader, guideBodyLabel);
    guidePopupRoot.getStyleClass().add("vcs-guide-popup");
    guidePopupRoot.setAlignment(Pos.CENTER);
    guidePopupRoot.getChildren().addAll(guideArrowLabel, guideCard);
    guidePopup.getContent().setAll(guidePopupRoot);
  }

  private void configureInitializingOverlay() {
    initializingOverlay.getStyleClass().add("vcs-initializing-overlay");
    initializingOverlay.setVisible(false);
    initializingOverlay.setManaged(false);
    initializingOverlay.setPickOnBounds(true);
    initializingCard.getStyleClass().add("vcs-initializing-card");
    initializingCard.setAlignment(Pos.CENTER);
    initializingCard.prefWidthProperty().bind(initializingOverlay.widthProperty());
    initializingCard.maxWidthProperty().bind(initializingOverlay.widthProperty());
    initializingCard.prefHeightProperty().bind(initializingOverlay.heightProperty());
    initializingCard.maxHeightProperty().bind(initializingOverlay.heightProperty());
    initializingSpinner.getStyleClass().add("vcs-initializing-spinner");
    initializingSpinner.setMaxSize(36, 36);
    initializingTitleLabel.getStyleClass().add("vcs-initializing-title");
    initializingBodyLabel.getStyleClass().add("vcs-initializing-body");
    initializingBodyLabel.setWrapText(true);
    initializingBodyLabel.maxWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
        () -> Math.max(180.0, Math.min(640.0, initializingOverlay.getWidth() - 48.0)),
        initializingOverlay.widthProperty()));
    initializingCard.getChildren().addAll(initializingSpinner, initializingTitleLabel, initializingBodyLabel);
    initializingOverlay.getChildren().add(initializingCard);
    StackPane.setAlignment(initializingCard, Pos.CENTER);
  }

  private Label sectionLabel(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("vcs-section-label");
    return label;
  }

  private void updateCenterViewMode() {
    boolean graphMode = btnGraphView.isSelected();
    if (centerViewStack.getChildren().size() >= 2) {
      Node changesView = centerViewStack.getChildren().get(0);
      Node graphView = centerViewStack.getChildren().get(1);
      changesView.setVisible(!graphMode);
      changesView.setManaged(!graphMode);
      graphView.setVisible(graphMode);
      graphView.setManaged(graphMode);
    }
  }

  public void setOnOpenRelativePath(Consumer<String> onOpenRelativePath) {
    this.onOpenRelativePath = onOpenRelativePath;
  }

  /**
   * Registers a callback fired after operations that can add, remove, or overwrite files on disk
   * outside the editor's knowledge (branch switch/create, pull, force pull, stash pop) so hosts
   * can refresh things like the Project Explorer tree. Not fired for operations that only touch
   * the index/history (stage, commit, push).
   */
  public void setOnWorkingTreeChanged(Runnable onWorkingTreeChanged) {
    this.onWorkingTreeChanged = onWorkingTreeChanged;
  }

  private void notifyWorkingTreeChanged() {
    if (onWorkingTreeChanged != null) Platform.runLater(onWorkingTreeChanged);
  }

  public void setProjectRoot(File projectRoot) {
    if (disposed) return;
    this.projectRoot = projectRoot;
    statusLoaded = false;
    dismissedGuideKey = "";
    repoLabel.setText(projectRoot == null ? "No project loaded" : projectRoot.getName());
    repoLabel.setTooltip(projectRoot == null ? null : new Tooltip(projectRoot.getAbsolutePath()));
    refreshStatus(true);
  }

  public void refreshStatus() {
    refreshStatus(false);
  }

  private void refreshStatus(boolean checkRemote) {
    if (disposed) return;
    runAsync("Refresh status", () -> loadStatus(checkRemote));
  }

  private void loadStatus(boolean forceRemoteCheck) {
    boolean git = vcs.isGitAvailable();
    Platform.runLater(() -> {
      gitAvailable = git;
      updateToolAvailabilityLabel(git);
    });

    if (projectRoot == null) {
      Platform.runLater(() -> applyNoProjectState());
      return;
    }

    if (!git) {
      Platform.runLater(() -> applyGitMissingState());
      return;
    }

    if (!vcs.isRepository(projectRoot)) {
      Platform.runLater(() -> applyNotInitializedState());
      return;
    }

    try {
      boolean hasRemote = vcs.hasRemote(projectRoot);
      String remoteCheckText = lastRemoteCheckText();
      if (!hasRemote) {
        remoteCheckText = "Online check: remote not connected";
        lastRemoteFailure = "";
      }
      if (hasRemote && shouldCheckRemote(forceRemoteCheck)) {
        try {
          GitVcsService.CommandResult fetch = vcs.fetch(projectRoot);
          lastRemoteCheckMs = System.currentTimeMillis();
          remoteCheckText = "Online check: " + LocalTime.now().format(CHECK_TIME_FORMAT);
          if (!fetch.success()) {
            remoteCheckText += " (failed)";
          }
          lastRemoteFailure = fetch.success() ? "" : safe(fetch.output());
          lastRemoteCheckDisplay = remoteCheckText;
        } catch (Exception ex) {
          lastRemoteCheckMs = System.currentTimeMillis();
          lastRemoteFailure = safeMessage(ex);
          remoteCheckText = "Online check failed: " + lastRemoteFailure;
          lastRemoteCheckDisplay = remoteCheckText;
          appendLog("Online check failed: " + lastRemoteFailure);
        }
      }

      GitVcsService.RepositoryStatus status = vcs.getRepositoryStatus(projectRoot);
      String remoteUrl = hasRemote ? vcs.getRemoteUrl(projectRoot) : null;
      List<String> branches = vcs.listBranches(projectRoot);
      List<GitVcsService.ChangeGraphEntry> graphEntries = vcs.changeGraphEntries(projectRoot, 80);
      List<String> conflictedPaths = conflictedPaths(status);
      boolean hasConflicts = !conflictedPaths.isEmpty();
      String finalRemoteCheckText = remoteCheckText;
      Platform.runLater(() -> applyRepositoryStatus(status, hasRemote, remoteUrl, branches, graphEntries, hasConflicts, conflictedPaths, finalRemoteCheckText));
    } catch (Exception ex) {
      appendLog(safeMessage(ex));
      Platform.runLater(() -> {
        markStatusLoaded();
        setNextStep("Could not read version-control status. See Activity for details.", "vcs-next-step-danger");
        updateControlsForState();
      });
    }
  }

  private void applyNoProjectState() {
    markStatusLoaded();
    repositoryInitialized = false;
    currentHasRemote = false;
    currentHasUpstream = false;
    currentHasConflicts = false;
    currentAhead = 0;
    currentBehind = 0;
    currentChangeCount = 0;
    currentBranch = "";
    lastRemoteFailure = "";
    branchLabel.setText("--");
    summaryLabel.setText("no project");
    syncLabel.setVisible(false);
    syncLabel.setManaged(false);
    remoteLabel.setText("No remote");
    btnRemoveRemote.setVisible(false);
    btnRemoveRemote.setManaged(false);
    listChanges.getItems().clear();
    listGraph.getItems().clear();
    cbBranch.getItems().clear();
    setSetupVisible(false);
    setInitControlsVisible(false, null);
    setNextStep("Open a project to begin.", "vcs-next-step-info");
    updateControlsForState();
  }

  private void applyGitMissingState() {
    markStatusLoaded();
    repositoryInitialized = false;
    currentHasRemote = false;
    currentHasUpstream = false;
    currentHasConflicts = false;
    currentAhead = 0;
    currentBehind = 0;
    currentChangeCount = 0;
    lastRemoteFailure = "";
    branchLabel.setText("--");
    summaryLabel.setText("Git unavailable");
    syncLabel.setVisible(false);
    syncLabel.setManaged(false);
    btnRemoveRemote.setVisible(false);
    btnRemoveRemote.setManaged(false);
    listChanges.getItems().clear();
    listGraph.getItems().clear();
    cbBranch.getItems().clear();
    setSetupVisible(false);
    setInitControlsVisible(false, null);
    setNextStep("Install Git before using version control.", "vcs-next-step-danger");
    updateControlsForState();
  }

  private void applyNotInitializedState() {
    markStatusLoaded();
    repositoryInitialized = false;
    currentHasRemote = false;
    currentHasUpstream = false;
    currentHasConflicts = false;
    currentAhead = 0;
    currentBehind = 0;
    currentChangeCount = 0;
    currentBranch = "";
    lastRemoteFailure = "";
    branchLabel.setText("not initialized");
    summaryLabel.setText("not initialized");
    syncLabel.setVisible(false);
    syncLabel.setManaged(false);
    btnRemoveRemote.setVisible(false);
    btnRemoveRemote.setManaged(false);
    listChanges.getItems().clear();
    listGraph.getItems().clear();
    cbBranch.getItems().clear();
    setSetupVisible(false);
    setInitControlsVisible(true, "Track changes to this project and save snapshots locally. You can connect a remote later.");
    setNextStep("Start here: initialize version control for this project.", "vcs-next-step-warning");
    updateControlsForState();
  }

  private void applyRepositoryStatus(GitVcsService.RepositoryStatus status,
                                     boolean hasRemote,
                                     String remoteUrl,
                                     List<String> branches,
                                     List<GitVcsService.ChangeGraphEntry> graphEntries,
                                     boolean hasConflicts,
                                     List<String> conflictedPaths,
                                     String remoteCheckText) {
    markStatusLoaded();
    repositoryInitialized = true;
    currentHasRemote = hasRemote;
    currentHasUpstream = status.upstream() != null && !status.upstream().isBlank();
    currentHasConflicts = hasConflicts;
    currentAhead = Math.max(0, status.ahead());
    currentBehind = Math.max(0, status.behind());
    currentChangeCount = status.entries() == null ? 0 : status.entries().size();
    currentBranch = safe(status.branch());

    String upstream = status.upstream() == null || status.upstream().isBlank() ? "not linked to online branch" : status.upstream();
    branchLabel.setText(currentBranch);
    branchLabel.setTooltip(new Tooltip("Current branch: " + currentBranch));
    syncLabel.setText((currentAhead > 0 ? "↑" + currentAhead + " " : "") + (currentBehind > 0 ? "↓" + currentBehind : ""));
    syncLabel.setTooltip(new Tooltip(
        currentAhead + " local snapshot" + plural(currentAhead) + " not uploaded, "
            + currentBehind + " online snapshot" + plural(currentBehind) + " waiting (" + upstream + ")"));
    syncLabel.setVisible(currentAhead > 0 || currentBehind > 0);
    syncLabel.setManaged(currentAhead > 0 || currentBehind > 0);
    summaryLabel.setText(status.clean() ? "clean" : currentChangeCount + " changed file" + plural(currentChangeCount));
    summaryLabel.setTooltip(new Tooltip(status.clean()
        ? "No local file changes"
        : currentChangeCount + " changed file" + plural(currentChangeCount)));
    incomingLabel.setTooltip(new Tooltip(buildIncomingText(hasRemote, currentBehind)));
    String checkText = remoteCheckText == null || remoteCheckText.isBlank() ? lastRemoteCheckText() : remoteCheckText;
    lastRemoteCheckLabel.setTooltip(new Tooltip(checkText));
    if (hasConflicts) {
      int count = conflictedPaths == null ? 0 : conflictedPaths.size();
      conflictLabel.setText("Conflicts in " + count + " file" + plural(count) + " need manual resolution before syncing.");
      conflictLabel.setTooltip(new Tooltip(conflictedPaths == null || conflictedPaths.isEmpty()
          ? "Conflicting files"
          : "Conflicting files:\n" + String.join("\n", conflictedPaths)));
    } else {
      conflictLabel.setText("");
      conflictLabel.setTooltip(null);
    }
    conflictLabel.setVisible(hasConflicts);
    conflictLabel.setManaged(hasConflicts);

    remoteLabel.getStyleClass().removeAll("vcs-remote-configured", "vcs-remote-missing");
    if (hasRemote && remoteUrl != null && !remoteUrl.isBlank()) {
      remoteLabel.setText("Remote connected");
      remoteLabel.setTooltip(new Tooltip(remoteUrl));
      remoteLabel.getStyleClass().add("vcs-remote-configured");
      btnConfigureRemote.setText("Change");
      btnRemoveRemote.setVisible(true);
      btnRemoveRemote.setManaged(true);
      setSetupVisible(false);
    } else {
      remoteLabel.setText("No remote");
      remoteLabel.setTooltip(new Tooltip("Remote: not configured"));
      remoteLabel.getStyleClass().add("vcs-remote-missing");
      btnConfigureRemote.setText("Add Remote");
      btnRemoveRemote.setVisible(false);
      btnRemoveRemote.setManaged(false);
      setSetupVisible(true);
    }

    listChanges.setItems(FXCollections.observableArrayList(status.entries()));
    listGraph.setItems(FXCollections.observableArrayList(graphEntries == null ? List.of() : graphEntries));
    updateBranchChoices(branches, currentBranch);
    setInitControlsVisible(false, null);
    updateNextStep(status, hasRemote, hasConflicts);
    updateSyncChipStyle();
    updateControlsForState();
  }

  private boolean shouldCheckRemote(boolean forceRemoteCheck) {
    if (forceRemoteCheck) return true;
    return lastRemoteCheckMs < 0L || System.currentTimeMillis() - lastRemoteCheckMs >= REMOTE_CHECK_INTERVAL_MS;
  }

  private String lastRemoteCheckText() {
    return lastRemoteCheckDisplay;
  }

  private String buildIncomingText(boolean hasRemote, int behind) {
    if (!hasRemote) return "Incoming: remote not connected";
    if (behind > 0) return "Incoming: " + behind + " online snapshot" + plural(behind) + " waiting";
    return "Incoming: none found";
  }

  private void updateNextStep(GitVcsService.RepositoryStatus status, boolean hasRemote, boolean hasConflicts) {
    if (hasConflicts) {
      setNextStep("Resolve the listed conflicts before saving or syncing.", "vcs-next-step-danger");
    } else if (hasRemote && !lastRemoteFailure.isBlank()) {
      setNextStep("Online check failed. Fix authentication or network access, then Check Online again.", "vcs-next-step-danger");
    } else if (currentBehind > 0 && currentChangeCount > 0) {
      setNextStep("Save Snapshot or Shelve your local edits, then Get Updates.", "vcs-next-step-warning");
    } else if (currentBehind > 0) {
      setNextStep("Get Updates first. The online repository has work this copy does not have yet.", "vcs-next-step-warning");
    } else if (currentChangeCount > 0) {
      setNextStep("Describe the change and click Save Snapshot.", "vcs-next-step-info");
    } else if (!hasRemote) {
      setNextStep("Recommended: connect a remote repository so the project can be backed up online.", "vcs-next-step-warning");
    } else if (currentAhead > 0 || !currentHasUpstream) {
      setNextStep("Send Online to upload your saved snapshot" + plural(Math.max(1, currentAhead)) + ".", "vcs-next-step-success");
    } else {
      setNextStep("Everything is up to date.", "vcs-next-step-success");
    }
  }

  private void setNextStep(String text, String toneClass) {
    nextStepLabel.setText(text == null || text.isBlank() ? "Status unavailable." : text);
    nextStepLabel.getStyleClass().removeAll(
        "vcs-next-step-info",
        "vcs-next-step-success",
        "vcs-next-step-warning",
        "vcs-next-step-danger");
    nextStepLabel.getStyleClass().add(toneClass == null || toneClass.isBlank() ? "vcs-next-step-info" : toneClass);
  }

  private void updateBranchChoices(List<String> branches, String selectedBranch) {
    boolean editingBranchField = cbBranch.getEditor() != null && cbBranch.getEditor().isFocused();
    updatingBranchSelection = true;
    try {
      cbBranch.setItems(FXCollections.observableArrayList(branches == null ? List.of() : branches));
      if (editingBranchField) {
        return;
      }
      cbBranch.setValue(selectedBranch == null || selectedBranch.isBlank() ? null : selectedBranch);
      if (cbBranch.getEditor() != null) {
        cbBranch.getEditor().setText(selectedBranch == null ? "" : selectedBranch);
      }
    } finally {
      updatingBranchSelection = false;
    }
  }

  private void setSetupVisible(boolean visible) {
    setupBox.setVisible(visible);
    setupBox.setManaged(visible);
  }

  private boolean hasConflicts(GitVcsService.RepositoryStatus status) {
    return !conflictedPaths(status).isEmpty();
  }

  private List<String> conflictedPaths(GitVcsService.RepositoryStatus status) {
    if (status == null || status.entries() == null) return List.of();
    List<String> paths = new ArrayList<>();
    for (GitVcsService.StatusEntry entry : status.entries()) {
      String index = entry.indexStatus();
      String workTree = entry.workTreeStatus();
      boolean conflicted = "U".equals(index) || "U".equals(workTree)
          || ("D".equals(index) && "D".equals(workTree))
          || ("A".equals(index) && "A".equals(workTree));
      if (conflicted) paths.add(entry.path());
    }
    return paths;
  }

  private void updateSyncChipStyle() {
    syncLabel.getStyleClass().removeAll("vcs-sync-neutral", "vcs-sync-success", "vcs-sync-warning", "vcs-sync-danger");
    if (currentHasConflicts) {
      syncLabel.getStyleClass().add("vcs-sync-danger");
    } else if (!currentHasRemote || currentBehind > 0) {
      syncLabel.getStyleClass().add("vcs-sync-warning");
    } else {
      syncLabel.getStyleClass().add("vcs-sync-success");
    }
  }

  private void updateActionEmphasis() {
    clearActionState(btnCommit);
    clearActionState(btnPull);
    clearActionState(btnPush);
    clearActionState(btnFetch);
    if (currentHasConflicts) {
      btnCommit.getStyleClass().add("vcs-action-state-danger");
      btnPull.getStyleClass().add("vcs-action-state-danger");
      btnPush.getStyleClass().add("vcs-action-state-danger");
    } else if (currentBehind > 0) {
      btnPull.getStyleClass().add("vcs-action-state-attention");
    } else if (currentChangeCount > 0) {
      btnCommit.getStyleClass().add("vcs-action-state-attention");
    } else if (currentAhead > 0 || (repositoryInitialized && currentHasRemote && !currentHasUpstream)) {
      btnPush.getStyleClass().add("vcs-action-state-attention");
    } else if (repositoryInitialized && currentHasRemote) {
      btnFetch.getStyleClass().add("vcs-action-state-ready");
    }
  }

  private void clearActionState(Button button) {
    button.getStyleClass().removeAll(
        "vcs-action-state-attention",
        "vcs-action-state-ready",
        "vcs-action-state-danger");
  }

  private void updateGuidance() {
    clearGuideTargets();
    GuidanceStep step = computeGuidanceStep();
    if (!hints.isSelected() || step == null || step.targets().isEmpty() || !guideTabActive) {
      lastGuideKey = "";
      guidePopup.hide();
      return;
    }

    for (Node target : step.targets()) {
      if (target != null && !target.getStyleClass().contains("vcs-guide-target")) {
        target.getStyleClass().add("vcs-guide-target");
        currentGuideTargets.add(target);
      }
    }

    if (getScene() == null || getScene().getWindow() == null || busy) {
      return;
    }

    String previousGuideKey = lastGuideKey;
    lastGuideKey = step.key();
    if (step.key().equals(dismissedGuideKey)) {
      guidePopup.hide();
      return;
    }

    if (!guidePopup.isShowing() || !step.key().equals(previousGuideKey)) {
      Platform.runLater(() -> showGuidePopup(step));
    }
  }

  private void clearGuideTargets() {
    for (Node target : currentGuideTargets) {
      if (target != null) target.getStyleClass().remove("vcs-guide-target");
    }
    currentGuideTargets.clear();
  }

  private GuidanceStep computeGuidanceStep() {
    if (projectRoot == null) {
      return new GuidanceStep(
          "no-project",
          List.of(titleLabel),
          "Open a project",
          "Select a JVN project first. Version control guidance starts once a project is loaded.",
          "info");
    }
    if (!gitAvailable) {
      return new GuidanceStep(
          "git-missing",
          List.of(toolLabel),
          "Git is missing",
          "Install Git and restart the editor so snapshots, pull, and push can run.",
          "danger");
    }
    if (!repositoryInitialized) {
      return new GuidanceStep(
          "init-repository",
          List.of(btnInitialize, initBox),
          "Start here",
          "Initialize version control. This creates the local repository used for snapshots and sync.",
          "warning");
    }
    if (currentHasConflicts) {
      return new GuidanceStep(
          "conflicts",
          List.of(listChanges),
          "Resolve conflicts first",
          "Files marked Conflict must be fixed manually before Save Snapshot, Get Updates, or Send Online can continue.",
          "danger");
    }
    if (currentHasRemote && !lastRemoteFailure.isBlank()) {
      return new GuidanceStep(
          "remote-failed:" + failureBucket(lastRemoteFailure),
          List.of(btnFetch, remoteLabel),
          "Online check failed",
          remoteFailureGuidance(lastRemoteFailure),
          "danger");
    }
    if (currentBehind > 0 && currentChangeCount > 0) {
      return new GuidanceStep(
          "behind-with-edits",
          List.of(txtCommitMessage, btnCommit, btnStash),
          "Protect local edits",
          "Save Snapshot or Shelve your local changes before getting the online updates.",
          "warning");
    }
    if (currentBehind > 0) {
      return new GuidanceStep(
          "behind",
          List.of(btnPull),
          "Get updates",
          "The online repository has newer work. Pull it before pushing your own snapshots.",
          "warning");
    }
    if (currentChangeCount > 0) {
      boolean messageReady = txtCommitMessage.getText() != null && !txtCommitMessage.getText().isBlank();
      return new GuidanceStep(
          messageReady ? "commit-ready" : "commit-message",
          messageReady ? List.of(btnCommit, txtCommitMessage) : List.of(txtCommitMessage, btnCommit),
          messageReady ? "Save snapshot" : "Describe the change",
          messageReady
              ? "Click Save Snapshot to record these file changes locally."
              : "Write a short title for what changed, then click Save Snapshot.",
          "info");
    }
    if (!currentHasRemote) {
      return new GuidanceStep(
          "add-remote",
          List.of(btnConfigureRemote, setupBox),
          "Connect online backup",
          "Add or create a remote repository so this project can be shared and restored.",
          "warning");
    }
    if (currentAhead > 0 || !currentHasUpstream) {
      return new GuidanceStep(
          "push",
          List.of(btnPush),
          "Send online",
          "Upload your saved snapshot to the remote repository.",
          "success");
    }
    return new GuidanceStep(
        "clean",
        List.of(btnFetch),
        "All clear",
        "No local edits or waiting uploads. Check Online whenever you want to verify remote changes.",
        "success");
  }

  private void showGuidePopup(GuidanceStep step) {
    if (step == null || step.targets().isEmpty() || getScene() == null) return;
    Node anchor = firstVisibleTarget(step.targets());
    if (anchor == null) return;
    var bounds = anchor.localToScreen(anchor.getBoundsInLocal());
    Window window = getScene().getWindow();
    if (bounds == null || window == null || !window.isShowing()) return;

    guideCard.getStyleClass().removeAll(
        "vcs-guide-card-info",
        "vcs-guide-card-success",
        "vcs-guide-card-warning",
        "vcs-guide-card-danger");
    guideCard.getStyleClass().add("vcs-guide-card-" + step.tone());
    guideTitleLabel.setText(step.title());
    guideBodyLabel.setText(step.body());

    double x = bounds.getMinX() + (bounds.getWidth() / 2.0) - (GUIDE_POPUP_WIDTH / 2.0);
    double y = bounds.getMaxY() + 8.0;
    double minX = window.getX() + 10.0;
    double maxX = window.getX() + Math.max(10.0, window.getWidth() - GUIDE_POPUP_WIDTH - 10.0);
    x = Math.max(minX, Math.min(x, maxX));

    guidePopup.hide();
    guidePopup.show(window, x, y);
    if (guidePopupRoot.getScene() != null && getScene() != null) {
      guidePopupRoot.getScene().getStylesheets().setAll(getScene().getStylesheets());
    }
  }

  private Node firstVisibleTarget(List<Node> targets) {
    if (targets == null) return null;
    for (Node target : targets) {
      if (target != null && target.isVisible() && target.getScene() != null) return target;
    }
    return null;
  }

  private String failureBucket(String failure) {
    String lower = failure == null ? "" : failure.toLowerCase(Locale.ROOT);
    if (lower.contains("username") || lower.contains("authentication") || lower.contains("auth")) return "auth";
    if (lower.contains("certificate") || lower.contains("ssl") || lower.contains("tls")) return "certificate";
    if (lower.contains("could not resolve") || lower.contains("timed out") || lower.contains("network")) return "network";
    if (lower.contains("remote origin already exists")) return "remote-exists";
    return "general";
  }

  private String remoteFailureGuidance(String failure) {
    String bucket = failureBucket(failure);
    return switch (bucket) {
      case "auth" -> "Git could not authenticate. Run `git fetch origin` in a terminal, sign in if prompted, then return and Check Online.";
      case "certificate" -> "The remote certificate was rejected. Check VPN/proxy/certificate settings, then run Check Online again.";
      case "network" -> "The remote could not be reached. Check VPN/network access and the remote URL, then retry.";
      default -> "Open the Activity log for details, fix the remote problem, then use Check Online again.";
    };
  }

  private String plural(int count) {
    return count == 1 ? "" : "s";
  }

  private void initializeRepository() {
    runAsync("Initialize repository", () -> {
      if (projectRoot == null) {
        appendLog("Select a project before initializing version control.");
        return;
      }
      try {
        boolean initialCommit = chkInitCommit.isSelected();
        vcs.bootstrapRepository(projectRoot, initialCommit, "Initialize JVN project scaffold");
        appendLog("Repository initialized.");
      } catch (Exception ex) {
        appendLog(ex.getMessage());
      }
      loadStatus(true);
    });
  }

  private static final int DIVERGED_WARNING_THRESHOLD = 15;

  private void runFetch() {
    runAsync("Fetch", () -> {
      if (projectRoot == null) {
        appendLog("Select a project before fetching.");
        return;
      }
      try {
        appendCommandResult(vcs.fetch(projectRoot, this::appendConsoleLine));
        lastRemoteCheckMs = System.currentTimeMillis();
        lastRemoteCheckDisplay = "Online check: " + LocalTime.now().format(CHECK_TIME_FORMAT);
        GitVcsService.RepositoryStatus status = vcs.getRepositoryStatus(projectRoot);
        if (status.behind() >= DIVERGED_WARNING_THRESHOLD) {
          appendLog("Warning: this branch is " + status.behind()
              + " snapshots behind the remote. Consider getting updates before making further changes"
              + " to reduce the chance of a difficult rebase.");
        }
        if (status.ahead() >= DIVERGED_WARNING_THRESHOLD) {
          appendLog("Warning: this branch is " + status.ahead()
              + " snapshots ahead of the remote and not yet sent online. Consider sending online soon.");
        }
      } catch (Exception ex) {
        lastRemoteCheckMs = System.currentTimeMillis();
        lastRemoteCheckDisplay = "Online check failed: " + safeMessage(ex);
        appendLog(ex.getMessage());
      }
      loadStatus(false);
    });
  }

  private void runCommit() {
    runAsync("Commit", () -> {
      if (projectRoot == null) {
        appendLog("Select a project before committing.");
        return;
      }
      if (!confirmPreflight("Save Snapshot")) return;
      String message = txtCommitMessage.getText();
      if (message == null || message.isBlank()) {
        message = "Update project files";
      }
      try {
        GitVcsService.CommandResult result = vcs.commitAll(projectRoot, message.trim());
        appendCommandResult(result);
        Platform.runLater(() -> txtCommitMessage.clear());
      } catch (Exception ex) {
        appendLog(ex.getMessage());
      }
      loadStatus(false);
    });
  }

  /**
   * Runs a preflight check and shows a blocking summary dialog on the FX thread.
   * Must be called from the background worker thread. Returns true if the user
   * chose to proceed.
   */
  private boolean confirmPreflight(String actionLabel) {
    if (GitVcsService.isAuthCooldownActive(lastAuthFailureMs, System.currentTimeMillis(), REMOTE_CHECK_INTERVAL_MS)) {
      return showPreflightBlockedDialog(actionLabel, lastAuthFailureMessage);
    }

    GitVcsService.PreflightResult result;
    try {
      result = vcs.preflight(projectRoot);
    } catch (Exception ex) {
      return showPreflightUnavailableDialog(actionLabel, ex.getMessage());
    }

    if (!result.credentialsOk()) {
      lastAuthFailureMs = System.currentTimeMillis();
      lastAuthFailureMessage = result.credentialIssue();
    }

    return showPreflightDialog(actionLabel, result);
  }

  private boolean showPreflightDialog(String actionLabel, GitVcsService.PreflightResult result) {
    CompletableFuture<Boolean> decision = new CompletableFuture<>();
    Platform.runLater(() -> {
      VBox content = new VBox(6);
      content.getStyleClass().add("editor-dialog-form");
      for (String line : PreflightSummaryFormatter.format(result)) {
        Label lineLabel = new Label(line);
        lineLabel.getStyleClass().add("editor-dialog-field-label");
        content.getChildren().add(lineLabel);
      }

      Optional<String> choice = EditorDialogs.show(
          getScene() == null ? null : getScene().getWindow(),
          actionLabel + "?",
          "Review what will happen before continuing.",
          content,
          EditorDialogs.ActionSpec.neutral("cancel", "Cancel", null).defaultFocus(true),
          EditorDialogs.ActionSpec.accent("proceed", actionLabel, null));
      decision.complete("proceed".equals(choice.orElse(null)));
    });
    return decision.join();
  }

  private boolean showPreflightUnavailableDialog(String actionLabel, String errorMessage) {
    CompletableFuture<Boolean> decision = new CompletableFuture<>();
    Platform.runLater(() -> {
      Optional<String> choice = EditorDialogs.show(
          getScene() == null ? null : getScene().getWindow(),
          actionLabel + "?",
          "Could not read the current repository state: " + errorMessage
              + "\n\nYou can still proceed, but the summary above could not be confirmed.",
          null,
          EditorDialogs.ActionSpec.neutral("cancel", "Cancel", null).defaultFocus(true),
          EditorDialogs.ActionSpec.accent("proceed", actionLabel, null));
      decision.complete("proceed".equals(choice.orElse(null)));
    });
    return decision.join();
  }

  private boolean showPreflightBlockedDialog(String actionLabel, String reason) {
    CompletableFuture<Boolean> decision = new CompletableFuture<>();
    Platform.runLater(() -> {
      Optional<String> choice = EditorDialogs.show(
          getScene() == null ? null : getScene().getWindow(),
          "Sign-in required",
          "The last attempt failed to authenticate with the remote repository:\n\n" + reason
              + "\n\nFix your credentials before trying again.",
          null,
          EditorDialogs.ActionSpec.neutral("cancel", "Cancel", null).defaultFocus(true),
          EditorDialogs.ActionSpec.accent("proceed", "Try Anyway", null));
      decision.complete("proceed".equals(choice.orElse(null)));
    });
    return decision.join();
  }

  private void runPull() {
    runAsync("Pull", () -> {
      if (projectRoot == null) {
        appendLog("Select a project before pulling.");
        return;
      }
      if (hasUncommittedChanges() && !confirmAutostashPull()) {
        appendLog("Pull cancelled.");
        return;
      }
      try {
        appendCommandResult(vcs.pullRebase(projectRoot, this::appendConsoleLine));
        notifyWorkingTreeChanged();
      } catch (GitVcsService.GitVcsException ex) {
        if (ex.hasMergeConflict()) {
          appendLog(ex.getMessage());
          appendLog("Resolve the conflicted files listed above in the editor, then use Stage on "
              + "each resolved file and Save Snapshot to continue, restore the pre-pull state "
              + "manually to back out, or use Force Pull (Danger Zone) to discard local history "
              + "and match the remote instead.");
        } else {
          appendLog(ex.getMessage());
        }
      } catch (Exception ex) {
        appendLog(ex.getMessage());
      }
      loadStatus(true);
    });
  }

  /** Must be called from the background worker thread. */
  private boolean hasUncommittedChanges() {
    CompletableFuture<Boolean> result = new CompletableFuture<>();
    Platform.runLater(() -> result.complete(!listChanges.getItems().isEmpty()));
    return result.join();
  }

  /**
   * Called from the background worker before a pull that will temporarily shelve uncommitted
   * changes. Must be called from the background worker thread.
   */
  private boolean confirmAutostashPull() {
    CompletableFuture<Boolean> decision = new CompletableFuture<>();
    Platform.runLater(() -> {
      boolean confirmed = EditorDialogs.confirm(
          getScene() == null ? null : getScene().getWindow(),
          "Pull With Local Changes",
          "You have uncommitted local changes. Getting updates will temporarily shelve them, "
              + "rebase your branch onto the incoming changes, then restore them. "
              + "If the rebase touches the same lines, restoring may fail and leave your changes "
              + "in the stash list instead of your working files.\n\nContinue?",
          "Get Updates",
          false);
      decision.complete(confirmed);
    });
    return decision.join();
  }

  private static final Set<String> PROTECTED_BRANCH_NAMES = Set.of("main", "master", "stable");

  private void runPush() {
    runAsync("Push", () -> {
      if (projectRoot == null) {
        appendLog("Select a project before pushing.");
        return;
      }
      if (!confirmPreflight("Send Online")) return;
      if (PROTECTED_BRANCH_NAMES.contains(currentBranch) && !confirmProtectedBranchPush(currentBranch)) {
        appendLog("Push cancelled.");
        return;
      }
      try {
        appendCommandResult(vcs.pushSafe(projectRoot, this::appendConsoleLine));
      } catch (Exception ex) {
        if (GitVcsService.isCredentialFailure(ex.getMessage())) {
          lastAuthFailureMs = System.currentTimeMillis();
          lastAuthFailureMessage = ex.getMessage();
        }
        appendLog(ex.getMessage());
        if (GitVcsService.isNonFastForwardRejection(ex.getMessage())) {
          appendLog("The remote has snapshots this branch doesn't have. Use Get Updates to pull "
              + "them in first, then Send Online again — or use Force Push (Danger Zone) if you "
              + "intend to overwrite the remote's history with yours.");
        }
      }
      loadStatus(true);
    });
  }

  /**
   * Called from the background worker before pushing directly to a protected branch
   * (main/master/stable). Must be called from the background worker thread.
   */
  private boolean confirmProtectedBranchPush(String branch) {
    CompletableFuture<Boolean> decision = new CompletableFuture<>();
    Platform.runLater(() -> {
      boolean confirmed = EditorDialogs.confirm(
          getScene() == null ? null : getScene().getWindow(),
          "Push to Protected Branch",
          "You're about to push directly to '" + branch + "'. This branch is typically shared "
              + "and often protected — consider using a feature branch and a pull request instead.\n\n"
              + "Push anyway?",
          "Push Anyway",
          true);
      decision.complete(confirmed);
    });
    return decision.join();
  }

  private void runForcePull() {
    runAsync("Force Pull", () -> {
      if (projectRoot == null) {
        appendLog("Select a project before force-pulling.");
        return;
      }
      if (!confirmDestructiveForceAction(
          "Force Pull",
          "Force pulling will make '" + currentBranch + "' match origin/" + currentBranch
              + " exactly. Any local commits not on the remote, and any uncommitted changes, "
              + "will be permanently discarded. This cannot be undone.",
          currentBranch)) {
        appendLog("Force pull cancelled.");
        return;
      }
      try {
        appendCommandResult(vcs.forcePull(projectRoot, this::appendConsoleLine));
        notifyWorkingTreeChanged();
      } catch (Exception ex) {
        appendLog(ex.getMessage());
      }
      loadStatus(true);
    });
  }

  private void runForcePush() {
    runAsync("Force Push", () -> {
      if (projectRoot == null) {
        appendLog("Select a project before force-pushing.");
        return;
      }
      if (!confirmPreflight("Force Push")) return;
      if (!confirmDestructiveForceAction(
          "Force Push",
          "Force pushing will overwrite the remote copy of '" + currentBranch + "' with your local "
              + "history. Commits on the remote that aren't in your local history will be lost for "
              + "anyone who hasn't already fetched them. This uses force-with-lease, so it will be "
              + "rejected if someone else pushed since your last fetch — but it can still discard "
              + "work once it succeeds.",
          currentBranch)) {
        appendLog("Force push cancelled.");
        return;
      }
      try {
        appendCommandResult(vcs.forcePush(projectRoot, this::appendConsoleLine));
      } catch (Exception ex) {
        if (GitVcsService.isCredentialFailure(ex.getMessage())) {
          lastAuthFailureMs = System.currentTimeMillis();
          lastAuthFailureMessage = ex.getMessage();
        }
        appendLog(ex.getMessage());
      }
      loadStatus(true);
    });
  }

  /**
   * Shows a type-to-confirm dialog requiring the user to type {@code confirmToken} exactly before
   * the destructive action button enables, for irreversible operations like force pull/push where
   * a plain Yes/No is too easy to click through. Must be called from the background worker thread.
   */
  private boolean confirmDestructiveForceAction(String title, String message, String confirmToken) {
    CompletableFuture<Boolean> decision = new CompletableFuture<>();
    Platform.runLater(() -> showTypeToConfirmDialog(title, message, confirmToken, decision));
    return decision.join();
  }

  private void showTypeToConfirmDialog(String title, String message, String confirmToken,
                                       CompletableFuture<Boolean> decision) {
    StackPane overlay = new StackPane();
    overlay.getStyleClass().add("editor-dialog-overlay");

    VBox card = new VBox(12);
    card.getStyleClass().add("editor-dialog-card");
    card.setMaxWidth(480);
    card.setFillWidth(true);
    card.setPadding(new Insets(16));

    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().add("editor-dialog-title");

    Label messageLabel = new Label(message);
    messageLabel.getStyleClass().add("editor-dialog-message");
    messageLabel.setWrapText(true);

    Label instructionLabel = new Label("Type \"" + confirmToken + "\" to confirm:");
    instructionLabel.getStyleClass().add("editor-dialog-field-label");

    TextField confirmField = new TextField();
    confirmField.getStyleClass().add("vcs-text-field");
    confirmField.setPromptText(confirmToken);

    Button cancelButton = new Button("Cancel");
    cancelButton.getStyleClass().addAll("editor-dialog-button", "editor-dialog-button-neutral");
    Button confirmButton = new Button(title);
    confirmButton.getStyleClass().addAll("editor-dialog-button", "editor-dialog-button-danger");
    confirmButton.setDisable(true);

    confirmField.textProperty().addListener((obs, oldVal, newVal) ->
        confirmButton.setDisable(!confirmToken.equals(newVal)));

    HBox actionsBox = new HBox(8, cancelButton, confirmButton);
    actionsBox.setAlignment(Pos.CENTER_RIGHT);

    card.getChildren().addAll(titleLabel, messageLabel, instructionLabel, confirmField, actionsBox);
    overlay.getChildren().add(card);
    overlay.setAlignment(Pos.CENTER);

    Stage stage = new Stage(StageStyle.TRANSPARENT);
    Window owner = getScene() == null ? null : getScene().getWindow();
    if (owner != null) {
      stage.initOwner(owner);
      stage.initModality(Modality.WINDOW_MODAL);
    } else {
      stage.initModality(Modality.APPLICATION_MODAL);
    }
    Scene scene = new Scene(overlay);
    scene.setFill(null);
    scene.getStylesheets().addAll(getScene() == null ? List.of() : getScene().getStylesheets());
    stage.setScene(scene);

    cancelButton.setOnAction(e -> {
      decision.complete(false);
      stage.close();
    });
    confirmButton.setOnAction(e -> {
      decision.complete(true);
      stage.close();
    });
    overlay.setOnMouseClicked(e -> {
      if (e.getTarget() == overlay) {
        decision.complete(false);
        stage.close();
      }
    });
    stage.setOnCloseRequest(e -> decision.complete(false));

    stage.show();
    Platform.runLater(confirmField::requestFocus);
  }

  private void runStash() {
    runAsync("Stash", () -> {
      if (projectRoot == null) return;
      try {
        appendCommandResult(vcs.stash(projectRoot, null));
      } catch (Exception ex) {
        appendLog("Stash failed: " + ex.getMessage());
      }
      loadStatus(false);
    });
  }

  private void runStashPop() {
    runAsync("Stash Pop", () -> {
      if (projectRoot == null) return;
      try {
        appendCommandResult(vcs.stashPop(projectRoot));
        notifyWorkingTreeChanged();
      } catch (Exception ex) {
        appendLog("Stash pop failed: " + ex.getMessage());
      }
      loadStatus(false);
    });
  }

  private void runStageSelected() {
    List<GitVcsService.StatusEntry> entries = selectedChangeEntries();
    if (entries.isEmpty()) return;
    runAsync("Stage", () -> {
      int changed = 0;
      try {
        for (GitVcsService.StatusEntry entry : entries) {
          vcs.stageFile(projectRoot, entry.path());
          changed++;
        }
        appendLog("Staged " + changed + " file" + plural(changed) + ".");
      } catch (Exception ex) {
        appendLog("Stage failed: " + ex.getMessage());
      }
      loadStatus(false);
    });
  }

  private void runUnstageSelected() {
    List<GitVcsService.StatusEntry> entries = selectedChangeEntries();
    if (entries.isEmpty()) return;
    runAsync("Unstage", () -> {
      int changed = 0;
      try {
        for (GitVcsService.StatusEntry entry : entries) {
          vcs.unstageFile(projectRoot, entry.path());
          changed++;
        }
        appendLog("Unstaged " + changed + " file" + plural(changed) + ".");
      } catch (Exception ex) {
        appendLog("Unstage failed: " + ex.getMessage());
      }
      loadStatus(false);
    });
  }

  private void runDiscardSelected() {
    List<GitVcsService.StatusEntry> entries = selectedChangeEntries();
    if (entries.isEmpty()) return;
    String target = entries.size() == 1
        ? entries.get(0).path()
        : entries.size() + " selected files";
    if (!EditorDialogs.confirm(
        getScene() == null ? null : getScene().getWindow(),
        entries.size() == 1 ? "Discard File Change" : "Discard Selected File Changes",
        "Discard changes in " + target + "? This cannot be undone.",
        "Discard",
        true)) {
      appendLog("Discard cancelled.");
      return;
    }
    runAsync("Discard", () -> {
      int changed = 0;
      try {
        for (GitVcsService.StatusEntry entry : entries) {
          vcs.discardFile(projectRoot, entry.path());
          changed++;
        }
        appendLog("Discarded " + changed + " file" + plural(changed) + ".");
      } catch (Exception ex) {
        appendLog("Discard failed: " + ex.getMessage());
      }
      loadStatus(false);
    });
  }

  private void runDiffSelected() {
    List<GitVcsService.StatusEntry> entries = selectedChangeEntries();
    if (entries.isEmpty()) return;
    runAsync("Diff", () -> {
      try {
        StringBuilder output = new StringBuilder();
        for (GitVcsService.StatusEntry entry : entries) {
          String diff = vcs.diffFile(projectRoot, entry.path());
          if (diff.isBlank()) {
            output.append("No diff available for: ").append(entry.path()).append("\n\n");
          } else {
            output.append("--- diff ").append(entry.path()).append(" ---\n").append(diff).append("\n\n");
          }
        }
        appendLog(output.toString().strip());
      } catch (Exception ex) {
        appendLog("Diff failed: " + ex.getMessage());
      }
    });
  }

  private void runSwitchBranch(String branchName) {
    runAsync("Switch branch", () -> {
      try {
        appendCommandResult(vcs.switchBranch(projectRoot, branchName));
        notifyWorkingTreeChanged();
      } catch (GitVcsService.GitVcsException ex) {
        if (ex.hasLocalChangesConflict()) {
          handleSwitchBranchConflict(branchName);
        } else {
          appendLog("Branch switch failed: " + ex.getMessage());
        }
      } catch (Exception ex) {
        appendLog("Branch switch failed: " + ex.getMessage());
      }
      loadStatus(false);
    });
  }

  /**
   * Called from the background worker after a switch fails because local changes would be
   * overwritten. Offers to stash them first, GitHub-Desktop style, then retries the switch.
   */
  private void handleSwitchBranchConflict(String branchName) {
    CompletableFuture<String> decision = new CompletableFuture<>();
    Platform.runLater(() -> {
      Optional<String> choice = EditorDialogs.show(
          getScene() == null ? null : getScene().getWindow(),
          "Local Changes Would Be Overwritten",
          "Switching to '" + branchName + "' would overwrite uncommitted local changes.\n\n"
              + "You can stash your changes first and switch, or cancel and handle them manually.",
          null,
          EditorDialogs.ActionSpec.neutral("cancel", "Cancel", null).defaultFocus(true),
          EditorDialogs.ActionSpec.accent("stash", "Stash Changes and Switch", null));
      decision.complete(choice.orElse(null));
    });
    if (!"stash".equals(decision.join())) {
      appendLog("Branch switch cancelled.");
      return;
    }
    try {
      appendCommandResult(vcs.stash(projectRoot, "Auto-stash before switching to " + branchName));
      appendCommandResult(vcs.switchBranch(projectRoot, branchName));
      notifyWorkingTreeChanged();
    } catch (Exception ex) {
      appendLog("Branch switch failed: " + ex.getMessage());
    }
  }

  private void runCreateBranch() {
    String name = cbBranch.getEditor() != null ? cbBranch.getEditor().getText() : null;
    if (name == null || name.isBlank()) name = cbBranch.getValue();
    if (name == null || name.isBlank()) {
      appendLog("Enter a branch name in the branch selector to create a new branch.");
      return;
    }
    String branchToCreate = name.trim();
    if (branchToCreate.equals(currentBranch)) {
      appendLog("Enter a new branch name in the branch selector to create a new branch (it currently shows your current branch: " + currentBranch + ").");
      return;
    }
    runAsync("Create branch", () -> {
      try {
        appendCommandResult(vcs.createBranch(projectRoot, branchToCreate));
        notifyWorkingTreeChanged();
      } catch (Exception ex) {
        appendLog("Create branch failed: " + ex.getMessage());
      }
      loadStatus(false);
    });
  }

  private void showAddRemoteDialog() {
    VBox content = new VBox(12);
    content.getStyleClass().add("editor-dialog-form");

    Label nameLabel = new Label("Remote Name");
    nameLabel.getStyleClass().add("editor-dialog-field-label");
    TextField nameField = new TextField("origin");
    nameField.getStyleClass().add("editor-dialog-text-field");
    nameField.setPromptText("origin");
    Label nameHint = new Label("Usually \"origin\" — only change if you know what you're doing");
    nameHint.getStyleClass().add("editor-dialog-message");

    Label urlLabel = new Label("Repository URL");
    urlLabel.getStyleClass().add("editor-dialog-field-label");
    TextField urlField = new TextField();
    urlField.getStyleClass().add("editor-dialog-text-field");
    urlField.setPromptText("https://github.com/username/repository.git");
    urlField.setPrefWidth(380);
    Label urlHint = new Label("Example: https://github.com/YourName/YourProject.git");
    urlHint.getStyleClass().add("editor-dialog-message");

    Label privacyNote = new Label(
        "\uD83D\uDD12 Public vs Private: Create your repository on GitHub/GitLab first, then paste the URL here.\n" +
        "The repository's visibility (public/private) is set on the hosting service, not in Git.");
    privacyNote.getStyleClass().add("editor-dialog-message");
    privacyNote.setWrapText(true);
    privacyNote.setMaxWidth(380);

    content.getChildren().addAll(
        nameLabel, nameField, nameHint,
        urlLabel, urlField, urlHint,
        privacyNote
    );
    Optional<String> result = EditorDialogs.show(
        getScene() == null ? null : getScene().getWindow(),
        "Add Git Remote",
        "Connect to a remote repository by pasting its Git URL.",
        content,
        urlField,
        EditorDialogs.ActionSpec.neutral("cancel", "Cancel", null),
        EditorDialogs.ActionSpec.accent("add", "Add Remote", null));
    if (result.isPresent() && "add".equals(result.get()) && !urlField.getText().trim().isBlank()) {
      runAddRemote(nameField.getText().trim(), urlField.getText().trim());
    }
  }

  private void showGitHubDeviceFlowDialog(Runnable onSaved) {
    VBox content = new VBox(10);
    content.getStyleClass().add("editor-dialog-form");

    Label statusLabel = new Label("Requesting a sign-in code from GitHub...");
    statusLabel.getStyleClass().add("editor-dialog-message");
    statusLabel.setWrapText(true);
    statusLabel.setMaxWidth(380);

    Label codeLabel = new Label("");
    codeLabel.getStyleClass().add("vcs-device-code");
    codeLabel.setVisible(false);
    codeLabel.setManaged(false);

    Button btnOpenBrowser = new Button("Open github.com/login/device");
    btnOpenBrowser.getStyleClass().addAll("vcs-action-button", "vcs-action-button-accent");
    btnOpenBrowser.setVisible(false);
    btnOpenBrowser.setManaged(false);

    Button btnCopyCode = new Button("Copy Code");
    btnCopyCode.getStyleClass().add("vcs-text-button");
    btnCopyCode.setVisible(false);
    btnCopyCode.setManaged(false);

    HBox actionsRow = new HBox(8, btnOpenBrowser, btnCopyCode);
    actionsRow.setAlignment(Pos.CENTER_LEFT);

    Button btnUseToken = new Button("Paste a personal access token instead");
    btnUseToken.getStyleClass().add("vcs-text-button");

    content.getChildren().addAll(statusLabel, codeLabel, actionsRow, btnUseToken);

    Timeline[] pollTimeline = new Timeline[1];
    boolean[] usingFallback = {false};

    btnUseToken.setOnAction(e -> {
      usingFallback[0] = true;
      if (pollTimeline[0] != null) pollTimeline[0].stop();
      Window dialogWindow = btnUseToken.getScene() == null ? null : btnUseToken.getScene().getWindow();
      if (dialogWindow instanceof javafx.stage.Stage stage) stage.close();
    });

    worker.submit(() -> {
      try {
        GitHubApiClient.DeviceCodeResponse device = githubApiClient.requestDeviceCode();
        Platform.runLater(() -> {
          statusLabel.setText("1. Click below to open GitHub. 2. Enter this code. 3. Approve the request.");
          codeLabel.setText(device.userCode());
          codeLabel.setVisible(true);
          codeLabel.setManaged(true);
          btnOpenBrowser.setVisible(true);
          btnOpenBrowser.setManaged(true);
          btnCopyCode.setVisible(true);
          btnCopyCode.setManaged(true);

          btnOpenBrowser.setOnAction(e -> {
            try {
              java.awt.Desktop.getDesktop().browse(new java.net.URI(device.verificationUri()));
            } catch (Exception ex) {
              appendLog("Could not open browser: " + ex.getMessage());
            }
          });
          btnCopyCode.setOnAction(e -> {
            javafx.scene.input.ClipboardContent clipboardContent = new javafx.scene.input.ClipboardContent();
            clipboardContent.putString(device.userCode());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(clipboardContent);
          });

          pollTimeline[0] = new Timeline(new KeyFrame(
              Duration.seconds(device.interval()),
              e -> pollDeviceFlowOnce(device, statusLabel, pollTimeline, usingFallback, onSaved)));
          pollTimeline[0].setCycleCount(Timeline.INDEFINITE);
          pollTimeline[0].play();
        });
      } catch (Exception ex) {
        Platform.runLater(() -> statusLabel.setText("Could not start sign-in: " + ex.getMessage()));
      }
    });

    EditorDialogs.show(
        getScene() == null ? null : getScene().getWindow(),
        "Sign in with GitHub",
        "Approve this device in your browser to connect your GitHub account.",
        content,
        EditorDialogs.ActionSpec.neutral("cancel", "Cancel", null));

    if (pollTimeline[0] != null) pollTimeline[0].stop();
    if (usingFallback[0]) showGitHubTokenDialog(onSaved);
  }

  private void pollDeviceFlowOnce(GitHubApiClient.DeviceCodeResponse device,
                                  Label statusLabel,
                                  Timeline[] pollTimeline,
                                  boolean[] usingFallback,
                                  Runnable onSaved) {
    worker.submit(() -> {
      try {
        GitHubApiClient.DeviceTokenResult result = githubApiClient.pollDeviceToken(device.deviceCode());
        Platform.runLater(() -> {
          switch (result.status()) {
            case PENDING -> { /* keep waiting */ }
            case SLOW_DOWN -> {
              if (pollTimeline[0] != null) pollTimeline[0].stop();
              pollTimeline[0] = new Timeline(new KeyFrame(
                  Duration.seconds(Math.max(5, result.retryIntervalSeconds())),
                  e -> pollDeviceFlowOnce(device, statusLabel, pollTimeline, usingFallback, onSaved)));
              pollTimeline[0].setCycleCount(Timeline.INDEFINITE);
              pollTimeline[0].play();
            }
            case EXPIRED -> {
              if (pollTimeline[0] != null) pollTimeline[0].stop();
              statusLabel.setText("The code expired before it was approved. Close this dialog and try again.");
            }
            case DENIED -> {
              if (pollTimeline[0] != null) pollTimeline[0].stop();
              statusLabel.setText("Sign-in was declined. Close this dialog and try again.");
            }
            case ERROR -> {
              if (pollTimeline[0] != null) pollTimeline[0].stop();
              statusLabel.setText(result.errorMessage() == null ? "GitHub sign-in failed." : result.errorMessage());
            }
            case SUCCESS -> {
              if (pollTimeline[0] != null) pollTimeline[0].stop();
              statusLabel.setText("Verifying...");
              finishDeviceFlowSignIn(result.accessToken(), statusLabel, onSaved);
            }
          }
        });
      } catch (Exception ex) {
        Platform.runLater(() -> statusLabel.setText("Sign-in check failed: " + ex.getMessage()));
      }
    });
  }

  private void finishDeviceFlowSignIn(String accessToken, Label statusLabel, Runnable onSaved) {
    worker.submit(() -> {
      try {
        if (!githubApiClient.verifyToken(accessToken)) {
          Platform.runLater(() -> statusLabel.setText("GitHub rejected the sign-in. Close this dialog and try again."));
          return;
        }
        githubTokenStore.saveToken(accessToken);
        appendLog("Signed in to GitHub.");
        Platform.runLater(() -> {
          updateGitHubTokenStatus();
          Window owner = statusLabel.getScene() == null ? null : statusLabel.getScene().getWindow();
          if (owner instanceof javafx.stage.Stage stage) stage.close();
          onSaved.run();
        });
      } catch (Exception ex) {
        Platform.runLater(() -> statusLabel.setText("Could not save sign-in: " + ex.getMessage()));
      }
    });
  }

  private void showGitHubTokenDialog(Runnable onSaved) {
    VBox content = new VBox(10);
    content.getStyleClass().add("editor-dialog-form");

    Label tokenLabel = new Label("GitHub Personal Access Token");
    tokenLabel.getStyleClass().add("editor-dialog-field-label");
    PasswordField tokenField = new PasswordField();
    tokenField.getStyleClass().add("editor-dialog-text-field");
    tokenField.setPromptText("ghp_...");
    tokenField.setPrefWidth(350);

    Label hint = new Label(
        "Create a token at github.com/settings/tokens with \"repo\" scope, then paste it here. "
            + "It is encrypted and stored locally on this machine.");
    hint.getStyleClass().add("editor-dialog-message");
    hint.setWrapText(true);
    hint.setMaxWidth(380);

    content.getChildren().addAll(tokenLabel, tokenField, hint);

    Optional<String> result = EditorDialogs.show(
        getScene() == null ? null : getScene().getWindow(),
        "Connect GitHub Account",
        "A personal access token lets the editor create repositories on your behalf.",
        content,
        tokenField,
        EditorDialogs.ActionSpec.neutral("cancel", "Cancel", null),
        EditorDialogs.ActionSpec.accent("save", "Save Token", null));

    if (result.isPresent() && "save".equals(result.get()) && !tokenField.getText().trim().isBlank()) {
      String token = tokenField.getText().trim();
      runAsync("Verify GitHub token", () -> {
        try {
          if (!githubApiClient.verifyToken(token)) {
            Platform.runLater(() -> EditorDialogs.warning(
                getScene() == null ? null : getScene().getWindow(),
                "Invalid Token",
                "GitHub rejected this token. Check that it is correct and has not expired."));
            return;
          }
          githubTokenStore.saveToken(token);
          appendLog("GitHub token saved.");
          Platform.runLater(() -> {
            updateGitHubTokenStatus();
            onSaved.run();
          });
        } catch (Exception ex) {
          appendLog("Could not verify GitHub token: " + ex.getMessage());
          Platform.runLater(() -> EditorDialogs.warning(
              getScene() == null ? null : getScene().getWindow(),
              "GitHub Connection Failed",
              ex.getMessage() == null ? "Could not reach GitHub." : ex.getMessage()));
        }
      });
    }
  }

  private void removeGitHubToken() {
    boolean confirmed = EditorDialogs.confirm(
        getScene() == null ? null : getScene().getWindow(),
        "Remove GitHub Token",
        "This removes the saved GitHub token from this machine. You can add a new one at any time.",
        "Remove",
        true);
    if (!confirmed) return;
    try {
      githubTokenStore.clearToken();
      appendLog("GitHub token removed.");
    } catch (Exception ex) {
      appendLog("Could not remove GitHub token: " + ex.getMessage());
    }
    updateGitHubTokenStatus();
  }

  private void updateGitHubTokenStatus() {
    boolean present = githubTokenStore.hasToken();
    applyGitHubTokenStatus(present, present ? null : false);
    if (!present) return;

    Optional<String> token = githubTokenStore.loadToken();
    if (token.isEmpty()) {
      applyGitHubTokenStatus(true, false);
      return;
    }
    try {
      worker.submit(() -> {
        boolean valid;
        try {
          valid = githubApiClient.verifyToken(token.get());
        } catch (Exception ex) {
          return;
        }
        boolean stillValid = valid;
        if (!disposed) Platform.runLater(() -> applyGitHubTokenStatus(true, stillValid));
      });
    } catch (RejectedExecutionException ex) {
      // Worker is shutting down; leave status as "checking...".
    }
  }

  /**
   * @param present whether a token is stored locally
   * @param valid   null while validity is still being checked; otherwise whether GitHub accepted it
   */
  private void applyGitHubTokenStatus(boolean present, Boolean valid) {
    String text;
    boolean ok;
    if (!present) {
      text = "GitHub: not connected";
      ok = false;
    } else if (valid == null) {
      text = "GitHub: checking...";
      ok = true;
    } else if (valid) {
      text = "GitHub: connected";
      ok = true;
    } else {
      text = "GitHub: not authorized";
      ok = false;
    }
    githubTokenStatusLabel.setText(text);
    githubTokenStatusLabel.getStyleClass().removeAll("vcs-remote-configured", "vcs-remote-missing");
    githubTokenStatusLabel.getStyleClass().add(ok ? "vcs-remote-configured" : "vcs-remote-missing");
    btnChangeGitHubToken.setText(present ? "Change" : "Sign In");
    btnRemoveGitHubToken.setVisible(present);
    btnRemoveGitHubToken.setManaged(present);
  }

  private void showCreateGitHubRepoDialog(boolean isPrivate) {
    if (!githubTokenStore.hasToken()) {
      showGitHubDeviceFlowDialog(() -> showCreateGitHubRepoDialog(isPrivate));
      return;
    }

    VBox content = new VBox(10);
    content.getStyleClass().add("editor-dialog-form");

    Label nameLabel = new Label("Repository Name");
    nameLabel.getStyleClass().add("editor-dialog-field-label");
    String defaultName = projectRoot != null ? projectRoot.getName() : "my-project";
    TextField repoNameField = new TextField(defaultName);
    repoNameField.getStyleClass().add("editor-dialog-text-field");
    repoNameField.setPrefWidth(350);

    Label visLabel = new Label(isPrivate
        ? "\uD83D\uDD12 This repository will be private — only you and collaborators can see it."
        : "\uD83C\uDF10 This repository will be public — anyone can see it.");
    visLabel.getStyleClass().addAll("vcs-dialog-visibility", isPrivate ? "vcs-dialog-visibility-private" : "vcs-dialog-visibility-public");
    visLabel.setWrapText(true);
    visLabel.setMaxWidth(350);

    content.getChildren().addAll(nameLabel, repoNameField, visLabel);

    Optional<String> result = EditorDialogs.show(
        getScene() == null ? null : getScene().getWindow(),
        "Create GitHub Repository",
        "This will create a new repository on your GitHub account, set it as the remote, and push your code.",
        content,
        repoNameField,
        EditorDialogs.ActionSpec.neutral("cancel", "Cancel", null),
        EditorDialogs.ActionSpec.accent("create", isPrivate ? "Create Private Repo" : "Create Public Repo", null));
    if (result.isPresent() && "create".equals(result.get()) && !repoNameField.getText().trim().isBlank()) {
      runCreateGitHubRepo(repoNameField.getText().trim(), isPrivate);
    }
  }

  private void runCreateGitHubRepo(String repoName, boolean isPrivate) {
    runAsync("Create GitHub repo", () -> {
      try {
        Optional<String> token = githubTokenStore.loadToken();
        if (token.isEmpty()) {
          appendLog("Create GitHub repo failed: no GitHub token configured.");
          loadStatus(true);
          return;
        }

        appendLog("Creating " + (isPrivate ? "private" : "public") + " GitHub repository: " + repoName + "...");
        GitHubApiClient.CreatedRepo created = githubApiClient.createRepository(token.get(), repoName, isPrivate);
        appendLog("Repository created successfully on GitHub: " + created.htmlUrl());
        appendLog("Setting up remote 'origin'...");
        appendCommandResult(vcs.addRemote(projectRoot, "origin", created.cloneUrl()));

        appendLog("Pushing code to GitHub...");
        try {
          appendCommandResult(vcs.pushSafe(projectRoot));
          appendLog("Code pushed successfully! Your project is now on GitHub.");
        } catch (Exception pushEx) {
          appendLog("⚠ " + pushEx.getMessage());
          appendLog("\nRepository was created but initial push failed.");
          appendLog("You can push manually later using the Push button.");
        }
      } catch (Exception ex) {
        appendLog("Create GitHub repo failed: " + ex.getMessage());
      }
      loadStatus(true);
    });
  }

  private void runAddRemote(String name, String url) {
    runAsync("Add remote", () -> {
      try {
        appendCommandResult(vcs.addRemote(projectRoot, name, url));
        appendLog("Remote '" + name + "' added: " + url);
      } catch (Exception ex) {
        appendLog("Add remote failed: " + ex.getMessage());
      }
      loadStatus(true);
    });
  }

  private void confirmRemoveRemote() {
    boolean confirmed = EditorDialogs.confirm(
        getScene() == null ? null : getScene().getWindow(),
        "Remove Remote",
        "This disconnects 'origin' from this project. Your local commits and history are kept; "
            + "you can add a remote again at any time.",
        "Remove",
        true);
    if (!confirmed) return;
    runRemoveRemote();
  }

  private void runRemoveRemote() {
    runAsync("Remove remote", () -> {
      try {
        appendCommandResult(vcs.removeRemote(projectRoot, "origin"));
      } catch (Exception ex) {
        appendLog("Remove remote failed: " + ex.getMessage());
      }
      loadStatus(true);
    });
  }

  private void appendCommandResult(GitVcsService.CommandResult result) {
    if (result == null) return;
    String output = result.output();
    if (output == null || output.isBlank()) {
      appendLog(result.commandLine() + " (exit " + result.exitCode() + ")");
      return;
    }
    appendLog(result.commandLine() + "\n" + output);
  }

  private void openChangedEntry(GitVcsService.StatusEntry entry) {
    if (entry == null || onOpenRelativePath == null) return;
    String relative = entry.path();
    if (relative == null || relative.isBlank()) return;
    if (relative.contains(" -> ")) {
      String[] parts = relative.split(" -> ", 2);
      relative = parts.length == 2 ? parts[1] : relative;
    }
    onOpenRelativePath.accept(relative);
  }

  private List<GitVcsService.StatusEntry> selectedChangeEntries() {
    return List.copyOf(listChanges.getSelectionModel().getSelectedItems());
  }

  private void updateToolAvailabilityLabel(boolean git) {
    toolLabel.setText("Git: " + (git ? "ok" : "missing"));
  }

  private void runAsync(String actionName, Runnable action) {
    if (busy || disposed) return;
    setBusy(true, actionName);
    try {
      worker.submit(() -> {
        try {
          action.run();
        } catch (Exception ex) {
          appendLog(actionName + " failed: " + ex.getMessage());
        } finally {
          Platform.runLater(() -> setBusy(false, null));
        }
      });
    } catch (RejectedExecutionException ex) {
      setBusy(false, null);
    }
  }

  private void setBusy(boolean busy) {
    setBusy(busy, null);
  }

  private void setBusy(boolean busy, String actionName) {
    this.busy = busy;
    this.busyActionName = busy && actionName != null ? actionName : "";
    updateInitializingOverlay();
    updateControlsForState();
  }

  private void markStatusLoaded() {
    statusLoaded = true;
    updateInitializingOverlay();
  }

  private void updateInitializingOverlay() {
    boolean initializingRepository = busyActionName.toLowerCase(Locale.ROOT).contains("initialize");
    boolean show = busy && (!statusLoaded || initializingRepository);
    if (show) {
      if (initializingRepository) {
        initializingTitleLabel.setText("Initializing repository");
        initializingBodyLabel.setText("Creating Git tracking and preparing the first project snapshot.");
      } else {
        initializingTitleLabel.setText("Initializing version control");
        initializingBodyLabel.setText("Reading repository status, branches, changed files, and online state.");
      }
    }
    initializingOverlay.setVisible(show);
    initializingOverlay.setManaged(show);
  }

  private void updateControlsForState() {
    boolean hasProject = projectRoot != null;
    boolean repoReady = hasProject && gitAvailable && repositoryInitialized;
    repositoryTools.setVisible(repoReady);
    repositoryTools.setManaged(repoReady);
    nextStepLabel.setVisible(!initBox.isVisible());
    nextStepLabel.setManaged(!initBox.isVisible());
    boolean hasSelection = !listChanges.getSelectionModel().getSelectedItems().isEmpty();
    boolean hasChanges = currentChangeCount > 0;
    boolean canSync = repoReady && currentHasRemote && !currentHasConflicts;

    btnRefresh.setDisable(busy);
    btnInitialize.setDisable(busy || !hasProject || !gitAvailable || repositoryInitialized);
    btnFetch.setDisable(busy || !repoReady || !currentHasRemote);
    btnPull.setDisable(busy || !canSync);
    btnPush.setDisable(busy || !canSync || currentBehind > 0 || (currentAhead == 0 && currentHasUpstream));
    btnForcePull.setDisable(busy || !repoReady || !currentHasRemote);
    btnForcePush.setDisable(busy || !repoReady || !currentHasRemote);
    btnCommit.setDisable(busy || !repoReady || !hasChanges || currentHasConflicts);
    btnStash.setDisable(busy || !repoReady || !hasChanges);
    btnStashPop.setDisable(busy || !repoReady);
    btnStageSelected.setDisable(busy || !repoReady || !hasSelection);
    btnUnstageSelected.setDisable(busy || !repoReady || !hasSelection);
    btnDiscardSelected.setDisable(busy || !repoReady || !hasSelection);
    btnDiffSelected.setDisable(busy || !repoReady || !hasSelection);
    cbBranch.setDisable(busy || !repoReady);
    btnNewBranch.setDisable(busy || !repoReady);
    txtCommitMessage.setDisable(busy || !repoReady);
    listChanges.setDisable(!repoReady);
    chkInitCommit.setDisable(busy || !hasProject || repositoryInitialized);
    btnConfigureRemote.setDisable(busy || !repoReady);
    btnRemoveRemote.setDisable(busy || !repoReady);
    updateActionEmphasis();
    updateGuidance();
  }

  private void setInitControlsVisible(boolean visible, String hintText) {
    initHintLabel.setText((hintText == null || hintText.isBlank())
        ? "This project has no Git repository. Click Initialize to enable version control."
        : hintText);
    // Control entire initBox visibility
    initBox.setVisible(visible);
    initBox.setManaged(visible);
  }

  private void appendLog(String message) {
    if (message == null || message.isBlank()) return;
    Platform.runLater(() -> {
      if (!txtLog.getText().isBlank()) txtLog.appendText("\n\n");
      txtLog.appendText(message.trim());
      txtLog.setScrollTop(Double.MAX_VALUE);
    });
  }

  /**
   * Appends a single streamed progress line (e.g. from a JGit {@code ProgressMonitor} callback
   * during a fetch/pull/push) as its own console line, without the blank-line separation used
   * between discrete log entries. Safe to call from any thread.
   */
  private void appendConsoleLine(String line) {
    if (line == null || line.isBlank()) return;
    Platform.runLater(() -> {
      if (!txtLog.getText().isBlank()) txtLog.appendText("\n");
      txtLog.appendText("> " + line.trim());
      txtLog.setScrollTop(Double.MAX_VALUE);
    });
  }

  private String safe(String value) {
    return value == null || value.isBlank() ? "--" : value;
  }

  private String safeMessage(Throwable ex) {
    if (ex == null) return "Unknown error";
    String message = ex.getMessage();
    return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message.trim();
  }

  /** Hides the floating guidance popup without disposing the view, e.g. when its tab loses focus. */
  public void hideGuidePopup() {
    guideTabActive = false;
    guidePopup.hide();
  }

  /** Marks the guidance popup as eligible to show again, e.g. when its tab regains focus. */
  public void showGuidePopupIfActive() {
    guideTabActive = true;
    updateGuidance();
  }

  public void dispose() {
    if (disposed) return;
    disposed = true;
    guidePopup.hide();
    clearGuideTargets();
    if (autoRefreshTimer != null) {
      autoRefreshTimer.stop();
      autoRefreshTimer = null;
    }
    worker.shutdownNow();
  }

  private record GuidanceStep(String key, List<Node> targets, String title, String body, String tone) {}

  private static final class StatusCell extends ListCell<GitVcsService.StatusEntry> {
    @Override
    protected void updateItem(GitVcsService.StatusEntry item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setText(null);
        getStyleClass().removeAll(
            "vcs-status-untracked",
            "vcs-status-modified",
            "vcs-status-added",
            "vcs-status-deleted",
            "vcs-status-renamed",
            "vcs-status-conflict");
        return;
      }

      setText(friendlyStatus(item) + " - " + item.path());
      getStyleClass().removeAll(
          "vcs-status-untracked",
          "vcs-status-modified",
          "vcs-status-added",
          "vcs-status-deleted",
          "vcs-status-renamed",
          "vcs-status-conflict");

      String code = item.code().toUpperCase(Locale.ROOT);
      if (isConflict(item)) {
        getStyleClass().add("vcs-status-conflict");
      } else if (item.isUntracked()) {
        getStyleClass().add("vcs-status-untracked");
      } else if (code.contains("M")) {
        getStyleClass().add("vcs-status-modified");
      } else if (code.contains("A")) {
        getStyleClass().add("vcs-status-added");
      } else if (code.contains("D")) {
        getStyleClass().add("vcs-status-deleted");
      } else if (code.contains("R") || code.contains("C")) {
        getStyleClass().add("vcs-status-renamed");
      }
    }
  }

  private static final class GraphCell extends ListCell<GitVcsService.ChangeGraphEntry> {
    private static final double ROW_HEIGHT = 36.0;
    private static final double GRAPH_STEP = 8.0;
    private static final double GRAPH_LEFT = 12.0;

    @Override
    protected void updateItem(GitVcsService.ChangeGraphEntry item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setText(null);
        setGraphic(null);
        return;
      }

      Pane graphPane = new Pane();
      graphPane.getStyleClass().add("vcs-graph-lane");
      graphPane.setMinHeight(ROW_HEIGHT);
      graphPane.setPrefHeight(ROW_HEIGHT);
      graphPane.setMinWidth(graphWidth(item.graphPrefix()));
      graphPane.setPrefWidth(graphWidth(item.graphPrefix()));
      drawGraphPrefix(graphPane, item.graphPrefix(), hasHeadRef(item.refs()));

      Label subjectLabel = new Label(item.subject() == null || item.subject().isBlank() ? "(no message)" : item.subject());
      subjectLabel.getStyleClass().add("vcs-graph-subject");
      subjectLabel.setMaxWidth(Double.MAX_VALUE);

      String author = item.author() == null || item.author().isBlank() ? "unknown" : item.author();
      Label metaLabel = new Label(item.hash() + "  " + author);
      metaLabel.getStyleClass().add("vcs-graph-meta");

      VBox textColumn = new VBox(2, subjectLabel, metaLabel);
      textColumn.setAlignment(Pos.CENTER_LEFT);
      HBox.setHgrow(textColumn, Priority.ALWAYS);

      HBox refsBox = buildRefs(item.refs());
      Region spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);

      HBox row = new HBox(8, graphPane, textColumn, spacer, refsBox);
      row.getStyleClass().add("vcs-graph-row");
      row.setAlignment(Pos.CENTER_LEFT);
      row.setMinHeight(ROW_HEIGHT);

      setText(null);
      setGraphic(row);
    }

    private static double graphWidth(String graphPrefix) {
      int length = graphPrefix == null || graphPrefix.isBlank() ? 2 : graphPrefix.length();
      return Math.max(42.0, GRAPH_LEFT + (length * GRAPH_STEP) + 12.0);
    }

    private static void drawGraphPrefix(Pane graphPane, String graphPrefix, boolean head) {
      String prefix = graphPrefix == null || graphPrefix.isBlank() ? "* " : graphPrefix;
      boolean drewCommit = false;
      for (int i = 0; i < prefix.length(); i++) {
        char c = prefix.charAt(i);
        double x = GRAPH_LEFT + (i * GRAPH_STEP);
        if (c == '|') {
          addLine(graphPane, x, 0.0, x, ROW_HEIGHT, "vcs-graph-line");
        } else if (c == '*') {
          addLine(graphPane, x, 0.0, x, ROW_HEIGHT, "vcs-graph-line-active");
          Circle dot = new Circle(x, ROW_HEIGHT / 2.0, head ? 6.0 : 5.0);
          dot.getStyleClass().add(head ? "vcs-graph-dot-head" : "vcs-graph-dot");
          graphPane.getChildren().add(dot);
          drewCommit = true;
        } else if (c == '/') {
          addLine(graphPane, x + GRAPH_STEP, 0.0, x, ROW_HEIGHT, "vcs-graph-line-branch");
        } else if (c == '\\') {
          addLine(graphPane, x, 0.0, x + GRAPH_STEP, ROW_HEIGHT, "vcs-graph-line-branch");
        }
      }
      if (!drewCommit) {
        double x = GRAPH_LEFT;
        addLine(graphPane, x, 0.0, x, ROW_HEIGHT, "vcs-graph-line");
      }
    }

    private static void addLine(Pane graphPane,
                                double startX,
                                double startY,
                                double endX,
                                double endY,
                                String styleClass) {
      Line line = new Line(startX, startY, endX, endY);
      line.getStyleClass().add(styleClass);
      graphPane.getChildren().add(line);
    }

    private static HBox buildRefs(String refs) {
      HBox box = new HBox(4);
      box.setAlignment(Pos.CENTER_RIGHT);
      if (refs == null || refs.isBlank()) return box;
      int added = 0;
      for (String raw : refs.split(",")) {
        String ref = cleanRef(raw);
        if (ref.isBlank()) continue;
        Label chip = new Label(ref);
        chip.getStyleClass().addAll("vcs-graph-ref", refStyle(ref));
        box.getChildren().add(chip);
        added++;
        if (added >= 3) break;
      }
      return box;
    }

    private static String cleanRef(String raw) {
      String ref = raw == null ? "" : raw.trim();
      if (ref.startsWith("HEAD -> ")) return ref.substring("HEAD -> ".length()).trim();
      if (ref.startsWith("tag: ")) return ref.substring("tag: ".length()).trim();
      return ref;
    }

    private static String refStyle(String ref) {
      if (ref == null) return "vcs-graph-ref-local";
      String lower = ref.toLowerCase(Locale.ROOT);
      if (lower.startsWith("origin/") || lower.startsWith("upstream/")) return "vcs-graph-ref-remote";
      if (lower.startsWith("refs/tags/")) return "vcs-graph-ref-tag";
      return "vcs-graph-ref-local";
    }

    private static boolean hasHeadRef(String refs) {
      return refs != null && refs.contains("HEAD");
    }
  }

  private static String friendlyStatus(GitVcsService.StatusEntry item) {
    if (isConflict(item)) return "Conflict";
    if (item.isUntracked()) return "New";
    String code = item.code().toUpperCase(Locale.ROOT);
    if (code.contains("A")) return "Added";
    if (code.contains("D")) return "Deleted";
    if (code.contains("R")) return "Renamed";
    if (code.contains("C")) return "Copied";
    if (code.contains("M")) return "Changed";
    return "Updated";
  }

  private static boolean isConflict(GitVcsService.StatusEntry item) {
    String index = item.indexStatus();
    String workTree = item.workTreeStatus();
    return "U".equals(index)
        || "U".equals(workTree)
        || ("D".equals(index) && "D".equals(workTree))
        || ("A".equals(index) && "A".equals(workTree));
  }
}
