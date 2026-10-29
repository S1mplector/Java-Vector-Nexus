package com.jvn.audio.simp3;

import com.jvn.core.audio.AudioFacade;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AsyncAudioServiceTest {
  @Test void blockingLoadDoesNotBlockControlsAndCommandsStayOrdered() throws Exception {
    CountDownLatch loading = new CountDownLatch(1), release = new CountDownLatch(1), stopped = new CountDownLatch(1), closed = new CountDownLatch(1);
    List<String> events = new CopyOnWriteArrayList<>();
    AudioFacade backend = new AudioFacade() {
      public void playBgm(String path, boolean loop) { loading.countDown(); try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } events.add("play"); }
      public void setBgmVolume(float value) { events.add("volume"); }
      public void stopBgm() { events.add("stop"); stopped.countDown(); }
      public void playSfx(String path) { events.add("sfx"); }
      public void close() { events.add("close"); closed.countDown(); }
    };
    AsyncAudioService audio = new AsyncAudioService(backend);
    try {
      audio.playBgm("track", true);
      assertTrue(loading.await(2, TimeUnit.SECONDS));
      audio.setBgmVolume(.4f); audio.stopBgm();
      assertEquals(.4f, audio.getBgmVolume());
      assertTrue(events.isEmpty(), "Control methods must return while the backend is blocked");
      release.countDown(); assertTrue(stopped.await(2, TimeUnit.SECONDS));
      assertEquals(List.of("play", "volume", "stop"), events);
    } finally { release.countDown(); audio.close(); }
    assertTrue(closed.await(2, TimeUnit.SECONDS));
    audio.close(); audio.playSfx("ignored");
    assertEquals(List.of("play", "volume", "stop", "close"), events);
  }
}
