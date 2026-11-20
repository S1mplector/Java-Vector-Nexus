## Install JVN 0.9.0

Download the Engine Hub for your platform. The Windows x64 installer and Apple Silicon disk image include JDK 21 and the Editor workspace. The first Editor launch needs an internet connection to download Gradle dependencies.

| Platform | Download |
| --- | --- |
| Windows x64 | [EXE installer](https://github.com/S1mplector/Java-Vector-Nexus/releases/download/engine-v0.9.0/jvn-engine-hub-0.9.0-windows-x64.exe) |
| macOS on Apple Silicon | [DMG](https://github.com/S1mplector/Java-Vector-Nexus/releases/download/engine-v0.9.0/jvn-engine-hub-0.9.0-macos-arm64.dmg) |
| Linux x64 | [Portable archive](https://github.com/S1mplector/Java-Vector-Nexus/releases/download/engine-v0.9.0/jvn-engine-hub-0.9.0-linux-x64.tar.gz) |

This prerelease also includes portable Windows and macOS archives and a SHA-256 checksum for each download. The Windows installer is unsigned. The macOS app is ad-hoc signed and has not been notarized.

## Playtesting

The Run window now keeps the project, launch profile, session state and live CPU, heap and FPS readings beside the console. Search or filter the Output tab, request a hot reload, or switch to Render inspector to examine frame timing and image caches. Stopping a playtest records who stopped it and keeps the output available for inspection.

## Authoring

- **Scene lighting.** Select and nudge lights in the outliner, adjust the rig against your scene, and compare the graded background with the original. Puppeteer playback preserves the background grade.
- **Story Map.** Right-click an arc and choose **Run from Entry** to playtest its route. Follow incoming and outgoing connections or open the script at its source.
- **Build and Git.** Review packaging checks in Build & Publish and manage project changes in the revised Version Control view.
- **Guided tutorial.** Open a Tutorial Workspace and choose an independent lesson with Pip to try staging, movement and lighting.
- **Editor and Hub.** The Hub has a new compass icon and the Editor has a storyboard-and-pen icon. Open **Help → What's New** to browse this release by topic in the current Editor theme.
