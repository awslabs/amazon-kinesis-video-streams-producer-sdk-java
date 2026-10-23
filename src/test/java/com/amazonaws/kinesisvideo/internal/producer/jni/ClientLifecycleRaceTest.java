package com.amazonaws.kinesisvideo.internal.producer.jni;

import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.producer.ClientInfo;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_KEY_FRAME;
import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_NONE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VERSION_ZERO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Frees streams and clients while other threads are inside native calls on them. Runs fully offline: the service
 * callbacks answer every service call locally, so no AWS account or network is used.
 *
 * Before the client lifecycle fix this test corrupts the native heap (run it with the JNI built with
 * -DADDRESS_SANITIZER=ON to see the heap-use-after-free reports). With the fix, freeing waits for in-flight calls and
 * later calls fail fast.
 */
public class ClientLifecycleRaceTest
{
    private static final Logger log = LogManager.getLogger(ClientLifecycleRaceTest.class);
    private static final String DEVICE_NAME = "client-lifecycle-race-test";
    private static final int STREAM_COUNT = 4;
    private static final int FRAME_SIZE_BYTES = 512;
    private static final int KEY_FRAME_INTERVAL = 25;
    private static final long FRAME_DURATION = 20 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
    private static final long THREAD_JOIN_TIMEOUT_MS = 20000;
    private static final int ITERATIONS = Integer.getInteger("kvs.lifecycleRace.iterations", 15);
    // Extra threads that call the metrics APIs, like putFrame used to on every key frame, and threads that keep the
    // CPUs busy so that callers get descheduled inside the race window, the way they do on a loaded host
    private static final int METRICS_POLLERS = Integer.getInteger("kvs.lifecycleRace.metricsPollers", 8);
    private static final int CPU_BURNERS = Integer.getInteger("kvs.lifecycleRace.cpuBurners", 0);

    private ExecutorService mServiceExecutor;
    private ExecutorService mWorkers;

