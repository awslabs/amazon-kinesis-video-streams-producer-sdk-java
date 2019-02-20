package com.amazonaws.kinesisvideo.demoapp.contants;

import com.amazonaws.kinesisvideo.producer.TrackInfo;

import static com.amazonaws.kinesisvideo.producer.MkvTrackInfoType.AUDIO;
import static com.amazonaws.kinesisvideo.producer.MkvTrackInfoType.VIDEO;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.AUDIO_CODEC_ID;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.AUDIO_TRACK_ID;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VIDEO_CODEC_ID;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VIDEO_TRACK_ID;

public final class DemoTrackInfos {
    private DemoTrackInfos() {
    }

    // Codec private data could be extracted using gstreamer plugin
    // CHECKSTYLE:SUPPRESS:LineLength
    // GST_DEBUG=4 gst-launch-1.0 rtspsrc location="YourRtspUrl" short-header=TRUE protocols=tcp ! rtph264depay ! decodebin ! videorate ! videoscale ! vtenc_h264_hw allow-frame-reordering=FALSE max-keyframe-interval=25 bitrate=1024 realtime=TRUE ! video/x-h264,stream-format=avc,alignment=au,profile=baseline,width=640,height=480,framerate=1/25 ! multifilesink location=./frame%03d.h264 index=1 | grep codec_data
    private static final byte[] AVCC_EXTRA_DATA = {(byte) 0x01, (byte) 0x42, (byte) 0xc0, (byte) 0x28, (byte) 0xff,
            (byte) 0xe1, (byte) 0x00, (byte) 0x1a, (byte) 0x67, (byte) 0x42, (byte) 0xc0, (byte) 0x28, (byte) 0xdb,
            (byte) 0x02, (byte) 0x80, (byte) 0xf6, (byte) 0xc0, (byte) 0x5a, (byte) 0x80, (byte) 0x80, (byte) 0x80,
            (byte) 0xa0, (byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 0x00, (byte) 0x20, (byte) 0x00, (byte) 0x57,
            (byte) 0xe4, (byte) 0x01, (byte) 0xe3, (byte) 0x06, (byte) 0x5c, (byte) 0x01, (byte) 0x00, (byte) 0x04,
            (byte) 0x68, (byte) 0xca, (byte) 0x8c, (byte) 0xb2};
    private static final byte[] AAC_EXTRA_DATA = {(byte) 0x11, (byte) 0x90};

    public static TrackInfo[] createTrackInfoList() {
        return new TrackInfo[] {
                new TrackInfo(VIDEO_TRACK_ID, VIDEO_CODEC_ID, "VideoTrack", AVCC_EXTRA_DATA, VIDEO),
                new TrackInfo(AUDIO_TRACK_ID, AUDIO_CODEC_ID, "AudioTrack", AAC_EXTRA_DATA, AUDIO)
        };
    }
}
