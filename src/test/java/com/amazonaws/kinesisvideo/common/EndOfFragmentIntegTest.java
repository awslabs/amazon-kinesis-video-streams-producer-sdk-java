package com.amazonaws.kinesisvideo.common;

import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.producer.FragmentAckType;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFragmentAck;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Time;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClient;
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

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.HTTP_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * This test checks for race conditions during the Cleanup/stopping path.
 * When using {@link com.amazonaws.kinesisvideo.producer.ClientInfo.AutomaticStreamingFlags#AUTOMATIC_STREAMING_INTERMITTENT_PRODUCER},
 * the native producer will add send an End of Fragment (eofr) after 20s of no putFrame calls to a stream.
 * <p>
 * This repeated test will run:
 * <ul>
 *     <li>{@value #NUMBER_OF_STREAMS_PER_ITERATION} streams for each iteration.</li>
 *     <li>All the streams will stream {@value #NUMBER_OF_FRAMES_TO_STREAM} frames at {@value #FPS} fps with
 *     a keyframe interval of {@value #KEYFRAME_INTERVAL}, then stop calling putFrame.</li>
 *     <li>Each stream will call {@code stopStream()} and {@code free()} randomly between {@link #INTERMITTENT_PRODUCER_WAIT_TIME_BEFORE_SENDING_EOFR_WAIT_LOWER_BOUND}
 *     and {@link #INTERMITTENT_PRODUCER_WAIT_TIME_BEFORE_SENDING_EOFR_WAIT_UPPER_BOUND}</li>
 *     <li>The above scenario will be repeated for {@value #NUMBER_OF_ITERATIONS} iterations.</li>
 * </ul>
 * </p>
 */
@RunWith(Parameterized.class)
public class EndOfFragmentIntegTest extends ProducerTestBase {

    private static final Logger log = LogManager.getLogger(com.amazonaws.kinesisvideo.common.EndOfFragmentIntegTest.class);

    // Intermittent producer mode --> there is a thread owned by the native code which scans
    // across all the streams owned by the client for any streams without any putFrame calls for the last 20s
    // We will stop the stream around that time to confirm that the stream is successfully stopped.
    private static final Duration INTERMITTENT_PRODUCER_WAIT_TIME_BEFORE_SENDING_EOFR_WAIT_LOWER_BOUND = Duration.ofSeconds(19);
    private static final Duration INTERMITTENT_PRODUCER_WAIT_TIME_BEFORE_SENDING_EOFR_WAIT_UPPER_BOUND = Duration.ofSeconds(24);

    /**
     * Configurable parameters when running locally.
     * Keep these numbers low so the CI can pass in a reasonable amount of time.
     *
     * <p>
     * Note: You may run into {@code 0x52000001 - STATUS_MAX_STREAM_COUNT} if this is higher than {@value #NUMBER_OF_STREAMS}.
     * Increase that DeviceInfo parameter for the Kinesis Video Client constructor if needed.
     * </p>
     *
     * @see ProducerTestBase#NUMBER_OF_STREAMS
     */
    private static final int NUMBER_OF_ITERATIONS = 3;
    private static final int NUMBER_OF_STREAMS_PER_ITERATION = 10;

    /**
     * Parameters for each stream's media configuration (frames)
     */
    private static final int NUMBER_OF_FRAMES_TO_STREAM = 10;
    private static final int FPS = 5;
    private static final int KEYFRAME_INTERVAL = 5;

    /**
     * List of streams created during tests that need to be cleaned up.
     */
    private final List<KinesisVideoProducerStream> createdStreams = new ArrayList<>();

    /**
     * Sets up the test environment before each test method.
     * This will fail if the JNI (java.library.path) can't be loaded.
     */
    @Before
    @SuppressWarnings({"ConstantConditions"})
    public void setUp() {
        final boolean jniLoaded = isJNILoaded();
        if (!jniLoaded) {
            fail("JNI library not found.");
        }

        assumeTrue("The lower bound should be lower than the upper bound!",
                INTERMITTENT_PRODUCER_WAIT_TIME_BEFORE_SENDING_EOFR_WAIT_LOWER_BOUND
                        .compareTo(INTERMITTENT_PRODUCER_WAIT_TIME_BEFORE_SENDING_EOFR_WAIT_UPPER_BOUND) < 1);

        assumeTrue("You need to increase the number of streams the client is configured for before starting the test!",
                NUMBER_OF_STREAMS_PER_ITERATION < NUMBER_OF_STREAMS);

        createProducer();
    }

    @After
    public void tearDown() {
        boolean failure = false;

        final List<String> streamNames = this.createdStreams.stream()
                .map(KinesisVideoProducerStream::getStreamName)
                .collect(Collectors.toList());

        try {
            freeStreams();
        } catch (final Exception e) {
            failure = true;
            log.error("Failed to free streams {}", this.createdStreams, e);
        }

        final AmazonKinesisVideo awsSdkKinesisVideoClient = AmazonKinesisVideoClientBuilder.standard().build();
        for (final String streamName : streamNames) {
            try {
                final DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest().withStreamName(streamName);
                final DescribeStreamResult describeStreamResult = awsSdkKinesisVideoClient.describeStream(describeStreamRequest);

                final DeleteStreamRequest deleteStreamRequest = new DeleteStreamRequest()
                        .withStreamARN(describeStreamResult.getStreamInfo().getStreamARN())
                        .withCurrentVersion(describeStreamResult.getStreamInfo().getVersion());
                awsSdkKinesisVideoClient.deleteStream(deleteStreamRequest);
            } catch (final Exception e) {
                failure = true;
                log.error("Failed to delete the stream: {}", streamName, e);
            }
        }

        this.createdStreams.clear();

        assertFalse("An exception happened during cleanup!", failure);
    }

    // Using this as a way to repeat the test multiple times
    @Parameterized.Parameters(name = "Iteration {index}")
    public static Object[][] dataFor_test_When_StoppingNearIntermittentProducer_Then_StreamingSuccessfully() {
        return new Object[NUMBER_OF_ITERATIONS][0];
    }

    public EndOfFragmentIntegTest() {
        // No-op
    }

    /**
     * Tests that the producer correctly handles intermittent producer scenarios where streams are stopped
     * after a random wait period near the intermittent producer timeout threshold.
     *
     * <p><strong>Test Flow:</strong></p>
     * <ol>
     *   <li>Create multiple Kinesis Video Streams for testing</li>
     *   <li>Start streaming video frames to all streams concurrently</li>
     *   <li>Each stream will wait for a random period between 19-24 seconds (near the 20s intermittent producer threshold)</li>
     *   <li>Stop each stream synchronously and free resources</li>
     *   <li>Verify that all operations complete successfully without errors</li>
     * </ol>
     *
     * <p><strong>Expected Behavior:</strong></p>
     * <ul>
     *   <li>All streams should be created successfully</li>
     *   <li>Video frames should be streamed without errors</li>
     *   <li>Streams should stop gracefully even when stopped near the intermittent producer timeout</li>
     *   <li>No error callbacks should be triggered</li>
     *   <li>All received ACKs should be successful (HTTP 200)</li>
     *   <li>PERSISTED ACKs should be received</li>
     * </ul>
     */
    @Test
    @SuppressWarnings({"UnnecessaryLocalVariable"})
    public void test_When_StoppingNearIntermittentProducer_Then_StreamingSuccessfully() throws Exception {
        final String testName = new Object(){}.getClass().getEnclosingMethod().getName();
        final String testStreamName = "EndOfFragmentIntegTest_" + testName + "_" + System.currentTimeMillis();

        // 1 - Create streams
        for (int i = 0; i < NUMBER_OF_STREAMS_PER_ITERATION; i++) {
            final String testStreamNameI = testStreamName + "_" + i;
            log.info("Creating KVS stream: {}", testStreamNameI);
            final KinesisVideoProducerStream stream = createTestStream(
                    testStreamNameI,
                    StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                    TEST_LATENCY,
                    TEST_BUFFER_DURATION
            );
            assertNotNull("Stream should be created", stream);
            this.createdStreams.add(stream);
        }

        final ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_STREAMS_PER_ITERATION,
                new ThreadFactoryBuilder().setNameFormat(testStreamName + "-%d").build());

        // 2 - Start streaming normally
        final byte[] frameData = createTestFrameData();
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch completionLatch = new CountDownLatch(NUMBER_OF_STREAMS_PER_ITERATION);

        for (final KinesisVideoProducerStream stream : this.createdStreams) {
            executorService.execute(() -> {
                try {
                    startLatch.await();
                    long currentFrameTs = System.currentTimeMillis();
                    for (int i = 0; i < NUMBER_OF_FRAMES_TO_STREAM; i++) {
                        final int frameIndex = i;
                        final long timestampUs = currentFrameTs * 1000;

                        final KinesisVideoFrame frame = new KinesisVideoFrame(
                                frameIndex,
                                frameIndex % KEYFRAME_INTERVAL == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE,
                                timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
                                timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
                                this.frameDuration_,
                                ByteBuffer.wrap(frameData)
                        );
                        stream.putFrame(frame);

                        Thread.sleep(1000 / FPS);
                        currentFrameTs += (1000 / FPS);
                    }

                    final long randomWaitTimeForIntermittentProducerEofrMs = calculateRandomWaitTimeForIntermittentProducer();
                    log.info("Waiting {} milliseconds", randomWaitTimeForIntermittentProducerEofrMs);
                    Thread.sleep(randomWaitTimeForIntermittentProducerEofrMs);
                    log.info("Calling stopStream");
                    stream.stopStreamSync();
                    log.info("Calling freeStream");
                    freeStream(stream);
                } catch (final Throwable t) {
                    fail(t.getMessage());
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Kick off all the streams at the same time
        startLatch.countDown();

        // Wait for everything to complete
        assertTrue("Ran into an error while stopping (timed out)",
                completionLatch.await(NUMBER_OF_FRAMES_TO_STREAM / FPS + 30, TimeUnit.SECONDS));

        // Wait additional time for any delayed error callbacks
        log.debug("Waiting for all acks to come in...");
        Thread.sleep(5000);

        // 3 - Verify no errors
        assertEquals("Should have received no errors. " +
                "Error status: 0x" + Long.toHexString(this.errorStatus_), 0, this.errorStatus_);


        log.debug("All the received acks: {}", this.receivedFragmentAcks_);
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
     * @return random milliseconds in the range: {@code [lowerBound, upperBound)}
     * @see #INTERMITTENT_PRODUCER_WAIT_TIME_BEFORE_SENDING_EOFR_WAIT_LOWER_BOUND
     * @see #INTERMITTENT_PRODUCER_WAIT_TIME_BEFORE_SENDING_EOFR_WAIT_UPPER_BOUND
     */
    private long calculateRandomWaitTimeForIntermittentProducer() {
        final long lowerBound = INTERMITTENT_PRODUCER_WAIT_TIME_BEFORE_SENDING_EOFR_WAIT_LOWER_BOUND.toMillis();
        final long upperBound = INTERMITTENT_PRODUCER_WAIT_TIME_BEFORE_SENDING_EOFR_WAIT_UPPER_BOUND.toMillis();

        return (long) (lowerBound + Math.random() * (upperBound - lowerBound));
    }
}
