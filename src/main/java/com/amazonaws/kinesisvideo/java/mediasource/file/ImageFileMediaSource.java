package com.amazonaws.kinesisvideo.java.mediasource.file;

import com.amazonaws.kinesisvideo.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceConfiguration;
import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceSink;
import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceState;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.mediasource.OnFrameDataAvailable;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;

/**
 * MediaSource based on local image files. Currently, this MediaSource expects
 * a series of H264 frames.
 */
public class ImageFileMediaSource implements MediaSource {
    private static final long HUNDREDS_OF_NANOS_IN_MS = 10 * 1000;
    private static final int FRAME_FLAG_KEY_FRAME = 1;
    private static final int FRAME_FLAG_NONE = 0;
    private static final long FRAME_DURATION_20_MS = 20L;
    private static final int FRAGMENT_DURATION_SECONDS = 2;

    private ImageFileMediaSourceConfiguration imageFileMediaSourceConfiguration;
    private MediaSourceState mediaSourceState;
    private MediaSourceSink mediaSourceSink;
    private ImageFrameSource imageFrameSource;
    private int frameIndex;

    @Override
    public MediaSourceState getMediaSourceState() {
        return mediaSourceState;
    }

    @Override
    public MediaSourceConfiguration getConfiguration() {
        return imageFileMediaSourceConfiguration;
    }

    @Override
    public void initialize(@Nonnull final MediaSourceSink mediaSourceSink) throws KinesisVideoException {
        this.mediaSourceSink = mediaSourceSink;
    }

    @Override
    public void configure(final MediaSourceConfiguration configuration) {
        if (!(configuration instanceof ImageFileMediaSourceConfiguration)) {
            throw new IllegalStateException("Configuration must be an instance of OpenCvMediaSourceConfiguration");
        }

        this.imageFileMediaSourceConfiguration = (ImageFileMediaSourceConfiguration) configuration;
        this.frameIndex = 0;
    }

    @Override
    public void start() throws KinesisVideoException {
        mediaSourceState = MediaSourceState.RUNNING;
        imageFrameSource = new ImageFrameSource(imageFileMediaSourceConfiguration);
        imageFrameSource.onBytesAvailable(createKinesisVideoFrameAndPushToProducer());
        imageFrameSource.start();
    }

    @Override
    public void stop() throws KinesisVideoException {
        if (imageFrameSource != null) {
            imageFrameSource.stop();
        }

        mediaSourceState = MediaSourceState.STOPPED;
    }

    @Override
    public boolean isStopped() {
        return false;
    }

    @Override
    public void free() throws KinesisVideoException {

    }

    private OnFrameDataAvailable createKinesisVideoFrameAndPushToProducer() {
        return new OnFrameDataAvailable() {
            @Override
            public void onFrameDataAvailable(final ByteBuffer data) {
                final long currentTimeMs = System.currentTimeMillis();

                final int flags = isKeyFrame()
                        ? FRAME_FLAG_KEY_FRAME
                        : FRAME_FLAG_NONE;

                final KinesisVideoFrame frame = new KinesisVideoFrame(
                        frameIndex++,
                        flags,
                        currentTimeMs * HUNDREDS_OF_NANOS_IN_MS,
                        currentTimeMs * HUNDREDS_OF_NANOS_IN_MS,
                        FRAME_DURATION_20_MS * HUNDREDS_OF_NANOS_IN_MS,
                        data);

                if (frame.getSize() == 0) {
                    return;
                }

                putFrame(frame);
            }
        };
    }

    private boolean isKeyFrame() {
        return frameIndex % imageFileMediaSourceConfiguration.getFps() == 0;
    }

    private void putFrame(final KinesisVideoFrame kinesisVideoFrame) {
        try {
            mediaSourceSink.onFrame(kinesisVideoFrame);
        } catch (final KinesisVideoException ex) {
            throw new RuntimeException(ex);
        }
    }
}
