package com.amazonaws.kinesisvideo.internal.producer.jni;

import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.producer.ClientInfo;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFragmentAck;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StorageInfo;
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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_KEY_FRAME;
import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_NONE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.HTTP_OK;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VERSION_ZERO;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * stopStreamAsync() and close(). Runs fully offline.
 */
public class AsyncStopCloseTest
{
    private static final Logger log = LogManager.getLogger(AsyncStopCloseTest.class);
    private static final long WAIT_MS = 10000;
    private static final long FRAME_DURATION = 20 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

    private ExecutorService mServiceExecutor;
    private ExecutorService mWorkers;

    @Before
    public void setUp()
    {
        assumeTrue("Needs the KinesisVideoProducerJNI library on java.library.path", OfflineCallbacks.isJniLoaded());
        mServiceExecutor = Executors.newFixedThreadPool(2, AsyncStopCloseTest::daemon);
        mWorkers = Executors.newCachedThreadPool(AsyncStopCloseTest::daemon);
    }

    @After
    public void tearDown() throws Exception
    {
        mWorkers.shutdownNow();
        mServiceExecutor.shutdownNow();
        mWorkers.awaitTermination(5, TimeUnit.SECONDS);
        mServiceExecutor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test(timeout = 60000)
    public void stopStreamAsyncCompletesWhenTheStreamCloses() throws Exception
    {
        final NativeKinesisVideoProducerJni producer = createProducer(new OfflineCallbacks.Service(mServiceExecutor, mWorkers));
        final KinesisVideoProducerStream stream = producer.createStreamSync(
                OfflineCallbacks.streamInfo("async-stop", false, false), new DefaultStreamCallbacks());
        putFrames(stream, 50);

        final long start = System.currentTimeMillis();
        final CompletableFuture<Void> closed = stream.stopStreamAsync();
        final long returnedMs = System.currentTimeMillis() - start;
        closed.get(WAIT_MS, TimeUnit.MILLISECONDS);

        assertTrue("stopStreamAsync waited " + returnedMs + " ms", returnedMs < 500);
        producer.close();
    }

    /**
     * From an application callback, stopStreamAsync() returns at once and the stream still closes.
     */
    @Test(timeout = 60000)
    public void stopStreamAsyncFromACallbackReturnsAtOnce() throws Exception
    {
        final UploadTracking service = new UploadTracking(mServiceExecutor, mWorkers);
        final NativeKinesisVideoProducerJni producer = createProducer(service);
        final AtomicReference<KinesisVideoProducerStream> streamRef = new AtomicReference<>();
        final AtomicReference<CompletableFuture<Void>> closedRef = new AtomicReference<>();
        final AtomicLong returnedMs = new AtomicLong(-1);
        final CountDownLatch called = new CountDownLatch(1);
        final KinesisVideoProducerStream stream = producer.createStreamSync(
                OfflineCallbacks.streamInfo("async-stop-callback", false, false), new DefaultStreamCallbacks() {
                    @Override
                    public void fragmentAckReceived(final long uploadHandle, @Nonnull final KinesisVideoFragmentAck ack)
                    {
                        final long start = System.currentTimeMillis();
                        closedRef.set(streamRef.get().stopStreamAsync());
                        returnedMs.set(System.currentTimeMillis() - start);
                        called.countDown();
                    }
                });
        streamRef.set(stream);
        putFrames(stream, 50);
        final Long uploadHandle = service.awaitUploadHandle("async-stop-callback");
        assertNotNull(uploadHandle);

        stream.parseFragmentAck(uploadHandle, "{\"EventType\":\"BUFFERING\",\"FragmentTimecode\":0,\"FragmentNumber\":\"1\"}");
        assertTrue("fragmentAckReceived was never called", called.await(WAIT_MS, TimeUnit.MILLISECONDS));
        assertTrue("stopStreamAsync from a callback waited " + returnedMs.get() + " ms", returnedMs.get() < 500);
        closedRef.get().get(WAIT_MS, TimeUnit.MILLISECONDS);

        producer.close();
    }

    @Test(timeout = 60000)
    public void closeIsIdempotent() throws Exception
    {
        final NativeKinesisVideoProducerJni producer;
        try (NativeKinesisVideoProducerJni p = createProducer(new OfflineCallbacks.Service(mServiceExecutor, mWorkers))) {
            producer = p;
            p.createStreamSync(OfflineCallbacks.streamInfo("close-once"), new DefaultStreamCallbacks());
        }
        assertFalse(producer.isInitialized());
        producer.close();
        producer.close();
    }

    @Test(timeout = 60000)
    public void concurrentClosesFreeTheClientOnce() throws Exception
    {
        for (int iteration = 0; iteration < 10; iteration++) {
            final NativeKinesisVideoProducerJni producer = createProducer(new OfflineCallbacks.Service(mServiceExecutor, mWorkers));
            producer.createStreamSync(OfflineCallbacks.streamInfo("close-concurrent-" + iteration), new DefaultStreamCallbacks());
            final CountDownLatch start = new CountDownLatch(1);
            final List<Future<?>> closers = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                closers.add(mWorkers.submit(() -> {
                    start.await();
                    producer.close();
                    return null;
                }));
            }
            start.countDown();
            for (final Future<?> closer : closers) {
                closer.get(WAIT_MS, TimeUnit.MILLISECONDS);
            }
            assertFalse(producer.isInitialized());
        }
    }

    private static void putFrames(final KinesisVideoProducerStream stream, final int count) throws ProducerException
    {
        final ByteBuffer data = ByteBuffer.wrap(new byte[512]);
        final long start = System.currentTimeMillis() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
        for (int index = 0; index < count; index++) {
            final long timestamp = start + index * FRAME_DURATION;
            stream.putFrame(new KinesisVideoFrame(index, index % 25 == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE,
                    timestamp, timestamp, FRAME_DURATION, data));
        }
    }

    private static final class UploadTracking extends OfflineCallbacks.Service
    {
        private final Map<String, Long> mUploadHandles = new ConcurrentHashMap<String, Long>();
        private final AtomicLong mNextUploadHandle = new AtomicLong(100);

        UploadTracking(final ExecutorService executor, final ExecutorService uploaders)
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

        Long awaitUploadHandle(final String streamName) throws InterruptedException
        {
            final long deadline = System.currentTimeMillis() + WAIT_MS;
            while (!mUploadHandles.containsKey(streamName) && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            return mUploadHandles.get(streamName);
        }
    }

    private static NativeKinesisVideoProducerJni createProducer(final OfflineCallbacks.Service serviceCallbacks)
            throws ProducerException
    {
        final NativeKinesisVideoProducerJni producer = new NativeKinesisVideoProducerJni(new OfflineCallbacks.Auth(),
                new DefaultStorageCallbacks(), serviceCallbacks, log);
        producer.createSync(new DeviceInfo(VERSION_ZERO, "async-stop-close-test",
                new StorageInfo(0, StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, 64 * 1024 * 1024, 90, "/tmp"),
                2, null, "JNI " + NativeKinesisVideoProducerJni.EXPECTED_LIBRARY_VERSION, new ClientInfo()));
        return producer;
    }

    private static Thread daemon(final Runnable runnable)
    {
        final Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    }
}
