package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jvn.editor.ui.AssetAutoLabelService.AssetKind;
import com.jvn.editor.ui.AssetAutoLabelService.AssetSuggestion;
import com.jvn.editor.ui.AssetAutoLabelService.LabelStatus;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("NullAway")
class AssetAutoLabelServiceTest {
  @TempDir Path tempDir;

  @Test
  void classifiesEverySupportedAssetTaxonomy() throws Exception {
    Map<String, AssetKind> cases = new LinkedHashMap<>();
    cases.put("assets/backgrounds/school.png", AssetKind.BACKGROUND);
    cases.put("assets/characters/ari/eyes/open.png", AssetKind.CHARACTER_LAYER);
    cases.put("assets/panels/chapter_1/panel_a.png", AssetKind.PANEL);
    cases.put("assets/props/lunch_tray.png", AssetKind.PROP);
    cases.put("assets/ui/phone/icon.png", AssetKind.UI);
    cases.put("assets/effects/rain.png", AssetKind.EFFECT);
    cases.put("assets/audio/theme.ogg", AssetKind.AUDIO);
    cases.put("assets/video/intro.webm", AssetKind.VIDEO);
    cases.put("assets/fonts/dialogue.ttf", AssetKind.FONT);
    cases.put("assets/data/atlas.json", AssetKind.DATA);
    for (Map.Entry<String, AssetKind> testCase : cases.entrySet()) {
      Path file = tempDir.resolve(testCase.getKey());
      Files.createDirectories(file.getParent());
      Files.write(file, new byte[] {0});
      assertTrue(AssetAutoLabelService.isSupportedAsset(file), testCase.getKey());
      assertEquals(testCase.getValue(), AssetAutoLabelService.kindFromPath(file), testCase.getKey());
    }
  }

  @Test
  void vnsDeclarationsWinAndDeclaredSiblingsTeachNewLayerLabels() throws Exception {
    Path project = createProject();
    Path eyes = project.resolve("assets/characters/ari/head/head normal/eyes");
    Path declared = touch(eyes.resolve("ari_hn_e_-_06.png"));
    touch(project.resolve("assets/backgrounds/classroom.png"));
    touch(project.resolve("assets/panels/chapter_1/panel_a.png"));
    Files.writeString(project.resolve("scripts/definitions/visuals.vns"), """
        @character ari "Ari"
        @charlayer ari normal_eyes_06 "assets/characters/ari/head/head normal/eyes/ari_hn_e_-_06.png"
        @background classroom assets/backgrounds/classroom.png
        @charlayer comic_panel panel_a assets/panels/chapter_1/panel_a.png
        """, StandardCharsets.UTF_8);

    AssetAutoLabelService service = new AssetAutoLabelService();
    AssetAutoLabelService.ScanResult first = service.scan(project);
    AssetSuggestion declaredSuggestion = byPath(first, project.relativize(declared).toString());
    assertEquals(LabelStatus.DECLARED, declaredSuggestion.status());
    assertEquals(AssetKind.CHARACTER_LAYER, declaredSuggestion.kind());
    assertEquals("ari", declaredSuggestion.owner());
    assertEquals("normal_eyes_06", declaredSuggestion.label());
    assertEquals(3, first.declaredCount());

    Path newLayer = touch(eyes.resolve("ari_hn_e_-_07.png"));
    AssetAutoLabelService.ScanResult second = service.scan(project);
    AssetSuggestion inferred = byPath(second, project.relativize(newLayer).toString());
    assertTrue(inferred.isNew());
    assertEquals(LabelStatus.SUGGESTED, inferred.status());
    assertEquals(AssetKind.CHARACTER_LAYER, inferred.kind());
    assertEquals("ari", inferred.owner());
    assertTrue(inferred.label().startsWith("normal_eyes"), inferred.label());
    assertTrue(inferred.confidence() >= 0.80, Double.toString(inferred.confidence()));
    assertTrue(inferred.reason().contains("declared sibling"));
  }

