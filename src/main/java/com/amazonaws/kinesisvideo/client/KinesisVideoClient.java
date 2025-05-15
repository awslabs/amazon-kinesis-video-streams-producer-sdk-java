package com.amazonaws.kinesisvideo.client;

import com.amazonaws.kinesisvideo.client.mediasource.UnknownMediaSourceException;
import com.amazonaws.kinesisvideo.client.mediasource.UnsupportedConfigurationException;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceConfiguration;
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
     *
     * @return true if initialized. false otherwise.
     */
    boolean isInitialized();

    /**
     * Initializes the client object.
     *
     * @param deviceInfo Device info for which the client needs to be initialized.
     * @throws KinesisVideoException if unable to initialize KinesisVideoClient.
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
     * Register a media source. The media source will be binding to kinesis video producer stream
     * to send out data from media source.
     * Sync call to create the stream and bind to media source.
     *
     * @param mediaSource media source binding to kinesis video producer stream
     * @throws KinesisVideoException if unable to register media source.
     */
    void registerMediaSource(final MediaSource mediaSource) throws KinesisVideoException;

    /**
     * Register a media source ASYNC. The media source will be binding to kinesis video producer stream
     * via CreateStream and send out data from media source.
     * Async call to create the stream and bind to media source.
     *
     * @param mediaSource media source binding to kinesis video producer stream
     * @throws KinesisVideoException if unable to register media source.
     */
    void registerMediaSourceAsync(final MediaSource mediaSource) throws KinesisVideoException;

    /**
     * Un-Register a media source. The media source will stop binding to kinesis video producer stream
     * and it cannot send data via producer stream afterwards until register again.
     * Sync call and could be block for 15 seconds if error happens when stopping stream.
     *
     * @param mediaSource media source to stop binding to kinesis video producer stream
     * @throws KinesisVideoException if unable to unregister media source.
     */
    void unregisterMediaSource(final MediaSource mediaSource) throws KinesisVideoException;

    /**
     * Start all registered media sources
     *
     * @throws KinesisVideoException if unable to start all media sources.
     */
    void startAllMediaSources() throws KinesisVideoException;

    /**
     * Free a media source. Async call to clean up resources if error happens.
     *
     * @param mediaSource media source binding to kinesis video producer stream to be freed
     * @throws KinesisVideoException if unable to free media source.
     */
    void freeMediaSource(@Nonnull final MediaSource mediaSource) throws KinesisVideoException;

    /**
     * Stop all registered media sources
     *
     * @throws KinesisVideoException if unable to stop all media sources.
     */
    void stopAllMediaSources() throws KinesisVideoException;

    /**
     * Try create a media source. Use the mediaSourceConfiguration to determine,
     * the media source type, create the instance, and ensure that it is configured with working
     * parameters
     *
     * @param streamName Stream name for the media source
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
     *
     * @throws KinesisVideoException if unable to free resources.
     */
    void free() throws KinesisVideoException;
}
