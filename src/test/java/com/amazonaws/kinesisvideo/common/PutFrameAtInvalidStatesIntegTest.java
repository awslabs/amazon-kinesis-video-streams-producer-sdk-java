package com.amazonaws.kinesisvideo.common;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.kinesisvideo.auth.DefaultAuthCallbacks;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentialsProvider;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.internal.client.NativeKinesisVideoClient;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducer;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.internal.producer.jni.NativeKinesisVideoProducerJni;
import com.amazonaws.kinesisvideo.internal.service.DefaultServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.java.auth.JavaCredentialsFactory;
import com.amazonaws.kinesisvideo.java.service.JavaKinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
import com.amazonaws.kinesisvideo.producer.FrameFlags;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFragmentAck;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.ProducerException;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.amazonaws.kinesisvideo.internal.producer.jni.NativeKinesisVideoProducerJni.PRODUCER_NATIVE_LIBRARY_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;

public class PutFrameAtInvalidStatesIntegTest {

    private static final Logger log = LogManager.getLogger(PutFrameAtInvalidStatesIntegTest.class);

    private static final int STATUS_STREAM_HAS_BEEN_STOPPED = 0x52000052;

    private static final int TEN_SECONDS = 10;
    private static final int NUMBER_OF_FRAMES_TO_STREAM = 10;
    private static final int FPS = 5;
    private static final int GOP = FPS; // 1 second GOP
    private static final int FRAME_DURATION_MS = 1000 / FPS;

    private String streamName;

    @Rule
    public Timeout globalTimeout = Timeout.seconds(TEN_SECONDS);

    @Before
    public void setup() {
        try {
            System.loadLibrary(PRODUCER_NATIVE_LIBRARY_NAME);
        } catch (final UnsatisfiedLinkError e) {
            fail("JNI library not found.");
        }

        assumeNotNull("Unable to find credentials!", DefaultAWSCredentialsProviderChain.getInstance().getCredentials());

        final String prefix = Optional.ofNullable(System.getenv("TEST_STREAMS_PREFIX")).orElse("") + "PutFrameAfterFreeIntegTest";
        this.streamName = String.join("-", prefix, Long.toString(System.currentTimeMillis()), UUID.randomUUID().toString());

        boolean success = true;

        final AmazonKinesisVideo awsSdkKinesisVideoClient = AmazonKinesisVideoClientBuilder.standard().build();
        try {
            final CreateStreamRequest createStreamRequest = new CreateStreamRequest()
                    .withStreamName(this.streamName)
                    .withDataRetentionInHours(2);
            awsSdkKinesisVideoClient.createStream(createStreamRequest);
        } catch (final Exception e) {
            success = false;
            log.error("Failed to create the stream: {}", this.streamName, e);
        }

        assertTrue("There was an error cleaning up the streams, check the logs above!", success);
    }

    @After
    public void teardown() {
        boolean success = true;

        final AmazonKinesisVideo awsSdkKinesisVideoClient = AmazonKinesisVideoClientBuilder.standard().build();
        try {
            final DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest().withStreamName(this.streamName);
            final DescribeStreamResult describeStreamResult = awsSdkKinesisVideoClient.describeStream(describeStreamRequest);

            final DeleteStreamRequest deleteStreamRequest = new DeleteStreamRequest()
                    .withStreamARN(describeStreamResult.getStreamInfo().getStreamARN())
                    .withCurrentVersion(describeStreamResult.getStreamInfo().getVersion());
            awsSdkKinesisVideoClient.deleteStream(deleteStreamRequest);
        } catch (final Exception e) {
            success = false;
            log.error("Failed to delete the stream: {}", this.streamName, e);
        }

        assertTrue("There was an error cleaning up the streams, check the logs above!", success);
    }

