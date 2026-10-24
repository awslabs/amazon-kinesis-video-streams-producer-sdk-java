package com.amazonaws.kinesisvideo.internal.producer.jni;

import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.producer.ClientInfo;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFragmentAck;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StorageInfo;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;
import com.amazonaws.kinesisvideo.producer.Time;
import com.amazonaws.kinesisvideo.storage.DefaultStorageCallbacks;
import com.amazonaws.kinesisvideo.streaming.DefaultStreamCallbacks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_KEY_FRAME;
import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_NONE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.HTTP_OK;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VERSION_ZERO;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * Java locks and the native client lock must always be taken in the same order, whichever thread PIC calls back on.
 *
 * In production each interleaving depends on timing. The tests pin it, either by holding one of the producer's Java
 * locks themselves until both threads are waiting on it, or by pausing inside a service callback. Runs fully offline.
 */
public class CallbackLockOrderTest
{
    private static final Logger log = LogManager.getLogger(CallbackLockOrderTest.class);
    private static final String DEVICE_NAME = "callback-lock-order-test";
    private static final String READY_STREAM = "lock-order-becomes-ready";
    private static final String CREATED_STREAM = "lock-order-gets-created";
    private static final String SYNC_PUT_STREAM = "lock-order-synchronous-put-stream";
    private static final String OTHER_STREAM = "lock-order-other";
    private static final String ACKED_STREAM = "lock-order-stops-on-ack";
    private static final long FRAME_DURATION = 20 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
    private static final int HTTP_NOT_FOUND = 404;
    private static final long WAIT_MS = 10000;

    private ExecutorService mServiceExecutor;
    private ExecutorService mWorkers;

    @Before
    public void setUp()
    {
        assumeTrue("Needs the KinesisVideoProducerJNI library on java.library.path",
                OfflineCallbacks.isJniLoaded());
        mServiceExecutor = Executors.newFixedThreadPool(2, CallbackLockOrderTest::daemon);
        mWorkers = Executors.newCachedThreadPool(CallbackLockOrderTest::daemon);
    }

