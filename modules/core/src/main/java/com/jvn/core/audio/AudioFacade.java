package com.jvn.core.audio;

import java.io.File;

/**
 * Platform-agnostic audio API used by the engine and VN runtime.
 *
 * <p>{@code AudioFacade} defines three main channels — <b>BGM</b>
 * (background music), <b>SFX</b> (sound effects), and <b>Voice</b> —
 * plus optional real-time spectrum analysis.</p>
 *
 * <p>Only three methods are abstract ({@link #playBgm}, {@link #stopBgm},
 * {@link #playSfx}). All others provide sensible default (no-op)
 * implementations so backends only need to override what they support.</p>
 *
 * <h2>Channel Hierarchy</h2>
 * <pre>
 * BGM   — loopable background music track (one at a time)
 * SFX   — fire-and-forget sound effects
 * Voice — treated as SFX by default; override for dedicated voice channel
 * </pre>
 */
public interface AudioFacade extends AutoCloseable {

  /** Configure the project root used to resolve relative audio asset identifiers. */
  default void setProjectRoot(File root) {}

  // ──────────────────────────────────────────────────────────────────────────
  //  Core playback (abstract)
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Start playing background music.
   *
   * @param trackId the asset path or identifier for the music track
   * @param loop    {@code true} to loop indefinitely
   */
  void playBgm(String trackId, boolean loop);

  /** Keep the requested track playing without restarting an active loop. */
  default void ensureBgmPlaying(String trackId, boolean loop) {
    AudioSnapshot current = snapshot();
    if (trackId.equals(current.bgmTrackId()) && loop == current.bgmLooping()) {
      switch (current.bgmStatus()) {
        case PLAYING, LOADING -> { return; }
        case PAUSED -> { resumeBgm(); return; }
        default -> { }
      }
    }
    playBgm(trackId, loop);
  }

  /** Stop the currently playing background music. */
  void stopBgm();

  /**
   * Play a one-shot sound effect.
   *
   * @param sfxId the asset path or identifier for the sound effect
   */
  void playSfx(String sfxId);

  // ──────────────────────────────────────────────────────────────────────────
  //  Voice (defaults to SFX channel)
  // ──────────────────────────────────────────────────────────────────────────

  /** Play a voice line. Defaults to {@link #playSfx(String)}. */
  default void playVoice(String voiceId) { playSfx(voiceId); }

  /** Stop the SFX channel. Default is a no-op. */
  default void stopSfx() {}

  /** Stop the voice channel. Defaults to {@link #stopSfx()}. */
  default void stopVoice() { stopSfx(); }

  /** Stop all audio channels (voice, SFX, BGM). */
  default void stopAllAudio() {
    stopVoice();
    stopSfx();
    stopBgm();
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Volume controls
  // ──────────────────────────────────────────────────────────────────────────

  /** Set the BGM volume [0, 1]. */
  default void setBgmVolume(float volume) {}

  /** Set the SFX volume [0, 1]. */
  default void setSfxVolume(float volume) {}

  /** Set the voice volume [0, 1]. */
  default void setVoiceVolume(float volume) {}

  /** Set a master gain applied after all channel gains. */
  default void setMasterVolume(float volume) {}

  /** Mute or restore output without discarding configured volume levels. */
  default void setMuted(boolean muted) {}

  default float getMasterVolume() { return 1f; }
  default float getBgmVolume() { return 1f; }
  default float getSfxVolume() { return 1f; }
  default float getVoiceVolume() { return 1f; }
  default boolean isMuted() { return false; }

  // ──────────────────────────────────────────────────────────────────────────
  //  Advanced BGM controls (optional)
  // ──────────────────────────────────────────────────────────────────────────

  /** Pause the current BGM track. */
  default void pauseBgm() {}

  /** Resume a previously paused BGM track. */
  default void resumeBgm() {}

  /** Pause all audio channels. */
  default void pauseAllAudio() { pauseBgm(); }

  /** Resume all audio channels. */
  default void resumeAllAudio() { resumeBgm(); }

  /** Seek the BGM track to the given position in seconds. */
  default void seekBgmSeconds(double seconds) {}

  /**
   * Cross-fade from the current BGM to a new track over {@code ms} milliseconds.
   *
   * @param trackId the new track to fade in
   * @param ms      cross-fade duration in milliseconds
   * @param loop    whether the new track should loop
   */
  default void crossfadeBgm(String trackId, long ms, boolean loop) {}

  /** Fade the current BGM to silence and stop it. */
  default void fadeOutBgm(long ms) { stopBgm(); }

  // ──────────────────────────────────────────────────────────────────────────
  //  Capabilities, state, and lifecycle
  // ──────────────────────────────────────────────────────────────────────────

  /** Stable backend identifier suitable for logs and diagnostics. */
  default String backendId() { return getClass().getName(); }

  /** Explicit feature declaration; callers should consult this before optional operations. */
  default AudioCapabilities capabilities() { return AudioCapabilities.basic(); }

  /** Point-in-time transport and mixer state. */
  default AudioSnapshot snapshot() { return AudioSnapshot.unavailable(); }

  default void addListener(AudioListener listener) {}
  default void removeListener(AudioListener listener) {}

  /** Release players, workers, listeners, and temporary resources. */
  @Override
  default void close() { stopAllAudio(); }

  // ──────────────────────────────────────────────────────────────────────────
  //  Spectrum analysis (optional)
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Real-time BGM spectrum magnitudes in dB (typically −60 … 0).
   *
   * @return magnitude array, or {@code null} when unsupported
   */
  default float[] getBgmSpectrumMagnitudes() { return null; }

  /** @return {@code true} if the backend can provide spectrum data */
  default boolean supportsBgmSpectrum() { return false; }

  /**
   * Timestamp of the latest spectrum sample in {@link System#nanoTime()} units.
   *
   * @return nanosecond timestamp, or 0 when unsupported
   */
  default long getBgmSpectrumUpdatedAtNanos() { return 0L; }
}
