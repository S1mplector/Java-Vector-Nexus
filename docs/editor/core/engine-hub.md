# JVN Engine Hub

The JVN Engine Hub is the small desktop control panel for the engine workspace. It is meant for the common tasks you do before opening the full editor or dropping into Gradle manually.

Launch it from the repository root:

```bash
# macOS / Linux
./jvn
```

```bat
:: Windows
jvn.bat
```

The hub starts through a small direct-launch cache, without configuring Gradle. Because the Swing hub has no external dependencies, the first launch compiles only its two source files with JDK 21 `javac`; later launches execute the cached classes immediately. The cache is rebuilt automatically when the hub source or version changes.

To force a cache rebuild or select a launch path explicitly:

```bash
./jvn --rebuild-launcher
./jvn --direct   # never invoke Gradle
./jvn --gradle   # legacy Gradle :hub:run path
```

The equivalent persistent setting is `JVN_LAUNCH_MODE=auto|direct|gradle`. Auto mode is the default: it uses direct startup and falls back to Gradle only if `javac` is unavailable and there is no valid cache. Concurrent launches share a compilation lock so they cannot corrupt or redundantly rebuild the cache. The direct JVM also uses a low-overhead serial collector and startup-tier compilation; override or extend its flags with `JVN_HUB_JAVA_OPTS`. Gradle remains in use for editor/runtime builds and other workspace actions launched from the hub.

## Hub Menus

Every Engine Hub dropdown—including the footer **More** menu—begins with live context for that command group, such as the current engine branch, update state, Gradle configuration, active task, UI scale, or next render pipeline. Commands include short descriptions, consistent icons, keyboard mnemonics, and state-aware availability so an action that conflicts with a running task is visibly disabled. Each group has its own accent; rendering controls and their nested submenus use the purple Render Pipeline accent throughout. Press `F5` to refresh engine metadata or the platform menu shortcut plus `Q` to quit.

The **View** dropdown can independently hide or show the scrolling **Performance Graph**, the CPU, JVM heap, thread, and Hub-task **Performance Metric Chips**, and contextual **Tooltips** throughout the Hub. These choices persist across Hub launches. Hiding both performance displays collapses the monitor and pauses its sampling timer, which removes the monitor's periodic CPU query and repaint work entirely.

### Project Explorer Icons

Use **View -> Project Explorer Icons** to choose how the editor renders project-tree icons. On Linux,
JVN can follow the current GTK/freedesktop theme or lock to an installed icon theme. On every
platform, **JVN Defaults** selects the bundled SVG icon set.

The menu controls icon size (12–28 logical pixels), semantic folder variants, file-type variants,
theme inheritance, bundled fallbacks, and smooth scaling. **Configure and Preview...** shows the
complete profile before saving it. Changes are stored in
`~/.jvn-editor/project-icons.properties` and apply to newly launched editor processes; restart an
already-open editor to reload the icon pack. `JVN_ICON_THEME=<name>` or
`-Djvn.icon.theme=<name>` overrides automatic Linux theme detection for that launch.

## Cache-First Application Launches

**Run Editor** and **Run Launcher** also avoid Gradle on warm starts. After source, resources, or build definitions change, JVN runs one preparation task to compile the affected modules and record the exact JavaFX module path and runtime classpath. Later starts invoke Java directly until that cache becomes stale.

The same path is used by `./jvnw editor`, `./jvnw launcher`, and `./jvnw runtime`. Control it with `JVN_APP_LAUNCH_MODE=auto|direct|gradle`: `auto` refreshes when needed, `direct` guarantees that no Gradle process will be started and reports a stale cache, and `gradle` always uses the original run task. Run `scripts/launch-app.sh editor --rebuild` to force a refresh.

