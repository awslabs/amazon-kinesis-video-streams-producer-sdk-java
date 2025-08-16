package com.amazonaws.kinesisvideo.common;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.kinesisvideo.auth.DefaultAuthCallbacks;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.internal.client.NativeKinesisVideoClient;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducer;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.internal.producer.ServiceCallbacks;
import com.amazonaws.kinesisvideo.internal.service.DefaultServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.java.auth.JavaCredentialsFactory;
import com.amazonaws.kinesisvideo.java.service.JavaKinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.producer.AuthCallbacks;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFragmentAck;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StorageCallbacks;
import com.amazonaws.kinesisvideo.producer.StorageInfo;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Tag;
import com.amazonaws.kinesisvideo.producer.Time;
import com.amazonaws.kinesisvideo.storage.DefaultStorageCallbacks;
import com.amazonaws.kinesisvideo.streaming.DefaultStreamCallbacks;
import com.amazonaws.kinesisvideo.util.StreamInfoConstants;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.model.CreateStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DeleteStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamResult;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.amazonaws.kinesisvideo.internal.producer.jni.NativeKinesisVideoProducerJni.PRODUCER_NATIVE_LIBRARY_NAME;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

/**
 * Multi-Client Test - Validates per-client JVM context refactoring
 * <p>
 * This test ensures that multiple KinesisVideoClient instances can operate
 * independently without interfering with each other's callbacks or logging.
 */
public class MultiClientTest extends ProducerTestBase {
    private static final Logger log = LogManager.getLogger(MultiClientTest.class);
    private static final int NUM_CLIENTS = 3;
    private static final int FRAMES_PER_CLIENT = 500;
    private static final int FPS = System.getenv("CI") != null ? 10 : 50;

    private static final long FRAME_DURATION_MS = 1000 / FPS;
    private static final int KEYFRAME_INTERVAL = FPS; // GOP = 1 second
    private static final int DURATION_ZERO = 0;

    private ExecutorService testExecutor;

    private final List<TestStreamContext> streamContexts = new ArrayList<>();

    // Compensating for slower devices / CI environments
    private final int GRACE_PERIOD_SECS = 15;

    @Rule
    public Timeout globalTimeout = Timeout.seconds(FRAMES_PER_CLIENT / FPS + this.GRACE_PERIOD_SECS + (WAIT_5_SECONDS_FOR_ACKS / 1000));

    private static class TestStreamContext {
        @Nullable
        NativeKinesisVideoClient client = null;
        @Nullable
        KinesisVideoProducer producer = null;
        @Nullable
        KinesisVideoProducerStream stream = null;
        @Nullable
        List<KinesisVideoFragmentAck> acksReceived = null;
        @Nullable
        ScheduledExecutorService scheduledExecutorService = null;
        @Nullable
        String streamName = null;
        @Nonnull
        AtomicBoolean describeStreamCalled = new AtomicBoolean(false);
        @Nonnull
        AtomicBoolean putMediaCalled = new AtomicBoolean(false);
        @Nonnull
        AtomicBoolean streamReadyCalled = new AtomicBoolean(false);
        @Nonnull
        AtomicBoolean streamClosedCalled = new AtomicBoolean(false);
    }

    private KinesisVideoClientConfiguration clientConfiguration;

