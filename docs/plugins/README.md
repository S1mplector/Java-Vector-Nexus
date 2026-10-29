# Plugins

JVN plugins add scripting commands, editor tools, asset importers, animation easing curves, image effects, and runtime observers through a versioned public API. Plugins are ordinary JAR files with a `jvn-plugin.json` manifest at the archive root.

## Start here

- [Authoring your first plugin](authoring.md)
- [Manifest reference](manifest.md)
- [Extension-point reference](extension-points.md)
- [Animation easing extensions](animation-extensions.md)
- [Image effects and Pikupiku](image-effects.md)
- [Plugin API reference](api-reference.md)
- [Host and lifecycle reference](host-reference.md)
- [Packaging and distribution](distribution.md)
- [Compatibility and security](compatibility-security.md)

The buildable [example plugin](../../modules/plugin-example/README.md) covers commands and tools. The bundled [Pikupiku plugin](../../modules/plugin-pikupiku/README.md) demonstrates image effects and typed editor services.

## Installation locations

| Location | Scope |
| --- | --- |
| `~/.jvn/plugins/*.jar` | Every JVN project for the current user |
| `<project>/plugins/*.jar` | One project, editor and packaged runtime |
| Java `ServiceLoader` provider | First-party or application-bundled plugins |

The editor reloads the host when a project is opened. The game runtime discovers plugins during startup. Restart the relevant application after replacing a user-level plugin JAR.

## Design guarantees

- Plugin identity, versions, dependencies, and capabilities are validated before plugin code initializes.
- Dependencies initialize before dependents and stop after them.
- Registrations belong to the plugin that created them and are removed during failure or shutdown.
- Duplicate extension IDs are rejected deterministically.
- Listener and plugin failures are reported without terminating the engine or editor.
- Plugin code receives narrow registries and context data, not unrestricted engine or editor objects.

The plugin host is an extensibility boundary, not a security sandbox. Only install code you trust.