On Linux/X11, the application launcher also checks whether the default GLX provider can create a hardware context. If the default provider is broken but the Mesa provider works, JVN uses Mesa for that launch and passes the GPU pipeline flags before JavaFX initializes. Toggle this from **Render Pipeline > Automatic Mesa GLX Recovery**, or set `JVN_DISABLE_GLX_FALLBACK=1` to disable it for a launch. The editor performance strip reports **GPU active**, **GPU fallback**, or **GPU off** so a software-rendering fallback is visible.

## Render Pipeline

The Engine Hub's **Render Pipeline** menu controls the JavaFX rendering profile used by newly launched JVN processes:

| Profile | Behavior |
|---------|----------|
| Adaptive Selection | Lets JavaFX choose the platform renderer and fallbacks. This is the recommended default. |
| GPU Preferred | Tries Direct3D on Windows or OpenGL ES2 elsewhere, while retaining the software renderer as a safe fallback. |
| Software Compatibility | Uses the CPU renderer for driver troubleshooting and compatibility testing. |

The selected profile is shared by the editor, Puppeteer and other previews, the standalone launcher, and game-runtime processes. It is stored as `graphics.mode` in `~/.jvn-editor/editor-preferences.properties` and takes effect when the next process starts; an already-open editor is not reconfigured in place.

The bundled JavaFX 23 renderer uses `d3d,sw` on Windows and `es2,sw` on macOS/Linux. GPU Preferred retains JavaFX's driver qualification checks; it does not force a rejected driver or claim a native Metal backend. Explicit advanced `prism.order` / `prism.forceGPU` overrides remain available to manual launches.

**Preview Frame Budget** controls drawing in docked, detached, and fullscreen editor previews:

| Budget | Use |
|---|---|
| Automatic | 60 FPS with hardware features; 30 FPS with software rendering |
| Efficient — 30 FPS | Reduce preview draw work and power use while editing |
| Balanced — 60 FPS | Smooth previews at a standard display refresh rate |
| High Refresh — 120 FPS | Raise the preview cap when the display and editor frame limit permit it |

This is a cap, not a guaranteed frame rate. Lower budgets preserve elapsed animation time while skipping draw calls; the game runtime's timing is unchanged. The setting is `render.previewMaxFps` in the tuning file (`0` means automatic). A manual `-Djvn.editor.previewMaxFps` override takes priority. At a stable 60 Hz pulse rate, the efficient budget performs 300 draws over ten seconds instead of 600.

**Check Actual Renderer** starts a short JavaFX process using the chosen profile and prints its initialized Prism backend, hardware capabilities, and preview cap to the Hub console. It does not open the full editor. The equivalent command is `./gradlew :editor:probeGraphics` (use `gradlew.bat` on Windows). It needs a graphical desktop and the platform's JavaFX native libraries.

The **Advanced Rendering** submenu also controls:

- display synchronization (Prism VSync)
- dirty-region rendering
- occlusion culling
- shape caching for complex shapes, all shapes, or no shapes

The **Render Diagnostics** submenu can enable verbose Prism startup output, dirty-region visualization, overdraw visualization, and render-graph capture. These probes are intended for short diagnostic sessions and may reduce performance while enabled. **Disable All Render Diagnostics** returns them to the quiet state.

**Open Render Graph Viewer** opens a compact live window for the latest detailed JavaFX slow-pulse tree emitted by a Hub-managed editor, preview, launcher, or game process. Enable capture, launch the process from the Hub, and interact with its UI; the viewer retains the newest bounded graph and can copy or clear it. JavaFX only emits detailed trees for pulses over its logging threshold, so a smooth idle scene may remain in the waiting state. Render-graph capture enables the dirty-region roots Prism requires for that diagnostic launch, but JVN does not automatically enable JavaFX's unstable internal PulseLogger. It remains off by default because it adds overhead and can fail inside the JavaFX render thread.

Advanced values are stored in `~/.jvn-editor/render-pipeline.properties` and applied before JavaFX initializes. Use **Inspect Render Stack** to review every selected option, the requested backend order, desktop session, display capabilities, preference paths, and—on Linux when `glxinfo` is installed—the active OpenGL vendor and renderer. **Copy Render Stack Summary** creates a compact report suitable for performance or driver bug reports.

