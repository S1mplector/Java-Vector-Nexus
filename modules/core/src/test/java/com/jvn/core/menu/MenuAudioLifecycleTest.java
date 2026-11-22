package com.jvn.core.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jvn.core.audio.AudioFacade;
import com.jvn.core.audio.AudioPlaybackStatus;
import com.jvn.core.audio.AudioSnapshot;
import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;

class MenuAudioLifecycleTest {

  @Test
  void gameplayHandoffStopsOnlyMenuBgm() {
    RecordingAudio audio = new RecordingAudio();

    MenuAudioLifecycle.beginGameplay(null, audio);

    assertEquals(1, audio.bgmStops);
    assertEquals(0, audio.sfxStops);
    assertEquals(0, audio.voiceStops);
  }

  @Test
  void sharedMusicStartsOnDirectEntryAndKeepsPlayingAcrossMenuHandoffs() {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    engine.setSharedMenuBgmPath("tutorial.mp3");
    RecordingAudio audio = new RecordingAudio();

    MenuAudioLifecycle.beginGameplay(engine, audio);
    MenuAudioLifecycle.beginGameplay(engine, audio);

    assertEquals("tutorial.mp3", audio.snapshot().bgmTrackId());
    assertTrue(audio.snapshot().bgmLooping());
    assertEquals(1, audio.bgmStarts);
    assertEquals(0, audio.bgmStops);
  }

  private static final class RecordingAudio implements AudioFacade {
    private String track;
    private boolean looping;
    private int bgmStarts;
    private int bgmStops;
    private int sfxStops;
    private int voiceStops;

    @Override public void playBgm(String trackId, boolean loop) {
      track = trackId;
      looping = loop;
      bgmStarts++;
    }
    @Override public void playSfx(String sfxId) {}
    @Override public void stopBgm() { track = null; bgmStops++; }
    @Override public void stopSfx() { sfxStops++; }
    @Override public void stopVoice() { voiceStops++; }
    @Override public AudioSnapshot snapshot() {
      return new AudioSnapshot("test", track == null ? AudioPlaybackStatus.STOPPED : AudioPlaybackStatus.PLAYING,
          track, looping, 0, 0, 1, 1, 1, 1, false, 0, 0, "");
    }
  }
}