    @Test
    @SuppressWarnings({"ConstantConditions"})
    public void whenPutFrameAfterStreamStopped_thenProducerExceptionThrown() throws Exception {
        final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        final KinesisVideoClientConfiguration configuration = KinesisVideoClientConfiguration.builder()
                .withCredentialsProvider(JavaCredentialsFactory.createKinesisVideoCredentialsProvider(DefaultAWSCredentialsProviderChain.getInstance()))
                .build();
        final NativeKinesisVideoClient client = new NativeKinesisVideoClient(configuration, new JavaKinesisVideoServiceClient(), executorService);

        final String deviceName = "whenPutFrameAfterStreamStopped_thenProducerExceptionThrown";
        final KinesisVideoProducer producer = client.initializeNewKinesisVideoProducer(createTestDeviceInfo(deviceName));

        final StreamInfo streamInfo = createStreamInfo(this.streamName);
        final List<KinesisVideoFragmentAck> acksReceived = new ArrayList<>();
        final StreamCallbacks streamCallbacks = new DefaultStreamCallbacks() {
            @Override
            public void fragmentAckReceived(long uploadHandle, @Nonnull KinesisVideoFragmentAck fragmentAck) throws ProducerException {
                super.fragmentAckReceived(uploadHandle, fragmentAck);
                acksReceived.add(fragmentAck);
            }
        };
        final KinesisVideoProducerStream stream = producer.createStreamSync(streamInfo, streamCallbacks);

        final KinesisVideoFrame[] frames = createFrameset();
        for (int i = 0; i < NUMBER_OF_FRAMES_TO_STREAM; i++) {
            stream.putFrame(frames[i]);
            Thread.sleep(FRAME_DURATION_MS);
        }

        stream.stopStreamSync();

        final KinesisVideoFrame frameAfterFree = frames[NUMBER_OF_FRAMES_TO_STREAM];
        try {
            stream.putFrame(frameAfterFree);
        } catch (final ProducerException e) {
            assertEquals("Expected to receive STATUS_STREAM_HAS_BEEN_STOPPED (0x52000052), " +
                            "but instead received: 0x" + Long.toHexString(e.getStatusCode()),
                    STATUS_STREAM_HAS_BEEN_STOPPED, e.getStatusCode());
        }

        assertFalse("Did not receive any acks!", acksReceived.isEmpty());

        executorService.shutdownNow();
    }

    @Test
    @SuppressWarnings({"ConstantConditions"})
    public void whenPutFrameAfterStreamFreedViaFreeStreams_thenExceptionThrown() throws Exception {
        final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        final KinesisVideoClientConfiguration configuration = KinesisVideoClientConfiguration.builder()
                .withCredentialsProvider(JavaCredentialsFactory.createKinesisVideoCredentialsProvider(DefaultAWSCredentialsProviderChain.getInstance()))
                .build();
        final NativeKinesisVideoClient client = new NativeKinesisVideoClient(configuration, new JavaKinesisVideoServiceClient(), executorService);

        final String deviceName = "whenPutFrameAfterStreamFreedViaFreeStreams_thenExceptionThrown";
        final KinesisVideoProducer producer = client.initializeNewKinesisVideoProducer(createTestDeviceInfo(deviceName));

        final StreamInfo streamInfo = createStreamInfo(this.streamName);
        final List<KinesisVideoFragmentAck> acksReceived = new ArrayList<>();
        final StreamCallbacks streamCallbacks = new DefaultStreamCallbacks() {
            @Override
            public void fragmentAckReceived(long uploadHandle, @Nonnull KinesisVideoFragmentAck fragmentAck) throws ProducerException {
                super.fragmentAckReceived(uploadHandle, fragmentAck);
                acksReceived.add(fragmentAck);
            }
        };
        final KinesisVideoProducerStream stream = producer.createStreamSync(streamInfo, streamCallbacks);

        final KinesisVideoFrame[] frames = createFrameset();
        for (int i = 0; i < NUMBER_OF_FRAMES_TO_STREAM; i++) {
            stream.putFrame(frames[i]);
            Thread.sleep(FRAME_DURATION_MS);
        }

        stream.stopStreamSync();
        producer.freeStreams();

        final KinesisVideoFrame frameAfterFree = frames[NUMBER_OF_FRAMES_TO_STREAM];
        try {
            stream.putFrame(frameAfterFree);
        } catch (final IllegalStateException e) {
            // Expected
            log.info("Received expected IllegalStateException from putFrame", e);
        } catch (final ProducerException e) {
            log.error("PutFrame after free threw an unexpected exception", e);
            fail("Unexpected exception: " + e);
        }

        assertFalse("Did not receive any acks!", acksReceived.isEmpty());

        executorService.shutdownNow();
    }

