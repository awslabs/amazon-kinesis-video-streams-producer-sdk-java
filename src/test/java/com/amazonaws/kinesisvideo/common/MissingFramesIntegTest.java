package com.amazonaws.kinesisvideo.common;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.java.service.CachedInfoMultiAuthServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.producer.ClientInfo;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
import com.amazonaws.kinesisvideo.producer.FragmentAckType;
import com.amazonaws.kinesisvideo.producer.FrameOrderMode;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.MkvTrackInfoType;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StorageInfo;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Tag;
import com.amazonaws.kinesisvideo.producer.Time;
import com.amazonaws.kinesisvideo.producer.TrackInfo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClient;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.model.APIName;
import com.amazonaws.services.kinesisvideo.model.DeleteStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamResult;
import com.amazonaws.services.kinesisvideo.model.GetDataEndpointRequest;
import com.amazonaws.services.kinesisvideo.model.GetDataEndpointResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_BITRATE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_REPLAY_DURATION;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_STALENESS_DURATION;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_TIMESCALE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.MAX_LATENCY_ZERO;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.NOT_ADAPTIVE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.NO_KMS_KEY_ID;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RECALCULATE_METRICS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RECOVER_ON_FAILURE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RELATIVE_TIMECODES;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.REQUEST_FRAGMENT_ACKS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RETENTION_ONE_HOUR;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.USE_FRAME_TIMECODES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * Integration test for a MediaSource that has a flaky 2nd track (inconsistently produces frames).
 *
 * <p>The KVS backend requires the tracks in each fragment, to be consistent throughout the PutMedia connection.
 * When one track stops producing frames, the service will close the PutMedia connection with 4011: FRAMES_MISSING_FOR_TRACK
 * error ack. Retry behavior: The SDK will drop the current fragment, open a new PutMedia connection, and resume
 * from the next fragment. This continues until the 2nd track recovers. Ideally, the application should stop the stream
 * until the tracks are consistent again, then start the stream back up to avoid unnecessary retries.</p>
 *
 * <p><strong>Test Scenarios:</strong></p>
 * <ul>
 *   <li>{@link #test_whenCameraUnreachableScenario_thenWhenCameraIsReachableAgainItRecovers()}:
 *      Testing stream recovery once the 2nd track resumes producing frames again and without stopping the stream</li>
 * </ul>
 */
public class MissingFramesIntegTest extends ProducerTestBase {

    private static final Logger log = LogManager.getLogger(MissingFramesIntegTest.class);
    private static final int STORAGE_INFO_VERSION_ZERO = 0;
    private static final int ONE_SECOND_HUNDREDS_OF_NANOS = 1000 * 10000;
    private static final int TEN_SECONDS_HUNDREDS_OF_NANOS = 10 * ONE_SECOND_HUNDREDS_OF_NANOS;

    private static final int PUT_MEDIA_ERROR_ACK_FRAMES_MISSING_FOR_TRACK = 4011;
    private static final long STATUS_ACK_ERR_FRAMES_MISSING_FOR_TRACK = 0x5200007d;
    private static final long STATUS_MAX_FRAME_TIMESTAMP_DELTA_BETWEEN_TRACKS_EXCEEDED = 0x52000085;

    /**
     * List of stream names created during tests that need to be cleaned up.
     */
    private final List<String> createdStreams = new ArrayList<>();

    /**
     * Sets up the test environment before each test method.
     * This will fail if the JNI (java.library.path) can't be loaded.
     */
    @Before
    @SuppressWarnings({"UnnecessaryLocalVariable", "ExtractMethodRecommender"})
    public void setUp() {
        final boolean jniLoaded = isJNILoaded();
        if (!jniLoaded) {
            fail("JNI library not found.");
        }

        final int deviceInfoVersionOne = 1;
        final long fourGBStorageSizeInBytes = 4L * 1024 * 1024 * 1024;
        final int oneStream = 1;
        final String clientId = "TestClient";

        final long createClientTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;
        final long createStreamTimeout = TEN_SECONDS_HUNDREDS_OF_NANOS;
        final long stopStreamTimeout = TEN_SECONDS_HUNDREDS_OF_NANOS;
        final long offlineBufferAvailabilityTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;
        final int logLevel = ClientInfo.DEFAULT_LOG_LEVEL;

        // ClientInfo V1 fields
        final boolean doLogMetrics = true;

        // ClientInfo V2 fields
        final long metricsLoggingPeriod = ONE_SECOND_HUNDREDS_OF_NANOS;
        final ClientInfo.AutomaticStreamingFlags automaticStreamingFlags = ClientInfo.AutomaticStreamingFlags.AUTOMATIC_STREAMING_ALWAYS_CONTINUOUS;
        final long reservedCallbackPeriod = ONE_SECOND_HUNDREDS_OF_NANOS;

        // ClientInfo V3 fields
        final long serviceCallCompletionTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;
        final long serviceCallConnectionTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;

        final ClientInfo clientInfoV3 = new ClientInfo(createClientTimeout, createStreamTimeout, stopStreamTimeout,
                offlineBufferAvailabilityTimeout, logLevel, doLogMetrics, automaticStreamingFlags,
                serviceCallCompletionTimeout, serviceCallConnectionTimeout);

        createCachedProducer(new DeviceInfo(deviceInfoVersionOne,
                MissingFramesIntegTest.class.getSimpleName(),
                new StorageInfo(STORAGE_INFO_VERSION_ZERO,
                        StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM,
                        fourGBStorageSizeInBytes,
                        SPILL_RATIO_PERCENT,
                        ""),
                oneStream, null, clientId, clientInfoV3));
        assumeTrue("ServiceCallbacks should be caching type!", this.serviceCallbacks instanceof CachedInfoMultiAuthServiceCallbacksImpl);
    }

    /**
     * Cleans up resources after each test method.
     *
     * <p>This method:</p>
     * <ul>
     *   <li>Clears all tracking lists and stream information</li>
     *   <li>Frees all streams and producer resources</li>
     * </ul>
     */
    @After
    public void tearDown() {
        boolean failure = false;

        try {
            freeStreams();
        } catch (final Exception e) {
            failure = true;
            log.error("Failed to free streams {}", this.createdStreams, e);
        }

        final AmazonKinesisVideo awsSdkKinesisVideoClient = AmazonKinesisVideoClient.builder().build();
        final String prefix = Optional.ofNullable(System.getenv("TEST_STREAMS_PREFIX")).orElse("");
        for (final String streamName : this.createdStreams) {
            final String finalStreamName = prefix + streamName;
            try {
                final DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest().withStreamName(finalStreamName);
                final DescribeStreamResult describeStreamResult = awsSdkKinesisVideoClient.describeStream(describeStreamRequest);

                final DeleteStreamRequest deleteStreamRequest = new DeleteStreamRequest()
                        .withStreamARN(describeStreamResult.getStreamInfo().getStreamARN())
                        .withCurrentVersion(describeStreamResult.getStreamInfo().getVersion());
                awsSdkKinesisVideoClient.deleteStream(deleteStreamRequest);
            } catch (final Exception e) {
                failure = true;
                log.error("Failed to delete the stream: {}", finalStreamName, e);
            }
        }

        this.createdStreams.clear();

        assertFalse("An exception happened during cleanup!", failure);
    }

    /**
     * Creates test frame data for streaming.
     *
     * @return byte array containing test frame data
     */
    private byte[] createTestFrameData() {
        final byte[] frameData = new byte[TEST_FRAME_SIZE_BYTES];
        for (int i = 0; i < frameData.length; i++) {
            frameData[i] = (byte) (i % 256);
        }
        return frameData;
    }

    /**
     * Creates a stream with the given streamName (plus prefix) and tracks.
     * Also adds the info to the cache.
     */
    @SuppressWarnings("ConstantConditions")
    protected KinesisVideoProducerStream createTestStream(@Nonnull final String streamName,
                                                          @Nonnull final TrackInfo[] tracks) {
        assumeTrue("StreamName cannot be null", streamName != null);
        assumeTrue("Tracks cannot be null", tracks != null);
        KinesisVideoProducerStream kinesisVideoProducerStream = null;

        final String prefix = Optional.ofNullable(System.getenv("TEST_STREAMS_PREFIX")).orElse("");
        final String finalStreamName = prefix + streamName;
        prepareStream(finalStreamName);

        final CachedInfoMultiAuthServiceCallbacksImpl cache = (CachedInfoMultiAuthServiceCallbacksImpl) this.serviceCallbacks;

        try {
            final AmazonKinesisVideo kinesisVideoClient = AmazonKinesisVideoClientBuilder.defaultClient();

            // Cache DescribeStream
            final DescribeStreamResult describeStreamResult = kinesisVideoClient.describeStream(new DescribeStreamRequest().withStreamName(finalStreamName));
            cache.addStreamInfoToCache(finalStreamName, describeStreamResult);

            // Cache GetDataEndpoint
            final GetDataEndpointResult getDataEndpointResult = kinesisVideoClient.getDataEndpoint(new GetDataEndpointRequest()
                    .withStreamName(finalStreamName)
                    .withAPIName(APIName.PUT_MEDIA)
            );
            cache.addStreamingEndpointToCache(finalStreamName, getDataEndpointResult.getDataEndpoint());

            // Cache GetStreamingToken
            cache.addCredentialsProviderToCache(finalStreamName, DefaultAWSCredentialsProviderChain.getInstance());
        } catch (final Exception e) {
            log.error("Failed to fetch the stream details: {}", finalStreamName, e);
            fail();
        }

        final StreamInfo streamInfo = new StreamInfo(
                StreamInfo.STREAM_INFO_CURRENT_VERSION,
                finalStreamName,
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                "audio/PCM",
                NO_KMS_KEY_ID,
                RETENTION_ONE_HOUR,
                NOT_ADAPTIVE,
                MAX_LATENCY_ZERO,
                1L * Time.HUNDREDS_OF_NANOS_IN_A_SECOND, // 1 second GOP
                false, // no keyframe fragmentation
                USE_FRAME_TIMECODES,
                RELATIVE_TIMECODES,
                REQUEST_FRAGMENT_ACKS,
                RECOVER_ON_FAILURE,
                DEFAULT_BITRATE,
                this.fps_,
                TEN_SECONDS_HUNDREDS_OF_NANOS, // 10s buffer
                DEFAULT_REPLAY_DURATION,
                DEFAULT_STALENESS_DURATION,
                DEFAULT_TIMESCALE,
                RECALCULATE_METRICS,
                new Tag[]{
                        new Tag("device", "Test Device"),
                        new Tag("stream", "Test Stream")},
                StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_FLAG_NONE,
                null,
                tracks,
                FrameOrderMode.FRAME_ORDERING_MODE_MULTI_TRACK_AV_COMPARE_DTS_ONE_MS_COMPENSATE,
                StreamInfo.StorePressurePolicy.CONTENT_STORE_PRESSURE_POLICY_DROP_TAIL_ITEM,
                this.allowStreamCreation
        );

        try {
            kinesisVideoProducerStream = this.kinesisVideoProducer.createStreamSync(streamInfo, this.streamCallbacks);
        } catch (final Exception e) {
            log.error("Failed to create the stream: {}", finalStreamName, e);
            fail();
        }

        return kinesisVideoProducerStream;
    }

    /**
     * Tests that the producer correctly handles and reports {@code KMS_KEY_INVALID_STATE} error when streaming
     * to a stream configured with a marked for deletion KMS key.
     *
     * <p><strong>Test Flow:</strong></p>
     * <ol>
     *   <li>Setup 1 stream with 2 audio tracks</li>
     *   <li>Use the caching provider</li>
     *   <li>Stream normally for 40s</li>
     *   <li>One track is missing for 45s</li>
     *   <li>Stream normally again for 40s</li>
     * </ol>
     *
     * <p><strong>Expected Behavior:</strong></p>
     * <ul>
     *   <li>The stream should recover after both tracks return</li>
     * </ul>
     */
    @Test
    public void test_whenCameraUnreachableScenario_thenWhenCameraIsReachableAgainItRecovers()
            throws InterruptedException {
        final String testName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        final String testStreamName = "MissingFramesIntegTest_" + testName + "_" + System.currentTimeMillis();

        final TrackInfo[] tracks = new TrackInfo[]{
                new TrackInfo(1, "A_MS/ACM", "audio-track-1", null, MkvTrackInfoType.AUDIO),
                new TrackInfo(2, "A_MS/ACM", "audio-track-2", null, MkvTrackInfoType.AUDIO)
        };

        // 1 - Create a Kinesis Video Stream with two tracks
        final KinesisVideoProducerStream stream = createTestStream(testStreamName, tracks);
        assertNotNull("Stream should be created", stream);
        this.createdStreams.add(testStreamName);

        // 2 - Start streaming normally for 5s
        final int framesCorrectly = 250;
        final int framesAfterFailing = 2250;
        final int fps = 50;
        long currentFrameTs = System.currentTimeMillis();
        final byte[] frameData = createTestFrameData();

        for (int frameIndex = 0; frameIndex < framesCorrectly; frameIndex++) {
            final long timestampUs = currentFrameTs * 1000;

            final KinesisVideoFrame frame = new KinesisVideoFrame(
                    frameIndex,
                    FRAME_FLAG_KEY_FRAME,
                    timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
                    timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
                    this.frameDuration_,
                    ByteBuffer.wrap(frameData),
                    (frameIndex % 2) + 1
            );

            try {
                stream.putFrame(frame);
            } catch (final ProducerException e) {
                log.error(e);
                fail("Unexpected exception during putFrame: " + e);
            }

            // Make sure no errors via streamErrorReport callback
            if (this.errorStatus_ != 0) {
                log.error("Received an unexpected stream error: {}", Long.toHexString(this.errorStatus_));
                fail("Received an unexpected stream error: " + Long.toHexString(this.errorStatus_));
            }

            Thread.sleep(1000 / fps);
            currentFrameTs += (1000 / fps);
        }

        // 3 - Interrupt the 2nd track for 40s
        for (int frameIndex = framesCorrectly; frameIndex < framesAfterFailing + framesCorrectly; frameIndex++) {
            final long timestampUs = currentFrameTs * 1000;

            final KinesisVideoFrame frame = new KinesisVideoFrame(
                    frameIndex,
                    FRAME_FLAG_KEY_FRAME,
                    timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
                    timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
                    1000 / fps * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                    ByteBuffer.wrap(frameData),
                    1
            );

            try {
                stream.putFrame(frame);
            } catch (final ProducerException e) {
                log.error(e);
                assertEquals("Expected to receive STATUS_MAX_FRAME_TIMESTAMP_DELTA_BETWEEN_TRACKS_EXCEEDED",
                        STATUS_MAX_FRAME_TIMESTAMP_DELTA_BETWEEN_TRACKS_EXCEEDED, e.getStatusCode());
            }

            if (this.errorStatus_ != 0) {
                assertEquals("Expected to receive stream error: 0x" + Long.toHexString(this.errorStatus_),
                        STATUS_ACK_ERR_FRAMES_MISSING_FOR_TRACK, this.errorStatus_);
            }

            Thread.sleep(1000 / fps);
            currentFrameTs += (1000 / fps);
        }

        // Stream normally (recover) for 20s
        for (int frameIndex = framesAfterFailing + framesCorrectly; frameIndex < framesAfterFailing + framesCorrectly + 1000; frameIndex++) {
            final long timestampUs = currentFrameTs * 1000;

            final KinesisVideoFrame frame = new KinesisVideoFrame(
                    frameIndex,
                    FRAME_FLAG_KEY_FRAME,
                    timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
                    timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
                    1000 / fps * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                    ByteBuffer.wrap(frameData),
                    ((frameIndex + 1) % 2) + 1 // To alternate the track order (to start with the 2nd track to immediately allow frames in again
            );

            try {
                stream.putFrame(frame);
            } catch (final ProducerException e) {
                log.error(e);
                fail("Unexpected exception during putFrame: " + e);
            }

            Thread.sleep(1000 / fps);
            currentFrameTs += (1000 / fps);
        }

        // Wait additional time for any delayed error callbacks
        log.debug("Waiting for all acks to come in...");
        Thread.sleep(5000);

        System.out.println(this.receivedFragmentAcks_);

        // 3 - Verify PERSISTED acks
        final long persistedAcksCount = this.receivedFragmentAcks_.stream()
                .filter(ack -> ack.getAckType().getIntType() == FragmentAckType.FRAGMENT_ACK_TYPE_PERSISTED)
                .count();

        assertTrue("Didn't receive any PERSISTED ACKs. Received: " + this.receivedFragmentAcks_, persistedAcksCount > 0);

        // Wait additional time for any delayed error callbacks
        log.debug("Waiting for all acks to come in...");
        Thread.sleep(5000);

        // 4 - Verify the frames missing for track error ack count
        // The fragmentAckReceived callback implementation (test) stores all the acks received into this receivedFragmentAcks_ array
        final long numPutMediaFramesMissingForTrackAcks = this.receivedFragmentAcks_.stream()
                .filter(ack -> ack.getResult() == PUT_MEDIA_ERROR_ACK_FRAMES_MISSING_FOR_TRACK)
                .count();
        assertNotEquals("Received no 4011 acks. Expected at least one: " + this.receivedFragmentAcks_,
                0L, numPutMediaFramesMissingForTrackAcks);

        // 5 - Verify persisted acks from the recovered PutMedia
        final long timestampMsRecovery = framesAfterFailing / fps * 1000;
        final long persistedAcksAfterRecovery = this.receivedFragmentAcks_.stream()
                .filter(ack -> ack.getTimestamp() > timestampMsRecovery)
                .filter(ack -> ack.getAckType().getIntType() == FragmentAckType.FRAGMENT_ACK_TYPE_PERSISTED)
                .count();

        assertTrue("Didn't receive any PERSISTED ACKs after recovery. Received: " + this.receivedFragmentAcks_, persistedAcksAfterRecovery > 0);
    }
}
