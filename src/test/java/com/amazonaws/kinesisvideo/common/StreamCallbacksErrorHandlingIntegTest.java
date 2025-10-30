package com.amazonaws.kinesisvideo.common;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.kinesisvideo.client.KinesisVideoClient;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.java.auth.JavaCredentialsFactory;
import com.amazonaws.kinesisvideo.java.client.KinesisVideoJavaClientFactory;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSource;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSourceConfiguration;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
import com.amazonaws.kinesisvideo.producer.FragmentAckType;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFragmentAck;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StorageInfo;
import com.amazonaws.kinesisvideo.producer.Tag;
import com.amazonaws.kinesisvideo.streaming.DefaultStreamCallbacks;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;

public class StreamCallbacksErrorHandlingIntegTest {

    private static final Logger log = LogManager.getLogger(StreamCallbacksErrorHandlingIntegTest.class);

    private static final int FPS_25 = 25;
    private static final String H264_FILES_DIR = "src/main/resources/data/h264/";
    private static final String IMAGE_FILENAME_FORMAT = "frame-%03d.h264";
    private static final int START_FILE_INDEX = 1;
    private static final int END_FILE_INDEX = 375;

    private static final int STREAMING_DURATION_MS = 3000;
    private static final int SETUP_TEARDOWN_PADDING_MS = 5000;

    private String streamName = null;

    @Rule
    public Timeout globalTimeout = Timeout.millis(STREAMING_DURATION_MS + SETUP_TEARDOWN_PADDING_MS);

    @Before
    public void setUp() {
        final boolean jniLoaded = ProducerTestBase.isJNILoaded();
        if (!jniLoaded) {
            fail("JNI library not found.");
        }

        assumeNotNull("Unable to find credentials!", DefaultAWSCredentialsProviderChain.getInstance().getCredentials());
    }

    @After
    public void tearDown() {
        if (this.streamName != null) {
            final AmazonKinesisVideo awsSdkKinesisVideoClient = AmazonKinesisVideoClientBuilder.standard().build();
            try {
                final DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest().withStreamName(this.streamName);
                final DescribeStreamResult describeStreamResult = awsSdkKinesisVideoClient.describeStream(describeStreamRequest);

                final DeleteStreamRequest deleteStreamRequest = new DeleteStreamRequest()
                        .withStreamARN(describeStreamResult.getStreamInfo().getStreamARN())
                        .withCurrentVersion(describeStreamResult.getStreamInfo().getVersion());
                awsSdkKinesisVideoClient.deleteStream(deleteStreamRequest);
            } catch (final Exception e) {
                log.error("Failed to delete the stream: {}", this.streamName, e);
                fail("Failed to delete the stream: " + this.streamName);
            }
        }
    }

    /**
     * Fault-injection scenario: Throwing a runtime exception in the user-implementation of the fragmentAckReceived
     * callback. The SDK should log and ignore application errors and continue.
     */
    @Test
    public void when_streamCallbacksThrowRuntimeException_thenProducerContinuesNormally()
            throws KinesisVideoException, InterruptedException {
        final String testName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        final String testStreamName = Optional.ofNullable(System.getenv("TEST_STREAMS_PREFIX")).orElse("") + "StreamCallbacksErrorHandlingIntegTest" + testName + "_" + System.currentTimeMillis();
        this.streamName = testStreamName;
        createStream(this.streamName);

        final KinesisVideoClientConfiguration configuration = KinesisVideoClientConfiguration.builder()
                .withCredentialsProvider(JavaCredentialsFactory
                        .createKinesisVideoCredentialsProvider(DefaultAWSCredentialsProviderChain
                                .getInstance()
                        )
                )
                .build();
        final DeviceInfo deviceInfo = createTestDeviceInfo(testName + "-device");
        final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2);
        final KinesisVideoClient client = KinesisVideoJavaClientFactory.createKinesisVideoClient(configuration,
                deviceInfo, executorService);

        assertTrue("Client should be initialized!", client.isInitialized());

        final List<KinesisVideoFragmentAck> acksReceived = new ArrayList<>();
        final AtomicBoolean streamReadyCalled = new AtomicBoolean(false);
        final AtomicBoolean streamClosedCalled = new AtomicBoolean(false);
        final MediaSource mediaSource = new ImageFileMediaSource(testStreamName);
        mediaSource.configure(new ImageFileMediaSourceConfiguration.Builder()
                .fps(FPS_25)
                .dir(H264_FILES_DIR)
                .filenameFormat(IMAGE_FILENAME_FORMAT)
                .startFileIndex(START_FILE_INDEX)
                .endFileIndex(END_FILE_INDEX)
                .streamCallbacks(new DefaultStreamCallbacks() {
                    @Override
                    public void fragmentAckReceived(final long uploadHandle, @Nonnull final KinesisVideoFragmentAck fragmentAck) throws ProducerException {
                        super.fragmentAckReceived(uploadHandle, fragmentAck);
                        acksReceived.add(fragmentAck);

                        assertNotEquals("Received an unexpected ERROR ack: " + fragmentAck,
                                FragmentAckType.FRAGMENT_ACK_TYPE_ERROR, fragmentAck.getAckType().getIntType());

                        throw new RuntimeException("Throwing an exception to check error handling in fragmentAckReceived!");
                    }

                    @Override
                    public void streamErrorReport(final long uploadHandle, final long frameTimecode, final long statusCode) throws ProducerException {
                        super.streamErrorReport(uploadHandle, frameTimecode, statusCode);

                        fail("Received an unexpected ERROR for the stream: 0x" + Long.toHexString(statusCode));
                    }

                    @Override
                    public void streamReady() throws ProducerException {
                        super.streamReady();
                        streamReadyCalled.set(true);
                    }

                    @Override
                    public void streamClosed(final long uploadHandle) throws ProducerException {
                        super.streamClosed(uploadHandle);
                        streamClosedCalled.set(true);
                    }
                })
                .build());

