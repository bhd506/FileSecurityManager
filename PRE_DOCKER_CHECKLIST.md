# Pre-Docker checklist

The project is structured for containerization, but this checklist should be green before the image is treated as deployable.

- [ ] `./gradlew clean test` succeeds on the deployment branch.
- [ ] `./gradlew :core:installDist` succeeds.
- [ ] Initial scan marks expected development files `SAFE`/`FLAGGED`/`QUARANTINED`.
- [ ] CREATE and MODIFY events rescan files.
- [ ] DELETE events remove active state without initiating a scan.
- [ ] A file changing during its scan is retried and stale actions are discarded.
- [ ] Quarantine moves content out of the source tree and preserves `QUARANTINED` state.
- [ ] Delete-after-quarantine deletes the quarantine copy and records permanent deletion metadata.
- [ ] Status API reports `SAFE` only for actively tracked `SAFE` files.
- [ ] Mirror authorization is absent by default.
- [ ] A rule with `allowMirror` mirrors the approved version.
- [ ] Modifying an approved source file does not propagate the unscanned version before reauthorization.
- [ ] Mirror-side changes in reverse/bidirectional modes are subsequently scanned by core.
- [ ] Persistent bidirectional mirror state survives application restart.
- [ ] Source, quarantine, log, and mirror-base paths are separate writable mount points/directories.
- [ ] Status API exposure/authentication policy is decided before binding it outside loopback.

For Docker, the natural container paths are expected to be `/data`, `/quarantine`, `/logs`, `/mirrors`, and `/etc/file-security/config.yaml`.
