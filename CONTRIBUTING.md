# Contributing to Java Vector Nexus

Thank you for improving JVN. Contributions are welcome across the engine,
editor, VNS and JES languages, runtime, packaging, documentation, tests, and
supported extension APIs.

By participating, you agree to follow the
[Code of Conduct](CODE_OF_CONDUCT.md). Report suspected vulnerabilities through
[SECURITY.md](SECURITY.md), not through a public issue.

## Choose the right contribution path

You can open a pull request directly for focused fixes, tests, and documentation
improvements. Start with an issue or proposal before investing in:

- breaking VNS, JES, save-format, project-format, or public API changes;
- new production dependencies or plugin capabilities;
- architectural rewrites or cross-module ownership changes;
- new platform-support claims;
- large editor redesigns or generated-asset changes;
- work that overlaps an active issue or pull request.

Search existing issues and pull requests first. An issue is a place to align on
scope, not a reservation indefinitely; communicate if plans or availability
change.

For questions about using JVN, consult the
[documentation index](docs/INDEX.md) before filing a defect. Reproducible engine
or documentation problems still belong in the issue tracker.

## Prerequisites

- A full Java 21 JDK. Eclipse Temurin 21 is the recommended distribution.
- Git.
- Bash for repository helper scripts. The Gradle wrapper itself remains
  available on Windows.
- Node.js 22 when changing documentation or running the complete contributor
  verification script.

No global Gradle installation is required.

Run the environment preflight before diagnosing build failures:

```bash
./jvnw doctor
```

## Set up a development branch

`stable` is the default integration and release branch. Pull requests normally
target `stable`.

If you are contributing from a fork:

```bash
git clone https://github.com/<your-account>/Java-Vector-Nexus.git
cd Java-Vector-Nexus
git remote add upstream https://github.com/S1mplector/Java-Vector-Nexus.git
git fetch upstream
git switch stable
git pull --ff-only upstream stable
git switch -c fix/short-description
./jvnw doctor
./jvnw compile
```

Contributors with repository write access can create the topic branch from
`origin/stable` instead. Useful prefixes include `feat/`, `fix/`, `docs/`,
`test/`, and `chore/`.

Do not commit credentials, private game content, local IDE state, build output,
Gradle caches, or unrelated media. Keep a pull request focused enough that its
behavior and risk can be reviewed independently.

## Repository map

| Area | Primary location | Focused verification |
| --- | --- | --- |
| Engine, VNS state, assets, saves | `modules/core` | `./gradlew :core:test` |
| JES parsing and execution | `modules/scripting` | `./gradlew :scripting:test` |
| JavaFX rendering and media | `modules/fx` | `./gradlew :fx:test` |
| Runtime and VN/JES interop | `modules/runtime` | `./gradlew :runtime:test` |
| Editor and authoring tools | `modules/editor` | `./gradlew :editor:test` |
| Swing desktop backend | `modules/swing` | `./gradlew :swing:test` |
| Renderer contracts | `modules/render-api` | `./gradlew :render-api:test` |
| Plugin contracts and host | `modules/plugin-api`, `modules/plugin-runtime` | `./gradlew :plugin-api:javadoc :plugin-runtime:test` |
| Hub and launch workflows | `modules/hub` | `./gradlew :hub:test` |
| Audio integration | `modules/audio` | `./gradlew :audio:test` |
| Platform scaffolds | `modules/android-runtime`, `modules/ios-runtime`, `modules/web-runtime` | focused module tests |
| Documentation | `docs`, root Markdown | `node scripts/doc-lint.mjs --strict` |
| Packaging and release | root `build.gradle.kts`, `.github/workflows` | focused packaging or smoke task |

Read the [architecture documentation](docs/architecture/README.md), the
[scripting contract](docs/scripting/spec/README.md), and the
[Plugin API reference](docs/plugins/api-reference.md) before modifying a public
boundary.

Android, iOS, and web are currently compile-tested scaffolds, not supported
player deployment targets. A contribution must not describe a scaffold as
supported until it has an implemented runtime, packaging path, and end-to-end
verification.

## Development and verification

Use the smallest relevant check while iterating:

```bash
./jvnw compile
./gradlew :core:test --tests com.example.ChangedBehaviorTest
./jvnw quick
```

Before requesting review, run:

```bash
./scripts/verify-contribution.sh
```

This is the same primary verification path used by CI. It:

1. tests the `jvnw` bootstrap behavior;
2. runs the full Gradle `ci` task;
3. builds Plugin API Javadocs;
4. runs strict documentation lint;
5. checks patch whitespace.

If the full script cannot run on your platform, run the relevant commands
individually and explain the exception and focused evidence in the pull
request. Do not claim a check passed if it was skipped.

## Engineering expectations

- Preserve module boundaries. `core`, `render-api`, and `plugin-api` must not
  acquire JavaFX or editor dependencies.
- Prefer explicit contracts, immutable snapshots, bounded caches, deterministic
  cleanup, and clear resource ownership.
- Keep public APIs source-compatible where practical. Deprecate and document a
  replacement before removal.
- Use SLF4J for application diagnostics instead of standard output.
- Include actionable error context without logging secrets or complete
  user-authored files unnecessarily.
