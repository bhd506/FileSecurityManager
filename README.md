# FileSecurityManager

Java 21 file-security runtime with scoped rules, continuous filesystem scanning, quarantine/delete actions, a read-only status API, and separately configured targeted mirroring.

## Modules

- `core` — configuration compiler, conditions/rules/actions, scan runtime, state, quarantine/logging, status HTTP API, and mirror authorization integration.
- `mirror` — standalone file mirroring/synchronization engine used through its public API by core.

## Development run

The checked-in development config assumes the repository-root paths below:

- source: `test-data/`
- quarantine: `quarantine/`
- logs/state: `logs/`
- mirror base: `mirror-data/`
- config: `config/config.yaml`

Run:

```bash
./gradlew test
./gradlew :core:run
```

The `core:run` task is configured to use the repository root as its working directory, so the normal `Main` defaults work consistently.

Explicit application arguments are, in order:

```text
configPath sourceRoot quarantineRoot logRoot mirrorRoot
```

For example:

```bash
./gradlew :core:run --args="config/config.yaml test-data quarantine logs mirror-data"
```

## Runnable distribution

The core uses Gradle's `application` plugin. Build a self-contained application directory (application JAR plus all dependency JARs and launcher scripts) with:

```bash
./gradlew :core:installDist
```

Output:

```text
core/build/install/core/
```

This distribution is the intended input for the upcoming container image; a plain `core.jar` is not a fat JAR.

## Status API

The development config enables the API on loopback at port `8080`.

```text
GET /api/v1/health
GET /api/v1/files/state?path=public/public-file.txt
GET /api/v1/files/states
```

See `core/STATUS_API.md`.

## Mirroring and security authorization

Mirror systems are declared separately under top-level `mirrors`. Declaring one does not automatically grant it files.

A rule explicitly authorizes a scanned file version:

```yaml
onMatch:
  - allowMirror: backup
```

Every source modification revokes previous mirror authorization before that new version is acted on. Core-managed mirrors do not automatically propagate source-side watch events, so an unscanned modification cannot race ahead into a mirror using the previous version's permission. If the new scan authorizes the file again, `addTarget` immediately reconciles the approved version.

Mirror-side changes for active targets are still observed. If a configured mirror writes a change back into the source tree, the normal source watcher sees that modification and scans it like any other external change.

See `core/CONFIG_SYNTAX.md` and `mirror/README.md`.

## Important runtime invariants

- configured RuleSet directory roots are expected to exist and remain part of the static source-tree skeleton;
- source, quarantine, log, and mirror-base roots must not overlap;
- symlinks are not followed by source/mirror tree walkers;
- actions are collected across all applicable RuleSets before execution;
- action phases are `FLAG -> QUARANTINE -> DELETE -> MIRROR`;
- scanning verifies file content did not change before actions run;
- deleted files have no active state; deletion history is append-only in the deletion log;
- `SAFE` means a scan completed and no action changed the recorded state.

## Before Docker

Run the full suite locally:

```bash
./gradlew clean test
./gradlew :core:installDist
```

Then run `:core:run`, exercise the source tree manually, query the status API, and verify expected contents under `quarantine/`, `logs/`, and `mirror-data/`.
