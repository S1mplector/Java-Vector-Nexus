package com.jvn.core.engine;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.input.Input;
import com.jvn.core.menu.MenuActionHandler;
import com.jvn.core.scene.Scene;
import com.jvn.core.scene.SceneManager;
import com.jvn.core.tween.TweenRunner;
import com.jvn.core.vn.VnInteropFactory;

/**
 * Central game engine orchestrator for Java-Vector-Nexus.
 *
 * <p>The {@code Engine} owns the core subsystems — scene management, input handling,
 * tween animation, and frame statistics — and drives them through a deterministic
 * update loop each display frame. It is <b>platform-agnostic</b>: the host renderer
 * (e.g. JavaFX, Swing, or a headless test harness) calls {@link #update(long)} once
 * per frame with the elapsed wall-clock delta in milliseconds.</p>
 *
 * <h2>Update Pipeline</h2>
 * <pre>
 *   ┌─────────────────────────────────────────────────────┐
 *   │  1. Record raw delta → FrameStats                   │
 *   │  2. Notify listeners (preUpdate)                    │
 *   │  3. Clamp → Smooth → Scale delta                    │
 *   │  4. [if paused/stopped] skip game logic, end frame  │
 *   │  5. Fixed-update loop (physics, deterministic sim)  │
 *   │  6. Variable update (tweens, scene.update)          │
 *   │  7. Late update (camera follow, post-logic work)    │
 *   │  8. End input frame; notify listeners (postUpdate)  │
 *   └─────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Fixed vs Variable Timestep</h2>
 * <p>When {@link #setFixedUpdateStepMs(long, int)} is configured with a positive step,
 * the engine runs a <b>semi-fixed timestep</b>: it accumulates elapsed time and invokes
 * {@link Scene#fixedUpdate(long)} in constant-sized chunks, up to a configurable
 * maximum number of steps per frame to prevent the "spiral of death." The leftover
 * accumulator fraction is exposed via {@link #getInterpolationAlpha()} so renderers
 * can interpolate between physics snapshots for smooth visuals.</p>
 *
 * <h2>Time Scaling</h2>
 * <p>The global {@link #setTimeScale(double)} multiplier is applied <em>after</em>
 * clamping and smoothing, affecting both fixed and variable updates uniformly.
 * This makes slow-motion and fast-forward trivial to implement.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>The engine is <b>not thread-safe</b>. All calls — including {@code update},
 * scene transitions, and input injection — must happen on the same thread
 * (typically the render/UI thread).</p>
 *
 * @see Scene
 * @see SceneManager
 * @see EngineListener
 * @see FrameStats
 */
