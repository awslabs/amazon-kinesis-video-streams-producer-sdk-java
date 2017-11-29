package com.amazonaws.kinesisvideo.client;

import com.amazonaws.kinesisvideo.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceConfiguration;
import com.amazonaws.kinesisvideo.client.mediasource.UnknownMediaSourceException;
import com.amazonaws.kinesisvideo.client.mediasource.UnsupportedConfigurationException;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;

import javax.annotation.Nonnull;
import java.util.List;

/**
 *
 * Top level interface representing an Kinesis Video Streams client
 *
 *
 */
public interface KinesisVideoClient {
    /**
     * Returns whether the client has been initialized
     */
    boolean isInitialized();

    /**
     * Initializes the client object.
     */
    void initialize(@Nonnull final DeviceInfo deviceInfo)
            throws KinesisVideoException;

    /**
     * List few known media sources available to the application. The configurations returned
     * are expected to be working as is. The returned list is not exhaustive,
     * some working configurations will likely be missing from the list.
     *
     * @return list of configuration builders to allow further configuration
     */
    List<MediaSourceConfiguration.Builder<? extends MediaSourceConfiguration>> listSupportedConfigurations();

    /**
     * Register a media source
     */
    void registerMediaSource(final String streamName, final MediaSource mediaSource) throws KinesisVideoException;

    /**
     * Start all registered media sources
     */
    void startAllMediaSources() throws KinesisVideoException;

    /**
     * Stop all registered media sources
     */
    void stopAllMediaSources() throws KinesisVideoException;

    /**
     * Try create a media source. Use the mediaSourceConfiguration to determine,
     * the media source type, create the instance, and ensure that it is configured with working
     * parameters
     *
     * @param mediaSourceConfiguration, configuration to create specific media source
     * @return configured and working media source
     * @throws UnsupportedConfigurationException is thrown when the configuration is not supported,
     *                                           e.g. camera resolution or encoding
     * @throws UnknownMediaSourceException       is thrown when the media source type is unknown and
     *                                           cannot be created
     */
    MediaSource createMediaSource(
            final String streamName,
            final MediaSourceConfiguration mediaSourceConfiguration) throws KinesisVideoException;


    /**
     * Stops the media sources and frees/releases the underlying objects
     */
    void free() throws KinesisVideoException;
}
