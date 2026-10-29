package com.jvn.audio.simp3;

import com.jvn.core.audio.*;
import java.io.File;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.LoggerFactory;

/** Serializes blocking backend operations away from the game/UI thread. */
public final class AsyncAudioService implements AudioFacade {
  private final AudioFacade backend;
  private final ThreadPoolExecutor commands = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
      new ArrayBlockingQueue<>(256), task -> { Thread t = new Thread(task, "jvn-audio-commands"); t.setDaemon(true); return t; });
  private final AtomicBoolean snapshotPending = new AtomicBoolean();
  private volatile AudioSnapshot latest;
  private volatile long snapshotTime;
  private boolean closed;
  private volatile float master = 1, bgm = 1, sfx = 1, voice = 1;
  private volatile boolean muted;

  public AsyncAudioService(AudioFacade backend) {
    this.backend = Objects.requireNonNull(backend);
    latest = backend.snapshot();
    master = backend.getMasterVolume(); bgm = backend.getBgmVolume();
    sfx = backend.getSfxVolume(); voice = backend.getVoiceVolume(); muted = backend.isMuted();
  }
  private synchronized void submit(Runnable command) {
    if (closed) return;
    try {
      commands.execute(() -> {
        try { command.run(); latest = backend.snapshot(); snapshotTime = System.nanoTime(); }
        catch (RuntimeException error) { LoggerFactory.getLogger(getClass()).warn("Audio command failed", error); }
      });
    } catch (RejectedExecutionException full) {
      LoggerFactory.getLogger(getClass()).warn("Audio command queue is full; command discarded");
    }
  }
  @Override public void setProjectRoot(File root) { submit(() -> backend.setProjectRoot(root)); }
  @Override public void playBgm(String path, boolean loop) { submit(() -> backend.playBgm(path, loop)); }
  @Override public void stopBgm() { submit(backend::stopBgm); }
  @Override public void playSfx(String path) { submit(() -> backend.playSfx(path)); }
  @Override public void playVoice(String path) { submit(() -> backend.playVoice(path)); }
  @Override public void stopSfx() { submit(backend::stopSfx); }
  @Override public void stopVoice() { submit(backend::stopVoice); }
  @Override public void stopAllAudio() { submit(backend::stopAllAudio); }
  @Override public void pauseBgm() { submit(backend::pauseBgm); }
  @Override public void resumeBgm() { submit(backend::resumeBgm); }
  @Override public void pauseAllAudio() { submit(backend::pauseAllAudio); }
  @Override public void resumeAllAudio() { submit(backend::resumeAllAudio); }
  @Override public void seekBgmSeconds(double seconds) { submit(() -> backend.seekBgmSeconds(seconds)); }
  @Override public void crossfadeBgm(String path, long ms, boolean loop) { submit(() -> backend.crossfadeBgm(path, ms, loop)); }
  @Override public void fadeOutBgm(long ms) { submit(() -> backend.fadeOutBgm(ms)); }
  @Override public void setMasterVolume(float value) { master = gain(value); submit(() -> backend.setMasterVolume(gain(value))); }
  @Override public void setBgmVolume(float value) { bgm = gain(value); submit(() -> backend.setBgmVolume(gain(value))); }
  @Override public void setSfxVolume(float value) { sfx = gain(value); submit(() -> backend.setSfxVolume(gain(value))); }
  @Override public void setVoiceVolume(float value) { voice = gain(value); submit(() -> backend.setVoiceVolume(gain(value))); }
  @Override public void setMuted(boolean value) { muted = value; submit(() -> backend.setMuted(value)); }
  @Override public float getMasterVolume() { return master; }
  @Override public float getBgmVolume() { return bgm; }
  @Override public float getSfxVolume() { return sfx; }
  @Override public float getVoiceVolume() { return voice; }
  @Override public boolean isMuted() { return muted; }
  @Override public String backendId() { return backend.backendId(); }
  @Override public AudioCapabilities capabilities() { return backend.capabilities(); }
  @Override public void addListener(AudioListener listener) { backend.addListener(listener); }
  @Override public void removeListener(AudioListener listener) { backend.removeListener(listener); }
  @Override public float[] getBgmSpectrumMagnitudes() { return backend.getBgmSpectrumMagnitudes(); }
  @Override public long getBgmSpectrumUpdatedAtNanos() { return backend.getBgmSpectrumUpdatedAtNanos(); }
  @Override public boolean supportsBgmSpectrum() { return backend.supportsBgmSpectrum(); }
  @Override public AudioSnapshot snapshot() {
    if (System.nanoTime() - snapshotTime > 250_000_000L && snapshotPending.compareAndSet(false, true)) {
      submit(() -> { try { latest = backend.snapshot(); } finally { snapshotPending.set(false); } });
    }
    return latest;
  }
  @Override public synchronized void close() {
    if (closed) return;
    closed = true;
    commands.getQueue().clear();
    commands.execute(() -> { try { backend.close(); } catch (Exception error) { LoggerFactory.getLogger(getClass()).warn("Audio cleanup failed", error); } });
    commands.shutdown();
  }
  private static float gain(float value) { return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0; }
}
