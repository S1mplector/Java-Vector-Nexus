package com.jvn.fx.menu;

import com.jvn.core.scene.Scene;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ObjDoubleConsumer;

/** Tracks the foreground layers while the tutorial's backdrop stays in place. */
final class TutorialMenuTransition {
  private static final long DURATION_NS = 420_000_000L;
  private final Map<Scene, Fade> layers = new LinkedHashMap<>();
  private Scene active;
  private long renderedAt;

  Scene active() { return active; }

  void show(Scene scene, long now, boolean keepOutgoing, boolean fadeIn) {
    if (scene == active) return;
    if (keepOutgoing) {
      layers.replaceAll((layer, fade) -> new Fade(fade.opacity(now), 0.0, now));
    } else {
      layers.clear();
    }
    Fade existing = layers.get(scene);
    double startOpacity = existing == null ? 0.0 : existing.from();
    if (!fadeIn) startOpacity = 1.0;
    layers.put(scene, new Fade(startOpacity, 1.0, now));
    active = scene;
  }

  void draw(long now, ObjDoubleConsumer<Scene> render) {
    renderedAt = now;
    layers.entrySet().removeIf(entry -> entry.getValue().opacity(now) == 0.0 && entry.getKey() != active);
    layers.forEach((scene, fade) -> {
      double opacity = fade.opacity(now);
      if (opacity > 0.0) render.accept(scene, opacity);
    });
  }

  boolean acceptsPointer(Scene scene) {
    Fade fade = layers.get(scene);
    return scene == active && fade != null && fade.opacity(renderedAt) == 1.0;
  }

  void reset() {
    active = null;
    layers.clear();
  }

  private record Fade(double from, double to, long started) {
    double opacity(long now) {
      double t = Math.clamp((now - started) / (double) DURATION_NS, 0.0, 1.0);
      if (t == 1.0) return to;
      double ease = t * t * (3.0 - 2.0 * t);
      return from + (to - from) * ease;
    }
  }
}
