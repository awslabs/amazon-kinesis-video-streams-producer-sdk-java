package com.amazonaws.kinesisvideo.common;

import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.producer.FragmentAckType;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFragmentAck;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Time;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClient;
import com.amazonaws.services.kinesisvideo.model.DeleteStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.HTTP_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
 * <p><strong>Test Scenarios:</strong></p>
 * <ul>
 *   <li>{@link #test_When_KmsKeyIsGood_Then_StreamingSuccessfully()} ()}: Original single-stream valid KMS test</li>
 *   <li>{@link #test_When_KmsKeyScheduledToBeDeleted_Then_StreamErroredCallbackInvoked()}: Original single-stream KMS error test</li>
 * </ul>
 *
 * <p><strong>Error Code Reference:</strong></p>
 * <ul>
 *   <li>{@link ProducerTestBase#STATUS_KMS_KEY_INVALID_STATE} - KMS key is in invalid state (e.g., disabled)</li>
 * </ul>
 */
public class KmsSingleStreamIntegTest extends ProducerTestBase {

    private static final Logger log = LogManager.getLogger(KmsSingleStreamIntegTest.class);

    /**
     * List of KMS key IDs created during tests that need to be cleaned up.
     * Keys are scheduled for deletion in the tearDown method.
     */
    private final List<String> createdKmsKeys = new ArrayList<>();

    /**
     * List of stream names created during tests that need to be cleaned up.
     */
    private final List<String> createdStreams = new ArrayList<>();

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
        boolean failure = false;

        for (final String keyId : this.createdKmsKeys) {
            try {
                deleteKmsKey(keyId);
            } catch (final Exception e) {
                failure = true;
                log.error("Failed to clean up KMS key {}: {}", keyId, e.getMessage());
            }
        }

        this.createdKmsKeys.clear();

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
     * Create a KMS key with a description. It does not need to be unique (even though it can't be deleted for 7d).
     *
     * @param testName the description of the KMS key.
     * @return ID of the KMS key (UUID)
     */
    private String setupKmsKey(final String testName) {
        log.info("Creating KMS key for test...");
        final String kmsKeyId = createKmsKey(testName);
        assertNotNull("KMS key should be created", kmsKeyId);
        this.createdKmsKeys.add(kmsKeyId);
        return kmsKeyId;
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
     * Tests that the producer correctly handles and reports {@code KMS_KEY_INVALID_STATE} error when streaming
     * to a stream configured with a marked for deletion KMS key.
     *
     * <p><strong>Test Flow:</strong></p>
     * <ol>
     *   <li>Create a new KMS key for testing</li>
     *   <li>Create a Kinesis Video Stream configured with the KMS key</li>
     *   <li>Delete (schedule for deletion) the KMS key</li>
     *   <li>Attempt to stream video data to the stream</li>
     *   <li>Verify that KMS error is reported</li>
     * </ol>
     *
     * <p><strong>Expected Behavior:</strong></p>
     * <ul>
     *   <li>The stream should be created successfully with the valid KMS key</li>
     *   <li>After key deletion, streaming attempts should fail</li>
     *   <li>The error should be captured via the streamErrorReport callback</li>
     *   <li>No errored ACKs received</li>
     *   <li>Should have received some PERSISTED ACKS</li>
     * </ul>
     */
    @Test
    public void test_When_KmsKeyIsGood_Then_StreamingSuccessfully()
            throws ProducerException, InterruptedException {
        final String testName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        final String testStreamName = "KmsSingleStreamIntegTest_" + testName + "_" + System.currentTimeMillis();
        final String kmsKeyId = setupKmsKey("KmsSingleStreamIntegTest_" + testName);

        // 1 - Create a Kinesis Video Stream with the KMS key
        log.info("Creating KVS stream with KMS key: {}", kmsKeyId);
        final KinesisVideoProducerStream stream = createTestStreamWithKms(
                testStreamName,
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                TEST_LATENCY,
                TEST_BUFFER_DURATION,
                kmsKeyId
        );
        assertNotNull("Stream should be created", stream);
        this.createdStreams.add(testStreamName);

        // 2 - Start streaming normally
        final int keyframeInterval = 5;
        final int numFrames = 10;
        final int fps = 5;
        long currentFrameTs = System.currentTimeMillis();
        final byte[] frameData = createTestFrameData();

        for (int frameIndex = 0; frameIndex < numFrames; frameIndex++) {
            final long timestampUs = currentFrameTs * 1000;

            final KinesisVideoFrame frame = new KinesisVideoFrame(
                    frameIndex,
                    frameIndex % keyframeInterval == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE,
                    timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
                    timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
                    this.frameDuration_,
                    ByteBuffer.wrap(frameData)
            );

            stream.putFrame(frame);

            // Check if we've received the expected KMS error via streamErrorReport callback
            if (this.errorStatus_ != 0) {
                fail("Received an unexpected stream error: " + Long.toHexString(this.errorStatus_));
            }

            Thread.sleep(1000 / fps);
            currentFrameTs += (1000 / fps);
        }

        // Wait additional time for any delayed error callbacks
        log.debug("Waiting for all acks to come in...");
        Thread.sleep(5000);

        // 3 - Verify no errors
        assertEquals("Should have received no errors. " +
                "Error status: 0x" + Long.toHexString(this.errorStatus_), 0, this.errorStatus_);

        // The fragmentAckReceived callback implementation (test) stores all the acks received into this receivedFragmentAcks_ array
        for (final KinesisVideoFragmentAck ack : this.receivedFragmentAcks_) {
            assertEquals("Received a non-successful ACK: " + ack, HTTP_OK, ack.getResult());
        }

        final long persistedAcksCount = this.receivedFragmentAcks_.stream()
                .filter(ack -> ack.getAckType().getIntType() == FragmentAckType.FRAGMENT_ACK_TYPE_PERSISTED)
                .count();

        assertTrue("Didn't receive any PERSISTED ACKs. Received: " + this.receivedFragmentAcks_, persistedAcksCount > 0);
    }

    /**
     * Tests that the producer correctly handles and reports {@code KMS_KEY_INVALID_STATE} error when streaming
     * to a stream configured with a marked for deletion KMS key.
     *
     * <p><strong>Test Flow:</strong></p>
     * <ol>
     *   <li>Create a new KMS key for testing</li>
     *   <li>Create a Kinesis Video Stream configured with the KMS key</li>
     *   <li>Delete (schedule for deletion) the KMS key</li>
     *   <li>Attempt to stream video data to the stream</li>
     *   <li>Verify that KMS error is reported</li>
     *   <li>Verify no PERSISTED ACKs were received</li>
     * </ol>
     *
     * <p><strong>Expected Behavior:</strong></p>
     * <ul>
     *   <li>The stream should be created successfully with the valid KMS key</li>
     *   <li>After key deletion, streaming attempts should fail</li>
     *   <li>The error should be captured via the streamErrorReport callback</li>
     * </ul>
     */
    @Test
    public void test_When_KmsKeyScheduledToBeDeleted_Then_StreamErroredCallbackInvoked()
            throws ProducerException, InterruptedException {
        final String methodName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        final String testStreamName = "KmsSingleStreamIntegTest_" + methodName + "_" + System.currentTimeMillis();
        final String kmsKeyId = setupKmsKey("KmsSingleStreamIntegTest_" + methodName);

        // 1 - Create a Kinesis Video Stream with the KMS key
        log.info("Creating KVS stream with KMS key: {}", kmsKeyId);
        final KinesisVideoProducerStream stream = createTestStreamWithKms(
                testStreamName,
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                TEST_LATENCY,
                TEST_BUFFER_DURATION,
                kmsKeyId
        );
        assertNotNull("Stream should be created", stream);
        this.createdStreams.add(testStreamName);

        // 2 - Delete the KMS key (schedule for deletion)
        //     This makes the key inaccessible for encryption operations
        log.info("Deleting KMS key: {}", kmsKeyId);
        deleteKmsKey(kmsKeyId);
        this.createdKmsKeys.remove(kmsKeyId);

        // 3 - Start streaming to trigger the KMS error
        final int keyframeInterval = 5;
        final int numFrames = 10;
        final int fps = 5;
        long currentFrameTs = System.currentTimeMillis();
        final byte[] frameData = createTestFrameData();

        for (int frameIndex = 0; frameIndex < numFrames; frameIndex++) {
            final long timestampUs = currentFrameTs * 1000;

            final KinesisVideoFrame frame = new KinesisVideoFrame(
                    frameIndex,
                    frameIndex % keyframeInterval == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE,
                    timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
                    timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
                    this.frameDuration_,
                    ByteBuffer.wrap(frameData)
            );

            stream.putFrame(frame);

            // Check if we've received the expected KMS error via streamErrorReport callback
            if (this.errorStatus_ != 0) {
                log.info("Received error status: 0x{}", Long.toHexString(this.errorStatus_));
                assertEquals("Received an unexpected stream error: " + Long.toHexString(this.errorStatus_),
                        STATUS_KMS_KEY_INVALID_STATE, this.errorStatus_);
            }

            Thread.sleep(1000 / fps);
            currentFrameTs += (1000 / fps);
        }

        // Wait additional time for any delayed error callbacks
        log.debug("Waiting for all acks to come in...");
        Thread.sleep(5000);

        // 4 - Verify the KMS error
        assertEquals("Should have received KMS_KEY_INVALID_STATE error. " +
                "Error status: 0x" + Long.toHexString(this.errorStatus_), STATUS_KMS_KEY_INVALID_STATE, this.errorStatus_);

        // The fragmentAckReceived callback implementation (test) stores all the acks received into this receivedFragmentAcks_ array
        final long numPersistedAcks = this.receivedFragmentAcks_.stream()
                .filter(ack -> ack.getAckType().getIntType() == FragmentAckType.FRAGMENT_ACK_TYPE_PERSISTED)
                .count();
        assertEquals("Received a PERSISTED ack. There shouldn't be any: " + this.receivedFragmentAcks_, 0, numPersistedAcks);

        final long num4505ErroredAcks = this.receivedFragmentAcks_.stream()
                .filter(ack -> ack.getResult() == RESULT_KMS_KEY_INVALID_STATE)
                .count();
        assertTrue("Did not receive any KMS errored ACKs: " + this.receivedFragmentAcks_, num4505ErroredAcks > 0);

        final long receivedAcksCount = this.receivedFragmentAcks_.stream()
                .filter(ack -> ack.getAckType().getIntType() == FragmentAckType.FRAGMENT_ACK_TYPE_RECEIVED)
                .count();

        assertTrue("Didn't receive any RECEIVED ACKs: " + this.receivedFragmentAcks_, receivedAcksCount > 0);
    }
}
