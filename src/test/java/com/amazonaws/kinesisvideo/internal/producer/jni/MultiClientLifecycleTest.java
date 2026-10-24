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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Several clients in one process. The JNI keeps some state for the whole process: the wrapper registry, and PIC's
 * single log function, which the JNI routes through the first client. Clients are freed one at a time, the first one
 * first, while the others keep streaming; then a new client is created. Run with the JNI built with ASan. Fully offline.
 */
public class MultiClientLifecycleTest
{
    private static final Logger log = LogManager.getLogger(MultiClientLifecycleTest.class);
    private static final int CLIENTS = 4;
    private static final int STREAMS_PER_CLIENT = 2;
    private static final long FRAME_DURATION = 2 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

    private ExecutorService mServiceExecutor;
    private ExecutorService mWorkers;

    @Before
    public void setUp()
    {
        assumeTrue("Needs the KinesisVideoProducerJNI library on java.library.path", OfflineCallbacks.isJniLoaded());
        mServiceExecutor = Executors.newFixedThreadPool(4, MultiClientLifecycleTest::daemon);
        mWorkers = Executors.newCachedThreadPool(MultiClientLifecycleTest::daemon);
    }

    @After
    public void tearDown() throws Exception
    {
        mWorkers.shutdownNow();
        mServiceExecutor.shutdownNow();
        mWorkers.awaitTermination(5, TimeUnit.SECONDS);
        mServiceExecutor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test(timeout = 180000)
    public void clientsCanBeFreedWhileOtherClientsStream() throws Exception
    {
        for (int round = 0; round < 3; round++) {
            final List<NativeKinesisVideoProducerJni> clients = new ArrayList<>();
            final List<AtomicLong> progress = new ArrayList<>();
            final AtomicBoolean[] stop = new AtomicBoolean[CLIENTS];
            final AtomicInteger unexpected = new AtomicInteger();
            final List<Future<?>> workers = new ArrayList<>();
            for (int c = 0; c < CLIENTS; c++) {
                final NativeKinesisVideoProducerJni client = createClient("multi-" + round + "-" + c);
                clients.add(client);
                stop[c] = new AtomicBoolean();
                final AtomicLong frames = new AtomicLong();
                progress.add(frames);
                for (int s = 0; s < STREAMS_PER_CLIENT; s++) {
                    final KinesisVideoProducerStream stream = client.createStreamSync(
                            OfflineCallbacks.streamInfo("multi-" + round + "-" + c + "-" + s), new DefaultStreamCallbacks());
                    final AtomicBoolean clientStop = stop[c];
                    workers.add(mWorkers.submit(() -> publish(stream, client, clientStop, frames, unexpected)));
                }
            }
            Thread.sleep(300);

            // Free the clients one by one, the first one (which PIC's log function goes through) first
            for (int c = 0; c < CLIENTS; c++) {
                stop[c].set(true);
                clients.get(c).close();
                assertFalse(clients.get(c).isInitialized());
                for (int other = c + 1; other < CLIENTS; other++) {
                    final long before = progress.get(other).get();
                    assertTrue("client " + other + " stopped streaming after client " + c + " was freed",
                            waitForMore(progress.get(other), before));
                }
            }
            for (final Future<?> worker : workers) {
                worker.get(30, TimeUnit.SECONDS);
            }
            assertEquals("Unexpected errors in round " + round, 0, unexpected.get());
        }

        // A client created after all the others were freed works, including logging
        try (NativeKinesisVideoProducerJni client = createClient("multi-after")) {
            final KinesisVideoProducerStream stream = client.createStreamSync(OfflineCallbacks.streamInfo("multi-after-0"),
                    new DefaultStreamCallbacks());
            final AtomicBoolean stop = new AtomicBoolean();
            final AtomicLong frames = new AtomicLong();
            final AtomicInteger unexpected = new AtomicInteger();
            final Future<?> worker = mWorkers.submit(() -> publish(stream, client, stop, frames, unexpected));
            assertTrue(waitForMore(frames, 0));
            stop.set(true);
            worker.get(30, TimeUnit.SECONDS);
            assertEquals(0, unexpected.get());
        }
    }

    private static Void publish(final KinesisVideoProducerStream stream, final NativeKinesisVideoProducerJni client,
                                final AtomicBoolean stop, final AtomicLong frames, final AtomicInteger unexpected)
    {
        final ByteBuffer data = ByteBuffer.wrap(new byte[512]);
        final long start = System.currentTimeMillis() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
        for (long n = 0; !stop.get(); n++) {
            final long ts = start + n * FRAME_DURATION;
            try {
                stream.putFrame(new KinesisVideoFrame((int) n, n % 25 == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE,
                        ts, ts, FRAME_DURATION, data));
                frames.incrementAndGet();
                if (n % 50 == 0) {
                    client.getMetrics();
                    stream.getMetrics();
                }
                Thread.sleep(1);
            } catch (final IllegalStateException | IllegalArgumentException | ProducerException expected) {
                // this client is being freed
            } catch (final InterruptedException e) {
                return null;
            } catch (final Throwable t) {
                log.error("Unexpected error", t);
                unexpected.incrementAndGet();
            }
        }
        return null;
    }

    private static boolean waitForMore(final AtomicLong counter, final long before) throws InterruptedException
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

    private NativeKinesisVideoProducerJni createClient(final String name) throws ProducerException
    {
        final NativeKinesisVideoProducerJni producer = new NativeKinesisVideoProducerJni(new OfflineCallbacks.Auth(),
                new DefaultStorageCallbacks(), new OfflineCallbacks.Service(mServiceExecutor, mWorkers), log);
        producer.createSync(new DeviceInfo(VERSION_ZERO, name,
                new StorageInfo(0, StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, 32 * 1024 * 1024, 90, "/tmp"),
                STREAMS_PER_CLIENT + 1, null, "JNI " + NativeKinesisVideoProducerJni.EXPECTED_LIBRARY_VERSION, new ClientInfo()));
        return producer;
    }

    private static Thread daemon(final Runnable runnable)
    {
        final Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    }
}
