package com.jvn.editor.ui;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import javafx.scene.Scene;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(FxToolkitExtension.class)
class EditorMenuStripFxTest {
  @Test void keepsCategoryGraphicsAndLabelsReachableAfterResizing() throws Exception {
    FxToolkit.runFx(() -> {
      MenuBar bar = new MenuBar();
      bar.setUseSystemMenuBar(false);
      for (var role : EditorMenuIcon.values()) {
        var menu = role.menu(role == EditorMenuIcon.VERSION_CONTROL ? "Version Control" : role.name());
        menu.getItems().add(new MenuItem("Command"));
        bar.getMenus().add(menu);
      }
      EditorMenuStrip strip = new EditorMenuStrip(bar);
      StackPane root = new StackPane(strip);
      root.getStyleClass().add("main-editor-command-bar");
      Scene scene = new Scene(root, 1800, 100);
      EditorTheme.apply(scene);
      root.applyCss();
      root.layout();
      double wide = bar.prefWidth(-1);

      root.resize(480, 100);
      root.layout();
      root.applyCss();
      root.layout();
      assertTrue(bar.prefWidth(-1) < wide);
      assertTrue(bar.getWidth() >= bar.prefWidth(-1));
      var last = bar.getMenus().getLast();
      last.show();
      assertTrue(strip.getHvalue() > 0);
      last.hide();
      for (var m : bar.getMenus()) {
        assertSame(scene, m.getGraphic().getScene(), m.getText());
        assertFalse(m.getGraphic().getBoundsInParent().isEmpty(), m.getText());
      }

      root.resize(1800, 100);
      root.layout();
      root.applyCss();
      root.layout();
      assertEquals(wide, bar.prefWidth(-1), 1.0);
    });
  }
}
