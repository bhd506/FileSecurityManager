# Testing

The test suite is under `src/test/java` and uses JUnit Jupiter.

Run with:

```bash
./gradlew test
```

The included tests cover:

- configuration defaults and validation
- path mapping and root escape rejection
- target-registry behavior
- internal watcher-event suppression
- SHA-256 fingerprints
- verified copy/move/delete transfer behavior
- in-memory and persistent sync state
- one-time copy and move semantics
- source-to-mirror and mirror-to-source modes
- real `WatchService`-driven updates
- bidirectional propagation and deletion
- feedback-loop suppression
- initial and offline bidirectional conflicts
- persistent restart state
- target/root/symlink validation
- idempotent target registration and service lifecycle
