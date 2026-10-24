package com.amazonaws.kinesisvideo.internal.producer.jni;

import org.apache.logging.log4j.Logger;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.producer.FrameFlags;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFragmentAck;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoMetrics;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoStreamMetrics;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.internal.producer.ReadResult;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
import com.amazonaws.kinesisvideo.producer.Time;
import com.amazonaws.kinesisvideo.streaming.DefaultStreamCallbacks;
import com.amazonaws.kinesisvideo.internal.producer.StreamEventMetadata;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of {@link KinesisVideoProducerStream}
 *
 *
 */
public class NativeKinesisVideoProducerStream implements KinesisVideoProducerStream
{
    final static long TIMEOUT_IN_MS = 30000; // 30 seconds
    private class NativeDataInputStream extends InputStream {
        /**
         * Whether the stream has been closed
         */
        private AtomicBoolean mStreamClosed = new AtomicBoolean(false);

        // Set the notification values
        private final Object mMonitor = new Object();
        private boolean mDataAvailable = false;
        private long mAvailableDataSize = 0;
        private final ReadResult mReadResult;
        final long mUploadHandle;

        public NativeDataInputStream(final long uploadHandle) {
            mUploadHandle = uploadHandle;
            mReadResult = new ReadResult();
        }

        @Override
        public int read() throws IOException
        {
            throw new IOException("Can't call byte-by-byte");
        }

        @Override
        public int read(final byte[] b,
                        final int off,
                        final int len)
                throws IOException {
            if (mStreamClosed.get()) {
                mLog.warn("Stream {} with uploadHandle {} has been closed", mStreamInfo.getName(), mUploadHandle);
            }

            // Read from the KinesisVideo Producer
            // NOTE: This implementation is a blocking call and the blocking
            // is handled by simply spin-locking until the data is available.
            int bytesRead = -1;

            while (!mStreamClosed.get()) {
                synchronized (mMonitor) {
                    while (!mDataAvailable && !mStreamClosed.get()) {
                        try {
                            mLog.debug("no data for stream {} with uploadHandle {}, waiting", mStreamInfo.getName(),
                                    mUploadHandle);
                            mMonitor.wait(TIMEOUT_IN_MS);
                        } catch (final InterruptedException e) {
                            mLog.error("Waiting for the data availability with uploadHandle {}", mUploadHandle);
                            mLog.error(e);
                        }
                    }

                    // Clear the availability indicator for now
                    mDataAvailable = false;
                    if (mStreamClosed.get()) {
                        // Indicate the EOS
                        bytesRead = -1;
                        mLog.debug("Being notified to close stream {} with uploadHandle {}",
                                mStreamInfo.getName(), mUploadHandle);
                        return bytesRead;
                    }
                }

                try {
                    mKinesisVideoProducerJni.getStreamData(mStreamHandle, mUploadHandle, b, off, len, mReadResult);
                    bytesRead = mReadResult.getReadBytes();
                    mLog.trace("getStreamData fill {} bytes for stream {} with uploadHandle {}", bytesRead,
                            mStreamInfo.getName(), mUploadHandle);

                    if (mReadResult.isEndOfStream()) {
                        // EOS for current session
                        mLog.info("Received end-of-stream indicator for {}, uploadHandle {}",
                                mStreamInfo.getName(), mUploadHandle);

                        // Set the flag so the stream is not valid any longer
                        mStreamClosed.set(true);

                        if (0 == bytesRead) {
                            // Indicate the EOS
                            bytesRead = -1;
                        }
                    }

                    synchronized (mMonitor) {
                        if (bytesRead != 0) {
                            // Got some bytes - break from the loop.
                            // Make sure we don't await again if we still have some data
                            if (bytesRead != -1 && mAvailableDataSize - bytesRead > 0) {
                                mDataAvailable = true;
                            }

                            break;
                        }
                    }
                } catch (final ProducerException e) {
                    mLog.error("Reader threw an exception", e);
                    throw new IOException(e);
                }
            }

            mLog.debug("Streamed {} bytes for stream {} with uploadHandle {}", bytesRead, mStreamInfo.getName(),
                    mUploadHandle);

            if (-1 == bytesRead) {
                mLog.debug("Closing stream {} with uploadHandle {}", mStreamInfo.getName(), mUploadHandle);
            }

            return bytesRead;
        }

