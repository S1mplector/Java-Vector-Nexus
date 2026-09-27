# Build System

JVN's game packaging has three output layers:

- **Portable zip**: cross-target zip with runtime jars and JavaFX jars. Players still need Java 21 installed.
- **Desktop bundle**: self-contained zip with a prebuilt target runtime plus target-specific JavaFX natives. Players do not need Java installed.
- **Native package**: app image or installer built by `jpackage`. Locally this is current-host only. Players do not need Java installed.

Portable packaging supports `type=vn` and `type=jes` game manifests. It rejects `type=gradle` manifests and the engine workspace itself because those describe development run commands, not a distributable game.

Need the shipping-oriented overview first? Start at [JVN Build And Release Docs](README.md).

## Choosing A Format

| Format | Use It When | Player Needs Java? | Cross-Target From One Machine? |
|---|---|---:|---:|
| **Portable zip** | You want the lightest packaging path and Java 21 on the player machine is acceptable | Yes | Yes |
| **Desktop bundle** | You want a normal shippable desktop artifact without asking players to install Java | No | Yes |
| **Native package** | You need installer/app-bundle polish, host integration, signing, or store-ready packaging | No | Not locally |

## Editor Popup

Open a game project in the editor, then use one of:

```text
Build -> Build & Publish...
File -> Project -> Build & Publish...
Run -> Build & Publish...
Project Explorer -> Build
```

The popup reads the current project's `jvn.project`, lets you set the release name/version, target, format, native package type, and release profile, then launches the matching Gradle task in the run console. Use **Ship Build** when you want the editor to build the selected package plan and write release manifests in one step. Use **Scan Dependencies** when you want an in-view shipping report for missing media, bad script/config links, broken menu targets, bad stage/timeline references, unused media, and packaging blockers. The report groups errors, warnings, and cleanup notes, supports per-finding copy/open actions, and still provides a console scan button for CI-style output. The view also reveals output folders, copies CLI/publish notes, shows checksum availability for completed artifacts, and can run the selected release profile.

If a game does not have release configuration yet, **Create Release Config** writes a safe cross-platform starter profile at `config/release/jvn-release.properties`, selects it, and reveals it for authoring. Existing configurations remain untouched.

Before enabling build actions, the popup validates:

- the selected project is not the JVN engine workspace
- `jvn.project` is readable and uses `type=vn` or `type=jes`
- VN `entryVns` or JES `entry` resolves to an existing script
- the selected target is supported
- the output folder is writable

Warnings are shown for easy-to-miss cases such as a project folder with leading/trailing whitespace, a script-only package without an `assets/` folder, or missing release-profile config for native packaging.

