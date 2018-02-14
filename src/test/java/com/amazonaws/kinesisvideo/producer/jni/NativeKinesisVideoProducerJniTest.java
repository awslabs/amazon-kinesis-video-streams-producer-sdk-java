package com.amazonaws.kinesisvideo.producer.jni;

import com.amazonaws.kinesisvideo.producer.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class NativeKinesisVideoProducerJniTest extends NativeKinesisVideoProducerJniTestBase {
    private static final long TEST_LONG_FRAME_DURATION = 40 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
    private static final long TEST_BUFFER_DURATION = 40 * Time.HUNDREDS_OF_NANOS_IN_A_SECOND;

    private static final int TEST_FRAME_SIZE = 1000;

    public NativeKinesisVideoProducerJniTest() throws Exception {
    }

    @Test
    public void producerCreateSyncTest() throws Exception {
        String filePathWithoutExtension = System.getProperty("tests.additional.LD_LIBRARY_PATH");
        final Properties props = System.getProperties();
        if (filePathWithoutExtension == null || filePathWithoutExtension.isEmpty()) {
            filePathWithoutExtension = "../KinesisVideoProducerJNI/build/lib/libKinesisVideoProducerJNI";
        }

        final File file = new File(filePathWithoutExtension);

        final NativeKinesisVideoProducerJni producer = new NativeKinesisVideoProducerJni(
                mAuthCallbacks,
                mStorageCallbacks,
                mServiceCallbacks,
                mLog);

        producer.createSync(mDeviceInfo, file.getCanonicalPath());

        // Should be initialized and ready
        Assert.assertTrue(producer.isInitialized());
        Assert.assertTrue(producer.isReady());
    }

    @Test
    public void producerStreamCreateSyncTest() throws Exception {
        final KinesisVideoProducerStream stream = mProducer.createStreamSync(mStreamInfo, mStreamCallbacks);

        // The latch should have been stepped down
        Assert.assertEquals(0, mStreamReadyLatch.getCount());
    }

    @Test
    public void putGetFrameBoundaryTest() throws Exception {
        createTestStream();

        int i, j, filledSize, offset, bufferSize;
        boolean validPattern;
        final byte tempBuffer[] = new byte[TEST_FRAME_SIZE];
        final ByteBuffer frameData = ByteBuffer.wrap(tempBuffer);
        final byte getDataBuffer[] = new byte[TEST_FRAME_SIZE * 2];
        long timestamp;

        // Produce frames
        for (i = 0, timestamp = 0; timestamp < TEST_BUFFER_DURATION; timestamp += TEST_LONG_FRAME_DURATION, i++) {

            // Key frame every 10th
            final int flags = i % 10 == 0 ? FrameFlags.FRAME_FLAG_KEY_FRAME : FrameFlags.FRAME_FLAG_NONE;

            // Set the frame bits
            Arrays.fill(tempBuffer, (byte) i);
            frameData.rewind();
            final KinesisVideoFrame frame = new KinesisVideoFrame(i, flags, timestamp, timestamp, TEST_LONG_FRAME_DURATION, frameData);

            mStream.putFrame(frame);
        }

        // Consume frames on the boundary and validate

        for (i = 0, timestamp = 0; timestamp < TEST_BUFFER_DURATION; timestamp += TEST_LONG_FRAME_DURATION, i++) {

            // The first frame will have the cluster and MKV overhead
            if (i == 0) {
                offset = MKV_HEADER_OVERHEAD;
            } else if (i % 10 == 0) {
                // Cluster start will have cluster overhead
                offset = MKV_CLUSTER_OVERHEAD;
            } else {
                // Simple block overhead
                offset = MKV_SIMPLE_BLOCK_OVERHEAD;
            }

            // Set the buffer size to be the offset + frame bits size
            bufferSize = TEST_FRAME_SIZE + offset;

            Arrays.fill(getDataBuffer, (byte) 55);
            filledSize = mStream.getStreamData(getDataBuffer, 0, bufferSize);
            Assert.assertEquals(bufferSize, filledSize);

            // Validate the fill pattern
            validPattern = true;
            for (j = 0; j < TEST_FRAME_SIZE; j++) {
                if (getDataBuffer[offset + j] != (byte) i) {
                    validPattern = false;
                    break;
                }
            }

            final String message = String.format("Failed at offset: %d from the beginning of frame: %d", j, i);
            Assert.assertTrue(message, validPattern);
        }
    }

    @Test
    public void putGetFrameBoundaryInterleavedTest() throws Exception {
        createTestStream();

        int i, j, filledSize, offset, bufferSize = 0;
        boolean validPattern;
        final byte tempBuffer[] = new byte[TEST_FRAME_SIZE];
        final ByteBuffer frameData = ByteBuffer.wrap(tempBuffer);
        final byte getDataBuffer[] = new byte[TEST_FRAME_SIZE * 2];
        long timestamp;

        // Produce frames
        for (i = 0, timestamp = 0; timestamp < TEST_BUFFER_DURATION; timestamp += TEST_LONG_FRAME_DURATION, i++) {

            // Key frame every 10th
            final int flags = i % 10 == 0 ? FrameFlags.FRAME_FLAG_KEY_FRAME : FrameFlags.FRAME_FLAG_NONE;

            // Set the frame bits
            Arrays.fill(tempBuffer, (byte) i);
            frameData.rewind();
            final KinesisVideoFrame frame = new KinesisVideoFrame(i, flags, timestamp, timestamp, TEST_LONG_FRAME_DURATION, frameData);

            mStream.putFrame(frame);

            // Consume frames on the boundary and validate

            // The first frame will have the cluster and MKV overhead
            if (i == 0) {
                offset = MKV_HEADER_OVERHEAD;
            } else if (i % 10 == 0) {
                // Cluster start will have cluster overhead
                offset = MKV_CLUSTER_OVERHEAD;
            } else {
                // Simple block overhead
                offset = MKV_SIMPLE_BLOCK_OVERHEAD;
            }

            // Set the buffer size to be the offset + frame bits size
            bufferSize = TEST_FRAME_SIZE + offset;

            Arrays.fill(getDataBuffer, (byte) 55);
            mDataAvailableLatch.await(STREAM_CREATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            filledSize = mStream.getStreamData(getDataBuffer, 0, bufferSize);
            Assert.assertEquals(bufferSize, filledSize);

            // Validate the fill pattern
            validPattern = true;
            for (j = 0; j < TEST_FRAME_SIZE; j++) {
                if (getDataBuffer[offset + j] != (byte) i) {
                    validPattern = false;
                    break;
                }
            }

            final String message = String.format("Failed at offset: %d from the beginning of frame: %d", j, i);
            Assert.assertTrue(message, validPattern);
        }

        filledSize = mStream.getStreamData(getDataBuffer, 0, bufferSize);
        Assert.assertEquals(0, filledSize);
    }

    @Test
    public void putGetFrameBoundaryWithCPD1ByteTest() throws Exception {
        // Set some CPD bits
        final byte[] cpd = new byte[10];
        mStreamInfo = new StreamInfo(mStreamInfo.getVersion(),
                mStreamInfo.getName(),
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                mStreamInfo.getContentType(),
                mStreamInfo.getKmsKeyId(),
                mStreamInfo.getRetentionPeriod(),
                mStreamInfo.isAdaptive(),
                mStreamInfo.getMaxLatency(),
                mStreamInfo.getFragmentDuration(),
                mStreamInfo.isKeyFrameFragmentation(),
                mStreamInfo.isFrameTimecodes(),
                mStreamInfo.isAbsoluteFragmentTimes(),
                mStreamInfo.isFragmentAcks(),
                mStreamInfo.isRecoverOnError(),
                mStreamInfo.getCodecId(),
                mStreamInfo.getTrackName(),
                mStreamInfo.getAvgBandwidthBps(),
                mStreamInfo.getFrameRate(),
                mStreamInfo.getBufferDuration(),
                mStreamInfo.getReplayDuration(),
                mStreamInfo.getConnectionStalenessDuration(),
                mStreamInfo.getTimecodeScale(),
                mStreamInfo.isRecalculateMetrics(),
                cpd,
                mStreamInfo.getTags(),
                StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_FLAG_NONE);
        createTestStream();

        int i, j, filledSize, offset, bufferSize;
        boolean validPattern;
        final byte tempBuffer[] = new byte[TEST_FRAME_SIZE];
        final ByteBuffer frameData = ByteBuffer.wrap(tempBuffer);
        final byte getDataBuffer[] = new byte[TEST_FRAME_SIZE * 2];
        long timestamp;

        // Produce frames
        for (i = 0, timestamp = 0; timestamp < TEST_BUFFER_DURATION; timestamp += TEST_LONG_FRAME_DURATION, i++) {

            // Key frame every 10th
            final int flags = i % 10 == 0 ? FrameFlags.FRAME_FLAG_KEY_FRAME : FrameFlags.FRAME_FLAG_NONE;

            // Set the frame bits
            Arrays.fill(tempBuffer, (byte) i);
            frameData.rewind();
            final KinesisVideoFrame frame = new KinesisVideoFrame(i, flags, timestamp, timestamp, TEST_LONG_FRAME_DURATION, frameData);

            mStream.putFrame(frame);
        }

        // Consume frames on the boundary and validate

        for (i = 0, timestamp = 0; timestamp < TEST_BUFFER_DURATION; timestamp += TEST_LONG_FRAME_DURATION, i++) {

            // The first frame will have the cluster and MKV overhead
            if (i == 0) {
                // We should account for the CPD bits
                // CPD + CPD elem size + CPD encoded len
                offset = MKV_HEADER_OVERHEAD + cpd.length + MKV_CODEC_PRIVATE_DATA_ELEM_SIZE + 1;
            } else if (i % 10 == 0) {
                // Cluster start will have cluster overhead
                offset = MKV_CLUSTER_OVERHEAD;
            } else {
                // Simple block overhead
                offset = MKV_SIMPLE_BLOCK_OVERHEAD;
            }

            // Set the buffer size to be the offset + frame bits size
            bufferSize = TEST_FRAME_SIZE + offset;

            Arrays.fill(getDataBuffer, (byte) 55);
            filledSize = mStream.getStreamData(getDataBuffer, 0, bufferSize);
            Assert.assertEquals(bufferSize, filledSize);

            // Validate the fill pattern
            validPattern = true;
            for (j = 0; j < TEST_FRAME_SIZE; j++) {
                if (getDataBuffer[offset + j] != (byte) i) {
                    validPattern = false;
                    break;
                }
            }

            final String message = String.format("Failed at offset: %d from the beginning of frame: %d", j, i);
            Assert.assertTrue(message, validPattern);
        }
    }

    @Test
    public void putGetFrameBoundaryWithCPD2ByteTest() throws Exception {
        // Set some CPD bits
        final byte[] cpd = new byte[0x4000 - 2];
        mStreamInfo = new StreamInfo(mStreamInfo.getVersion(),
                mStreamInfo.getName(),
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                mStreamInfo.getContentType(),
                mStreamInfo.getKmsKeyId(),
                mStreamInfo.getRetentionPeriod(),
                mStreamInfo.isAdaptive(),
                mStreamInfo.getMaxLatency(),
                mStreamInfo.getFragmentDuration(),
                mStreamInfo.isKeyFrameFragmentation(),
                mStreamInfo.isFrameTimecodes(),
                mStreamInfo.isAbsoluteFragmentTimes(),
                mStreamInfo.isFragmentAcks(),
                mStreamInfo.isRecoverOnError(),
                mStreamInfo.getCodecId(),
                mStreamInfo.getTrackName(),
                mStreamInfo.getAvgBandwidthBps(),
                mStreamInfo.getFrameRate(),
                mStreamInfo.getBufferDuration(),
                mStreamInfo.getReplayDuration(),
                mStreamInfo.getConnectionStalenessDuration(),
                mStreamInfo.getTimecodeScale(),
                mStreamInfo.isRecalculateMetrics(),
                cpd,
                mStreamInfo.getTags(),
                StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_FLAG_NONE);
        createTestStream();

        int i, j, filledSize, offset, bufferSize;
        boolean validPattern;
        final byte tempBuffer[] = new byte[TEST_FRAME_SIZE];
        final ByteBuffer frameData = ByteBuffer.wrap(tempBuffer);
        final byte getDataBuffer[] = new byte[TEST_FRAME_SIZE * 2 + cpd.length];
        long timestamp;

        // Produce frames
        for (i = 0, timestamp = 0; timestamp < TEST_BUFFER_DURATION; timestamp += TEST_LONG_FRAME_DURATION, i++) {

            // Key frame every 10th
            final int flags = i % 10 == 0 ? FrameFlags.FRAME_FLAG_KEY_FRAME : FrameFlags.FRAME_FLAG_NONE;

            // Set the frame bits
            Arrays.fill(tempBuffer, (byte) i);
            frameData.rewind();
            final KinesisVideoFrame frame = new KinesisVideoFrame(i, flags, timestamp, timestamp, TEST_LONG_FRAME_DURATION, frameData);

            mStream.putFrame(frame);
        }

        // Consume frames on the boundary and validate

        for (i = 0, timestamp = 0; timestamp < TEST_BUFFER_DURATION; timestamp += TEST_LONG_FRAME_DURATION, i++) {

            // The first frame will have the cluster and MKV overhead
            if (i == 0) {
                // We should account for the CPD bits
                // CPD + CPD elem size + CPD encoded len
                offset = MKV_HEADER_OVERHEAD + cpd.length + MKV_CODEC_PRIVATE_DATA_ELEM_SIZE + 2;
            } else if (i % 10 == 0) {
                // Cluster start will have cluster overhead
                offset = MKV_CLUSTER_OVERHEAD;
            } else {
                // Simple block overhead
                offset = MKV_SIMPLE_BLOCK_OVERHEAD;
            }

            // Set the buffer size to be the offset + frame bits size
            bufferSize = TEST_FRAME_SIZE + offset;

            Arrays.fill(getDataBuffer, (byte) 55);
            filledSize = mStream.getStreamData(getDataBuffer, 0, bufferSize);
            Assert.assertEquals(bufferSize, filledSize);

            // Validate the fill pattern
            validPattern = true;
            for (j = 0; j < TEST_FRAME_SIZE; j++) {
                if (getDataBuffer[offset + j] != (byte) i) {
                    validPattern = false;
                    break;
                }
            }

            final String message = String.format("Failed at offset: %d from the beginning of frame: %d", j, i);
            Assert.assertTrue(message, validPattern);
        }
    }

    @Test
    public void putGetFrameBoundaryWithCPD3ByteTest() throws Exception {
        // Set some CPD bits
        final byte[] cpd = new byte[0x4000];
        mStreamInfo = new StreamInfo(mStreamInfo.getVersion(),
                mStreamInfo.getName(),
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                mStreamInfo.getContentType(),
                mStreamInfo.getKmsKeyId(),
                mStreamInfo.getRetentionPeriod(),
                mStreamInfo.isAdaptive(),
                mStreamInfo.getMaxLatency(),
                mStreamInfo.getFragmentDuration(),
                mStreamInfo.isKeyFrameFragmentation(),
                mStreamInfo.isFrameTimecodes(),
                mStreamInfo.isAbsoluteFragmentTimes(),
                mStreamInfo.isFragmentAcks(),
                mStreamInfo.isRecoverOnError(),
                mStreamInfo.getCodecId(),
                mStreamInfo.getTrackName(),
                mStreamInfo.getAvgBandwidthBps(),
                mStreamInfo.getFrameRate(),
                mStreamInfo.getBufferDuration(),
                mStreamInfo.getReplayDuration(),
                mStreamInfo.getConnectionStalenessDuration(),
                mStreamInfo.getTimecodeScale(),
                mStreamInfo.isRecalculateMetrics(),
                cpd,
                mStreamInfo.getTags(),
                StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_FLAG_NONE);
        createTestStream();

        int i, j, filledSize, offset, bufferSize;
        boolean validPattern;
        final byte tempBuffer[] = new byte[TEST_FRAME_SIZE];
        final ByteBuffer frameData = ByteBuffer.wrap(tempBuffer);
        final byte getDataBuffer[] = new byte[TEST_FRAME_SIZE * 2 + cpd.length];
        long timestamp;

        // Produce frames
        for (i = 0, timestamp = 0; timestamp < TEST_BUFFER_DURATION; timestamp += TEST_LONG_FRAME_DURATION, i++) {

            // Key frame every 10th
            final int flags = i % 10 == 0 ? FrameFlags.FRAME_FLAG_KEY_FRAME : FrameFlags.FRAME_FLAG_NONE;

            // Set the frame bits
            Arrays.fill(tempBuffer, (byte) i);
            frameData.rewind();
            final KinesisVideoFrame frame = new KinesisVideoFrame(i, flags, timestamp, timestamp, TEST_LONG_FRAME_DURATION, frameData);

            mStream.putFrame(frame);
        }

        // Consume frames on the boundary and validate

        for (i = 0, timestamp = 0; timestamp < TEST_BUFFER_DURATION; timestamp += TEST_LONG_FRAME_DURATION, i++) {

            // The first frame will have the cluster and MKV overhead
            if (i == 0) {
                // We should account for the CPD bits
                // CPD + CPD elem size + CPD encoded len
                offset = MKV_HEADER_OVERHEAD + cpd.length + MKV_CODEC_PRIVATE_DATA_ELEM_SIZE + 3;
            } else if (i % 10 == 0) {
                // Cluster start will have cluster overhead
                offset = MKV_CLUSTER_OVERHEAD;
            } else {
                // Simple block overhead
                offset = MKV_SIMPLE_BLOCK_OVERHEAD;
            }

            // Set the buffer size to be the offset + frame bits size
            bufferSize = TEST_FRAME_SIZE + offset;

            Arrays.fill(getDataBuffer, (byte) 55);
            filledSize = mStream.getStreamData(getDataBuffer, 0, bufferSize);
            Assert.assertEquals(bufferSize, filledSize);

            // Validate the fill pattern
            validPattern = true;
            for (j = 0; j < TEST_FRAME_SIZE; j++) {
                if (getDataBuffer[offset + j] != (byte) i) {
                    validPattern = false;
                    break;
                }
            }

            final String message = String.format("Failed at offset: %d from the beginning of frame: %d", j, i);
            Assert.assertTrue(message, validPattern);
        }
    }

    @Test
    public void putGetFrameBoundaryHalfBufferTest() throws Exception {
        createTestStream();

        int i, j, filledSize, offset, bufferSize;
        boolean validPattern;
        final byte tempBuffer[] = new byte[TEST_FRAME_SIZE];
        final ByteBuffer frameData = ByteBuffer.wrap(tempBuffer);
        final byte getDataBuffer[] = new byte[TEST_FRAME_SIZE * 2];
        long timestamp;

        // Produce frames
        for (i = 0, timestamp = 0; timestamp < TEST_BUFFER_DURATION; timestamp += TEST_LONG_FRAME_DURATION, i++) {

            // Key frame every 10th
            final int flags = i % 10 == 0 ? FrameFlags.FRAME_FLAG_KEY_FRAME : FrameFlags.FRAME_FLAG_NONE;

            // Set the frame bits
            Arrays.fill(tempBuffer, (byte) i);
            frameData.rewind();
            final KinesisVideoFrame frame = new KinesisVideoFrame(i, flags, timestamp, timestamp, TEST_LONG_FRAME_DURATION, frameData);

            mStream.putFrame(frame);
        }

        // Consume frames on the boundary and validate

        for (i = 0, timestamp = 0; timestamp < TEST_BUFFER_DURATION; timestamp += TEST_LONG_FRAME_DURATION, i++) {

            // The first frame will have the cluster and MKV overhead
            if (i == 0) {
                offset = MKV_HEADER_OVERHEAD;
            } else if (i % 10 == 0) {
                // Cluster start will have cluster overhead
                offset = MKV_CLUSTER_OVERHEAD;
            } else {
                // Simple block overhead
                offset = MKV_SIMPLE_BLOCK_OVERHEAD;
            }

            // Set the buffer size to be the offset + frame bits size
            bufferSize = TEST_FRAME_SIZE / 2 + offset;

            Arrays.fill(getDataBuffer, (byte) 55);
            filledSize = mStream.getStreamData(getDataBuffer, 0, bufferSize);
            Assert.assertEquals(bufferSize, filledSize);

            // get the second half - store the offset in filledSize
            filledSize = bufferSize;
            bufferSize = TEST_FRAME_SIZE / 2;
            filledSize = mStream.getStreamData(getDataBuffer, filledSize, bufferSize);
            Assert.assertEquals(bufferSize, filledSize);

            // Validate the fill pattern
            validPattern = true;
            for (j = 0; j < TEST_FRAME_SIZE; j++) {
                if (getDataBuffer[offset + j] != (byte) i) {
                    validPattern = false;
                    break;
                }
            }

            final String message = String.format("Failed at offset: %d from the beginning of frame: %d", j, i);
            Assert.assertTrue(message, validPattern);
        }
    }

    @Test
    public void putGetFrameBoundaryHalfBufferInterleavedTest() throws Exception {
        createTestStream();

        int i, j, filledSize, offset, bufferSize = 0;
        boolean validPattern;
        final byte tempBuffer[] = new byte[TEST_FRAME_SIZE];
        final ByteBuffer frameData = ByteBuffer.wrap(tempBuffer);
        final byte getDataBuffer[] = new byte[TEST_FRAME_SIZE * 2];
        long timestamp;

        // Produce frames
        for (i = 0, timestamp = 0; timestamp < TEST_BUFFER_DURATION; timestamp += TEST_LONG_FRAME_DURATION, i++) {

            // Key frame every 10th
            final int flags = i % 10 == 0 ? FrameFlags.FRAME_FLAG_KEY_FRAME : FrameFlags.FRAME_FLAG_NONE;

            // Set the frame bits
            Arrays.fill(tempBuffer, (byte) i);
            frameData.rewind();
            final KinesisVideoFrame frame = new KinesisVideoFrame(i, flags, timestamp, timestamp, TEST_LONG_FRAME_DURATION, frameData);

            mStream.putFrame(frame);

            // Consume frames on the boundary and validate

            // The first frame will have the cluster and MKV overhead
            if (i == 0) {
                offset = MKV_HEADER_OVERHEAD;
            } else if (i % 10 == 0) {
                // Cluster start will have cluster overhead
                offset = MKV_CLUSTER_OVERHEAD;
            } else {
                // Simple block overhead
                offset = MKV_SIMPLE_BLOCK_OVERHEAD;
            }

            // Set the buffer size to be the offset + frame bits size
            bufferSize = TEST_FRAME_SIZE / 2 + offset;

            Arrays.fill(getDataBuffer, (byte) 55);
            mDataAvailableLatch.await(STREAM_CREATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            filledSize = mStream.getStreamData(getDataBuffer, 0, bufferSize);
            Assert.assertEquals(bufferSize, filledSize);

            // get the second half - store the offset in filledSize
            filledSize = bufferSize;
            bufferSize = TEST_FRAME_SIZE / 2;
            filledSize = mStream.getStreamData(getDataBuffer, filledSize, bufferSize);
            Assert.assertEquals(bufferSize, filledSize);

            // Validate the fill pattern
            validPattern = true;
            for (j = 0; j < TEST_FRAME_SIZE; j++) {
                if (getDataBuffer[offset + j] != (byte) i) {
                    validPattern = false;
                    break;
                }
            }

            final String message = String.format("Failed at offset: %d from the beginning of frame: %d", j, i);
            Assert.assertTrue(message, validPattern);
        }

        filledSize = mStream.getStreamData(getDataBuffer, 0, bufferSize);
        Assert.assertEquals(0, filledSize);
    }

    @Test
    public void putGetNonFrameBoundaryBufferTest() throws Exception {
        createTestStream();

        int i, j, filledSize, offset, bufferSize;
        boolean validPattern;
        final byte tempBuffer[] = new byte[TEST_FRAME_SIZE];
        final ByteBuffer frameData = ByteBuffer.wrap(tempBuffer);
        final byte getDataBuffer[] = new byte[TEST_FRAME_SIZE * 2];
        long timestamp;

        // Produce frames
        for (i = 0, timestamp = 0; timestamp < TEST_BUFFER_DURATION; timestamp += TEST_LONG_FRAME_DURATION, i++) {

            // Key frame every 10th
            final int flags = i % 10 == 0 ? FrameFlags.FRAME_FLAG_KEY_FRAME : FrameFlags.FRAME_FLAG_NONE;

            // Set the frame bits
            Arrays.fill(tempBuffer, (byte) i);
            frameData.rewind();
            final KinesisVideoFrame frame = new KinesisVideoFrame(i, flags, timestamp, timestamp, TEST_LONG_FRAME_DURATION, frameData);

            mStream.putFrame(frame);
        }

        // Consume half of the frames on the boundary and validate

        for (i = 0, timestamp = 0; timestamp < TEST_BUFFER_DURATION / 2; timestamp += TEST_LONG_FRAME_DURATION, i++) {

            // The first frame will have the cluster and MKV overhead
            if (i == 0) {
                offset = MKV_HEADER_OVERHEAD;
            } else if (i % 10 == 0) {
                // Cluster start will have cluster overhead
                offset = MKV_CLUSTER_OVERHEAD;
            } else {
                // Simple block overhead
                offset = MKV_SIMPLE_BLOCK_OVERHEAD;
            }

            // Set the buffer size to be the offset + frame bits size
            bufferSize = TEST_FRAME_SIZE + offset;

            Arrays.fill(getDataBuffer, (byte) 55);
            filledSize = mStream.getStreamData(getDataBuffer, 0, bufferSize);
            Assert.assertEquals(bufferSize, filledSize);

            // Validate the fill pattern
            validPattern = true;
            for (j = 0; j < TEST_FRAME_SIZE; j++) {
                if (getDataBuffer[offset + j] != (byte) i) {
                    validPattern = false;
                    break;
                }
            }

            final String message = String.format("Failed at offset: %d from the beginning of frame: %d", j, i);
            Assert.assertTrue(message, validPattern);
        }

        // Read 1.5 size of frame
        bufferSize = TEST_FRAME_SIZE + TEST_FRAME_SIZE / 2 + MKV_CLUSTER_OVERHEAD + MKV_SIMPLE_BLOCK_OVERHEAD;
        Arrays.fill(getDataBuffer, (byte) 55);
        filledSize = mStream.getStreamData(getDataBuffer, 0, bufferSize);
        Assert.assertEquals(bufferSize, filledSize);

        // Validate the first frame fill pattern
        validPattern = true;
        for (j = 0; j < TEST_FRAME_SIZE; j++) {
            if (getDataBuffer[MKV_CLUSTER_OVERHEAD + j] != (byte) i) {
                validPattern = false;
                break;
            }
        }

        String message = String.format("Failed at offset: %d from the beginning of frame: %d", j, i);
        Assert.assertTrue(message, validPattern);

        // Validate the second frame fill pattern
        validPattern = true;
        i++;
        for (j = 0; j < TEST_FRAME_SIZE / 2; j++) {
            if (getDataBuffer[TEST_FRAME_SIZE + MKV_CLUSTER_OVERHEAD + MKV_SIMPLE_BLOCK_OVERHEAD + j] != (byte) i) {
                validPattern = false;
                break;
            }
        }

        message = String.format("Failed at offset: %d from the beginning of frame: %d", j, i);
        Assert.assertTrue(message, validPattern);
    }
}
