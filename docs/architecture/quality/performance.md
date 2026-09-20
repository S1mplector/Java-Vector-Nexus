# Performance and Build Footprint

This guide covers practical optimization for JVN projects and editor/runtime workflows.

## Build-Level Performance

### Use targeted Gradle tasks during development

Instead of full `build` every iteration, prefer focused tasks:

```bash
./gradlew :core:compileJava :scripting:compileJava :editor:compileJava
./gradlew :core:test :scripting:test
```

### Keep build graph deterministic

Simp3-compatible audio is bundled in `audio` and built by default.
No extra `-PuseSimp3` toggles are needed.

### Avoid stale/global Gradle contention

Lock-heavy environments (especially Linux) should:
- run with VFS watch disabled (`org.gradle.vfs.watch=false`)
- isolate Gradle user home when running from tools (editor already does this)

## Runtime Performance

### Verify and profile the desktop GPU path

The Engine Hub's **Render Pipeline → GPU Preferred** profile selects JavaFX's native
hardware backend while retaining a software fallback. On Linux hybrid-GPU systems,
Hub-managed launches also ask `switcheroo-control` for the discrete adapter. Existing
`DRI_PRIME` or NVIDIA PRIME variables are preserved so Steam, gamescope, and desktop
GPU preferences remain authoritative.

Use **Render Pipeline → Inspect Render Stack** before a test. On Linux the report runs
the same adapter probe used by the managed launcher. Every hardware launch also writes
the resolved OpenGL vendor and renderer to the Hub log when `glxinfo` is available.
For a platform-independent recording, choose **Render Diagnostics → Launch Editor with
Java Flight Recorder**. The launch enables JavaFX's startup diagnostics (including the
actual Prism pipeline and renderer) and writes a `.jfr` recording under the platform's
JVN Engine Hub state directory:

- Linux: `$XDG_STATE_HOME/jvn-engine-hub/profiles` or `~/.local/state/jvn-engine-hub/profiles`
- macOS: `~/Library/Application Support/JVN Engine Hub/profiles`
- Windows: `%LOCALAPPDATA%\JVN Engine Hub\profiles`

Open the recording in JDK Mission Control. JFR measures Java frame submission, CPU,
allocation, locks, and GC; use a vendor GPU tool alongside it when shader or GPU-core
timings are required. The runtime's F3 HUD provides FPS, heap, cache-hit, timeline, and
draw-call signals during an interactive test.

The HUD's `Draws` line reports total per-frame draw calls, with the `char` figure
breaking out draws spent on character sprites and layered-expression crossfades
(everything else — background, particles, audio visualizer — falls under the total but
outside `char`). A high `char` count relative to the total points to expensive layered
character scenes (many visible characters, or characters mid-expression-crossfade with
several layers each) as the main cost; a high total with a low `char` count instead
points at background/effects work. The counter resets every frame and only tracks call
counts, not GPU time, so pair it with JFR when you need actual timings.

The HUD's `Relights` line counts stage-lighting recomposites: full per-pixel relights of
a character layer (`VnStageLightingSupport.buildLitCharacter`), which only run when the
active stage has lights and the lit-bitmap cache misses. The cache key snaps character
position to a small pixel grid (`VnRenderer.LIGHTING_CACHE_POSITION_GRID_PX`), so idle
bob/breathing jitter reuses the previous frame's lit bitmap instead of relighting every
frame; a real position change, expression/layer swap, stage change, or resize still
invalidates it correctly. A `Relights` count near zero during a static, lit scene means
the cache is holding; a count that tracks the frame rate during rapid expression swaps
or idle animation means something is defeating the cache (e.g. a new custom transition
path bypassing `drawCharacterImage`) and is worth investigating.

The `:testkit:jmh` suite is useful for deterministic CPU-side microbenchmarks, but it
does not prove which GPU rendered a JavaFX window. In particular, do not use the
placeholder `RenderFrameBench` as an adapter-usage test.

Editor VNS preview windows use bounded frame pacing: 60 FPS on a hardware-capable
pipeline and 30 FPS when JavaFX reports the software path. This keeps CPU rendering
responsive instead of letting preview work monopolize every JavaFX pulse. For a
controlled comparison, override the cap with
`-Djvn.editor.previewMaxFps=15..240`. UI layout/style overrides are applied when they
change rather than reparsed and reloaded on every preview frame.

### Layered timeline CPU cost

