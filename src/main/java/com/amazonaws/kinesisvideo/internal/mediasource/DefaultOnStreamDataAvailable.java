package com.amazonaws.kinesisvideo.internal.mediasource;

import javax.annotation.Nullable;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceSink;
import com.amazonaws.kinesisvideo.internal.producer.StreamEventMetadata;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;

public class DefaultOnStreamDataAvailable implements OnStreamDataAvailable {
    final MediaSourceSink mediaSourceSink;

    public DefaultOnStreamDataAvailable(final MediaSourceSink mediaSourceSink) {
        this.mediaSourceSink = mediaSourceSink;
    }

    @Override
    public void onFrameDataAvailable(final KinesisVideoFrame frame) throws KinesisVideoException {
        // ignore frame of size 0
        if (frame.getSize() == 0) {
            throw new KinesisVideoException("Empty frame is provided in frame data available.");
        }

        mediaSourceSink.onFrame(frame);
    }

    @Override
    public void onFragmentMetadataAvailable(final String metadataName, final String metadataValue,
                                            final boolean persistent) throws KinesisVideoException {
        mediaSourceSink.onFragmentMetadata(metadataName, metadataValue, persistent);
    }

    @Override
    public void onEventMetadataAvailable(final int event, @Nullable final StreamEventMetadata streamEventMetadata)
            throws KinesisVideoException {
        mediaSourceSink.onEventMetadata(event, streamEventMetadata);
    }
}
