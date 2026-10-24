package com.amazonaws.kinesisvideo.internal.producer.jni;

/**
 * Tracks whether the current thread is inside a callback from native code.
 *
 * PIC calls back into Java on threads that may hold the native client lock: the thread of a putFrame, getStreamData or
 * parseFragmentAck call, a thread delivering a service call result, or PIC's own timer thread. A call into the producer
 * from such a thread that needs a Java lock can deadlock with a thread that holds the Java lock and is waiting for the
 * native one, and a call that waits for PIC to make progress, such as stopStreamSync(), stalls every stream of the
 * client. NativeKinesisVideoProducerJni uses this to run those calls after the callback has returned, or to reject them.
 */
final class CallbackThreads
{
    // Counts nesting, since a callback can call into native code that calls back into Java on the same thread
    private static final ThreadLocal<int[]> sDepth = ThreadLocal.withInitial(() -> new int[1]);

    private CallbackThreads()
    {
    }

    static void enter()
    {
        sDepth.get()[0]++;
    }

    static void exit()
    {
        sDepth.get()[0]--;
    }

    static boolean isInsideCallback()
    {
        return sDepth.get()[0] > 0;
    }
}
