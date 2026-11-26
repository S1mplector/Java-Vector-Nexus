package com.jvn.fx.vn;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.assets.FilesystemAssetManager;
import com.jvn.core.vn.VnNode;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnScene;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

record VnTopicMap(String title, String portrait, List<Branch> branches) {
  static final String VARIABLE = "ui.topicMap";
  record Branch(String target, String title, String icon, String color, List<Lesson> lessons) {}
  record Lesson(String target, String title, String icon, String completedFlag) {
    boolean completed(VnScene scene) {
      return Boolean.TRUE.equals(scene.getState().getVariable(completedFlag));
    }
  }

  static VnTopicMap load(File root, String path) throws IOException {
    Properties data = new Properties();
    try (var reader = new InputStreamReader(assets(root).open(AssetType.CONFIG, path), StandardCharsets.UTF_8)) {
      data.load(reader);
    }
    List<Branch> branches = new ArrayList<>();
    for (String route : keys(data, "branches")) {
      List<Lesson> lessons = new ArrayList<>();
      for (String target : keys(data, route + ".lessons")) {
        lessons.add(new Lesson(target, title(data, target), data.getProperty(target + ".icon", ""),
            data.getProperty(target + ".completed", "")));
      }
      branches.add(new Branch(route, title(data, route), data.getProperty(route + ".icon", ""),
          data.getProperty(route + ".color", ""), List.copyOf(lessons)));
    }
    return new VnTopicMap(title(data, "map"), data.getProperty("map.portrait", ""), List.copyOf(branches));
  }

  static AssetCatalog assets(File root) {
    return root == null ? new AssetCatalog() : new AssetCatalog(new FilesystemAssetManager(root.toPath()));
  }

  static int choiceIndex(VnNode node, String target) {
    if (node == null || node.getType() != VnNodeType.CHOICE) return -1;
    for (int i = 0; i < node.getChoices().size(); i++) {
      var choice = node.getChoices().get(i);
      if (choice.isEnabled() && target.equals(choice.getTargetLabel())) return i;
    }
    return -1;
  }

  static boolean open(VnScene scene, String branch, String lesson) {
    if (lesson != null && select(scene, lesson)) return true;
    if (choiceIndex(scene.getState().getCurrentNode(), branch) < 0) select(scene, "topics");
    if (!select(scene, branch)) return false;
    return lesson == null || select(scene, lesson);
  }

  private static boolean select(VnScene scene, String target) {
    VnNode node = scene.getState().getCurrentNode();
    int index = choiceIndex(node, target);
    if (index < 0) return false;
    scene.selectChoice(index);
    return scene.getState().getCurrentNode() != node;
  }

  private static String title(Properties data, String key) {
    return data.getProperty(key + ".title", key);
  }

  private static List<String> keys(Properties data, String key) {
    return java.util.Arrays.stream(data.getProperty(key, "").split(","))
        .map(String::strip).filter(value -> !value.isEmpty()).distinct().toList();
  }
}
