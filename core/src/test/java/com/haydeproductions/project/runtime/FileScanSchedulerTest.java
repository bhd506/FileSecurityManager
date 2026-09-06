package com.haydeproductions.project.runtime;

import com.haydeproductions.project.scan.FileChangedDuringScanException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FileScanSchedulerTest {

    @TempDir
    Path tempDir;

    @Test
    void repeatedEventsAreDebouncedIntoOneScan() throws Exception {
        AtomicInteger scans = new AtomicInteger();

        try (FileScanScheduler scheduler = new FileScanScheduler(
                path -> scans.incrementAndGet(),
                Duration.ofMillis(75),
                1
        )) {
            Path file = tempDir.resolve("file.txt");

            for (int i = 0; i < 10; i++) {
                scheduler.schedule(file);
            }

            assertTrue(scheduler.awaitIdle(Duration.ofSeconds(3)));
            assertEquals(1, scans.get());
        }
    }

    @Test
    void samePathNeverScansConcurrentlyAndDirtyRunGetsOneFollowUp()
            throws Exception {

        AtomicInteger scans = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        try (FileScanScheduler scheduler = new FileScanScheduler(
                path -> {
                    int scanNumber = scans.incrementAndGet();
                    int nowActive = active.incrementAndGet();
                    maximumActive.accumulateAndGet(nowActive, Math::max);

                    try {
                        if (scanNumber == 1) {
                            firstStarted.countDown();
                            assertTrue(
                                    releaseFirst.await(3, TimeUnit.SECONDS)
                            );
                        }
                    } finally {
                        active.decrementAndGet();
                    }
                },
                Duration.ofMillis(25),
                2
        )) {
            Path file = tempDir.resolve("file.txt");
            scheduler.schedule(file);

            assertTrue(firstStarted.await(3, TimeUnit.SECONDS));

            scheduler.schedule(file);
            scheduler.schedule(file);
            scheduler.schedule(file);

            releaseFirst.countDown();

            assertTrue(scheduler.awaitIdle(Duration.ofSeconds(4)));
            assertEquals(2, scans.get());
            assertEquals(1, maximumActive.get());
        }
    }

    @Test
    void differentPathsCanScanInParallel() throws Exception {
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        try (FileScanScheduler scheduler = new FileScanScheduler(
                path -> {
                    bothStarted.countDown();
                    assertTrue(release.await(3, TimeUnit.SECONDS));
                },
                Duration.ZERO,
                2
        )) {
            scheduler.schedule(tempDir.resolve("a.txt"));
            scheduler.schedule(tempDir.resolve("b.txt"));

            assertTrue(bothStarted.await(3, TimeUnit.SECONDS));
            release.countDown();
            assertTrue(scheduler.awaitIdle(Duration.ofSeconds(3)));
        }
    }

    @Test
    void invalidatedScanIsAutomaticallyRetried() throws Exception {
        AtomicInteger scans = new AtomicInteger();
        Path file = tempDir.resolve("file.txt");

        try (FileScanScheduler scheduler = new FileScanScheduler(
                path -> {
                    if (scans.incrementAndGet() == 1) {
                        throw new FileChangedDuringScanException(path);
                    }
                },
                Duration.ofMillis(20),
                1
        )) {
            scheduler.schedule(file);

            assertTrue(scheduler.awaitIdle(Duration.ofSeconds(3)));
            assertEquals(2, scans.get());
        }
    }

    @Test
    void ordinaryFailureIsReportedAndNotAutomaticallyRetried()
            throws Exception {

        AtomicInteger scans = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        AtomicReference<Exception> failure = new AtomicReference<>();

        try (FileScanScheduler scheduler = new FileScanScheduler(
                path -> {
                    scans.incrementAndGet();
                    throw new Exception("boom");
                },
                Duration.ZERO,
                1,
                (path, exception) -> {
                    failures.incrementAndGet();
                    failure.set(exception);
                }
        )) {
            scheduler.schedule(tempDir.resolve("file.txt"));

            assertTrue(scheduler.awaitIdle(Duration.ofSeconds(3)));
            assertEquals(1, scans.get());
            assertEquals(1, failures.get());
            assertEquals("boom", failure.get().getMessage());
        }
    }

    @Test
    void scheduleNormalizesEquivalentPaths() throws Exception {
        AtomicInteger scans = new AtomicInteger();

        try (FileScanScheduler scheduler = new FileScanScheduler(
                path -> scans.incrementAndGet(),
                Duration.ofMillis(50),
                1
        )) {
            scheduler.schedule(tempDir.resolve("folder/../file.txt"));
            scheduler.schedule(tempDir.resolve("file.txt"));

            assertTrue(scheduler.awaitIdle(Duration.ofSeconds(3)));
            assertEquals(1, scans.get());
        }
    }

    @Test
    void closedSchedulerRejectsNewWork() {
        FileScanScheduler scheduler = new FileScanScheduler(
                path -> { },
                Duration.ZERO,
                1
        );

        scheduler.close();

        assertThrows(
                IllegalStateException.class,
                () -> scheduler.schedule(tempDir.resolve("file.txt"))
        );
    }

    @Test
    void constructorRejectsNegativeDebounce() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FileScanScheduler(
                        path -> { },
                        Duration.ofMillis(-1),
                        1
                )
        );
    }

    @Test
    void constructorRejectsNonPositiveWorkerCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FileScanScheduler(
                        path -> { },
                        Duration.ZERO,
                        0
                )
        );
    }
}