    @Before
    public void setUp()
    {
        assumeTrue("Needs the KinesisVideoProducerJNI library on java.library.path", OfflineCallbacks.isJniLoaded());
        mServiceExecutor = Executors.newFixedThreadPool(2);
        mWorkers = Executors.newCachedThreadPool();
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
     * free() runs while publishers are inside putFrame and the upload threads are inside getStreamData.
     */
    @Test(timeout = 300000)
    public void freeClientWhileCallsAreInFlight() throws Exception
    {
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            final NativeKinesisVideoProducerJni producer = createProducer();
            final List<KinesisVideoProducerStream> streams = createStreams(producer, "free-client-" + iteration);
            final AtomicBoolean stop = new AtomicBoolean();
            final AtomicInteger unexpectedErrors = new AtomicInteger();
            final List<Future<?>> publishers = startPublishers(producer, streams, stop, new AtomicLong[STREAM_COUNT], unexpectedErrors);

            Thread.sleep(50);
            producer.free();

            stop.set(true);
            for (final Future<?> publisher : publishers) {
                publisher.get(THREAD_JOIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            }
            assertEquals("Unexpected publisher errors in iteration " + iteration, 0, unexpectedErrors.get());
        }
    }

    /**
     * One client, several streams: each stream is freed while its own publisher is still calling putFrame, and the
     * other streams have to keep accepting frames.
     */
    @Test(timeout = 300000)
    public void freeOneStreamWhileTheOtherStreamsKeepStreaming() throws Exception
    {
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            final NativeKinesisVideoProducerJni producer = createProducer();
            final List<KinesisVideoProducerStream> streams = createStreams(producer, "free-stream-" + iteration);
            final AtomicBoolean stop = new AtomicBoolean();
            final AtomicInteger unexpectedErrors = new AtomicInteger();
            final AtomicLong[] framesAccepted = new AtomicLong[STREAM_COUNT];
            final List<Future<?>> publishers = startPublishers(producer, streams, stop, framesAccepted, unexpectedErrors);

            Thread.sleep(50);
            for (int freed = 0; freed < STREAM_COUNT - 1; freed++) {
                producer.freeStream(streams.get(freed));

                // The streams that are still open must keep accepting frames
                final long[] before = snapshot(framesAccepted);
                for (int open = freed + 1; open < STREAM_COUNT; open++) {
                    assertTrue("Stream " + open + " stopped accepting frames after stream " + freed + " was freed",
                            waitForMoreFrames(framesAccepted[open], before[open]));
                }
            }

            stop.set(true);
            for (final Future<?> publisher : publishers) {
                publisher.get(THREAD_JOIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            }
            producer.freeStreams();
            producer.free();
            assertEquals("Unexpected publisher errors in iteration " + iteration, 0, unexpectedErrors.get());
        }
    }

    private NativeKinesisVideoProducerJni createProducer() throws ProducerException
    {
        final OfflineCallbacks.Service serviceCallbacks = new OfflineCallbacks.Service(mServiceExecutor, mWorkers);
        final NativeKinesisVideoProducerJni producer = new NativeKinesisVideoProducerJni(new OfflineCallbacks.Auth(),
                new DefaultStorageCallbacks(), serviceCallbacks, log);
        final StorageInfo storageInfo = new StorageInfo(0, StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM,
                32 * 1024 * 1024, 90, "/tmp");
        producer.createSync(new DeviceInfo(VERSION_ZERO, DEVICE_NAME, storageInfo, STREAM_COUNT, null,
                "JNI " + NativeKinesisVideoProducerJni.EXPECTED_LIBRARY_VERSION, new ClientInfo()));
        return producer;
    }

    private static List<KinesisVideoProducerStream> createStreams(final NativeKinesisVideoProducerJni producer,
                                                                  final String prefix) throws ProducerException
    {
        final List<KinesisVideoProducerStream> streams = new ArrayList<KinesisVideoProducerStream>();
        for (int i = 0; i < STREAM_COUNT; i++) {
            streams.add(producer.createStreamSync(OfflineCallbacks.streamInfo(prefix + "-" + i), new DefaultStreamCallbacks()));
        }
        return streams;
    }

    /**
     * One publisher per stream, so every stream has its own monotonic timeline.
     */
    private List<Future<?>> startPublishers(final NativeKinesisVideoProducerJni producer,
                                            final List<KinesisVideoProducerStream> streams, final AtomicBoolean stop,
                                            final AtomicLong[] framesAccepted, final AtomicInteger unexpectedErrors)
    {
        final List<Future<?>> publishers = new ArrayList<Future<?>>();
        for (int i = 0; i < METRICS_POLLERS; i++) {
            final KinesisVideoProducerStream stream = streams.get(i % streams.size());
            publishers.add(mWorkers.submit(() -> {
                while (!stop.get()) {
                    try {
                        producer.getMetrics();
                        stream.getMetrics();
                    } catch (final IllegalStateException | IllegalArgumentException | ProducerException expected) {
                        // The stream or the client has been freed
                    } catch (final Throwable t) {
                        log.error("Unexpected error from getMetrics", t);
                        unexpectedErrors.incrementAndGet();
                    }
                }
                return null;
            }));
        }
        for (int i = 0; i < CPU_BURNERS; i++) {
            publishers.add(mWorkers.submit(() -> {
                long spin = 0;
                while (!stop.get()) {
                    spin++;
                }
                return spin;
            }));
        }
        for (int i = 0; i < streams.size(); i++) {
            final KinesisVideoProducerStream stream = streams.get(i);
            final AtomicLong accepted = new AtomicLong();
            framesAccepted[i] = accepted;
            publishers.add(mWorkers.submit(() -> {
                final ByteBuffer data = ByteBuffer.wrap(new byte[FRAME_SIZE_BYTES]);
                final long start = System.currentTimeMillis() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
                int index = 0;
                while (!stop.get()) {
                    final long timestamp = start + index * FRAME_DURATION;
                    final int flags = index % KEY_FRAME_INTERVAL == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE;
                    try {
                        stream.putFrame(new KinesisVideoFrame(index, flags, timestamp, timestamp, FRAME_DURATION, data));
                        accepted.incrementAndGet();
                    } catch (final IllegalStateException | IllegalArgumentException expectedAfterFree) {
                        // The stream or the client has been freed
                    } catch (final ProducerException expectedAfterFree) {
                        // The native call rejected a frame for a stream that is going away
                    } catch (final Throwable t) {
                        log.error("Unexpected error from putFrame", t);
                        unexpectedErrors.incrementAndGet();
                    }
                    index++;
                }
                return null;
            }));
        }
        return publishers;
    }

    private static boolean waitForMoreFrames(final AtomicLong counter, final long before) throws InterruptedException
    {
        final long deadline = System.currentTimeMillis() + 5000;
        while (counter.get() <= before) {
            if (System.currentTimeMillis() > deadline) {
                return false;
            }
            Thread.sleep(5);
        }
        return true;
    }

    private static long[] snapshot(final AtomicLong[] counters)
    {
        final long[] values = new long[counters.length];
        for (int i = 0; i < counters.length; i++) {
            values[i] = counters[i].get();
        }
        return values;
    }
}
