package com.amazonaws.kinesisvideo.internal.producer.jni;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts native calls in flight on one client or stream so that the native object is never freed while another
 * thread is still inside it.
 *
 * Callers never wait here: {@link #tryEnter()} either admits the call or, once {@link #closeAndDrain(long)} has
 * started, rejects
 * it immediately. Only the closing thread waits, and only for a bounded time. Because no caller can be blocked by the
 * gate, the gate cannot take part in a lock cycle with the native locks or with Java callbacks that call back into the
 * producer from native threads.
 */
final class ShutdownGate
{
    private final AtomicInteger mInFlightCalls = new AtomicInteger();
    private final ThreadLocal<int[]> mCallsOnThisThread = ThreadLocal.withInitial(() -> new int[1]);
    private volatile boolean mClosing = false;

    /**
     * @return true if the call may proceed; the caller must then call {@link #exit()} exactly once
     */
    boolean tryEnter()
    {
        mInFlightCalls.incrementAndGet();
        // Check after counting the call: closeAndDrain() sets mClosing before it reads the count, so either this sees
        // mClosing or closeAndDrain() sees this call and waits for it
        if (mClosing) {
            release();
            return false;
        }

        mCallsOnThisThread.get()[0]++;
        return true;
    }

    void exit()
    {
        mCallsOnThisThread.get()[0]--;
        release();
    }

    boolean isClosing()
    {
        return mClosing;
    }

    boolean isEnteredByCurrentThread()
    {
        return mCallsOnThisThread.get()[0] > 0;
    }

    /**
     * Rejects new calls and waits for the in-flight ones to return. Must be called while holding no lock that an
     * in-flight call might need. Idempotent: later calls wait for the same drain.
     *
     * @return false if the calls did not drain within the timeout; the caller must then leave the native object alone
     * @throws IllegalStateException if the current thread is itself inside a call on this gate, which would otherwise
     *                               wait for itself
     */
    boolean closeAndDrain(final long timeoutMs)
    {
        if (isEnteredByCurrentThread()) {
            throw new IllegalStateException("Cannot free a Kinesis Video client or stream from inside a call on it, "
                    + "for example from one of its callbacks");
        }

        mClosing = true;
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        synchronized (this) {
            while (mInFlightCalls.get() != 0) {
                final long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    return false;
                }

                try {
                    TimeUnit.NANOSECONDS.timedWait(this, remainingNanos);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        return true;
    }

    private void release()
    {
        if (mInFlightCalls.decrementAndGet() == 0 && mClosing) {
            synchronized (this) {
                notifyAll();
            }
        }
    }
}