    @Test
    @SuppressWarnings({"ConstantConditions"})
    public void whenPutFrameAfterStreamFreedViaStreamFreed_thenExceptionThrown() throws Exception {
        final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        final KinesisVideoClientConfiguration configuration = KinesisVideoClientConfiguration.builder()
                .withCredentialsProvider(JavaCredentialsFactory.createKinesisVideoCredentialsProvider(DefaultAWSCredentialsProviderChain.getInstance()))
                .build();
        final NativeKinesisVideoClient client = new NativeKinesisVideoClient(configuration, new JavaKinesisVideoServiceClient(), executorService);

        final String deviceName = "whenPutFrameAfterStreamFreedViaStreamFreed_thenExceptionThrown";
        final KinesisVideoProducer producer = client.initializeNewKinesisVideoProducer(createTestDeviceInfo(deviceName));

        final StreamInfo streamInfo = createStreamInfo(this.streamName);
        final List<KinesisVideoFragmentAck> acksReceived = new ArrayList<>();
        final StreamCallbacks streamCallbacks = new DefaultStreamCallbacks() {
            @Override
            public void fragmentAckReceived(final long uploadHandle, @Nonnull final KinesisVideoFragmentAck fragmentAck) throws ProducerException {
                super.fragmentAckReceived(uploadHandle, fragmentAck);
                acksReceived.add(fragmentAck);
            }
        };
        final KinesisVideoProducerStream stream = producer.createStreamSync(streamInfo, streamCallbacks);

        final KinesisVideoFrame[] frames = createFrameset();
        for (int i = 0; i < NUMBER_OF_FRAMES_TO_STREAM; i++) {
            stream.putFrame(frames[i]);
            Thread.sleep(FRAME_DURATION_MS);
        }

        stream.stopStreamSync();
        stream.streamFreed();

        final KinesisVideoFrame frameAfterFree = frames[NUMBER_OF_FRAMES_TO_STREAM];
        try {
            stream.putFrame(frameAfterFree);
        } catch (final IllegalStateException e) {
            // Expected
            log.info("Received expected IllegalStateException from putFrame", e);
        } catch (final ProducerException e) {
            log.error("PutFrame after free threw an unexpected exception", e);
            fail("Unexpected exception: " + e);
        }

        assertFalse("Did not receive any acks!", acksReceived.isEmpty());

        executorService.shutdownNow();
    }

    @Test
    @SuppressWarnings({"ConstantConditions"})
    public void whenRawPutFrameCalledAfterStreamFreed_thenExceptionThrown() throws Exception {
        final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2);
        final KinesisVideoCredentialsProvider credentialsProvider = JavaCredentialsFactory
                .createKinesisVideoCredentialsProvider(DefaultAWSCredentialsProviderChain.getInstance());
        final KinesisVideoClientConfiguration configuration = KinesisVideoClientConfiguration.builder()
                .withCredentialsProvider(credentialsProvider)
                .build();

        final NativeKinesisVideoProducerJni jni = new NativeKinesisVideoProducerJni(
                new DefaultAuthCallbacks(credentialsProvider, executorService,
                        LogManager.getLogger(NativeKinesisVideoProducerJni.class)),
                new DefaultStorageCallbacks(),
                new DefaultServiceCallbacksImpl(
                        LogManager.getLogger(DefaultServiceCallbacksImpl.class),
                        executorService, configuration, new JavaKinesisVideoServiceClient())
        );

        final String deviceName = "whenRawPutFrameCalledAfterStreamFreed_thenExceptionThrown";
        jni.createSync(createTestDeviceInfo(deviceName));
        assertTrue(jni.isInitialized());
        assertTrue(jni.isReady());

        final StreamInfo streamInfo = createStreamInfo(this.streamName);
        final List<KinesisVideoFragmentAck> acksReceived = new ArrayList<>();

        final CountDownLatch streamReady = new CountDownLatch(1);
        final KinesisVideoProducerStream stream = jni.createStream(streamInfo, new DefaultStreamCallbacks() {
            @Override
            public void streamReady() throws ProducerException {
                super.streamReady();
                streamReady.countDown();
            }

            @Override
            public void fragmentAckReceived(final long uploadHandle, @Nonnull final KinesisVideoFragmentAck fragmentAck)
                    throws ProducerException {
                super.fragmentAckReceived(uploadHandle, fragmentAck);
                acksReceived.add(fragmentAck);
            }
        });
        assertTrue("Timed out while waiting for stream to be created!",
                streamReady.await(TEN_SECONDS, TimeUnit.SECONDS));
        final long streamHandle = stream.getStreamHandle();

        final KinesisVideoFrame[] frames = createFrameset();

        for (int i = 0; i < NUMBER_OF_FRAMES_TO_STREAM; i++) {
            final KinesisVideoFrame frame = frames[i];
            jni.putFrame(streamHandle, frame);

            Thread.sleep(FRAME_DURATION_MS);
        }