    @Before
    public void setUp() {
        try {
            System.loadLibrary(PRODUCER_NATIVE_LIBRARY_NAME);
        } catch (final UnsatisfiedLinkError e) {
            fail("JNI library not found.");
        }

        this.testExecutor = Executors.newFixedThreadPool(NUM_CLIENTS * 2, new ThreadFactoryBuilder().setNameFormat("test-executor-%d").build());

        assumeTrue(DefaultAWSCredentialsProviderChain.getInstance().getCredentials() != null);

        final String prefix = Optional.ofNullable(System.getenv("TEST_STREAMS_PREFIX")).orElse("");
        final AmazonKinesisVideo awsSdkKinesisVideoClient = AmazonKinesisVideoClientBuilder.standard().build();
        boolean success = true;

        this.clientConfiguration = KinesisVideoClientConfiguration.builder()
                .withCredentialsProvider(JavaCredentialsFactory.createKinesisVideoCredentialsProvider(DefaultAWSCredentialsProviderChain.getInstance()))
                .build();

        for (int i = 0; i < NUM_CLIENTS; i++) {
            final String streamName = String.join("-", prefix, "test-multi-client-stream",
                    Integer.toString(i), Long.toString(System.currentTimeMillis()), UUID.randomUUID().toString());
            try {
                log.info("Creating stream {}", streamName);
                final CreateStreamRequest createStreamRequest = new CreateStreamRequest()
                        .withStreamName(streamName)
                        .withDataRetentionInHours(2);
                awsSdkKinesisVideoClient.createStream(createStreamRequest);
                final TestStreamContext testStreamContext = new TestStreamContext();
                testStreamContext.streamName = streamName;
                this.streamContexts.add(testStreamContext);
            } catch (final Throwable t) {
                log.error("Encountered an error creating stream: {}!", streamName, t);
                success = false;
            }
        }

        assertTrue("There was an issue creating streams, check the logs above!", success);
    }

    @After
    public void tearDown() throws Exception {
        boolean success = true;
        if (this.testExecutor != null) {
            this.testExecutor.shutdownNow();
            if (!this.testExecutor.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                log.error("testExecutor did not finish in time");
                success = false;
            }
        }

        // Clean up streams
        for (final TestStreamContext testStreamContext : this.streamContexts) {
            final NativeKinesisVideoClient client = testStreamContext.client;
            final KinesisVideoProducer producer = testStreamContext.producer;
            final KinesisVideoProducerStream stream = testStreamContext.stream;
            final ScheduledExecutorService scheduledExecutorService = testStreamContext.scheduledExecutorService;

            if (stream != null) {
                try {
                    stream.stopStreamSync();
                } catch (final Exception e) {
                    log.warn("Error stopping stream: " + e.getMessage());
                    success = false;
                }
            }

            if (producer != null) {
                try {
                    producer.freeStreams();
                    producer.free();
                } catch (final Exception e) {
                    log.warn("Error freeing producer", e);
                    success = false;
                }
            }

            if (client != null) {
                try {
                    client.free();
                } catch (final Exception e) {
                    log.warn("Error freeing client: " + e.getMessage());
                    success = false;
                }
            }

            if (scheduledExecutorService != null) {
                scheduledExecutorService.shutdownNow();
                assumeTrue("scheduledExecutorService timed out while shutting down", scheduledExecutorService.awaitTermination(10, TimeUnit.SECONDS));
            }
        }

        // Clean up streams from AWS
        deleteStreams();

        assertTrue("There was an issue in the cleanup, check the logs above.", success);
    }

    @Test
    @SuppressWarnings("ExtractMethodRecommender")
    public void givenMultipleStreamContexts_whenCreatingClientsAndProducers_thenAllInstancesAreCreatedSuccessfully() throws Exception {
        // Create multiple clients
        for (int i = 0; i < NUM_CLIENTS; i++) {
            final TestStreamContext testStreamContext = this.streamContexts.get(i);
            assumeNotNull(testStreamContext);
            testStreamContext.scheduledExecutorService = Executors.newScheduledThreadPool(2, new ThreadFactoryBuilder().setNameFormat("client-" + i + "-executor-%d").build());
            final DeviceInfo deviceInfo = createTestDeviceInfo("test-device-" + i);

            final AuthCallbacks authCallbacks = new DefaultAuthCallbacks(this.clientConfiguration.getCredentialsProvider(),
                    testStreamContext.scheduledExecutorService, LogManager.getLogger(DefaultAuthCallbacks.class));

            final StorageCallbacks storageCallbacks = new DefaultStorageCallbacks();
            final ServiceCallbacks serviceCallbacks = new DefaultServiceCallbacksImpl(log, testStreamContext.scheduledExecutorService,
                    this.clientConfiguration, new JavaKinesisVideoServiceClient());
            final StreamCallbacks streamCallbacks = new DefaultStreamCallbacks();

            final NativeKinesisVideoClient client = new NativeKinesisVideoClient(log, authCallbacks, storageCallbacks,
                    serviceCallbacks, streamCallbacks);

            client.initialize(deviceInfo);
            testStreamContext.client = client;

            assertTrue("Producer " + i + " should be initialized", client.isInitialized());
        }

        // Cleanup will free the clients and producers to verify that we can free them in the order they were created
    }

