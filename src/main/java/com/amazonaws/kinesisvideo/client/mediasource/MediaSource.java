package com.amazonaws.kinesisvideo.client.mediasource;

import javax.annotation.Nonnull;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.producer.StreamInfo;

/**
 * Interface representing a media source.
 */
public interface MediaSource {
    /**
     * Returns the {@link MediaSourceState}
     */
    MediaSourceState getMediaSourceState();

    /**
     * Returns the {@link MediaSourceConfiguration} used to create this media source
     * @deprecated Once constructed, configuration should be private. This method will be removed in a future release.
     */
    @Deprecated
    default MediaSourceConfiguration getConfiguration() {
        throw new UnsupportedOperationException("Once constructed, configuration should be private. This method will be removed in a future release.");
    }

    /**
     * Returns the {@link StreamInfo} describing the stream this media source produces
     */
    StreamInfo getStreamInfo(String streamName);

    /**
     * Initializes the media source with a {@link MediaSourceSink} object
     */
    void initialize(@Nonnull MediaSourceSink mediaSourceSink) throws KinesisVideoException;

    /**
     * Configures the media source
     * @deprecated {@link MediaSource} configuration should be specified on construction and not modified thereafter. This method will be
     *      removed in a future release.
     */
    @Deprecated
    default void configure(MediaSourceConfiguration configuration) {
        throw new UnsupportedOperationException("MediaSources should be configured on initialization and not modified thereafter. " +
                "This method will be removed in a future release.");
    }

    /**
     * Starts the media source
     */
    void start() throws KinesisVideoException;

    /**
     * Stops the media source
     */
    void stop() throws KinesisVideoException;

    /**
     * Returns true if media source is now stopped
     */
    boolean isStopped();

    /**
     * Releases resources
     */
    void free() throws KinesisVideoException;
}