  @Test
  void previewIsReadOnlyAndReviewedRegistryDecisionsSurviveRescans() throws Exception {
    Path project = createProject();
    Path prop = touch(project.resolve("assets/miscs/cake.png"));
    AssetAutoLabelService service = new AssetAutoLabelService();
    AssetAutoLabelService.ScanResult preview = service.preview(project);
    assertFalse(Files.exists(project.resolve(AssetAutoLabelService.REGISTRY_PATH)));

    AssetSuggestion suggestion = byPath(preview, project.relativize(prop).toString())
        .reviewed(AssetKind.PROP, "birthday_cake", "whole");
    service.saveDecision(project, suggestion, LabelStatus.IGNORED);
    AssetSuggestion persisted = byPath(service.scan(project), project.relativize(prop).toString());
    assertEquals(LabelStatus.IGNORED, persisted.status());
    assertEquals("birthday_cake", persisted.owner());
    assertEquals("whole", persisted.label());
    assertTrue(Files.isRegularFile(project.resolve(AssetAutoLabelService.REGISTRY_PATH)));
  }

  @Test
  void unchangedRescanDoesNotRewriteRegistry() throws Exception {
    Path project = createProject();
    touch(project.resolve("assets/backgrounds/room.png"));
    AssetAutoLabelService service = new AssetAutoLabelService();
    service.scan(project);
    Path registry = project.resolve(AssetAutoLabelService.REGISTRY_PATH);
    byte[] first = Files.readAllBytes(registry);
    service.scan(project);
    assertTrue(java.util.Arrays.equals(first, Files.readAllBytes(registry)));
  }

  @Test
  void reviewedDeclarationsAreQuotedLinkedAndIdempotent() throws Exception {
    Path project = createProject();
    Path prop = touch(project.resolve("assets/miscs/lunch tray.png"));
    AssetAutoLabelService service = new AssetAutoLabelService();
    AssetSuggestion suggestion = byPath(service.preview(project), project.relativize(prop).toString())
        .reviewed(AssetKind.PROP, "lunch_tray", "full");

    AssetAutoLabelService.AppliedDeclaration first = service.applyDeclaration(project, suggestion);
    AssetAutoLabelService.AppliedDeclaration second = service.applyDeclaration(project, suggestion);
    assertNotNull(first.declarationFile());
    assertTrue(first.characterAdded());
    assertTrue(first.entryIncludeAdded());
    assertFalse(second.characterAdded());
    assertFalse(second.entryIncludeAdded());

    String generated = Files.readString(
        project.resolve(AssetAutoLabelService.AUTO_DECLARATIONS_PATH));
    assertTrue(generated.contains("@character lunch_tray \"\""));
    assertTrue(generated.contains(
        "@charlayer lunch_tray full \"assets/miscs/lunch tray.png\""), generated);
    assertEquals(1, occurrences(generated, "@charlayer lunch_tray full"));
    String entry = Files.readString(project.resolve("scripts/story/main.vns"));
    assertEquals(1, occurrences(entry, "@include /definitions/auto_labels.vns"));
  }

  @Test
  void importsExternalAssetsIntoRecommendedUniqueLocations() throws Exception {
    Path project = createProject();
    Path outside = touch(tempDir.resolve("outside/sunset.png"));
    AssetAutoLabelService service = new AssetAutoLabelService();
    AssetSuggestion dropped = service.suggestDroppedAsset(project, outside)
        .reviewed(AssetKind.BACKGROUND, "", "sunset");
    Path first = service.importDroppedAsset(project, outside, dropped);
    Path second = service.importDroppedAsset(project, outside, dropped);
    assertEquals(project.resolve("assets/backgrounds/sunset.png"), first);
    assertEquals(project.resolve("assets/backgrounds/sunset_2.png"), second);
    assertTrue(Files.isRegularFile(first));
    assertTrue(Files.isRegularFile(second));
  }

