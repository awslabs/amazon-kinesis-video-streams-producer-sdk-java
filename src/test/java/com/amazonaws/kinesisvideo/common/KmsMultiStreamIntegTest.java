package com.amazonaws.kinesisvideo.common;

import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.producer.FragmentAckType;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFragmentAck;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Time;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.model.DeleteStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamResult;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.HTTP_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Integration test for KMS key behavior with Kinesis Video Streams.
 *
 * <p>This test class validates the behavior of Kinesis Video Streams when configured with KMS encryption,
 * specifically testing the error handling when a KMS key becomes unavailable after stream creation.
 * The test is parameterized to test multiple combinations of healthy and KMS-errored streams to ensure
 * that KMS errors in one stream do not affect other streams using the same client.</p>
 *
 * <p><strong>Test Scenario:</strong></p>
 * <ul>
 *   <li>{@link #testMultipleStreamsWithKmsErrors()}: Parameterized test with a mix of healthy and unhealthy streams</li>
 * </ul>
 *
 * <p><strong>Parameterized Test Parameters:</strong></p>
 * <ul>
 *   <li>Number of healthy streams (with valid KMS keys)</li>
 *   <li>Number of KMS-errored streams (with deleted KMS keys)</li>
 * </ul>
 *
 * <p><strong>Error Code Reference:</strong></p>
 * <ul>
 *   <li>0x4506 - KMS_KEY_NOT_FOUND: The stream's specified KMS key is not found</li>
 *   <li>0x5200006A - STATUS_ACK_ERR_KMS_KEY_INVALID_STATE: KMS key is in invalid state (e.g., deleted/disabled)</li>
 * </ul>
 *
 * @see <a href="https://docs.aws.amazon.com/kinesisvideostreams/latest/dg/API_dataplane_PutMedia.html">putMedia documentation</a>
 * @see <a href="https://docs.aws.amazon.com/kinesisvideostreams/latest/dg/producer-sdk-errors.html">Error code reference</a>
 */
@RunWith(Parameterized.class)
public class KmsMultiStreamIntegTest extends ProducerTestBase {

    private static final Logger log = LogManager.getLogger(KmsMultiStreamIntegTest.class);

    /**
     * Test parameters for parameterized tests.
     * Each array contains: [numberOfHealthyStreams, numberOfKmsErroredStreams]
     *
     * @return Collection of test parameter arrays
     */
    @Parameters(name = "HealthyStreams={0}, KmsErroredStreams={1}")
    public static Collection<Object[]> testParameters() {
        return Arrays.asList(new Object[][]{
                {1, 1},  // 1 healthy stream, 1 KMS-errored stream
                {2, 1},  // 2 healthy streams, 1 KMS-errored stream
                {1, 2},  // 1 healthy stream, 2 KMS-errored streams
                {3, 2},  // 3 healthy streams, 2 KMS-errored streams
                {5, 5},  // 5 healthy streams, 5 KMS-errored streams
        });
    }

    // Video parameters
    private static final int NUM_FRAMES = 50;
    private static final int KEYFRAME_INTERVAL = 10;
    private static final int FRAMES_PER_SECOND = 5;

    // How long to wait for the putFrame loop background thread to complete
    private static final int TIMEOUT_SECS = NUM_FRAMES / FRAMES_PER_SECOND + 15;
    private static final int FORCE_SHUTDOWN_TIMEOUT_SECS = 8;

    /**
     * Number of healthy streams to create for this test run.
     */
    private final int numberOfHealthyStreams;

    /**
     * Number of KMS-errored streams to create for this test run.
     */
    private final int numberOfKmsErroredStreams;

    /**
     * Constructor for parameterized test.
     *
     * @param numberOfHealthyStreams    Number of streams with valid KMS keys
     * @param numberOfKmsErroredStreams Number of streams with deleted KMS keys
     */
    public KmsMultiStreamIntegTest(final int numberOfHealthyStreams, final int numberOfKmsErroredStreams) {
        this.numberOfHealthyStreams = numberOfHealthyStreams;
        this.numberOfKmsErroredStreams = numberOfKmsErroredStreams;
    }

    /**
     * List of KMS key IDs created during tests for cleanup purposes.
     * Keys are scheduled for deletion in the {@link #tearDown()} method.
     */
    private final List<String> createdKmsKeys = new ArrayList<>();

    /**
     * List of stream names created during tests for cleanup purposes.
     * Streams are deleted in the {@link #tearDown()} method.
     */
    private final List<String> createdStreams = new ArrayList<>();

    /**
     * List of healthy streams (with valid KMS keys) for multi-stream testing.
     */
    private final List<TestStreamInformation> healthyStreams = new ArrayList<>();

    /**
     * List of KMS-errored streams (with deleted KMS keys) for multi-stream testing.
     */
    private final List<TestStreamInformation> kmsErroredStreams = new ArrayList<>();

    /**
     * Information about a test stream including its producer stream object and metadata.
     */
    private static class TestStreamInformation {
        final String streamName;
        final String kmsKeyId;
        KinesisVideoProducerStream producerStream;
        volatile boolean hasKmsError = false;
        volatile long errorStatus = 0;
        volatile Exception streamingException = null;
        volatile boolean streamingSuccessful = false;
        final List<KinesisVideoFragmentAck> acksReceived = Collections.synchronizedList(new ArrayList<>());

        TestStreamInformation(final String streamName, final String kmsKeyId,
                              final KinesisVideoProducerStream producerStream) {
            this.streamName = streamName;
            this.kmsKeyId = kmsKeyId;
            this.producerStream = producerStream;
        }

        void setProducerStream(final KinesisVideoProducerStream producerStream) {
            this.producerStream = producerStream;
        }
    }

    /**
     * Sets up the test environment before each test method.
     * This will fail if the JNI (java.library.path) can't be loaded.
     */
    @Before
    public void setUp() {
        final boolean jniLoaded = isJNILoaded();
        if (!jniLoaded) {
            fail("JNI library not found.");
        }

        createProducer();
    }

    /**
     * Cleans up resources after each test method.
     *
     * <p>This method:</p>
     * <ul>
     *   <li>Schedules all created KMS keys for deletion (minimum 7-day waiting period)</li>
     *   <li>Clears all tracking lists and stream information</li>
     *   <li>Frees all streams and producer resources</li>
     * </ul>
     *
     * <p><strong>Note:</strong> KMS keys cannot be immediately deleted and are scheduled for deletion
     * with a 7-day waiting period (minimum allowed by AWS). Streams are automatically cleaned up
     * by AWS after their retention period expires.</p>
     *
     * @see <a href="https://docs.aws.amazon.com/kms/latest/developerguide/deleting-keys.html">Delete an AWS KMS key</a>
     */
    @After
    public void tearDown() {
        boolean errored = false;
        for (final String keyId : this.createdKmsKeys) {
            try {
                deleteKmsKey(keyId);
            } catch (final Exception e) {
                log.warn("Failed to clean up KMS key {}: {}", keyId, e.getMessage());
                errored = true;
            }
        }

        this.createdKmsKeys.clear();
        this.healthyStreams.clear();
        this.kmsErroredStreams.clear();

        try {
            freeStreams();
        } catch (final Exception e) {
            log.warn("Failed to free streams: {}", e.getMessage());
            errored = true;
        }

        final AmazonKinesisVideo awsSdkKinesisVideoClient = AmazonKinesisVideoClientBuilder.standard().build();
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
                log.error("Failed to delete the stream: {}", streamName, e);
                errored = true;
            }
        }

        this.createdStreams.clear();

        assertFalse("There was an issue in cleaning up the resources! Check the logs above.", errored);
    }

    /**
     * Parameterized test that verifies multiple streams behavior with a mix of healthy and KMS-errored streams.
     *
     * <p>This test creates multiple streams using a single KinesisVideoProducer client and verifies that:</p>
     * <ul>
     *   <li>Healthy streams (with valid KMS keys) continue to work normally</li>
     *   <li>KMS-errored streams (with deletion in progress KMS keys) fail with error 0x4506</li>
     *   <li>Errors in KMS-errored streams do not affect healthy streams</li>
     *   <li>All streams operate independently within the same client</li>
     * </ul>
     *
     * <p><strong>Test Flow:</strong></p>
     * <ol>
     *   <li>Create KMS keys for all streams (healthy + errored)</li>
     *   <li>Create all streams with their respective KMS keys</li>
     *   <li>Delete KMS keys for the "errored" streams</li>
     *   <li>Stream data to all streams concurrently</li>
     *   <li>Verify healthy streams succeed and errored streams fail appropriately</li>
     * </ol>
     *
     * <p><strong>Concurrency:</strong> Uses thread pool to stream to multiple streams simultaneously,
     * simulating real-world usage patterns where multiple streams are active concurrently.</p>
     *
     * @throws Exception if test setup fails or unexpected errors occur
     */
    @Test
    public void testMultipleStreamsWithKmsErrors() throws Exception {
        final String testName = "KmsMultiStreamIntegTest - " + new Object() {
        }.getClass().getEnclosingMethod().getName();
        final long testStartTimeMs = System.currentTimeMillis();
        log.info("Starting parameterized test with {} healthy streams and {} KMS-errored streams",
                this.numberOfHealthyStreams, this.numberOfKmsErroredStreams);

        assertTrue("Invalid scenario - no healthy streams.", this.numberOfHealthyStreams >= 1);
        assertTrue("Invalid scenario - no errored streams.", this.numberOfKmsErroredStreams >= 1);

        // Create KMS keys for this test run (one for healthy streams, one for errored streams)
        // Shared between all the streams in the test
        final String healthyKeyDescription = testName + "- Healthy streams KMS key - " + testStartTimeMs + " - " + System.nanoTime();
        final String healthyStreamsKmsKey = createKmsKey(healthyKeyDescription);
        this.createdKmsKeys.add(healthyStreamsKmsKey);
        log.info("Created shared KMS key for {} healthy streams: {}", this.numberOfHealthyStreams, healthyStreamsKmsKey);

        final String erroredKeyDescription = "Errored streams KMS key - " + testStartTimeMs + " - " + System.nanoTime();
        final String erroredStreamsKmsKey = createKmsKey(erroredKeyDescription);
        this.createdKmsKeys.add(erroredStreamsKmsKey);
        log.info("Created shared KMS key for {} errored streams: {}", this.numberOfKmsErroredStreams, erroredStreamsKmsKey);

        // Create streams using the shared KMS keys
        for (int i = 0; i < this.numberOfHealthyStreams; i++) {
            createHealthyStream(i, testStartTimeMs, healthyStreamsKmsKey);
        }

        for (int i = 0; i < this.numberOfKmsErroredStreams; i++) {
            createKmsErroredStream(i, testStartTimeMs, erroredStreamsKmsKey);
        }

        // Delete the KMS key for errored streams
        // KMS uses a soft deletion, so it can't be used for encryption anymore (invalid state error)
        log.info("Deleting shared KMS key for errored streams scenario");
        deleteKmsKey(erroredStreamsKmsKey);
        this.createdKmsKeys.remove(erroredStreamsKmsKey);
        log.info("Marked KMS key for deletion: {}", erroredStreamsKmsKey);

        // Stream data to all streams concurrently
        log.info("Starting concurrent streaming to all {} streams",
                this.healthyStreams.size() + this.kmsErroredStreams.size());

        final ExecutorService healthyPool = Executors.newFixedThreadPool(this.numberOfHealthyStreams,
                new ThreadFactoryBuilder().setNameFormat("healthy-stream-%d").build());

        final ExecutorService unhealthyPool = Executors.newFixedThreadPool(this.numberOfKmsErroredStreams,
                new ThreadFactoryBuilder().setNameFormat("kms-errored-stream-%d").build());

        final List<Future<?>> streamingTasks = new ArrayList<>();

        try {
            // Kick off all the threads
            for (final TestStreamInformation streamInfo : this.healthyStreams) {
                final Future<?> task = healthyPool.submit(() -> streamToHealthyStream(streamInfo));
                streamingTasks.add(task);
            }

            for (final TestStreamInformation streamInfo : this.kmsErroredStreams) {
                final Future<?> task = unhealthyPool.submit(() -> streamToKmsErroredStream(streamInfo));
                streamingTasks.add(task);
            }

            // Wait for all streaming tasks to complete
            for (final Future<?> task : streamingTasks) {
                task.get(TIMEOUT_SECS, TimeUnit.SECONDS);
            }

        } finally {
            healthyPool.shutdown();
            if (!healthyPool.awaitTermination(FORCE_SHUTDOWN_TIMEOUT_SECS, TimeUnit.SECONDS)) {
                healthyPool.shutdownNow();
            }

            unhealthyPool.shutdown();
            if (!unhealthyPool.awaitTermination(FORCE_SHUTDOWN_TIMEOUT_SECS, TimeUnit.SECONDS)) {
                unhealthyPool.shutdownNow();
            }
        }

        verifyStreamingResults();

        log.info("Parameterized test completed successfully");
    }

    private void createKmsErroredStream(final int i, final long testStartTime, final String erroredStreamsKmsKey) {
        final String streamName = "KmsIntegrationTest_KmsErrored_" + i + "_" + testStartTime;

        log.info("Creating KMS-errored stream {}/{}: {} (using shared KMS key)", i + 1, this.numberOfKmsErroredStreams, streamName);

        // Create placeholder StreamTestInfo for callback creation
        final TestStreamInformation streamInfo = new TestStreamInformation(streamName, erroredStreamsKmsKey, null);
        this.kmsErroredStreams.add(streamInfo);

        // Create custom callbacks that reference this stream info
        final MultiStreamTestCallbacks customCallbacks = new MultiStreamTestCallbacks(this, streamInfo);

        final KinesisVideoProducerStream stream = createTestStreamWithKmsAndCallbacks(
                streamName,
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                TEST_LATENCY,
                TEST_BUFFER_DURATION,
                erroredStreamsKmsKey,
                customCallbacks
        );

        streamInfo.setProducerStream(stream);
        this.createdStreams.add(streamName);
    }

    private void createHealthyStream(final int i, final long testStartTime, final String healthyStreamsKmsKey) {
        final String streamName = "KmsIntegrationTest_Healthy_" + i + "_" + testStartTime;

        log.info("Creating healthy stream {}/{}: {} (using shared KMS key)", i + 1, this.numberOfHealthyStreams, streamName);

        final TestStreamInformation streamInfo = new TestStreamInformation(streamName, healthyStreamsKmsKey, null);
        this.healthyStreams.add(streamInfo);

        final MultiStreamTestCallbacks customCallbacks = new MultiStreamTestCallbacks(this, streamInfo);

        final KinesisVideoProducerStream stream = createTestStreamWithKmsAndCallbacks(
                streamName,
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                TEST_LATENCY,
                TEST_BUFFER_DURATION,
                healthyStreamsKmsKey,
                customCallbacks
        );

        streamInfo.setProducerStream(stream);
        this.createdStreams.add(streamName);
    }

    /**
     * Streams data to a healthy stream and verifies successful operation.
     *
     * @param streamInfo Information about the stream to stream to
     */
    private void streamToHealthyStream(final TestStreamInformation streamInfo) {
        log.info("Starting streaming to healthy stream: {}", streamInfo.streamName);

        try {
            final byte[] frameData = createTestFrameData();

            // Stream several frames to verify normal operation
            for (int frameIndex = 0; frameIndex < NUM_FRAMES; frameIndex++) {
                final long currentTimeMs = System.currentTimeMillis();
                final long timestampUs = currentTimeMs * 1000;

                final KinesisVideoFrame frame = new KinesisVideoFrame(
                        frameIndex,
                        frameIndex % KEYFRAME_INTERVAL == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE,
                        timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
                        timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
                        this.frameDuration_,
                        ByteBuffer.wrap(frameData)
                );

                streamInfo.producerStream.putFrame(frame);
                Thread.sleep(1000 / FRAMES_PER_SECOND);
            }

            // Wait for all the acks to come in
            Thread.sleep(5000);
            streamInfo.streamingSuccessful = true;

            log.info("Successfully completed streaming to healthy stream: {}", streamInfo.streamName);

        } catch (final Exception e) {
            streamInfo.streamingException = e;
            streamInfo.streamingSuccessful = false;
            log.error("Failed to stream to healthy stream: {}", streamInfo.streamName, e);
        }
    }

    /**
     * Streams data to a KMS-errored stream and captures the expected KMS error.
     *
     * @param streamInfo Information about the stream to stream to
     */
    private void streamToKmsErroredStream(final TestStreamInformation streamInfo) {
        log.info("Starting streaming to KMS-errored stream: {}", streamInfo.streamName);

        try {
            final byte[] frameData = createTestFrameData();

            // Attempt to stream frames and expect KMS error
            for (int frameIndex = 0; frameIndex < NUM_FRAMES; frameIndex++) {
                final long currentTimeMs = System.currentTimeMillis();
                final long timestampUs = currentTimeMs * 1000;

                final KinesisVideoFrame frame = new KinesisVideoFrame(
                        frameIndex,
                        frameIndex % KEYFRAME_INTERVAL == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE,
                        timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
                        timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
                        this.frameDuration_,
                        ByteBuffer.wrap(frameData)
                );

                streamInfo.producerStream.putFrame(frame);

                // Check for KMS error via the stream-specific error status
                // The custom callback updates the streamInfo.errorStatus field
                if (streamInfo.errorStatus == STATUS_KMS_KEY_INVALID_STATE) {
                    streamInfo.hasKmsError = true;
                    log.info("Detected KMS error for stream: {} (error code: 0x{})",
                            streamInfo.streamName, Long.toHexString(streamInfo.errorStatus));
                    break;
                } else if (streamInfo.errorStatus != 0) {
                    fail("Detected an unexpected error for stream: " + streamInfo.streamName + ", error code: 0x" + Long.toHexString(streamInfo.errorStatus));
                }

                Thread.sleep(1000 / FRAMES_PER_SECOND);
            }

            // Wait for any delayed error callbacks
            Thread.sleep(5000);

            // Final check for KMS error
            if (streamInfo.errorStatus == STATUS_KMS_KEY_INVALID_STATE) {
                streamInfo.hasKmsError = true;
            }

            log.info("Completed streaming attempt to KMS-errored stream: {} (KMS error detected: {})",
                    streamInfo.streamName, streamInfo.hasKmsError);

        } catch (final Exception e) {
            streamInfo.streamingException = e;
            log.info("Caught exception for KMS-errored stream {}: {}",
                    streamInfo.streamName, e.getMessage());
        }
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
     * Verifies the results of streaming to multiple streams.
     *
     * <p>This method checks that:</p>
     * <ul>
     *   <li>All healthy streams completed successfully without KMS errors</li>
     *   <li>All KMS-errored streams failed with the expected KMS error</li>
     *   <li>Healthy streams were not affected by errors in KMS-errored streams</li>
     * </ul>
     */
    private void verifyStreamingResults() {
        log.info("Verifying streaming results for {} healthy and {} KMS-errored streams",
                this.healthyStreams.size(), this.kmsErroredStreams.size());

        // Verify healthy streams succeeded
        for (final TestStreamInformation streamInfo : this.healthyStreams) {
            assertTrue("Healthy stream " + streamInfo.streamName + " should have completed successfully" +
                            (streamInfo.streamingException != null ?
                                    ". Exception: " + streamInfo.streamingException.getMessage() : ""),
                    streamInfo.streamingSuccessful);

            assertFalse("Healthy stream " + streamInfo.streamName + " should not have KMS errors. " +
                            "Error status: 0x" + Long.toHexString(streamInfo.errorStatus),
                    streamInfo.hasKmsError);

            final List<KinesisVideoFragmentAck> acksReceived = streamInfo.acksReceived;
            final long errorAcksCount = acksReceived.stream().filter(ack -> ack.getResult() != HTTP_OK).count();
            assertEquals("Received an error ack for a healthy stream: " + acksReceived, 0, errorAcksCount);

            log.info("Healthy stream {} operated successfully", streamInfo.streamName);
        }

        // Verify KMS-errored streams failed appropriately
        for (final TestStreamInformation streamInfo : this.kmsErroredStreams) {
            assertTrue("KMS-errored stream " + streamInfo.streamName +
                            " should have received KMS_KEY_NOT_FOUND error (0x4506) or KMS_KEY_INVALID_STATE error (0x5200006a) or related exception. " +
                            "Error status: 0x" + Long.toHexString(streamInfo.errorStatus) +
                            (streamInfo.streamingException != null ?
                                    ", Exception: " + streamInfo.streamingException.getMessage() : ""),
                    streamInfo.hasKmsError || streamInfo.errorStatus == 0x4506L || streamInfo.errorStatus == 0x5200006aL ||
                            (streamInfo.streamingException != null &&
                                    (streamInfo.streamingException.getMessage().contains("KMS") ||
                                            streamInfo.streamingException.getMessage().contains("4506") ||
                                            streamInfo.streamingException.getMessage().contains("5200006a"))));

            final List<KinesisVideoFragmentAck> acksReceived = streamInfo.acksReceived;

            // The fragmentAckReceived callback implementation (test) stores all the acks received into this receivedFragmentAcks_ array
            final long numPersistedAcks = acksReceived.stream()
                    .filter(ack -> ack.getAckType().getIntType() == FragmentAckType.FRAGMENT_ACK_TYPE_PERSISTED)
                    .count();
            assertEquals(streamInfo.streamName + " Received a PERSISTED ack. There shouldn't be any: " + acksReceived, 0, numPersistedAcks);

            final long num4505ErroredAcks = acksReceived.stream()
                    .filter(ack -> ack.getResult() == RESULT_KMS_KEY_INVALID_STATE)
                    .count();
            assertTrue(streamInfo.streamName + " Did not receive any KMS errored ACKs: " + acksReceived, num4505ErroredAcks > 0);

            final long receivedAcksCount = acksReceived.stream()
                    .filter(ack -> ack.getAckType().getIntType() == FragmentAckType.FRAGMENT_ACK_TYPE_RECEIVED)
                    .count();

            assertTrue(streamInfo.streamName + " Didn't receive any RECEIVED ACKs: " + acksReceived, receivedAcksCount > 0);

            log.info("KMS-errored stream {} failed as expected with KMS error (shared key: {})",
                    streamInfo.streamName, streamInfo.kmsKeyId);
        }

        // Summary
        log.info("Stream isolation verification completed successfully:");
        log.info("  - {} healthy streams operated without issues (shared {} KMS key{})",
                this.healthyStreams.size(),
                this.healthyStreams.isEmpty() ? 0 : 1,
                this.healthyStreams.size() <= 1 ? "" : "s");
        log.info("  - {} KMS-errored streams failed with expected KMS errors (shared {} KMS key{})",
                this.kmsErroredStreams.size(),
                this.kmsErroredStreams.isEmpty() ? 0 : 1,
                this.kmsErroredStreams.size() <= 1 ? "" : "s");
        log.info("  - No cross-stream error propagation detected");
        log.info("  - Verified that multiple streams can share the same KMS key");
    }

    /**
     * Custom stream callback that tracks errors <i>per stream</i> instead of globally.
     * This allows us to verify that errors in one stream don't affect others.
     */
    private class MultiStreamTestCallbacks extends TestStreamCallBacks {
        private final TestStreamInformation streamInfo;
        private final Logger logger = LogManager.getLogger(MultiStreamTestCallbacks.class);

        protected MultiStreamTestCallbacks(final ProducerTestBase producerTestBase, final TestStreamInformation streamInfo) {
            super(producerTestBase);
            this.streamInfo = streamInfo;
        }

        @Override
        public void streamErrorReport(final long uploadHandle, final long frameTimecode, final long statusCode)
                throws ProducerException {
            // Call parent implementation for global tracking
            super.streamErrorReport(uploadHandle, frameTimecode, statusCode);

            // Track error for this specific stream
            this.streamInfo.errorStatus = statusCode;
            if (statusCode == STATUS_KMS_KEY_INVALID_STATE) {
                this.streamInfo.hasKmsError = true;
            }

            this.logger.error("Stream {} reported error. Frame timecode: {}, Status code: 0x{}",
                    this.streamInfo.streamName, frameTimecode, Long.toHexString(statusCode));
        }

        @Override
        public void fragmentAckReceived(final long uploadHandle,
                                        @Nonnull final KinesisVideoFragmentAck fragmentAck) throws ProducerException {
            super.fragmentAckReceived(uploadHandle, fragmentAck);

            this.streamInfo.acksReceived.add(fragmentAck);
        }
    }
}
