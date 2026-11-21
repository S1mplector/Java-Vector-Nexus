package com.jvn.editor.ui;

import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.transform.Scale;

/** Small Aero-style glyphs for the editor's shared notices and report actions. */
final class DialogIcon extends StackPane {
  enum Kind { ERROR, WARNING, INFO, REPORT, COPY, CLOSE }

  private DialogIcon(Kind kind, double size) {
    Group artwork = new Group();
    Rectangle artboard = new Rectangle(24, 24);
    artboard.getStyleClass().add("editor-notice-icon-artboard");
    artwork.getChildren().add(artboard);
    switch (kind) {
      case ERROR -> {
        artwork.getChildren().add(plate(path("M8 2.5 H16 L21.5 8 V16 L16 21.5 H8 L2.5 16 V8 Z")));
        artwork.getChildren().add(path("M8.5 8.5 L15.5 15.5 M15.5 8.5 L8.5 15.5"));
      }
      case WARNING -> {
        artwork.getChildren().add(plate(path("M11 3.5 Q12 2 13 3.5 L22 19 Q23 21 20.5 21 H3.5 Q1 21 2 19 Z")));
        artwork.getChildren().add(path("M12 9 V13.5 M12 17 H12.1"));
      }
      case INFO -> {
        artwork.getChildren().add(plate(new Circle(12, 12, 9.5)));
        artwork.getChildren().add(path("M12 7 H12.1 M12 11 V17"));
      }
      case REPORT -> {
        artwork.getChildren().add(plate(path("M5 2.5 H14 L19.5 8 V21.5 H5 Z")));
        artwork.getChildren().add(path("M14 3 V8 H19 M8.5 12 H16 M8.5 16 H16"));
      }
      case COPY -> {
        artwork.getChildren().add(path("M3 16 V3 H16"));
        artwork.getChildren().add(plate(path("M7 7 H21 V21 H7 Z")));
        artwork.getChildren().add(path("M10 11 H18 M10 15 H18"));
      }
      case CLOSE -> {
        artwork.getChildren().add(plate(new Circle(12, 12, 9.5)));
        artwork.getChildren().add(path("M8.5 8.5 L15.5 15.5 M15.5 8.5 L8.5 15.5"));
      }
    }

    double ratio = size / 24;
    artwork.getTransforms().add(new Scale(ratio, ratio));
    getChildren().setAll(artwork);
    getStyleClass().addAll("editor-notice-icon",
        "editor-notice-icon-" + kind.name().toLowerCase(java.util.Locale.ROOT));
    setMinSize(size, size);
    setPrefSize(size, size);
    setMaxSize(size, size);
    setMouseTransparent(true);
  }

  static DialogIcon of(Kind kind, double size) {
    return new DialogIcon(kind, size);
  }

  private static SVGPath path(String data) {
    SVGPath path = new SVGPath();
    path.setContent(data);
    path.getStyleClass().add("editor-notice-icon-mark");
    return path;
  }

  private static Shape plate(Shape shape) {
    shape.getStyleClass().setAll("editor-notice-icon-plate");
    return shape;
  }
}
