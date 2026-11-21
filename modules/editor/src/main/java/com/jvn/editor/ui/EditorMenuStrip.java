package com.jvn.editor.ui;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;

/** Keeps native menu navigation and full category names as window space changes. */
public final class EditorMenuStrip extends ScrollPane {
  private static final PseudoClass STACKED = PseudoClass.getPseudoClass("stacked");
  private final MenuBar bar;
  private boolean stacked;
  private double inlineWidth;

  public EditorMenuStrip(MenuBar bar) {
    super(bar);
    this.bar = bar;
    getStyleClass().add("editor-menu-strip");
    setMinWidth(0);
    setMinHeight(Region.USE_PREF_SIZE);
    setMaxHeight(Region.USE_PREF_SIZE);
    setFitToWidth(true);
    setHbarPolicy(ScrollBarPolicy.NEVER);
    setFocusTraversable(false);
    setVbarPolicy(ScrollBarPolicy.NEVER);
    bar.setMinWidth(Region.USE_PREF_SIZE);
    for (Menu menu : bar.getMenus()) {
      menu.showingProperty().addListener((o, old, showing) -> {
        if (showing) reveal(menu.getGraphic());
      });
    }
  }

  @Override protected void layoutChildren() {
    if (!stacked) inlineWidth = bar.prefWidth(-1);
    boolean stack = getWidth() < inlineWidth;
    if (stack != stacked) {
      stacked = stack;
      pseudoClassStateChanged(STACKED, stack);
      bar.applyCss();
    }
    double height = bar.prefHeight(-1);
    var scrollbar = getWidth() < bar.prefWidth(-1) ? ScrollBarPolicy.ALWAYS : ScrollBarPolicy.NEVER;
    if (height != getPrefViewportHeight() || scrollbar != getHbarPolicy()) {
      setPrefViewportHeight(height);
      setHbarPolicy(scrollbar);
      // The surrounding VBox has already measured this row for the current layout pass.
      Platform.runLater(() -> { if (getParent() != null) getParent().requestLayout(); });
    }
    super.layoutChildren();
  }

  private void reveal(Node graphic) {
    double viewport = getViewportBounds().getWidth();
    double overflow = bar.getWidth() - viewport;
    if (overflow <= 0 || graphic == null) return;
    Node button = graphic;
    while (button != null && !(button instanceof MenuButton)) button = button.getParent();
    if (button == null) return;
    Bounds bounds = bar.sceneToLocal(button.localToScene(button.getBoundsInLocal()));
    double left = getHvalue() * overflow;
    if (bounds.getMinX() < left) setHvalue(Math.max(0, bounds.getMinX() / overflow));
    else if (bounds.getMaxX() > left + viewport) setHvalue(Math.min(1, (bounds.getMaxX() - viewport) / overflow));
  }
}
