# Render-API: Graphics Abstraction Layer

**Module:** `modules/render-api/`  
**Package:** `com.jvn.render`  
**Purpose:** Abstract graphics rendering interfaces. JavaFX and Swing are
usable desktop backends; Web has an executable Canvas 2D bootstrap; Android and
iOS remain non-deployable scaffolds.

---

## Overview

The render-api module defines the contract between JVN's scene graph and platform-specific rendering backends. Instead of coupling the scene system to a single graphics library, render-api provides an abstraction that allows different backends to implement 2D drawing primitives independently.

### Key Abstractions

- **RendererRegistry** — auto-discovers and manages available renderer backends via Java ServiceLoader
- **RendererFactory** — creates platform-specific `Blitter2D` instances
- **RenderSurface** — rendering-target abstraction (size, pixel scale, validity, presentation)
- **Blitter2D** (from core) — drawing primitive interface (rect, circle, sprite, text)
- **InputSource** — a separate platform-input abstraction

---

## Architecture

```
Application (Editor/Runtime)
          ↓
     Engine.java (core)
          ↓
   RendererRegistry (render-api)
          ↓ (discovers)
   RendererFactory implementations
     (deployable status varies)
          ↓
  RenderSurface (window/framebuffer)
  + Blitter2D (drawing commands)
```

Service discovery proves that a factory is on the classpath; it does not prove
that the target has a build pipeline or implemented native bindings. See the
[platform status matrix](../../runtime/platforms/README.md).

---

## Core Classes

### RendererRegistry

**Location:** `modules/render-api/src/main/java/com/jvn/render/RendererRegistry.java`

Discovers and manages platform-specific renderer implementations using Java `ServiceLoader`.

```java
public class RendererRegistry {
  public RendererRegistry();  // auto-loads factories from classpath
  
  public RendererFactory get(String name);           // get by name
  public RendererFactory getFirst();                 // get first available
  public List<String> getAvailableRenderers();       // list all names
  public boolean isAvailable(String name);           // check availability
}
```

**Usage:**
```java
RendererRegistry registry = new RendererRegistry();
RendererFactory factory = registry.get("JavaFX");
if (factory != null) {
  Blitter2D blitter = factory.createBlitter2D(surface);
}
```

**How it works:**
1. Scans classpath for `RendererFactory` implementations
2. Platform modules may provide a factory; only JavaFX/Swing have supported desktop launch paths
3. Factories self-register via Java ServiceLoader; no manual registration needed

---

### RendererFactory

**Location:** `modules/render-api/src/main/java/com/jvn/render/RendererFactory.java`

Creates a backend-specific blitter for a compatible surface.

Factories also expose `getCapabilities()` so launchers and project validators can
negotiate optional features before a renderer is created. The same immutable
`RendererCapabilities` value is available from `Blitter2D#getCapabilities()`.

```java
RendererCapabilities capabilities = factory.getCapabilities();
if (capabilities.supports(RenderFeature.BLUR)) {
  // Enable the portable blur effect path.
}
capabilities.require(RenderFeature.OFFSCREEN_RENDER_TARGETS);
```

Advanced operations also have typed overloads, including `RenderBlendMode`,
`StrokeCap`, `StrokeJoin`, `TextHorizontalAlign`, and `TextVerticalAlign`.
Unsupported legacy calls emit a warn-once diagnostic instead of disappearing
silently; typed calls fail immediately unless the capability was advertised.

### Offscreen Targets and Composition

JavaFX and Swing advertise `OFFSCREEN_RENDER_TARGETS`. Their blitters can create
backend-owned `RenderTarget2D` surfaces, render into them with the same API, and
composite them back into the current destination. `Compositor2D` owns target
lifetimes and provides crossfades, alpha masking, blend selection, opacity, blur,
and colour-matrix options with capability validation.

```java
try (Compositor2D compositor = new Compositor2D(blitter)) {
  RenderTarget2D from = compositor.renderToTarget(w, h, scale, b -> renderOldScene(b));
  RenderTarget2D to = compositor.renderToTarget(w, h, scale, b -> renderNewScene(b));
  compositor.crossFade(from, to, progress, 0, 0, w, h);
}
```

