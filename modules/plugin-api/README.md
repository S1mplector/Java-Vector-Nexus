# Plugin API

Dependency-light public API for JVN extensions. Plugin projects should compile against this module and avoid dependencies on `core`, `runtime`, or `editor` unless an extension specification explicitly permits one.

The current API contract is `1.2.0`. See [Plugin documentation](../../docs/plugins/README.md).

Generate the annotated API reference with:

```bash
./gradlew :plugin-api:javadoc
```
