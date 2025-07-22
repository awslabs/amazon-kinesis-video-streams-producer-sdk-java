package com.amazonaws.kinesisvideo.common;

import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Time;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests check that putFrame with a non-direct ByteBuffer is OK.
 * The JNI layer will grab the frame data using GetDirectBufferAddress, but it may perform additional logic
 * on the frameData based on the Stream's NAL adaption flags.
 *
 * <p>See setFrame() and putKinesisVideoFrame() in the JNI</p>
 */
@RunWith(Parameterized.class)
public class PutFrameNalAdaptionFlagsTest extends ProducerTestBase {

    @Before
    public void checkJNIAvailability() {
        final boolean jniLoaded = isJNILoaded();
        if (!jniLoaded) {
            fail("JNI library not found.");
        }
    }

    @Parameterized.Parameters(name = "{index}: NalAdaptionFlag={0}")
    public static Collection<Object[]> parametersFor_when_putFrameWithNullByteBufferData_then_exceptionIsThrown() {
        return Arrays.stream(StreamInfo.NalAdaptationFlags.values())
                .map(nalAdaptationFlag -> new Object[]{nalAdaptationFlag})
                .collect(Collectors.toList());
    }

    private final StreamInfo.NalAdaptationFlags nalAdaptationFlags;

    public PutFrameNalAdaptionFlagsTest(final StreamInfo.NalAdaptationFlags nalAdaptationFlag) {
        this.nalAdaptationFlags = nalAdaptationFlag;
    }

    /**
     * This test verifies that a ProducerException is thrown when attempting to put a frame with a null ByteBuffer data
     * using all the NAL adaption flags.
     *
     * <p>The JNI has a special case with {@link com.amazonaws.kinesisvideo.producer.StreamInfo.NalAdaptationFlags#NAL_ADAPTATION_ANNEXB_CPD_NALS}</p>
     *
     * <p>See KinesisVideoClientWrapper.cpp in the JNI.</p>
     */
    @Test
    public void when_putFrameWithNullByteBufferData_then_exceptionIsThrown() {
        final KinesisVideoProducerStream kinesisVideoProducerStream;
        final String testStreamName = "JavaProducerApiTestStream_when_putFrameWithNullByteBufferData_then_exceptionIsThrown";

        createProducer();

        // Create a test stream with the appropriate NAL adaptation flag
        kinesisVideoProducerStream = createTestStream(testStreamName,
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, TEST_LATENCY, TEST_BUFFER_DURATION, this.nalAdaptationFlags);

        // Create a non-direct ByteBuffer which will cause JNI to return null when calling GetDirectBufferAddress
        final ByteBuffer nonDirectBuffer = ByteBuffer.allocate(10);

        final long currentTimeMs = System.currentTimeMillis() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
        final KinesisVideoFrame frame = new KinesisVideoFrame(
                0,
                FRAME_FLAG_KEY_FRAME,
                currentTimeMs,
                currentTimeMs,
                this.frameDuration_,
                nonDirectBuffer) {

            // Override getData() to return our non-direct buffer directly without the conversion logic
            // that's in the original implementation
            @Override
            @Nonnull
            public ByteBuffer getData() {
                return nonDirectBuffer;
            }
        };

        try {
            // This should throw an exception due to the null safety check in JNI
            kinesisVideoProducerStream.putFrame(frame);
            fail("Expected ProducerException to be thrown when using a ByteBuffer that returns null address");
        } catch (final ProducerException e) {
            // Expected exception
            assertEquals(ProducerException.STATUS_INVALID_OPERATION, e.getStatusCode());
        } finally {
            freeTestStream(kinesisVideoProducerStream);
        }
    }
}