    @Test
    public void givenMultipleStreamContexts_whenCreatingClientsAndProducersInReverseOrder_thenAllInstancesAreCreatedSuccessfully() throws Exception {
        givenMultipleStreamContexts_whenCreatingClientsAndProducers_thenAllInstancesAreCreatedSuccessfully();

        // Verify that we can delete clients in a different order
        Collections.reverse(this.streamContexts);
    }

    /**
     * Test 2: Multiple clients can stream simultaneously without interference
     */
    @Test
    public void givenMultipleClientsAndStreams_whenStreamingConcurrently_thenAllClientsReceiveTheirOwnCallbacks() throws Exception {
        log.info("Testing concurrent streaming with multiple clients");

        // Create clients and streams
        createMultipleClientsAndStreams();

        final List<Future<Void>> streamingTasks = new ArrayList<>();
        final CountDownLatch startLatch = new CountDownLatch(1);

        for (int clientIndex = 0; clientIndex < NUM_CLIENTS; clientIndex++) {
            final int finalClientIndex = clientIndex;
            final TestStreamContext testStreamContext = this.streamContexts.get(clientIndex);
            assumeNotNull(testStreamContext);
            assumeNotNull(testStreamContext.stream);

            final Future<Void> task = this.testExecutor.submit(() -> {
                try {
                    // Wait for the start signal
                    startLatch.await();

                    streamFrames(testStreamContext);

                    testStreamContext.stream.stopStreamSync();
                } catch (final Exception e) {
                    log.error("Error streaming for client {}", finalClientIndex, e);
                    throw new RuntimeException(e);
                }
                return null;
            });
            streamingTasks.add(task);
        }

        // Kickoff all the streams at the same time
        startLatch.countDown();

        // Wait for all streaming to complete
        for (final Future<Void> task : streamingTasks) {
            task.get(FRAMES_PER_CLIENT / FPS + this.GRACE_PERIOD_SECS + (WAIT_5_SECONDS_FOR_ACKS / 1000), TimeUnit.SECONDS);
        }

        // Verify each client received its own callbacks
        for (int i = 0; i < NUM_CLIENTS; i++) {
            final TestStreamContext testStreamContext = this.streamContexts.get(i);
            assumeNotNull(testStreamContext);
            assumeNotNull(testStreamContext.acksReceived);

            assertFalse("Client " + i + " should have received ACKs!", testStreamContext.acksReceived.isEmpty());

            assertTrue("StreamReady callback was not invoked for client/stream " + i + ", streamName=" + testStreamContext.streamName,
                    testStreamContext.streamReadyCalled.get());
            assertTrue("StreamClosed callback was not invoked for client/stream " + i + ", streamName=" + testStreamContext.streamName,
                    testStreamContext.streamClosedCalled.get());

            assertTrue("DescribeStream callback was not invoked for client/stream " + i + ", streamName=" + testStreamContext.streamName,
                    testStreamContext.describeStreamCalled.get());
            assertTrue("PutMedia callback was not invoked for client/stream " + i + ", streamName=" + testStreamContext.streamName,
                    testStreamContext.putMediaCalled.get());
        }
    }

