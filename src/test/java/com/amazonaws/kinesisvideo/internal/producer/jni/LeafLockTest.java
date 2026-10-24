package com.amazonaws.kinesisvideo.internal.producer.jni;

import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.producer.ClientInfo;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StorageInfo;
import com.amazonaws.kinesisvideo.producer.StreamDescription;
import com.amazonaws.kinesisvideo.producer.StreamStatus;
import com.amazonaws.kinesisvideo.producer.Time;
import com.amazonaws.kinesisvideo.storage.DefaultStorageCallbacks;
import com.amazonaws.kinesisvideo.streaming.DefaultStreamCallbacks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.HTTP_OK;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RETENTION_ONE_HOUR;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VERSION_ZERO;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VIDEO_CONTENT_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * No Java lock is held across a native call: stream creation no longer runs one stream at a time, and service results
 * are let in by the call gates, which drop the ones that arrive for a stream or client that has been freed. Runs fully
 * offline.
 */
public class LeafLockTest
{
    private static final Logger log = LogManager.getLogger(LeafLockTest.class);
    private static final long WAIT_MS = 10000;
    private static final int STATUS_DUPLICATE_STREAM_NAME = 0x5200004b;

    private ExecutorService mServiceExecutor;
    private ExecutorService mWorkers;

    @Before
    public void setUp()
    {
        assumeTrue("Needs the KinesisVideoProducerJNI library on java.library.path", OfflineCallbacks.isJniLoaded());
        mServiceExecutor = Executors.newFixedThreadPool(2, LeafLockTest::daemon);
        mWorkers = Executors.newCachedThreadPool(LeafLockTest::daemon);
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
    public void streamsCanBeCreatedConcurrently() throws Exception
    {
        final NativeKinesisVideoProducerJni producer = createProducer(new OfflineCallbacks.Service(mServiceExecutor, mWorkers), 8);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<KinesisVideoProducerStream>> created = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            final String name = "leaf-concurrent-" + i;
            created.add(mWorkers.submit(() -> {
                start.await();
                return producer.createStreamSync(OfflineCallbacks.streamInfo(name), new DefaultStreamCallbacks());
            }));
        }
        start.countDown();
        for (final Future<KinesisVideoProducerStream> stream : created) {
            assertNotNull(stream.get(WAIT_MS, TimeUnit.MILLISECONDS));
        }

        producer.freeStreams();
        producer.free();
    }