        @Override
        public int read(final byte[] b)
                throws IOException
        {
            return read(b, 0, b.length);
        }

        @Override
        public void close()
                throws IOException
        {
            // Set the stream to stopped state only once
            if (mStreamClosed.compareAndSet(false, true)) {
                // Notify the awaiting thread
                notifyReaderThread(0, 0);
            }
        }

        protected void notifyReaderThread(final long duration, final long availableSize) {
            // Unblock the awaiting reading code block
            synchronized (mMonitor) {
                mAvailableDataSize = availableSize;
                mLog.debug("Data availability notification. Upload handle: {}, Size: {}, Duration {} ",
                        mUploadHandle, availableSize, duration);

                mDataAvailable = true;

                mMonitor.notify();
            }
        }

        protected void endOfReaderThread() {
            // Unblock the awaiting reading code block
            synchronized (mMonitor) {
                mDataAvailable = true;
                mStreamClosed.set(true);
                mMonitor.notify();
            }
        }
    }

    private static final long STREAM_READY_POLLING_INTERVAL_MS = 100;
    private static final int SERVICE_CALL_RESULT_OK = 200;
    private final NativeKinesisVideoProducerJni mKinesisVideoProducerJni;
    private volatile long mStreamHandle;
    private final StreamInfo mStreamInfo;
    private final DeviceInfo mDeviceInfo;
    private final StreamCallbacks mStreamCallbacks;
    private final CountDownLatch mReadyLatch;
    private final CountDownLatch mStoppedLatch;
    private final Logger mLog;
    private final KinesisVideoStreamMetrics mStreamMetrics;
    private final Map<Long, NativeDataInputStream> mInputStreamMap;
    private final AtomicBoolean mDataAvailableQueued = new AtomicBoolean();

    /**
     * Completes when the stream has closed, with mStoppedLatch
     */
    private final CompletableFuture<Void> mClosed = new CompletableFuture<Void>();

    /**
     * The callbacks the application implements itself. Those it leaves to DefaultStreamCallbacks are no-ops and are not
     * handed to the callback thread at all, which matters for streamDataAvailable: PIC raises it for every frame.
     */
    private final Set<String> mAppCallbacks;
    private final AtomicReference<long[]> mLatestDataAvailable = new AtomicReference<long[]>();

    public NativeKinesisVideoProducerStream(final @Nonnull NativeKinesisVideoProducerJni kinesisVideoProducerJni,
                                            final @Nonnull StreamInfo streamInfo,
                                            final long streamHandle,
                                            final @Nonnull Logger log,
                                            final @Nullable StreamCallbacks streamCallbacks, 
                                            final @Nullable DeviceInfo deviceInfo) {
        mKinesisVideoProducerJni = Preconditions.checkNotNull(kinesisVideoProducerJni);
        mStreamInfo = Preconditions.checkNotNull(streamInfo);
        Preconditions.checkState(streamHandle != NativeKinesisVideoProducerJni.INVALID_STREAM_HANDLE_VALUE);
        mStreamHandle = streamHandle;
        mStreamCallbacks = streamCallbacks;
        mAppCallbacks = implementedCallbacks(streamCallbacks);
        mReadyLatch = new CountDownLatch(1);
        mStoppedLatch = new CountDownLatch(1);
        mLog = Preconditions.checkNotNull(log);
        mStreamMetrics = new KinesisVideoStreamMetrics();
        // Concurrent: PutMedia tasks add to it while putFrame and getStreamData threads look streams up
        mInputStreamMap = new ConcurrentHashMap<Long, NativeDataInputStream>();
        mDeviceInfo = deviceInfo;
    }