## Source-First Preview Builds

JVN does not publish official prebuilt Engine Hub, editor, or runtime binaries yet. During the current preview phase, run the hub from a source checkout with `./jvn` on macOS/Linux or `jvn.bat` on Windows. The hub then uses the same checkout for editor launches, builds, tests, update operations, shortcut installation, and project packaging.

This source-first workflow is deliberate. The engine and editor are still changing quickly, and the wrapper-driven hub keeps the Gradle wrapper, module classpath, generated resources, Java toolchain, and repository update state aligned. It also avoids shipping stale platform launchers while packaging, signing/notarization, update channels, and cross-platform QA are still being finalized.

Official prebuilt binaries are planned from the first major JVN release onward, currently expected by the end of 2026. Until then, the supported desktop path is to clone the repository, run `./jvnw` for commands, and use the Engine Hub for the no-terminal desktop workflow.

## High-Resolution Display Scaling

The hub automatically enlarges its window, fonts, icon buttons, and status surfaces on high-DPI or very high-resolution displays. This is meant to keep the compact Swing control panel readable on 2K/3K/4K OLED panels and Linux desktops that report a large physical framebuffer without applying Swing UI scaling.

Use **View > UI Scale** for automatic sizing, common presets, or **Custom Scale**. The custom dialog accepts percentages such as `125` or `125%`, scale factors such as `1.25`, and locale decimal input such as `1,25`. Fixed values are validated from 75% through 185% and saved in the Hub UI preferences.

If your desktop reports unusual DPI data, override the hub scale manually:

```bash
java -Djvn.hub.uiScale=1.35 -jar build/distributions/jvn-engine-hub-<version>.jar
```

For wrapper-driven launches, use the environment variable form:

```bash
JVN_HUB_UI_SCALE=1.35 ./jvn
```

Open **About** from the hub header to see the scale value and detected screen details.

## Distrobox JDK Detection

On Bazzite, the Linux launchers can use a JDK installed in Distrobox. `./jvn` and `./jvnw` inspect available Distrobox containers, prefer common development names such as `jvn`, `java`, `jdk`, and `devbox`, and choose the first container that provides both Java 21+ and `javac`. The selected container is entered before Gradle starts, which lets the Engine Hub and its **Run Editor** button use that container's Java toolchain.

Use an exact container name with:

```bash
JVN_DISTROBOX_CONTAINER=my-devbox ./jvn
```

Force the same detection on a non-Bazzite host with:

```bash
JVN_DISTROBOX=1 ./jvn
```

Disable the automatic handoff with:

```bash
JVN_DISTROBOX=0 ./jvn
```

If `./jvnw editor` fails with `Unable to load glass GTK library`, JavaFX started but the container is missing desktop native libraries. Install the GTK/X11 runtime libraries inside the selected Distrobox container, then retry:

```bash
# Fedora / Bazzite-style container
sudo dnf install gtk3 libXtst libXxf86vm alsa-lib libXrender libXrandr libXinerama libXi

# Debian / Ubuntu-style container
sudo apt install libgtk-3-0 libxtst6 libxxf86vm1 libasound2 libxrender1 libxrandr2 libxinerama1 libxi6
```

## Package A Self-Contained Hub Jar

For local testing or internal distribution, build the packaged Engine Hub jar:

```bash
./scripts/package-engine-hub.sh
```

```powershell
.\scripts\package-engine-hub.ps1
```

Or run the full release pipeline directly:

```bash
./gradlew :hub:packageEngineHubRelease
```

The artifact is written to `build/distributions/jvn-engine-hub-<version>.jar`.

The release task builds the jar, launches that exact artifact in extraction-only mode, verifies the bundled engine workspace and warmed Gradle cache, and writes a neighboring `.sha256` checksum. Java 21 or newer is the only host prerequisite.

