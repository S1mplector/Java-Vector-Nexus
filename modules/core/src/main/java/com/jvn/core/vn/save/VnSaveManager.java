package com.jvn.core.vn.save;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import com.jvn.core.io.AtomicFiles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import com.jvn.core.vn.CharacterPosition;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.VnState;
import com.jvn.core.vn.VnStoragePaths;

/**
 * Manages saving and loading VN game state.
 */
public class VnSaveManager {
  private static final Logger log = LoggerFactory.getLogger(VnSaveManager.class);
  private static final String SAVE_EXTENSION = ".json";
  private static final String LEGACY_EXTENSION = ".sav";
  private static final String AUTOSAVE_PREFIX = "_autosave_";
  private static final int AUTOSAVE_SLOT_COUNT = 3;

  private final Path saveDirectory;

  public VnSaveManager(String saveDir) {
    this.saveDirectory = Paths.get(saveDir);
    try {
      Files.createDirectories(saveDirectory);
    } catch (IOException e) {
      log.error("Failed to create save directory: {}", e.getMessage());
    }
  }

  public VnSaveManager() {
    this(VnStoragePaths.saves().toString());
  }

  public String getSaveDirectory() {
    return saveDirectory.toString();
  }

  public String sanitizeSaveName(String name) {
    return sanitizeFileName(name);
  }

  public int getAutosaveSlotCount() {
    return AUTOSAVE_SLOT_COUNT;
  }

  public String getAutosaveSlotName(int slotIndex) {
    int idx = Math.max(0, Math.min(slotIndex, AUTOSAVE_SLOT_COUNT - 1));
    return AUTOSAVE_PREFIX + idx;
  }

  /**
   * Save the current VN state to a named slot.
   */
  public void save(VnState state, String saveName) throws IOException {
    if (state == null) throw new IllegalArgumentException("state cannot be null");
    if (state.getScenario() == null) throw new IllegalStateException("VnState has no scenario");

    VnSaveData saveData = new VnSaveData();
    saveData.setSaveName(saveName);
    saveData.setSchemaVersion(VnSaveData.CURRENT_SCHEMA_VERSION);
    saveData.setScenarioId(state.getScenario().getId());
    saveData.setScriptName(state.getSourceScriptName());
    saveData.setCurrentNodeIndex(state.getCurrentNodeIndex());
    saveData.setCurrentBackgroundId(state.getCurrentBackgroundId());
    saveData.setVariables(new java.util.HashMap<>(state.getVariables()));
    saveData.setReadNodes(state.getReadNodes());
    saveData.setCallStack(state.getCallStackSnapshot());
    saveData.setGlobalPositionCharacters(state.getGlobalPositionCharactersSnapshot());
    java.util.Map<String, String> definedPositions = new java.util.HashMap<>();
    for (var entry : state.getCharacterDefinedPositionsSnapshot().entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) continue;
      definedPositions.put(entry.getKey(), entry.getValue().getName());
    }
    saveData.setCharacterDefinedPositions(definedPositions);

    java.util.Map<String, String[]> vis = new java.util.HashMap<>();
    for (var entry : state.getVisibleCharacters().entrySet()) {
      String pos = entry.getKey().getName();
      CharacterPosition basePosition = entry.getKey().getBasePosition();
      VnState.CharacterSlot slot = entry.getValue();
      vis.put(pos, new String[] {
          slot.getCharacterId(),
          slot.getExpression(),
          Integer.toString(slot.getLayerOrder()),
          slot.getDisplaySlot(),
          basePosition.getName(),
          Double.toString(basePosition.getXFraction()),
          Double.toString(basePosition.getYFraction()),
          Boolean.toString(basePosition.isCustom())
      });
    }
    saveData.setVisibleCharacters(vis);

    saveData.setSkipMode(state.isSkipMode());
    saveData.setAutoPlayMode(state.isAutoPlayMode());
    saveData.setAutoPlayTimer(state.getAutoPlayTimer());
    saveData.setUiHidden(state.isUiHidden());

    VnSettings s = state.getSettings();
    VnSaveData.SettingsData sd = new VnSaveData.SettingsData();
    sd.setTextSpeed(s.getTextSpeed());
    sd.setBgmVolume(s.getBgmVolume());
    sd.setSfxVolume(s.getSfxVolume());
    sd.setVoiceVolume(s.getVoiceVolume());
    sd.setAutoPlayDelay(s.getAutoPlayDelay());
    sd.setSkipUnreadText(s.isSkipUnreadText());
    sd.setSkipAfterChoices(s.isSkipAfterChoices());
    sd.setClickRevealBeforeAdvance(s.isClickRevealBeforeAdvance());
    sd.setPhysicsFixedStepMs(s.getPhysicsFixedStepMs());
    sd.setPhysicsMaxSubSteps(s.getPhysicsMaxSubSteps());
    sd.setPhysicsDefaultFriction(s.getPhysicsDefaultFriction());
    sd.setInputProfilePath(s.getInputProfilePath());
    sd.setInputProfileSerialized(s.getInputProfileSerialized());
    sd.setDisplayWidth(s.getDisplayWidth());
    sd.setDisplayHeight(s.getDisplayHeight());
    sd.setAutoFitResolution(s.isAutoFitResolution());
    sd.setAccessibilityTheme(s.getAccessibilityTheme());
    sd.setTextToSpeechEnabled(s.isTextToSpeechEnabled());
    sd.setUiFontScale(s.getUiFontScale());
    saveData.setSettings(sd);

