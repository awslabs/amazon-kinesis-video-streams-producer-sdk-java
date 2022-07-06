package com.amazonaws.kinesisvideo.common;

import com.amazonaws.kinesisvideo.producer.FrameOrderMode;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Tag;
import com.amazonaws.kinesisvideo.producer.TrackInfo;
import static com.amazonaws.kinesisvideo.producer.MkvTrackInfoType.VIDEO;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VIDEO_CODEC_ID;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import static com.amazonaws.kinesisvideo.producer.StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_FLAG_NONE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.*;

public class ProducerUnitTests extends ProducerTestBase {
    @Test
    public void checkStorePressurePolicyRetentionZero() {

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

        StreamInfo streamInfo = new StreamInfo(VERSION_TWO,
                "Test Stream 1",
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                "video/h264",
                NO_KMS_KEY_ID,
                0,
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
                new Tag[] {
                        new Tag("device", "Test Device"),
                        new Tag("stream", "Test Stream") },
                NAL_ADAPTATION_FLAG_NONE,
                null,
                trackInfoList,
                FrameOrderMode.FRAME_ORDER_MODE_PASS_THROUGH,
                StreamInfo.StorePressurePolicy.CONTENT_STORE_PRESSURE_POLICY_OOM);

        assertEquals(StreamInfo.StorePressurePolicy.CONTENT_STORE_PRESSURE_POLICY_DROP_TAIL_ITEM.getIntValue(),
                streamInfo.getStorePressurePolicy());
    }
}