  @Test
  void reportsOneScopedVnsLabelThatPointsToDifferentAssets() throws Exception {
    Path project = createProject();
    Path first = touch(project.resolve("assets/backgrounds/room_day.png"));
    Path second = touch(project.resolve("assets/backgrounds/room_night.png"));
    Files.writeString(project.resolve("scripts/definitions/visuals.vns"), """
        @background room assets/backgrounds/room_day.png
        @background room assets/backgrounds/room_night.png
        """);
    AssetAutoLabelService.ScanResult result = new AssetAutoLabelService().preview(project);
    for (Path asset : java.util.List.of(first, second)) {
      AssetSuggestion conflict = byPath(result, project.relativize(asset).toString());
      assertEquals(LabelStatus.CONFLICT, conflict.status());
      assertTrue(conflict.reason().contains("points to multiple files"));
    }
  }

  @Test
  void treatsMultipleLabelsForOneFileAsIntentionalAliases() throws Exception {
    Path project = createProject();
    Path asset = touch(project.resolve("assets/backgrounds/room.png"));
    Files.writeString(project.resolve("scripts/definitions/visuals.vns"), """
        @background room_day assets/backgrounds/room.png
        @background room_night assets/backgrounds/room.png
        """);
    AssetSuggestion aliased = byPath(
        new AssetAutoLabelService().preview(project), project.relativize(asset).toString());
    assertEquals(LabelStatus.DECLARED, aliased.status());
    assertTrue(aliased.reason().contains("2 VNS aliases"));
  }

  @Test
  void bulkApplyWritesCharactersDeclarationsAndRegistryOnce() throws Exception {
    Path project = createProject();
    Path first = touch(project.resolve("assets/props/tray_full.png"));
    Path second = touch(project.resolve("assets/props/tray_empty.png"));
    AssetAutoLabelService service = new AssetAutoLabelService();
    AssetAutoLabelService.ScanResult scan = service.preview(project);
    java.util.List<AssetSuggestion> reviewed = java.util.List.of(
        byPath(scan, project.relativize(first).toString())
            .reviewed(AssetKind.PROP, "lunch_tray", "full"),
        byPath(scan, project.relativize(second).toString())
            .reviewed(AssetKind.PROP, "lunch_tray", "empty"));

    AssetAutoLabelService.BatchAppliedDeclarations applied =
        service.applyDeclarations(project, reviewed);
    assertEquals(2, applied.declarationsGenerated());
    assertEquals(1, applied.charactersAdded());
    assertEquals(0, applied.labelsSaved());
    String generated = Files.readString(
        project.resolve(AssetAutoLabelService.AUTO_DECLARATIONS_PATH));
    assertEquals(1, occurrences(generated, "@character lunch_tray"));
    assertTrue(generated.contains("@charlayer lunch_tray full assets/props/tray_full.png"));
    assertTrue(generated.contains("@charlayer lunch_tray empty assets/props/tray_empty.png"));
    assertEquals(0, service.applyDeclarations(project, reviewed).declarationsGenerated());
    assertEquals(2, service.preview(project).declaredCount());
  }

  @Test
  void surfacesDeclarationsWhoseAssetFileIsMissing() throws Exception {
    Path project = createProject();
    Files.writeString(project.resolve("scripts/definitions/visuals.vns"),
        "@background vanished assets/backgrounds/vanished.png\n");
    AssetAutoLabelService.ScanResult result = new AssetAutoLabelService().preview(project);
    assertEquals(0, result.currentAssetCount());
    assertEquals(1, result.missingCount());
    assertEquals(LabelStatus.MISSING, result.assets().getFirst().status());
  }

  @Test
  void reservesEveryAliasAndSavedLabelForScanAndDrop() throws Exception {
    Path project = createProject();
    touch(project.resolve("assets/backgrounds/original.png"));
    Path added = touch(project.resolve("assets/backgrounds/night.png"));
    Files.writeString(project.resolve("scripts/definitions/visuals.vns"), """
        @background day assets/backgrounds/original.png
        @background night assets/backgrounds/original.png
        """);
    AssetAutoLabelService service = new AssetAutoLabelService();
    AssetSuggestion suggestion = byPath(service.preview(project), "assets/backgrounds/night.png");
    assertEquals("night_2", suggestion.label());
    assertEquals("night_2", service.suggestDroppedAsset(project, added).label());
    service.saveDecision(project, suggestion.reviewed(AssetKind.BACKGROUND, "", "night_2"),
        LabelStatus.LABELED);
    Path outside = touch(tempDir.resolve("outside/backgrounds/night.png"));
    assertEquals("night_3", service.suggestDroppedAsset(project, outside).label());
  }