    // Optional RPG state passthrough.
    saveData.setRpgState(state.getRpgState());

    save(saveData, saveName);
  }

  /**
   * Persist a pre-built save payload to disk.
   */
  public void save(VnSaveData saveData, String saveName) throws IOException {
    if (saveData == null) throw new IllegalArgumentException("saveData cannot be null");
    String slot = sanitizeFileName(saveName);

    saveData.setSaveName(slot);
    saveData.setSchemaVersion(VnSaveData.CURRENT_SCHEMA_VERSION);
    saveData.setSaveTimestamp(System.currentTimeMillis());
    VnSaveMigration.migrateInPlace(saveData, slot);

    Path saveFile = saveDirectory.resolve(slot + SAVE_EXTENSION);
    writeAtomically(saveFile, saveData);
  }

  /**
   * Save to rotating autosave slots. Reuses the oldest existing autosave slot.
   *
   * @return The slot name that was written.
   */
  public String autosave(VnState state) throws IOException {
    int slot = chooseAutosaveSlot();
    String name = getAutosaveSlotName(slot);
    save(state, name);
    return name;
  }

  /**
   * Save to a specific autosave slot index.
   */
  public void autosave(VnState state, int slotIndex) throws IOException {
    String name = getAutosaveSlotName(slotIndex);
    save(state, name);
  }

  /**
   * Load a saved game state by name.
   * Supports both new JSON format and legacy .sav format for backward compatibility.
   */
  public VnSaveData load(String saveName) throws IOException, ClassNotFoundException {
    String slot = sanitizeFileName(saveName);
    Path jsonFile = saveDirectory.resolve(slot + SAVE_EXTENSION);
    Path legacyFile = saveDirectory.resolve(slot + LEGACY_EXTENSION);

    VnSaveData data;

    // Try JSON format first
    if (Files.exists(jsonFile)) {
      data = VnSaveSerializer.readFromFile(jsonFile);
    } else if (Files.exists(legacyFile)) {
      // Fall back to legacy Java serialization
      try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(legacyFile.toFile())))) {
        Object obj = ois.readObject();
        if (!(obj instanceof VnSaveData parsed)) {
          throw new IOException("Unsupported save payload in " + saveName);
        }
        data = parsed;
      }
      // Migrate legacy save to JSON format
      try {
        writeAtomically(jsonFile, data);
        Files.deleteIfExists(legacyFile); // Clean up legacy file after successful migration
      } catch (IOException e) {
        log.warn("Could not migrate legacy save to JSON: {}", e.getMessage());
      }
    } else {
      throw new FileNotFoundException("Save file not found: " + saveName);
    }

    boolean migrated = VnSaveMigration.migrateInPlace(data, slot);
    if (migrated) {
      try {
        writeAtomically(jsonFile, data);
      } catch (IOException e) {
        log.warn("Migrated save could not be written back: {}", e.getMessage());
      }
    }

    return data;
  }

  /**
   * Load the newest autosave by timestamp.
   */
  public VnSaveData loadLatestAutoSave() throws IOException, ClassNotFoundException {
    List<String> autos = listAutoSaves();
    String best = null;
    long bestTs = Long.MIN_VALUE;
    for (String name : autos) {
      try {
        VnSaveData d = load(name);
        long ts = d != null ? d.getSaveTimestamp() : Long.MIN_VALUE;
        if (ts > bestTs) {
          bestTs = ts;
          best = name;
        }
      } catch (Exception ignored) {
        // reason: slot metadata unreadable; skip to next candidate
      }
    }
    if (best == null) {
      throw new FileNotFoundException("No autosave slots found");
    }
    return load(best);
  }

  /**
   * Lists all save slot names (without file extension).
   */
  public List<String> listSaves() {
    List<String> saves = new ArrayList<>();
    java.util.Set<String> seen = new java.util.HashSet<>();
    File dir = saveDirectory.toFile();
    if (dir.exists() && dir.isDirectory()) {
      // List JSON saves
      File[] jsonFiles = dir.listFiles(file -> file.isFile() && file.getName().endsWith(SAVE_EXTENSION));
      if (jsonFiles != null) {
        for (File file : jsonFiles) {
          String name = file.getName();
          String slot = name.substring(0, name.length() - SAVE_EXTENSION.length());
          saves.add(slot);
          seen.add(slot);
        }
      }
      // Also list legacy .sav files not yet migrated
      File[] legacyFiles = dir.listFiles(file -> file.isFile() && file.getName().endsWith(LEGACY_EXTENSION));
      if (legacyFiles != null) {
        for (File file : legacyFiles) {
          String name = file.getName();
          String slot = name.substring(0, name.length() - LEGACY_EXTENSION.length());
          if (!seen.contains(slot)) {
            saves.add(slot);
          }
        }
      }
    }
    saves.sort(String::compareTo);
    return saves;
  }

  /**
   * Lists autosave slot names only.
   */
  public List<String> listAutoSaves() {
    List<String> autos = new ArrayList<>();
    for (String name : listSaves()) {
      if (name.startsWith(AUTOSAVE_PREFIX)) {
        autos.add(name);
      }
    }
    return autos;
  }

  /**
   * Delete a save file.
   */
  public boolean deleteSave(String saveName) {
    String slot = sanitizeFileName(saveName);
    Path jsonFile = saveDirectory.resolve(slot + SAVE_EXTENSION);
    Path legacyFile = saveDirectory.resolve(slot + LEGACY_EXTENSION);
    try {
      boolean deletedJson = Files.deleteIfExists(jsonFile);
      boolean deletedLegacy = Files.deleteIfExists(legacyFile);
      try {
        Path thumb = saveDirectory.resolve(slot + ".png");
        Files.deleteIfExists(thumb);
      } catch (Exception ignored) {
        // reason: thumbnail deletion is best-effort; save slot itself is already removed
      }
      return deletedJson || deletedLegacy;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Rename a save file.
   */
  public boolean renameSave(String oldName, String newName) {
    Path oldFile = saveDirectory.resolve(sanitizeFileName(oldName) + SAVE_EXTENSION);
    Path newFile = saveDirectory.resolve(sanitizeFileName(newName) + SAVE_EXTENSION);
    try {
      if (!Files.exists(oldFile)) return false;
      Files.move(oldFile, newFile, StandardCopyOption.REPLACE_EXISTING);
      // Try to rename sidecar thumbnail if present.
      try {
        Path oldPng = saveDirectory.resolve(sanitizeFileName(oldName) + ".png");
        Path newPng = saveDirectory.resolve(sanitizeFileName(newName) + ".png");
        if (Files.exists(oldPng)) {
          Files.move(oldPng, newPng, StandardCopyOption.REPLACE_EXISTING);
        }
      } catch (Exception ignored) {
        // reason: sidecar thumbnail rename is best-effort; the save slot itself was renamed
      }
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Apply loaded save data to a VN state.
   */
  public void applyToState(VnSaveData saveData, VnState state) {
    if (saveData == null || state == null) return;

    VnSaveMigration.migrateInPlace(saveData, saveData.getSaveName());

    state.setCurrentNodeIndex(saveData.getCurrentNodeIndex());
    state.restoreRetainedDialogue();
    state.setCurrentBackgroundId(saveData.getCurrentBackgroundId());
    state.setVariables(saveData.getVariables());
    state.setReadNodes(saveData.getReadNodes());
    state.setCallStack(saveData.getCallStack());

    java.util.Map<String, CharacterPosition> definedPositions = new java.util.HashMap<>();
    for (var entry : saveData.getCharacterDefinedPositions().entrySet()) {
      String characterId = entry.getKey();
      String positionName = entry.getValue();
      if (characterId == null || characterId.isBlank() || positionName == null || positionName.isBlank()) continue;
      try {
        CharacterPosition cp = CharacterPosition.predefined(positionName);
        if (cp != null) definedPositions.put(characterId, cp);
      } catch (Exception ignored) {
        // reason: unknown predefined position name in save data; character entry skipped
      }
    }

    state.clearAllCharacters();
    for (var entry : saveData.getVisibleCharacters().entrySet()) {
      String pos = entry.getKey();
      String[] data = entry.getValue();
      try {
        CharacterPosition position = restoreCharacterPosition(pos, data);
        if (position == null) continue;
        String charId = data.length > 0 ? data[0] : null;
        String expr = data.length > 1 ? data[1] : "neutral";
        Integer layer = null;
        if (data.length > 2) {
          try {
            layer = Integer.parseInt(data[2]);
          } catch (NumberFormatException ignored) {
// reason: malformed numeric text input; caller uses fallback value
          }
        }
        String displaySlot = data.length > 3 ? data[3] : null;
        if (charId != null) state.showCharacter(position, charId, expr, layer, displaySlot);
      } catch (IllegalArgumentException ignored) {
        // reason: invalid argument from untrusted input; caller handles absent result
      }
    }
    state.setGlobalPositionState(saveData.getGlobalPositionCharacters(), definedPositions);

    state.setSkipMode(saveData.isSkipMode());
    state.setAutoPlayMode(saveData.isAutoPlayMode());
    state.setAutoPlayTimer(saveData.getAutoPlayTimer());
    state.setUiHidden(saveData.isUiHidden());

    if (saveData.getSettings() != null) {
      var sd = saveData.getSettings();
      VnSettings s = state.getSettings();
      s.setTextSpeed(sd.getTextSpeed());
      s.setBgmVolume(sd.getBgmVolume());
      s.setSfxVolume(sd.getSfxVolume());
      s.setVoiceVolume(sd.getVoiceVolume());
      s.setAutoPlayDelay(sd.getAutoPlayDelay());
      s.setSkipUnreadText(sd.isSkipUnreadText());
      s.setSkipAfterChoices(sd.isSkipAfterChoices());
      s.setClickRevealBeforeAdvance(sd.isClickRevealBeforeAdvance());
      s.setPhysicsFixedStepMs(sd.getPhysicsFixedStepMs());
      s.setPhysicsMaxSubSteps(sd.getPhysicsMaxSubSteps());
      s.setPhysicsDefaultFriction(sd.getPhysicsDefaultFriction());
      if (sd.getInputProfilePath() != null) s.setInputProfilePath(sd.getInputProfilePath());
      if (sd.getInputProfileSerialized() != null) s.setInputProfileSerialized(sd.getInputProfileSerialized());
      s.setDisplayWidth(sd.getDisplayWidth());
      s.setDisplayHeight(sd.getDisplayHeight());
      s.setAutoFitResolution(sd.isAutoFitResolution());
      s.setAccessibilityTheme(sd.getAccessibilityTheme());
      s.setTextToSpeechEnabled(sd.isTextToSpeechEnabled());
      s.setUiFontScale(sd.getUiFontScale());
    }
    state.setRpgState(saveData.getRpgState());
  }

  private int chooseAutosaveSlot() {
    int candidate = 0;
    long oldest = Long.MAX_VALUE;
    for (int i = 0; i < AUTOSAVE_SLOT_COUNT; i++) {
      String slotName = getAutosaveSlotName(i);
      Path path = saveDirectory.resolve(slotName + SAVE_EXTENSION);
      if (!Files.exists(path)) {
        return i;
      }
      try {
        VnSaveData data = load(slotName);
        long ts = data != null ? data.getSaveTimestamp() : 0;
        if (ts < oldest) {
          oldest = ts;
          candidate = i;
        }
      } catch (Exception e) {
        // Corrupt/unreadable slot gets overwritten first.
        return i;
      }
    }
    return candidate;
  }

  private void writeAtomically(Path finalPath, VnSaveData saveData) throws IOException {
    AtomicFiles.write(finalPath, VnSaveSerializer.toJson(saveData).getBytes(StandardCharsets.UTF_8));
  }

  private CharacterPosition restoreCharacterPosition(String savedKey, String[] data) {
    String baseName = data != null && data.length > 4 && data[4] != null && !data[4].isBlank()
        ? data[4]
        : savedKey;
    CharacterPosition predefined = CharacterPosition.predefined(baseName);
    if (predefined != null) return predefined;

    Double x = data != null && data.length > 5 ? parseDoubleOrNull(data[5]) : null;
    Double y = data != null && data.length > 6 ? parseDoubleOrNull(data[6]) : null;
    if (x != null) {
      return CharacterPosition.named(baseName == null || baseName.isBlank() ? "custom" : baseName, x, y == null ? -1.0 : y);
    }

    ParsedInlinePosition inline = parseInlinePositionName(baseName);
    if (inline != null) return CharacterPosition.at(inline.x(), inline.y());
    inline = parseInlinePositionName(savedKey);
    if (inline != null) return CharacterPosition.at(inline.x(), inline.y());
    return null;
  }

  private Double parseDoubleOrNull(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Double.parseDouble(raw.trim());
    } catch (NumberFormatException ignored) {
      // reason: malformed save data should skip this optional coordinate
      return null;
    }
  }

  private ParsedInlinePosition parseInlinePositionName(String name) {
    if (name == null || !name.startsWith("_at_")) return null;
    String body = name.substring("_at_".length());
    int sep = body.lastIndexOf('_');
    if (sep <= 0 || sep >= body.length() - 1) return null;
    Double x = parseDoubleOrNull(body.substring(0, sep));
    Double y = parseDoubleOrNull(body.substring(sep + 1));
    if (x == null || y == null) return null;
    return new ParsedInlinePosition(x, y);
  }

  private String sanitizeFileName(String name) {
    if (name == null || name.isBlank()) return "unnamed";
    return name.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private record ParsedInlinePosition(double x, double y) {}
}
