package com.jvn.fx.audio;

import com.jvn.core.assets.AudioAssetResolver;
import com.jvn.core.audio.AudioCapabilities;
import com.jvn.core.audio.AudioChannel;
import com.jvn.core.audio.AudioFacade;
import com.jvn.core.audio.AudioListener;
import com.jvn.core.audio.AudioSnapshot;
import com.jvn.core.audio.AudioStateTracker;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.media.AudioSpectrumListener;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FxAudioService implements AudioFacade {
  private static final int MAX_SFX_PLAYERS = 32;
  private static final int MAX_VOICE_PLAYERS = 8;
  private static final Logger log = LoggerFactory.getLogger(FxAudioService.class);

  private MediaPlayer bgmPlayer;
  private MediaPlayer crossfadePlayer;
  private Timeline bgmCrossfadeTimeline;
  private Timeline bgmFadeTimeline;
  private double bgmCrossfadeProgress;
  private final List<MediaPlayer> sfxPlayers = new ArrayList<>();
  private final List<MediaPlayer> voicePlayers = new ArrayList<>();
  private final AudioStateTracker state = new AudioStateTracker("javafx", AudioCapabilities.full(true));
  private volatile boolean closed;
  private File projectRoot;
  private volatile float[] latestBgmSpectrum;
  private volatile long latestBgmSpectrumUpdatedAtNanos;
  private final AudioSpectrumListener bgmSpectrumListener = (timestamp, duration, magnitudes, phases) -> {
    if (magnitudes == null || magnitudes.length == 0) return;
    float[] copy = new float[magnitudes.length];
    System.arraycopy(magnitudes, 0, copy, 0, magnitudes.length);
    latestBgmSpectrum = copy;
    latestBgmSpectrumUpdatedAtNanos = System.nanoTime();
  };

  @Override
  public void setProjectRoot(File root) {
    this.projectRoot = root;
  }

  public FxAudioService() {
  }

  @Override
  public void playBgm(String trackId, boolean loop) {
    runOnFxThread(() -> playBgmFx(trackId, loop));
  }

  @Override
  public void ensureBgmPlaying(String trackId, boolean loop) {
    runOnFxThread(() -> AudioFacade.super.ensureBgmPlaying(trackId, loop));
  }

  @Override
  public void stopBgm() {
    runOnFxThread(this::stopBgmFx);
  }

  @Override
  public void stopAllAudio() {
    runOnFxThread(() -> {
      stopBgmFx();
      stopPlayersFx(sfxPlayers);
      stopPlayersFx(voicePlayers);
    });
  }

  @Override
  public void playSfx(String sfxId) {
    runOnFxThread(() -> playClipFx(sfxId, state.mix().effective(AudioChannel.SFX), sfxPlayers, AudioChannel.SFX));
  }

  @Override
  public void playVoice(String voiceId) {
    runOnFxThread(() -> playClipFx(voiceId, state.mix().effective(AudioChannel.VOICE), voicePlayers, AudioChannel.VOICE));
  }

  @Override
  public void stopSfx() {
    runOnFxThread(() -> stopPlayersFx(sfxPlayers));
  }

  @Override
  public void stopVoice() {
    runOnFxThread(() -> stopPlayersFx(voicePlayers));
  }

  @Override
  public void setBgmVolume(float volume) {
    state.mix().setBgmVolume(volume);
    state.mixChanged(AudioChannel.BGM);
    runOnFxThread(this::applyBgmVolumesFx);
  }

  @Override
  public void setSfxVolume(float volume) {
    state.mix().setSfxVolume(volume);
    state.mixChanged(AudioChannel.SFX);
    runOnFxThread(() -> applyVolumeFx(sfxPlayers, state.mix().effective(AudioChannel.SFX)));
  }

  @Override
  public void setVoiceVolume(float volume) {
    state.mix().setVoiceVolume(volume);
    state.mixChanged(AudioChannel.VOICE);
    runOnFxThread(() -> applyVolumeFx(voicePlayers, state.mix().effective(AudioChannel.VOICE)));
  }

  @Override
  public void setMasterVolume(float volume) {
    state.mix().setMasterVolume(volume);
    state.mixChanged(AudioChannel.MASTER);
    runOnFxThread(this::applyAllVolumesFx);
  }

  @Override
  public void setMuted(boolean muted) {
    state.mix().setMuted(muted);
    state.mixChanged(AudioChannel.MASTER);
    runOnFxThread(this::applyAllVolumesFx);
  }

  @Override public float getMasterVolume() { return state.mix().masterVolume(); }
  @Override public float getBgmVolume() { return state.mix().bgmVolume(); }
  @Override public float getSfxVolume() { return state.mix().sfxVolume(); }
  @Override public float getVoiceVolume() { return state.mix().voiceVolume(); }
  @Override public boolean isMuted() { return state.mix().muted(); }

  @Override
  public void pauseBgm() {
    runOnFxThread(() -> {
      try {
        if (bgmPlayer != null) bgmPlayer.pause();
        state.paused();
      } catch (Exception e) {
        log.debug("pauseBgm error", e);
      }
    });
  }

  @Override
  public void resumeBgm() {
    runOnFxThread(() -> {
      try {
        if (bgmPlayer != null) {
          applyBgmVolumesFx();
          bgmPlayer.play();
          state.resumed();
        }
      } catch (Exception e) {
        log.debug("resumeBgm error", e);
      }
    });
  }

  @Override
  public void pauseAllAudio() {
    runOnFxThread(() -> {
      try {
        if (bgmPlayer != null) bgmPlayer.pause();
      } catch (Exception e) {
        log.debug("pauseAllAudio bgm error", e);
      }
      pausePlayersFx(sfxPlayers);
      pausePlayersFx(voicePlayers);
    });
  }

  @Override
  public void resumeAllAudio() {
    runOnFxThread(() -> {
      try {
        if (bgmPlayer != null) {
          applyBgmVolumesFx();
          bgmPlayer.play();
        }
      } catch (Exception e) {
        log.debug("resumeAllAudio bgm error", e);
      }
      playPlayersFx(sfxPlayers);
      playPlayersFx(voicePlayers);
    });
  }

  @Override
  public void seekBgmSeconds(double seconds) {
    if (seconds < 0) return;
    runOnFxThread(() -> {
      try {
        if (bgmPlayer != null) bgmPlayer.seek(Duration.seconds(seconds));
      } catch (Exception e) {
        log.debug("seekBgmSeconds error", e);
      }
    });
  }

  @Override
  public void crossfadeBgm(String trackId, long ms, boolean loop) {
    runOnFxThread(() -> crossfadeBgmFx(trackId, ms, loop));
  }

  @Override
  public void fadeOutBgm(long ms) {
    runOnFxThread(() -> fadeOutBgmFx(ms));
  }

  private void playBgmFx(String trackId, boolean loop) {
    cancelCrossfadeFx();
    cancelFadeOutFx();
    state.loading(trackId, loop);
    MediaPlayer next = createPlayer(trackId, loop, state.mix().effective(AudioChannel.BGM), true, AudioChannel.BGM);
    if (next == null) return;

    stopPlayer(bgmPlayer);
    bgmPlayer = next;
    resetSpectrumData();
    next.play();
    state.started(AudioChannel.BGM, trackId);
  }

  private void stopBgmFx() {
    cancelCrossfadeFx();
    cancelFadeOutFx();
    stopPlayer(bgmPlayer);
    bgmPlayer = null;
    resetSpectrumData();
    state.stopped(AudioChannel.BGM, "");
  }

  private void playClipFx(String trackId, double volume, List<MediaPlayer> players, AudioChannel channel) {
    cleanupStoppedPlayers(players);
    int limit = channel == AudioChannel.VOICE ? MAX_VOICE_PLAYERS : MAX_SFX_PLAYERS;
    while (players.size() >= limit) {
      stopPlayer(players.remove(0));
    }
    MediaPlayer player = createPlayer(trackId, false, volume, false, channel);
    if (player == null) return;

    player.setOnEndOfMedia(() -> {
      stopPlayer(player);
      players.remove(player);
      state.completed(channel, trackId);
    });
    players.add(player);
    player.play();
    state.started(channel, trackId);
  }

  private void crossfadeBgmFx(String trackId, long ms, boolean loop) {
    cancelCrossfadeFx();

    cancelFadeOutFx();
    state.loading(trackId, loop);
    MediaPlayer next = createPlayer(trackId, loop, 0.0, true, AudioChannel.BGM);
    if (next == null) {
      playBgmFx(trackId, loop);
      return;
    }

    MediaPlayer previous = bgmPlayer;
    if (previous == null || ms <= 0L) {
      stopPlayer(previous);
      bgmPlayer = next;
      bgmCrossfadeProgress = 1.0;
      next.setVolume(state.mix().effective(AudioChannel.BGM));
      next.play();
      state.started(AudioChannel.BGM, trackId);
      return;
    }

    final long startedAtNanos = System.nanoTime();
    final long durationMs = Math.max(1L, ms);
    final Timeline[] timelineRef = new Timeline[1];

    next.play();
    crossfadePlayer = next;
    bgmCrossfadeProgress = 0.0;

    KeyFrame tick = new KeyFrame(Duration.millis(33), event ->
        updateCrossfadeFx(previous, next, startedAtNanos, durationMs, timelineRef[0]));
    timelineRef[0] = new Timeline(new KeyFrame(Duration.ZERO, event ->
        updateCrossfadeFx(previous, next, startedAtNanos, durationMs, timelineRef[0])), tick);
    timelineRef[0].setCycleCount(Timeline.INDEFINITE);
    bgmCrossfadeTimeline = timelineRef[0];
    timelineRef[0].play();
  }

  private void updateCrossfadeFx(
      MediaPlayer previous,
      MediaPlayer next,
      long startedAtNanos,
      long durationMs,
      Timeline timeline
  ) {
    if (timeline == null || timeline != bgmCrossfadeTimeline || next != crossfadePlayer) {
      return;
    }

    double progress = Math.min(1.0, Math.max(0.0, (System.nanoTime() - startedAtNanos) / 1_000_000.0 / durationMs));
    bgmCrossfadeProgress = progress;

    double targetVolume = state.mix().effective(AudioChannel.BGM);
    safeSetVolume(previous, targetVolume * (1.0 - progress));
    safeSetVolume(next, targetVolume * progress);

    if (progress >= 1.0) {
      timeline.stop();
      stopPlayer(previous);
      bgmPlayer = next;
      crossfadePlayer = null;
      bgmCrossfadeTimeline = null;
      bgmCrossfadeProgress = 1.0;
      safeSetVolume(next, targetVolume);
      state.started(AudioChannel.BGM, "");
    }
  }

  private MediaPlayer createPlayer(String trackId, boolean loop, double volume, boolean spectrum, AudioChannel channel) {
    String channelName = channel.name().toLowerCase();
    String urlStr = resolveMediaUrl(trackId);
    if (urlStr == null) {
      log.warn("{} media not found for trackId={}", channelName, trackId);
      state.error(channel, trackId, "Audio asset not found");
      return null;
    }

    try {
      MediaPlayer player = new MediaPlayer(new Media(urlStr));
      if (loop) player.setCycleCount(MediaPlayer.INDEFINITE);
      player.setVolume(volume);
      player.setOnError(() -> {
        log.warn("{} playback error for trackId={}: {}", channelName, trackId, player.getError());
        state.error(channel, trackId, String.valueOf(player.getError()));
      });
      if (spectrum) configureSpectrumListener(player);
      return player;
    } catch (Exception e) {
      log.warn("Failed to create {} player for trackId={}", channelName, trackId, e);
      state.error(channel, trackId, e.getMessage());
      return null;
    }
  }

  private void fadeOutBgmFx(long ms) {
    cancelFadeOutFx();
    if (bgmPlayer == null || ms <= 0L) {
      stopBgmFx();
      return;
    }
    final MediaPlayer player = bgmPlayer;
    final long start = System.nanoTime();
    final long duration = Math.max(1L, ms);
    final double initial = player.getVolume();
    Timeline timeline = new Timeline(new KeyFrame(Duration.millis(33), event -> {
      double progress = Math.min(1.0, (System.nanoTime() - start) / 1_000_000.0 / duration);
      safeSetVolume(player, initial * (1.0 - progress));
      if (progress >= 1.0 && bgmFadeTimeline != null) stopBgmFx();
    }));
    timeline.setCycleCount(Timeline.INDEFINITE);
    bgmFadeTimeline = timeline;
    timeline.play();
  }

  private void cancelFadeOutFx() {
    if (bgmFadeTimeline != null) {
      bgmFadeTimeline.stop();
      bgmFadeTimeline = null;
    }
  }

  private void cancelCrossfadeFx() {
    if (bgmCrossfadeTimeline != null) {
      bgmCrossfadeTimeline.stop();
      bgmCrossfadeTimeline = null;
    }
    if (crossfadePlayer != null) {
      stopPlayer(crossfadePlayer);
      crossfadePlayer = null;
    }
    bgmCrossfadeProgress = 0.0;
  }

  private void stopPlayersFx(List<MediaPlayer> players) {
    for (MediaPlayer player : new ArrayList<>(players)) {
      stopPlayer(player);
    }
    players.clear();
  }

  private void pausePlayersFx(List<MediaPlayer> players) {
    for (MediaPlayer player : new ArrayList<>(players)) {
      try {
        player.pause();
      } catch (Exception e) {
        log.debug("pausePlayers error", e);
      }
    }
  }

  private void playPlayersFx(List<MediaPlayer> players) {
    for (MediaPlayer player : new ArrayList<>(players)) {
      try {
        player.play();
      } catch (Exception e) {
        log.debug("playPlayers error", e);
      }
    }
  }

  private void cleanupStoppedPlayers(List<MediaPlayer> players) {
    Iterator<MediaPlayer> it = players.iterator();
    while (it.hasNext()) {
      MediaPlayer player = it.next();
      MediaPlayer.Status status;
      try {
        status = player.getStatus();
      } catch (Exception e) {
        stopPlayer(player);
        it.remove();
        continue;
      }
      if (status == MediaPlayer.Status.STOPPED
          || status == MediaPlayer.Status.DISPOSED
          || status == MediaPlayer.Status.HALTED) {
        stopPlayer(player);
        it.remove();
      }
    }
  }

  private void applyBgmVolumesFx() {
    double target = state.mix().effective(AudioChannel.BGM);
    if (crossfadePlayer != null && bgmCrossfadeTimeline != null) {
      safeSetVolume(bgmPlayer, target * (1.0 - bgmCrossfadeProgress));
      safeSetVolume(crossfadePlayer, target * bgmCrossfadeProgress);
      return;
    }
    safeSetVolume(bgmPlayer, target);
  }

  private void applyAllVolumesFx() {
    applyBgmVolumesFx();
    applyVolumeFx(sfxPlayers, state.mix().effective(AudioChannel.SFX));
    applyVolumeFx(voicePlayers, state.mix().effective(AudioChannel.VOICE));
  }

  private void applyVolumeFx(List<MediaPlayer> players, double volume) {
    for (MediaPlayer player : new ArrayList<>(players)) {
      safeSetVolume(player, volume);
    }
  }

  private void stopPlayer(MediaPlayer player) {
    if (player == null) return;
    try {
      player.stop();
    } catch (Exception e) {
      log.debug("stopPlayer error", e);
    }
    try {
      player.dispose();
    } catch (Exception e) {
      log.debug("disposePlayer error", e);
    }
  }

  private void safeSetVolume(MediaPlayer player, double volume) {
    if (player == null) return;
    try {
      player.setVolume(volume);
    } catch (Exception e) {
      log.debug("setVolume error", e);
    }
  }

  private String resolveMediaUrl(String id) {
    try {
      File file = AudioAssetResolver.resolveFile(projectRoot, id);
      if (file != null) return file.toURI().toString();

      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      if (loader == null) loader = getClass().getClassLoader();
      URL url = AudioAssetResolver.resolveClasspathUrl(loader, id);
      return url == null ? null : url.toExternalForm();
    } catch (Exception e) {
      log.warn("resolveMediaUrl error for id={}", id, e);
      return null;
    }
  }

  private void resetSpectrumData() {
    latestBgmSpectrum = null;
    latestBgmSpectrumUpdatedAtNanos = 0L;
  }

  private void runOnFxThread(Runnable action) {
    if (action == null) return;
    try {
      if (Platform.isFxApplicationThread()) {
        action.run();
      } else {
        Platform.runLater(action);
      }
    } catch (IllegalStateException ignored) {
            // reason: JavaFX state race on shutdown; not actionable at call site
      action.run();
    }
  }

  @Override public String backendId() { return state.backendId(); }
  @Override public AudioCapabilities capabilities() { return state.capabilities(); }

  @Override
  public AudioSnapshot snapshot() {
    double position = 0.0;
    double duration = 0.0;
    try {
      if (bgmPlayer != null) {
        position = bgmPlayer.getCurrentTime().toSeconds();
        duration = bgmPlayer.getTotalDuration().toSeconds();
      }
    } catch (Exception ignored) {
      // Snapshot collection is best-effort during asynchronous disposal.
    }
    return state.snapshot(position, duration, sfxPlayers.size(), voicePlayers.size());
  }

  @Override public void addListener(AudioListener listener) { state.addListener(listener); }
  @Override public void removeListener(AudioListener listener) { state.removeListener(listener); }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    runOnFxThread(() -> {
      stopBgmFx();
      stopPlayersFx(sfxPlayers);
      stopPlayersFx(voicePlayers);
      state.closed();
    });
  }

  @Override
  public float[] getBgmSpectrumMagnitudes() {
    float[] data = latestBgmSpectrum;
    if (data == null || data.length == 0) return null;
    return data.clone();
  }

  @Override
  public boolean supportsBgmSpectrum() {
    return true;
  }

  @Override
  public long getBgmSpectrumUpdatedAtNanos() {
    return latestBgmSpectrumUpdatedAtNanos;
  }

  private void configureSpectrumListener(MediaPlayer player) {
    if (player == null) return;
    try {
      player.setAudioSpectrumNumBands(64);
      player.setAudioSpectrumInterval(0.033);
      player.setAudioSpectrumThreshold(-60);
      player.setAudioSpectrumListener(bgmSpectrumListener);
    } catch (Exception e) {
      log.debug("configureSpectrumListener error", e);
    }
  }
}