    /**
     * The first createStream for a name is still inside the native call (its DescribeStream callback is held) when a
     * second one for the same name starts: the second is rejected instead of corrupting the first one's state.
     */
    @Test(timeout = 60000)
    public void duplicateConcurrentCreateIsRejected() throws Exception
    {
        final CountDownLatch inDescribe = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final OfflineCallbacks.Service callbacks = new OfflineCallbacks.Service(mServiceExecutor, mWorkers) {
            @Override
            public void describeStream(final String streamName, final long callAfter, final long timeout,
                                       @Nullable final byte[] authData, final int authType, final long streamHandle,
                                       final KinesisVideoProducerStream stream)
            {
                inDescribe.countDown();
                try {
                    release.await();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                super.describeStream(streamName, callAfter, timeout, authData, authType, streamHandle, stream);
            }
        };
        final NativeKinesisVideoProducerJni producer = createProducer(callbacks, 2);
        final Future<KinesisVideoProducerStream> first = mWorkers.submit(
                () -> producer.createStreamSync(OfflineCallbacks.streamInfo("leaf-duplicate"), new DefaultStreamCallbacks()));
        assertTrue("The first createStream never reached DescribeStream", inDescribe.await(WAIT_MS, TimeUnit.MILLISECONDS));

        try {
            producer.createStream(OfflineCallbacks.streamInfo("leaf-duplicate"), new DefaultStreamCallbacks());
            fail("A second createStream for a stream that is being created must be rejected");
        } catch (final ProducerException expected) {
            // Rejected by the pending-stream check while the first call is inside the native call, or by PIC itself
            // (duplicate stream name) once the first call has returned and its DescribeStream runs elsewhere
            assertTrue(expected.getMessage(), expected.getMessage().contains("already being created")
                    || expected.getStatusCode() == STATUS_DUPLICATE_STREAM_NAME);
        } finally {
            release.countDown();
        }

        assertNotNull(first.get(WAIT_MS, TimeUnit.MILLISECONDS));
        producer.freeStreams();
        producer.free();
    }

    /**
     * Results that arrive after their stream was freed, holding the stale stream object or only its stale handle, never
     * reach PIC. Run with the JNI built with ASan to check that nothing touches freed memory.
     */
    @Test(timeout = 60000)
    public void resultsForAFreedStreamAreDropped() throws Exception
    {
        final NativeKinesisVideoProducerJni producer = createProducer(new OfflineCallbacks.Service(mServiceExecutor, mWorkers), 2);
        final KinesisVideoProducerStream stream = producer.createStreamSync(OfflineCallbacks.streamInfo("leaf-late"),
                new DefaultStreamCallbacks());
        final long staleHandle = stream.getStreamHandle();
        producer.freeStream(stream);

        final StreamDescription description = new StreamDescription(VERSION_ZERO, OfflineCallbacks.OFFLINE_DEVICE_NAME,
                "leaf-late", VIDEO_CONTENT_TYPE, "1", "arn:aws:kinesisvideo:us-west-2:123456789012:stream/leaf-late/1",
                StreamStatus.ACTIVE, System.currentTimeMillis() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND, RETENTION_ONE_HOUR, "");
        producer.describeStreamResult(stream, staleHandle, description, HTTP_OK);
        producer.describeStreamResult(null, staleHandle, description, HTTP_OK);
        producer.getStreamingEndpointResult(null, staleHandle, "https://localhost", HTTP_OK);
        producer.getStreamingTokenResult(null, staleHandle, "token".getBytes(StandardCharsets.UTF_8),
                OfflineCallbacks.expiresInOneHour(), HTTP_OK);
        producer.tagResourceResult(null, staleHandle, HTTP_OK);
        producer.putStreamResult(stream, 1, HTTP_OK);
        producer.streamTerminated(staleHandle, 1, HTTP_OK);
        producer.stopStream(staleHandle);

        producer.free();
    }

    /**
     * Result events and stops race with free(). The client gate lets each one either finish before the client is freed
     * or not start at all.
     */
    @Test(timeout = 120000)
    public void resultEventsDuringFreeAreSafe() throws Exception
    {
        for (int iteration = 0; iteration < 20; iteration++) {
            final NativeKinesisVideoProducerJni producer = createProducer(new OfflineCallbacks.Service(mServiceExecutor, mWorkers), 2);
            final KinesisVideoProducerStream stream = producer.createStreamSync(
                    OfflineCallbacks.streamInfo("leaf-free-" + iteration), new DefaultStreamCallbacks());
            final long handle = stream.getStreamHandle();
            final AtomicBoolean stop = new AtomicBoolean();
            final AtomicInteger unexpected = new AtomicInteger();
            final List<Future<?>> callers = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                final int kind = i;
                callers.add(mWorkers.submit(() -> {
                    while (!stop.get()) {
                        try {
                            if (kind % 3 == 0) {
                                producer.tagResourceResult(null, handle, HTTP_OK);
                            } else if (kind % 3 == 1) {
                                producer.getStreamingEndpointResult(stream, handle, "https://localhost", HTTP_OK);
                            } else {
                                producer.getMetrics();
                            }
                        } catch (final ProducerException | IllegalStateException expected) {
                            // PIC rejects a result it is not waiting for; getMetrics after free is rejected
                        } catch (final Throwable t) {
                            log.error("Unexpected error", t);
                            unexpected.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            Thread.sleep(30);
            producer.free();
            stop.set(true);
            for (final Future<?> caller : callers) {
                caller.get(WAIT_MS, TimeUnit.MILLISECONDS);
            }
            assertEquals("Unexpected errors in iteration " + iteration, 0, unexpected.get());
        }
    }

    private static NativeKinesisVideoProducerJni createProducer(final OfflineCallbacks.Service serviceCallbacks,
                                                                final int streamCount) throws ProducerException
    {
        final NativeKinesisVideoProducerJni producer = new NativeKinesisVideoProducerJni(new OfflineCallbacks.Auth(),
                new DefaultStorageCallbacks(), serviceCallbacks, log);
        producer.createSync(new DeviceInfo(VERSION_ZERO, "leaf-lock-test",
                new StorageInfo(0, StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, 64 * 1024 * 1024, 90, "/tmp"),
                streamCount, null, "JNI " + NativeKinesisVideoProducerJni.EXPECTED_LIBRARY_VERSION, new ClientInfo()));
        return producer;
    }

    private static Thread daemon(final Runnable runnable)
    {
        final Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    }
}
