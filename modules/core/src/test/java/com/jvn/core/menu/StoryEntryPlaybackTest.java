package com.jvn.core.menu;

import com.jvn.core.assets.*;
import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.core.vn.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StoryEntryPlaybackTest {
  @TempDir Path project;
  Engine engine;
  MainMenuScene menu;

  @BeforeEach void setUp() throws Exception {
    Files.createDirectories(project.resolve("scripts/story"));
    Files.writeString(project.resolve("scripts/story/route.vns"), """
        @scenario route
        @character guide "Guide"
        @label start
        guide: Opening line.
        @label alternate
        guide: Selected entry.
        """);
    AssetCatalog.setDefaultManager(new FilesystemAssetManager(project));
    engine = new Engine(ApplicationConfig.builder().build());
    menu = new MainMenuScene(engine, new VnSettings(), null, "story/other.vns", null);
    engine.scenes().push(menu);
  }

  @AfterEach void restore() { AssetCatalog.setDefaultManager(new ClasspathAssetManager()); }

  @Test void playsOtherScriptAtExactLabel() {
    menu.startFromEntry("story/route.vns", "alternate");
    var scene = assertInstanceOf(VnScene.class, engine.scenes().peek());
    assertEquals("story/route.vns", scene.getState().getSourceScriptName());
    assertEquals("Selected entry.", scene.getState().getCurrentNode().getDialogue().getText());
  }

  @Test void blankEntryPlaysFromBeginning() {
    menu.startFromEntry("story/route.vns", "");
    var scene = assertInstanceOf(VnScene.class, engine.scenes().peek());
    assertEquals("Opening line.", scene.getState().getCurrentNode().getDialogue().getText());
  }

  @Test void missingLabelSurfacesErrorInTheRuntime() {
    menu.startFromEntry("story/route.vns", "missing");
    var scene = assertInstanceOf(VnScene.class, engine.scenes().peek());
    assertNotNull(scene.getActiveError());
  }
}
