package com.amazonaws.kinesisvideo.client.mediasource;

import javax.annotation.Nonnull;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;

/**
 * Interface representing a media source.
 *
 *
 */
public interface MediaSource {
    /**
     * Returns the {@link MediaSourceState}
     */
    MediaSourceState getMediaSourceState();

    /**
     * Returns the {@link MediaSourceConfiguration} used to create this media source
     */
    MediaSourceConfiguration getConfiguration();

    /**
     * Initializes the media source with a {@link MediaSourceSink} object
     */
    void initialize(@Nonnull final MediaSourceSink mediaSourceSink) throws KinesisVideoException;

    /**
     * Configures the media source
     */
    void configure(final MediaSourceConfiguration configuration);

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
