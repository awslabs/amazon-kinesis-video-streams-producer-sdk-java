package com.amazonaws.kinesisvideo.producer;

import com.amazonaws.kinesisvideo.common.ProducerTestBase;
import org.junit.Test;

import static com.amazonaws.kinesisvideo.producer.MkvTrackInfoType.VIDEO;
import static com.amazonaws.kinesisvideo.producer.StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_FLAG_NONE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_BITRATE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_GOP_DURATION;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_REPLAY_DURATION;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_STALENESS_DURATION;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_TIMESCALE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_TRACK_ID;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.KEYFRAME_FRAGMENTATION;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.NOT_ADAPTIVE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.NO_KMS_KEY_ID;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.NO_RETENTION;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RECALCULATE_METRICS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RECOVER_ON_FAILURE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RELATIVE_TIMECODES;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.REQUEST_FRAGMENT_ACKS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RETENTION_ONE_HOUR;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.USE_FRAME_TIMECODES;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VERSION_TWO;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VIDEO_CODEC_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class StreamInfoTest extends ProducerTestBase {


    /**
     * Persisted ACKs are used to clear the content store. In retention = 0 case, there are no persisted ACKS,
     * so OOM policy should not be used.
     */
    @Test
    public void whenStorePressurePolicyRetentionZero_thenContentStorePolicyOverrideDropTailItem() {

        final byte[] AVCC_EXTRA_DATA = {
                (byte) 0x01, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0xFF, (byte) 0xE1, (byte) 0x00, (byte) 0x22,
                (byte) 0x27, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0x89, (byte) 0x8B, (byte) 0x60, (byte) 0x50,
                (byte) 0x1E, (byte) 0xD8, (byte) 0x08, (byte) 0x80, (byte) 0x00, (byte) 0x13, (byte) 0x88,
                (byte) 0x00, (byte) 0x03, (byte) 0xD0, (byte) 0x90, (byte) 0x70, (byte) 0x30, (byte) 0x00, (byte) 0x5D,
                (byte) 0xC0, (byte) 0x00, (byte) 0x17, (byte) 0x70, (byte) 0x5E, (byte) 0xF7, (byte) 0xC1, (byte) 0xF0,
                (byte) 0x88, (byte) 0x46, (byte) 0xE0, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x28, (byte) 0xCE,
                (byte) 0x1F, (byte) 0x20};

        final TrackInfo[] trackInfoList = new TrackInfo[]{
                new TrackInfo(DEFAULT_TRACK_ID, VIDEO_CODEC_ID, "VideoTrack", AVCC_EXTRA_DATA, VIDEO)};

        final StreamInfo streamInfo = new StreamInfo(VERSION_TWO,
                "Test Stream 1",
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                "video/h264",
                NO_KMS_KEY_ID,
                NO_RETENTION,
                NOT_ADAPTIVE,
                TEST_LATENCY,
                DEFAULT_GOP_DURATION,
                KEYFRAME_FRAGMENTATION,
                USE_FRAME_TIMECODES,
                RELATIVE_TIMECODES,
                REQUEST_FRAGMENT_ACKS,
                RECOVER_ON_FAILURE,
                DEFAULT_BITRATE,
                fps_,
                TEST_BUFFER_DURATION,
                DEFAULT_REPLAY_DURATION,
                DEFAULT_STALENESS_DURATION,
                DEFAULT_TIMESCALE,
                RECALCULATE_METRICS,
                new Tag[]{
                        new Tag("device", "Test Device"),
                        new Tag("stream", "Test Stream")},
                NAL_ADAPTATION_FLAG_NONE,
                null,
                trackInfoList,
                FrameOrderMode.FRAME_ORDER_MODE_PASS_THROUGH,
                StreamInfo.StorePressurePolicy.CONTENT_STORE_PRESSURE_POLICY_OOM,
                false);

        assertEquals("When retention=0, the content store policy should be overwritten to drop tail item",
                StreamInfo.StorePressurePolicy.CONTENT_STORE_PRESSURE_POLICY_DROP_TAIL_ITEM.getIntValue(),
                streamInfo.getStorePressurePolicy());
    }

    @Test
    public void testGettersReturnExpectedValues() {
        final byte[] AVCC_EXTRA_DATA = {
                (byte) 0x01, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0xFF, (byte) 0xE1, (byte) 0x00, (byte) 0x22,
                (byte) 0x27, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0x89, (byte) 0x8B, (byte) 0x60, (byte) 0x50,
                (byte) 0x1E, (byte) 0xD8, (byte) 0x08, (byte) 0x80, (byte) 0x00, (byte) 0x13, (byte) 0x88,
                (byte) 0x00, (byte) 0x03, (byte) 0xD0, (byte) 0x90, (byte) 0x70, (byte) 0x30, (byte) 0x00, (byte) 0x5D,
                (byte) 0xC0, (byte) 0x00, (byte) 0x17, (byte) 0x70, (byte) 0x5E, (byte) 0xF7, (byte) 0xC1, (byte) 0xF0,
                (byte) 0x88, (byte) 0x46, (byte) 0xE0, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x28, (byte) 0xCE,
                (byte) 0x1F, (byte) 0x20};

        final TrackInfo[] trackInfoList = new TrackInfo[]{
                new TrackInfo(DEFAULT_TRACK_ID, VIDEO_CODEC_ID, "VideoTrack", AVCC_EXTRA_DATA, VIDEO)};

        final Tag[] tags = new Tag[]{
                new Tag("device", "Test Device"),
                new Tag("stream", "Test Stream")};

        final StreamInfo streamInfo = new StreamInfo(VERSION_TWO,
                "Test Stream 1",
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                "video/h264",
                NO_KMS_KEY_ID,
                RETENTION_ONE_HOUR,
                NOT_ADAPTIVE,
                TEST_LATENCY,
                DEFAULT_GOP_DURATION,
                KEYFRAME_FRAGMENTATION,
                USE_FRAME_TIMECODES,
                RELATIVE_TIMECODES,
                REQUEST_FRAGMENT_ACKS,
                RECOVER_ON_FAILURE,
                DEFAULT_BITRATE,
                fps_,
                TEST_BUFFER_DURATION,
                DEFAULT_REPLAY_DURATION,
                DEFAULT_STALENESS_DURATION,
                DEFAULT_TIMESCALE,
                RECALCULATE_METRICS,
                tags,
                NAL_ADAPTATION_FLAG_NONE,
                null,
                trackInfoList,
                FrameOrderMode.FRAME_ORDER_MODE_PASS_THROUGH,
                StreamInfo.StorePressurePolicy.CONTENT_STORE_PRESSURE_POLICY_OOM,
                false);

        assertEquals(VERSION_TWO, streamInfo.getVersion());
        assertEquals("Test Stream 1", streamInfo.getName());
        assertEquals("video/h264", streamInfo.getContentType());
        assertEquals(NO_KMS_KEY_ID, streamInfo.getKmsKeyId());
        assertEquals(StreamInfo.StreamingType.STREAMING_TYPE_REALTIME.getIntValue(), streamInfo.getStreamingType());
        assertEquals(RETENTION_ONE_HOUR, streamInfo.getRetentionPeriod());
        assertEquals(NOT_ADAPTIVE, streamInfo.isAdaptive());
        assertEquals(TEST_LATENCY, streamInfo.getMaxLatency());
        assertEquals(DEFAULT_GOP_DURATION, streamInfo.getFragmentDuration());
        assertEquals(KEYFRAME_FRAGMENTATION, streamInfo.isKeyFrameFragmentation());
        assertEquals(USE_FRAME_TIMECODES, streamInfo.isFrameTimecodes());
        assertEquals(RELATIVE_TIMECODES, streamInfo.isAbsoluteFragmentTimes());
        assertEquals(REQUEST_FRAGMENT_ACKS, streamInfo.isFragmentAcks());
        assertEquals(RECOVER_ON_FAILURE, streamInfo.isRecoverOnError());
        assertEquals(DEFAULT_BITRATE, streamInfo.getAvgBandwidthBps());
        assertEquals(fps_, streamInfo.getFrameRate());
        assertEquals(TEST_BUFFER_DURATION, streamInfo.getBufferDuration());
        assertEquals(DEFAULT_REPLAY_DURATION, streamInfo.getReplayDuration());
        assertEquals(DEFAULT_STALENESS_DURATION, streamInfo.getConnectionStalenessDuration());
        assertEquals(DEFAULT_TIMESCALE, streamInfo.getTimecodeScale());
        assertEquals(RECALCULATE_METRICS, streamInfo.isRecalculateMetrics());
        assertNotNull(streamInfo.getTags());
        assertEquals(tags.length, streamInfo.getTags().length);
        assertEquals("device", streamInfo.getTags()[0].getName());
        assertEquals("Test Device", streamInfo.getTags()[0].getValue());
        assertEquals(trackInfoList.length, streamInfo.getTrackInfoList().length);
        assertEquals(DEFAULT_TRACK_ID, trackInfoList[0].getTrackId());
        assertEquals(VIDEO_CODEC_ID, trackInfoList[0].getCodecId());
        assertEquals("VideoTrack", trackInfoList[0].getTrackName());
        assertEquals(FrameOrderMode.FRAME_ORDER_MODE_PASS_THROUGH.intValue(), streamInfo.getFrameOrderMode());
        assertEquals(NAL_ADAPTATION_FLAG_NONE.getIntValue(), streamInfo.getNalAdaptationFlags());
        assertEquals(StreamInfo.StorePressurePolicy.CONTENT_STORE_PRESSURE_POLICY_OOM.getIntValue(), streamInfo.getStorePressurePolicy());
        assertFalse(streamInfo.isAllowStreamCreation());
    }
}