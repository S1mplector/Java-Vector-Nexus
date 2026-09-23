package com.jvn.fx.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnState;
import com.jvn.core.vn.script.VnScriptParser;
import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FxToolkitExtension.class)
class VnRendererNarrationTextBoxTest {
  @TempDir Path project;

  @Test
  void switchesBetweenSpokenDialogueAndUnnamedThoughts() throws Exception {
    verifyTextBoxes(true);
  }

  @Test
  void unnamedThoughtsFallBackToDialogueAssetWhenNarrationAssetIsAbsent() throws Exception {
    verifyTextBoxes(false);
  }

  private void verifyTextBoxes(boolean narrationAsset) throws Exception {
    writeImage("dialogue.png", 0xffff0000);
    writeImage("thought.png", 0xff0000ff);
    Path layout = project.resolve("config/ui/dialogue.layout");
    Files.createDirectories(layout.getParent());
    Files.writeString(layout, """
        textBoxX=0
        textBoxY=0
        textBoxWidth=1
        textBoxHeight=1
        textBoxAsset=dialogue.png
        textBoxOpacity=0
        nameBoxXOffset=5
        nameBoxYOffset=5
        nameBoxWidth=50
        nameBoxHeight=15
        nameBoxColor=#00FFFF
        nameBoxOpacity=1
        nameTextFontSize=8
        dialogueTextFontSize=8
        dialogueTextTopPadding=30
        """ + (narrationAsset ? "textBoxNarrationAsset=thought.png\n" : ""));
    VnScenario scenario = new VnScriptParser().parseFromString("""
        @scenario textbox_switch
        @character john "John"
        @character john_thought ""
        john: Hello.
        john_thought: A private thought.
        john: Goodbye.
        """);
    assertEquals("", scenario.getNodes().get(1).getDialogue().getSpeakerName());
    assertEquals("john_thought", scenario.getNodes().get(1).getDialogue().getSpeakerId());
    VnState state = new VnState();
    state.setScenario(scenario);
    Canvas canvas = FxToolkit.runFx(() -> new Canvas(192, 108));
    VnRenderer renderer = FxToolkit.runFx(() -> {
      VnRenderer result = new VnRenderer(canvas.getGraphicsContext2D());
      result.setProjectRoot(project.toFile());
      return result;
    });
    assertFrame(renderer, canvas, state, scenario, Color.RED, Color.CYAN);
    state.advance();
    Color thoughtColor = narrationAsset ? Color.BLUE : Color.RED;
    assertFrame(renderer, canvas, state, scenario, thoughtColor, thoughtColor);
    state.advance();
    assertFrame(renderer, canvas, state, scenario, Color.RED, Color.CYAN);
  }

  private void assertFrame(VnRenderer renderer, Canvas canvas, VnState state,
                           VnScenario scenario, Color box, Color namePlate) throws Exception {
    WritableImage image = null;
    // Asset decoding is asynchronous; wait for the rendered pixels, not a fixed sleep.
    for (int attempt = 0; attempt < 100; attempt++) {
      image = FxToolkit.runFx(() -> {
        renderer.render(state, scenario, 192, 108);
        return canvas.snapshot(null, null);
      });
      if (box.equals(image.getPixelReader().getColor(180, 90))
          && namePlate.equals(image.getPixelReader().getColor(8, 8))) break;
      Thread.sleep(50);
    }
    assertEquals(box, image.getPixelReader().getColor(180, 90), "Textbox skin");
    assertEquals(namePlate, image.getPixelReader().getColor(8, 8), "Name plate visibility");
  }

  private void writeImage(String name, int argb) throws Exception {
    BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 2; y++) {
      for (int x = 0; x < 2; x++) image.setRGB(x, y, argb);
    }
    ImageIO.write(image, "png", project.resolve(name).toFile());
  }
}
