package com.amazonaws.kinesisvideo.common;

import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.producer.ClientInfo;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.KvsRetryStrategy;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StorageInfo;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Tag;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.nio.ByteBuffer;

import static com.amazonaws.kinesisvideo.producer.ProducerException.STATUS_SUCCESS;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * These tests check the struct migration/versioning pattern used in PIC.
 * <ul>
 *     <li>{@link DeviceInfo} is the main parameter object for configuring the KVS Producer client.</li>
 *     <li>The v0 of the struct has a few members</li>
 *     <li>The v1 of the struct has a couple new members, in particular, the {@link ClientInfo} parameter,
 *     which also has multiple struct versions on its own</li>
 * </ul>
 *
 * @see <a href="https://github.com/awslabs/amazon-kinesis-video-streams-pic">Amazon Kinesis Video Streams PIC</a>
 */
public class DeviceInfoClientInfoVersionTest extends ProducerTestBase {

    private static final int STORAGE_INFO_VERSION_ZERO = 0;
    private static final int ONE_SECOND_HUNDREDS_OF_NANOS = 1000 * 10000;
    private static final int TEN_SECONDS_HUNDREDS_OF_NANOS = 10 * ONE_SECOND_HUNDREDS_OF_NANOS;

    @Rule
    public Timeout globalTimeout = Timeout.seconds(15);

    private StorageInfo storageInfo;

    @Before
    public void setUp() {
        final boolean jniLoaded = isJNILoaded();
        if (!jniLoaded) {
            fail("JNI library not found.");
        }

        final long storageSizeBytes = 10 * 1024 * 1024; // 10 MiB
        final int spillRatioPercent = 90;
        final String rootDirectory = "/tmp";

        this.storageInfo = new StorageInfo(STORAGE_INFO_VERSION_ZERO,
                StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, storageSizeBytes,
                spillRatioPercent, rootDirectory);
    }

    // Note: Suppressing the warnings to make the code more readable since the code uses long parameter lists
    @Test
    @SuppressWarnings("ConstantConditions")
    public void test_deviceInfo_v0_constructor_works() throws ProducerException {
        final String deviceName = "java-test-application";
        final int streamCount = 10;
        final Tag[] tags = null;

        final DeviceInfo deviceInfo = DeviceInfo.createDeviceInfoV0(deviceName, this.storageInfo, streamCount, tags);

        createProducer(deviceInfo);

        final String methodName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        testStreaming(methodName);

        free();

    }

    @Test
    @SuppressWarnings({"UnnecessaryLocalVariable", "ConstantConditions", "ExtractMethodRecommender"})
    public void test_deviceInfo_v1_constructor_with_clientInfo_v0() throws ProducerException {

        final long createClientTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;
        final long createStreamTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;
        final long stopStreamTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;
        final long offlineBufferAvailabilityTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;
        final int logLevel = ClientInfo.LOG_LEVEL_DEBUG;
        final boolean doLogMetrics = true;

        final ClientInfo clientInfo = ClientInfo.createClientInfoV0(createClientTimeout,
                createStreamTimeout, stopStreamTimeout, offlineBufferAvailabilityTimeout,
                logLevel, doLogMetrics);

        final String deviceName = "java-test-application";
        final int streamCount = 10;
        final Tag[] tags = null;
        final String methodName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        final String clientId = String.format("ProducerJava-%s-%s", this.getClass().getSimpleName(), methodName);

        final DeviceInfo deviceInfo = DeviceInfo.createDeviceInfoV1(deviceName, this.storageInfo, streamCount, tags,
                clientId, clientInfo);

        createProducer(deviceInfo);

        testStreaming(methodName);

        free();

    }

    @Test
    @SuppressWarnings({"UnnecessaryLocalVariable", "ConstantConditions", "ExtractMethodRecommender"})
    public void test_deviceInfo_v1_constructor_with_clientInfo_v1() throws ProducerException {

        // ClientInfo V0 fields
        final long createClientTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;
        final long createStreamTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;
        final long stopStreamTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;
        final long offlineBufferAvailabilityTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;
        final int logLevel = ClientInfo.DEFAULT_LOG_LEVEL;
        final boolean doLogMetrics = true;

        // ClientInfo V1 fields
        final long metricsLoggingPeriod = ONE_SECOND_HUNDREDS_OF_NANOS;

        final ClientInfo clientInfo = ClientInfo.createClientInfoV1(createClientTimeout,
                createStreamTimeout, stopStreamTimeout, offlineBufferAvailabilityTimeout,
                logLevel, doLogMetrics, metricsLoggingPeriod);

        final String deviceName = "java-test-application";
        final int streamCount = 10;
        final Tag[] tags = null;
        final String methodName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        final String clientId = String.format("ProducerJava-%s-%s", this.getClass().getSimpleName(), methodName);

        final DeviceInfo deviceInfo = DeviceInfo.createDeviceInfoV1(deviceName, this.storageInfo, streamCount, tags,
                clientId, clientInfo);

        createProducer(deviceInfo);

        testStreaming(methodName);

        free();
    }

