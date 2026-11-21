# Render API Module

Platform-agnostic rendering abstraction layer for Java Vector Nexus.

## Overview

The `render-api` module defines the core interfaces and service patterns for rendering across multiple platforms:
- **Desktop:** JavaFX and Swing
- **Web:** executable Canvas 2D bootstrap (TeaVM)
- **Mobile:** Android and iOS JVM scaffolds

## Core Interfaces

### RenderSurface
Abstraction for a rendering target (canvas, view, or widget).

```java
public interface RenderSurface {
  double getWidth();
  double getHeight();
  double getPixelScale();  // e.g., 2.0 for Retina
  void present();          // Flush buffer to screen
  boolean isValid();
  void dispose();
}
```

**Implementations:**
- `FxRenderSurface` (fx module): JavaFX Canvas wrapper
- `WebCanvasRenderSurface` (web-runtime): HTML5 canvas element
- `AndroidRenderSurface` (android-runtime): Android surface scaffold
- `IosRenderSurface` (ios-runtime): iOS surface scaffold

### Blitter2D
2D drawing API supporting shapes, text, images, and transformations.

```java
public interface Blitter2D {
  void clear(double r, double g, double b, double a);
  void setFill(double r, double g, double b, double a);
  void setStroke(double r, double g, double b, double a);
  void fillRect(double x, double y, double w, double h);
  void strokeRect(double x, double y, double w, double h);
  void drawImage(String classpath, double x, double y, double w, double h);
  void drawImageRegion(String classpath, double sx, double sy, double sw, double sh,
                       double dx, double dy, double dw, double dh);
  // ... 20+ more methods for shapes, text, transformations
}
```

**Implementations:**
- `FxBlitter2D` (fx module): JavaFX GraphicsContext wrapper
- `WebRenderer` (web-runtime): HTML5 Canvas 2D API
- `AndroidRenderer` (android-runtime): Android Canvas
- `IosRenderer` (ios-runtime): iOS CoreGraphics

### InputSource
Callback interface for input events (keyboard, mouse, touch).

```java
public interface InputSource {
  default void onKeyEvent(String key, boolean pressed) {}
  default void onMouseEvent(double x, double y, int button, boolean pressed) {}
  default void onTouchEvent(double x, double y, boolean pressed) {}
  default void onScrollEvent(double x, double y, double deltaX, double deltaY) {}
}
```

### RendererFactory
Service provider interface for platform-specific renderer creation.

```java
public interface RendererFactory {
  Blitter2D createBlitter2D(RenderSurface surface);
  String getRendererName();
  RendererCapabilities getCapabilities();
}
```

Use the capability object to choose a fallback or reject a project requirement
before rendering. Optional operations no longer silently disappear: legacy calls
produce warn-once diagnostics, while typed overloads validate support eagerly.

### Portable composition

`RenderTarget2D` and `Compositor2D` provide backend-owned offscreen layers.
JavaFX and Swing currently support target creation, compositing, crossfades, and
destination-alpha masks. Composite options can request opacity, typed blend modes,
blur, and a 4x5 colour matrix; unsupported effects fail capability validation.

## Service Discovery

### RendererRegistry

Discovers available renderers at runtime using Java `ServiceLoader`.

```java
RendererRegistry registry = new RendererRegistry();

// Get first available renderer
RendererFactory factory = registry.getFirst();

// Get by name
RendererFactory fxFactory = registry.get("JavaFX");

// List all available
List<String> names = registry.getAvailableRenderers();
// Output: ["JavaFX", "Canvas 2D", "Android Canvas", "iOS CoreGraphics"]
```

## Implementation Registration

Each platform registers its renderer via service metadata:

**File:** `META-INF/services/com.jvn.render.RendererFactory`

**Content (web-runtime):**
```
com.jvn.web.WebRendererFactory
```

**Content (android-runtime):**
```
com.jvn.android.AndroidRendererFactory
```

## Usage Pattern

### Creating a Renderer

```java
// Discover available renderers
RendererRegistry registry = new RendererRegistry();
RendererFactory factory = registry.get("JavaFX");

// Create rendering surface
RenderSurface surface = new FxRenderSurface(canvasNode);

// Create renderer
Blitter2D renderer = factory.createBlitter2D(surface);

// Draw
renderer.setFill(1.0, 0.0, 0.0, 1.0);  // Red
renderer.fillRect(10, 10, 100, 50);
renderer.drawImage("game/images/hero.png", 50, 50, 32, 32);
renderer.present();
```

### Implementing a New Renderer

1. Create `RenderSurface` implementation
2. Create `Blitter2D` implementation
3. Create `RendererFactory` implementation
4. Register via `META-INF/services/com.jvn.render.RendererFactory`

Example (hypothetical Vulkan renderer):

```java
public class VulkanRendererFactory implements RendererFactory {
  @Override
  public Blitter2D createBlitter2D(RenderSurface surface) {
    if (surface instanceof VulkanRenderSurface) {
      return new VulkanBlitter2D(surface);
    }
    throw new IllegalArgumentException("Requires VulkanRenderSurface");
  }

  @Override
  public String getRendererName() {
    return "Vulkan";
  }
}
```

Register in: `META-INF/services/com.jvn.render.RendererFactory`
```
com.example.VulkanRendererFactory
```

## Architecture Benefits

- **Decoupling:** Engine code doesn't depend on platform implementations
- **Extensibility:** New renderers via ServiceLoader without recompilation
- **Testing:** Mock implementations for unit testing
- **Performance:** Each renderer optimized for its platform
- **Modularity:** Optional platform modules (web/mobile/desktop)

## Dependencies

- `java.base` (Java 21+)
- `com.jvn:core` (for engine integration)

## Future Enhancements

- **Rendering Pipeline:** Extend offscreen targets from JavaFX/Swing to web and mobile backends
- **Shaders:** Custom shader support for advanced effects
- **GPU Acceleration:** Direct GPU memory management
- **Multi-threading:** Thread-safe rendering queues
