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
    assertTrue(byPath(service.scan(project), project.relativize(newLayer).toString()).isNew(),
        "A rescan must not silently consume a newly discovered asset");
    assertTrue(byPath(new AssetAutoLabelService().scan(project), project.relativize(newLayer).toString()).isNew(),
        "New flags survive reopening the project");
    service.saveDecision(project, inferred, LabelStatus.IGNORED);
    assertFalse(byPath(service.scan(project), project.relativize(newLayer).toString()).isNew());
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

  @Test
  void conflictingAndMissingSiblingsCannotAuthorizeAutomaticLabels() throws Exception {
    Path project = createProject();
    touch(project.resolve("assets/props/item_01.png"));
    touch(project.resolve("assets/props/item_02.png"));
    touch(project.resolve("assets/props/item_03.png"));
    Files.writeString(project.resolve("scripts/definitions/visuals.vns"), """
        @charlayer wrong idle assets/props/item_01.png
        @charlayer wrong idle assets/props/item_02.png
        @charlayer ghost other assets/props/item_04.png
        """);
    var suggestion = byPath(new AssetAutoLabelService().preview(project), "assets/props/item_03.png");
    assertTrue(suggestion.confidence() < 0.80);
    assertFalse(suggestion.reason().contains("declared sibling"));
  }

  @Test
  void sharedAncestorDoesNotAssignMajorityOwnerToUnrelatedSubfolder() throws Exception {
    Path project = createProject();
    touch(project.resolve("assets/characters/shared/first/a.png"));
    touch(project.resolve("assets/characters/shared/second/b.png"));
    touch(project.resolve("assets/characters/shared/third/c.png"));
    Files.writeString(project.resolve("scripts/definitions/visuals.vns"), """
        @charlayer alice a assets/characters/shared/first/a.png
        @charlayer bob b assets/characters/shared/second/b.png
        """);
    var suggestion = byPath(new AssetAutoLabelService().preview(project),
        "assets/characters/shared/third/c.png");
    assertEquals("shared", suggestion.owner());
    assertTrue(suggestion.confidence() < 0.80);
  }

  @Test
  void deletedReviewedAssetsRemainVisibleAndRecoverWhenRestored() throws Exception {
    Path project = createProject();
    Path audio = touch(project.resolve("assets/audio/theme.ogg"));
    AssetAutoLabelService service = new AssetAutoLabelService();
    service.saveDecision(project, service.scan(project).assets().getFirst(), LabelStatus.LABELED);
    Files.delete(audio);
    var missing = service.scan(project);
    assertEquals(1, missing.missingCount());
    assertEquals(0, missing.currentAssetCount());
    touch(audio);
    assertEquals(LabelStatus.LABELED, service.scan(project).assets().getFirst().status());
  }

  @Test
  void retryRepairsMissingEntryIncludeWithoutDuplicatingDeclarations() throws Exception {
    Path project = createProject();
    touch(project.resolve("assets/backgrounds/room.png"));
    AssetAutoLabelService service = new AssetAutoLabelService();
    var suggestion = service.preview(project).assets().getFirst();
    service.applyDeclaration(project, suggestion);
    Path entry = project.resolve("scripts/story/main.vns");
    Files.writeString(entry, "@scenario test\n");
    assertTrue(service.applyDeclaration(project, suggestion).entryIncludeAdded());
    Files.writeString(entry, "@scenario test\n");
    var batch = service.applyDeclarations(project, java.util.List.of(suggestion));
    assertTrue(batch.entryIncludeAdded());
    assertEquals(0, batch.declarationsGenerated());
    assertEquals(1, occurrences(Files.readString(project.resolve(
        AssetAutoLabelService.AUTO_DECLARATIONS_PATH)), "@background room"));
  }

  @Test
  void malformedRegistryAndNullBatchFailBeforeScriptChanges() throws Exception {
    Path project = createProject();
    touch(project.resolve("assets/backgrounds/room.png"));
    AssetAutoLabelService service = new AssetAutoLabelService();
    var suggestion = service.preview(project).assets().getFirst();
    assertThrows(java.io.IOException.class, () -> service.applyDeclarations(project,
        java.util.Arrays.asList(suggestion, null)));
    Path registry = project.resolve(AssetAutoLabelService.REGISTRY_PATH);
    Files.createDirectories(registry.getParent());
    String malformed = "broken=" + (char) 92 + "uZZZZ";
    Files.writeString(registry, malformed);
    assertThrows(java.io.IOException.class, () -> service.applyDeclaration(project, suggestion));
    assertThrows(java.io.IOException.class, () -> service.applyDeclarations(project, java.util.List.of(suggestion)));
    assertFalse(Files.exists(project.resolve(AssetAutoLabelService.AUTO_DECLARATIONS_PATH)));
    assertEquals("@scenario test\n", Files.readString(project.resolve("scripts/story/main.vns")));
    assertEquals(malformed, Files.readString(registry));
  }

  @Test
  void savedLabelCollisionsAreVisibleAndResolveAfterReview() throws Exception {
    Path project = createProject();
    touch(project.resolve("assets/backgrounds/day.png"));
    touch(project.resolve("assets/backgrounds/night.png"));
    AssetAutoLabelService service = new AssetAutoLabelService();
    var scan = service.preview(project);
    var day = byPath(scan, "assets/backgrounds/day.png").reviewed(AssetKind.BACKGROUND, "", "room");
    var night = byPath(scan, "assets/backgrounds/night.png").reviewed(AssetKind.BACKGROUND, "", "room");
    service.saveDecision(project, day, LabelStatus.LABELED);
    service.saveDecision(project, night, LabelStatus.LABELED);
    assertEquals(2, service.preview(project).byStatus().get(LabelStatus.CONFLICT));
    service.saveDecision(project, night.reviewed(AssetKind.BACKGROUND, "", "night"), LabelStatus.LABELED);
    assertEquals(2, service.preview(project).labeledCount());
    Files.writeString(project.resolve("scripts/definitions/visuals.vns"),
        "@background room assets/backgrounds/night.png\n");
    assertEquals(LabelStatus.CONFLICT, byPath(service.preview(project), "assets/backgrounds/day.png").status());
  }

  @Test
  void backgroundReviewAlwaysUsesGlobalLabelScope() throws Exception {
    Path project = createProject();
    touch(project.resolve("assets/backgrounds/day.png"));
    AssetAutoLabelService service = new AssetAutoLabelService();
    var suggestion = service.preview(project).assets().getFirst().reviewed(AssetKind.BACKGROUND, "unused", "room");
    assertEquals("", suggestion.owner());
    service.saveDecision(project, suggestion, LabelStatus.LABELED);
    Path external = touch(tempDir.resolve("outside/backgrounds/room.png"));
    assertEquals("room_2", service.suggestDroppedAsset(project, external).label());
  }

  @Test
  void rejectsSymlinkEscapesAndControlCharactersBeforeWrites() throws Exception {
    Path project = createProject();
    Path external = touch(tempDir.resolve("outside/room.png"));
    Path link = project.resolve("assets/backgrounds/room.png");
    Files.createDirectories(link.getParent());
    Files.createSymbolicLink(link, external);
    AssetAutoLabelService service = new AssetAutoLabelService();
    var suggestion = service.preview(project).assets().getFirst();
    assertThrows(java.io.IOException.class, () -> service.applyDeclaration(project, suggestion));
    Path newline = touch(project.resolve("assets/backgrounds/room\nnight.png"));
    var invalid = new AssetSuggestion(newline, project.relativize(newline).toString(),
        AssetKind.BACKGROUND, "", "night", LabelStatus.LABELED, 1, "", null, -1, false);
    assertThrows(java.io.IOException.class, () -> service.applyDeclaration(project, invalid));
    assertFalse(Files.exists(project.resolve(AssetAutoLabelService.AUTO_DECLARATIONS_PATH)));
    assertFalse(Files.exists(project.resolve(AssetAutoLabelService.REGISTRY_PATH)));
  }

  @Test
  void emptyBatchIsANoOp() throws Exception {
    Path project = createProject();
    AssetAutoLabelService service = new AssetAutoLabelService();
    assertEquals(0, service.applyDeclarations(project, null).declarationsGenerated());
    assertEquals(0, service.applyDeclarations(project, java.util.List.of()).labelsSaved());
    assertFalse(Files.exists(project.resolve(AssetAutoLabelService.REGISTRY_PATH)));
    assertFalse(Files.exists(project.resolve(AssetAutoLabelService.AUTO_DECLARATIONS_PATH)));
  }

  @Test
  void rejectsContradictoryReviewsForSameAssetBeforeWriting() throws Exception {
    Path project = createProject();
    touch(project.resolve("assets/characters/ari/idle.png"));
    AssetAutoLabelService service = new AssetAutoLabelService();
    var layer = service.preview(project).assets().getFirst()
        .reviewed(AssetKind.CHARACTER_LAYER, "ari", "idle");
    var sprite = layer.reviewed(AssetKind.CHARACTER_SPRITE, "ari", "idle");
    assertThrows(java.io.IOException.class,
        () -> service.applyDeclarations(project, java.util.List.of(layer, sprite)));
    assertFalse(Files.exists(project.resolve(AssetAutoLabelService.AUTO_DECLARATIONS_PATH)));
    service.applyDeclaration(project, layer);
    assertThrows(java.io.IOException.class, () -> service.applyDeclaration(project, sprite));
  }

  @Test
  void repeatedNonVnsReviewsAreSavedAndCountedOnce() throws Exception {
    Path project = createProject();
    touch(project.resolve("assets/audio/theme.ogg"));
    AssetAutoLabelService service = new AssetAutoLabelService();
    var audio = service.preview(project).assets().getFirst();
    assertEquals(1, service.applyDeclarations(project, java.util.List.of(audio, audio)).labelsSaved());
  }

  @Test
  void generatedFileAsEntryNeverIncludesItself() throws Exception {
    Path project = createProject();
    touch(project.resolve("assets/backgrounds/room.png"));
    Files.writeString(project.resolve("jvn.project"),
        "entryVns=" + AssetAutoLabelService.AUTO_DECLARATIONS_PATH + "\n");
    AssetAutoLabelService service = new AssetAutoLabelService();
    service.applyDeclaration(project, service.preview(project).assets().getFirst());
    assertFalse(Files.readString(project.resolve(AssetAutoLabelService.AUTO_DECLARATIONS_PATH))
        .contains("@include"));
  }

  @Test
  void entryIncludePreservesWindowsLineEndings() throws Exception {
    Path project = createProject();
    touch(project.resolve("assets/backgrounds/room.png"));
    Path entry = project.resolve("scripts/story/main.vns");
    Files.writeString(entry, "@scenario test\r\n# Keep this comment\r\n");
    AssetAutoLabelService service = new AssetAutoLabelService();
    service.applyDeclaration(project, service.preview(project).assets().getFirst());
    assertEquals("@scenario test\r\n# Keep this comment\r\n@include /definitions/auto_labels.vns\r\n",
        Files.readString(entry));
  }

  @Test
  void externalDropsLearnFromProjectSiblingsInsteadOfSourceDirectory() throws Exception {
    Path project = createProject();
    touch(project.resolve("assets/backgrounds/bg_room_day.png"));
    touch(project.resolve("assets/backgrounds/bg_room_night.png"));
    Files.writeString(project.resolve("scripts/definitions/visuals.vns"), """
        @background room_day assets/backgrounds/bg_room_day.png
        @background room_night assets/backgrounds/bg_room_night.png
        """);
    Path dropped = touch(tempDir.resolve("outside/backgrounds/bg_room_evening.png"));
    var suggestion = new AssetAutoLabelService().suggestDroppedAsset(project, dropped);
    assertEquals("room_evening", suggestion.label());
    assertTrue(suggestion.confidence() >= 0.80);
    assertTrue(suggestion.reason().contains("2 declared siblings"));
  }

  @Test
  void identifiesWholePosesLayersAndUnrelatedArtInMixedDirectories() throws Exception {
    Path project = createProject();
    for (String file : java.util.List.of("eyes.png", "GingerThoughtful.png", "gingerEyesClosed.png",
        "gingerbread.png", "gingerMenuIcon.png", "plate.png", "bg_station.png")) {
      touch(project.resolve("assets/art").resolve(file));
    }
    touch(project.resolve("assets/characters/ada/neutral.png"));
    touch(project.resolve("assets/characters/ada/ref.png"));
    Files.writeString(project.resolve("scripts/definitions/visuals.vns"), """
        @character ginger "Ginger"
        @character ada "Ada"
        @charlayer ginger eyes assets/art/eyes.png
        @charimg ginger borrowed assets/characters/ada/ref.png
        """);
    var scan = new AssetAutoLabelService().preview(project);
    var pose = byPath(scan, "assets/art/GingerThoughtful.png");
    assertEquals(AssetKind.CHARACTER_SPRITE, pose.kind());
    assertEquals("ginger", pose.owner());
    assertEquals("thoughtful", pose.label());
    assertTrue(pose.confidence() >= .8);
    var layer = byPath(scan, "assets/art/gingerEyesClosed.png");
    assertEquals(AssetKind.CHARACTER_LAYER, layer.kind());
    assertEquals("ginger", layer.owner());
    assertEquals(AssetKind.UI, byPath(scan, "assets/art/gingerMenuIcon.png").kind());
    assertEquals(AssetKind.BACKGROUND, byPath(scan, "assets/art/bg_station.png").kind());
    assertEquals("ada", byPath(scan, "assets/characters/ada/neutral.png").owner());
    for (String unrelated : java.util.List.of("plate.png", "gingerbread.png")) {
      var image = byPath(scan, "assets/art/" + unrelated);
      assertEquals(AssetKind.UNKNOWN, image.kind());
      assertEquals("", image.owner());
      assertTrue(image.confidence() < .8);
    }
  }

  @Test
  void resolvesRuntimePathsAndUsesScriptAndConfigurationEvidence() throws Exception {
    Path project = createProject();
    for (String file : java.util.List.of("game/images/hall.png", "game/images/brand.png", "game/fonts/type.woff2",
        "game/audio/welcome.wav", "game/audio/shared.ogg", "game/audio/voice/ada/line.wav")) {
      touch(project.resolve(file));
    }
    Files.createDirectories(project.resolve("game/scripts"));
    Files.writeString(project.resolve("game/scripts/demo.vns"), """
        @background hall hall.png
        [sfx welcome.wav]
        [bgm shared.ogg]
        [sfx shared.ogg]
        """);
    Files.createDirectories(project.resolve("config"));
    Files.writeString(project.resolve("config/ui.properties"),
        "profile.release.icon=game/images/brand.png\ntitleFontFamily=game/fonts/type.woff2\n");
    var service = new AssetAutoLabelService();
    var scan = service.preview(project);
    assertEquals(0, scan.missingCount());
    assertEquals(LabelStatus.DECLARED, byPath(scan, "game/images/hall.png").status());
    var icon = byPath(scan, "game/images/brand.png");
    assertEquals(AssetKind.PROJECT_ICON, icon.kind());
    assertTrue(icon.reason().contains("profile.release.icon"));
    assertTrue(byPath(scan, "game/fonts/type.woff2").reason().contains("titleFontFamily"));
    var sound = byPath(scan, "game/audio/welcome.wav");
    assertEquals(AssetKind.SOUND_EFFECT, sound.kind());
    assertTrue(sound.reason().contains("demo.vns:2"));
    var shared = byPath(scan, "game/audio/shared.ogg");
    assertEquals(AssetKind.AUDIO, shared.kind());
    assertTrue(shared.confidence() < .8);
    var voice = byPath(scan, "game/audio/voice/ada/line.wav");
    assertEquals(AssetKind.VOICE, voice.kind());
    assertEquals("ada", voice.owner());
    var applied = service.applyDeclarations(project, java.util.List.of(sound, icon, voice));
    assertEquals(3, applied.labelsSaved());
    assertEquals(0, applied.declarationsGenerated());
    assertEquals("ada", byPath(service.preview(project), voice.relativePath()).owner());
    var dropped = service.suggestDroppedAsset(project, touch(tempDir.resolve("drop/characters/ada/idle.png")));
    assertEquals("ada", dropped.owner());
    assertEquals(AssetKind.CHARACTER_SPRITE, dropped.kind());
  }

  @Test
  void identifiesRenamedCopiesAndFlagsImagesSharedBetweenActors() throws Exception {
    Path project = createProject();
    byte[] png = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    for (String file : java.util.List.of("assets/art/hero.png", "assets/import/untitled.png")) {
      Files.write(touch(project.resolve(file)), png);
    }
    var script = project.resolve("scripts/definitions/visuals.vns");
    Files.writeString(script, "@charimg ada idle assets/art/hero.png");
    var service = new AssetAutoLabelService();
    var copy = byPath(service.preview(project), "assets/import/untitled.png");
    assertEquals(AssetKind.CHARACTER_SPRITE, copy.kind());
    assertEquals("ada", copy.owner());
    assertTrue(copy.reason().contains("Identical file"));
    assertTrue(copy.confidence() >= .8);
    Files.writeString(script, "\n@charimg ben idle assets/art/hero.png", java.nio.file.StandardOpenOption.APPEND);
    var shared = byPath(service.preview(project), "assets/import/untitled.png");
    assertTrue(shared.confidence() < .8);
    assertEquals("", shared.owner());
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
