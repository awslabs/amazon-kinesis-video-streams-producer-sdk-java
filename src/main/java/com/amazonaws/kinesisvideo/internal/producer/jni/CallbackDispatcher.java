package com.amazonaws.kinesisvideo.internal.producer.jni;

import com.amazonaws.kinesisvideo.common.logging.LogContext;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Runs the producer's application-facing callbacks (StreamCallbacks, StorageCallbacks and ServiceCallbacks) on one
 * thread per client, in the order PIC raised them.
 *
 * PIC raises callbacks on threads that hold native locks: the thread of a putFrame, getStreamData or parseFragmentAck
 * call, a thread delivering a service result, or PIC's timer thread. Application code run there stalls every stream of
 * the client while it runs, and a call back into the producer can close a lock cycle. Here it runs after the native call
 * has moved on, with no SDK or PIC lock held, so it may call any producer API, including stop and free.
 */
final class CallbackDispatcher
{
    interface Callback
    {
        void run() throws Exception;
    }

    private final Logger mLog;
    private final ExecutorService mExecutor;
    private volatile Thread mThread;

    CallbackDispatcher(final Logger log)
    {
        mLog = log;
        mExecutor = Executors.newSingleThreadExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "KinesisVideoProducer-callbacks");
            thread.setDaemon(true);
            mThread = thread;
            return thread;
        });
    }

    /**
     * Queues the callback and returns at once. An exception from the callback is logged, as an exception from a
     * callback run on a native thread used to be.
     */
    void dispatch(final String name, final Callback callback)
    {
        try {
            // With the client and stream tag of the thread that raised it, so its log lines go to the same file
            mExecutor.execute(LogContext.wrap(() -> {
                try {
                    callback.run();
                } catch (final Exception e) {
                    mLog.error("{} callback threw", name, e);
                }
            }));
        } catch (final RejectedExecutionException e) {
            mLog.debug("{} callback dropped: the client has been freed", name);
        }
    }

    boolean isCallbackThread()
    {
        return Thread.currentThread() == mThread;
    }

    /**
     * Callbacks already queued still run; calls they make into a freed client are rejected by its call gate.
     */
    void shutdown()
    {
        mExecutor.shutdown();
    }
}
