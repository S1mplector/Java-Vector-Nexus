package com.jvn.editor.ui.actioneditor.toolbar;

import com.jvn.editor.ui.actioneditor.ActionEditorDialogOverlay;
import com.jvn.editor.ui.RefreshIcon;
import com.jvn.editor.ui.actioneditor.AnimatedToolbarPane;
import com.jvn.editor.ui.actioneditor.CollapsibleToolbarCluster;
import com.jvn.editor.ui.actioneditor.PuppeteerWindow;

import javafx.geometry.Pos;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Builds the menu-bar command strip at the top of PuppeteerWindow's toolbar.
 * Extracted from PuppeteerWindow to reduce that class's line count.
 */
public final class PuppeteerToolbarFactory {

  private PuppeteerToolbarFactory() {}

  /**
   * Builds and returns the HBox containing the full MenuBar for PuppeteerWindow.
   * All action handlers delegate back to {@code w}.
   */
  public static HBox buildToolbarCommandBar(PuppeteerWindow w) {
    // === File menu ===
    MenuItem miSaveRegister = new MenuItem("Save & Register");
    miSaveRegister.setOnAction(e -> w.requestRegisterTimeline());
    MenuItem miVerifyRuntime = new MenuItem("Verify Runtime Registration...");
    miVerifyRuntime.setOnAction(e -> w.showRuntimeVerificationReport());
    MenuItem miRefreshCode = new MenuItem("Refresh Generated Code");
    miRefreshCode.setGraphic(RefreshIcon.of(16));
    miRefreshCode.setOnAction(e -> w.requestRefreshGeneratedCode());
    MenuItem miStagePreview = new MenuItem("Stage Code Preview");
    miStagePreview.setOnAction(e -> w.stagePreviewFromCode());
    MenuItem miCommitPreview = new MenuItem("Commit Staged Preview");
    miCommitPreview.setOnAction(e -> w.commitStagedPreview());
    MenuItem miDiscardPreview = new MenuItem("Discard Staged Preview");
    miDiscardPreview.setOnAction(e -> w.discardStagedPreview());
    MenuItem miCopyExportCode = new MenuItem("Copy Exported Code");
    miCopyExportCode.setOnAction(e -> w.copyExportedCodeToClipboard());
    MenuItem miSaveClip = new MenuItem("Save Selection as Clip");
    miSaveClip.setOnAction(e -> w.saveSelectionAsClip());
    MenuItem miLoadClip = new MenuItem("Load Clip at Playhead");
    miLoadClip.setOnAction(e -> w.loadAndApplyClip());
    MenuItem miImportAssets = new MenuItem("Import Assets...");
    miImportAssets.setOnAction(e -> w.showAssetImporterWindow());
    MenuItem miRecordGif = new MenuItem("Record Preview as GIF...");
    miRecordGif.setOnAction(e -> w.showRecordGifDialog());
    MenuItem miClose = new MenuItem("Close Puppeteer");
    miClose.setOnAction(e -> w.requestWindowClose());

    Menu fileMenu = new Menu("File");
    fileMenu.getItems().add(w.buildWorkflowMenu());
    fileMenu.getItems().addAll(
        miSaveRegister,
        miVerifyRuntime,
        new SeparatorMenuItem(),
        miRefreshCode,
        miStagePreview,
        miCommitPreview,
        miDiscardPreview,
        new SeparatorMenuItem(),
        miCopyExportCode,
        miSaveClip,
        miLoadClip,
        miImportAssets,
        miRecordGif,
        new SeparatorMenuItem(),
        miClose
    );
    fileMenu.setOnShowing(e -> {
      String timelineName = w.tfTimelineName != null ? w.tfTimelineName.getText().trim() : "";
      boolean hasTimelineName = !timelineName.isBlank();
      boolean hasTrack = w.selectedTrackForEditing(false) != null;
      miSaveRegister.setText(w.dirty || w.previewStaged ? "Save & Register" : "Save & Register Again");
      miSaveRegister.setDisable(!hasTimelineName);
      miVerifyRuntime.setDisable(w.project == null);
      miStagePreview.setText(w.previewStaged ? "Restage Code Preview" : "Stage Code Preview");
      miStagePreview.setDisable(w.codePreview == null || w.codePreview.getCode() == null || w.codePreview.getCode().isBlank());
      miCommitPreview.setDisable(!w.previewStaged);
      miDiscardPreview.setDisable(!w.previewStaged);
      miCopyExportCode.setDisable(w.codePreview == null || w.codePreview.getCode() == null || w.codePreview.getCode().isBlank());
      miSaveClip.setDisable(!hasTrack || w.projectRoot == null);
      miLoadClip.setDisable(!hasTrack || !w.hasSavedClips());
      miImportAssets.setDisable(w.projectRoot == null || !w.projectRoot.isDirectory());
      miClose.setText(w.dirty || w.previewStaged ? "Close..." : "Close");
    });

    // === Edit menu ===
    MenuItem miUndo = new MenuItem("Undo");
    miUndo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN));
    miUndo.setOnAction(e -> w.executeUndo());
    MenuItem miRedo = new MenuItem("Redo");
    miRedo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    miRedo.setOnAction(e -> w.executeRedo());
    MenuItem miAddKeyframe = new MenuItem("Add Keyframe at Playhead");
    miAddKeyframe.setOnAction(e -> w.timelinePanel.addKeyframeAtPlayhead());
    MenuItem miDeleteKeyframes = new MenuItem("Delete Selected Keyframes");
    miDeleteKeyframes.setOnAction(e -> w.timelinePanel.deleteSelectedKeyframe());
    MenuItem miCopyKeyframes = new MenuItem("Copy Keyframes");
    miCopyKeyframes.setOnAction(e -> w.copySelectedKeyframesToClipboard());
    MenuItem miPasteKeyframes = new MenuItem("Paste Keyframes at Playhead");
    miPasteKeyframes.setOnAction(e -> w.pasteCopiedKeyframesAtPlayhead());
    MenuItem miDuplicateKeyframes = new MenuItem("Duplicate Keyframes");
    miDuplicateKeyframes.setOnAction(e -> w.duplicateSelectedKeyframesBySnapStep());
    MenuItem miDistributeKeyframes = new MenuItem("Distribute Selected Keyframes");
    miDistributeKeyframes.setOnAction(e -> w.timelinePanel.distributeSelectedKeyframes());
    MenuItem miReverseKeyframes = new MenuItem("Reverse Selected Keyframes");
    miReverseKeyframes.setOnAction(e -> w.timelinePanel.reverseSelectedKeyframes());
    MenuItem miApplyPreset = new MenuItem("Apply Animation Preset...");
    miApplyPreset.setOnAction(e -> w.showPresetMenuOverlay());
    MenuItem miPlaceInSlot = new MenuItem("Place Entity in VN Slot...");
    miPlaceInSlot.setOnAction(e -> w.showSlotMenuOverlay());
    MenuItem miEyeFocus = new MenuItem("Eye Focus / Look At...");
    miEyeFocus.setOnAction(e -> w.showEyeFocusOverlay());

    Menu editMenu = new Menu("Edit");
    editMenu.getItems().addAll(
        miUndo, miRedo,
        new SeparatorMenuItem(),
        miAddKeyframe, miDeleteKeyframes,
        new SeparatorMenuItem(),
        miCopyKeyframes, miPasteKeyframes, miDuplicateKeyframes,
        new SeparatorMenuItem(),
        miDistributeKeyframes, miReverseKeyframes,
        new SeparatorMenuItem(),
        miApplyPreset, miPlaceInSlot, miEyeFocus
    );
    editMenu.setOnShowing(e -> {
      int selectionCount = w.timelinePanel != null ? w.timelinePanel.getSelectionCount() : 0;
      boolean hasSelection = selectionCount > 0;
      boolean hasTarget = w.timelinePanel != null
          && w.timelinePanel.getSelectedEntity() != null
          && !w.timelinePanel.getSelectedEntity().isBlank();
      boolean entityTarget = hasTarget && !w.timelinePanel.isSelectedGroup();
      boolean hasEditableKeyframe = hasSelection
          || (w.keyframeEditor != null && w.keyframeEditor.getCurrentKeyframe() != null);

      miUndo.setText(w.commandStack.canUndo() ? "Undo " + w.commandStack.undoDescription() : "Undo");
      miRedo.setText(w.commandStack.canRedo() ? "Redo " + w.commandStack.redoDescription() : "Redo");
      miUndo.setDisable(!w.commandStack.canUndo());
      miRedo.setDisable(!w.commandStack.canRedo());
      miAddKeyframe.setDisable(!hasTarget);
      miDeleteKeyframes.setDisable(!hasEditableKeyframe);
      miCopyKeyframes.setDisable(!hasSelection);
      miPasteKeyframes.setDisable(!hasTarget || w.timelinePanel.getCopiedKeyframeCount() == 0);
      miDuplicateKeyframes.setDisable(!hasSelection);
      miDistributeKeyframes.setDisable(selectionCount < 3);
      miReverseKeyframes.setDisable(selectionCount < 2);
      miApplyPreset.setDisable(!entityTarget);
      miPlaceInSlot.setDisable(!entityTarget);
      miEyeFocus.setDisable(w.project == null);
    });

    // === Timeline menu ===
    MenuItem miJumpStart = new MenuItem("Jump to Start");
    miJumpStart.setAccelerator(new KeyCodeCombination(KeyCode.HOME));
    miJumpStart.setOnAction(e -> w.rewind());

    MenuItem miJumpEnd = new MenuItem("Jump to End");
    miJumpEnd.setAccelerator(new KeyCodeCombination(KeyCode.END));
    miJumpEnd.setOnAction(e -> {
      if (w.project == null) return;
      w.project.setPlayheadMs(w.project.getTotalDurationMs());
      w.updateTimeLabel();
      w.updatePreview();
      w.timelinePanel.refresh();
    });

    MenuItem miTimelinePrevKey = new MenuItem("Previous Keyframe");
    miTimelinePrevKey.setAccelerator(new KeyCodeCombination(KeyCode.LEFT, KeyCombination.SHORTCUT_DOWN));
    miTimelinePrevKey.setOnAction(e -> {
      if (w.timelinePanel.jumpPlayheadToPreviousKeyframe()) { w.updateTimeLabel(); w.updatePreview(); }
      w.refreshSidebarTabs();
    });

    MenuItem miTimelineNextKey = new MenuItem("Next Keyframe");
    miTimelineNextKey.setAccelerator(new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.SHORTCUT_DOWN));
    miTimelineNextKey.setOnAction(e -> {
      if (w.timelinePanel.jumpPlayheadToNextKeyframe()) { w.updateTimeLabel(); w.updatePreview(); }
      w.refreshSidebarTabs();
    });

    MenuItem miTimelineFocusSel = new MenuItem("Focus Timeline on Selection");
    miTimelineFocusSel.setOnAction(e -> w.timelinePanel.zoomToSelection());
    MenuItem miTimelineZoomFit = new MenuItem("Zoom Timeline to Fit");
    miTimelineZoomFit.setOnAction(e -> w.timelinePanel.zoomToFit());

    Menu timelineMenu = new Menu("Timeline");
    timelineMenu.getItems().addAll(
        miJumpStart, miJumpEnd,
        new SeparatorMenuItem(),
        miTimelinePrevKey, miTimelineNextKey,
        new SeparatorMenuItem(),
        miTimelineFocusSel, miTimelineZoomFit
    );
    timelineMenu.setOnShowing(e -> {
      boolean hasTarget = w.timelinePanel != null && w.timelinePanel.getSelectedEntity() != null
          && !w.timelinePanel.getSelectedEntity().isBlank();
      boolean hasSelection = w.timelinePanel != null && w.timelinePanel.getSelectionCount() > 0;
      miTimelinePrevKey.setDisable(!hasTarget);
      miTimelineNextKey.setDisable(!hasTarget);
      miTimelineFocusSel.setDisable(!hasSelection);
    });

    // === Scene menu ===
    MenuItem miSceneImport = new MenuItem("Import Assets...");
    miSceneImport.setOnAction(e -> w.showAssetImporterWindow());

    MenuItem miSceneAddCue = new MenuItem("Add Audio Cue at Playhead");
    miSceneAddCue.setOnAction(e -> w.showAddAudioCueDialog());

    MenuItem miSceneClearCues = new MenuItem("Clear All Audio Cues");
    miSceneClearCues.setOnAction(e -> {
      if (w.project == null || w.project.getAudioCues().isEmpty()) return;
      w.overlayDialog.showDialog("Clear Audio Cues",
          "Remove all audio cues from this animation? This cannot be undone from the cue panel.",
          null,
          ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", w.overlayDialog::hideOverlay).defaultFocus(true),
          ActionEditorDialogOverlay.ActionSpec.danger("Clear", () -> {
            w.project.clearAudioCues();
            w.timelinePanel.refresh();
            w.refreshExportPreviewAndMarkDirty();
          }));
    });

    MenuItem miSceneManageEvents = new MenuItem("Manage Event Cues...");
    miSceneManageEvents.setOnAction(e -> w.showEventCueManagerDialog(null));

    MenuItem miSceneClearEvents = new MenuItem("Clear All Event Cues");
    miSceneClearEvents.setOnAction(e -> {
      if (w.project == null || w.project.getEditorEventCues().isEmpty()) return;
      w.overlayDialog.showDialog("Clear Event Cues",
          "Remove all timeline event cues from this animation?",
          null,
          ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", w.overlayDialog::hideOverlay).defaultFocus(true),
          ActionEditorDialogOverlay.ActionSpec.danger("Clear", () -> {
            w.project.clearEditorEventCues();
            w.timelinePanel.refresh();
            w.updatePreview();
            w.refreshExportPreviewAndMarkDirty();
          }));
    });

    MenuItem miSceneFitPreview = new MenuItem("Fit Preview Viewport");
    miSceneFitPreview.setOnAction(e -> w.animationPreview.fitToContent());

    Menu sceneMenu = new Menu("Scene");
    sceneMenu.getItems().addAll(
        miSceneImport,
        new SeparatorMenuItem(),
        miSceneAddCue, miSceneClearCues,
        new SeparatorMenuItem(),
        miSceneManageEvents, miSceneClearEvents,
        new SeparatorMenuItem(),
        miSceneFitPreview
    );
    sceneMenu.setOnShowing(e -> {
      boolean hasRoot = w.projectRoot != null && w.projectRoot.isDirectory();
      miSceneImport.setDisable(!hasRoot);
      miSceneClearCues.setDisable(w.project == null || w.project.getAudioCues().isEmpty());
      miSceneClearEvents.setDisable(w.project == null || w.project.getEditorEventCues().isEmpty());
    });

    // === View menu ===
    CheckMenuItem miShowToolbar = new CheckMenuItem("Show Top Toolbar");
    miShowToolbar.setOnAction(e -> w.setTopToolbarVisible(miShowToolbar.isSelected()));
    CheckMenuItem miShowCodePane = new CheckMenuItem("Show Code Pane");
    miShowCodePane.setOnAction(e -> w.setCodePaneVisible(miShowCodePane.isSelected()));
    CheckMenuItem miOnionSkin = new CheckMenuItem("Onion Skin Preview");
    miOnionSkin.setOnAction(e -> w.animationPreview.setOnionSkinning(miOnionSkin.isSelected()));
    CheckMenuItem miInterpolationGhosts = new CheckMenuItem("Interpolation Ghosts");
    miInterpolationGhosts.setOnAction(e -> w.animationPreview.setShowInterpolationGhosts(miInterpolationGhosts.isSelected()));
    CheckMenuItem miShowSafeGuides = new CheckMenuItem("Show Safe Guides");
    miShowSafeGuides.setOnAction(e -> w.animationPreview.setShowSafeGuides(miShowSafeGuides.isSelected()));
    CheckMenuItem miShowTitleGuides = new CheckMenuItem("Show Title Guides");
    miShowTitleGuides.setOnAction(e -> w.animationPreview.setShowTitleGuides(miShowTitleGuides.isSelected()));
    RadioMenuItem miLayoutDynamic = new RadioMenuItem("Toolbar Layout: Dynamic");
    RadioMenuItem miLayoutCompact = new RadioMenuItem("Toolbar Layout: Compact");
    ToggleGroup layoutMenuGroup = new ToggleGroup();
    miLayoutDynamic.setToggleGroup(layoutMenuGroup);
    miLayoutCompact.setToggleGroup(layoutMenuGroup);
    miLayoutDynamic.setOnAction(e -> w.setToolbarLayoutMode(AnimatedToolbarPane.LayoutMode.DYNAMIC));
    miLayoutCompact.setOnAction(e -> w.setToolbarLayoutMode(AnimatedToolbarPane.LayoutMode.COMPACT));
    MenuItem miFocusTimeline = new MenuItem("Focus Timeline on Selection");
    miFocusTimeline.setOnAction(e -> w.timelinePanel.zoomToSelection());
    MenuItem miZoomFit = new MenuItem("Zoom Timeline to Fit");
    miZoomFit.setOnAction(e -> w.timelinePanel.zoomToFit());
    MenuItem miFullscreenPreview = new MenuItem("Toggle Focused Preview Layout");
    miFullscreenPreview.setOnAction(e -> w.togglePreviewFocusMode());

    MenuItem miExpandAll = new MenuItem("Expand All Clusters");
    miExpandAll.setOnAction(e -> w.toolbarClusters.values().forEach(c -> c.setExpanded(true)));
    MenuItem miCollapseAll = new MenuItem("Collapse All Clusters");
    miCollapseAll.setOnAction(e -> w.toolbarClusters.values().forEach(c -> c.setExpanded(false)));

    Menu toolbarClustersMenu = new Menu("Toolbar Clusters");
    toolbarClustersMenu.setOnShowing(e -> {
      toolbarClustersMenu.getItems().clear();
      for (CollapsibleToolbarCluster cluster : w.toolbarClusters.values()) {
        CheckMenuItem mi = new CheckMenuItem(cluster.getTitle());
        mi.setSelected(cluster.isExpanded());
        mi.setOnAction(ev -> cluster.setExpanded(mi.isSelected()));
        toolbarClustersMenu.getItems().add(mi);
      }
    });

    Menu viewMenu = new Menu("View");
    viewMenu.getItems().addAll(
        miShowToolbar,
        miShowCodePane,
        miOnionSkin,
        miInterpolationGhosts,
        miShowSafeGuides,
        miShowTitleGuides,
        new SeparatorMenuItem(),
        miLayoutDynamic,
        miLayoutCompact,
        new SeparatorMenuItem(),
        toolbarClustersMenu,
        miExpandAll,
        miCollapseAll,
        new SeparatorMenuItem(),
        miFocusTimeline,
        miZoomFit,
        miFullscreenPreview
    );
    viewMenu.setOnShowing(e -> {
      miShowToolbar.setSelected(w.isTopToolbarVisible());
      miShowCodePane.setSelected(w.codePaneVisible);
      miOnionSkin.setSelected(w.animationPreview.isOnionSkinning());
      miInterpolationGhosts.setSelected(w.animationPreview.isShowInterpolationGhosts());
      miShowSafeGuides.setSelected(w.animationPreview.isShowSafeGuides());
      miShowTitleGuides.setSelected(w.animationPreview.isShowTitleGuides());
      miLayoutDynamic.setSelected(w.getToolbarLayoutMode() == AnimatedToolbarPane.LayoutMode.DYNAMIC);
      miLayoutCompact.setSelected(w.getToolbarLayoutMode() == AnimatedToolbarPane.LayoutMode.COMPACT);
      miFullscreenPreview.setDisable(w.scene == null && !w.isPreviewFullscreenActive());
    });

    // === Playback menu ===
    MenuItem miPlayPause = new MenuItem("Play");
    miPlayPause.setOnAction(e -> {
      if (w.project.isPlaying()) w.pause();
      else w.play();
    });
    MenuItem miStop = new MenuItem("Stop");
    miStop.setOnAction(e -> w.stop());
    MenuItem miRewind = new MenuItem("Rewind");
    miRewind.setOnAction(e -> w.rewind());
    CheckMenuItem miLoopPlayback = new CheckMenuItem("Loop Playback");
    miLoopPlayback.setOnAction(e -> {
      w.project.setLooping(miLoopPlayback.isSelected());
      if (w.cbLoop != null) w.cbLoop.setSelected(miLoopPlayback.isSelected());
      w.refreshExportPreviewAndMarkDirty();
    });
    MenuItem miLoopIn = new MenuItem("Set Loop In at Playhead");
    miLoopIn.setOnAction(e -> {
      double inMs = w.project.getPlayheadMs();
      double outMs = w.project.hasLoopRegion() ? w.project.getLoopEndMs() : w.project.getTotalDurationMs();
      if (inMs < outMs) {
        w.project.setLoopRegion(inMs, outMs);
        w.timelinePanel.refresh();
        w.refreshExportPreviewAndMarkDirty();
      }
    });
    MenuItem miLoopOut = new MenuItem("Set Loop Out at Playhead");
    miLoopOut.setOnAction(e -> {
      double outMs = w.project.getPlayheadMs();
      double inMs = w.project.hasLoopRegion() ? w.project.getLoopStartMs() : 0.0;
      if (outMs > inMs) {
        w.project.setLoopRegion(inMs, outMs);
        w.timelinePanel.refresh();
        w.refreshExportPreviewAndMarkDirty();
      }
    });
    MenuItem miLoopClear = new MenuItem("Clear Loop Region");
    miLoopClear.setOnAction(e -> {
      w.project.clearLoopRegion();
      w.timelinePanel.refresh();
      w.refreshExportPreviewAndMarkDirty();
    });

    Menu playbackMenu = new Menu("Playback");
    playbackMenu.getItems().addAll(
        miPlayPause, miStop, miRewind,
        new SeparatorMenuItem(),
        miLoopPlayback, miLoopIn, miLoopOut, miLoopClear
    );
    playbackMenu.setOnShowing(e -> {
      miPlayPause.setText(w.project.isPlaying() ? "Pause" : "Play");
      miLoopPlayback.setSelected(w.project.isLooping());
      miLoopClear.setDisable(!w.project.hasLoopRegion());
    });

    // === Help menu ===
    MenuItem miShowShortcuts = new MenuItem("Keyboard Shortcuts");
    miShowShortcuts.setOnAction(e -> w.showShortcutsOverlay());
    Menu helpMenu = new Menu("Help");
    helpMenu.getItems().add(miShowShortcuts);

    MenuBar menuBar = new MenuBar(fileMenu, editMenu, timelineMenu, viewMenu, playbackMenu, sceneMenu, helpMenu);
    menuBar.setUseSystemMenuBar(false);
    menuBar.setFocusTraversable(false);
    menuBar.setMinHeight(Region.USE_PREF_SIZE);
    menuBar.setMaxWidth(Region.USE_PREF_SIZE);

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    HBox bar = new HBox(10, menuBar, spacer);
    bar.getStyleClass().add("puppeteer-toolbar-command-bar");
    bar.setAlignment(Pos.CENTER_LEFT);
    bar.setMaxWidth(Double.MAX_VALUE);
    return bar;
  }
}
