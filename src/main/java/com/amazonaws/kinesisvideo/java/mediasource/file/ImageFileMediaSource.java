package com.amazonaws.kinesisvideo.java.mediasource.file;

import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_A_SECOND;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_BITRATE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_BUFFER_DURATION_IN_SECONDS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_GOP_DURATION;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_REPLAY_DURATION_IN_SECONDS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_STALENESS_DURATION_IN_SECONDS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_TIMESCALE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.FRAME_RATE_25;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.KEYFRAME_FRAGMENTATION;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.MAX_LATENCY_ZERO;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.NOT_ADAPTIVE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.NO_KMS_KEY_ID;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RECALCULATE_METRICS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RECOVER_ON_FAILURE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RELATIVE_TIMECODES;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.REQUEST_FRAGMENT_ACKS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RETENTION_ONE_HOUR;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.USE_FRAME_TIMECODES;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VERSION_ZERO;

import javax.annotation.Nonnull;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.amazonaws.kinesisvideo.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceConfiguration;
import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceSink;
import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceState;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.mediasource.OnFrameDataAvailable;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Tag;

/**
 * MediaSource based on local image files. Currently, this MediaSource expects
 * a series of H264 frames.
 */
public class ImageFileMediaSource implements MediaSource {
    private static final byte[] AVCC_EXTRA_DATA = {
            (byte) 0x01,
            (byte) 0x64, (byte) 0x00, (byte) 0x28,
            (byte) 0xff, (byte) 0xe1, (byte) 0x00,
            (byte) 0x0e,
            (byte) 0x27, (byte) 0x64, (byte) 0x00, (byte) 0x28, (byte) 0xac, (byte) 0x2b, (byte) 0x40, (byte) 0x50, (byte) 0x1e, (byte) 0xd0, (byte) 0x0f, (byte) 0x12, (byte) 0x26, (byte) 0xa0, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x28, (byte) 0xee, (byte) 0x1f, (byte) 0x2c};

    private static final int FRAME_FLAG_KEY_FRAME = 1;
    private static final int FRAME_FLAG_NONE = 0;
    private static final long FRAME_DURATION_20_MS = 20L;
    private final Log log = LogFactory.getLog(ImageFileMediaSource.class);

    private ImageFileMediaSourceConfiguration imageFileMediaSourceConfiguration;
    private MediaSourceState mediaSourceState;
    private MediaSourceSink mediaSourceSink;
    private ImageFrameSource imageFrameSource;
    private int frameIndex;

    @Override
    public MediaSourceState getMediaSourceState() {
        return mediaSourceState;
    }

    @Override
    public MediaSourceConfiguration getConfiguration() {
        return imageFileMediaSourceConfiguration;
    }

    @Override public StreamInfo getStreamInfo(final String streamName) {
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
                new Tag[] {
                        new Tag("device", "Test Device"),
                        new Tag("stream", "Test Stream") },
                /*
                 * Here we have the CPD hardcoded in AVCC format already, hence no need to adapt NAL.
                 */
                StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_ANNEXB_NALS);
    }

    @Override
    public void initialize(@Nonnull final MediaSourceSink mediaSourceSink) throws KinesisVideoException {
        this.mediaSourceSink = mediaSourceSink;
    }

    @Override
    public void configure(final MediaSourceConfiguration configuration) {
        if (!(configuration instanceof ImageFileMediaSourceConfiguration)) {
            throw new IllegalStateException("Configuration must be an instance of OpenCvMediaSourceConfiguration");
        }

        this.imageFileMediaSourceConfiguration = (ImageFileMediaSourceConfiguration) configuration;
        this.frameIndex = 0;
    }

    @Override
    public void start() throws KinesisVideoException {
        mediaSourceState = MediaSourceState.RUNNING;
        imageFrameSource = new ImageFrameSource(imageFileMediaSourceConfiguration);
        imageFrameSource.onBytesAvailable(createKinesisVideoFrameAndPushToProducer());
        imageFrameSource.start();
    }

    @Override
    public void stop() throws KinesisVideoException {
        if (imageFrameSource != null) {
            imageFrameSource.stop();
        }

        mediaSourceState = MediaSourceState.STOPPED;
    }

    @Override
    public boolean isStopped() {
        return false;
    }

    @Override
    public void free() throws KinesisVideoException {

    }

    private OnFrameDataAvailable createKinesisVideoFrameAndPushToProducer() {
        return data -> {
            final long currentTimeMs = System.currentTimeMillis();

            final int flags = isKeyFrame()
                    ? FRAME_FLAG_KEY_FRAME
                    : FRAME_FLAG_NONE;

            final KinesisVideoFrame frame = new KinesisVideoFrame(
                    frameIndex++,
                    flags,
                    currentTimeMs * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                    currentTimeMs * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                    FRAME_DURATION_20_MS * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                    data);

            if (frame.getSize() == 0) {
                return;
            }

            putFrame(frame);
        };
    }

    private boolean isKeyFrame() {
        return frameIndex % imageFileMediaSourceConfiguration.getFps() == 0;
    }

    private void putFrame(final KinesisVideoFrame kinesisVideoFrame) {
        try {
            mediaSourceSink.onFrame(kinesisVideoFrame);
        } catch (final KinesisVideoException ex) {
            log.error("Failed to put frame with Exception", ex);
        }
    }
}
