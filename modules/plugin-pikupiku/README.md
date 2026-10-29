# Pikupiku Line Wiggler

Bundled native Java port of the [Pikupiku](https://github.com/S1mplector/pikupiku) procedural contour renderer. Plugin ID `com.jvn.pikupiku`; effect `pikupiku.lineboil`; Plugin API 1.2+.

The plugin contributes a toolkit-independent image effect and an editor tool that requests the host's typed preview service. The editor and JavaFX runtime bundle it through ServiceLoader. It does not launch external processes.

```bash
./gradlew :plugin-pikupiku:test :plugin-pikupiku:jar
```

See [image-effect documentation](../../docs/plugins/image-effects.md) for scripting, presets, controls, export, limits, threading, and integration. The original MIT license is distributed in `META-INF/PIKUPIKU-LICENSE.txt`. The implementation preserves source alpha and prepares deterministic held drawings. GIF encoding is replaced with host PNG-frame export.
