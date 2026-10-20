# What's New in JVN 0.8.0

JVN 0.8.0 improves reliability across authoring, asset loading, persistence, and
live reload. Existing project and save formats remain unchanged.

## Project editing

- Workspace indexing accepts relative project roots, detects folder-link cycles,
  and skips linked directories outside the project. One unreadable folder no
  longer hides the rest of the workspace.
- Creating a text file preserves an existing file's contents and rejects directory
  targets. Existing links cannot redirect creation outside the project.
- Rename accepts a single filename and retains the extension when omitted.
  Directory paths are rejected instead of moving the file unexpectedly.

## Assets and memory

- Packed assets use the ZIP central directory to index and locate entries without
  decompressing preceding assets. Reads stream from the selected entry rather
  than first copying its full contents into memory. Close asset streams after use.
- Pack listings include sorted immediate children and implicit folders. Invalid
  ZIP files now fail during construction instead of appearing empty.
- Image caches retain insertion-time weights and evict before adding a new weight,
  preventing stale accounting and overflow. Reinsert a changed value to update
  its accounted weight.

## Saves and settings

- Saves, settings, and persistent progress write to unique sibling temporary files
  before replacement. Concurrent writers cannot truncate or delete each other's
  temporary files. The last completed replacement wins; this does not merge
  simultaneous edits. Filesystems without atomic moves use a regular replacement.
- Save lists are sorted and exclude directories named with save extensions.
- Non-finite audio volume values retain the current valid setting.

## Live reload and plugins

- Hot reload binds only to `127.0.0.1`. Connections, queued work, read timeouts,
  and command lengths are bounded. Shutdown closes connected clients as well as
  the listener, and startup reports invalid or occupied ports immediately.
- Editor reload requests reject line breaks and invalid ports and use a connection
  timeout. Callback failures do not prevent later valid reload commands.
- Plugin caret ranges now enforce pre-1.0 compatibility: `^0.7.0` excludes `0.8.0`,
  and `^0.0.4` excludes `0.0.5`. See the [manifest reference](../../plugins/manifest.md).

## Distribution and validation

This is a source release requiring JDK 21. Official prebuilt engine binaries are
still planned for the first major release. Use the repository's `./jvnw` wrapper.

The pass adds regression coverage for archive traversal, cache accounting,
concurrent and failed file replacement, workspace links and file operations,
hot-reload sockets and invalid commands, and plugin compatibility boundaries.

The pre-existing native-package CI jobs require a valid license-acceptance receipt
on each runner; fresh Windows and macOS runners currently fail at that gate. This
release does not bypass acceptance or claim native-installer verification.

License terms and acceptance requirements are unchanged. See the
[release guide](README.md) and [acceptance guide](license-acceptance.md).
