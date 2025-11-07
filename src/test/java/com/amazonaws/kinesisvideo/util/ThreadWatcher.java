package com.amazonaws.kinesisvideo.util;

import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.fail;

public final class ThreadWatcher implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(ThreadWatcher.class);

    private final List<String> threadsToIgnore;
    private final List<String> threadsBefore;
    private final long timeoutMs;
    private final long pollingIntervalMs;

    /**
     * Captures the baseline threads. When {@link #close()} is called, it waits up to {@code timeout}
     * to verify the threads have returned to the baseline.
     *
     * @param timeout         How long to wait for the threads to return to baseline after {@link #close()}
     * @param pollingInterval How often to check the threads have returned to baseline during {@link #close()}
     * @param threadsToIgnore Which threads to ignore in the comparison
     */
    @SuppressWarnings("ConstantConditions")
    public ThreadWatcher(@Nonnull final Duration timeout,
                         @Nonnull final Duration pollingInterval,
                         @Nullable final List<String> threadsToIgnore) {
        Preconditions.checkArgument(timeout != null, "timeout cannot be null");
        Preconditions.checkArgument(pollingInterval != null, "pollingInterval cannot be null");

        Stream<String> threadsBefore = Thread.getAllStackTraces().keySet()
                .stream()
                .map(Thread::getName);

        if (threadsToIgnore != null) {
            threadsBefore = threadsBefore.filter(name -> !threadsToIgnore.contains(name));
            this.threadsToIgnore = new ArrayList<>(threadsToIgnore);
        } else {
            this.threadsToIgnore = Collections.emptyList();
        }

        this.threadsBefore = threadsBefore
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

        log.info("Threads before: {}", this.threadsBefore);

        this.timeoutMs = timeout.toMillis();
        this.pollingIntervalMs = pollingInterval.toMillis();
    }

    @Override
    public void close() throws Exception {
        verityThreadShutdown(this.threadsBefore, this.timeoutMs, this.pollingIntervalMs);
    }

    /**
     * Waits up to the timeout for the JVM to reclaim the threads.
     * Use case: When shutting down a thread pool and the awaitTermination returns success,
     * there is a brief window where the JVM hasn't reclaimed the thread and the Thread.getAllStackTraces()
     * will not immediately return.
     *
     * @throws AssertionError if the timeout is exceeded
     */
    @SuppressWarnings("ConstantConditions")
    public void verityThreadShutdown(@Nonnull final List<String> expectedThreads,
                                     final long timeoutMs,
                                     final long pollingIntervalMs) {
        Preconditions.checkArgument(expectedThreads != null, "expectedThreads cannot be null");

        for (long i = 0; i < timeoutMs; i += pollingIntervalMs) {
            final List<String> threadsNow = Thread.getAllStackTraces().keySet()
                    .stream()
                    .map(Thread::getName)
                    .filter(name -> !this.threadsToIgnore.contains(name))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());

            log.info("Cleanup iteration {}/{} ms - Current thread count: {}, Expected: {}",
                    i, timeoutMs, threadsNow.size(), expectedThreads.size());

            if (threadsNow.equals(expectedThreads)) {
                break; // Threads are cleaned up
            } else {
                // Threads are not cleaned up yet
                final List<String> extraThreads = new ArrayList<>(threadsNow);
                extraThreads.removeAll(this.threadsBefore);
                if (!extraThreads.isEmpty()) {
                    log.warn("extra threads are still running: {}", extraThreads);
                }
            }

            if (i + pollingIntervalMs >= timeoutMs) {
                //time has exceeded shutdown timeout
                log.error("Expected threads: {}", this.threadsBefore);
                log.error("Current threads: {}", threadsNow);
                fail("Timeout waiting for threads to be cleaned up properly");
            }

            try {
                Thread.sleep(pollingIntervalMs);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