The popup is described from the editor side in [Editor Guide](../../editor/core/editor.md#game-build-publish).

## CLI Commands

All CLI builds require `-PjvnGameProject=<dir>`. The path is used exactly as provided, so quote paths with spaces or intentional trailing whitespace.

For engine development, the root build now exposes a few short feedback-loop commands:

| Command | Purpose |
|---------|---------|
| `./jvnw compile` / `./gradlew compileAll` | Compile every module without running tests |
| `./jvnw quick` / `./gradlew quickCheck` | Compile every module and run the fast core/runtime verification slice |
| `./jvnw build-info` / `./gradlew printJvnBuildEnvironment` | Print active Java, Gradle, JavaFX, build-dir, and module configuration |
| `./gradlew buildSystemHelp` | Print the recommended build commands from Gradle |

Use `./jvnw quick` while iterating and `./jvnw ci` before sharing a larger change.

| Command | Output |
|---------|--------|
| `./jvnw dist -PjvnGameProject=<dir>` | Game archive for the current host OS/arch |
| `./jvnw dist-all -PjvnGameProject=<dir>` | Game archives for every supported target |
| `./jvnw dist-runtime -PjvnGameProject=<dir>` | Self-contained desktop bundle for the current target |
| `./jvnw dist-runtime-all -PjvnGameProject=<dir>` | Self-contained desktop bundles for every supported target |
| `./jvnw dist-preflight -PjvnGameProject=<dir>` | Validate the selected package plan and write JSON/Markdown build reports |
| `./jvnw runtime-cache` | Print cached prebuilt desktop runtimes |
| `./jvnw runtime-cache-clear` | Clear cached prebuilt desktop runtimes |
| `./jvnw native -PjvnGameProject=<dir>` | Current-host native package using the host default type |
| `./jvnw release-native -PjvnGameProject=<dir>` | Current-host native package plus release-profile hooks |
| `./gradlew assembleJvnGameRelease -PjvnGameProject=<dir>` | Build the selected package plan and write release manifests |
| `./gradlew writeJvnGameReleaseManifest -PjvnGameProject=<dir>` | Write release manifests for already-built selected artifacts |
| `./gradlew assembleJvnGamePortableCurrent -PjvnGameProject=<dir>` | Same as `./jvnw dist` |
| `./gradlew assembleJvnGamePortable -PjvnGameProject=<dir>` | Same as `./jvnw dist-all` |
| `./gradlew assembleJvnGameBundledRuntimeCurrent -PjvnGameProject=<dir>` | Self-contained desktop bundle for the current target |
| `./gradlew assembleJvnGameBundledRuntime -PjvnGameProject=<dir>` | Self-contained desktop bundles for every supported target |
| `./gradlew packageJvnGameNativeCurrent -PjvnGameProject=<dir>` | Current-host native package |
| `./gradlew releaseJvnGameNativeCurrent -PjvnGameProject=<dir>` | Native package + signing/notarization/publish hooks |
| `./gradlew validateJvnGameProject -PjvnGameProject=<dir>` | Validate and print selected game metadata |
| `./gradlew validateJvnGameDependencies -PjvnGameProject=<dir>` | Scan missing assets/audio/fonts/scripts, unused media, bad stage presets, menu links, timeline references, and packaging blockers |
| `./gradlew printJvnGamePortableTargets` | List supported platform targets |
| `./gradlew printJvnBundledRuntimeCache` | Print cached prebuilt desktop runtimes |
| `./gradlew clearJvnBundledRuntimeCache` | Clear cached prebuilt desktop runtimes |
| `./gradlew printJvnGameNativePackageTypes` | List supported native package types for this host |
| `./gradlew printJvnGameReleaseProfiles` | List available release profiles from the selected game project |

Optional properties:

| Property | Purpose |
|----------|---------|
| `-PjvnGameName=<name>` | Override the archive/launcher display name |
| `-PjvnGameVersion=<version>` | Override release version used in archive names |
| `-PjvnNativeVersion=<version>` | Override version used for native packages when installer rules require numeric versions |
| `-PjvnNativePackageType=<type>` | Override current-host native package type (`app-image`, `dmg`, `pkg`, `exe`, `msi`, `deb`, `rpm`) |
| `-PjvnBundledRuntimeImageType=<jre|jdk>` | Prefer a specific prebuilt runtime image type for desktop bundles |
| `-PjvnBundledRuntimeVendor=<vendor>` | Override the Adoptium vendor segment used for desktop-bundle runtime downloads (default: `eclipse`) |
| `-PjvnRefreshBundledRuntime=true` | Force a fresh download of the prebuilt runtime archive instead of reusing the local cache |
| `-PjvnReleaseProfile=<name>` | Select release profile from `jvn-release.properties` |
| `-PjvnDependencyProject=<dir>` | Override the project scanned by `validateJvnGameDependencies`; defaults to `jvnGameProject`, `jvnProject`, then the workspace root |
| `-PjvnShowInfo=true` | Include informational dependency-scan findings such as unused media assets |
| `-PjvnFailOnWarning=true` | Make `validateJvnGameDependencies` fail when warnings are present |
| `-PjvnBuildDir=<dir>` | Override the workspace build root; relative paths resolve from the JVN workspace root |
| `-PjvnBuildOutputDir=<dir>` | Override only the packaged game artifact output folder; reports and generated intermediates stay under `jvnBuildDir` |
| `-PjvnAllowEngineWorkspacePackage=true` | Advanced escape hatch for intentionally packaging the engine workspace |

Archives are written to:

```text
<jvnBuildDir>/distributions/games/   (default: build/distributions/games/)
```

If `-PjvnBuildOutputDir=<dir>` is set, packaged game artifacts and their `.sha256` sidecars are written there instead.

Each completed game artifact also receives a sibling `.sha256` file. The checksum sidecar is written after the package has been opened and checked for required launch content, so a missing checksum is a useful sign that the build did not finish its packaging verification step.

### Controlling Shipped Files

Add a `.jvnignore` file at the game project root to exclude authoring-only content from every desktop package. Use one Gradle/Ant-style path pattern per line; blank lines and lines beginning with `#` are ignored.

```text
# Source art and local production notes
source-art/**
notes/**
**/*.kra
**/*.psd
```

JVN always excludes version-control metadata, IDE state, build outputs, saves, logs, temporary files, and `.jvnignore` itself. Authored patterns are added to those safe defaults and apply consistently to portable zips, desktop bundles, and native packages.

`dist-preflight` writes both machine-readable and human-readable reports:

```text
<jvnBuildDir>/reports/jvn-game-build/build-plan.json
<jvnBuildDir>/reports/jvn-game-build/build-plan.md
```

`assembleJvnGameRelease` writes the final shipping manifest pair:

```text
<jvnBuildDir>/reports/jvn-game-release/release-manifest.json
<jvnBuildDir>/reports/jvn-game-release/release-manifest.md
```

## Asset And Dependency Validation

Run the deep project scan before packaging a release candidate:

```bash
./gradlew validateJvnGameDependencies -PjvnGameProject=/path/to/game -PjvnShowInfo=true
```

The scan reports:

- missing images, audio, fonts, scripts, stage presets, and config references
- broken menu links and `RUN_SCRIPT` targets
- bad VNS stage preset usage such as `[stage id]` without a matching declaration/export
- bad Puppeteer/JES timeline calls such as `[call jes_timeline name]` without `scripts/timelines/name.jes`
- parse errors in VNS scripts and JES timeline files
- packaging blockers such as missing `jvn.project`, unsupported game type, or missing entry script
- unused conventional media under `assets/`, `images/`, `audio/`, `fonts/`, `ui/`, `video/`, and `game/`

Errors fail the task. Warnings are printed but do not fail unless `-PjvnFailOnWarning=true` is set. Informational cleanup findings, including unused media, are printed when `-PjvnShowInfo=true` is set.

## Portable Targets

| Target | JavaFX classifier | Per-target Gradle task |
|--------|-------------------|------------------------|
| `windows-x64` | `win` | `assembleJvnGamePortableWindowsX64` |
| `linux-x64` | `linux` | `assembleJvnGamePortableLinuxX64` |
| `macos-x64` | `mac` | `assembleJvnGamePortableMacosX64` |
| `macos-aarch64` | `mac-aarch64` | `assembleJvnGamePortableMacosAarch64` |

Linux aarch64 is not part of the current supported target set. Add the target definition and release validation before publishing Linux aarch64 bundles.

## Desktop Bundles

Desktop bundles are the Ren'Py-like packaging path:

- buildable for every supported desktop target from one machine
- no Java install required on the player machine
- game files stay visible as a normal packaged folder
- launch scripts stay platform-native for the selected target

Internally a desktop bundle stages:

- the JVN runtime jars
- the game project files
- target-specific JavaFX native jars
- a downloaded prebuilt Eclipse Temurin runtime for the selected target

The downloaded runtime archive is SHA-256 verified against the metadata returned by the Adoptium API before JVN reuses it.

That means JVN no longer depends on local `jlink` for cross-target self-contained desktop builds.

Downloaded runtime archives and extracted caches live under `<jvnBuildDir>/downloads/jvnRuntime/` and `<jvnBuildDir>/vendor-runtimes/` (default: `build/...`). Use `./jvnw runtime-cache` to inspect them and `./jvnw runtime-cache-clear` to wipe them.

## Local Native Packaging

Local `jpackage` runs are still **host-bound**:

- build mac packages on macOS
- build Windows packages on Windows
- build Linux packages on Linux

Supported native package types by host:

- macOS: `app-image`, `dmg`, `pkg`
- Windows: `app-image`, `exe`, `msi`
- Linux: `app-image`, `deb`, `rpm`

## Cross-Host Native Builds

True cross-host native packaging is handled through the reusable CI matrix workflow at [native-builds.yml](../../../.github/workflows/native-builds.yml).

Treat GitHub here as the current execution backend, not as a packaging requirement. The real requirement is matching host builders for the native targets.

That workflow fans out to matching GitHub-hosted runners for:

- `linux-x64` on `ubuntu-24.04`
- `windows-x64` on `windows-2022`
- `macos-x64` on `macos-15-intel`
- `macos-aarch64` on `macos-14`

It checks out the engine, checks out the selected game repository, validates the chosen `jvn.project`, then builds:

- desktop bundles when requested
- native `app-image` bundles
- native installer packages for the target host (`deb`/`rpm`, `exe`/`msi`, `dmg`/`pkg`)

Example caller workflow inside a game repository:

```yaml
name: Release Native Builds

on:
  workflow_dispatch:
  push:
    tags:
      - "v*"

jobs:
  native:
    uses: S1mplector/Java-Vector-Nexus/.github/workflows/native-builds.yml@main
    with:
      game_project_path: .
      release_profile: release
      run_release_profile_hooks: true
    secrets: inherit
```

For monorepos, set `game_project_path` to the game folder. For manual dispatches in the engine repository, set `game_repository` to the external game repo you want to package. Use `game_version` when you need to override the version coming from `jvn.project`.

## Archive Layout

Each archive expands into a self-contained game folder:

```text
<game>-<version>-<target>/
|-- bin/
|   `-- <game-launcher>
|-- game/
|   |-- jvn.project
|   |-- scripts/
|   |-- assets/
|   `-- config/
|-- lib/
|   |-- *.jar
|   `-- javafx/
|       `-- javafx native jars for the target
|-- BUILD-METADATA.txt
`-- README.txt
```

Windows archives use `.bat` launchers. Linux and macOS archives use executable shell scripts.

The launchers keep JavaFX jars on the module path and the rest of JVN on the classpath. They fail early with a clear message when Java is missing, Java is older than 21, or the bundled `game/jvn.project` file is missing.

Runtime configuration such as `entryVns`, `width`, `height`, `runtime.ui`, `runtime.audio`, and `runtime.locale` is read from the bundled `game/jvn.project`. VN builds also pass the resolved entry script to `--script`; JES builds pass the configured `entry` file to `--jes`.

## Desktop Bundle Layout

Desktop bundles expand into this shape:

```text
<game>-<version>-<target>-runtime/
|-- bin/
|-- game/
|-- lib/
|-- runtime/
|-- BUILD-METADATA.txt
`-- README.txt
```

The `runtime/` directory contains the downloaded prebuilt runtime archive contents for the selected target. The launcher points directly at that runtime, keeps JavaFX jars on the module path, and launches the bundled game with the same `--assets`, `--script`, or `--jes` wiring used by the portable packages.

## Native Packages

Native packaging uses `jpackage` with:

- input jars from the JVN runtime modules
- the bundled `game/` directory added as app content
- a prebuilt runtime image from `createJvnGameRuntimeImageCurrent`
- `com.jvn.runtime.GamePackageLauncher` as the package entrypoint

Current-host native app versions must satisfy installer rules, so JVN derives a numeric native version automatically when the game version is a development label such as `0.1.2-SNAPSHOT`. Override it with `-PjvnNativeVersion=...` when needed.

## Release Profiles

Release profiles live in one of these locations inside the game project:

```text
config/release/jvn-release.properties
config/release/release.properties
release/jvn-release.properties
jvn-release.properties
```

Example:

```properties
defaultProfile=release

profile.release.vendor=Studio Name
profile.release.description=Was I Write?
profile.release.aboutUrl=https://example.com/was-i-write
profile.release.icon=packaging/icon.icns
profile.release.mac.packageIdentifier=com.example.wasiwrite
profile.release.mac.sign=true
profile.release.mac.signingIdentity=Developer ID Application: Studio Name
profile.release.mac.notarize=true
profile.release.mac.notarytoolProfile=AC_PASSWORD
profile.release.publish.command.1=butler push "{artifact}" user/game:mac
```

Supported first-pass profile categories:

- package metadata: `vendor`, `description`, `aboutUrl`, `copyright`, `licenseFile`, `icon`
- runtime tuning: `runtime.modules`
- mac packaging: `mac.packageIdentifier`, `mac.packageName`, `mac.sign`, `mac.signingIdentity`, `mac.keychain`, `mac.notarize`, `mac.notarytoolProfile`, `mac.entitlements`, `mac.appStore`
- Windows packaging/signing: `win.dirChooser`, `win.menu`, `win.shortcut`, `win.perUserInstall`, `win.console`, `win.sign`, `win.signtool`, `win.certificateFile`, `win.certificatePasswordEnv`, `win.subjectName`, `win.timestampUrl`
- Linux packaging: `linux.shortcut`, `linux.packageName`, `linux.appCategory`, `linux.debMaintainer`, `linux.rpmLicenseType`
- publish hooks: `publish.command.<n>`

## Release Workflow

1. Open the game in the editor and run it once from the editor.
2. Open `Build & Publish...`.
3. Set the release version.
4. Build the current target for smoke testing.
5. Build desktop bundles locally for the targets you want to ship.
6. Smoke-test at least the current host locally, then use matching machines or CI for native installers when you need them.
7. Run the release profile when signing, notarization, or publish commands are configured.
8. Upload the generated artifacts from `<jvnBuildDir>/distributions/games/` (default: `build/distributions/games/`) or from the CI artifact set.

CLI equivalent:

```bash
./jvnw dist-runtime \
  -PjvnGameProject=/absolute/path/to/my-game \
  -PjvnGameVersion=1.0.0
```

## Remaining Gaps

What is still not solved:

- local `jpackage` remains host-bound; cross-host native packaging depends on CI or self-hosted matching runners
- Linux aarch64 remains unsupported
- platform-store specific publishing still needs profile command templates and external tooling
- mac signing/notarization and Windows signing require real local credentials/tools; JVN now provides hooks, not credentials


## JVN 0.7.0 final release validation

Developer-tool startup and game export require acceptance of the current license
and terms. Review through **Help → License & Terms**, or use
`:licensing:run --args=show` followed by the explicit CLI acceptance command.
CI must provision its own valid local receipt; no auto-accept or bypass flag exists.

Every official runtime supplies visible Settings access and **Made with [JVN SVG
logo] (actual engine version)**. In JavaFX the credit has reserved space outside
the project canvas; Swing provides engine Settings with the same bundled SVG.
There is no project preference to disable or replace these controls.

Packaging adds `JVN-EXPORT-CONTRACT.properties` with the policy, engine version,
logo/license/notice hashes, and expected runtime jar hashes. Final verification
opens the actual archive or extracted native payload and checks those values and
required runtime classes. It rejects stale/missing/modified jars, replaced logos,
missing notices, ambiguous paths, and unsafe or case-colliding ZIP entries.
Verification precedes checksums, release manifests, update/store bundles, and
publish hooks. Cached archive tasks cannot bypass the check.

Native verification requires platform extraction tools: `hdiutil` for DMG,
`pkgutil` for PKG, `msiexec` for MSI, WiX `dark` plus `msiexec` for EXE,
`dpkg-deb` for DEB, and `bsdtar` for RPM. Missing tools or uninspectable payloads
fail validation. Use `app-image` ZIPs when installer extraction tools are unavailable.
These platform paths require testing on their respective hosts.

These controls enforce official builds, not an unmodifiable source-code trust
boundary. A modified engine remains subject to the license's credit requirements.
