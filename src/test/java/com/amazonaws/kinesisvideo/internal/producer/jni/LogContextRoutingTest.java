package com.amazonaws.kinesisvideo.internal.producer.jni;

import com.amazonaws.kinesisvideo.common.logging.LogContext;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.producer.ClientInfo;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StorageInfo;
import com.amazonaws.kinesisvideo.producer.Time;
import com.amazonaws.kinesisvideo.storage.DefaultStorageCallbacks;
import com.amazonaws.kinesisvideo.streaming.DefaultStreamCallbacks;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_KEY_FRAME;
import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_NONE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VERSION_ZERO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Each client's and each stream's log lines, the SDK's and PIC's, carry that client's or stream's LogContext tag, so a
 * RoutingAppender (log4j2-per-stream.xml) can write them to files of their own. Two clients, three streams, all
 * streaming at once. Fully offline.
 */
public class LogContextRoutingTest
{
    private static final String LOGGER = "LogContextRoutingTest.producer";
    private static final long FRAME_DURATION = 2 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
    private static final int PIC_LOG_LEVEL_DEBUG = 2;

    private static final class Line
    {
        final String mFile;
        final String mClient;
        final String mStreamName;
        final String mMessage;
        final String mThread;

        Line(final LogEvent event)
        {
            mFile = event.getContextData().getValue(LogContext.FILE);
            mClient = event.getContextData().getValue(LogContext.CLIENT);
            mStreamName = event.getContextData().getValue(LogContext.STREAM_NAME);
            mMessage = event.getMessage().getFormattedMessage();
            mThread = event.getThreadName();
        }

        boolean isPic()
        {
            return mMessage.startsWith("[PIC]");
        }

        @Override
        public String toString()
        {
            return "[" + mThread + "] " + mFile + " " + mClient + " " + mStreamName + " - " + mMessage;
        }
    }

    private static final class Capture extends AbstractAppender
    {
        final List<Line> mLines = new CopyOnWriteArrayList<>();