  @Test
  void mixedFoldersKeepNonImageTypesAndGlobalBackgroundScope() throws Exception {
    Path project = createProject();
    touch(project.resolve("assets/characters/ari/idle.png"));
    touch(project.resolve("assets/characters/ari/voice.ogg"));
    touch(project.resolve("assets/characters/ari/atlas.json"));
    Files.writeString(project.resolve("scripts/definitions/visuals.vns"), """
        @charlayer ari idle assets/characters/ari/idle.png
        """);
    AssetAutoLabelService.ScanResult result = new AssetAutoLabelService().preview(project);
    assertEquals(AssetKind.AUDIO, byPath(result, "assets/characters/ari/voice.ogg").kind());
    assertEquals(AssetKind.DATA, byPath(result, "assets/characters/ari/atlas.json").kind());
    assertEquals("", byPath(result, "assets/characters/ari/voice.ogg").owner());
    assertTrue(byPath(result, "assets/characters/ari/atlas.json").confidence() < 0.80);
  }

  @Test
  void rejectsBatchCollisionsAndMissingFilesBeforeAnyWrites() throws Exception {
    Path project = createProject();
    touch(project.resolve("assets/backgrounds/day.png"));
    Path night = touch(project.resolve("assets/backgrounds/night.png"));
    AssetAutoLabelService service = new AssetAutoLabelService();
    var scan = service.preview(project);
    var day = byPath(scan, "assets/backgrounds/day.png").reviewed(AssetKind.BACKGROUND, "", "room");
    var duplicate = byPath(scan, "assets/backgrounds/night.png").reviewed(AssetKind.BACKGROUND, "", "room");
    assertThrows(java.io.IOException.class,
        () -> service.applyDeclarations(project, java.util.List.of(day, duplicate)));
    assertFalse(Files.exists(project.resolve(AssetAutoLabelService.AUTO_DECLARATIONS_PATH)));
    assertFalse(Files.exists(project.resolve(AssetAutoLabelService.REGISTRY_PATH)));
    Files.delete(night);
    var missing = duplicate.reviewed(AssetKind.BACKGROUND, "", "night");
    assertThrows(java.io.IOException.class,
        () -> service.applyDeclarations(project, java.util.List.of(day, missing)));
    assertFalse(Files.exists(project.resolve(AssetAutoLabelService.AUTO_DECLARATIONS_PATH)));
    service.applyDeclaration(project, day);
    touch(night);
    assertThrows(java.io.IOException.class, () -> service.applyDeclaration(project, duplicate));
    assertEquals(1, service.preview(project).declaredCount());
  }

  @Test
  void removedDeclarationsReturnToReviewInsteadOfStaleDeclaredStatus() throws Exception {
    Path project = createProject();
    touch(project.resolve("assets/backgrounds/day.png"));
    AssetAutoLabelService service = new AssetAutoLabelService();
    service.applyDeclaration(project, service.preview(project).assets().getFirst());
    Files.delete(project.resolve(AssetAutoLabelService.AUTO_DECLARATIONS_PATH));
    assertEquals(LabelStatus.SUGGESTED, service.preview(project).assets().getFirst().status());
  }

  @Test
  void generatedCharacterLayersAndSpritesLoadThroughEntryScript() throws Exception {
    Path project = createProject();
    touch(project.resolve("assets/characters/ari/eyes open.png"));
    touch(project.resolve("assets/characters/ari/happy.png"));
    AssetAutoLabelService service = new AssetAutoLabelService();
    var scan = service.preview(project);
    service.applyDeclarations(project, java.util.List.of(
        byPath(scan, "assets/characters/ari/eyes open.png").reviewed(AssetKind.CHARACTER_LAYER, "ari", "eyes_open"),
        byPath(scan, "assets/characters/ari/happy.png").reviewed(AssetKind.CHARACTER_SPRITE, "ari", "happy")));
    try (var input = Files.newInputStream(project.resolve("scripts/story/main.vns"))) {
      var scenario = new com.jvn.core.vn.script.VnScriptParser().parse(input, "story/main.vns",
          path -> Files.newInputStream(project.resolve("scripts").resolve(path.replaceFirst("^/", ""))));
      assertEquals("assets/characters/ari/eyes open.png", scenario.getCharacter("ari").getLayerPath("eyes_open"));
      assertEquals("assets/characters/ari/happy.png", scenario.getCharacter("ari").getExpressionPath("happy"));
    }
  }

