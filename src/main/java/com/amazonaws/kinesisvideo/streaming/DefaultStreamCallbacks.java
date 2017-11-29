package com.amazonaws.kinesisvideo.streaming;

import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;

public class DefaultStreamCallbacks implements StreamCallbacks {
    @Override
    public void streamUnderflowReport() throws ProducerException {
        // no-op
    }

    @Override
    public void streamLatencyPressure(final long duration) throws ProducerException {
        // no-op
    }

    @Override
    public void streamConnectionStale(final long duration) throws ProducerException {
        // no-op
    }

    @Override
    public void droppedFrameReport(final long frameTimecode) throws ProducerException {
        // no-op
    }

    @Override
    public void streamErrorReport(final long frameTimecode, final long statusCode) throws ProducerException {
        // no-op
    }

    @Override
    public void droppedFragmentReport(final long fragmentTimecode) throws ProducerException {
        // no-op
    }

    @Override
    public void streamDataAvailable(final long duration, final long availableSize) throws ProducerException {
        // no-op
    }

    @Override
    public void streamReady() throws ProducerException {
        // no-op
    }

    @Override
    public void streamClosed() throws ProducerException {
        // no-op
    }
}
