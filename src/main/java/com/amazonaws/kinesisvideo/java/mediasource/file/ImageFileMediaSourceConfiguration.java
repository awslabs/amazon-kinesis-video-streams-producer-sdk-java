package com.amazonaws.kinesisvideo.java.mediasource.file;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceConfiguration;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Tag;

import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_A_SECOND;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_AN_HOUR;

public class ImageFileMediaSourceConfiguration implements MediaSourceConfiguration {

    private final int fps;
    private final String dir;
    private final String filenameFormat;
    private final int startFileIndex;
    private final int endFileIndex;

    public ImageFileMediaSourceConfiguration(final Builder builder) {
        this.fps = builder.fps;
        this.dir = builder.dir;
        this.filenameFormat = builder.filenameFormat;
        this.startFileIndex = builder.startFileIndex;
        this.endFileIndex = builder.endFileIndex;
    }

    public int getFps() {
        return fps;
    }

    public String getDir() {
        return dir;
    }

    public String getFilenameFormat() {
        return filenameFormat;
    }

    public int getStartFileIndex() {
        return startFileIndex;
    }

    public int getEndFileIndex() {
        return endFileIndex;
    }

    @Override
    public String getMediaSourceType() {
        return null;
    }

    @Override
    public String getMediaSourceDescription() {
        return null;
    }

    public static class Builder implements MediaSourceConfiguration.Builder<ImageFileMediaSourceConfiguration> {
        private int fps;
        private String dir;
        private String filenameFormat;
        private int startFileIndex;
        private int endFileIndex;

        public Builder fps(final int fps) {
            this.fps = fps;
            if (fps <= 0) {
                throw new IllegalArgumentException("Fps should not be negative or zero.");
            }
            return this;
        }

        public Builder dir(final String dir) {
            this.dir = dir;
            return this;
        }

        public Builder filenameFormat(final String filenameFormat) {
            this.filenameFormat = filenameFormat;
            return this;
        }

        public Builder startFileIndex(final int index) {
            this.startFileIndex = index;
            return this;
        }

        public Builder endFileIndex(final int index) {
            this.endFileIndex = index;
            return this;
        }

        @Override
        public ImageFileMediaSourceConfiguration build() {
            return new ImageFileMediaSourceConfiguration(this);
        }
    }

    private static final boolean NOT_ADAPTIVE = false;
    private static final boolean KEYFRAME_FRAGMENTATION = true;
    private static final boolean SDK_GENERATES_TIMECODES = false;
    private static final boolean RELATIVE_FRAGMENT_TIMECODES = false;
    private static final String NO_KMS_KEY_ID = null;
    private static final int VERSION_ZERO = 0;
    private static final long MAX_LATENCY_ZERO = 0L;
    private static final long RETENTION_ONE_HOUR = 1L * HUNDREDS_OF_NANOS_IN_AN_HOUR;
    private static final boolean REQUEST_FRAGMENT_ACKS = true;
    private static final boolean RECOVER_ON_FAILURE = true;
    private static final long DEFAULT_GOP_DURATION = 2000L * HUNDREDS_OF_NANOS_IN_A_SECOND;
    private static final int DEFAULT_BITRATE = 2_000_000;
    private static final int DEFAULT_TIMESCALE = 10_000;
    private static final int FRAMERATE_30 = 30;
    private static final int FRAME_RATE_25 = 25;
    private static final boolean USE_FRAME_TIMECODES = true;
    private static final boolean ABSOLUTE_TIMECODES = true;
    private static final boolean RELATIVE_TIMECODES = false;
    private static final boolean RECALCULATE_METRICS = true;
    // CHECKSTYLE:SUPPRESS:LineLength
    private static final byte[] AVCC_EXTRA_DATA = {
            (byte) 0x01,
            (byte) 0x64, (byte) 0x00, (byte) 0x28,
            (byte) 0xff, (byte) 0xe1, (byte) 0x00,
            (byte) 0x0e,
            (byte) 0x27, (byte) 0x64, (byte) 0x00, (byte) 0x28, (byte) 0xac, (byte) 0x2b, (byte) 0x40, (byte) 0x50, (byte) 0x1e, (byte) 0xd0, (byte) 0x0f, (byte) 0x12, (byte) 0x26, (byte) 0xa0, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x28, (byte) 0xee, (byte) 0x1f, (byte) 0x2c};
    /**
     * Default buffer duration for a stream
     */
    public static final long DEFAULT_BUFFER_DURATION_IN_SECONDS = 40;

    /**
     * Default replay duration for a stream
     */
    public static final long DEFAULT_REPLAY_DURATION_IN_SECONDS = 20;

    /**
     * Default connection staleness detection duration.
     */
    public static final long DEFAULT_STALENESS_DURATION_IN_SECONDS = 20;

    public StreamInfo toStreamInfo(final String streamName) {
        return new StreamInfo(VERSION_ZERO,
                streamName,
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                "video/h264",
                NO_KMS_KEY_ID,
                RETENTION_ONE_HOUR,
                NOT_ADAPTIVE,
                MAX_LATENCY_ZERO,
                DEFAULT_GOP_DURATION * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                KEYFRAME_FRAGMENTATION,
                USE_FRAME_TIMECODES,
                RELATIVE_TIMECODES,
                REQUEST_FRAGMENT_ACKS,
                RECOVER_ON_FAILURE,
                "V_MPEG4/ISO/AVC",
                "we-did-it",
                DEFAULT_BITRATE,
                FRAME_RATE_25,
                DEFAULT_BUFFER_DURATION_IN_SECONDS * HUNDREDS_OF_NANOS_IN_A_SECOND,
                DEFAULT_REPLAY_DURATION_IN_SECONDS * HUNDREDS_OF_NANOS_IN_A_SECOND,
                DEFAULT_STALENESS_DURATION_IN_SECONDS * HUNDREDS_OF_NANOS_IN_A_SECOND,
                DEFAULT_TIMESCALE,
                RECALCULATE_METRICS,
                AVCC_EXTRA_DATA,
                getTags(),
                /*
                 * Here we have the CPD hardcoded in AVCC format already, hence no need to adapt NAL.
                 */
                StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_ANNEXB_NALS);
    }

    private static Tag[] getTags() {
        final List<Tag> tagList = new ArrayList<>();
        tagList.add(new Tag("device", "Test Device"));
        tagList.add(new Tag("stream", "Test Stream"));
        return tagList.toArray(new Tag[0]);
    }
}