  @Test
  void learnsAuthoredEyeAndPanelPatternsInsteadOfUsingDirectoryMajority() throws Exception {
    Path project = createProject();
    String eyes = "assets/characters/john_doe/head/head normal/eyes/";
    for (String number : java.util.List.of("01", "02", "03")) {
      touch(project.resolve(eyes + "john_doe_hn_e_-_" + number + ".png"));
      touch(project.resolve("assets/panels/Panel_Cafe_2_wendi_eyes" + number + ".png"));
    }
    touch(project.resolve("assets/panels/Panel_Cafe_1_base.png"));
    Files.writeString(project.resolve("scripts/definitions/visuals.vns"),
        "@charlayer john eyes_n_01 \"" + eyes + "john_doe_hn_e_-_01.png\"\n"
        + "@charlayer john eyes_n_02 \"" + eyes + "john_doe_hn_e_-_02.png\"\n"
        + "@charlayer panel_01 base assets/panels/Panel_Cafe_1_base.png\n"
        + "@charlayer panel_02 eyes_01 assets/panels/Panel_Cafe_2_wendi_eyes01.png\n"
        + "@charlayer panel_02 eyes_02 assets/panels/Panel_Cafe_2_wendi_eyes02.png\n");
    var result = new AssetAutoLabelService().preview(project);
    var eye = byPath(result, eyes + "john_doe_hn_e_-_03.png");
    assertEquals("john", eye.owner());
    assertEquals("eyes_n_03", eye.label());
    assertTrue(eye.reason().contains("label pattern learned"));
    var panel = byPath(result, "assets/panels/Panel_Cafe_2_wendi_eyes03.png");
    assertEquals("panel_02", panel.owner());
    assertEquals("eyes_03", panel.label());
    assertTrue(panel.confidence() >= 0.80);
  }

  @Test
  void ambiguousSharedFolderStaysBelowAutoLabelThreshold() throws Exception {
    Path project = createProject();
    for (String name : java.util.List.of("first", "second", "new")) {
      touch(project.resolve("assets/props/" + name + ".png"));
    }
    Files.writeString(project.resolve("scripts/definitions/visuals.vns"), """
        @charlayer first idle assets/props/first.png
        @charlayer second idle assets/props/second.png
        """);
    var suggestion = byPath(new AssetAutoLabelService().preview(project), "assets/props/new.png");
    assertTrue(suggestion.confidence() < 0.80);
  }

  private Path createProject() throws Exception {
    Path project = tempDir.resolve("project");
    Files.createDirectories(project.resolve("scripts/story"));
    Files.createDirectories(project.resolve("scripts/definitions"));
    Files.writeString(project.resolve("jvn.project"), "entryVns=scripts/story/main.vns\n");
    Files.writeString(project.resolve("scripts/story/main.vns"), "@scenario test\n");
    return project;
  }

  private Path touch(Path path) throws Exception {
    Files.createDirectories(path.getParent());
    Files.write(path, new byte[] {0});
    return path;
  }

  private AssetSuggestion byPath(AssetAutoLabelService.ScanResult result, String relativePath) {
    String normalized = relativePath.replace('\\', '/');
    return result.assets().stream().filter(asset -> asset.relativePath().equals(normalized))
        .findFirst().orElseThrow();
  }

  private int occurrences(String source, String needle) {
    int count = 0;
    int index = 0;
    while ((index = source.indexOf(needle, index)) >= 0) {
      count++;
      index += needle.length();
    }
    return count;
  }
}
