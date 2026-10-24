package com.amazonaws.kinesisvideo.internal.producer.jni;

import com.amazonaws.kinesisvideo.util.CalledByNativeCode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.internal.producer.*;
import com.amazonaws.kinesisvideo.producer.AuthCallbacks;
import com.amazonaws.kinesisvideo.producer.AuthInfo;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFragmentAck;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoMetrics;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StorageCallbacks;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;
import com.amazonaws.kinesisvideo.producer.StreamDescription;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Tag;
import com.amazonaws.kinesisvideo.streaming.DefaultStreamCallbacks;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static com.amazonaws.kinesisvideo.producer.ProducerException.STATUS_INVALID_OPERATION;
import static com.amazonaws.kinesisvideo.producer.ProducerException.STATUS_SUCCESS;

/**
 * JNI interface to the native Producer SDK library wrapper
 *
 *
 */
@ThreadSafe
@SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
public class NativeKinesisVideoProducerJni implements KinesisVideoProducer {
    /**
     * Specifying invalid client handle value
     */
    public static final long INVALID_CLIENT_HANDLE_VALUE = 0;

    /**
     * Specifying invalid stream handle value
     */
    public static final long INVALID_STREAM_HANDLE_VALUE = 0;

    /**
     * Name of the native library
     */
    public static final String PRODUCER_NATIVE_LIBRARY_NAME = "KinesisVideoProducerJNI";

    /**
     * The expected library version.
     */
    public static final String EXPECTED_LIBRARY_VERSION = "1.14.0";

    /**
     * How long free() and freeStream() wait for in-flight native calls before giving up and leaving the native object
     * allocated
     */
    private static final long FREE_TIMEOUT_MS = 30000;

    /**
     * The manifest handle will be set after call to parse()
     */
    private volatile long mClientHandle = INVALID_CLIENT_HANDLE_VALUE;

    /**
     * Will store whether the library has been initialized
     */
    private boolean mLibraryInitialized = false;

    /**
     * Indicates whether the producer object is ready for creating streams, etc..
     */
    private volatile boolean mIsReady = false;

    /**
     * Taken only by create() and free(), so two of them never run at once. No other code takes it, so holding it across
     * the native call cannot be part of a lock cycle.
     */
    private final Object mLifecycleLock = new Object();

    /**
     * Leaf lock: makes "look the stream up, or park its ready event" in streamReady atomic with "register the stream" in
     * createStream. Held for a few map operations only, never across a native call or a callback.
     */
    private final Object mStreamRegistryLock = new Object();

    /**
     * Released once create() has stored the client handle, so that client results that arrive first can wait for it
     */
    private final CountDownLatch mClientCreated = new CountDownLatch(1);

    /**
     * PIC's SERVICE_CALL_UNKNOWN. A service call that fails on the callback thread is reported to PIC with it, and PIC
     * retries it like any other failed service call.
     */
    private static final int SERVICE_CALL_UNKNOWN = 10006;

    /**
     * Token for a failed call. Not null: the JNI copies the token array without checking for null.
     */
    private static final byte[] NO_TOKEN = new byte[0];

    /**
     * Runs StreamCallbacks, StorageCallbacks and ServiceCallbacks on the client's callback thread, with no SDK or PIC
     * lock held. Serializes them, as mCallbackSyncObject used to, without holding a lock while they run.
     */
    private final CallbackDispatcher mCallbacks;

    /**
     * Keeps free() and freeStream() from releasing the native client or a stream while another thread is still inside a
     * native call on it. Every native call except create and free goes through it.
     */
    private final StreamManagerShutdownGuard mShutdownGuard = new StreamManagerShutdownGuard(FREE_TIMEOUT_MS);

    /**
     * Streams whose createStream() is still running, by name. PIC rejects duplicate stream names, so the name identifies
     * the stream before its handle is known. Replaces the single in-progress StreamInfo/StreamCallbacks pair, so several
     * streams can be created at once.
     */
    private final Map<String, PendingStream> mPendingStreams = new ConcurrentHashMap<String, PendingStream>();

    /**
     * Streams freeStream() is freeing right now, so that two concurrent calls free a stream only once
     */
    private final Set<Long> mFreeingStreams = ConcurrentHashMap.newKeySet();

    private static final class PendingStream
    {
        final StreamInfo mStreamInfo;
        final StreamCallbacks mStreamCallbacks;
        volatile long mStreamHandle = INVALID_STREAM_HANDLE_VALUE;

        PendingStream(final StreamInfo streamInfo, final StreamCallbacks streamCallbacks)
        {
            mStreamInfo = streamInfo;
            mStreamCallbacks = streamCallbacks != null ? streamCallbacks : new DefaultStreamCallbacks();
        }
    }