    // Creates clients and streams in order: (Client, Stream), (Client, Stream), ...
    @SuppressWarnings({"UnnecessaryLocalVariable", "ExtractMethodRecommender"})
    private void createMultipleClientsAndStreams() throws Exception {
        for (int i = 0; i < NUM_CLIENTS; i++) {
            final TestStreamContext testStreamContext = this.streamContexts.get(i);
            assumeNotNull(testStreamContext);

            final String streamName = testStreamContext.streamName;
            final DeviceInfo deviceInfo = createTestDeviceInfo("test-device-" + i);
            testStreamContext.scheduledExecutorService = Executors.newScheduledThreadPool(2, new ThreadFactoryBuilder().setNameFormat("client-" + i + "-executor-%d").build());

            final AuthCallbacks authCallbacks = new DefaultAuthCallbacks(this.clientConfiguration.getCredentialsProvider(),
                    testStreamContext.scheduledExecutorService, LogManager.getLogger(DefaultAuthCallbacks.class));

            final StorageCallbacks storageCallbacks = new DefaultStorageCallbacks();
            final ServiceCallbacks serviceCallbacks = new DefaultServiceCallbacksImpl(log,
                    testStreamContext.scheduledExecutorService, this.clientConfiguration, new JavaKinesisVideoServiceClient()) {
                @Override
                public void describeStream(@Nonnull final String streamName, final long callAfter, final long timeout, @Nullable final byte[] authData, final int authType, final long streamHandle, final KinesisVideoProducerStream stream) throws ProducerException {
                    testStreamContext.describeStreamCalled.set(true);
                    super.describeStream(streamName, callAfter, timeout, authData, authType, streamHandle, stream);
                }

                @Override
                public void putStream(@Nonnull final String streamName, @Nonnull final String containerType, final long streamStartTime, final boolean absoluteFragmentTimes, final boolean ackRequired, @Nonnull final String dataEndpoint, final long callAfter, final long timeout, @Nullable final byte[] authData, final int authType, final KinesisVideoProducerStream kinesisVideoProducerStream) throws ProducerException {
                    testStreamContext.putMediaCalled.set(true);
                    super.putStream(streamName, containerType, streamStartTime, absoluteFragmentTimes, ackRequired, dataEndpoint, callAfter, timeout, authData, authType, kinesisVideoProducerStream);
                }
            };
            final StreamCallbacks streamCallbacks = new DefaultStreamCallbacks();

            final NativeKinesisVideoClient client = new NativeKinesisVideoClient(log, authCallbacks, storageCallbacks,
                    serviceCallbacks, streamCallbacks);
            testStreamContext.client = client;

            final KinesisVideoProducer producer = client.initializeNewKinesisVideoProducer(deviceInfo);
            testStreamContext.producer = producer;

            final List<KinesisVideoFragmentAck> fragmentAcksReceived = new ArrayList<>();
            testStreamContext.acksReceived = fragmentAcksReceived;

            final StreamInfo streamInfo = createStreamInfo(streamName);
            final KinesisVideoProducerStream stream = producer.createStreamSync(streamInfo, new DefaultStreamCallbacks() {
                @Override
                public void fragmentAckReceived(final long uploadHandle, @Nonnull final KinesisVideoFragmentAck fragmentAck)
                        throws ProducerException {
                    super.fragmentAckReceived(uploadHandle, fragmentAck);
                    log.info("Received {} for stream: {}", fragmentAck, streamName);
                    fragmentAcksReceived.add(fragmentAck);
                }

                @Override
                public void streamReady() throws ProducerException {
                    testStreamContext.streamReadyCalled.set(true);
                    super.streamReady();
                }

                @Override
                public void streamClosed(final long uploadHandle) throws ProducerException {
                    testStreamContext.streamClosedCalled.set(true);
                    super.streamClosed(uploadHandle);
                }
            });
            testStreamContext.stream = stream;
        }
    }

    private void streamFrames(@Nonnull final TestStreamContext testStreamContext)
            throws Exception {
        assumeNotNull(testStreamContext);

        final KinesisVideoProducerStream stream = testStreamContext.stream;
        assumeNotNull(stream);

        final String streamName = testStreamContext.streamName;
        assumeNotNull(streamName);

        final long startTimestampMs = System.currentTimeMillis();
        for (int frameIndex = 0; frameIndex < FRAMES_PER_CLIENT; frameIndex++) {
            final long currentTimestampMs = startTimestampMs + (frameIndex * FRAME_DURATION_MS);
            final long timestampHundredsOfNanos = currentTimestampMs * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

            final String frameData = streamName + "-Frame-" + frameIndex + "-Data";
            final ByteBuffer frameBuffer = ByteBuffer.wrap(frameData.getBytes());

            final int frameFlags = frameIndex % KEYFRAME_INTERVAL == 0 ?
                    FRAME_FLAG_KEY_FRAME :
                    FRAME_FLAG_NONE;

            final KinesisVideoFrame frame = new KinesisVideoFrame(
                    frameIndex,
                    frameFlags,
                    timestampHundredsOfNanos,
                    timestampHundredsOfNanos,
                    DURATION_ZERO,
                    frameBuffer
            );

            stream.putFrame(frame);

            final long now = System.currentTimeMillis();
            final long nextFrameMs = startTimestampMs + (frameIndex + 1) * FRAME_DURATION_MS;
            final long sleepTime = (nextFrameMs - now) / 2;
            if (sleepTime > 0) {
                Thread.sleep(sleepTime);
            } else {
                log.warn("[{}] Submitting frames behind schedule by {} ms!", testStreamContext.streamName, Math.abs(sleepTime));
            }
        }

        Thread.sleep(WAIT_5_SECONDS_FOR_ACKS);
    }