- Avoid reflection when a supported interface can express the dependency.
- Keep tests deterministic and independent of the network, user-home contents,
  local timezone, locale, and wall-clock timing.
- Preserve user data and existing worktree changes. Avoid destructive Git,
  migration, storage, and packaging operations in tests.
- Match the local style of a file. Broad formatting migrations should be
  separate, discussed changes.

Existing NullAway warnings are not permission to add new avoidable nullness
problems. New code should make ownership and nullability clearer than the code
it replaces.

## Testing requirements

Every behavior fix should include a regression test at the lowest sensible
layer. New features should test their supported path and important failure
paths.

Prefer:

- unit tests for parsing, validation, state, migration, and pure
  transformations;
- malformed-input tests for languages, manifests, archives, and configuration;
- contract tests for render, audio, storage, and plugin boundaries;
- focused integration tests for runtime and editor wiring;
- small, readable checked-in fixtures over opaque generated data.

Do not weaken, disable, broadly exclude, or reorder a failing test merely to
make CI green. If a failure is reproducible and unrelated on current `stable`,
link an issue and include the evidence in the pull request.

UI changes should include screenshots or a short recording when visual review
is material. Accessibility changes should cover keyboard behavior, readable
state, persistence, and renderer/runtime wiring as applicable.

## Contract-sensitive changes

### VNS and JES

Syntax, diagnostics, runtime behavior, documentation, editor support, and VS
Code fixtures form one contract. A language change normally requires:

1. parser or runtime implementation;
2. valid and malformed-script tests;
3. editor diagnostics and highlighting updates;
4. VS Code fixture coverage when relevant;
5. normative specification and changelog updates;
6. a compatibility or deprecation path for breaking behavior.

### Saves, settings, and persistent data

Preserve per-game isolation and explicit storage overrides. Schema changes must
bump the schema version, migrate older data step by step, retain safe defaults,
and test both serialization and restoration. Never use a real user-home
directory in tests.

### Plugins and executable project content

Plugins and inline Java execute with the user's JVM permissions; JVN does not
sandbox them. Do not imply otherwise in APIs, diagnostics, or documentation.
Changes to discovery, class loading, capability exposure, release hooks, or
package verification require explicit trust-boundary review.

### Packaging and platforms

Packaging changes should validate the selected game manifest, target, runtime
modules, launcher behavior, and artifact metadata. Use minimal fixtures and
avoid signing, notarizing, publishing, or downloading large runtimes in ordinary
unit tests.

Platform documentation must distinguish implemented support from compile-only
scaffolding. Claims should match reproducible artifacts and CI coverage.

## Dependencies and generated material

Discuss new runtime dependencies before adding them. A proposal should explain:

- why an existing JDK or repository facility is insufficient;
- license and redistribution compatibility;
- runtime size and platform impact;
- update and vulnerability-management cost;
- whether the dependency crosses a public or plugin boundary.

Commit generated files only when they are reviewed source artifacts required by
the repository. Record the generator and reproduction command. Do not submit
generated code or media whose source, license, or reproducibility is unknown.

Contributors remain responsible for code and text produced with automated or
generative tools. Review it, test it, remove secrets and copied material, and
disclose substantial generated content when that information would help
reviewers assess provenance or risk.

## Documentation

The engine repository is the documentation source of truth. Follow
[Documentation Maintenance](docs/MAINTENANCE.md):

- update normative documentation with behavior changes;
- link new pages from their section index and `docs/INDEX.md`;
- use repository-relative links;
- run strict documentation lint;
- do not update only a website mirror.

Examples must reflect supported syntax and platform behavior. Mark proposals,
scaffolds, and future work as such.

## Commits and pull requests

Use imperative commit subjects that describe the outcome. A scoped prefix is
helpful but not mandatory:

```text
fix(runtime): isolate plugin listener failures
docs(vns): specify inline Java packaging requirements
```

A pull request should include:

- the problem and user impact;
- the chosen solution and important alternatives;
- compatibility, migration, security, and platform impact;
- exact verification commands and results;
- documentation changes;
- screenshots or recordings for visible UI changes;
- follow-up work that is intentionally out of scope.

Complete the pull-request template. Keep the branch current with `stable`, reply
to review questions with evidence, and resolve conversations only after the
underlying concern is addressed or agreement is recorded.

Reviewers may request a smaller change, additional failure-path tests,
documentation, or a migration plan. Maintainers decide when required review and
checks are sufficient and may close inactive or out-of-scope work with an
explanation.

## Licensing and provenance

JVN does not currently require a separate contributor license agreement. By
submitting a contribution, you represent that:

- you created the contribution or have the right to submit it;
- it does not knowingly include incompatible or undisclosed third-party
  material;
- you agree that it may be distributed under the applicable repository
  license.

New contributions to portions governed by the JVN Source-Available Attribution
License are submitted under that license. Contributors retain their copyright;
submission does not assign ownership to S1mplector or other maintainers.
Contributions to separately licensed components follow those components' terms.
Earlier MIT contributions and grants remain subject to their existing terms;
maintainers must obtain any additional authorization required before claiming
exclusive new terms over another contributor's work.

See [LICENSE.md](LICENSE.md) for the current terms and the boundaries for
previously licensed material, separately licensed files, and dependencies.
