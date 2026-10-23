package com.amazonaws.kinesisvideo.internal.producer.jni;

import com.amazonaws.kinesisvideo.producer.ProducerException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for {@link StreamManagerShutdownGuard}. A ReentrantLock stands in for the native client lock (the JNI SyncMutex
 * and the
 * PIC client lock) that every native call takes, and that PIC holds while it invokes callbacks on its timer thread.
 */
public class StreamManagerShutdownGuardTest
{
    private static final long CLOSE_TIMEOUT_MS = 10000;
    private static final long HANG_TIMEOUT_MS = 5000;
    private static final long STREAM_A = 1;
    private static final long STREAM_B = 2;

    private final StreamManagerShutdownGuard mGuard = new StreamManagerShutdownGuard(CLOSE_TIMEOUT_MS);
    private final ExecutorService mExecutor = Executors.newCachedThreadPool();

    @Test
    public void freeStreamWaitsForInFlightCallOnThatStream() throws Exception
    {
        mGuard.addStream(STREAM_A);
        final CountDownLatch callStarted = new CountDownLatch(1);
        final CountDownLatch releaseCall = new CountDownLatch(1);

        final Future<?> call = mExecutor.submit(() -> {
            mGuard.callWithStream(STREAM_A, () -> {
                callStarted.countDown();
                await(releaseCall);
            });
            return null;
        });
        assertTrue(callStarted.await(HANG_TIMEOUT_MS, TimeUnit.MILLISECONDS));

        final Future<?> close = mExecutor.submit(() -> {
            mGuard.closeStream(STREAM_A);
            return null;
        });
        Thread.sleep(200);
        assertFalse("closeStream must wait for the in-flight call", close.isDone());

        releaseCall.countDown();
        call.get(HANG_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        close.get(HANG_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    @Test
    public void otherStreamsKeepWorkingWhileOneStreamIsFreed() throws Exception
    {
        mGuard.addStream(STREAM_A);
        mGuard.addStream(STREAM_B);
        final CountDownLatch callStarted = new CountDownLatch(1);
        final CountDownLatch releaseCall = new CountDownLatch(1);

        mExecutor.submit(() -> {
            mGuard.callWithStream(STREAM_A, () -> {
                callStarted.countDown();
                await(releaseCall);
            });
            return null;
        });
        assertTrue(callStarted.await(HANG_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        final Future<?> close = mExecutor.submit(() -> {
            mGuard.closeStream(STREAM_A);
            return null;
        });
        Thread.sleep(200);

        // Stream A is draining; stream B must not notice
        final AtomicBoolean ranOnB = new AtomicBoolean();
        mGuard.callWithStream(STREAM_B, () -> ranOnB.set(true));
        assertTrue(ranOnB.get());
        assertFalse(close.isDone());

        releaseCall.countDown();
        close.get(HANG_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    @Test
    public void callsAfterFreeFailFast() throws Exception
    {
        mGuard.addStream(STREAM_A);
        mGuard.addStream(STREAM_B);

        mGuard.closeStream(STREAM_A);
        mGuard.removeStream(STREAM_A);
        assertRejected(() -> mGuard.callWithStream(STREAM_A, () -> fail("ran on a freed stream")));
        mGuard.callWithStream(STREAM_B, () -> { });

        mGuard.closeClient();
        assertRejected(() -> mGuard.callWithClient(() -> fail("ran on a freed client")));
        assertRejected(() -> mGuard.callWithStream(STREAM_B, () -> fail("ran on a stream of a freed client")));
    }

    /**
     * The scenario that hangs with a blocking read/write lock: a publisher is inside putFrame waiting for the native
     * client lock; PIC's timer thread holds that lock and runs a callback that calls putFragmentMetadata; meanwhile
     * freeStream is waiting for the publisher. With the gate the callback's call is rejected instead of queued, the
     * timer thread releases the native lock, and everything completes.
     */
    @Test
    public void timerCallbackCallingPutFragmentMetadataDuringFreeStreamDoesNotDeadlock() throws Exception
    {
        for (int iteration = 0; iteration < 200; iteration++) {
            final StreamManagerShutdownGuard guard = new StreamManagerShutdownGuard(CLOSE_TIMEOUT_MS);
            guard.addStream(STREAM_A);
            final ReentrantLock nativeClientLock = new ReentrantLock();
            final CountDownLatch timerHoldsNativeLock = new CountDownLatch(1);
            final CountDownLatch publisherInsidePutFrame = new CountDownLatch(1);
            final AtomicReference<Throwable> callbackResult = new AtomicReference<Throwable>();

            final Future<?> timerThread = mExecutor.submit(() -> {
                nativeClientLock.lock();
                try {
                    timerHoldsNativeLock.countDown();
                    await(publisherInsidePutFrame);
                    waitUntil(() -> isStreamClosing(guard));
                    // Callback from PIC's timer thread, native lock held, calling back into the producer
                    try {
                        guard.callWithStream(STREAM_A, () -> { });
                    } catch (final Throwable t) {
                        callbackResult.set(t);
                    }
                } finally {
                    nativeClientLock.unlock();
                }
                return null;
            });
            assertTrue(timerHoldsNativeLock.await(HANG_TIMEOUT_MS, TimeUnit.MILLISECONDS));

            final Future<?> publisher = mExecutor.submit(() -> {
                guard.callWithStream(STREAM_A, () -> {
                    publisherInsidePutFrame.countDown();
                    nativeClientLock.lock();
                    nativeClientLock.unlock();
                });
                return null;
            });
            assertTrue(publisherInsidePutFrame.await(HANG_TIMEOUT_MS, TimeUnit.MILLISECONDS));

            final Future<?> freeStream = mExecutor.submit(() -> {
                guard.closeStream(STREAM_A);
                return null;
            });

            try {
                freeStream.get(HANG_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                publisher.get(HANG_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                timerThread.get(HANG_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (final java.util.concurrent.TimeoutException e) {
                fail("Deadlock between freeStream, putFrame and a timer callback in iteration " + iteration);
            }
            assertTrue("The callback's call must be rejected, not queued",
                    callbackResult.get() instanceof IllegalStateException);
        }
    }

    /**
     * One client, several streams, many publishers, a timer thread that calls back into the producer while holding the
     * native lock, and streams freed one by one followed by the client. Checks that nothing hangs and that no call runs
     * on a stream after freeStream for it has returned.
     */
    @Test
    public void multiStreamStressNoDeadlockAndNoCallAfterFree() throws Exception
    {
        final int streamCount = 8;
        final ReentrantLock nativeClientLock = new ReentrantLock();
        final AtomicBoolean[] streamFreed = new AtomicBoolean[streamCount];
        for (int i = 0; i < streamCount; i++) {
            mGuard.addStream(i);
            streamFreed[i] = new AtomicBoolean();
        }

        final AtomicBoolean stop = new AtomicBoolean();
        final AtomicInteger callsAfterFree = new AtomicInteger();
        final AtomicInteger completedCalls = new AtomicInteger();
        final List<Future<?>> workers = new ArrayList<Future<?>>();

        for (int p = 0; p < 6; p++) {
            final long seed = p;
            workers.add(mExecutor.submit(() -> {
                final Random random = new Random(seed);
                while (!stop.get()) {
                    final int stream = random.nextInt(streamCount);
                    try {
                        mGuard.callWithStream(stream, () -> {
                            nativeClientLock.lock();
                            try {
                                if (streamFreed[stream].get()) {
                                    callsAfterFree.incrementAndGet();
                                }
                                completedCalls.incrementAndGet();
                            } finally {
                                nativeClientLock.unlock();
                            }
                        });
                    } catch (final IllegalStateException expectedAfterFree) {
                        // Rejected: stream or client already closing
                    }
                }
                return null;
            }));
        }

        // PIC timer thread: takes the native lock, then calls back into the producer, like a callback would
        workers.add(mExecutor.submit(() -> {
            final Random random = new Random(42);
            while (!stop.get()) {
                nativeClientLock.lock();
                try {
                    mGuard.callWithStream(random.nextInt(streamCount), () -> { });
                } catch (final IllegalStateException expectedAfterFree) {
                    // Rejected: stream or client already closing
                } finally {
                    nativeClientLock.unlock();
                }
            }
            return null;
        }));

        Thread.sleep(100);
        for (int i = 0; i < streamCount; i++) {
            final int stream = i;
            final Future<?> free = mExecutor.submit(() -> {
                mGuard.closeStream(stream);
                // Only now may the native stream be freed; mark it the way freeKinesisVideoStream would invalidate it
                streamFreed[stream].set(true);
                mGuard.removeStream(stream);
                return null;
            });
            try {
                free.get(HANG_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (final java.util.concurrent.TimeoutException e) {
                fail("freeStream hung for stream " + stream);
            }
            Thread.sleep(20);
        }
        mExecutor.submit(() -> {
            mGuard.closeClient();
            return null;
        }).get(HANG_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        stop.set(true);
        for (final Future<?> worker : workers) {
            worker.get(HANG_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
        assertEquals("A call ran on a stream after it was freed", 0, callsAfterFree.get());
        assertTrue("The publishers should have made progress", completedCalls.get() > 0);
    }

    @Test
    public void freeFromInsideACallOnTheSameClientFailsFastInsteadOfWaitingForItself() throws Exception
    {
        mGuard.addStream(STREAM_A);
        mGuard.addStream(STREAM_B);

        mGuard.callWithStream(STREAM_A, () -> {
            assertRejected(() -> mGuard.closeStream(STREAM_B));
            assertRejected(() -> mGuard.closeClient());
        });

        // Nothing was marked closing by the rejected attempts
        mGuard.callWithStream(STREAM_B, () -> { });
    }

    @Test
    public void closeTimesOutWhenACallNeverReturns() throws Exception
    {
        final StreamManagerShutdownGuard guard = new StreamManagerShutdownGuard(300);
        guard.addStream(STREAM_A);
        final CountDownLatch callStarted = new CountDownLatch(1);
        final CountDownLatch releaseCall = new CountDownLatch(1);
        mExecutor.submit(() -> {
            guard.callWithStream(STREAM_A, () -> {
                callStarted.countDown();
                await(releaseCall);
            });
            return null;
        });
        assertTrue(callStarted.await(HANG_TIMEOUT_MS, TimeUnit.MILLISECONDS));

        try {
            guard.closeStream(STREAM_A);
            fail("closeStream must give up so the caller leaks the stream instead of freeing it");
        } catch (final ProducerException expected) {
            // Expected
        } finally {
            releaseCall.countDown();
        }
    }

    private interface ThrowingRunnable
    {
        void run() throws ProducerException;
    }

    private interface Condition
    {
        boolean isTrue();
    }

    private static void assertRejected(final ThrowingRunnable runnable) throws ProducerException
    {
        try {
            runnable.run();
            fail("Expected IllegalStateException");
        } catch (final IllegalStateException expected) {
            // Expected
        }
    }

    private static void await(final CountDownLatch latch)
    {
        try {
            if (!latch.await(HANG_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("latch timed out");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void waitUntil(final Condition condition)
    {
        final long deadline = System.currentTimeMillis() + HANG_TIMEOUT_MS;
        while (!condition.isTrue()) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("condition not reached");
            }
            Thread.yield();
        }
    }

    private static boolean isStreamClosing(final StreamManagerShutdownGuard guard)
    {
        return guard.isStreamClosing(STREAM_A);
    }
}
