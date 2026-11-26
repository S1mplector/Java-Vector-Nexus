package com.jvn.scripting.jes;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.ClasspathAssetManager;
import com.jvn.core.assets.FilesystemAssetManager;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class JesProjectTilemapTest {
  @Test
  void readsEditableCollisionLayerFromProjectAssets(@TempDir Path project) throws Exception {
    Files.createDirectories(project.resolve("assets/maps"));
    Files.writeString(project.resolve("assets/maps/walls.csv"), "-1,0\n0,-1\n");
    AssetCatalog.setDefaultManager(new FilesystemAssetManager(project));
    try {
      var scene = JesLoader.load("""
          scene "Map" {
            tileset "tiles" { image: "tiles.png" tileW: 16 tileH: 16 cols: 1 }
            map "room" {
              tileset: "tiles" width: 2 height: 2 tileW: 32 tileH: 32
              layer "walls" { data: "assets/maps/walls.csv" collision: true }
            }
          }
          """);
      assertFalse(scene.isTileBlocked(0, 0));
      assertTrue(scene.isTileBlocked(1, 0));
      assertTrue(scene.isTileBlocked(0, 1));
      assertFalse(scene.isTileBlocked(1, 1));
    } finally {
      AssetCatalog.setDefaultManager(new ClasspathAssetManager());
    }
  }
}