    @Override
    public InputStream getDataStream(final long uploadHandle) throws ProducerException {
        final NativeDataInputStream inputStream = new NativeDataInputStream(uploadHandle);
        mInputStreamMap.put(uploadHandle, inputStream);
        return inputStream;
    }

    @Override
    public void getStreamData(final long uploadHandle,
                              @Nonnull final byte[] fillBuffer,
                              final int offset,
                              final int length,
                              @Nonnull final ReadResult readResult) throws ProducerException {
        Preconditions.checkState(mStreamHandle != NativeKinesisVideoProducerJni.INVALID_STREAM_HANDLE_VALUE);
        mKinesisVideoProducerJni.getStreamData(mStreamHandle, uploadHandle, fillBuffer, offset, length, readResult);
    }

    @Override
    public void putFrame(@Nonnull final KinesisVideoFrame kinesisVideoFrame) throws ProducerException {
        Preconditions.checkNotNull(kinesisVideoFrame);
        Preconditions.checkState(mStreamHandle != NativeKinesisVideoProducerJni.INVALID_STREAM_HANDLE_VALUE);

        mKinesisVideoProducerJni.putFrame(mStreamHandle, kinesisVideoFrame);
    }

    @Override
    public void putFragmentMetadata(@Nonnull final String metadataName, @Nonnull final String metadataValue, boolean persistent)
            throws ProducerException {
        Preconditions.checkNotNull(metadataName);
        Preconditions.checkNotNull(metadataValue);
        Preconditions.checkState(mStreamHandle != NativeKinesisVideoProducerJni.INVALID_STREAM_HANDLE_VALUE);

        mKinesisVideoProducerJni.putFragmentMetadata(mStreamHandle, metadataName, metadataValue, persistent);
    }

    @Override
    public void putEventMetadata(final int event, @Nullable StreamEventMetadata streamEventMetadata)
            throws ProducerException {
        Preconditions.checkArgument(event >= 0, "Event type cannot be negative, got: %s", event);
        Preconditions.checkState(mStreamHandle != NativeKinesisVideoProducerJni.INVALID_STREAM_HANDLE_VALUE, 
                "Stream handle is invalid - stream may have been freed or not initialized");

        mKinesisVideoProducerJni.putEventMetadata(mStreamHandle, event, streamEventMetadata);
    }

    @Override
    public void fragmentAck(final long uploadHandle, final @Nonnull KinesisVideoFragmentAck kinesisVideoFragmentAck) throws ProducerException {
        Preconditions.checkNotNull(kinesisVideoFragmentAck);
        Preconditions.checkState(mStreamHandle != NativeKinesisVideoProducerJni.INVALID_STREAM_HANDLE_VALUE);

        mKinesisVideoProducerJni.fragmentAck(mStreamHandle, uploadHandle, kinesisVideoFragmentAck);
    }

    @Override
    public void parseFragmentAck(final long uploadHandle, final @Nonnull String kinesisVideoFragmentAck) throws ProducerException {
        Preconditions.checkNotNull(kinesisVideoFragmentAck);
        Preconditions.checkState(mStreamHandle != NativeKinesisVideoProducerJni.INVALID_STREAM_HANDLE_VALUE);

        mKinesisVideoProducerJni.parseFragmentAck(mStreamHandle, uploadHandle, kinesisVideoFragmentAck);
    }

    @Override
    public void streamFormatChanged(@Nullable final byte[] codecPrivateData, int trackId)
            throws ProducerException {

        Preconditions.checkState(mStreamHandle != NativeKinesisVideoProducerJni.INVALID_STREAM_HANDLE_VALUE);
        mKinesisVideoProducerJni.streamFormatChanged(mStreamHandle, codecPrivateData, trackId);
    }

    @Override
    public void streamTerminated(final long uploadHandle, final int statusCode) throws ProducerException {
        Preconditions.checkState(mStreamHandle != NativeKinesisVideoProducerJni.INVALID_STREAM_HANDLE_VALUE);
        mKinesisVideoProducerJni.streamTerminated(mStreamHandle, uploadHandle, statusCode);
    }

