# Mirror module

Standalone targeted file mirroring/synchronisation module.

## Public concepts

- `MirrorService` - add/remove targets, start/stop watchers, and manually reconcile.
- `MirrorConfig` - source root, mirror root, mirror mode, transfer mode, conflict policy, debounce and optional persistent state file.
- `MirrorMode`
  - `ONE_TIME`: source-side changes cause one reconciliation; mirror-side changes are ignored.
  - `SOURCE_TO_MIRROR`: source is authoritative.
  - `MIRROR_TO_SOURCE`: mirror is authoritative.
  - `BIDIRECTIONAL`: changes on either side propagate; conflicts are detected using the last synchronised fingerprint.
- `TransferMode`
  - `COPY`
  - `MOVE` (intentionally restricted to `ONE_TIME`, because continuous mirroring and destructive move semantics conflict).

## Safety choices

- Files are copied through a temporary destination and verified before commit.
- MOVE is implemented as verified copy followed by source deletion.
- Symbolic-link targets are rejected.
- Source and mirror roots may not overlap.
- Watch events only trigger reconciliation; they never directly mean "copy" or "delete".
- Internal writes are suppressed using expected fingerprints, preventing feedback loops.
- Bidirectional conflicts default to `FAIL`, which leaves both sides untouched.
- Persistent bidirectional state can be enabled with `MirrorConfig.Builder.stateFile(...)`.

## Typical use

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

Targets are identified internally by paths relative to the source root. Adding a target reconciles it immediately.