Run it with:

```bash
java -jar build/distributions/jvn-engine-hub-<version>.jar
```

The standard artifact includes the warmed Gradle dependency cache so the shipped Hub carries the engine workspace and the dependencies needed by its normal build and launch actions. For quick local packaging tests, build the smaller network-dependent variant:

```bash
./scripts/package-engine-hub.sh --lite
```

```powershell
.\scripts\package-engine-hub.ps1 --lite
```

That artifact is written to `build/distributions/jvn-engine-hub-lite-<version>.jar`. It contains the complete engine workspace but downloads missing Gradle dependencies on demand. The standard release jar is host-platform-sensitive because JavaFX contains native libraries; build and publish it separately on macOS, Windows, and Linux release workers.

This jar contains a clean copy of the engine workspace. On first launch it extracts that workspace under `~/.jvn/engine-hub/<version>/<bundle-hash>/engine` and starts the normal hub against it. The hub can then launch the editor, launcher, Gradle builds, shortcut installers, docs, and updates from the extracted workspace.

To override the extraction cache location:

```bash
java -Djvn.packagedEngineRoot=/path/to/cache -jar build/distributions/jvn-engine-hub-<version>.jar
```

For development, you can also point the packaged launcher at an existing checkout:

```bash
java -jar build/distributions/jvn-engine-hub-<version>.jar --project-root /path/to/Java-Vector-Nexus
```

To verify extraction without opening the GUI:

```bash
java -jar build/distributions/jvn-engine-hub-<version>.jar --extract-only
```

## What It Does

The hub exposes the main workspace actions as buttons:

| Action | What it runs |
|--------|--------------|
| Run Editor | Starts the full JVN editor |
| Run Launcher | Starts the standalone JVN launcher |
| Build All | Builds the workspace |
| Run Tests | Runs the test suite |
| Build Shortcuts | Installs native OS shortcuts for this checkout |
| Update Engine | Runs the guarded engine update flow from `origin/stable` (`git pull --rebase origin stable` with hub preflight and recovery UI) |

The hub shows a compact status strip instead of a terminal-style output panel. Long task output is reduced to simple progress and completion messages.

## Maintenance State

The hub reads launcher maintenance state from the committed `.jvn/maintenance.properties`
file. It currently supports `launcher.maintenance` and `launcher.message`.

The running hub re-reads this file after a successful **Update Engine** action, so the
launcher maintenance state can change without closing and reopening the hub.

When `launcher.maintenance=true`, the **Run Launcher** button stays visible but displays a striped maintenance state. Clicking it shows the configured message instead of launching the standalone launcher. The default committed state is `launcher.maintenance=false`, so the launcher opens normally.

## Update Engine Recovery

**Update Engine** performs a Git preflight before starting the pull/rebase from `origin/stable`. It detects unfinished Git operations, generated build output, and local changes that need user attention before the update can proceed.

Safe Mode changes the update path from a plain update into a guarded recovery flow:

- tracked local changes are autostashed before update work starts
- stale rebase/merge/cherry-pick state is detected and can be aborted from the recovery dialog
- generated build output can be cleaned before retrying
- after a failed update, the hub shows the Git status/recovery commands instead of only reporting `exit 1`

If Safe Mode recovery also fails, copy the dialog details or run `git status --short --branch` in the engine checkout. The checkout should not be manually reset unless the local changes are known to be disposable.

## Developer Mode

Developer Mode is toggled from the Engine Hub. When enabled, hub-launched editor and launcher windows receive `jvn.editor.developerMode=true` and `jvn.launcher.developerMode=true`.

In Developer Mode, both the editor and launcher add a collapsed **Logs** panel at the top of the window. Expand it to scan recent log-like files from the selected project, workspace `.jvn/logs`, workspace `logs`, Gradle daemon logs, build temp logs, and the user log directory. The panel can refresh, copy the visible tail, reveal the selected log, or **Save Logs...** into a folder you choose, such as the Desktop.

