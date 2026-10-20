package com.jvn.core.assets;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads a ZIP asset pack using its central directory, without decompressing assets
 * to build the index. Each open stream owns its archive handle; callers must close it.
 * Directory listings contain sorted immediate children, including implicit folders.
 */
public class PackFileAssetManager implements AssetManager {
  private static final Logger log = LoggerFactory.getLogger(PackFileAssetManager.class);
  private final Path packFile;
  private final Set<String> index = new HashSet<>();
  private final Map<String, Set<String>> directoryIndex = new HashMap<>();

  /** @throws IOException if the pack is missing, unreadable, or not a valid ZIP */
  public PackFileAssetManager(String packFilePath) throws IOException {
    packFile = Path.of(packFilePath).toAbsolutePath().normalize();
    try (ZipFile zip = new ZipFile(packFile.toFile())) {
      var entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String name = entry.getName();
        if (!entry.isDirectory()) index.add(name);
        String[] parts = name.split("/");
        String parent = "";
        for (String part : parts) {
          if (part.isEmpty()) continue;
          directoryIndex.computeIfAbsent(parent, ignored -> new TreeSet<>()).add(part);
          parent += part + "/";
        }
      }
    }
    log.info("Loaded pack file with {} entries: {}", index.size(), packFile);
  }

  @Override
  public boolean exists(AssetType type, String name) {
    return index.contains(AssetPaths.build(type, name));
  }

  @Override
  public URL url(AssetType type, String name) {
    // The AssetManager API permits stream-only stores.
    return null;
  }

  @Override
  public InputStream open(AssetType type, String name) throws IOException {
    String path = AssetPaths.build(type, name);
    if (!index.contains(path)) throw new IOException("Asset not found in pack: " + path);
    ZipFile zip = new ZipFile(packFile.toFile());
    try {
      ZipEntry entry = zip.getEntry(path);
      if (entry == null) throw new IOException("Entry not found in pack: " + path);
      return new FilterInputStream(zip.getInputStream(entry)) {
        @Override
        public void close() throws IOException {
          try {
            super.close();
          } finally {
            zip.close();
          }
        }
      };
    } catch (IOException | RuntimeException | Error failure) {
      try { zip.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
      throw failure;
    }
  }

  @Override
  public List<String> list(String directory) {
    String dir = directory == null ? "" : directory.replace('\\', '/');
    while (dir.startsWith("/")) dir = dir.substring(1);
    if (!dir.isEmpty() && !dir.endsWith("/")) dir += "/";
    Set<String> children = directoryIndex.get(dir);
    return children == null ? List.of() : new ArrayList<>(children);
  }
}