public class Engine implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(Engine.class);

  /** Immutable application configuration snapshot provided at construction time. */
  private final ApplicationConfig config;

  /** Whether the engine has been started via {@link #start()}. */
  private boolean started;

  /** Whether the engine is currently paused; game logic is skipped while {@code true}. */
  private boolean paused;

  /** Stack-based scene manager; the top scene receives update callbacks. */
  private final SceneManager sceneManager = new SceneManager();

  /** Shared input state; polled by scenes and reset at the end of each frame. */
  private final Input input = new Input();

  /** Global tween runner; updated each variable-update phase. */
  private final TweenRunner tweens = new TweenRunner();

  /** Rolling-window frame timing statistics for diagnostics and profiling. */
  private final FrameStats frameStats = new FrameStats();

  /**
   * Registered observers notified at the start and end of each frame.
   *
   * <p>Mutations made from inside a listener callback are deferred via
   * {@link #pendingListenerMutations} so iteration in {@link #update(long)}
   * is never corrupted. Mirrors the re-entrancy strategy used by
   * {@link com.jvn.core.tween.TweenRunner}.</p>
   */
  private final List<EngineListener> listeners = new ArrayList<>();

  /**
   * Queued add/remove operations accumulated while {@link #dispatchingListeners}
   * is true. Applied atomically after the current dispatch finishes so
   * listeners can safely register / unregister themselves from inside a callback.
   */
  private List<Runnable> pendingListenerMutations = new ArrayList<>();
  private List<Runnable> drainingListenerMutations = new ArrayList<>();

  /** Runtime resources whose lifetime is bounded by this engine instance. */
  private final List<AutoCloseable> ownedResources = new ArrayList<>();

  /** Guards deterministic, idempotent teardown after {@link #stop()}. */
  private boolean resourcesClosed;

  /** True while iterating {@link #listeners}; re-entrant mutations are queued. */
  private boolean dispatchingListeners = false;

  /** Optional factory for creating VN interop bridges (set by runtime layer). */
  private VnInteropFactory vnInteropFactory;

  /** Optional handler that intercepts custom menu actions at runtime. */
  private MenuActionHandler menuActionHandler;
  private String sharedMenuBgmPath;

  // ──────────────────────────────────────────────────────────────────────────
  //  Timing parameters
  // ──────────────────────────────────────────────────────────────────────────

  /** Maximum allowed raw delta (ms). Deltas above this are clamped to prevent spiral-of-death. */
  private long maxDeltaMs = 75;

  /**
   * Exponential moving average (EMA) smoothing factor for delta times.
   * 0 = no smoothing (raw deltas); 1 = instant response.
   * Default 0.1 provides a gentle low-pass filter that absorbs occasional hitches.
   */
  private double deltaSmoothing = 0.1;

  /** Running EMA of frame deltas; initialised to -1 to signal "first frame." */
  private double smoothedDeltaMs = -1.0;

  /** Duration of one fixed-update step (ms). 0 = fixed timestep disabled. */
  private long fixedUpdateMs = 0;

  /** Maximum number of fixed-update steps per frame to prevent spiral-of-death. */
  private int maxFixedSteps = 5;

  /** Time (ms) accumulated toward the next fixed-update tick. */
  private long accumulatorMs = 0;

  /** Global time multiplier applied after clamping and smoothing. Clamped to [0, 10]. */
  private double timeScale = 1.0;

  /**
   * Interpolation alpha for rendering.
   * When using a fixed timestep, this is the fraction of a fixed step
   * remaining in the accumulator after the last physics tick.
   * Renderers can use this to interpolate between the previous and current
   * physics state for smooth visuals: {@code lerp(prevState, curState, alpha)}.
   * Value is always in [0.0, 1.0]. When fixed timestep is disabled, this is 0.0.
   */
  private double interpolationAlpha = 0.0;

  /**
   * Construct a new engine with the given application configuration.
   *
   * <p>If {@code config} is non-null, timing parameters (fixed-update step,
   * max steps, time scale) are initialised from it immediately. Pass {@code null}
   * for a bare engine with default timing (no fixed timestep, 1× scale).</p>
   *
   * @param config application configuration, or {@code null} for defaults
   */
  public Engine(ApplicationConfig config) {
    this.config = config;
    if (config != null) {
      setFixedUpdateStepMs(config.fixedUpdateMs(), config.fixedUpdateMaxSteps());
      setTimeScale(config.timeScale());
    }
  }

  /**
   * Mark the engine as started. While started, the update loop will process
   * game logic (scenes, tweens, fixed updates). Call this after all initial
   * scenes and configuration have been set up.
   */
  public void start() {
    if (resourcesClosed) {
      throw new IllegalStateException("A stopped engine cannot be restarted after its resources are closed");
    }
    resetFrameTiming();
    this.started = true;
  }

  /**
   * Mark the engine as stopped. Game logic ceases on the next frame.
   * Unlike {@link #setPaused(boolean)}, stopping is typically permanent
   * (e.g. application shutdown).
   */
  public void stop() {
    this.started = false;
    close();
  }

  /**
   * Give the engine ownership of a runtime resource such as an audio backend.
   *
   * <p>Owned resources are closed in reverse registration order when the engine
   * stops, including when a platform window is closed directly. Registering the
   * same object more than once is harmless.</p>
   *
   * @param resource resource to close with the engine; {@code null} is ignored
   * @return the supplied resource for convenient construction-time wiring
   */
  public <T extends AutoCloseable> T own(T resource) {
    if (resource == null) return null;
    if (resourcesClosed) {
      closeResource(resource);
      return resource;
    }
    boolean alreadyOwned = false;
    for (int i = 0; i < ownedResources.size(); i++) {
      if (ownedResources.get(i) == resource) {
        alreadyOwned = true;
        break;
      }
    }
    if (!alreadyOwned) ownedResources.add(resource);
    return resource;
  }

  /**
   * Release all engine-owned runtime resources. This operation is idempotent.
   */
  @Override
  public void close() {
    if (resourcesClosed) return;
    resourcesClosed = true;
    for (int i = ownedResources.size() - 1; i >= 0; i--) {
      closeResource(ownedResources.get(i));
    }
    ownedResources.clear();
  }

  private void closeResource(AutoCloseable resource) {
    try {
      resource.close();
    } catch (Exception ex) {
      log.warn("Failed to close engine-owned resource {}", resource.getClass().getName(), ex);
    }
  }

  /** @return {@code true} if the engine has been started and not yet stopped */
  public boolean isStarted() {
    return started;
  }

  // --- Pause ---

  /**
   * Pause the engine. While paused, game time is frozen — no scene updates,
   * fixed updates, tweens, or late updates run. Input is still processed
   * and {@link #input()} remains responsive, so pause menus can react.
   * Listeners still receive pre/post update callbacks.
   */
  public void setPaused(boolean paused) {
    if (this.paused && !paused) resetFrameTiming();
    this.paused = paused;
  }

  public boolean isPaused() {
    return paused;
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Configuration
  // ──────────────────────────────────────────────────────────────────────────

  /** @return the immutable application config provided at construction, or {@code null} */
  public ApplicationConfig getConfig() {
    return config;
  }

  // --- Main update loop ---

  /**
   * Main frame entry point. Called once per display frame by the renderer.
   *
   * <p><b>Frame pipeline:</b></p>
   * <ol>
   *   <li>Record raw delta for stats; notify listeners ({@code preUpdate})</li>
   *   <li>Clamp &rarr; smooth &rarr; scale delta</li>
   *   <li>If paused or not started: skip game logic, still process input</li>
   *   <li><b>Fixed update phase</b> — accumulate time, call {@link Scene#fixedUpdate(long)}
   *       at fixed intervals. Compute {@link #getInterpolationAlpha()} from remainder.</li>
   *   <li><b>Variable update phase</b> — update tweens, call {@link Scene#update(long)} once</li>
   *   <li><b>Late update phase</b> — call {@link Scene#lateUpdate(long)} once</li>
   *   <li>End input frame; notify listeners ({@code postUpdate})</li>
   * </ol>
   */
  public void update(long deltaMs) {
    frameStats.record(deltaMs);
    dispatchPreUpdate(deltaMs);

    long clamped = clampDelta(deltaMs);
    long smoothed = smoothDelta(clamped);
    long effective = applyTimeScale(smoothed);

    // `try/finally` guarantees postUpdate + input.endFrame() run even when a
    // scene / tween callback throws — otherwise edge-triggered input state
    // would get permanently stuck and listeners would silently desync.
    try {
      if (!started || paused) {
        interpolationAlpha = 0.0;
        return;
      }

      // --- Fixed update phase ---
      if (fixedUpdateMs > 0) {
        accumulatorMs = saturatingAdd(accumulatorMs, effective);
        int steps = 0;
        while (accumulatorMs >= fixedUpdateMs && steps < maxFixedSteps) {
          Scene fixedScene = sceneManager.peek();
          if (fixedScene != null) {
            fixedScene.fixedUpdate(fixedUpdateMs);
          }
          accumulatorMs -= fixedUpdateMs;
          steps++;
        }
        if (steps == maxFixedSteps && accumulatorMs > fixedUpdateMs) {
          // Cap residue at one step so we don't immediately re-trigger the
          // spiral on the next frame, but keep interpolationAlpha bounded.
          accumulatorMs = fixedUpdateMs;
        }
        interpolationAlpha = (double) accumulatorMs / fixedUpdateMs;
        if (interpolationAlpha < 0.0) interpolationAlpha = 0.0;
        else if (interpolationAlpha > 1.0) interpolationAlpha = 1.0;
      } else {
        interpolationAlpha = 0.0;
      }

      // --- Variable update phase ---
      tweens.update(effective);
      Scene current = sceneManager.peek();
      if (current != null) {
        current.update(effective);
      }

      // --- Late update phase ---
      // A scene that transitioned during update has already received onExit;
      // do not call into it again, and do not late-update its replacement
      // before that replacement has received a regular update.
      if (current != null && sceneManager.peek() == current) {
        current.lateUpdate(effective);
      }
    } finally {
      input.endFrame();
      dispatchPostUpdate(effective);
    }
  }

  /**
   * Fire {@link EngineListener#preUpdate(long)} on every registered listener.
   * Mutations to the listener list from inside a callback are deferred via
   * {@link #pendingListenerMutations}; exceptions from one listener do not
   * prevent subsequent listeners from being notified.
   */
  private void dispatchPreUpdate(long deltaMs) {
    dispatchingListeners = true;
    try {
      for (int i = 0, n = listeners.size(); i < n; i++) {
        try {
          listeners.get(i).preUpdate(deltaMs);
        } catch (RuntimeException ex) {
          reportListenerFailure("preUpdate", ex);
        }
      }
    } finally {
      dispatchingListeners = false;
      drainPendingListenerMutations();
    }
  }

  /** Symmetric counterpart to {@link #dispatchPreUpdate(long)}. */
  private void dispatchPostUpdate(long deltaMs) {
    dispatchingListeners = true;
    try {
      for (int i = 0, n = listeners.size(); i < n; i++) {
        try {
          listeners.get(i).postUpdate(deltaMs);
        } catch (RuntimeException ex) {
          reportListenerFailure("postUpdate", ex);
        }
      }
    } finally {
      dispatchingListeners = false;
      drainPendingListenerMutations();
    }
  }

  private void drainPendingListenerMutations() {
    if (pendingListenerMutations.isEmpty()) return;
    // Swap instead of copying to avoid per-drain allocation.
    List<Runnable> batch = pendingListenerMutations;
    pendingListenerMutations = drainingListenerMutations;
    drainingListenerMutations = batch;
    pendingListenerMutations.clear();
    for (int i = 0, n = batch.size(); i < n; i++) {
      try {
        batch.get(i).run();
      } catch (RuntimeException ex) {
        reportListenerFailure("listener-mutation", ex);
      }
    }
    batch.clear();
  }

  private static void reportListenerFailure(String phase, Throwable t) {
    log.error("Engine: {} threw {}: {}", phase, t.getClass().getSimpleName(), t.getMessage(), t);
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Subsystem accessors
  // ──────────────────────────────────────────────────────────────────────────

  /** @return the stack-based {@link SceneManager} used to push/pop/swap scenes */
  public SceneManager scenes() {
    return sceneManager;
  }

  /** @return the shared {@link Input} state polled by scenes each frame */
  public Input input() {
    return input;
  }

  /** @return the global {@link TweenRunner} for fire-and-forget value animations */
  public TweenRunner tweens() {
    return tweens;
  }

  /** @return the rolling-window {@link FrameStats} for FPS / frame-time diagnostics */
  public FrameStats frameStats() {
    return frameStats;
  }

  /**
   * Interpolation alpha for fixed-timestep rendering.
   * When using a fixed timestep, this is the fractional progress toward the
   * next fixed tick (range [0.0, 1.0]). Use it to interpolate visual state
   * between the last two physics snapshots for stutter-free rendering:
   * <pre>{@code
   * double renderX = prevX + (curX - prevX) * engine.getInterpolationAlpha();
   * }</pre>
   * Returns 0.0 when fixed timestep is disabled.
   */
  public double getInterpolationAlpha() {
    return interpolationAlpha;
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Timing configuration
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Set the maximum allowed raw frame delta. Any delta exceeding this value
   * is clamped down, preventing the "spiral of death" when the application
   * is suspended or a debugger break causes a huge time jump.
   *
   * @param ms max delta in milliseconds; 0 or negative disables clamping
   */
  public void setMaxDeltaMs(long ms) {
    this.maxDeltaMs = ms <= 0 ? 0 : ms;
  }

  /**
   * Set the exponential moving average smoothing factor for frame deltas.
   *
   * <p>Lower values produce a heavier low-pass filter (smoother but laggier);
   * higher values track real frame times more closely. A value of {@code 0}
   * disables smoothing entirely.</p>
   *
   * @param alpha smoothing factor, clamped to [0.0, 1.0]
   */
  public void setDeltaSmoothing(double alpha) {
    if (Double.isNaN(alpha) || Double.isInfinite(alpha) || alpha < 0) alpha = 0;
    if (alpha > 1) alpha = 1;
    this.deltaSmoothing = alpha;
  }

  /**
   * Configure the fixed-update timestep.
   *
   * <p>When {@code stepMs > 0}, the engine accumulates elapsed time and fires
   * {@link Scene#fixedUpdate(long)} in constant-size chunks. This is ideal for
   * physics, networking, or any simulation that requires deterministic ticks.</p>
   *
   * @param stepMs   duration of one fixed step in ms; 0 or negative disables fixed update
   * @param maxSteps maximum fixed steps per frame (min 1) to prevent spiral-of-death
   */
  public void setFixedUpdateStepMs(long stepMs, int maxSteps) {
    long resolvedStep = stepMs <= 0 ? 0 : stepMs;
    if (this.fixedUpdateMs != resolvedStep) {
      accumulatorMs = 0;
      interpolationAlpha = 0.0;
    }
    this.fixedUpdateMs = resolvedStep;
    this.maxFixedSteps = Math.max(1, maxSteps);
  }

  /**
   * Set the global time scale. Multiplies the effective delta after clamping
   * and smoothing. Use for slow-motion ({@code 0.5}), fast-forward ({@code 2.0}),
   * or freeze ({@code 0.0}) effects.
   *
   * @param scale time multiplier; clamped to [0.0, 10.0]
   */
  public void setTimeScale(double scale) {
    if (Double.isNaN(scale) || Double.isInfinite(scale) || scale < 0) scale = 0;
    if (scale > 10.0) scale = 10.0;
    this.timeScale = scale;
  }

  /** @return current time scale multiplier */
  public double getTimeScale() {
    return timeScale;
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Listener management
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Register an {@link EngineListener} to receive frame-boundary callbacks.
   * Duplicate registrations are silently ignored.
   *
   * <p>Safe to call from inside a listener callback: the addition is queued
   * and applied after the current dispatch completes, so iteration is never
   * corrupted.</p>
   *
   * @param listener the listener to add; {@code null} is ignored
   */
  public void addListener(EngineListener listener) {
    if (listener == null) return;
    if (dispatchingListeners) {
      pendingListenerMutations.add(() -> {
        if (!listeners.contains(listener)) listeners.add(listener);
      });
      return;
    }
    if (!listeners.contains(listener)) {
      listeners.add(listener);
    }
  }

  /**
   * Unregister a previously registered listener. No-op if not found.
   *
   * <p>Safe to call from inside a listener callback — the removal is
   * deferred until the current dispatch finishes.</p>
   *
   * @param listener the listener to remove
   */
  public void removeListener(EngineListener listener) {
    if (listener == null) return;
    if (dispatchingListeners) {
      pendingListenerMutations.add(() -> listeners.remove(listener));
      return;
    }
    listeners.remove(listener);
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Internal helpers — delta processing pipeline
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Clamp the raw delta to [{@code 0}, {@link #maxDeltaMs}].
   * Negative deltas (clock skew) are floored to zero.
   */
  private long clampDelta(long deltaMs) {
    if (deltaMs < 0) return 0;
    if (maxDeltaMs > 0 && deltaMs > maxDeltaMs) return maxDeltaMs;
    return deltaMs;
  }

  /**
   * Apply exponential moving average smoothing to the clamped delta.
   * On the very first frame ({@code smoothedDeltaMs < 0}) the EMA is
   * seeded with the raw value to avoid a ramp-up artefact.
   */
  private long smoothDelta(long deltaMs) {
    if (deltaSmoothing <= 0) return deltaMs;
    if (smoothedDeltaMs < 0) smoothedDeltaMs = deltaMs;
    smoothedDeltaMs = smoothedDeltaMs + (deltaMs - smoothedDeltaMs) * deltaSmoothing;
    if (smoothedDeltaMs < 0) smoothedDeltaMs = 0;
    return Math.round(smoothedDeltaMs);
  }

  /**
   * Multiply the smoothed delta by the global {@link #timeScale}.
   * Fast-path short-circuits for the common cases of 1× and 0× scale.
   */
  private long applyTimeScale(long deltaMs) {
    if (timeScale == 1.0) return deltaMs;
    if (timeScale == 0.0) return 0;
    return Math.round(deltaMs * timeScale);
  }

  private void resetFrameTiming() {
    smoothedDeltaMs = -1.0;
    accumulatorMs = 0;
    interpolationAlpha = 0.0;
  }

  private static long saturatingAdd(long a, long b) {
    if (b <= 0) return a;
    return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Interop / menu wiring — set by the runtime layer
  // ──────────────────────────────────────────────────────────────────────────

  /** Set the factory used to create VN interop bridges for visual-novel scenes. */
  public void setVnInteropFactory(VnInteropFactory f) { this.vnInteropFactory = f; }

  /** @return the current VN interop factory, or {@code null} if not configured */
  public VnInteropFactory getVnInteropFactory() { return vnInteropFactory; }

  /** Set the handler that intercepts custom menu action keys at runtime. */
  public void setMenuActionHandler(MenuActionHandler handler) { this.menuActionHandler = handler; }

  /** @return the current menu action handler, or {@code null} if not configured */
  public MenuActionHandler getMenuActionHandler() { return menuActionHandler; }

  /** Optional music shared by the title menu and the game it opens. */
  public void setSharedMenuBgmPath(String path) { sharedMenuBgmPath = path; }

  public String getSharedMenuBgmPath() { return sharedMenuBgmPath; }
}
