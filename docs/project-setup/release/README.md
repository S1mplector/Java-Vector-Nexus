# JVN Build And Release Docs

Use this hub when the question is "how do we ship this game?"

## Pick The Output First

| Output | Best For | Player Needs Java? | Built From One Machine For Multiple Targets? |
|---|---|---:|---:|
| Portable zip | Internal handoff, debug drops, teams that already control the runtime | Yes | Yes |
| Desktop bundle | Normal desktop shipping, itch uploads, QA builds, external testers | No | Yes |
| Native package | Final installer/app-bundle polish | No | Not locally; use matching host or CI |

## Start Here

- [Build System](build-system.md) — commands, targets, runtime cache, release profiles
- [Deployment & Packaging](deployment.md) — broader packaging strategy, asset bundling, distribution tradeoffs

- [Game Licensing and JVN Attribution](licensing.md) — required in-game credit, packaged notices, and license transition
- [License Acceptance and Receipts](license-acceptance.md) — consent UI, local evidence, verification, and limitations

## Common Routes

### I want to build from the editor

1. Open the game in the editor
2. Use **Build -> Build & Publish...**, **Run -> Build & Publish...**, or the Project Explorer root **Build** button
3. Read [Build System](build-system.md#editor-popup) for the popup behavior and validation rules

### I want a self-contained cross-target build

1. Use [Build System](build-system.md#desktop-bundles)
2. Prefer `./jvnw dist-runtime-all -PjvnGameProject=<dir>`
3. Use `./jvnw runtime-cache` and `./jvnw runtime-cache-clear` to manage cached runtimes

### I want to catch broken assets before shipping

1. Use **Scan Dependencies** in **Build & Publish...** for the grouped editor report, or run `./gradlew validateJvnGameDependencies -PjvnGameProject=<dir> -PjvnShowInfo=true`
2. Fix errors first; they are packaging blockers
3. Review warnings for missing media, broken menu/script/stage/timeline references, and info findings for unused media cleanup

### I want native installers or app bundles

1. Read [Build System](build-system.md#local-native-packaging)
2. Use [Build System](build-system.md#cross-host-native-builds) for matching-host CI builds
3. Configure signing, notarization, and publish hooks under [Release Profiles](build-system.md#release-profiles)

## Practical Rules

- Prefer **Desktop Bundle** as the default release artifact for most teams
- Use **Portable Zip** for internal engineering workflows or when the Java requirement is acceptable
- Use **Native Package** when platform-native installation, signing, or store-ready polish matters enough to justify the host-specific path

## Related Docs

- [Documentation Index](../../INDEX.md)
- [Editor Guide](../../editor/core/editor.md#game-build-publish)
- [New Project Wizard](../onboarding/new-project-wizard.md)

- [What's New in JVN 0.7.0](whats-new-0.7.0.md) — licensing, Settings credit, and release verification

- [What's New in JVN 0.8.0](whats-new-0.8.0.md) — authoring, asset loading, persistence, and live-reload reliability
