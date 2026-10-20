package com.jvn.core.vn;

import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import com.jvn.core.io.AtomicFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class VnSettingsStore {
  private final Path settingsPath;

  public VnSettingsStore() {
    this(VnStoragePaths.settings().toString());
  }

  public VnSettingsStore(String path) {
    this.settingsPath = Paths.get(path);
  }

  public VnSettings load() {
    VnSettings s = new VnSettings();
    try {
      ensureDir();
      if (Files.exists(settingsPath)) {
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(settingsPath.toFile())) {
          p.load(in);
        }
        try { s.setTextSpeed(Integer.parseInt(p.getProperty("text_speed", Integer.toString(s.getTextSpeed())))); } catch (Exception ignored) { // reason: malformed settings value; property retains its default
        }
        try { s.setBgmVolume(Float.parseFloat(p.getProperty("bgm_volume", Float.toString(s.getBgmVolume())))); } catch (Exception ignored) { // reason: malformed settings value; property retains its default
        }
        try { s.setSfxVolume(Float.parseFloat(p.getProperty("sfx_volume", Float.toString(s.getSfxVolume())))); } catch (Exception ignored) { // reason: malformed settings value; property retains its default
        }
        try { s.setVoiceVolume(Float.parseFloat(p.getProperty("voice_volume", Float.toString(s.getVoiceVolume())))); } catch (Exception ignored) { // reason: malformed settings value; property retains its default
        }
        try { s.setAutoPlayDelay(Long.parseLong(p.getProperty("auto_play_delay", Long.toString(s.getAutoPlayDelay())))); } catch (Exception ignored) { // reason: malformed settings value; property retains its default
        }
        try { s.setSkipUnreadText(Boolean.parseBoolean(p.getProperty("skip_unread_text", Boolean.toString(s.isSkipUnreadText())))); } catch (Exception ignored) { // reason: malformed settings value; property retains its default
        }
        try { s.setSkipAfterChoices(Boolean.parseBoolean(p.getProperty("skip_after_choices", Boolean.toString(s.isSkipAfterChoices())))); } catch (Exception ignored) { // reason: malformed settings value; property retains its default
        }
        try { s.setClickRevealBeforeAdvance(Boolean.parseBoolean(p.getProperty("click_reveal_before_advance", Boolean.toString(s.isClickRevealBeforeAdvance())))); } catch (Exception ignored) { // reason: malformed settings value; property retains its default
        }
        try { s.setPhysicsFixedStepMs(Long.parseLong(p.getProperty("physics_fixed_step_ms", Long.toString(s.getPhysicsFixedStepMs())))); } catch (Exception ignored) { // reason: malformed settings value; property retains its default
        }
        try { s.setPhysicsMaxSubSteps(Integer.parseInt(p.getProperty("physics_max_substeps", Integer.toString(s.getPhysicsMaxSubSteps())))); } catch (Exception ignored) { // reason: malformed settings value; property retains its default
        }
        try { s.setPhysicsDefaultFriction(Double.parseDouble(p.getProperty("physics_default_friction", Double.toString(s.getPhysicsDefaultFriction())))); } catch (Exception ignored) { // reason: malformed settings value; property retains its default
        }
        try { s.setInputProfilePath(p.getProperty("input_profile_path", s.getInputProfilePath())); } catch (Exception ignored) { // reason: malformed settings value; property retains its default
        }
        try { s.setInputProfileSerialized(p.getProperty("input_profile_serialized", s.getInputProfileSerialized())); } catch (Exception ignored) { // reason: malformed settings value; property retains its default
        }
        try { s.setDisplayWidth(Integer.parseInt(p.getProperty("display_width", Integer.toString(s.getDisplayWidth())))); } catch (Exception ignored) { // reason: malformed settings value; property retains its default
        }
        try { s.setDisplayHeight(Integer.parseInt(p.getProperty("display_height", Integer.toString(s.getDisplayHeight())))); } catch (Exception ignored) { // reason: malformed settings value; property retains its default
        }
        try { s.setAutoFitResolution(Boolean.parseBoolean(p.getProperty("auto_fit_resolution", Boolean.toString(s.isAutoFitResolution())))); } catch (Exception ignored) { // reason: malformed settings value; property retains its default
        }
        try { s.setAccessibilityTheme(p.getProperty("accessibility_theme", s.getAccessibilityTheme())); } catch (Exception ignored) { // reason: malformed settings value; property retains its default
        }
        try { s.setTextToSpeechEnabled(Boolean.parseBoolean(p.getProperty("text_to_speech_enabled", Boolean.toString(s.isTextToSpeechEnabled())))); } catch (Exception ignored) { // reason: malformed settings value; property retains its default
        }
        try { s.setUiFontScale(Double.parseDouble(p.getProperty("ui_font_scale", Double.toString(s.getUiFontScale())))); } catch (Exception ignored) { // reason: malformed settings value; property retains its default
        }
      }
    } catch (Exception ignored) {
      // reason: settings file missing or unreadable; all properties retain their defaults
    }
    return s;
  }

  public void save(VnSettings s) {
    if (s == null) return;
    try {
      ensureDir();
      Properties p = new Properties();
      p.setProperty("text_speed", Integer.toString(s.getTextSpeed()));
      p.setProperty("bgm_volume", Float.toString(s.getBgmVolume()));
      p.setProperty("sfx_volume", Float.toString(s.getSfxVolume()));
      p.setProperty("voice_volume", Float.toString(s.getVoiceVolume()));
      p.setProperty("auto_play_delay", Long.toString(s.getAutoPlayDelay()));
      p.setProperty("skip_unread_text", Boolean.toString(s.isSkipUnreadText()));
      p.setProperty("skip_after_choices", Boolean.toString(s.isSkipAfterChoices()));
      p.setProperty("click_reveal_before_advance", Boolean.toString(s.isClickRevealBeforeAdvance()));
      p.setProperty("physics_fixed_step_ms", Long.toString(s.getPhysicsFixedStepMs()));
      p.setProperty("physics_max_substeps", Integer.toString(s.getPhysicsMaxSubSteps()));
      p.setProperty("physics_default_friction", Double.toString(s.getPhysicsDefaultFriction()));
      p.setProperty("input_profile_path", s.getInputProfilePath());
      p.setProperty("input_profile_serialized", s.getInputProfileSerialized());
      p.setProperty("display_width", Integer.toString(s.getDisplayWidth()));
      p.setProperty("display_height", Integer.toString(s.getDisplayHeight()));
      p.setProperty("auto_fit_resolution", Boolean.toString(s.isAutoFitResolution()));
      p.setProperty("accessibility_theme", s.getAccessibilityTheme());
      p.setProperty("text_to_speech_enabled", Boolean.toString(s.isTextToSpeechEnabled()));
      p.setProperty("ui_font_scale", Double.toString(s.getUiFontScale()));
      try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        p.store(out, "JVN Settings");
        AtomicFiles.write(settingsPath, out.toByteArray());
      }
    } catch (Exception ignored) {
      // reason: settings could not be persisted; in-memory state is still valid
    }
  }

  private void ensureDir() throws Exception {
    Path dir = settingsPath.getParent();
    if (dir != null) Files.createDirectories(dir);
  }
}