    @After
    public void tearDown() throws Exception
    {
        if (mWorkers != null) {
            mWorkers.shutdownNow();
            mWorkers.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (mServiceExecutor != null) {
            mServiceExecutor.shutdownNow();
            mServiceExecutor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /**
     * Thread A delivers the streaming token of one stream. It holds mSyncObject and the native client
     * lock while PIC
     * moves the stream to READY and calls streamReady, which takes mCallbackSyncObject. Thread B delivers the
     * CreateStream result of another stream. If createStreamResult holds mCallbackSyncObject while it calls into
     * native
     * code, B holds the lock A is waiting for and waits for the native client lock that A holds.
     */
    @Test(timeout = 60000)
    public void createStreamResultDoesNotDeadlockWithStreamReady() throws Exception
    {
        final HoldingServiceCallbacks serviceCallbacks = new HoldingServiceCallbacks(mServiceExecutor, mWorkers);
        final NativeKinesisVideoProducerJni producer = createProducer(serviceCallbacks);

        // One stream stops just before READY, the other one waits for its CreateStream result
        producer.createStream(OfflineCallbacks.streamInfo(READY_STREAM, false), new DefaultStreamCallbacks());
        producer.createStream(OfflineCallbacks.streamInfo(CREATED_STREAM, true), new DefaultStreamCallbacks());
        final Runnable deliverToken = serviceCallbacks.mHeldToken.poll(WAIT_MS, TimeUnit.MILLISECONDS);
        final Runnable deliverCreateResult = serviceCallbacks.mHeldCreateResult.poll(WAIT_MS, TimeUnit.MILLISECONDS);
        assertNotNull("The ready stream never asked for a streaming token", deliverToken);
        assertNotNull("The created stream never asked for CreateStream", deliverCreateResult);

        final Thread a = daemon(deliverToken);
        a.setName("A-getStreamingTokenResult");
        final Thread b = daemon(deliverCreateResult);
        b.setName("B-createStreamResult");

        synchronized (lockOf(producer, "mCallbackSyncObject")) {
            a.start();
            awaitBlocked(a, "streamReady");
            b.start();
            awaitBlocked(b, "createStreamResult");
        }

        a.join(WAIT_MS);
        b.join(WAIT_MS);
        if (a.isAlive() || b.isAlive()) {
            fail("Deadlock between the token result and createStreamResult:\n" + describe(a) + describe(b));
        }

        producer.freeStreams();
        producer.free();
    }

    /**
     * A ServiceCallbacks implementation answers PutStream synchronously, from inside the callback. The first putFrame
     * after a stream is ready makes PIC call putStream on the putFrame thread, which holds the native client lock but
     * not mSyncObject, and putStreamResult then needs mSyncObject. Thread Q stops another
     * stream while the callback is
     * still working out its answer: Q holds mSyncObject and needs the native client lock. A result
     * delivered from inside
     * a callback now runs after the callback returns.
     */
    @Test(timeout = 60000)
    public void synchronousResultFromAPutFrameCallbackDoesNotDeadlock() throws Exception
    {
        final SynchronousPutStreamCallbacks serviceCallbacks = new SynchronousPutStreamCallbacks(mServiceExecutor, mWorkers);
        final NativeKinesisVideoProducerJni producer = createProducer(serviceCallbacks);
        final KinesisVideoProducerStream syncStream = producer.createStreamSync(
                OfflineCallbacks.streamInfo(SYNC_PUT_STREAM, false), new DefaultStreamCallbacks());
        final KinesisVideoProducerStream otherStream = producer.createStreamSync(
                OfflineCallbacks.streamInfo(OTHER_STREAM, false), new DefaultStreamCallbacks());

        final Thread p = daemon(() -> {
            final long now = System.currentTimeMillis() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
            try {
                syncStream.putFrame(new KinesisVideoFrame(0, FRAME_FLAG_KEY_FRAME, now, now,
                        20 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND, ByteBuffer.wrap(new byte[512])));
            } catch (final ProducerException e) {
                log.warn("putFrame failed", e);
            }
        });
        p.setName("P-putFrame");
        final Thread q = daemon(() -> {
            try {
                producer.stopStream(otherStream.getStreamHandle());
            } catch (final ProducerException e) {
                log.warn("stopStream failed", e);
            }
        });
        q.setName("Q-stopStream");

        p.start();
        assertTrue("PIC never called putStream", serviceCallbacks.mInPutStream.await(WAIT_MS, TimeUnit.MILLISECONDS));
        q.start();
        awaitIn(q, "stopKinesisVideoStream");
        serviceCallbacks.mAnswer.countDown();

        p.join(WAIT_MS);
        q.join(WAIT_MS);
        if (p.isAlive() || q.isAlive()) {
            fail("Deadlock between a synchronous putStreamResult and stopStream:\n" + describe(p) + describe(q));
        }

        producer.freeStreams();
        producer.free();
    }

    /**
     * The DefaultStreamCallbacks Javadoc suggests calling mediaSource.stop() from fragmentAckReceived on an error ack, and
     * the SDK's media sources implement stop() with stopStreamSync(). PIC calls fragmentAckReceived on the thread that
     * delivers the ack, inside parseFragmentAck, so that thread holds the native client lock while stopStreamSync() waits
     * for the stream to close. Closing needs getStreamData, which needs the same lock, so the wait could only time out,
     * with every other stream of the client blocked until it did. From inside a callback it now only starts the stop.
     */
    @Test(timeout = 120000)
    public void stoppingAStreamFromItsAckCallbackDoesNotStallTheOtherStreams() throws Exception
    {
        final UploadTrackingCallbacks serviceCallbacks = new UploadTrackingCallbacks(mServiceExecutor, mWorkers);
        final NativeKinesisVideoProducerJni producer = createProducer(serviceCallbacks);
        final AtomicReference<KinesisVideoProducerStream> streamToStop = new AtomicReference<KinesisVideoProducerStream>();
        final AtomicLong stopMillis = new AtomicLong(-1);
        final StreamCallbacks stopOnAck = new DefaultStreamCallbacks() {
            @Override
            public void fragmentAckReceived(final long uploadHandle, @Nonnull final KinesisVideoFragmentAck fragmentAck)
                    throws ProducerException
            {
                final long start = System.currentTimeMillis();
                streamToStop.get().stopStreamSync();
                stopMillis.set(System.currentTimeMillis() - start);
            }
        };
        final KinesisVideoProducerStream acked = producer.createStreamSync(
                OfflineCallbacks.streamInfo(ACKED_STREAM, false), stopOnAck);
        streamToStop.set(acked);
        final KinesisVideoProducerStream other = producer.createStreamSync(
                OfflineCallbacks.streamInfo(OTHER_STREAM, false), new DefaultStreamCallbacks());

        final AtomicBoolean stop = new AtomicBoolean();
        final AtomicLong longestGapMs = new AtomicLong();
        final Thread ackedPublisher = daemon(() -> publish(acked, stop, new AtomicLong()));
        final Thread otherPublisher = daemon(() -> publish(other, stop, longestGapMs));
        ackedPublisher.start();
        otherPublisher.start();

        final long deadline = System.currentTimeMillis() + WAIT_MS;
        while (!serviceCallbacks.mUploadHandles.containsKey(ACKED_STREAM) && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        final Long uploadHandle = serviceCallbacks.mUploadHandles.get(ACKED_STREAM);
        assertNotNull("PutStream was never called for " + ACKED_STREAM, uploadHandle);
        Thread.sleep(500);
        longestGapMs.set(0);

        // What AckConsumer does with every ack it reads from the PutMedia response
        final Thread ackThread = daemon(() -> {
            try {
                acked.parseFragmentAck(uploadHandle,
                        "{\"EventType\":\"BUFFERING\",\"FragmentTimecode\":0,\"FragmentNumber\":\"1\"}");
            } catch (final ProducerException e) {
                log.warn("parseFragmentAck failed", e);
            }
        });
        ackThread.setName("ack-consumer");
        ackThread.start();
        ackThread.join(60000);
        Thread.sleep(500);
        stop.set(true);
        ackedPublisher.join(WAIT_MS);
        otherPublisher.join(WAIT_MS);

        log.info("stopStreamSync from the ack callback took {} ms; the other stream accepted no frame for {} ms",
                stopMillis.get(), longestGapMs.get());
        assertTrue("stopStreamSync from the ack callback took " + stopMillis.get() + " ms and the other stream "
                + "accepted no frame for " + longestGapMs.get() + " ms", longestGapMs.get() < 2000);
        assertTrue("stopStreamSync from the ack callback waited " + stopMillis.get() + " ms",
                stopMillis.get() >= 0 && stopMillis.get() < 2000);

        producer.freeStreams();
        producer.free();
    }

    /**
     * PIC calls streamReady on the thread that delivers the streaming token, inside the native call and on the stream
     * PIC is still working on. Freeing from there would free the stream, or the client, under PIC, so it is rejected.
     */
    @Test(timeout = 60000)
    public void freeFromInsideACallbackIsRejected() throws Exception
    {
        final NativeKinesisVideoProducerJni producer = createProducer(new OfflineCallbacks.Service(mServiceExecutor, mWorkers));
        final AtomicReference<Throwable> fromFree = new AtomicReference<Throwable>();
        final AtomicReference<Throwable> fromFreeStreams = new AtomicReference<Throwable>();
        final CountDownLatch called = new CountDownLatch(1);
        final StreamCallbacks freeOnReady = new DefaultStreamCallbacks() {
            @Override
            public void streamReady() throws ProducerException
            {
                fromFree.set(attempt(producer::free));
                fromFreeStreams.set(attempt(producer::freeStreams));
                called.countDown();
            }
        };

        producer.createStreamSync(OfflineCallbacks.streamInfo(OTHER_STREAM, false), freeOnReady);
        assertTrue("streamReady was never called", called.await(WAIT_MS, TimeUnit.MILLISECONDS));
        assertTrue("free() from streamReady: " + fromFree.get(), fromFree.get() instanceof IllegalStateException);
        assertTrue("freeStreams() from streamReady: " + fromFreeStreams.get(),
                fromFreeStreams.get() instanceof IllegalStateException);

        // Both still work from a thread of the application's own
        producer.freeStreams();
        producer.free();
    }

    private interface ProducerCall
    {
        void call() throws ProducerException;
    }

    @Nullable
    private static Throwable attempt(final ProducerCall call)
    {
        try {
            call.call();
            return null;
        } catch (final Throwable t) {
            return t;
        }
    }

    /**
     * Puts frames in real time and records the longest wall-clock gap between two accepted frames.
     */
    private static void publish(final KinesisVideoProducerStream stream, final AtomicBoolean stop,
                                final AtomicLong longestGapMs)
    {
        final ByteBuffer data = ByteBuffer.wrap(new byte[512]);
        final long start = System.currentTimeMillis() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
        long lastAccepted = System.currentTimeMillis();
        for (int index = 0; !stop.get(); index++) {
            final long timestamp = start + index * FRAME_DURATION;
            final int flags = index % 25 == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE;
            try {
                stream.putFrame(new KinesisVideoFrame(index, flags, timestamp, timestamp, FRAME_DURATION, data));
                final long now = System.currentTimeMillis();
                longestGapMs.accumulateAndGet(now - lastAccepted, Math::max);
                lastAccepted = now;
                Thread.sleep(20);
            } catch (final ProducerException | IllegalStateException | IllegalArgumentException e) {
                // The stream has been stopped
                return;
            } catch (final InterruptedException e) {
                return;
            }
        }
    }

    private static NativeKinesisVideoProducerJni createProducer(
            final OfflineCallbacks.Service serviceCallbacks) throws ProducerException
    {
        final NativeKinesisVideoProducerJni producer = new NativeKinesisVideoProducerJni(
                new OfflineCallbacks.Auth(), new DefaultStorageCallbacks(), serviceCallbacks, log);
        final StorageInfo storageInfo = new StorageInfo(0, StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM,
                32 * 1024 * 1024, 90, "/tmp");
        producer.createSync(new DeviceInfo(VERSION_ZERO, DEVICE_NAME, storageInfo, 2, null,
                "JNI " + NativeKinesisVideoProducerJni.EXPECTED_LIBRARY_VERSION, new ClientInfo()));
        return producer;
    }

    /**
     * Answers PutStream for SYNC_PUT_STREAM on the calling thread, like a callbacks implementation that has the
     * result cached, once the test lets it. Nothing drains that stream; the test only needs the first frame.
     */
    private static final class SynchronousPutStreamCallbacks extends OfflineCallbacks.Service
    {
        final CountDownLatch mInPutStream = new CountDownLatch(1);
        final CountDownLatch mAnswer = new CountDownLatch(1);

        SynchronousPutStreamCallbacks(final ExecutorService executor, final ExecutorService uploaders)
        {
            super(executor, uploaders);
        }

        @Override
        public void putStream(@Nonnull final String streamName, @Nonnull final String containerType,
                              final long streamStartTime, final boolean absoluteFragmentTimes,
                              final boolean ackRequired, @Nonnull final String streamingEndpoint,
                              final long callAfter, final long timeout, @Nullable final byte[] authData,
                              final int authType, final KinesisVideoProducerStream stream)
        {
            if (SYNC_PUT_STREAM.equals(streamName)) {
                mInPutStream.countDown();
                try {
                    mAnswer.await();
                    mProducer.putStreamResult(stream, 1, HTTP_OK);
                } catch (final ProducerException | InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            } else {
                super.putStream(streamName, containerType, streamStartTime, absoluteFragmentTimes, ackRequired,
                        streamingEndpoint, callAfter, timeout, authData, authType, stream);
            }
        }
    }

    /**
     * Delivers results like the offline callbacks, except that it hands two of them to the test instead: the streaming
     * token of READY_STREAM and the CreateStream result of CREATED_STREAM. CREATED_STREAM is reported as not found so
     * that PIC asks to create it.
     */
    private static final class HoldingServiceCallbacks extends OfflineCallbacks.Service
    {
        final BlockingQueue<Runnable> mHeldToken = new LinkedBlockingQueue<Runnable>();
        final BlockingQueue<Runnable> mHeldCreateResult = new LinkedBlockingQueue<Runnable>();
        private boolean mTokenHeld;

        HoldingServiceCallbacks(final ExecutorService executor, final ExecutorService uploaders)
        {
            super(executor, uploaders);
        }

        @Override
        public void describeStream(@Nonnull final String streamName, final long callAfter, final long timeout,
                                   @Nullable final byte[] authData, final int authType, final long streamHandle,
                                   final KinesisVideoProducerStream stream)
        {
            if (CREATED_STREAM.equals(streamName)) {
                deliver(() -> mProducer.describeStreamResult(stream, streamHandle, null, HTTP_NOT_FOUND));
            } else {
                super.describeStream(streamName, callAfter, timeout, authData, authType, streamHandle, stream);
            }
        }

        @Override
        public void createStream(@Nonnull final String deviceName, @Nonnull final String streamName,
                                 @Nonnull final String contentType, @Nullable final String kmsKeyId,
                                 final long retentionPeriod, final long callAfter, final long timeout,
                                 @Nullable final byte[] authData, final int authType,
                                 @Nonnull final KinesisVideoProducerStream stream)
        {
            mHeldCreateResult.add(swallowing(() -> mProducer.createStreamResult(stream, arn(streamName), HTTP_OK)));
        }

        @Override
        public synchronized void getStreamingToken(@Nonnull final String streamName, final long callAfter,
                                                   final long timeout, @Nullable final byte[] authData,
                                                   final int authType, final long streamHandle,
                                                   final KinesisVideoProducerStream stream)
        {
            if (READY_STREAM.equals(streamName) && !mTokenHeld) {
                mTokenHeld = true;
                mHeldToken.add(swallowing(() -> mProducer.getStreamingTokenResult(stream, streamHandle,
                        "offline-token".getBytes(StandardCharsets.UTF_8),
                        OfflineCallbacks.expiresInOneHour(), HTTP_OK)));
            } else {
                super.getStreamingToken(streamName, callAfter, timeout, authData, authType, streamHandle, stream);
            }
        }

        private static Runnable swallowing(final Result result)
        {
            return () -> {
                try {
                    result.deliver();
                } catch (final ProducerException e) {
                    log.warn("Result delivery failed", e);
                }
            };
        }
    }

    /**
     * Like the offline callbacks, and remembers the upload handle each stream gets from PutStream.
     */
    private static final class UploadTrackingCallbacks extends OfflineCallbacks.Service
    {
        final Map<String, Long> mUploadHandles = new ConcurrentHashMap<String, Long>();
        private final AtomicLong mNextUploadHandle = new AtomicLong(100);

        UploadTrackingCallbacks(final ExecutorService executor, final ExecutorService uploaders)
        {
            super(executor, uploaders);
        }

        @Override
        public void putStream(@Nonnull final String streamName, @Nonnull final String containerType,
                              final long streamStartTime, final boolean absoluteFragmentTimes,
                              final boolean ackRequired, @Nonnull final String streamingEndpoint,
                              final long callAfter, final long timeout, @Nullable final byte[] authData,
                              final int authType, final KinesisVideoProducerStream stream)
        {
            final long uploadHandle = mNextUploadHandle.getAndIncrement();
            deliver(() -> {
                mProducer.putStreamResult(stream, uploadHandle, HTTP_OK);
                mUploadHandles.put(streamName, uploadHandle);
                mUploaders.execute(() -> drain(stream, uploadHandle));
            });
        }
    }

    private static Object lockOf(final NativeKinesisVideoProducerJni producer, final String fieldName) throws Exception
    {
        final Field field = NativeKinesisVideoProducerJni.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(producer);
    }

    /**
     * Waits until the thread is blocked on a monitor inside the given NativeKinesisVideoProducerJni method.
     */
    private static void awaitBlocked(final Thread thread, final String method) throws InterruptedException
    {
        final long deadline = System.currentTimeMillis() + WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (thread.getState() == Thread.State.BLOCKED && isIn(thread, method)) {
                return;
            }
            Thread.sleep(1);
        }
        fail(thread.getName() + " never blocked in " + method + ":\n" + describe(thread));
    }

    /**
     * Waits until the thread is inside the given native method, then gives it time to block on the native lock.
     */
    private static void awaitIn(final Thread thread, final String method) throws InterruptedException
    {
        final long deadline = System.currentTimeMillis() + WAIT_MS;
        while (!isIn(thread, method)) {
            if (System.currentTimeMillis() > deadline) {
                fail(thread.getName() + " never reached " + method + ":\n" + describe(thread));
            }
            Thread.sleep(1);
        }
        Thread.sleep(200);
    }

    private static boolean isIn(final Thread thread, final String method)
    {
        for (final StackTraceElement frame : thread.getStackTrace()) {
            if (frame.getClassName().equals(NativeKinesisVideoProducerJni.class.getName())
                    && frame.getMethodName().equals(method)) {
                return true;
            }
        }
        return false;
    }

    private static String describe(final Thread thread)
    {
        final ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(new long[] {thread.getId()}, true, true)[0];
        if (info == null) {
            return thread.getName() + ": finished\n";
        }
        final StringBuilder text = new StringBuilder(thread.getName()).append(": ").append(info.getThreadState());
        if (info.getLockName() != null) {
            text.append(" on ").append(info.getLockName()).append(" owned by ").append(info.getLockOwnerName());
        }
        text.append('\n');
        final StackTraceElement[] stack = info.getStackTrace();
        for (int i = 0; i < Math.min(stack.length, 12); i++) {
            text.append("    at ").append(stack[i]).append('\n');
        }
        return text.toString();
    }

    private static Thread daemon(final Runnable runnable)
    {
        final Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    }
}
