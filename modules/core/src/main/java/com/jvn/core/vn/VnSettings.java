package com.jvn.core.vn;

/**
 * Configuration settings for visual novel playback
 */
public class VnSettings {
  private int textSpeed = 30; // ms per character; 0 means instant text
  private float bgmVolume = 0.7f;
  private float sfxVolume = 0.8f;
  private float voiceVolume = 1.0f;
  private long autoPlayDelay = 2000; // ms to wait before auto-advancing
  private boolean skipUnreadText = false;
  private boolean skipAfterChoices = false;
  private boolean clickRevealBeforeAdvance = true;
  private long physicsFixedStepMs = 0; // 0 = variable
  private int physicsMaxSubSteps = 4;
  private double physicsDefaultFriction = 0.2;
  private String inputProfilePath = System.getProperty("user.home") + "/.jvn/input-bindings.properties";
  private String inputProfileSerialized = ""; // serialized ActionBindingProfile (optional)
  private int displayWidth = 1920;
  private int displayHeight = 1080;
  private boolean autoFitResolution = false;
  /** Accessibility theme preset: "none", "highcontrast", or "opendyslexic". */
  private String accessibilityTheme = "none";
  private boolean textToSpeechEnabled = false;
  private double uiFontScale = 1.0;

  public int getTextSpeed() { return textSpeed; }
  public void setTextSpeed(int speed) { this.textSpeed = Math.max(0, Math.min(speed, 200)); }

  public float getBgmVolume() { return bgmVolume; }
  public void setBgmVolume(float volume) { if (Float.isFinite(volume)) this.bgmVolume = Math.max(0f, Math.min(volume, 1f)); }

  public float getSfxVolume() { return sfxVolume; }
  public void setSfxVolume(float volume) { if (Float.isFinite(volume)) this.sfxVolume = Math.max(0f, Math.min(volume, 1f)); }

  public float getVoiceVolume() { return voiceVolume; }
  public void setVoiceVolume(float volume) { if (Float.isFinite(volume)) this.voiceVolume = Math.max(0f, Math.min(volume, 1f)); }

  public long getAutoPlayDelay() { return autoPlayDelay; }
  public void setAutoPlayDelay(long delay) { this.autoPlayDelay = Math.max(500, delay); }

  public boolean isSkipUnreadText() { return skipUnreadText; }
  public void setSkipUnreadText(boolean skip) { this.skipUnreadText = skip; }

  public boolean isSkipAfterChoices() { return skipAfterChoices; }
  public void setSkipAfterChoices(boolean skip) { this.skipAfterChoices = skip; }

  public boolean isClickRevealBeforeAdvance() { return clickRevealBeforeAdvance; }
  public void setClickRevealBeforeAdvance(boolean enabled) { this.clickRevealBeforeAdvance = enabled; }

  public long getPhysicsFixedStepMs() { return physicsFixedStepMs; }
  public void setPhysicsFixedStepMs(long ms) { this.physicsFixedStepMs = Math.max(0, ms); }

  public int getPhysicsMaxSubSteps() { return physicsMaxSubSteps; }
  public void setPhysicsMaxSubSteps(int steps) { this.physicsMaxSubSteps = Math.max(1, steps); }

  public double getPhysicsDefaultFriction() { return physicsDefaultFriction; }
  public void setPhysicsDefaultFriction(double friction) {
    if (Double.isNaN(friction) || Double.isInfinite(friction)) friction = 0;
    this.physicsDefaultFriction = Math.max(0.0, Math.min(1.0, friction));
  }

  public String getInputProfilePath() { return inputProfilePath; }
  public void setInputProfilePath(String path) {
    if (path != null && !path.isBlank()) this.inputProfilePath = path;
  }

  public String getInputProfileSerialized() { return inputProfileSerialized; }
  public void setInputProfileSerialized(String serialized) {
    this.inputProfileSerialized = serialized == null ? "" : serialized;
  }

  public int getDisplayWidth() { return displayWidth; }
  public void setDisplayWidth(int width) { this.displayWidth = Math.max(320, Math.min(width, 7680)); }

  public int getDisplayHeight() { return displayHeight; }
  public void setDisplayHeight(int height) { this.displayHeight = Math.max(180, Math.min(height, 4320)); }

  public boolean isAutoFitResolution() { return autoFitResolution; }
  public void setAutoFitResolution(boolean autoFit) { this.autoFitResolution = autoFit; }

  public String getAccessibilityTheme() { return accessibilityTheme; }
  public void setAccessibilityTheme(String theme) {
    this.accessibilityTheme = (theme == null || theme.isBlank()) ? "none" : theme.trim().toLowerCase(java.util.Locale.ROOT);
  }

  public boolean isTextToSpeechEnabled() { return textToSpeechEnabled; }
  public void setTextToSpeechEnabled(boolean enabled) { this.textToSpeechEnabled = enabled; }

  public double getUiFontScale() { return uiFontScale; }
  public void setUiFontScale(double scale) {
    if (!Double.isFinite(scale)) scale = 1.0;
    this.uiFontScale = Math.max(0.75, Math.min(2.0, scale));
  }

  public void copyFrom(VnSettings source) {
    if (source == null || source == this) return;
    setTextSpeed(source.getTextSpeed());
    setBgmVolume(source.getBgmVolume());
    setSfxVolume(source.getSfxVolume());
    setVoiceVolume(source.getVoiceVolume());
    setAutoPlayDelay(source.getAutoPlayDelay());
    setSkipUnreadText(source.isSkipUnreadText());
    setSkipAfterChoices(source.isSkipAfterChoices());
    setClickRevealBeforeAdvance(source.isClickRevealBeforeAdvance());
    setPhysicsFixedStepMs(source.getPhysicsFixedStepMs());
    setPhysicsMaxSubSteps(source.getPhysicsMaxSubSteps());
    setPhysicsDefaultFriction(source.getPhysicsDefaultFriction());
    setInputProfilePath(source.getInputProfilePath());
    setInputProfileSerialized(source.getInputProfileSerialized());
    setDisplayWidth(source.getDisplayWidth());
    setDisplayHeight(source.getDisplayHeight());
    setAutoFitResolution(source.isAutoFitResolution());
    setAccessibilityTheme(source.getAccessibilityTheme());
    setTextToSpeechEnabled(source.isTextToSpeechEnabled());
    setUiFontScale(source.getUiFontScale());
  }

  public VnSettings copy() {
    VnSettings copy = new VnSettings();
    copy.copyFrom(this);
    return copy;
  }
}