    @Override
    public void stopStream() throws ProducerException {
        // Stop the underlying native stream
        mKinesisVideoProducerJni.stopStream(mStreamHandle);
    }

    @Override
    public CompletableFuture<Void> stopStreamAsync()
    {
        try {
            stopStream();
        } catch (final ProducerException e) {
            final CompletableFuture<Void> failed = new CompletableFuture<Void>();
            failed.completeExceptionally(e);
            return failed;
        }

        // A dependent future, so that callers cannot complete or cancel the stream's own
        return mClosed.thenApply(Function.identity());
    }

    @Override
    public void stopStreamSync() throws ProducerException {
        if (mKinesisVideoProducerJni.isCallbackThread()) {
            mLog.warn("stopStreamSync() for {} called from an application callback: the client's later callbacks wait "
                    + "until the stream has closed. Use stopStreamAsync() to return at once.", mStreamInfo.getName());
        }

        if (CallbackThreads.isInsideCallback()) {
            // This thread can hold the native client lock that the stream needs to finish, so waiting here would
            // only time out and stall the client's other streams meanwhile
            mLog.warn("stopStreamSync() for {} called from inside a callback; stopping without waiting",
                    mStreamInfo.getName());
            stopStream();
            return;
        }

        // Kick off stream stop
        stopStream();

        // Await for the data to finish.
        try {
            awaitStopped();
        } catch (final ProducerException e) {
            Exception storedException = null;
            for (NativeDataInputStream inputStream : mInputStreamMap.values()) {
                try {
                    inputStream.close();
                } catch (final IOException e1) {
                    storedException = e1;
                }
            }
            mLog.error("Stopping stream threw an exception. Force stopping the input stream.", e);
            if (storedException != null) {
                throw new ProducerException(storedException);
            }
        } finally {
            for (final InputStream stream : mInputStreamMap.values()) {
                try {
                    stream.close();
                } catch (final IOException e) {
                    mLog.error(e);
                }
            }
        }
    }

    @Nonnull
    @Override
    public KinesisVideoStreamMetrics getMetrics() throws ProducerException {
        Preconditions.checkState(mStreamHandle != NativeKinesisVideoProducerJni.INVALID_STREAM_HANDLE_VALUE);
        mKinesisVideoProducerJni.getStreamMetrics(mStreamHandle, mStreamMetrics);
        return mStreamMetrics;
    }

    @Override
    public String getStreamName() {
        return mStreamInfo.getName();
    }

    @Override
    public long getStreamHandle() {
        return mStreamHandle;
    }

    /**
     * Runs an application callback on the client's callback thread, with no native lock held
     */
    private void toApp(final String name, final CallbackDispatcher.Callback callback)
    {
        if (mAppCallbacks.contains(name)) {
            mKinesisVideoProducerJni.dispatchCallback(name, callback);
        }
    }

    private static Set<String> implementedCallbacks(@Nullable final StreamCallbacks callbacks)
    {
        final Set<String> implemented = new HashSet<String>();
        if (callbacks == null) {
            return implemented;
        }

        for (final Method callback : StreamCallbacks.class.getMethods()) {
            try {
                final Method implementation = callbacks.getClass().getMethod(callback.getName(), callback.getParameterTypes());
                if (implementation.getDeclaringClass() != DefaultStreamCallbacks.class) {
                    implemented.add(callback.getName());
                }
            } catch (final NoSuchMethodException e) {
                implemented.add(callback.getName());
            }
        }
        return implemented;
    }

    @Override
    public void streamFreed() throws ProducerException {
        mLog.debug("{} Stream Freed.", mStreamInfo.getSummary());
        streamClosed(ReadResult.INVALID_UPLOAD_HANDLE_VALUE);
        mStreamHandle = NativeKinesisVideoProducerJni.INVALID_STREAM_HANDLE_VALUE;
    }