Render targets are backend-local: attempting to draw a Swing target through a
JavaFX blitter (or vice versa) is rejected explicitly.

```java
public interface RendererFactory {
  default RendererCapabilities getCapabilities();
  Blitter2D createBlitter2D(RenderSurface surface);
  String getRendererName();
}
```

**Implementation Examples:**
- `modules/fx/...` — supported JavaFX desktop backend
- `modules/swing/...` — supported secondary Swing desktop backend
- `modules/web-runtime/...` — TeaVM/Canvas bootstrap built with `:web-runtime:webDist`
- `modules/android-runtime/...` — Android-shaped scaffold; no Android build
- `modules/ios-runtime/...` — CoreGraphics-shaped scaffold; no iOS build

**Pattern:** Each platform module implements `RendererFactory` and registers itself via ServiceLoader config file.

---

### RenderSurface

**Location:** `modules/render-api/src/main/java/com/jvn/render/RenderSurface.java`

Abstracts the window or framebuffer that receives drawing commands.

```java
public interface RenderSurface {
  double getWidth();
  double getHeight();
  double getPixelScale();
  void present();
  boolean isValid();
  void dispose();
}
```

**Platform Examples:**
- **Browser:** a Canvas 2D surface drives the TeaVM bootstrap.
- **Android/iOS:** surface classes describe planned integrations.

---

### InputSource

**Location:** `modules/render-api/src/main/java/com/jvn/render/InputSource.java`

The host can implement `onKeyEvent(int, char, boolean)`, `onMouseEvent(double, double, int, boolean)`, `onTouchEvent(double[][])` and `onScrollEvent(double, double, double, double)`. These are default callbacks. Override the events the host handles.

## How rendering flows

The host creates a surface for its backend. `RendererRegistry` discovers `RendererFactory` providers on the runtime classpath. A factory creates a `Blitter2D` for a compatible surface. The scene draws through that blitter and the host presents the frame.

```java
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.render.RenderSurface;
import com.jvn.render.RendererFactory;
import com.jvn.render.RendererRegistry;

class SceneOutput {
  static Blitter2D attach(RenderSurface surface, String backend) {
    RendererRegistry registry = new RendererRegistry();
    RendererFactory factory = registry.get(backend);
    if (factory == null) {
      throw new IllegalStateException("Available renderers: " + registry.getAvailableRenderers());
    }
    return factory.createBlitter2D(surface);
  }
}
```

For example, provide an `FxRenderSurface` when selecting `"JavaFX"`. The JavaFX factory uses its graphics context and initial viewport size. Ask the factory for `getCapabilities()` before selecting optional rendering features. The order of discovered providers is unsuitable as a persisted backend preference. Store the backend name and check that it is available on startup.

## Add a backend

1. Implement `RenderSurface` for the drawing target and `Blitter2D` for its drawing operations.
2. Implement `RendererFactory`. Give the backend a distinct name, report supported capabilities and validate the surface type when creating a blitter.
3. Add the factory’s fully qualified class name to `META-INF/services/com.jvn.render.RendererFactory` in the backend module.
4. Put that module on the host’s runtime classpath. Verify that `getAvailableRenderers()` lists its name and that a frame rendered through its surface is visible.

Desktop JavaFX and Swing have working launch paths. The browser module produces an executable Canvas bootstrap. Android and iOS remain scaffolds. See [platform runtimes](../../runtime/platforms/README.md) before planning an export.

## Related Documentation

- **2D Engine:** [docs/architecture/core/2d-engine.md](2d-engine.md) — scene graph, entities, rendering pipeline
- **Platform Runtimes:**
  - [Android Runtime](../../runtime/platforms/android-runtime.md)
  - [iOS Runtime](../../runtime/platforms/ios-runtime.md)
  - [Web Runtime](../../runtime/platforms/web-runtime.md)
- **System Architecture:** [docs/architecture/core/system-architecture.md](system-architecture.md) — how renderer fits into Engine
- **Interop:** [docs/runtime/core/interop.md](../../runtime/core/interop.md) — Java↔native integration

---

**Last Updated:** May 2026  
**Module Status:** Stable API, used by all platforms
