# Plugin API reference

This page defines the behavioral contract of Plugin API `1.2.0`. The generated Javadocs provide member-level details; this reference explains how the types work together.

## Package map

| Package | Purpose |
| --- | --- |
| `com.jvn.plugin.api` | Entrypoint, descriptor, context, capabilities, registration, and registries |
| `com.jvn.plugin.api.script` | VNS and future language command calls and results |
| `com.jvn.plugin.api.editor` | Editor actions without an `EditorApp` dependency |
| `com.jvn.plugin.api.asset` | Explicit asset import requests and outcomes |
| `com.jvn.plugin.api.runtime` | Runtime lifecycle events |
| `com.jvn.plugin.api.animation` | Metadata-rich animation easing contributions |
| `com.jvn.plugin.api.image` | Immutable pixels, validated controls, prepared frames, and optional editor service |

Only packages under `com.jvn.plugin.api` form the compatibility contract. `com.jvn.plugin.runtime` is a host implementation and is intended for application embedding and tests, not ordinary plugin logic.

## Entrypoint contract

`JvnPlugin` has three phases:

| Method | Allowed work | Must not assume |
| --- | --- | --- |
| `initialize` | Read immutable configuration, create lightweight state, register extensions | That another plugin has started |
| `start` | Start executors, watchers, or connections | That callbacks occur on a UI thread |
| `stop` | Cancel work and close every owned resource | That initialization completed fully |

The entrypoint requires a public no-argument constructor. One instance is created per host lifecycle. `stop` should tolerate partial state because the host uses it for rollback.

## Context contract

`PluginContext` is scoped to one plugin:

- `descriptor()` is the validated manifest, not plugin-controlled mutable data.
- `environment()` distinguishes editor, runtime, and test hosts.
- `jvnVersion()` describes the application; use `jvnApi` for compatibility decisions.
- `dataDirectory()` is the only host-assigned persistent plugin location.
- `projectDirectory()` may be `null`.
- `configuration()` is an immutable snapshot of `config.properties`.
- `logger()` is namespaced by plugin ID.
- `registries()` enforces declared capabilities and registration ownership.

Do not retain contexts or host service objects in static fields. Doing so can prevent external plugin classloaders from being reclaimed.

## Registration semantics

`ExtensionRegistry.register` normalizes IDs to lowercase. IDs are unique per registry across the host. Registration order is deterministic and visible through `entries()`.

The returned `Registration` is idempotent. Closing it removes that extension early. Regardless of whether a plugin retains the handle, the host removes all registrations owned by the plugin during rollback or shutdown.

Host-level registry views reject registration. Plugin-owned registries are revoked after failure or stop, including previously retained handles. IDs are normalized with `Locale.ROOT`.

Recommended IDs use the plugin namespace:

```text
studio.example.inventory.grant
studio.example.assets.aseprite
studio.example.tools.dialogue-report
```

## Script data rules

`ScriptCommandInvocation.arguments()` contains tokens after VNS quoting and escaping have been processed. A command must validate count, type, range, and project state itself.

Treat `variables()` as read-only. Return assignments in `ScriptCommandResult.variables()`:

```java
return new ScriptCommandResult(
    true,
    createdItemId,
    Map.of("inventory.lastCreated", createdItemId));
```

JVN applies returned assignments after successful execution. Exceptions enter normal interop error reporting. Commands execute synchronously, so blocking I/O will block scene progress.

## Threading matrix

| Callback | Current thread | Guidance |
| --- | --- | --- |
| `initialize`, `start`, `stop` | Host lifecycle caller | Keep bounded; do not assume JavaFX |
| `ScriptCommand.execute` | Runtime script-processing thread | Return quickly; avoid rendering waits |
| `EditorTool.open` | JavaFX application thread | Create UI here; move expensive work off-thread |
| `ImageEffect.prepare` | Host worker | Thread-safe, bounded, interruptible pixel preparation |
| `AssetImporter.importAsset` | Calling importer workflow | Do not assume a specific thread |
| `RuntimeListener` callbacks | Host lifecycle caller | Observe quickly; isolate long work |

Thread assignments beyond those documented are not compatibility guarantees. Plugin-owned background work must use named, bounded executors and stop them during `stop()`.

## Null and collection behavior

- Optional project paths may be `null`.
- Optional text in descriptors is normalized to an empty string.
- Optional collections are normalized to immutable empty collections.
- Registry IDs, implementations, required descriptor fields, and dependency IDs reject `null` or blank values.
- Snapshot collections returned by the API are immutable.

## Error behavior

Plugins may throw checked or runtime exceptions from lifecycle and callback methods. The host converts these into diagnostics and continues where safe. Expected user errors should still be returned as extension-specific diagnostics when the result type supports them; exceptions are for operations that cannot complete.

Never catch `Throwable` in plugin code. The host does so only at the isolation boundary to prevent one extension from terminating the product.

## Contribution API

`PluginContext.contribute()` is the author-focused entry point for metadata-rich extension families. `PluginContributions.animations()` exposes `AnimationContributions.easing(id, definition)`.

`AnimationEasingDefinition` builds immutable easing definitions. `AnimationParameter` describes validated numeric inputs, and `AnimationEasingFrame` supplies clamped progress plus resolved parameter values to the evaluator. See [Animation Easing Extensions](animation-extensions.md) for the full behavioral contract.

API 1.2 adds `PluginContributions.images()` and `ImageEffectContributions.effect(id, effect)`. The default interface methods preserve earlier host implementers; a host without image support throws `UnsupportedOperationException`. See [Image Effects](image-effects.md) for numeric validation, pixel limits, threading, and typed editor services.

## Generating Javadocs

```bash
./gradlew :plugin-api:javadoc
open modules/plugin-api/build/docs/javadoc/index.html
```

Treat Javadoc warnings in `plugin-api` as release blockers because IDE documentation is part of the supported extension surface.