    @Override
    public void streamUnderflowReport() throws ProducerException
    {
        toApp("streamUnderflowReport", () -> mStreamCallbacks.streamUnderflowReport());
    }

    @Override
    public void streamLatencyPressure(final long duration) throws ProducerException
    {
        toApp("streamLatencyPressure", () -> mStreamCallbacks.streamLatencyPressure(duration));
    }

    @Override
    public void streamConnectionStale(final long lastAckDuration) throws ProducerException
    {
        toApp("streamConnectionStale", () -> mStreamCallbacks.streamConnectionStale(lastAckDuration));
    }

    @Override
    public void fragmentAckReceived(final long uploadHandle,
                                    @Nonnull final KinesisVideoFragmentAck fragmentAck) throws ProducerException
    {
        toApp("fragmentAckReceived", () -> mStreamCallbacks.fragmentAckReceived(uploadHandle, fragmentAck));
    }

    @Override
    public void droppedFrameReport(final long frameTimecode) throws ProducerException
    {
        toApp("droppedFrameReport", () -> mStreamCallbacks.droppedFrameReport(frameTimecode));
    }

    @Override
    public void droppedFragmentReport(final long fragmentTimecode) throws ProducerException
    {
        toApp("droppedFragmentReport", () -> mStreamCallbacks.droppedFragmentReport(fragmentTimecode));
    }

    @Override
    public void streamErrorReport(final long uploadHandle, final long fragmentTimecode, final long statusCode) throws ProducerException
    {
        toApp("streamErrorReport", () -> mStreamCallbacks.streamErrorReport(uploadHandle, fragmentTimecode, statusCode));
    }

    @Override
    public void streamDataAvailable(final long uploadHandle, final long duration, final long availableSize) throws ProducerException
    {
        // Unblock the reader thread
        final NativeDataInputStream inputStreamToNotify = mInputStreamMap.get(uploadHandle);
        if (inputStreamToNotify != null) {
            inputStreamToNotify.notifyReaderThread(duration, availableSize);
        } else {
            mLog.warn("Data available notification for non-existing uploadHandle {}", uploadHandle);
        }

        // One per frame: coalesce, so a slow application callback cannot grow the queue without bound
        if (mAppCallbacks.contains("streamDataAvailable")) {
            mLatestDataAvailable.set(new long[] {uploadHandle, duration, availableSize});
            if (mDataAvailableQueued.compareAndSet(false, true)) {
                mKinesisVideoProducerJni.dispatchCallback("streamDataAvailable", () -> {
                    mDataAvailableQueued.set(false);
                    final long[] latest = mLatestDataAvailable.get();
                    mStreamCallbacks.streamDataAvailable(latest[0], latest[1], latest[2]);
                });
            }
        }
    }

    @Override
    public void streamReady() throws ProducerException
    {
        mLog.debug("Stream {} is ready", mStreamInfo.getName());
        // Release the ready latch
        mReadyLatch.countDown();

        toApp("streamReady", () -> mStreamCallbacks.streamReady());
    }

