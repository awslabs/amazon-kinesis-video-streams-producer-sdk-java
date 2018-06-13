package com.amazonaws.kinesisvideo.mediasource;

import static com.amazonaws.kinesisvideo.common.preconditions.Preconditions.checkNotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceSink;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.KinesisVideoProducerStream;

/**
 * Implementation of the MediaSourceSink interface that pushes frames and stream configuration
 * into an instance of KinesisVideoProducerStream.
 *
 * Primary purpose of this is to be used by the KinesisVideoClient implementation.
 *
 * For example, when an instance of media source is being created or registered with KinesisVideoClient,
 * an instance of this sink is created, and the media source is initialized with this.
 *
 * It's then media source's job to produce the frames and push them into the sink
 * it has been initialized with
 */
public class ProducerStreamSink implements MediaSourceSink {
    private final KinesisVideoProducerStream producerStream;

    public ProducerStreamSink(final KinesisVideoProducerStream producerStream) {
        this.producerStream = producerStream;
    }

    @Override
    public void onFrame(@Nonnull final KinesisVideoFrame kinesisVideoFrame) throws KinesisVideoException {
        checkNotNull(kinesisVideoFrame);
        producerStream.putFrame(kinesisVideoFrame);
    }

    @Override
    public void onCodecPrivateData(@Nullable final byte[] bytes) throws KinesisVideoException {
        producerStream.streamFormatChanged(bytes);
    }

}
