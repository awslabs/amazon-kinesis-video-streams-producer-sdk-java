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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * The data-path calls of different streams no longer share a client-wide lock in the JNI. Every stream's putFrame,
 * getStreamData, parseFragmentAck and metrics run at the same time for a few seconds; each stream must keep making
 * progress, with no unexpected errors (and, with the JNI built with ASan, no memory errors). Runs fully offline.
 */
public class PerStreamConcurrencyTest
{
    private static final Logger log = LogManager.getLogger(PerStreamConcurrencyTest.class);
    private static final int STREAMS = 6;
    private static final long RUN_MS = Long.getLong("kvs.perStream.runMs", 4000);
    private static final long FRAME_DURATION = Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

    private ExecutorService mServiceExecutor;
    private ExecutorService mWorkers;

    @Before
    public void setUp()
    {
        assumeTrue("Needs the KinesisVideoProducerJNI library on java.library.path", OfflineCallbacks.isJniLoaded());
        mServiceExecutor = Executors.newFixedThreadPool(2, PerStreamConcurrencyTest::daemon);
        mWorkers = Executors.newCachedThreadPool(PerStreamConcurrencyTest::daemon);
    }

    @After
    public void tearDown() throws Exception
    {
        mWorkers.shutdownNow();
        mServiceExecutor.shutdownNow();
        mWorkers.awaitTermination(5, TimeUnit.SECONDS);
        mServiceExecutor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test(timeout = 120000)
    public void allDataPathCallsOfAllStreamsRunConcurrently() throws Exception
    {
        final UploadTracking service = new UploadTracking(mServiceExecutor, mWorkers);
        final NativeKinesisVideoProducerJni producer = new NativeKinesisVideoProducerJni(new OfflineCallbacks.Auth(),
                new DefaultStorageCallbacks(), service, log);
        producer.createSync(new DeviceInfo(VERSION_ZERO, "per-stream-test",
                new StorageInfo(0, StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, 256L * 1024 * 1024, 90, "/tmp"),
                STREAMS, null, "JNI " + NativeKinesisVideoProducerJni.EXPECTED_LIBRARY_VERSION, new ClientInfo()));
        final List<KinesisVideoProducerStream> streams = new ArrayList<>();
        for (int i = 0; i < STREAMS; i++) {
            streams.add(producer.createStreamSync(OfflineCallbacks.streamInfo("per-stream-" + i), new DefaultStreamCallbacks()));
        }

        final AtomicBoolean stop = new AtomicBoolean();
        final AtomicInteger unexpected = new AtomicInteger();
        final AtomicLong[] frames = new AtomicLong[STREAMS];
        final AtomicLong[] acks = new AtomicLong[STREAMS];
        final List<Future<?>> workers = new ArrayList<>();
        for (int i = 0; i < STREAMS; i++) {
            final KinesisVideoProducerStream stream = streams.get(i);
            final AtomicLong frameCount = frames[i] = new AtomicLong();
            final AtomicLong ackCount = acks[i] = new AtomicLong();
            workers.add(mWorkers.submit(() -> {
                final ByteBuffer data = ByteBuffer.wrap(new byte[512]);
                final long start = System.currentTimeMillis() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
                for (long n = 0; !stop.get(); n++) {
                    final long ts = start + n * FRAME_DURATION;
                    try {
                        stream.putFrame(new KinesisVideoFrame((int) n, n % 25 == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE,
                                ts, ts, FRAME_DURATION, data));
                        frameCount.incrementAndGet();
                    } catch (final ProducerException expected) {
                        // buffer pressure rejections count the same with or without the lock
                    } catch (final Throwable t) {
                        log.error("putFrame", t);
                        unexpected.incrementAndGet();
                    }
                }
                return null;
            }));
            workers.add(mWorkers.submit(() -> {
                final Long uploadHandle = service.awaitUploadHandle(stream.getStreamName());
                assertNotNull(uploadHandle);
                while (!stop.get()) {
                    try {
                        stream.parseFragmentAck(uploadHandle,
                                "{\"EventType\":\"BUFFERING\",\"FragmentTimecode\":0,\"FragmentNumber\":\"1\"}");
                        ackCount.incrementAndGet();
                    } catch (final ProducerException expected) {
                        // PIC rejects an ack outside the view window
                        ackCount.incrementAndGet();
                    } catch (final Throwable t) {
                        log.error("parseFragmentAck", t);
                        unexpected.incrementAndGet();
                    }
                    Thread.sleep(2);
                }
                return null;
            }));
            workers.add(mWorkers.submit(() -> {
                while (!stop.get()) {
                    try {
                        stream.getMetrics();
                        producer.getMetrics();
                    } catch (final Throwable t) {
                        log.error("metrics", t);
                        unexpected.incrementAndGet();
                    }
                }
                return null;
            }));
        }

        Thread.sleep(RUN_MS);
        stop.set(true);
        for (final Future<?> worker : workers) {
            worker.get(30, TimeUnit.SECONDS);
        }
        for (int i = 0; i < STREAMS; i++) {
            log.info("stream {}: {} frames, {} acks", i, frames[i].get(), acks[i].get());
            assertTrue("stream " + i + " put no frames", frames[i].get() > 0);
            assertTrue("stream " + i + " parsed no acks", acks[i].get() > 0);
        }
        assertEquals("Unexpected errors", 0, unexpected.get());

        producer.freeStreams();
        producer.free();
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
            final long deadline = System.currentTimeMillis() + 10000;
            while (!mUploadHandles.containsKey(streamName) && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            return mUploadHandles.get(streamName);
        }
    }

    private static Thread daemon(final Runnable runnable)
    {
        final Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    }
}
