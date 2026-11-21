# Engine lifecycle and frame timing

The desktop host owns its window and render loop. `Engine` owns the scene stack, input state, tweens and update timing. Starting the engine enables updates. The host supplies each frame’s elapsed time and draws the scene through the selected backend.

## Start a session

```java
import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.core.scene.Scene;

class Session {
  final Engine engine = new Engine(ApplicationConfig.builder()
      .title("First scene").width(1280).height(720).fixedUpdate(16, 4).build());

  void open(Scene scene) {
    engine.scenes().push(scene);
    engine.start();
  }

  void frame(long elapsedMs) {
    engine.update(elapsedMs);
  }

  void close() {
    engine.stop();
  }
}
```

## What happens during a frame

1. `update(long deltaMs)` records timing in `frameStats()` and notifies pre-update listeners. The engine clamps, smooths and scales elapsed time.
2. If fixed updates are enabled, elapsed time accumulates until the active scene can receive `fixedUpdate(stepMs)`. The per-frame step limit bounds catch-up work.
3. The engine advances tweens, then calls `update(effectiveMs)` on the active scene. If that scene is still on top, it receives `lateUpdate(effectiveMs)`.
4. Input edge state is cleared at the frame boundary and post-update listeners run. The host handles drawing. Inspect `frameStats().getFps()` and `frameStats().getAvgMs()` to see the sampled timing.

`setPaused(true)` freezes scene and tween updates. Input frame boundaries and listener notifications continue. Use `setPaused(false)` to resume. `stop()` ends the session and releases owned resources. Register resources with `engine.own(resource)` when their lifetime should follow the engine. They close in reverse registration order.

## Scene transitions

| Operation | Scene callbacks |
| --- | --- |
| `scenes().push(next)` | Pause the previous top and enter the new scene. |
| `scenes().pop()` | Exit the top and resume the newly exposed scene. |
| `scenes().replace(next)` | Exit the current top and enter its replacement. The scene below stays paused. |

Implement `Scene.update(long deltaMs)` for game logic. Override `onEnter`, `onPause`, `onResume` or `onExit` when a scene has work at those boundaries. For example, a settings screen can be pushed above a running scene and popped to resume play.

## Related Documentation

- **System Architecture:** [docs/architecture/core/system-architecture.md](system-architecture.md) — where Engine fits
- **2D Engine:** [docs/architecture/core/2d-engine.md](2d-engine.md) — Scene2D and entities
- **Animation API:** [docs/scripting/timeline/animation/core-animation-api.md](../../scripting/timeline/animation/core-animation-api.md) — TimelineRunner details
- **Input System:** [docs/scripting/jes/systems/jes-input.md](../../scripting/jes/systems/jes-input.md) — input bindings in JES
- **Runtime:** [docs/runtime/core/runtime.md](../../runtime/core/runtime.md) — JvnApp wrapper