    @Nonnull
    @SuppressWarnings("ConstantConditions")
    private DeviceInfo createTestDeviceInfo(@Nonnull final String deviceName) {
        assumeNotNull("Device name cannot be null", deviceName);

        final int storageInfoVersion = 0;
        final StorageInfo.DeviceStorageType storageType = StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM;
        final long storageSizeBytes = 1024 * 1024 * 10; // 10 MB
        final int spillRatio = 90;
        final String rootDirectory = "/tmp";
        final StorageInfo storageInfo = new StorageInfo(storageInfoVersion,
                storageType,
                storageSizeBytes,
                spillRatio,
                rootDirectory);

        final int deviceInfoVersion = 0;
        final Tag[] tags = null;
        final int numStreams = 1;
        return new DeviceInfo(deviceInfoVersion,
                deviceName,
                storageInfo,
                numStreams,
                tags);
    }

    private StreamInfo createStreamInfo(final String streamName) {
        return new StreamInfo(
                StreamInfo.STREAM_INFO_CURRENT_VERSION,
                streamName,
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                "video/h264",
                StreamInfoConstants.NO_KMS_KEY_ID,
                StreamInfoConstants.RETENTION_ONE_HOUR,
                StreamInfoConstants.NOT_ADAPTIVE,
                StreamInfoConstants.MAX_LATENCY_ZERO,
                StreamInfoConstants.DEFAULT_GOP_DURATION,
                StreamInfoConstants.KEYFRAME_FRAGMENTATION,
                StreamInfoConstants.USE_FRAME_TIMECODES,
                StreamInfoConstants.RELATIVE_TIMECODES,
                StreamInfoConstants.REQUEST_FRAGMENT_ACKS,
                StreamInfoConstants.RECOVER_ON_FAILURE,
                "V_MPEG4/ISO/AVC",
                "test-track",
                StreamInfoConstants.DEFAULT_BITRATE,
                MultiClientTest.FPS,
                StreamInfoConstants.DEFAULT_BUFFER_DURATION,
                StreamInfoConstants.DEFAULT_REPLAY_DURATION,
                StreamInfoConstants.DEFAULT_STALENESS_DURATION,
                StreamInfoConstants.DEFAULT_TIMESCALE,
                StreamInfoConstants.RECALCULATE_METRICS,
                null,
                new Tag[]{
                        new Tag("device", "Test Device"),
                        new Tag("stream", "Test Stream")},
                StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_FLAG_NONE,
                this.allowStreamCreation
        );
    }

    private void deleteStreams() {
        final AmazonKinesisVideo awsSdkKinesisVideoClient = AmazonKinesisVideoClientBuilder.standard().build();
        boolean success = true;
        for (final TestStreamContext testStreamContext : this.streamContexts) {
            final String streamName = testStreamContext.streamName;
            if (streamName == null) {
                continue;
            }

            try {
                log.info("Deleting stream {}", streamName);
                final DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest().withStreamName(streamName);
                final DescribeStreamResult describeStreamResult = awsSdkKinesisVideoClient.describeStream(describeStreamRequest);

                final DeleteStreamRequest deleteStreamRequest = new DeleteStreamRequest()
                        .withStreamARN(describeStreamResult.getStreamInfo().getStreamARN())
                        .withCurrentVersion(describeStreamResult.getStreamInfo().getVersion());
                awsSdkKinesisVideoClient.deleteStream(deleteStreamRequest);
            } catch (final Throwable t) {
                log.error("Encountered an error deleting healthy stream: {}!", streamName, t);
                success = false;
            }
        }
        assertTrue("Encountered an issue cleaning up the streams! Check the logs above", success);
    }
}
