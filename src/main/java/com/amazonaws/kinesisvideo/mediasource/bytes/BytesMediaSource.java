package com.amazonaws.kinesisvideo.mediasource.bytes;

import static com.amazonaws.kinesisvideo.producer.StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_FLAG_NONE;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_AN_HOUR;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_A_SECOND;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.ABSOLUTE_TIMECODES;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_BITRATE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_BUFFER_DURATION_IN_SECONDS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_GOP_DURATION;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_REPLAY_DURATION_IN_SECONDS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_STALENESS_DURATION_IN_SECONDS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_TIMESCALE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.FRAME_RATE_30;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.KEYFRAME_FRAGMENTATION;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.MAX_LATENCY_ZERO;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.NOT_ADAPTIVE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.NO_KMS_KEY_ID;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RECALCULATE_METRICS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RECOVER_ON_FAILURE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.REQUEST_FRAGMENT_ACKS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.USE_FRAME_TIMECODES;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VERSION_ZERO;

import java.nio.ByteBuffer;

import javax.annotation.Nonnull;

import com.amazonaws.kinesisvideo.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceConfiguration;
import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceSink;
import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceState;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.mediasource.OnFrameDataAvailable;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Tag;

public class BytesMediaSource implements MediaSource {
    private static final String TAG = "BytesMediaSource";
    private static final long HUNDREDS_OF_NANOS_IN_MS = 10 * 1000;
    private static final int KEY_FRAME_EVERY_60_FRAMES = 60;
    private static final int FRAME_FLAG_KEY_FRAME = 1;
    private static final int FRAME_FLAG_NONE = 0;
    private static final long DEFAULT_FRAME_DURATION_33MS = 33L;

    private BytesMediaSourceConfiguration configuration;
    private MediaSourceState mediaSourceState;
    private MediaSourceSink mediaSourceSink;
    private BytesGenerator bytesGenerator;
    private int frameIndex;
    private long lastTimestampMillis;


    @Override
    public MediaSourceState getMediaSourceState() {
        return mediaSourceState;
    }

    @Override
    public MediaSourceConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public StreamInfo getStreamInfo(final String streamName) {
        return new StreamInfo(VERSION_ZERO,
                streamName,
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                "application/octet-stream",
                NO_KMS_KEY_ID,
                configuration.getRetentionPeriodInHours() * HUNDREDS_OF_NANOS_IN_AN_HOUR,
                NOT_ADAPTIVE,
                MAX_LATENCY_ZERO,
                DEFAULT_GOP_DURATION * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                KEYFRAME_FRAGMENTATION,
                USE_FRAME_TIMECODES,
                ABSOLUTE_TIMECODES,
                REQUEST_FRAGMENT_ACKS,
                RECOVER_ON_FAILURE,
                null,
                null,
                DEFAULT_BITRATE,
                FRAME_RATE_30,
                DEFAULT_BUFFER_DURATION_IN_SECONDS * HUNDREDS_OF_NANOS_IN_A_SECOND,
                DEFAULT_REPLAY_DURATION_IN_SECONDS * HUNDREDS_OF_NANOS_IN_A_SECOND,
                DEFAULT_STALENESS_DURATION_IN_SECONDS * HUNDREDS_OF_NANOS_IN_A_SECOND,
                DEFAULT_TIMESCALE,
                RECALCULATE_METRICS,
                null,
                new Tag[] {
                        new Tag("device", "Test Device"),
                        new Tag("stream", "Test Stream") },
                NAL_ADAPTATION_FLAG_NONE);
    }

    @Override
    public void initialize(@Nonnull final MediaSourceSink mediaSourceSink) throws KinesisVideoException {
        this.mediaSourceSink = mediaSourceSink;
    }

    @Override
    public void configure(final MediaSourceConfiguration configuration) {
        if (!(configuration instanceof BytesMediaSourceConfiguration)) {
            throw new IllegalArgumentException("can only use BytesMediaSourceConfiguration");
        }

        this.configuration = (BytesMediaSourceConfiguration) configuration;
    }

    @Override
    public void start() throws KinesisVideoException {
        mediaSourceState = MediaSourceState.RUNNING;
        bytesGenerator = new BytesGenerator(configuration.getFps());
        bytesGenerator.onFrameDataAvailable(createFrameAndPushToProducer());
        bytesGenerator.start();
    }

    private OnFrameDataAvailable createFrameAndPushToProducer() {
        return new OnFrameDataAvailable() {
            @Override
            public void onFrameDataAvailable(final ByteBuffer data) {
                final long currentTimeMs = System.currentTimeMillis();
                final long decodingTs = currentTimeMs * HUNDREDS_OF_NANOS_IN_MS;
                final long presentationTs = currentTimeMs * HUNDREDS_OF_NANOS_IN_MS;
                final long msSinceLastFrame = currentTimeMs - lastTimestampMillis;
                final long frameDuration = lastTimestampMillis == 0
                        ? DEFAULT_FRAME_DURATION_33MS * HUNDREDS_OF_NANOS_IN_MS
                        : msSinceLastFrame * HUNDREDS_OF_NANOS_IN_MS / 2;

                final int flags = isKeyFrame()
                        ? FRAME_FLAG_KEY_FRAME
                        : FRAME_FLAG_NONE;


                final KinesisVideoFrame frame = new KinesisVideoFrame(
                        frameIndex++,
                        flags,
                        decodingTs,
                        presentationTs,
                        frameDuration,
                        data);

                // ignore frame of size 0 or duration of 0
                if (frame.getSize() == 0 || frameDuration == 0) {
                    return;
                }

                lastTimestampMillis = currentTimeMs;
                submitFrameOnUIThread(frame);
            }
        };
    }

    private boolean isKeyFrame() {
        return frameIndex % KEY_FRAME_EVERY_60_FRAMES == 0;
    }

    private void submitFrameOnUIThread(final KinesisVideoFrame frame) {
        try {
            mediaSourceSink.onFrame(frame);
        } catch (final KinesisVideoException e) {
            // TODO: log/throw
        }
    }

    @Override
    public void stop() throws KinesisVideoException {
        if (bytesGenerator != null) {
            bytesGenerator.stop();
        }

        mediaSourceState = MediaSourceState.STOPPED;
    }

    @Override
    public boolean isStopped() {
        return mediaSourceState == MediaSourceState.STOPPED;
    }

    @Override
    public void free() throws KinesisVideoException { }
}
