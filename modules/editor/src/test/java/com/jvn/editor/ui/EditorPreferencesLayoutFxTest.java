package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.nio.file.Path;
import java.util.List;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FxToolkitExtension.class)
class EditorPreferencesLayoutFxTest {
  @TempDir Path temporary;

  @Test
  void filtersAcrossCategoriesAndKeepsEditsAtDockWidth() throws Exception {
    FxToolkit.runFx(() -> {
      EditorPreferencesStore store = new EditorPreferencesStore(temporary.resolve("editor.properties"));
      EditorSettingsView view = new EditorSettingsView(store);
      Scene scene = new Scene(view, 270, 630);
      scene.getStylesheets().add(css("editor.css"));
      view.applyCss();
      view.layout();
      assertEquals(List.of("Appearance"), visibleNames(view));

      Region appearance = visibleSections(view).getFirst();
      @SuppressWarnings("unchecked")
      Spinner<Integer> fontSize = (Spinner<Integer>) appearance.lookup(".spinner");
      fontSize.getValueFactory().setValue(18);

      TextField filter = (TextField) view.lookup(".sidebar-tool-search-field");
      filter.setText("hardware gpu");
      view.layout();
      assertEquals(List.of("Runtime"), visibleNames(view));
      filter.setText("no-matching-option");
      assertTrue(visibleSections(view).isEmpty());

      @SuppressWarnings("unchecked")
      ComboBox<String> category = (ComboBox<String>) view.getTop().lookup(".combo-box");
      category.getSelectionModel().select("Sidebar Panels");
      view.applyCss();
      view.layout();
      assertTrue(filter.getText().isEmpty());
      assertEquals(List.of("Sidebar Panels"), visibleNames(view));
      for (Node control : visibleSections(view).getFirst().lookupAll(".check-box")) {
        assertTrue(control.localToScene(control.getLayoutBounds()).getMaxX() <= scene.getWidth(),
            "Chooser checkboxes fit the dock");
      }
      category.getSelectionModel().select("Runtime");
      assertEquals(List.of("Runtime"), visibleNames(view));
      category.getSelectionModel().select("Status Bar");
      assertEquals(List.of("Status Bar"), visibleNames(view));
      view.save();
      assertEquals(18, store.load().getCodeEditorFontSize());
      return null;
    });
  }

  @Test
  void focusedDockTabUsesASolidOrangeFrameAndGlassFill() throws Exception {
    for (String theme : List.of("editor.css", "editor-light.css")) {
      FxToolkit.runFx(() -> {
        TabPane tabs = new TabPane(new Tab("Project"), new Tab("Editor Settings"));
        tabs.getStyleClass().add("sidebar-tab-pane");
        Scene scene = new Scene(tabs, 400, 100);
        scene.getStylesheets().add(css(theme));
        tabs.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
        tabs.applyCss();
        tabs.layout();
        Region selected = (Region) tabs.lookup(".tab:selected");
        Region focusIndicator = (Region) selected.lookup(".focus-indicator");
        Color frame = (Color) selected.getBorder().getStrokes().getFirst().getTopStroke();
        assertTrue(frame.getRed() > frame.getGreen() && frame.getGreen() > frame.getBlue());
        assertEquals(2, selected.getBackground().getFills().size());
        assertTrue(focusIndicator.getBorder() == null
            || focusIndicator.getBorder().getInsets().getTop() == 0);
        return null;
      });
    }
  }

  private static List<Region> visibleSections(EditorSettingsView view) {
    return view.lookupAll(".editor-preferences-section").stream().filter(Node::isVisible)
        .map(Region.class::cast).toList();
  }

  private static List<String> visibleNames(EditorSettingsView view) {
    return visibleSections(view).stream()
        .map(section -> ((Label) section.lookup(".sidebar-tool-section-title")).getText()).toList();
  }

  private static String css(String theme) {
    return EditorPreferencesLayoutFxTest.class.getResource("/com/jvn/editor/" + theme).toExternalForm();
  }
}
