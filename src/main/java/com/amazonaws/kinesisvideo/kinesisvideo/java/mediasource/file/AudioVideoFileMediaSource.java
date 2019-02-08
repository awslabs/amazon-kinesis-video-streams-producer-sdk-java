package com.amazonaws.kinesisvideo.java.mediasource.file;

import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceState;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceConfiguration;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceSink;
import com.amazonaws.kinesisvideo.internal.mediasource.DefaultOnStreamDataAvailable;
import com.amazonaws.kinesisvideo.internal.mediasource.multitrack.MultiTrackMediaSource;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * MediaSource based on local image files. Currently, this MediaSource expects
 * a series of H264 frames.
 */
public class AudioVideoFileMediaSource extends MultiTrackMediaSource {
    private final String streamName;

    private AudioVideoFileMediaSourceConfiguration mediaSourceConfiguration;
    private MediaSourceState mediaSourceState;
    private MediaSourceSink mediaSourceSink;
    private AudioVideoFrameSource audioVideoFrameSource;
    private CompletableFuture<Boolean> future;

    public AudioVideoFileMediaSource(@Nonnull final String streamName, CompletableFuture<Boolean> future) {
        super(streamName);
        this.streamName = streamName;
        this.future = future;
    }

    public AudioVideoFileMediaSource(@Nonnull final String streamName) {
        this(streamName, new CompletableFuture<Boolean>());
    }

    @Override
    public MediaSourceState getMediaSourceState() {
        return mediaSourceState;
    }

    @Override
    public MediaSourceConfiguration getConfiguration() {
        return mediaSourceConfiguration;
    }

    @Override
    public void initialize(@Nonnull final MediaSourceSink mediaSourceSink) throws KinesisVideoException {
        super.initialize(mediaSourceSink);
        this.mediaSourceSink = mediaSourceSink;
    }

    @Override
    public void configure(@Nonnull final MediaSourceConfiguration configuration) {
        super.configure(configuration);

        Preconditions.checkState(this.mediaSourceConfiguration == null);

        if (!(configuration instanceof AudioVideoFileMediaSourceConfiguration)) {
            throw new IllegalStateException(
                    "Configuration must be an instance of AudioVideoFileMediaSourceConfiguration");
        }
        this.mediaSourceConfiguration = (AudioVideoFileMediaSourceConfiguration) configuration;
    }

    @Override
    public void start() throws KinesisVideoException {
        mediaSourceState = MediaSourceState.RUNNING;
        audioVideoFrameSource = new AudioVideoFrameSource(mediaSourceConfiguration);
        audioVideoFrameSource.onStreamDataAvailable(new DefaultOnStreamDataAvailable(mediaSourceSink));
        audioVideoFrameSource.start();
    }

    @Override
    public void stop() throws KinesisVideoException {
        if (audioVideoFrameSource != null) {
            audioVideoFrameSource.stop();
        }

        try {
            mediaSourceSink.getProducerStream().stopStreamSync();
        } finally {
            mediaSourceState = MediaSourceState.STOPPED;
            future.complete(true);
        }
    }

    @Override
    public boolean isStopped() {
        return false;
    }

    @Override
    public void free() throws KinesisVideoException {
        // No-op
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
