package com.amazonaws.kinesisvideo.client.mediasource;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;

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
}
