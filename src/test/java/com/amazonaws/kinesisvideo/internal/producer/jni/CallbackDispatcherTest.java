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
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_KEY_FRAME;
import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_NONE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.HTTP_OK;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VERSION_ZERO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Application and service callbacks run on the client's callback thread, after the native call that raised them has
 * moved on. Runs fully offline.
 */
public class CallbackDispatcherTest
{
    private static final Logger log = LogManager.getLogger(CallbackDispatcherTest.class);
    private static final long WAIT_MS = 10000;
    private static final long FRAME_DURATION = 20 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

    private ExecutorService mServiceExecutor;
    private ExecutorService mWorkers;

    @Before
    public void setUp()
    {
        assumeTrue("Needs the KinesisVideoProducerJNI library on java.library.path", OfflineCallbacks.isJniLoaded());
        mServiceExecutor = Executors.newFixedThreadPool(2, CallbackDispatcherTest::daemon);
        mWorkers = Executors.newCachedThreadPool(CallbackDispatcherTest::daemon);
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
    public void applicationAndServiceCallbacksRunOnTheCallbackThread() throws Exception
    {
        final Set<String> serviceThreads = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
        final OfflineCallbacks.Service service = new OfflineCallbacks.Service(mServiceExecutor, mWorkers) {
            @Override
            public void getStreamingToken(@Nonnull final String streamName, final long callAfter, final long timeout,
                                          @Nullable final byte[] authData, final int authType, final long streamHandle,
                                          final KinesisVideoProducerStream stream)
            {
                serviceThreads.add(Thread.currentThread().getName());
                super.getStreamingToken(streamName, callAfter, timeout, authData, authType, streamHandle, stream);
            }
        };
        final NativeKinesisVideoProducerJni producer = createProducer(service);
        final Set<String> appThreads = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
        final CountDownLatch ready = new CountDownLatch(3);
        for (int i = 0; i < 3; i++) {
            producer.createStreamSync(OfflineCallbacks.streamInfo("dispatch-thread-" + i), new DefaultStreamCallbacks() {
                @Override
                public void streamReady()
                {
                    appThreads.add(Thread.currentThread().getName());
                    ready.countDown();
                }
            });
        }
        assertTrue(ready.await(WAIT_MS, TimeUnit.MILLISECONDS));
        assertEquals(new HashSet<String>(Collections.singleton("KinesisVideoProducer-callbacks")), appThreads);
        assertEquals(new HashSet<String>(Collections.singleton("KinesisVideoProducer-callbacks")), serviceThreads);

        producer.freeStreams();
        producer.free();
    }

    /**
     * The application's fragmentAckReceived takes 3 s. The ack thread, and the client's other stream, carry on.
     */
    @Test(timeout = 60000)
    public void aSlowApplicationCallbackDoesNotStallTheClient() throws Exception
    {
        final UploadTracking service = new UploadTracking(mServiceExecutor, mWorkers);
        final NativeKinesisVideoProducerJni producer = createProducer(service);
        final CountDownLatch inCallback = new CountDownLatch(1);
        final KinesisVideoProducerStream acked = producer.createStreamSync(OfflineCallbacks.streamInfo("dispatch-slow"),
                new DefaultStreamCallbacks() {
                    @Override
                    public void fragmentAckReceived(final long uploadHandle, @Nonnull final KinesisVideoFragmentAck ack)
                    {
                        inCallback.countDown();
                        try {
                            Thread.sleep(3000);
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
        final KinesisVideoProducerStream other = producer.createStreamSync(OfflineCallbacks.streamInfo("dispatch-other"),
                new DefaultStreamCallbacks());
        final AtomicBoolean stop = new AtomicBoolean();
        final AtomicLong longestGapMs = new AtomicLong();
        mWorkers.execute(() -> publish(acked, stop, new AtomicLong()));
        mWorkers.execute(() -> publish(other, stop, longestGapMs));
        final Long uploadHandle = service.awaitUploadHandle("dispatch-slow");
        assertNotNull(uploadHandle);
        Thread.sleep(500);
        longestGapMs.set(0);

        final long start = System.currentTimeMillis();
        acked.parseFragmentAck(uploadHandle, "{\"EventType\":\"BUFFERING\",\"FragmentTimecode\":0,\"FragmentNumber\":\"1\"}");
        final long parseMs = System.currentTimeMillis() - start;
        assertTrue("The application callback never ran", inCallback.await(WAIT_MS, TimeUnit.MILLISECONDS));
        Thread.sleep(3200);
        stop.set(true);

        assertTrue("parseFragmentAck waited " + parseMs + " ms for the application callback", parseMs < 1000);
        assertTrue("The other stream accepted no frame for " + longestGapMs.get() + " ms", longestGapMs.get() < 1000);
        producer.freeStreams();
        producer.free();
    }

    /**
     * ServiceCallbacks.getStreamingToken throws the first time. PIC is told the call failed and asks again, so the
     * stream still becomes ready.
     */
    @Test(timeout = 90000)
    public void aFailedServiceCallIsReportedAndRetried() throws Exception
    {
        final AtomicInteger calls = new AtomicInteger();
        final OfflineCallbacks.Service service = new OfflineCallbacks.Service(mServiceExecutor, mWorkers) {
            @Override
            public void getStreamingToken(@Nonnull final String streamName, final long callAfter, final long timeout,
                                          @Nullable final byte[] authData, final int authType, final long streamHandle,
                                          final KinesisVideoProducerStream stream)
            {
                if (calls.incrementAndGet() == 1) {
                    throw new IllegalStateException("token service unavailable");
                }
                super.getStreamingToken(streamName, callAfter, timeout, authData, authType, streamHandle, stream);
            }
        };
        final NativeKinesisVideoProducerJni producer = createProducer(service);
        producer.createStreamSync(OfflineCallbacks.streamInfo("dispatch-retry"), new DefaultStreamCallbacks());
        assertTrue("PIC never asked again for the token, calls: " + calls.get(), calls.get() >= 2);

        producer.freeStreams();
        producer.free();
    }

    private static void publish(final KinesisVideoProducerStream stream, final AtomicBoolean stop, final AtomicLong longestGapMs)
    {
        final ByteBuffer data = ByteBuffer.wrap(new byte[512]);
        final long start = System.currentTimeMillis() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
        long lastAccepted = System.currentTimeMillis();
        for (int index = 0; !stop.get(); index++) {
            final long timestamp = start + index * FRAME_DURATION;
            try {
                stream.putFrame(new KinesisVideoFrame(index, index % 25 == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE,
                        timestamp, timestamp, FRAME_DURATION, data));
                final long now = System.currentTimeMillis();
                longestGapMs.accumulateAndGet(now - lastAccepted, Math::max);
                lastAccepted = now;
                Thread.sleep(20);
            } catch (final ProducerException | IllegalStateException | IllegalArgumentException | InterruptedException e) {
                return;
            }
        }
    }

    /**
     * Like the offline callbacks, and remembers the upload handle each stream gets from PutStream
     */
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
        producer.createSync(new DeviceInfo(VERSION_ZERO, "callback-dispatcher-test",
                new StorageInfo(0, StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, 64 * 1024 * 1024, 90, "/tmp"),
                4, null, "JNI " + NativeKinesisVideoProducerJni.EXPECTED_LIBRARY_VERSION, new ClientInfo()));
        return producer;
    }

    private static Thread daemon(final Runnable runnable)
    {
        final Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    }
}
