package com.jvn.core.menu;

import com.jvn.core.audio.AudioFacade;
import com.jvn.core.engine.Engine;

/**
 * Shared audio handoff rules for transitions between menu and gameplay scenes.
 */
final class MenuAudioLifecycle {
  private MenuAudioLifecycle() {}

  /**
   * Carry shared music into gameplay when configured. Otherwise hand off
   * from silence so the gameplay scene can start its own music.
   */
  static void beginGameplay(Engine engine, AudioFacade audio) {
    if (audio == null) return;
    String sharedTrack = engine != null ? engine.getSharedMenuBgmPath() : null;
    if (sharedTrack == null) audio.stopBgm();
    else audio.ensureBgmPlaying(sharedTrack, true);
  }
}
