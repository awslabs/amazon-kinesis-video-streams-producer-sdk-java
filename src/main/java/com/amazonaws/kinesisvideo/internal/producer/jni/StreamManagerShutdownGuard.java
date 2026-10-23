package com.amazonaws.kinesisvideo.internal.producer.jni;

import com.amazonaws.kinesisvideo.producer.ProducerException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.amazonaws.kinesisvideo.producer.ProducerException.STATUS_INVALID_OPERATION;

/**
 * Lifetime guard for one native Kinesis Video client and its streams.
 *
 * Every call that passes the client handle to native code without holding the producer's sync object goes through
 * {@link #callWithClient} or {@link #callWithStream}. {@link #closeStream} and {@link #closeClient} reject new calls
 * and wait for in-flight ones before the caller frees the native stream or client. A stream being freed only blocks
 * calls on that stream; the other streams of the client keep running.
 */
final class StreamManagerShutdownGuard
{
    /**
     * A native call made while the client (and, for stream calls, the stream) is pinned.
     */
    interface NativeCall
    {
        void call() throws ProducerException;
    }

    private final ShutdownGate mClientGate = new ShutdownGate();
    private final Map<Long, ShutdownGate> mStreamGates = new ConcurrentHashMap<Long, ShutdownGate>();
    private final long mCloseTimeoutMs;

    StreamManagerShutdownGuard(final long closeTimeoutMs)
    {
        mCloseTimeoutMs = closeTimeoutMs;
    }

    void addStream(final long streamHandle)
    {
        mStreamGates.put(streamHandle, new ShutdownGate());
    }

    void removeStream(final long streamHandle)
    {
        mStreamGates.remove(streamHandle);
    }

    /**
     * @throws IllegalStateException if the client is being freed or has been freed
     */
    void callWithClient(final NativeCall nativeCall) throws ProducerException
    {
        if (!mClientGate.tryEnter()) {
            throw new IllegalStateException("The Kinesis Video client has been freed");
        }

        try {
            nativeCall.call();
        } finally {
            mClientGate.exit();
        }
    }

    /**
     * @throws IllegalStateException if the client or the stream is being freed or has been freed
     */
    void callWithStream(final long streamHandle, final NativeCall nativeCall) throws ProducerException
    {
        callWithClient(() -> {
            final ShutdownGate streamGate = mStreamGates.get(streamHandle);
            if (streamGate == null || !streamGate.tryEnter()) {
                throw new IllegalStateException("The Kinesis Video stream has been freed");
            }

            try {
                nativeCall.call();
            } finally {
                streamGate.exit();
            }
        });
    }

    /**
     * Rejects new calls on the stream and waits for in-flight ones. Call while holding no other lock.
     *
     * @throws ProducerException if the calls did not drain in time; the stream must not be freed then
     */
    void closeStream(final long streamHandle) throws ProducerException
    {
        // A thread inside any call on this client may hold the native client lock that the stream's in-flight calls
        // are waiting for, so it must not wait for them
        rejectIfCalledFromInsideClient();

        final ShutdownGate streamGate = mStreamGates.get(streamHandle);
        if (streamGate != null && !streamGate.closeAndDrain(mCloseTimeoutMs)) {
            throw new ProducerException("Timed out waiting for in-flight calls on the stream; not freeing it",
                    STATUS_INVALID_OPERATION);
        }
    }

    /**
     * Rejects new calls on the client and all of its streams and waits for in-flight ones. Call while holding no
     * other lock.
     *
     * @throws ProducerException if the calls did not drain in time; the client must not be freed then
     */
    void closeClient() throws ProducerException
    {
        rejectIfCalledFromInsideClient();

        if (!mClientGate.closeAndDrain(mCloseTimeoutMs)) {
            throw new ProducerException("Timed out waiting for in-flight calls on the client; not freeing it",
                    STATUS_INVALID_OPERATION);
        }
    }

    boolean isStreamClosing(final long streamHandle)
    {
        final ShutdownGate streamGate = mStreamGates.get(streamHandle);
        return streamGate == null || streamGate.isClosing();
    }

    private void rejectIfCalledFromInsideClient()
    {
        if (mClientGate.isEnteredByCurrentThread()) {
            throw new IllegalStateException("Cannot free a Kinesis Video client or stream from inside a call on the "
                    + "same client, for example from one of its callbacks");
        }
    }
}
