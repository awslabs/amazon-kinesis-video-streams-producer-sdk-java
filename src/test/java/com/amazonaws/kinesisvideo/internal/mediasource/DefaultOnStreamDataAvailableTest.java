package com.amazonaws.kinesisvideo.internal.mediasource;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceSink;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DefaultOnStreamDataAvailableTest {

    private static final Logger log = LogManager.getLogger(DefaultOnStreamDataAvailableTest.class);

    /**
     * Mock implementation that tracks when certain callbacks were invoked.
     */
    private static class TestMediaSourceSink implements MediaSourceSink {
        boolean onFrameCalled = false;
        boolean onFragmentMetadataCalled = false;
        KinesisVideoFrame receivedFrame;
        String receivedMetadataName;
        String receivedMetadataValue;
        boolean receivedPersistent;

        @Override
        public void onFrame(@Nonnull final KinesisVideoFrame frame) throws KinesisVideoException {
            this.onFrameCalled = true;
            this.receivedFrame = frame;
        }

        @Override
        public void onCodecPrivateData(final byte[] codecPrivateData) throws KinesisVideoException {
        }

        @Override
        public void onCodecPrivateData(final byte[] codecPrivateData,
                                       final int trackId) throws KinesisVideoException {
        }

        @Override
        public void onFragmentMetadata(@Nonnull final String metadataName,
                                       @Nonnull final String metadataValue,
                                       final boolean persistent) throws KinesisVideoException {
            this.onFragmentMetadataCalled = true;
            this.receivedMetadataName = metadataName;
            this.receivedMetadataValue = metadataValue;
            this.receivedPersistent = persistent;
        }

        @Override
        public KinesisVideoProducerStream getProducerStream() {
            return null;
        }
    }

    /**
     * Passing {@code null} to a required parameter.
     */
    @SuppressWarnings("ConstantConditions")
    @Test(expected = IllegalArgumentException.class)
    public void givenNullMediaSourceSink_whenConstructing_thenThrowsException() {
        new DefaultOnStreamDataAvailable(null);
    }

    /**
     * Passing {@code null} to a required parameter.
     */
    @SuppressWarnings("ConstantConditions")
    @Test(expected = IllegalArgumentException.class)
    public void givenNullFrame_whenOnFrameDataAvailable_thenThrowsException() throws KinesisVideoException {
        final TestMediaSourceSink sink = new TestMediaSourceSink();
        final DefaultOnStreamDataAvailable dataAvailable = new DefaultOnStreamDataAvailable(sink);
        dataAvailable.onFrameDataAvailable((KinesisVideoFrame) null);
    }

    /**
     * Passing zero-size frame.
     */
    @Test
    public void givenEmptyFrame_whenOnFrameDataAvailable_thenThrowsKinesisVideoException() throws KinesisVideoException {
        final TestMediaSourceSink sink = new TestMediaSourceSink();
        final DefaultOnStreamDataAvailable dataAvailable = new DefaultOnStreamDataAvailable(sink);

        final KinesisVideoFrame emptyFrame = new KinesisVideoFrame(0, 0,
                0, 0, 0, ByteBuffer.allocate(0));

        try {
            dataAvailable.onFrameDataAvailable(emptyFrame);
            fail("Expected KinesisVideoException");
        } catch (final KinesisVideoException e) {
            // Happy path
            log.info("Received expected exception", e);
        }
    }

    /**
     * Happy path. Checking that it forwards the frame correctly.
     */
    @Test
    public void givenValidFrame_whenOnFrameDataAvailable_thenCallsMediaSourceSink() throws KinesisVideoException {
        final TestMediaSourceSink sink = new TestMediaSourceSink();
        final DefaultOnStreamDataAvailable dataAvailable = new DefaultOnStreamDataAvailable(sink);

        final KinesisVideoFrame validFrame = new KinesisVideoFrame(0, 0,
                0, 0, 0, ByteBuffer.allocate(100));

        dataAvailable.onFrameDataAvailable(validFrame);

        assertTrue(sink.onFrameCalled);
        assertEquals(validFrame, sink.receivedFrame);
    }

    /**
     * Passing {@code null} to a required parameter.
     */
    @SuppressWarnings("ConstantConditions")
    @Test(expected = IllegalArgumentException.class)
    public void givenNullMetadataName_whenOnFragmentMetadataAvailable_thenThrowsException() throws KinesisVideoException {
        final TestMediaSourceSink sink = new TestMediaSourceSink();
        final DefaultOnStreamDataAvailable dataAvailable = new DefaultOnStreamDataAvailable(sink);
        dataAvailable.onFragmentMetadataAvailable(null, "value", true);
    }

    /**
     * Passing {@code null} to a required parameter.
     */
    @SuppressWarnings("ConstantConditions")
    @Test(expected = IllegalArgumentException.class)
    public void givenNullMetadataValue_whenOnFragmentMetadataAvailable_thenThrowsException() throws KinesisVideoException {
        final TestMediaSourceSink sink = new TestMediaSourceSink();
        final DefaultOnStreamDataAvailable dataAvailable = new DefaultOnStreamDataAvailable(sink);
        dataAvailable.onFragmentMetadataAvailable("name", null, true);
    }

    /**
     * Happy path. Checking that it forwards the metadata correctly.
     */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void givenValidParameters_whenOnFragmentMetadataAvailable_thenCallsMediaSourceSink() throws KinesisVideoException {
        final TestMediaSourceSink sink = new TestMediaSourceSink();
        final DefaultOnStreamDataAvailable dataAvailable = new DefaultOnStreamDataAvailable(sink);
        final String testMetadataName = "testName";
        final String testMetadataValue = "testValue";

        dataAvailable.onFragmentMetadataAvailable(testMetadataName, testMetadataValue, true);

        assertTrue(sink.onFragmentMetadataCalled);
        assertEquals(testMetadataName, sink.receivedMetadataName);
        assertEquals(testMetadataValue, sink.receivedMetadataValue);
        assertTrue(sink.receivedPersistent);
    }
}
