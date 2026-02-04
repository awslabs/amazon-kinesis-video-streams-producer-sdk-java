package com.amazonaws.kinesisvideo.internal.mediasource;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.internal.producer.StreamEventMetadata;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;

/**
 * In a media pipeline, the "source" (an object that generates or produces data), will forward
 * the data to a "sink" (an object that receives the data).
 *
 * @see com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSource
 * @see com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceSink
 * @see com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSource
 * @see ProducerStreamSink
 */
public interface OnStreamDataAvailable {
    default void onFrameDataAvailable(@Nonnull final ByteBuffer frame) throws KinesisVideoException {
        // no-op
    }

    default void onFrameDataAvailable(@Nonnull final KinesisVideoFrame frame) throws KinesisVideoException {
        // no-op
    }

    default void onFragmentMetadataAvailable(@Nonnull final String metadataName, @Nonnull final String metadataValue,
                                             final boolean persistent) throws KinesisVideoException {
        // no-op
    }
    default void onEventMetadataAvailable(final int event, final StreamEventMetadata streamEventMetadata) throws KinesisVideoException {
        // no-op
    }
}
