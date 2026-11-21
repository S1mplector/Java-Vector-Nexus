package com.jvn.editor.ui;

import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/** Shell artwork for the editor's top-level menu headings. */
public enum EditorMenuIcon {
  APPLICATION, FILE, EDIT, VIEW, NAVIGATE, PROJECT, TEXT, VNS, ASSETS, DIAGNOSTICS,
  RUN, BUILD, TOOLS, VERSION_CONTROL, WINDOW, DEVELOPER, HELP;

  private static final int SIZE = 20;

  public Menu menu(String title) {
    return new Menu(title, graphic());
  }

  public Node graphic() {
    Node icon = switch (this) {
      case APPLICATION -> editorMark();
      case FILE -> ShellFileArtwork.document(SIZE, null);
      case EDIT -> SidebarToolArtwork.freehand("#e4ac40");
      case VIEW -> SidebarToolArtwork.show("#449bbb");
      case NAVIGATE -> SidebarToolArtwork.locate("#69acd8");
      case PROJECT -> SidebarPanelArtwork.of(AeroIcon.Kind.PROJECT, SIZE);
      case TEXT -> SidebarToolArtwork.type();
      case VNS -> SidebarToolArtwork.speech("#a88ad2");
      case ASSETS -> SidebarPanelArtwork.of(AeroIcon.Kind.ASSETS, SIZE);
      case DIAGNOSTICS -> SidebarPanelArtwork.of(AeroIcon.Kind.DIAGNOSTICS, SIZE);
      case RUN -> AeroIcon.runProject(SIZE);
      case BUILD -> SidebarToolArtwork.packageBox();
      case TOOLS -> SidebarToolArtwork.spanner();
      case VERSION_CONTROL -> SidebarPanelArtwork.of(AeroIcon.Kind.VERSION_CONTROL, SIZE);
      case WINDOW -> SidebarToolArtwork.dock("#539dcc");
      case DEVELOPER -> SidebarPanelArtwork.of(AeroIcon.Kind.SCRIPT_EDITOR, SIZE);
      case HELP -> AeroIcon.of(AeroIcon.Kind.HELP, SIZE);
    };
    icon.getStyleClass().add("editor-menu-icon");
    icon.setMouseTransparent(true);
    return icon;
  }

  private static ImageView editorMark() {
    var url = EditorMenuIcon.class.getResource("/com/jvn/editor/images/jvn_editor_icon.png");
    var view = new ImageView(new Image(url.toExternalForm(), SIZE * 3, SIZE * 3, true, true));
    view.setFitWidth(SIZE);
    view.setFitHeight(SIZE);
    view.setPreserveRatio(true);
    return view;
  }
}