    /**
     * Runs, in order, the calls that were made from inside a callback from native code, after the callback returned.
     * See {@link CallbackThreads}.
     */
    private final ExecutorService mDeferredCalls = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "KinesisVideoProducer-deferred-calls");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Keeps the mapping between the stream handle and the Kinesis Video stream object. Concurrent because native
     * callback threads read it without a lock.
     */
    private final Map<Long, KinesisVideoProducerStream> mKinesisVideoHandleMap = new ConcurrentHashMap<Long, KinesisVideoProducerStream>();

    /**
     * Keeps track of handles that are ready in the Native side, but the Java object creation is still in progress for.
     */
    private final Set<Long> mReadyStreamHandles = ConcurrentHashMap.newKeySet();

    /**
     * Callbacks for integration with the device auth subsystem.
     */
    private final AuthCallbacks mAuthCallbacks;

    /**
     * Callbacks for device storage integration
     */
    private final StorageCallbacks mStorageCallbacks;

    /**
     * Callbacks for service calls
     */
    private final ServiceCallbacks mServiceCallbacks;

    /**
     * Logger interface
     */
    private final Logger mLog;

    /**
     * Helper class for loading the native libraries
     */
    private final NativeLibraryLoader mLibraryLoader;

    /**
     * Latch which will release when the client is ready
     */
    private final CountDownLatch mReadyLatch;

    /**
     * Used to store metrics. The metrics can be updated.
     */
    private final KinesisVideoMetrics mKinesisVideoMetrics;

    /**
     * Used to store device info
     */
    private DeviceInfo mDeviceInfo;

    /**
     * If using the custom allocators from PIC.
     */
    private boolean mIsTrackingMallocs;

    /**
     * Public constructor.
     * @param authCallbacks Authentication callbacks
     * @param storageCallbacks Storage callbacks
     * @param serviceCallbacks Service call callbacks
     * @throws ProducerException
     */
    public NativeKinesisVideoProducerJni(final @Nonnull AuthCallbacks authCallbacks,
                                         final @Nonnull StorageCallbacks storageCallbacks,
                                         final @Nonnull ServiceCallbacks serviceCallbacks) throws ProducerException {

        this(authCallbacks,
                storageCallbacks,
                serviceCallbacks,
                LogManager.getLogger(NativeKinesisVideoProducerJni.class));
    }

    /**
     * Public constructor
     * @param authCallbacks Authentication callbacks
     * @param storageCallbacks Storage callbacks
     * @param serviceCallbacks Service call callbacks
     * @param log logger object to use for logging
     * @throws ProducerException
     */
    public NativeKinesisVideoProducerJni(final @Nonnull AuthCallbacks authCallbacks,
                                         final @Nonnull StorageCallbacks storageCallbacks,
                                         final @Nonnull ServiceCallbacks serviceCallbacks,
                                         final @Nonnull Logger log) throws ProducerException {
        this(authCallbacks,
                storageCallbacks,
                serviceCallbacks,
                log,
                new CountDownLatch(1));
    }

    /**
     * Public constructor
     * @param authCallbacks Authentication callbacks
     * @param storageCallbacks Storage callbacks
     * @param serviceCallbacks Service call callbacks
     * @param log log object to use for logging
     * @param readyLatch Ready latch for synch creation
     * @throws ProducerException
     */
    public NativeKinesisVideoProducerJni(final @Nonnull AuthCallbacks authCallbacks,
                                         final @Nonnull StorageCallbacks storageCallbacks,
                                         final @Nonnull ServiceCallbacks serviceCallbacks,
                                         final @Nonnull Logger log,
                                         final @Nonnull CountDownLatch readyLatch) throws ProducerException {
        this(authCallbacks, storageCallbacks, serviceCallbacks, log, readyLatch, false);
    }

    /**
     * Public constructor
     * @param authCallbacks Authentication callbacks
     * @param storageCallbacks Storage callbacks
     * @param serviceCallbacks Service call callbacks
     * @param log log object to use for logging
     * @param readyLatch Ready latch for synch creation
     * @param isTrackingMallocs Whether to set up PIC's instrumented allocators
     * @throws ProducerException
     */
    public NativeKinesisVideoProducerJni(final @Nonnull AuthCallbacks authCallbacks,
                                         final @Nonnull StorageCallbacks storageCallbacks,
                                         final @Nonnull ServiceCallbacks serviceCallbacks,
                                         final @Nonnull Logger log,
                                         final @Nonnull CountDownLatch readyLatch,
                                         final boolean isTrackingMallocs) throws ProducerException {
        mLog = Preconditions.checkNotNull(log);
        mAuthCallbacks = Preconditions.checkNotNull(authCallbacks);
        mStorageCallbacks = Preconditions.checkNotNull(storageCallbacks);
        mServiceCallbacks = Preconditions.checkNotNull(serviceCallbacks);
        mReadyLatch = Preconditions.checkNotNull(readyLatch);
        mLibraryLoader = new NativeLibraryLoader(mLog);
        mCallbacks = new CallbackDispatcher(mLog);
        mServiceCallbacks.initialize(this);
        mKinesisVideoMetrics = new KinesisVideoMetrics();
        mIsTrackingMallocs = isTrackingMallocs;
    }

    /**
     * Creates the underlying Kinesis Video client object
     * @param deviceInfo {@link DeviceInfo} object
     * @throws ProducerException
     */
    @Override
    public void create(final @Nonnull DeviceInfo deviceInfo) throws ProducerException
    {
        create(deviceInfo, "");
    }

    /**
     * Creates the underlying Kinesis Video client object synchronously
     * @param deviceInfo {@link DeviceInfo} object
     * @throws ProducerException
     */
    @Override
    public void createSync(final @Nonnull DeviceInfo deviceInfo) throws ProducerException
    {
        createSync(deviceInfo, "");
    }

    /**
     * Creates the underlying Kinesis Video client object.
     * @param deviceInfo {@link DeviceInfo} object
     * @param nativeLibraryPath Native library path
     * @throws ProducerException
     */
    public void create(final @Nonnull DeviceInfo deviceInfo, final @Nonnull String nativeLibraryPath) throws ProducerException
    {
        Preconditions.checkNotNull(deviceInfo);

        synchronized (mLifecycleLock) {
            Preconditions.checkState(!isInitialized());
            mDeviceInfo = deviceInfo;
            if (!mLibraryInitialized) {
                initializeLibrary(nativeLibraryPath);

                // We are initialized
                mLibraryInitialized = true;
            }

            if (mIsTrackingMallocs) {
                setupInstrumentedAllocators();
            }

            try {
                mClientHandle = createKinesisVideoClient(deviceInfo);
            } finally {
                mClientCreated.countDown();
            }
        }
    }

    /**
     * PIC asks for the device and its token from inside createKinesisVideoClient, so their results can arrive before
     * mClientHandle is set. Wait for it, as holding the client lock across the native call used to make them do.
     */
    private void awaitClientCreated() throws ProducerException
    {
        try {
            if (!mClientCreated.await(KinesisVideoProducer.READY_TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS)) {
                mLog.warn("Client creation did not finish in time; delivering the result anyway");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProducerException(e);
        }
    }

    /**
     * Makes a native call for an event about one stream, such as a service result: under the stream's gate once
     * createStream() has registered the stream, under the client gate while it is still being created, and not at all
     * once the stream or the client has been freed. Unlike the API calls, a late event is dropped instead of thrown.
     */
    private void callForStream(final String name, final long streamHandle,
            final StreamManagerShutdownGuard.NativeCall call)
            throws ProducerException
    {
        final boolean called = !mShutdownGuard.hasStream(streamHandle) && isBeingCreated(streamHandle)
                ? mShutdownGuard.tryCallWithClient(call)
                : mShutdownGuard.tryCallWithStream(streamHandle, call);
        if (!called) {
            mLog.debug("{} for stream 0x{} dropped: the stream or the client has been freed", name,
                    Long.toHexString(streamHandle));
        }
    }

    private boolean isBeingCreated(final long streamHandle)
    {
        for (final PendingStream pending : mPendingStreams.values()) {
            if (pending.mStreamHandle == streamHandle) {
                return true;
            }
        }
        return false;
    }

    /**
     * The stream object to hand to ServiceCallbacks: the registered stream, or a stand-in while createStream() is still
     * running and the stream is not registered yet.
     */
    @Nullable
    private KinesisVideoProducerStream streamOrPending(final long streamHandle, final String streamName)
    {
        final KinesisVideoProducerStream stream = mKinesisVideoHandleMap.get(streamHandle);
        if (stream != null) {
            return stream;
        }

        final PendingStream pending = mPendingStreams.get(streamName);
        if (pending == null) {
            return null;
        }

        pending.mStreamHandle = streamHandle;
        return new PendingCreationKinesisVideoStream(streamHandle, pending.mStreamInfo, pending.mStreamCallbacks);
    }

    /**
     * Creates the underlying Kinesis Video client object synchronously.
     * @param deviceInfo {@link DeviceInfo} object
     * @param nativeLibraryPath Native library path
     * @throws ProducerException
     */
    public void createSync(final @Nonnull DeviceInfo deviceInfo, final @Nonnull String nativeLibraryPath) throws ProducerException
    {
        create(deviceInfo, nativeLibraryPath);

        // Block until client is ready or it times out.
        try {
            if (!mReadyLatch.await(KinesisVideoProducer.READY_TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS)) {
                throw new ProducerException("Kinesis Video producer creation time out", 0);
            }
        } catch (final InterruptedException ex) {
            throw new ProducerException(ex);
        }
    }

    /**
     * Hands the call to mDeferredCalls if the current thread is inside a callback from native code. The call then
     * runs once the callback has returned, and its errors are logged instead of thrown.
     *
     * @return true if the call was deferred and the caller must return
     */
    private boolean deferIfInsideCallback(final String name, final StreamManagerShutdownGuard.NativeCall call)
    {
        if (!CallbackThreads.isInsideCallback()) {
            return false;
        }

        try {
            mDeferredCalls.execute(() -> {
                try {
                    call.call();
                } catch (final ProducerException | RuntimeException e) {
                    mLog.error("{} made from inside a callback failed", name, e);
                }
            });
        } catch (final RejectedExecutionException e) {
            mLog.warn("{} made from inside a callback after the client was freed is dropped", name);
        }
        return true;
    }

    /**
     * Runs an application callback on the client's callback thread
     */
    void dispatchCallback(final String name, final CallbackDispatcher.Callback callback)
    {
        mCallbacks.dispatch(name, callback);
    }

    /**
     * @return whether the current thread is the client's callback thread
     */
    boolean isCallbackThread()
    {
        return mCallbacks.isCallbackThread();
    }

    /**
     * Hands a ServiceCallbacks call to the callback thread and lets the native callback return. PIC only learns about
     * a failure from a result, so if the call throws, a failed result is delivered instead.
     */
    private void dispatchServiceCall(final String name, final CallbackDispatcher.Callback call,
                                     @Nullable final StreamManagerShutdownGuard.NativeCall reportFailure)
    {
        mCallbacks.dispatch(name, () -> {
            try {
                call.run();
            } catch (final Exception e) {
                mLog.error("ServiceCallbacks.{} failed; reporting a failed call to PIC", name, e);
                if (reportFailure != null) {
                    reportFailure.call();
                }
            }
        });
    }

    /**
     * @throws IllegalStateException if the current thread is inside a callback from native code
     */
    private static void rejectIfInsideCallback(final String name)
    {
        if (CallbackThreads.isInsideCallback()) {
            throw new IllegalStateException(name + "() cannot be called from inside a Kinesis Video callback; "
                    + "call it from another thread");
        }
    }

    /**
     * Returns whether the object has been initialized
     * @return whether initialized
     */
    @Override
    public boolean isInitialized()
    {
        // mClientHandle is volatile: callbacks call this, and must not need a lock
        return mClientHandle != INVALID_CLIENT_HANDLE_VALUE;
    }

    /**
     * Returns whether the object is ready
     * @return whether the object is ready
     */
    @Override
    public boolean isReady()
    {
        return mIsReady;
    }

    @Override
    @Nonnull
    public KinesisVideoMetrics getMetrics() throws ProducerException
    {
        Preconditions.checkState(isInitialized());
        mShutdownGuard.callWithClient(() -> getKinesisVideoMetrics(mClientHandle, mKinesisVideoMetrics));

        return mKinesisVideoMetrics;
    }

    /**
     * Frees the underlying Kinesis Video object. This call is prompt and will terminate the ongoing transmission and
     * drop the buffers.
     * @throws ProducerException
     */
    @Override
    public void free() throws ProducerException
    {
        rejectIfInsideCallback("free");

        synchronized (mLifecycleLock) {
            Preconditions.checkState(isInitialized());

            // Rejects new calls on the client and its streams and waits for in-flight ones. Once it returns, no other
            // thread is inside a native call on this client.
            mShutdownGuard.closeClient();

            freeKinesisVideoClient(mClientHandle);
            mClientHandle = INVALID_CLIENT_HANDLE_VALUE;
        }

        // Deferred calls and callbacks still queued find the client freed: their calls into it are rejected
        mDeferredCalls.shutdown();
        mCallbacks.shutdown();
    }

    /**
     * Frees the streams and the client, once. Concurrent and repeated calls return without doing anything.
     */
    @Override
    public void close() throws ProducerException
    {
        rejectIfInsideCallback("close");

        synchronized (mLifecycleLock) {
            if (!isInitialized()) {
                return;
            }

            freeStreams();
            free();
        }
    }

    /**
     * Creates a Kinesis Video stream
     * @param streamInfo      Stream information {@link StreamInfo} object
     * @param streamCallbacks Optional stream callbacks {@link StreamCallbacks}
     * @return Newly create Kinesis Video stream
     * @throws ProducerException
     */
    @Override
    public KinesisVideoProducerStream createStream(final @Nonnull StreamInfo streamInfo,
                                                   final @Nullable StreamCallbacks streamCallbacks)
            throws ProducerException
    {
        Preconditions.checkNotNull(streamInfo);
        Preconditions.checkState(isInitialized());

        // KinesisVideoProducerStream isn't in the map until the native call returns, and the service callbacks PIC
        // makes before that need the streamInfo and the callbacks
        final PendingStream pending = new PendingStream(streamInfo, streamCallbacks);
        if (mPendingStreams.putIfAbsent(streamInfo.getName(), pending) != null) {
            throw new ProducerException("Stream " + streamInfo.getName() + " is already being created",
                    STATUS_INVALID_OPERATION);
        }

        final KinesisVideoProducerStream[] createdStream = new KinesisVideoProducerStream[1];
        try {
            mShutdownGuard.callWithClient(() -> {
                // Create the native stream
                long streamHandle = INVALID_STREAM_HANDLE_VALUE;
                try {
                    streamHandle = createKinesisVideoStream(mClientHandle, streamInfo);
                    pending.mStreamHandle = streamHandle;

                    final KinesisVideoProducerStream kinesisVideoProducerStream = new NativeKinesisVideoProducerStream(this,
                            streamInfo,
                            streamHandle,
                            mLog,
                            streamCallbacks,
                            mDeviceInfo);

                    // Register the gate first, so that whoever finds the stream in the map can also call into it
                    mShutdownGuard.addStream(streamHandle);
                    synchronized (mStreamRegistryLock) {
                        mKinesisVideoHandleMap.put(streamHandle, kinesisVideoProducerStream);
                    }

                    createdStream[0] = kinesisVideoProducerStream;
                } catch (final Exception e) {
                    mLog.error("Encountered exception creating {}", streamInfo.getSummary(), e);
                    if (streamHandle != INVALID_STREAM_HANDLE_VALUE) {
                        mShutdownGuard.removeStream(streamHandle);
                        freeKinesisVideoStream(mClientHandle, streamHandle);
                    }
                    throw e;
                }
            });
        } finally {
            mPendingStreams.remove(streamInfo.getName(), pending);
        }

        return createdStream[0];
    }

    /**
     * Creates an Kinesis Video stream synchronously
     * @param streamInfo      Stream information {@link StreamInfo} object
     * @param streamCallbacks Optional stream callnbacks {@link StreamCallbacks}
     * @return Newly created Kinesis Video stream
     * @throws ProducerException
     */
    @Override
    public KinesisVideoProducerStream createStreamSync(final @Nonnull StreamInfo streamInfo,
                                                       final @Nullable StreamCallbacks streamCallbacks)
            throws ProducerException
    {
        final NativeKinesisVideoProducerStream stream = (NativeKinesisVideoProducerStream) createStream(streamInfo, streamCallbacks);
        try {
            // Block until ready
            stream.awaitReady(mReadyStreamHandles);
        } catch (final ProducerException e) {
            freeStream(stream);
            throw e;
        }
        return stream;
    }

    /**
     * Stops all Kinesis Video streams. This is an async operation and the buffers will continue draining.
     * @throws ProducerException
     */
    @Override
    public void stopStreams() throws ProducerException
    {
        if (deferIfInsideCallback("stopStreams", () -> stopStreams())) {
            return;
        }

        // Idempotent call if already closed
        if (!isInitialized()) {
            return;
        }

        // Stop the streams
        if (!mShutdownGuard.tryCallWithClient(() -> stopKinesisVideoStreams(mClientHandle))) {
            mLog.debug("stopStreams dropped: the client has been freed");
        }
    }

    /**
     * Stops an Kinesis Video stream. This is an async operation and the buffers will continue draining until streamStopped
     * event is received.
     * @param streamHandle the handle of the stream
     * @throws ProducerException
     */
    public void stopStream(final long streamHandle) throws ProducerException
    {
        if (deferIfInsideCallback("stopStream", () -> stopStream(streamHandle))) {
            return;
        }

        // Idempotent call if already closed
        if (!isInitialized()) {
            return;
        }

        // Stop the stream
        callForStream("stopStream", streamHandle, () -> stopKinesisVideoStream(mClientHandle, streamHandle));
    }

    @Override
    public void freeStreams() throws ProducerException
    {
        rejectIfInsideCallback("freeStreams");

        Preconditions.checkState(isInitialized());
        final Collection<KinesisVideoProducerStream> streamCollection =
                new HashSet<KinesisVideoProducerStream>(mKinesisVideoHandleMap.values());

        try {
            for (final KinesisVideoProducerStream stream : streamCollection) {

                // Free the stream. freeStream() waits for the stream's in-flight calls.
                freeStream(stream);
            }
        } finally {
            // Remove from the map
            mKinesisVideoHandleMap.clear();
        }
    }

    @Override
    public void freeStream(final @Nonnull KinesisVideoProducerStream stream) throws ProducerException
    {
        rejectIfInsideCallback("freeStream");

        Preconditions.checkNotNull(stream);

        // Idempotent call if already closed
        if (!isInitialized()) {
            return;
        }

        // A stream that has been freed already has no handle
        final long streamHandle = stream.getStreamHandle();
        if (streamHandle == INVALID_STREAM_HANDLE_VALUE || !mFreeingStreams.add(streamHandle)) {
            return;
        }

        try {
            // Rejects new calls on this stream and waits for in-flight ones; the client's other streams keep running.
            mShutdownGuard.closeStream(streamHandle);

            mReadyStreamHandles.remove(streamHandle);
            stream.streamFreed();
            try {
                // Free the stream: no other call is inside it now
                freeKinesisVideoStream(mClientHandle, streamHandle);
            } finally {
                mKinesisVideoHandleMap.remove(streamHandle);
                mShutdownGuard.removeStream(streamHandle);
            }
        } finally {
            mFreeingStreams.remove(streamHandle);
        }
    }

    /**
     * Reports an abnormal stream termination event.
     * @param streamHandle the handle of the stream
     * @param uploadHandle the client stream upload handle
     * @param statusCode the status code of the termination event.
     * @throws ProducerException
     */
    public void streamTerminated(final long streamHandle, final long uploadHandle, final int statusCode) throws ProducerException
    {
        if (deferIfInsideCallback("streamTerminated", () -> streamTerminated(streamHandle, uploadHandle, statusCode))) {
            return;
        }

        // Idempotent call if already closed
        if (!isInitialized()) {
            return;
        }

        callForStream("streamTerminated", streamHandle,
                () -> kinesisVideoStreamTerminated(mClientHandle, streamHandle, uploadHandle, statusCode));
    }

    /**
     * Fills in the stream metrics object
     * @param streamHandle the handle of the stream
     * @param streamMetrics stream metrics to fill in
     * @throws ProducerException
     */
    public void getStreamMetrics(final long streamHandle, @Nonnull final KinesisVideoStreamMetrics streamMetrics)
            throws ProducerException
    {
        Preconditions.checkState(isInitialized());

        mShutdownGuard.callWithStream(streamHandle,
                () -> getKinesisVideoStreamMetrics(mClientHandle, streamHandle, streamMetrics));
    }

    /**
     * Put a frame.
     *
     * @param streamHandle the handle of the stream
     * @param kinesisVideoFrame an KinesisVideoFrame object
     * @throws ProducerException
     */
    public void putFrame(final long streamHandle, final @Nonnull KinesisVideoFrame kinesisVideoFrame) throws ProducerException
    {
        Preconditions.checkState(isInitialized());
        Preconditions.checkNotNull(kinesisVideoFrame);
        Preconditions.checkArgument(mKinesisVideoHandleMap.containsKey(streamHandle), "StreamHandle 0x" + Long.toHexString(streamHandle) + " is not valid!");

        mShutdownGuard.callWithStream(streamHandle,
                () -> putKinesisVideoFrame(mClientHandle, streamHandle, kinesisVideoFrame));
    }

    /**
     * Put a fragment metadata.
     *
     * @param streamHandle the handle of the stream
     * @param metadataName metadata name
     * @param metadataValue metadata value
     * @param persistent whether this is persistent metadata or not
     * @throws ProducerException
     */
    public void putFragmentMetadata(final long streamHandle, final @Nonnull String metadataName, @Nonnull final String metadataValue,
                                    final boolean persistent) throws ProducerException
    {
        Preconditions.checkState(isInitialized());
        Preconditions.checkNotNull(metadataName);
        Preconditions.checkNotNull(metadataValue);

        mShutdownGuard.callWithStream(streamHandle, () -> putKinesisVideoFragmentMetadata(mClientHandle, streamHandle,
                metadataName, metadataValue, persistent));
    }

    /**
     * Put an event metadata.
     *
     * @param streamHandle  The handle of the stream.
     * @param event  The type of event(s), a value from the StreamEventType enum.
 *                   If you want to submit multiple events in one call it is suggested to use bit-wise
 *                   OR combination from StreamEventType enum.
     * @param streamEventMetadata  Optional metadata. This metadata will be applied
 *                                 to all events included in THIS function call.
     * @throws ProducerException
     */
    public void putEventMetadata(final long streamHandle, final int event, final @Nullable StreamEventMetadata streamEventMetadata)
        throws ProducerException
    {
        Preconditions.checkState(isInitialized());
        Preconditions.checkArgument(event >= 0);
        mShutdownGuard.callWithStream(streamHandle,
                () -> putKinesisVideoEventMetadata(mClientHandle, streamHandle, event, streamEventMetadata));
    }

    /**
     * Report an ACK for a fragment
     *
     * @param streamHandle the handle of the stream
     * @param kinesisVideoFragmentAck an KinesisVideoFragmentAck object
     * @throws ProducerException
     */
    public void fragmentAck(final long streamHandle, final long uploadHandle, final @Nonnull KinesisVideoFragmentAck kinesisVideoFragmentAck) throws ProducerException
    {
        Preconditions.checkState(isInitialized());
        Preconditions.checkNotNull(kinesisVideoFragmentAck);

        mShutdownGuard.callWithStream(streamHandle, () -> kinesisVideoStreamFragmentAck(mClientHandle, streamHandle,
                uploadHandle, kinesisVideoFragmentAck));
    }

    /**
     * Parses and processes an ACK string
     *
     * @param streamHandle the handle of the stream
     * @param kinesisVideoFragmentAck string containing ACKs
     * @throws ProducerException
     */
    public void parseFragmentAck(final long streamHandle, final long uploadHandle, final @Nonnull String kinesisVideoFragmentAck) throws ProducerException
    {
        Preconditions.checkState(isInitialized());
        Preconditions.checkNotNull(kinesisVideoFragmentAck);

        mShutdownGuard.callWithStream(streamHandle, () -> kinesisVideoStreamParseFragmentAck(mClientHandle,
                streamHandle,
                uploadHandle, kinesisVideoFragmentAck));
    }

    /**
     * Get stream data from the buffer for specific upload Handle.
     * Each uploadHandle correspond to a PutMedia connection to
     * Kinesis Video Streams.
     *
     * @param streamHandle     the handle of the stream
     * @param uploadHandle     the client stream upload handle
     * @param fillBuffer    The buffer to fill
     * @param offset    The start of the buffer
     * @param length    The number of bytes to fill
     * @param readResult    The read result to fill in
     * @throws ProducerException
     */
    public void getStreamData(final long streamHandle,
                              final long uploadHandle,
                              final @Nonnull byte[] fillBuffer,
                              final int offset,
                              final int length,
                              final @Nonnull ReadResult readResult) throws ProducerException
    {
        Preconditions.checkState(isInitialized());
        Preconditions.checkNotNull(fillBuffer);
        Preconditions.checkNotNull(readResult);

        mShutdownGuard.callWithStream(streamHandle, () -> getKinesisVideoStreamData(mClientHandle, streamHandle,
                uploadHandle, fillBuffer, offset, length, readResult));
    }

    /**
     * Stream format has changed. Currently, only supporting Codec Private Data
     *
     * @param streamHandle     the handle of the stream
     * @param codecPrivateData    Codec Private Data or null to reset
     * @throws ProducerException
     */
    public void streamFormatChanged(final long streamHandle, final @Nullable byte[] codecPrivateData,
                                    final long trackId) throws ProducerException
    {
        Preconditions.checkState(isInitialized());

        mShutdownGuard.callWithStream(streamHandle,
                () -> kinesisVideoStreamFormatChanged(mClientHandle, streamHandle, codecPrivateData, trackId));
    }

    /**
     * Returns the device certificate object
     *
     * @return Device certificate
     */
    @Nullable
    @CalledByNativeCode
    private AuthInfo getDeviceCertificate()
    {
        CallbackThreads.enter();
        try {
            return mAuthCallbacks.getDeviceCertificate();
        } finally {
            CallbackThreads.exit();
        }
    }

    /**
     * Returns the device security token bits
     *
     * @return Device security token
     */
    @Nullable
    @CalledByNativeCode
    private AuthInfo getSecurityToken()
    {
        CallbackThreads.enter();
        try {
            return mAuthCallbacks.getSecurityToken();
        } finally {
            CallbackThreads.exit();
        }
    }

    /**
     * Returns the device fingerprint
     *
     * @return Device fingerprint
     */
    @Nullable
    @CalledByNativeCode
    private String getDeviceFingerprint()
    {
        CallbackThreads.enter();
        try {
            return mAuthCallbacks.getDeviceFingerprint();
        } finally {
            CallbackThreads.exit();
        }
    }

    /**
     * Reports stream underflow
     *
     * @param streamHandle     the handle of the stream
     */
    @CalledByNativeCode
    private void streamUnderflowReport(final long streamHandle) throws ProducerException
    {
        CallbackThreads.enter();
        try {
            if (!mKinesisVideoHandleMap.containsKey(streamHandle)) {
                throw new ProducerException("Invalid stream handle.", STATUS_INVALID_OPERATION);
            }
            final KinesisVideoProducerStream kinesisVideoProducerStream = mKinesisVideoHandleMap.get(streamHandle);
            mLog.trace("[{}] streamUnderflowReport", kinesisVideoProducerStream.getStreamName());
            kinesisVideoProducerStream.streamUnderflowReport();
        } finally {
            CallbackThreads.exit();
        }
    }

    /**
     * Reports storage overflow pressure
     */
    @CalledByNativeCode
    private void storageOverflowPressure(final long remainingSize)
    {
        CallbackThreads.enter();
        try {
            mLog.trace("storageOverflowPressure - {}", remainingSize);
            mCallbacks.dispatch("storageOverflowPressure", () -> mStorageCallbacks.storageOverflowPressure(remainingSize));
        } finally {
            CallbackThreads.exit();
        }
    }

    /**
     * Reports buffer temporal overflow pressure
     */
    @CalledByNativeCode
    private void bufferDurationOverflowPressure(final long streamHandle, final long remainDuration) throws ProducerException {
        CallbackThreads.enter();
        try {
            if (!mKinesisVideoHandleMap.containsKey(streamHandle)) {
                throw new ProducerException("Invalid stream handle.", STATUS_INVALID_OPERATION);
            }

            final KinesisVideoProducerStream kinesisVideoProducerStream = mKinesisVideoHandleMap.get(streamHandle);
            mLog.trace("[{}] bufferDurationOverflowPressure - {}", kinesisVideoProducerStream.getStreamName(), remainDuration);
            kinesisVideoProducerStream.bufferDurationOverflowPressure(remainDuration);
        } finally {
            CallbackThreads.exit();
        }
    }

    /**
     * Reports stream latency pressure
     */
    @CalledByNativeCode
    private void streamLatencyPressure(final long streamHandle, final long duration) throws ProducerException
    {
        CallbackThreads.enter();
        try {
            if (!mKinesisVideoHandleMap.containsKey(streamHandle)) {
                throw new ProducerException("Invalid stream handle.", STATUS_INVALID_OPERATION);
            }

            final KinesisVideoProducerStream kinesisVideoProducerStream = mKinesisVideoHandleMap.get(streamHandle);
            mLog.trace("[{}] streamLatencyPressure - {}", kinesisVideoProducerStream.getStreamName(), duration);
            kinesisVideoProducerStream.streamLatencyPressure(duration);
        } finally {
            CallbackThreads.exit();
        }
    }

    /**
     * Reports stream connection staleness
     */
    @CalledByNativeCode
    private void streamConnectionStale(final long streamHandle, final long lastAckDuration) throws ProducerException
    {
        CallbackThreads.enter();
        try {
            if (!mKinesisVideoHandleMap.containsKey(streamHandle)) {
                throw new ProducerException("Invalid stream handle.", STATUS_INVALID_OPERATION);
            }

            final KinesisVideoProducerStream kinesisVideoProducerStream = mKinesisVideoHandleMap.get(streamHandle);
            mLog.trace("[{}] streamConnectionStale - {}", kinesisVideoProducerStream.getStreamName(), lastAckDuration);
            kinesisVideoProducerStream.streamConnectionStale(lastAckDuration);
        } finally {
            CallbackThreads.exit();
        }
    }

    /**
     * Reports received fragment ACK
     *
     * @param streamHandle the handle of the stream
     * @param uploadHandle the client stream upload handle
     * @param fragmentAck ACK for the fragment
     */
    @CalledByNativeCode
    private void fragmentAckReceived(final long streamHandle, final long uploadHandle, @Nonnull final KinesisVideoFragmentAck fragmentAck)
            throws ProducerException
    {
        CallbackThreads.enter();
        try {
            if (!mKinesisVideoHandleMap.containsKey(streamHandle)) {
                throw new ProducerException("Invalid stream handle.", STATUS_INVALID_OPERATION);
            }

            final KinesisVideoProducerStream kinesisVideoProducerStream = mKinesisVideoHandleMap.get(streamHandle);
            mLog.trace("[{}] fragmentAckReceived - {}", kinesisVideoProducerStream.getStreamName(), fragmentAck);
            kinesisVideoProducerStream.fragmentAckReceived(uploadHandle, fragmentAck);
        } finally {
            CallbackThreads.exit();
        }
    }

    /**
     * Reports dropped frame
     */
    @CalledByNativeCode
    private void droppedFrameReport(final long streamHandle, final long frameTimecode) throws ProducerException
    {
        CallbackThreads.enter();
        try {
            if (!mKinesisVideoHandleMap.containsKey(streamHandle)) {
                throw new ProducerException("Invalid stream handle.", STATUS_INVALID_OPERATION);
            }

            final KinesisVideoProducerStream kinesisVideoProducerStream = mKinesisVideoHandleMap.get(streamHandle);
            mLog.trace("[{}] droppedFrameReport - {}", kinesisVideoProducerStream.getStreamName(), frameTimecode);
            kinesisVideoProducerStream.droppedFrameReport(frameTimecode);
        } finally {
            CallbackThreads.exit();
        }
    }

    /**
     * Reports dropped fragment
     */
    @CalledByNativeCode
    private void droppedFragmentReport(final long streamHandle, final long fragmentTimecode) throws ProducerException
    {
        CallbackThreads.enter();
        try {
            if (!mKinesisVideoHandleMap.containsKey(streamHandle)) {
                throw new ProducerException("Invalid stream handle.", STATUS_INVALID_OPERATION);
            }

            final KinesisVideoProducerStream kinesisVideoProducerStream = mKinesisVideoHandleMap.get(streamHandle);
            mLog.trace("[{}] droppedFragmentReport - {}", kinesisVideoProducerStream.getStreamName(), fragmentTimecode);
            kinesisVideoProducerStream.droppedFragmentReport(fragmentTimecode);
        } finally {
            CallbackThreads.exit();
        }
    }

    /**
     * Reports stream error.
     */
    @CalledByNativeCode
    private void streamErrorReport(final long streamHandle, final long uploadHandle, final long fragmentTimecode, final long statusCode) throws ProducerException
    {
        CallbackThreads.enter();
        try {
            if (!mKinesisVideoHandleMap.containsKey(streamHandle)) {
                throw new ProducerException("Invalid stream handle.", STATUS_INVALID_OPERATION);
            }

            final KinesisVideoProducerStream kinesisVideoProducerStream = mKinesisVideoHandleMap.get(streamHandle);
            mLog.trace("[{}] streamErrorReport - {}, 0x{}", kinesisVideoProducerStream.getStreamName(), fragmentTimecode, Long.toHexString(statusCode));
            kinesisVideoProducerStream.streamErrorReport(uploadHandle, fragmentTimecode, statusCode);
        } finally {
            CallbackThreads.exit();
        }
    }

    /**
     * Reports stream data is available
     */
    @CalledByNativeCode
    private void streamDataAvailable(final long streamHandle, final String streamName, final long uploadHandle, final long duration, final long availableSize) throws ProducerException
    {
        CallbackThreads.enter();
        try {
            if (!mKinesisVideoHandleMap.containsKey(streamHandle)) {
                throw new ProducerException("Invalid stream handle.", STATUS_INVALID_OPERATION);
            }

            final KinesisVideoProducerStream kinesisVideoProducerStream = mKinesisVideoHandleMap.get(streamHandle);
            kinesisVideoProducerStream.streamDataAvailable(uploadHandle, duration, availableSize);
        } finally {
            CallbackThreads.exit();
        }
    }

    /**
     * Reports stream is ready
     */
    @CalledByNativeCode
    private void streamReady(final long streamHandle) throws ProducerException
    {
        CallbackThreads.enter();
        try {
            // Either createStream() has registered the stream, or it will find the handle parked here
            final KinesisVideoProducerStream kinesisVideoProducerStream;
            synchronized (mStreamRegistryLock) {
                kinesisVideoProducerStream = mKinesisVideoHandleMap.get(streamHandle);
                if (kinesisVideoProducerStream == null) {
                    mLog.info("Stream Ready for non-existing stream handle 0x{}", Long.toHexString(streamHandle));
                    mReadyStreamHandles.add(streamHandle);
                    return;
                }
            }

            mLog.trace("[{}] streamReady", kinesisVideoProducerStream.getStreamName());
            kinesisVideoProducerStream.streamReady();
        } finally {
            CallbackThreads.exit();
        }
    }

    /**
     * Reports stream is closed
     */
    @CalledByNativeCode
    private void streamClosed(final long streamHandle, final long uploadHandle) throws ProducerException
    {
        CallbackThreads.enter();
        try {
            final KinesisVideoProducerStream kinesisVideoProducerStream = mKinesisVideoHandleMap.get(streamHandle);
            if (kinesisVideoProducerStream == null) {
                throw new ProducerException("Invalid stream handle.", STATUS_INVALID_OPERATION);
            }

            mLog.trace("[{}] streamClosed", kinesisVideoProducerStream.getStreamName());
            kinesisVideoProducerStream.streamClosed(uploadHandle);
        } finally {
            CallbackThreads.exit();
        }
    }

    /**
     * Reports client is ready
     */
    @CalledByNativeCode
    private void clientReady(final long clientHandle) throws ProducerException
    {
        CallbackThreads.enter();
        try {
            if (mClientHandle != clientHandle) {
                throw new ProducerException("Invalid client handle.", STATUS_INVALID_OPERATION);
            }

            mIsReady = true;
            mLog.trace("clientReady");

            // Release the ready latch
            mReadyLatch.countDown();
        } finally {
            CallbackThreads.exit();
        }
    }

    /**
     * Callback from the native code to schedule a call to create stream
     * @param deviceName - Device name
     * @param streamName - Stream name
     * @param contentType - Stream content type
     * @param kmsKeyId - KMS Key Id
     * @param retentionPeriod - Stream retention period
     * @param callAfter - Call after this time
     * @param timeout - Time out for the call
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param customData - Custom data to use to call the event functions
     * @return STATUS of the call
     */
    @CalledByNativeCode
    private int createStream(final @Nonnull String deviceName,
            final @Nonnull String streamName,
            final @Nonnull String contentType,
            final @Nullable String kmsKeyId,
            final long retentionPeriod,
            final long callAfter,
            final long timeout,
            final @Nullable byte[] authData,
            final int authType,
            final long customData) throws ProducerException
    {
        CallbackThreads.enter();
        try {
            final KinesisVideoProducerStream stream = streamOrPending(customData, streamName);
            dispatchServiceCall("createStream",
                    () -> mServiceCallbacks.createStream(deviceName, streamName, contentType, kmsKeyId,
                            retentionPeriod, callAfter, timeout, authData, authType, stream),
                    stream == null ? null : () -> createStreamResult(stream, null, SERVICE_CALL_UNKNOWN));
            return STATUS_SUCCESS;
        } finally {
            CallbackThreads.exit();
        }
    }

    @Override
    public void createStreamResult(@Nonnull KinesisVideoProducerStream stream, @Nullable String streamArn, int httpStatusCode) throws ProducerException {
        if (deferIfInsideCallback("createStreamResult", () -> createStreamResult(stream, streamArn, httpStatusCode))) {
            return;
        }

        // No Java lock across the native call: PIC can call back into Java from inside it
        final long streamHandle = stream.getStreamHandle();
        callForStream("createStreamResult", streamHandle,
                () -> createStreamResultEvent(mClientHandle, streamHandle, httpStatusCode, streamArn));
    }

    /**
     * Callback from the native code to schedule a call to describe stream
     * @param streamName - Stream name
     * @param callAfter - Call after this time
     * @param timeout - Time out for the call
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param streamHandle - Custom data to use to call the event functions
     * @return STATUS of the call
     */
    @CalledByNativeCode
    private int describeStream(final @Nonnull String streamName,
            final long callAfter,
            final long timeout,
            final byte[] authData,
            final int authType,
            final long streamHandle) throws ProducerException
    {
        CallbackThreads.enter();
        try {
            final KinesisVideoProducerStream stream = streamOrPending(streamHandle, streamName);
            dispatchServiceCall("describeStream",
                    () -> mServiceCallbacks.describeStream(streamName, callAfter, timeout, authData, authType,
                            streamHandle, stream),
                    () -> describeStreamResult(stream, streamHandle, null, SERVICE_CALL_UNKNOWN));
            return STATUS_SUCCESS;
        } finally {
            CallbackThreads.exit();
        }
    }

    @Override
    public void describeStreamResult(final KinesisVideoProducerStream stream,
                                      final long streamHandle,
                                      final @Nullable StreamDescription streamDescription,
                                      final int httpStatusCode) throws ProducerException
    {
        if (deferIfInsideCallback("describeStreamResult", () -> describeStreamResult(stream, streamHandle, streamDescription, httpStatusCode))) {
            return;
        }

        final long handle = stream == null ? streamHandle : stream.getStreamHandle();
        if (handle != INVALID_STREAM_HANDLE_VALUE) {
            callForStream("describeStreamResult", handle,
                    () -> describeStreamResultEvent(mClientHandle, handle, httpStatusCode, streamDescription));
        }
    }

    /**
     * Callback from the native code to schedule a call to get streaming endpoint
     * @param streamName - Stream name
     * @param apiName - API to call
     * @param callAfter - Call after this time
     * @param timeout - Time out for the call
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param streamHandle - Custom data to use to call the event functions
     * @return STATUS of the call
     */
    @CalledByNativeCode
    private int getStreamingEndpoint(final @Nonnull String streamName,
            final @Nonnull String apiName,
            final long callAfter,
            final long timeout,
            final @Nullable byte[] authData,
            final int authType,
            final long streamHandle) throws ProducerException
    {
        CallbackThreads.enter();
        try {
            final KinesisVideoProducerStream stream = mKinesisVideoHandleMap.get(streamHandle);
            dispatchServiceCall("getStreamingEndpoint",
                    () -> mServiceCallbacks.getStreamingEndpoint(streamName, apiName, callAfter, timeout, authData,
                            authType, streamHandle, stream),
                    () -> getStreamingEndpointResult(stream, streamHandle, null, SERVICE_CALL_UNKNOWN));
            return STATUS_SUCCESS;
        } finally {
            CallbackThreads.exit();
        }
    }

    @Override
    public void getStreamingEndpointResult(final KinesisVideoProducerStream stream,
                                           final long streamHandle,
                                           final @Nullable String endpoint,
                                           final int httpStatusCode) throws ProducerException
    {
        if (deferIfInsideCallback("getStreamingEndpointResult", () -> getStreamingEndpointResult(stream, streamHandle, endpoint, httpStatusCode))) {
            return;
        }

        final long handle = stream == null ? streamHandle : stream.getStreamHandle();
        if (handle != INVALID_STREAM_HANDLE_VALUE) {
            callForStream("getStreamingEndpointResult", handle,
                    () -> getStreamingEndpointResultEvent(mClientHandle, handle, httpStatusCode, endpoint));
        }
    }

    /**
     * Callback from the native code to schedule a call to get streaming token
     * @param streamName - Stream name
     * @param callAfter - Call after this time
     * @param timeout - Time out for the call
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param streamHandle - Custom data to use to call the event functions
     * @return STATUS of the call
     */
    @CalledByNativeCode
    private int getStreamingToken(final @Nonnull String streamName,
            final long callAfter,
            final long timeout,
            final @Nullable byte[] authData,
            final int authType,
            final long streamHandle) throws ProducerException
    {
        CallbackThreads.enter();
        try {
            final KinesisVideoProducerStream stream = mKinesisVideoHandleMap.get(streamHandle);
            dispatchServiceCall("getStreamingToken",
                    () -> mServiceCallbacks.getStreamingToken(streamName, callAfter, timeout, authData, authType,
                            streamHandle, stream),
                    () -> getStreamingTokenResult(stream, streamHandle, NO_TOKEN, 0, SERVICE_CALL_UNKNOWN));
            return STATUS_SUCCESS;
        } finally {
            CallbackThreads.exit();
        }
    }

    @Override
    public void getStreamingTokenResult(final KinesisVideoProducerStream stream,
                                        final long streamHandle,
                                        final @Nullable byte[] token,
                                        final long expiration,
                                        final int httpStatusCode) throws ProducerException
    {
        if (deferIfInsideCallback("getStreamingTokenResult", () -> getStreamingTokenResult(stream, streamHandle, token, expiration, httpStatusCode))) {
            return;
        }

        final int tokenSize = token == null ? 0 : token.length;
        final long handle = stream == null ? streamHandle : stream.getStreamHandle();
        if (handle != INVALID_STREAM_HANDLE_VALUE) {
            callForStream("getStreamingTokenResult", handle, () -> getStreamingTokenResultEvent(mClientHandle, handle,
                    httpStatusCode, token, tokenSize, expiration));
        }
    }

    /**
     * Schedules a call to put stream API
     * @param streamName - Stream name
     * @param containerType - Container type
     * @param streamStartTime - Stream start timestamp
     * @param absoluteFragmentTimes - Whether to use absolute fragment times
     * @param ackRequired - Whether an application level ACK is required
     * @param streamingEndpoint - The streaming endpoint to use
     * @param callAfter - Call after this time
     * @param timeout - Time out for the call
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param streamHandle - Custom data to use to call the event functions
     * @return STATUS of the call
     */
    @CalledByNativeCode
    private int putStream(final @Nonnull String streamName,
            final @Nonnull String containerType,
            final long streamStartTime,
            final boolean absoluteFragmentTimes,
            final boolean ackRequired,
            final @Nonnull String streamingEndpoint,
            final long callAfter,
            final long timeout,
            final @Nullable byte[] authData,
            final int authType,
            final long streamHandle) throws ProducerException
    {
        CallbackThreads.enter();
        try {
            final KinesisVideoProducerStream stream = mKinesisVideoHandleMap.get(streamHandle);
            dispatchServiceCall("putStream",
                    () -> mServiceCallbacks.putStream(streamName, containerType, streamStartTime,
                            absoluteFragmentTimes, ackRequired, streamingEndpoint, callAfter, timeout, authData, authType,
                            stream),
                    stream == null ? null : () -> putStreamResult(stream, ReadResult.INVALID_UPLOAD_HANDLE_VALUE,
                            SERVICE_CALL_UNKNOWN));
            return STATUS_SUCCESS;
        } finally {
            CallbackThreads.exit();
        }
    }

    @Override
    public void putStreamResult(final KinesisVideoProducerStream stream, final long clientStreamHandle,
                                final int httpStatusCode) throws ProducerException
    {
        if (deferIfInsideCallback("putStreamResult", () -> putStreamResult(stream, clientStreamHandle, httpStatusCode))) {
            return;
        }

        final long handle = stream.getStreamHandle();
        if (handle != INVALID_STREAM_HANDLE_VALUE) {
            callForStream("putStreamResult", handle,
                    () -> putStreamResultEvent(mClientHandle, handle, httpStatusCode, clientStreamHandle));
        }
    }

    /**
     * Schedules a call to tag resource API
     * @param resourceArn - Resource ARN
     * @param tags - Array of tags
     * @param callAfter - Call after this time
     * @param timeout - Time out for the call
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param streamHandle - Custom data to use to call the event functions
     * @return STATUS of the call
     */
    @CalledByNativeCode
    private int tagResource(final @Nonnull String resourceArn,
            final @Nonnull Tag[] tags,
            final long callAfter,
            final long timeout,
            final @Nullable byte[] authData,
            final int authType,
            final long streamHandle) throws ProducerException
    {
        CallbackThreads.enter();
        try {
            final KinesisVideoProducerStream stream = mKinesisVideoHandleMap.get(streamHandle);
            dispatchServiceCall("tagResource",
                    () -> mServiceCallbacks.tagResource(resourceArn, tags, callAfter, timeout, authData, authType,
                            streamHandle, stream),
                    () -> tagResourceResult(stream, streamHandle, SERVICE_CALL_UNKNOWN));
            return STATUS_SUCCESS;
        } finally {
            CallbackThreads.exit();
        }
    }

    @Override
    public void tagResourceResult(final KinesisVideoProducerStream stream,
                                  final long streamHandle, final int httpStatusCode) throws ProducerException
    {
        if (deferIfInsideCallback("tagResourceResult", () -> tagResourceResult(stream, streamHandle, httpStatusCode))) {
            return;
        }

        final long handle = stream == null ? streamHandle : stream.getStreamHandle();
        if (handle != INVALID_STREAM_HANDLE_VALUE) {
            callForStream("tagResourceResult", handle,
                    () -> tagResourceResultEvent(mClientHandle, handle, httpStatusCode));
        }
    }

    /**
     * Schedules a call to create device API
     *
     * IMPORTANT!!! This should schedule a call in a different thread and not
     * result in an immediate call to CreateDeviceResult as the mClientHandle
     * hasn't been set yet.
     *
     * @param deviceName - Device name to use when creating a new device object
     * @param callAfter - Call after this time
     * @param timeout - Time out for the call
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param customData - Custom data to use to call the event functions
     * @return STATUS of the call
     */
    @CalledByNativeCode
    private int createDevice(final @Nonnull String deviceName,
            final long callAfter,
            final long timeout,
            final @Nullable byte[] authData,
            final int authType,
            final long customData) throws ProducerException
    {
        CallbackThreads.enter();
        try {
            dispatchServiceCall("createDevice",
                    () -> mServiceCallbacks.createDevice(deviceName, callAfter, timeout, authData, authType, customData),
                    () -> createDeviceResult(customData, null, SERVICE_CALL_UNKNOWN));
            return STATUS_SUCCESS;
        } finally {
            CallbackThreads.exit();
        }
    }

    @Override
    @SuppressWarnings({"ConstantConditions"})
    public void createDeviceResult(final long customData, @Nullable String deviceArn, int httpStatusCode) throws ProducerException {
        if (deferIfInsideCallback("createDeviceResult", () -> createDeviceResult(customData, deviceArn, httpStatusCode))) {
            return;
        }

        awaitClientCreated();
        if (!mShutdownGuard.tryCallWithClient(() -> createDeviceResultEvent(mClientHandle, customData, httpStatusCode,
                deviceArn))) {
            mLog.debug("createDeviceResult dropped: the client has been freed");
        }
    }

    /**
     * Schedules a call to device cert to token API
     *
     * IMPORTANT!!! This should schedule a call in a different thread and not
     * result in an immediate call to CreateDeviceResult as the mClientHandle
     * hasn't been set yet.
     *
     * @param deviceName - Device name to use when creating a new device object
     * @param callAfter - Call after this time
     * @param timeout - Time out for the call
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param customData - Custom data to use to call the event functions
     * @return STATUS of the call
     */
    @CalledByNativeCode
    private int deviceCertToToken(final @Nonnull String deviceName,
            final long callAfter,
            final long timeout,
            final @Nullable byte[] authData,
            final int authType,
            final long customData) throws ProducerException
    {
        CallbackThreads.enter();
        try {
            dispatchServiceCall("deviceCertToToken",
                    () -> mServiceCallbacks.deviceCertToToken(deviceName, callAfter, timeout, authData, authType,
                            customData),
                    () -> deviceCertToTokenResult(customData, NO_TOKEN, 0, SERVICE_CALL_UNKNOWN));
            return STATUS_SUCCESS;
        } finally {
            CallbackThreads.exit();
        }
    }

    @Override
    @SuppressWarnings({"ConstantConditions"})
    public void deviceCertToTokenResult(final long customData, final @Nullable byte[] token, final long expiration, final int httpStatusCode) throws ProducerException
    {
        if (deferIfInsideCallback("deviceCertToTokenResult", () -> deviceCertToTokenResult(customData, token, expiration, httpStatusCode))) {
            return;
        }

        final int tokenSize = token == null ? 0 : token.length;
        awaitClientCreated();
        if (!mShutdownGuard.tryCallWithClient(() -> deviceCertToTokenResultEvent(mClientHandle, customData,
                httpStatusCode,
                token, tokenSize, expiration))) {
            mLog.debug("deviceCertToTokenResult dropped: the client has been freed");
        }
    }

    /**
     * Awaits for the initialization/loading
     */
    private void initializeLibrary(final @Nonnull String nativeLibraryPath) throws ProducerException
    {
        // Load the native library
        if (!mLibraryLoader.loadNativeLibrary(nativeLibraryPath, PRODUCER_NATIVE_LIBRARY_NAME)) {
            throw new ProducerException("Failed loading native library", STATUS_INVALID_OPERATION);
        }

        // Check the library version info
        final String libraryVersion = getNativeLibraryVersion();

        // Get the compile time for reporting purposes
        final String compileTime = getNativeCodeCompileTime();

        mLog.trace("{} library: version {}, compile time {}", PRODUCER_NATIVE_LIBRARY_NAME, libraryVersion, compileTime);

        Preconditions.checkState(libraryVersion.equals(EXPECTED_LIBRARY_VERSION),
                    String.format("FATAL DEPLOYMENT ERROR: This app is built "
                            + "to run with version %s of the lib%s.so library, but version %s was found on this device",
                    EXPECTED_LIBRARY_VERSION, PRODUCER_NATIVE_LIBRARY_NAME, libraryVersion));
    }

    /**
     * Logs PIC logs
     *
     * @param level - log level from PIC
     * @param tag - tag from PIC
     * @param picFmt - fmt from PIC
     * @param message - the string to be logged
     */
    @CalledByNativeCode
    public void logPrint(final @Nonnull int level, final @Nonnull String tag, final @Nonnull String picFmt, final @Nonnull String message) {
        String toPrint = "[PIC] " + tag + " - " + message;
        switch (level) {
            case 1:
                mLog.trace(toPrint);
                break;
            case 2:
                mLog.debug(toPrint);
                break;
            case 3:
                mLog.info(toPrint);
                break;
            case 4:
                mLog.warn(toPrint);
                break;
            default:
                mLog.error(toPrint);
                break;
        }
    }

    /**
     * Returns the native library version
     *
     * @return
     */
    private native String getNativeLibraryVersion();

    /**
     * returns the native library compile time
     *
     * @return
     */
    private native String getNativeCodeCompileTime();

    /**
     * Creates a new Kinesis Video client native component
     *
     * @param deviceInfo
     *         Instance of {@link DeviceInfo} object
     * @return A newly created producer client object handle
     * @throws ProducerException
     */
    private native long createKinesisVideoClient(final @Nonnull DeviceInfo deviceInfo)
            throws ProducerException;

    /**
     * Releases the underlying native component
     *
     * @param clientHandle
     *          Client handle
     * @throws ProducerException
     */
    private native void freeKinesisVideoClient(long clientHandle) throws ProducerException;

    /**
     * Stops the underlying native streams
     *
     * @param clientHandle
     *          Client handle
     * @throws ProducerException
     */
    private native void stopKinesisVideoStreams(long clientHandle) throws ProducerException;

    /**
     * Stops the underlying native stream
     *
     * @param clientHandle Client handle
     * @param streamHandle Stream handle
     * @throws ProducerException
     */
    private native void stopKinesisVideoStream(long clientHandle, long streamHandle) throws ProducerException;

    /**
     * Frees the underlying native stream. The existing buffer content (if any) will be dropped and not sent.
     *
     * @param clientHandle Client handle
     * @param streamHandle Stream handle
     * @throws ProducerException
     */
    private native void freeKinesisVideoStream(long clientHandle, long streamHandle) throws ProducerException;

    /**
     * Creates a new Kinesis Video stream native component
     * @param clientHandle Client handle
     * @param streamInfo
     *         Instance of {@link StreamInfo} object
     * @return A newly created stream object handle
     * @throws ProducerException
     */
    private native long createKinesisVideoStream(long clientHandle, final @Nonnull StreamInfo streamInfo)
            throws ProducerException;

    /**
     * Puts a frame into the frame buffer.
     *
     * @param clientHandle Client handle
     * @param streamHandle Stream handle
     * @param kinesisVideoFrame Kinesis Video frame to put
     * @throws ProducerException
     */
    private native void putKinesisVideoFrame(long clientHandle, long streamHandle,
                                             final @Nonnull KinesisVideoFrame kinesisVideoFrame)
            throws ProducerException;

    /**
     * Puts a fragment Metadata into the native producer.
     *
     * @param clientHandle Client handle
     * @param streamHandle Stream handle
     * @param metadataName metadata name
     * @param metadataValue metadata value
     * @param persistent whether this is persistent metadata or not
     * @throws ProducerException
     */
    private native void putKinesisVideoFragmentMetadata(long clientHandle, long streamHandle,
                                                        final @Nonnull String metadataName,
                                                        final @Nonnull String metadataValue, boolean persistent)
            throws ProducerException;

    /**
     * Puts an event Metadata into the native producer.
     *
     * @param clientHandle The client handle.
     * @param streamHandle The handle of the stream.
     * @param event The type of event(s), a value from the StreamEventType enum.
 *                  If you want to submit multiple events in one call it is suggested to use bit-wise
 *                  OR combination from StreamEventType enum.
     * @param streamEventMetadata Optional metadata. This metadata will be applied
 *                                to all events included in THIS function call.
     * @throws ProducerException
     */
    private native void putKinesisVideoEventMetadata(long clientHandle, long streamHandle, final int event, final StreamEventMetadata streamEventMetadata)
            throws ProducerException;

    /**
     * Reports a stream fragment ACK.
     *
     * @param clientHandle Client handle
     * @param streamHandle Stream handle
     * @param uploadHandle the client stream upload handle
     * @param kinesisVideoFragmentAck Kinesis Video fragment ack to report for the stream
     * @throws ProducerException
     */
    private native void kinesisVideoStreamFragmentAck(long clientHandle, long streamHandle, long uploadHandle,
                                                      final @Nonnull KinesisVideoFragmentAck kinesisVideoFragmentAck)
            throws ProducerException;

    /**
     * Parses and processes a string containing fragment ACKs.
     *
     * @param clientHandle Client handle
     * @param streamHandle Stream handle
     * @param uploadHandle the client stream upload handle
     * @param kinesisVideoFragmentAck Kinesis Video fragment ack string to report for the stream
     * @throws ProducerException
     */
    private native void kinesisVideoStreamParseFragmentAck(long clientHandle, long streamHandle, long uploadHandle,
                                                           final @Nonnull String kinesisVideoFragmentAck)
            throws ProducerException;

    /**
     * Gets the Kinesis Video client object metrics.
     *
     * @param clientHandle Client handle
     * @param metrics - Kinesis Video metrics object to fill in
     * @throws ProducerException
     */
    private native void getKinesisVideoMetrics(long clientHandle, @Nonnull final KinesisVideoMetrics metrics)
            throws ProducerException;

    /**
     * Gets the Kinesis Video stream object metrics.
     *
     * @param clientHandle Client handle
     * @param streamHandle Stream handle
     * @param metrics - Kinesis Video stream metrics object to fill in
     * @throws ProducerException
     */
    private native void getKinesisVideoStreamMetrics(long clientHandle, long streamHandle,
                                                     final @Nonnull KinesisVideoStreamMetrics metrics)
            throws ProducerException;

    /**
     * Sends describe stream result event to SDK
     *
     * @param clientHandle client handle
     * @param streamHandle stream handle
     * @param httpStatusCode HTTP status code from Kinesis Video service for describeStream API
     * @param streamDescription stream description object
     * @throws ProducerException
     */
    private native void describeStreamResultEvent(long clientHandle, long streamHandle, int httpStatusCode,
                                                  final @Nullable StreamDescription streamDescription)
            throws ProducerException;

    /**
     * Sends get streaming endpoint result event to SDK
     *
     * @param clientHandle client handle
     * @param streamHandle stream handle
     * @param httpStatusCode HTTP status code from Kinesis Video service for getStreamingEndpoint API
     * @param streamingEndpoint streaming endpoint
     * @throws ProducerException
     */
    private native void getStreamingEndpointResultEvent(long clientHandle, long streamHandle, int httpStatusCode,
                                                        final @Nullable String streamingEndpoint)
            throws ProducerException;

    /**
     * Sends get streaming token result event to SDK
     *
     * @param clientHandle client handle
     * @param streamHandle stream handle
     * @param httpStatusCode HTTP status code from Kinesis Video service for getStreamingToken API
     * @param streamingToken streaming token
     * @param tokenSize token size
     * @param tokenExpiration token expiration time in 100ns absolute time
     * @throws ProducerException
     */
    private native void getStreamingTokenResultEvent(long clientHandle, long streamHandle, int httpStatusCode,
                                                     final @Nullable byte[] streamingToken, int tokenSize,
                                                     long tokenExpiration)
            throws ProducerException;

    /**
     * Sends put stream result event to SDK
     *
     * @param clientHandle client handle
     * @param streamHandle stream handle
     * @param httpStatusCode HTTP status code from Kinesis Video service for putStream API
     * @param clientStreamHandle client stream handle
     * @throws ProducerException
     */
    private native void putStreamResultEvent(long clientHandle, long streamHandle, int httpStatusCode, long clientStreamHandle)
            throws ProducerException;

    /**
     * Sends tag resource result event to SDK
     *
     * @param clientHandle client handle
     * @param customData custom data passed in
     * @param httpStatusCode HTTP status code from Kinesis Video service for putStream API
     * @throws ProducerException
     */
    private native void tagResourceResultEvent(long clientHandle, long customData, int httpStatusCode)
            throws ProducerException;

    /**
     * Sends create stream result event to SDK
     *
     * @param clientHandle client handle
     * @param streamHandle stream handle
     * @param httpStatusCode HTTP status code from Kinesis Video service for createStream API
     * @param streamArn Stream ARN on success
     * @throws ProducerException
     */
    private native void createStreamResultEvent(long clientHandle, long streamHandle, int httpStatusCode, final @Nullable String streamArn)
            throws ProducerException;

    /**
     * Get MKV stream data from the frame buffer.
     *
     * @param clientHandle the handle of the client
     * @param streamHandle the handle of the stream
     * @param fillBuffer the buffer to fill
     * @param offset the offset into the buffer to fill
     * @param length the length of the buffer to fill
     * @param readResult the result of the read operation
     * @throws ProducerException
     */
    private native void getKinesisVideoStreamData(long clientHandle, long streamHandle, long uploadHandle,
                                                  final @Nonnull byte[] fillBuffer, int offset,
                                                  int length, final @Nonnull ReadResult readResult)
            throws ProducerException;

    /**
     * Stream format has changed. Currently, only CPD is supported
     *
     * @param clientHandle the handle of the client
     * @param streamHandle the handle of the stream
     * @param codecPrivateData Codec Private Data bits or null to reset
     * @param trackId Track Id of the changed CPD belongs to
     * @throws ProducerException
     */
    private native void kinesisVideoStreamFormatChanged(long clientHandle, long streamHandle,
                                                        final @Nullable byte[] codecPrivateData, long trackId)
            throws ProducerException;

    /**
     * Sends create device result event to SDK
     *
     * @param clientHandle client handle
     * @param deviceHandle device handle
     * @param httpStatusCode HTTP status code from Kinesis Video service for createStream API
     * @param deviceArn device ARN if successful.
     * @throws ProducerException
     */
    private native void createDeviceResultEvent(long clientHandle, long deviceHandle, int httpStatusCode,
                                                final @Nullable String deviceArn) throws ProducerException;

    /**
     * Sends device certificate to token exchange result event to SDK
     *
     * @param clientHandle client handle
     * @param deviceHandle device object handle
     * @param httpStatusCode HTTP status code from Kinesis Video service for getStreamingToken API
     * @param token streaming token
     * @param tokenSize token size
     * @param tokenExpiration token expiration time in 100ns absolute time
     * @throws ProducerException
     */
    private native void deviceCertToTokenResultEvent(long clientHandle, long deviceHandle, int httpStatusCode,
                                                     final @Nullable byte[] token, int tokenSize, long tokenExpiration)
            throws ProducerException;

    /**
     * Reports an abnormal stream termination.
     *
     * @param clientHandle the handle of the client
     * @param streamHandle the handle of the stream
     * @param uploadHandle the client stream upload handle
     * @param statusCode the status code of the termination event
     * @throws ProducerException
     */
    private native void kinesisVideoStreamTerminated(long clientHandle, long streamHandle, long uploadHandle,
                                                     int statusCode)
            throws ProducerException;

    /**
     * Overrides malloc and free with a custom one for tracking purposes.
     */
    public static native void setupInstrumentedAllocators();

    /**
     * Return the current value of the allocated memory.
     * Note: Due to lazy memory allocation, this may not accurately reflect the current RSS usage.
     *
     * @return the current memory currently malloc'd by the native codebase in bytes.
     */
    public native long getCurrentAllocationBytes();
}