        Capture()
        {
            super("LogContextRoutingTest", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(final LogEvent event)
        {
            mLines.add(new Line(event));
        }
    }

    private final Logger mLog = LogManager.getLogger(LOGGER);
    private final Capture mCapture = new Capture();
    private ExecutorService mServiceExecutor;
    private ExecutorService mWorkers;

    @Before
    public void setUp()
    {
        assumeTrue("Needs the KinesisVideoProducerJNI library on java.library.path", OfflineCallbacks.isJniLoaded());
        mServiceExecutor = Executors.newFixedThreadPool(2, LogContextRoutingTest::daemon);
        mWorkers = Executors.newCachedThreadPool(LogContextRoutingTest::daemon);

        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final Configuration configuration = context.getConfiguration();
        final LoggerConfig loggerConfig = new LoggerConfig(LOGGER, Level.DEBUG, false);
        mCapture.start();
        loggerConfig.addAppender(mCapture, Level.DEBUG, null);
        configuration.addLogger(LOGGER, loggerConfig);
        context.updateLoggers();
    }

    @After
    public void tearDown() throws Exception
    {
        if (mWorkers == null) {
            return;
        }
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        context.getConfiguration().removeLogger(LOGGER);
        context.updateLoggers();
        mCapture.stop();
        mWorkers.shutdownNow();
        mServiceExecutor.shutdownNow();
        mWorkers.awaitTermination(5, TimeUnit.SECONDS);
        mServiceExecutor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test(timeout = 60000)
    public void eachClientAndStreamLogsUnderItsOwnTag() throws Exception
    {
        final Map<String, String> clientOfStream = new HashMap<>();
        final int beforeA = mCapture.mLines.size();
        final NativeKinesisVideoProducerJni a = createClient("logs-a");
        final String clientA = clientTagFrom(beforeA);
        final int beforeB = mCapture.mLines.size();
        final NativeKinesisVideoProducerJni b = createClient("logs-b");
        final String clientB = clientTagFrom(beforeB);
        try {

            final List<Future<?>> publishers = new ArrayList<>();
            for (final String name : new String[] {"logs-a-0", "logs-a-1", "logs-b-0"}) {
                final NativeKinesisVideoProducerJni client = name.startsWith("logs-a") ? a : b;
                clientOfStream.put(name, name.startsWith("logs-a") ? clientA : clientB);
                final KinesisVideoProducerStream stream = client.createStreamSync(OfflineCallbacks.streamInfo(name),
                        new DefaultStreamCallbacks() {
                            @Override
                            public void streamReady()
                            {
                                // An application callback, run on the client's callback thread
                                mLog.info("app streamReady {}", name);
                            }
                        });
                publishers.add(mWorkers.submit(() -> publish(stream)));
            }
            for (final Future<?> publisher : publishers) {
                publisher.get(30, TimeUnit.SECONDS);
            }

            // Scopes are restored: nothing leaks onto the application's thread
            assertNull(ThreadContext.get(LogContext.FILE));
            assertNull(ThreadContext.get(LogContext.CLIENT));
        } finally {
            a.close();
            b.close();
        }
        assertNull(ThreadContext.get(LogContext.FILE));

        final List<Line> lines = mCapture.mLines;
        assertFalse("no PIC lines captured", lines.stream().noneMatch(Line::isPic));
        assertFalse(clientA.equals(clientB));

        // Client-level lines: tagged with the client, no stream. They include PIC's lines from creating the client, the
        // first client's too (all but the few PIC logs before it installs the JNI's log function)
        for (final String client : new String[] {clientA, clientB}) {
            assertTrue("PIC's client creation lines not in client_" + client, lines.stream().anyMatch(
                    line -> line.isPic() && line.mMessage.contains("heapInitialize")
                            && ("client_" + client).equals(line.mFile)));
            assertTrue("freeing the client not in client_" + client, lines.stream().anyMatch(
                    line -> line.isPic() && line.mMessage.contains("freeKinesisVideoClient")
                            && ("client_" + client).equals(line.mFile)));
        }
        for (final Line line : lines) {
            if (line.mFile != null && line.mFile.startsWith("client_")) {
                assertNull(line.toString(), line.mStreamName);
                assertEquals(line.toString(), "client_" + line.mClient, line.mFile);
            }
        }

        // Stream-level lines: one file per stream, under the stream's own client, with PIC and application lines in it
        final Set<String> streamFiles = new HashSet<>();
        for (final Map.Entry<String, String> entry : clientOfStream.entrySet()) {
            final String name = entry.getKey();
            final Set<String> files = new HashSet<>();
            for (final Line line : lines) {
                if (name.equals(line.mStreamName)) {
                    files.add(line.mFile);
                    assertEquals(line.toString(), entry.getValue(), line.mClient);
                }
            }
            assertEquals(name + " logged to " + files, 1, files.size());
            final String file = files.iterator().next();
            assertTrue(file, file.startsWith("stream_"));
            streamFiles.add(file);

            assertTrue("no PIC lines in " + file, lines.stream().anyMatch(line -> line.isPic() && file.equals(line.mFile)));
            assertTrue("streamReady callback not in " + file, lines.stream().anyMatch(
                    line -> line.mMessage.equals("app streamReady " + name) && file.equals(line.mFile)));
        }
        assertEquals(3, streamFiles.size());

        // A PIC line that names a stream is in that stream's file, or in no stream's file (PIC's timer thread)
        for (final Line line : lines) {
            if (!line.isPic() || line.mStreamName == null) {
                continue;
            }
            for (final String name : clientOfStream.keySet()) {
                if (!name.equals(line.mStreamName) && line.mMessage.matches(".*\\b" + name + "\\b.*")) {
                    throw new AssertionError("Line about " + name + " logged for " + line.mStreamName + ": " + line);
                }
            }
        }
    }

    @Test
    public void scopesNestAndRestore()
    {
        try (LogContext client = LogContext.client("901")) {
            assertEquals("client_901", ThreadContext.get(LogContext.FILE));
            try (LogContext stream = LogContext.stream("901", "902", "s")) {
                assertEquals("stream_902", ThreadContext.get(LogContext.FILE));
                assertEquals("s", ThreadContext.get(LogContext.STREAM_NAME));
                try (LogContext same = LogContext.stream("901", "902", "s");
                     LogContext unchanged = LogContext.unchanged()) {
                    assertEquals("stream_902", ThreadContext.get(LogContext.FILE));
                }
                assertEquals("stream_902", ThreadContext.get(LogContext.FILE));
            }
            assertEquals("client_901", ThreadContext.get(LogContext.FILE));
            assertNull(ThreadContext.get(LogContext.STREAM_NAME));
        }
        assertNull(ThreadContext.get(LogContext.FILE));
        assertNull(ThreadContext.get(LogContext.CLIENT));
    }

    @Test
    public void handedOffWorkKeepsTheSubmittersTag() throws Exception
    {
        final ScheduledExecutorService executor = LogContext.propagating(Executors.newSingleThreadScheduledExecutor());
        try {
            try (LogContext stream = LogContext.stream("911", "912", "s")) {
                assertEquals("stream_912", executor.submit(() -> ThreadContext.get(LogContext.FILE)).get());
                assertEquals("stream_912", executor.schedule(() -> ThreadContext.get(LogContext.FILE), 1,
                        TimeUnit.MILLISECONDS).get());
            }
            // The executor's thread does not keep the tag, and untagged work stays untagged
            assertNull(executor.submit(() -> ThreadContext.get(LogContext.FILE)).get());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * The tag of the client created after the first {@code from} lines
     */
    private String clientTagFrom(final int from)
    {
        final List<Line> lines = mCapture.mLines;
        for (int i = from; i < lines.size(); i++) {
            if (lines.get(i).mFile != null && lines.get(i).mFile.startsWith("client_")) {
                return lines.get(i).mClient;
            }
        }
        throw new AssertionError("No client-tagged line while creating the client: " + lines.subList(from, lines.size()));
    }

    private static void publish(final KinesisVideoProducerStream stream)
    {
        final ByteBuffer data = ByteBuffer.wrap(new byte[512]);
        final long start = System.currentTimeMillis() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
        for (int n = 0; n < 100; n++) {
            final long ts = start + n * FRAME_DURATION;
            try {
                stream.putFrame(new KinesisVideoFrame(n, n % 25 == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE, ts, ts,
                        FRAME_DURATION, data));
                Thread.sleep(1);
            } catch (final ProducerException e) {
                throw new AssertionError(e);
            } catch (final InterruptedException e) {
                return;
            }
        }
    }

    @SuppressWarnings("deprecation")
    private NativeKinesisVideoProducerJni createClient(final String name) throws ProducerException
    {
        final NativeKinesisVideoProducerJni producer = new NativeKinesisVideoProducerJni(new OfflineCallbacks.Auth(),
                new DefaultStorageCallbacks(), new OfflineCallbacks.Service(mServiceExecutor, mWorkers), mLog);
        producer.createSync(new DeviceInfo(VERSION_ZERO, name,
                new StorageInfo(0, StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, 32 * 1024 * 1024, 90, "/tmp"),
                4, null, "JNI " + NativeKinesisVideoProducerJni.EXPECTED_LIBRARY_VERSION,
                new ClientInfo(0, 0, 0, 0, PIC_LOG_LEVEL_DEBUG, false, 0, 0)));
        return producer;
    }

    private static Thread daemon(final Runnable runnable)
    {
        final Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    }
}
