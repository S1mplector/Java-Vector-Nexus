package com.jvn.editor.ui;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(FxToolkitExtension.class)
class AssetAutoLabelLayoutTest {
  @Test void queueAndPrimaryActionStayUsableAtSidebarAndWideSizes() throws Exception {
    String project = System.getenv().getOrDefault("JVN_LABEL_UI_PROJECT", "");
    AssetAutoLabelService.ScanResult scan = project.isBlank() ? sample()
        : new AssetAutoLabelService().preview(Path.of(project));
    for (String theme : List.of("editor.css", "editor-light.css")) {
      for (int width : new int[]{420,1000}) {
        FxToolkit.runFx(() -> {
          AssetAutoLabelDashboardView view = new AssetAutoLabelDashboardView();
          Scene scene = new Scene(view,width,720);
          scene.getStylesheets().add(getClass().getResource("/com/jvn/editor/"+theme).toExternalForm());
          view.acceptScan(scan);
          view.applyCss();view.layout();
          ListView<?> queue = (ListView<?>) view.lookup("#asset-auto-label-table");
          assertTrue(queue.getHeight() >= 180, "Asset queue must not collapse to one row: " + queue.getHeight());
          assertTrue(queue.getItems().stream().allMatch(asset ->
              ((AssetAutoLabelService.AssetSuggestion)asset).status() == AssetAutoLabelService.LabelStatus.SUGGESTED));
          SplitPane split = (SplitPane) view.lookup("#asset-label-review-split");
          assertEquals(width >= 760 ? javafx.geometry.Orientation.HORIZONTAL : javafx.geometry.Orientation.VERTICAL, split.getOrientation());
          Button action = (Button)view.lookup("#asset-label-auto-ready");
          assertFalse(action.isDisabled());
          assertTrue(action.getWidth() >= action.prefWidth(-1) - 1, "Primary action must not be truncated");
          String output = System.getenv().getOrDefault("JVN_LABEL_UI_OUTPUT", "");
          if (!output.isBlank()) {
            Path folder=Path.of(output);Files.createDirectories(folder);
            javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(view.snapshot(null,null),null),
                "png",folder.resolve(theme.replace(".css", "")+"-"+width+".png").toFile());
          }
          return null;
        });
      }
    }
  }

  private static AssetAutoLabelService.ScanResult sample() {
    List<AssetAutoLabelService.AssetSuggestion> assets = new ArrayList<>();
    for (int i=0;i<20;i++) assets.add(new AssetAutoLabelService.AssetSuggestion(
        Path.of("assets/characters/hero/eyes/eyes_"+i+".png"), "assets/characters/hero/eyes/eyes_"+i+".png",
        AssetAutoLabelService.AssetKind.CHARACTER_LAYER,"hero","eyes_"+i,
        AssetAutoLabelService.LabelStatus.SUGGESTED,.9,"Matches declared sibling assets",null,0,true));
    return new AssetAutoLabelService.ScanResult(Path.of("."),assets,
        Map.of(AssetAutoLabelService.LabelStatus.SUGGESTED,20), Map.of(),Set.of("hero"),20,20,0);
  }
}
