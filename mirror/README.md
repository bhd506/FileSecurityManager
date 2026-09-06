# Mirror module

Standalone targeted file mirroring/synchronisation module. The module remains usable without the security core; the core depends only on its public API.

## Public concepts

- `MirrorService` — add/remove/suspend targets, start/stop watchers, and manually reconcile.
- `MirrorConfig` — source root, mirror root, mirror mode, transfer mode, conflict policy, debounce, optional persistent state file, and source-watch policy.
- `MirrorMode`
  - `ONE_TIME`: source-side changes cause reconciliation; mirror-side changes are ignored.
  - `SOURCE_TO_MIRROR`: source is authoritative.
  - `MIRROR_TO_SOURCE`: mirror is authoritative.
  - `BIDIRECTIONAL`: changes on either side propagate; conflicts are detected against the last synchronised fingerprint.
- `TransferMode`
  - `COPY`
  - `MOVE` (restricted to `ONE_TIME`).

## Target lifecycle

`addTarget(path)` activates a target and immediately reconciles it.

`removeTarget(path)` permanently removes the target and its stored sync history.

`suspendTarget(path)` deactivates the target **without** deleting synchronization history. This is useful when an external authority temporarily revokes permission and may later re-enable the same target.

## Safety choices

- Files are copied through a temporary destination and verified before commit.
- A transfer re-checks the source after destination commit; if the source changed in the final transfer window, the operation retries.
- `MOVE` is verified copy followed by source deletion.
- Symbolic-link targets/traversal are rejected.
- Source and mirror roots may not overlap.
- Persistent state files may not live inside either managed root.
- Watch events only request reconciliation; they never directly mean copy/delete.
- Internal filesystem writes are fingerprint-suppressed for a short lifetime, including duplicate watch notifications.
- Per-target reconciliation is serialized through bounded striped locks rather than an unbounded lock map.
- Bidirectional conflicts default to `FAIL`, leaving both sides untouched.
- Persistent bidirectional state can be enabled with `MirrorConfig.Builder.stateFile(...)`.

## Security-core integration

The standalone default is `watchSourceChanges(true)`.

The file-security core deliberately creates mirror services with:

```java
watchSourceChanges(false)
```

This is important: authorization applies to the scanned **version** of a source file. A source modification must not be propagated using authorization granted to the previous version. The core sees the change, suspends the target, rescans it, and only an explicit mirror action re-enables it. Mirror-side watching remains active.

## Typical standalone use

```java
MirrorConfig config = MirrorConfig.builder(sourceRoot, mirrorRoot)
        .mode(MirrorMode.ONE_TIME)
        .transferMode(TransferMode.COPY)
        .build();

try (MirrorService mirrors = MirrorServices.create(config)) {
    mirrors.addTarget(sourceRoot.resolve("folder/file.txt"));
    mirrors.start();
}
```
