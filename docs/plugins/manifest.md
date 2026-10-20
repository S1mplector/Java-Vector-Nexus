# Plugin manifest reference

Every external plugin JAR must contain `/jvn-plugin.json`.

```json
{
  "id": "com.example.dialogue-tools",
  "name": "Dialogue Tools",
  "version": "1.4.0",
  "jvnApi": ">=1.0.0 <2.0.0",
  "entrypoint": "com.example.dialogue.DialoguePlugin",
  "description": "Dialogue validation and reporting tools.",
  "vendor": "Example Studio",
  "capabilities": ["editor.tool", "script.command"],
  "dependencies": [
    {"id": "com.example.foundation", "version": "^2.1.0"}
  ]
}
```

## Fields

| Field | Required | Contract |
| --- | --- | --- |
| `id` | Yes | Globally unique, stable reverse-domain identifier |
| `name` | Yes | Human-readable display name |
| `version` | Yes | Semantic version: `major.minor.patch` |
| `jvnApi` | Yes | Accepted Plugin API version range |
| `entrypoint` | Yes | Public no-argument class implementing `JvnPlugin` |
| `description` | No | Short plain-text summary |
| `vendor` | No | Author or organization |
| `capabilities` | No | Extension families the plugin may access |
| `dependencies` | No | Required plugin IDs and accepted versions |

Supported ranges are exact versions, `*`, `1.x`, `1.2.x`, caret ranges such as `^1.2.0`, and whitespace-separated comparisons such as `>=1.0.0 <2.0.0`.

Caret ranges preserve the first non-zero version component: `^0.7.0` accepts
`0.7.9` but rejects `0.8.0`, and `^0.0.4` accepts only patch `0.0.4`.
Use an explicit comparison range when broader compatibility is intentional.

## Capability identifiers

| Identifier | Registry |
| --- | --- |
| `script.command` | Script commands |
| `editor.tool` | Editor Tools menu actions |
| `asset.importer` | Asset importers |
| `runtime.listener` | Runtime lifecycle listeners |
| `animation.easing` | Named easing curves and their authoring metadata |

Unknown capabilities, malformed JSON, missing required fields, duplicate plugin IDs, incompatible API ranges, and invalid dependencies prevent loading. Diagnostics include a stable code suitable for tooling.
