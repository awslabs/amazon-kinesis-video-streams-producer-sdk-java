package com.amazonaws.kinesisvideo.internal.mediasource.multitrack;

import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceState;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceConfiguration;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceSink;
import com.amazonaws.kinesisvideo.internal.mediasource.DefaultOnStreamDataAvailable;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;
import com.amazonaws.kinesisvideo.producer.StreamInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_AN_HOUR;

import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_BITRATE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_GOP_DURATION;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.KEYFRAME_FRAGMENTATION;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.NOT_ADAPTIVE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.NO_KMS_KEY_ID;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RECALCULATE_METRICS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RECOVER_ON_FAILURE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.REQUEST_FRAGMENT_ACKS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.USE_FRAME_TIMECODES;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VERSION_ZERO;

public class MultiTrackMediaSource implements MediaSource {
    private final String streamName;

    private MediaSourceState mediaSourceState;
    private MediaSourceSink mediaSourceSink;
    private MultiTrackFrameSource frameSource;
    private MultiTrackMediaSourceConfiguration configuration;

    public MultiTrackMediaSource(final @Nonnull String streamName) {
        this.streamName = streamName;
    }

    @Override
    public MediaSourceState getMediaSourceState() {
        return  mediaSourceState;
    }

    @Override
    public MediaSourceConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public StreamInfo getStreamInfo() throws KinesisVideoException {
        return new StreamInfo(VERSION_ZERO,
                streamName,
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                configuration.getContentType(),
                NO_KMS_KEY_ID,
                configuration.getRetentionPeriodInHours() * HUNDREDS_OF_NANOS_IN_AN_HOUR,
                NOT_ADAPTIVE,
                configuration.getLatencyPressure(),
                DEFAULT_GOP_DURATION,
                KEYFRAME_FRAGMENTATION,
                USE_FRAME_TIMECODES,
                configuration.isAbsoluteTimecode(),
                REQUEST_FRAGMENT_ACKS,
                RECOVER_ON_FAILURE,
                DEFAULT_BITRATE,
                configuration.getFps(),
                configuration.getBufferDuration(),
                configuration.getReplayDuration(),
                configuration.getStalenessDuration(),
                configuration.getTimecodeScale(),
                RECALCULATE_METRICS,
                null,
                configuration.getNalAdaptationFlag(),
                null,
                configuration.getTrackInfoList());
    }

    @Override
    public void initialize(@Nonnull final MediaSourceSink mediaSourceSink) throws KinesisVideoException {
        this.mediaSourceSink = mediaSourceSink;
    }

    @Override
    public void configure(final MediaSourceConfiguration configuration) {
        Preconditions.checkState(this.configuration == null);

        if (!(configuration instanceof MultiTrackMediaSourceConfiguration)) {
            throw new IllegalArgumentException("can only use MultiTrackMediaSourceConfiguration");
        }

        this.configuration = (MultiTrackMediaSourceConfiguration) configuration;
    }

    @Override
    public void start() throws KinesisVideoException {
        frameSource = new MultiTrackFrameSource(configuration);
        frameSource.onStreamDataAvailable(new DefaultOnStreamDataAvailable(mediaSourceSink));
        frameSource.start();
    }

    @Override
    public void stop() throws KinesisVideoException {
        if (frameSource != null) {
            frameSource.stop();
        }
        mediaSourceSink.getProducerStream().stopStreamSync();

        mediaSourceState = MediaSourceState.STOPPED;
    }

    @Override
    public boolean isStopped() {
        return mediaSourceState == MediaSourceState.STOPPED;
    }

    @Override
    public void free() throws KinesisVideoException {
    }

    @Override
    public MediaSourceSink getMediaSourceSink() {
        return mediaSourceSink;
    }

    @Nullable
    @Override
    public StreamCallbacks getStreamCallbacks() {
        return null;
    }
}