When the launcher opens the editor in Developer Mode, it also forwards Developer Mode to the editor and captures that child process output under the workspace `.jvn/logs` folder.

Developer Mode also adds a **DevTools** menu to the editor and launcher menu bars. It includes runtime/JVM diagnostics, manual GC, log-panel refresh, a developer settings file shortcut, a launcher output-capture toggle, an editor JVM heap setting, and **Save Diagnostics Bundle...**. The editor DevTools menu also includes **Auto-write Editor Diagnostics**. The diagnostics bundle writes a timestamped folder and `.zip` containing discovered logs, crash/audit/diagnostic files, Gradle daemon output, launcher logs, a manifest of copied/skipped files, and JVM/runtime memory details. The heap setting is stored in `~/.jvn-editor/devtools.properties` and applies to the next editor launch started from the launcher.

### Tools: verification and profiling

- **Build & Verify > Quick Verification** compiles every module and runs the fast core/runtime test slice.
- **Build & Verify > Prepare Editor Launch** compiles the editor and refreshes its direct-launch metadata without opening it.
- **Performance & Recordings > Record Editor Session (JFR)** launches the editor with Java Flight Recorder. Close that editor to finish the recording.
- **Open Recordings Folder** opens the same location used by direct and Gradle launches: `%LOCALAPPDATA%/JVN Engine Hub/profiles` on Windows, `~/Library/Application Support/JVN Engine Hub/profiles` on macOS, and `$XDG_STATE_HOME/jvn-engine-hub/profiles` (or `~/.local/state/jvn-engine-hub/profiles`) on Linux. `JVN_PROFILE_DIR` overrides the location.
- **Copy Performance Setup** copies the rendering and JVM settings so two recordings can be compared with their configuration recorded.

Commands that start a build, probe, or application are disabled while a Hub-managed task is running. Diagnostics, logs, and JVM memory settings remain available from Tools.

### JVM memory settings

Open **Tools > JVM Memory Settings...** to configure the JVM used by applications
started through Engine Hub. The policy applies on the next launch to the editor,
launcher, embedded preview tools in those processes, and the game runtime. The Hub
passes it through both cache-first and Gradle fallback paths; Windows uses its Gradle
launch path.

Available controls have direct JVM effects:

- **Initial heap** emits `-Xms<n>m`. Leave it automatic unless pre-reserving the heap
  is useful for a known workload.
- **Maximum heap** emits `-Xmx<n>m` and is the precise Java heap ceiling. It does not
  include JavaFX native textures, thread stacks, code cache, or other process memory.
- **Garbage collector** selects the JDK default, G1, ZGC, or Serial collector. G1 is a
  balanced explicit choice, ZGC prioritizes short pauses, and Serial minimizes GC
  overhead for small heaps.
- **Heap dump on OutOfMemoryError** emits the standard heap-dump flags and stores HPROF
  files under `~/.jvn/heap-dumps`.
- **Exit after OutOfMemoryError** emits `-XX:+ExitOnOutOfMemoryError`, avoiding a process
  continuing after memory exhaustion has left it unusable.
- **String deduplication** emits `-XX:+UseStringDeduplication` and requires G1 or ZGC.
- **Extra JVM arguments** accepts advanced options with quote-aware tokenization.
  Options managed by the dedicated controls are rejected here so conflicting heap or
  GC flags cannot silently override the visible values.

Settings are persisted in `~/.jvn/jvm-launch.properties`; the resolved, one-option-per-line
launch file is `~/.jvn/jvm-launch.args`. The latter format preserves argument values that
contain spaces. **Reset to JDK Defaults** removes explicit heap and collector choices.
The older Developer Mode editor-only maximum-heap setting remains compatible, but an
Engine Hub `-Xmx` value takes precedence when both are present.

