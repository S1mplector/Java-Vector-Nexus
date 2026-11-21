package com.jvn.editor.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

/** The paragraph graphic reserves the same space that it paints, even while scrolling. */
final class CodeEditorGutter {
  private CodeEditorGutter() {}

  static StackPane wrap(Node lineNumber) {
    StackPane gutter = new StackPane(lineNumber);
    gutter.setAlignment(Pos.CENTER_LEFT);
    gutter.getStyleClass().add("code-editor-gutter");
    return gutter;
  }
}
