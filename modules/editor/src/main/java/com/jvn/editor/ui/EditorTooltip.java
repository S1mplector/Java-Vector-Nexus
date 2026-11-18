package com.jvn.editor.ui;

import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

/** Opt-in formatting for editor-authored hover tips. */
public final class EditorTooltip {
  private EditorTooltip() {}

  public static Tooltip rich(String source) {
    HelpText.Rendered rendered = HelpText.render(source);
    VBox content = rendered.view();
    content.setPrefWidth(Math.min(320, content.prefWidth(-1)));
    Tooltip tooltip = new Tooltip(rendered.text());
    tooltip.getStyleClass().add("editor-rich-tooltip");
    tooltip.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    tooltip.setGraphic(content);
    return tooltip;
  }
}
