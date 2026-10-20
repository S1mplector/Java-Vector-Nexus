package com.jvn.core.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class PackFileAssetManagerTest {
  @TempDir Path directory;

  @Test
  void listsImplicitDirectoriesAndStreamsIndependentAssets() throws Exception {
    Path pack = directory.resolve("assets with spaces.pack");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(pack))) {
      for (String name : List.of("game/scripts/story/b.vns", "game/scripts/story/a.vns", "manifest.txt")) {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(name.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    PackFileAssetManager assets = new PackFileAssetManager(pack.toString());
    assertEquals(List.of("game", "manifest.txt"), assets.list(""));
    assertEquals(List.of("scripts"), assets.list("/game/"));
    assertEquals(List.of("story"), assets.list("game/scripts"));
    assertEquals(List.of("a.vns", "b.vns"), assets.list("game/scripts/story/"));
    assertEquals(List.of(), assets.list("missing"));
    assets.list("game/scripts/story").clear();
    assertEquals(2, assets.list("game/scripts/story").size());
    assertTrue(assets.exists(AssetType.SCRIPT, "story/a.vns"));
    try (var a = assets.open(AssetType.SCRIPT, "story/a.vns");
         var b = assets.open(AssetType.SCRIPT, "story/b.vns")) {
      a.close();
      assertEquals("game/scripts/story/b.vns", new String(b.readAllBytes(), StandardCharsets.UTF_8));
    }
    assertThrows(IOException.class, () -> assets.open(AssetType.SCRIPT, "missing.vns"));
    Files.delete(pack);
  }

  @Test
  void rejectsNonArchiveInputInsteadOfSilentlyIndexingAnEmptyPack() throws Exception {
    Path pack = Files.writeString(directory.resolve("invalid.pack"), "this is not a zip");
    assertThrows(IOException.class, () -> new PackFileAssetManager(pack.toString()));
  }
}