The VN renderer retains immutable layer target names and group topology across frames.
The metadata cache is bounded to 16 character/expression/slot contexts and is cleared
on project changes, cache resets, and disposal. Entity proxies, visibility, and transform
values are still resolved each frame, so new timelines and expression changes remain live.
This avoids repeatedly sanitizing every declared expression name and scanning group
membership while head/body groups animate. Target-name sanitization also uses a single
pass rather than compiling a regular expression for each alias.

To reproduce the Was I Write lightning scene with real assets and collect a JFR profile:

```bash
JVN_LIGHTNING_ROOT=/path/to/Was_I_Write ./gradlew :editor:test \
  --tests '*LightningRenderPerformanceTest'
```

The opt-in test replays the section after source line 263 in
`scripts/story/620-686-lightning.vns`. It checks that authored group positions change,
flushes Canvas rendering through a 1280×720 snapshot, and writes timing, a final frame,
and `render.jfr` under `modules/editor/build/reports/lightning` by default.
Set `JVN_LIGHTNING_OUTPUT` to choose another output directory. Snapshot-inclusive timings
are useful for comparing the same machine and scene; they are not an interactive FPS
or GPU-only benchmark.

### Preview memory safety

JavaFX image caches are bounded by estimated decoded raster bytes as well as entry
count. This matters during animation: a 1024×1024 ARGB frame is approximately 4 MiB,
so an entry-only cache of 256 transformed frames could retain about 1 GiB even when
the source image is small on disk. VNS stage composites, JES/Puppeteer processed
sprites, menu images, phone images, and Puppeteer's source-image cache all use byte
ceilings. A raster larger than an individual cache's budget may render, but is not
retained.

Closing a VNS/JES tab or Puppeteer window stops its preview activity and clears its
renderer caches. Switching a preview to another project also clears path-dependent
images. Consequently, a healthy preview heap should rise and fall within a bounded
range during repeated animation; it should not grow in proportion to the number of
frames previewed.

Do not use a larger `-Xmx` value or repeated manual GC as the primary remedy for
steadily growing preview memory. The collector cannot reclaim images while a renderer
cache still holds strong references to them. Heap tuning remains useful for projects
that legitimately keep many large decoded source assets live, but it does not replace
the cache ceilings and lifecycle cleanup.

### VNS rendering and flow

- Keep dialogue effects focused; animated per-character spans cost more than plain text.
- Avoid excessive flash/shake spam in rapid loops.
- Keep history overlay closed during normal play in low-spec targets.

### Menu rendering

- Prefer lightweight style overrides in profile files instead of large dynamic image stacks.
- Keep menu item counts bounded for keyboard/controller-first flows.

### JES scenes

- Control entity count in active scenes.
- Minimize dynamic physics body churn where possible.
- Prefer declarative timeline actions over per-frame custom callback logic.
- Disable debug overlays in production gameplay.

### Physics

- High body counts with pairwise checks scale poorly.
- Use static colliders for map geometry instead of many dynamic bodies.
- Tune fixed-step settings in `VnSettings` to balance stability and CPU cost.

## Asset Strategy

### Images

- Match source resolution to target viewport scale.
- Use sprite sheets where practical.
- Avoid oversized transparent textures for small UI elements.

### Audio

- Use compressed formats for long tracks.
- Keep simultaneous channels intentional.
- Normalize volumes to avoid heavy runtime gain swings.

### External asset overlays

Using `--assets` is good for iteration, but production packaging should keep required assets deterministic and versioned.

## Save/Load Throughput

- Save writes use temp file + atomic move, which is robust but still disk-bound.
- Avoid saving every frame; use explicit checkpoints/autosave cadence.
- Keep large custom payloads in `rpgState` compact and serializable.

## Editor Responsiveness Tips

- Keep huge generated files outside regular script/config editing tabs when possible.
- For large Markdown documentation sets, use the public documentation website for read-only lookup and open only what you edit.
- Use project filters in tree and timeline views to reduce noise.

## Quick Diagnostic Checklist

If a build or runtime session feels slow:

1. Confirm Gradle daemon/lock health (`./gradlew --status`, `./gradlew --stop`).
2. Check whether full build is necessary vs targeted module tasks.
3. Verify environment/toolchain consistency (Java version, Gradle wrapper health).
4. Profile scene content density (entities, physics, text effects, timeline activity).
5. Re-test with simplified asset set to isolate content cost vs engine cost.
