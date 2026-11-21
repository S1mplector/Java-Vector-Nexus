package com.jvn.core.vn;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Properties;

/**
 * Resolves runtime-owned files for the active game.
 *
 * <p>Games launched through the runtime are stored below
 * {@code ~/.jvn/games/<game-id>}. Tools without an active game keep the legacy
 * {@code ~/.jvn} root. Set {@value #STORAGE_ROOT_PROPERTY} to use an explicit
 * root instead.</p>
 */
public final class VnStoragePaths {
  public static final String GAME_ID_PROPERTY = "jvn.game.id";
  public static final String STORAGE_ROOT_PROPERTY = "jvn.storage.root";

  private static final int MAX_GAME_ID_LENGTH = 96;

  private VnStoragePaths() {
  }

  public static void configureGame(String gameId) {
    String normalized = normalizeId(gameId);
    if (normalized.isEmpty()) {
      System.clearProperty(GAME_ID_PROPERTY);
    } else {
      System.setProperty(GAME_ID_PROPERTY, normalized);
    }
  }

  public static String resolveGameId(Properties manifest, Path projectRoot) {
    String explicitId = manifest == null ? "" : normalizeId(manifest.getProperty("id"));
    if (!explicitId.isEmpty()) return explicitId;

    String name = manifest == null ? "" : value(manifest.getProperty("name"));
    String author = manifest == null ? "" : value(manifest.getProperty("author"));
    String derived = normalizeId(author.isEmpty() ? name : author + "-" + name);
    if (!derived.isEmpty()) return derived;

    if (projectRoot != null && projectRoot.getFileName() != null) {
      derived = normalizeId(projectRoot.getFileName().toString());
      if (!derived.isEmpty()) return derived;
    }
    return "game";
  }

  public static String sanitizeGameId(String gameId) {
    String normalized = normalizeId(gameId);
    return normalized.isEmpty() ? "game" : normalized;
  }

  public static Path root() {
    String configuredRoot = System.getProperty(STORAGE_ROOT_PROPERTY, "").trim();
    if (!configuredRoot.isEmpty()) {
      try {
        return Paths.get(configuredRoot).toAbsolutePath().normalize();
      } catch (InvalidPathException ignored) {
        // Fall through to the safe default when an external override is malformed.
      }
    }

    Path base = Paths.get(System.getProperty("user.home"), ".jvn");
    String gameId = normalizeId(System.getProperty(GAME_ID_PROPERTY));
    return gameId.isEmpty() ? base : base.resolve("games").resolve(gameId);
  }

  /** Games can load any chapter saved in their own per-game directory. */
  public static boolean isGameSaveDirectory(Path directory) {
    String gameId = normalizeId(System.getProperty(GAME_ID_PROPERTY));
    if (gameId.isEmpty()) return false;
    Path expected = Paths.get(System.getProperty("user.home"), ".jvn", "games", gameId, "saves");
    return expected.toAbsolutePath().normalize().equals(directory.toAbsolutePath().normalize());
  }

  public static Path saves() {
    return root().resolve("saves");
  }

  public static Path persistentData() {
    return root().resolve("persistent.json");
  }

  public static Path settings() {
    return root().resolve("settings.properties");
  }

  private static String normalizeId(String raw) {
    if (raw == null || raw.isBlank()) return "";
    String ascii = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT);
    String normalized = ascii
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-+", "")
        .replaceAll("-+$", "");
    if (normalized.length() > MAX_GAME_ID_LENGTH) {
      normalized = normalized.substring(0, MAX_GAME_ID_LENGTH).replaceAll("-+$", "");
    }
    return normalized;
  }

  private static String value(String raw) {
    return raw == null ? "" : raw.trim();
  }
}
