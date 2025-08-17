package com.amazonaws.kinesisvideo.internal.mediasource;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceSink;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;

import javax.annotation.Nonnull;

/**
 * Forwards the received media data to the configured {@link MediaSourceSink}.
 */
public class DefaultOnStreamDataAvailable implements OnStreamDataAvailable {
    @Nonnull
    final MediaSourceSink mediaSourceSink;

    @SuppressWarnings("ConstantConditions")
    public DefaultOnStreamDataAvailable(@Nonnull final MediaSourceSink mediaSourceSink) {
        Preconditions.checkArgument(mediaSourceSink != null, "MediaSourceSink cannot be null");
        this.mediaSourceSink = mediaSourceSink;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void onFrameDataAvailable(@Nonnull final KinesisVideoFrame frame) throws KinesisVideoException {
        Preconditions.checkArgument(frame != null, "KinesisVideoFrame cannot be null");

        // ignore frame of size 0
        if (frame.getSize() == 0) {
            throw new KinesisVideoException("Empty frame is provided in frame data available.");
        }

        this.mediaSourceSink.onFrame(frame);
    }

    @Override
    public void onFragmentMetadataAvailable(@Nonnull final String metadataName, @Nonnull final String metadataValue,
                                            final boolean persistent) throws KinesisVideoException {
        Preconditions.checkArgument(metadataName != null, "Metadata name cannot be null");
        Preconditions.checkArgument(metadataValue != null, "Metadata value cannot be null");
        this.mediaSourceSink.onFragmentMetadata(metadataName, metadataValue, persistent);
    }
}
