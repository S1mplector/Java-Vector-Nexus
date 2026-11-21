package com.jvn.editor.ui;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.FilesystemAssetManager;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnScenarioLoader;
import com.jvn.core.vn.VnScene;
import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import javafx.scene.control.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(FxToolkitExtension.class)
class NewProjectWorkspaceFxTest {
  @TempDir Path parent;

  @Test void defaultProjectCreatesOnePlayableSceneAndStoresTheSelectedOptions() throws Exception {
    Path original = Files.createDirectory(parent.resolve("My_Visual_Novel"));
    Path marker = Files.writeString(original.resolve("keep.txt"), "existing project");
    Files.createDirectory(parent.resolve("My_Visual_Novel_2"));
    Path project = FxToolkit.runFx(() -> {
      NewProjectWizard wizard = new NewProjectWizard(null);
      try {
        wizard.show();
        var root = wizard.getScene().getRoot();
        var tabs = (TabPane) root.lookup(".tab-pane");
        assertEquals(List.of("Project", "Content", "Runtime", "Playback", "Review"),
            tabs.getTabs().stream().map(Tab::getText).toList());
        var choices = root.lookupAll(".setup-template").stream().map(RadioButton.class::cast).toList();
        assertEquals(3, choices.size());
        assertEquals("Writing Project", choices.stream().filter(RadioButton::isSelected).findFirst().orElseThrow().getText());
        assertTrue(choices.stream().noneMatch(button -> button.getText().equals("Starter Story")));
        field(wizard, "txtLocation", TextField.class).setText(parent.toString());
        var create = field(wizard, "btnCreate", Button.class);
        assertTrue(create.isDisabled());
        var available = field(wizard, "availableFolder", Button.class);
        assertTrue(available.isVisible());
        available.fire();
        assertEquals("My_Visual_Novel_3", field(wizard, "txtFolderName", TextField.class).getText());
        assertEquals("My Visual Novel", field(wizard, "txtProjectName", TextField.class).getText());
        tabs.getSelectionModel().select(2);
        var resolution = field(wizard, "cmbResolution", ComboBox.class);
        resolution.getSelectionModel().select(resolution.getItems().indexOf("1080x1920 (Portrait)"));
        field(wizard, "scaleToDisplay", CheckBox.class).setSelected(false);
        tabs.getSelectionModel().selectLast();
        field(wizard, "txtReleaseVersion", TextField.class).setText("1.2.3-beta");
        var saveId = field(wizard, "txtGameId", TextField.class);
        saveId.setText("Bad Save ID");
        assertTrue(create.isDisabled());
        saveId.setText("lantern-studio");
        assertFalse(create.isDisabled());
        create.fire();
        assertFalse(wizard.isShowing());
        return wizard.getCreatedProjectDir().toPath();
      } finally {
        wizard.close();
      }
    });
    assertEquals("existing project", Files.readString(marker));
    assertEquals(parent.resolve("My_Visual_Novel_3"), project);
    var manifest = new Properties();
    try (var in = Files.newInputStream(project.resolve("jvn.project"))) { manifest.load(in); }
    assertEquals("writing_project", manifest.getProperty("scaffold.template"));
    assertEquals("1080", manifest.getProperty("width"));
    assertEquals("1920", manifest.getProperty("height"));
    assertEquals("1.2.3-beta", manifest.getProperty("version"));
    assertEquals("lantern-studio", manifest.getProperty("id"));
    assertEquals("false", manifest.getProperty("feature.sampleContent"));
    try (var scripts = Files.list(project.resolve("scripts/story"))) {
      assertEquals(List.of("prologue.vns"), scripts.map(path -> path.getFileName().toString()).toList());
    }
    assertFalse(Files.readString(project.resolve("scripts/definitions/characters.vns")).contains("lavender"));
    assertFalse(Files.readString(project.resolve("config/story/story.storymap")).contains("BranchDemo"));
    for (String menu : List.of("main", "load", "save", "settings")) {
      assertTrue(Files.isRegularFile(project.resolve("config/menu/menus/" + menu + ".menu")), menu);
    }
    assertFalse(Files.readString(project.resolve("config/menu/styles/default.style")).contains("assets/demo/"));
    var assets = new AssetCatalog(new FilesystemAssetManager(project));
    var loader = new VnScenarioLoader(assets, null, "scripts");
    var scene = new VnScene(loader.load(manifest.getProperty("entryVns")));
    scene.onEnter();
    assertEquals(VnNodeType.DIALOGUE, scene.getState().getCurrentNode().getType());
    assertEquals("Your opening line goes here.", scene.getState().getCurrentNode().getDialogue().getText());
  }

  private static <T> T field(NewProjectWizard wizard, String name, Class<T> type) throws Exception {
    var field = NewProjectWizard.class.getDeclaredField(name);
    field.setAccessible(true);
    return type.cast(field.get(wizard));
  }
}
