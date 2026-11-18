package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.util.List;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class HelpUiFxTest {
  @Test
  void rendersHelpMarkupWithoutLosingItsReadableText() throws Exception {
    FxToolkit.runFx(() -> {
      String source = """
          ## Shortcuts
          - Press `F5` to **refresh**.
            This clears the cache.
          2. Press *Escape* to clear the filter.

          ```vns
          @label start
            [end]
          ```
          """;
      HelpText.Rendered document = HelpText.render(source);
      assertEquals("""
          Shortcuts

          • Press F5 to refresh. This clears the cache.
          2. Press Escape to clear the filter.

          @label start
            [end]""", document.text());
      assertNotNull(document.view().lookup(".help-heading"));
      assertEquals(2, document.view().lookupAll(".help-list-row").size());
      assertNotNull(document.view().lookup(".help-strong"));
      assertNotNull(document.view().lookup(".help-emphasis"));
      assertNotNull(document.view().lookup(".help-code-block"));

      var tooltip = EditorTooltip.rich("**Refresh** · `F5`");
      assertEquals("Refresh · F5", tooltip.getText());
      assertEquals(ContentDisplay.GRAPHIC_ONLY, tooltip.getContentDisplay());
      assertNotNull(((VBox) tooltip.getGraphic()).lookup(".help-code"));
      return null;
    });
  }

  @Test
  void wrapsOrdinaryTooltipsAtACompactWidthAndKeepsCustomSizes() throws Exception {
    FxToolkit.runFx(() -> {
      Tooltip ordinary = new Tooltip("Ordinary hover text");
      ordinary.setSkin(new EditorTooltipSkin(ordinary));
      assertEquals(360, ordinary.getMaxWidth());

      Tooltip customized = new Tooltip("Editor diagnostic");
      customized.setMaxWidth(520);
      customized.setSkin(new EditorTooltipSkin(customized));
      assertEquals(520, customized.getMaxWidth());
      return null;
    });
  }

  @Test
  void sizesHelpToShortTopicsAndScrollsLongOnesInBothThemes() throws Exception {
    for (String style : List.of("editor.css", "editor-light.css")) {
      FxToolkit.runFx(() -> {
        String theme = getClass().getResource("/com/jvn/editor/" + style).toExternalForm();
        var shortHelp = SidebarToolHelp.createHelpWindow(null, List.of(theme), "Short help", "A short paragraph.");
        var longHelp = SidebarToolHelp.createHelpWindow(null, List.of(theme), "Long help", "Paragraph of help.\n\n".repeat(60));
        try {
          ScrollPane shortScroll = (ScrollPane) shortHelp.getScene().lookup(".editor-help-scroll");
          ScrollPane longScroll = (ScrollPane) longHelp.getScene().lookup(".editor-help-scroll");
          assertTrue(shortScroll.getPrefViewportHeight() < 120);
          assertTrue(longScroll.getPrefViewportHeight() >= 250 && longScroll.getPrefViewportHeight() <= 400);
          assertEquals(ScrollPane.ScrollBarPolicy.NEVER, longScroll.getHbarPolicy());
          assertTrue(shortHelp.getScene().getRoot().lookupAll(".text-area").isEmpty());
        } finally {
          shortHelp.close();
          longHelp.close();
        }
        return null;
      });
    }
  }
}