    @Override
    public void streamClosed(final long uploadHandle) throws ProducerException
    {
        mLog.debug("Stream {} is closed", mStreamInfo.getName());

        if (uploadHandle == ReadResult.INVALID_UPLOAD_HANDLE_VALUE) {
            for (final Map.Entry<Long, NativeDataInputStream> stream : mInputStreamMap.entrySet()) {
                try {
                    stream.getValue().close();
                    final long closedUploadHandle = stream.getKey();
                    toApp("streamClosed", () -> mStreamCallbacks.streamClosed(closedUploadHandle));
                } catch (final IOException e) {
                    mLog.error("stream close failed with exception ", e);
                }
            }

            // Release the stopped latch
            mStoppedLatch.countDown();
            mClosed.complete(null);
        } else {
            // A session can close without an input stream, for example when PutMedia failed before reading from it.
            // The latch must still be released, or stopStreamSync() waits for its whole timeout.
            final NativeDataInputStream inputStream = mInputStreamMap.get(uploadHandle);
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (final IOException e) {
                    mLog.error("stream close failed with exception ", e);
                }
            }

            // Release the stopped latch
            mStoppedLatch.countDown();
            mClosed.complete(null);

            toApp("streamClosed", () -> mStreamCallbacks.streamClosed(uploadHandle));
        }
    }

    @Override
    public void bufferDurationOverflowPressure(final long remainDuration) throws ProducerException {
        toApp("bufferDurationOverflowPressure", () -> mStreamCallbacks.bufferDurationOverflowPressure(remainDuration));
    }

    /**
     * Create a new connection to continue sending data in current buffer
     * @throws ProducerException
     */
    @Override
    public void resetConnection() throws ProducerException {
        mLog.debug("Current connection of stream {} is being reset", mStreamInfo.getName());

        streamTerminated(ReadResult.INVALID_UPLOAD_HANDLE_VALUE, SERVICE_CALL_RESULT_OK);
    }

    @Deprecated
    public void awaitReady() throws ProducerException
    {
        // Block until client is ready or it times out.
        try {
            if (!mReadyLatch.await(READY_TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS)) {
                throw new ProducerException(this.mStreamInfo.getSummary() + ": KinesisVideo producer stream creation time out", ProducerException.STATUS_OPERATION_TIMED_OUT);
            }
        } catch (final InterruptedException e) {
            throw new ProducerException(e);
        }
    }

    @SuppressWarnings("ConstantConditions")
    public void awaitReady(@Nonnull final Set<Long> readyStreamHandles) throws ProducerException
    {
        Preconditions.checkArgument(readyStreamHandles != null, "readyStreamHandles cannot be null");

        // Block until stream is ready or it times out.
        // Check if the stream is ready in the native side (fencepost)
        if (readyStreamHandles.remove(this.mStreamHandle)) {
            mLog.info("Stream {} (handle=0x{}) is already ready", this.mStreamInfo.getSummary(), Long.toHexString(this.mStreamHandle));
            mReadyLatch.countDown();
            toApp("streamReady", () -> mStreamCallbacks.streamReady());
            return;
        }

        final long endTime = System.currentTimeMillis() + READY_TIMEOUT_IN_MILLISECONDS;

        try {
            while (!mReadyLatch.await(STREAM_READY_POLLING_INTERVAL_MS, TimeUnit.MILLISECONDS)) {
                if (System.currentTimeMillis() > endTime) {
                    // Best-effort abort of a half-constructed stream; ignore failures.
                    throw new ProducerException(
                            this.mStreamInfo.getSummary() + ": KinesisVideo producer stream creation time out",
                            ProducerException.STATUS_OPERATION_TIMED_OUT);
                }
                if (readyStreamHandles.remove(this.mStreamHandle)) {
                    mLog.info("Stream {} (handle=0x{}) became already ready", this.mStreamInfo.getSummary(), Long.toHexString(this.mStreamHandle));
                    mReadyLatch.countDown();
                    toApp("streamReady", () -> mStreamCallbacks.streamReady());
                    return;
                }
            }
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ProducerException(ie);
        }
    }

    public void awaitStopped() throws ProducerException
    {
        // Block until client is stopped or it times out.
        try {
            // Default time unit precision supplied should be in milliseconds
            long timeout = mDeviceInfo.getClientInfo().getStopStreamTimeout() / Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
            if (timeout == 0) {
                timeout = STOPPED_TIMEOUT_IN_MILLISECONDS;
            }
            if (!mStoppedLatch.await(timeout, TimeUnit.MILLISECONDS)) {
                throw new ProducerException("KinesisVideo producer stream stopping time out", ProducerException.STATUS_OPERATION_TIMED_OUT);
            }

        } catch (final InterruptedException e) {
            throw new ProducerException(e);
        }
    }

    @Nonnull
    @Override
    public StreamInfo getStreamInfo() {
        return mStreamInfo;
    }
}
