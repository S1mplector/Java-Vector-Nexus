package com.jvn.editor.ui;

import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/** Moves the same editing controls between the compact inspector and the wide workspace. */
final class LightingStudioOutliner extends TabPane {
  private final BorderPane compactLights;
  private final BorderPane compactCasters;
  private final TitledPane compactScene;
  private final ListView<?> lights;
  private final ListView<?> casters;
  private final Node sceneEntities;
  private final BorderPane lightSection;
  private final BorderPane casterSection;
  private final Tab sceneTab = new Tab("Scene");
  private final double compactLightHeight;
  private final double compactCasterHeight;
  private boolean docked;

  LightingStudioOutliner(BorderPane compactLights, ListView<?> lights, Runnable inspectLight, Runnable addLight,
      BorderPane compactCasters, ListView<?> casters, Runnable inspectCaster, Runnable addCaster,
      TitledPane compactScene) {
    this.compactLights = compactLights;
    this.compactCasters = compactCasters;
    this.compactScene = compactScene;
    this.lights = lights;
    this.casters = casters;
    this.sceneEntities = compactScene.getContent();
    compactLightHeight = lights.getMaxHeight();
    compactCasterHeight = casters.getMaxHeight();
    lightSection = section("Lights", "Add light", inspectLight, addLight);
    casterSection = section("Shadow casters", "Add shadow caster", inspectCaster, addCaster);
    lights.focusedProperty().addListener((obs, old, focused) -> { if (docked && focused) inspectLight.run(); });
    casters.focusedProperty().addListener((obs, old, focused) -> { if (docked && focused) inspectCaster.run(); });
    SplitPane lists = new SplitPane(lightSection, casterSection);
    lists.setOrientation(Orientation.VERTICAL);
    setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
    getTabs().addAll(new Tab("Outliner", lists), sceneTab);
    getStyleClass().add("lighting-outliner");
    setId("lighting-outliner");
    setPrefWidth(224);
  }

  void setDocked(boolean wide) {
    if (wide == docked) return;
    docked = wide;
    if (wide) {
      compactLights.setCenter(null);
      compactCasters.setCenter(null);
      compactScene.setContent(null);
      lightSection.setCenter(lights);
      casterSection.setCenter(casters);
      sceneTab.setContent(sceneEntities);
    } else {
      lightSection.setCenter(null);
      casterSection.setCenter(null);
      sceneTab.setContent(null);
      compactLights.setCenter(lights);
      compactCasters.setCenter(casters);
      compactScene.setContent(sceneEntities);
    }
    lights.setMaxHeight(wide ? Double.MAX_VALUE : compactLightHeight);
    casters.setMaxHeight(wide ? Double.MAX_VALUE : compactCasterHeight);
    compactScene.setManaged(!wide);
    compactScene.setVisible(!wide);
  }

  private static BorderPane section(String name, String action, Runnable inspect, Runnable add) {
    Label title = new Label(name);
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    Button button = new Button("+");
    button.setAccessibleText(action);
    button.setTooltip(new Tooltip(action));
    button.setOnAction(event -> { inspect.run(); add.run(); });
    HBox header = new HBox(6, title, spacer, button);
    header.setAlignment(Pos.CENTER_LEFT);
    header.getStyleClass().add("lighting-outliner-header");
    BorderPane section = new BorderPane();
    section.setTop(header);
    return section;
  }
}