    @Test
    @SuppressWarnings({"UnnecessaryLocalVariable", "ConstantConditions", "ExtractMethodRecommender"})
    public void test_deviceInfo_v1_constructor_with_clientInfo_v2() throws ProducerException {

        // ClientInfo V0 fields
        final long createClientTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;
        final long createStreamTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;
        final long stopStreamTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;
        final long offlineBufferAvailabilityTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;
        final int logLevel = ClientInfo.DEFAULT_LOG_LEVEL;

        // ClientInfo V1 fields
        final boolean doLogMetrics = true;

        // ClientInfo V2 fields
        final long metricsLoggingPeriod = ONE_SECOND_HUNDREDS_OF_NANOS;
        final ClientInfo.AutomaticStreamingFlags automaticStreamingFlags = null;
        final long reservedCallbackPeriod = ONE_SECOND_HUNDREDS_OF_NANOS;
        final KvsRetryStrategy kvsRetryStrategy = null;

        final ClientInfo clientInfo = ClientInfo.createClientInfoV2(createClientTimeout,
                createStreamTimeout, stopStreamTimeout, offlineBufferAvailabilityTimeout,
                logLevel, doLogMetrics, metricsLoggingPeriod, automaticStreamingFlags,
                reservedCallbackPeriod, kvsRetryStrategy);

        final String deviceName = "java-test-application";
        final int streamCount = 10;
        final Tag[] tags = null;
        final String methodName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        final String clientId = String.format("ProducerJava-%s-%s", this.getClass().getSimpleName(), methodName);

        final DeviceInfo deviceInfo = DeviceInfo.createDeviceInfoV1(deviceName, this.storageInfo, streamCount, tags,
                clientId, clientInfo);

        createProducer(deviceInfo);

        testStreaming(methodName);

        free();
    }

    @Test
    @SuppressWarnings({"UnnecessaryLocalVariable", "ConstantConditions", "ExtractMethodRecommender"})
    public void test_deviceInfo_v1_constructor_with_clientInfo_v3() throws ProducerException {

        // ClientInfo V0 fields
        final long createClientTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;
        final long createStreamTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;
        final long stopStreamTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;
        final long offlineBufferAvailabilityTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;
        final int logLevel = ClientInfo.DEFAULT_LOG_LEVEL;

        // ClientInfo V1 fields
        final boolean doLogMetrics = true;

        // ClientInfo V2 fields
        final long metricsLoggingPeriod = ONE_SECOND_HUNDREDS_OF_NANOS;
        final ClientInfo.AutomaticStreamingFlags automaticStreamingFlags = null;
        final long reservedCallbackPeriod = ONE_SECOND_HUNDREDS_OF_NANOS;
        final KvsRetryStrategy kvsRetryStrategy = null;

        // ClientInfo V3 fields
        final long serviceCallCompletionTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;
        final long serviceCallConnectionTimeout = ONE_SECOND_HUNDREDS_OF_NANOS;

        final ClientInfo clientInfo = ClientInfo.createClientInfoV3(createClientTimeout,
                createStreamTimeout, stopStreamTimeout, offlineBufferAvailabilityTimeout,
                logLevel, doLogMetrics, metricsLoggingPeriod, automaticStreamingFlags,
                reservedCallbackPeriod, kvsRetryStrategy,
                serviceCallCompletionTimeout, serviceCallConnectionTimeout);

        final String deviceName = "java-test-application";
        final int streamCount = 10;
        final Tag[] tags = null;
        final String methodName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        final String clientId = String.format("ProducerJava-%s-%s", this.getClass().getSimpleName(), methodName);

        final DeviceInfo deviceInfo = DeviceInfo.createDeviceInfoV1(deviceName, this.storageInfo, streamCount, tags,
                clientId, clientInfo);

        createProducer(deviceInfo);

        testStreaming(methodName);

        free();
    }

    @SuppressWarnings({"UnnecessaryLocalVariable"})
    private void testStreaming(final String methodName) throws ProducerException {
        final String streamName = "DeviceInfoClientInfoVersionTest-" + methodName + "-" + System.currentTimeMillis();
        final StreamInfo.StreamingType streamingType = StreamInfo.StreamingType.STREAMING_TYPE_REALTIME;
        final long maxLatency = TEN_SECONDS_HUNDREDS_OF_NANOS;
        final long bufferDuration = TEN_SECONDS_HUNDREDS_OF_NANOS;

        final KinesisVideoProducerStream stream = createTestStream(streamName, streamingType, maxLatency, bufferDuration);

        final long now = System.currentTimeMillis();
        final int fps = 10;
        final int durationSec = 3;
        final int keyFrameIntervalSec = 1;

        // Stream durationSec seconds ago until now
        for (int index = 0; index < durationSec * fps; index++) {
            final int flags = index % (keyFrameIntervalSec * fps) == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE;
            final long frameDurationMs = 1000 / fps;
            final long timestampMs = now - (durationSec * 1000) + index * frameDurationMs;
            final long dtsPtsHundredsOfNanos = timestampMs * HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
            final byte[] mockData = new byte[]{1, 2, 3, 4};
            final KinesisVideoFrame testFrame = new KinesisVideoFrame(index, flags,
                    dtsPtsHundredsOfNanos, dtsPtsHundredsOfNanos,
                    1000 / fps * HUNDREDS_OF_NANOS_IN_A_MILLISECOND, ByteBuffer.wrap(mockData));
            try {
                stream.putFrame(testFrame);

                Thread.sleep(frameDurationMs);
            } catch (final Exception e) {
                e.printStackTrace();
                fail("Failed to put the frames into the stream! " + e.getMessage());
            }
        }
        try {
            Thread.sleep(WAIT_5_SECONDS_FOR_ACKS);
        } catch (final InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        // frameDropped_ is set to false initially. It can be set to true by droppedFrameReport callback in case there
        // was a frame that was dropped during the test
        assertFalse(this.frameDropped_);
        // errorStatus_ is set to STATUS_SUCCESS initially. It can be set to a different statusCode by
        // streamErrorReport callback in case an error is encountered during the test
        assertEquals(STATUS_SUCCESS, this.errorStatus_);
        // bufferingAckInSequence_ is true initially. It can be set to false by fragmentAckReceived callback in case the
        // (current timestamp - previous timestamp of the ack) > fragment duration
        assertTrue(this.bufferingAckInSequence_);

        freeTestStream(stream);

        deleteStream(streamName);
    }
}
