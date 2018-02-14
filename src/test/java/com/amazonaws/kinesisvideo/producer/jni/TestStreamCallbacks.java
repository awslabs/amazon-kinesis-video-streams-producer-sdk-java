package com.amazonaws.kinesisvideo.producer.jni;

import com.amazonaws.kinesisvideo.producer.KinesisVideoFragmentAck;
import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;

import javax.annotation.Nonnull;
import java.util.concurrent.CountDownLatch;

public class TestStreamCallbacks implements StreamCallbacks {
    private final Log mLog;
    private final CountDownLatch mReadyLatch;

    public TestStreamCallbacks(final @Nonnull Log log, final @Nonnull CountDownLatch readyLatch) {
        mLog = log;
        mReadyLatch = readyLatch;
    }

    @Override
    public void streamUnderflowReport() throws ProducerException {

    }

    @Override
    public void streamLatencyPressure(long duration) throws ProducerException {

    }

    @Override
    public void streamConnectionStale(long lastAckDuration) throws ProducerException {

    }

    @Override
    public void fragmentAckReceived(@Nonnull final KinesisVideoFragmentAck fragmentAck) throws ProducerException {

    }

    @Override
    public void droppedFrameReport(long frameTimecode) throws ProducerException {

    }

    @Override
    public void droppedFragmentReport(long fragmentTimecode) throws ProducerException {

    }

    @Override
    public void streamErrorReport(long fragmentTimecode, long statusCode) throws ProducerException {

    }

    @Override
    public void streamDataAvailable(long uploadHandle, long duration, long availableSize) throws ProducerException {

    }

    @Override
    public void streamReady() throws ProducerException {
        mReadyLatch.countDown();
    }

    @Override
    public void streamClosed(long uploadHandle) throws ProducerException {

    }
}