        stream.stopStreamSync();
        jni.freeStream(stream);

        final KinesisVideoFrame frameAfterFree = frames[NUMBER_OF_FRAMES_TO_STREAM];
        try {
            jni.putFrame(streamHandle, frameAfterFree);
        } catch (final IllegalArgumentException e) {
            // Expected
            log.info("Received expected IllegalArgumentException from putFrame", e);
        } catch (final Exception e) {
            log.error("PutFrame after free threw an unexpected exception", e);
            fail("Unexpected exception: " + e);
        }

        assertFalse("Did not receive any acks!", acksReceived.isEmpty());

        executorService.shutdownNow();
    }

    @Test
    @SuppressWarnings({"ConstantConditions"})
    public void whenRawPutFrameCalledWithInvalidHandle_thenExceptionThrown() throws Exception {
        final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2);
        final KinesisVideoCredentialsProvider credentialsProvider = JavaCredentialsFactory
                .createKinesisVideoCredentialsProvider(DefaultAWSCredentialsProviderChain.getInstance());
        final KinesisVideoClientConfiguration configuration = KinesisVideoClientConfiguration.builder()
                .withCredentialsProvider(credentialsProvider)
                .build();

        final NativeKinesisVideoProducerJni jni = new NativeKinesisVideoProducerJni(
                new DefaultAuthCallbacks(credentialsProvider, executorService,
                        LogManager.getLogger(NativeKinesisVideoProducerJni.class)),
                new DefaultStorageCallbacks(),
                new DefaultServiceCallbacksImpl(
                        LogManager.getLogger(DefaultServiceCallbacksImpl.class),
                        executorService, configuration, new JavaKinesisVideoServiceClient())
        );

        final String deviceName = "whenRawPutFrameCalledWithInvalidHandle_thenExceptionThrown";
        jni.createSync(createTestDeviceInfo(deviceName));
        assertTrue(jni.isInitialized());
        assertTrue(jni.isReady());

        final KinesisVideoFrame[] frames = createFrameset();
        final long randomStreamHandle = 0x12345678L; // An arbitrary address

        final KinesisVideoFrame frameAfterFree = frames[NUMBER_OF_FRAMES_TO_STREAM];
        try {
            jni.putFrame(randomStreamHandle, frameAfterFree);
        } catch (final IllegalArgumentException e) {
            // Expected
            log.info("Received expected IllegalArgumentException from putFrame", e);
        } catch (final Exception e) {
            log.error("PutFrame after free threw an unexpected exception", e);
            fail("Unexpected exception: " + e);
        }

        executorService.shutdownNow();
    }

    /**
     * Creates {@link #NUMBER_OF_FRAMES_TO_STREAM} + 1 frames to stream.
     * The payload of the last frame is {@code Put frame after free!}.
     */
    @Nonnull
    private KinesisVideoFrame[] createFrameset() {
        final KinesisVideoFrame[] frames = new KinesisVideoFrame[NUMBER_OF_FRAMES_TO_STREAM + 1];
        long currentFrameTs = System.currentTimeMillis();
        for (int i = 0; i < NUMBER_OF_FRAMES_TO_STREAM + 1; i++) {
            final long timestampUs = currentFrameTs * 1000;

            final ByteBuffer frameData;
            if (i == NUMBER_OF_FRAMES_TO_STREAM) {
                frameData = ByteBuffer.wrap("Put frame after free!".getBytes(StandardCharsets.UTF_8));
            } else {
                final String frameContents = String.join("-", this.streamName, "frame",
                        Integer.toString(i), "ts", Long.toString(timestampUs));
                frameData = ByteBuffer.wrap(frameContents.getBytes(StandardCharsets.UTF_8));
            }

            final KinesisVideoFrame frame = new KinesisVideoFrame(
                    i,
                    i % GOP == 0 ? FrameFlags.FRAME_FLAG_KEY_FRAME : FrameFlags.FRAME_FLAG_NONE,
                    timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
                    timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
                    FRAME_DURATION_MS * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                    frameData
            );
            frames[i] = frame;
            currentFrameTs += FRAME_DURATION_MS;
        }

        return frames;
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

    private StreamInfo createStreamInfo(@Nonnull final String streamName) {
        assumeNotNull("Stream name cannot be null", streamName);
        assumeFalse("Stream name cannot be empty", streamName.isEmpty());

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
                StreamInfoConstants.FRAME_RATE_25,
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
                false
        );
    }
}