        client.registerMediaSource(mediaSource);

        mediaSource.start();

        log.info("Started media source");
        Thread.sleep(STREAMING_DURATION_MS);

        log.info("Stopping media source");
        mediaSource.stop();
        client.unregisterMediaSource(mediaSource);
        mediaSource.free();

        final long persistedAcksCount = acksReceived.stream()
                .filter(ack -> ack.getAckType().getIntType() == FragmentAckType.FRAGMENT_ACK_TYPE_PERSISTED)
                .count();

        assertTrue("Didn't receive any PERSISTED ACKs. Received: " + acksReceived, persistedAcksCount > 0);
        assertTrue("StreamReady callback wasn't called", streamReadyCalled.get());
        assertTrue("StreamClosed callback wasn't called", streamClosedCalled.get());

        executorService.shutdownNow();
    }

    /**
     * Fault-injection scenario: Throwing an Error in the user-implementation of the fragmentAckReceived
     * callback. The SDK should log and ignore application errors and continue.
     */
    @Test
    public void when_streamCallbacksThrowError_thenProducerContinuesNormally()
            throws KinesisVideoException, InterruptedException {
        final String testName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        final String testStreamName = Optional.ofNullable(System.getenv("TEST_STREAMS_PREFIX")).orElse("") + "StreamCallbacksErrorHandlingIntegTest" + testName + "_" + System.currentTimeMillis();
        this.streamName = testStreamName;
        createStream(this.streamName);

        final KinesisVideoClientConfiguration configuration = KinesisVideoClientConfiguration.builder()
                .withCredentialsProvider(JavaCredentialsFactory
                        .createKinesisVideoCredentialsProvider(DefaultAWSCredentialsProviderChain
                                .getInstance()
                        )
                )
                .build();
        final DeviceInfo deviceInfo = createTestDeviceInfo(testName + "-device");
        final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2);
        final KinesisVideoClient client = KinesisVideoJavaClientFactory.createKinesisVideoClient(configuration,
                deviceInfo, executorService);

        assertTrue("Client should be initialized!", client.isInitialized());

        final List<KinesisVideoFragmentAck> acksReceived = new ArrayList<>();
        final AtomicBoolean streamReadyCalled = new AtomicBoolean(false);
        final AtomicBoolean streamClosedCalled = new AtomicBoolean(false);
        final MediaSource mediaSource = new ImageFileMediaSource(testStreamName);
        mediaSource.configure(new ImageFileMediaSourceConfiguration.Builder()
                .fps(FPS_25)
                .dir(H264_FILES_DIR)
                .filenameFormat(IMAGE_FILENAME_FORMAT)
                .startFileIndex(START_FILE_INDEX)
                .endFileIndex(END_FILE_INDEX)
                .streamCallbacks(new DefaultStreamCallbacks() {
                    @Override
                    public void fragmentAckReceived(final long uploadHandle, @Nonnull final KinesisVideoFragmentAck fragmentAck) throws ProducerException {
                        super.fragmentAckReceived(uploadHandle, fragmentAck);
                        acksReceived.add(fragmentAck);

                        assertNotEquals("Received an unexpected ERROR ack: " + fragmentAck,
                                FragmentAckType.FRAGMENT_ACK_TYPE_ERROR, fragmentAck.getAckType().getIntType());

                        throw new Error("Throwing an exception to check error handling in fragmentAckReceived!");
                    }

                    @Override
                    public void streamErrorReport(final long uploadHandle, final long frameTimecode, final long statusCode) throws ProducerException {
                        super.streamErrorReport(uploadHandle, frameTimecode, statusCode);

                        fail("Received an unexpected ERROR for the stream: 0x" + Long.toHexString(statusCode));
                    }

                    @Override
                    public void streamReady() throws ProducerException {
                        super.streamReady();
                        streamReadyCalled.set(true);
                    }

                    @Override
                    public void streamClosed(final long uploadHandle) throws ProducerException {
                        super.streamClosed(uploadHandle);
                        streamClosedCalled.set(true);
                    }
                })
                .build());

        client.registerMediaSource(mediaSource);

        mediaSource.start();

        log.info("Started media source");
        Thread.sleep(STREAMING_DURATION_MS);

        log.info("Stopping media source");
        mediaSource.stop();
        client.unregisterMediaSource(mediaSource);
        mediaSource.free();

        final long persistedAcksCount = acksReceived.stream()
                .filter(ack -> ack.getAckType().getIntType() == FragmentAckType.FRAGMENT_ACK_TYPE_PERSISTED)
                .count();

        assertTrue("Didn't receive any PERSISTED ACKs. Received: " + acksReceived, persistedAcksCount > 0);
        assertTrue("StreamReady callback wasn't called", streamReadyCalled.get());
        assertTrue("StreamClosed callback wasn't called", streamClosedCalled.get());

        executorService.shutdownNow();
    }

    private void createStream(@Nonnull final String streamName) {
        assumeNotNull(streamName, "StreamName cannot be null");

        final AmazonKinesisVideo awsSdkKinesisVideoClient = AmazonKinesisVideoClientBuilder.standard().build();
        try {
            final CreateStreamRequest createStreamRequest = new CreateStreamRequest()
                    .withStreamName(streamName)
                    .withDataRetentionInHours(2);
            awsSdkKinesisVideoClient.createStream(createStreamRequest);
        } catch (final Exception e) {
            log.error("Failed to create the stream: {}", streamName, e);
            fail("Failed to create the stream: " + streamName);
        }
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
}