Use **DevTools > Auto-write Editor Diagnostics** when diagnosing freezes or machine slowdowns. While enabled, the editor appends a heartbeat line every few seconds under the current project or workspace `.jvn/logs` folder, in a file named like `editor-heartbeat-20260713-184500.log`. Each line records uptime, active file, dirty-tab count, CPU text, heap/non-heap/JVM memory, FPS, thread counts, and GC deltas. Because the file is appended continuously, it can still contain useful evidence after a forced restart.

## Install Desktop Shortcuts

Open the hub and click **Build Shortcuts**. It chooses the right installer for the current OS:

| OS | Installed shortcut | Launcher behavior |
|----|--------------------|-------------------|
| macOS | `~/Applications/JVN Engine Hub.app` | Native app launcher, no Terminal window |
| Linux | `~/.local/share/applications/jvn-engine-hub.desktop` | Desktop entry with `Terminal=false` |
| Windows | Start Menu and Desktop `.lnk` shortcuts | Hidden WSH/PowerShell launcher, no command prompt |

You can also run the installers directly from the repository root.

```bash
# macOS
./install-macos-launcher.sh

# Linux
./install-linux-launcher.sh
```

```powershell
# Windows PowerShell
.\install-windows-launcher.ps1

# Skip the desktop shortcut and only add Start Menu entry
.\install-windows-launcher.ps1 -NoDesktop
```

The installers are user-local. They should not be run with `sudo` or Administrator elevation.

Installed shortcuts use the centered app-icon SVG at `docs/assets/images/jvn_app_icon.svg` as the canonical icon artwork. Linux installs the SVG directly, macOS converts that SVG into the app bundle `.icns`, and Windows stores the SVG alongside the hidden launcher and assigns it to the generated shortcuts when supported by the shell.

## Shortcut Logs

Installed shortcuts launch without opening a terminal. If startup fails, check the launcher log:

| OS | Log path |
|----|----------|
| macOS | `~/Library/Logs/JVN Engine Hub/launcher.log` |
| Linux | `~/.local/state/jvn-engine-hub/launcher.log` |
| Windows | `%LOCALAPPDATA%\JVN Engine Hub\Logs\launcher.log` |

Failure dialogs point at these logs. On Linux, the wrapper uses `notify-send`, `zenity`, `kdialog`, or `xmessage` when available.

If **Run Editor** or **Documentation** fails on Linux while starting the JavaFX toolkit, check that the desktop session exposes a display and that the GTK/X11 native libraries JavaFX loads are installed. On Linux Mint/Ubuntu this usually means installing `libgtk-3-0`, `libxtst6`, `libxxf86vm1`, and the system OpenGL/Mesa packages for the machine's GPU.

## macOS Privacy Notes

If the repository is stored under protected folders such as Desktop, Documents, or Downloads, macOS may ask for folder access when the shortcut first launches. Allow access for **JVN Engine Hub** so the app can run `gradlew` from the checkout.

The macOS installer builds a small native launcher when `swiftc` is available. If `swiftc` is not available, it falls back to a shell launcher inside the `.app` bundle. Both paths keep Terminal closed and write to the same log file.

## Reinstall Or Remove

Run **Build Shortcuts** again after moving the repository or after pulling launcher changes. The shortcuts record the checkout path they were generated from.

Remove installed shortcuts manually:

```bash
# macOS
rm -rf "$HOME/Applications/JVN Engine Hub.app"
rm -rf "$HOME/Library/Application Support/JVN Engine Hub"
rm -rf "$HOME/Library/Logs/JVN Engine Hub"

# Linux
rm -f "$HOME/.local/share/applications/jvn-engine-hub.desktop"
rm -rf "$HOME/.local/share/jvn-engine-hub"
rm -f "$HOME/.local/share/icons/hicolor/scalable/apps/jvn-engine-hub.svg"
rm -rf "$HOME/.local/state/jvn-engine-hub"
```

On Windows, remove the Start Menu/Desktop shortcuts and `%LOCALAPPDATA%\JVN Engine Hub`.
