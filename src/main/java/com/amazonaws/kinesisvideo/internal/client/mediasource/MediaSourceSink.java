package com.amazonaws.kinesisvideo.internal.client.mediasource;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.internal.producer.StreamEventMetadata;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Acts as a media source sink
 *
 *
 */
public interface MediaSourceSink {
    /**
     * Offers a frame from the source.
     */
    void onFrame(final @Nonnull KinesisVideoFrame kinesisVideoFrame) throws KinesisVideoException;

    void onCodecPrivateData(final @Nullable byte[] codecPrivateData) throws KinesisVideoException;

    void onCodecPrivateData(final @Nullable byte[] codecPrivateData, final int trackId) throws KinesisVideoException;

    void onFragmentMetadata(final @Nonnull String metadataName, final @Nonnull String metadataValue, final boolean persistent)
            throws KinesisVideoException;
    
    void onEventMetadata(final int event, @Nonnull final StreamEventMetadata streamEventMetadata)
            throws KinesisVideoException;

    KinesisVideoProducerStream getProducerStream();
}
